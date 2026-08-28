package com.vidien.ewallet.auth.domain.exception;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email da duoc dung: " + email);
    }
}
