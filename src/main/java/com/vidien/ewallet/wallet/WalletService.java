package com.vidien.ewallet.wallet;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.vidien.ewallet.transaction.TransactionRepository;
import com.vidien.ewallet.transaction.TransactionView;

/**
 * Tang nghiep vu, va la noi dat ranh gioi transaction.
 *
 * <p>
 * Chia viec: controller lo HTTP, repository lo SQL, service lo "mot viec nghiep vu gom nhung
 * buoc nao". Service khong duoc biet toi ResponseEntity hay HttpServletRequest; controller
 * khong duoc biet toi chu SELECT nao.
 *
 * <p>
 * @Transactional dat o day chu khong phai o controller (giu transaction mo suot ca luc Jackson
 * dung JSON, phi mot ket noi trong pool chi co 5 cai) va cung khong phai o repository (qua hep,
 * moi method mot transaction rieng thi khong goi duoc hai lenh ghi vao chung mot don vi).
 *
 * <p>
 * Bay can nho: @Transactional chi co tac dung khi loi goi DI QUA PROXY cua Spring. Mot method
 * trong class nay goi thang sang method khac cung class (this.x()) thi khong qua proxy, va
 * annotation bi bo qua HOAN TOAN - khong loi, khong canh bao.
 */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final TransactionRepository transactions;

    public WalletService(WalletRepository wallets, TransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    /**
     * readOnly = true noi voi database rang giao dich nay khong ghi gi, Postgres bo qua duoc
     * mot phan viec chuan bi ghi.
     */
    @Transactional(readOnly = true)
    public Wallet findById(long walletId) {
        return wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /**
     * Nap tien = HAI lenh ghi, va chung phai cung song hoac cung chet.
     *
     * <p>
     * Neu UPDATE xong ma INSERT chet thi tien da vao vi nhung khong co dong nao ghi lai: so du
     * va lich su giao dich lech nhau vinh vien, khong ai biet cho toi luc khach hang khieu nai.
     * @Transactional goi ca hai vao mot don vi - chet o dau cung quay ve nhu chua tung xay ra.
     */
    @Transactional
    public Wallet deposit(long walletId, BigDecimal amount) {
        // update() tra ve so dong bi sua. 0 = khong co vi nao mang id do, va biet duoc dieu do
        // ma khong ton them mot cau SELECT.
        int updated = wallets.addToBalance(walletId, amount);
        if (updated == 0) {
            throw new WalletNotFoundException(walletId);
        }

        transactions.insertDeposit(walletId, amount);

        return wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /**
     * Lich su giao dich cua mot vi.
     *
     * <p>
     * Kiem vi ton tai TRUOC: khong co buoc nay thi vi khong ton tai se tra ve mang rong 200,
     * y het mot vi that su chua co giao dich nao. Hai chuyen khac han nhau ma client khong
     * phan biet duoc.
     */
    @Transactional(readOnly = true)
    public List<TransactionView> history(long walletId, int limit) {
        if (wallets.findById(walletId).isEmpty()) {
            throw new WalletNotFoundException(walletId);
        }

        return transactions.findByWalletId(walletId, limit).stream()
                .map(tx -> TransactionView.of(tx, walletId))
                .toList();
    }
}
