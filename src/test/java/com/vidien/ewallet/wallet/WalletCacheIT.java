package com.vidien.ewallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.vidien.ewallet.support.PostgresIT;
import com.vidien.ewallet.transfer.domain.TransferService;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.WalletService;
import com.vidien.ewallet.wallet.domain.exception.WalletNotFoundException;

/**
 * ⭐ Cache: bon cau hoi ma chi mot Redis THAT + mot Postgres THAT moi tra loi duoc.
 *
 * <p>
 * Cache la loai tinh nang <b>hong ma khong bao</b>. Khong co cache thi he thong cham; cache
 * hong thi he thong <b>tra ve so tien sai</b>, va tra ve rat nhanh. Bon test duoi day khong
 * kiem "cache co nhanh khong" - do la thu khong test duoc va cung khong dang. Chung kiem bon
 * cach cache lam sai:
 *
 * <ol>
 *   <li>cache khong duoc dung - {@code @Cacheable} khai bao ma khong ai kich hoat</li>
 *   <li>cache lam nguoi khong co quyen doc duoc so du - lo BOLA mo lai</li>
 *   <li>cache giu so du cu sau khi nap tien</li>
 *   <li>chuyen tien xoa cache mot ben, quen ben con lai</li>
 * </ol>
 */
class WalletCacheIT extends PostgresIT {

    @Autowired
    private WalletService walletService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private UserRepository users;
    @Autowired
    private CacheManager cacheManager;

    private long viA;
    private long viB;
    private long viNguoiLa;

    /**
     * Moi vi tung duoc tao trong file nay, de xoa cache cua chung truoc moi test.
     *
     * <p>
     * ⚠️ Cho nay ban dau la mot dong {@code cacheManager.getCache("wallet").clear()}. <b>No
     * khong lam gi ca.</b> Do bang tay tren Redis that:
     *
     * <pre>
     * TRUOC clear, get(viA) = ValueWrapper for [Wallet@43717598]
     * SAU  clear, get(viA) = ValueWrapper for [Wallet@7ef432ce]   &lt;- van con
     * SAU  evict, get(viA) = null
     * </pre>
     *
     * Doi tuong doi dia chi giua hai lan doc, tuc la no thuc su duoc dung lai tu Redis chu
     * khong phai mot ban trong RAM - gia tri van nam nguyen do sau {@code clear()}.
     * {@code evict(key)} thi xoa that.
     *
     * <p>
     * Day la lan thu BAY trong project mot cau lenh trong nhu dang lam viec ma khong lam gi.
     * Va lan nay no nam trong CHINH BO TEST: mot dong don dep khong don gi de lai cac test
     * nhin thay ban cache cua nhau, tuc la ket qua doi theo THU TU CHAY.
     */
    private static final List<Long> DA_TAO = new ArrayList<>();

    @BeforeEach
    void dungDuLieu() {
        xoaHetDuLieu();

        // Xoa tung khoa mot. Xem ghi chu o DA_TAO: clear() khong xoa duoc.
        DA_TAO.forEach(id -> cacheManager.getCache("wallet").evict(id));
        DA_TAO.clear();

        viA = taoVi("a@test.com", "100000.00");
        viB = taoVi("b@test.com", "100000.00");
        viNguoiLa = taoVi("ke-la@test.com", "500.00");
    }

    private long taoVi(String email, String soDu) {
        User u = users.save(User.builder()
                .email(email).passwordHash("x").fullName("T").build());
        long viId = walletService.createForUser(u.getId()).getId();
        db.sql("UPDATE wallets SET balance = :b WHERE id = :id")
                .param("b", new BigDecimal(soDu)).param("id", viId).update();
        DA_TAO.add(viId);
        return viId;
    }

    /** Doi so du SAU LUNG ung dung - khong qua service, nen khong co gi xoa cache. */
    private void doiSoDuLenLut(long viId, String soDu) {
        db.sql("UPDATE wallets SET balance = :b WHERE id = :id")
                .param("b", new BigDecimal(soDu)).param("id", viId).update();
    }

    private BigDecimal doc(long viId) {
        return walletService.findById(viId, viId).getBalance();
    }

    @Test
    @DisplayName("cache CO chay that - doi so du sau lung app thi lan doc thu hai van ra so cu")
    void cacheThucSuDuocDung() {
        assertThat(doc(viA)).isEqualByComparingTo("100000.00");

        // Sua thang vao database, khong qua service. Neu cache dang chay thi lan doc sau van
        // tra ve con so cu.
        doiSoDuLenLut(viA, "777.00");

        assertThat(doc(viA))
                .as("doc lan hai van ra so cu => cache dang duoc dung that")
                .isEqualByComparingTo("100000.00");

        // Va de chac rang khang dinh tren khong phai do lenh UPDATE that bai: doc THANG
        // database, khong qua service.
        //
        // ⚠️ Ban dau cho nay la evict() roi doc lai. No dung ve y nghia nhung ĐỎ MỘT LẦN
        // trong bon lan chay full suite - tuc la mot test FLAKY. Chua truy ra co che (cung
        // ho voi chuyen clear() khong xoa gi, ghi o DA_TAO), nhung mot test flaky thi phai
        // sua ngay chu khong duoc chay lai cho toi khi xanh: no day nguoi ta thoi quen bo qua
        // mau do.
        //
        // Ban nay khong phu thuoc vao thoi diem lenh xoa lan toi Redis nua - no chi hoi
        // database mot cau ma database luon tra loi duoc.
        BigDecimal trongDatabase = db.sql("SELECT balance FROM wallets WHERE id = :id")
                .param("id", viA).query(BigDecimal.class).single();
        assertThat(trongDatabase)
                .as("UPDATE co that su chay - nen so cu o tren dung la tu cache")
                .isEqualByComparingTo("777.00");
    }

    @Test
    @DisplayName("⭐ cache hit KHONG duoc nhay qua kiem quyen - lo BOLA khong mo lai")
    void cacheHitVanPhaiKiemQuyen() {
        // Chu vi doc truoc: cache cua viA gio DA CO.
        assertThat(doc(viA)).isEqualByComparingTo("100000.00");

        // Nguoi la doc vi cua nguoi khac. Neu @Cacheable dat nham len chinh method co
        // requireOwn thi cache hit se tra ve 100000.00 kem ma 200.
        assertThatThrownBy(() -> walletService.findById(viA, viNguoiLa))
                .as("cache hit van phai di qua requireOwn")
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    @DisplayName("nap tien xong thi lan doc ngay sau do ra SO MOI, khong phai so cu")
    void napTienThiCacheBiXoa() {
        assertThat(doc(viA)).isEqualByComparingTo("100000.00");   // nap cache

        walletService.deposit(viA, new BigDecimal("5000.00"), viA);

        assertThat(doc(viA)).isEqualByComparingTo("105000.00");
    }

    @Test
    @DisplayName("⭐ chuyen tien xoa cache CA HAI vi, khong chi vi nguon")
    void chuyenTienThiXoaCacheCaHaiBen() {
        // Nap cache cho ca hai. Day la kich ban that: ca hai nguoi deu dang mo app.
        assertThat(doc(viA)).isEqualByComparingTo("100000.00");
        assertThat(doc(viB)).isEqualByComparingTo("100000.00");

        transferService.transfer(viA, viB, new BigDecimal("30000.00"), viA, null);

        assertThat(doc(viA))
                .as("nguoi GUI phai thay so du moi")
                .isEqualByComparingTo("70000.00");

        // Day la ben hay bi quen. Nguoi nhan nhin man hinh cu, tuong tien chua toi, va
        // nguoi gui thi da bi tru - mot cuoc goi len tong dai va rat co the mot lenh gui lai.
        assertThat(doc(viB))
                .as("nguoi NHAN cung phai thay so du moi")
                .isEqualByComparingTo("130000.00");
    }
}
