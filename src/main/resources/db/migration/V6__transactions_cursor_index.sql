-- V6: them `id` vao hai index lich su giao dich, de phan trang bang CURSOR chay duoc.
--
-- V1 danh hai index nay:
--
--   (from_wallet_id, created_at DESC)
--   (to_wallet_id,   created_at DESC)
--
-- Chung phuc vu rat tot cau hoi "20 giao dich moi nhat cua vi X". Nhung phan trang bang
-- cursor hoi mot cau khac: "20 giao dich tiep theo, tinh tu dong (created_at, id) nay tro
-- xuong". Cau do can mot THU TU TOAN PHAN - tuc la khong duoc phep co hai dong bang nhau.
--
-- `created_at` mot minh KHONG du. Postgres luu TIMESTAMPTZ toi micro-giay, va hai giao dich
-- ghi trong cung mot micro-giay la chuyen hoan toan xay ra duoc duoi tai. Khi do:
--
--   - Thu tu giua chung do PLANNER quyet dinh, va no co quyen doi giua hai lan chay.
--   - Cursor tro toi mot trong hai dong khong noi duoc "dong con lai da xem hay chua".
--
-- Ket qua la mot dong bi BO QUA hoac hien RA HAI LAN, tuy huong gio. Voi mot bang tien thi
-- do la loi khong the giai thich voi nguoi dung.
--
-- `id` la BIGSERIAL, khong bao gio trung, nen (created_at, id) la thu tu toan phan.
--
-- 📌 DO DUOC, khong phai suy doan. Tren 200.000 dong, cursor voi index CU van chay
-- (0.302ms) nhung EXPLAIN cho thay Postgres chi dung duoc `created_at` lam Index Cond, con
-- ROW(created_at, id) roi xuong `Filter`, va phai them mot node `Incremental Sort`. Voi index
-- co `id`: ca ROW(created_at, id) vao thang Index Cond, khong Filter, khong Sort, 0.221ms va
-- doc 21 dong thay vi 42.
--
-- So sanh voi kieu OFFSET tren cung bo du lieu: 35.051ms, doc 100.020 dong, 2456 buffers.
--
-- CREATE INDEX (khong CONCURRENTLY): bang nay dang nho va Flyway chay trong transaction.
-- CONCURRENTLY khong chay duoc trong transaction, va khi bang lon that thi doi la mot buoc
-- van hanh rieng chu khong phai mot dong migration.

CREATE INDEX ix_transactions_from_created_id
    ON transactions (from_wallet_id, created_at DESC, id DESC);

CREATE INDEX ix_transactions_to_created_id
    ON transactions (to_wallet_id, created_at DESC, id DESC);

-- Bo hai index cu: chung la TIEN TO cua hai index moi, nen moi cau query cu van chay dung
-- va nhanh nhu truoc. Giu ca bon la tra tien ghi hai lan cho moi INSERT ma khong duoc gi.
DROP INDEX ix_transactions_from_created;
DROP INDEX ix_transactions_to_created;
