package com.vidien.ewallet.audit;

/**
 * Danh sach su kien duoc ghi nhat ky.
 *
 * <p>
 * La enum chu khong phai chuoi tu do: go nham "TRANFER_REJECTED" thieu chu S thi voi chuoi la
 * mot dong nhat ky khong ai tim thay bao gio, con voi enum thi khong bien dich duoc. Cung mot
 * hinh dang loi voi vu go sai ten truong JSONB do hom 28/08 - chi khac la o day chan duoc.
 *
 * <p>
 * ⚠️ Chi ghi su kien co Y NGHIA VE TIEN hoac VE BAO MAT. Ghi het moi thu thi nhat ky phinh ra
 * va luc co su co that se khong tim thay gi - dung cai ly do da lam /health tra 500 cho moi
 * URL sai hom 27/08 lam ngap log.
 */
public enum AuditEvent {

    /** Dang nhap that bai. Dem duoc so lan lien tiep -> phat hien do mat khau. */
    LOGIN_FAILED,

    /** Tai khoan moi. Diem bat dau cua moi dau vet ve sau. */
    REGISTERED,

    /** Chuyen tien thanh cong. So cai da co dong nay, nhat ky ghi kem ngu canh. */
    TRANSFER_SUCCEEDED,

    /** Chuyen tien bi tu choi - het tien, sai vi dich, tu chuyen cho minh. */
    TRANSFER_REJECTED,

    /**
     * ⭐ Su kien dang gia nhat ca danh sach: co nguoi nhan minh la mot vi khong phai cua ho.
     *
     * <p>
     * So cai KHONG co dong nao cho viec nay - dung, vi khong co dong tien nao chay. Nhung day
     * la thu duy nhat phan biet duoc "frontend go nham" voi "co nguoi dang do vi nguoi khac",
     * va phan biet duoc bang cach dem: mot lan la loi, hai muoi lan trong mot phut thi khong.
     */
    ACCESS_DENIED,

    /**
     * 🚨 Mot refresh token DA DUNG ROI duoc trinh ra lan nua - nghia la co HAI ban sao dang
     * ton tai. Su kien nghiem trong nhat trong danh sach nay: no khong the xay ra o mot
     * client hoat dong binh thuong.
     */
    REFRESH_TOKEN_REUSED
}
