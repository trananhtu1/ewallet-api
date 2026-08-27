package com.vidien.ewallet.wallet;

/**
 * Vi khong ton tai.
 *
 * <p>
 * BAT BUOC ke thua RuntimeException, khong duoc la Exception. Spring mac dinh chi rollback
 * transaction khi gap RuntimeException hoac Error; checked exception thi no COMMIT binh thuong.
 * Neu class nay la checked, mot lenh nap tien vao vi khong ton tai co the tru tien roi van
 * duoc luu lai.
 */
public class WalletNotFoundException extends RuntimeException {

    private final long walletId;

    public WalletNotFoundException(long walletId) {
        super("Khong tim thay vi id=" + walletId);
        this.walletId = walletId;
    }

    public long walletId() {
        return walletId;
    }
}
