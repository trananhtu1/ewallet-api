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
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
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

                // ⚠️⚠️ HAI DONG NAY LA THU LAM CODE CHAY DUOC VOI KHO KHONG PHAI AWS.
                //
                // Tu ban 2.30.0, AWS SDK TU GAN mot header `x-amz-checksum-crc32` vao moi
                // lenh PutObject - mot lop kiem toan ven du lieu bat mac dinh. AWS S3 that
                // thi hieu no. Nhieu kho tuong thich S3 thi KHONG, va chung tra ve loi kieu
                //
                //     NotImplemented: Header 'x-amz-checksum-crc32' not implemented
                //
                // Cloudflare R2, OVHcloud va Backblaze deu vo vi dung chuyen nay khi SDK
                // nhay qua 2.30. Thong bao loi khong noi mot chu nao ve checksum tu phia
                // nguoi goi - no chi la mot ma 4xx/5xx tu kho, o giua mot lenh upload binh
                // thuong.
                //
                // 📌 Supabase thi CHAP NHAN header do - da do that bang mot lan upload len
                // production ngay 04/09, va anh len duoc. Nen hai dong nay KHONG can thiet
                // cho nha cung cap hien tai.
                //
                // Van dat, vi ca file nay dua tren mot loi hua: "doi kho chi ton mot bien
                // moi truong". Thieu hai dong nay thi loi hua do sai voi R2 va Backblaze -
                // hai cai ten nhieu kha nang duoc chon nhat khi het han muc Supabase.
                //
                // WHEN_REQUIRED = chi tinh checksum khi thao tac BAT BUOC phai co, thay vi
                // mac dinh WHEN_SUPPORTED (tinh bat cu khi nao co the).
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                // Client nhe nhat cua SDK. Vai lan goi mot phut thi khong can Netty.
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
