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
    private final Auditor audit;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository tokens, Auditor audit,
            @Value("${app.jwt.refresh-ttl:P7D}") Duration ttl) {
        this.tokens = tokens;
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
     *
     * <p>
     * ⚠️ CO Y KHONG CO @Transactional. Day la ket luan sau hai lan sua sai.
     *
     * <p>
     * Lan 1: co @Transactional, va lenh thu hoi family bi rollback cuon di boi chinh dong
     * throw ngay sau no. Chua bang cach tach ra mot bean REQUIRES_NEW.
     *
     * <p>
     * Lan 2: REQUIRES_NEW lai <b>tu chan chinh transaction ngoai cua no</b> - cung mot bang,
     * cung nhung dong do. Do that: {@code 55P03 canceling statement due to lock timeout,
     * while updating tuple in relation "refresh_tokens"}, va nguoi dung nhan 500 sau 5 giay
     * thay vi 401 ngay.
     *
     * <p>
     * 📌 Ket luan dung: <b>don vi nguyen tu o day la MOT CAU UPDATE, khong phai ca method.</b>
     * {@code claimForRotation()} tu no da phan xu duoc "ai la nguoi dau tien" - do la toan bo
     * tinh nguyen tu can thiet. Boc them mot transaction quanh no khong mua duoc gi, ma them
     * hai nguy co: rollback cuon mat lenh thu hoi, va tu chan chinh minh.
     *
     * <p>
     * <b>Mot transaction khong tu dong an toan hon.</b> No chi dung khi co NHIEU lenh ghi phai
     * cung song hoac cung chet - o day khong co.
     *
     * <p>
     * Doi lai: neu {@code issue()} hong sau khi da gianh xong, token cu chet ma khong co token
     * moi -> nguoi dung phai dang nhap lai. Hiem, va la huong hong AN TOAN (fail-closed).
     */
    public Rotated rotate(String presentedToken) {
        Optional<RefreshToken> found = tokens.findByHash(hash(presentedToken));

        if (found.isEmpty()) {
            // Khong co trong bang: token bia ra, hoac tu mot lan reset database. Khong co
            // family nao de thu hoi, va cung khong co gi de bao dong - ghi nhat ky thi thanh
            // ra ai gui rac cung tao duoc mot dong log.
            throw new InvalidRefreshTokenException();
        }

        RefreshToken t = found.get();

        // Chan som cac ly do KHONG phai dau hieu tan cong: het han, hoac da bi thu hoi.
        // Kiem truoc de khong lam on nhat ky bang nhung ca binh thuong.
        if (t.revokedAt() != null || !Instant.now().isBefore(t.expiresAt())) {
            throw new InvalidRefreshTokenException();
        }

        // 🚨 DA DUNG ROI ma con duoc trinh ra lan nua -> co hai ban sao dang ton tai.
        //
        // Hai duong vao nhanh nay:
        //   1. doc thay used_at != null  -> ro rang, lan dung thu hai cach lan dau mot khoang
        //   2. claimForRotation() tra 0  -> hai request DONG THOI, ca hai deu doc thay
        //      "chua dung" nhung chi mot ben gianh duoc cau UPDATE
        //
        // Duong thu hai la duong da bo sot o ban dau, va da do that: hai lenh /refresh song
        // song voi cung mot token -> CA HAI deu 200. Doc-roi-ghi khong bao gio du de phan xu
        // chuyen "ai la nguoi dau tien" - dieu kien phai nam trong cau UPDATE.
        if (t.usedAt() != null || tokens.claimForRotation(t.id()) == 0) {
            // Goi THANG, khong qua bean REQUIRES_NEW: method nay khong co transaction nen
            // lenh UPDATE tu commit ngay - khong co gi de rollback cuon di, va cung khong co
            // transaction ngoai nao de tu chan chinh minh.
            int killed = tokens.revokeFamily(t.familyId());

            log.warn("PHAT HIEN DUNG LAI refresh token cua user {} - thu hoi ca family {} ({} token)",
                    t.userId(), t.familyId(), killed);

            audit.record(AuditEvent.REFRESH_TOKEN_REUSED, t.userId(),
                    Map.of("familyId", t.familyId().toString(), "revokedCount", killed,
                            // Co the null o duong thu hai (hai request dong thoi): luc doc thi
                            // chua ai dung, den luc ghi moi thua. Ghi ro de nguoi doc nhat ky
                            // phan biet duoc hai kich ban.
                            "detectedBy", t.usedAt() == null ? "CONCURRENT_CLAIM" : "SECOND_USE"));

            throw new InvalidRefreshTokenException();
        }

        // Toi day: da gianh duoc quyen, va chi MOT request lam duoc dieu do.
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
