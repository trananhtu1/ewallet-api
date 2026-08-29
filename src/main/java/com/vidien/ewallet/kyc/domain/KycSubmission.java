/*
 * FEATURE  : KYC — nộp ảnh CCCD (giả lập)
 * VAI TRÒ  : Entity hồ sơ. Kiểu lai: cột thường cho thứ hay lọc, JSONB cho metadata ảnh.
 * LIÊN QUAN: KycStatus · KycSubmissionRepository · V7
 */
package com.vidien.ewallet.kyc.domain;

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
 * Ho so KYC - kieu lai giua cot thuong va JSONB.
 *
 * <p>
 * Ranh gioi da <b>do</b> o buoi 12, khong thiet ke lai: {@code user_id} / {@code status} /
 * {@code created_at} la <b>cot thuong</b> vi moi cau truy van deu loc theo chung va chung can
 * index; {@code metadata} la <b>JSONB</b> vi hinh dang cua no se doi.
 *
 * <p>
 * ⚠️ <b>Khong co truong nao chua anh.</b> Bang nay giu metadata, khong giu byte. Anh that thi
 * len object storage; ban gia lap nay khong luu anh o dau ca, chi con {@code sha256} trong
 * metadata de doi chieu. Nhet vai MB vao mot dong Postgres thi moi lan doc dong do la keo ca
 * ngan ay qua ket noi, backup phinh theo so anh, va cache cua Postgres bi day boi thu khong ai
 * truy van.
 */
@Entity
@Table(name = "kyc_submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class KycSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status;

    /**
     * Metadata anh: {@code mime}, {@code bytes}, {@code sha256}, {@code originalName}, va con
     * doi ve sau.
     *
     * <p>
     * {@code @JdbcTypeCode(SqlTypes.JSON)} - cach cua Hibernate 6 tro di, khong con phai cai
     * thu vien ngoai nao. {@code columnDefinition = "jsonb"} de {@code ddl-auto=validate} doi
     * chieu duoc voi V7: doi mot ben ma quen ben kia thi app <b>tu choi khoi dong</b> kem ten
     * cot sai, thay vi chay roi ghi nham kieu.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
