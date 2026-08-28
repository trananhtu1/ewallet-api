package com.vidien.ewallet.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Chi can spring-boot-starter-security nam tren classpath la Spring KHOA HET moi endpoint va
 * in ra mot mat khau ngau nhien luc khoi dong. File nay ton tai de gianh lai quyen quyet dinh.
 */
@Configuration
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /**
     * BCrypt tu sinh SALT ngau nhien va nhet luon vao trong chuoi ket qua.
     *
     * <p>
     * He qua de quen: bam CUNG mot mat khau hai lan ra HAI chuoi khac nhau. Nen khong bao gio
     * duoc so sanh bang equals() - phai dung passwordEncoder.matches(raw, hash), no doc salt
     * tu chinh cai hash ra roi bam lai.
     *
     * <p>
     * strength 10 la mac dinh. Moi don vi tang gap DOI thoi gian bam. Tang len 12 thi an toan
     * hon nhung moi lan dang nhap ton them CPU - va tren Render goi free thi dieu do dem duoc.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Khoa ky token. HS256 doi khoa it nhat 256 bit = 32 KY TU. Ngan hon la Nimbus nem loi
     * ngay luc khoi dong - to hon nhieu so voi mot he thong chay tiep voi khoa yeu.
     *
     * <p>
     * KHONG co gia tri mac dinh, cung ly do voi spring.datasource.*: quen dat bien tren Render
     * thi app chet ngay luc deploy voi thong bao ro rang, thay vi song sot roi phat token ky
     * bang mot chuoi ai cung doan duoc.
     */
    private SecretKey signingKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(signingKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF sinh ra de chong tan cong dua tren COOKIE tu dong gui kem. API nay
                // khong dung cookie, token nam trong header Authorization va trinh duyet
                // khong tu gan header do - nen CSRF khong ap dung. Tat co can nhac, khong
                // phai tat cho het loi.
                .csrf(csrf -> csrf.disable())

                // Khong tao session. Moi request tu mang theo token cua no. Day la dieu kien
                // de sau nay chay nhieu instance ma khong can chia se session.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CORS: dung lai cau hinh CorsConfig da co, khong khai bao lai lan hai.
                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                        .permitAll()
                        .requestMatchers("/ping", "/health").permitAll()

                        // Da khoa (28/08). Truoc do co y mo de khong chan phien lam FE, va
                        // FE gio da co man dang nhap nen mon no do dong duoc.
                        //
                        // ⚠️ NHUNG dong nay MOT MINH NO KHONG PHAI LA BAO MAT. No chi tra loi
                        // "anh la ai", khong tra loi "anh duoc dung vao cai gi". Chi khoa den
                        // day thi bat ky ai DANG KY XONG deu goi duoc:
                        //     GET  /api/wallets/1          -> xem so du nguoi khac
                        //     POST /api/wallets/1/deposits -> ghi vao vi nguoi khac
                        //     POST /api/transfers {"fromWalletId": 1} -> rut tien nguoi khac
                        // Dang ky mat 5 giay. Lo do ten la BOLA/IDOR, hang 1 OWASP API Top 10.
                        //
                        // Cho bit lo that su nam o tang service: xem WalletService.requireOwn.
                        .requestMatchers("/api/wallets/**", "/api/transfers/**").authenticated()

                        .anyRequest().authenticated())

                // Bat cho app doc token JWT o header Authorization: Bearer ...
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        // Token hong -> 401 co dung hinh dang ErrorResponse nhu moi loi khac.
                        .authenticationEntryPoint(authenticationEntryPoint))

                // Va ca truong hop khong he gui token vao endpoint doi xac thuc.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }
}
