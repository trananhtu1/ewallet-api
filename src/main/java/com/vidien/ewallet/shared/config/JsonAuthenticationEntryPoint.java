package com.vidien.ewallet.shared.config;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.vidien.ewallet.shared.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.vidien.ewallet.shared.exception.GlobalExceptionHandler;

/**
 * Vi sao phai co file nay: GlobalExceptionHandler KHONG voi toi day duoc.
 *
 * <p>
 * Token hong bi tu choi trong CHUOI FILTER, tuc la truoc khi Spring MVC vao cuoc. Ma
 * @RestControllerAdvice chi bat exception THOAT RA TU MOT CONTROLLER. Khong co controller
 * nao chay thi khong co gi de no bat - da hoc o bai @ControllerAdvice, "Bay A".
 *
 * <p>
 * Hau qua neu bo qua: token rac tra 401 voi BODY RONG, trong khi moi loi khac cua API deu co
 * hinh dang { timestamp, status, code, message, path }. Frontend dang doc err.code se nhan
 * duoc undefined va roi vao nhanh "loi khong xac dinh".
 *
 * <p>
 * Nen 401 phai duoc viet BANG TAY vao response, ngay tai tang filter.
 *
 * <p>
 * Chu y import: Boot 4.1 dung JACKSON 3, package la tools.jackson.databind chu khong con la
 * com.fasterxml.jackson.databind. Rieng ANNOTATION (@JsonInclude, @JsonFormat, @JsonIgnore)
 * thi van o com.fasterxml.jackson.annotation. Hai package khac nhau trong cung mot thu vien -
 * chep code tren mang ve gan nhu chac chan sai cho nay.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // KHONG dua authException.getMessage() ra ngoai: no co the noi ro token sai cho nao,
        // va do la thong tin giup nguoi do token chinh sua cho lan sau.
        ErrorResponse body = ErrorResponse.of(401, "UNAUTHENTICATED",
                "Bạn cần đăng nhập để thực hiện thao tác này", request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
