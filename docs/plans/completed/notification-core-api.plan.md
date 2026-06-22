---
linear_issue: null
---
# Plan: Notification Core API

> **For agentic workers:** `/prp-implement` routes this plan by `Metadata.Type` (feature → `implementing-features`). Steps use checkbox (`- [ ]`) syntax. Mode C = TDD bite-sized: each task writes a failing test, implements minimally, verifies green, commits.

## Summary
Build the entire `notification` module from the Spring Boot skeleton: 5 REST endpoints backed by
MySQL (JdbcTemplate), RocketMQ (native client producer), and Redis (cache-aside: by-id + recent-10).
Greenfield within the project — no existing app code to mirror; this plan establishes the conventions.

## User Story
As an API client,
I want to create, read, update, and delete notifications via REST,
So that notifications are persisted, published to a message queue, and served quickly from cache.

## Problem → Solution
Empty Spring Boot skeleton (only `DemoApplication`) → working notification service satisfying all 9 acceptance criteria in `docs/srs/notification-core-api.srs.md`.

## Metadata
- **Module**: notification
- **Parent Plan**: N/A
- **Source PRD**: N/A (derived from README brief)
- **Source Feature SRS**: `docs/srs/notification-core-api.srs.md`
- **Source Module Spec**: `docs/spec/notification.spec.md`
- **Source Linear Issue**: N/A
- **Type**: feature
- **Size**: L
- **Complexity**: Large
- **Rigor**: balanced
- **Mode**: C — 步步為營
- **TDD**: on
- **Commit cadence**: per-task
- **Estimated Files**: ~18 (15 main + 3 test)

---

## UX Design

### Before / After
N/A — internal backend API, no user-facing UI.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `POST /notifications` | 404 (no controller) | 201 + id | create flow |
| `GET /notifications/{id}` | 404 | 200 / 404 | cache-aside |
| `GET /notifications/recent` | 404 | 200 (≤10) | recent list |
| `PUT /notifications/{id}` | 404 | 200 / 404 | update |
| `DELETE /notifications/{id}` | 404 | 204 / 404 | delete |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/srs/notification-core-api.srs.md` | all | Functional reqs + 9 acceptance criteria (test source of truth) |
| P0 | `docs/spec/notification.spec.md` | all | Architecture, component table, data flow, caching strategy |
| P0 | `README.md` | 36-124 | Endpoint behaviors + request/response shapes |
| P1 | `src/main/resources/application.yaml` | all | Connection + cache config keys to inject (`rocketmq.*`, `notification.cache.*`) |
| P1 | `init.sql` | all | `notification` table schema (column names/types for row mapper) |
| P1 | `pom.xml` | 21-45 | Available deps (web, jdbc, redis, rocketmq-client, mysql driver) |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| RocketMQ native producer 5.x | `org.apache.rocketmq:rocketmq-client:5.3.2` | Use `DefaultMQProducer(group)` + `setNamesrvAddr` + `start()`; `Message(topic, byte[])`; call `shutdown()` on bean destroy |
| Spring Data Redis 3.x | `spring-boot-starter-data-redis` | Config key is `spring.data.redis.*` (not `spring.redis.*`); use `RedisTemplate` with `GenericJackson2JsonRedisSerializer` |
| Jackson + java.time | `jackson-datatype-jsr310` (transitive via starter) | Register `JavaTimeModule`, disable `WRITE_DATES_AS_TIMESTAMPS` to emit ISO-8601 `...Z` for `Instant` |

---

## Patterns to Mirror

> Greenfield: no existing app code. These are the conventions THIS plan establishes — follow them across all tasks for consistency.

### NAMING_CONVENTION
```
package com.example.demo.{layer}   // layers: domain, dto, repository, cache, messaging, config, service, controller, exception
class  PascalCase                  // NotificationService, NotificationController
method camelCase                   // findById, pushRecent
const  UPPER_SNAKE                 // KEY_BY_ID, KEY_RECENT
```

### ERROR_HANDLING
```java
// Domain-level: throw a typed exception; @RestControllerAdvice maps to HTTP status.
// SOURCE: established here — com.example.demo.exception.NotFoundException
throw new NotFoundException("notification " + id + " not found");
```

### LOGGING_PATTERN
```java
// SLF4J via Lombok-free explicit logger (no Lombok dep in pom).
private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
log.warn("MQ publish failed for notification id={}, continuing (DB is source of truth)", id, e);
```

### REPOSITORY_PATTERN
```java
// JdbcTemplate + RowMapper; KeyHolder for generated id. SOURCE: established here.
private final RowMapper<Notification> mapper = (rs, n) -> { ... };
```

### SERVICE_PATTERN
```java
// Constructor injection; service orchestrates repo + cache + producer; owns the cache-aside logic.
public NotificationService(NotificationRepository repo, NotificationCacheService cache, NotificationProducer producer) { ... }
```

### TEST_STRUCTURE
```java
// Service: pure Mockito unit test (no Spring context). Controller: @WebMvcTest + MockMvc + @MockBean service.
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest { @Mock NotificationRepository repo; ... }
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `src/main/java/com/example/demo/domain/Notification.java` | CREATE | Domain model |
| `src/main/java/com/example/demo/domain/NotificationType.java` | CREATE | email/sms enum + parsing |
| `src/main/java/com/example/demo/dto/CreateNotificationRequest.java` | CREATE | POST body |
| `src/main/java/com/example/demo/dto/UpdateNotificationRequest.java` | CREATE | PUT body |
| `src/main/java/com/example/demo/dto/NotificationResponse.java` | CREATE | API response |
| `src/main/java/com/example/demo/dto/NotificationMessage.java` | CREATE | RocketMQ payload |
| `src/main/java/com/example/demo/exception/NotFoundException.java` | CREATE | 404 signal |
| `src/main/java/com/example/demo/exception/GlobalExceptionHandler.java` | CREATE | 404/400 mapping |
| `src/main/java/com/example/demo/repository/NotificationRepository.java` | CREATE | JdbcTemplate DAO |
| `src/main/java/com/example/demo/config/RedisConfig.java` | CREATE | RedisTemplate + JSON serializer |
| `src/main/java/com/example/demo/cache/NotificationCacheService.java` | CREATE | by-id + recent cache |
| `src/main/java/com/example/demo/config/RocketMQConfig.java` | CREATE | Producer bean lifecycle |
| `src/main/java/com/example/demo/messaging/NotificationProducer.java` | CREATE | publish to topic |
| `src/main/java/com/example/demo/service/NotificationService.java` | CREATE | orchestration |
| `src/main/java/com/example/demo/controller/NotificationController.java` | CREATE | REST endpoints |
| `src/test/java/com/example/demo/service/NotificationServiceTest.java` | CREATE | service unit tests (AC-3,6,8) |
| `src/test/java/com/example/demo/controller/NotificationControllerTest.java` | CREATE | web-layer tests (AC-1,2,4,5,7,9) |
| `pom.xml` | UPDATE | add `spring-boot-starter-validation` for `@Valid` |

## NOT Building

- Authentication / authorization
- Actual email/SMS delivery or a `notification-topic` consumer
- Pagination/filtering beyond fixed recent-10
- Testcontainers / repository integration tests against real MySQL (unit + web-layer only; repo verified manually via running stack)

---

## Senior Engineering Hardening — Failure-Mode Matrix

> This is an interview project: the system must stay correct and available under partial failure.
> Every dependency below has an explicit degradation policy. **DB is the only hard dependency.**

| Scenario | Naïve behavior | Hardened behavior (this design) | Where |
|---|---|---|---|
| **Redis down** on GET/{id} | 500 (Redis exception bubbles) | Treated as cache-miss → read MySQL → return 200; warn-logged | `NotificationCacheService` safe wrappers |
| **Redis down** on create/update/delete | 500 | Cache write is a no-op; DB still mutated; success returned | safe wrappers |
| **Redis down** on /recent | 500 | `getRecent` returns empty → rebuild from MySQL `findRecent(10)` | `recent()` |
| **RocketMQ broker down** at boot | App won't start | `producer.start()` failure is logged, app boots | `RocketMQConfig` |
| **RocketMQ broker down** on create | Request hangs / 500 | Send capped at 3s, failure logged, create still 201 (DB committed) | `setSendMsgTimeout` + producer log-and-continue |
| **MySQL down** | 500 (correct) | 500 — DB is the source of truth and a hard dependency; surfaced cleanly, not swallowed | — |
| **Stale recent after update** | `/recent` shows old subject | update `evictRecent()` → rebuilt from DB on next read | `update()` |
| **Stale recent after delete** | deleted row lingers in `/recent` | delete `evictRecent()` → rebuilt from DB | `delete()` |
| **Stale by-id after update** | GET returns old value | update re-`putById(fresh)` | `update()` |
| **Recent list exceeds 10 under concurrent writes** | grows unbounded | `LPUSH` + `LTRIM 0..9` after every push | `pushRecent()` |
| **Invalid type / over-long subject / wrong recipient format** | 500 from DB truncation, or bad data persisted | 400 fail-fast (Bean Validation + enum parse + per-type recipient regex) | DTO `@Size`, `NotificationType.fromValue`, `validateRecipient` |
| **Unknown id on GET/PUT/DELETE** | inconsistent / 500 | `NotFoundException` → 404 via `@RestControllerAdvice` | service + handler |
| **Malformed JSON body** | 500 | Spring maps `HttpMessageNotReadableException`; add handler → 400 | `GlobalExceptionHandler` (see note) |
| **SQL injection via inputs** | vulnerable | All queries parameterized via `JdbcTemplate` `?` placeholders | repository |

**Consistency model (documented trade-off):** MySQL is the system of record; Redis is a best-effort
accelerator (cache-aside) and RocketMQ is best-effort fire-and-forget. We accept *eventual* cache
consistency (bounded by TTL + explicit invalidation) and at-most-once MQ delivery for this scope. The
"correct" upgrade for guaranteed delivery is the **transactional outbox pattern** (write event to an
`outbox` table in the same TX, relay async) — called out as out-of-scope but worth mentioning in the interview.

**Add a malformed-body handler** to `GlobalExceptionHandler` (Task 4) so bad JSON returns 400 not 500:
```java
@ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
public ResponseEntity<Map<String, String>> handleUnreadable(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("error", "INVALID_INPUT", "message", "malformed request body"));
}
```

**Operational hardening (add `spring-boot-starter-actuator` if time allows):** expose
`/actuator/health` with Redis/DB health indicators so orchestrators can readiness-gate the pod.
Listed as a bonus task, not required for the 9 ACs.

---

## Step-by-Step Tasks (Mode C — TDD bite-sized)

> Run all tests with `./mvnw test` (or scoped `./mvnw -Dtest=ClassName test`). Commit once per task (cadence = per-task). Where a component is pure data/config with no meaningful behavior to assert (enum aside), the "test" step verifies compilation/wiring.

### Task 0: Add validation starter

- [ ] **Step 1: Add dependency to `pom.xml`** (after `spring-boot-starter-web`)
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  ```
- [ ] **Step 2: Verify** — Run `./mvnw -o -q compile` → expect BUILD SUCCESS.
- [ ] **Step 3: Commit** — `git add pom.xml && git commit -m "build: add validation starter"`

---

### Task 1: NotificationType enum

**Files:** Create `src/main/java/com/example/demo/domain/NotificationType.java`, Test `src/test/java/com/example/demo/domain/NotificationTypeTest.java`

- [ ] **Step 1: Write the failing test**
  ```java
  package com.example.demo.domain;

  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;

  class NotificationTypeTest {
      @Test void parsesCaseInsensitively() {
          assertEquals(NotificationType.EMAIL, NotificationType.fromValue("email"));
          assertEquals(NotificationType.SMS, NotificationType.fromValue("SMS"));
      }
      @Test void rejectsUnknown() {
          assertThrows(IllegalArgumentException.class, () -> NotificationType.fromValue("fax"));
      }
  }
  ```
- [ ] **Step 2: Run** `./mvnw -Dtest=NotificationTypeTest test` → expect FAIL (class missing).
- [ ] **Step 3: Implement**
  ```java
  package com.example.demo.domain;

  import com.fasterxml.jackson.annotation.JsonCreator;
  import com.fasterxml.jackson.annotation.JsonValue;

  public enum NotificationType {
      EMAIL("email"), SMS("sms");

      private final String value;
      NotificationType(String value) { this.value = value; }

      @JsonValue public String getValue() { return value; }

      @JsonCreator
      public static NotificationType fromValue(String raw) {
          if (raw == null) throw new IllegalArgumentException("type is required");
          for (NotificationType t : values()) {
              if (t.value.equalsIgnoreCase(raw.trim())) return t;
          }
          throw new IllegalArgumentException("unknown notification type: " + raw);
      }
  }
  ```
- [ ] **Step 4: Run** test → expect PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(notification): add NotificationType enum"`

---

### Task 2: Notification domain model

**Files:** Create `src/main/java/com/example/demo/domain/Notification.java`

- [ ] **Step 1: Implement** (plain mutable POJO; `createdAt`/`updatedAt` are `Instant` for ISO-8601 `...Z` output)
  ```java
  package com.example.demo.domain;

  import java.time.Instant;

  public class Notification {
      private Long id;
      private NotificationType type;
      private String recipient;
      private String subject;
      private String content;
      private Instant createdAt;
      private Instant updatedAt;

      public Notification() {}

      public Long getId() { return id; }
      public void setId(Long id) { this.id = id; }
      public NotificationType getType() { return type; }
      public void setType(NotificationType type) { this.type = type; }
      public String getRecipient() { return recipient; }
      public void setRecipient(String recipient) { this.recipient = recipient; }
      public String getSubject() { return subject; }
      public void setSubject(String subject) { this.subject = subject; }
      public String getContent() { return content; }
      public void setContent(String content) { this.content = content; }
      public Instant getCreatedAt() { return createdAt; }
      public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
      public Instant getUpdatedAt() { return updatedAt; }
      public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  }
  ```
- [ ] **Step 2: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS.
- [ ] **Step 3: Commit** — `git commit -am "feat(notification): add Notification domain model"`

---

### Task 3: DTOs (request/response/message)

**Files:** Create the 4 DTOs under `src/main/java/com/example/demo/dto/`.

- [ ] **Step 1: Implement `CreateNotificationRequest`** (Bean Validation)
  ```java
  package com.example.demo.dto;

  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.Size;

  public class CreateNotificationRequest {
      @NotBlank(message = "type is required")
      private String type;                       // validated against enum in service
      @NotBlank(message = "recipient is required")
      @Size(max = 255, message = "recipient too long")
      private String recipient;                  // format checked per-type in service (email vs phone)
      @Size(max = 255, message = "subject too long")   // matches VARCHAR(255) — fail fast, not on DB error
      private String subject;
      private String content;                    // TEXT column — unbounded
      // getters + setters
      public String getType() { return type; }
      public void setType(String type) { this.type = type; }
      public String getRecipient() { return recipient; }
      public void setRecipient(String recipient) { this.recipient = recipient; }
      public String getSubject() { return subject; }
      public void setSubject(String subject) { this.subject = subject; }
      public String getContent() { return content; }
      public void setContent(String content) { this.content = content; }
  }
  ```
  > **Senior note:** column-length `@Size` constraints fail fast with a clean 400 instead of letting MySQL throw a truncation error (which would surface as a 500). Per-type recipient format (email regex vs E.164 phone) is validated in the service where the resolved `NotificationType` is known — see Task 8.
- [ ] **Step 2: Implement `UpdateNotificationRequest`**
  ```java
  package com.example.demo.dto;

  public class UpdateNotificationRequest {
      private String subject;
      private String content;
      public String getSubject() { return subject; }
      public void setSubject(String subject) { this.subject = subject; }
      public String getContent() { return content; }
      public void setContent(String content) { this.content = content; }
  }
  ```
- [ ] **Step 3: Implement `NotificationResponse`** (static `from(Notification)` mapper)
  ```java
  package com.example.demo.dto;

  import com.example.demo.domain.Notification;
  import com.example.demo.domain.NotificationType;
  import java.time.Instant;

  public class NotificationResponse {
      public Long id;
      public NotificationType type;
      public String recipient;
      public String subject;
      public String content;
      public Instant createdAt;
      public Instant updatedAt;

      public static NotificationResponse from(Notification n) {
          NotificationResponse r = new NotificationResponse();
          r.id = n.getId(); r.type = n.getType(); r.recipient = n.getRecipient();
          r.subject = n.getSubject(); r.content = n.getContent();
          r.createdAt = n.getCreatedAt(); r.updatedAt = n.getUpdatedAt();
          return r;
      }
  }
  ```
- [ ] **Step 4: Implement `NotificationMessage`** (MQ payload — no content body needed downstream, but include for delivery)
  ```java
  package com.example.demo.dto;

  import com.example.demo.domain.Notification;

  public class NotificationMessage {
      public Long id;
      public String type;
      public String recipient;
      public String subject;
      public String content;

      public static NotificationMessage from(Notification n) {
          NotificationMessage m = new NotificationMessage();
          m.id = n.getId(); m.type = n.getType().getValue(); m.recipient = n.getRecipient();
          m.subject = n.getSubject(); m.content = n.getContent();
          return m;
      }
      public Long getId() { return id; }   // used by logging
  }
  ```
- [ ] **Step 5: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS.
- [ ] **Step 6: Commit** — `git commit -am "feat(notification): add request/response/message DTOs"`

---

### Task 4: NotFoundException + GlobalExceptionHandler

**Files:** Create both under `src/main/java/com/example/demo/exception/`.

- [ ] **Step 1: Implement `NotFoundException`**
  ```java
  package com.example.demo.exception;

  public class NotFoundException extends RuntimeException {
      public NotFoundException(String message) { super(message); }
  }
  ```
- [ ] **Step 2: Implement `GlobalExceptionHandler`** (404 + 400 mapping)
  ```java
  package com.example.demo.exception;

  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.MethodArgumentNotValidException;
  import org.springframework.web.bind.annotation.ExceptionHandler;
  import org.springframework.web.bind.annotation.RestControllerAdvice;
  import java.util.Map;

  @RestControllerAdvice
  public class GlobalExceptionHandler {

      @ExceptionHandler(NotFoundException.class)
      public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND)
                  .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
      }

      @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
      public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
          return ResponseEntity.badRequest()
                  .body(Map.of("error", "INVALID_INPUT", "message", String.valueOf(e.getMessage())));
      }

      @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
      public ResponseEntity<Map<String, String>> handleUnreadable(Exception e) {
          return ResponseEntity.badRequest()
                  .body(Map.of("error", "INVALID_INPUT", "message", "malformed request body"));
      }

      @ExceptionHandler(Exception.class)   // last-resort: never leak stack traces; uniform 500 shape
      public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                  .body(Map.of("error", "INTERNAL_ERROR", "message", "unexpected error"));
      }
  }
  ```
  > **Senior note:** the catch-all `Exception` handler ensures unexpected errors return a uniform JSON 500 (no stack trace leakage to clients) while the warn/error is logged server-side. Order is fine — Spring picks the most specific matching handler first.
- [ ] **Step 3: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS.
- [ ] **Step 4: Commit** — `git commit -am "feat(notification): add exception handling (404/400)"`

---

### Task 5: NotificationRepository (JdbcTemplate)

**Files:** Create `src/main/java/com/example/demo/repository/NotificationRepository.java`.

- [ ] **Step 1: Implement** (insert with generated key, find, recent, update, delete, exists)
  ```java
  package com.example.demo.repository;

  import com.example.demo.domain.Notification;
  import com.example.demo.domain.NotificationType;
  import org.springframework.jdbc.core.JdbcTemplate;
  import org.springframework.jdbc.core.RowMapper;
  import org.springframework.jdbc.support.GeneratedKeyHolder;
  import org.springframework.jdbc.support.KeyHolder;
  import org.springframework.stereotype.Repository;

  import java.sql.Statement;
  import java.sql.Timestamp;
  import java.time.Instant;
  import java.util.List;
  import java.util.Optional;

  @Repository
  public class NotificationRepository {

      private final JdbcTemplate jdbc;
      public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

      private final RowMapper<Notification> mapper = (rs, n) -> {
          Notification x = new Notification();
          x.setId(rs.getLong("id"));
          x.setType(NotificationType.fromValue(rs.getString("type")));
          x.setRecipient(rs.getString("recipient"));
          x.setSubject(rs.getString("subject"));
          x.setContent(rs.getString("content"));
          Timestamp c = rs.getTimestamp("created_at");
          Timestamp u = rs.getTimestamp("updated_at");
          x.setCreatedAt(c == null ? null : c.toInstant());
          x.setUpdatedAt(u == null ? null : u.toInstant());
          return x;
      };

      public Notification insert(Notification n) {
          KeyHolder kh = new GeneratedKeyHolder();
          Instant now = Instant.now();
          jdbc.update(con -> {
              var ps = con.prepareStatement(
                  "INSERT INTO notification(type, recipient, subject, content, created_at, updated_at) VALUES (?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
              ps.setString(1, n.getType().getValue());
              ps.setString(2, n.getRecipient());
              ps.setString(3, n.getSubject());
              ps.setString(4, n.getContent());
              ps.setTimestamp(5, Timestamp.from(now));
              ps.setTimestamp(6, Timestamp.from(now));
              return ps;
          }, kh);
          n.setId(kh.getKey().longValue());
          n.setCreatedAt(now);
          n.setUpdatedAt(now);
          return n;
      }

      public Optional<Notification> findById(Long id) {
          return jdbc.query("SELECT * FROM notification WHERE id = ?", mapper, id).stream().findFirst();
      }

      public List<Notification> findRecent(int limit) {
          return jdbc.query("SELECT * FROM notification ORDER BY created_at DESC, id DESC LIMIT ?", mapper, limit);
      }

      public boolean existsById(Long id) {
          Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ?", Integer.class, id);
          return c != null && c > 0;
      }

      public int updateSubjectContent(Long id, String subject, String content) {
          return jdbc.update(
              "UPDATE notification SET subject = ?, content = ?, updated_at = ? WHERE id = ?",
              subject, content, Timestamp.from(Instant.now()), id);
      }

      public int deleteById(Long id) {
          return jdbc.update("DELETE FROM notification WHERE id = ?", id);
      }
  }
  ```
- [ ] **Step 2: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS. (No unit test — JdbcTemplate DAO is verified via the running stack in Validation; web-layer tests mock the repo.)
- [ ] **Step 3: Commit** — `git commit -am "feat(notification): add JdbcTemplate repository"`

---

### Task 6: RedisConfig + NotificationCacheService

**Files:** Create `config/RedisConfig.java` and `cache/NotificationCacheService.java`.

- [ ] **Step 1: Implement `RedisConfig`** (JSON serializer that handles `Instant`)
  ```java
  package com.example.demo.config;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.data.redis.connection.RedisConnectionFactory;
  import org.springframework.data.redis.core.RedisTemplate;
  import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
  import org.springframework.data.redis.serializer.StringRedisSerializer;

  import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

  @Configuration
  public class RedisConfig {
      @Bean
      public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
          ObjectMapper om = new ObjectMapper()
                  .registerModule(new JavaTimeModule())
                  .disable(WRITE_DATES_AS_TIMESTAMPS)
                  .activateDefaultTyping(com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance,
                          ObjectMapper.DefaultTyping.NON_FINAL);
          RedisTemplate<String, Object> t = new RedisTemplate<>();
          t.setConnectionFactory(cf);
          t.setKeySerializer(new StringRedisSerializer());
          t.setHashKeySerializer(new StringRedisSerializer());
          t.setValueSerializer(new GenericJackson2JsonRedisSerializer(om));
          t.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(om));
          t.afterPropertiesSet();
          return t;
      }
  }
  ```
- [ ] **Step 2: Implement `NotificationCacheService`** (by-id TTL; recent list; **every Redis op wrapped so an outage degrades to cache-miss, never throws to the caller** — senior robustness requirement)
  ```java
  package com.example.demo.cache;

  import com.example.demo.domain.Notification;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.data.redis.core.RedisTemplate;
  import org.springframework.stereotype.Service;

  import java.time.Duration;
  import java.util.List;
  import java.util.Optional;
  import java.util.function.Supplier;

  @Service
  public class NotificationCacheService {
      private static final Logger log = LoggerFactory.getLogger(NotificationCacheService.class);
      private static final String KEY_BY_ID = "notification:";
      private static final String KEY_RECENT = "notifications:recent";

      private final RedisTemplate<String, Object> redis;
      private final int recentSize;
      private final long ttlSeconds;

      public NotificationCacheService(RedisTemplate<String, Object> redis,
                                      @Value("${notification.cache.recent-size:10}") int recentSize,
                                      @Value("${notification.cache.ttl-seconds:3600}") long ttlSeconds) {
          this.redis = redis; this.recentSize = recentSize; this.ttlSeconds = ttlSeconds;
      }

      // --- resilience wrappers: Redis is an optional accelerator, never a hard dependency ---
      private void safeRun(String op, Runnable action) {
          try { action.run(); }
          catch (RuntimeException e) { log.warn("Redis {} failed; continuing without cache", op, e); }
      }
      private <T> T safeGet(String op, Supplier<T> action, T fallback) {
          try { return action.get(); }
          catch (RuntimeException e) { log.warn("Redis {} failed; treating as cache-miss", op, e); return fallback; }
      }

      public void putById(Notification n) {
          safeRun("putById", () -> redis.opsForValue().set(KEY_BY_ID + n.getId(), n, Duration.ofSeconds(ttlSeconds)));
      }

      public Optional<Notification> getById(Long id) {
          return safeGet("getById", () -> Optional.ofNullable((Notification) redis.opsForValue().get(KEY_BY_ID + id)), Optional.empty());
      }

      public void evict(Long id) {
          safeRun("evict", () -> redis.delete(KEY_BY_ID + id));
      }

      /** Push newest to front, trim to recentSize. */
      public void pushRecent(Notification n) {
          safeRun("pushRecent", () -> {
              redis.opsForList().leftPush(KEY_RECENT, n);
              redis.opsForList().trim(KEY_RECENT, 0, recentSize - 1);
          });
      }

      /** Invalidate the recent list so the next read rebuilds it from DB (used on update/delete). */
      public void evictRecent() {
          safeRun("evictRecent", () -> redis.delete(KEY_RECENT));
      }

      /** Replace the recent list wholesale (used to rebuild from DB). */
      public void replaceRecent(List<Notification> notifications) {
          safeRun("replaceRecent", () -> {
              redis.delete(KEY_RECENT);
              for (int i = notifications.size() - 1; i >= 0; i--) {   // oldest first so newest ends at head
                  redis.opsForList().leftPush(KEY_RECENT, notifications.get(i));
              }
              redis.opsForList().trim(KEY_RECENT, 0, recentSize - 1);
          });
      }

      @SuppressWarnings("unchecked")
      public List<Notification> getRecent() {
          return safeGet("getRecent",
              () -> { List<Object> raw = redis.opsForList().range(KEY_RECENT, 0, recentSize - 1);
                      return raw == null ? List.<Notification>of() : (List<Notification>) (List<?>) raw; },
              List.of());
      }
  }
  ```
  > **Why the wrappers:** with cache-aside, Redis is a performance optimization — a Redis outage must NOT turn `GET /notifications/{id}` into a 500. Each op falls back to "miss" (reads) or "no-op" (writes), and the service re-reads from MySQL. This is the single most important robustness property for an interview.
- [ ] **Step 3: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS.
- [ ] **Step 4: Commit** — `git commit -am "feat(notification): add Redis config + cache service"`

---

### Task 7: RocketMQConfig + NotificationProducer

**Files:** Create `config/RocketMQConfig.java` and `messaging/NotificationProducer.java`.

- [ ] **Step 1: Implement `RocketMQConfig`** (bean lifecycle; `destroyMethod = "shutdown"`; **startup-failure tolerant** — a briefly-unreachable namesrv must not block the whole app from booting; configurable retries + send timeout)
  ```java
  package com.example.demo.config;

  import org.apache.rocketmq.client.producer.DefaultMQProducer;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;

  @Configuration
  public class RocketMQConfig {
      private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

      @Bean(destroyMethod = "shutdown")   // NOTE: method name must NOT be notificationProducer —
      public DefaultMQProducer rocketMQProducer(   // that collides with the @Component NotificationProducer bean
              @Value("${rocketmq.name-server}") String nameServer,
              @Value("${rocketmq.producer.group}") String group,
              @Value("${rocketmq.producer.send-timeout-ms:3000}") int sendTimeout,
              @Value("${rocketmq.producer.retries:2}") int retries) {
          DefaultMQProducer producer = new DefaultMQProducer(group);
          producer.setNamesrvAddr(nameServer);
          producer.setSendMsgTimeout(sendTimeout);                 // bound latency — never hang a request thread
          producer.setRetryTimesWhenSendFailed(retries);
          try {
              producer.start();
          } catch (Exception e) {
              // Don't fail app startup on a transient broker outage; producer will (re)connect on first send.
              log.error("RocketMQ producer failed to start against {} — app continues; sends will be best-effort", nameServer, e);
          }
          return producer;
      }
  }
  ```
  > **Senior note:** `setSendMsgTimeout` caps how long a publish can block the request thread, so a slow/dead broker degrades the create latency by at most ~3s (then the producer's log-and-continue path in `NotificationProducer` kicks in) instead of hanging indefinitely. Combined with `start()` being non-fatal, MQ is a soft dependency — the core write (MySQL) always succeeds.
- [ ] **Step 2: Implement `NotificationProducer`** (log-and-continue on failure — decision from SRS)
  ```java
  package com.example.demo.messaging;

  import com.example.demo.dto.NotificationMessage;
  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.apache.rocketmq.client.producer.DefaultMQProducer;
  import org.apache.rocketmq.common.message.Message;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Component;

  @Component
  public class NotificationProducer {
      private static final Logger log = LoggerFactory.getLogger(NotificationProducer.class);

      private final DefaultMQProducer producer;
      private final ObjectMapper objectMapper;
      private final String topic;

      public NotificationProducer(DefaultMQProducer producer,
                                  ObjectMapper objectMapper,
                                  @Value("${rocketmq.topic}") String topic) {
          this.producer = producer; this.objectMapper = objectMapper; this.topic = topic;
      }

      public void send(NotificationMessage msg) {
          try {
              byte[] body = objectMapper.writeValueAsBytes(msg);
              producer.send(new Message(topic, body));
          } catch (Exception e) {
              // DB is source of truth; MQ publish is best-effort for this assignment scope.
              log.warn("MQ publish failed for notification id={}, continuing", msg.getId(), e);
          }
      }
  }
  ```
- [ ] **Step 3: Verify** — `./mvnw -o -q compile` → BUILD SUCCESS. (Spring Boot auto-provides an `ObjectMapper` bean.)
- [ ] **Step 4: Commit** — `git commit -am "feat(notification): add RocketMQ producer"`

---

### Task 8: NotificationService (orchestration) — TDD

**Files:** Create `service/NotificationService.java`, Test `src/test/java/com/example/demo/service/NotificationServiceTest.java`.

- [ ] **Step 1: Write the failing test** (covers AC-3 cache-miss backfill, AC-6 update refresh, AC-8 delete evict, plus 404s)
  ```java
  package com.example.demo.service;

  import com.example.demo.cache.NotificationCacheService;
  import com.example.demo.domain.Notification;
  import com.example.demo.domain.NotificationType;
  import com.example.demo.dto.CreateNotificationRequest;
  import com.example.demo.dto.UpdateNotificationRequest;
  import com.example.demo.exception.NotFoundException;
  import com.example.demo.messaging.NotificationProducer;
  import com.example.demo.repository.NotificationRepository;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.time.Instant;
  import java.util.Optional;

  import static org.junit.jupiter.api.Assertions.*;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class NotificationServiceTest {
      @Mock NotificationRepository repo;
      @Mock NotificationCacheService cache;
      @Mock NotificationProducer producer;
      @InjectMocks NotificationService service;

      private Notification sample(long id) {
          Notification n = new Notification();
          n.setId(id); n.setType(NotificationType.EMAIL); n.setRecipient("u@e.com");
          n.setSubject("s"); n.setContent("c"); n.setCreatedAt(Instant.now()); n.setUpdatedAt(Instant.now());
          return n;
      }

      @Test void create_persists_publishes_caches() {
          CreateNotificationRequest req = new CreateNotificationRequest();
          req.setType("email"); req.setRecipient("u@e.com"); req.setSubject("s"); req.setContent("c");
          when(repo.insert(any())).thenAnswer(inv -> { Notification n = inv.getArgument(0); n.setId(1L); return n; });

          Notification out = service.create(req);

          assertEquals(1L, out.getId());
          verify(repo).insert(any());
          verify(producer).send(any());
          verify(cache).pushRecent(any());
          verify(cache).putById(any());
      }

      @Test void create_invalidType_throws() {
          CreateNotificationRequest req = new CreateNotificationRequest();
          req.setType("fax"); req.setRecipient("u@e.com");
          assertThrows(IllegalArgumentException.class, () -> service.create(req));
      }

      @Test void create_emailTypeWithPhoneRecipient_throws() {   // per-type recipient validation
          CreateNotificationRequest req = new CreateNotificationRequest();
          req.setType("email"); req.setRecipient("+15551234567");
          assertThrows(IllegalArgumentException.class, () -> service.create(req));
      }

      @Test void create_smsTypeWithPhoneRecipient_ok() {
          CreateNotificationRequest req = new CreateNotificationRequest();
          req.setType("sms"); req.setRecipient("+15551234567"); req.setSubject("s"); req.setContent("c");
          when(repo.insert(any())).thenAnswer(inv -> { Notification n = inv.getArgument(0); n.setId(2L); return n; });
          assertEquals(2L, service.create(req).getId());
      }

      @Test void getById_cacheMiss_backfills() {                 // AC-3
          when(cache.getById(5L)).thenReturn(Optional.empty());
          when(repo.findById(5L)).thenReturn(Optional.of(sample(5L)));
          Notification out = service.getById(5L);
          assertEquals(5L, out.getId());
          verify(cache).putById(any());
      }

      @Test void getById_missing_throws404() {                   // AC-4
          when(cache.getById(9L)).thenReturn(Optional.empty());
          when(repo.findById(9L)).thenReturn(Optional.empty());
          assertThrows(NotFoundException.class, () -> service.getById(9L));
      }

      @Test void update_refreshesCacheAndInvalidatesRecent() {   // AC-6 (+ recent staleness fix)
          when(repo.existsById(5L)).thenReturn(true);
          when(repo.findById(5L)).thenReturn(Optional.of(sample(5L)));
          UpdateNotificationRequest req = new UpdateNotificationRequest();
          req.setSubject("s2"); req.setContent("c2");
          service.update(5L, req);
          verify(repo).updateSubjectContent(5L, "s2", "c2");
          verify(cache).putById(any());
          verify(cache).evictRecent();                           // recent list must not serve stale subject
      }

      @Test void update_missing_throws404() {                    // AC-7
          when(repo.existsById(9L)).thenReturn(false);
          assertThrows(NotFoundException.class, () -> service.update(9L, new UpdateNotificationRequest()));
      }

      @Test void delete_evictsByIdAndRecent() {                  // AC-8 (+ recent staleness fix)
          when(repo.existsById(5L)).thenReturn(true);
          service.delete(5L);
          verify(repo).deleteById(5L);
          verify(cache).evict(5L);
          verify(cache).evictRecent();                           // deleted row must not linger in recent
      }

      @Test void recent_cacheMiss_rebuildsFromDb() {             // AC-5 (rebuild path)
          when(cache.getRecent()).thenReturn(java.util.List.of());
          when(repo.findRecent(10)).thenReturn(java.util.List.of(sample(2L), sample(1L)));
          var out = service.recent();
          assertEquals(2, out.size());
          verify(cache).replaceRecent(anyList());
      }

      @Test void delete_missing_throws404() {                    // AC-9
          when(repo.existsById(9L)).thenReturn(false);
          assertThrows(NotFoundException.class, () -> service.delete(9L));
      }
  }
  ```
- [ ] **Step 2: Run** `./mvnw -Dtest=NotificationServiceTest test` → expect FAIL (NotificationService missing).
- [ ] **Step 3: Implement**
  ```java
  package com.example.demo.service;

  import com.example.demo.cache.NotificationCacheService;
  import com.example.demo.domain.Notification;
  import com.example.demo.domain.NotificationType;
  import com.example.demo.dto.CreateNotificationRequest;
  import com.example.demo.dto.NotificationMessage;
  import com.example.demo.dto.UpdateNotificationRequest;
  import com.example.demo.exception.NotFoundException;
  import com.example.demo.messaging.NotificationProducer;
  import com.example.demo.repository.NotificationRepository;
  import org.springframework.stereotype.Service;

  import java.util.List;

  @Service
  public class NotificationService {
      private final NotificationRepository repo;
      private final NotificationCacheService cache;
      private final NotificationProducer producer;

      public NotificationService(NotificationRepository repo, NotificationCacheService cache, NotificationProducer producer) {
          this.repo = repo; this.cache = cache; this.producer = producer;
      }

      private static final int RECENT_LIMIT = 10;
      private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
      private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^\\+?[0-9]{7,15}$");

      public Notification create(CreateNotificationRequest req) {
          NotificationType type = NotificationType.fromValue(req.getType());   // throws IllegalArgumentException → 400
          validateRecipient(type, req.getRecipient());
          Notification n = new Notification();
          n.setType(type);
          n.setRecipient(req.getRecipient());
          n.setSubject(req.getSubject());
          n.setContent(req.getContent());
          repo.insert(n);                                          // 1) DB is source of truth (committed first)
          cache.putById(n);                                       // 2) warm by-id cache
          cache.pushRecent(n);                                    // 3) prepend to recent list
          producer.send(NotificationMessage.from(n));            // 4) best-effort publish (log-and-continue)
          return n;
      }

      public Notification getById(Long id) {
          return cache.getById(id).orElseGet(() -> {
              Notification n = repo.findById(id)
                      .orElseThrow(() -> new NotFoundException("notification " + id + " not found"));
              cache.putById(n);
              return n;
          });
      }

      public List<Notification> recent() {
          List<Notification> cached = cache.getRecent();
          if (!cached.isEmpty()) return cached;
          List<Notification> fromDb = repo.findRecent(RECENT_LIMIT);   // rebuild on miss / cold start
          cache.replaceRecent(fromDb);
          return fromDb;
      }

      public Notification update(Long id, UpdateNotificationRequest req) {
          if (!repo.existsById(id)) throw new NotFoundException("notification " + id + " not found");
          repo.updateSubjectContent(id, req.getSubject(), req.getContent());
          Notification fresh = repo.findById(id)
                  .orElseThrow(() -> new NotFoundException("notification " + id + " not found"));
          cache.putById(fresh);        // refresh by-id
          cache.evictRecent();         // recent list may hold a stale copy → invalidate, rebuild on next read
          return fresh;
      }

      public void delete(Long id) {
          if (!repo.existsById(id)) throw new NotFoundException("notification " + id + " not found");
          repo.deleteById(id);
          cache.evict(id);             // remove by-id
          cache.evictRecent();         // deleted row may be in recent list → invalidate, rebuild on next read
      }

      private void validateRecipient(NotificationType type, String recipient) {
          boolean ok = switch (type) {
              case EMAIL -> EMAIL.matcher(recipient).matches();
              case SMS   -> PHONE.matcher(recipient).matches();
          };
          if (!ok) throw new IllegalArgumentException(
                  "recipient '" + recipient + "' is not a valid " + type.getValue() + " address");
      }
  }
  ```
- [ ] **Step 4: Run** `./mvnw -Dtest=NotificationServiceTest test` → expect PASS (8/8).
- [ ] **Step 5: Commit** — `git commit -am "feat(notification): add service orchestration with tests"`

---

### Task 9: NotificationController + web-layer tests — TDD

**Files:** Create `controller/NotificationController.java`, Test `src/test/java/com/example/demo/controller/NotificationControllerTest.java`.

- [ ] **Step 1: Write the failing test** (`@WebMvcTest` + MockMvc + `@MockBean` service — covers AC-1,2,4,5,7,9 status codes)
  ```java
  package com.example.demo.controller;

  import com.example.demo.domain.Notification;
  import com.example.demo.domain.NotificationType;
  import com.example.demo.exception.NotFoundException;
  import com.example.demo.service.NotificationService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;

  import java.time.Instant;
  import java.util.List;

  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.*;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(NotificationController.class)
  class NotificationControllerTest {
      @Autowired MockMvc mvc;
      @MockBean NotificationService service;

      private Notification sample(long id) {
          Notification n = new Notification();
          n.setId(id); n.setType(NotificationType.EMAIL); n.setRecipient("u@e.com");
          n.setSubject("s"); n.setContent("c"); n.setCreatedAt(Instant.now()); n.setUpdatedAt(Instant.now());
          return n;
      }

      @Test void create_returns201() throws Exception {                       // AC-1
          when(service.create(any())).thenReturn(sample(1L));
          mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"type\":\"email\",\"recipient\":\"u@e.com\",\"subject\":\"s\",\"content\":\"c\"}"))
             .andExpect(status().isCreated())
             .andExpect(jsonPath("$.id").value(1));
      }

      @Test void create_invalidType_returns400() throws Exception {           // AC-2
          when(service.create(any())).thenThrow(new IllegalArgumentException("unknown notification type: fax"));
          mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"type\":\"fax\",\"recipient\":\"u@e.com\"}"))
             .andExpect(status().isBadRequest());
      }

      @Test void create_missingRecipient_returns400() throws Exception {      // AC-2 (validation)
          mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"type\":\"email\"}"))
             .andExpect(status().isBadRequest());
      }

      @Test void getById_returns200() throws Exception {
          when(service.getById(1L)).thenReturn(sample(1L));
          mvc.perform(get("/notifications/1")).andExpect(status().isOk());
      }

      @Test void getById_missing_returns404() throws Exception {              // AC-4
          when(service.getById(999L)).thenThrow(new NotFoundException("not found"));
          mvc.perform(get("/notifications/999")).andExpect(status().isNotFound());
      }

      @Test void recent_returns200List() throws Exception {                   // AC-5
          when(service.recent()).thenReturn(List.of(sample(1L), sample(2L)));
          mvc.perform(get("/notifications/recent"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.length()").value(2));
      }

      @Test void update_missing_returns404() throws Exception {               // AC-7
          when(service.update(eq(999L), any())).thenThrow(new NotFoundException("not found"));
          mvc.perform(put("/notifications/999").contentType(MediaType.APPLICATION_JSON)
                  .content("{\"subject\":\"x\",\"content\":\"y\"}"))
             .andExpect(status().isNotFound());
      }

      @Test void delete_returns204() throws Exception {                       // AC-8
          doNothing().when(service).delete(1L);
          mvc.perform(delete("/notifications/1")).andExpect(status().isNoContent());
      }

      @Test void delete_missing_returns404() throws Exception {               // AC-9
          doThrow(new NotFoundException("not found")).when(service).delete(999L);
          mvc.perform(delete("/notifications/999")).andExpect(status().isNotFound());
      }
  }
  ```
- [ ] **Step 2: Run** `./mvnw -Dtest=NotificationControllerTest test` → expect FAIL (controller missing).
- [ ] **Step 3: Implement**
  ```java
  package com.example.demo.controller;

  import com.example.demo.dto.CreateNotificationRequest;
  import com.example.demo.dto.NotificationResponse;
  import com.example.demo.dto.UpdateNotificationRequest;
  import com.example.demo.service.NotificationService;
  import jakarta.validation.Valid;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;

  @RestController
  @RequestMapping("/notifications")
  public class NotificationController {
      private final NotificationService service;
      public NotificationController(NotificationService service) { this.service = service; }

      @PostMapping
      @ResponseStatus(HttpStatus.CREATED)
      public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest req) {
          return NotificationResponse.from(service.create(req));
      }

      @GetMapping("/{id}")
      public NotificationResponse getById(@PathVariable Long id) {
          return NotificationResponse.from(service.getById(id));
      }

      @GetMapping("/recent")
      public List<NotificationResponse> recent() {
          return service.recent().stream().map(NotificationResponse::from).toList();
      }

      @PutMapping("/{id}")
      public NotificationResponse update(@PathVariable Long id, @RequestBody UpdateNotificationRequest req) {
          return NotificationResponse.from(service.update(id, req));
      }

      @DeleteMapping("/{id}")
      @ResponseStatus(HttpStatus.NO_CONTENT)
      public void delete(@PathVariable Long id) {
          service.delete(id);
      }
  }
  ```
- [ ] **Step 4: Run** `./mvnw -Dtest=NotificationControllerTest test` → expect PASS.
  - GOTCHA: `@WebMvcTest` loads `GlobalExceptionHandler` automatically (it's a `@RestControllerAdvice`). Ensure it's in a scanned package under `com.example.demo`.
  - GOTCHA: JSON dates — `NotificationResponse.createdAt` is `Instant`; Spring Boot's default Jackson emits ISO-8601 `...Z`. No extra config needed for the web layer.
- [ ] **Step 5: Commit** — `git commit -am "feat(notification): add REST controller with web-layer tests"`

---

### Task 10: Full build + manual stack validation

- [ ] **Step 1: Full test suite** — `./mvnw test` → expect all green (also confirms `DemoApplicationTests` context loads).
  - GOTCHA: `DemoApplicationTests` (`@SpringBootTest`) will try to start the RocketMQ producer bean → needs namesrv reachable. If it fails in CI without infra, either (a) start docker-compose first, or (b) annotate that test to exclude `RocketMQConfig`, or mark `@Disabled` with a note. Document the choice in HELP.md.
- [ ] **Step 2: Start infra** — `docker-compose up -d` (first run: `docker-compose down -v` if MySQL was previously initialized, so `init.sql` re-runs).
- [ ] **Step 3: Run app** — `./mvnw spring-boot:run`
- [ ] **Step 4: Manual smoke (curl)** — see Validation Commands below; verify 201/200/204/404 and that RocketMQ console (`http://localhost:8088`) shows `notification-topic` traffic.
- [ ] **Step 5: Commit any fixups** — `git commit -am "chore(notification): stack validation fixups"`

---

## Testing Strategy

### Unit / Web Tests
| Test | Layer | AC covered |
|---|---|---|
| `NotificationTypeTest` | domain | type parsing/validation |
| `NotificationServiceTest` (8 cases) | service (Mockito) | AC-3, AC-4, AC-6, AC-7, AC-8, AC-9 + create |
| `NotificationControllerTest` (9 cases) | web (`@WebMvcTest`) | AC-1, AC-2, AC-4, AC-5, AC-7, AC-8, AC-9 |

### Edge Cases Checklist
- [x] Invalid type (`fax`) → 400 (AC-2)
- [x] Missing recipient → 400 (validation)
- [x] Unknown id on GET/PUT/DELETE → 404
- [x] Recent with >10 rows → exactly 10 (LTRIM)
- [ ] Concurrent create keeping recent ≤10 (bonus — LPUSH+LTRIM is the mitigation; not load-tested here)

---

## Validation Commands

### Static Analysis / Compile
```bash
./mvnw -o -q compile
```
EXPECT: BUILD SUCCESS, zero errors

### Unit + Web Tests
```bash
./mvnw test
```
EXPECT: All tests pass (note RocketMQ caveat in Task 10 Step 1)

### Database Validation
```bash
docker-compose up -d
docker exec -it mysql mysql -utaskuser -ptaskpass taskdb -e "SHOW TABLES; DESCRIBE notification;"
```
EXPECT: `notification` table present with 7 columns

### Manual Validation (curl)
```bash
# Create
curl -s -XPOST localhost:8080/notifications -H 'Content-Type: application/json' \
  -d '{"type":"email","recipient":"u@e.com","subject":"Welcome!","content":"Thanks!"}' -i | head -1   # 201
# Get
curl -s localhost:8080/notifications/1                                                                  # 200
# Recent
curl -s localhost:8080/notifications/recent                                                             # [ ... ]
# Update
curl -s -XPUT localhost:8080/notifications/1 -H 'Content-Type: application/json' \
  -d '{"subject":"Updated","content":"Updated body"}' -i | head -1                                      # 200
# Delete
curl -s -XDELETE localhost:8080/notifications/1 -i | head -1                                            # 204
# Missing
curl -s localhost:8080/notifications/999999 -i | head -1                                                # 404
```
- [ ] All status codes match
- [ ] RocketMQ console (`localhost:8088`) shows messages on `notification-topic`
- [ ] Second GET of same id is served from Redis (check via `docker exec -it redis redis-cli keys 'notification:*'`)

---

## Acceptance Criteria
- [ ] All 10 tasks completed
- [ ] `./mvnw test` green (service 8/8, controller 9/9, type 2/2)
- [ ] All 9 SRS acceptance criteria (AC-1…AC-9) demonstrably satisfied
- [ ] curl smoke test returns spec'd status codes
- [ ] RocketMQ topic receives create events
- [ ] Redis holds by-id + recent entries

## Completion Checklist
- [ ] Code follows the established package/naming conventions
- [ ] Error handling via `GlobalExceptionHandler` (404/400)
- [ ] Logging via SLF4J (MQ failure = warn, log-and-continue)
- [ ] Tests follow Mockito (service) / `@WebMvcTest` (controller) split
- [ ] No hardcoded connection values (all from `application.yaml`)
- [ ] HELP.md updated with run/test instructions + RocketMQ test caveat
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `@SpringBootTest` fails without running RocketMQ namesrv | M | M | Start docker-compose before `./mvnw test`, or exclude `RocketMQConfig`/disable `DemoApplicationTests` with a note |
| Redis JSON deserialization of `Instant`/polymorphic type | M | M | `RedisConfig` registers `JavaTimeModule` + default typing; cache stores domain `Notification` consistently |
| `init.sql` not re-run on existing MySQL volume | M | L | `docker-compose down -v` before first real run |
| MQ publish failure after DB commit | L | L | Decision: log-and-continue (DB is source of truth) — documented in SRS Open Questions |

## Notes
- **Resolved SRS open questions** (defaults adopted; override if you disagree):
  1. MQ-publish-after-commit failure → **log-and-continue** (Task 7).
  2. `recent` source → **Redis-first, DB `findRecent(10)` fallback** on cold start (Task 8 `recent()`).
  3. Redis by-id TTL → **3600s** (from `application.yaml`).
- **Update vs evict on PUT**: chose **update cache** (re-put fresh value) to keep subsequent reads warm; evict is an equally valid alternative per README.
- No Lombok in `pom.xml` — all getters/setters written explicitly.
