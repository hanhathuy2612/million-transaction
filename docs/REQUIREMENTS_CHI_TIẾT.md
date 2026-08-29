# Tài liệu yêu cầu chi tiết
## Hệ thống xử lý giao dịch thanh toán (Million Transaction)

| Thuộc tính | Giá trị |
|---|---|
| Mã dự án | MILLIONS-TRANSACTION |
| Phiên bản | 2.0 |
| Cập nhật | 2026-08-29 |
| Base URL (local) | `http://localhost:8888` |
| Package gốc | `com.hnh.example.transaction_example` |

---

## Mục lục

1. [Tổng quan kỹ thuật](#1-tổng-quan-kỹ-thuật)
2. [Đặc tả API](#2-đặc-tả-api)
3. [Luồng xử lý giao dịch](#3-luồng-xử-lý-giao-dịch)
4. [Idempotency](#4-idempotency)
5. [Sổ cái (Ledger)](#5-sổ-cái-ledger)
6. [Webhook & Kafka](#6-webhook--kafka)
7. [Analytics](#7-analytics)
8. [Xác thực & bảo mật](#8-xác-thực--bảo-mật)
9. [Cơ sở dữ liệu](#9-cơ-sở-dữ-liệu)
10. [Kiến trúc hạ tầng](#10-kiến-trúc-hạ-tầng)
11. [Cấu hình ứng dụng](#11-cấu-hình-ứng-dụng)
12. [Monitoring & Health](#12-monitoring--health)
13. [Ma trận triển khai](#13-ma-trận-triển-khai)

---

## 1. Tổng quan kỹ thuật

### 1.1. Mục tiêu thiết kế

- Xử lý khối lượng lớn giao dịch qua hàng đợi Redis + xử lý batch.
- Đảm bảo không mất sự kiện nhờ Transactional Outbox Pattern.
- Tách đọc/ghi MySQL Master-Slave.
- Idempotency hai tầng: Redis (nhanh) + MySQL (bền vững).

### 1.2. Headers chuẩn

| Header | Hằng số | Dùng cho |
|---|---|---|
| `Authorization` | — | `Bearer <JWT>` — tất cả API trừ auth/health |
| `X-Merchant-ID` | `HeaderConstant.MERCHANT_ID` | Payment, Analytics, List payments |
| `Idempotency-Key` | `HeaderConstant.IDEMPOTENCY_KEY` | Create payment (bắt buộc), Refund (bắt buộc), Capture (tùy chọn) |
| `Content-Type` | — | `application/json` |

---

## 2. Đặc tả API

### 2.1. Authentication

#### POST `/api/v1/auth/register`

**Request (`RegisterRequest`):**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+84123456789"
}
```

**Response (`UserResponse`):** thông tin user vừa tạo.

**Quy tắc:** email unique; password BCrypt; không yêu cầu JWT.

---

#### POST `/api/v1/auth/login`

**Request (`LoginRequest`):**
```json
{
  "username": "admin@example.com",
  "password": "admin"
}
```

**Response (`TokenResponse`):**
```json
{ "token": "eyJhbGciOiJIUzUxMiJ9..." }
```

**Response header:** `Authorization: Bearer <token>`

| Cấu hình JWT | Giá trị |
|---|---|
| Algorithm | HS512 |
| Validity | 604800 giây (7 ngày) |
| Secret | `app.security.authentication.jwt.base64-secret` |

User mặc định (Liquibase): `admin@example.com`

---

### 2.2. Payments

#### POST `/api/v1/payments`

**Headers:** `Authorization`, `X-Merchant-ID`, `Idempotency-Key`

**Request (`PaymentRequest`):**
```json
{
  "merchantId": "merchant_123",
  "amount": 100.00,
  "currency": "USD",
  "paymentMethodId": "pm_123",
  "description": "Order #123",
  "referenceId": "ref_123"
}
```

**Validation:**
- `amount` ≥ 0.01; `@Digits(integer=10, fraction=2)`
- `currency`: `USD` | `EUR` | `GBP` | `JPY`
- JPY: không có phần thập phân (`scale == 0`)
- `merchantId` header phải khớp body

**Response (`PaymentResponse`) — HTTP 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "merchant_123",
  "amount": 100.00,
  "currency": "USD",
  "status": "PENDING",
  "paymentMethodId": "pm_123",
  "description": "Order #123",
  "referenceId": "ref_123",
  "capturedAmount": 0,
  "refundedAmount": 0,
  "createdDate": "2026-08-29T14:30:00",
  "lastModifiedDate": "2026-08-29T14:30:00"
}
```

**Lỗi thường gặp:**

| HTTP | Nguyên nhân |
|---|---|
| 400 | Validation, merchant ID không khớp, idempotency key trùng body khác |
| 401 | JWT không hợp lệ |
| 409 | Trạng thái conflict (ít xảy ra ở create) |

---

#### GET `/api/v1/payments/{paymentId}`

- Transaction read-only → route slave DB.
- Không yêu cầu `X-Merchant-ID`.
- Response bổ sung: `authorizedAt`, `capturedAt`, `failedAt`, `failureReason`, `processorTransactionId`, `processorName`.

---

#### GET `/api/v1/payments`

**Headers:** `X-Merchant-ID`

**Query params (Spring Pageable):**
- `page` (default 0), `size` (default 20), `sort` (default `createdDate`)

**Response headers:**
- `X-Total-Count`, `X-Total-Pages`, `X-Current-Page`, `X-Page-Size`

---

#### POST `/api/v1/payments/{paymentId}/capture`

**Request (`CaptureRequest`):**
```json
{
  "amount": 50.00,
  "description": "Partial capture",
  "referenceId": "cap_ref_001"
}
```

> **Lưu ý:** `amount` là **bắt buộc** (không có default full amount).

**Điều kiện:** `status == AUTHORIZED` và `capturedAmount + amount ≤ payment.amount`

**Kết quả trạng thái:**
- Capture đủ → `CAPTURED`
- Capture một phần → vẫn `AUTHORIZED`

---

#### POST `/api/v1/payments/{paymentId}/refunds`

**Request (`RefundRequest`):**
```json
{
  "amount": 25.00,
  "reason": "Customer request",
  "referenceId": "refund_ref_001"
}
```

**Headers:** `Idempotency-Key` bắt buộc.

**Điều kiện:** `status ∈ {CAPTURED, PARTIALLY_REFUNDED}` và `refundedAmount + amount ≤ capturedAmount`

---

#### POST `/api/v1/payments/{paymentId}/void` — *Chưa triển khai*

Domain hỗ trợ `CANCELLED` và ledger `VOID`, nhưng không có controller endpoint.

---

### 2.3. Analytics

#### GET `/api/v1/analytics/payments`

**Params:** `fromDate`, `toDate` (ISO datetime), header `X-Merchant-ID`

**Validation:** `fromDate ≤ toDate`; khoảng tối đa 90 ngày.

**Response (query DB thực, cache Redis 15 phút):**
```json
{
  "totalVolume": 15000.00,
  "capturedVolume": 14750.00,
  "totalCount": 150,
  "capturedCount": 145,
  "failedCount": 5,
  "successRate": 96.67,
  "statusBreakdown": {
    "AUTHORIZED": 10,
    "CAPTURED": 145,
    "FAILED": 5
  },
  "averageTransactionAmount": 100.00
}
```

> `successRate` = `(capturedCount / totalCount) × 100` — chỉ tính status `CAPTURED`.

---

#### GET `/api/v1/analytics/metrics`

**Trạng thái:** trả **dữ liệu mock cố định** (chưa query DB).

```json
{
  "merchantId": "merchant_123",
  "periodStart": "2026-01-01T00:00:00",
  "periodEnd": "2026-01-31T23:59:59",
  "totalAuthorizations": 150,
  "totalCaptures": 145,
  "totalRefunds": 5,
  "totalFailures": 10,
  "conversionRate": 96.67,
  "refundRate": 3.45,
  "failureRate": 6.25
}
```

---

### 2.4. Webhooks (REST — stub)

`WebhookController` trả dữ liệu mock, **chưa** gọi `WebhookService`.

| Method | Path | Trạng thái |
|---|---|---|
| POST | `/api/v1/webhooks` | Stub |
| GET | `/api/v1/webhooks` | Stub |
| DELETE | `/api/v1/webhooks/{webhookId}` | Stub |
| POST | `/api/v1/webhooks/{webhookId}/test` | Stub |

**Webhook delivery thực** (qua Kafka consumer → `WebhookService`):

**Payload gửi đi:**
```json
{
  "id": "uuid",
  "eventType": "payment.authorized",
  "data": { "...eventData from Kafka..." },
  "timestamp": "2026-08-29T14:30:00Z"
}
```

**Headers gửi đi:**
- `X-Webhook-Signature: sha256=<Base64 HMAC-SHA256>`
- `X-Webhook-Event-Type: payment.authorized`

**Retry:** 3 lần; backoff `1000 × 2^attempt` ms (2s, 4s).

**DB schema thực (`CreateWebhookRequest`):**
```json
{
  "merchantId": "merchant_123",
  "webhookUrl": "https://merchant.example.com/webhook",
  "webhookSecret": "whsec_...",
  "events": "payment.authorized,payment.captured"
}
```

---

### 2.5. Error Response

```json
{
  "timestamp": "2026-08-29T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Capture amount exceeds remaining authorized amount",
  "path": "/api/v1/payments/{id}/capture",
  "validationErrors": { "amount": "Amount must be greater than 0" }
}
```

| Exception | HTTP |
|---|---|
| `IllegalArgumentException` | 400 |
| `IllegalStateException` | 409 |
| `MethodArgumentNotValidException` | 400 |
| `AuthenticationException` | 401 |

---

## 3. Luồng xử lý giao dịch

### 3.1. Tạo payment (2 phase)

```
Phase 1 (đồng bộ — PaymentService.createPayment)
  1. IdempotencyService.checkIdempotency
  2. PaymentGuardService.guardWrite (semaphore)
  3. PaymentProcessingService.createNewPaymentPhase1
       → validate currency/precision
       → save Payment (PENDING)
       → OutboxService.publishPendingPayment
  4. PaymentQueueService.enqueuePayment → Redis list "payment:queue"
  5. IdempotencyService.storeIdempotentResponse
  6. Return 200 OK

Phase 2 (bất đồng bộ — BatchPaymentProcessor @Scheduled 1s)
  1. Dequeue batch (batch-size: 50)
  2. PaymentProcessingService.processPaymentAsync (@Async)
       → StripePaymentProcessorService.simulatePayment
       → Success: AUTHORIZED + ledger AUTHORIZATION + outbox PAYMENT_AUTHORIZED
       → Failure: FAILED + outbox PAYMENT_FAILED
```

### 3.2. Capture / Refund (đồng bộ)

```
PaymentService.capturePayment / refundPayment
  1. Idempotency check (nếu có key)
  2. Validate trạng thái + số tiền
  3. Update Payment entity
  4. PaymentLedger entry
  5. OutboxService.publishPaymentCaptured / publishPaymentRefunded
  6. Return 200 OK
```

> Capture/refund **không** gọi `StripePaymentProcessorService.capturePayment/refundPayment`.

### 3.3. Outbox → Kafka

```
OutboxRelayService @Scheduled 5s
  → OutboxService.getUnpublishedEvents
  → KafkaTemplate.send("payments.events.v1", paymentId, payload)
  → mark published
```

**Event types (`PaymentEvent` constant):**
- `payment.pending`
- `payment.authorized`
- `payment.captured`
- `payment.refunded`
- `payment.failed`

---

## 4. Idempotency

**Service:** `IdempotencyService.java`

| Tầng | Chi tiết |
|---|---|
| Redis key | `idempotency:{merchantId}:{idempotencyKey}` |
| Redis TTL | 24 giờ (`DEFAULT_REDIS_TTL`) |
| DB table | `idempotency_keys` |
| DB TTL | Create: 7 ngày; Capture: 3 ngày; Refund: 30 ngày |
| Hash | SHA-256 của request body |
| Cleanup | Cron `0 0 2 * * *` — xóa key hết hạn |

**Luồng kiểm tra:**
1. Redis hit → return cached response
2. DB hit + chưa hết hạn + hash khớp → return + warm Redis
3. DB hit + hash khác → `400 Bad Request`
4. Miss → xử lý bình thường, lưu response

---

## 5. Sổ cái (Ledger)

**Bảng:** `payment_ledger` (Liquibase `002-create-payment-ledger-table.xml`)

| Cột | Kiểu | Mô tả |
|---|---|---|
| seq | BIGINT AUTO_INCREMENT | PK, thứ tự bút toán |
| payment_id | VARCHAR(36) | FK payments |
| entry_type | ENUM | AUTHORIZATION, CAPTURE, REFUND, VOID, FEE, CHARGEBACK |
| delta_amount | DECIMAL(19,2) | Thay đổi số tiền |
| balance_after | DECIMAL(19,2) | Số dư sau bút toán |
| occurred_at | TIMESTAMP | Thời điểm ghi |
| description | VARCHAR(500) | |
| reference_id | VARCHAR(100) | |

**Quy tắc:** immutable; `balance_after = balance_trước + delta_amount`.

---

## 6. Webhook & Kafka

### 6.1. Kafka Topics

| Topic | Partitions | Retention | Sử dụng |
|---|---|---|---|
| `payments.events.v1` | 12 | 7 ngày | Events chính |
| `payments.events.v1.dlt` | 3 | 30 ngày | Dead letter (định nghĩa, chưa wired) |
| `webhooks.events.v1` | 6 | 1 ngày | Định nghĩa, chưa sử dụng |

### 6.2. Consumer Groups

| Group ID | Mục đích |
|---|---|
| `webhook-processor` | Gửi webhook + ghi analytics log |
| `read-model-projector` | Stub projection (logging only) |

**Cấu hình consumer:** manual commit, concurrency=3, `max-poll-records=100`.

### 6.3. Redis Queue

| Key | Loại | Mục đích |
|---|---|---|
| `payment:queue` | List | Hàng đợi payment chờ authorization |
| `payment:processing` | Set | Payment đang xử lý |

---

## 7. Analytics

| Endpoint | Implementation | Cache |
|---|---|---|
| `/analytics/payments` | `PaymentRepository.findByMerchantIdAndCreatedDateBetween` | Redis 15 phút |
| `/analytics/metrics` | Mock constants trong `AnalyticsService` | Không |

Event recording (từ Kafka): log structured metrics, không persist DB.

---

## 8. Xác thực & bảo mật

**File cấu hình:** `SecurityConfiguration.java`

| Path pattern | Access |
|---|---|
| `/api/v1/health/**` | Public |
| `/api/v1/auth/login`, `/register` | Public |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public |
| `/actuator/**` | Public |
| `/api/**` (còn lại) | JWT required |

**Password:** BCrypt (`BCryptPasswordEncoder`)

**CORS:** allow all origins/headers/methods (development config).

**Chưa triển khai:** rate limiting, merchant isolation trên GET payment by ID.

---

## 9. Cơ sở dữ liệu

**Changelog master:** `src/main/resources/db/changelog/db.changelog-master.xml`

| File | Bảng |
|---|---|
| `001-create-payments-table.xml` | `payments` |
| `002-create-payment-ledger-table.xml` | `payment_ledger` |
| `003-create-outbox-events-table.xml` | `outbox_events` |
| `004-create-idempotency-keys-table.xml` | `idempotency_keys` |
| `005-create-user-and-authority.xml` | `app_user`, `authority`, `user_authority` |
| `006-add-webhook-table.xml` | `webhooks` |

**JPA:** `ddl-auto: validate` — schema chỉ thay đổi qua Liquibase.

### 9.1. Bảng `payments` (tóm tắt)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | UUID | PK |
| merchant_id | VARCHAR | NOT NULL |
| amount | DECIMAL(19,2) | |
| currency | CHAR(3) | |
| status | VARCHAR | Enum PaymentStatus |
| captured_amount | DECIMAL(19,2) | Default 0 |
| refunded_amount | DECIMAL(19,2) | Default 0 |
| processor_transaction_id | VARCHAR | Từ Stripe simulate |
| processor_name | VARCHAR | |

### 9.2. Bảng `outbox_events`

| Cột | Kiểu |
|---|---|
| id | BIGINT PK |
| aggregate_id | BINARY(16) |
| aggregate_type | VARCHAR |
| event_type | VARCHAR |
| payload | JSON |
| published | BOOLEAN |
| published_at | TIMESTAMP |
| version | INT |

### 9.3. Master-Slave Routing

**Class:** `RoutingDataSource.java`

| Điều kiện | Route |
|---|---|
| `@Transactional(readOnly=true)` + active transaction | Slave (:3308) |
| Mặc định | Master (:3307) |

---

## 10. Kiến trúc hạ tầng

### 10.1. Docker services (local)

Khởi động: `scripts/start-infra.ps1` hoặc `docker/start.sh`

| Service | Port | Thư mục |
|---|---|---|
| MySQL Master | 3307 | `docker/mysql/` |
| MySQL Slave | 3308 | `docker/mysql/` |
| Redis | 6379 (password: redis123) | `docker/redis/` |
| Kafka | 9092 | `docker/kafka/` |
| Kafka UI | 8080 | |
| Adminer | 8089 | |

Database: `millions_transaction`, root password: `FormosVN@123`

### 10.2. Thread pools (`AsyncConfig.java`)

| Executor | core / max | queue |
|---|---|---|
| paymentProcessingExecutor | 20 / 50 | 1000 |
| outboxRelayExecutor | 10 / 25 | 500 |
| webhookExecutor | 5 / 20 | 500 |
| analyticsExecutor | 3 / 10 | 200 |

---

## 11. Cấu hình ứng dụng

**File:** `src/main/resources/application.yml`

```yaml
server:
  port: 8888

spring:
  datasource:
    master:
      jdbc-url: jdbc:mysql://localhost:3307/millions_transaction
      hikari.maximum-pool-size: 60
    slave:
      jdbc-url: jdbc:mysql://localhost:3308/millions_transaction
      hikari.maximum-pool-size: 60
  data.redis:
    host: localhost
    port: 6379
    password: redis123
    timeout: 1000ms
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
      enable-idempotence: true
    consumer:
      group-id: payments-service
      enable-auto-commit: false
      max-poll-records: 100

outbox.relay.interval: 5000

app:
  security.authentication.jwt:
    token-validity-in-seconds: 604800
  payment:
    guard.timeout: 1000
    processing:
      batch-size: 50
      max-retries: 5
      retry-delay: 1000

stripe:
  secret-key: ${STRIPE_SECRET_KEY:sk_test_...}
  currency: usd
```

---

## 12. Monitoring & Health

| Endpoint | Mô tả |
|---|---|
| GET `/api/v1/health/detailed` | Kiểm tra Master DB, Slave DB, Redis, Kafka, outbox lag |
| GET `/api/v1/health/ready` | Readiness (K8s) |
| GET `/api/v1/health/live` | Liveness (K8s) |
| GET `/api/v1/health/metrics` | Sample payment metrics |
| GET `/actuator/health` | Spring Actuator |
| GET `/actuator/prometheus` | Prometheus metrics |

---

## 13. Ma trận triển khai

| Tính năng | Trạng thái | Ghi chú |
|---|---|---|
| Create payment + async auth | ✅ | simulatePayment luôn success |
| Get / List payments | ✅ | Chưa enforce merchant ownership on GET by ID |
| Capture / Refund | ✅ | DB-only, không gọi Stripe |
| Void payment | ❌ | Enum có, API chưa có |
| Idempotency | ✅ | Redis + MySQL |
| Ledger | ✅ | AUTHORIZATION, CAPTURE, REFUND |
| Outbox → Kafka | ✅ | Relay 5s |
| Webhook delivery | ✅ | Qua Kafka consumer |
| Webhook REST config | ⚠️ Stub | WebhookController mock |
| Analytics /payments | ✅ | Query DB + cache |
| Analytics /metrics | ⚠️ Mock | Hardcoded values |
| JWT Auth | ✅ | HS512, 7 ngày |
| Rate limiting | ❌ | |
| Stripe real integration | ⚠️ Partial | Chỉ simulate auth |
| Merchant data isolation | ⚠️ Partial | List có filter; GET by ID chưa |

---

**Tài liệu liên quan:**
- [Yêu cầu chức năng](./TÀI_LIỆU_YÊU_CẦU_CHỨC_NĂNG.md)
- [Yêu cầu nghiệp vụ BA](./BUSINESS_REQUIREMENTS_BA.md)
