package com.vidien.ewallet.audit.domain;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.audit.infra.AuditLogRepository;

/**
 * Chi lam mot viec: mo mot transaction MOI va ghi mot dong.
 *
 * <p>
 * ⭐ VI SAO TACH KHOI Auditor - va day la mot cai bay tinh vi hon ca bay self-invocation:
 *
 * <p>
 * Ban dau viet try/catch NGAY TRONG method co {@code @Transactional(REQUIRES_NEW)}. Doc thi
 * hop ly. Nhung khong chay duoc, va ly do nam o thu tu:
 *
 * <pre>
 *   proxy mo transaction
 *     -> INSERT that bai, Postgres danh dau transaction la ABORTED
 *     -> catch cua minh nuot exception, method tra ve binh thuong
 *   proxy thay khong co exception nao thoat ra  ->  goi COMMIT
 *     -> COMMIT tren mot transaction da aborted  ->  no lai vo, o mot cho khac
 * </pre>
 *
 * Bat loi thi phai bat NGOAI ranh gioi transaction, tuc la o mot bean khac goi vao day.
 *
 * <p>
 * 📌 Luat: <b>try/catch dat trong mot method @Transactional khong cuu duoc loi cua DATABASE.</b>
 * Thu vo la lenh COMMIT, ma COMMIT thi nam ngoai than method.
 */
@Service
public class AuditWriter {

    private final AuditLogRepository auditLog;

    public AuditWriter(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event, Long actorId, Map<String, Object> payload) {
        auditLog.save(AuditLog.builder()
                .event(event)
                .actorId(actorId)
                .payload(payload)
                .build());
    }
}
