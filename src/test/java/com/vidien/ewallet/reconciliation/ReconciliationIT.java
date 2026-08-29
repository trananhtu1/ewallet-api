/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Canh giữ bất biến, và canh cả việc job KHÔNG tự sửa số dư.
 * LIÊN QUAN: ReconciliationService · ReconciliationRun · V8
 */
package com.vidien.ewallet.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.vidien.ewallet.reconciliation.domain.ReconciliationRun;
import com.vidien.ewallet.reconciliation.domain.ReconciliationService;
import com.vidien.ewallet.reconciliation.domain.ReconciliationStatus;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.transfer.domain.TransferService;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;

/**
 * ⭐ Đối soát: bất biến của cả hệ thống, và một tính chất **âm** cũng quan trọng ngang.
 *
 * <p>
 * Tính chất âm đó là {@link #jobKhongTuSuaSoDu()}: job phát hiện lệch thì <b>ghi lại và báo
 * động</b>, tuyệt đối không được lặng lẽ chỉnh số dư cho khớp. Một batch "tự sửa" là thứ xoá
 * mất bằng chứng — sau đó không ai biết lệch bao nhiêu, từ bao giờ, vì sao.
 *
 * <p>
 * Không test nào của những buổi trước bắt được điều đó, vì nó không phải một lỗi — nó là một
 * <b>quyết định</b> rất dễ bị người sau đảo ngược cho "tiện".
 */
class ReconciliationIT extends PostgresIT {

    @Autowired
    private ReconciliationService reconciliation;
    @Autowired
    private WalletService walletService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private UserRepository users;

    private long viA;
    private long viB;

    @BeforeEach
    void dungDuLieu() {
        xoaHetDuLieu();

        viA = taoVi("a@test.com");
        viB = taoVi("b@test.com");
    }

    private long taoVi(String email) {
        User u = users.save(User.builder()
                .email(email).passwordHash("x").fullName("T").build());
        return walletService.createForUser(u.getId()).getId();
    }

    /** Nạp tiền qua service THẬT — đây là đường duy nhất tiền vào hệ thống. */
    private void nap(long vi, String soTien) {
        walletService.deposit(vi, new BigDecimal(soTien), vi);
    }

    @Test
    @DisplayName("hệ thống rỗng: tổng 0, khớp 0, status OK")
    void heThongRongThiVanKhop() {
        ReconciliationRun r = reconciliation.chay();

        assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.OK);
        assertThat(r.getWalletTotal()).isEqualByComparingTo("0.00");
        assertThat(r.getDrift()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("⭐ nạp rồi chuyển tiền qua lại: tổng KHÔNG đổi, đối soát khớp")
    void chuyenTienNoiBoKhongLamLechTong() {
        nap(viA, "100000.00");
        nap(viB, "50000.00");

        // Chuyển qua chuyển lại nhiều lần. Tiền chỉ luân chuyển nội bộ - tổng phải y nguyên.
        transferService.transfer(viA, viB, new BigDecimal("30000.00"), viA, null);
        transferService.transfer(viB, viA, new BigDecimal("12345.67"), viB, null);
        transferService.transfer(viA, viB, new BigDecimal("999.99"), viA, null);

        ReconciliationRun r = reconciliation.chay();

        assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.OK);
        assertThat(r.getWalletTotal())
                .as("tổng ví phải bằng tổng tiền đã nạp vào, dù chuyển bao nhiêu lần")
                .isEqualByComparingTo("150000.00");
        assertThat(r.getLedgerTotal()).isEqualByComparingTo("150000.00");
        assertThat(r.getDrift()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("⭐ số dư bị sửa sau lưng hệ thống -> DRIFT, và chỉ đúng ví đó")
    void soDuBiSuaSauLungThiBatDuoc() {
        nap(viA, "100000.00");
        nap(viB, "50000.00");

        // Kịch bản thật mà đối soát sinh ra để bắt: một câu UPDATE chạy thẳng vào database,
        // không qua ứng dụng. Có thể là một script vá tay, một job khác, hoặc một lỗi.
        db.sql("UPDATE wallets SET balance = balance + 7777.00 WHERE id = :id")
                .param("id", viA).update();

        ReconciliationRun r = reconciliation.chay();

        assertThat(r.getStatus()).isEqualTo(ReconciliationStatus.DRIFT);
        assertThat(r.getDrift()).isEqualByComparingTo("7777.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lech =
                (List<Map<String, Object>>) r.getDetails().get("driftingWallets");

        assertThat(lech)
                .as("phải chỉ ra ĐÚNG ví nào lệch, không chỉ nói tổng lệch bao nhiêu")
                .hasSize(1);
        assertThat(((Number) lech.get(0).get("walletId")).longValue()).isEqualTo(viA);
        assertThat(lech.get(0).get("recorded")).isEqualTo("107777.00");
        assertThat(lech.get(0).get("recomputed")).isEqualTo("100000.00");
    }

    @Test
    @DisplayName("⭐ phát hiện lệch xong thì KHÔNG được tự sửa số dư")
    void jobKhongTuSuaSoDu() {
        nap(viA, "100000.00");
        db.sql("UPDATE wallets SET balance = 999999.00 WHERE id = :id")
                .param("id", viA).update();

        reconciliation.chay();

        BigDecimal sauKhiDoiSoat = db.sql("SELECT balance FROM wallets WHERE id = :id")
                .param("id", viA).query(BigDecimal.class).single();

        // Một batch lặng lẽ "chỉnh lại cho khớp" là thứ xoá mất bằng chứng: sau đó không ai
        // biết lệch bao nhiêu, từ bao giờ, vì sao. Job ghi lại và báo động; người thật quyết
        // định sửa gì.
        assertThat(sauKhiDoiSoat)
                .as("số dư phải NGUYÊN như cũ - job chỉ quan sát, không can thiệp")
                .isEqualByComparingTo("999999.00");
    }

    @Test
    @DisplayName("giao dịch FAILED không làm tiền chạy, nên không được tính vào đối soát")
    void giaoDichThatBaiKhongLamLech() {
        nap(viA, "1000.00");

        // Chuyển quá số dư -> ném, nhưng V3/REQUIRES_NEW vẫn ghi một dòng FAILED vào sổ cái.
        try {
            transferService.transfer(viA, viB, new BigDecimal("999999.00"), viA, null);
        } catch (RuntimeException mongDoi) {
            // đúng như mong đợi
        }

        Integer soDongFailed = db.sql(
                "SELECT count(*) FROM transactions WHERE status = 'FAILED'")
                .query(Integer.class).single();
        assertThat(soDongFailed).as("phải có dòng FAILED thì test này mới có nghĩa").isEqualTo(1);

        assertThat(reconciliation.chay().getStatus()).isEqualTo(ReconciliationStatus.OK);
    }

    @Test
    @DisplayName("chạy lại trong cùng ngày: GHI ĐÈ, không sinh dòng thứ hai")
    void chayLaiTrongCungNgayThiGhiDe() {
        nap(viA, "1000.00");

        Long lanDau = reconciliation.chay().getId();
        nap(viA, "500.00");
        ReconciliationRun lanHai = reconciliation.chay();

        Integer soDong = db.sql("SELECT count(*) FROM reconciliation_runs")
                .query(Integer.class).single();

        // Job phải chạy lại được: server restart, deploy giữa chừng, hoặc người vận hành bấm
        // tay. Ba dòng cho một ngày với ba kết quả khác nhau thì không ai biết dòng nào là thật.
        assertThat(soDong).isEqualTo(1);
        assertThat(lanHai.getId()).isEqualTo(lanDau);
        assertThat(lanHai.getWalletTotal()).isEqualByComparingTo("1500.00");
    }
}
