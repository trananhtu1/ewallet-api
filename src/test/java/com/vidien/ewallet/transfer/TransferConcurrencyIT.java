package com.vidien.ewallet.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.transfer.domain.TransferService;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;

/**
 * ⭐ File nay canh nhung tinh chat MA CHI DATABASE MOI QUYET DINH DUOC.
 *
 * <p>
 * Toan bo nhung so do trong PROGRESS.md tu 27/08 den gio - 12 lenh nguoc chieu, 20 lenh vuot
 * so du, 10 lenh cung khoa chong lap - deu la <b>do bang tay bang curl</b>. Do mot lan chung
 * minh no dung HOM DO. File nay chuyen chung thanh thu chay lai duoc.
 *
 * <p>
 * Vi sao dieu do quan trong: dot doi sang JPA vua roi cham vao dung nhung duong do. Neu khong
 * co file nay thi cach duy nhat biet chung con dung la <b>lai do bang tay</b> - va den lan
 * thu ba thi khong ai do nua.
 */
class TransferConcurrencyIT extends PostgresIT {

    @Autowired
    private TransferService transferService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcClient db;

    private long viA;
    private long viB;

    @BeforeEach
    void dungDuLieu() {
        db.sql("DELETE FROM transactions").update();
        db.sql("DELETE FROM audit_log").update();
        db.sql("DELETE FROM refresh_tokens").update();
        db.sql("DELETE FROM wallets").update();
        db.sql("DELETE FROM users").update();

        viA = taoVi("a@test.com", "100000.00");
        viB = taoVi("b@test.com", "100000.00");
    }

    private long taoVi(String email, String soDu) {
        User u = users.save(User.builder()
                .email(email).passwordHash("x").fullName("T").build());
        long viId = walletService.createForUser(u.getId()).getId();
        db.sql("UPDATE wallets SET balance = :b WHERE id = :id")
                .param("b", new BigDecimal(soDu)).param("id", viId).update();
        return viId;
    }

    private BigDecimal tongHeThong() {
        return db.sql("SELECT sum(balance) FROM wallets").query(BigDecimal.class).single();
    }

    /** Chay N viec CUNG LUC, tra ve so viec thanh cong. Loi bi nuot co y - dem la du. */
    private int chayCungLuc(int n, Callable<?> viec) throws Exception {
        // try-with-resources khong dung duoc: ExecutorService chi la AutoCloseable tu Java 19,
        // project nay build o Java 17. shutdown() trong finally la cach cua ban 17.
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Boolean>> jobs = IntStream.range(0, n)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            viec.call();
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .toList();

            return (int) pool.invokeAll(jobs).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * ⭐ Kich ban deadlock hom 27/08: 6 lenh A→B va 6 lenh B→A chay cung luc.
     *
     * <p>
     * Ban dau (khoa theo thu tu nguon-dich) do duoc <b>6/12 tra ve 500</b>, log Postgres ghi
     * {@code deadlock detected} kem vong cho khep kin. Sau khi khoa theo id tang dan: 12/12.
     */
    @Test
    @DisplayName("12 lenh nguoc chieu cung luc -> 12/12 thanh cong, khong deadlock")
    void khongDeadlockKhiChuyenNguocChieu() throws Exception {
        BigDecimal truoc = tongHeThong();

        int thanhCong = chayCungLuc(12, () -> {
            boolean xuoi = Thread.currentThread().getId() % 2 == 0;
            long tu = xuoi ? viA : viB;
            long den = xuoi ? viB : viA;
            return transferService.transfer(tu, den, new BigDecimal("100.00"), tu, null);
        });

        assertThat(thanhCong).isEqualTo(12);
        // Bat bien manh nhat cua ca he thong: tien chuyen cho, khong sinh ra, khong mat di.
        assertThat(tongHeThong()).isEqualByComparingTo(truoc);
    }

    /**
     * ⭐ Kich ban "20 lenh chuyen tien cung luc, vi chi du 14".
     *
     * <p>
     * Neu kiem so du o Java roi moi ghi thi ca 20 lenh deu doc thay "con du" va vi thanh AM.
     * Dieu kien {@code AND balance >= :amount} nam trong cau UPDATE la thu chan dieu do.
     */
    @Test
    @DisplayName("vi chi du 14 lenh -> dung 14 thanh cong, vi khong bao gio am")
    void khongBaoGioAmDuoiTai() throws Exception {
        db.sql("UPDATE wallets SET balance = 70000 WHERE id = :id").param("id", viA).update();
        BigDecimal truoc = tongHeThong();

        int thanhCong = chayCungLuc(20,
                () -> transferService.transfer(viA, viB, new BigDecimal("5000.00"), viA, null));

        assertThat(thanhCong).isEqualTo(14);

        BigDecimal soDuA = db.sql("SELECT balance FROM wallets WHERE id = :id")
                .param("id", viA).query(BigDecimal.class).single();
        assertThat(soDuA).isEqualByComparingTo("0.00");
        assertThat(soDuA).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(tongHeThong()).isEqualByComparingTo(truoc);
    }

    /**
     * ⭐ 10 request cung mot khoa chong lap. Day la kich ban "mang chap chon, client tu retry".
     *
     * <p>
     * Kiem khoa TRUOC lenh khoa dong thi ca 10 deu doc thay "chua co" va ca 10 deu chuyen.
     * Kiem SAU thi 9 request bi chan o lockById cho toi khi request dau tien commit xong.
     */
    @Test
    @DisplayName("10 request cung Idempotency-Key -> tien chi di MOT lan, so cai MOT dong")
    void chiChuyenMotLanDuMuoiRequest() throws Exception {
        BigDecimal truoc = tongHeThong();

        chayCungLuc(10, () -> transferService.transfer(viA, viB, new BigDecimal("500.00"),
                viA, "cung-mot-khoa"));

        Integer soDong = db.sql(
                "SELECT count(*) FROM transactions WHERE idempotency_key = 'cung-mot-khoa'")
                .query(Integer.class).single();
        assertThat(soDong).isEqualTo(1);

        BigDecimal soDuA = db.sql("SELECT balance FROM wallets WHERE id = :id")
                .param("id", viA).query(BigDecimal.class).single();
        assertThat(soDuA).isEqualByComparingTo("99500.00");
        assertThat(tongHeThong()).isEqualByComparingTo(truoc);
    }

    /**
     * ⭐ Dong so cai FAILED phai SONG SOT qua rollback.
     *
     * <p>
     * No duoc ghi bang {@code REQUIRES_NEW} ngay giua mot transaction sap bi rollback. Va no
     * co khoa ngoai tro vao chinh hai cai vi dang bi khoa - do la ly do lenh khoa phai la
     * {@code FOR NO KEY UPDATE} chu khong phai {@code FOR UPDATE}.
     *
     * <p>
     * Test nay <b>chinh la thu canh cho ai do doi lai thanh {@code @Lock(PESSIMISTIC_WRITE)}
     * cho "dung JPA cho chuan"</b>: doi xong thi test nay treo roi do voi 55P03.
     */
    @Test
    @DisplayName("chuyen qua so du -> so cai co dong FAILED, va so du KHONG doi")
    void ghiVetThatBaiMaKhongMatTien() {
        BigDecimal truoc = tongHeThong();

        try {
            transferService.transfer(viA, viB, new BigDecimal("999999.00"), viA, null);
        } catch (RuntimeException mongDoi) {
            // InsufficientFunds - dung nhu mong doi.
        }

        Integer soDongFailed = db.sql(
                "SELECT count(*) FROM transactions WHERE status = 'FAILED'")
                .query(Integer.class).single();
        assertThat(soDongFailed).isEqualTo(1);

        // Transaction chinh da rollback, nhung dong so cai thi khong.
        assertThat(tongHeThong()).isEqualByComparingTo(truoc);
    }
}
