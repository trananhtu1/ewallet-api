package com.vidien.ewallet.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.transaction.api.dto.TransactionPage;
import com.vidien.ewallet.transaction.domain.exception.InvalidCursorException;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;

/**
 * ⭐ Phan trang bang cursor: nhung tinh chat KHONG the kiem bang unit test.
 *
 * <p>
 * Cau hoi o day khong phai <i>"ham co tra ve dung so dong khong"</i> - cai do mock duoc.
 * Cau hoi la <i>"khi du lieu THAY DOI giua hai lan lat trang thi nguoi dung thay gi"</i>, va
 * nguoi tra loi la Postgres: thu tu dong, cach so sanh bo gia tri, va viec index co giu duoc
 * thu tu toan phan hay khong.
 *
 * <p>
 * Test {@link #chenDongMoiGiuaChungKhongLamHienLaiDongDaXem()} chinh la thu se DO ngay neu ai
 * do doi ve {@code LIMIT/OFFSET} cho "gon hon". No la ly do ton tai cua ca file nay.
 */
class TransactionPagingIT extends PostgresIT {

    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcClient db;

    private long vi;
    private long viKia;

    @BeforeEach
    void dungDuLieu() {
        db.sql("DELETE FROM transactions").update();
        db.sql("DELETE FROM audit_log").update();
        db.sql("DELETE FROM refresh_tokens").update();
        db.sql("DELETE FROM wallets").update();
        db.sql("DELETE FROM users").update();

        vi = taoVi("chu@test.com");
        viKia = taoVi("doi@test.com");
    }

    private long taoVi(String email) {
        User u = users.save(User.builder()
                .email(email).passwordHash("x").fullName("T").build());
        return walletService.createForUser(u.getId()).getId();
    }

    /**
     * Ghi mot giao dich voi {@code created_at} DAT TAY.
     *
     * <p>
     * Khong dung {@code now()} mac dinh: test nay noi ve THU TU, nen thu tu phai do test quyet
     * dinh chu khong do toc do may chay test.
     *
     * <p>
     * ⚠️ Doi sang {@link OffsetDateTime} truoc khi bind. Driver pgjdbc <b>tu choi</b>
     * {@code Instant}: <i>"Can't infer the SQL type to use for an instance of
     * java.time.Instant"</i>. Ly do hop ly - {@code Instant} la mot diem tren truc thoi gian,
     * khong mang mui gio, nen driver khong biet nen goi no la {@code timestamp} hay
     * {@code timestamptz}. {@code OffsetDateTime} co mui gio nen khong con cho doan.
     */
    private void ghi(Instant luc, String soTien) {
        db.sql("""
                INSERT INTO transactions
                    (from_wallet_id, to_wallet_id, amount, type, status, created_at)
                VALUES (:tu, :den, :tien, 'TRANSFER', 'SUCCESS', :luc)
                """)
                .param("tu", vi).param("den", viKia)
                .param("tien", new BigDecimal(soTien))
                .param("luc", OffsetDateTime.ofInstant(luc, ZoneOffset.UTC))
                .update();
    }

    /** Lat het moi trang, gom tat ca id lai theo dung thu tu nguoi dung nhin thay. */
    private List<Long> latHet(int limit) {
        List<Long> thay = new ArrayList<>();
        String moc = null;
        // Tran cung de mot loi phan trang thanh vong lap vo tan khong treo ca lan chay test.
        for (int vong = 0; vong < 100; vong++) {
            TransactionPage trang = walletService.history(vi, limit, moc, vi);
            trang.items().forEach(v -> thay.add(v.id()));
            if (!trang.hasMore()) {
                return thay;
            }
            moc = trang.nextCursor();
        }
        throw new IllegalStateException("Lat qua 100 trang - phan trang khong bao gio ket thuc");
    }

    @Test
    @DisplayName("lat het 55 giao dich voi limit 10: moi dong hien dung MOT lan, dung thu tu")
    void latHetThiMoiDongHienDungMotLan() {
        Instant goc = Instant.parse("2026-08-29T00:00:00Z");
        for (int i = 0; i < 55; i++) {
            ghi(goc.plusSeconds(i), "10.00");
        }

        List<Long> thay = latHet(10);

        assertThat(thay).hasSize(55);
        assertThat(thay).doesNotHaveDuplicates();
        // Moi nhat truoc: id sinh tang dan theo thoi gian nen thu tu phai la giam dan.
        assertThat(thay).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    @DisplayName("⭐ chen giao dich MOI giua hai lan lat: khong dong nao hien lai lan hai")
    void chenDongMoiGiuaChungKhongLamHienLaiDongDaXem() {
        Instant goc = Instant.parse("2026-08-29T00:00:00Z");
        for (int i = 0; i < 20; i++) {
            ghi(goc.plusSeconds(i), "10.00");
        }

        TransactionPage trang1 = walletService.history(vi, 5, null, vi);
        List<Long> daXem = trang1.items().stream().map(v -> v.id()).toList();
        assertThat(daXem).hasSize(5);

        // Nguoi dung dang doc thi co 3 giao dich moi ghi vao - MOI HON tat ca dong dang co.
        for (int i = 1; i <= 3; i++) {
            ghi(goc.plusSeconds(100 + i), "99.00");
        }

        TransactionPage trang2 = walletService.history(vi, 5, trang1.nextCursor(), vi);
        List<Long> tiep = trang2.items().stream().map(v -> v.id()).toList();

        // Day la ca ma OFFSET lam sai: voi OFFSET 5, ba dong cuoi cua trang 1 se hien lai.
        assertThat(tiep).doesNotContainAnyElementsOf(daXem);
        assertThat(tiep).hasSize(5);
    }

    @Test
    @DisplayName("⭐ sau giao dich TRUNG created_at den micro-giay: khong sot, khong trung")
    void trungThoiDiemDenMicroGiayVanKhongSotKhongTrung() {
        // Cung MOT gia tri timestamptz cho ca sau dong. Postgres luu toi micro-giay, nen day
        // khong phai truong hop nhan tao: hai giao dich ghi cung luc duoi tai la co that.
        Instant motLuc = Instant.parse("2026-08-29T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        for (int i = 0; i < 6; i++) {
            ghi(motLuc, "10.00");
        }

        // limit 2 -> bat buoc cursor phai cat NGAY GIUA cum dong trung nhau.
        List<Long> thay = latHet(2);

        assertThat(thay).hasSize(6);
        assertThat(thay).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("trang cuoi: hasMore=false va nextCursor=null")
    void trangCuoiThiKhongCoMocTiepTheo() {
        Instant goc = Instant.parse("2026-08-29T00:00:00Z");
        for (int i = 0; i < 3; i++) {
            ghi(goc.plusSeconds(i), "10.00");
        }

        TransactionPage trang = walletService.history(vi, 10, null, vi);

        assertThat(trang.items()).hasSize(3);
        assertThat(trang.hasMore()).isFalse();
        assertThat(trang.nextCursor()).isNull();
    }

    @Test
    @DisplayName("cursor bia ra thi TU CHOI, khong lang le tra ve trang dau")
    void cursorBiaRaThiTuChoi() {
        ghi(Instant.parse("2026-08-29T00:00:00Z"), "10.00");

        assertThatThrownBy(() -> walletService.history(vi, 10, "khong-phai-cursor", vi))
                .isInstanceOf(InvalidCursorException.class);
    }
}
