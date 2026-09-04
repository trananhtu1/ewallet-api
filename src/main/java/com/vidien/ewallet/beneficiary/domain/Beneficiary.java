/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Một dòng trong sổ địa chỉ của người dùng.
 * LIÊN QUAN: BeneficiaryRepository · BeneficiaryService · V9
 */
package com.vidien.ewallet.beneficiary.domain;

import java.time.Instant;
import com.vidien.ewallet.user.domain.User;
import com.vidien.ewallet.wallet.domain.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mot nguoi nhan da luu.
 *
 * <p>
 * ⭐ Day la entity DAU TIEN trong project dung {@code @ManyToOne} that su, va do
 * la mot lua chon co y - nguoc voi {@link Wallet}, noi {@code userId} co tinh de
 * lam mot {@code Long} tran.
 *
 * <p>
 * Vi sao khac: {@code Wallet} nam tren duong chuyen tien, va duong do khong bao
 * gio can biet ten chu vi. Con man hinh nay <b>luon luon</b> hien ten chu vi -
 * do la ly do no ton tai. Quan he doi tuong o day khong phai gánh nang, no la
 * cai dang can.
 *
 * <p>
 * ⚠️ Nhung {@code FetchType.LAZY} tren ca hai, va do moi la cho de sai. Mac dinh
 * cua {@code @ManyToOne} la <b>EAGER</b>, nghia la moi lan doc mot nguoi nhan la
 * Hibernate tu di lay ca User lan Wallet - ke ca khi khong ai dung toi. Voi mot
 * danh sach 20 dong thi do la 40 cau truy van khong ai xin.
 *
 * <p>
 * Dat LAZY moi la mot nua cau chuyen. Nua con lai: doc danh sach ma <b>co</b>
 * dung toi quan he thi phai lay chung TRONG CUNG mot cau - xem
 * {@code BeneficiaryRepository.findAllByOwnerWithWallet}.
 */
@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    /** Ten do nguoi luu tu dat. Khong phai ten that cua chu vi - xem ghi chu o V9. */
    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
