# Sport Events REST API

A production-ready Spring Boot REST API for managing sport events with:
- **JPA + H2** persistence with optimistic locking
- **Pagination** on list endpoints
- **API key authentication** for write operations
- **SSE** real-time push for `EVENT_CREATED` and `EVENT_UPDATE`
- **Swagger UI** with full OpenAPI docs
- **Docker** multi-stage build + Compose

---

## Tech Stack

| Layer        | Choice                                        |
|--------------|-----------------------------------------------|
| Framework    | Spring Boot 3.2 (Java 17)                     |
| Persistence  | Spring Data JPA + H2 (in-memory, swappable)   |
| Concurrency  | JPA `@Version` optimistic locking             |
| Security     | Spring Security + `X-API-Key` header filter   |
| Real-time    | SSE (`text/event-stream`)                     |
| API Docs     | SpringDoc OpenAPI 2 / Swagger UI              |
| Build        | Maven wrapper                                 |
| Container    | Docker multi-stage + Docker Compose           |

---

## Running

### Maven (local)
```bash
./mvnw spring-boot:run
```

### Docker Compose
```bash
# default dev API key
docker compose up --build

# custom API key
APP_SECURITY_API_KEY=my-secret docker compose up --build
```

### Run tests
```bash
./mvnw test
```

---

## Authentication

Write operations (`POST`, `PATCH`) require the `X-API-Key` header.

| Environment  | Key value                                  |
|--------------|--------------------------------------------|
| Local dev    | `dev-api-key-change-in-production`         |
| Tests        | `test-api-key`                             |
| Production   | Set `APP_SECURITY_API_KEY` env var         |

Read operations (`GET`) and SSE subscriptions are public.

---

## Swagger UI

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Interactive UI (click 🔒 to add API key) |
| http://localhost:8080/api-docs        | Raw OpenAPI JSON                          |
| http://localhost:8080/h2-console      | H2 database console (dev only)            |

---

## REST API Reference

### POST /api/events — Create event
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-in-production" \
  -d '{"name":"Champions League Final","sport":"football","startTime":"2030-06-01T20:00:00"}'
```

### GET /api/events — List events (paginated)
```bash
curl "http://localhost:8080/api/events"
curl "http://localhost:8080/api/events?status=ACTIVE&sport=football&page=0&size=20"
```

Response shape:
```json
{
  "content": [ { ...event... } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### GET /api/events/{id} — Get by ID
```bash
curl http://localhost:8080/api/events/{id}
```

### PATCH /api/events/{id}/status — Change status
```bash
curl -X PATCH http://localhost:8080/api/events/{id}/status \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-api-key-change-in-production" \
  -d '{"status":"ACTIVE"}'
```

#### Status transition rules

| From       | To         | Allowed? | Condition                          |
|------------|------------|----------|------------------------------------|
| `INACTIVE` | `ACTIVE`   | ✅        | `startTime` must be in the future  |
| `ACTIVE`   | `FINISHED` | ✅        | —                                  |
| `INACTIVE` | `FINISHED` | ❌        | —                                  |
| `ACTIVE`   | `INACTIVE` | ❌        | —                                  |
| `FINISHED` | *any*      | ❌        | Terminal state                     |

Concurrent transitions are protected by JPA `@Version` — a 422 is returned if two requests race.

---

## Real-time: SSE

```bash
curl -N http://localhost:8080/api/events/subscribe          # all events
curl -N http://localhost:8080/api/events/{id}/subscribe     # one event
```

Browser (`EventSource`):
```js
const es = new EventSource('/api/events/subscribe');
es.addEventListener('event-created', e => console.log('New event:', JSON.parse(e.data)));
es.addEventListener('event-update',  e => console.log('Updated:',   JSON.parse(e.data)));
```

SSE event names pushed by the server:
- `event-created` — a new event was created
- `event-update`  — an event's status changed
- `connected`     — sent once on subscription to confirm the stream is open

---

## Project Structure

```
src/main/java/com/sportevents/
├── config/
│   ├── OpenApiConfig.java           # Swagger bean + ApiKeyAuth scheme
│   └── SecurityConfig.java          # Spring Security + X-API-Key filter
├── controller/
│   └── SportEventController.java    # REST + SSE endpoints
├── dto/
│   ├── CreateEventRequest.java
│   ├── UpdateStatusRequest.java
│   ├── SportEventResponse.java
│   ├── PagedResponse.java           # Generic pagination wrapper
│   └── ErrorResponse.java           # OpenAPI error schema
├── exception/
│   ├── SportEventNotFoundException.java
│   ├── InvalidStatusTransitionException.java
│   └── GlobalExceptionHandler.java
├── model/
│   ├── SportEvent.java              # JPA entity with @Version
│   └── SportEventStatus.java
├── publisher/
│   └── EventPublisher.java          # Interface: publishCreated + publishUpdated
├── repository/
│   └── SportEventRepository.java    # Spring Data JPA with filtered+paged query
├── service/
│   └── SportEventService.java       # Business logic, List<EventPublisher>
└── sse/
    └── SseEmitterRegistry.java      # EventPublisher impl for SSE

src/test/
├── resources/application.properties  # Test DB + fixed API key
└── java/com/sportevents/
    ├── service/SportEventServiceTest.java          # Unit — mocked repo
    ├── repository/SportEventRepositoryTest.java    # @DataJpaTest
    ├── controller/SportEventControllerTest.java    # MockMvc + security
    ├── sse/SseEmitterRegistryTest.java
    └── integration/
        └── SportEventIntegrationTest.java          # Full HTTP, repo cleanup
```

---

## Error Responses

```json
{
  "timestamp": "2030-06-01T20:00:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Cannot activate an event whose start time is in the past."
}
```

| Status | Scenario |
|--------|----------|
| 400    | Validation error |
| 401    | Missing/wrong API key |
| 404    | Event not found |
| 422    | Invalid transition or concurrent modification |
| 500    | Unexpected error |
