package com.vidien.ewallet.wallet;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tầng duy nhất được phép biết SQL. Controller và service không bao giờ thấy chữ SELECT nào.
 *
 * <p>
 * JdbcClient là API mới (Spring 6.1+), gọn hơn JdbcTemplate nhưng chạy trên chính nó. Nói "dùng
 * JdbcTemplate" lúc phỏng vấn vẫn đúng.
 */
@Repository
public class WalletRepository {
    private final JdbcClient db;

    public WalletRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<Wallet> findById(long id) {
        return db.sql("""
                SELECT id, user_id, balance, version, created_at
                FROM wallets
                WHERE id = :id
                """)
                // :id là THAM SỐ, không phải nối chuỗi. Đây là thứ chặn SQL injection:
                // giá trị đi đường riêng, database không bao giờ đọc nó như là câu lệnh.
                .param("id", id).query(Wallet.class)
                // optional() = "0 hoặc 1 dòng". Nhiều hơn 1 thì ném lỗi, đúng ý mình.
                // Dùng single() thì 0 dòng cũng ném lỗi - không hợp cho việc tra cứu.
                .optional();
    }
}
