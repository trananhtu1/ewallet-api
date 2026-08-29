/*
 * FEATURE  : KYC — nộp ảnh CCCD (giả lập)
 * VAI TRÒ  : Kiểm nội dung ảnh và rút metadata. KHÔNG giữ byte ảnh, chỉ giữ SHA-256.
 * LIÊN QUAN: KycService · KycController · V7__create_kyc_submissions.sql
 */
package com.vidien.ewallet.kyc.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import com.vidien.ewallet.kyc.domain.exception.InvalidDocumentException;

/**
 * ⭐ Mot anh giay to da duoc KIEM, cung metadata rut ra tu chinh noi dung file.
 *
 * <p>
 * <b>Toan bo file nay ton tai vi mot cau: KHONG TIN VAO NHUNG GI CLIENT NOI VE FILE.</b>
 *
 * <p>
 * {@code MultipartFile} mang theo hai thu do <b>trinh duyet</b> khai bao:
 * {@code getContentType()} va {@code getOriginalFilename()}. Ca hai deu la <b>de nghi</b>, y
 * het {@code {id}} tren duong dan trong bai BOLA. Mot request tu tay lam co the noi
 * {@code Content-Type: image/jpeg} cho mot file {@code .exe}, va no khong sai cu phap o dau ca.
 *
 * <p>
 * Nen o day kiem <b>byte dau file</b> - thu ma nguoi gui khong khai bao ma chinh noi dung phai
 * mang:
 *
 * <pre>
 * JPEG: FF D8 FF
 * PNG : 89 50 4E 47 0D 0A 1A 0A
 * </pre>
 *
 * <p>
 * ⚠️ Va can noi ro gioi han cua no: khop magic bytes <b>khong</b> chung minh file la anh that.
 * Mot file JPEG hop le co the mang payload o duoi. No chi loai bo duoc lop de nhat va lam ke
 * tan cong ton cong hon. Bao ve that su la: khong bao gio chay file nguoi dung gui len, khong
 * bao gio phuc vu no tu cung domain, va quet virus o mot dich vu rieng.
 *
 * @param mime kieu <b>doc duoc tu noi dung</b>, khong phai kieu client khai
 * @param bytes so byte that
 * @param sha256 ma bam de doi chieu - ban gia lap khong luu anh, chi luu dau van tay nay
 * @param originalName ten file client khai; CHI de hien lai cho nguoi dung, khong dung lam
 *        duong dan bao gio
 */
public record DocumentImage(String mime, long bytes, String sha256, String originalName) {

    /** 5 MB. Anh CCCD chup bang dien thoai thuong duoi 2 MB. */
    public static final long TOI_DA = 5L * 1024 * 1024;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * Kiem noi dung va rut metadata.
     *
     * @param noiDung byte that cua file
     * @param tenClientKhai ten file do client gui - khong duoc tin
     */
    public static DocumentImage kiem(byte[] noiDung, String tenClientKhai) {
        if (noiDung == null || noiDung.length == 0) {
            throw new InvalidDocumentException("Anh rong");
        }
        if (noiDung.length > TOI_DA) {
            // Cho nay gan nhu khong bao gio chay: gioi han da duoc chan o tang servlet TRUOC
            // khi vao day (spring.servlet.multipart.max-file-size). Giu lai vi neu mai kia co
            // duong nao goi thang vao service - mot job nhap lieu chang han - thi no la hang
            // rao duy nhat.
            throw new InvalidDocumentException("Anh lon hon " + (TOI_DA / 1024 / 1024) + "MB");
        }

        String mime = doanKieuTuNoiDung(noiDung);
        if (mime == null) {
            throw new InvalidDocumentException("Chi nhan anh JPEG hoac PNG");
        }

        return new DocumentImage(mime, noiDung.length, bam(noiDung), lamSachTen(tenClientKhai));
    }

    private static String doanKieuTuNoiDung(byte[] b) {
        if (batDauBang(b, JPEG)) {
            return "image/jpeg";
        }
        if (batDauBang(b, PNG)) {
            return "image/png";
        }
        return null;
    }

    private static boolean batDauBang(byte[] b, byte[] dauHieu) {
        if (b.length < dauHieu.length) {
            return false;
        }
        for (int i = 0; i < dauHieu.length; i++) {
            if (b[i] != dauHieu[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * SHA-256 cua noi dung.
     *
     * <p>
     * Ban gia lap khong luu anh, nen day la thu duy nhat con lai de tra loi cau <i>"co phai
     * dung file nay khong"</i>. No cung lam lo ra chuyen mot nguoi nop <b>cung mot anh</b> hai
     * lan - hoac hai nguoi khac nhau nop <b>cung mot anh</b>, thu ma bo phan chong gian lan
     * rat muon biet.
     */
    private static String bam(byte[] noiDung) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(noiDung));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 la bat buoc trong moi ban JDK. Den day duoc nghia la JVM hong.
            throw new IllegalStateException("JVM khong co SHA-256", e);
        }
    }

    /**
     * Ten file: chi giu phan ten, bo moi thu trong nhu duong dan.
     *
     * <p>
     * ⚠️ Ten do client khai co the la {@code ../../etc/passwd} hoac
     * {@code C:\Windows\system32\x.jpg}. Cho nay khong dung ten de mo file nao ca - no chi
     * duoc luu vao JSONB roi hien lai cho nguoi dung - nhung cat sach dau phan cach ngay tu
     * bien la thoi quen re nhat. Ngay nao co ai them mot dong ghi file bang ten nay thi cho do
     * da an toan san.
     */
    private static String lamSachTen(String ten) {
        if (ten == null || ten.isBlank()) {
            return "khong-ten";
        }
        String chiTen = ten.replace('\\', '/');
        chiTen = chiTen.substring(chiTen.lastIndexOf('/') + 1);
        return chiTen.length() > 100 ? chiTen.substring(0, 100) : chiTen;
    }

    /** Dang de nhet vao cot JSONB. */
    public Map<String, Object> toMetadata(String vaiTro) {
        return Map.of(
                "role", vaiTro,
                "mime", mime,
                "bytes", bytes,
                "sha256", sha256,
                "originalName", originalName);
    }
}
