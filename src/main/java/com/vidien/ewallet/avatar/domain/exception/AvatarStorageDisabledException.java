package com.vidien.ewallet.avatar.domain.exception;

/** Kho anh chua duoc cau hinh - xem app.storage.* trong application.properties. */
public class AvatarStorageDisabledException extends RuntimeException {
    public AvatarStorageDisabledException() {
        super("Tính năng ảnh đại diện chưa được bật trên máy chủ này");
    }
}
