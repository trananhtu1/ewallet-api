package com.vidien.ewallet.kyc.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.kyc.domain.KycStatus;
import com.vidien.ewallet.kyc.domain.KycSubmission;

@Repository
public interface KycSubmissionRepository extends JpaRepository<KycSubmission, Long> {

    /** Ho so dang cho cua mot nguoi. Unique index co dieu kien (V7) dam bao toi da mot dong. */
    Optional<KycSubmission> findByUserIdAndStatus(long userId, KycStatus status);

    /** Lich su nop cua mot nguoi, moi nhat truoc. Khop index ix_kyc_user_created. */
    List<KycSubmission> findByUserIdOrderByCreatedAtDesc(long userId);
}
