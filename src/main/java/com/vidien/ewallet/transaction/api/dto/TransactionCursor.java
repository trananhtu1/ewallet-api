package com.vidien.ewallet.transaction.api.dto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import com.vidien.ewallet.transaction.domain.Transaction;
import com.vidien.ewallet.transaction.domain.exception.InvalidCursorException;

/**
 * Moc phan trang: mot cap {@code (createdAt, id)} da duoc ma hoa thanh chuoi.
 *
 * <p>
 * <b>Vi sao lai la mot CAP chu khong phai mot so.</b> Phan trang can mot thu tu TOAN PHAN -
 * hai dong bat ky phai so sanh duoc voi nhau. {@code created_at} mot minh khong du: hai giao
 * dich ghi trong cung mot micro-giay thi thu tu giua chung do planner quyet dinh, va cursor
 * tro toi mot trong hai dong khong noi duoc dong con lai da xem hay chua. Xem {@code V6}.
 *
 * <p>
 * <b>Vi sao ma hoa Base64 chu khong tra thang {@code ?after_id=123&after_at=...}.</b> Khong
 * phai de giau - Base64 ai cung giai duoc trong ba giay, va no KHONG phai bao mat. Ly do la
 * <b>hop dong</b>: mot chuoi mo nghia la client se doc no, sinh no, va tu tang len 1. Den luc
 * server doi cach phan trang - them mot cot vao moc chang han - thi moi client tu che cursor
 * deu vo. Mot chuoi doc thi noi duoc dung mot cau: <i>"cai nay cua server, dung dong vao"</i>.
 *
 * <p>
 * URL-safe Base64 ({@link Base64#getUrlEncoder()}) vi cursor di trong query string. Base64
 * thuong sinh ra {@code +} va {@code /}; trong URL thi {@code +} bi doc thanh DAU CACH va
 * {@code /} thi vo nghia. Va {@code withoutPadding()} de bo dau {@code =} - no hop le trong
 * query string nhung lam nguoi doc log tuong day la mot phep gan.
 *
 * @param createdAt thoi diem cua dong cuoi trang truoc
 * @param id ma cua dong cuoi trang truoc, dung de pha the hoa khi createdAt trung nhau
 */
public record TransactionCursor(Instant createdAt, long id) {

    private static final String NGAN_CACH = "|";

    /** Moc tro toi dong cuoi cua trang vua tra ve. */
    public static TransactionCursor of(Transaction tx) {
        return new TransactionCursor(tx.getCreatedAt(), tx.getId());
    }

    /**
     * Ma hoa thanh chuoi cho client cam ve.
     *
     * <p>
     * {@code Instant.toString()} ra dinh dang ISO-8601 giu du do phan giai nano ma Java co.
     * Postgres luu TIMESTAMPTZ toi MICRO-giay, tuc la gia tri doc len tu database luon co
     * nano chia het cho 1000 - khong co chuyen lam tron mat khi vong ve.
     */
    public String encode() {
        String tho = createdAt.toString() + NGAN_CACH + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tho.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Doc nguoc chuoi client gui len.
     *
     * <p>
     * ⚠️ Bat {@code RuntimeException} chu khong liet ke tung loai. Duong di tu mot chuoi rac
     * den mot ngoai le co it nhat bon nhanh: {@code IllegalArgumentException} (Base64 sai ky
     * tu), {@code DateTimeParseException} (thoi gian sai dinh dang), {@code NumberFormatException}
     * (id khong phai so), {@code ArrayIndexOutOfBoundsException} (thieu dau ngan cach). Bo sot
     * mot nhanh la mot chuoi rac bien thanh HTTP 500.
     *
     * <p>
     * Day la mot trong RAT IT cho bat {@code RuntimeException} la dung: dau vao hoan toan
     * khong dang tin, va MOI cach no hong deu quy ve cung mot ket luan - 400.
     */
    public static TransactionCursor decode(String chuoi) {
        try {
            String tho = new String(Base64.getUrlDecoder().decode(chuoi), StandardCharsets.UTF_8);
            int vach = tho.lastIndexOf(NGAN_CACH);
            if (vach < 0) {
                throw new IllegalArgumentException("thieu dau ngan cach");
            }
            return new TransactionCursor(
                    Instant.parse(tho.substring(0, vach)),
                    Long.parseLong(tho.substring(vach + 1)));
        } catch (RuntimeException e) {
            throw new InvalidCursorException(chuoi, e);
        }
    }
}
