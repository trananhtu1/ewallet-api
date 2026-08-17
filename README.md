# ewallet-api

REST API cho ứng dụng ví điện tử — nạp tiền, chuyển tiền giữa hai ví, lịch sử giao dịch.

**Stack:** Java 17 · Spring Boot 4.1.0 · PostgreSQL (Neon) · Maven

Frontend nằm ở repo riêng: [`ewallet-web`](https://github.com/trananhtu1/ewallet-web)

---

## Yêu cầu

- **JDK 17** — `java -version` phải ra `17.x.x`
- Không cần cài Maven, project đã kèm `mvnw`

## Cấu hình

Copy `.env.example` thành `.env` rồi điền thông tin database:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DBNAME?sslmode=require
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
```

> ⚠️ Neon cho chuỗi kết nối định dạng `libpq`:
> `postgresql://user:pass@host/db?sslmode=require`
>
> **JDBC không hiểu định dạng đó.** Phải thêm tiền tố `jdbc:` và tách
> user/password ra thành hai biến riêng, nếu không sẽ nhận
> `Driver claims to not accept jdbcUrl`.

`.env` đã bị `.gitignore` chặn, không bao giờ commit.

## Chạy

| | Lệnh |
|---|---|
| **Windows** | `.\mvnw.cmd spring-boot:run` |
| **macOS / Linux** | `./mvnw spring-boot:run` |

Lần đầu chạy sẽ tải Maven và ~75 jar về `~/.m2`, mất vài phút. Lần sau nhanh.

Mặc định nghe cổng **3003** → <http://localhost:3003/health>

## Biến môi trường

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `PORT` | `3003` | Render tự cấp, **không** được hardcode cổng |
| `SPRING_DATASOURCE_URL` | *không có* | Thiếu là app không khởi động — cố ý, để vỡ lúc deploy chứ không vỡ lúc có người dùng |
| `SPRING_DATASOURCE_USERNAME` | *không có* | |
| `SPRING_DATASOURCE_PASSWORD` | *không có* | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Nhiều origin thì phân cách bằng dấu phẩy. Để trống là tắt CORS |
| `LOGGING_LEVEL_COM_VIDIEN_EWALLET` | `INFO` | Đặt `DEBUG` để bật log chi tiết, không cần deploy lại |

## Endpoint

### `GET /health`

```json
{
  "status": "ok",
  "service": "ewallet-api",
  "profile": "local",
  "java": "17.0.20",
  "db": { "status": "ok", "ms": 60 },
  "time": "2026-08-17T11:58:40Z"
}
```

`db` chạy `SELECT 1` thật sang database. Khi database lỗi, endpoint vẫn trả `200`
với `db.status = "error"` — **cố ý**: nếu trả `500` thì Render kết luận app chết và
restart liên tục, dù app hoàn toàn bình thường và chỉ có database đang ngủ.

## Xử lý sự cố

Cổng đã bị chiếm:

| | Lệnh |
|---|---|
| **Windows** | `netstat -ano \| findstr :3003` rồi `taskkill /PID <pid> /F` |
| **macOS** | `lsof -ti:3003` rồi `kill <pid>` |

Frontend báo lỗi CORS → kiểm tra `CORS_ALLOWED_ORIGINS` có đúng origin của FE không.
Log lúc khởi động in ra danh sách origin được phép.

📓 Các sự cố đã gặp và cách sửa: [`INCIDENTS.md`](INCIDENTS.md)
