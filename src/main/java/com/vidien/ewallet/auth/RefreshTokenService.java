package com.vidien.ewallet.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.audit.AuditEvent;
import com.vidien.ewallet.audit.Auditor;

/**
 * Sinh, doi va thu hoi refresh token.
 *
 * <p>
 * ⭐ Refresh token la CHUOI NGAU NHIEN, khong phai JWT - va do la lua chon co y, nguoc han
 * voi access token. Ly do: thu duy nhat can o day la THU HOI DUOC, ma JWT thi khong. Neu lam
 * refresh token bang JWT thi van phai tra ve database de hoi "cai nay bi thu hoi chua", tuc
 * la ganh toan bo chi phi cua ca hai ma khong duoc uu diem cua cai nao.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /**
     * 32 byte = 256 bit ngau nhien. Doan trung mot cai la chuyen khong xay ra trong doi nguoi.
     *
     * <p>
     * SecureRandom chu khong phai Random: Random sinh so tu mot seed 48 bit va doan duoc day
     * neu biet vai gia tri lien tiep. Cho token thi do la lo hong, khong phai chi tiet.
     */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository tokens;
    private final RefreshTokenRevoker revoker;
    private final Auditor audit;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository tokens, RefreshTokenRevoker revoker,
            Auditor audit,
            @Value("${app.jwt.refresh-ttl:P7D}") Duration ttl) {
        this.tokens = tokens;
        this.revoker = revoker;
        this.audit = audit;
        this.ttl = ttl;
    }

    /** Dang nhap / dang ky: mo mot chuoi moi. */
    @Transactional
    public String issueNewFamily(long userId) {
        return issue(userId, UUID.randomUUID());
    }

    private String issue(long userId, UUID familyId) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        // base64url: khong co ky tu +, / hay = nen nhet vao JSON, header hay URL deu khong
        // phai ma hoa them lan nua.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        tokens.insert(userId, hash(token), familyId, Instant.now().plus(ttl));
        return token;
    }

    /**
     * ⭐ XOAY VONG + PHAT HIEN DUNG LAI. Day la phan dang gia nhat ca file.
     *
     * <p>
     * Luat: moi refresh token dung duoc <b>dung mot lan</b>. Doi xong thi no chet, va client
     * nhan mot cai moi trong cung family.
     *
     * <p>
     * Vi sao phai vay: refresh token song 7 ngay, nen neu bi sao chep thi ke trom co 7 ngay de
     * dung. Xoay vong khong ngan duoc viec sao chep, nhung no <b>bien viec sao chep thanh
     * PHAT HIEN DUOC</b>:
     *
     * <pre>
     *   ke trom sao chep token T
     *   nguoi that dung T   -> T chet, nguoi that cam T2
     *   ke trom dung T      -> T da used  ->  ĐÂY LA BANG CHUNG co hai ban sao
     * </pre>
     *
     * Hoac nguoc lai - ke trom dung truoc, nguoi that dung sau. Kieu gi cung co mot ben trinh
     * ra mot token DA DUNG ROI, va dieu do khong bao gio xay ra o mot client hoat dong binh
     * thuong.
     *
     * <p>
     * Luc do khong the chi tu choi rieng cai token do: ke trom co the dang cam T2. Nen thu hoi
     * <b>CA FAMILY</b> - toan bo chuoi ke tu lan dang nhap dau. Ca hai ben deu phai dang nhap
     * lai, va do la gia phai tra dung: mot lan phien phuc cho nguoi that, doi lay viec cat
     * duong ke trom ngay.
     */
    @Transactional
    public Rotated rotate(String presentedToken) {
        Optional<RefreshToken> found = tokens.findByHash(hash(presentedToken));

        if (found.isEmpty()) {
            // Khong co trong bang: token bia ra, hoac tu mot lan reset database. Khong co
            // family nao de thu hoi, va cung khong co gi de bao dong - ghi nhat ky thi thanh
            // ra ai gui rac cung tao duoc mot dong log.
            throw new InvalidRefreshTokenException();
        }

        RefreshToken t = found.get();

        // 🚨 DA DUNG ROI ma con duoc trinh ra lan nua -> co hai ban sao dang ton tai.
        if (t.usedAt() != null) {
            // Goi qua bean KHAC voi REQUIRES_NEW: dong duoi day nem exception, va neu lenh
            // thu hoi nam trong cung transaction thi no bi rollback cuon di. Da do that,
            // xem RefreshTokenRevoker.
            int killed = revoker.revokeFamily(t.familyId());

            log.warn("PHAT HIEN DUNG LAI refresh token cua user {} - thu hoi ca family {} ({} token)",
                    t.userId(), t.familyId(), killed);

            audit.record(AuditEvent.REFRESH_TOKEN_REUSED, t.userId(),
                    Map.of("familyId", t.familyId().toString(), "revokedCount", killed,
                            "originallyUsedAt", t.usedAt().toString()));

            throw new InvalidRefreshTokenException();
        }

        if (!t.isUsable(Instant.now())) {
            // Het han hoac da bi thu hoi. Khong phai dau hieu tan cong - het han la chuyen
            // binh thuong, con bi thu hoi thi hoac da dang xuat, hoac family da bi giet o
            // nhanh tren.
            throw new InvalidRefreshTokenException();
        }

        tokens.markUsed(t.id());
        return new Rotated(t.userId(), issue(t.userId(), t.familyId()));
    }

    /** Dang xuat that su: thu hoi moi refresh token con song. Access token cu van song not TTL. */
    @Transactional
    public int revokeAllForUser(long userId) {
        return tokens.revokeAllForUser(userId);
    }

    /**
     * SHA-256, khong phai BCrypt - va khac biet nam o ENTROPY chu khong o so thich.
     *
     * <p>
     * BCrypt duoc lam CHAM CO Y de mot ke co bang bam phai ton hang thang moi do xong mot mat
     * khau nguoi ta tu nghi ra. Token o day la 256 bit tu SecureRandom: khong ai do duoc, ke
     * ca co bang bam va vo han thoi gian. Dung BCrypt chi lam cham moi lan doi token ma khong
     * mua duoc gi.
     */
    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 la thuat toan BAT BUOC co trong moi ban Java. Nhanh nay khong the xay ra;
            // neu xay ra thi he thong dang hong o mot muc khong nen chay tiep.
            throw new IllegalStateException("JVM khong co SHA-256", e);
        }
    }

    /** Ket qua mot lan xoay vong: chu cua token, va token moi. */
    public record Rotated(long userId, String refreshToken) {
    }
}
