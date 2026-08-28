package com.vidien.ewallet.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.audit.AuditEvent;
import com.vidien.ewallet.audit.Auditor;
import com.vidien.ewallet.config.SecurityConfig;
import com.vidien.ewallet.user.User;
import com.vidien.ewallet.user.UserRepository;
import com.vidien.ewallet.wallet.WalletRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;
    private final Auditor audit;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users, WalletRepository wallets,
            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl:PT15M}") Duration tokenTtl, Auditor audit,
            RefreshTokenService refreshTokens) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
        this.audit = audit;
        this.refreshTokens = refreshTokens;
    }

    /**
     * Dang ky = tao NGUOI DUNG va tao VI, hai lenh ghi, mot transaction.
     *
     * <p>
     * Neu tao user xong ma tao vi hong thi he thong co mot nguoi dang nhap duoc nhung khong
     * co vi - moi endpoint tien bac deu 404 va khong ai hieu vi sao. @Transactional lam ca
     * hai cung song hoac cung chet, dung nhu bai nap tien.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Kiem truoc de tra loi tu te. Nhung KHONG dua vao mot minh no: giua luc kiem va luc
        // ghi van co khe ho cho hai request dang ky cung email cung luc. Bit khe ho do la
        // unique index ux_users_email_lower o V1 - no la thu quyet dinh cuoi cung.
        if (users.findByEmail(request.email()).isPresent()) {
            throw new EmailAlreadyUsedException(request.email());
        }

        String hash = passwordEncoder.encode(request.password());
        long userId = users.insert(request.email(), hash, request.fullName());
        long walletId = wallets.insertForUser(userId);

        audit.record(AuditEvent.REGISTERED, userId,
                Map.of("email", request.email(), "walletId", walletId));

        return issueToken(userId, request.email(), request.fullName(), walletId);
    }

    /**
     * ⚠️ KHONG con readOnly = true. Truoc PR nay dang nhap chi DOC (tim user, tim vi) nen
     * readOnly la dung. Gio no phat mot refresh token, tuc la GHI mot dong vao database.
     *
     * <p>
     * Da do khi quen doi: {@code PSQLException: cannot execute INSERT in a read-only transaction}.
     *
     * <p>
     * 📌 Va cho dang hoc nam o day: {@code RefreshTokenService.issueNewFamily} CO
     * {@code @Transactional} rieng, khong readOnly - nhung no <b>khong cuu duoc</b>. Propagation
     * mac dinh la REQUIRED, nghia la "co transaction roi thi GIA NHAP", va transaction dang co
     * la read-only. Co readOnly la thuoc tinh cua transaction, khong phai cua method - method
     * ben trong khong the go no ra.
     *
     * <p>
     * Muon go that thi phai REQUIRES_NEW, tuc la mo han mot transaction khac - va luc do dong
     * refresh token se commit doc lap voi phan con lai, tuc la mat tinh "tat ca hoac khong gi
     * ca". Sua o dung cho: bo readOnly khoi method nay, vi no da that su ghi.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = users.findByEmail(request.email());

        if (found.isEmpty()) {
            // Ghi nhat ky RIENG cho "email khong ton tai" va "sai mat khau" - nhung CHI trong
            // nhat ky. Response ra ngoai van la MOT thong bao duy nhat (401 INVALID_CREDENTIALS),
            // vi tach ra la tang ke tan cong cong cu do xem email nao co that.
            //
            // Nhat ky thi nguoc lai: no danh cho NGUOI VAN HANH, va nguoi van hanh can phan
            // biet. 50 lan "khong ton tai" tu mot IP la quet danh sach email; 50 lan "sai mat
            // khau" tren MOT email la do mat khau. Hai chuyen khac han nhau.
            audit.record(AuditEvent.LOGIN_FAILED, null,
                    Map.of("email", request.email(), "reason", "NO_SUCH_EMAIL"));
            throw new InvalidCredentialsException();
        }

        User user = found.get();

        // matches() doc salt tu chinh cai hash roi bam lai mat khau vua nhap de so.
        // KHONG BAO GIO dung equals() - bam cung mot mat khau hai lan ra hai chuoi khac nhau.
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            // KHONG BAO GIO ghi mat khau vua nhap vao nhat ky, ke ca dang bam. Nhat ky la
            // thu duoc doc nhieu nhat va bao ve it nhat trong ca he thong.
            audit.record(AuditEvent.LOGIN_FAILED, user.id(),
                    Map.of("email", request.email(), "reason", "WRONG_PASSWORD"));
            throw new InvalidCredentialsException();
        }

        long walletId = wallets.findByUserId(user.id())
                .orElseThrow(InvalidCredentialsException::new);

        return issueToken(user.id(), user.email(), user.fullName(), walletId);
    }

    /**
     * Doi refresh token lay mot cap token moi.
     *
     * <p>
     * Khong doi mat khau, khong doi access token cu. Nguoi goi chi can chung minh dang cam
     * mot refresh token con dung duoc - va no bi <b>xoay vong</b>: cai vua dung chet ngay,
     * client nhan cai moi. Chi tiet va ly do o RefreshTokenService.rotate().
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);

        User user = users.findById(rotated.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        long walletId = wallets.findByUserId(user.id())
                .orElseThrow(InvalidRefreshTokenException::new);

        // fullName de null: client da co roi tu luc dang nhap. @JsonInclude(NON_NULL) tren
        // AuthResponse loai no khoi JSON.
        return buildResponse(user.id(), user.email(), null, walletId, rotated.refreshToken());
    }

    /** Dang xuat THAT SU: thu hoi moi refresh token. Access token cu song not TTL 15 phut. */
    @Transactional
    public int logout(long userId) {
        return refreshTokens.revokeAllForUser(userId);
    }

    private AuthResponse issueToken(long userId, String email, String fullName, long walletId) {
        return buildResponse(userId, email, fullName, walletId,
                refreshTokens.issueNewFamily(userId));
    }

    /**
     * Access token KHONG duoc ma hoa, chi duoc KY.
     *
     * <p>
     * Bat cu ai cam token deu doc duoc phan claims - dan vao jwt.io la thay. Chu ky chi bao
     * dam khong ai SUA duoc noi dung, khong bao dam noi dung bi mat. Nen trong nay chi de
     * nhung thu khong ngai lo: id, email, ten. Khong bao gio de mat khau hay so du.
     */
    private AuthResponse buildResponse(long userId, String email, String fullName, long walletId,
            String refreshToken) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                // iss va aud lay tu HANG SO trong SecurityConfig, khong go tay chuoi o hai
                // noi. Go tay thi mot ngay nao do sua mot dau roi quen dau kia, va trieu
                // chung se la "moi nguoi dung bong dung bi 401" - dung luc deploy.
                .issuer(SecurityConfig.ISSUER)
                // aud = "token nay duoc phat cho DICH VU nao". Hom nay chi co mot dich vu nen
                // no bang chinh iss. Ngay tach service ra, day la thu ngan token cua dich vu
                // nay dung duoc o dich vu kia.
                .audience(List.of(SecurityConfig.ISSUER))
                .issuedAt(now)
                .expiresAt(now.plus(tokenTtl))
                // subject la "token nay noi ve AI". Dung id chu khong dung email: email co
                // the doi, id thi khong.
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("walletId", walletId)
                .build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims))
                .getTokenValue();

        return new AuthResponse(token, refreshToken, tokenTtl.toSeconds(), walletId, fullName);
    }
}
