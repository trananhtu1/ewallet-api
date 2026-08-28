package com.vidien.ewallet.transfer;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.transaction.TransactionRepository;
import com.vidien.ewallet.wallet.InsufficientFundsException;
import com.vidien.ewallet.wallet.NotYourWalletException;
import com.vidien.ewallet.wallet.SameWalletTransferException;
import com.vidien.ewallet.wallet.Wallet;
import com.vidien.ewallet.wallet.WalletNotFoundException;
import com.vidien.ewallet.wallet.WalletRepository;

/**
 * Trai tim project: tru o vi nay, cong o vi kia, ghi mot dong so cai - TAT CA hoac KHONG GI CA.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository wallets;
    private final TransactionRepository transactions;

    public TransferService(WalletRepository wallets, TransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Transactional
    public Wallet transfer(long fromWalletId, long toWalletId, BigDecimal amount,
            long callerWalletId) {
        // ⭐ CHO BIT LO BOLA, va day la ca thuan nhat cua no: vi NGUON den tu BODY.
        // Khong co dong nay thi bat ky ai dang ky xong deu goi duoc
        //     POST /api/transfers {"fromWalletId": 1, "toWalletId": <vi cua toi>, ...}
        // va rut sach vi so 1. Endpoint da khoa bang .authenticated(), SQL chay dung,
        // transaction dung, deadlock da chong - va tien van bi lay mat.
        //
        // Kiem TRUOC MOI THU: truoc ca lockById. Khoa mot dong vi cua nguoi khac dù chi trong
        // vai mili giay cung la de mot nguoi la chan duong giao dich that cua ho.
        if (fromWalletId != callerWalletId) {
            throw new NotYourWalletException(fromWalletId, callerWalletId);
        }

        // toWalletId thi KHONG kiem - chuyen tien cho nguoi khac chinh la muc dich cua ham nay.

        // Chan tu dau: chuyen cho chinh minh khong phai giao dich, va neu de lot thi doan duoi
        // se tru roi cong lai tren CUNG mot dong -> so du khong doi nhung so cai co them mot
        // dong vo nghia.
        if (fromWalletId == toWalletId) {
            throw new SameWalletTransferException(fromWalletId);
        }

        // === CHONG DEADLOCK ===
        // Khoa hai vi theo THU TU ID TANG DAN, khong theo thu tu nguon-dich.
        // Neu khoa theo nguon-dich: lenh 1->2 khoa vi 1 roi doi vi 2, lenh 2->1 khoa vi 2 roi
        // doi vi 1 -> hai ben doi nhau vinh vien. Postgres phat hien va giet mot ben (SQLSTATE
        // 40P01), nguoi dung ben do an 500 khong ro nguyen nhan.
        // Khoa theo id tang dan thi ca hai lenh deu doi vi 1 truoc: mot ben doi, roi chay, xong.
        long firstLock = Math.min(fromWalletId, toWalletId);
        long secondLock = Math.max(fromWalletId, toWalletId);
        wallets.lockById(firstLock);
        wallets.lockById(secondLock);

        // Tu day tro di hai dong da bi khoa: doc so du roi quyet dinh la an toan, khong ai
        // chen ngang duoc. Day chinh la pessimistic locking.
        Wallet from = wallets.findById(fromWalletId)
                .orElseThrow(() -> new WalletNotFoundException(fromWalletId));
        Wallet to = wallets.findById(toWalletId)
                .orElseThrow(() -> new WalletNotFoundException(toWalletId));

        if (from.balance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromWalletId, from.balance(), amount);
        }

        // Dieu kien balance >= :amount van nam trong cau UPDATE - lop chan thu hai.
        int subtracted = wallets.subtractFromBalance(fromWalletId, amount);
        if (subtracted == 0) {
            throw new InsufficientFundsException(fromWalletId, from.balance(), amount);
        }
        wallets.addToBalance(to.id(), amount);

        transactions.insertTransfer(fromWalletId, toWalletId, amount);

        log.info("Chuyen {} tu vi {} sang vi {}", amount, fromWalletId, toWalletId);

        return wallets.findById(fromWalletId)
                .orElseThrow(() -> new WalletNotFoundException(fromWalletId));
    }
}
