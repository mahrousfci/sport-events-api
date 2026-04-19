package com.sportevents.controller;

import com.sportevents.dto.CreateEventRequest;
import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.dto.UpdateStatusRequest;
import com.sportevents.exception.GlobalExceptionHandler;
import com.sportevents.exception.InvalidStatusTransitionException;
import com.sportevents.exception.SportEventNotFoundException;
import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import com.sportevents.publisher.EventPublisher;
import com.sportevents.repository.SportEventRepository;
import com.sportevents.service.SportEventService;
import com.sportevents.sse.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SportEventController — MockMvc")
class SportEventControllerTest {

    private MockMvc mockMvc;
    private FakeSportEventService service;

    @BeforeEach
    void setUp() {
        service = new FakeSportEventService();
        SportEventController controller = new SportEventController(service, new SseEmitterRegistry());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private SportEventResponse sample(SportEventStatus status) {
        SportEvent e = new SportEvent("Test", "football", LocalDateTime.now().plusHours(2));
        e.setStatus(status);
        return SportEventResponse.from(e);
    }

    @Nested
    @DisplayName("POST /api/events")
    class Create {

        @Test
        @DisplayName("201 with valid body")
        void created() throws Exception {
            service.createEventResult = sample(SportEventStatus.INACTIVE);

            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Match\",\"sport\":\"football\",\"startTime\":\"2030-01-01T10:00:00\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test
        @DisplayName("400 when name missing")
        void missingName() throws Exception {
            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sport\":\"football\",\"startTime\":\"2030-01-01T10:00:00\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/events")
    class List_ {

        @Test
        @DisplayName("200 with paged response")
        void paged() throws Exception {
            service.getAllEventsResult = new PagedResponse<>(
                    List.of(sample(SportEventStatus.INACTIVE)), 0, 20, 1, 1);

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("passes status and sport filters to service")
        void filtersPassedThrough() throws Exception {
            service.getAllEventsResult = new PagedResponse<>(List.of(), 0, 20, 0, 0);

            mockMvc.perform(get("/api/events?status=ACTIVE&sport=hockey"))
                    .andExpect(status().isOk());

            org.assertj.core.api.Assertions.assertThat(service.lastStatus).isEqualTo(SportEventStatus.ACTIVE);
            org.assertj.core.api.Assertions.assertThat(service.lastSport).isEqualTo("hockey");
            org.assertj.core.api.Assertions.assertThat(service.lastPage).isEqualTo(0);
            org.assertj.core.api.Assertions.assertThat(service.lastSize).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("GET /api/events/{id}")
    class GetById {

        @Test
        @DisplayName("200 when found")
        void found() throws Exception {
            SportEventResponse r = sample(SportEventStatus.INACTIVE);
            service.getEventByIdHandler = id -> r;

            mockMvc.perform(get("/api/events/{id}", r.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(r.getId()));
        }

        @Test
        @DisplayName("404 when not found")
        void notFound() throws Exception {
            String id = UUID.randomUUID().toString();
            service.getEventByIdHandler = requestedId -> {
                throw new SportEventNotFoundException(requestedId);
            };

            mockMvc.perform(get("/api/events/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/events/{id}/status")
    class PatchStatus {

        @Test
        @DisplayName("200 on valid transition")
        void valid() throws Exception {
            SportEventResponse r = sample(SportEventStatus.ACTIVE);
            service.updateStatusHandler = (id, status) -> r;

            mockMvc.perform(patch("/api/events/{id}/status", r.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("422 on invalid transition")
        void invalidTransition() throws Exception {
            String id = UUID.randomUUID().toString();
            service.updateStatusHandler = (requestedId, status) -> {
                throw new InvalidStatusTransitionException("Cannot change FINISHED");
            };

            mockMvc.perform(patch("/api/events/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message", containsString("FINISHED")));
        }

        @Test
        @DisplayName("404 when event not found")
        void notFound() throws Exception {
            String id = UUID.randomUUID().toString();
            service.updateStatusHandler = (requestedId, status) -> {
                throw new SportEventNotFoundException(requestedId);
            };

            mockMvc.perform(patch("/api/events/{id}/status", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    private static class FakeSportEventService extends SportEventService {
        private SportEventResponse createEventResult;
        private PagedResponse<SportEventResponse> getAllEventsResult;
        private Function<String, SportEventResponse> getEventByIdHandler = id -> {
            throw new UnsupportedOperationException("getEventByIdHandler not configured");
        };
        private BiFunction<String, SportEventStatus, SportEventResponse> updateStatusHandler = (id, status) -> {
            throw new UnsupportedOperationException("updateStatusHandler not configured");
        };

        private SportEventStatus lastStatus;
        private String lastSport;
        private Integer lastPage;
        private Integer lastSize;

        FakeSportEventService() {
            super(mock(SportEventRepository.class), List.<EventPublisher>of());
        }

        @Override
        public SportEventResponse createEvent(CreateEventRequest request) {
            return createEventResult;
        }

        @Override
        public PagedResponse<SportEventResponse> getAllEvents(
                SportEventStatus status, String sport, int page, int size) {
            lastStatus = status;
            lastSport = sport;
            lastPage = page;
            lastSize = size;
            return getAllEventsResult;
        }

        @Override
        public SportEventResponse getEventById(String id) {
            return getEventByIdHandler.apply(id);
        }

        @Override
        public SportEventResponse updateStatus(String id, SportEventStatus newStatus) {
            return updateStatusHandler.apply(id, newStatus);
        }
    }
}
