# Module Spec: notification

> **Status**: BROWNFIELD CREATE (from assignment scaffold — target spec, implementation pending)
> **Last Updated**: 2026-06-22
> **Source**: code-sync (derived from README.md + provided infrastructure)

---

## Overview

通知服務（Notification Service）。提供 RESTful API 讓使用者送出 **email** 或 **sms**
通知。通知會持久化於 **MySQL**、推送事件至 **RocketMQ**、並以 **Redis** 快取最近通知與
單筆查詢。

技術棧：Java 21 / Spring Boot 3.5.3 / MySQL 8 / Redis 7 / RocketMQ 5.1.4。

---

## Domain Model

### Bounded Context
- **Context Name**: Notification
- **Domain Layer**: Core Domain
- **Parent Module**: N/A

### Ubiquitous Language
| Term | Definition |
|------|-----------|
| Notification | 一則待送出的通知，含類型、收件人、主旨與內容 |
| Type | 通知通道，限 `email` 或 `sms` |
| Recipient | 收件人（email 信箱或手機號碼） |
| Recent Notifications | 最近 10 筆通知，由 Redis 維護 |
| notification-topic | RocketMQ topic，建立通知時發佈事件 |

### Domain Events
| Event | Trigger Condition | Consumers |
|---|---|---|
| NotificationCreated | `POST /notifications` 成功寫入 MySQL 後 | RocketMQ `notification-topic` 訂閱者（外部 worker） |

---

## Architecture

分層架構（Layered）：

```
HTTP ─▶ NotificationController ─▶ NotificationService ─┬─▶ NotificationRepository (JdbcTemplate) ─▶ MySQL
                                                       ├─▶ NotificationCacheService (RedisTemplate) ─▶ Redis
                                                       └─▶ NotificationProducer ─▶ RocketMQ (notification-topic)
```

| Layer | Component | Responsibility |
|---|---|---|
| Controller | `NotificationController` | 5 個 REST endpoint、HTTP 狀態碼映射 |
| DTO | `CreateNotificationRequest` / `UpdateNotificationRequest` / `NotificationResponse` / `NotificationMessage` | 請求 / 回應 / MQ 訊息格式 |
| Service | `NotificationService` | 編排 DB + MQ + Cache，定義交易邊界 |
| Repository | `NotificationRepository` | JdbcTemplate CRUD 持久化 |
| Cache | `NotificationCacheService` | by-id 快取 + 最近 10 筆 |
| Messaging | `NotificationProducer` + `RocketMQConfig` | 建立 Producer bean、送 `notification-topic` |
| Config | `RedisConfig` | RedisTemplate 序列化設定 |
| Exception | `GlobalExceptionHandler` + `NotFoundException` | 統一錯誤處理（400 / 404） |
| Domain | `Notification` + `NotificationType` (enum) | 領域模型 |

### Caching Strategy (cache-aside)
- **by-id**：key `notification:{id}` → JSON，含 TTL。
- **recent**：`notifications:recent`（List 或依 createdAt 排序的 ZSet），上限 10 筆。
- DB 為 single source of truth；Redis miss 時回讀 DB 並回寫。

---

## Data Model

Table: `notification`

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| type | VARCHAR(16) | `email` \| `sms` |
| recipient | VARCHAR(255) | email 或手機 |
| subject | VARCHAR(255) | |
| content | TEXT | |
| created_at | DATETIME | 建立時間 |
| updated_at | DATETIME | PUT 時更新 |

---

## API Contracts

| Method | Path | Request | Success | Errors |
|---|---|---|---|---|
| POST | `/notifications` | `{type, recipient, subject, content}` | 201 Created（含 id） | 400 驗證失敗 |
| GET | `/notifications/{id}` | — | 200 NotificationResponse | 404 不存在 |
| GET | `/notifications/recent` | — | 200 陣列（最近 10 筆） | — |
| PUT | `/notifications/{id}` | `{subject, content}` | 200 更新後物件 | 404 不存在 |
| DELETE | `/notifications/{id}` | — | 204 No Content | 404 不存在 |

- `recent` 回應欄位：`id, type, recipient, subject, createdAt`（ISO-8601 UTC）。
- RocketMQ 訊息 DTO：`NotificationMessage`，topic 固定 `notification-topic`。

---

## Requirements (S-SRS)

### Functional
| ID | Requirement |
|---|---|
| SRS-F-01 | `POST /notifications` → 寫 MySQL → 送 RocketMQ → 加入 Redis 最近 10 筆，回 201 |
| SRS-F-02 | `GET /notifications/{id}` → 先 Redis；miss 讀 MySQL 並回寫，200/404 |
| SRS-F-03 | `GET /notifications/recent` → 回最近 10 筆（優先 Redis），200 |
| SRS-F-04 | `PUT /notifications/{id}` → 更新 subject/content；Redis 同步或失效；404 if not exists |
| SRS-F-05 | `DELETE /notifications/{id}` → 刪 MySQL + 移除 Redis；204/404 |

### Interface / Data
| ID | Requirement |
|---|---|
| SRS-I-01 | 建立請求 `{type, recipient, subject, content}`，`type ∈ {email, sms}` |
| SRS-I-02 | 更新請求僅 `{subject, content}` 可改 |
| SRS-I-03 | recent 回應含 `id, type, recipient, subject, createdAt`（ISO-8601 UTC） |
| SRS-I-04 | 自訂 RocketMQ 訊息 DTO，topic 固定 `notification-topic` |
| SRS-D-01 | 持久化於 MySQL `taskdb`，schema 由 `init.sql` 建立 |
| SRS-D-02 | Redis 維護「最近 10 筆」與「by-id」兩種快取（上限/TTL） |
| SRS-D-03 | DB 為 single source of truth，Redis 為 cache-aside |

### Non-Functional
| ID | Requirement |
|---|---|
| SRS-N-01 | 技術棧：Java 21、Spring Boot 3.5.3、MySQL 8、Redis 7、RocketMQ 5.1.4 |
| SRS-N-02 | `docker-compose up -d` 一鍵啟動（3306/6379/9876/10911/8088） |
| SRS-N-03 | 一致且模組化的分層結構 |
| SRS-N-04 | 統一錯誤處理與具語意的 HTTP 狀態碼 |
| SRS-N-05 | (Bonus) 考慮並發 / race condition（更新與快取一致性） |
| SRS-N-06 | (Bonus) 測試覆蓋率盡可能高（unit + integration） |
| SRS-N-07 | 連線設定外部化於 `application.yaml` |

---

## Assumptions & Constraints
- **AS-01**: REST 需加入 `spring-boot-starter-web`（鷹架未含）。✅ 已於前置設定補上。
- **AS-02**: MySQL driver 需加入 `mysql-connector-j`。✅ 已補。
- **AS-03**: RocketMQ 採原生 `rocketmq-client 5.3.2`，需手動建立 Producer bean 並管理生命週期。
- **CON-01**: grpc 鎖定 1.33.0（rocketmq 5.x client 相容性），不可變動。
- **CON-02**: 預期完成時間約 3 小時，聚焦 API 正確性與各技術正確接入。

---

## Change History

| Timestamp | Source | Feature SRS | Summary |
|---|---|---|---|
| 2026-06-22 | code-sync | — | Created from brownfield analysis — notification service target spec derived from README + infra; prerequisite configs (pom web/mysql driver, application.yaml, init.sql) bootstrapped |
| 2026-06-22 | README brief | `docs/srs/notification-core-api.srs.md` | Feature SRS authored — notification core CRUD API (5 endpoints) with MySQL/RocketMQ/Redis wiring + acceptance criteria |
| 2026-06-22 | senior-review | `docs/plans/notification-core-api.plan.md` | Plan authored (Mode C TDD) + hardened: Redis/MQ graceful degradation, recent-cache invalidation on update/delete, per-type recipient validation, bounded MQ publish, uniform error mapping. Failure-Mode Matrix added |
