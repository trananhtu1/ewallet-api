package com.vidien.ewallet.user.domain;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nguoi dung.
 *
 * <p>
 * ⚠️ KHONG co {@code @OneToOne Wallet wallet} du quan he do co that trong database. Ly do
 * giong ben Wallet: doc mot user thi khong bao gio can keo theo ca cai vi, va dat quan he o
 * day la mo duong cho mot cau truy van an nau minh o tang view.
 *
 * <p>
 * 💣 {@code passwordHash} khong co {@code @JsonIgnore} nua - vi entity nay KHONG BAO GIO ra
 * khoi API. Cai gi ra ngoai thi di qua {@code AuthResponse}. Do la cach chac chan hon mot
 * annotation: khong phai nho dan nhan, chi can khong tra entity ra ngoai.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;
}
