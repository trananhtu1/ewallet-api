/*
 * FEATURE  : Ảnh đại diện
 * VAI TRÒ  : Ghi/đọc file trên kho tương thích S3. Tắt được bằng cấu hình.
 * LIÊN QUAN: AvatarService · application.properties (app.storage.*)
 */
package com.vidien.ewallet.avatar.infra;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Dung {@link S3Client} tro vao <b>bat ky kho nao noi giao thuc S3</b>.
 *
 * <p>
 * ⭐ Day la ca diem cua viec chon giao thuc S3 thay vi API rieng cua mot nha cung cap: cung
 * mot doan code nay chay voi <b>AWS S3, Cloudflare R2, Supabase Storage, MinIO</b> - khac
 * nhau o dung mot dong {@code endpoint} trong bien moi truong.
 *
 * <p>
 * ⚠️ {@code pathStyleAccessEnabled(true)} - <b>bat buoc voi moi kho khong phai AWS</b>. AWS
 * mac dinh dung "virtual host style": {@code https://ten-bucket.s3.amazonaws.com/khoa}, tuc
 * la ten bucket nam trong <b>ten mien</b>. Cac kho khac khong co ten mien dai tro san cho
 * tung bucket, ho dung {@code https://kho.example.com/ten-bucket/khoa}. Bo dong nay thi SDK
 * sinh ra mot ten mien khong ton tai, va loi bao ve la <b>khong phan giai duoc DNS</b> - mot
 * thong bao khong lien quan gi toi nguyen nhan.
 *
 * <p>
 * ⚠️ {@code @ConditionalOnProperty}: thieu cau hinh thi bean nay <b>khong duoc dung</b>, va
 * ung dung van khoi dong binh thuong - chi la khong doi duoc avatar. Cung luat da dung cho
 * Redis: mot tinh nang phu khong duoc quyen lam ca he thong khong len duoc.
 */
@Configuration
public class ObjectStorage {

    @Bean
    @ConditionalOnProperty(name = "app.storage.enabled", havingValue = "true")
    S3Client s3Client(@Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.region}") String region,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey) {

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                // Kho khong phai AWS van doi mot ten vung - phan lon nhan "auto" hoac
                // "us-east-1". No chi tham gia vao viec tinh chu ky, khong tro vao dau ca.
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                // Client nhe nhat cua SDK. Vai lan goi mot phut thi khong can Netty.
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
