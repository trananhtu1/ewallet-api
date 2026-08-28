package com.vidien.ewallet.auth.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.vidien.ewallet.auth.api.dto.AuthResponse;
import com.vidien.ewallet.auth.domain.AuthService;
import com.vidien.ewallet.auth.domain.Caller;
import com.vidien.ewallet.auth.api.dto.LoginRequest;
import com.vidien.ewallet.auth.api.dto.RefreshRequest;
import com.vidien.ewallet.auth.api.dto.RegisterRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 201 CREATED chu khong phai 200: mot nguoi dung va mot vi vua duoc TAO RA. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Doi refresh token lay mot cap token moi.
     *
     * <p>
     * KHONG doi xac thuc - day la endpoint <b>permitAll</b>, cung nhom voi /login. Ly do:
     * client goi vao day <b>dung luc access token da het han</b>, nen doi token hop le o day
     * la doi mot thu vua chet. Ban than refresh token la giay thong hanh.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /**
     * Dang xuat THAT SU - thu hoi moi refresh token cua nguoi nay.
     *
     * <p>
     * Truoc PR nay, "dang xuat" chi la frontend vut token di. Ai da sao chep token ra thi van
     * dung tiep duoc het han. Gio thi refresh token chet han, nen ke do cung chi con dung
     * duoc toi khi access token het han - <b>toi da 15 phut</b>.
     *
     * <p>
     * Endpoint nay <b>doi</b> access token: phai biet dang thu hoi cua AI. 204 vi khong co gi
     * de tra ve.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(Caller.userId(jwt));
    }
}
