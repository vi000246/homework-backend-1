---
linear_issue: null
---
# SRS: Notification Core API

## Metadata
- **Module**: `notification`
- **Module Spec**: `docs/spec/notification.spec.md`
- **Source PRD**: N/A (derived from `README.md` assignment brief)
- **Source Linear Issue**: N/A
- **Created**: 2026-06-22

## Feature Summary

Implements the complete notification CRUD API (5 endpoints) for the `notification` module,
wiring MySQL persistence, RocketMQ event publishing, and Redis caching (by-id + recent-10).
This is the first and only feature of the module — greenfield within the project skeleton.

## Delta from Current Module State

> Refer to `docs/spec/notification.spec.md` for target architecture. The module currently
> has **zero implementation** (only `DemoApplication` + empty test). This feature builds the
> entire module.

### New / Changed API Endpoints

| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/notifications` | Create notification (DB + MQ + cache) | none |
| GET | `/notifications/{id}` | Fetch by id (cache-aside) | none |
| GET | `/notifications/recent` | List 10 most recent | none |
| PUT | `/notifications/{id}` | Update subject/content | none |
| DELETE | `/notifications/{id}` | Delete (DB + cache) | none |

### New / Changed Data Models

New table `notification` (see `init.sql`): `id, type, recipient, subject, content, created_at, updated_at`.

### Changed Business Logic

All new. Core flow: Controller → Service orchestrates Repository (MySQL) + CacheService (Redis) + Producer (RocketMQ).

### Explicitly Out of Scope

- Authentication / authorization (assignment defines none)
- Actual email/SMS delivery (only persistence + event publish; delivery is a downstream MQ consumer, not built here)
- Pagination / filtering beyond `recent` (fixed 10)
- Consuming `notification-topic` (only producing)

## Functional Requirements (Implementation Checklist)

**Infrastructure / wiring**
- [ ] `RocketMQConfig` — build `Producer` bean from `rocketmq.name-server` + group; manage start/shutdown lifecycle
- [ ] `RedisConfig` — `RedisTemplate<String,Object>` with JSON serialization
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`) — map `NotFoundException`→404, validation→400

**Domain / persistence**
- [ ] `Notification` domain model + `NotificationType` enum (`email` | `sms`)
- [ ] `NotificationRepository` (JdbcTemplate) — `insert`, `findById`, `findRecent(limit)`, `updateSubjectContent`, `deleteById`, `existsById`

**Cache**
- [ ] `NotificationCacheService` — `putById`/`getById` (`notification:{id}`, TTL), `pushRecent`/`getRecent` (capped 10), `evict`

**Messaging**
- [ ] `NotificationProducer` — send `NotificationMessage` to `notification-topic`

**DTO**
- [ ] `CreateNotificationRequest`, `UpdateNotificationRequest`, `NotificationResponse`, `NotificationMessage`

**Service / Controller (per endpoint)**
- [ ] SRS-F-01 Create: validate → insert MySQL → publish MQ → push recent cache → 201
- [ ] SRS-F-02 Get by id: cache hit return; miss → DB → backfill cache; 404 if absent
- [ ] SRS-F-03 Recent: serve from Redis recent set (fallback DB `findRecent(10)`)
- [ ] SRS-F-04 Update: 404 if absent → update MySQL subject/content → update-or-evict cache → 200
- [ ] SRS-F-05 Delete: 404 if absent → delete MySQL → evict cache → 204

**Tests**
- [ ] Unit tests for Service (mock repo/cache/producer)
- [ ] Integration/web-layer tests for Controller status codes (201/200/204/404/400)

## Non-Functional Requirements

| Category | Target | How Achieved |
|---|---|---|
| Correctness | All 5 endpoints return spec'd status codes | Web-layer tests |
| Consistency | Cache never serves stale after update/delete | Update-or-evict on PUT, evict on DELETE (cache-aside) |
| Concurrency (bonus) | Recent list stays ≤10 under parallel writes | Atomic Redis ops (LPUSH+LTRIM) |
| Observability | Failures logged with id/type | Structured logging in Service |
| Resilience | MQ/Redis outage degrades gracefully | DB is source of truth; cache/MQ failures logged, don't block core write decision (document chosen policy) |

## Architecture Notes

Layered (cache-aside). See `docs/spec/notification.spec.md` for the component table and data flow.
Key decision: RocketMQ uses the **native client** (`rocketmq-client 5.3.2`), so the producer is a
manually-managed bean rather than starter auto-config. Persistence uses **JdbcTemplate** (scaffold
ships `starter-jdbc`, not JPA).

**Open design point (needs a decision in plan):** when RocketMQ publish fails *after* the DB row is
committed, do we (a) accept eventual inconsistency and log, or (b) wrap in a transaction and roll back?
Recommend (a) — DB is source of truth, MQ is fire-and-forget for this assignment scope.

## Acceptance Criteria

### AC-1: Create persists, publishes, caches
- **Given**: services (MySQL/Redis/RocketMQ) are up
- **When**: `POST /notifications` with valid `{type, recipient, subject, content}`
- **Then**: returns 201 with generated `id`; row exists in MySQL; message sent to `notification-topic`; entry appears in recent cache
- **Test**: `NotificationControllerTest::create_returns201AndPersists`

### AC-2: Create rejects invalid type
- **Given**: services up
- **When**: `POST /notifications` with `type` not in {email, sms} or missing recipient
- **Then**: returns 400
- **Test**: `NotificationControllerTest::create_invalidType_returns400`

### AC-3: Get by id is cache-aside
- **Given**: a notification exists in MySQL but not in Redis
- **When**: `GET /notifications/{id}`
- **Then**: returns 200 with the notification; afterwards the entry is present in Redis
- **Test**: `NotificationServiceTest::getById_cacheMiss_backfills`

### AC-4: Get missing id returns 404
- **Given**: no notification with id 999999
- **When**: `GET /notifications/999999`
- **Then**: returns 404
- **Test**: `NotificationControllerTest::getById_missing_returns404`

### AC-5: Recent returns ≤10 newest
- **Given**: 12 notifications created
- **When**: `GET /notifications/recent`
- **Then**: returns exactly 10, ordered newest-first, each with `id,type,recipient,subject,createdAt`
- **Test**: `NotificationControllerTest::recent_returnsLast10`

### AC-6: Update changes fields and refreshes cache
- **Given**: a notification exists (and is cached)
- **When**: `PUT /notifications/{id}` with `{subject, content}`
- **Then**: returns 200; MySQL row updated; cached value updated or evicted (no stale read)
- **Test**: `NotificationServiceTest::update_refreshesCache`

### AC-7: Update missing id returns 404
- **Given**: no notification with given id
- **When**: `PUT /notifications/{id}`
- **Then**: returns 404
- **Test**: `NotificationControllerTest::update_missing_returns404`

### AC-8: Delete removes from DB and cache
- **Given**: a notification exists and is cached
- **When**: `DELETE /notifications/{id}`
- **Then**: returns 204; row gone from MySQL; cache entry removed
- **Test**: `NotificationServiceTest::delete_evictsCache`

### AC-9: Delete missing id returns 404
- **Given**: no notification with given id
- **When**: `DELETE /notifications/{id}`
- **Then**: returns 404
- **Test**: `NotificationControllerTest::delete_missing_returns404`

## Resolved Decisions (senior-review pass — interview project)

> Open questions resolved during planning. Rationale captured for interview discussion.

| Decision | Choice | Rationale |
|---|---|---|
| MQ publish failure after DB commit | **log-and-continue** | DB is source of truth; MQ best-effort. Guaranteed delivery → transactional outbox (out of scope, mention in interview) |
| `recent` source of truth | **Redis-first, rebuild from DB `findRecent(10)` on miss; invalidate on update/delete** | Prevents stale/deleted rows lingering in the recent list |
| Redis by-id TTL | **3600s** (configurable) | Bounds staleness window even if an invalidation is missed |
| Redis unavailability | **degrade to cache-miss / no-op, never 500** | Cache is an accelerator, not a hard dependency |
| RocketMQ unavailability at boot | **app still starts**; sends are best-effort with 3s timeout | A transient broker outage must not block the service |
| Update cache strategy | **re-put fresh by-id + evict recent** | Keeps subsequent by-id reads warm; recent rebuilt lazily |
| Recipient validation | **per-type format (email regex / E.164 phone) → 400** | Reject bad data at the edge, not at the DB |

## Robustness NFRs (added in senior-review pass)

| Property | Requirement |
|---|---|
| Graceful degradation | GET/recent succeed from MySQL when Redis is down |
| Bounded latency | Publish capped (3s) so a dead broker can't hang request threads |
| Fail-fast validation | Invalid type / over-long field / wrong recipient format → 400, never 500 |
| No info leakage | Unexpected errors → uniform JSON 500, no stack traces to clients |
| Hard-dependency clarity | Only MySQL is required; Redis + RocketMQ are soft dependencies |

See the **Failure-Mode Matrix** in `docs/plans/notification-core-api.plan.md` for the full scenario table.

## Plan

- `docs/plans/notification-core-api.plan.md` (Mode C — TDD bite-sized; hardened in senior-review pass)
