package com.vidien.ewallet.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    /**
     * @Valid = câu lệnh "đọc sổ đi". Thiếu chữ này thì mọi annotation trong TransferRequest im lặng
     *        không chạy, và amount = -999999 đi thẳng xuống dưới.
     *
     *        <p>
     * @RequestBody = "đọc từ thân request, để Jackson dựng object". Thiếu chữ này thì Spring lại đi
     *              tìm dữ liệu ở query string, và mọi field sẽ là null.
     */
    @PostMapping
    public ResponseEntity<ErrorResponse> create(@Valid @RequestBody TransferRequest request,
            HttpServletRequest httpRequest) {
        // Dòng log này là BẰNG CHỨNG của cả bài học: nó chỉ in ra khi dữ liệu đã sạch.
        // Bắn amount = -5 thì sẽ KHÔNG thấy dòng này trong console - thân method không
        // hề chạy, Spring đã chặn từ trước khi vào đây.
        log.info("Valid da qua: {} => {} , so tien {}", request.fromWalletId(),
                request.toWalletId(), request.amount());
        ErrorResponse body = ErrorResponse.of(501, "NOT_IMPLEMENTED",
                "Chức năng chuyển tiền chưa được cài đặt", httpRequest.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);

    }
}
