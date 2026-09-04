package com.vidien.ewallet.avatar.domain.exception;

/** Tep gui len khong dung la mot anh dung duoc. */
public class InvalidAvatarException extends RuntimeException {
    public InvalidAvatarException(String message) {
        super(message);
    }
}
