package com.vidien.ewallet.user.domain;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Mot dong trong bang users.
 *
 * <p>
 * passwordHash mang @JsonIgnore: du co ai lo tay tra ca object nay ra API thi Jackson van
 * KHONG in no ra. Mot lop chan re tien cho mot loi rat dat.
 */
public record User(Long id, String email, @JsonIgnore String passwordHash, String fullName,
        Instant createdAt) {
}
