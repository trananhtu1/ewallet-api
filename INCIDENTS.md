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

⚠️ **Bắt buộc có `--entrypoint sh`.** Image này khai
`ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]`, nên thứ viết sau tên image
**không** chạy như một lệnh — Docker nối chúng vào làm **tham số vị trí** (`$0`, `$1`…)
cho `sh -c` đó rồi bỏ qua. Hậu quả: câu lệnh tưởng là chẩn đoán lại đi **khởi động app**.

Bản cũ của mục này thiếu `--entrypoint`, đã thử lại ngày 20/08: dòng `echo` không hề in ra,
thay vào đó Spring khởi động. Loại sai tệ nhất — nó *trông như* đang chạy đúng.

```powershell
# JVM có đọc được cgroup không
docker run --rm --memory=512m --entrypoint sh ewallet-api:local -c "java -Xlog:os+container=trace -version"

# Thuốc CŨ (sai) — heap do JVM tự đoán
docker run --rm --memory=512m --entrypoint sh ewallet-api:local -c "java -XX:MaxRAMPercentage=70 -XX:+PrintFlagsFinal -version | grep 'size_t MaxHeapSize'"

# Thuốc ĐANG DÙNG — heap tuyệt đối
docker run --rm --memory=512m --entrypoint sh ewallet-api:local -c "java -Xms64m -Xmx300m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC -XX:+PrintFlagsFinal -version | grep -E 'size_t MaxHeapSize|bool UseSerialGC'"
```

### Xác minh lại 20/08 — ngay trước khi deploy Render

Build lại image từ đầu rồi đo lại, để chắc con số hôm 17/08 không phải ăn may:

| Đo | 17/08 | 20/08 |
|---|---|---|
| `MaxHeapSize` với `-Xmx300m` | `314572800` `{command line}` | **`314572800` `{command line}`** |
| `MaxHeapSize` với `MaxRAMPercentage=70` | `8749318144` (8.1GB) | **`8749318144` (8.1GB)** |
| Dò cgroup | `controller memory is not enabled` | **y hệt** |
| RAM container lúc chạy | 167.6MB / 512MB | **167MB / 512MB (32.6%)** |
| `/health` trong container | — | ok · `profile: prod` · Neon **52ms** |

Ba ngày sau, image dựng lại từ đầu, con số **không đổi**. Nên đây là tính chất cố định của
môi trường WSL2 này, không phải hiện tượng ngẫu nhiên gặp một lần.

Còn một phép tính khớp nữa, đáng nói khi phỏng vấn: `docker info` báo RAM máy thật
`12488142848` bytes = 11.6GB. **11.6GB × 70% = 8.14GB** — đúng bằng con số heap ở dòng thứ
hai. Đó là bằng chứng trực tiếp JVM đã lấy RAM của *máy thật* thay vì giới hạn 512MB của
container, không phải suy đoán.

Và chỗ nhìn nhanh nhất để biết JVM có nghe mình không là **cột nhãn** của `PrintFlagsFinal`:
`{command line}` = do mình chỉ định · `{ergonomic}` = **JVM tự đoán**.

---

## 3. Deploy đổ vì health check hết giờ — sai mật khẩu không giết app, nó làm app **chậm**

| | |
|---|---|
| **Ngày** | 20/08/2026 |
| **Hiện tượng** | Render báo `Deploy failed`: *"Timed out after waiting for internal health check to return a successful response code at ewallet-api-smn5.onrender.com:10000/health"*. Trong log runtime có `password authentication failed for user 'neondb_owner'`, và 5 phút sau là `SpringApplicationShutdownHook - Commencing graceful shutdown`. |
| **Tìm ra bằng cách nào** | Dựng lại **đúng điều kiện Render ngay tại máy** thay vì đoán: `docker run --memory=512m -e PORT=10000` kèm mật khẩu **cố ý sai**. Hai nghi phạm dễ nghĩ nhất bị loại ngay: (1) log ra `Tomcat started on port 10000` → app **có** đọc biến `PORT`; (2) `/health` vẫn trả **HTTP 200** → sai mật khẩu **không** làm app chết. Nhưng phép thử lộ ra con số thật: `"db":{"status":"error","ms":10000}` — đúng bằng `connection-timeout`. |
| **Nguyên nhân gốc** | Chuỗi ba mắt xích: mật khẩu sai → Hikari không lấy được connection, mỗi lần gọi phải chờ hết `connection-timeout=10000` → `/health` treo 10 giây mới trả lời → Render bỏ cuộc trước đó. Chữ trong thông báo nói đúng bản chất: *"Timed out **waiting for** ... to return"* — không phải app trả về mã lỗi, mà là **không kịp trả lời**. |
| **Cách sửa** | Reset mật khẩu ở Neon Console → Roles → `neondb_owner`, rồi cập nhật **cả hai nơi**: `.env` ở máy và biến `SPRING_DATASOURCE_PASSWORD` trên Render. Xác minh ở máy trước bằng container (~30 giây) rồi mới sửa trên Render — mỗi lần Render deploy lại tốn 5–10 phút, quá đắt để dùng làm chỗ thử mật khẩu. |

### Điều phản trực giác — phần đáng kể nhất

Trực giác nói *"sai mật khẩu thì app chết"*. Đo ra thì ngược lại: app **sống khoẻ**, trả
`200`, chỉ **chậm**. Và cái giết nó là **thời gian**, không phải mã trạng thái.

Mỉa mai hơn: `/health` được thiết kế cố ý trả `200` kể cả khi DB lỗi, để Render **khỏi**
restart-loop — ý đúng, nhưng vẫn đổ, vì phòng nhầm mặt trận. Phòng được *mã lỗi*, không
phòng được *độ trễ*.

### Ba cách đọc log học được trong một buổi

**1. Nhìn định dạng là biết log từ máy nào.** Hai định dạng đã cấu hình khác nhau từ trước,
đến hôm nay mới thấy nó có ích thật:

| | Máy mình | Render (`prod`) |
|---|---|---|
| Mẫu | `21:46:34 WARN PoolBase` | `2026-08-20 14:49:56.854 WARN [HikariPool-1:connection-adder] com.zaxxer.hikari.pool.PoolBase` |
| Có ngày | không | **có** |
| Có tên thread | không | **có** |
| Tên logger | rút gọn `%logger{0}` | đầy đủ `%logger{36}` |

Dán nhầm log của máy mình rồi đi sửa Render là mất buổi. Một cái liếc vào định dạng là xong.

**2. `graceful shutdown` = bị *bảo* dừng, không phải chết.** Đối chiếu thẳng với mục 2:

| | Render dừng (hôm nay) | Kernel OOM-kill (mục 2) |
|---|---|---|
| Tín hiệu | `SIGTERM` | `SIGKILL` |
| Log | có, đầy đủ | **không có gì** |
| Nhìn ra sao | `Commencing graceful shutdown` | app biến mất, không lý do |

Thấy `graceful shutdown` là **loại ngay** giả thuyết hết RAM.

**3. `curl` chia nhỏ được chỗ hỏng.** Lúc URL không trả lời, `-w` cho biết hỏng ở chặng nào:

```
DNS      0.010s ✅      TCP      0.041s ✅      TLS      0.080s ✅
Byte đầu 0.000s ❌      Tổng     180s (tự cắt)
```

Bắt tay TLS xong trong 80ms → **edge của Render sống**. Không có byte nào sau đó → **phía
sau edge không có instance nào đang chạy**. Khác hẳn với "app sống nhưng lỗi", vốn sẽ trả
`200` sau 10 giây như đã đo. Phân biệt được *im lặng* với *trả lời chậm* là điều rút ra.

### Hai cái bẫy phụ, cùng gặp trong buổi

**a. Sửa file cấu hình mà không restart thì vô nghĩa.** Sửa `.env` xong, terminal chạy
`mvnw` vẫn báo sai mật khẩu — vì `spring.config.import=optional:file:./.env` đọc **đúng
một lần lúc khởi động**, không theo dõi file. Chứng minh: container khởi động mới, đọc
đúng file đó, ra `"db":{"status":"ok","ms":51}`.

**b. `${VAR:default}` — phần mặc định bị bỏ qua khi biến tồn tại.** Lúc lỗi CORS trên
production, phản xạ đầu tiên là sửa giá trị mặc định trong `application.properties`:

```properties
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173,https://ewallet-web.vercel.app/}
```

Sửa vậy **không có tác dụng gì**: trên Render biến `CORS_ALLOWED_ORIGINS` đã tồn tại, nên
Spring lấy giá trị của biến và **không bao giờ đọc tới phần mặc định**. Chưa kể nó hardcode
domain vào source — đúng thứ mà `CorsConfig` được viết ra để tránh. Sửa đúng là đổi **env
var trên Render**: không push, không build lại, restart ~1 phút thay vì 5–10 phút.

### Một giả thuyết bị phép đo bác bỏ

Nghi dấu `/` ở cuối domain làm hỏng so khớp origin. Đo hai lượt trong container:

| Cấu hình | Trình duyệt gửi `Origin: https://ewallet-web.vercel.app` | Kết quả |
|---|---|---|
| `https://ewallet-web.vercel.app/` (có `/`) | | ✅ có `Access-Control-Allow-Origin` |
| `https://ewallet-web.vercel.app` (không `/`) | | ✅ có `Access-Control-Allow-Origin` |

**Spring tự cắt dấu `/` thừa.** Bỏ dấu `/` vẫn là thói quen tốt, nhưng nó **không** phải
nguyên nhân — giữ lại đây để khỏi đi lại đường cụt đó lần sau.

### 🔴 Còn nợ — cái bẫy vẫn nằm nguyên đó

Sửa mật khẩu chỉ giải quyết *lần này*. Gốc vấn đề chưa đụng tới: **liveness probe không
được phụ thuộc vào dịch vụ ngoài.** Render hỏi *"app còn sống không"*, mà `/health` lại đi
hỏi Neon hộ nó. Neon free **tự ngủ** khi không ai dùng → sẽ có ngày deploy lại đúng lúc
Neon ngủ, `/health` chậm, `Deploy failed` — mà chẳng có gì sai cả.

Cần tách hai loại, chưa làm:

| Endpoint | Trả lời câu gì | Chạm DB? | Ai gọi |
|---|---|---|---|
| `/ping` | app còn sống không | ❌ | Render (`healthCheckPath`) |
| `/health` | mọi thứ có ổn không | ✅ nhưng chờ tối đa 2s | người, lúc chẩn đoán |

### 🎤 Câu chuyện phỏng vấn

> *"Lần deploy đầu của em đổ với thông báo health check timeout. Em dựng lại đúng điều kiện
> production ở máy — container giới hạn 512MB, PORT=10000, mật khẩu cố tình sai — và loại
> được hai nghi phạm: app vẫn nghe đúng cổng, và /health vẫn trả 200 chứ không chết. Nhưng
> nó trả sau đúng 10 giây, bằng connection-timeout của Hikari. Tức là sai mật khẩu không
> giết app, nó làm app chậm, và Render giết app vì chậm. Trớ trêu là em đã cố ý cho /health
> trả 200 khi DB lỗi để tránh restart-loop — phòng đúng hướng mã lỗi nhưng không phòng độ
> trễ. Sửa trước mắt là mật khẩu, còn sửa gốc là tách /ping không chạm DB cho liveness
> probe, vì health check của nền tảng mà phụ thuộc database thì database ngủ là app bị
> khai tử oan."*

Không bịa được: có con số (10 giây = `connection-timeout`), có cách phát hiện (dựng lại
điều kiện production tại máy để loại nghi phạm), và có phần tự phản biện (thiết kế `/health`
của chính mình sai chỗ nào).
