package com.vidien.ewallet.shared.exception;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.vidien.ewallet.auth.domain.exception.EmailAlreadyUsedException;
import com.vidien.ewallet.auth.domain.exception.InvalidCredentialsException;
import com.vidien.ewallet.auth.domain.exception.InvalidRefreshTokenException;
import com.vidien.ewallet.wallet.domain.exception.InsufficientFundsException;
import com.vidien.ewallet.wallet.domain.exception.NotYourWalletException;
import com.vidien.ewallet.wallet.domain.exception.SameWalletTransferException;
import com.vidien.ewallet.wallet.domain.exception.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import com.vidien.ewallet.shared.dto.ErrorResponse;

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
         * Ràng buộc trên THAM SỐ của controller trượt: @Positive trên {id} chẳng hạn.
         *
         * <p>
         * Ba loại lỗi "dữ liệu sai" giờ đã đủ bộ, và chúng chết ở ba chỗ khác nhau:
         *
         * <pre>
         * sai trong body, sai KIỂU     -> Jackson chết lúc dựng object -> HttpMessageNotReadable
         * sai trong body, sai LUẬT     -> @Valid trượt                 -> MethodArgumentNotValid
         * sai ở URL, sai KIỂU (abc)    -> type converter chết          -> MethodArgumentTypeMismatch
         * sai ở URL, sai LUẬT (-1)     -> method validation trượt      -> HandlerMethodValidation  &lt;- đây
         * </pre>
         *
         * <p>
         * KHÔNG cần @Validated trên controller. Từ Spring Framework 6.1, Spring MVC tự kiểm ràng
         * buộc trên tham số controller (không qua proxy AOP) - đã đo thật. Nếu gõ thêm @Validated
         * thì Spring đổi sang đường AOP cũ và ném ConstraintViolationException, tức là handler này
         * không bắt được nữa.
         *
         * <p>
         * ⚠️ Vì sao phải có handler này dù Spring ĐÃ ghi 400 sẵn trong exception: đo trước khi
         * viết, client nhận về 500 INTERNAL_ERROR. Tên exception ghi rõ {@code 400 BAD_REQUEST
         * "Validation failure"} nhưng handler Exception.class ở dưới bắt trước và hạ cấp nó.
         * Đây là LẦN THỨ HAI cùng một hình dạng - NoResourceFoundException (27/08) cũng bị lưới
         * chắn cuối biến 404 thành 500. Luật: lưới chắn cuối không phải chỗ an toàn, nó là chỗ
         * nguy hiểm nhất file.
         */
        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<ErrorResponse> handleParamValidation(
                        HandlerMethodValidationException e, HttpServletRequest request) {

                // Mỗi kết quả = một tham số trượt; mỗi tham số có thể trượt NHIỀU luật cùng lúc,
                // nên phải duyệt hai tầng. Cùng lý do fieldErrors là mảng chứ không phải map.
                //
                // Tên method là getParameterValidationResults(), KHÔNG phải
                // getAllValidationResults() như hầu hết bài viết trên mạng - cái tên đó là của
                // Spring 6.x và đã bị bỏ ở Spring 7 (Boot 4.1). Cách tìm ra: javap thẳng vào
                // spring-web-7.0.8.jar để đọc danh sách method, thay vì đoán.
                List<ErrorResponse.FieldError> fieldErrors = e.getParameterValidationResults()
                                .stream()
                                .flatMap(result -> result.getResolvableErrors().stream()
                                                .map(error -> new ErrorResponse.FieldError(
                                                                result.getMethodParameter()
                                                                                .getParameterName(),
                                                                error.getDefaultMessage())))
                                .toList();

                // WARN, không ERROR, và không kèm stack trace: lỗi của client, app vẫn khoẻ.
                log.warn("Tham so khong hop le tai {}: {}", request.getRequestURI(), fieldErrors);

                ErrorResponse body = ErrorResponse.of(400, "INVALID_PARAMETER",
                                "Tham số trên đường dẫn không hợp lệ", request.getRequestURI(),
                                fieldErrors);

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

        /**
         * Người gọi tự nhận đang dùng một ví không phải của họ. **403**, không phải 401.
         *
         * <p>
         * Khác nhau ở chỗ: <b>401 = tôi không biết anh là ai</b> (chưa đăng nhập, token hỏng,
         * token hết hạn) — đăng nhập lại thì được. <b>403 = tôi biết anh là ai, và câu trả lời
         * là không</b> — đăng nhập lại vô ích. Trả 401 ở đây sẽ khiến frontend đá người dùng
         * về màn đăng nhập cho một việc mà đăng nhập không cứu được.
         *
         * <p>
         * Log ở mức <b>WARN kèm chi tiết</b>, khác với các lỗi client khác: đây có thể là
         * frontend gõ nhầm, nhưng cũng có thể là một người đang dò ví của người khác. Đó là
         * dòng log đáng để lại dấu vết.
         */
        @ExceptionHandler(NotYourWalletException.class)
        public ResponseEntity<ErrorResponse> handleNotYourWallet(NotYourWalletException e,
                        HttpServletRequest request) {
                log.warn("Tu choi quyen tai {}: {}", request.getRequestURI(), e.getMessage());

                ErrorResponse body = ErrorResponse.of(403, "NOT_YOUR_WALLET",
                                "Ví nguồn không thuộc về tài khoản đang đăng nhập",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
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

        /**
         * Refresh token không dùng được. **401** — client phải cho người dùng đăng nhập lại.
         *
         * <p>
         * Bốn nguyên nhân, **một** thông báo: không tồn tại · hết hạn · đã thu hồi · **đã dùng
         * rồi**. Nói rõ *"token này đã bị dùng lại"* là xác nhận cho kẻ trộm rằng nó vừa bị
         * phát hiện, và là một gợi ý để lần sau làm nhanh tay hơn. Chi tiết ở lại `audit_log`
         * dưới sự kiện `REFRESH_TOKEN_REUSED`.
         *
         * <p>
         * Cùng nguyên tắc với `INVALID_CREDENTIALS` gộp chung sai email và sai mật khẩu.
         */
        @ExceptionHandler(InvalidRefreshTokenException.class)
        public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(
                        InvalidRefreshTokenException e, HttpServletRequest request) {
                log.warn("Refresh token khong hop le tai {}", request.getRequestURI());

                ErrorResponse body = ErrorResponse.of(401, "INVALID_REFRESH_TOKEN",
                                "Phiên đã hết hạn, vui lòng đăng nhập lại",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        /**
         * Duong dan khong ton tai. PHAI la 404, khong duoc de roi xuong handler Exception.
         *
         * <p>
         * Do tren production truoc khi sua: MOI duong dan bia ra deu tra 500 INTERNAL_ERROR.
         * Ba hau qua that:
         * <ul>
         * <li>5xx la nhom ma HTTP client va monitoring TU DONG THU LAI - bien mot cai go nham
         * URL thanh nhieu request vo ich.
         * <li>Moi lan bot quet lung tung la mot dong ERROR kem nguyen stack trace trong log.
         * Log day rac thi luc co su co that se khong tim thay gi.
         * <li>Frontend go sai duong dan se tuong server hong, di tim nham cho.
         * </ul>
         *
         * <p>
         * Nguyen nhan: handler @ExceptionHandler(Exception.class) o duoi la luoi chan cuoi, va
         * no bat luon ca NoResourceFoundException. Luoi chan cuoi khong biet phan biet "loi cua
         * server" voi "khach go sai dia chi" - phai noi ro cho no.
         */
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e,
                        HttpServletRequest request) {
                // DEBUG chu khong phai WARN: bot quet lung tung suot ngay, khong dang bao dong.
                log.debug("Khong co duong dan {}", request.getRequestURI());

                ErrorResponse body = ErrorResponse.of(404, "NOT_FOUND",
                                "Không tìm thấy đường dẫn này", request.getRequestURI());

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        /** Dung duong dan nhung sai dong tu (GET vao cho chi nhan POST). 405, khong phai 500. */
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
                        HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
                ErrorResponse body = ErrorResponse.of(405, "METHOD_NOT_ALLOWED",
                                "Phương thức " + e.getMethod() + " không dùng được ở đường dẫn này",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
        }
}
