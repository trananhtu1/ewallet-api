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

    // ⭐ HAI CAU @Query LICH SU DA CHUYEN SANG TransactionSearchRepository (29/08, buoi 16).
    //
    // Chung la findFirstPage() va findAfterCursor(), viet o buoi 14. Bo loc lam so to hop len
    // 32, va viet 32 cau @Query thi khong ai bao tri noi - nen ca hai duong doc gio dung mot
    // ham sinh SQL. Khi khong co bo loc nao, ham do sinh ra DUNG cau cu.
    //
    // Bang chung khong phai loi hua: TransactionPagingIT (5 test cua buoi 14) khong sua mot
    // dong nao va van xanh - no dang kiem duong khong-loc di qua cai cai dat moi.
    //
    // Ly do giu SQL viet tay van y nguyen tu 27/08: JPQL khong co UNION, va Specification /
    // Criteria API sinh ra dung cau OR da do la cham (3.57ms so voi 0.32ms).
}
