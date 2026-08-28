package com.vidien.ewallet.audit.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.audit.domain.AuditLog;

/**
 * Khong co method nao tu viet - {@code save()} cua JpaRepository la du.
 *
 * <p>
 * Truoc day file nay tu ghep cau INSERT va tu goi {@code objectMapper.writeValueAsString()}
 * cho cot JSONB, kem mot ghi chu ve viec phai ep {@code ::jsonb} vi JDBC khong co kieu tuong
 * ung. {@code @JdbcTypeCode(SqlTypes.JSON)} tren entity lo ca hai chieu.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
