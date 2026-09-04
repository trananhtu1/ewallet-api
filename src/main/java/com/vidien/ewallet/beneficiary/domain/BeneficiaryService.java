/*
 * FEATURE  : Người nhận đã lưu
 * VAI TRÒ  : Luật nghiệp vụ của sổ địa chỉ — ai được xem, ai được xoá.
 * LIÊN QUAN: BeneficiaryRepository · WalletRepository · UserRepository
 */
package com.vidien.ewallet.beneficiary.domain;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.beneficiary.domain.exception.BeneficiaryAlreadySavedException;
import com.vidien.ewallet.beneficiary.domain.exception.BeneficiaryNotFoundException;
import com.vidien.ewallet.beneficiary.infra.BeneficiaryRepository;
import com.vidien.ewallet.user.infra.UserRepository;
import com.vidien.ewallet.wallet.domain.exception.SameWalletTransferException;
import com.vidien.ewallet.wallet.domain.exception.WalletNotFoundException;
import com.vidien.ewallet.wallet.infra.WalletRepository;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaries;
    private final WalletRepository wallets;
    private final UserRepository users;

    public BeneficiaryService(BeneficiaryRepository beneficiaries, WalletRepository wallets,
            UserRepository users) {
        this.beneficiaries = beneficiaries;
        this.wallets = wallets;
        this.users = users;
    }

    /**
     * So dia chi cua CHINH nguoi goi.
     *
     * <p>
     * Khong nhan {@code ownerId} tu tham so URL - lay tu token o tang controller.
     * Nhan tu tham so la dung lai lo BOLA duoi mot cai ten khac: doi mot chu so
     * tren thanh dia chi la doc duoc so dia chi cua nguoi khac.
     *
     * <p>
     * ⭐ Goi ban SINH SAN, khong goi ban JOIN FETCH - va do la ket luan sau khi
     * DO, nguoc voi du dinh ban dau. Do duoc dung mot cau SQL cho ca danh sach:
     * {@code getWallet().getId()} doc khoa ngoai co san trong dong, khong cham
     * database. Xem ghi chu day du o
     * {@link BeneficiaryRepository#findAllByOwnerIdOrderByCreatedAtDesc}.
     */
    @Transactional(readOnly = true)
    public List<Beneficiary> cuaToi(long ownerId) {
        return beneficiaries.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    /**
     * Luu mot vi vao so dia chi.
     *
     * <p>
     * Ba dieu kien, va thu tu kiem la co y - re nhat truoc:
     * <ol>
     * <li>Khong tu luu chinh minh
     * <li>Vi phai ton tai
     * <li>Chua co trong so
     * </ol>
     */
    @Transactional
    public Beneficiary luu(long ownerId, long ownerWalletId, long walletId, String label) {
        if (walletId == ownerWalletId) {
            // Dung lai ngoai le cua man chuyen tien: cung mot y nghia, va nguoi
            // dung khong can phan biet hai cho phat sinh no.
            throw new SameWalletTransferException(walletId);
        }

        // Kiem vi ton tai TRUOC khi cham vao bang beneficiaries. De database bao
        // loi khoa ngoai thi ma tra ve se la mot loi rang buoc chung chung, va
        // thong bao noi ve ten constraint chu khong noi ve cai vi.
        if (wallets.findById(walletId).isEmpty()) {
            throw new WalletNotFoundException(walletId);
        }

        if (beneficiaries.existsByOwnerIdAndWalletId(ownerId, walletId)) {
            throw new BeneficiaryAlreadySavedException();
        }

        // getReferenceById chu khong findById: chi can KHOA NGOAI de ghi xuong,
        // khong can doc noi dung hai dong do len. Hibernate dung mot proxy chi
        // mang id - bot hai cau SELECT cho moi lan luu.
        return beneficiaries.save(Beneficiary.builder()
                .owner(users.getReferenceById(ownerId))
                .wallet(wallets.getReferenceById(walletId))
                .label(label.trim())
                .build());
    }

    /**
     * Xoa mot nguoi nhan.
     *
     * <p>
     * ⚠️ Tim bang {@code findByIdAndOwnerId}, khong phai {@code findById} roi
     * kiem chu so huu sau. Hai cach cho ket qua giong nhau khi viet dung, nhung
     * cach nay <b>khong the viet sai</b>: khong co mot dong nao o giua de ai do
     * xoa nham lan sau.
     *
     * <p>
     * Va nem "khong tim thay" chu khong phai "khong co quyen" khi no thuoc ve
     * nguoi khac: 403 la mot cau tra loi CO - no xac nhan dong do ton tai.
     */
    @Transactional
    public void xoa(long ownerId, long beneficiaryId) {
        var b = beneficiaries.findByIdAndOwnerId(beneficiaryId, ownerId)
                .orElseThrow(BeneficiaryNotFoundException::new);
        beneficiaries.delete(b);
    }
}
