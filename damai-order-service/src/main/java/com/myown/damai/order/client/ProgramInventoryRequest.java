package com.myown.damai.order.client;

import java.util.List;

/**
 * Carries an order inventory operation request sent to the program service.
 */
public record ProgramInventoryRequest(
        Long orderNumber,
        List<ProgramInventoryItemRequest> items
) {
}
