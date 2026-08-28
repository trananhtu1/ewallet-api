package com.vidien.ewallet.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.vidien.ewallet.auth.domain.RefreshTokenService;
import com.vidien.ewallet.auth.domain.exception.InvalidRefreshTokenException;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;

/**
 * ⭐ Xoay vong refresh token + phat hien dung lai.
 *
 * <p>
 * File nay ton tai vi mot loi CU THE: ban dau {@code rotate()} doc dong token roi moi ghi
 * {@code used_at}. Hai request /refresh song song voi cung mot token thi <b>ca hai deu doc
 * thay "chua dung"</b>, va ca hai deu tra ve 200 - dung cai ma co che nay sinh ra de chan.
 *
 * <p>
 * Loi do KHONG bat duoc bang unit test co mock: mock tra ve cai minh bao no tra ve. No chi lo
 * ra khi co mot database that phan xu hai cau UPDATE cham nhau. Do la ranh gioi giua *Test va
 * *IT trong project nay.
 */
class RefreshTokenRotationIT extends PostgresIT {

    @Autowired
    private RefreshTokenService refreshTokens;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcClient db;

    private long userId;

    @BeforeEach
    void dungDuLieu() {
        db.sql("DELETE FROM audit_log").update();
        db.sql("DELETE FROM refresh_tokens").update();
        db.sql("DELETE FROM wallets").update();
        db.sql("DELETE FROM users").update();

        userId = users.save(User.builder()
                .email("xoay@test.com").passwordHash("x").fullName("T").build()).getId();
    }

    private String detectedBy() {
        return db.sql("""
                SELECT payload->>'detectedBy' FROM audit_log
                WHERE event = 'REFRESH_TOKEN_REUSED' ORDER BY id DESC LIMIT 1
                """).query(String.class).single();
    }

    private int soTokenConSong() {
        return db.sql("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL")
                .query(Integer.class).single();
    }

    @Test
    @DisplayName("xoay hop le -> token moi khac token cu, va token cu bi danh dau da dung")
    void xoayBinhThuong() {
        String cu = refreshTokens.issueNewFamily(userId);

        String moi = refreshTokens.rotate(cu).refreshToken();

        assertThat(moi).isNotEqualTo(cu);
        Integer daDung = db.sql("SELECT count(*) FROM refresh_tokens WHERE used_at IS NOT NULL")
                .query(Integer.class).single();
        assertThat(daDung).isEqualTo(1);
        // Ca hai dong deu thuoc mot family - do la thu cho phep thu hoi CA CHUOI ve sau.
        Integer soFamily = db.sql("SELECT count(DISTINCT family_id) FROM refresh_tokens")
                .query(Integer.class).single();
        assertThat(soFamily).isEqualTo(1);
    }

    /**
     * Kich ban ro rang: token bi danh cap, ke tan cong dung truoc, chu that dung sau (hoac
     * nguoc lai). Ai la nguoi thu hai khong quan trong - <b>he thong khong phan biet duoc</b>,
     * nen no giet ca chuoi va bat ca hai dang nhap lai.
     */
    @Test
    @DisplayName("dung lai token da xoay -> tu choi, thu hoi CA family, ghi SECOND_USE")
    void dungLaiTokenCu() {
        String cu = refreshTokens.issueNewFamily(userId);
        refreshTokens.rotate(cu);

        assertThatThrownBy(() -> refreshTokens.rotate(cu))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // Khong con token nao song - ke ca cai VUA duoc phat o lan xoay hop le.
        assertThat(soTokenConSong()).isZero();
        assertThat(detectedBy()).isEqualTo("SECOND_USE");
    }

    /**
     * ⭐⭐ Kich ban da bo sot: hai request DONG THOI, cung mot token.
     *
     * <p>
     * Ca hai cung doc thay {@code used_at IS NULL}. Chi cau
     * {@code UPDATE ... WHERE used_at IS NULL} moi phan xu duoc - ben thua nhan ve 0 dong.
     *
     * <p>
     * Test nay se DO neu ai do sua {@code rotate()} thanh doc-roi-ghi cho "de doc hon".
     */
    @Test
    @DisplayName("2 request dong thoi cung token -> dung 1 thanh cong, ghi CONCURRENT_CLAIM")
    void haiRequestDongThoi() throws Exception {
        String cu = refreshTokens.issueNewFamily(userId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, 2)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            refreshTokens.rotate(cu);
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();

            long thanhCong = pool.invokeAll(jobs).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(thanhCong).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        // Phat hien dung lai -> ca family chet, ke ca token vua phat cho ben thang.
        assertThat(soTokenConSong()).isZero();
        assertThat(detectedBy()).isEqualTo("CONCURRENT_CLAIM");
    }

    @Test
    @DisplayName("token bia ra -> tu choi, va KHONG ghi bao dong")
    void tokenBiaRa() {
        assertThatThrownBy(() -> refreshTokens.rotate("khong-he-ton-tai"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        Integer soBaoDong = db.sql(
                "SELECT count(*) FROM audit_log WHERE event = 'REFRESH_TOKEN_REUSED'")
                .query(Integer.class).single();
        // Ghi log o day thi bat ky ai gui rac cung tao duoc mot dong bao dong - nhat ky ngap
        // tieng on la nhat ky khong ai doc.
        assertThat(soBaoDong).isZero();
    }
}
