package com.vidien.ewallet.transaction;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * So cai: moi dong tien di qua he thong deu de lai mot dong o day.
 *
 * <p>
 * Luu y hai chu "transaction" trong project nay mang hai nghia khac han nhau. Bang transactions
 * la GIAO DICH TIEN TE, thu khach hang nhin thay. Con @Transactional la GIAO DICH DATABASE, co
 * che cua Postgres. Mot lan nap tien = 1 giao dich database chua 1 giao dich tien te.
 */
@Repository
public class TransactionRepository {

    private final JdbcClient db;

    public TransactionRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * from_wallet_id de NULL vi nap tien khong co vi nguon - tien di tu ngoai he thong vao.
     * Rang buoc ck_transactions_endpoints o V1 se tu choi neu dien nham mot vi nguon vao day.
     */
    public void insertDeposit(long toWalletId, BigDecimal amount) {
        db.sql("""
                INSERT INTO transactions (from_wallet_id, to_wallet_id, amount, type, status)
                VALUES (NULL, :toWalletId, :amount, 'DEPOSIT', 'SUCCESS')
                """)
                .param("toWalletId", toWalletId)
                .param("amount", amount)
                .update();
    }

    /**
     * Ghi mot lan chuyen tien thanh cong vao so cai.
     *
     * <p>
     * Chua ghi duoc dong status='FAILED': no nam trong chinh cai transaction bi rollback nen
     * se bien mat cung. Muon giu lai phai dung transaction rieng (propagation REQUIRES_NEW).
     * Ghi lai lam mon no, chua lam.
     */
    public void insertTransfer(long fromWalletId, long toWalletId, BigDecimal amount) {
        db.sql("""
                INSERT INTO transactions (from_wallet_id, to_wallet_id, amount, type, status)
                VALUES (:fromWalletId, :toWalletId, :amount, 'TRANSFER', 'SUCCESS')
                """)
                .param("fromWalletId", fromWalletId)
                .param("toWalletId", toWalletId)
                .param("amount", amount)
                .update();
    }

    /**
     * Lich su cua mot vi: ca tien vao lan tien ra, moi nhat truoc.
     *
     * <p>
     * Ban dau viet dang "WHERE from_wallet_id = :id OR to_wallet_id = :id ORDER BY created_at
     * DESC LIMIT 20" - ngan va doc duoc ngay. Do tren 50.000 dong: 3.57ms, va EXPLAIN cho thay
     * Postgres phai gom TAT CA giao dich cua vi do (BitmapOr tren ca hai index) roi SORT lai
     * moi lay duoc 20 dong dau. Chi phi tang theo so giao dich cua vi - vi cang dung lau cang cham.
     *
     * <p>
     * Ban UNION ALL duoi day tach thanh hai nhanh, moi nhanh khop dung MOT index (bao gom ca
     * created_at DESC trong index) nen doc san theo thu tu va DUNG SOM sau 20 dong. Postgres
     * ghep hai nhanh bang Merge Append, khong con node Sort nao. Do cung bo du lieu: 0.32ms.
     *
     * <p>
     * Nhanh hon 11 lan, va quan trong hon: chi phi khong con tang theo so giao dich cua vi.
     */
    public List<Transaction> findByWalletId(long walletId, int limit) {
        return db.sql("""
                SELECT * FROM (
                    (SELECT id, from_wallet_id, to_wallet_id, amount, type, status, created_at
                     FROM transactions WHERE from_wallet_id = :id
                     ORDER BY created_at DESC LIMIT :limit)
                    UNION ALL
                    (SELECT id, from_wallet_id, to_wallet_id, amount, type, status, created_at
                     FROM transactions WHERE to_wallet_id = :id
                     ORDER BY created_at DESC LIMIT :limit)
                ) x
                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("id", walletId)
                .param("limit", limit)
                .query(Transaction.class)
                .list();
    }
}
