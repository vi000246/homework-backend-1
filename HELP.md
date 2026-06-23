# HELP — Notification Service Setup & Run

A Spring Boot notification API backed by **MySQL** (persistence), **Redis** (cache-aside), and
**RocketMQ** (event publishing). Java 21 / Spring Boot 3.5.3.

## Prerequisites
- Java 21+ and Maven (or use the bundled `./mvnw`)
- Docker + Docker Compose (for MySQL / Redis / RocketMQ)

## 1. Start infrastructure
```bash
docker-compose up -d
# First run only, or after schema changes — wipe the MySQL volume so init.sql re-runs:
# docker-compose down -v && docker-compose up -d
```
| Service | Port |
|---|---|
| MySQL | 3306 (db `taskdb`, user `taskuser`, pass `taskpass`) |
| Redis | 6379 |
| RocketMQ Namesrv | 9876 |
| RocketMQ Broker | 10911 |
| RocketMQ Console | 8088 (http://localhost:8088) |

The `notification` table is created automatically by `init.sql` on first MySQL start.

## 2. Build & test
```bash
./mvnw test          # 32 unit/web-layer tests — runs WITHOUT infra (context-load is resilient)
./mvnw verify        # + Testcontainers integration tests (real MySQL+Redis) — needs Docker
./mvnw spring-boot:run
```
> `mvn test` does **not** require Docker: service/producer/cache tests use Mockito, the controller test
> uses `@WebMvcTest`, and the full-context test loads with infra absent (DB pool + MQ producer + Redis
> all init lazily / non-fatally). Integration tests (`*IT`, run by `mvn verify`) spin up real MySQL +
> Redis via Testcontainers.
> Coverage report after `mvn test`: open `target/site/jacoco/index.html`.

### Optimistic concurrency (optional, on PUT)
`PUT /notifications/{id}` accepts an optional `If-Match: "<version>"` header. Each response carries a
`version`. If the supplied version is stale (someone else updated first), the API returns **409
VERSION_CONFLICT** instead of silently overwriting (lost-update prevention). Without `If-Match`, the
update is unconditional (backward compatible).

## 3. API smoke test (curl)
```bash
# Create (201)
curl -s -XPOST localhost:8080/notifications -H 'Content-Type: application/json' \
  -d '{"type":"email","recipient":"user@example.com","subject":"Welcome!","content":"Thanks!"}' -i | head -1
# Get by id (200 / 404)
curl -s localhost:8080/notifications/1
# Recent (<=10, newest first)
curl -s localhost:8080/notifications/recent
# Update subject/content (200 / 404)
curl -s -XPUT localhost:8080/notifications/1 -H 'Content-Type: application/json' \
  -d '{"subject":"Updated","content":"Updated body"}' -i | head -1
# Delete (204 / 404)
curl -s -XDELETE localhost:8080/notifications/1 -i | head -1
```
Verify cache: `docker exec -it redis redis-cli keys 'notification*'`
Verify MQ: open the RocketMQ console at http://localhost:8088 -> topic `notification-topic`.

## 4. Endpoints
| Method | Path | Behavior | Codes |
|---|---|---|---|
| POST | `/notifications` | persist -> publish MQ -> cache | 201 / 400 |
| GET | `/notifications/{id}` | cache-aside read | 200 / 404 |
| GET | `/notifications/recent` | 10 newest (Redis, DB fallback) | 200 |
| PUT | `/notifications/{id}` | update subject/content, refresh cache | 200 / 404 |
| DELETE | `/notifications/{id}` | delete + evict cache | 204 / 404 |

## Design notes (for reviewers)
- **MySQL is the only hard dependency.** Redis and RocketMQ are best-effort: a Redis outage degrades
  to cache-miss (reads fall back to MySQL, never 500); an MQ outage is logged and the create still
  succeeds. Publishes are time-bounded (3s) so a dead broker can't hang request threads.
- **Cache consistency:** by-id is updated/evicted on write; the recent list is invalidated on
  update/delete and rebuilt from MySQL on the next read (no stale or deleted rows served).
- **Validation:** unknown type, over-long fields, and wrong recipient format (email vs phone) -> 400.
- Full architecture/decisions: `docs/spec/notification.spec.md`,
  `docs/srs/completed/notification-core-api.srs.md`,
  `docs/plans/completed/notification-core-api.plan.md` (incl. Failure-Mode Matrix).

## Submission note
This directory is not yet a git repo. To submit to GitHub:
```bash
git init && git add . && git commit -m "feat: notification service"
# then create a public repo and push
```
