# TÀI LIỆU YÊU CẦU CHỨC NĂNG
## HỆ THỐNG XỬ LÝ GIAO DỊCH THANH TOÁN

### 1. TỔNG QUAN HỆ THỐNG

**Tên hệ thống:** Hệ thống xử lý giao dịch thanh toán hàng triệu giao dịch  
**Mục đích:** Xây dựng hệ thống thanh toán có khả năng xử lý hàng triệu giao dịch với độ tin cậy cao, bảo mật và khả năng mở rộng  
**Đối tượng sử dụng:** Các doanh nghiệp, thương gia cần tích hợp hệ thống thanh toán vào ứng dụng của mình

### 2. YÊU CẦU CHỨC NĂNG CHÍNH

#### 2.1. QUẢN LÝ GIAO DỊCH THANH TOÁN

**2.1.1. Tạo giao dịch thanh toán**
- **Mô tả:** Cho phép tạo giao dịch thanh toán mới với thông tin đầy đủ
- **Dữ liệu đầu vào:**
  - Mã thương gia (merchant_id)
  - Số tiền (amount)
  - Loại tiền tệ (currency) - hỗ trợ VND, USD, EUR
  - Phương thức thanh toán (payment_method_id)
  - Mô tả giao dịch (description)
  - Mã tham chiếu (reference_id)
- **Quy trình xử lý:**
  - Kiểm tra tính hợp lệ của dữ liệu đầu vào
  - Tạo giao dịch với trạng thái PENDING
  - Đưa vào hàng đợi xử lý bất đồng bộ
  - Trả về thông tin giao dịch với mã định danh duy nhất
- **Yêu cầu đặc biệt:**
  - Hỗ trợ tính năng idempotency (tránh tạo giao dịch trùng lặp)
  - Xử lý bất đồng bộ để đảm bảo hiệu suất cao

**2.1.2. Thanh toán (Capture)**
- **Mô tả:** Chuyển giao dịch từ trạng thái AUTHORIZED sang CAPTURED
- **Điều kiện:** Giao dịch phải ở trạng thái AUTHORIZED
- **Quy trình:**
  - Kiểm tra trạng thái giao dịch
  - Cập nhật số tiền đã thu (captured_amount)
  - Cập nhật trạng thái giao dịch
  - Ghi nhận vào sổ cái (ledger)

**2.1.3. Hoàn tiền (Refund)**
- **Mô tả:** Hoàn lại tiền cho khách hàng
- **Điều kiện:** Giao dịch phải ở trạng thái CAPTURED hoặc PARTIALLY_REFUNDED
- **Quy trình:**
  - Kiểm tra số tiền còn lại có thể hoàn
  - Cập nhật số tiền đã hoàn (refunded_amount)
  - Cập nhật trạng thái giao dịch
  - Ghi nhận vào sổ cái

**2.1.4. Hủy giao dịch (Void)**
- **Mô tả:** Hủy giao dịch chưa được thanh toán
- **Điều kiện:** Giao dịch phải ở trạng thái AUTHORIZED
- **Quy trình:**
  - Cập nhật trạng thái giao dịch thành CANCELLED
  - Ghi nhận vào sổ cái

#### 2.2. QUẢN LÝ TRẠNG THÁI GIAO DỊCH

**Các trạng thái giao dịch:**
- **PENDING:** Giao dịch mới được tạo, đang chờ xử lý
- **AUTHORIZED:** Giao dịch đã được ủy quyền, chờ thanh toán
- **CAPTURED:** Giao dịch đã được thanh toán thành công
- **PARTIALLY_REFUNDED:** Giao dịch đã được hoàn tiền một phần
- **REFUNDED:** Giao dịch đã được hoàn tiền toàn bộ
- **FAILED:** Giao dịch thất bại
- **CANCELLED:** Giao dịch đã bị hủy

#### 2.3. HỆ THỐNG SỔ CÁI (LEDGER)

**2.3.1. Ghi nhận giao dịch**
- **Mô tả:** Ghi lại mọi thay đổi về số tiền trong giao dịch
- **Các loại bút toán:**
  - AUTHORIZATION: Ghi nhận số tiền được ủy quyền
  - CAPTURE: Ghi nhận số tiền đã thu
  - REFUND: Ghi nhận số tiền đã hoàn
  - VOID: Ghi nhận việc hủy giao dịch
  - FEE: Ghi nhận phí giao dịch
  - CHARGEBACK: Ghi nhận giao dịch bị từ chối

**2.3.2. Tính toán số dư**
- **Mô tả:** Tự động tính toán số dư sau mỗi giao dịch
- **Quy tắc:** Số dư sau = Số dư trước + Số tiền thay đổi

#### 2.4. TÍNH NĂNG IDEMPOTENCY

**2.4.1. Tránh giao dịch trùng lặp**
- **Mô tả:** Đảm bảo cùng một yêu cầu không được xử lý nhiều lần
- **Cách thức:** Sử dụng idempotency key từ phía client
- **Lưu trữ:** Cache kết quả trong Redis với thời gian hết hạn

#### 2.5. HỆ THỐNG WEBHOOK

**2.5.1. Cấu hình webhook**
- **Mô tả:** Cho phép thương gia cấu hình endpoint nhận thông báo
- **Thông tin cấu hình:**
  - URL endpoint
  - Danh sách sự kiện cần nhận
  - Secret key để xác thực
  - Trạng thái hoạt động

**2.5.2. Gửi thông báo**
- **Các sự kiện được gửi:**
  - payment.authorized: Giao dịch được ủy quyền
  - payment.captured: Giao dịch được thanh toán
  - payment.refunded: Giao dịch được hoàn tiền
  - payment.failed: Giao dịch thất bại
  - payment.cancelled: Giao dịch bị hủy

**2.5.3. Bảo mật webhook**
- **Chữ ký HMAC:** Tạo chữ ký SHA256 để xác thực
- **Retry logic:** Tự động thử lại khi gửi thất bại
- **Timeout:** Giới hạn thời gian chờ phản hồi

#### 2.6. HỆ THỐNG PHÂN TÍCH (ANALYTICS)

**2.6.1. Thống kê giao dịch**
- **Tổng khối lượng giao dịch:** Tổng số tiền giao dịch
- **Tỷ lệ thành công:** Phần trăm giao dịch thành công
- **Phân tích theo trạng thái:** Số lượng giao dịch theo từng trạng thái
- **Trung bình giao dịch:** Số tiền trung bình mỗi giao dịch

**2.6.2. Báo cáo theo thời gian**
- **Mô tả:** Thống kê theo khoảng thời gian được chỉ định
- **Tham số:** Ngày bắt đầu, ngày kết thúc
- **Kết quả:** Dữ liệu thống kê chi tiết

#### 2.7. HỆ THỐNG XÁC THỰC VÀ BẢO MẬT

**2.7.1. Đăng ký người dùng**
- **Mô tả:** Cho phép tạo tài khoản mới
- **Thông tin yêu cầu:**
  - Email (duy nhất)
  - Mật khẩu (mã hóa BCrypt)
  - Họ tên
  - Số điện thoại

**2.7.2. Đăng nhập**
- **Mô tả:** Xác thực người dùng và cấp token
- **Phương thức:** JWT Token
- **Thời hạn:** 7 ngày (có thể cấu hình)

**2.7.3. Phân quyền**
- **Mô tả:** Kiểm soát quyền truy cập API
- **Cơ chế:** JWT với roles và authorities
- **Bảo vệ:** Tất cả endpoint thanh toán yêu cầu xác thực

#### 2.8. TÍCH HỢP THANH TOÁN

**2.8.1. Stripe Integration**
- **Mô tả:** Tích hợp với Stripe để xử lý thanh toán thực tế
- **Chức năng:**
  - Tạo Payment Intent
  - Xác nhận thanh toán
  - Hoàn tiền
  - Xử lý lỗi

### 3. YÊU CẦU PHI CHỨC NĂNG

#### 3.1. HIỆU SUẤT
- **Thông lượng:** Hỗ trợ hàng triệu giao dịch
- **Độ trễ:** Phản hồi API < 500ms cho 95% requests
- **Khả năng mở rộng:** Hỗ trợ horizontal scaling

#### 3.2. ĐỘ TIN CẬY
- **Availability:** 99.9% uptime
- **Data consistency:** Đảm bảo tính nhất quán dữ liệu
- **Transaction integrity:** ACID compliance

#### 3.3. BẢO MẬT
- **Mã hóa:** Tất cả dữ liệu nhạy cảm được mã hóa
- **Authentication:** JWT-based authentication
- **Authorization:** Role-based access control
- **Audit trail:** Ghi lại mọi thay đổi quan trọng

#### 3.4. KHẢ NĂNG MỞ RỘNG
- **Database:** Master-Slave replication
- **Caching:** Redis for session and idempotency
- **Message Queue:** Kafka for event processing
- **Load Balancing:** Hỗ trợ multiple instances

### 4. KIẾN TRÚC HỆ THỐNG

#### 4.1. KIẾN TRÚC DATABASE
- **Master Database:** Xử lý tất cả write operations
- **Slave Database:** Xử lý read operations cho analytics
- **Replication:** Real-time replication từ master sang slave

#### 4.2. KIẾN TRÚC MESSAGE QUEUE
- **Kafka:** Xử lý events bất đồng bộ
- **Topics:** Phân tách theo loại event
- **Consumer Groups:** Đảm bảo xử lý tuần tự

#### 4.3. KIẾN TRÚC CACHING
- **Redis:** Cache cho idempotency và session
- **TTL:** Time-to-live cho cache entries
- **Clustering:** Redis cluster cho high availability

### 5. API ENDPOINTS

#### 5.1. PAYMENT ENDPOINTS
- `POST /api/v1/payments` - Tạo giao dịch mới
- `GET /api/v1/payments/{id}` - Lấy thông tin giao dịch
- `POST /api/v1/payments/{id}/capture` - Thanh toán giao dịch
- `POST /api/v1/payments/{id}/refund` - Hoàn tiền
- `POST /api/v1/payments/{id}/void` - Hủy giao dịch

#### 5.2. WEBHOOK ENDPOINTS
- `POST /api/v1/webhooks` - Cấu hình webhook
- `GET /api/v1/webhooks` - Lấy danh sách webhook
- `DELETE /api/v1/webhooks/{id}` - Xóa webhook

#### 5.3. ANALYTICS ENDPOINTS
- `GET /api/v1/analytics` - Lấy thống kê giao dịch
- `GET /api/v1/analytics/merchants/{id}` - Thống kê theo thương gia

#### 5.4. AUTHENTICATION ENDPOINTS
- `POST /api/v1/auth/register` - Đăng ký
- `POST /api/v1/auth/login` - Đăng nhập

### 6. ĐỊNH DẠNG DỮ LIỆU

#### 6.1. PAYMENT REQUEST
```json
{
  "merchantId": "merchant_123",
  "amount": 100000,
  "currency": "VND",
  "paymentMethodId": "pm_123",
  "description": "Thanh toán đơn hàng #123",
  "referenceId": "ref_123"
}
```

#### 6.2. PAYMENT RESPONSE
```json
{
  "id": "payment_uuid",
  "merchantId": "merchant_123",
  "amount": 100000,
  "currency": "VND",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

#### 6.3. WEBHOOK PAYLOAD
```json
{
  "id": "webhook_uuid",
  "eventType": "payment.authorized",
  "data": {
    "paymentId": "payment_uuid",
    "merchantId": "merchant_123",
    "amount": 100000,
    "status": "AUTHORIZED"
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 7. XỬ LÝ LỖI

#### 7.1. MÃ LỖI HTTP
- **400 Bad Request:** Dữ liệu đầu vào không hợp lệ
- **401 Unauthorized:** Không có quyền truy cập
- **402 Payment Required:** Thanh toán thất bại
- **409 Conflict:** Xung đột dữ liệu (idempotency)
- **500 Internal Server Error:** Lỗi hệ thống

#### 7.2. ERROR RESPONSE FORMAT
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid payment amount",
  "path": "/api/v1/payments"
}
```

### 8. MONITORING VÀ LOGGING

#### 8.1. HEALTH CHECK
- **Endpoint:** `/api/v1/health`
- **Kiểm tra:** Database connection, Redis, Kafka
- **Response:** Trạng thái của từng component

#### 8.2. METRICS
- **Transaction metrics:** Số lượng, tỷ lệ thành công, thời gian xử lý
- **System metrics:** CPU, Memory, Database connections
- **Business metrics:** Revenue, conversion rate

#### 8.3. LOGGING
- **Structured logging:** JSON format
- **Log levels:** DEBUG, INFO, WARN, ERROR
- **Correlation ID:** Theo dõi request qua các service

### 9. DEPLOYMENT VÀ INFRASTRUCTURE

#### 9.1. DOCKER CONTAINERS
- **Application:** Spring Boot application
- **Database:** MySQL 8.0 với Master-Slave
- **Cache:** Redis cluster
- **Message Queue:** Kafka cluster

#### 9.2. ENVIRONMENT CONFIGURATION
- **Development:** Local development setup
- **Staging:** Production-like environment
- **Production:** High availability setup

#### 9.3. DATABASE MIGRATION
- **Liquibase:** Quản lý schema changes
- **Versioning:** Database version control
- **Rollback:** Khả năng rollback changes

### 10. TESTING

#### 10.1. UNIT TESTING
- **Coverage:** Tối thiểu 80% code coverage
- **Frameworks:** JUnit 5, Mockito
- **Test data:** Test fixtures và builders

#### 10.2. INTEGRATION TESTING
- **Database:** Test với real database
- **API:** End-to-end API testing
- **External services:** Mock external dependencies

#### 10.3. LOAD TESTING
- **Tools:** JMeter, Gatling
- **Scenarios:** High volume transaction processing
- **Metrics:** Response time, throughput, error rate

---

**Phiên bản tài liệu:** 1.0  
**Ngày tạo:** 2024  
**Người tạo:** Development Team  
**Trạng thái:** Draft
