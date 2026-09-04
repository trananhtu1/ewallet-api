/*
 * FEATURE  : Sao kê theo tháng
 * VAI TRÒ  : Biên HTTP — ví lấy từ TOKEN, không từ tham số.
 */
package com.vidien.ewallet.statement.api;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.vidien.ewallet.statement.api.dto.MonthlyStatement;
import com.vidien.ewallet.statement.infra.StatementRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/**
 * Sao ke theo thang cua CHINH nguoi goi.
 *
 * <p>
 * ⚠️ Khong nhan {@code walletId} tu URL. Sao ke la buc tranh day du nhat ve thoi quen
 * chi tieu cua mot nguoi - de no nhan id tu tham so la mo mot lo BOLA nghiem trong hon
 * ca xem so du.
 *
 * <p>
 * KHONG dat {@code @Validated} tren lop - xem ghi chu o {@code ReconciliationController}:
 * no bien mot tham so sai thanh HTTP 500.
 */
@RestController
@RequestMapping("/api/statement")
public class StatementController {

    private final StatementRepository statements;

    public StatementController(StatementRepository statements) {
        this.statements = statements;
    }

    @GetMapping
    public List<MonthlyStatement> theoThang(
            @RequestParam(defaultValue = "6") @Positive @Max(24) int months,
            @AuthenticationPrincipal Jwt jwt) {

        return statements.theoThang(jwt.getClaim("walletId"), months);
    }
}
