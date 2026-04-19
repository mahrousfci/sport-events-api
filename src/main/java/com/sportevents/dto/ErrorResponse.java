package com.sportevents.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ErrorResponse", description = "Standard error body returned on 4xx/5xx responses")
public class ErrorResponse {

    @Schema(description = "ISO-8601 timestamp of the error", example = "2030-06-01T20:00:00")
    private String timestamp;

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "HTTP reason phrase", example = "Not Found")
    private String error;

    @Schema(description = "Human-readable error message", example = "Sport event not found with id: abc123")
    private String message;

    // Getters and setters omitted — this class is documentation-only
    public String getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
}
