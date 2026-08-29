package com.vidien.ewallet.kyc.domain.exception;

/** Anh gui len khong dung dinh dang cho phep, hoac rong. 400 - loi cua ben goi. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
