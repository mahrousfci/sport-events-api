package com.sportevents.controller;

import com.sportevents.dto.CreateEventRequest;
import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.dto.UpdateStatusRequest;
import com.sportevents.model.SportEventStatus;
import com.sportevents.service.SportEventService;
import com.sportevents.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Sport Events", description = "CRUD operations and real-time subscriptions for sport events")
public class SportEventController {

    private final SportEventService service;
    private final SseEmitterRegistry sseRegistry;

    public SportEventController(SportEventService service, SseEmitterRegistry sseRegistry) {
        this.service     = service;
        this.sseRegistry = sseRegistry;
    }

    // ── POST /api/events ───────────────────────────────────────────────────
    @Operation(summary = "Create a sport event",
               description = "Creates a new sport event with INACTIVE status. Broadcasts EVENT_CREATED to all subscribers.",
               security = @SecurityRequirement(name = "ApiKeyAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Event created",
            content = @Content(schema = @Schema(implementation = SportEventResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PostMapping
    public ResponseEntity<SportEventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEvent(request));
    }

    // ── GET /api/events ────────────────────────────────────────────────────
    @Operation(summary = "List sport events (paginated)",
               description = "Returns a paginated list of events. Filter by `status` and/or `sport`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of events")
    })
    @GetMapping
    public ResponseEntity<PagedResponse<SportEventResponse>> listEvents(
            @Parameter(description = "Filter by event status")
            @RequestParam(required = false) SportEventStatus status,
            @Parameter(description = "Filter by sport type (case-insensitive)")
            @RequestParam(required = false) String sport,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1–100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(service.getAllEvents(status, sport, page, size));
    }

    // ── GET /api/events/{id} ───────────────────────────────────────────────
    @Operation(summary = "Get a sport event by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event found",
            content = @Content(schema = @Schema(implementation = SportEventResponse.class))),
        @ApiResponse(responseCode = "404", description = "Event not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SportEventResponse> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(service.getEventById(id));
    }

    // ── PATCH /api/events/{id}/status ─────────────────────────────────────
    @Operation(summary = "Change event status",
               description = """
                   INACTIVE → ACTIVE (startTime must be future) | ACTIVE → FINISHED.
                   All other transitions are forbidden. Protected by concurrent-write detection.
                   """,
               security = @SecurityRequirement(name = "ApiKeyAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated",
            content = @Content(schema = @Schema(implementation = SportEventResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid API key",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @ApiResponse(responseCode = "404", description = "Event not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
        @ApiResponse(responseCode = "422", description = "Invalid transition or concurrent modification",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<SportEventResponse> updateStatus(
            @Parameter(description = "Event UUID", required = true) @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.getStatus()));
    }

    // ── GET /api/events/subscribe (SSE — all) ─────────────────────────────
    @Operation(summary = "Subscribe to all event updates (SSE)",
               description = """
                   Opens a `text/event-stream`. Pushes:
                   - `event-created` on new events
                   - `event-update` on status changes

                   ```bash
                   curl -N http://localhost:8080/api/events/subscribe
                   ```
                   """)
    @ApiResponse(responseCode = "200", description = "SSE stream opened")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToAll() {
        return sseRegistry.registerGlobal();
    }

    // ── GET /api/events/{id}/subscribe (SSE — single) ─────────────────────
    @Operation(summary = "Subscribe to a single event's updates (SSE)",
               description = "Opens a `text/event-stream` scoped to one event. Returns 404 if event not found.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SSE stream opened"),
        @ApiResponse(responseCode = "404", description = "Event not found",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
    })
    @GetMapping(value = "/{id}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvent(@PathVariable String id) {
        service.getEventById(id);
        return sseRegistry.registerForEvent(id);
    }
}
