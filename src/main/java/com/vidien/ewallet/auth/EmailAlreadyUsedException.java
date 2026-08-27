package com.vidien.ewallet.auth;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String email) {
        super("Email da duoc dung: " + email);
    }
}
