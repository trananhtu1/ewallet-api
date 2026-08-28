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
 * Ban dau viet try/catch NGAY TRONG method co @Transactional(REQUIRES_NEW). Doc thi hop ly:
 * "ghi hong thi bo qua". Nhung khong chay duoc, va ly do nam o thu tu:
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
 * 📌 Luat rut ra: <b>try/catch dat trong mot method @Transactional khong cuu duoc loi cua
 * DATABASE.</b> No cuu duoc loi logic cua minh, con loi lam hong transaction thi phai bat
 * o ben ngoai - vi thu vo la lenh COMMIT, ma COMMIT thi nam ngoai than method.
 */
@Service
public class AuditWriter {

    private final AuditLogRepository auditLog;

    public AuditWriter(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event, Long actorId, Map<String, Object> payload) {
        auditLog.insert(event, actorId, payload);
    }
}
