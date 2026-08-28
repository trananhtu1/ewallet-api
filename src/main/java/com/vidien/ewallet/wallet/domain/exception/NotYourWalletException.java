package com.vidien.ewallet.wallet.domain.exception;

import com.vidien.ewallet.wallet.domain.WalletService;

/**
 * Nguoi goi tu nhan minh dang dung mot vi khong phai cua ho.
 *
 * <p>
 * ⚠️ extends RuntimeException, KHONG phai Exception. Do la luat da do bang so hom 27/08:
 * Spring chi rollback voi RuntimeException; checked exception thi COMMIT. Nem mot checked
 * exception o giua hai lenh ghi lam tien vao vi ma so cai khong co dong nao.
 *
 * <p>
 * 📌 Vi sao lop nay ton tai rieng, trong khi WalletService.requireOwn co y nem
 * WalletNotFoundException (404) cho dung mot lo'i noi doi:
 *
 * <ul>
 * <li>GET /api/wallets/{id} la mot cau TRA CUU. Tra 403 cho vi cua nguoi khac va 404 cho vi
 * khong ton tai la tang ke tan cong cong cu quet 1..N de dem so nguoi dung. O do hai cau tra
 * loi phai giong het nhau -> 404 het.
 * <li>POST /api/transfers thi nguoi goi khong HOI ve mot cai vi, ho DANG NHAN MINH LA cai vi
 * do. Va id ho nhan la id ho tu go vao, khong phai thu he thong tiet lo. Noi thang "cai nay
 * khong phai cua anh" o day khong lam lo them gi, ma lai la tin hieu go loi that su ro cho
 * frontend.
 * </ul>
 */
public class NotYourWalletException extends RuntimeException {

    public NotYourWalletException(long claimedWalletId, long callerWalletId) {
        super("Vi " + claimedWalletId + " khong thuoc ve nguoi goi (vi " + callerWalletId + ")");
    }
}
