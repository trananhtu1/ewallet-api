/*
 * FEATURE  : Hồ sơ người dùng
 * VAI TRÒ  : Biên HTTP — chỉ trả về hồ sơ của CHÍNH người gọi.
 */
package com.vidien.ewallet.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.avatar.domain.AvatarService;
import com.vidien.ewallet.user.api.dto.MeResponse;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.user.infra.UserRepository;

/**
 * ⚠️ Chi co {@code /me}, khong co {@code /{id}}.
 *
 * <p>
 * Mot endpoint doc ho so theo id la mot lo BOLA cho san: doi mot chu so la doc duoc email va
 * ten cua nguoi khac. Khong co nhu cau nao trong ung dung nay can no, nen no khong ton tai -
 * cach re nhat de mot lo hong khong bao gio xuat hien.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final AvatarService avatars;

    public UserController(UserRepository users, AvatarService avatars) {
        this.users = users;
        this.avatars = avatars;
    }

    @GetMapping("/me")
    public MeResponse toi(@AuthenticationPrincipal Jwt jwt) {
        User u = users.findById(Long.parseLong(jwt.getSubject())).orElseThrow();

        return new MeResponse(u.getId(), u.getEmail(), u.getFullName(),
                avatars.urlCua(u).orElse(null));
    }
}
