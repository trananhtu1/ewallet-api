/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Truy vấn nhật ký đối soát.
 * LIÊN QUAN: ReconciliationRun · ReconciliationService · V8 (ux_recon_one_per_day)
 */
package com.vidien.ewallet.reconciliation.infra;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.reconciliation.domain.ReconciliationRun;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

    /** Ket qua cua mot ngay. Unique index trong V8 dam bao toi da mot dong. */
    Optional<ReconciliationRun> findByBusinessDate(LocalDate businessDate);
}
