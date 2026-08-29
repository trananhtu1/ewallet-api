/*
 * FEATURE  : Nền cho mọi integration test
 * VAI TRÒ  : Chặn lần build 'xanh giả': REQUIRE_DOCKER=true mà không có Docker thì ĐỎ.
 * LIÊN QUAN: PostgresIT
 */
package com.vidien.ewallet.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * ⭐ Mot test khong kiem nghiep vu nao ca. No kiem <b>lan build nay co dang tin khong</b>.
 *
 * <p>
 * <b>Van de no giai quyet, ghi ngay 29/08.</b> {@code PostgresIT} khai
 * {@code @Testcontainers(disabledWithoutDocker = true)}. Tren may Mac khong co Docker thi do la
 * dung: test bo qua thay vi lam do ca lan build. Nhung tren may LE RA PHAI CO Docker thi cung
 * dong do bien mot <b>loi ha tang</b> thanh mot dong khong ai doc:
 *
 * <pre>
 * Tests run: 32, Failures: 0, Errors: 0, Skipped: 32
 * BUILD SUCCESS
 * </pre>
 *
 * Da xay ra that: Docker chay ngon lanh, ma Testcontainers 1.21.3 khong noi chuyen duoc voi
 * Docker Engine 29 nen tra ve "khong co Docker" - va build van xanh. Neu chi nhin mau build
 * thi hom do da tick xong mot o checkbox dua tren 32 test chua bao gio chay.
 *
 * <p>
 * <b>Cach chua.</b> Mot lop chan khong dung Testcontainers de tu tat minh. Khi
 * {@code REQUIRE_DOCKER=true} ma khong tim thay Docker thi no <b>DO</b>, va thong bao noi dung
 * chuyen gi xay ra thay vi de nguoi doc tu doan tu mot con so Skipped.
 *
 * <pre>
 * # may Mac / may khong co Docker - khong dat bien, moi thu nhu cu
 * ./mvnw verify
 *
 * # may Windows o nha, va CI sau nay
 * REQUIRE_DOCKER=true ./mvnw verify
 * </pre>
 *
 * <p>
 * 📌 Vi sao la mot test rieng chu khong sua {@code disabledWithoutDocker}: gia tri cua
 * annotation la hang so luc BIEN DICH, khong the doi theo bien moi truong. Va dat mot
 * {@code Assumptions} vao {@code @BeforeAll} cua {@code PostgresIT} thi vuong thu tu chay giua
 * extension cua Spring va cua Testcontainers. Mot test doc lap thi khong phu thuoc vao thu tu
 * nao ca - no chi tra loi dung mot cau, va tra loi to.
 */
class DockerRequiredIT {

    private static final String BIEN = "REQUIRE_DOCKER";

    @Test
    @DisplayName("REQUIRE_DOCKER=true thi Docker PHAI co - khong duoc lang le bo qua")
    void khiBatBuocThiDockerPhaiCo() {
        if (!Boolean.parseBoolean(System.getenv(BIEN))) {
            // Khong dat bien = may phat trien binh thuong. Im lang di qua, dung lam phien.
            return;
        }

        assertThat(DockerClientFactory.instance().isDockerAvailable())
                .as("""
                        %s=true nhung Testcontainers khong tim thay Docker.

                        Moi integration test vua bi BO QUA, va build se bao SUCCESS neu khong \
                        co dong nay. Kiem theo thu tu:
                          1. docker ps           - daemon co chay khong
                          2. docker version      - Engine co qua moi so voi Testcontainers khong
                          3. so phien ban testcontainers-bom trong pom.xml

                        Da gap that 29/08: Docker 29 + Testcontainers 1.21.3 -> HTTP 400, va \
                        Testcontainers dich no thanh "khong co Docker".""".formatted(BIEN))
                .isTrue();
    }
}
