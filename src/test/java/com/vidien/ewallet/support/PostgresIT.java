/*
 * FEATURE  : Nền cho mọi integration test
 * VAI TRÒ  : Singleton Postgres + Redis thật. Và một chỗ duy nhất để dọn dữ liệu.
 * LIÊN QUAN: DockerRequiredIT · mọi *IT
 */
package com.vidien.ewallet.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
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
 * <b>SINGLETON container, KHONG dung {@code @Container}</b> - va day la mot bai hoc phai tra
 * gia moi biet, ngay 29/08/2026, lan dau tien cac test nay duoc chay that.
 *
 * <p>
 * {@code @Container} giao vong doi container cho <b>tung lop con</b>: JUnit khoi dong truoc
 * lop do va <b>DUNG no lai sau khi lop do xong</b>. Nhung Spring thi <b>CACHE application
 * context</b> - hai lop con co cung cau hinh dung chung mot context, tuc la dung chung mot
 * Hikari pool. Ket qua: lop thu nhat chay xanh, container chet theo no, lop thu hai lay dung
 * cai pool cu con tro vao cong cua container da chet:
 *
 * <pre>
 * Connection to localhost:56700 refused
 * HikariPool-1 - Connection is not available, request timed out after 10012ms
 * </pre>
 *
 * <p>
 * Cach dung: tu goi {@code start()} trong static block va <b>khong bao gio stop</b>. Container
 * song suot ca lan chay JVM, moi lop con dung chung. Khong ro ri gi ca - Ryuk (container phu
 * ma Testcontainers tu dung len) don dep khi JVM tat.
 *
 * <p>
 * {@code @Testcontainers} van giu lai, vi {@code disabledWithoutDocker} nam o extension chu
 * khong nam o {@code @Container}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    /**
     * Redis THAT, cung ly do voi Postgres that: cache trong RAM
     * ({@code ConcurrentMapCacheManager}) khong tra loi duoc cau hoi can hoi nhat.
     *
     * <p>
     * Cache RAM khong tuan tu hoa gi ca - no giu nguyen tham chieu Java. Nghia la moi loi
     * serialize deu <b>tang hinh</b>: kieu {@code Instant} ghi sai dinh dang, thieu thong tin
     * kieu de dung lai object, {@code BigDecimal} bien thanh {@code double} - khong loi nao lo
     * ra cho toi khi len production noi cai cache la Redis that.
     *
     * <p>
     * Va no cung khong co TTL that, khong co chuyen hai instance dung chung mot cho nho.
     * <b>Test tren mot ha tang khac production la test mot he thong khac</b> - cung mot cau
     * da viet cho H2 o tren.
     */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Flyway chay tren container nay, khong phai tren Neon. Nghia la <b>ca cac file migration
     * cung duoc kiem</b>: viet mot cau SQL sai o V6 thi test do, truoc khi no cham production.
     *
     * <p>
     * JWT_SECRET la bat buoc luc khoi dong (co y - xem application.properties), nen phai cap
     * mot gia tri o day. 32 ky tu vi HS256 doi it nhat the.
     */
    @Autowired
    protected JdbcClient db;

    /**
     * Xoa sach du lieu nghiep vu, dung THU TU khoa ngoai cho phep.
     *
     * <p>
     * ⚠️ <b>Ham nay o day chu khong copy vao tung file test, va do la mot bai hoc phai tra gia
     * moi rut ra.</b> Truoc do bon file IT moi file mot ban sao cua danh sach DELETE. Them bang
     * {@code kyc_submissions} o V7 lam <b>hai muoi</b> test do cung mot luc:
     *
     * <pre>
     * DELETE FROM users
     *   ERROR: update or delete on table "users" violates foreign key constraint
     *          "kyc_submissions_user_id_fkey"
     * </pre>
     *
     * Bang moi thi chi mot file biet, ba file kia khong. Gio them bang la sua o day, mot lan.
     *
     * <p>
     * 📌 Thu tu KHONG duoc doi tuy tien: bang con truoc, bang cha sau. {@code users} phai la
     * dong cuoi cung vi moi bang khac deu tro toi no.
     */
    protected void xoaHetDuLieu() {
        db.sql("DELETE FROM reconciliation_runs").update();
        db.sql("DELETE FROM kyc_submissions").update();
        db.sql("DELETE FROM transactions").update();
        db.sql("DELETE FROM audit_log").update();
        db.sql("DELETE FROM refresh_tokens").update();
        db.sql("DELETE FROM wallets").update();
        db.sql("DELETE FROM users").update();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "khoa-test-32-ky-tu-toi-thieu-cho-HS256");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");

        // Cache BAT trong test. Mac dinh cua app la `none` (xem application.properties) vi
        // Render chua co Redis - nhung test ma chay voi cache tat thi no dang canh giu mot
        // duong khac han duong that.
        // ⚠️ TAT job doi soat trong test. No chay theo lich, nen neu bat thi giua chung mot
        // test nao do no se quet hai bang dung luc test dang dung du lieu - va ket qua doi
        // theo thoi diem. Do la dinh nghia cua mot test flaky.
        //
        // ReconciliationIT goi thang service thay vi doi toi gio.
        registry.add("app.reconciliation.enabled", () -> "false");

        registry.add("spring.cache.type", () -> "redis");

        // ⚠️ Phai la `url`, KHONG phai host + port.
        //
        // application.properties luon dat `spring.data.redis.url` (mac dinh localhost:6379),
        // va Spring Boot cho `url` DE LEN host/port. Neu o day van dang ky host/port thi hai
        // dong do bi bo qua, test se noi vao Redis o localhost thay vi container - va tren mot
        // may co san Redis chay thi no VAN XANH, chi la dang test nham mot database khac.
        //
        // Loai loi im lang quen thuoc: khong do, chi sai.
        registry.add("spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }
}
