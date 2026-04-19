package com.sportevents.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Payload for creating a new sport event")
public class CreateEventRequest {

    @Schema(description = "Human-readable event name", example = "Champions League Final", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    private String name;

    @Schema(description = "Sport type (free-form string)", example = "football", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Sport is required")
    private String sport;

    @Schema(description = "Event start time in ISO-8601 format", example = "2030-06-01T20:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSport() { return sport; }
    public void setSport(String sport) { this.sport = sport; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
}
