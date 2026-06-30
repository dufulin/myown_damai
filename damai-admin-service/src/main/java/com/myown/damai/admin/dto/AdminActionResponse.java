package com.myown.damai.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Exposes the result returned by a privileged downstream operation.
 */
public record AdminActionResponse(
        String action,
        JsonNode result
) {
}
