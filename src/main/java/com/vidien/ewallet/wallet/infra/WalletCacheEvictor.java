/*
 * FEATURE  : Cache số dư ví (Redis)
 * VAI TRÒ  : Xoá cache SAU KHI transaction commit, không phải trong lúc đang ghi.
 * LIÊN QUAN: WalletChangedEvent · WalletCache · WalletService · TransferService
 * BÀI GIẢNG: java-learn/java/07-cache/BUOI-15-REDIS-CACHE.md
 */
package com.vidien.ewallet.wallet.infra;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import com.vidien.ewallet.wallet.domain.WalletChangedEvent;

/**
 * Xoa cache cua mot vi, <b>SAU KHI</b> transaction ghi da commit.
 *
 * <p>
 * {@code @TransactionalEventListener} khac {@code @EventListener} o dung mot chuyen, va chuyen
 * do la toan bo ly do file nay ton tai: no <b>hoan</b> viec xu ly lai cho toi khi transaction
 * ket thuc. Voi {@code AFTER_COMMIT} thi luc listener chay, du lieu moi <b>da nhin thay duoc</b>
 * boi moi ket noi khac.
 *
 * <p>
 * <b>Vi sao khong xoa cache ngay trong service.</b> Luc do transaction chua commit. Mot request
 * khac doc vi trong khoang giua se: khong thay cache -> doc database -> doc duoc <b>gia tri
 * CU</b> -> nap cache bang gia tri cu. Roi transaction commit. Database dung, cache sai, va no
 * sai cho toi khi het TTL.
 *
 * <p>
 * <b>Vi sao AFTER_COMMIT chu khong AFTER_COMPLETION.</b> {@code AFTER_COMPLETION} chay ca khi
 * <b>rollback</b>. Nghe co ve an toan hon - "xoa thua con hon xoa thieu" - nhung no bien moi
 * lan chuyen tien that bai thanh mot lan xoa cache vo ich. Ma chuyen tien that bai la chuyen
 * xay ra thuong xuyen (khong du so du, sai vi). Rollback thi <b>database khong doi gi</b>, nen
 * cache cung khong sai gi.
 *
 * <p>
 * ⚠️ Bay: {@code @TransactionalEventListener} <b>lang le khong lam gi</b> neu su kien duoc phat
 * ra ngoai transaction - khong loi, khong canh bao. Day lai la ho loi quen thuoc cua project:
 * mot thu khai bao dung nhung thieu cai kich hoat no. Cho nay an toan vi ca hai cho phat su
 * kien ({@code deposit}, {@code transfer}) deu nam trong method {@code @Transactional}.
 */
@Component
public class WalletCacheEvictor {

    private final WalletCache cache;

    public WalletCacheEvictor(WalletCache cache) {
        this.cache = cache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWalletChanged(WalletChangedEvent event) {
        cache.evict(event.walletId());
    }
}
