package com.vidien.ewallet.wallet.domain.exception;

import java.math.BigDecimal;

/** So du khong du de chuyen. RuntimeException, vi ly do da do o buoi 3. */
public class InsufficientFundsException extends RuntimeException {

    private final long walletId;
    private final BigDecimal balance;
    private final BigDecimal requested;

    public InsufficientFundsException(long walletId, BigDecimal balance, BigDecimal requested) {
        super("Vi id=" + walletId + " co " + balance + " nhung can " + requested);
        this.walletId = walletId;
        this.balance = balance;
        this.requested = requested;
    }

    public long walletId() {
        return walletId;
    }

    public BigDecimal balance() {
        return balance;
    }

    public BigDecimal requested() {
        return requested;
    }
}
