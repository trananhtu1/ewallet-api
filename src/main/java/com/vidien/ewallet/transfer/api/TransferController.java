package com.vidien.ewallet.transfer.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.vidien.ewallet.auth.domain.Caller;
import org.springframework.web.bind.annotation.RequestHeader;
import com.vidien.ewallet.wallet.api.WalletMapper;
import com.vidien.ewallet.wallet.api.dto.WalletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.vidien.ewallet.transfer.api.dto.TransferRequest;
import com.vidien.ewallet.transfer.domain.TransferService;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final WalletMapper walletMapper;

    public TransferController(TransferService transferService, WalletMapper walletMapper) {
        this.transferService = transferService;
        this.walletMapper = walletMapper;
    }

    /**
     * Tra ve vi NGUON sau khi chuyen - thu nguoi goi muon biet nhat la "toi con bao nhieu".
     *
     * <p>
     * @Valid = cau lenh "doc so di". Thieu chu nay thi moi annotation trong TransferRequest im
     * lang khong chay, va amount = -999999 di thang xuong duoi.
     *
     * <p>
     * @RequestBody = "doc tu than request". Thieu chu nay thi Spring di tim du lieu o query
     * string, va moi field se la null.
     */
    /**
     * Khoa chong lap di trong HEADER chu khong nam trong body.
     *
     * <p>
     * Ly do: no khong phai du lieu nghiep vu. Body mo ta "chuyen bao nhieu cho ai" - hai lan
     * bam nut thi body GIONG HET NHAU, va do chinh la van de. Khoa nay mo ta "day co phai lan
     * gui lai cua dung lenh do khong", tuc la thong tin ve BAN THAN REQUEST. Stripe, Adyen va
     * hau het cong thanh toan deu dat no o header vi ly do nay.
     *
     * <p>
     * required = false: khong gui thi van chuyen tien binh thuong. Bat buoc thi mot client cu
     * chua kip cap nhat se gay 400 cho moi lenh chuyen tien - qua dat cho mot thu ma khong co
     * no van chay dung, chi la khong chong duoc lap.
     *
     * <p>
     * @Size(max = 64) khop voi cot VARCHAR(64) o V1. Thieu no thi mot khoa 10.000 ky tu se di
     * xuong toi tan driver Postgres roi mo'i vo, va vo bang mot exception kho doc.
     */
    @PostMapping
    public WalletResponse create(@Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key",
                    required = false) @Size(max = 64,
                            message = "Idempotency-Key toi da 64 ky tu") String idempotencyKey) {

        return walletMapper.toResponse(transferService.transfer(request.fromWalletId(), request.toWalletId(),
                request.amount(), Caller.walletId(jwt), idempotencyKey));
    }
}
