/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Truy vấn nhật ký đối soát.
 * LIÊN QUAN: ReconciliationRun · ReconciliationService · V8 (ux_recon_one_per_day)
 */
package com.vidien.ewallet.reconciliation.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.reconciliation.domain.ReconciliationRun;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    /** Ket qua cua mot ngay. Unique index trong V8 dam bao toi da mot dong. */
    Optional<ReconciliationRun> findByBusinessDate(LocalDate businessDate);

    /**
     * Vai lan chay gan nhat, ngay moi truoc.
     *
     * <p>
     * Dung {@code Limit} cua Spring Data 3.2+ thay vi {@code Pageable}: o day chi
     * can "lay N dong dau", khong can dem tong so dong. {@code Pageable} keo theo
     * mot cau {@code COUNT(*)} tren ca bang ma khong ai doc toi ket qua.
     */
    List<ReconciliationRun> findAllByOrderByBusinessDateDesc(Limit limit);

    /** Ten ngan cho cho goi. */
    default List<ReconciliationRun> findRecent(int limit) {
        return findAllByOrderByBusinessDateDesc(Limit.of(limit));
    }
}
