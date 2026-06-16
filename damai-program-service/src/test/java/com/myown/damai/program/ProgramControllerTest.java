package com.myown.damai.program;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies the basic program service API workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProgramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies category creation, program creation, listing, detail query, and seat initialization.
     */
    @Test
    void createProgramListDetailAndSeats() throws Exception {
        Long parentCategoryId = createCategory(0L, "Concert", 1);
        Long categoryId = createCategory(parentCategoryId, "Pop", 2);

        MvcResult programResult = mockMvc.perform(post("/api/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "areaId": 110000,
                                  "programCategoryId": %d,
                                  "parentProgramCategoryId": %d,
                                  "title": "Demo Concert",
                                  "actor": "Demo Singer",
                                  "place": "Demo Arena",
                                  "detail": "A database-backed program detail.",
                                  "importantNotice": "Please read ticket rules before buying.",
                                  "refundTicketRule": "No refund after ticket issued.",
                                  "entryRule": "Enter with valid ticket and ID.",
                                  "kindReminder": "Arrive early and keep your ticket safe.",
                                  "performanceDuration": "120 minutes",
                                  "entryTime": "60 minutes before showtime",
                                  "permitChooseSeat": 1,
                                  "highHeat": 1,
                                  "showTimes": [
                                    {
                                      "showTime": "2026-08-01T12:00:00Z"
                                    }
                                  ],
                                  "ticketCategories": [
                                    {
                                      "introduce": "VIP",
                                      "price": 680,
                                      "totalNumber": 20
                                    }
                                  ]
                                }
                                """.formatted(categoryId, parentCategoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.program.id", notNullValue()))
                .andExpect(jsonPath("$.data.program.title").value("Demo Concert"))
                .andExpect(jsonPath("$.data.notices.refundTicketRule").value("No refund after ticket issued."))
                .andExpect(jsonPath("$.data.showTimes", hasSize(1)))
                .andExpect(jsonPath("$.data.ticketCategories", hasSize(1)))
                .andReturn();

        JsonNode programJson = objectMapper.readTree(programResult.getResponse().getContentAsString());
        Long programId = programJson.path("data").path("program").path("id").asLong();
        Long ticketCategoryId = programJson.path("data").path("ticketCategories").get(0).path("id").asLong();

        mockMvc.perform(get("/api/programs")
                        .param("keyword", "Demo")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("areaId", "110000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(programId));

        mockMvc.perform(get("/api/programs/{programId}", programId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.program.id").value(programId))
                .andExpect(jsonPath("$.data.detail").value("A database-backed program detail."))
                .andExpect(jsonPath("$.data.notices.entryRule").value("Enter with valid ticket and ID."))
                .andExpect(jsonPath("$.data.ticketCategories[0].remainNumber").value(20));

        mockMvc.perform(post("/api/programs/{programId}/seats", programId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticketCategoryId": %d,
                                  "startRow": 1,
                                  "endRow": 2,
                                  "startCol": 1,
                                  "endCol": 3,
                                  "seatType": 1
                                }
                                """.formatted(ticketCategoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data", hasSize(6)));

        mockMvc.perform(get("/api/programs/{programId}/seats", programId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)));
    }

    /**
     * Creates one category and returns its generated id.
     */
    private Long createCategory(Long parentId, String name, Integer type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/programs/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": %d,
                                  "name": "%s",
                                  "type": %d
                                }
                                """.formatted(parentId, name, type)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("data").path("id").asLong();
    }
}
