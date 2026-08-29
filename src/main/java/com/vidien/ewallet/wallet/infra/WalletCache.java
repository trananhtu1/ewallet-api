package com.vidien.ewallet.wallet.infra;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import com.vidien.ewallet.wallet.domain.Wallet;

/**
 * ⭐ Lop doc vi CO CACHE - va no la mot bean RIENG, khong phai vai method trong WalletService.
 *
 * <p>
 * <b>Ly do thu nhat, va no la ly do BAO MAT.</b> {@code WalletService.findById} lam HAI viec:
 * kiem quyen ({@code requireOwn}) roi moi doc. Dat {@code @Cacheable} thang len method do thi
 * <b>cache hit se nhay qua CA HAI</b> - Spring tra ket qua tu cache va <b>than method khong
 * bao gio chay</b>. Nghia la:
 *
 * <pre>
 * ke tan cong goi GET /wallets/1  -> 403, dung
 * chu vi that goi  GET /wallets/1  -> 200, cache duoc nap
 * ke tan cong goi lai GET /wallets/1 -> 200 + SO DU CUA NGUOI KHAC
 * </pre>
 *
 * Do la <b>dung cai lo BOLA da bit o PR #2</b>, mo lai bang mot dong annotation trong nhin
 * hoan toan vo hai. {@code @Cacheable} khong phai "cai nhanh hon" - no la <b>khong chay method
 * nua</b>. Nen thu duy nhat duoc phep nam trong method co {@code @Cacheable} la viec DOC, con
 * kiem quyen phai o BEN NGOAI va chay TRUOC.
 *
 * <p>
 * <b>Ly do thu hai, va no la ly do KY THUAT.</b> Annotation cua Spring chi co tac dung khi loi
 * goi DI QUA PROXY. Mot method trong {@code WalletService} goi thang sang method khac cung
 * class ({@code this.x()}) thi khong qua proxy, va {@code @Cacheable} bi <b>bo qua HOAN TOAN -
 * khong loi, khong canh bao</b>, cache khong bao gio duoc dung. Day dung la loai loi da gap
 * NAM lan trong project: khai bao mot thu ma thieu cai kich hoat no. Bean rieng thi loi goi
 * bat buoc di qua proxy.
 *
 * <p>
 * 📌 Cung ly do voi ghi chu {@code @Transactional} trong {@code WalletService}. Cung mot bay,
 * hai annotation khac nhau.
 */
@Component
public class WalletCache {

    private final WalletRepository wallets;

    public WalletCache(WalletRepository wallets) {
        this.wallets = wallets;
    }

    /**
     * Doc vi, uu tien cache.
     *
     * <p>
     * ⚠️ Khoa cache la {@code walletId} - KHONG duoc dinh nguoi goi vao khoa. Dinh vao thi moi
     * nguoi goi mot ban sao, ty le trung giam, va quan trong hon: hai ban sao cua CUNG mot vi
     * co the lech nhau sau khi xoa cache sot mot cai.
     *
     * <p>
     * {@code unless = "#result == null"}: khong cache "vi khong ton tai". Cache no thi mot lan
     * goi id sai se ghi nho ket qua rong, va vi that su tao ra sau do se bi bao la khong ton
     * tai cho toi khi het TTL.
     */
    @Cacheable(value = "wallet", key = "#walletId", unless = "#result == null")
    public Wallet read(long walletId) {
        return wallets.findById(walletId).orElse(null);
    }

    /**
     * Bo ban cache cua mot vi.
     *
     * <p>
     * ⚠️ <b>Goi ham nay SAU KHI transaction da commit, khong phai trong luc dang ghi.</b> Xoa
     * som thi giua luc do mot request khac doc vi, khong thay cache, doc database - va no doc
     * duoc GIA TRI CU vi transaction ghi CHUA commit. Ket qua: cache duoc nap lai bang so du
     * cu, roi transaction commit, va <b>cache giu con so sai cho toi khi het TTL</b>.
     *
     * <p>
     * Do la mot cua so hep, nhung no la cua so mo suot ngay tren mot he thong that. Cach chan:
     * {@code @TransactionalEventListener(AFTER_COMMIT)} - xem {@link WalletCacheEvictor}.
     */
    @CacheEvict(value = "wallet", key = "#walletId")
    public void evict(long walletId) {
        // Than rong co y: toan bo viec nam o annotation.
    }

    // ⚠️ O DAY TUNG CO MOT METHOD find() BOC read() VAO Optional CHO GON:
    //
    //     public Optional<Wallet> find(long id) { return Optional.ofNullable(read(id)); }
    //
    // No lam CACHE NGUNG HOAT DONG HOAN TOAN, va khong mot dong log nao bao. Nguoi goi vao
    // find() thi di qua proxy - nhung find() KHONG co annotation nao, va loi goi read() ben
    // trong no la this.read(), tuc la KHONG con di qua proxy nua. @Cacheable bi bo qua sach.
    //
    // Do dung la cai bay ghi trong Javadoc dau file nay, va no van xay ra. Bat duoc nho
    // WalletCacheIT.cacheThucSuDuocDung: sua so du sau lung app roi doc lai - neu ra so MOI
    // thi cache chua bao gio duoc dung.
    //
    //     expected: 100000.00
    //      but was: 777.00
    //
    // Bai hoc: "bean rieng" chua du. Phai la MOT LOI GOI TU BEAN KHAC den DUNG method mang
    // annotation. Mot lop boc mong dat giua la du de vo hieu hoa no.
}
