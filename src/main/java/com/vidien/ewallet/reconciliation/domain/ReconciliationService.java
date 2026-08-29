/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Chạy bất biến của hệ thống và ghi kết quả. KHÔNG tự sửa số dư.
 * LIÊN QUAN: ReconciliationScheduler · ReconciliationRun · V8__create_reconciliation_runs.sql
 * BÀI GIẢNG: java-learn/java/04-database/BUOI-9-ISOLATION.md (vì sao REPEATABLE READ)
 */
package com.vidien.ewallet.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.audit.domain.AuditEvent;
import com.vidien.ewallet.audit.domain.Auditor;
import com.vidien.ewallet.reconciliation.infra.ReconciliationRunRepository;

/**
 * ⭐ Đối soát: chạy <b>bất biến của hệ thống</b> và nói ra khi nó vỡ.
 *
 * <p>
 * <b>Bất biến, viết bằng tiếng Việt trước khi viết bằng SQL:</b> tiền chỉ VÀO hệ thống bằng
 * một đường duy nhất — giao dịch {@code DEPOSIT} thành công. Chuyển tiền là luân chuyển nội bộ,
 * trừ bên này cộng bên kia, tổng không đổi. Giao dịch {@code FAILED} không làm tiền chạy.
 *
 * <pre>
 * SUM(wallets.balance) = SUM(transactions.amount WHERE type='DEPOSIT' AND status='SUCCESS')
 * </pre>
 *
 * <p>
 * <b>Câu đó là toàn bộ giá trị của việc đối soát.</b> Job chỉ là thứ chạy nó mỗi ngày. Viết
 * được bất biến ra là phần khó; lên lịch cho nó là phần dễ.
 *
 * <p>
 * ⚠️ <b>ISOLATION.REPEATABLE_READ, và đây không phải trang trí.</b> Mặc định của Postgres là
 * {@code READ COMMITTED}, nghĩa là <b>mỗi câu lệnh có một ảnh chụp riêng</b>. Job này đọc hai
 * con số bằng hai câu lệnh. Nếu có một lệnh nạp tiền commit vào đúng khe giữa chúng:
 *
 * <pre>
 * 10:00:00.000  đọc SUM(balance)      -> 1.000.000   (trước khi nạp)
 * 10:00:00.020  MỘT LỆNH NẠP 50.000 COMMIT
 * 10:00:00.040  đọc SUM(deposits)     -> 1.050.000   (sau khi nạp)
 *               => báo lệch 50.000, trong khi hệ thống HOÀN TOÀN ĐÚNG
 * </pre>
 *
 * Một báo động giả, xuất hiện <b>ngẫu nhiên</b> và <b>chỉ khi có tải</b> — tức là đúng lúc
 * người ta ít muốn nghi ngờ nó nhất. {@code REPEATABLE_READ} cho cả transaction <b>một ảnh
 * chụp duy nhất</b>, nên hai con số luôn nhìn cùng một khoảnh khắc.
 *
 * <p>
 * 📌 Đây là lần đầu bài isolation của buổi 9 được dùng để giải một bài toán thật, chứ không
 * phải để đọc bảng.
 *
 * <p>
 * ⚠️ <b>Và job này KHÔNG tự sửa số dư.</b> Một batch lặng lẽ "chỉnh lại cho khớp" là thứ xoá
 * mất bằng chứng: sau đó không ai biết lệch bao nhiêu, từ bao giờ, vì sao. Nó ghi lại và báo
 * động; người thật quyết định sửa gì.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final JdbcClient db;
    private final ReconciliationRunRepository runs;
    private final Auditor audit;
    private final Clock clock;

    public ReconciliationService(JdbcClient db, ReconciliationRunRepository runs, Auditor audit,
            Clock clock) {
        this.db = db;
        this.runs = runs;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Chạy một lượt đối soát cho ngày nghiệp vụ hôm nay.
     *
     * <p>
     * Ghi đè kết quả nếu ngày đó đã có dòng — job phải chạy lại được. Server restart, deploy
     * giữa chừng, hoặc người vận hành bấm tay đều dẫn tới lần chạy thứ hai, và ba dòng cho một
     * ngày với ba kết quả khác nhau thì không ai biết dòng nào là thật.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationRun chay() {
        long batDau = System.nanoTime();
        LocalDate ngay = LocalDate.now(clock);

        BigDecimal tongViDangGiu = motSo("""
                SELECT COALESCE(sum(balance), 0) FROM wallets
                """);

        BigDecimal tongTienDaVao = motSo("""
                SELECT COALESCE(sum(amount), 0) FROM transactions
                 WHERE type = 'DEPOSIT' AND status = 'SUCCESS'
                """);

        BigDecimal lech = tongViDangGiu.subtract(tongTienDaVao);

        // Chỉ đi tìm ví nào lệch KHI tổng đã lệch. Câu dưới quét cả hai bảng, và chạy nó mỗi
        // ngày trong khi mọi thứ vẫn đúng là trả tiền cho một câu trả lời luôn rỗng.
        List<Map<String, Object>> viLech =
                lech.signum() == 0 ? List.of() : timViLech();

        Map<String, Object> chiTiet = new HashMap<>();
        chiTiet.put("walletCount", motSoNguyen("SELECT count(*) FROM wallets"));
        chiTiet.put("depositCount", motSoNguyen(
                "SELECT count(*) FROM transactions WHERE type='DEPOSIT' AND status='SUCCESS'"));
        chiTiet.put("driftingWallets", viLech);

        ReconciliationStatus trangThai = lech.signum() == 0
                ? ReconciliationStatus.OK
                : ReconciliationStatus.DRIFT;

        ReconciliationRun ketQua = runs.findByBusinessDate(ngay)
                .orElseGet(() -> ReconciliationRun.builder().businessDate(ngay).build());

        ketQua.setStatus(trangThai);
        ketQua.setWalletTotal(tongViDangGiu);
        ketQua.setLedgerTotal(tongTienDaVao);
        ketQua.setDrift(lech);
        ketQua.setDetails(chiTiet);
        ketQua.setDurationMs((System.nanoTime() - batDau) / 1_000_000);

        runs.save(ketQua);

        if (trangThai == ReconciliationStatus.DRIFT) {
            // ⚠️ ERROR chứ không WARN. Số dư không khớp sổ cái là chuyện phải có người nhìn
            // ngay trong ngày, không phải một dòng trôi qua trong log.
            log.error("ĐỐI SOÁT LỆCH ngày {}: ví giữ {} nhưng sổ cái ghi {} (lệch {}), {} ví lệch",
                    ngay, tongViDangGiu, tongTienDaVao, lech, viLech.size());

            audit.record(AuditEvent.RECONCILIATION_DRIFT, null, Map.of(
                    "businessDate", ngay.toString(),
                    "walletTotal", tongViDangGiu.toString(),
                    "ledgerTotal", tongTienDaVao.toString(),
                    "drift", lech.toString(),
                    "driftingWalletCount", viLech.size()));
        } else {
            log.info("Đối soát ngày {}: KHỚP, tổng {} trên {} ví",
                    ngay, tongViDangGiu, chiTiet.get("walletCount"));
        }

        return ketQua;
    }

    /**
     * Từng ví: số dư ghi trong bảng so với số tính lại từ sổ cái.
     *
     * <p>
     * Chỉ chạy khi tổng đã lệch — xem chỗ gọi. Câu này trả lời câu hỏi mà người xử lý sự cố
     * hỏi ngay sau <i>"lệch bao nhiêu"</i>: <b>lệch ở đâu</b>.
     *
     * <p>
     * {@code FILTER (WHERE ...)} thay vì {@code CASE WHEN ... THEN ... END} bên trong
     * {@code sum()}: cùng kết quả, nhưng đọc ra ngay được ý định. Đây là cú pháp chuẩn SQL,
     * không phải thứ riêng của Postgres.
     */
    private List<Map<String, Object>> timViLech() {
        List<Map<String, Object>> ra = new ArrayList<>();

        db.sql("""
                SELECT w.id,
                       w.balance AS ghi_so,
                       COALESCE(sum(t.amount) FILTER (WHERE t.to_wallet_id = w.id), 0)
                     - COALESCE(sum(t.amount) FILTER (WHERE t.from_wallet_id = w.id), 0)
                           AS tinh_lai
                  FROM wallets w
                  LEFT JOIN transactions t
                         ON (t.to_wallet_id = w.id OR t.from_wallet_id = w.id)
                        AND t.status = 'SUCCESS'
                 GROUP BY w.id, w.balance
                HAVING w.balance <> COALESCE(sum(t.amount) FILTER (WHERE t.to_wallet_id = w.id), 0)
                                  - COALESCE(sum(t.amount) FILTER (WHERE t.from_wallet_id = w.id), 0)
                 ORDER BY w.id
                """)
                .query((rs, i) -> Map.<String, Object>of(
                        "walletId", rs.getLong("id"),
                        "recorded", rs.getBigDecimal("ghi_so").toString(),
                        "recomputed", rs.getBigDecimal("tinh_lai").toString()))
                .list()
                .forEach(ra::add);

        return ra;
    }

    private BigDecimal motSo(String sql) {
        return db.sql(sql).query(BigDecimal.class).single();
    }

    private Integer motSoNguyen(String sql) {
        return db.sql(sql).query(Integer.class).single();
    }
}
