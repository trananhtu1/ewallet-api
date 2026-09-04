/*
 * FEATURE  : Lịch sử giao dịch — lọc / tìm kiếm + phân trang
 * VAI TRÒ  : Sinh SQL lúc chạy: chỉ nối điều kiện khi nó thực sự có. Không có OR NULL nào.
 * LIÊN QUAN: TransactionFilter · TransactionCursor · V6 (index có id)
 */
package com.vidien.ewallet.transaction.infra;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.transaction.api.dto.TransactionCursor;
import com.vidien.ewallet.transaction.api.dto.TransactionFilter;
import com.vidien.ewallet.transaction.domain.Transaction;
import com.vidien.ewallet.transaction.domain.TransactionStatus;
import com.vidien.ewallet.transaction.domain.TransactionType;

/**
 * ⭐ Tim kiem lich su giao dich: cau SQL <b>duoc dung len luc chay</b>, khong phai mot chuoi
 * co dinh.
 *
 * <p>
 * <b>Vi sao khong dung {@code @Query} nhu hai cau kia.</b> Nam bo loc, moi cai co the co hoac
 * khong, la <b>32 to hop</b>. Viet 32 cau {@code @Query} thi khong ai bao tri noi; viet mot cau
 * duy nhat voi {@code (:x IS NULL OR cot = :x)} cho ca nam thi chay dung nhung Postgres phai
 * chuan bi MOT ke hoach dung cho ca 32 truong hop.
 *
 * <p>
 * ⚠️ <b>Va day la cho phai phan biet, khong duoc vo dua ca nam.</b> Buoi 14 da do duoc rang
 * {@code (:cursorAt IS NULL OR ...)} <b>giet index</b>. Nhung ket luan do KHONG ap dung cho moi
 * cot:
 *
 * <ul>
 *   <li>{@code created_at} <b>dieu khien</b> {@code Index Cond}. Boc no vao {@code OR NULL} la
 *       ha ca cau xuong {@code Filter} - mat sach cai gia da tra o V6.</li>
 *   <li>{@code status} / {@code type} <b>khong nam trong index</b>. Chung se la {@code Filter}
 *       dù viet cach nao. Voi chung thi {@code OR NULL} khong mat gi.</li>
 * </ul>
 *
 * File nay chon duong an toan cho ca hai: <b>chi noi them dieu kien khi no thuc su co</b>. Cau
 * SQL sinh ra luon la cau hep nhat co the, va khong bao gio co mot {@code OR NULL} nao.
 *
 * <p>
 * 📌 Va no la SQL dung THAM SO, khong phai noi chuoi. Moi gia tri nguoi dung gui len deu di qua
 * {@code :ten} - tuc la khong co duong nao cho SQL injection. Thu duy nhat duoc noi vao cau
 * lenh la <b>ten cot do chinh code nay quyet dinh</b>, khong bao gio la du lieu tu ben ngoai.
 */
@Repository
public class TransactionSearchRepository {

    private final JdbcClient db;

    public TransactionSearchRepository(JdbcClient db) {
        this.db = db;
    }

    private static final String COT = """
            id, from_wallet_id, to_wallet_id, amount, type, status, idempotency_key, note, created_at
            """;
    // ⚠️ Liet ke cot bang tay chu khong SELECT *, va do la co y: them mot cot vao bang ma
    // quen dong nay thi row mapper doc `rs.getString("note")` se nem
    // "column not found" - vo NGAY va noi ro thieu gi. SELECT * thi cot moi tu
    // chay vao, va cai vo se la mot cho khac, muon hon, kho lan hon.

    /**
     * Mot trang lich su, co loc va co cursor.
     *
     * @param cursor {@code null} = trang dau
     */
    public List<Transaction> search(long walletId, TransactionFilter loc, TransactionCursor cursor,
            int limit) {

        List<String> nhanh = new ArrayList<>();
        // ⭐ Loc theo huong = BO HAN mot nhanh, khong phai them mot dieu kien. Voi mot vi thi
        // "tien ra" chinh la from_wallet_id = X, va do dung la nhanh thu nhat cua UNION ALL.
        if (loc.canNhanhRa()) {
            nhanh.add(motNhanh("from_wallet_id", loc, cursor));
        }
        if (loc.canNhanhVao()) {
            nhanh.add(motNhanh("to_wallet_id", loc, cursor));
        }

        String sql = """
                SELECT * FROM (
                %s
                ) x
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """.formatted(String.join("\n    UNION ALL\n", nhanh));

        var goi = db.sql(sql).param("id", walletId).param("limit", limit);

        if (cursor != null) {
            // OffsetDateTime chu khong Instant: driver pgjdbc tu choi Instant vi no khong mang
            // mui gio - "Can't infer the SQL type". Da gap that o buoi 14.
            goi = goi.param("cursorAt", OffsetDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC))
                    .param("cursorId", cursor.id());
        }
        if (loc.type() != null) {
            goi = goi.param("type", loc.type().name());
        }
        if (loc.status() != null) {
            goi = goi.param("status", loc.status().name());
        }
        if (loc.from() != null) {
            goi = goi.param("from", OffsetDateTime.ofInstant(loc.from(), ZoneOffset.UTC));
        }
        if (loc.to() != null) {
            goi = goi.param("to", OffsetDateTime.ofInstant(loc.to(), ZoneOffset.UTC));
        }

        return goi.query(TransactionSearchRepository::doiDong).list();
    }

    /**
     * Mot nhanh cua UNION ALL.
     *
     * <p>
     * ⚠️ MOI dieu kien phai nam TRONG nhanh, khong duoc de o ngoai. Dat o ngoai thi nhanh van
     * lay {@code LIMIT} dong dau tien cua no roi vong ngoai moi loc - trang tra ve it hon
     * {@code limit} dong, hoac rong han, du duoi con day du lieu. Da ghi o buoi 14 cho cursor;
     * dung y het cho bo loc.
     *
     * @param cot {@code from_wallet_id} hoac {@code to_wallet_id} - do CODE quyet dinh, khong
     *        bao gio la du lieu nguoi dung gui len
     */
    private static String motNhanh(String cot, TransactionFilter loc, TransactionCursor cursor) {
        StringBuilder dieuKien = new StringBuilder("WHERE " + cot + " = :id");

        if (cursor != null) {
            dieuKien.append("\n       AND (created_at, id) < (:cursorAt, :cursorId)");
        }
        // created_at nam TRONG index (V6), nen hai dong nay thu hep Index Cond chu khong phai
        // doc dong len roi loai. Gan nhu mien phi.
        if (loc.from() != null) {
            dieuKien.append("\n       AND created_at >= :from");
        }
        if (loc.to() != null) {
            dieuKien.append("\n       AND created_at <= :to");
        }
        // type/status KHONG nam trong index -> day la Filter that su. Bo loc cang HIEM thi
        // Postgres cang phai doc sau moi gom du limit dong.
        if (loc.type() != null) {
            dieuKien.append("\n       AND type = :type");
        }
        if (loc.status() != null) {
            dieuKien.append("\n       AND status = :status");
        }

        return """
                    (SELECT %s
                     FROM transactions
                     %s
                     ORDER BY created_at DESC, id DESC LIMIT :limit)
                """.formatted(COT.strip(), dieuKien);
    }

    /**
     * Dung lai entity tu mot dong ket qua.
     *
     * <p>
     * Viet tay vi day la {@code JdbcClient} chu khong phai JPA - khong co ai anh xa ho. Doi lai
     * thi ro rang mot chuyen: doi ten mot cot ma quen sua o day thi <b>test do</b>, chu khong
     * phai mot {@code null} lang le troi vao JSON tra ve cho client.
     */
    private static Transaction doiDong(java.sql.ResultSet rs, int soDong) throws java.sql.SQLException {
        return Transaction.builder()
                .id(rs.getLong("id"))
                .fromWalletId(rs.getObject("from_wallet_id", Long.class))
                .toWalletId(rs.getObject("to_wallet_id", Long.class))
                .amount(rs.getBigDecimal("amount"))
                .type(TransactionType.valueOf(rs.getString("type")))
                .status(TransactionStatus.valueOf(rs.getString("status")))
                .idempotencyKey(rs.getString("idempotency_key"))
                .note(rs.getString("note"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class).toInstant())
                .build();
    }
}
