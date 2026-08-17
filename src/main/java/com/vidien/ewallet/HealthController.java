package com.vidien.ewallet;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint duy nhất cần thiết để biết "deploy đã sống chưa".
 *
 * <p>Mục đích không phải trả về "ok" cho vui. Khi deploy lên Render mà nó đổ,
 * mấy thông tin dưới đây trả lời ngay những câu hay hỏi nhất:
 * - dùng bản Java nào? (Render có thể dùng JDK khác máy mình)
 * - đang chạy profile nào? (đọc đúng file config chưa)
 * - nối được Neon chưa, mất bao lâu? (Neon ngủ -> lần gọi đầu rất chậm)
 */
@RestController
public class HealthController {

  private final String profile;
  private final JdbcTemplate jdbc;

  // Constructor injection - Spring tự truyền JdbcTemplate vào.
  // Không có @Autowired nào ở đây: một constructor duy nhất thì Spring tự hiểu.
  //
  // Giá trị sau dấu ":" là mặc định khi biến không tồn tại.
  // Thiếu dấu ":" mà biến không có -> Spring không khởi động nổi.
  public HealthController(
      @Value("${spring.profiles.active:local}") String profile, JdbcTemplate jdbc) {
    this.profile = profile;
    this.jdbc = jdbc;
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
    body.put("db", kiemTraDb());
    body.put("time", Instant.now().toString());
    return body;
  }

  /**
   * "SELECT 1" là cách rẻ nhất để hỏi database "còn sống không". Không đọc bảng
   * nào, không khoá gì, nhưng vẫn phải đi qua đủ mạng + TLS + xác thực - tức là
   * chứng minh được cả chuỗi kết nối lẫn mật khẩu đều đúng.
   */
  private Map<String, Object> kiemTraDb() {
    Map<String, Object> db = new LinkedHashMap<>();
    Instant batDau = Instant.now();
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      db.put("status", "ok");
      db.put("ms", Duration.between(batDau, Instant.now()).toMillis());
    } catch (Exception e) {
      // BẮT lỗi ở đây thay vì để nó bay ra ngoài - và đây là một quyết định
      // thiết kế, không phải nuốt lỗi cho tiện:
      //
      // Render gọi /health liên tục để biết app còn sống. Nếu Neon ngủ mà
      // endpoint này trả 500, Render kết luận app chết -> restart -> lại 500
      // -> restart mãi. Vòng lặp restart vì database ngủ, dù app hoàn toàn ổn.
      //
      // Nên: app "ok", db "lỗi". Tách hai điều đó ra là cố ý.
      db.put("status", "loi");
      db.put("ms", Duration.between(batDau, Instant.now()).toMillis());
      // getMostSpecificCause: Spring bọc lỗi qua nhiều lớp, lớp ngoài cùng
      // thường chỉ nói "không lấy được kết nối". Cái mình cần là nguyên nhân
      // trong cùng: sai mật khẩu? sai host? hết thời gian chờ?
      db.put("loi", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    return db;
  }
}
