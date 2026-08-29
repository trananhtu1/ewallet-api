package com.vidien.ewallet.transaction.api.dto;

import java.util.List;

/**
 * Mot trang lich su giao dich.
 *
 * <p>
 * <b>Khong co {@code total} va khong co {@code totalPages} - co y.</b> Dem tong so giao dich
 * cua mot vi la mot cau {@code COUNT(*)} quet HET, va no chay lai o MOI trang. Tren mot vi
 * co 200.000 dong thi rieng con so "trang 1/10000" dat hon ca 20 dong du lieu that. Client
 * gan nhu khong bao gio dung den no - giao dien lich su la cuon xuong, khong phai nhay trang.
 *
 * <p>
 * {@code hasMore} tra loi dung cau hoi ma giao dien can: <i>"con nut Xem them khong"</i>. No
 * duoc tinh bang cach hoi database {@code limit + 1} dong roi vut dong thua - re hon
 * {@code COUNT(*)} khong biet bao nhieu lan, va chinh xac tuyet doi.
 *
 * @param items cac giao dich cua trang nay, moi nhat truoc
 * @param nextCursor moc de xin trang tiep theo; {@code null} khi da het
 * @param hasMore con du lieu phia sau hay khong
 */
public record TransactionPage(List<TransactionView> items, String nextCursor, boolean hasMore) {

    /** Trang rong: khong co dong nao, va cung khong co gi phia sau. */
    public static TransactionPage empty() {
        return new TransactionPage(List.of(), null, false);
    }
}
