/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Truy vấn sổ địa chỉ — và chỗ N+1 được chặn.
 * LIÊN QUAN: Beneficiary · BeneficiaryService
 */
package com.vidien.ewallet.beneficiary.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.vidien.ewallet.beneficiary.domain.Beneficiary;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    /**
     * ⭐⭐ DAY LA CHO TUONG SE CO N+1, VA DO DUOC THI KHONG CO. Ghi lai ca hai.
     *
     * <p>
     * Ke hoach ban dau: bang nay co hai khoa ngoai, {@code wallet} la
     * {@code @ManyToOne} lazy, va {@code BeneficiaryResponse} doc
     * {@code b.getWallet().getId()} - dung cong thuc sach giao khoa cua N+1. Viet
     * san mot ban {@code JOIN FETCH} de chua.
     *
     * <p>
     * <b>Do that thi khong co gi de chua.</b> Bat {@code show-sql}, goi endpoint voi
     * 7 nguoi nhan, dem duoc <b>dung mot cau</b>:
     *
     * <pre>
     * select b1_0.id, b1_0.created_at, b1_0.label, b1_0.owner_user_id, b1_0.wallet_id
     * from beneficiaries b1_0 where b1_0.owner_user_id=? order by b1_0.created_at desc
     * </pre>
     *
     * Khong mot cau nao di lay vi. Ban {@code JOIN FETCH} do duoc cung dung mot cau
     * va cung ngan thoi gian (164ms so 165ms) - no khong sua gi ca, vi khong co gi
     * hong.
     *
     * <p>
     * ⭐ <b>Ly do</b>: {@code getId()} tren mot proxy lazy KHONG cham database. Khoa
     * ngoai {@code wallet_id} da nam san trong dong vua doc len, nen Hibernate tra
     * id ra tu do. Proxy chi thuc su di lay du lieu khi ai do doc mot truong
     * <b>khac id</b> - {@code getBalance()}, {@code getCreatedAt()}...
     *
     * <p>
     * 📌 Bai hoc that, va no khac cai da dinh viet: <b>N+1 khong den tu viec CO mot
     * quan he lazy, no den tu viec CHAM VAO quan he do.</b> Mot DTO chi lay id thi
     * an toan; them mot truong cua vi vao la N+1 xuat hien ngay, va khong co gi bao.
     *
     * <p>
     * Nen duong that dung cau don gian duoi day - {@code JOIN FETCH} o day la mot
     * cau JOIN tra tien cho thu khong ai dung. Thu canh giu la
     * {@code NPlusOneIT}: no dem so cau va do neu con so roi khoi 1.
     */
    List<Beneficiary> findAllByOwnerIdOrderByCreatedAtDesc(long ownerId);

    /**
     * Ban {@code JOIN FETCH} - GIU LAI nhung KHONG dung o duong that.
     *
     * <p>
     * Giu vi hai ly do. Mot: {@code NPlusOneIT} can no de doi chieu, va mot bai hoc
     * ve N+1 khong con cai sai de so thi chi la mot loi khuyen. Hai: ngay nao
     * response can them mot truong cua vi thi day la cau phai doi sang, va luc do
     * {@code NPlusOneIT} se do de noi ra dieu do.
     *
     * <p>
     * ⚠️ {@code JOIN FETCH} chu khong phai {@code JOIN} thuong: {@code JOIN} thuong
     * chi dung de LOC, du lieu lay ve khong duoc nhet vao doi tuong, va lazy loading
     * van no ra nhu cu. Hai cau doc gan giong het nhau.
     */
    @Query("""
            SELECT b FROM Beneficiary b
            JOIN FETCH b.wallet
            WHERE b.owner.id = :ownerId
            ORDER BY b.createdAt DESC
            """)
    List<Beneficiary> findAllByOwnerWithWallet(@Param("ownerId") long ownerId);

    boolean existsByOwnerIdAndWalletId(long ownerId, long walletId);

    Optional<Beneficiary> findByIdAndOwnerId(long id, long ownerId);
}
