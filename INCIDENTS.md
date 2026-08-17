# 📓 Nhật ký sự cố — ewallet

Mỗi sự cố ghi 5 cột (`ROADMAP.md:543`). Chỉ ghi cái **tự tay gặp**, không ghi cái đọc được.

Đến Week 6 file này là kho câu chuyện phỏng vấn. Thứ làm câu chuyện có giá không phải
kiến thức, mà là **con số** + **cách phát hiện** — hai thứ không bịa được (`ROADMAP.md:593`).

---

## 1. CORS chặn frontend gọi backend

| | |
|---|---|
| **Ngày** | 17/08/2026 |
| **Hiện tượng** | Trang React ở `localhost:5173` gọi `GET localhost:3003/health` thì thất bại. Trang chỉ báo `Failed to fetch` — gần như không nói gì. Console mới có dòng thật: `blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present`. |
| **Tìm ra bằng cách nào** | Debug từ ngoài vào trong. Gọi lại chính URL đó bằng `curl` → ra đủ JSON, `200 OK`. Nghĩa là server, cổng, route, DB đều không sao. Gọi lần hai có thêm `-H "Origin: http://localhost:5173"` cho giống trình duyệt → **vẫn `200` + vẫn đủ body**, nhưng response **không có** header `Access-Control-Allow-Origin`. Tab Network xác nhận: status `200`, response có dữ liệu. Vậy lỗi không nằm ở server cũng không ở code fetch — nằm ở **header thiếu**. |
| **Nguyên nhân gốc** | Spring Boot không tự thêm header CORS nào. Không có cấu hình thì response ra hoàn toàn bình thường, chỉ thiếu `Access-Control-Allow-Origin`. `5173` và `3003` là **hai origin khác nhau** (khác cổng là đã khác origin, dù cùng `localhost`), nên trình duyệt áp luật same-origin: nhận đủ dữ liệu rồi từ chối đưa cho JavaScript. |
| **Cách sửa** | Thêm `CorsConfig implements WebMvcConfigurer`, đọc danh sách origin từ biến `CORS_ALLOWED_ORIGINS` (mặc định `http://localhost:5173`). **Không** dùng `@CrossOrigin` hardcode vì sẽ phải sửa code lại khi FE lên domain Vercel. Xác minh bằng `curl -D -`: header `Access-Control-Allow-Origin: http://localhost:5173` đã xuất hiện. |

### Điều phản trực giác — phần đáng kể nhất

Lúc **chưa** cấu hình, server **không hề chặn gì**. Nó trả `200` + toàn bộ dữ liệu, và dữ
liệu đó **đã về tới máy**. Chính **trình duyệt** đọc thấy thiếu header cho phép rồi tự tay
đổ dữ liệu đi. `curl` không phải trình duyệt nên không ai thực thi luật đó → gọi thẳng vẫn ra.

CORS bảo vệ **người dùng** khỏi việc tab A đọc dữ liệu domain B bằng cookie của họ.
Nó **không** bảo vệ server.

### Nhưng có một khúc quanh — chỗ này mới ăn điểm

Sau khi cấu hình CORS trong Spring, đo lại bằng ba origin khác nhau:

| Gọi với | Kết quả |
|---|---|
| `Origin: http://localhost:5173` (được phép) | `200` + `Access-Control-Allow-Origin: http://localhost:5173` |
| `Origin: http://evil.example.com` (không được phép) | **`403 Forbidden`** — không có body |
| không có `Origin` (curl, server-to-server) | `200`, không có header CORS nào |

Tức là **Spring chủ động chặn bằng 403**, chứ không phải trả 200 rồi để trình duyệt tự lo.
Nên câu "server không bao giờ chặn CORS" chỉ đúng khi **chưa** cấu hình. Cấu hình xong thì
server chặn thật. Phân biệt được hai giai đoạn này là chỗ tách người đã làm với người chỉ đọc.

Dòng `Vary: Origin` cũng đáng chú ý: nó nói cho CDN biết response **thay đổi theo** `Origin`.
Thiếu nó, CDN có thể cache header cho phép của origin A rồi trả cho origin B.

### Cái sẽ vỡ lại ở Week 3 — biết trước để đỡ mất một buổi

`GET /health` là **simple request**, không có preflight. Nên lần này **không** thấy request
`OPTIONS` nào. Sang Week 3 gắn `Authorization: Bearer` cho JWT là trình duyệt bắt đầu gửi
`OPTIONS` hỏi trước, và CORS **vỡ lại theo kiểu khác** — sửa xong hôm nay vẫn vỡ.

Đã đo trước preflight bằng `curl -X OPTIONS`, hiện tại trả lời đúng:

```
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
Access-Control-Allow-Headers: authorization, content-type
Access-Control-Max-Age: 3600
```

Hai mìn còn lại ở Week 3:
1. **Spring Security chạy trước Spring MVC.** Thêm Security vào là filter chặn `OPTIONS`
   trước khi tới CORS config → lại lỗi CORS dù config vẫn đúng. Phải bật `.cors()` trong
   `SecurityFilterChain` và cho `OPTIONS` đi qua không cần xác thực.
2. **`allowedOrigins("*")` + `allowCredentials(true)` bị đặc tả CẤM.** Nếu chọn lưu token
   trong cookie `httpOnly` thì phải bật credentials, lúc đó `"*"` làm Spring ném lỗi.
   Đã liệt kê origin rõ từ đầu nên không phải sửa lại.

### Tái hiện lại sự cố CORS khi cần

Để trống biến rồi khởi động lại — `CorsConfig` sẽ không đăng ký gì:

```powershell
$env:CORS_ALLOWED_ORIGINS = ""
.\mvnw.cmd spring-boot:run
```

*(`ON-TAP.md:51` có bản ghi ngắn cho câu hỏi này, nhưng thiếu phần preflight và thiếu
khúc quanh 403 ở trên — nên đọc kèm mục này.)*

---

## 2. JVM không đọc được giới hạn RAM của container → heap 8GB trong hộp 512MB

| | |
|---|---|
| **Ngày** | 17/08/2026 |
| **Hiện tượng** | Chưa vỡ. Tìm ra lúc build thử `Dockerfile` tại máy trước khi đẩy lên Render, chạy với `docker run --memory=512m` đúng như gói free. |
| **Tìm ra bằng cách nào** | Không tin `Dockerfile` chỉ vì nó build thành công, nên đo thẳng con số JVM tự tính: `docker exec ... java -XX:+PrintFlagsFinal -version \| grep MaxHeapSize`. Kết quả **3122659328 = 2.9GB** trong container 512MB. Đối chiếu: cgroup báo đúng (`cat /sys/fs/cgroup/memory.max` = `536870912`), nhưng JVM lại không dùng. Chạy `java -Xlog:os+container=trace` thì ra nguyên nhân: `controller memory is not enabled` → `One or more required controllers disabled at kernel level` → JVM **tự tắt** container support rồi đọc RAM máy thật (11.9GB). Với `-XX:MaxRAMPercentage=70` thì heap thành **8749318144 = 8.1GB**. |
| **Nguyên nhân gốc** | `MaxRAMPercentage` tính theo lượng RAM mà JVM *nghĩ* là có. Nó biết giới hạn container nhờ đọc cgroup, và việc đó **không luôn thành công** — ở đây kernel WSL2 của Docker Desktop không phơi controller `memory` trong `/proc/cgroups`. Cấu hình theo phần trăm nên phụ thuộc vào một phép dò có thể thất bại im lặng. |
| **Cách sửa** | Đổi sang heap **tuyệt đối**: `-Xms64m -Xmx300m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC`. Gói free là 512MB cố định, biết trước con số thì đặt thẳng. Xác minh: `MaxHeapSize = 314572800` và nhãn đổi từ `{ergonomic}` thành **`{command line}`** — bằng chứng giá trị đến từ tham số, không phải JVM đoán. RAM container giảm từ 203MB xuống **167.6MB / 512MB**. |

### Vì sao đây là loại lỗi tệ nhất

Nếu để `MaxRAMPercentage` mà JVM dò sai trên production: heap được phép phình tới 8GB
trong hộp 512MB → **kernel giết tiến trình** (OOM-kill), **không phải** `OutOfMemoryError`
của Java. Khác biệt đó quyết định việc chẩn đoán được hay không:

| | `OutOfMemoryError` | OOM-kill của kernel |
|---|---|---|
| Stack trace | có | **không** |
| Log ghi lại | có | **không** |
| Nhìn thấy gì | biết chỗ nào hết bộ nhớ | app biến mất, Render báo restart |

Nên bug này không hiện ra như một lỗi, nó hiện ra như *"app tự restart, chả hiểu vì sao"*.

### Chỗ phải nói trung thực

Lỗi dò cgroup này là **đặc thù Docker Desktop trên WSL2**. Render chạy Linux thật với
cgroup v2 đủ controller, nên ở đó JVM **rất có thể** dò đúng và `MaxRAMPercentage=70` sẽ
cho ra 360MB như ý.

Tức là bản `Dockerfile` cũ *có thể* vẫn chạy tốt trên Render. Vẫn đổi vì: giá trị cố định
biết trước → không cần đánh cược vào một phép dò, mà kiểu chết khi đoán sai lại là loại
không có log. Và quan trọng với việc học: bản `-Xmx` **kiểm chứng được ngay tại máy**, bản
phần trăm thì không.

> 🎤 Câu chuyện phỏng vấn: *"Em build thử container tại máy với đúng giới hạn 512MB của
> gói free trước khi deploy. Cgroup báo đúng 512MB nhưng JVM vẫn tính heap 2.9GB, vì kernel
> WSL2 không phơi controller memory nên JVM tự tắt container support và đọc RAM máy thật.
> Nếu để cấu hình theo phần trăm thì thành heap 8GB trong hộp 512MB, và lỗi sẽ hiện ra như
> app tự restart không log vì bị kernel OOM-kill chứ không phải OutOfMemoryError. Em đổi
> sang -Xmx tuyệt đối, RAM xuống 167MB và kiểm chứng được bằng nhãn {command line} trong
> PrintFlagsFinal."*
>
> Không bịa được: có 4 con số, có lệnh dùng để phát hiện, và có phần tự phản biện là nó
> có thể không xảy ra trên Render.

### Tái hiện

```powershell
docker run --rm --memory=512m ewallet-api:local sh -c "java -Xlog:os+container=trace -version"
docker run --rm --memory=512m ewallet-api:local sh -c "java -XX:MaxRAMPercentage=70 -XX:+PrintFlagsFinal -version | grep 'size_t MaxHeapSize'"
```

