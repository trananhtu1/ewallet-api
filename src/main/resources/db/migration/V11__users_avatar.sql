-- V11: ảnh đại diện.
--
-- ⭐ Lưu ĐƯỜNG DẪN, không lưu ảnh. Đây là quyết định đáng ghi lại vì cách ngược
-- lại (cột BYTEA chứa ảnh) chạy được và rất cám dỗ vì nó đơn giản hơn.
--
-- Ba lý do không nhét ảnh vào Postgres:
--
--   1. Neon gói free có 500MB. 200 avatar 2MB là hết sạch — và hết theo kiểu
--      cả ứng dụng ngừng ghi được, không riêng gì avatar.
--
--   2. Mỗi bản sao lưu kéo theo toàn bộ ảnh. Một database 500MB toàn ảnh thì
--      khôi phục lâu gấp nhiều lần một database 5MB toàn số liệu.
--
--   3. Mỗi lần hiển thị ảnh là một request đi qua JVM 176MB thay vì đi thẳng
--      tới một kho được thiết kế cho việc đó.
--
-- Đổi lại: mất tính nguyên tử. Xoá dòng user thì ảnh trên kho KHÔNG tự mất —
-- database và kho là hai hệ thống, không có transaction chung. Chấp nhận, vì
-- hậu quả là vài file rác chứ không phải sai dữ liệu.

ALTER TABLE users
  ADD COLUMN avatar_key        VARCHAR(200),
  ADD COLUMN avatar_updated_at TIMESTAMPTZ;

-- Lưu KHOÁ trong kho (`avatars/8.jpg`), không lưu URL đầy đủ.
--
-- URL đầy đủ mang theo tên miền của nhà cung cấp. Đổi từ Supabase sang S3 thật
-- là phải UPDATE mọi dòng. Lưu khoá thì đổi nhà cung cấp chỉ là đổi một biến
-- môi trường — cùng một lý do đã dùng cho REDIS_URL.
COMMENT ON COLUMN users.avatar_key IS
  'Khoá của ảnh trong object storage, ví dụ avatars/8.jpg. NULL = chưa có ảnh.';
