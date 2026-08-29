# Tài liệu yêu cầu chức năng
## Hệ thống xử lý giao dịch thanh toán (Million Transaction)

| Thuộc tính | Giá trị |
|---|---|
| Mã dự án | MILLIONS-TRANSACTION |
| Phiên bản tài liệu | 2.0 |
| Cập nhật | 2026-08-29 |
| Trạng thái | Đồng bộ với source code hiện tại |
| Tech stack | Spring Boot 3.4, Java 17, MySQL 8, Redis, Kafka |

---

## 1. Tổng quan

### 1.1. Mục đích

Hệ thống cung cấp REST API cho merchant tạo và quản lý giao dịch thanh toán, với xử lý authorization bất đồng bộ qua hàng đợi Redis, phát sự kiện qua Kafka (Transactional Outbox), và hỗ trợ đọc/ghi tách biệt trên MySQL Master-Slave.

### 1.2. Đối tượng sử dụng

| Vai trò | Mô tả |
|---|---|
| Merchant / Developer | Tích hợp API thanh toán vào hệ thống nội bộ |
| Admin / Internal user | Đăng nhập JWT, truy cập API được bảo vệ |
| End customer | Không gọi API trực tiếp; thanh toán qua merchant |

### 1.3. Phạm vi triển khai hiện tại

| Đã triển khai | Chưa triển khai / stub |
|---|---|
| Tạo, tra cứu, liệt kê giao dịch | API Void (hủy giao dịch AUTHORIZED) |
| Capture / Refund đồng bộ | Gọi Stripe thực tế cho capture/refund |
| Idempotency (Redis + MySQL) | Rate limiting |
| Sổ cái (ledger) | Phân quyền merchant theo payment ID |
| Outbox → Kafka → Webhook delivery | WebhookController (REST cấu hình webhook) |
| Analytics từ DB (`/analytics/payments`) | Analytics metrics thực (`/analytics/metrics` trả mock) |
| JWT auth (login/register) | Đăng ký merchant tự phục vụ, xác thực email |
| Health check chi tiết | Xuất báo cáo Excel/PDF |

---

## 2. Kiến trúc xử lý giao dịch

```
Client (JWT)
    │
    ▼
PaymentController
    ├─ IdempotencyService (Redis → MySQL)
    ├─ PaymentGuardService (giới hạn ghi đồng thời)
    ├─ PaymentProcessingService.createNewPaymentPhase1 → PENDING + Outbox
    └─ PaymentQueueService → Redis list "payment:queue"
              │
              ▼ (BatchPaymentProcessor, mỗi 1 giây)
    PaymentProcessingService.processPaymentAsync
    └─ StripePaymentProcessorService.simulatePayment → AUTHORIZED / FAILED
              │
              ▼ (OutboxRelayService, mỗi 5 giây)
    Kafka topic "payments.events.v1"
              │
              ├─ WebhookService.sendWebhookAsync (HMAC, retry 3 lần)
              └─ AnalyticsService.recordPaymentEvent (log metrics)
```

---

## 3. Yêu cầu chức năng

### 3.1. Quản lý giao dịch thanh toán

#### 3.1.1. Tạo giao dịch — `POST /api/v1/payments`

**Headers bắt buộc:**
- `Authorization: Bearer <JWT>`
- `X-Merchant-ID`: phải khớp với `merchantId` trong body
- `Idempotency-Key`: bắt buộc

**Body (`PaymentRequest`):**

| Trường | Bắt buộc | Quy tắc |
|---|---|---|
| merchantId | Có | Khớp header `X-Merchant-ID` |
| amount | Có | ≥ 0.01; tối đa 10 chữ số nguyên + 2 thập phân |
| currency | Có | ISO 4217: `USD`, `EUR`, `GBP`, `JPY` |
| paymentMethodId | Có | — |
| description | Không | Tối đa 500 ký tự |
| referenceId | Không | Tối đa 100 ký tự |

**Quy trình:**
1. Kiểm tra idempotency (Redis trước, MySQL sau).
2. Tạo bản ghi `PENDING`, ghi outbox `payment.pending`.
3. Đưa vào hàng đợi Redis để authorization bất đồng bộ.
4. Trả về `200 OK` với `PaymentResponse` (trạng thái `PENDING`).

**Lưu ý:** Authorization thực hiện bởi worker nền; hiện tại `simulatePayment()` luôn thành công.

#### 3.1.2. Tra cứu giao dịch — `GET /api/v1/payments/{paymentId}`

- Trả về đầy đủ thông tin giao dịch.
- Đọc từ slave DB khi trong transaction read-only.
- **Hạn chế hiện tại:** chưa kiểm tra merchant sở hữu giao dịch.

#### 3.1.3. Liệt kê giao dịch — `GET /api/v1/payments`

- Header: `X-Merchant-ID`
- Hỗ trợ phân trang Spring (`page`, `size`, `sort`; mặc định size=20).
- Response headers: `X-Total-Count`, `X-Total-Pages`, `X-Current-Page`, `X-Page-Size`.

#### 3.1.4. Capture — `POST /api/v1/payments/{paymentId}/capture`

| Điều kiện | Chi tiết |
|---|---|
| Trạng thái | `AUTHORIZED` và `capturedAmount < amount` |
| Body | `amount` (bắt buộc), `description`, `referenceId` |
| Idempotency | `Idempotency-Key` tùy chọn |

**Hành vi:**
- Cộng dồn `capturedAmount`.
- Chỉ chuyển sang `CAPTURED` khi capture đủ toàn bộ `amount`; capture một phần giữ trạng thái `AUTHORIZED`.
- Ghi ledger `CAPTURE`, publish outbox `payment.captured`.
- **Không gọi Stripe** trong code hiện tại.

#### 3.1.5. Hoàn tiền — `POST /api/v1/payments/{paymentId}/refunds`

| Điều kiện | Chi tiết |
|---|---|
| Trạng thái | `CAPTURED` hoặc `PARTIALLY_REFUNDED` |
| Body | `amount` (bắt buộc), `reason`, `referenceId` |
| Idempotency | `Idempotency-Key` bắt buộc |

**Hành vi:**
- Cập nhật `refundedAmount`; `REFUNDED` nếu hoàn đủ, ngược lại `PARTIALLY_REFUNDED`.
- Ghi ledger `REFUND`, publish outbox `payment.refunded`.

#### 3.1.6. Void (hủy giao dịch) — *Chưa triển khai*

Enum `CANCELLED` và ledger type `VOID` đã có trong domain, nhưng **chưa có REST endpoint**. Đây là tính năng dự kiến.

---

### 3.2. Máy trạng thái giao dịch

```
PENDING ──(authorization thành công)──► AUTHORIZED
PENDING ──(authorization thất bại)────► FAILED

AUTHORIZED ──(capture đủ amount)──────► CAPTURED
AUTHORIZED ──(capture một phần)───────► AUTHORIZED  (giữ nguyên)

CAPTURED ──(refund một phần)──────────► PARTIALLY_REFUNDED
CAPTURED ──(refund toàn bộ)───────────► REFUNDED
PARTIALLY_REFUNDED ──(refund tiếp)────► REFUNDED | PARTIALLY_REFUNDED
```

| Trạng thái | Ý nghĩa |
|---|---|
| PENDING | Vừa tạo, chờ authorization bất đồng bộ |
| AUTHORIZED | Đã ủy quyền, có thể capture |
| CAPTURED | Đã thu tiền, có thể refund |
| PARTIALLY_REFUNDED | Đã hoàn một phần |
| REFUNDED | Đã hoàn toàn bộ |
| FAILED | Authorization thất bại |
| CANCELLED | Đã hủy *(chưa có API)* |

---

### 3.3. Sổ cái (Ledger)

Mỗi thay đổi số tiền ghi một bản ghi bất biến trong `payment_ledger`:

| Loại | Khi nào ghi |
|---|---|
| AUTHORIZATION | Authorization thành công |
| CAPTURE | Capture thành công |
| REFUND | Refund thành công |
| VOID | Hủy giao dịch *(chưa kích hoạt)* |
| FEE, CHARGEBACK | Định nghĩa sẵn, chưa sử dụng |

---

### 3.4. Idempotency

| Thuộc tính | Giá trị thực tế |
|---|---|
| Key format Redis | `idempotency:{merchantId}:{idempotencyKey}` |
| Cache Redis TTL | 24 giờ |
| Lưu DB TTL | Tạo payment: 7 ngày; Capture: 3 ngày; Refund: 30 ngày |
| So khớp request | SHA-256 hash body |
| Trùng key + cùng body | Trả response đã cache (`200 OK`) |
| Trùng key + khác body | `400 Bad Request` |
| Trạng thái không hợp lệ (capture/refund) | `409 Conflict` |

---

### 3.5. Webhook

**Gửi thông báo (đã triển khai qua Kafka consumer):**
- `WebhookService` lắng nghe sự kiện từ Kafka, gửi HTTP POST tới URL webhook của merchant.
- Header: `X-Webhook-Signature: sha256=<Base64 HMAC>`, `X-Webhook-Event-Type`.
- Retry tối đa 3 lần, exponential backoff (2s, 4s).

**Sự kiện hỗ trợ:**
`payment.pending`, `payment.authorized`, `payment.captured`, `payment.refunded`, `payment.failed`

**Cấu hình webhook qua REST (`WebhookController`):** hiện là **stub/mock**, chưa kết nối `WebhookService`. Dữ liệu thật lưu qua `WebhookService.createWebhook()` (chưa expose REST).

---

### 3.6. Analytics

| Endpoint | Nguồn dữ liệu | Cache |
|---|---|---|
| `GET /api/v1/analytics/payments` | Query DB theo merchant + khoảng thời gian | Redis 15 phút |
| `GET /api/v1/analytics/metrics` | **Dữ liệu mock cố định** | Không |

Tham số: header `X-Merchant-ID`, query `fromDate`/`toDate` (ISO datetime), tối đa 90 ngày.

---

### 3.7. Xác thực và bảo mật

| Endpoint | Mô tả |
|---|---|
| `POST /api/v1/auth/register` | Tạo user (`email`, `password`, `firstName`, `lastName`, `phoneNumber`) |
| `POST /api/v1/auth/login` | Trả `TokenResponse { token }` + header `Authorization: Bearer` |

| Cấu hình | Giá trị |
|---|---|
| JWT algorithm | HS512 |
| Thời hạn token | 7 ngày (604800 giây) |
| Mật khẩu | BCrypt |
| Public endpoints | `/api/v1/auth/**`, `/api/v1/health/**`, Swagger, Actuator |

**Roles:** `ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS` (seed qua Liquibase).

---

### 3.8. Tích hợp Stripe

| Giai đoạn | Hành vi hiện tại |
|---|---|
| Authorization (async) | `StripePaymentProcessorService.simulatePayment()` — luôn thành công |
| Capture / Refund | Chỉ cập nhật DB, không gọi Stripe API |

Stripe SDK và config (`stripe.secret-key`) đã có sẵn cho mở rộng sau.

---

## 4. Danh sách API

| Method | Path | Auth | Ghi chú |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Không | |
| POST | `/api/v1/auth/register` | Không | |
| POST | `/api/v1/payments` | JWT | + `X-Merchant-ID`, `Idempotency-Key` |
| GET | `/api/v1/payments/{id}` | JWT | |
| GET | `/api/v1/payments` | JWT | + `X-Merchant-ID`, phân trang |
| POST | `/api/v1/payments/{id}/capture` | JWT | `Idempotency-Key` tùy chọn |
| POST | `/api/v1/payments/{id}/refunds` | JWT | + `Idempotency-Key` |
| GET | `/api/v1/analytics/payments` | JWT | + `X-Merchant-ID` |
| GET | `/api/v1/analytics/metrics` | JWT | Mock data |
| POST/GET/DELETE | `/api/v1/webhooks` | JWT | Stub |
| GET | `/api/v1/health/detailed` | Không | DB, Redis, Kafka, outbox |
| GET | `/api/v1/health/ready` | Không | Readiness probe |
| GET | `/api/v1/health/live` | Không | Liveness probe |
| GET | `/api/v1/health/metrics` | Không | Sample metrics |

Swagger UI: `http://localhost:8888/swagger-ui.html`

---

## 5. Yêu cầu phi chức năng

### 5.1. Hiệu suất (mục tiêu thiết kế)

| Chỉ số | Mục tiêu |
|---|---|
| Throughput | 1.000.000 giao dịch/ngày |
| API latency | < 500ms (P95) |
| Concurrent writes | Semaphore guard (PaymentGuardService) |

### 5.2. Hạ tầng dữ liệu

| Thành phần | Vai trò |
|---|---|
| MySQL Master (:3307) | Ghi + đọc mặc định |
| MySQL Slave (:3308) | Đọc khi `@Transactional(readOnly=true)` |
| Redis (:6379) | Idempotency, payment queue, analytics cache |
| Kafka (:9092) | Topic `payments.events.v1` |
| Liquibase | Quản lý schema (`db/changelog/`) |

### 5.3. Scheduled jobs

| Job | Chu kỳ |
|---|---|
| BatchPaymentProcessor | 1 giây |
| OutboxRelayService | 5 giây |
| Idempotency cleanup | 2:00 AM hàng ngày |

---

## 6. Mã lỗi HTTP

| Mã | Ý nghĩa |
|---|---|
| 400 | Validation lỗi, idempotency key trùng với body khác |
| 401 | JWT không hợp lệ / thiếu |
| 409 | Trạng thái giao dịch không cho phép thao tác |
| 500 | Lỗi hệ thống không mong đợi |

**Định dạng lỗi:**
```json
{
  "timestamp": "2026-08-29T14:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Merchant ID in header must match request body",
  "path": "/api/v1/payments"
}
```

---

## 7. Tài liệu liên quan

- [Yêu cầu chi tiết kỹ thuật](./REQUIREMENTS_CHI_TIẾT.md) — API schema, DB, cấu hình
- [Yêu cầu nghiệp vụ BA](./BUSINESS_REQUIREMENTS_BA.md) — User stories, quy tắc kinh doanh
