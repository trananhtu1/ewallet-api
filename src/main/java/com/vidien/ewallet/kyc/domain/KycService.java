/*
 * FEATURE  : KYC — nộp ảnh CCCD (giả lập)
 * VAI TRÒ  : Tầng nghiệp vụ: nộp hồ sơ, chặn trùng, ghi nhật ký mã băm.
 * LIÊN QUAN: DocumentImage · KycSubmissionRepository · V7 (unique index có điều kiện)
 */
package com.vidien.ewallet.kyc.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.audit.domain.AuditEvent;
import com.vidien.ewallet.audit.domain.Auditor;
import com.vidien.ewallet.kyc.api.dto.KycView;
import com.vidien.ewallet.kyc.domain.exception.KycAlreadyPendingException;
import com.vidien.ewallet.kyc.infra.KycSubmissionRepository;

/**
 * Nop va xem ho so KYC (gia lap).
 *
 * <p>
 * "Gia lap" nghia la <b>khong co anh nao duoc luu lai</b>: he thong doc byte, kiem, rut
 * metadata, roi vut byte di. Cai con lai la mot dong trong {@code kyc_submissions} va mot ma
 * bam. Anh that thi len object storage, va do la mot dich vu khac.
 */
@Service
public class KycService {

    private final KycSubmissionRepository submissions;
    private final Auditor audit;

    public KycService(KycSubmissionRepository submissions, Auditor audit) {
        this.submissions = submissions;
        this.audit = audit;
    }

    /**
     * Nop mot ho so moi: hai anh, mat truoc va mat sau.
     *
     * <p>
     * ⭐ <b>HAI hang rao cho luat "moi nguoi mot ho so dang cho", va day khong phai thua.</b>
     *
     * <ol>
     *   <li>Cau {@code findByUserIdAndStatus} duoi day - de tra ve <b>thong bao doc duoc</b>.</li>
     *   <li>Unique index co dieu kien {@code ux_kyc_one_pending_per_user} trong V7 - de
     *       <b>that su chan</b>.</li>
     * </ol>
     *
     * Cau kiem mot minh no <b>khong du</b>: hai request nop cung luc thi ca hai deu doc thay
     * "chua co dong nao" va ca hai deu ghi. Dung ho loi voi khoa chong lap o buoi 28/08 - va
     * lan do da do duoc: 10 request dong thoi, neu chi kiem bang cau if thi ra 10 dong.
     *
     * <p>
     * Index bat duoc thi Hibernate nem {@code DataIntegrityViolationException}, va cho nay doi
     * no ve dung exception nghiep vu - de nguoi goi nhan 409 co ma ro rang thay vi 500.
     */
    @Transactional
    public KycView nop(long userId, DocumentImage matTruoc, DocumentImage matSau) {
        submissions.findByUserIdAndStatus(userId, KycStatus.PENDING).ifPresent(daCo -> {
            throw new KycAlreadyPendingException(userId);
        });

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("front", matTruoc.toMetadata("front"));
        metadata.put("back", matSau.toMetadata("back"));
        metadata.put("totalBytes", matTruoc.bytes() + matSau.bytes());

        KycSubmission moi = KycSubmission.builder()
                .userId(userId)
                .status(KycStatus.PENDING)
                .metadata(metadata)
                .build();

        try {
            // saveAndFlush chu khong save: can lenh INSERT chay NGAY de bat duoc vi pham unique
            // index o day. Voi save() thi Hibernate hoan lenh toi luc commit - tuc la sau khi
            // ra khoi khoi try nay - va exception se noi len duoi dang mot loi khong ai boc.
            submissions.saveAndFlush(moi);
        } catch (DataIntegrityViolationException e) {
            throw new KycAlreadyPendingException(userId);
        }

        // Nhat ky ghi CA HAI ma bam. So cai nghiep vu khong cho cho, ma day dung la thu bo phan
        // chong gian lan can: hai nguoi khac nhau nop cung mot anh thi hai dong nay trung nhau.
        audit.record(AuditEvent.KYC_SUBMITTED, userId, Map.of(
                "frontSha256", matTruoc.sha256(),
                "backSha256", matSau.sha256(),
                "totalBytes", matTruoc.bytes() + matSau.bytes()));

        return KycView.of(moi);
    }

    /** Lich su nop cua chinh minh, moi nhat truoc. */
    @Transactional(readOnly = true)
    public List<KycView> cuaToi(long userId) {
        return submissions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(KycView::of)
                .toList();
    }
}
