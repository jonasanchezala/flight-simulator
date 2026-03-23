package com.flight.simulator.dto;

import lombok.Builder;

@Builder
public record ErrorResponse(int status, String error, String message) {
}
