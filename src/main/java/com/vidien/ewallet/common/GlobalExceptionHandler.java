package com.vidien.ewallet.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.vidien.ewallet.auth.EmailAlreadyUsedException;
import com.vidien.ewallet.auth.InvalidCredentialsException;
import com.vidien.ewallet.wallet.InsufficientFundsException;
import com.vidien.ewallet.wallet.SameWalletTransferException;
import com.vidien.ewallet.wallet.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Bắt lỗi TẬP TRUNG cho toàn bộ controller.
 *
 * <p>
 * Không có lớp này thì mỗi controller phải tự try/catch, và mỗi chỗ sẽ trả về một hình dạng lỗi hơi
 * khác nhau. Frontend khi đó phải xử lý N kiểu lỗi.
 *
 * <p>
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody: giá trị trả về được Jackson biến thành
 *                       JSON, không phải tên của một trang HTML.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        /**
         * @Valid trượt. Spring ném đúng exception này, và nó mang theo BindingResult - danh sách
         *        field nào trượt luật nào.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(

                        MethodArgumentNotValidException e, HttpServletRequest request) {
                // getFieldErrors() trả về FieldError CỦA SPRING (org.springframework.validation).
                // Ta đổi sang FieldError của mình để không lộ kiểu nội bộ của Spring ra API.
                List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors()
                                .stream()
                                .map(springError -> new ErrorResponse.FieldError(
                                                springError.getField(),
                                                springError.getDefaultMessage()))
                                .toList();

                ErrorResponse body = ErrorResponse.of(400, "VALIDATION_FAILED",
                                "Dữ liệu gửi lên không hợp lệ", request.getRequestURI(),
                                fieldErrors);

                return ResponseEntity.badRequest().body(body);
        }

        /**
         * JSON hỏng, hoặc sai KIỂU dữ liệu (gửi "abc" vào chỗ cần số).
         *
         * <p>
         * Chỗ này phản trực giác: đây KHÔNG phải lỗi validation. Jackson chết ngay lúc dựng object,
         * nên @Valid còn chưa kịp chạy. Không có handler này thì client nhận về một hình dạng lỗi
         * khác hẳn - đúng bẫy 3 trong bài giảng.
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                        HttpServletRequest request) {

                // Chỉ log ở mức WARN: đây là lỗi của client, không phải app hỏng.
                log.warn("Body khong doc duoc tai {}: {}", request.getRequestURI(),
                                e.getMostSpecificCause().getMessage());

                ErrorResponse body = ErrorResponse.of(400, "MALFORMED_JSON",
                                "Body gửi lên không đọc được", request.getRequestURI());

                return ResponseEntity.badRequest().body(body);
        }

        /**
         * Sai KIỂU ở tham số URL: /api/wallets/abc trong khi {id} cần số.
         *
         * <p>
         * Chết ở bước Spring chuyển chuỗi thành long, TRƯỚC khi thân controller chạy. Cùng họ với
         * HttpMessageNotReadableException ở trên: cái chết lúc đang dựng tham số thì @Valid không
         * tới lượt.
         */
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException e, HttpServletRequest request) {
                // WARN, không ERROR, và KHÔNG kèm stack trace: lỗi của client, app vẫn khoẻ.
                log.warn("Tham so '{}' sai kieu tai {}: nhan duoc \"{}\"", e.getName(),
                                request.getRequestURI(), e.getValue());

                ErrorResponse body = ErrorResponse.of(400, "INVALID_PARAMETER",
                                "Tham số '" + e.getName() + "' phải là số nguyên",
                                request.getRequestURI());

                return ResponseEntity.badRequest().body(body);
        }

        /**
         * Lưới chắn cuối - mọi thứ không ai bắt đều rơi vào đây.
         *
         * <p>
         * ĐÂY LÀ RANH GIỚI BẢO MẬT, và là chỗ đúng/sai rõ rệt nhất cả file: log ĐẦY ĐỦ ở server (có
         * cả stack trace), nhưng response chỉ nói "Lỗi hệ thống".
         *
         * <p>
         * Ném nguyên e.getMessage() ra ngoài là tặng người lạ tên bảng, tên cột, đường dẫn file
         * trên máy chủ, đôi khi cả chuỗi kết nối. Chi tiết ở lại server.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleUnexpected(Exception e,
                        HttpServletRequest request) {

                // Tham số CUỐI là exception -> SLF4J in cả stack trace. Nếu viết
                // log.error("...", e.getMessage()) thì mất stack trace, gỡ lỗi bằng niềm tin.
                log.error("Loi khong luong truoc tai {}", request.getRequestURI(), e);

                ErrorResponse body = ErrorResponse.of(500, "INTERNAL_ERROR", "Lỗi hệ thống",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        /**
         * Vi khong ton tai. Nem tu tang service, dich sang 404 o day.
         *
         * <p>
         * Chi tiet id nam trong e.getMessage() va o lai server: response chi noi "khong tim
         * thay vi". Bien nay khong nhay cam lam, nhung giu dung mot luat cho ca file thi khong
         * phai nho ngoai le.
         */
        @ExceptionHandler(WalletNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException e,
                        HttpServletRequest request) {
                log.warn("{} tai {}", e.getMessage(), request.getRequestURI());

                ErrorResponse body = ErrorResponse.of(404, "WALLET_NOT_FOUND",
                                "Không tìm thấy ví", request.getRequestURI());

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        /**
         * So du khong du. 409 CONFLICT chu khong phai 400: body gui len HOP LE hoan toan, chi
         * la trang thai hien tai cua he thong khong cho phep. 400 se khien client tuong minh
         * gui sai va sua body - vo ich.
         *
         * <p>
         * So du that KHONG ra ngoai response: no la thong tin cua chu vi, con nguoi goi API thi
         * chua chac la chu vi (chua co JWT). Chi tiet o lai log.
         */
        @ExceptionHandler(InsufficientFundsException.class)
        public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e,
                        HttpServletRequest request) {
                log.warn("{} tai {}", e.getMessage(), request.getRequestURI());

                ErrorResponse body = ErrorResponse.of(409, "INSUFFICIENT_FUNDS",
                                "Số dư không đủ để thực hiện giao dịch", request.getRequestURI());

                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        /** Chuyen tien cho chinh minh: body sai that, nen 400. */
        @ExceptionHandler(SameWalletTransferException.class)
        public ResponseEntity<ErrorResponse> handleSameWallet(SameWalletTransferException e,
                        HttpServletRequest request) {
                ErrorResponse body = ErrorResponse.of(400, "SAME_WALLET",
                                "Ví nguồn và ví đích phải khác nhau", request.getRequestURI());

                return ResponseEntity.badRequest().body(body);
        }

        /** Email da co nguoi dung. 409 CONFLICT: body dung, chi la trang thai he thong khong cho. */
        @ExceptionHandler(EmailAlreadyUsedException.class)
        public ResponseEntity<ErrorResponse> handleEmailTaken(EmailAlreadyUsedException e,
                        HttpServletRequest request) {
                ErrorResponse body = ErrorResponse.of(409, "EMAIL_ALREADY_USED",
                                "Email này đã được đăng ký", request.getRequestURI());

                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        /**
         * Sai email hoac sai mat khau - MOT thong bao duy nhat cho ca hai.
         *
         * <p>
         * Tach ra thanh "email khong ton tai" va "mat khau sai" la tang cho ke tan cong mot
         * cong cu do xem email nao co that trong he thong (user enumeration). Log cung chi
         * ghi email, khong bao gio ghi mat khau vua nhap.
         */
        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleBadCredentials(InvalidCredentialsException e,
                        HttpServletRequest request) {
                log.warn("Dang nhap that bai tai {}", request.getRequestURI());

                ErrorResponse body = ErrorResponse.of(401, "INVALID_CREDENTIALS",
                                "Email hoặc mật khẩu không đúng", request.getRequestURI());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
}
