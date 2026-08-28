package com.vidien.ewallet.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Doc danh tinh nguoi goi ra tu token da duoc KIEM CHU KY.
 *
 * <p>
 * Vi sao la mot lop rieng chu khong goi jwt.getClaim() rai rac trong controller: day la
 * ranh gioi giua "thu nguoi goi NOI" va "thu he thong BIET". Moi so lieu di qua day deu la
 * so lieu Spring Security da xac minh chu ky truoc do; moi so lieu den tu URL hay body thi
 * khong. Gom lai mot cho de cho ranh gioi do co mot cai ten, va de khi doi cach xac thuc thi
 * sua mot file.
 *
 * <p>
 * ⚠️ Khong bao gio them ham nao vao day ma doc du lieu tu HttpServletRequest. Lop nay chi
 * duoc phep biet toi Jwt.
 */
public final class Caller {

    private Caller() {}

    /** Ai dang goi. `sub` la id nguoi dung - dung id chu khong dung email vi email doi duoc. */
    public static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    /**
     * Vi cua nguoi dang goi.
     *
     * <p>
     * Doc qua Number roi longValue() chu khong ep thang sang Long: JSON chi co MOT kieu so,
     * nen thu vien giai ma duoc quyen tra ve Integer cho gia tri nho va Long cho gia tri lon.
     * Ep thang sang Long thi he thong chay ngon cho toi ngay co nguoi dung thu 2^31 - roi
     * ClassCastException o mot cho khong lien quan gi toi con so do.
     */
    public static long walletId(Jwt jwt) {
        Number walletId = jwt.getClaim("walletId");
        if (walletId == null) {
            // Token ky dung nhung thieu claim: gan nhu chac chan la token cu phat truoc khi
            // doi cau truc claims. Nem ra chu khong tra 0 - vi 0 se di tiep xuong tang duoi
            // va bien thanh mot cau hoi ve "vi so 0".
            throw new IllegalStateException("Token khong co claim walletId");
        }
        return walletId.longValue();
    }
}
