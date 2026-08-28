package com.vidien.ewallet.audit.domain;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cua duy nhat de ghi nhat ky kiem toan. Moi service goi vao day, khong goi thang AuditWriter.
 *
 * <p>
 * Lop nay <b>khong</b> co @Transactional - do la ca diem cua no. Ranh gioi transaction nam o
 * AuditWriter, con try/catch nam o day, NGOAI ranh gioi do. Dat nguoc lai thi lenh COMMIT vo
 * ma khong ai bat duoc - giai thich day du o AuditWriter.
 */
@Service
public class Auditor {

    private static final Logger log = LoggerFactory.getLogger(Auditor.class);

    private final AuditWriter writer;

    public Auditor(AuditWriter writer) {
        this.writer = writer;
    }

    /**
     * ⚖️ QUYET DINH: ghi nhat ky HONG thi KHONG lam hong viec cua nguoi dung.
     *
     * <p>
     * Day la mot danh doi that, va huong nguoc lai cung co ly. Ngan hang co quy dinh kiem toan
     * nghiem ngat thuong chon nguoc: <b>khong ghi duoc thi khong lam</b> - tha tu choi giao
     * dich con hon co mot giao dich khong ai truy duoc.
     *
     * <p>
     * Chon huong nay vi hai le:
     * <ul>
     * <li>So cai (bang `transactions`) MOI la ban ghi chinh thuc ve tien, va no nam trong cung
     * transaction voi lenh chuyen. `audit_log` la lop bo sung - mat mot dong o day thi van truy
     * duoc dong tien, chi mat phan ngu canh.
     * <li>Tu choi mot lenh chuyen tien HOP LE cua khach vi mot bang ghi log truc trac la doi
     * mot van de nho lay mot van de to hon.
     * </ul>
     *
     * <p>
     * Doi lai phai co dieu kien: loi ghi nhat ky <b>phai het len</b> o muc ERROR kem stack
     * trace. Nuot mot loi ma khong noi gi la cach bien mot su co thanh mot bi an.
     *
     * <p>
     * Ngay nao du an vao dien phai tuan thu that thi doi cho nay thanh nem ra - mot dong code,
     * khong phai mot cuoc viet lai. Do la ly do try/catch nam o day chu khong rai khap noi.
     */
    public void record(AuditEvent event, Long actorId, Map<String, Object> payload) {
        try {
            writer.write(event, actorId, payload);
        } catch (RuntimeException e) {
            log.error("KHONG GHI DUOC NHAT KY {} cua actor {} - viec cua nguoi dung van chay",
                    event, actorId, e);
        }
    }
}
