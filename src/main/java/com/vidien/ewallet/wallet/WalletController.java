package com.vidien.ewallet.wallet;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletRepository wallets;

    public WalletController(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> findOne(@PathVariable long id, HttpServletRequest request) {
        Optional<Wallet> found = wallets.findById(id);

        if (found.isPresent()) {
            return ResponseEntity.ok(found.get());
        }

        ErrorResponse body = ErrorResponse.of(404, "WALLET_NOT_FOUND", "Không tìm thấy ví",
                request.getRequestURI());
        return ResponseEntity.status(404).body(body);
    }
}
