/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Biên HTTP — chủ sổ địa chỉ lấy từ TOKEN, không từ tham số.
 * LIÊN QUAN: BeneficiaryService · BeneficiaryResponse
 */
package com.vidien.ewallet.beneficiary.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.beneficiary.api.dto.BeneficiaryResponse;
import com.vidien.ewallet.beneficiary.api.dto.SaveBeneficiaryRequest;
import com.vidien.ewallet.beneficiary.domain.BeneficiaryService;
import jakarta.validation.Valid;

/**
 * So dia chi nguoi nhan.
 *
 * <p>
 * ⚠️ Khong endpoint nao nhan {@code ownerId}. Chu so lay tu <b>token</b>, va do
 * la thu duy nhat chan mot nguoi doc so dia chi cua nguoi khac. Nhan tu URL hay
 * tu body deu la mo lai lo BOLA da bit o PR #2.
 */
@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;

    public BeneficiaryController(BeneficiaryService service) {
        this.service = service;
    }

    @GetMapping
    public List<BeneficiaryResponse> cuaToi(@AuthenticationPrincipal Jwt jwt) {
        return service.cuaToi(userId(jwt)).stream().map(BeneficiaryResponse::of).toList();
    }

    /** 201: mot tai nguyen moi vua duoc tao, va lan goi thu hai se 409. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse luu(@Valid @RequestBody SaveBeneficiaryRequest req,
            @AuthenticationPrincipal Jwt jwt) {

        return BeneficiaryResponse.of(
                service.luu(userId(jwt), walletId(jwt), req.walletId(), req.label()));
    }

    /** 204: xoa xong thi khong con gi de tra ve. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void xoa(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        service.xoa(userId(jwt), id);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static long walletId(Jwt jwt) {
        return jwt.getClaim("walletId");
    }
}
