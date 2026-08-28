package com.vidien.ewallet.wallet.domain.exception;

/**
 * Chuyen tien cho chinh minh.
 *
 * <p>
 * Luat nay xuat hien LAN THU BA trong project: khong dat duoc bang annotation don le tren DTO
 * (day la cross-field), nen tang service chan de tra loi tu te, va rang buoc
 * ck_transactions_endpoints o V1 chan lan cuoi de du lieu ban khong ton tai duoc.
 */
public class SameWalletTransferException extends RuntimeException {

    public SameWalletTransferException(long walletId) {
        super("Vi nguon va vi dich cung la id=" + walletId);
    }
}
