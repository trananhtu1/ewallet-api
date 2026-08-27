package com.vidien.ewallet.wallet;

import java.math.BigDecimal;
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

    /**
     * Cong tien BANG SQL, khong doc so du ra Java roi cong roi ghi lai.
     *
     * <p>
     * Doc-roi-ghi la "lost update": hai request song song cung doc duoc 100, cung ghi 150, nap
     * hai lan ma chi vao mot lan. De database tu cong thi no khoa dong do trong luc UPDATE.
     *
     * <p>
     * Tra ve so dong bi sua: 0 nghia la khong co vi nao mang id do.
     */
    public int addToBalance(long walletId, BigDecimal amount) {
        return db.sql("""
                UPDATE wallets
                SET balance = balance + :amount,
                    version = version + 1
                WHERE id = :id
                """)
                .param("amount", amount)
                .param("id", walletId)
                .update();
    }

    /**
     * Khoa mot dong vi lai cho toi khi transaction ket thuc (SELECT ... FOR UPDATE).
     *
     * <p>
     * Bat cu transaction nao khac dong vao chinh dong nay se phai DUNG CHO. Nho vay doan
     * doc-so-du-roi-quyet-dinh o tang service moi an toan: khong ai chen ngang duoc o giua.
     *
     * <p>
     * QUAN TRONG - phai goi theo THU TU ID TANG DAN khi khoa nhieu vi. Hai lenh chuyen tien
     * nguoc chieu nhau (1->2 va 2->1) chay cung luc, moi ben khoa mot vi roi cho ben kia, se
     * deadlock. Postgres phat hien duoc va giet mot ben, nhung nguoi dung ben do an loi 500
     * ma khong hieu vi sao. Khoa theo thu tu co dinh thi tinh huong do khong ton tai.
     */
    public void lockById(long walletId) {
        db.sql("SELECT id FROM wallets WHERE id = :id FOR UPDATE")
                .param("id", walletId)
                .query(Long.class)
                .optional();
    }

    /**
     * Tru tien. Dieu kien balance >= :amount nam NGAY TRONG cau UPDATE, khong phai kiem o Java.
     *
     * <p>
     * Tra ve 0 khi: khong co vi nao id do, HOAC so du khong du. Tang service da khoa dong tu
     * truoc nen phan biet duoc hai truong hop, nhung dieu kien nay van giu lai lam lop chan
     * thu hai - va no la thu duy nhat con dung neu mai kia co ai goi thang repository.
     */
    public int subtractFromBalance(long walletId, BigDecimal amount) {
        return db.sql("""
                UPDATE wallets
                SET balance = balance - :amount,
                    version = version + 1
                WHERE id = :id AND balance >= :amount
                """)
                .param("amount", amount)
                .param("id", walletId)
                .update();
    }

    /** Tao vi cho mot nguoi dung moi. So du bat dau tu 0. */
    public long insertForUser(long userId) {
        return db.sql("""
                INSERT INTO wallets (user_id, balance)
                VALUES (:userId, 0)
                RETURNING id
                """)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    /** Moi nguoi dung dung MOT vi - rang buoc UNIQUE tren user_id o V1 bao dam dieu do. */
    public Optional<Long> findByUserId(long userId) {
        return db.sql("SELECT id FROM wallets WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }
}
