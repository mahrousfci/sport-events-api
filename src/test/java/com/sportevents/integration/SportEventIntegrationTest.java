package com.sportevents.integration;

import com.sportevents.dto.PagedResponse;
import com.sportevents.dto.SportEventResponse;
import com.sportevents.model.SportEventStatus;
import com.sportevents.repository.SportEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Sport Events API — Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SportEventIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired SportEventRepository repository;

    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void clean() { repository.deleteAll(); }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String base() { return "http://localhost:" + port + "/api/events"; }

    private String future() {
        return LocalDateTime.now().plusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    private String past() {
        return LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private HttpHeaders writeHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-API-Key", API_KEY);
        return h;
    }

    private HttpHeaders readHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<SportEventResponse> createEvent(String name, String sport, String startTime) {
        String body = String.format("{\"name\":\"%s\",\"sport\":\"%s\",\"startTime\":\"%s\"}",
                name, sport, startTime);
        return rest.postForEntity(base(), new HttpEntity<>(body, writeHeaders()), SportEventResponse.class);
    }

    private ResponseEntity<SportEventResponse> patchStatus(String id, String status) {
        return rest.exchange(base() + "/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"" + status + "\"}", writeHeaders()),
                SportEventResponse.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> errorBody(ResponseEntity<?> r) { return (Map<String, Object>) r.getBody(); }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /api/events")
    class Create {

        @Test @DisplayName("201 with INACTIVE status")
        void returns201() {
            ResponseEntity<SportEventResponse> r = createEvent("Final", "football", future());
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(r.getBody().getStatus()).isEqualTo(SportEventStatus.INACTIVE);
            assertThat(r.getBody().getId()).isNotBlank();
        }

        @Test @DisplayName("400 when name missing")
        void missingName() {
            HttpHeaders h = writeHeaders();
            String body = String.format("{\"sport\":\"hockey\",\"startTime\":\"%s\"}", future());
            ResponseEntity<Map> r = rest.postForEntity(base(), new HttpEntity<>(body, h), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test @DisplayName("401 when API key missing")
        void missingApiKey() {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            String body = String.format("{\"name\":\"A\",\"sport\":\"B\",\"startTime\":\"%s\"}", future());
            ResponseEntity<Map> r = rest.postForEntity(base(), new HttpEntity<>(body, h), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test @DisplayName("401 when API key is wrong")
        void wrongApiKey() {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("X-API-Key", "wrong-key");
            String body = String.format("{\"name\":\"A\",\"sport\":\"B\",\"startTime\":\"%s\"}", future());
            ResponseEntity<Map> r = rest.postForEntity(base(), new HttpEntity<>(body, h), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── LIST ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /api/events")
    class ListEvents {

        @Test @DisplayName("200 with paginated response")
        void paginatedResponse() {
            createEvent("A", "football", future());
            createEvent("B", "hockey",   future());

            ResponseEntity<Map> r = rest.exchange(base(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsKeys("content", "page", "size", "totalElements", "totalPages");
            assertThat((Integer) r.getBody().get("totalElements")).isEqualTo(2);
        }

        @Test @DisplayName("status filter works")
        void statusFilter() {
            createEvent("A", "football", future());
            String id2 = createEvent("B", "football", future()).getBody().getId();
            patchStatus(id2, "ACTIVE");

            ResponseEntity<Map> r = rest.exchange(base() + "?status=ACTIVE", HttpMethod.GET,
                    new HttpEntity<>(readHeaders()), Map.class);
            assertThat((Integer) r.getBody().get("totalElements")).isEqualTo(1);
        }

        @Test @DisplayName("sport filter is case-insensitive")
        void sportFilter() {
            createEvent("A", "rugby",    future());
            createEvent("B", "football", future());

            ResponseEntity<Map> r = rest.exchange(base() + "?sport=RUGBY", HttpMethod.GET,
                    new HttpEntity<>(readHeaders()), Map.class);
            assertThat((Integer) r.getBody().get("totalElements")).isEqualTo(1);
        }

        @Test @DisplayName("pagination page/size params work")
        void pagination() {
            for (int i = 0; i < 5; i++) createEvent("Match" + i, "football", future());

            ResponseEntity<Map> page0 = rest.exchange(base() + "?page=0&size=2", HttpMethod.GET,
                    new HttpEntity<>(readHeaders()), Map.class);
            assertThat((Integer) page0.getBody().get("totalElements")).isEqualTo(5);
            assertThat((Integer) page0.getBody().get("totalPages")).isEqualTo(3);
        }

        @Test @DisplayName("no API key needed for GET")
        void noAuthRequired() {
            createEvent("A", "football", future());
            ResponseEntity<Map> r = rest.exchange(base(), HttpMethod.GET,
                    new HttpEntity<>(readHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /api/events/{id}")
    class GetById {

        @Test @DisplayName("200 with correct body")
        void found() {
            SportEventResponse created = createEvent("Cup", "basketball", future()).getBody();
            ResponseEntity<SportEventResponse> r = rest.getForEntity(
                    base() + "/" + created.getId(), SportEventResponse.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody().getName()).isEqualTo("Cup");
        }

        @Test @DisplayName("404 for unknown id")
        void notFound() {
            ResponseEntity<Map> r = rest.getForEntity(base() + "/no-such-id", Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(errorBody(r).get("status")).isEqualTo(404);
        }
    }

    // ── STATUS TRANSITIONS ────────────────────────────────────────────────────

    @Nested @DisplayName("PATCH /api/events/{id}/status")
    class StatusTransitions {

        @Test @DisplayName("INACTIVE → ACTIVE succeeds for future event")
        void inactiveToActive() {
            String id = createEvent("M", "football", future()).getBody().getId();
            ResponseEntity<SportEventResponse> r = patchStatus(id, "ACTIVE");
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody().getStatus()).isEqualTo(SportEventStatus.ACTIVE);
        }

        @Test @DisplayName("ACTIVE → FINISHED succeeds")
        void activeToFinished() {
            String id = createEvent("M", "football", future()).getBody().getId();
            patchStatus(id, "ACTIVE");
            assertThat(patchStatus(id, "FINISHED").getBody().getStatus())
                    .isEqualTo(SportEventStatus.FINISHED);
        }

        @Test @DisplayName("INACTIVE → ACTIVE fails for past event → 422")
        void pastEventCannotActivate() {
            String id = createEvent("Old", "hockey", past()).getBody().getId();
            ResponseEntity<Map> r = rest.exchange(base() + "/" + id + "/status", HttpMethod.PATCH,
                    new HttpEntity<>("{\"status\":\"ACTIVE\"}", writeHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(r.getBody().get("message").toString()).containsIgnoringCase("past");
        }

        @Test @DisplayName("INACTIVE → FINISHED is forbidden → 422")
        void inactiveToFinished() {
            String id = createEvent("M", "tennis", future()).getBody().getId();
            ResponseEntity<Map> r = rest.exchange(base() + "/" + id + "/status", HttpMethod.PATCH,
                    new HttpEntity<>("{\"status\":\"FINISHED\"}", writeHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test @DisplayName("FINISHED → ACTIVE is forbidden → 422")
        void finishedIsTerminal() {
            String id = createEvent("M", "rugby", future()).getBody().getId();
            patchStatus(id, "ACTIVE");
            patchStatus(id, "FINISHED");
            ResponseEntity<Map> r = rest.exchange(base() + "/" + id + "/status", HttpMethod.PATCH,
                    new HttpEntity<>("{\"status\":\"ACTIVE\"}", writeHeaders()), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test @DisplayName("PATCH requires API key → 401 without it")
        void requiresApiKey() {
            String id = createEvent("M", "football", future()).getBody().getId();
            HttpHeaders noKey = new HttpHeaders();
            noKey.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> r = rest.exchange(base() + "/" + id + "/status", HttpMethod.PATCH,
                    new HttpEntity<>("{\"status\":\"ACTIVE\"}", noKey), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── ERROR BODY SHAPE ──────────────────────────────────────────────────────

    @Nested @DisplayName("Error response structure")
    class ErrorShape {

        @Test @DisplayName("error body contains timestamp, status, error, message")
        void errorBodyShape() {
            ResponseEntity<Map> r = rest.getForEntity(base() + "/unknown-id", Map.class);
            assertThat(r.getBody()).containsKeys("timestamp", "status", "error", "message");
        }
    }
}
