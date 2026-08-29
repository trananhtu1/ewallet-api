/*
 * FEATURE  : Cache số dư ví (Redis)
 * VAI TRÒ  : Sự kiện 'số dư vừa đổi'. Mang id chứ không mang cả Wallet.
 * LIÊN QUAN: WalletCacheEvictor · WalletService.deposit · TransferService.transfer
 * BÀI GIẢNG: java-learn/java/07-cache/BUOI-15-REDIS-CACHE.md
 */
package com.vidien.ewallet.wallet.domain;

/**
 * "So du cua vi nay vua doi." Phat ra trong transaction, xu ly SAU KHI commit.
 *
 * <p>
 * Vi sao khong goi thang {@code walletCache.evict(id)} trong service cho nhanh: vi luc do
 * transaction <b>chua commit</b>. Xoa cache som thi mot request khac doc vi trong khoang giua
 * se khong thay cache, doc thang database, va doc duoc <b>gia tri CU</b> - roi nap lai cache
 * bang chinh con so cu do. Transaction commit xong, database dung, ma cache sai, va no sai
 * <b>cho toi khi het TTL</b> chu khong tu sua.
 *
 * <p>
 * Cua so do hep - vai mili-giay. Nhung tren mot he thong that thi no mo suot ngay, va cai gia
 * la mot nguoi dung nhin thay so du cu sau khi vua chuyen tien. Ho se bam chuyen lai.
 *
 * <p>
 * 📌 Day cung la ly do event nay mang {@code walletId} chu khong mang ca {@code Wallet}. Doi
 * tuong bat duoc luc phat co the da cu hon luc listener chay; con {@code id} thi khong bao gio
 * cu. Su kien nen mang <b>cai gi vua doi</b>, dung mang <b>gia tri moi</b>.
 *
 * @param walletId vi vua bi thay doi so du
 */
public record WalletChangedEvent(long walletId) {
}
