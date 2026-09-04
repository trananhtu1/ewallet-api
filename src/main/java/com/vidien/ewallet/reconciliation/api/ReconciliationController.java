/*
 * FEATURE  : Đối soát cuối ngày
 * VAI TRÒ  : Biên HTTP — cho xem kết quả đối soát gần nhất.
 * LIÊN QUAN: ReconciliationRunRepository · ReconciliationView
 */
package com.vidien.ewallet.reconciliation.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.reconciliation.infra.ReconciliationRunRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Doi soat cuoi ngay - <b>chi doc</b>.
 *
 * <p>
 * ⚠️ KHONG co endpoint kich hoat chay doi soat. Co y: mot job quet hai bang va
 * cong tong toan he thong, ma bat ky ai dang nhap cung goi duoc thi do la mot
 * duong lam nghen database rat re tien. Job chay theo lich, va lich la thu duy
 * nhat quyet dinh khi nao no chay.
 *
 * <p>
 * Vi sao mot nguoi dung thuong duoc xem: no cho thay he thong <b>tu kiem tra
 * chinh minh</b>, va do la thu mot he thong tien nen noi ra. Cac so o day la
 * TONG toan he thong nen khong lo thong tin cua ai ca - chi tiet tung vi thi
 * nam trong cot {@code details} va da bi giu lai o {@link ReconciliationView}.
 */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationRunRepository runs;

    public ReconciliationController(ReconciliationRunRepository runs) {
        this.runs = runs;
    }

    /**
     * GET /api/reconciliation - vai lan chay gan nhat, moi truoc.
     *
     * <p>
     * {@code limit} co tran cung 30: khong dat tran thi mot ai do go
     * {@code ?limit=999999} la keo ca bang len RAM. Day la cung mot luat da ap
     * cho lich su giao dich.
     *
     * <p>
     * ⚠️ KHONG dat {@code @Validated} tren lop nay, du do la cach quen thuoc.
     * {@code @Validated} bat kiem qua mot proxy AOP va nem
     * {@code ConstraintViolationException} - mot ngoai le <b>khong ai bat</b>, nen
     * no roi vao luoi chan {@code @ExceptionHandler(Exception.class)} va tra ve
     * <b>500</b> cho mot request chi don gian la sai tham so. Do duoc: 999 -> 500.
     *
     * <p>
     * Tu Spring 6.1, rang buoc dat thang tren tham so cua controller duoc kiem
     * <b>san</b>, khong can annotation nao, va no nem
     * {@code HandlerMethodValidationException} - loai da co handler tra 400.
     * Bo mot dong di thi ma loi tro ve dung.
     *
     * <p>
     * 📌 Cung hinh dang voi su co {@code @Positive} hoi 24/08: khong phai thieu
     * kiem, ma la kiem xong roi ma bao sai ma.
     */
    @GetMapping
    public List<ReconciliationView> ganDay(
            @RequestParam(defaultValue = "7") @Positive @Max(30) int limit) {

        return runs.findRecent(limit).stream().map(ReconciliationView::of).toList();
    }
}
