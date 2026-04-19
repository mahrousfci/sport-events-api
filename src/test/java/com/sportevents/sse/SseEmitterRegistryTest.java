package com.sportevents.sse;

import com.sportevents.dto.SportEventResponse;
import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SseEmitterRegistry")
class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() { registry = new SseEmitterRegistry(); }

    private SportEventResponse sample() {
        SportEvent e = new SportEvent("Match", "football", LocalDateTime.now().plusHours(1));
        e.setStatus(SportEventStatus.ACTIVE);
        return SportEventResponse.from(e);
    }

    @Test @DisplayName("registerGlobal returns non-null emitter")
    void registerGlobal_returnsEmitter() {
        assertThat(registry.registerGlobal()).isNotNull();
    }

    @Test @DisplayName("registerForEvent returns non-null emitter")
    void registerForEvent_returnsEmitter() {
        assertThat(registry.registerForEvent("event-1")).isNotNull();
    }

    @Test @DisplayName("multiple registrations return independent emitters")
    void multipleRegistrations_areIndependent() {
        SseEmitter e1 = registry.registerGlobal();
        SseEmitter e2 = registry.registerGlobal();
        assertThat(e1).isNotSameAs(e2);
    }

    @Test @DisplayName("publishCreated does not throw with no subscribers")
    void publishCreated_noSubscribers_noException() {
        assertThatCode(() -> registry.publishCreated(sample())).doesNotThrowAnyException();
    }

    @Test @DisplayName("publishUpdated does not throw with no subscribers")
    void publishUpdated_noSubscribers_noException() {
        assertThatCode(() -> registry.publishUpdated(sample())).doesNotThrowAnyException();
    }

    @Test @DisplayName("publishCreated does not throw after emitter is completed")
    void publishCreated_completedEmitter_noException() {
        SseEmitter emitter = registry.registerGlobal();
        emitter.complete();
        assertThatCode(() -> registry.publishCreated(sample())).doesNotThrowAnyException();
    }

    @Test @DisplayName("publishUpdated does not throw after emitter is completed")
    void publishUpdated_completedEmitter_noException() {
        SseEmitter emitter = registry.registerGlobal();
        emitter.complete();
        assertThatCode(() -> registry.publishUpdated(sample())).doesNotThrowAnyException();
    }
}
