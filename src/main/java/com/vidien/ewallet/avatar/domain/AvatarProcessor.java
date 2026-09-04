/*
 * FEATURE  : Ảnh đại diện
 * VAI TRÒ  : Đọc ảnh người dùng gửi, thu nhỏ, và trả về JPEG sạch metadata.
 * LIÊN QUAN: AvatarService · DocumentImage (KYC, dùng lại phần kiểm magic bytes)
 */
package com.vidien.ewallet.avatar.domain;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import com.vidien.ewallet.avatar.domain.exception.InvalidAvatarException;

/**
 * Bien mot file nguoi dung gui thanh mot avatar vuong 256px, JPEG, khong metadata.
 *
 * <p>
 * ⭐⭐ <b>VI SAO PHAI GIAI MA CO SUBSAMPLING</b> - day la phan quan trong nhat file nay.
 *
 * <p>
 * {@code ImageIO.read(bytes)} doc ca anh vao bo nho duoi dang bitmap. Mot anh dien thoai
 * 4000x3000 la <b>12 trieu diem anh x 4 byte = 48MB</b> - trong mot container co
 * {@code -Xmx176m}. Hai nguoi doi avatar cung luc la het heap.
 *
 * <p>
 * Va file JPEG goc chi nang 3MB, nen moi gioi han ve <i>kich thuoc file</i> deu khong chan
 * duoc chuyen nay: JPEG nen rat tot, mot file nho co the bung ra rat lon. Day la mot huong
 * tan cong co ten rieng - <b>decompression bomb</b>.
 *
 * <p>
 * {@code setSourceSubsampling} bao bo giai ma <b>bo bot diem anh NGAY TRONG LUC DOC</b>, nen
 * bitmap day du khong bao gio ton tai. Anh 4000px voi buoc nhay 8 chi ton ~0.75MB.
 *
 * <p>
 * ⭐ <b>VA GHI LAI BANG ImageIO LA CACH XOA EXIF.</b> Anh chup bang dien thoai mang EXIF, trong
 * do co <b>toa do GPS</b>. Nguoi dung doi avatar bang mot tam anh chup o nha la vua cong khai
 * dia chi nha minh. Bo ghi JPEG cua {@code ImageIO} khong sao chep metadata tru khi duoc bao
 * ro - nen giai ma ra diem anh roi ma hoa lai la <b>chi con diem anh</b>.
 *
 * <p>
 * 📌 Do la ly do KHONG dung presigned URL cho avatar: file di thang len kho thi khong ai xoa
 * EXIF ho. Chi tiet trong ghi chu o {@code AvatarService}.
 */
@Component
public class AvatarProcessor {

    /** Canh avatar sau khi xu ly. 256 du net cho moi cho hien no. */
    private static final int CANH = 256;

    /**
     * Tran diem anh cua anh GOC. 40 trieu ~ anh 8000x5000.
     *
     * <p>
     * Chan o day chu khong o kich thuoc file, vi hai thu khong lien quan: mot JPEG 200KB co
     * the bung ra 100 trieu diem anh.
     */
    private static final long MAX_PIXEL = 40_000_000L;

    public byte[] xuLy(byte[] gui) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gui))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                // Khong bo giai ma nao nhan ra -> khong phai anh, bat ke duoi file la gi.
                throw new InvalidAvatarException("Tệp không phải là ảnh hợp lệ");
            }

            ImageReader reader = readers.next();
            reader.setInput(in);

            int rong = reader.getWidth(0);
            int cao = reader.getHeight(0);

            // Doc kich thuoc TRUOC khi giai ma mot diem anh nao - header du de biet.
            if ((long) rong * cao > MAX_PIXEL) {
                throw new InvalidAvatarException("Ảnh quá lớn, tối đa 40 triệu điểm ảnh");
            }

            BufferedImage anh = docThuNho(reader, rong, cao);
            reader.dispose();

            return maHoaLai(catVuong(anh));

        } catch (IOException e) {
            throw new InvalidAvatarException("Không đọc được tệp ảnh");
        }
    }

    /**
     * Giai ma voi buoc nhay, de bitmap day du khong bao gio nam trong bo nho.
     *
     * <p>
     * Buoc nhay chon sao cho anh sau khi doc con it nhat {@code CANH} diem o canh ngan - thu
     * nho them nua thi lam o buoc sau, tren mot anh da nho.
     */
    private BufferedImage docThuNho(ImageReader reader, int rong, int cao) throws IOException {
        int canhNgan = Math.min(rong, cao);
        int buoc = Math.max(1, canhNgan / CANH);

        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(buoc, buoc, 0, 0);

        return reader.read(0, param);
    }

    /** Cat vuong o giua roi thu ve dung {@code CANH}. */
    private BufferedImage catVuong(BufferedImage anh) {
        int canh = Math.min(anh.getWidth(), anh.getHeight());
        BufferedImage vuong = anh.getSubimage((anh.getWidth() - canh) / 2,
                (anh.getHeight() - canh) / 2, canh, canh);

        // TYPE_INT_RGB chu khong ARGB: JPEG khong co kenh trong suot. Doc mot PNG co nen
        // trong suot roi ghi thang ra JPEG voi ARGB se ra anh am ban mau.
        BufferedImage ra = new BufferedImage(CANH, CANH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ra.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(vuong, 0, 0, CANH, CANH, null);
        g.dispose();
        return ra;
    }

    private byte[] maHoaLai(BufferedImage anh) throws IOException {
        var out = new ByteArrayOutputStream();
        // Chinh ghi lai nay la cai xoa EXIF - xem ghi chu dau lop.
        ImageIO.write(anh, "jpg", out);
        return out.toByteArray();
    }
}
