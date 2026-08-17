package com.vidien.ewallet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cho phép frontend ở origin khác gọi API này.
 *
 * <p>Vì sao KHÔNG dùng @CrossOrigin("http://localhost:5173") gắn lên controller:
 * nó hardcode địa chỉ localhost vào source. Đến lúc frontend chạy trên domain
 * Vercel thì phải sửa code + build lại + deploy lại chỉ để đổi một chuỗi. Đọc
 * danh sách origin từ biến môi trường thì cùng một file .jar chạy được cả local
 * lẫn production, chỉ khác giá trị biến.
 *
 * <p>Nhắc lại điều dễ hiểu sai: cấu hình này KHÔNG "mở khoá" cho ai cả. Server
 * vốn đã trả dữ liệu cho mọi người gọi (curl chứng minh điều đó). Việc nó làm là
 * thêm header Access-Control-Allow-Origin để TRÌNH DUYỆT chịu đưa dữ liệu cho
 * JavaScript. Ai muốn lấy dữ liệu mà không qua trình duyệt thì vẫn lấy được như
 * cũ - CORS không phải lớp bảo vệ server.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  // Chuỗi phân cách bằng dấu phẩy -> Spring tự tách thành String[].
  // Ví dụ: CORS_ALLOWED_ORIGINS=http://localhost:5173,https://ewallet-web.vercel.app
  public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // Để trống biến -> không đăng ký gì -> lỗi CORS quay lại y như cũ.
    // Giữ đường này để còn tái hiện được sự cố mà xem lại, không phải để cho vui.
    if (allowedOrigins.length == 0 || allowedOrigins[0].isBlank()) {
      return;
    }

    registry
        .addMapping("/**")
        // Liệt kê đúng origin, KHÔNG dùng "*".
        //
        // Lý do thật, không phải vì "cho an toàn": "*" và allowCredentials(true)
        // là tổ hợp BỊ CẤM trong đặc tả CORS. Spring ném lỗi ngay lúc khởi động.
        // Sang Week 3 làm JWT bằng cookie httpOnly là phải bật credentials, lúc
        // đó "*" chết. Liệt kê rõ từ đầu thì không phải sửa lại.
        // (Cần khớp theo mẫu thì dùng allowedOriginPatterns, ví dụ
        //  "https://*.vercel.app" cho các bản preview deploy.)
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // Trình duyệt CHỈ cho JavaScript đọc vài header mặc định. Header tự đặt
        // muốn đọc được thì phải khai ở đây. Week 4 làm phân trang, muốn đọc
        // tổng số bản ghi từ header thì nhớ chỗ này - nếu không sẽ ngồi tìm mãi
        // vì Network tab thấy header rõ ràng mà code đọc ra null.
        .exposedHeaders("Location")
        // Chưa dùng cookie nên để false. Week 3 quyết định lưu token ở đâu
        // (localStorage vs cookie httpOnly) mới đổi - xem ROADMAP Nhóm D.
        .allowCredentials(false)
        // Trình duyệt nhớ kết quả preflight trong 1 giờ, đỡ phải hỏi lại trước
        // mỗi request. Chỉ có tác dụng với request CÓ preflight.
        .maxAge(3600);
  }
}
