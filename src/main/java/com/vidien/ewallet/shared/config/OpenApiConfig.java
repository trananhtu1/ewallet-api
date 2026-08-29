/*
 * FEATURE  : OpenAPI / Swagger UI
 * VAI TRÒ  : Mô tả API + nút Authorize. Thiếu nút đó thì trang chỉ để nhìn.
 * LIÊN QUAN: SecurityConfig (permitAll cho /swagger-ui/**) · mọi Controller
 */
package com.vidien.ewallet.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Mo ta API cho Swagger UI.
 *
 * <p>
 * <b>Vi sao co file nay thay vi de springdoc tu sinh het.</b> Tu sinh cho ra mot trang liet ke
 * duong dan va kieu du lieu - dung, va gan nhu vo dung: no khong noi duoc <b>ma loi nao co
 * nghia gi</b>, va quan trong hon, no khong cho nguoi doc <b>bam thu</b> mot endpoint doi
 * token. Thieu nut Authorize thi moi lan thu deu tra 401 va trang do chi de nhin.
 *
 * <p>
 * {@code SecurityScheme} duoi day chinh la nut do.
 *
 * <p>
 * 📌 Va day la mot trong hai ly do lam Swagger o giai doan nay. Ly do kia: <b>mot trang mo ra
 * duoc trong buoi phong van</b>. Ke ve mot API thi nguoi nghe phai tin; mo ra bam thu mot lenh
 * chuyen tien thi khong phai tin nua.
 */
@Configuration
public class OpenApiConfig {

    /** Ten dung o ca hai cho: khai bao scheme va tham chieu toi no. Lech mot chu la nut
        Authorize hien ra nhung khong gan token vao dau ca. */
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI ewalletOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ewallet-api")
                        .version("v1")
                        .description("""
                                Vi dien tu - API chuyen tien, so cai va nhat ky.

                                **Tien luon la CHUOI trong JSON.** `"amount": "1000.00"` chu
                                khong phai `1000.00`. Ly do da do duoc: JavaScript doc
                                `12345678901234567.89` thanh `12345678901234568` - mat 0.11
                                dong o mot so, va mat nhieu hon o so lon hon. Guiw len cung
                                phai la chuoi.

                                **Phan trang bang cursor, khong phai page/offset.** `nextCursor`
                                la chuoi doc khong ra: cam nguyen xi gui lai, dung tu che.

                                **Bam Authorize truoc.** Dang nhap qua `POST /api/auth/login`,
                                lay `token`, dan vao. Moi `/api/wallets/**` va
                                `/api/transfers/**` deu doi token.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Dan gia tri truong `token` tu response cua /api/auth/login. "
                                + "KHONG can go chu 'Bearer' - Swagger tu them.")));
    }
}
