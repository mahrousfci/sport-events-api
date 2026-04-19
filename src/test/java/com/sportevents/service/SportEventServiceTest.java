package com.sportevents.service;

import com.sportevents.dto.CreateEventRequest;
import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.exception.InvalidStatusTransitionException;
import com.sportevents.exception.SportEventNotFoundException;
import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import com.sportevents.publisher.EventPublisher;
import com.sportevents.repository.SportEventRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SportEventService — Unit Tests")
class SportEventServiceTest {

    private SportEventRepository repository;
    private EventPublisher publisher;
    private SportEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(SportEventRepository.class);
        publisher  = mock(EventPublisher.class);
        service    = new SportEventService(repository, List.of(publisher));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SportEvent futureEvent() {
        return new SportEvent("Match", "football", LocalDateTime.now().plusHours(2));
    }

    private SportEvent pastEvent() {
        return new SportEvent("Old Match", "hockey", LocalDateTime.now().minusHours(1));
    }

    private CreateEventRequest futureRequest(String sport) {
        CreateEventRequest r = new CreateEventRequest();
        r.setName("Match"); r.setSport(sport);
        r.setStartTime(LocalDateTime.now().plusHours(2));
        return r;
    }

    // ── createEvent ─────────────────────────────────────────────────────────

    @Nested @DisplayName("createEvent()")
    class Create {

        @Test @DisplayName("saves and returns INACTIVE event")
        void savesAndReturnsInactiveEvent() {
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            SportEventResponse res = service.createEvent(futureRequest("football"));
            assertThat(res.getStatus()).isEqualTo(SportEventStatus.INACTIVE);
            assertThat(res.getId()).isNotBlank();
            verify(repository).save(any());
        }

        @Test @DisplayName("publishCreated is called on all publishers")
        void broadcastsCreated() {
            when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
            service.createEvent(futureRequest("football"));
            verify(publisher).publishCreated(any());
            verify(publisher, never()).publishUpdated(any());
        }
    }

    // ── updateStatus ─────────────────────────────────────────────────────────

    @Nested @DisplayName("updateStatus() — valid transitions")
    class ValidTransitions {

        @Test @DisplayName("INACTIVE → ACTIVE for future event")
        void inactiveToActive() {
            SportEvent e = futureEvent();
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            SportEventResponse res = service.updateStatus(e.getId(), SportEventStatus.ACTIVE);
            assertThat(res.getStatus()).isEqualTo(SportEventStatus.ACTIVE);
        }

        @Test @DisplayName("ACTIVE → FINISHED")
        void activeToFinished() {
            SportEvent e = futureEvent();
            e.setStatus(SportEventStatus.ACTIVE);
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            SportEventResponse res = service.updateStatus(e.getId(), SportEventStatus.FINISHED);
            assertThat(res.getStatus()).isEqualTo(SportEventStatus.FINISHED);
        }

        @Test @DisplayName("publishUpdated called, not publishCreated")
        void broadcastsUpdated() {
            SportEvent e = futureEvent();
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            service.updateStatus(e.getId(), SportEventStatus.ACTIVE);
            verify(publisher).publishUpdated(any());
            verify(publisher, never()).publishCreated(any());
        }
    }

    @Nested @DisplayName("updateStatus() — invalid transitions")
    class InvalidTransitions {

        @Test @DisplayName("INACTIVE → ACTIVE past event throws")
        void inactiveToActivePastEvent() {
            SportEvent e = pastEvent();
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            assertThatThrownBy(() -> service.updateStatus(e.getId(), SportEventStatus.ACTIVE))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("past");
        }

        @Test @DisplayName("INACTIVE → FINISHED throws")
        void inactiveToFinished() {
            SportEvent e = futureEvent();
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            assertThatThrownBy(() -> service.updateStatus(e.getId(), SportEventStatus.FINISHED))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test @DisplayName("ACTIVE → INACTIVE throws")
        void activeToInactive() {
            SportEvent e = futureEvent(); e.setStatus(SportEventStatus.ACTIVE);
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            assertThatThrownBy(() -> service.updateStatus(e.getId(), SportEventStatus.INACTIVE))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test @DisplayName("FINISHED → any throws")
        void finishedToAny() {
            SportEvent e = futureEvent(); e.setStatus(SportEventStatus.FINISHED);
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            for (SportEventStatus s : SportEventStatus.values()) {
                assertThatThrownBy(() -> service.updateStatus(e.getId(), s))
                        .isInstanceOf(InvalidStatusTransitionException.class);
            }
        }

        @ParameterizedTest(name = "same status [{0}] is rejected")
        @EnumSource(SportEventStatus.class)
        @DisplayName("same status transition is always rejected")
        void sameStatus(SportEventStatus status) {
            SportEvent e = futureEvent(); e.setStatus(status);
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            assertThatThrownBy(() -> service.updateStatus(e.getId(), status))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test @DisplayName("no broadcast on rejected transition")
        void noBroadcastOnRejection() {
            SportEvent e = futureEvent();
            when(repository.findById(e.getId())).thenReturn(java.util.Optional.of(e));
            assertThatThrownBy(() -> service.updateStatus(e.getId(), SportEventStatus.FINISHED));
            verifyNoInteractions(publisher);
        }

        @Test @DisplayName("unknown id throws SportEventNotFoundException")
        void unknownId() {
            when(repository.findById("ghost")).thenReturn(java.util.Optional.empty());
            assertThatThrownBy(() -> service.updateStatus("ghost", SportEventStatus.ACTIVE))
                    .isInstanceOf(SportEventNotFoundException.class);
        }
    }
}
