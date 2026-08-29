package com.vidien.ewallet.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.vidien.ewallet.kyc.api.dto.KycView;
import com.vidien.ewallet.kyc.domain.DocumentImage;
import com.vidien.ewallet.kyc.domain.KycService;
import com.vidien.ewallet.kyc.domain.exception.InvalidDocumentException;
import com.vidien.ewallet.kyc.domain.exception.KycAlreadyPendingException;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;

/**
 * ⭐ KYC: nhung tinh chat chi database moi giu duoc.
 *
 * <p>
 * Test dat gia nhat o day la {@link #haiRequestCungLucChiTaoMotHoSo()}. Cau kiem trong service
 * <b>khong</b> chan duoc hai request dong thoi - ca hai deu doc thay "chua co dong nao" roi ca
 * hai cung ghi. Thu chan that su la unique index co dieu kien trong V7, va khong mot unit test
 * nao chung minh duoc dieu do.
 *
 * <p>
 * Cung hinh dang voi khoa chong lap ngay 28/08: 10 request cung khoa, neu chi kiem bang mot
 * cau if thi ra 10 dong so cai.
 */
class KycSubmissionIT extends PostgresIT {

    @Autowired
    private KycService kycService;
    @Autowired
    private UserRepository users;

    private long nguoiDung;

    /** Ba byte dau cua mot file JPEG that. Du de qua cua kiem magic bytes. */
    private static final byte[] JPEG_THAT = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x11, 0x22};
    private static final byte[] PNG_THAT =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x33};

    @BeforeEach
    void dungDuLieu() {
        xoaHetDuLieu();

        nguoiDung = users.save(User.builder()
                .email("kyc@test.com").passwordHash("x").fullName("T").build()).getId();
    }

    private static DocumentImage anh(byte[] noiDung, String ten) {
        return DocumentImage.kiem(noiDung, ten);
    }

    @Test
    @DisplayName("nop thanh cong: PENDING, metadata co ca hai mat, va KHONG lo sha256 ra ngoai")
    void nopThanhCong() {
        KycView v = kycService.nop(nguoiDung,
                anh(JPEG_THAT, "truoc.jpg"), anh(PNG_THAT, "sau.png"));

        assertThat(v.status()).isEqualTo("PENDING");
        assertThat(v.metadata()).containsKeys("front", "back", "totalBytes");
        assertThat(v.rejectReason()).isNull();

        // ⚠️ Ma bam la thu noi bo. Dua ra ngoai la cho nguoi ta mot cach kiem "anh cua toi da
        // nam trong he thong chua" ma khong can nop.
        assertThat(v.metadata()).doesNotContainKey("sha256");

        @SuppressWarnings("unchecked")
        Map<String, Object> truoc = (Map<String, Object>) v.metadata().get("front");
        assertThat(truoc.get("mime")).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("⭐ kieu file doc tu NOI DUNG, khong tin ten file")
    void kieuFileDocTuNoiDung() {
        // Ten file noi la .png nhung byte dau la JPEG. Su that nam o byte.
        DocumentImage d = anh(JPEG_THAT, "toi-noi-day-la-png.png");
        assertThat(d.mime()).isEqualTo("image/jpeg");

        // Va mot file khong phai anh thi bi tu choi du ten rat thuyet phuc.
        assertThatThrownBy(() -> anh("MZ\u0090\u0000khong-phai-anh".getBytes(), "cccd.jpg"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("JPEG hoac PNG");
    }

    @Test
    @DisplayName("ten file dang duong dan bi cat sach, chi con phan ten")
    void tenFileBiLamSach() {
        assertThat(anh(JPEG_THAT, "../../etc/passwd").originalName()).isEqualTo("passwd");
        assertThat(anh(JPEG_THAT, "C:\\Windows\\system32\\x.jpg").originalName()).isEqualTo("x.jpg");
        assertThat(anh(JPEG_THAT, null).originalName()).isEqualTo("khong-ten");
    }

    @Test
    @DisplayName("anh rong thi tu choi")
    void anhRongThiTuChoi() {
        assertThatThrownBy(() -> anh(new byte[0], "rong.jpg"))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    @DisplayName("nop lan hai khi con ho so dang cho -> 409")
    void nopLanHaiKhiDangCho() {
        kycService.nop(nguoiDung, anh(JPEG_THAT, "a.jpg"), anh(PNG_THAT, "b.png"));

        assertThatThrownBy(() -> kycService.nop(nguoiDung,
                anh(JPEG_THAT, "a2.jpg"), anh(PNG_THAT, "b2.png")))
                .isInstanceOf(KycAlreadyPendingException.class);
    }

    @Test
    @DisplayName("⭐ 8 request nop CUNG LUC -> dung MOT dong, do unique index chu khong do cau if")
    void haiRequestCungLucChiTaoMotHoSo() throws Exception {
        int n = 8;
        // try-with-resources khong dung duoc: ExecutorService chi la AutoCloseable tu Java 19,
        // project nay build o Java 17. shutdown() trong finally la cach cua ban 17.
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Object>> viec = IntStream.range(0, n)
                    .<Callable<Object>>mapToObj(i -> () -> {
                        try {
                            return kycService.nop(nguoiDung,
                                    anh(JPEG_THAT, "a" + i + ".jpg"), anh(PNG_THAT, "b.png"));
                        } catch (RuntimeException e) {
                            // Nuot co y: dem so DONG trong bang moi la cau tra loi, khong phai
                            // so lan nem.
                            return null;
                        }
                    })
                    .toList();
            pool.invokeAll(viec);
        } finally {
            pool.shutdown();
        }

        Integer soDong = db.sql("SELECT count(*) FROM kyc_submissions WHERE user_id = :u")
                .param("u", nguoiDung).query(Integer.class).single();

        assertThat(soDong)
                .as("cau if trong service khong chan duoc dong thoi - unique index moi chan")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("bi tu choi roi thi nop lai duoc - unique index CO DIEU KIEN chi chan PENDING")
    void biTuChoiThiNopLaiDuoc() {
        kycService.nop(nguoiDung, anh(JPEG_THAT, "a.jpg"), anh(PNG_THAT, "b.png"));

        // Nguoi duyet tu choi.
        db.sql("""
                UPDATE kyc_submissions
                   SET status = 'REJECTED', reject_reason = 'Anh mo', reviewed_at = now()
                 WHERE user_id = :u
                """).param("u", nguoiDung).update();

        KycView lanHai = kycService.nop(nguoiDung,
                anh(JPEG_THAT, "a2.jpg"), anh(PNG_THAT, "b2.png"));

        assertThat(lanHai.status()).isEqualTo("PENDING");
        assertThat(kycService.cuaToi(nguoiDung)).hasSize(2);
    }

    @Test
    @DisplayName("REJECTED bat buoc phai co ly do - CHECK constraint chan o database")
    void tuChoiPhaiCoLyDo() {
        kycService.nop(nguoiDung, anh(JPEG_THAT, "a.jpg"), anh(PNG_THAT, "b.png"));

        // Tu choi ma khong ghi ly do: mot dong "REJECTED" trong khong ai giai thich duoc cho
        // khach hang. V7 chan bang ck_kyc_reason_matches_status.
        assertThatThrownBy(() -> db.sql(
                "UPDATE kyc_submissions SET status = 'REJECTED' WHERE user_id = :u")
                .param("u", nguoiDung).update())
                .hasMessageContaining("ck_kyc_reason_matches_status");
    }

    @Test
    @DisplayName("nhat ky ghi lai ca hai ma bam, du so cai khong co dong nao")
    void nhatKyGhiMaBam() {
        kycService.nop(nguoiDung, anh(JPEG_THAT, "a.jpg"), anh(PNG_THAT, "b.png"));

        // ⚠️ jsonb_exists(...) chu KHONG phai toan tu `payload ? 'key'`.
        //
        // Postgres dung `?` lam toan tu "JSONB co khoa nay khong". JDBC thi dung `?` lam
        // placeholder tham so. Viet toan tu do vao day thi driver dem duoc 2 placeholder va
        // Spring bao:
        //
        //   Not allowed to mix named and traditional ? placeholders.
        //   You have 1 named parameter(s) and 2 traditional placeholder(s)
        //
        // jsonb_exists() la dang HAM cua cung toan tu do - khong con dau `?` nao de nham lan.
        Integer soDong = db.sql("""
                SELECT count(*) FROM audit_log
                 WHERE event = 'KYC_SUBMITTED'
                   AND actor_id = :u
                   AND jsonb_exists(payload, 'frontSha256')
                   AND jsonb_exists(payload, 'backSha256')
                """).param("u", nguoiDung).query(Integer.class).single();

        assertThat(soDong).isEqualTo(1);
    }
}
