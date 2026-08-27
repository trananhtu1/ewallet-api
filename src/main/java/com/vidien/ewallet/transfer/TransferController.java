package com.vidien.ewallet.transfer;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.wallet.Wallet;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Tra ve vi NGUON sau khi chuyen - thu nguoi goi muon biet nhat la "toi con bao nhieu".
     *
     * <p>
     * @Valid = cau lenh "doc so di". Thieu chu nay thi moi annotation trong TransferRequest im
     * lang khong chay, va amount = -999999 di thang xuong duoi.
     *
     * <p>
     * @RequestBody = "doc tu than request". Thieu chu nay thi Spring di tim du lieu o query
     * string, va moi field se la null.
     */
    @PostMapping
    public Wallet create(@Valid @RequestBody TransferRequest request) {
        return transferService.transfer(request.fromWalletId(), request.toWalletId(),
                request.amount());
    }
}
