package com.vidien.ewallet.transaction.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.transaction.domain.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Tim mot lan chuyen tien da ghi bang khoa chong lap.
     *
     * <p>
     * Pham vi tim la (VI NGUON, khoa) chu khong phai mot minh khoa - xem V2. Khoa do NGUOI GOI
     * tu sinh, nen hai nguoi dung khac nhau rat co the cung go "transfer-1"; tim theo mot minh
     * khoa thi nguoi thu hai nhan duoc ket qua giao dich CUA NGUOI KHAC.
     *
     * <p>
     * Day la derived query - Spring Data doc ten method roi sinh cau JPQL. Doi ten truong
     * trong entity ma quen doi o day thi <b>app chet luc khoi dong</b>, khong phai luc chay.
     */
    Optional<Transaction> findByFromWalletIdAndIdempotencyKey(Long fromWalletId, String key);

    /**
     * ⭐ Lich su cua mot vi: ca tien vao lan tien ra, moi nhat truoc.
     *
     * <p>
     * <b>Van la native query, va day la mot trong hai cho co y giu SQL viet tay trong ca
     * project.</b> Ly do da do duoc, khong phai so thich:
     *
     * <p>
     * Ban dau viet {@code WHERE from_wallet_id = :id OR to_wallet_id = :id ORDER BY created_at
     * DESC LIMIT 20} - ngan va doc duoc ngay. Do tren 50.000 dong: <b>3.57ms</b>, va EXPLAIN
     * cho thay Postgres phai gom TAT CA giao dich cua vi do (BitmapOr tren ca hai index) roi
     * SORT lai moi lay duoc 20 dong dau. Chi phi tang theo so giao dich cua vi.
     *
     * <p>
     * Ban {@code UNION ALL} duoi day tach thanh hai nhanh, moi nhanh khop dung MOT index (bao
     * gom ca {@code created_at DESC} trong index) nen doc san theo thu tu va DUNG SOM sau 20
     * dong. Postgres ghep bang Merge Append, khong con node Sort nao. Cung bo du lieu:
     * <b>0.32ms</b> - nhanh hon 11 lan.
     *
     * <p>
     * ⚠️ JPQL <b>khong co UNION</b>. Va {@code Specification} hay {@code Criteria API} cung
     * khong: chung sinh ra dung cai cau {@code OR} da do la cham. Day la vi du cu the cho cau
     * <i>"JPA cho gan het, tru cho no khong dien dat noi"</i>.
     */
    @Query(value = """
            SELECT * FROM (
                (SELECT id, from_wallet_id, to_wallet_id, amount, type, status,
                        idempotency_key, created_at
                 FROM transactions WHERE from_wallet_id = :id
                 ORDER BY created_at DESC LIMIT :limit)
                UNION ALL
                (SELECT id, from_wallet_id, to_wallet_id, amount, type, status,
                        idempotency_key, created_at
                 FROM transactions WHERE to_wallet_id = :id
                 ORDER BY created_at DESC LIMIT :limit)
            ) x
            ORDER BY created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Transaction> findByWalletId(@Param("id") long walletId, @Param("limit") int limit);
}
