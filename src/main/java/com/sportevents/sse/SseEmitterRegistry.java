package com.sportevents.sse;

import com.sportevents.dto.SportEventResponse;
import com.sportevents.publisher.EventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE implementation of {@link EventPublisher}.
 *
 * Subscription modes:
 *  - Global   → GET /api/events/subscribe
 *  - Per-event → GET /api/events/{id}/subscribe
 *
 * SSE event names:
 *  - "connected"    : sent once on registration
 *  - "event-created": new event created
 *  - "event-update" : event status changed
 */
@Component
public class SseEmitterRegistry implements EventPublisher {

    private final List<SseEmitter> globalEmitters = new CopyOnWriteArrayList<>();
    private final Map<String, List<SseEmitter>> eventEmitters = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public SseEmitter registerGlobal() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        globalEmitters.add(emitter);
        emitter.onCompletion(() -> globalEmitters.remove(emitter));
        emitter.onTimeout(()    -> globalEmitters.remove(emitter));
        emitter.onError(e       -> globalEmitters.remove(emitter));
        sendRaw(emitter, "connected", "Subscription established. Listening for updates...");
        return emitter;
    }

    public SseEmitter registerForEvent(String eventId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        eventEmitters.computeIfAbsent(eventId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEventEmitter(eventId, emitter));
        emitter.onTimeout(()    -> removeEventEmitter(eventId, emitter));
        emitter.onError(e       -> removeEventEmitter(eventId, emitter));
        sendRaw(emitter, "connected", "Subscription established. Listening for updates...");
        return emitter;
    }

    // -----------------------------------------------------------------------
    // EventPublisher implementation
    // -----------------------------------------------------------------------

    @Override
    public void publishCreated(SportEventResponse event) {
        push("event-created", event, event.getId());
    }

    @Override
    public void publishUpdated(SportEventResponse event) {
        push("event-update", event, event.getId());
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void push(String eventName, Object data, String eventId) {
        SseEmitter.SseEventBuilder built = SseEmitter.event().name(eventName).data(data);
        globalEmitters.forEach(e -> trySend(e, built));
        eventEmitters.getOrDefault(eventId, List.of()).forEach(e -> trySend(e, built));
    }

    private void trySend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendRaw(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void removeEventEmitter(String eventId, SseEmitter emitter) {
        List<SseEmitter> list = eventEmitters.get(eventId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) eventEmitters.remove(eventId);
        }
    }
}
