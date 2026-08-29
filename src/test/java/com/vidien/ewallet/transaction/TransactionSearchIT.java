/*
 * FEATURE  : Lịch sử giao dịch — lọc / tìm kiếm
 * VAI TRÒ  : Lọc phải hợp tác được với phân trang, và phải nằm TRONG nhánh UNION ALL.
 * LIÊN QUAN: TransactionFilter · TransactionSearchRepository
 */
package com.vidien.ewallet.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.transaction.api.dto.TransactionFilter;
import com.vidien.ewallet.transaction.api.dto.TransactionPage;
import com.vidien.ewallet.transaction.domain.TransactionStatus;
import com.vidien.ewallet.transaction.domain.TransactionType;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;

/**
 * ⭐ Loc lich su giao dich, va cho de sai nhat: <b>loc phai hop tac duoc voi phan trang</b>.
 *
 * <p>
 * Mot bo loc viet sai van tra ve du lieu <b>dung dinh dang</b>. Khong exception, khong 500 -
 * chi la danh sach ngan hon su that, hoac trang 2 rong trong khi ben duoi con day. Nguoi dung
 * khong co cach nao biet.
 *
 * <p>
 * Ba cach sai duoc canh o day:
 * <ol>
 *   <li>loc dat NGOAI {@code UNION ALL} -> trang tra ve thieu dong</li>
 *   <li>loc theo huong lam sai ca huong con lai</li>
 *   <li>loc + cursor cung luc -> trang 2 mat dong hoac hien lai dong da xem</li>
 * </ol>
 */
class TransactionSearchIT extends PostgresIT {

    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository users;

    private long vi;
    private long viKia;

    private static final Instant GOC = Instant.parse("2026-08-29T00:00:00Z");

    @BeforeEach
    void dungDuLieu() {
        xoaHetDuLieu();

        vi = taoVi("chu@test.com");
        viKia = taoVi("doi@test.com");
    }

    private long taoVi(String email) {
        User u = users.save(User.builder()
                .email(email).passwordHash("x").fullName("T").build());
        return walletService.createForUser(u.getId()).getId();
    }

    /**
     * Ghi mot giao dich voi day du dac diem de loc.
     *
     * @param ra {@code true} = tien di RA khoi vi (from = vi), {@code false} = tien VAO
     */
    private void ghi(int giay, boolean ra, TransactionType loai, TransactionStatus trangThai) {
        // ⚠️ (Long) chu khong de nguyen `vi`. `vi` la `long` nguyen thuy, nen
        //     ra ? vi : (loai == DEPOSIT ? null : viKia)
        // co kieu chung la `long`, va Java se UNBOX cai `null` -> NullPointerException ngay
        // truoc khi cham toi database. Da do that: "Cannot invoke Long.longValue()".
        //
        // Cung ho voi bai hoc 24/08: de `Long id` chu khong `long id` trong DTO, vi kieu
        // nguyen thuy khong nhan null. Lan nay no do o mot bieu thuc ba ngoi trong TEST.
        Long tuVi = ra ? (Long) vi : (loai == TransactionType.DEPOSIT ? null : (Long) viKia);

        db.sql("""
                INSERT INTO transactions
                    (from_wallet_id, to_wallet_id, amount, type, status, created_at)
                VALUES (:tu, :den, 100.00, :loai, :tt, :luc)
                """)
                .param("tu", tuVi)
                .param("den", ra ? viKia : vi)
                .param("loai", loai.name())
                .param("tt", trangThai.name())
                .param("luc", OffsetDateTime.ofInstant(GOC.plusSeconds(giay), ZoneOffset.UTC))
                .update();
    }

    private TransactionPage doc(TransactionFilter loc, int limit, String cursor) {
        return walletService.history(vi, limit, cursor, loc, vi);
    }

    private static List<String> huong(TransactionPage trang) {
        return trang.items().stream().map(v -> v.direction()).toList();
    }

    @Test
    @DisplayName("loc theo huong: IN chi ra tien vao, OUT chi ra tien ra")
    void locTheoHuong() {
        ghi(1, true, TransactionType.TRANSFER, TransactionStatus.SUCCESS);    // OUT
        ghi(2, false, TransactionType.TRANSFER, TransactionStatus.SUCCESS);   // IN
        ghi(3, true, TransactionType.TRANSFER, TransactionStatus.SUCCESS);    // OUT

        assertThat(huong(doc(new TransactionFilter(null, null, "OUT", null, null), 20, null)))
                .containsExactly("OUT", "OUT");

        assertThat(huong(doc(new TransactionFilter(null, null, "IN", null, null), 20, null)))
                .containsExactly("IN");

        // Khong loc thi ra ca ba - de chac rang hai khang dinh tren khong phai do thieu du lieu.
        assertThat(doc(TransactionFilter.none(), 20, null).items()).hasSize(3);
    }

    @Test
    @DisplayName("loc theo status: chi ra dong FAILED, va dung so luong")
    void locTheoStatus() {
        for (int i = 1; i <= 10; i++) {
            ghi(i, true, TransactionType.TRANSFER,
                    i % 5 == 0 ? TransactionStatus.FAILED : TransactionStatus.SUCCESS);
        }

        TransactionPage trang = doc(
                new TransactionFilter(null, TransactionStatus.FAILED, null, null, null), 20, null);

        assertThat(trang.items()).hasSize(2);
        assertThat(trang.items()).allSatisfy(v -> assertThat(v.status()).isEqualTo("FAILED"));
    }

    @Test
    @DisplayName("loc theo khoang thoi gian: chi ra dong nam trong khoang, bao gom hai dau")
    void locTheoKhoangThoiGian() {
        for (int i = 1; i <= 10; i++) {
            ghi(i, true, TransactionType.TRANSFER, TransactionStatus.SUCCESS);
        }

        TransactionPage trang = doc(new TransactionFilter(null, null, null,
                GOC.plusSeconds(3), GOC.plusSeconds(6)), 20, null);

        // giay 3,4,5,6 -> 4 dong. Bao gom CA HAI dau: >= va <=.
        assertThat(trang.items()).hasSize(4);
    }

    @Test
    @DisplayName("⭐ loc + phan trang cung luc: lat het van dung so, khong sot khong trung")
    void locVaPhanTrangHopTacDuoc() {
        // 20 dong, mot nua FAILED. Loc FAILED con 10, lat bang limit 3 -> 4 trang.
        for (int i = 1; i <= 20; i++) {
            ghi(i, true, TransactionType.TRANSFER,
                    i % 2 == 0 ? TransactionStatus.FAILED : TransactionStatus.SUCCESS);
        }

        TransactionFilter chiFailed =
                new TransactionFilter(null, TransactionStatus.FAILED, null, null, null);

        List<Long> thay = new java.util.ArrayList<>();
        String moc = null;
        for (int vong = 0; vong < 20; vong++) {
            TransactionPage trang = doc(chiFailed, 3, moc);
            trang.items().forEach(v -> thay.add(v.id()));
            if (!trang.hasMore()) {
                break;
            }
            moc = trang.nextCursor();
        }

        // Day la cho bo loc dat SAI VI TRI se lo ra: neu loc o ngoai UNION ALL thi moi nhanh
        // van lay 3 dong dau (phan lon la SUCCESS), vong ngoai loc sach, va trang tra ve gan
        // nhu rong du duoi con 10 dong FAILED.
        assertThat(thay).hasSize(10);
        assertThat(thay).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("hai bo loc cung luc: giao cua chung, khong phai hop")
    void haiBoLocCungLuc() {
        ghi(1, true, TransactionType.TRANSFER, TransactionStatus.FAILED);    // OUT + FAILED  ✓
        ghi(2, true, TransactionType.TRANSFER, TransactionStatus.SUCCESS);   // OUT + SUCCESS
        ghi(3, false, TransactionType.TRANSFER, TransactionStatus.FAILED);   // IN  + FAILED

        TransactionPage trang = doc(
                new TransactionFilter(null, TransactionStatus.FAILED, "OUT", null, null), 20, null);

        assertThat(trang.items()).hasSize(1);
        assertThat(trang.items().get(0).direction()).isEqualTo("OUT");
        assertThat(trang.items().get(0).status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("loc theo type: DEPOSIT khong co vi doi phuong, van ra dung")
    void locTheoType() {
        ghi(1, false, TransactionType.DEPOSIT, TransactionStatus.SUCCESS);
        ghi(2, true, TransactionType.TRANSFER, TransactionStatus.SUCCESS);

        TransactionPage trang = doc(
                new TransactionFilter(TransactionType.DEPOSIT, null, null, null, null), 20, null);

        assertThat(trang.items()).hasSize(1);
        assertThat(trang.items().get(0).type()).isEqualTo("DEPOSIT");
        // Nap tien khong co vi nguon -> doi phuong phai la null, khong phai "vi #null".
        assertThat(trang.items().get(0).counterpartyWalletId()).isNull();
        assertThat(trang.items().get(0).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
