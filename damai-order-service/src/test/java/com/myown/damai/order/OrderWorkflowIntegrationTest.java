package com.myown.damai.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.client.ProgramInventoryClient;
import com.myown.damai.order.client.ProgramOrderSnapshot;
import com.myown.damai.order.client.ProgramTicketPriceSnapshot;
import com.myown.damai.order.client.TicketUserClient;
import com.myown.damai.order.dao.OrderAsyncMessageDao;
import com.myown.damai.order.dao.OrderDao;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderPayRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import com.myown.damai.order.entity.OrderStatus;
import com.myown.damai.order.messaging.OrderAsyncConsumer;
import com.myown.damai.order.messaging.OrderAsyncProducer;
import com.myown.damai.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises the asynchronous and concurrent order workflow through real Spring transactions and MyBatis persistence.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "damai.order.async.enabled=false",
        "damai.order.inventory.enabled=true",
        "damai.order.timeout-scan-enabled=false",
        "damai.order.lock.redisson-enabled=false"
})
class OrderWorkflowIntegrationTest {

    private static final Long SHOW_TIME_ID = 60001L;
    private static final Long TICKET_CATEGORY_ID = 50001L;
    private static final BigDecimal TICKET_PRICE = new BigDecimal("680");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private OrderAsyncMessageDao orderAsyncMessageDao;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProgramInventoryClient programInventoryClient;

    @MockBean
    private TicketUserClient ticketUserClient;

    /**
     * Resets external service mocks before each database-backed workflow test.
     */
    @BeforeEach
    void setUpExternalServiceMocks() {
        reset(programInventoryClient, ticketUserClient);
        when(programInventoryClient.getOrderSnapshot(anyLong(), anyLong(), anyList()))
                .thenAnswer(invocation -> programSnapshot(invocation.getArgument(0)));
    }

    /**
     * Verifies a redelivered Kafka message is claimed once and creates only one persisted order.
     */
    @Test
    void duplicateKafkaConsumptionCreatesOneOrder() throws Exception {
        Long programId = 91001L;
        Long userId = 92001L;
        Long orderNumber = 99002001L;
        OrderCreateRequest request = new OrderCreateRequest(
                programId,
                SHOW_TIME_ID,
                TICKET_CATEGORY_ID,
                userId,
                List.of(93001L)
        );
        OrderAsyncCreateMessage message = new OrderAsyncCreateMessage(
                "order-create:" + orderNumber,
                orderNumber,
                request,
                programSnapshot(programId)
        );
        String payload = objectMapper.writeValueAsString(message);
        OrderAsyncProducer retryProducer = mock(OrderAsyncProducer.class);
        OrderAsyncConsumer consumer = new OrderAsyncConsumer(
                objectMapper,
                orderService,
                orderAsyncMessageDao,
                retryProducer,
                3
        );

        consumer.consumeCreateOrderMessage(payload, "damai-order-create-test", String.valueOf(programId), 0, 1L);
        consumer.consumeCreateOrderMessage(payload, "damai-order-create-test", String.valueOf(programId), 0, 2L);

        Order persistedOrder = orderDao.findOrderByOrderNumber(orderNumber).orElseThrow();
        OrderAsyncMessage trackedMessage = orderAsyncMessageDao.findByMessageKey(message.messageKey()).orElseThrow();
        assertEquals(orderNumber, persistedOrder.orderNumber);
        assertEquals(OrderAsyncMessageStatus.SUCCEEDED.code(), trackedMessage.messageStatus);
        assertEquals(1, orderDao.listOrdersByUserId(userId, 10, 0).size());
        verify(programInventoryClient, times(1)).lockInventory(anyLong(), any());
        verify(retryProducer, never()).sendRetryOrderMessage(any(), any());
        verify(retryProducer, never()).sendDeadLetterMessage(any(), any());
    }

    /**
     * Verifies concurrent payment and cancellation produce exactly one terminal winner and one inventory action.
     */
    @Test
    void paymentAndCancellationRaceHasSingleWinner() throws Exception {
        Long programId = 91002L;
        Long userId = 92002L;
        OrderResponse createdOrder = orderService.createOrder(new OrderCreateRequest(
                programId,
                SHOW_TIME_ID,
                TICKET_CATEGORY_ID,
                userId,
                List.of(93002L)
        ));
        clearInvocations(programInventoryClient);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> paymentResult = executor.submit(() -> runRaceAction(
                    ready,
                    start,
                    () -> orderService.markOrderPaid(
                            createdOrder.orderNumber(),
                            new OrderPayRequest("trade-race-1", TICKET_PRICE, Instant.now())
                    ),
                    "PAID"
            ));
            Future<String> cancellationResult = executor.submit(() -> runRaceAction(
                    ready,
                    start,
                    () -> orderService.cancelOrderForUser(createdOrder.orderNumber(), userId),
                    "CANCELED"
            ));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Set<String> results = Set.of(
                    paymentResult.get(10, TimeUnit.SECONDS),
                    cancellationResult.get(10, TimeUnit.SECONDS)
            );
            assertTrue(results.contains("PAID") || results.contains("CANCELED"));
            assertTrue(results.stream().anyMatch(result -> result.startsWith("CONFLICT:")));
        } finally {
            executor.shutdownNow();
        }

        Order finalOrder = orderDao.findOrderByOrderNumber(createdOrder.orderNumber()).orElseThrow();
        OrderStatus finalStatus = OrderStatus.fromCode(finalOrder.orderStatus);
        assertTrue(finalStatus == OrderStatus.PAID || finalStatus == OrderStatus.CANCELED);
        assertNotNull(finalOrder.updatedAt);
        if (finalStatus == OrderStatus.PAID) {
            verify(programInventoryClient, times(1)).markInventorySold(anyLong(), any());
            verify(programInventoryClient, never()).releaseInventory(anyLong(), any());
        } else {
            verify(programInventoryClient, times(1)).releaseInventory(anyLong(), any());
            verify(programInventoryClient, never()).markInventorySold(anyLong(), any());
        }
    }

    /**
     * Runs one synchronized race action and converts the expected losing conflict into a result value.
     */
    private String runRaceAction(
            CountDownLatch ready,
            CountDownLatch start,
            RaceAction action,
            String successResult
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            action.run();
            return successResult;
        } catch (BusinessException exception) {
            return "CONFLICT:" + exception.getCode();
        }
    }

    /**
     * Builds the trusted program snapshot used by the order integration boundary.
     */
    private ProgramOrderSnapshot programSnapshot(Long programId) {
        return new ProgramOrderSnapshot(
                programId,
                SHOW_TIME_ID,
                "Integration Concert",
                "Integration Arena",
                "https://example.com/integration.png",
                Instant.parse("2026-08-01T12:00:00Z"),
                1,
                List.of(new ProgramTicketPriceSnapshot(TICKET_CATEGORY_ID, TICKET_PRICE))
        );
    }

    /**
     * Represents one order state transition participating in a synchronized race.
     */
    @FunctionalInterface
    private interface RaceAction {

        /**
         * Executes the transition.
         */
        void run();
    }
}
