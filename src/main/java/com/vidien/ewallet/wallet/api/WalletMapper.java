package com.vidien.ewallet.wallet.api;

import java.util.List;
import org.mapstruct.Mapper;
import com.vidien.ewallet.wallet.api.dto.WalletResponse;
import com.vidien.ewallet.wallet.domain.Wallet;

/**
 * Doi entity sang DTO tra ve.
 *
 * <p>
 * MapStruct SINH CODE luc bien dich - mo {@code target/generated-sources} ra doc duoc ca ham
 * {@code new WalletResponse(wallet.getId(), ...)}. Khac han cac mapper dua tren reflection:
 * chung chay luc runtime va go sai ten truong thi im lang tra ve null.
 *
 * <p>
 * ⚠️ Va o pom da bat {@code unmappedTargetPolicy=ERROR}: them mot truong vao
 * {@code WalletResponse} ma quen map thi <b>BUILD DO</b>, khong phai mot truong null lot ra
 * frontend roi vai ngay sau moi co nguoi hoi. Do la ca ly do chon MapStruct.
 *
 * <p>
 * {@code componentModel=spring} dat o pom (khong lap lai o day): mapper sinh ra la mot
 * {@code @Component}, tiem vao service nhu moi bean khac.
 */
@Mapper
public interface WalletMapper {

    WalletResponse toResponse(Wallet wallet);

    List<WalletResponse> toResponses(List<Wallet> wallets);
}
