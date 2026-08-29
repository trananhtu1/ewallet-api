/*
 * FEATURE  : Cache số dư ví (Redis)
 * VAI TRÒ  : @EnableCaching + TTL 10 phút + JSON (Jackson 3). Mặc định TẮT nếu chưa có Redis.
 * LIÊN QUAN: WalletCache · application.properties (spring.cache.type)
 * BÀI GIẢNG: java-learn/java/07-cache/BUOI-15-REDIS-CACHE.md
 */
package com.vidien.ewallet.shared.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Cau hinh cache. Bon quyet dinh o day, ca bon deu do dung sai chu khong do so thich.
 *
 * <p>
 * <b>1. {@code @EnableCaching} phai co.</b> Thieu no thi moi {@code @Cacheable} trong project
 * <b>im lang khong lam gi</b> - khong loi, khong canh bao, va cache chua bao gio duoc dung.
 * Day dung la ho loi da gap NAM lan: {@code @Valid} thieu o tham so, {@code @RestController}
 * thay vi {@code @RestControllerAdvice}, {@code starter-flyway} khong chay, bon file
 * {@code *IT} thieu Failsafe, {@code disabledWithoutDocker} nuot ca tieng keu. Ghi lai o day
 * de lan sau con nho di kiem.
 *
 * <p>
 * <b>2. TTL 10 phut, va TTL la thu KHONG duoc bo.</b> Cache duoc xoa chu dong sau moi lan ghi
 * ({@code WalletCacheEvictor}), nen ve ly thuyet khong can TTL. Nhung "ve ly thuyet" la cho
 * nguy hiem: mot duong ghi moi ma quen phat su kien, mot lan Redis mat ket noi dung luc xoa,
 * mot lan deploy giua chung - bat ky cai nao cung de lai mot con so sai <b>vinh vien</b> neu
 * khong co TTL. TTL bien "sai mai mai" thanh "sai toi da 10 phut". No khong phai cai chinh,
 * no la <b>luoi</b>.
 *
 * <p>
 * <b>3. JSON chu khong phai JDK serialization.</b> Mac dinh cua Spring la
 * {@code JdkSerializationRedisSerializer}: no doi entity implement {@code Serializable}, ghi ra
 * mot chuoi byte <b>khong doc duoc bang mat</b>, va vo ngay khi class doi hinh dang. Doi mot
 * ten truong la moi ban cache cu thanh mot qua bom. JSON thi doc duoc bang
 * {@code redis-cli GET}, va thieu mot truong thi no la {@code null} chu khong phai mot ngoai
 * le luc doc.
 *
 * <p>
 * <b>4. ⚠️ {@code GenericJacksonJsonRedisSerializer}, KHONG phai
 * {@code GenericJackson2JsonRedisSerializer}.</b> Moi bai huong dan tren mang deu viet cai thu
 * hai, va no van con trong spring-data-redis 4.1 nen doc qua thi tuong dung. Nhung Spring Boot
 * 4 da chuyen sang <b>Jackson 3</b>: package doi tu {@code com.fasterxml.jackson.databind} sang
 * {@code tools.jackson.databind}. Chep nham thi loi la
 * {@code package com.fasterxml.jackson.databind does not exist} - mot thong bao khong he goi y
 * rang van de la PHIEN BAN LON cua thu vien. Chi rieng {@code jackson-annotations} giu nguyen
 * package cu, nen {@code @JsonFormat} trong project van chay va cang lam nguoi doc tuong Jackson
 * 2 con day du.
 *
 * <p>
 * {@code enableDefaultTyping} la BAT BUOC: khong co no, Jackson doc chuoi len va khong biet
 * phai dung lai thanh class gi - ra {@code LinkedHashMap} va {@code ClassCastException} o cho
 * goi. {@code BasicPolymorphicTypeValidator} gioi han danh sach class duoc phep dung lai: nhan
 * mot ten class bat ky tu Redis roi dung no len la mot lo hong da co ten (deserialization
 * gadget), va Redis khong phai luc nao cung chi minh ghi vao.
 *
 * <p>
 * 📌 {@code @ConditionalOnProperty}: khong co Redis thi <b>khong dung bean nay</b>, va Spring
 * Boot roi ve cache trong RAM. Nghia la app van chay o may chua cai Redis - quan trong voi
 * Render, noi Key Value instance phai tao rieng.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 10 phut. Xem ghi chu 2 o tren: day la luoi, khong phai cai chinh. */
    private static final Duration TTL = Duration.ofMinutes(10);

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    RedisCacheConfiguration redisCacheConfiguration() {
        // Chi cho dung lai class cua project va cua JDK. Wallet co BigDecimal va Instant, ca
        // hai nam trong java.* - Jackson 3 tu biet doc java.time, khong can them module nao.
        BasicPolymorphicTypeValidator chiCacClassNay = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.vidien.ewallet.")
                .allowIfSubType("java.")
                .build();

        GenericJacksonJsonRedisSerializer json = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(chiCacClassNay)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                // Khong ghi gia tri null xuong Redis: xem `unless` trong WalletCache.
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(json));
    }
}
