package com.vidien.ewallet.kyc.api;

import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.vidien.ewallet.kyc.api.dto.KycView;
import com.vidien.ewallet.kyc.domain.DocumentImage;
import com.vidien.ewallet.kyc.domain.KycService;
import com.vidien.ewallet.kyc.domain.exception.InvalidDocumentException;

/**
 * KYC - nop anh CCCD (gia lap).
 *
 * <p>
 * ⚠️ Nguoi dung lay tu <b>token</b>, khong bao gio tu tham so. Nhan {@code userId} tu body la
 * dung lai lo BOLA da bit o PR #2 duoi mot cai ten khac: bat ky ai dang ky xong cung nop duoc
 * ho so <b>vao ten nguoi khac</b>.
 */
@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /**
     * POST /api/kyc - multipart, hai file: {@code front} va {@code back}.
     *
     * <p>
     * 201 chu khong 200: mot tai nguyen moi vua duoc tao ra, va lan goi thu hai se KHONG tao
     * them (409). Ma tra ve phai noi dung chuyen do.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KycView nop(@RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back,
            @AuthenticationPrincipal Jwt jwt) {

        return kycService.nop(userId(jwt), doc(front, "front"), doc(back, "back"));
    }

    /** GET /api/kyc/me - lich su nop cua chinh minh. */
    @GetMapping("/me")
    public List<KycView> cuaToi(@AuthenticationPrincipal Jwt jwt) {
        return kycService.cuaToi(userId(jwt));
    }

    /**
     * Doc byte va giao cho {@link DocumentImage} kiem.
     *
     * <p>
     * ⚠️ {@code file.getContentType()} KHONG duoc dung o dau ca. No la thu trinh duyet khai,
     * va mot request tu tay lam noi gi cung duoc. Kieu that duoc doc tu byte dau file.
     */
    private static DocumentImage doc(MultipartFile file, String vaiTro) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Thieu anh " + vaiTro);
        }
        try {
            return DocumentImage.kiem(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            // Doc khong duoc thi la loi phia server hoac ket noi dut giua chung - khong phai
            // loi cua file. Khong nuot thanh InvalidDocument, vi lam vay la bao nguoi dung
            // chup lai anh cho mot chuyen khong lien quan gi toi anh.
            throw new IllegalStateException("Khong doc duoc anh " + vaiTro, e);
        }
    }

    /** {@code sub} cua token la userId - dat luc phat token trong AuthService. */
    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
