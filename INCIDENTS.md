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

### ✅ Khoản nợ đã trả — 21/08

Sửa mật khẩu chỉ giải quyết *lần này*. Gốc vấn đề là: **liveness probe không được phụ
thuộc vào dịch vụ ngoài.** Render hỏi *"app còn sống không"*, mà `/health` lại đi hỏi Neon
hộ nó. Neon free **tự ngủ** khi không ai dùng → sẽ có ngày deploy lại đúng lúc Neon ngủ,
`/health` chậm, `Deploy failed` — mà chẳng có gì sai cả.

Đã tách hai loại ngày 21/08:

| Endpoint | Trả lời câu gì | Chạm DB? | Ai gọi | Đo với mật khẩu cố ý sai |
|---|---|---|---|---|
| `/ping` | app còn sống không | ❌ | Render (`healthCheckPath`) | **20ms** |
| `/health` | mọi thứ có ổn không | ✅ chờ tối đa 2s | người, lúc chẩn đoán | **2.01s** (trước: 10s) |

Nhưng việc bọc timeout ấy lại **đẻ ra một cái bẫy mới**, tệ hơn cái nó vá — xem mục 4.

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

---

## 4. Pool tự tạo ra mà không ai dùng — `CompletableFuture` lặng lẽ đẻ một OS thread cho mỗi request

| | |
|---|---|
| **Ngày** | 21/08/2026 |
| **Hiện tượng** | Không có. Đây là bẫy **chặn được trước khi nổ**, giống mục 2. Code vá timeout cho `/health` (mục 3) chạy đúng ở máy, test đúng, và mang một lỗi chỉ lộ ra trên container. |
| **Tìm ra bằng cách nào** | Thêm **tạm** một trường `thread` vào JSON của `/health` để xem query thật sự chạy ở đâu. Ra `"thread":"ForkJoinPool.commonPool-worker-1"` — trong khi trong file có sẵn một pool tên `db-health-check` **chưa từng chạy dòng nào**. Chạy lại với `-XX:ActiveProcessorCount=1` để giả lập container thì tên thread đổi thành `Thread-0`, và gọi bốn lần ra `Thread-1`, `Thread-2`, `Thread-3`, `Thread-4` — **mỗi request một OS thread mới**. |
| **Nguyên nhân gốc** | `CompletableFuture.supplyAsync(supplier)` thiếu tham số executor thì dùng `ASYNC_POOL`. Đọc thẳng `src.zip` của JDK 17: `USE_COMMON_POOL = (ForkJoinPool.getCommonPoolParallelism() > 1)` — **`> 1`, không phải `> 0`**. Container 1 nhân cho parallelism = 1, `1 > 1` là **sai**, nên JDK rơi xuống `ThreadPerTaskExecutor`, mà thân hàm của nó đúng một dòng: `new Thread(r).start()`. Không pool, không hàng đợi, không trần. |
| **Cách sửa** | Truyền executor tường minh: `supplyAsync(supplier, DB_CHECK_POOL)`, với `DB_CHECK_POOL` là `ThreadPoolExecutor(2, 2, …, new ArrayBlockingQueue<>(2), factory, AbortPolicy)` — thread daemon đặt tên `db-health-check`. |

### Điều phản trực giác — phần đáng kể nhất

**Cùng một dòng code, đúng ở máy 8 nhân, sai ở hộp 1 nhân.** Không phải "chậm hơn" —
mà đi vào một nhánh code hoàn toàn khác của JDK. Đo cạnh nhau:

| | Máy nhà | Ép `-XX:ActiveProcessorCount=1` |
|---|---|---|
| `availableProcessors()` | 8 | 1 |
| `getCommonPoolParallelism()` | 7 | 1 |
| `USE_COMMON_POOL` (`> 1`) | ✅ đúng | ❌ **sai** |
| Thread chạy query | `ForkJoinPool.commonPool-worker-1` | `Thread-1`, `Thread-2`, `Thread-3`… |
| Trần số thread | 7 | **không có** |

Và đây đúng loại chết đã gặp ở mục 2: thread tràn trong hộp 512MB thì kernel **OOM-kill**,
không stack trace, không log, chỉ thấy app tự restart.

### Hai điều tra được từ `src.zip`, cả hai đều ngược với cái mình tưởng

**a. `probe.cancel(true)` không dừng được query.** Javadoc `CompletableFuture.cancel`
ghi thẳng: *"this value has no effect in this implementation because interrupts are not
used to control processing"*. Nên hết 2 giây là **ngừng chờ**, không phải **ngừng chạy** —
thread vẫn bị giữ tới khi Hikari bỏ cuộc ở giây thứ 10. Cắt ngang không được thì phải
chặn ở **đầu vào**, đó mới là lý do pool phải có trần.

**b. `Executors.newFixedThreadPool(2)` KHÔNG giới hạn việc.** Nó là
`new ThreadPoolExecutor(n, n, 0L, MILLISECONDS, new LinkedBlockingQueue<>())` — hàng đợi
**không có trần**. Giới hạn số thread mà không giới hạn hàng đợi thì database chết là
task xếp hàng vô tận, im lặng. Có trần mới có backpressure.

### Đo sau khi sửa — bắn 6 request đồng thời, mật khẩu cố ý sai

| Đo | Kết quả |
|---|---|
| `jcmd <pid> Thread.print` | đúng **2** thread `db-health-check` (daemon) · **0** thread `Thread-N` · **0** `ForkJoinPool` |
| 6 request | 4 × `db.status=timeout` (2 chạy + 2 xếp hàng) · 2 × `db.status=busy` (bị từ chối ngay) |
| Mã HTTP | **cả 6 đều `200`** — không cái nào thành `500` |

Nhánh `busy` là chỗ dễ bỏ sót nhất: `AbortPolicy` ném `RejectedExecutionException`, mà nó
là `RuntimeException` — không bắt thì nó bay lên thành **HTTP 500**, đúng cái `/health`
được viết ra để tránh. Ba `catch` ban đầu (`Timeout`, `Execution`, `Interrupted`) đều
không đỡ được nó vì nó ném lúc **nộp việc**, chưa tới lúc **chờ việc**.

### Chỗ phải nói trung thực

Con số 1 nhân là **giả lập bằng `-XX:ActiveProcessorCount=1`, chưa đo trên Render**.
Chưa biết Render free báo `availableProcessors()` là mấy — nếu nó báo ≥ 3 thì
`USE_COMMON_POOL` đúng và bẫy `ThreadPerTaskExecutor` không bật.

Vẫn sửa, vì hai lý do độc lập với số nhân:

1. `commonPool` là pool **dùng chung của cả JVM**, sinh ra cho việc ngốn CPU. Ném vào đó
   một việc ngồi chờ mạng 10 giây là chiếm chỗ của mọi `parallelStream` và mọi
   `CompletableFuture` khác trong app — bây giờ chưa có, Week 3 trở đi thì có.
2. Kiểu chết khi đoán sai là kiểu **không có log** (mục 2 đã trả giá một lần).

Cách đo khi cần chốt: thêm một dòng log `Runtime.getRuntime().availableProcessors()` lúc
khởi động rồi đọc log Render — rẻ, làm lúc deploy lần tới.

### Tái hiện

```bash
# 1. Xem query chạy ở thread nào: thêm tạm vào checkDatabase()
#    result.put("thread", Thread.currentThread().getName())  -> trong supplier

# 2. Giả lập container 1 nhân
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:ActiveProcessorCount=1"

# 3. So hai con so quyet dinh - file roi, chay bang single-file source (JDK 11+)
cat > Fjp.java <<'EOF'
import java.util.concurrent.ForkJoinPool;
public class Fjp {
  public static void main(String[] a) {
    System.out.println(Runtime.getRuntime().availableProcessors()
        + " nhan -> getCommonPoolParallelism() = " + ForkJoinPool.getCommonPoolParallelism());
  }
}
EOF
java Fjp.java                            # 8 nhan -> 7  => "> 1" DUNG
java -XX:ActiveProcessorCount=1 Fjp.java # 1 nhan -> 1  => "> 1" SAI

# 4. Ép nhánh busy: mật khẩu sai + bắn 6 request cùng lúc
SPRING_DATASOURCE_PASSWORD=co-y-sai ./mvnw spring-boot:run
for i in 1 2 3 4 5 6; do curl -s localhost:3003/health & done

# 5. Đếm thread thật
jcmd <pid> Thread.print | grep -E '^"(db-health-check|Thread-|ForkJoinPool)'
```

### 🎤 Câu chuyện phỏng vấn

> *"Em bọc timeout cho health check bằng `CompletableFuture.supplyAsync`, có tạo hẳn một
> thread pool riêng. Chạy ở máy thì đúng. Nhưng em thêm tạm tên thread vào response để
> kiểm tra, và thấy query chạy ở `ForkJoinPool.commonPool-worker-1` — cái pool em tạo ra
> chưa từng chạy dòng nào, vì em quên truyền nó vào `supplyAsync`. Em ép JVM về 1 nhân cho
> giống container thì tên thread đổi thành `Thread-1`, `Thread-2`, `Thread-3` — mỗi request
> một OS thread mới. Đọc `src.zip` của JDK thì thấy điều kiện là
> `getCommonPoolParallelism() > 1`, dấu lớn hơn 1 chứ không phải lớn hơn 0, nên container
> 1 nhân rơi vào nhánh `ThreadPerTaskExecutor` gọi thẳng `new Thread().start()`, không
> trần. Trong hộp 512MB thì đó là đường dẫn tới OOM-kill không có log. Em sửa bằng
> `ThreadPoolExecutor` có hàng đợi chặn — cố ý không dùng `newFixedThreadPool` vì hàng đợi
> của nó vô hạn — và bắt thêm `RejectedExecutionException`, vì nó ném lúc nộp việc nên ba
> catch cũ không đỡ được và sẽ thành HTTP 500. Chỗ em phải nói thật là em mới giả lập 1
> nhân ở máy, chưa đo trên Render."*

Không bịa được: có con số (8/7 so với 1/1, 6 request ra 4 + 2), có cách phát hiện (in tên
thread ra rồi ép số nhân), có chỗ đọc source thay vì đoán, và có phần tự nhận cái chưa đo.

---

## 5. Docker chạy, test vẫn `Skipped: 8`, và build vẫn `SUCCESS`

| | |
|---|---|
| **Ngày** | 29/08/2026 |
| **Hiện tượng** | **Không có hiện tượng nào cả — đó chính là sự cố.** Mang code về máy Windows có Docker để chạy 8 integration test lần đầu. Docker Desktop bật, `docker ps` xanh, `docker run --rm hello-world` exit 0. Chạy `./mvnw verify` ra `Tests run: 8, Failures: 0, Errors: 0, **Skipped: 8**` kèm `BUILD SUCCESS` — **y hệt kết quả trên máy Mac không có Docker**. |
| **Tìm ra bằng cách nào** | **Đọc số lượng test, không đọc màu build.** `BUILD SUCCESS` là thật, và nếu dừng ở đó thì đã tick xong ô checkbox dựa trên 8 test chưa bao giờ chạy. Đào tiếp vào log Failsafe thấy `Could not find a valid Docker environment`, cả hai strategy của Testcontainers đều ăn **HTTP 400**. Thử thêm ba đường đều không ăn: `DOCKER_HOST` trỏ thẳng `npipe:////./pipe/dockerDesktopLinuxEngine`, `DOCKER_API_VERSION=1.55`, và nâng lên `1.21.4` — bản này **kéo đúng docker-java 3.4.2** như cũ. Dò `javap` vào jar mới thấy bản có docker-java 3.7.1 là **Testcontainers 2.0.5**. |
| **Nguyên nhân gốc** | Testcontainers **1.21.3** (giữa 2025) không bắt tay được với **Docker Engine 29.7.2 / API 1.55** (08/2026). `docker` CLI chạy được vì nó **đọc context** (`desktop-linux`); Testcontainers thì **hardcode** `//./pipe/docker_engine`. Nhưng đó mới là nửa đầu. Nửa sau nguy hiểm hơn: Testcontainers **dịch HTTP 400 thành "không có Docker"**, và `@Testcontainers(disabledWithoutDocker = true)` biến điều đó thành một dòng `Skipped` — tức là **một lỗi hạ tầng bị hạ cấp thành một dòng không ai đọc**. |
| **Cách chữa** | Nâng `testcontainers-bom` `1.21.3 → 2.0.5` (đổi 3 dòng `pom.xml`, không phải sửa một import nào). Và thêm `DockerRequiredIT`: khi `REQUIRE_DOCKER=true` mà không tìm thấy Docker thì **build ĐỎ**, kèm thông báo chỉ thẳng vào ba bước kiểm. Không sửa được `disabledWithoutDocker` thành có điều kiện — nó là hằng số **lúc biên dịch**. |

**Và test chạy được rồi thì 4 cái ĐỎ** — một lỗi có sẵn trong bộ test từ ngày viết ra:
`@Container` dừng container sau **mỗi lớp con**, còn Spring thì **cache application context**,
nên lớp thứ hai nhận một Hikari pool trỏ vào container đã chết (`Connection to localhost:56700
refused`). Sửa bằng singleton container.

> 💡 **Câu để lại:** *"Một bộ test chưa chạy thì không phải là test — nó là mã nguồn có hình
> dạng của test."*

📌 **Bài học rộng hơn, và nó lặp lại BA lần trong đúng một ngày:** thư viện cũ **chạy được**
với hạ tầng mới, chỉ là sai.

| | Cũ | Mới | Triệu chứng |
|---|---|---|---|
| Testcontainers | 1.21.3 | Docker Engine 29 | `Skipped`, build xanh |
| Jackson | 2 (`com.fasterxml.jackson.databind`) | 3 (`tools.jackson.databind`) | `package does not exist` |
| springdoc | 2.9.0 | Spring Boot 4 | khởi động được, `/v3/api-docs` trả **500** |

**Đọc số phiên bản trước khi đọc hướng dẫn.**

---

## 6. Cache chưa bao giờ chạy, và đoạn Javadoc cảnh báo về đúng chuyện đó nằm ngay phía trên

| | |
|---|---|
| **Ngày** | 29/08/2026 |
| **Hiện tượng** | Lại là **không có hiện tượng**. Thêm Redis + `@Cacheable` cho đường đọc số dư. App chạy, test xanh, Redis lên, không một dòng cảnh báo. Cache **chưa từng được dùng một lần nào**. |
| **Tìm ra bằng cách nào** | Một test cố tình **sửa số dư thẳng trong database, không qua service**, rồi đọc lại. Nếu cache đang chạy thì phải ra **số cũ**. Nó ra số mới: `expected: 100000.00 but was: 777.00`. Đây là cách duy nhất chứng minh cache thật sự chạy — đọc code rồi tin thì không chứng minh được gì. |
| **Nguyên nhân gốc** | `WalletCache` có một hàm bọc `find()` gọi `this.read()` cho gọn. Người gọi vào `find()` thì **đi qua proxy**, nhưng `find()` **không mang annotation nào**; còn lời gọi `read()` bên trong nó là `this.read()` — **không còn đi qua proxy**. Spring bỏ qua `@Cacheable` hoàn toàn. Đoạn Javadoc ở **đầu chính file đó** giải thích đúng cái bẫy này, và bẫy vẫn xảy ra ở cuối file. |
| **Cách chữa** | Bỏ hàm bọc, gọi thẳng `cache.read()` từ `WalletService`. **Bài học tinh hơn cái đã biết:** *"để `@Cacheable` ở bean riêng"* **chưa đủ** — phải là **một lời gọi từ bean khác đến ĐÚNG method mang annotation**. Một lớp bọc mỏng đặt giữa, kể cả nằm trong chính bean đó, là đủ để vô hiệu hoá. |

**Và cùng buổi đó, `cacheManager.getCache(...).clear()` cũng không xoá gì cả:**

```
TRƯỚC clear, get(viA) = ValueWrapper for [Wallet@43717598]
SAU   clear, get(viA) = ValueWrapper for [Wallet@7ef432ce]    <- vẫn còn
SAU   evict, get(viA) = null                                    <- evict thì xoá thật
```

Địa chỉ đối tượng **đổi giữa hai lần đọc** → nó thật sự được dựng lại từ Redis. *(Chưa truy ra
cơ chế — chỉ ghi hành vi đã đo, và đổi cách viết theo nó.)* Dòng đó đang làm nhiệm vụ **cô lập
test**, nên các test đã nhìn thấy bản cache của nhau.

> 💡 **Câu để lại:** *"Ba lần trong một ngày, thứ hỏng đều là một câu lệnh trông như đang làm
> việc mà không làm gì. Cách phát hiện luôn giống nhau: **phá dữ liệu sau lưng nó rồi hỏi
> lại**."*

---

## 7. `OutOfMemoryError: Metaspace` — và thứ bị gọi tên không phải thủ phạm

| | |
|---|---|
| **Ngày** | 03/09/2026 |
| **Hiện tượng** | Deploy sau khi thêm Redis vào Blueprint. Render báo `No open ports detected` rồi `Port scan timeout reached` — tiến trình **còn sống** nhưng không mở cổng nào. Log phía trên: `BeanCreationException: Error creating bean with name 'redisConnectionFactory' ... : Metaspace` / `Caused by: java.lang.OutOfMemoryError: Metaspace`. |
| **Tìm ra bằng cách nào** | Chẩn đoán đầu tiên **sai**, và cái sửa nó là một phép đo. Đọc `redisConnectionFactory` rồi kết luận "Lettuce + Netty nạp thêm class → vượt trần 96m". Theo hướng đó thì `CACHE_TYPE=none` phải cứu được — **đã thử, đổ y hệt**. Đó là dữ kiện bác bỏ. Nên đo thay vì cãi: cùng một bản jar, cùng một máy, chỉ đổi một biến → `none` **114.6 MB**, `redis` **114.8 MB**. Redis tốn **0.2 MB**. |
| **Nguyên nhân gốc** | App cần **~115 MB** metaspace, trần đặt **96 MB** trong `Dockerfile` từ hồi app còn nhỏ. Nó đã **vượt trần từ trước**, và vượt dần suốt một tháng: JPA entity, MapStruct mapper, springdoc, KYC, đối soát — mỗi thứ thêm vài nghìn class. `redisConnectionFactory` chỉ là **bean tình cờ được dựng đúng lúc chạm trần**. Và `CACHE_TYPE` vô tác dụng vì `DataRedisAutoConfiguration` điều kiện là `@ConditionalOnClass`, **không phải** `@ConditionalOnProperty` — có `spring-data-redis` trên classpath là Lettuce nạp hết, bất kể cờ. |
| **Cách chữa** | **Chuyển ngân sách, không xin thêm.** Heap đặt 300m nhưng đo thật chỉ dùng **73 MB** — Hikari tối đa 5 kết nối, phân trang chặn 100 dòng, không chỗ nào đọc cả bảng lên RAM. Bớt heap xuống `176m`, nâng metaspace lên `160m`. Thêm ba trần trước đây để mặc định: `ReservedCodeCacheSize=48m`, `MaxDirectMemorySize=32m`, `-Xss512k`. Tomcat từ 200 thread xuống 20. Đo lại bằng đúng cờ production: **297 MB / 512 MB**. |

### Vì sao `-Xmx300m` không cứu được

Metaspace **nằm ngoài heap**. Hai vùng riêng, hai trần riêng:

| Vùng | Chứa gì | Trần |
|---|---|---|
| Heap | đối tượng — `Wallet`, `BigDecimal`, response | `-Xmx` |
| **Metaspace** | **metadata của class** — tên method, chữ ký, annotation | `-XX:MaxMetaspaceSize` |

App có 300 MB heap rảnh rỗi mà vẫn chết, vì chỗ chật là chỗ khác.

### Hai loại "hết bộ nhớ" khác hẳn nhau

| | Ai giết | Dấu hiệu |
|---|---|---|
| `OutOfMemoryError: Metaspace` | **JVM tự báo** | có stack trace, đọc được |
| OOM-kill của kernel | **hệ điều hành** | không log, không trace, chỉ thấy restart |

Lần này là loại thứ nhất — **may**. Sự cố số 2 trong file này là loại thứ hai, và nó tốn nhiều thời gian hơn hẳn.

⚠️ Đó cũng là lý do lần này thêm `MaxDirectMemorySize=32m`: Netty (nền của Lettuce) cấp phát buffer **ngoài heap** và mặc định bằng `-Xmx`. Không chặn thì chỗ đó phình âm thầm cho tới khi kernel giết container — tức là **biến một lỗi đọc được thành một lỗi không đọc được**.

### Cách đo, chép lại chạy được

```bash
# Nới trần lên thật cao để ĐO ĐƯỢC ĐỈNH, không phải để chạy như vậy
java -Xmx176m -XX:MaxMetaspaceSize=400m -XX:+UseSerialGC -jar target/ewallet-api-*.jar &
PID=$!

# Đợi app lên HẲN - đo sớm là đo thiếu, class nạp dần theo request đầu tiên
until curl -s -o /dev/null --max-time 2 http://localhost:3003/ping; do sleep 3; done

jcmd $PID GC.heap_info | grep Metaspace
#   Metaspace  used 114841K, committed 115520K, reserved 262144K

# Bức tranh đầy đủ - cần -XX:NativeMemoryTracking=summary lúc khởi động
jcmd $PID VM.native_memory summary | grep -E "^Total|Java Heap \(|Class \(|Thread \(|Code \("
#   Total: reserved=582094KB, committed=296822KB
```

📌 **`committed` mới là RAM đã thực sự chiếm.** `reserved` chỉ là không gian địa chỉ JVM giữ chỗ — nhìn `reserved=582MB` mà hoảng là hiểu nhầm; con số phải so với 512MB là `committed`.

### 💡 Câu để lại

> **Stack trace gọi tên giọt nước cuối cùng, không gọi tên cái bình đầy.**

Cùng hình dạng với **sự cố số 10 của `PROGRESS.md`** *(25/08, `create_at`)*: stack trace đổ tội cho một câu SQL đúng, vì nó chỉ biết chỗ **phát hiện** ra lỗi, không biết chỗ **gây** ra lỗi.

Và một câu nữa, rút từ chỗ `CACHE_TYPE=none` không cứu được:

> **Một cờ tắt tính năng tắt HÀNH VI, nó không tắt việc NẠP CLASS.**

📌 Bài học vận hành: khi một giới hạn được đặt bằng con số cứng — `MaxMetaspaceSize`, `maximum-pool-size`, `max-file-size` — thì **con số đó có tuổi**. Nó đúng vào ngày đặt và mục ruỗng dần theo mỗi thư viện được thêm vào. Không có gì nhắc, cho tới ngày nó đổ.
