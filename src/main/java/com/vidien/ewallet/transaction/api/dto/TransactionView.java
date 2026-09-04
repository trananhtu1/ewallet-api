package com.vidien.ewallet.transaction.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.vidien.ewallet.transaction.domain.Transaction;

/**
 * Thu CLIENT can, khong phai thu BANG co.
 *
 * <p>
 * Bang luu {@code from_wallet_id} va {@code to_wallet_id} - trung lap va bat frontend tu suy
 * ra. Nhung mot nguoi mo lich su vi cua minh chi muon biet hai dieu: tien VAO hay RA, va doi
 * phuong la ai. Hai truong {@code direction} va {@code counterpartyWalletId} duoc TINH RA,
 * khong nam trong bang.
 *
 * <p>
 * 📌 Chuyen sang {@code api/dto/} cung dot doi structure: no la hop dong voi frontend, khong
 * phai mot khai niem cua domain.
 *
 * <p>
 * ⚠️ VI SAO KHONG DUNG MapStruct cho cai nay, trong khi {@code WalletMapper} thi co:
 * {@code direction} phu thuoc vao NGUOI DANG XEM chu khong chi vao dong du lieu - cung mot
 * giao dich, vi 1 thay "IN", vi 2 thay "OUT". MapStruct lam duoc bang {@code @Context}, nhung
 * luc do doc kho hon han mot ham static ba dong. Sinh code tu dong dung khi anh xa la co hoc;
 * cho nao co LUAT thi viet tay ro hon.
 */
public record TransactionView(
        Long id,
        String direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String type,
        String status,
        Long counterpartyWalletId,
        String note,
        Instant createdAt) {

    /** direction tinh theo GOC NHIN cua vi dang xem: cung mot dong, hai vi thay hai chieu. */
    public static TransactionView of(Transaction tx, long viewerWalletId) {
        boolean incoming = tx.getToWalletId() != null && tx.getToWalletId() == viewerWalletId;
        Long counterparty = incoming ? tx.getFromWalletId() : tx.getToWalletId();

        return new TransactionView(
                tx.getId(),
                incoming ? "IN" : "OUT",
                tx.getAmount(),
                // .name() chu khong .toString(): enum giu nguyen ten hang so du ai co lo viet
                // toString() de bay gio hay mai kia.
                tx.getType().name(),
                tx.getStatus().name(),
                counterparty,
                tx.getNote(),
                tx.getCreatedAt());
    }
}
