package com.vidien.ewallet.wallet.domain;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.vidien.ewallet.transaction.infra.TransactionRepository;
import com.vidien.ewallet.transaction.domain.Transaction;
import com.vidien.ewallet.transaction.domain.TransactionStatus;
import com.vidien.ewallet.transaction.domain.TransactionType;
import com.vidien.ewallet.transaction.api.dto.TransactionCursor;
import com.vidien.ewallet.transaction.api.dto.TransactionPage;
import com.vidien.ewallet.transaction.api.dto.TransactionView;
import com.vidien.ewallet.wallet.domain.exception.WalletNotFoundException;
import com.vidien.ewallet.wallet.infra.WalletRepository;

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
     * ⭐ CHO BIT LO BOLA. Moi {id} den tu nguoi goi deu la DE NGHI, khong phai su that.
     *
     * <p>
     * Khoa endpoint bang .authenticated() chi tra loi "anh la ai". Ham nay tra loi cau con
     * lai: "anh duoc dung vao cai gi". Thieu no thi bat ky ai dang ky xong - mat 5 giay -
     * deu doc duoc so du va ghi duoc vao vi cua nguoi khac, va moi cau SQL van chay dung.
     *
     * <p>
     * 📌 Nem WalletNotFoundException chu KHONG phai mot loi "khong co quyen" rieng, va day
     * la co y: hai cau tra loi phai KHONG PHAN BIET DUOC tu ben ngoai. Neu vi cua nguoi khac
     * tra 403 con vi khong ton tai tra 404 thi ke tan cong chi can quet 1..N la biet chinh
     * xac he thong co bao nhieu vi va id nao dang duoc dung - mot cong cu do so nguoi dung
     * mien phi. Cung ly do voi INVALID_CREDENTIALS gop chung "sai email" va "sai mat khau".
     *
     * <p>
     * So sanh theo VI chu khong theo NGUOI DUNG: rang buoc UNIQUE tren wallets.user_id o V1
     * bao dam moi nguoi dung dung mot vi, nen hai cach tuong duong hom nay. Ngay nao cho
     * phep mot nguoi nhieu vi thi cho nay phai doi thanh cau hoi "vi nay co thuoc ve userId
     * khong" - va no la mot cau truy van, khong con la mot phep so sanh.
     */
    /**
     * Tao vi cho mot nguoi dung vua dang ky. So du bat dau tu 0.
     *
     * <p>
     * Dung {@code save()} chu khong phai native INSERT: JPA tra ve luon doi tuong da co id,
     * khong phai di mot vong SELECT nua. {@code version} do {@code @Version} tu dat, con
     * {@code created_at} do DEFAULT cua database - nen o day chi phai dien dung hai truong.
     */
    @Transactional
    public Wallet createForUser(long userId) {
        return wallets.save(Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .build());
    }

    private void requireOwn(long walletId, long callerWalletId) {
        if (walletId != callerWalletId) {
            throw new WalletNotFoundException(walletId);
        }
    }

    /**
     * readOnly = true noi voi database rang giao dich nay khong ghi gi, Postgres bo qua duoc
     * mot phan viec chuan bi ghi.
     */
    @Transactional(readOnly = true)
    public Wallet findById(long walletId, long callerWalletId) {
        requireOwn(walletId, callerWalletId);

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
    public Wallet deposit(long walletId, BigDecimal amount, long callerWalletId) {
        // Kiem quyen TRUOC khi cham vao database. Dat sau lenh UPDATE thi tien da vao vi
        // nguoi khac roi moi nem exception - @Transactional se rollback that, nhung day la
        // dua vao mot co che khac de cuu mot loi logic. Chan o dau la re nhat va chac nhat.
        requireOwn(walletId, callerWalletId);

        // update() tra ve so dong bi sua. 0 = khong co vi nao mang id do, va biet duoc dieu do
        // ma khong ton them mot cau SELECT.
        int updated = wallets.addToBalance(walletId, amount);
        if (updated == 0) {
            throw new WalletNotFoundException(walletId);
        }

        transactions.save(Transaction.builder()
                .fromWalletId(null)   // nap tien khong co vi nguon - tien tu ngoai he thong vao
                .toWalletId(walletId)
                .amount(amount)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .build());

        return wallets.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /**
     * Lich su giao dich cua mot vi, phan trang bang CURSOR.
     *
     * <p>
     * Kiem vi ton tai TRUOC: khong co buoc nay thi vi khong ton tai se tra ve mang rong 200,
     * y het mot vi that su chua co giao dich nao. Hai chuyen khac han nhau ma client khong
     * phan biet duoc.
     *
     * <p>
     * <b>Xin limit + 1 dong, tra ve limit.</b> Dong thu {@code limit + 1} khong bao gio den
     * tay client - no chi de tra loi cau <i>"con nut Xem them khong"</i>. Cach kia la
     * {@code COUNT(*)} cua ca vi, va tren mot vi 200.000 giao dich thi rieng con so do dat hon
     * ca 20 dong du lieu that, lai phai tinh lai o MOI trang.
     *
     * <p>
     * ⚠️ Moc cua trang sau lay tu dong CUOI CUNG DUOC TRA VE, khong phai dong thu
     * {@code limit + 1}. Lay nham dong thua thi dong do bi nhay qua - no chua bao gio hien
     * ra man hinh ma cursor da di qua no roi.
     *
     * @param cursor moc client cam ve tu lan goi truoc; {@code null} nghia la trang dau
     */
    @Transactional(readOnly = true)
    public TransactionPage history(long walletId, int limit, String cursor, long callerWalletId) {
        requireOwn(walletId, callerWalletId);

        if (wallets.findById(walletId).isEmpty()) {
            throw new WalletNotFoundException(walletId);
        }

        // Xin thua MOT dong de biet con trang sau hay khong. Dong nay bi vut ngay duoi day.
        int xin = limit + 1;

        List<Transaction> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = transactions.findFirstPage(walletId, xin);
        } else {
            TransactionCursor moc = TransactionCursor.decode(cursor);
            rows = transactions.findAfterCursor(walletId, moc.createdAt(), moc.id(), xin);
        }

        if (rows.isEmpty()) {
            return TransactionPage.empty();
        }

        boolean conNua = rows.size() > limit;
        List<Transaction> trang = conNua ? rows.subList(0, limit) : rows;

        String mocSau = conNua
                ? TransactionCursor.of(trang.get(trang.size() - 1)).encode()
                : null;

        return new TransactionPage(
                trang.stream().map(tx -> TransactionView.of(tx, walletId)).toList(),
                mocSau,
                conNua);
    }
}
