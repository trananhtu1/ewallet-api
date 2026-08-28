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

    public AuthService(UserRepository users, WalletRepository wallets,
            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl:PT2H}") Duration tokenTtl, Auditor audit) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
        this.audit = audit;
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

    @Transactional(readOnly = true)
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
     * Token KHONG duoc ma hoa, chi duoc KY.
     *
     * <p>
     * Bat cu ai cam token deu doc duoc phan claims - dan vao jwt.io la thay. Chu ky chi bao
     * dam khong ai SUA duoc noi dung, khong bao dam noi dung bi mat. Nen trong nay chi de
     * nhung thu khong ngai lo: id, email, ten. Khong bao gio de mat khau hay so du.
     */
    private AuthResponse issueToken(long userId, String email, String fullName, long walletId) {
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

        return new AuthResponse(token, tokenTtl.toSeconds(), walletId, fullName);
    }
}
