package com.vidien.ewallet.audit;

import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditLogRepository {

    private final JdbcClient db;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    /**
     * Ghi mot dong nhat ky.
     *
     * <p>
     * ::jsonb trong cau SQL chu khong phai de driver tu doan: JDBC khong co kieu nao tuong ung
     * voi jsonb, nen truyen mot chuoi vao thang se bi Postgres tu choi voi
     * "column payload is of type jsonb but expression is of type character varying".
     * Ep kieu tuong minh o day la cach ngan nhat, va no cung lam cau SQL noi ro y dinh.
     *
     * <p>
     * Chuyen Map thanh chuoi JSON bang chinh ObjectMapper cua ung dung - khong noi chuoi bang
     * tay. Noi tay thi mot email co dau nhay kep la sinh ra JSON hong, va do la SQL injection
     * o dang khac: du lieu tro thanh cu phap.
     */
    public void insert(AuditEvent event, Long actorId, Map<String, Object> payload) {
        db.sql("""
                INSERT INTO audit_log (event, actor_id, payload)
                VALUES (:event, :actorId, :payload::jsonb)
                """)
                .param("event", event.name())
                .param("actorId", actorId)
                .param("payload", objectMapper.writeValueAsString(payload))
                .update();
    }
}
