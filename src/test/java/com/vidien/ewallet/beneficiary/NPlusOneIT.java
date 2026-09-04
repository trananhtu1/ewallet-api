package com.vidien.ewallet.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.beneficiary.domain.Beneficiary;
import com.vidien.ewallet.beneficiary.infra.BeneficiaryRepository;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;
import jakarta.persistence.EntityManager;

/**
 * ⭐⭐ N+1 - do bang SO CAU TRUY VAN, khong bang cam giac.
 *
 * <p>
 * N+1 la loai loi <b>khong bao gio bao loi</b>: man hinh dung, du lieu dung, test
 * chuc nang xanh. No chi hien ra khi du lieu lon len - tuc la tren production.
 *
 * <p>
 * Nen no phai duoc canh bang mot con so, va con so do phai den tu Hibernate chu
 * khong tu doc log bang mat. {@code Statistics} dem dung so cau JDBC da ban ra.
 *
 * <p>
 * ⚠️ {@code @Transactional} tren lop test la BAT BUOC o day: lazy loading chi xay
 * ra khi persistence context con song. Khong co no thi ban ngay tho nem
 * {@code LazyInitializationException} - do van la mot loi, nhung la loi KHAC, va
 * no che mat dieu ta muon do.
 */
@Transactional
class NPlusOneIT extends PostgresIT {

    private static final int SO_NGUOI_NHAN = 20;

    @Autowired
    private BeneficiaryRepository beneficiaries;
    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository users;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcClient db;

    private long ownerId;

    @BeforeEach
    void dungDuLieu() {
        db.sql("DELETE FROM beneficiaries").update();

        User chu = users.save(User.builder()
                .email("so-dia-chi@test.com").passwordHash("x").fullName("Chủ sổ").build());
        ownerId = chu.getId();
        walletService.createForUser(ownerId);

        for (int i = 0; i < SO_NGUOI_NHAN; i++) {
            User nguoiKhac = users.save(User.builder()
                    .email("nhan" + i + "@test.com").passwordHash("x").fullName("N" + i).build());
            long viId = walletService.createForUser(nguoiKhac.getId()).getId();

            beneficiaries.save(Beneficiary.builder()
                    .owner(users.getReferenceById(ownerId))
                    .wallet(em.getReference(com.vidien.ewallet.wallet.domain.Wallet.class, viId))
                    .label("Người nhận " + i)
                    .build());
        }
        em.flush();
        em.clear();
    }

    private Statistics thongKe() {
        Statistics s = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        s.setStatisticsEnabled(true);
        s.clear();
        return s;
    }

    /**
     * ⭐ Test QUAN TRONG NHAT o day, va no canh giu duong that.
     *
     * <p>
     * Doc {@code getId()} tren mot quan he lazy KHONG cham database - khoa ngoai da
     * nam san trong dong vua doc len. Nen endpoint nay chi ton MOT cau, du dung cau
     * truy van sinh san khong co JOIN FETCH.
     *
     * <p>
     * Test do se ngay khi ai do them mot truong cua vi vao {@code BeneficiaryResponse}
     * - va do dung la luc N+1 that su xuat hien.
     */
    @Test
    @DisplayName("doc mot id cua quan he lazy: KHONG sinh them cau nao")
    void docIdKhongChamDatabase() {
        Statistics st = thongKe();

        // Doc y het cach BeneficiaryResponse.of() lam.
        for (Beneficiary b : beneficiaries.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)) {
            assertThat(b.getWallet().getId()).isNotNull();
        }

        assertThat(st.getPrepareStatementCount())
                .as("con so nay > 1 nghia la co ai vua them mot truong CUA VI vao "
                        + "BeneficiaryResponse. Do dung la N+1, va cach chua la doi service "
                        + "sang findAllByOwnerWithWallet()")
                .isEqualTo(1L);
    }

    /**
     * Cham vao mot truong KHAC id thi proxy moi that su di lay du lieu - va do la
     * luc N+1 hien ra. Test nay chung minh co che, khong canh giu duong that.
     */
    @Test
    @DisplayName("doc mot truong KHAC id: moi dong mot cau - day moi la N+1")
    void docTruongKhacIdMoiSinhRaNPlusOne() {
        Statistics st = thongKe();

        for (Beneficiary b : beneficiaries.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)) {
            // getCreatedAt() khong nam san trong dong beneficiaries -> phai di lay.
            assertThat(b.getWallet().getCreatedAt()).isNotNull();
        }

        assertThat(st.getPrepareStatementCount())
                .as("1 cau danh sach + %d cau lay tung cai vi", SO_NGUOI_NHAN)
                .isEqualTo(SO_NGUOI_NHAN + 1L);
    }

    @Test
    @DisplayName("JOIN FETCH: van MOT cau du doc truong khac id")
    void joinFetchChiMotCau() {
        Statistics st = thongKe();

        for (Beneficiary b : beneficiaries.findAllByOwnerWithWallet(ownerId)) {
            assertThat(b.getWallet().getCreatedAt()).isNotNull();
        }

        assertThat(st.getPrepareStatementCount())
                .as("JOIN FETCH gom tat ca vao mot cau; > 1 nghia la ai do vua doi no "
                        + "thanh JOIN thuong - JOIN thuong chi LOC, khong nhet du lieu vao "
                        + "doi tuong, va lazy loading no ra nhu cu")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("hai cach cho ra CUNG du lieu - khac nhau chi o so cau truy van")
    void cungKetQua() {
        var thoSo = beneficiaries.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        var fetch = beneficiaries.findAllByOwnerWithWallet(ownerId);

        assertThat(fetch).hasSize(SO_NGUOI_NHAN);
        assertThat(fetch.stream().map(Beneficiary::getId).toList())
                .containsExactlyElementsOf(thoSo.stream().map(Beneficiary::getId).toList());
    }
}
