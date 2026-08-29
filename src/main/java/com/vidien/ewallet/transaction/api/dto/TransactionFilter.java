package com.vidien.ewallet.transaction.api.dto;

import java.time.Instant;
import com.vidien.ewallet.transaction.domain.TransactionStatus;
import com.vidien.ewallet.transaction.domain.TransactionType;

/**
 * Bo loc lich su giao dich. Moi truong deu co the {@code null} = khong loc theo truong do.
 *
 * <p>
 * <b>Ba loai loc, va chung KHONG giong nhau ve gia.</b> Day la y chinh cua ca buoi:
 *
 * <table border="1">
 * <caption>Gia cua tung bo loc</caption>
 * <tr><th>Loc theo</th><th>Postgres lam gi</th><th>Gia</th></tr>
 * <tr>
 *   <td>{@code direction}</td>
 *   <td><b>Bo han mot nhanh</b> cua UNION ALL</td>
 *   <td><b>AM</b> - nhanh hon khong loc</td>
 * </tr>
 * <tr>
 *   <td>{@code from} / {@code to}</td>
 *   <td>Thu hep {@code Index Cond} - index da co {@code created_at}</td>
 *   <td>Gan bang khong</td>
 * </tr>
 * <tr>
 *   <td>{@code type} / {@code status}</td>
 *   <td>{@code Filter}: doc dong len ROI moi loai</td>
 *   <td><b>Tang theo do hiem</b> cua gia tri</td>
 * </tr>
 * </table>
 *
 * <p>
 * ⚠️ Dong cuoi la cho de sap bay. {@code status = 'FAILED'} nghe nhu mot bo loc binh thuong,
 * nhung {@code status} KHONG nam trong index. Voi mot vi co 200.000 giao dich ma chi 3 dong
 * {@code FAILED}, Postgres phai doc <b>rat sau</b> vao lich su moi gom du 20 dong - va cang lat
 * trang thi cang sau. Mot bo loc cang HIEM thi cang DAT, dung theo chieu nguoc voi truc giac.
 *
 * <p>
 * 📌 {@code direction} khong phai mot cot trong bang. Voi mot vi X: {@code IN} nghia la
 * {@code to_wallet_id = X}, {@code OUT} nghia la {@code from_wallet_id = X}. Ma cau lich su
 * von da la {@code UNION ALL} cua dung hai nhanh do - nen loc theo huong chi la <b>bo di mot
 * nhanh</b>. Khong them mot dieu kien nao ca.
 *
 * @param type loai giao dich, {@code null} = ca hai
 * @param status trang thai, {@code null} = ca hai
 * @param direction {@code "IN"} / {@code "OUT"}, {@code null} = ca hai chieu
 * @param from moc thoi gian som nhat (bao gom), {@code null} = khong gioi han
 * @param to moc thoi gian muon nhat (bao gom), {@code null} = khong gioi han
 */
public record TransactionFilter(TransactionType type, TransactionStatus status, String direction,
        Instant from, Instant to) {

    /** Khong loc gi ca. */
    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null);
    }

    public boolean coLoc() {
        return type != null || status != null || direction != null || from != null || to != null;
    }

    /** Nhanh "tien ra" ({@code from_wallet_id = X}) co can chay khong. */
    public boolean canNhanhRa() {
        return !"IN".equals(direction);
    }

    /** Nhanh "tien vao" ({@code to_wallet_id = X}) co can chay khong. */
    public boolean canNhanhVao() {
        return !"OUT".equals(direction);
    }
}
