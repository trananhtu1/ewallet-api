package com.vidien.ewallet.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nen cho moi integration test: mot Postgres THAT chay trong Docker.
 *
 * <p>
 * ⚠️ <b>KHONG dung H2</b>, va day khong phai so thich. H2 khong co
 * {@code FOR NO KEY UPDATE}, khong co {@code JSONB}, khong cho {@code LIMIT} trong nhanh con
 * cua {@code UNION ALL}, va khong tai hien duoc deadlock. Tuc la no khong chay duoc dung
 * nhung thu can canh giu nhat. <b>Test tren mot database khac production la test mot he thong
 * khac.</b>
 *
 * <p>
 * {@code disabledWithoutDocker = true}: may Mac dang dung de phat trien KHONG co Docker, nen
 * cac test nay se BO QUA o do thay vi lam do ca lan build. Chung chay that tren may Windows va
 * tren CI. Ghi ro o day de nguoi doc khong tuong chung da chay va da xanh.
 *
 * <p>
 * {@code @ServiceConnection}: Spring Boot tu lay URL/user/pass tu container, khong phai tu
 * {@code @DynamicPropertySource} viet tay ba dong nhu truoc kia.
 *
 * <p>
 * Container la {@code static}: MOT Postgres dung chung cho ca lop con, khong dung mot cai moi
 * cho tung method. Khoi dong Postgres mat vai giay - nhan len so luong test thi do la phut.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIT {

    @org.testcontainers.junit.jupiter.Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    /**
     * Flyway chay tren container nay, khong phai tren Neon. Nghia la <b>ca cac file migration
     * cung duoc kiem</b>: viet mot cau SQL sai o V6 thi test do, truoc khi no cham production.
     *
     * <p>
     * JWT_SECRET la bat buoc luc khoi dong (co y - xem application.properties), nen phai cap
     * mot gia tri o day. 32 ky tu vi HS256 doi it nhat the.
     */
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "khoa-test-32-ky-tu-toi-thieu-cho-HS256");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");
    }
}
