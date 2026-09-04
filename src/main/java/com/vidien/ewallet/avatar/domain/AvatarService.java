/*
 * FEATURE  : Ảnh đại diện
 * VAI TRÒ  : Nhận ảnh, xử lý, đẩy lên kho, lưu khoá vào users.
 * LIÊN QUAN: AvatarProcessor · ObjectStorage · UserRepository · V11
 */
package com.vidien.ewallet.avatar.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.avatar.domain.exception.AvatarStorageDisabledException;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Doi anh dai dien.
 *
 * <p>
 * ⭐⭐ <b>VI SAO PROXY QUA BACKEND CHU KHONG PRESIGNED URL</b> - ghi lai day du vi day la
 * cau se bi hoi, va cau tra loi "em chua nghe presigned" thi khac han "em co can nhac".
 *
 * <p>
 * <b>Presigned URL</b> la cach backend ky mot duong link co han, trinh duyet dung link do
 * day file <b>thang len kho</b> - byte anh khong he di qua may chu ung dung. Do la cach
 * dung cho file lon: khong ai proxy mot video 500MB qua mot container 176MB.
 *
 * <p>
 * Nhung avatar thi <b>khong phai file lon</b>, va no can ba viec chi lam duoc o may chu:
 *
 * <ol>
 * <li><b>Xoa EXIF.</b> Anh chup bang dien thoai mang toa do GPS. Nguoi dung doi avatar bang
 * mot tam anh chup o nha la vua cong khai dia chi nha minh. Khong tin duoc vao trinh duyet
 * cho viec nay.
 * <li><b>Chan decompression bomb.</b> Mot JPEG 200KB co the bung ra 100 trieu diem anh. Chi
 * doc header moi biet, va chi lam duoc khi cam file trong tay.
 * <li><b>Thu ve 256px.</b> Kho khong biet gi ve anh, no chi luu byte.
 * </ol>
 *
 * <p>
 * Voi presigned thi ca ba viec do phai chuyen sang mot buoc xu ly bat dong bo chay sau -
 * them mot hang doi va mot worker, cho mot tam anh 200KB.
 *
 * <p>
 * 📌 Ranh gioi de nho: <b>file lon thi presigned, file can xu ly thi proxy.</b> Avatar roi
 * vao ve thu hai; ma KYC neu ngay nao giu anh that thi roi vao ve thu nhat.
 */
@Service
public class AvatarService {

    private final UserRepository users;
    private final AvatarProcessor processor;

    /**
     * ⚠️ {@link ObjectProvider} chu khong tiem thang {@code S3Client}.
     *
     * <p>
     * Bean do chi ton tai khi {@code app.storage.enabled=true}. Tiem thang thi thieu cau hinh
     * la <b>ca ung dung khong khoi dong duoc</b> - mot tinh nang phu lam chet ca he thong.
     * {@code ObjectProvider} cho phep hoi "co khong" luc chay.
     */
    private final ObjectProvider<S3Client> s3;

    private final String bucket;
    private final String publicUrl;

    public AvatarService(UserRepository users, AvatarProcessor processor,
            ObjectProvider<S3Client> s3, @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.public-url}") String publicUrl) {
        this.users = users;
        this.processor = processor;
        this.s3 = s3;
        this.bucket = bucket;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public String doiAnh(long userId, byte[] guiLen) {
        S3Client client = s3.getIfAvailable();
        if (client == null) {
            throw new AvatarStorageDisabledException();
        }

        // Xu ly TRUOC khi cham vao kho: hong o day thi khong de lai file rac.
        byte[] anh = processor.xuLy(guiLen);

        // Khoa co dinh theo userId, khong sinh ten ngau nhien: doi anh lan hai la GHI DE, va
        // khong tich luy rac. Doi lai la khong co lich su anh cu - dung y muon.
        //
        // ⚠️ Khoa KHONG chua ten bucket. Ba thu nay de nham lan, va nham thi ra mot duong
        // dan lap:
        //
        //   bucket     = "avatars"        <- cai NGAN chua file
        //   khoa       = "8.jpg"          <- duong dan BEN TRONG ngan do
        //   URL cong   = <public-url>/8.jpg
        //                 └ .../object/public/avatars  (da co ten bucket o day roi)
        //
        // Ban dau khoa la "avatars/8.jpg" -> URL ra .../public/avatars/avatars/8.jpg. Van tai
        // duoc anh, nen khong co gi bao loi - chi la mot doan duong dan thua nam do mai mai.
        String khoa = userId + ".jpg";

        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(khoa)
                // Dat contentType tu MINH, khong lay tu client khai: da ma hoa lai thanh JPEG
                // o buoc tren nen day la su that, khong phai loi khai bao.
                .contentType("image/jpeg")
                .build(), RequestBody.fromBytes(anh));

        User u = users.findById(userId).orElseThrow();
        u.setAvatarKey(khoa);
        u.setAvatarUpdatedAt(Instant.now());

        return urlCong(khoa, u.getAvatarUpdatedAt());
    }

    /** URL cong khai de trinh duyet tai anh. {@code Optional.empty()} khi chua co anh. */
    public Optional<String> urlCua(User u) {
        return Optional.ofNullable(u.getAvatarKey()).map(k -> urlCong(k, u.getAvatarUpdatedAt()));
    }

    /**
     * ⚠️ Gan them {@code ?v=<moc thoi gian>} vao cuoi URL.
     *
     * <p>
     * Khoa co dinh theo userId nghia la doi anh KHONG doi URL - va trinh duyet dang giu ban
     * cu trong bo nho dem se hien anh cu, co khi hang gio. Nguoi dung doi anh xong, tai lai
     * trang, van thay mat cu: mot loi khong co thong bao nao.
     *
     * <p>
     * Them mot tham so doi theo moi lan cap nhat thi URL khac di, va trinh duyet coi do la
     * mot anh khac. Ky thuat nay ten la <b>cache busting</b>.
     */
    private String urlCong(String khoa, Instant capNhat) {
        return publicUrl + "/" + khoa + "?v=" + (capNhat == null ? 0 : capNhat.toEpochMilli());
    }
}
