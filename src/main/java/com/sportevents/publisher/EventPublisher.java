package com.sportevents.publisher;

import com.sportevents.dto.SportEventResponse;

/**
 * Abstraction over all real-time push channels (SSE, WebSocket, …).
 *
 * The service holds a {@code List<EventPublisher>} and iterates it, so adding
 * a new channel (e.g. webhooks) requires zero changes to the service layer.
 */
public interface EventPublisher {

    /**
     * Called when an event is first created.
     * The payload type distinguishes this from a status update at the receiver.
     */
    void publishCreated(SportEventResponse event);

    /**
     * Called whenever an event's status changes.
     */
    void publishUpdated(SportEventResponse event);
}
