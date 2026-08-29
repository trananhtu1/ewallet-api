package com.vidien.ewallet.wallet.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.vidien.ewallet.auth.domain.Caller;
import com.vidien.ewallet.transaction.api.dto.TransactionPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import com.vidien.ewallet.wallet.api.dto.DepositRequest;
import com.vidien.ewallet.shared.dto.ErrorResponse;
import com.vidien.ewallet.shared.exception.GlobalExceptionHandler;
import com.vidien.ewallet.wallet.api.dto.WalletResponse;
import com.vidien.ewallet.wallet.domain.exception.WalletNotFoundException;
import com.vidien.ewallet.wallet.domain.WalletService;

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

    /**
     * ⭐ Mapper la thu ngan entity ra khoi hop dong API.
     *
     * <p>
     * Truoc dot nay, {@code Wallet} vua la dong trong bang vua la JSON tra ve - doi mot cot la
     * doi luon hop dong voi frontend. Gio controller <b>khong bao gio</b> tra entity: moi
     * duong ra deu di qua {@code toResponse()}.
     */
    private final WalletMapper walletMapper;

    public WalletController(WalletService walletService, WalletMapper walletMapper) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
    }

    /**
     * GET /me - vi cua chinh nguoi dang goi. KHONG co id nao tren duong dan.
     *
     * <p>
     * Day la cach chong BOLA manh nhat, va no khong phai la "kiem quyen ky hon": no lam cho
     * cau hoi sai KHONG DIEN DAT DUOC. Khong co cho de go id thi khong co gi de doan.
     * /{id} ben duoi van giu vi frontend dang goi no, nhung /me la duong nen dung.
     *
     * <p>
     * Khai bao TRUOC /{id} cho de doc. Ky thuat ma noi thi Spring da uu tien duong dan CO DINH
     * hon duong dan co bien nen khong vo, nhung thu tu dung la thu khong phai nho.
     */
    @GetMapping("/me")
    public WalletResponse findMine(@AuthenticationPrincipal Jwt jwt) {
        long walletId = Caller.walletId(jwt);
        return walletMapper.toResponse(walletService.findById(walletId, walletId));
    }

    @GetMapping("/{id}")
    public WalletResponse findOne(@PathVariable @Positive(message = "Ma vi phai la so duong") long id,
            @AuthenticationPrincipal Jwt jwt) {
        return walletMapper.toResponse(walletService.findById(id, Caller.walletId(jwt)));
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
    public WalletResponse deposit(@PathVariable @Positive(message = "Ma vi phai la so duong") long id,
            @Valid @RequestBody DepositRequest request, @AuthenticationPrincipal Jwt jwt) {
        return walletMapper.toResponse(
                walletService.deposit(id, request.amount(), Caller.walletId(jwt)));
    }

    /**
     * GET /{id}/transactions?limit=20&cursor=...
     *
     * <p>
     * limit co MAC DINH va co TRAN. Khong co tran thi mot nguoi go limit=1000000 la keo het
     * bang ve, giet ca server lan trinh duyet. Math.min la mot dong, va no la dong duy nhat
     * dung giua API cong khai va mot cau query khong gioi han.
     *
     * <p>
     * <b>cursor thay cho page/offset, va day khong phai so thich.</b> Do tren 200.000 dong:
     * {@code OFFSET 100000} mat 35.051ms va bat Postgres doc 100.020 dong de tra ve 20; cursor
     * mat 0.221ms va doc 21 dong. Nhung ly do CHINH khong phai toc do:
     *
     * <p>
     * OFFSET dem theo VI TRI trong ket qua, ma vi tri thi thay doi khi co dong moi chen vao
     * dau. Do duoc: doc trang 1 ra {@code id 1,2,3,4,5}, co 3 giao dich moi ghi vao, doc tiep
     * {@code OFFSET 5} thi ra {@code id 3,4,5,6,7} - ba dong nguoi dung VUA XEM hien ra lan
     * hai. Neu co dong bi xoa thi nguoc lai: dong bi NHAY QUA, khong bao gio thay.
     *
     * <p>
     * Cursor dem theo GIA TRI ({@code created_at, id}) chu khong theo vi tri, nen chen bao
     * nhieu dong vao dau cung khong xe dich no. Cung kich ban do, trang 2 ra
     * {@code id 6,7,8,9,10}.
     *
     * <p>
     * Voi mot bang tien thi "thinh thoang hien lai mot giao dich" khong phai loi giao dien -
     * no lam nguoi dung tuong minh bi tru tien hai lan.
     */
    @GetMapping("/{id}/transactions")
    public TransactionPage history(
            @PathVariable @Positive(message = "Ma vi phai la so duong") long id,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt) {
        return walletService.history(id, Math.min(Math.max(limit, 1), 100), cursor,
                Caller.walletId(jwt));
    }
}
