package com.vidien.ewallet.transaction.domain.exception;

/**
 * Cursor khach gui len khong doc duoc.
 *
 * <p>
 * Cursor la chuoi DO SERVER PHAT RA, khach chi viec gui tra lai nguyen ven. Nen mot cursor
 * hong nghia la mot trong ba chuyen: khach tu bia ra, khach cat/sua chuoi, hoac ai do dang
 * do API. Ca ba deu la loi cua BEN GOI - 400, khong phai 500.
 *
 * <p>
 * ⚠️ Vi sao KHONG lang le coi cursor hong nhu "khong co cursor" roi tra ve trang dau: vi nhu
 * the la tra ve du lieu DUNG DINH DANG nhung SAI Y NGHIA. Nguoi dung bam "trang sau" va nhan
 * lai trang dau, khong mot thong bao nao. Do la kieu hong tu khoi phuc - thu kho debug nhat.
 */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor, Throwable cause) {
        super("Cursor khong doc duoc: " + cursor, cause);
    }
}
