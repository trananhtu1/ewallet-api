-- V2: doi rang buoc duy nhat cua idempotency_key tu TOAN BANG sang TUNG VI.
--
-- V1 dat: idempotency_key VARCHAR(64) UNIQUE  -> duy nhat tren toan bang.
-- Nghe hop ly cho toi luc nghi ky: khoa nay do NGUOI GOI tu sinh. Hai nguoi dung
-- khac nhau hoan toan co the cung sinh ra chuoi "transfer-1", va khi do:
--   - nguoi thu hai bi tu choi mot lenh chuyen tien HOAN TOAN HOP LE
--   - hoac te hon, neu code tra ve "ket qua cu" thi anh ta nhan duoc ket qua
--     giao dich CUA NGUOI KHAC
--
-- Pham vi dung la TUNG VI: "vi 3 da dung khoa transfer-1 chua". Hai vi khac nhau
-- dung cung mot chuoi la chuyen binh thuong, khong phai xung dot.
--
-- LUU Y ve NULL: nap tien va cac giao dich khong co khoa deu de NULL, va theo
-- chuan SQL thi NULL = NULL khong phai TRUE - nen NHIEU dong cung (vi, NULL)
-- van vao duoc. Da do dieu nay hom 25/08 khi thiet ke V1.

-- Ten rang buoc do Postgres tu dat cho `VARCHAR(64) UNIQUE` o V1.
ALTER TABLE transactions DROP CONSTRAINT transactions_idempotency_key_key;

CREATE UNIQUE INDEX ux_transactions_wallet_idempotency
  ON transactions (from_wallet_id, idempotency_key);
