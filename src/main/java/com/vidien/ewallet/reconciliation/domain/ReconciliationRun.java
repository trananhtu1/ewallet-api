/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Một dòng kết quả đối soát. Mỗi ngày nghiệp vụ đúng một dòng.
 * LIÊN QUAN: ReconciliationService · ReconciliationRunRepository · V8
 */
package com.vidien.ewallet.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * Ket qua mot luot doi soat.
 *
 * <p>
 * {@code businessDate} la NGAY NGHIEP VU, khong phai thoi diem chay. Job chay luc 00:05 sang
 * ngay 30 de doi soat ngay 29 thi {@code businessDate} la 29 - nguoi ke toan hoi "ngay 29 co
 * khop khong", khong hoi "luc 00:05 co khop khong".
 *
 * <p>
 * ⚠️ Ba con so {@code walletTotal} / {@code ledgerTotal} / {@code drift} deu la
 * {@code NUMERIC(19,2)}, khop voi {@code wallets.balance}. Dung {@code double} o day thi chinh
 * cai job di tim sai so lai la thu tao ra sai so - va no se bao dong gia o so le thu 15 sau dau
 * phay.
 */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "wallet_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal walletTotal;

    @Column(name = "ledger_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal drift;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ran_at", nullable = false, insertable = false, updatable = false)
    private Instant ranAt;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;
}
