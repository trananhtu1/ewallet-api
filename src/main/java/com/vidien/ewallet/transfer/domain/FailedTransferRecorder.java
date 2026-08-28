package com.vidien.ewallet.transfer.domain;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.vidien.ewallet.transaction.domain.Transaction;
import com.vidien.ewallet.transaction.domain.TransactionStatus;
import com.vidien.ewallet.transaction.domain.TransactionType;
import com.vidien.ewallet.transaction.infra.TransactionRepository;
import com.vidien.ewallet.wallet.infra.WalletRepository;

/**
 * Ghi mot lan chuyen tien THAT BAI vao so cai, trong MOT TRANSACTION RIENG.
 *
 * <p>
 * ⭐ VI SAO PHAI LA MOT CLASS RIENG, khong phai mot method trong TransferService:
 *
 * <p>
 * @Transactional hoat dong bang PROXY. Spring boc mot lop quanh bean, va chinh LOP BOC do doc
 * annotation roi mo/dong transaction. Mot method trong TransferService goi thang sang method
 * khac cung class (this.recordFailed()) thi loi goi KHONG RA KHOI OBJECT, khong di qua proxy,
 * va annotation khong ai doc.
 *
 * <p>
 * Da do that truoc khi tach: goi API chuyen qua so du, API tra 409 dung, so cai co
 * <b>0 dong FAILED</b>. Khong loi, khong canh bao, khong log - dong INSERT chay trong chinh
 * transaction dang bi rollback nen bien mat cung. Sau khi tach ra file nay: 1 dong.
 *
 * <p>
 * 📌 Day la LAN THU TU cua cung mot luat: khai bao mot thu ma thieu cai kich hoat no thi no
 * im lang khong lam gi. Truoc do: @Valid thieu o tham so · @RestControllerAdvice viet nham
 * thanh @RestController · flyway-database-postgresql thieu starter-flyway.
 */
@Service
public class FailedTransferRecorder {

    private static final Logger log = LoggerFactory.getLogger(FailedTransferRecorder.class);

    private final TransactionRepository transactions;

    public FailedTransferRecorder(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /**
     * REQUIRES_NEW = tam dung transaction dang chay, mo mot transaction HOAN TOAN MOI tren
     * mot ket noi khac, commit no, roi tra transaction cu lai. Dong ghi o day song sot ke ca
     * khi transaction ben ngoai rollback - do chinh la muc dich.
     *
     * <p>
     * ⚠️ CAI GIA CUA REQUIRES_NEW, va no dat hon ve ngoai: transaction moi chay tren MOT KET
     * NOI KHAC, nen no khong nhin thay gi cua transaction ngoai va co the bi chinh transaction
     * ngoai CHAN. Cu the o day: transaction ngoai dang giu khoa tren hai dong vi, con lenh
     * INSERT nay co khoa ngoai tro vao dung hai dong do -> Postgres phai kiem khoa ngoai ->
     * doi khoa. Ma transaction ngoai thi dang doi ham nay tra ve, trong Java.
     *
     * <p>
     * Do la mot vong cho ma <b>Postgres KHONG phat hien duoc</b>: no khong phai deadlock cua
     * database (transaction ngoai khong cho khoa nao ca, no cho mot loi goi Java), nen
     * deadlock_timeout khong cuu. Do that: treo den khi lock_timeout cat, SQLSTATE 55P03.
     *
     * <p>
     * Cach chua nam o WalletRepository.lockById: doi FOR UPDATE thanh FOR NO KEY UPDATE.
     * Xem giai thich day du o do.
     *
     * <p>
     * Hai ket noi cung luc cho MOT request, nen Hikari pool size 5 gio la gioi han 2 request
     * chuyen tien that bai chay song song - da tinh den, chua phai van de o quy mo nay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long fromWalletId, long toWalletId, BigDecimal amount) {
        transactions.save(Transaction.builder()
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .amount(amount)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.FAILED)
                .build());
        log.info("Ghi so cai: chuyen {} tu vi {} sang vi {} THAT BAI", amount, fromWalletId,
                toWalletId);
    }
}
