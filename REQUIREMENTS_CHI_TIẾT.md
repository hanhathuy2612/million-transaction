# TÀI LIỆU YÊU CẦU CHI TIẾT
## HỆ THỐNG XỬ LÝ GIAO DỊCH THANH TOÁN

---

## 1. TỔNG QUAN DỰ ÁN

**Tên dự án:** Payment Processing System  
**Mã dự án:** MILLIONS-TRANSACTION  
**Phiên bản:** 1.0  
**Ngày tạo:** 2024  
**PM:** [Tên PM]  
**Tech Lead:** [Tên Tech Lead]  

### 1.1. MỤC TIÊU KINH DOANH
- Xây dựng hệ thống thanh toán có thể xử lý **1,000,000+ giao dịch/ngày**
- Độ trễ API < 500ms cho 95% requests
- Uptime 99.9%
- Hỗ trợ 24/7 với SLA 4 giờ

### 1.2. ĐỐI TƯỢNG NGƯỜI DÙNG
- **Primary:** Merchants (thương gia) tích hợp API
- **Secondary:** Internal admin users
- **Tertiary:** End customers (qua merchant)

---

## 2. YÊU CẦU CHỨC NĂNG CHI TIẾT

### 2.1. QUẢN LÝ GIAO DỊCH THANH TOÁN

#### 2.1.1. TẠO GIAO DỊCH (POST /api/v1/payments)

**Mô tả:** Tạo giao dịch thanh toán mới với xử lý bất đồng bộ

**Input Requirements:**
```json
{
  "merchantId": "string (required, max 100 chars)",
  "amount": "decimal (required, min: 0.01, max: 999999999.99, precision: 2)",
  "currency": "string (required, ISO 4217, supported: USD,EUR,GBP,JPY)",
  "paymentMethodId": "string (required, max 100 chars)",
  "description": "string (optional, max 500 chars)",
  "referenceId": "string (optional, max 100 chars, unique per merchant)"
}
```

**Headers Required:**
- `Merchant-Id`: string (required)
- `Idempotency-Key`: string (required, max 100 chars, unique per merchant)
- `Authorization`: Bearer token (required)
- `Content-Type`: application/json

**Business Rules:**
1. **Validation Rules:**
   - Amount phải > 0.01
   - Currency phải thuộc danh sách hỗ trợ
   - JPY không hỗ trợ decimal places
   - Merchant ID trong header phải khớp với request body

2. **Idempotency Rules:**
   - Cùng idempotency key trong 24h sẽ trả về kết quả cũ
   - Cache trong Redis với TTL 24h
   - Key format: `idempotency:{merchantId}:{idempotencyKey}`

3. **Processing Rules:**
   - Tạo payment với status PENDING
   - Enqueue vào Kafka topic `payment-events`
   - Trả về response ngay lập tức (< 200ms)
   - Xử lý authorization bất đồng bộ

**Output:**
```json
{
  "id": "uuid",
  "merchantId": "string",
  "amount": "decimal",
  "currency": "string", 
  "status": "PENDING",
  "paymentMethodId": "string",
  "description": "string",
  "referenceId": "string",
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

**Acceptance Criteria:**
- [ ] API response time < 200ms cho 95% requests
- [ ] Hỗ trợ 1000 concurrent requests
- [ ] Idempotency hoạt động chính xác
- [ ] Validation errors trả về 400 với message rõ ràng
- [ ] Authorization errors trả về 401
- [ ] Duplicate idempotency key trả về 409 với data cũ

**Error Codes:**
- `400`: Invalid input data
- `401`: Unauthorized (invalid/missing token)
- `409`: Idempotency key conflict
- `429`: Rate limit exceeded
- `500`: Internal server error

---

#### 2.1.2. LẤY THÔNG TIN GIAO DỊCH (GET /api/v1/payments/{id})

**Mô tả:** Lấy thông tin chi tiết giao dịch

**Input:**
- Path parameter: `id` (UUID)
- Header: `Merchant-Id` (required)
- Header: `Authorization` (required)

**Business Rules:**
1. Chỉ merchant sở hữu giao dịch mới được xem
2. Response bao gồm tất cả thông tin giao dịch
3. Cache response 5 phút trong Redis

**Output:** Same as create payment + additional fields:
```json
{
  // ... all fields from create payment
  "capturedAmount": "decimal",
  "refundedAmount": "decimal", 
  "authorizedAt": "ISO8601",
  "capturedAt": "ISO8601",
  "failedAt": "ISO8601",
  "failureReason": "string",
  "processorTransactionId": "string",
  "processorName": "string"
}
```

**Acceptance Criteria:**
- [ ] Response time < 100ms cho 95% requests
- [ ] Cache hit ratio > 80%
- [ ] 404 khi giao dịch không tồn tại
- [ ] 403 khi merchant không có quyền xem

---

#### 2.1.3. THANH TOÁN GIAO DỊCH (POST /api/v1/payments/{id}/capture)

**Mô tả:** Chuyển giao dịch từ AUTHORIZED sang CAPTURED

**Input:**
```json
{
  "amount": "decimal (optional, default: full amount, min: 0.01)",
  "description": "string (optional, max 500 chars)"
}
```

**Business Rules:**
1. **Status Validation:**
   - Giao dịch phải ở trạng thái AUTHORIZED
   - capturedAmount + capture amount ≤ total amount

2. **Amount Rules:**
   - Nếu không specify amount → capture toàn bộ
   - Nếu specify amount → capture partial
   - Không được capture quá số tiền còn lại

3. **Processing:**
   - Cập nhật capturedAmount
   - Cập nhật status (CAPTURED hoặc PARTIALLY_REFUNDED)
   - Ghi ledger entry
   - Publish event

**Acceptance Criteria:**
- [ ] Chỉ capture được giao dịch AUTHORIZED
- [ ] Không capture quá số tiền còn lại
- [ ] Ledger được ghi chính xác
- [ ] Event được publish
- [ ] Response time < 300ms

**Error Codes:**
- `400`: Invalid amount hoặc status không hợp lệ
- `404`: Payment không tồn tại
- `409`: Payment không thể capture

---

#### 2.1.4. HOÀN TIỀN (POST /api/v1/payments/{id}/refund)

**Mô tả:** Hoàn tiền cho giao dịch đã capture

**Input:**
```json
{
  "amount": "decimal (optional, default: full captured amount)",
  "reason": "string (optional, max 500 chars)",
  "description": "string (optional, max 500 chars)"
}
```

**Business Rules:**
1. **Status Validation:**
   - Giao dịch phải CAPTURED hoặc PARTIALLY_REFUNDED
   - refundedAmount + refund amount ≤ capturedAmount

2. **Processing:**
   - Cập nhật refundedAmount
   - Cập nhật status (REFUNDED hoặc PARTIALLY_REFUNDED)
   - Ghi ledger entry
   - Publish event

**Acceptance Criteria:**
- [ ] Chỉ refund được giao dịch đã capture
- [ ] Không refund quá số tiền đã capture
- [ ] Ledger được ghi chính xác
- [ ] Event được publish

---

#### 2.1.5. HỦY GIAO DỊCH (POST /api/v1/payments/{id}/void)

**Mô tả:** Hủy giao dịch chưa capture

**Input:**
```json
{
  "reason": "string (optional, max 500 chars)"
}
```

**Business Rules:**
1. Chỉ hủy được giao dịch AUTHORIZED
2. Cập nhật status thành CANCELLED
3. Ghi ledger entry
4. Publish event

---

### 2.2. HỆ THỐNG SỔ CÁI (LEDGER)

#### 2.2.1. YÊU CẦU LEDGER

**Mô tả:** Ghi lại mọi thay đổi số tiền trong giao dịch

**Ledger Entry Types:**
- `AUTHORIZATION`: Ghi nhận số tiền được ủy quyền
- `CAPTURE`: Ghi nhận số tiền đã thu
- `REFUND`: Ghi nhận số tiền đã hoàn
- `VOID`: Ghi nhận việc hủy giao dịch
- `FEE`: Ghi nhận phí giao dịch
- `CHARGEBACK`: Ghi nhận giao dịch bị từ chối

**Database Schema:**
```sql
CREATE TABLE payment_ledger (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    entry_type ENUM('AUTHORIZATION','CAPTURE','REFUND','VOID','FEE','CHARGEBACK') NOT NULL,
    delta_amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(500),
    reference_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_payment_id (payment_id),
    INDEX idx_occurred_at (occurred_at)
);
```

**Business Rules:**
1. Mỗi thay đổi số tiền phải ghi 1 ledger entry
2. balance_after = balance_before + delta_amount
3. Ledger entries immutable (không được sửa/xóa)
4. Sequential numbering (seq) đảm bảo thứ tự

**Acceptance Criteria:**
- [ ] Mọi thay đổi số tiền đều có ledger entry
- [ ] Balance calculation chính xác
- [ ] Ledger entries không thể sửa đổi
- [ ] Performance: insert ledger < 10ms

---

### 2.3. TÍNH NĂNG IDEMPOTENCY

#### 2.3.1. YÊU CẦU IDEMPOTENCY

**Mô tả:** Đảm bảo cùng một request không được xử lý nhiều lần

**Implementation:**
1. **Cache Key Format:** `idempotency:{merchantId}:{idempotencyKey}`
2. **TTL:** 24 hours
3. **Storage:** Redis
4. **Scope:** Per merchant (khác merchant có thể dùng cùng key)

**Cache Value:**
```json
{
  "requestHash": "sha256_hash_of_request_body",
  "response": "original_response",
  "createdAt": "ISO8601",
  "expiresAt": "ISO8601"
}
```

**Business Rules:**
1. **Request Matching:**
   - So sánh request body hash
   - Nếu khác → 409 Conflict
   - Nếu giống → trả về cached response

2. **Expiration:**
   - TTL 24h từ lần request đầu tiên
   - Auto cleanup expired keys

**Acceptance Criteria:**
- [ ] Cùng request + idempotency key → cached response
- [ ] Khác request + cùng idempotency key → 409 error
- [ ] TTL 24h hoạt động chính xác
- [ ] Redis failure không ảnh hưởng business logic

---

### 2.4. HỆ THỐNG WEBHOOK

#### 2.4.1. CẤU HÌNH WEBHOOK (POST /api/v1/webhooks)

**Mô tả:** Merchant cấu hình endpoint nhận thông báo

**Input:**
```json
{
  "merchantId": "string (required)",
  "webhookUrl": "string (required, valid URL, HTTPS only)",
  "events": ["payment.authorized", "payment.captured", "payment.refunded", "payment.failed", "payment.cancelled"],
  "webhookSecret": "string (required, min 16 chars)",
  "isActive": "boolean (default: true)"
}
```

**Business Rules:**
1. **URL Validation:**
   - Phải là HTTPS
   - Phải accessible (health check)
   - Max 2000 chars

2. **Events:**
   - Chỉ gửi events được subscribe
   - Events phải thuộc danh sách hỗ trợ

3. **Security:**
   - HMAC SHA256 signature
   - Header: `X-Webhook-Signature: sha256=<signature>`
   - Header: `X-Webhook-Event-Type: <event_type>`

**Database Schema:**
```sql
CREATE TABLE webhooks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id VARCHAR(100) NOT NULL,
    webhook_url VARCHAR(2000) NOT NULL,
    events JSON NOT NULL,
    webhook_secret VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_is_active (is_active)
);
```

**Acceptance Criteria:**
- [ ] Chỉ accept HTTPS URLs
- [ ] Health check URL trước khi save
- [ ] Events validation
- [ ] Secret min 16 chars
- [ ] One active webhook per merchant

---

#### 2.4.2. GỬI WEBHOOK

**Mô tả:** Gửi thông báo khi có sự kiện payment

**Webhook Payload:**
```json
{
  "id": "uuid",
  "eventType": "payment.authorized|captured|refunded|failed|cancelled",
  "data": {
    "paymentId": "uuid",
    "merchantId": "string",
    "amount": "decimal",
    "currency": "string",
    "status": "string",
    "capturedAmount": "decimal",
    "refundedAmount": "decimal",
    "processorTransactionId": "string",
    "failureReason": "string"
  },
  "timestamp": "ISO8601"
}
```

**Delivery Rules:**
1. **Retry Logic:**
   - Max 3 retries
   - Backoff: 1s, 5s, 30s
   - Timeout: 30s per request

2. **Success Criteria:**
   - HTTP 2xx response
   - Response time < 30s

3. **Failure Handling:**
   - Log failed webhooks
   - Dead letter queue sau 3 retries
   - Alert monitoring

**Acceptance Criteria:**
- [ ] HMAC signature chính xác
- [ ] Retry logic hoạt động
- [ ] Timeout 30s
- [ ] Failed webhooks được log
- [ ] Performance: webhook delivery < 1s average

---

### 2.5. HỆ THỐNG ANALYTICS

#### 2.5.1. THỐNG KÊ GIAO DỊCH (GET /api/v1/analytics)

**Mô tả:** Lấy thống kê giao dịch theo merchant và khoảng thời gian

**Input:**
- Query params:
  - `merchantId`: string (required)
  - `startDate`: ISO8601 (required)
  - `endDate`: ISO8601 (required)
  - `currency`: string (optional, filter by currency)

**Business Rules:**
1. **Date Range:**
   - Max 90 days range
   - End date >= start date
   - Start date >= 1 year ago

2. **Data Source:**
   - Read từ slave database
   - Cache 5 phút

**Output:**
```json
{
  "totalVolume": "decimal",
  "capturedVolume": "decimal", 
  "totalCount": "integer",
  "capturedCount": "integer",
  "failedCount": "integer",
  "successRate": "decimal (percentage)",
  "statusBreakdown": {
    "AUTHORIZED": "integer",
    "CAPTURED": "integer", 
    "FAILED": "integer",
    "CANCELLED": "integer"
  },
  "averageTransactionAmount": "decimal",
  "currency": "string",
  "period": {
    "startDate": "ISO8601",
    "endDate": "ISO8601"
  }
}
```

**Acceptance Criteria:**
- [ ] Response time < 200ms
- [ ] Cache 5 phút hoạt động
- [ ] Date range validation
- [ ] Data accuracy 100%
- [ ] Support 90 days max range

---

### 2.6. HỆ THỐNG XÁC THỰC

#### 2.6.1. ĐĂNG KÝ (POST /api/v1/auth/register)

**Input:**
```json
{
  "email": "string (required, valid email, unique)",
  "password": "string (required, min 8 chars, max 100 chars)",
  "firstName": "string (required, max 100 chars)",
  "lastName": "string (required, max 100 chars)", 
  "phoneNumber": "string (optional, valid phone format)"
}
```

**Business Rules:**
1. **Password Requirements:**
   - Min 8 characters
   - Max 100 characters
   - BCrypt hash với salt rounds 12

2. **Email Validation:**
   - Valid email format
   - Unique trong hệ thống
   - Case insensitive

**Acceptance Criteria:**
- [ ] Email uniqueness validation
- [ ] Password strength validation
- [ ] BCrypt hash với salt rounds 12
- [ ] Response time < 300ms

---

#### 2.6.2. ĐĂNG NHẬP (POST /api/v1/auth/login)

**Input:**
```json
{
  "username": "string (email, required)",
  "password": "string (required)"
}
```

**Business Rules:**
1. **Authentication:**
   - Email + password validation
   - BCrypt password verification

2. **JWT Token:**
   - Expiry: 7 days
   - Algorithm: HS512
   - Claims: sub (email), authorities, iat, exp

**Output:**
```json
{
  "token": "jwt_token_string",
  "expiresAt": "ISO8601",
  "user": {
    "id": "uuid",
    "email": "string",
    "firstName": "string",
    "lastName": "string",
    "phoneNumber": "string"
  }
}
```

**Acceptance Criteria:**
- [ ] Invalid credentials → 401
- [ ] JWT token valid 7 days
- [ ] Token contains correct claims
- [ ] Response time < 200ms

---

## 3. YÊU CẦU PHI CHỨC NĂNG CHI TIẾT

### 3.1. HIỆU SUẤT

#### 3.1.1. THÔNG LƯỢNG
- **Target:** 1,000,000 transactions/day
- **Peak:** 50,000 transactions/hour (2x average)
- **Concurrent:** 1,000 concurrent requests
- **Burst:** 10,000 requests/minute

#### 3.1.2. ĐỘ TRỄ
- **API Response:**
  - Create Payment: < 200ms (95th percentile)
  - Get Payment: < 100ms (95th percentile)
  - Capture/Refund: < 300ms (95th percentile)
  - Analytics: < 200ms (95th percentile)

#### 3.1.3. THREAD POOL CONFIGURATION
```yaml
async:
  webhookExecutor:
    corePoolSize: 5
    maxPoolSize: 20
    queueCapacity: 500
  analyticsExecutor:
    corePoolSize: 3
    maxPoolSize: 10
    queueCapacity: 200
  paymentProcessingExecutor:
    corePoolSize: 20
    maxPoolSize: 50
    queueCapacity: 1000
  outboxRelayExecutor:
    corePoolSize: 10
    maxPoolSize: 25
    queueCapacity: 500
```

### 3.2. ĐỘ TIN CẬY

#### 3.2.1. AVAILABILITY
- **Target:** 99.9% uptime (8.76 hours downtime/year)
- **SLA:** 4 hours response time cho incidents
- **Monitoring:** 24/7 monitoring với alerts

#### 3.2.2. DATA CONSISTENCY
- **ACID Compliance:** Tất cả payment operations
- **Master-Slave:** Read từ slave, write vào master
- **Replication Lag:** < 1 second

#### 3.2.3. BACKUP & RECOVERY
- **Database Backup:** Daily full backup + hourly incremental
- **Recovery Time:** < 4 hours RTO, < 1 hour RPO
- **Redis Backup:** Daily snapshot

### 3.3. BẢO MẬT

#### 3.3.1. AUTHENTICATION
- **JWT Token:**
  - Algorithm: HS512
  - Expiry: 7 days
  - Secret: 512-bit random key
  - Claims: sub, authorities, iat, exp

#### 3.3.2. AUTHORIZATION
- **RBAC:** Role-based access control
- **API Protection:** All payment endpoints require authentication
- **Merchant Isolation:** Merchants chỉ access được data của mình

#### 3.3.3. DATA PROTECTION
- **Encryption:**
  - Password: BCrypt (salt rounds 12)
  - Webhook Secret: BCrypt
  - JWT Secret: 512-bit random
- **Network:** HTTPS only
- **Headers:** Security headers (HSTS, CSP, etc.)

### 3.4. SCALABILITY

#### 3.4.1. DATABASE
```yaml
mysql:
  master:
    connectionPool:
      maximumPoolSize: 60
      minimumIdle: 20
      connectionTimeout: 20000ms
      idleTimeout: 300000ms
      maxLifetime: 900000ms
  slave:
    connectionPool:
      maximumPoolSize: 60
      minimumIdle: 20
```

#### 3.4.2. CACHE (REDIS)
```yaml
redis:
  timeout: 1000ms
  jedis:
    pool:
      maxActive: 50
      maxIdle: 20
      minIdle: 10
```

#### 3.4.3. MESSAGE QUEUE (KAFKA)
```yaml
kafka:
  producer:
    acks: all
    retries: 3
    batchSize: 16384
    lingerMs: 5
    enableIdempotence: true
  consumer:
    groupId: payments-service
    autoOffsetReset: earliest
    enableAutoCommit: false
    maxPollRecords: 100
```

---

## 4. KIẾN TRÚC HỆ THỐNG

### 4.1. DATABASE ARCHITECTURE

#### 4.1.1. MASTER-SLAVE SETUP
```yaml
mysql-master:
  port: 3307
  config:
    serverId: 1
    innodbBufferPoolSize: 1G
    maxConnections: 300
    gtidMode: ON
    binlogFormat: ROW

mysql-slave:
  port: 3308  
  config:
    serverId: 2
    innodbBufferPoolSize: 1G
    maxConnections: 300
    gtidMode: ON
    readOnly: ON
```

#### 4.1.2. ROUTING LOGIC
- **Write Operations:** Always route to master
- **Read Operations:** Route to slave if `@Transactional(readOnly = true)`
- **Default:** Master (for safety)

### 4.2. EVENT-DRIVEN ARCHITECTURE

#### 4.2.1. KAFKA TOPICS
- `payment-events`: Payment lifecycle events
- `webhook-events`: Webhook delivery events
- `analytics-events`: Analytics data events

#### 4.2.2. OUTBOX PATTERN
```sql
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_data JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    
    INDEX idx_processed_at (processed_at)
);
```

### 4.3. CACHING STRATEGY

#### 4.3.1. REDIS USAGE
- **Idempotency Cache:** TTL 24h
- **Payment Cache:** TTL 5 minutes
- **Analytics Cache:** TTL 5 minutes
- **Session Cache:** TTL 7 days

---

## 5. API SPECIFICATIONS

### 5.1. OPENAPI 3.0 SPECIFICATION

**Base URL:** `https://api.payments.com/v1`  
**Authentication:** Bearer Token (JWT)  
**Content-Type:** `application/json`  
**Rate Limiting:** 1000 requests/minute per merchant

### 5.2. ERROR RESPONSE FORMAT

**Standard Error Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request", 
  "message": "Validation failed: amount must be greater than 0.01",
  "path": "/api/v1/payments",
  "requestId": "uuid"
}
```

**Error Codes:**
- `400`: Bad Request (validation errors)
- `401`: Unauthorized (invalid/missing token)
- `403`: Forbidden (insufficient permissions)
- `404`: Not Found (resource not found)
- `409`: Conflict (idempotency key conflict)
- `429`: Too Many Requests (rate limit exceeded)
- `500`: Internal Server Error
- `502`: Bad Gateway (external service error)
- `503`: Service Unavailable (maintenance)

### 5.3. PAGINATION

**Standard Pagination:**
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

## 6. MONITORING & OBSERVABILITY

### 6.1. HEALTH CHECKS

**Endpoint:** `/api/v1/health`

**Response:**
```json
{
  "status": "UP|DOWN",
  "components": {
    "database": {
      "status": "UP",
      "details": {
        "master": "UP",
        "slave": "UP"
      }
    },
    "redis": {
      "status": "UP"
    },
    "kafka": {
      "status": "UP"
    }
  }
}
```

### 6.2. METRICS

#### 6.2.1. BUSINESS METRICS
- Transaction volume (per hour/day)
- Success rate (per hour/day)
- Average transaction amount
- Top merchants by volume
- Failed transaction reasons

#### 6.2.2. TECHNICAL METRICS
- API response times (p50, p95, p99)
- Database connection pool usage
- Redis hit/miss ratio
- Kafka consumer lag
- Error rates by endpoint

### 6.3. LOGGING

#### 6.3.1. LOG FORMAT
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "logger": "com.hnh.example.PaymentService",
  "message": "Payment created successfully",
  "requestId": "uuid",
  "merchantId": "merchant_123",
  "paymentId": "payment_uuid",
  "duration": 150
}
```

#### 6.3.2. LOG LEVELS
- **ERROR:** System errors, payment failures
- **WARN:** Performance issues, retry attempts
- **INFO:** Business events, API calls
- **DEBUG:** Detailed execution flow

---

## 7. DEPLOYMENT & INFRASTRUCTURE

### 7.1. DOCKER CONFIGURATION

#### 7.1.1. APPLICATION CONTAINER
```dockerfile
FROM openjdk:17-jre-slim
COPY target/millions-transaction.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

#### 7.1.2. DOCKER COMPOSE
```yaml
version: "3.8"
services:
  app:
    image: millions-transaction:latest
    ports:
      - "8888:8888"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SPRING_DATASOURCE_MASTER_JDBC_URL=jdbc:mysql://mysql-master:3306/millions_transaction
      - SPRING_DATASOURCE_SLAVE_JDBC_URL=jdbc:mysql://mysql-slave:3306/millions_transaction
    depends_on:
      - mysql-master
      - mysql-slave
      - redis
      - kafka
```

### 7.2. ENVIRONMENT CONFIGURATION

#### 7.2.1. DEVELOPMENT
```yaml
spring:
  profiles: development
  datasource:
    master:
      jdbc-url: jdbc:mysql://localhost:3307/millions_transaction
    slave:
      jdbc-url: jdbc:mysql://localhost:3308/millions_transaction
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

#### 7.2.2. PRODUCTION
```yaml
spring:
  profiles: production
  datasource:
    master:
      jdbc-url: ${DB_MASTER_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
    slave:
      jdbc-url: ${DB_SLAVE_URL}
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      password: ${REDIS_PASSWORD}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
```

---

## 8. TESTING STRATEGY

### 8.1. UNIT TESTING

#### 8.1.1. COVERAGE REQUIREMENTS
- **Minimum Coverage:** 80%
- **Critical Paths:** 95% (payment processing, ledger)
- **Business Logic:** 90%

#### 8.1.2. TEST CATEGORIES
- **Unit Tests:** Individual methods/classes
- **Integration Tests:** Database, Redis, Kafka
- **Contract Tests:** API contracts
- **Performance Tests:** Load testing

### 8.2. INTEGRATION TESTING

#### 8.2.1. TEST CONTAINERS
```yaml
testcontainers:
  mysql:
    image: mysql:8.0
    database: test_db
  redis:
    image: redis:7-alpine
  kafka:
    image: confluentinc/cp-kafka:latest
```

#### 8.2.2. API TESTING
- **Happy Path:** Successful payment flows
- **Error Cases:** Invalid inputs, failures
- **Edge Cases:** Boundary conditions
- **Security:** Authentication, authorization

### 8.3. PERFORMANCE TESTING

#### 8.3.1. LOAD TESTING
- **Tools:** JMeter, Gatling
- **Scenarios:**
  - Normal load: 1000 concurrent users
  - Peak load: 5000 concurrent users
  - Stress test: 10000 concurrent users

#### 8.3.2. PERFORMANCE TARGETS
- **Throughput:** 1000 TPS
- **Response Time:** < 500ms (95th percentile)
- **Error Rate:** < 0.1%

---

## 9. SECURITY REQUIREMENTS

### 9.1. AUTHENTICATION & AUTHORIZATION

#### 9.1.1. JWT CONFIGURATION
```yaml
jwt:
  secret: ${JWT_SECRET} # 512-bit random key
  expiration: 604800 # 7 days in seconds
  algorithm: HS512
```

#### 9.1.2. PASSWORD POLICY
- **Minimum Length:** 8 characters
- **Maximum Length:** 100 characters
- **Hash Algorithm:** BCrypt with salt rounds 12
- **Password History:** Not required (v1)

### 9.2. API SECURITY

#### 9.2.1. RATE LIMITING
- **Per Merchant:** 1000 requests/minute
- **Per IP:** 5000 requests/minute
- **Burst:** 200 requests/minute

#### 9.2.2. CORS CONFIGURATION
```yaml
cors:
  allowedOrigins: ["https://merchant.example.com"]
  allowedMethods: ["GET", "POST", "PUT", "DELETE"]
  allowedHeaders: ["Authorization", "Content-Type", "Merchant-Id", "Idempotency-Key"]
  maxAge: 3600
```

### 9.3. DATA PROTECTION

#### 9.3.1. ENCRYPTION
- **In Transit:** TLS 1.3
- **At Rest:** Database encryption
- **Sensitive Data:** BCrypt for passwords/secrets

#### 9.3.2. AUDIT LOGGING
- **Authentication Events:** Login, logout, token refresh
- **Payment Events:** Create, capture, refund, void
- **Admin Events:** User management, configuration changes
- **Retention:** 7 years (compliance requirement)

---

## 10. ACCEPTANCE CRITERIA SUMMARY

### 10.1. FUNCTIONAL ACCEPTANCE

#### 10.1.1. PAYMENT PROCESSING
- [ ] Create payment với validation đầy đủ
- [ ] Idempotency hoạt động chính xác
- [ ] Capture/Refund với business rules
- [ ] Ledger entries chính xác
- [ ] Status transitions đúng logic

#### 10.1.2. WEBHOOK SYSTEM
- [ ] Webhook configuration validation
- [ ] HMAC signature generation/verification
- [ ] Retry logic với exponential backoff
- [ ] Failed webhook handling

#### 10.1.3. ANALYTICS
- [ ] Real-time statistics calculation
- [ ] Date range validation
- [ ] Cache performance
- [ ] Data accuracy

### 10.2. NON-FUNCTIONAL ACCEPTANCE

#### 10.2.1. PERFORMANCE
- [ ] API response times meet SLA
- [ ] Throughput 1000 TPS
- [ ] Concurrent users 1000
- [ ] Database performance optimized

#### 10.2.2. RELIABILITY
- [ ] 99.9% uptime
- [ ] Master-slave failover
- [ ] Data consistency
- [ ] Error handling

#### 10.2.3. SECURITY
- [ ] JWT authentication working
- [ ] RBAC authorization
- [ ] Rate limiting active
- [ ] Audit logging complete

### 10.3. OPERATIONAL ACCEPTANCE

#### 10.3.1. MONITORING
- [ ] Health checks working
- [ ] Metrics collection
- [ ] Alerting configured
- [ ] Log aggregation

#### 10.3.2. DEPLOYMENT
- [ ] Docker containers working
- [ ] Environment configurations
- [ ] Database migrations
- [ ] CI/CD pipeline

---

## 11. IMPLEMENTATION TIMELINE

### 11.1. SPRINT PLANNING

#### Sprint 1 (2 weeks): Core Payment Processing
- Payment creation API
- Basic validation
- Database setup
- Unit tests

#### Sprint 2 (2 weeks): Payment Operations
- Capture/Refund/Void APIs
- Ledger implementation
- Integration tests

#### Sprint 3 (2 weeks): Idempotency & Caching
- Redis integration
- Idempotency implementation
- Performance optimization

#### Sprint 4 (2 weeks): Webhook System
- Webhook configuration
- Event publishing
- Delivery mechanism

#### Sprint 5 (2 weeks): Analytics & Monitoring
- Analytics API
- Health checks
- Metrics collection

#### Sprint 6 (2 weeks): Security & Testing
- Authentication system
- Security hardening
- Performance testing

### 11.2. MILESTONES

- **Week 4:** Core payment processing complete
- **Week 8:** Full payment operations ready
- **Week 12:** Production-ready system

---

## 12. RISK ASSESSMENT

### 12.1. TECHNICAL RISKS

#### 12.1.1. HIGH RISK
- **Database Performance:** Master-slave lag, connection pool exhaustion
- **Kafka Reliability:** Message loss, consumer lag
- **Redis Failure:** Cache miss impact on performance

#### 12.1.2. MEDIUM RISK
- **Third-party Integration:** Stripe API failures
- **Network Issues:** External service timeouts
- **Memory Leaks:** Long-running processes

### 12.2. MITIGATION STRATEGIES

#### 12.2.1. DATABASE
- Connection pool monitoring
- Read replica failover
- Query optimization

#### 12.2.2. MESSAGE QUEUE
- Dead letter queues
- Consumer monitoring
- Message retention policies

#### 12.2.3. CACHE
- Cache warming strategies
- Fallback mechanisms
- Circuit breakers

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Next Review:** 2024  
**Approved By:** [PM Name], [Tech Lead Name]  
**Status:** Ready for Development
