/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Bật cơ chế @Scheduled. Thiếu file này thì job không bao giờ chạy.
 * LIÊN QUAN: ReconciliationScheduler
 */
package com.vidien.ewallet.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ⭐ {@code @EnableScheduling} - mot dong, va thieu no thi MOI {@code @Scheduled} trong project
 * <b>im lang khong bao gio chay</b>.
 *
 * <p>
 * Khong loi, khong canh bao, khong mot dong log. App khoi dong binh thuong, health check xanh,
 * va job doi soat dung im mai mai. Den luc co nguoi hoi "sao ba thang nay khong co dong doi
 * soat nao" thi da ba thang.
 *
 * <p>
 * 📌 <b>Day la lan thu TAM trong project cung mot ho loi</b> - khai bao mot thu ma thieu cai
 * kich hoat no:
 *
 * <ol>
 *   <li>{@code @Valid} thieu o tham so -> 200 cho body rac</li>
 *   <li>{@code @RestController} thay vi {@code @RestControllerAdvice} -> handler khong duoc goi</li>
 *   <li>{@code starter-flyway} co ma migration khong chay</li>
 *   <li>4 file {@code *IT} thieu {@code maven-failsafe-plugin} -> {@code mvn test} BUILD SUCCESS</li>
 *   <li>{@code disabledWithoutDocker} nuot ca tieng keu -> {@code Skipped: 8}</li>
 *   <li>{@code @EnableCaching} (co o {@code CacheConfig})</li>
 *   <li>{@code this.read()} khong qua proxy -> {@code @Cacheable} bi bo qua sach</li>
 *   <li><b>{@code @EnableScheduling} - dong nay</b></li>
 * </ol>
 *
 * Ghi lai o day de lan thu chin con nho di kiem.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * {@link Clock} lam mot bean, khong goi {@code LocalDate.now()} thang trong service.
     *
     * <p>
     * Vi sao: {@code LocalDate.now()} lay gio he thong, va test <b>khong the</b> noi voi no
     * rang "hom nay la ngay 30". Doi soat thi ngay nghiep vu la khai niem trung tam - can test
     * duoc canh "chay lai trong cung mot ngay" va "sang ngay hom sau thi la dong moi".
     *
     * <p>
     * Mot bean {@code Clock} lam viec do bang mot dong trong test:
     * {@code Clock.fixed(...)}. Khong co no thi phai doi toi nua dem.
     */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
