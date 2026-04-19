package com.sportevents.dto;

import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Represents a sport event returned by the API")
public class SportEventResponse {

    @Schema(description = "Unique event identifier (UUID)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String id;

    @Schema(description = "Human-readable event name", example = "Champions League Final")
    private String name;

    @Schema(description = "Sport type", example = "football")
    private String sport;

    @Schema(description = "Current event status", example = "INACTIVE")
    private SportEventStatus status;

    @Schema(description = "Scheduled start time (ISO-8601)", example = "2030-06-01T20:00:00")
    private LocalDateTime startTime;

    public static SportEventResponse from(SportEvent event) {
        SportEventResponse r = new SportEventResponse();
        r.id = event.getId();
        r.name = event.getName();
        r.sport = event.getSport();
        r.status = event.getStatus();
        r.startTime = event.getStartTime();
        return r;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSport() { return sport; }
    public SportEventStatus getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
}
