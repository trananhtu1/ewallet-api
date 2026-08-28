package com.vidien.ewallet.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.vidien.ewallet.audit.domain.AuditEvent;
import com.vidien.ewallet.audit.domain.Auditor;
import com.vidien.ewallet.transaction.domain.Transaction;
import com.vidien.ewallet.transaction.infra.TransactionRepository;
import com.vidien.ewallet.wallet.domain.Wallet;
import com.vidien.ewallet.wallet.domain.exception.InsufficientFundsException;
import com.vidien.ewallet.wallet.domain.exception.NotYourWalletException;
import com.vidien.ewallet.wallet.domain.exception.SameWalletTransferException;
import com.vidien.ewallet.wallet.infra.WalletRepository;

/**
 * Unit test cho luat nghiep vu cua chuyen tien - KHONG cham database.
 *
 * <p>
 * Cai gi test o day va cai gi phai de cho integration test, day la ranh gioi:
 *
 * <ul>
 * <li><b>O day:</b> nhung quyet dinh service tu dua ra - tu choi vi khong phai cua minh, tu
 * choi chuyen cho chinh minh, ghi nhat ky truoc khi nem. Mockito du, va chay trong mili giay.
 * <li><b>KHONG o day:</b> deadlock, idempotency duoi tai, {@code SUM(balance)}. Nhung thu do
 * do DATABASE quyet dinh, va mot mock thi luon tra ve dung cai minh bao no tra ve - test bang
 * mock chi chung minh minh biet minh viet gi, khong chung minh he thong dung.
 * Xem {@code TransferConcurrencyIT}.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private WalletRepository wallets;
    @Mock
    private TransactionRepository transactions;
    @Mock
    private FailedTransferRecorder failedTransfers;
    @Mock
    private Auditor audit;

    @InjectMocks
    private TransferService transferService;

    private static Wallet wallet(long id, String balance) {
        return Wallet.builder()
                .id(id)
                .userId(id)
                .balance(new BigDecimal(balance))
                .version(0)
                .build();
    }

    @Nested
    @DisplayName("chan truoc khi cham database")
    class ChanSom {

        /**
         * ⭐ Lo BOLA. Test nay canh cau hoi "anh duoc dung vao cai gi", khac han
         * "anh la ai" ma endpoint da lo.
         */
        @Test
        @DisplayName("vi nguon khong phai cua nguoi goi -> nem, va KHONG khoa dong nao")
        void tuChoiViKhongPhaiCuaMinh() {
            assertThatThrownBy(() -> transferService.transfer(1L, 3L, new BigDecimal("100.00"),
                    3L, null))
                    .isInstanceOf(NotYourWalletException.class);

            // Quan trong khong kem viec nem: KHONG duoc khoa dong nao ca. Khoa mot cai vi cua
            // nguoi khac du chi vai mili giay cung la chan duong giao dich that cua ho.
            verify(wallets, never()).lockById(anyLong());
        }

        @Test
        @DisplayName("vi nguon khong phai cua minh -> ghi nhat ky ACCESS_DENIED")
        void ghiNhatKyKhiBiTuChoiQuyen() {
            assertThatThrownBy(() -> transferService.transfer(1L, 3L, new BigDecimal("100.00"),
                    3L, null))
                    .isInstanceOf(NotYourWalletException.class);

            // So cai KHONG co dong nao cho viec nay - dung, vi khong dong tien nao chay.
            // Nen neu khong ghi nhat ky thi khong con dau vet nao.
            verify(audit).record(org.mockito.ArgumentMatchers.eq(AuditEvent.ACCESS_DENIED),
                    org.mockito.ArgumentMatchers.eq(3L), any());
            verify(transactions, never()).save(any());
        }

        @Test
        @DisplayName("chuyen cho chinh minh -> nem SameWallet, khong khoa")
        void tuChoiChuyenChoChinhMinh() {
            assertThatThrownBy(() -> transferService.transfer(3L, 3L, new BigDecimal("100.00"),
                    3L, null))
                    .isInstanceOf(SameWalletTransferException.class);

            verify(wallets, never()).lockById(anyLong());
        }
    }

    @Nested
    @DisplayName("khoa dong")
    class KhoaDong {

        /**
         * ⭐ Test canh giu cach chong deadlock. Deadlock THAT thi phai co database moi tai
         * hien duoc (xem IT), nhung LUAT sinh ra no thi test duoc o day: khoa theo id tang
         * dan, khong theo thu tu nguon-dich.
         */
        @Test
        @DisplayName("khoa theo ID TANG DAN, khong theo thu tu nguon-dich")
        void khoaTheoThuTuIdTangDan() {
            when(wallets.findById(5L)).thenReturn(Optional.of(wallet(5, "1000.00")));
            when(wallets.findById(2L)).thenReturn(Optional.of(wallet(2, "0.00")));
            when(wallets.subtractFromBalance(anyLong(), any())).thenReturn(1);

            // Chuyen tu vi 5 sang vi 2 - nguon LON hon dich.
            transferService.transfer(5L, 2L, new BigDecimal("100.00"), 5L, null);

            // Van phai khoa vi 2 TRUOC. Doi thu tu la tai hien lai dung cai deadlock
            // 6/12 request tra 500 hom 27/08.
            var order = org.mockito.Mockito.inOrder(wallets);
            order.verify(wallets).lockById(2L);
            order.verify(wallets).lockById(5L);
        }
    }

    @Nested
    @DisplayName("khong du tien")
    class KhongDuTien {

        @Test
        @DisplayName("nem InsufficientFunds, ghi so cai FAILED va ghi nhat ky kem so du luc do")
        void ghiVetKhiKhongDuTien() {
            when(wallets.findById(3L)).thenReturn(Optional.of(wallet(3, "50.00")));
            when(wallets.findById(4L)).thenReturn(Optional.of(wallet(4, "0.00")));

            assertThatThrownBy(() -> transferService.transfer(3L, 4L,
                    new BigDecimal("100.00"), 3L, null))
                    .isInstanceOf(InsufficientFundsException.class);

            // Hai dau vet khac nhau cho cung mot su kien, va deu can:
            // so cai ghi "co mot lan chuyen that bai", nhat ky ghi LY DO va SO DU LUC DO.
            verify(failedTransfers).record(3L, 4L, new BigDecimal("100.00"));
            verify(audit).record(org.mockito.ArgumentMatchers.eq(AuditEvent.TRANSFER_REJECTED),
                    org.mockito.ArgumentMatchers.eq(3L), any());

            // Va tuyet doi khong duoc tru tien.
            verify(wallets, never()).subtractFromBalance(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("chong lap")
    class ChongLap {

        /**
         * ⭐ Vi tri cua phep kiem la thu dang canh: SAU khi khoa dong, khong phai truoc.
         * Truoc thi con khe ho giua luc kiem va luc ghi.
         */
        @Test
        @DisplayName("khoa da dung roi -> tra ve vi hien tai, KHONG chuyen lan nua")
        void boQuaLenhLap() {
            when(wallets.findById(3L)).thenReturn(Optional.of(wallet(3, "500.00")));
            when(wallets.findById(4L)).thenReturn(Optional.of(wallet(4, "0.00")));
            when(transactions.findByFromWalletIdAndIdempotencyKey(3L, "da-bam-roi"))
                    .thenReturn(Optional.of(Transaction.builder().id(1L).build()));

            Wallet ketQua = transferService.transfer(3L, 4L, new BigDecimal("100.00"), 3L,
                    "da-bam-roi");

            assertThat(ketQua.getBalance()).isEqualByComparingTo("500.00");
            verify(wallets, never()).subtractFromBalance(anyLong(), any());
            verify(transactions, never()).save(any());
        }

        @Test
        @DisplayName("kiem khoa SAU khi da khoa hai dong vi")
        void kiemKhoaSauKhiKhoaDong() {
            when(wallets.findById(3L)).thenReturn(Optional.of(wallet(3, "500.00")));
            when(wallets.findById(4L)).thenReturn(Optional.of(wallet(4, "0.00")));
            when(transactions.findByFromWalletIdAndIdempotencyKey(3L, "k"))
                    .thenReturn(Optional.empty());
            when(wallets.subtractFromBalance(anyLong(), any())).thenReturn(1);

            transferService.transfer(3L, 4L, new BigDecimal("100.00"), 3L, "k");

            var order = org.mockito.Mockito.inOrder(wallets, transactions);
            order.verify(wallets).lockById(3L);
            order.verify(wallets).lockById(4L);
            order.verify(transactions).findByFromWalletIdAndIdempotencyKey(3L, "k");
        }
    }
}
