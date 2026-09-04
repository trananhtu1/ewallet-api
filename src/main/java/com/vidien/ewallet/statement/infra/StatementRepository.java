/*
 * FEATURE  : Sao kê theo tháng
 * VAI TRÒ  : Câu GROUP BY gộp giao dịch theo tháng.
 * LIÊN QUAN: MonthlyStatement · StatementController
 */
package com.vidien.ewallet.statement.infra;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.statement.api.dto.MonthlyStatement;

/**
 * ⭐ Cho dung {@code GROUP BY} that dau tien cua project, va no goi lai dung bai hoc
 * buoi 7: <b>gop TRUOC khi tra ve, khong gop o frontend.</b>
 *
 * <p>
 * Cach nguoc lai - lay het giao dich roi cong o JavaScript - hong o ba mat:
 * <ol>
 * <li>Chuyen ca nghin dong qua mang de hien ra 6 con so
 * <li>Cong tien bang {@code double}, dung cai bay ca he thong nay da tranh tu dau
 * <li>Sai ngay khi co phan trang: chi cong duoc trang dang cam tren tay
 * </ol>
 *
 * <p>
 * Dung {@code JdbcClient} chu khong JPA: day la mot cau <b>tong hop</b>, ket qua khong
 * phai entity nao ca. Ep no thanh JPQL thi phai khai them mot lop projection cho mot
 * cau truy van dung o dung mot cho.
 */
@Repository
public class StatementRepository {

    private final JdbcClient db;

    public StatementRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Tong hop {@code months} thang gan nhat cua mot vi.
     *
     * <p>
     * ⚠️ <b>{@code FILTER (WHERE ...)} chu khong phai {@code CASE WHEN}.</b> Ca hai deu
     * chay, nhung {@code FILTER} la cu phap chuan SQL:2003 danh rieng cho viec nay va
     * doc ra dung y dinh - "cong cot nay, chi nhung dong thoa dieu kien kia". Bang
     * {@code CASE} thi phai viet {@code SUM(CASE WHEN ... THEN amount ELSE 0 END)}, va
     * cai {@code ELSE 0} do la mot chi tiet ky thuat lot vao giua mot cau ve nghiep vu.
     *
     * <p>
     * ⚠️ {@code COALESCE} bao boc moi {@code SUM}: mot thang co giao dich nhung toan
     * tien ra thi {@code SUM(...) FILTER (tien vao)} tra ve <b>NULL</b>, khong phai 0.
     * Thieu {@code COALESCE} thi {@code BigDecimal} nhan null va man hinh hien "—" cho
     * mot thang thuc su co so lieu.
     *
     * <p>
     * ⚠️ Loc {@code status = 'SUCCESS'}: dong FAILED co ton tai trong so cai (co y - xem
     * TransferService), nhung tien khong he chay. Cong chung vao sao ke la bao cao mot
     * so tien chua bao gio ra khoi vi.
     *
     * <p>
     * {@code date_trunc} + {@code to_char} chu khong {@code EXTRACT(month)}: thang 9 cua
     * 2025 va cua 2026 la hai dong khac nhau, {@code EXTRACT} gop chung lam mot.
     */
    public List<MonthlyStatement> theoThang(long walletId, int months) {
        return db.sql("""
                SELECT to_char(date_trunc('month', created_at), 'YYYY-MM')            AS month,
                       COALESCE(SUM(amount) FILTER (WHERE to_wallet_id   = :id), 0)   AS money_in,
                       COALESCE(SUM(amount) FILTER (WHERE from_wallet_id = :id), 0)   AS money_out,
                       COUNT(*)                                                       AS cnt
                  FROM transactions
                 WHERE (from_wallet_id = :id OR to_wallet_id = :id)
                   AND status = 'SUCCESS'
                 GROUP BY date_trunc('month', created_at)
                 ORDER BY date_trunc('month', created_at) DESC
                 LIMIT :months
                """)
                .param("id", walletId)
                .param("months", months)
                .query((rs, n) -> {
                    var vao = rs.getBigDecimal("money_in");
                    var ra = rs.getBigDecimal("money_out");
                    // Tru bang BigDecimal o tang Java - van khong phai double, va o day
                    // thi ro hon la them mot cot nua vao cau SQL.
                    return new MonthlyStatement(rs.getString("month"), vao, ra,
                            vao.subtract(ra), rs.getInt("cnt"));
                })
                .list();
    }
}
