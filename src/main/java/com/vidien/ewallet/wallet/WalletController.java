package com.vidien.ewallet.wallet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import com.vidien.ewallet.transaction.TransactionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Chi noi chuyen voi WalletService, khong cam thang repository.
 *
 * <p>
 * Truoc day method findOne tu kiem Optional roi tu dung ErrorResponse 404. Bo di vi hai ly do:
 * hai duong vao cung mot bang thi som muon co mot luat nghiep vu chi ap cho mot duong, va viec
 * dich loi sang HTTP da co GlobalExceptionHandler lam roi. Khong tim thay vi thi nem
 * WalletNotFoundException, phan con lai khong phai viec cua controller.
 *
 * <p>
 * @Positive tren {id}: id am hay id 0 KHONG PHAI 404. 404 co nghia "thu nay co the ton tai
 * nhung hien khong co" - sua du lieu trong DB thi cau tra loi doi. Con id = -1 thi khong bao
 * gio ton tai duoc, vi BIGSERIAL bat dau tu 1 va chi tang. Do la cau hoi SAI HINH DANG, cung
 * ho voi /api/wallets/abc, nen phai 400. De 404 thi frontend tuong "user chua co vi" va di
 * tim nham cho, con moi phat -1 van ton mot vong xuong Neon de tim thu khong the co.
 *
 * <p>
 * KHONG can @Validated tren class. Tu Spring Framework 6.1 (Boot 3.2+), Spring MVC tu kiem
 * rang buoc tren tham so controller, khong qua proxy AOP - da do that: bo @Validated van chay.
 * Nguoc lai, them @Validated se DOI duong: Spring chuyen sang AOP cu va nem
 * ConstraintViolationException thay vi HandlerMethodValidationException, tuc la doi luon ca
 * handler phai viet ben GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/{id}")
    public Wallet findOne(@PathVariable @Positive(message = "Ma vi phai la so duong") long id) {
        return walletService.findById(id);
    }

    /**
     * POST /{id}/deposits - duong dan la DANH TU SO NHIEU, khong phai dong tu ("/deposit" hay
     * "/nap-tien"). Moi lan goi la tao them mot ban ghi nap tien trong bo suu tap do.
     *
     * <p>
     * @Valid la LENH doc cac annotation tren DepositRequest. Thieu no thi cac luat trong DTO
     * nam im, khong ai kiem, va so tien 0.001 di thang xuong database.
     */
    @PostMapping("/{id}/deposits")
    public Wallet deposit(@PathVariable @Positive(message = "Ma vi phai la so duong") long id,
            @Valid @RequestBody DepositRequest request) {
        return walletService.deposit(id, request.amount());
    }

    /**
     * GET /{id}/transactions?limit=20
     *
     * <p>
     * limit co MAC DINH va co TRAN. Khong co tran thi mot nguoi go limit=1000000 la keo het
     * bang ve, giet ca server lan trinh duyet. Math.min la mot dong, va no la dong duy nhat
     * dung giua API cong khai va mot cau query khong gioi han.
     */
    @GetMapping("/{id}/transactions")
    public List<TransactionView> history(
            @PathVariable @Positive(message = "Ma vi phai la so duong") long id,
            @RequestParam(defaultValue = "20") int limit) {
        return walletService.history(id, Math.min(Math.max(limit, 1), 100));
    }
}
