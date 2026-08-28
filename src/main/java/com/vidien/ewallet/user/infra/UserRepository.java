package com.vidien.ewallet.user.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.user.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tim theo email, KHONG phan biet hoa thuong.
     *
     * <p>
     * ⚠️ Viet tay JPQL chu khong dung {@code findByEmailIgnoreCase} sinh san. Ca hai deu ra
     * {@code LOWER(email) = LOWER(?)}, nhung viet ro o day de nguoi doc thay no <b>khop dung
     * voi unique index {@code ux_users_email_lower}</b> danh o V1 - tuc la cau nay dung index
     * chu khong quet ca bang.
     *
     * <p>
     * Viet {@code WHERE email = :email} thi vua sai nghiep vu (Anh@x.com khac anh@x.com) vua
     * khong dung duoc index do.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoringCase(@Param("email") String email);
}
