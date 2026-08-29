package com.vidien.ewallet.transaction.infra;

import java.time.Instant;
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
                 ORDER BY created_at DESC, id DESC LIMIT :limit)
                UNION ALL
                (SELECT id, from_wallet_id, to_wallet_id, amount, type, status,
                        idempotency_key, created_at
                 FROM transactions WHERE to_wallet_id = :id
                 ORDER BY created_at DESC, id DESC LIMIT :limit)
            ) x
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Transaction> findFirstPage(@Param("id") long walletId, @Param("limit") int limit);

    /**
     * ⭐ Trang THU HAI tro di: lay tiep tu moc {@code (cursorAt, cursorId)} tro xuong.
     *
     * <p>
     * <b>Vi sao la mot method RIENG chu khong nhet cursor vao cau tren bang
     * {@code (:cursorAt IS NULL OR (created_at, id) < (:cursorAt, :cursorId))}.</b> Cach do
     * gon hon mot nua so dong va no CHAY DUNG - nhung no giet index. Postgres phai chuan bi
     * MOT ke hoach dung cho ca hai truong hop, ma mot dieu kien co {@code OR} voi {@code NULL}
     * thi khong quy ve duoc {@code Index Cond}; no roi xuong {@code Filter}, tuc la doc dong
     * len roi moi loai. Toan bo cai gia phai tra o V6 mat sach.
     *
     * <p>
     * Hai cau, moi cau mot ke hoach toi uu. Do la mot lan lap lai co ly do.
     *
     * <p>
     * <b>Vi sao dieu kien cursor nam TRONG tung nhanh UNION ALL chu khong o ngoai.</b> Dat o
     * ngoai thi moi nhanh van doc {@code LIMIT :limit} dong DAU TIEN cua no - tuc la nhung
     * dong nguoi dung DA XEM - roi vong ngoai loc het di va tra ve rong. Trang 2 se trong,
     * trang 3 cung trong, mai mai. Dieu kien phai di VAO cho no chan duoc som nhat.
     *
     * <p>
     * ⚠️ {@code (created_at, id) < (:cursorAt, :cursorId)} la so sanh BO GIA TRI, khong phai
     * {@code created_at < :cursorAt AND id < :cursorId}. Hai cai KHAC HAN nhau: cai thu hai
     * doi ca hai cot cung nho hon, nen no vut mat moi dong co {@code created_at} nho hon
     * nhung {@code id} lon hon - va do la phan lon du lieu.
     *
     * <p>
     * 📌 {@code Instant} bind duoc o day la nho Hibernate doi kieu ho. Driver pgjdbc TU CHOI
     * {@code Instant} ({@code "Can't infer the SQL type"}) - da gap that trong
     * {@code TransactionPagingIT} khi dung {@code JdbcClient} thang, phai doi sang
     * {@code OffsetDateTime}. Cung mot kieu du lieu, hai tang, hai ket qua khac nhau.
     */
    @Query(value = """
            SELECT * FROM (
                (SELECT id, from_wallet_id, to_wallet_id, amount, type, status,
                        idempotency_key, created_at
                 FROM transactions
                 WHERE from_wallet_id = :id
                   AND (created_at, id) < (:cursorAt, :cursorId)
                 ORDER BY created_at DESC, id DESC LIMIT :limit)
                UNION ALL
                (SELECT id, from_wallet_id, to_wallet_id, amount, type, status,
                        idempotency_key, created_at
                 FROM transactions
                 WHERE to_wallet_id = :id
                   AND (created_at, id) < (:cursorAt, :cursorId)
                 ORDER BY created_at DESC, id DESC LIMIT :limit)
            ) x
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Transaction> findAfterCursor(@Param("id") long walletId,
            @Param("cursorAt") Instant cursorAt, @Param("cursorId") long cursorId,
            @Param("limit") int limit);
}
