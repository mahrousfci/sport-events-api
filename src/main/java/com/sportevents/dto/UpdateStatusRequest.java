package com.sportevents.dto;

import com.sportevents.model.SportEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for updating a sport event's status")
public class UpdateStatusRequest {

    @Schema(description = "Target status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Status is required")
    private SportEventStatus status;

    public SportEventStatus getStatus() { return status; }
    public void setStatus(SportEventStatus status) { this.status = status; }
}
