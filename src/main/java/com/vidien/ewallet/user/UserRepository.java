package com.vidien.ewallet.user;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient db;

    public UserRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Tim theo email, KHONG phan biet hoa thuong.
     *
     * <p>
     * LOWER(email) o day khop dung voi unique index ux_users_email_lower danh o V1, nen cau
     * nay dung duoc index chu khong quet ca bang. Viet WHERE email = :email thi vua sai
     * nghiep vu (Anh@x.com khac anh@x.com) vua khong dung duoc index do.
     */
    public Optional<User> findByEmail(String email) {
        return db.sql("""
                SELECT id, email, password_hash, full_name, created_at
                FROM users
                WHERE LOWER(email) = LOWER(:email)
                """)
                .param("email", email)
                .query(User.class)
                .optional();
    }

    /** Tra ve id vua sinh ra, khoi phai SELECT lai. */
    public long insert(String email, String passwordHash, String fullName) {
        return db.sql("""
                INSERT INTO users (email, password_hash, full_name)
                VALUES (:email, :passwordHash, :fullName)
                RETURNING id
                """)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .param("fullName", fullName)
                .query(Long.class)
                .single();
    }
}
