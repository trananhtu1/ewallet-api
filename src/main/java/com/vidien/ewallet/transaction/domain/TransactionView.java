package com.vidien.ewallet.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Thu CLIENT can, khong phai thu BANG co.
 *
 * <p>
 * Bang luu from_wallet_id va to_wallet_id - trung lap va bat frontend tu suy ra. Nhung mot
 * nguoi mo lich su vi cua minh chi muon biet hai dieu: tien VAO hay RA, va doi phuong la ai.
 * Hai truong direction va counterpartyWalletId duoc TINH RA, khong nam trong bang.
 *
 * <p>
 * Day cung la ly do DTO tra ve phai tach khoi record cua bang: doi hinh dang API khong duoc
 * keo theo doi schema, va nguoc lai.
 */
public record TransactionView(
        Long id,
        String direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String type,
        String status,
        Long counterpartyWalletId,
        Instant createdAt) {

    /** direction tinh theo GOC NHIN cua vi dang xem: cung mot dong, hai vi thay hai chieu. */
    public static TransactionView of(Transaction tx, long viewerWalletId) {
        boolean incoming = tx.toWalletId() != null && tx.toWalletId() == viewerWalletId;
        Long counterparty = incoming ? tx.fromWalletId() : tx.toWalletId();

        return new TransactionView(tx.id(), incoming ? "IN" : "OUT", tx.amount(), tx.type(),
                tx.status(), counterparty, tx.createdAt());
    }
}
