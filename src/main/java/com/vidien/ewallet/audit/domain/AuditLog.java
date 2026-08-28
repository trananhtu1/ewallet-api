package com.vidien.ewallet.audit.domain;

import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nhat ky kiem toan. Khac han bang {@code transactions}:
 *
 * <pre>
 * transactions = SO CAI.   Chi ghi thu LAM DUOC.
 * audit_log    = NHAT KY.  Ghi ca thu KHONG lam duoc va thu bi TU CHOI.
 * </pre>
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditEvent event;

    /**
     * Ai gay ra. NULL duoc: dang nhap that bai thi chua biet la ai.
     *
     * <p>
     * ⚠️ De {@code Long} chu KHONG phai {@code @ManyToOne User} - va day la lan thu hai cung
     * mot ly do, nhung o day no manh hon: nhat ky kiem toan phai <b>song lau hon</b> thu no
     * ghi ve. Xoa mot nguoi dung ma keo theo mat sach dau vet cua nguoi do la dieu te nhat mot
     * nhat ky co the lam. Bang nay cung khong co khoa ngoai o database - xem V3.
     */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "at", nullable = false, updatable = false, insertable = false)
    private Instant at;

    /**
     * ⭐ Cot JSONB, anh xa thang sang {@code Map}.
     *
     * <p>
     * {@code @JdbcTypeCode(SqlTypes.JSON)} la cach cua Hibernate 6 - truoc day phai cai them
     * mot thu vien rieng (hibernate-types) va khai bao {@code @TypeDef}. Gio no co san.
     *
     * <p>
     * Luc dung JdbcClient thi cho nay phai tu {@code objectMapper.writeValueAsString()} roi
     * ep {@code ::jsonb} trong cau SQL. JPA lo ca hai chieu.
     *
     * <p>
     * Moi loai su kien mot hinh dang khac nhau - do la ly do no la JSONB chu khong phai mot
     * bang rong toan cot NULL. Ket luan nay do duoc o bai SQL vs NoSQL (buoi 12).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
