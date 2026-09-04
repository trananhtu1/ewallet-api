# ewallet-api

**REST API cho một hệ thống ví điện tử** — đăng ký, đăng nhập, nạp tiền, chuyển tiền giữa hai
ví, và tra cứu lịch sử giao dịch.

🌐 **Demo:** [ewallet-web.vercel.app](https://ewallet-web.vercel.app) ·
📘 **API docs:** [`/swagger-ui/index.html`](https://ewallet-api-smn5.onrender.com/swagger-ui/index.html) ·
💻 **Frontend:** [`ewallet-web`](https://github.com/trananhtu1/ewallet-web)

> ⏳ Backend chạy trên gói free của Render nên **ngủ sau 15 phút** không có traffic.
> Request đầu tiên mất **~75 giây** để đánh thức. Những request sau bình thường.

---

## Về dự án

Đây là một dự án tự xây để làm việc với **phần khó của nghiệp vụ tài chính** — thứ mà một ứng
dụng CRUD thông thường không chạm tới.

Chuyển tiền nghe đơn giản: trừ ví này, cộng ví kia. Nhưng nó ép phải trả lời những câu hỏi mà
chỉ tiền mới đặt ra:

| Câu hỏi | Cách hệ thống trả lời |
|---|---|
| Trừ xong mà cộng lỗi thì sao? | Một `@Transactional` — cùng thành công hoặc cùng huỷ |
| Hai lệnh chuyển vào cùng một ví cùng lúc? | Khoá bi quan ở mức dòng, khoá theo **thứ tự ID tăng dần** |
| A→B và B→A chạy song song? | Cùng cơ chế trên — đo được **12 lệnh đồng thời, 12 thành công** |
| Người dùng bấm "Chuyển" hai lần vì mạng lag? | **Idempotency key** — 10 request cùng khoá chỉ tạo **1 dòng sổ cái** |
| Số tiền lẻ có bị sai số không? | `BigDecimal` + `NUMERIC(19,2)` — **không** dùng số thực dấu phẩy động |
| Chuyển quá số dư thì ghi lại ở đâu? | Dòng sổ cái `FAILED` **sống sót qua rollback** |
| Ai đã thử làm gì, kể cả khi bị từ chối? | Bảng `audit_log`, ghi bằng transaction riêng |
| Ai đảm bảo số liệu cuối ngày khớp? | Job **đối soát** chạy theo lịch, so sổ cái với số dư |

Ngoài ra: xác thực **JWT + refresh token có xoay vòng và phát hiện dùng lại**, phân trang
**cursor**, cache Redis, upload KYC, và bộ **49 test tự động**.

---

## Kiến trúc

Chia **theo tính năng**, không theo tầng kỹ thuật. Mở thư mục `wallet/` là thấy toàn bộ những
gì liên quan đến ví, không phải đi lục ba thư mục khác nhau.

```
com.vidien.ewallet
├── auth/            đăng ký · đăng nhập · JWT · refresh token
├── user/            người dùng
├── wallet/          ví · số dư · nạp tiền
├── transfer/        chuyển tiền  ← phần khó nhất
├── transaction/     sổ cái · lịch sử · phân trang cursor
├── kyc/             upload giấy tờ
├── audit/           nhật ký kiểm toán
├── reconciliation/  đối soát cuối ngày
└── shared/          cấu hình · xử lý lỗi · health check
```

Mỗi tính năng chia tiếp ba tầng: `api/` *(controller + DTO)* · `domain/` *(entity + service)* ·
`infra/` *(repository)*.

---

## Stack

| | |
|---|---|
| **Ngôn ngữ** | Java 17 |
| **Framework** | Spring Boot 4.1 · Spring Security · Spring Data JPA / Hibernate |
| **Cơ sở dữ liệu** | PostgreSQL 18 *(Neon)* · Flyway *(8 migration)* |
| **Cache** | Redis *(Spring Cache)* |
| **Sinh code** | Lombok · MapStruct |
| **Kiểm thử** | JUnit 5 · Mockito · **Testcontainers** |
| **Tài liệu** | springdoc-openapi *(Swagger UI)* |
| **Triển khai** | Docker · Render *(Infrastructure as Code)* |

---

## Chạy tại máy

**Cần:** JDK 17 *(`java -version` phải ra `17.x.x`)*. Không cần cài Maven — project kèm `mvnw`.

Copy `.env.example` thành `.env` rồi điền:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DBNAME?sslmode=require
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
JWT_SECRET=<chuỗi ngẫu nhiên ≥ 32 ký tự>
```

> ⚠️ Neon cho chuỗi kết nối dạng `libpq`: `postgresql://user:pass@host/db?sslmode=require`.
> **JDBC không hiểu định dạng đó** — phải thêm tiền tố `jdbc:` và tách user/password thành hai
> biến riêng, nếu không sẽ nhận `Driver claims to not accept jdbcUrl`.

Sinh khoá JWT: `openssl rand -base64 48`

Chạy:

| | Lệnh |
|---|---|
| **Windows** | `.\mvnw.cmd spring-boot:run` |
| **macOS / Linux** | `./mvnw spring-boot:run` |

Cổng mặc định **3003** → <http://localhost:3003/swagger-ui/index.html>

`.env` đã bị `.gitignore` chặn, không bao giờ commit.

---

## Kiểm thử

```bash
./mvnw test                        # 7 unit test, ~5 giây, không cần Docker
./mvnw verify                      # thêm 42 integration test — CẦN Docker
REQUIRE_DOCKER=true ./mvnw verify  # build ĐỎ nếu không tìm thấy Docker
```

Integration test dùng **PostgreSQL và Redis thật** qua Testcontainers, không dùng database
in-memory: `FOR NO KEY UPDATE`, JSONB và deadlock đều không tái hiện được trên H2 — mà đó đúng
là những thứ cần canh giữ nhất.

Máy không có Docker thì chúng **bỏ qua** thay vì đổ. Biến `REQUIRE_DOCKER=true` biến việc bỏ
qua đó thành lỗi, dành cho những máy lẽ ra phải có Docker.

---

## Biến môi trường

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `PORT` | `3003` | Render tự cấp — **không** hardcode cổng |
| `SPRING_DATASOURCE_URL` | *bắt buộc* | Thiếu là app không khởi động. Cố ý: vỡ lúc deploy còn hơn vỡ lúc có người dùng |
| `SPRING_DATASOURCE_USERNAME` | *bắt buộc* | |
| `SPRING_DATASOURCE_PASSWORD` | *bắt buộc* | |
| `JWT_SECRET` | *bắt buộc* | ≥ 32 ký tự cho HS256. Trên Render do `generateValue: true` tự sinh |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Nhiều origin phân cách bằng dấu phẩy |
| `CACHE_TYPE` | `none` | Đặt `redis` để bật cache |
| `REDIS_URL` | `redis://localhost:6379` | Trên Render do Blueprint tự điền |
| `LOGGING_LEVEL_COM_VIDIEN_EWALLET` | `INFO` | Đặt `DEBUG` để bật log chi tiết, không cần deploy lại |

---

## Health check — hai endpoint, hai câu hỏi khác nhau

| Endpoint | Trả lời câu gì | Chạm DB? | Ai gọi |
|---|---|---|---|
| `/ping` | tiến trình còn sống không | ❌ | **Render** *(`healthCheckPath`)* |
| `/health` | mọi thứ có ổn không | ✅ chờ tối đa **2s** | người, lúc chẩn đoán |

`/ping` không có I/O, không chạm database. Đo với mật khẩu Neon cố ý sai: vẫn trả trong **20ms**.

`/health` chạy `SELECT 1` thật — truy vấn rẻ nhất chứng minh được cả chuỗi kết nối lẫn mật khẩu:

```json
{
  "status": "ok",
  "service": "ewallet-api",
  "profile": "prod",
  "java": "17.0.20",
  "db": { "status": "ok", "ms": 60 },
  "time": "2026-09-04T11:58:40Z"
}
```

`db.status` có bốn giá trị: `ok` · `timeout` *(DB không trả lời trong 2s)* · `error` *(sai mật
khẩu, mất mạng…)* · `busy` *(hàng đợi đầy, từ chối ngay thay vì xếp hàng)*.

⚠️ **Cả bốn đều trả HTTP `200`** — cố ý. Trả `500` thì nền tảng kết luận app chết và restart
liên tục, dù app hoàn toàn bình thường và chỉ có database đang ngủ. Nhưng chính vì `/health`
chạm database nên nó **không** được làm liveness probe — đó là việc của `/ping`.

---

## Triển khai

Render **không hỗ trợ Java native** nên project đi qua `Dockerfile`. Render tự build image trên
máy chủ của họ, không cần cài Docker ở máy mình.

Dashboard → **New → Blueprint** → chọn repo này. Render đọc `render.yaml` và hỏi các biến bí mật
*(`sync: false` nghĩa là không lưu vào git)*.

Đã chốt trong `render.yaml`:

| | |
|---|---|
| Region | **Singapore** — cùng chỗ với Neon *(`ap-southeast-1`)*, đỡ đi vòng trái đất mỗi truy vấn |
| Health check | `/ping`, **không** `/health` |
| Bộ nhớ | 512MB → `-Xmx176m -XX:MaxMetaspaceSize=160m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC` |
| Cache | Key Value instance khai ngay trong Blueprint, `REDIS_URL` tự nối |
| Database | **Neon** — database của Render hết hạn sau 30 ngày, Neon thì không |

⚠️ **Không dùng `-XX:MaxRAMPercentage`.** Đo thật thì JVM có thể không đọc được giới hạn RAM của
container, khi đó `MaxRAMPercentage=70` cho ra heap **8.1GB trong hộp 512MB** → **kernel
OOM-kill: không stack trace, không log**, chỉ thấy app tự restart. Đặt con số tuyệt đối.

⚠️ **Metaspace nằm ngoài heap** — `-Xmx` không chạm tới nó. Spring + JPA + springdoc + Redis cần
~115MB metaspace; đặt trần quá thấp thì app chết lúc khởi động với `OutOfMemoryError: Metaspace`
dù heap còn trống.

---

## Xử lý sự cố

Cổng đã bị chiếm:

| | Lệnh |
|---|---|
| **Windows** | `netstat -ano \| findstr :3003` rồi `taskkill /PID <pid> /F` |
| **macOS** | `lsof -ti:3003` rồi `kill <pid>` |

Frontend báo lỗi CORS → kiểm `CORS_ALLOWED_ORIGINS` có đúng origin của FE không. Log lúc khởi
động in ra danh sách origin được phép.

App không khởi động, log ghi `Schema-validation` → schema trên database lệch với entity. Kiểm
Flyway đã chạy chưa. `ddl-auto=validate` cố ý từ chối khởi động thay vì âm thầm chạy sai.
