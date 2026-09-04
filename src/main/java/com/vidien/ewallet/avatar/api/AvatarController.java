/*
 * FEATURE  : Ảnh đại diện
 * VAI TRÒ  : Biên HTTP — người dùng lấy từ TOKEN, không từ tham số.
 * LIÊN QUAN: AvatarService
 */
package com.vidien.ewallet.avatar.api;

import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.vidien.ewallet.avatar.domain.AvatarService;
import com.vidien.ewallet.avatar.domain.exception.InvalidAvatarException;

/**
 * Doi anh dai dien cua CHINH nguoi goi.
 *
 * <p>
 * ⚠️ Khong nhan {@code userId}. Nhan tu tham so la cho bat ky ai doi anh dai dien cua nguoi
 * khac - cung lo BOLA da bit o PR #2, va o day hau qua la mao danh chu khong phai mat tien.
 */
@RestController
@RequestMapping("/api/users/me/avatar")
public class AvatarController {

    private final AvatarService avatars;

    public AvatarController(AvatarService avatars) {
        this.avatars = avatars;
    }

    @PostMapping
    public Map<String, String> doiAnh(@RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        if (file.isEmpty()) {
            throw new InvalidAvatarException("Chưa chọn tệp");
        }

        byte[] bytes;
        try {
            // getBytes() doc ca file vao bo nho. Chap nhan duoc vi Spring da chan o
            // max-file-size (5MB) TRUOC KHI controller chay mot dong nao - doc mot file 2GB
            // len RAM roi moi bao "qua lon" la mot duong tan cong bang chinh tinh nang cua
            // minh. Con chuyen file NHO ma bung ra bitmap khong lo thi AvatarProcessor lo.
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidAvatarException("Không đọc được tệp");
        }

        return Map.of("avatarUrl", avatars.doiAnh(Long.parseLong(jwt.getSubject()), bytes));
    }
}
