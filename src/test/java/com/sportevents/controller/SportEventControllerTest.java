package com.sportevents.controller;

import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.exception.InvalidStatusTransitionException;
import com.sportevents.exception.SportEventNotFoundException;
import com.sportevents.model.SportEvent;
import com.sportevents.model.SportEventStatus;
import com.sportevents.service.SportEventService;
import com.sportevents.sse.SseEmitterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SportEventController.class)
@DisplayName("SportEventController — MockMvc")
class SportEventControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  SportEventService service;
    @MockBean  SseEmitterRegistry sseRegistry;

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_VALUE  = "test-api-key";

    private SportEventResponse sample(SportEventStatus status) {
        SportEvent e = new SportEvent("Test", "football", LocalDateTime.now().plusHours(2));
        e.setStatus(status);
        return SportEventResponse.from(e);
    }

    // ── POST ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /api/events")
    class Create {

        @Test @DisplayName("201 with valid body and API key")
        @WithMockUser
        void created() throws Exception {
            when(service.createEvent(any())).thenReturn(sample(SportEventStatus.INACTIVE));
            mockMvc.perform(post("/api/events")
                            .header(API_KEY_HEADER, API_KEY_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Match\",\"sport\":\"football\",\"startTime\":\"2030-01-01T10:00:00\"}")
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test @DisplayName("400 when name missing")
        @WithMockUser
        void missingName() throws Exception {
            mockMvc.perform(post("/api/events")
                            .header(API_KEY_HEADER, API_KEY_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sport\":\"football\",\"startTime\":\"2030-01-01T10:00:00\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET list ────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /api/events")
    class List_ {

        @Test @DisplayName("200 with paged response")
        void paged() throws Exception {
            PagedResponse<SportEventResponse> paged = new PagedResponse<>(
                    java.util.List.of(sample(SportEventStatus.INACTIVE)), 0, 20, 1, 1);
            when(service.getAllEvents(null, null, 0, 20)).thenReturn(paged);

            mockMvc.perform(get("/api/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test @DisplayName("passes status and sport filters to service")
        void filtersPassedThrough() throws Exception {
            when(service.getAllEvents(eq(SportEventStatus.ACTIVE), eq("hockey"), anyInt(), anyInt()))
                    .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0));
            mockMvc.perform(get("/api/events?status=ACTIVE&sport=hockey"))
                    .andExpect(status().isOk());
            verify(service).getAllEvents(SportEventStatus.ACTIVE, "hockey", 0, 20);
        }
    }

    // ── GET by id ───────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /api/events/{id}")
    class GetById {

        @Test @DisplayName("200 when found")
        void found() throws Exception {
            SportEventResponse r = sample(SportEventStatus.INACTIVE);
            when(service.getEventById(r.getId())).thenReturn(r);
            mockMvc.perform(get("/api/events/{id}", r.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(r.getId()));
        }

        @Test @DisplayName("404 when not found")
        void notFound() throws Exception {
            String id = UUID.randomUUID().toString();
            when(service.getEventById(id)).thenThrow(new SportEventNotFoundException(id));
            mockMvc.perform(get("/api/events/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }

    // ── PATCH status ─────────────────────────────────────────────────────────

    @Nested @DisplayName("PATCH /api/events/{id}/status")
    class PatchStatus {

        @Test @DisplayName("200 on valid transition with API key")
        @WithMockUser
        void valid() throws Exception {
            SportEventResponse r = sample(SportEventStatus.ACTIVE);
            when(service.updateStatus(eq(r.getId()), eq(SportEventStatus.ACTIVE))).thenReturn(r);
            mockMvc.perform(patch("/api/events/{id}/status", r.getId())
                            .header(API_KEY_HEADER, API_KEY_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test @DisplayName("422 on invalid transition")
        @WithMockUser
        void invalidTransition() throws Exception {
            String id = UUID.randomUUID().toString();
            when(service.updateStatus(eq(id), any()))
                    .thenThrow(new InvalidStatusTransitionException("Cannot change FINISHED"));
            mockMvc.perform(patch("/api/events/{id}/status", id)
                            .header(API_KEY_HEADER, API_KEY_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}")
                            .with(csrf()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message", containsString("FINISHED")));
        }

        @Test @DisplayName("404 when event not found")
        @WithMockUser
        void notFound() throws Exception {
            String id = UUID.randomUUID().toString();
            when(service.updateStatus(eq(id), any())).thenThrow(new SportEventNotFoundException(id));
            mockMvc.perform(patch("/api/events/{id}/status", id)
                            .header(API_KEY_HEADER, API_KEY_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACTIVE\"}")
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
