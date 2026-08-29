/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Lên lịch chạy. Chỉ có mỗi việc gọi service đúng giờ.
 * LIÊN QUAN: ReconciliationService · SchedulingConfig (@EnableScheduling) · application.properties
 */
package com.vidien.ewallet.reconciliation.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chay doi soat moi ngay.
 *
 * <p>
 * File nay co dung MOT viec: goi service dung gio. Moi logic nam trong
 * {@link ReconciliationService} - va do la co y, vi mot ham co
 * {@code @Scheduled} thi <b>khong ai goi duoc tu test</b> theo cach tu nhien. Tach ra thi
 * {@code ReconciliationServiceIT} goi thang {@code chay()} duoc, khong phai doi toi gio.
 *
 * <p>
 * ⚠️ <b>{@code @Scheduled} khong tu chay neu thieu {@code @EnableScheduling}.</b> Khong loi,
 * khong canh bao, job dung im mai mai. Xem {@code SchedulingConfig} - va do la <b>lan thu TAM</b>
 * trong project ho loi "khai bao mot thu ma thieu cai kich hoat no".
 *
 * <p>
 * 📌 {@code fixedDelay} chu khong {@code fixedRate}: {@code fixedRate} dem tu luc BAT DAU lan
 * truoc, nen mot lan chay lau hon chu ky se lam lan sau chay chong len. Doi soat quet ca hai
 * bang; tren du lieu lon no se lau. {@code fixedDelay} dem tu luc KET THUC - khong bao gio
 * chong.
 */
@Component
@ConditionalOnProperty(name = "app.reconciliation.enabled", havingValue = "true",
        matchIfMissing = true)
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliation;

    public ReconciliationScheduler(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /**
     * Mac dinh 24 gio mot lan, doc tu {@code app.reconciliation.delay}.
     *
     * <p>
     * ⚠️ Bat {@code Exception} o day, va day la mot trong rat it cho bat rong la dung: neu de
     * ngoai le thoat ra khoi mot ham {@code @Scheduled} thi Spring <b>khong chay lan sau nua</b>
     * cho toi khi restart. Mot loi mang mot lan se lam job im lang chet han - va no im lang
     * dung cai kieu ma ca ngay hom nay da gap bay lan.
     */
    @Scheduled(fixedDelayString = "${app.reconciliation.delay:PT24H}",
            initialDelayString = "${app.reconciliation.initial-delay:PT1M}")
    public void chayTheoLich() {
        try {
            reconciliation.chay();
        } catch (Exception e) {
            log.error("Job doi soat that bai - lan sau van se chay", e);
        }
    }
}
