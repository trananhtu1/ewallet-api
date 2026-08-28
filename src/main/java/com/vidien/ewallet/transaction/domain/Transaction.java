package com.vidien.ewallet.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
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
 * So cai: moi dong tien di qua he thong deu de lai mot dong o day.
 *
 * <p>
 * Luu y hai chu "transaction" trong project mang hai nghia khac han. Bang nay la GIAO DICH
 * TIEN TE, thu khach hang nhin thay. Con {@code @Transactional} la GIAO DICH DATABASE.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL duoc: nap tien khong co vi nguon, tien di tu ngoai he thong vao. */
    @Column(name = "from_wallet_id")
    private Long fromWalletId;

    @Column(name = "to_wallet_id")
    private Long toWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * ⚠️ {@code EnumType.STRING}, KHONG BAO GIO {@code ORDINAL}.
     *
     * <p>
     * ORDINAL luu thu tu khai bao (0, 1, 2...). Them mot gia tri vao GIUA enum la moi dong cu
     * trong database doi y nghia - im lang, khong loi nao bao, va khong ai phat hien cho toi
     * luc doi soat. Voi bang so cai thi do la tham hoa.
     *
     * <p>
     * STRING cung khop voi {@code CHECK (type IN ('DEPOSIT','TRANSFER'))} o V1: go sai mot
     * chu la database tu choi ngay.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    /** Chong bam chuyen tien hai lan. Pham vi duy nhat la (vi nguon, khoa) - xem V2. */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
