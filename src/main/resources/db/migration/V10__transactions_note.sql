-- V10: lời nhắn kèm giao dịch.
--
-- Người chuyển gõ một dòng ngắn ("tiền nhà tháng 9"), cả hai bên đều thấy nó
-- trong lịch sử. MoMo, ZaloPay và mọi app ngân hàng đều có.
--
-- ⭐ NULLABLE, và đó là quyết định chứ không phải lười:
--
--   1. Bảng đã có dữ liệu. Thêm cột NOT NULL vào một bảng có sẵn thì phải kèm
--      DEFAULT, mà giá trị mặc định ở đây là gì? Chuỗi rỗng và NULL nghĩa khác
--      nhau: chuỗi rỗng là "đã gõ rồi xoá", NULL là "chưa bao giờ có".
--
--   2. Nạp tiền không có lời nhắn - không có ai để nhắn. Ép NOT NULL là bắt
--      mọi dòng DEPOSIT mang một chuỗi rỗng vô nghĩa.
--
-- ⚠️ 140 ký tự, không phải TEXT. Cột này HIỆN RA cho người nhận - tức là một
-- đường để gửi chữ tới màn hình người khác. Giới hạn ngắn làm nó hết dùng được
-- cho việc rải nội dung. Kiểm ở cả DTO (@Size) lẫn ở đây: DTO chặn sớm để báo
-- lỗi tử tế, ràng buộc này là thứ cuối cùng còn đúng nếu mai kia có ai ghi
-- thẳng vào bảng.
ALTER TABLE transactions
  ADD COLUMN note VARCHAR(140);

COMMENT ON COLUMN transactions.note IS
  'Lời nhắn do người chuyển gõ. NULL = không có (nạp tiền, hoặc để trống).';
