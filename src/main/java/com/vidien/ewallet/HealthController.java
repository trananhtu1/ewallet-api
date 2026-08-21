package com.vidien.ewallet;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hai endpoint, hai câu hỏi khác nhau - đừng gộp làm một.
 *
 * <p>{@code /ping} trả lời "tiến trình còn sống không". KHÔNG chạm database.
 * Đây là cái Render gọi liên tục (healthCheckPath). Health check của nền tảng mà
 * đi hỏi dịch vụ ngoài thì dịch vụ ngoài ngủ là app bị khai tử oan - đúng thứ đã
 * làm lần deploy đầu 20/08 đổ, xem INCIDENTS.md mục 3.
 *
 * <p>{@code /health} trả lời "mọi thứ có ổn không": Java nào, profile nào, Neon
 * nối được chưa và mất bao lâu. Dành cho người, lúc chẩn đoán. Được phép chạm
 * database - nhưng chỉ chờ tối đa 2 giây.
 */
@RestController
public class HealthController {

  /** Chờ database tối đa bấy nhiêu rồi bỏ cuộc, ngắn hơn hẳn connection-timeout 10s của Hikari. */
  private static final int DB_TIMEOUT_SECONDS = 2;

  /**
   * Pool riêng để chạy phép thử database. Ba chi tiết ở đây đều là quyết định, không phải mặc định:
   *
   * <p>1. KHÔNG dùng executor mặc định của CompletableFuture. Gọi
   * {@code supplyAsync(supplier)} thiếu tham số thứ hai thì việc chạy ở
   * {@code ForkJoinPool.commonPool()} - pool dùng chung của cả JVM, sinh ra cho
   * việc ngốn CPU, không phải cho việc ngồi chờ mạng 10 giây. Tệ hơn nữa:
   * {@code CompletableFuture.USE_COMMON_POOL} là {@code getCommonPoolParallelism() > 1},
   * mà container 1 nhân cho parallelism = 1, nên điều kiện SAI và JDK rơi xuống
   * {@code ThreadPerTaskExecutor} - đẻ một OS thread mới cho MỖI lần gọi, không
   * trần, không hàng đợi. Đã đo tại máy bằng -XX:ActiveProcessorCount=1: bốn lần
   * gọi /health ra bốn thread Thread-1..Thread-4.
   *
   * <p>2. KHÔNG dùng {@code Executors.newFixedThreadPool}. Nó gắn sẵn
   * {@code LinkedBlockingQueue} KHÔNG giới hạn: database chết thì task xếp hàng
   * vô tận, im lặng, không ai biết. Hàng đợi phải có trần thì mới có backpressure.
   *
   * <p>3. Quá tải thì TỪ CHỐI NGAY (AbortPolicy) thay vì chờ. Một phép thử sức
   * khoẻ mà phải xếp hàng thì con số đo được đã vô nghĩa rồi.
   */
  private static final Executor DB_CHECK_POOL = new ThreadPoolExecutor(
      2, 2, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(2),
      runnable -> {
        Thread thread = new Thread(runnable, "db-health-check");
        // Daemon: thread này không được phép giữ JVM sống lúc app muốn tắt.
        thread.setDaemon(true);
        return thread;
      },
      new ThreadPoolExecutor.AbortPolicy());

  private final String profile;
  private final JdbcTemplate jdbcTemplate;

  // Constructor injection - Spring tự truyền JdbcTemplate vào.
  // Không có @Autowired nào ở đây: một constructor duy nhất thì Spring tự hiểu.
  //
  // Giá trị sau dấu ":" là mặc định khi biến không tồn tại.
  // Thiếu dấu ":" mà biến không có -> Spring không khởi động nổi.
  public HealthController(
      @Value("${spring.profiles.active:local}") String profile, JdbcTemplate jdbcTemplate) {
    this.profile = profile;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Liveness probe. Không I/O, không database, không phụ thuộc gì bên ngoài.
   * Trả lời được câu này tức là tiến trình còn sống và Tomcat còn nhận request -
   * đúng và đủ những gì Render cần biết.
   */
  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("status", "ok");
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    // LinkedHashMap để giữ đúng thứ tự key khi trả JSON.
    // Map.of() KHÔNG giữ thứ tự - đọc log trên production sẽ loạn.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "ok");
    body.put("service", "ewallet-api");
    body.put("profile", profile);
    body.put("java", System.getProperty("java.version"));
    body.put("db", checkDatabase());
    body.put("time", Instant.now().toString());
    return body;
  }

  /**
   * "SELECT 1" là cách rẻ nhất để hỏi database "còn sống không". Không đọc bảng
   * nào, không khoá gì, nhưng vẫn phải đi qua đủ mạng + TLS + xác thực - tức là
   * chứng minh được cả chuỗi kết nối lẫn mật khẩu đều đúng.
   *
   * <p>Mọi lối ra của hàm này đều trả về bình thường, không ném lỗi lên trên.
   * Đó là chủ ý: /health nói "app ok, db error", chứ không trả 500 - trả 500 thì
   * nền tảng kết luận app chết và restart vô hạn dù chỉ có Neon đang ngủ.
   */
  private Map<String, Object> checkDatabase() {
    Map<String, Object> result = new LinkedHashMap<>();
    Instant startedAt = Instant.now();
    try {
      CompletableFuture<Integer> probe = CompletableFuture.supplyAsync(
          () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class), DB_CHECK_POOL);

      // Hết 2 giây là NGỪNG CHỜ, không phải NGỪNG CHẠY. Query vẫn nằm đó tới khi
      // Hikari bỏ cuộc ở giây thứ 10, giữ nguyên một thread trong pool suốt thời
      // gian ấy. probe.cancel(true) KHÔNG cứu được: javadoc CompletableFuture ghi
      // rõ "mayInterruptIfRunning ... has no effect in this implementation".
      // Vì cắt ngang không được nên mới phải chặn ở đầu vào: pool 2 thread, hàng
      // đợi 2 chỗ, đầy thì từ chối.
      probe.get(DB_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      result.put("status", "ok");
      result.put("ms", elapsedMs(startedAt));
    } catch (TimeoutException e) {
      // Database KHÔNG trả lời kịp. App vẫn khoẻ - phân biệt rõ hai chuyện.
      result.put("status", "timeout");
      result.put("ms", elapsedMs(startedAt));
      result.put("error", "Database khong tra loi trong " + DB_TIMEOUT_SECONDS + "s");
    } catch (ExecutionException e) {
      // Truy vấn NÉM LỖI (sai mật khẩu, mất mạng...). CompletableFuture bọc lỗi
      // gốc vào ExecutionException, nên phải getCause() mới ra lỗi thật.
      // Quên getCause() thì log ra "ExecutionException: null" - vô dụng.
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      result.put("status", "error");
      result.put("ms", elapsedMs(startedAt));
      result.put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
    } catch (InterruptedException e) {
      // Luồng bị yêu cầu dừng giữa chừng. Bắt InterruptedException mà không
      // bật lại cờ interrupt là XOÁ tín hiệu dừng - phần còn lại của chương
      // trình không biết là có ai đã bảo dừng. Luôn gọi lại interrupt().
      Thread.currentThread().interrupt();
      result.put("status", "error");
      result.put("ms", elapsedMs(startedAt));
      result.put("error", "Bi ngat khi dang kiem tra database");
    } catch (RejectedExecutionException e) {
      // Pool đầy: đang có phép thử treo và hàng đợi hết chỗ. Nghĩa là database
      // đang hỏng chứ không phải app hỏng, nên nói thẳng ra thay vì để lỗi này
      // bay lên thành HTTP 500 - đúng cái /health được viết ra để tránh.
      result.put("status", "busy");
      result.put("ms", elapsedMs(startedAt));
      result.put("error", "Dang co phep thu database khac chua xong");
    }
    return result;
  }

  private long elapsedMs(Instant startedAt) {
    return Duration.between(startedAt, Instant.now()).toMillis();
  }
}
