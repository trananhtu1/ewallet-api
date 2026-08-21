package com.vidien.ewallet.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Bắt lỗi TẬP TRUNG cho toàn bộ controller.
 *
 * <p>
 * Không có lớp này thì mỗi controller phải tự try/catch, và mỗi chỗ sẽ trả về một hình dạng lỗi hơi
 * khác nhau. Frontend khi đó phải xử lý N kiểu lỗi.
 *
 * <p>
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody: giá trị trả về được Jackson biến thành
 * JSON, không phải tên của một trang HTML.
 */
@RestController
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * @Valid trượt. Spring ném đúng exception này, và nó mang theo BindingResult - danh sách field
     *        nào trượt luật nào.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(

            MethodArgumentNotValidException e, HttpServletRequest request) {
        // getFieldErrors() trả về FieldError CỦA SPRING (org.springframework.validation).
        // Ta đổi sang FieldError của mình để không lộ kiểu nội bộ của Spring ra API.
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(springError -> new ErrorResponse.FieldError(springError.getField(),
                        springError.getDefaultMessage()))
                .toList();

        ErrorResponse body = ErrorResponse.of(400, "VALIDATION_FAILED",
                "Dữ liệu gửi lên không hợp lệ", request.getRequestURI(), fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * JSON hỏng, hoặc sai KIỂU dữ liệu (gửi "abc" vào chỗ cần số).
     *
     * <p>
     * Chỗ này phản trực giác: đây KHÔNG phải lỗi validation. Jackson chết ngay lúc dựng object,
     * nên @Valid còn chưa kịp chạy. Không có handler này thì client nhận về một hình dạng lỗi khác
     * hẳn - đúng bẫy 3 trong bài giảng.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
            HttpServletRequest request) {

        // Chỉ log ở mức WARN: đây là lỗi của client, không phải app hỏng.
        log.warn("Body khong doc duoc tai {}: {}", request.getRequestURI(),
                e.getMostSpecificCause().getMessage());

        ErrorResponse body = ErrorResponse.of(400, "MALFORMED_JSON", "Body gửi lên không đọc được",
                request.getRequestURI());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Lưới chắn cuối - mọi thứ không ai bắt đều rơi vào đây.
     *
     * <p>
     * ĐÂY LÀ RANH GIỚI BẢO MẬT, và là chỗ đúng/sai rõ rệt nhất cả file: log ĐẦY ĐỦ ở server (có cả
     * stack trace), nhưng response chỉ nói "Lỗi hệ thống".
     *
     * <p>
     * Ném nguyên e.getMessage() ra ngoài là tặng người lạ tên bảng, tên cột, đường dẫn file trên
     * máy chủ, đôi khi cả chuỗi kết nối. Chi tiết ở lại server.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {

        // Tham số CUỐI là exception -> SLF4J in cả stack trace. Nếu viết
        // log.error("...", e.getMessage()) thì mất stack trace, gỡ lỗi bằng niềm tin.
        log.error("Loi khong luong truoc tai {}", request.getRequestURI(), e);

        ErrorResponse body =
                ErrorResponse.of(500, "INTERNAL_ERROR", "Lỗi hệ thống", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
