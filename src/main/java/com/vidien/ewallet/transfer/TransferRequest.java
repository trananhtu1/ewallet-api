package com.vidien.ewallet.transfer;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        // Long (bọc) chứ KHÔNG phải long (nguyên thuỷ) - đây là bẫy im lặng.
        // long không nhận null được, nên JSON thiếu hẳn trường này thì Jackson gán 0,
        // @NotNull thấy 0 -> hợp lệ -> lọt xuống service và đi tìm ví có id = 0.
        // Bọc thành Long thì thiếu trường = null = trượt ngay ở biên.
        //
        // @Positive ở ĐÂY thì đúng, dù ở chỗ `amount` bên dưới nó là sai. Khác nhau ở kiểu:
        // id là số NGUYÊN nên "> 0" là chính xác cái cần hỏi, không có id 0.001 để lọt qua.
        // Thiếu nó thì fromWalletId = -1 xuống tới tận Neon rồi mới quay về 404 - sai mã,
        // vì BIGSERIAL bắt đầu từ 1, ví -1 không bao giờ tồn tại được.
        @NotNull(message = "Ví nguồn không được để trống") @Positive(
                message = "Ví nguồn phải là số dương") Long fromWalletId,
        @NotNull(message = "Ví đích không được để trống") @Positive(
                message = "Ví đích phải là số dương") Long toWalletId,

        // 💰 Chỗ ăn điểm. @Positive là SAI ở đây: nó chỉ hỏi "có > 0 không", mà 0.001
        // thì > 0 nên qua tuốt. Cột DB là NUMERIC(19,2), Postgres làm tròn xuống 0.00
        // và nhận -> ghi vào lịch sử một giao dịch chuyển 0 đồng, không lỗi nào báo.
        //
        // "0.01" là CHUỖI, không phải số. Đúng luật 1 của BigDecimal (A5): luôn khởi
        // tạo từ chuỗi. Viết 0.01 dạng số thì máy lưu 0.010000000000000000208...
        //
        // integer = 17 vì NUMERIC(19,2) là tổng 19 chữ số, 2 chữ số sau dấu phẩy ->
        // còn đúng 17 phía trước. Khớp @Digits với schema DB là chặn lỗi tràn số ngay
        // ở biên, thay vì để nó nổ thành exception khó hiểu từ driver Postgres.
        @NotNull(message = "Số tiền không được để trống") @DecimalMin(value = "0.01",
                message = "Số tiền tối thiểu là 0.01") @Digits(integer = 17, fraction = 2,
                        message = "Số tiền tối đa 2 chữ số thập phân") BigDecimal amount) {
}
