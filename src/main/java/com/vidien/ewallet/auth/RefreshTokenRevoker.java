package com.vidien.ewallet.auth;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thu hoi ca mot family refresh token, trong MOT TRANSACTION RIENG.
 *
 * <p>
 * ⭐ VI SAO PHAI TACH RA - va day la LAN THU BA cung mot cai bay trong project nay:
 *
 * <p>
 * Ban dau {@code rotate()} goi thang {@code tokens.revokeFamily(...)} roi nem
 * {@code InvalidRefreshTokenException} ngay dong sau. Doc thi hop ly. Nhung
 * {@code rotate()} co {@code @Transactional}, va exception do la RuntimeException -> Spring
 * rollback ca transaction -> <b>lenh thu hoi bi xoa sach</b>.
 *
 * <p>
 * Do that truoc khi sua, va bang chung rat ro:
 *
 * <pre>
 * nhat ky:  REFRESH_TOKEN_REUSED, revokedCount = 2     &lt;- lenh UPDATE DA chay
 * database: revoked_at IS NOT NULL  ->  f, f, f        &lt;- va da bi rollback
 * token cua ke trom goi lai         ->  HTTP 200       &lt;- van song
 * </pre>
 *
 * <p>
 * Dong nhat ky song sot con lenh thu hoi thi khong, vi Auditor da chay REQUIRES_NEW tu truoc.
 * Hai thu can song sot qua cung mot rollback, ma chi mot thu duoc chuan bi cho dieu do.
 *
 * <p>
 * 📌 Ba lan cung mot hinh dang, ba noi khac nhau:
 * <ol>
 * <li>dong so cai {@code FAILED} - viet trong transaction bi rollback, bien mat (buoi 10)
 * <li>dong nhat ky kiem toan - da sua bang REQUIRES_NEW tu dau (buoi 13)
 * <li>lenh thu hoi family nay - lai quen (hom nay)
 * </ol>
 * <b>Luat: thu gi phai song sot qua mot rollback thi phai nam trong transaction KHAC.</b>
 * Va cach nhan ra: bat cu khi nao viet mot lenh GHI ngay truoc mot lenh {@code throw}.
 */
@Service
public class RefreshTokenRevoker {

    private final RefreshTokenRepository tokens;

    public RefreshTokenRevoker(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId) {
        return tokens.revokeFamily(familyId);
    }
}
