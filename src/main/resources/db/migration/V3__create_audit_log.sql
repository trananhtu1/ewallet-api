-- V3: nhat ky kiem toan.
--
-- Khac han bang `transactions`. Doc ky cho nay vi hai bang de bi lan:
--   transactions = SO CAI. Chi ghi thu LAM DUOC. Tien di dau, tu vi nao sang vi nao.
--   audit_log    = NHAT KY. Ghi ca thu KHONG lam duoc, va ca thu bi TU CHOI.
-- Mot lan chuyen tien bi tu choi vi khong du tien: so cai co 1 dong FAILED, nhat ky co
-- 1 dong TRANSFER_REJECTED kem ly do. Mot lan co gang rut vi nguoi khac: so cai KHONG co
-- gi ca (dung), nhat ky PHAI co.

CREATE TABLE audit_log (
  id      BIGSERIAL   PRIMARY KEY,

  -- Cot thuong, khong nhet vao JSON: day la thu LUON CO va LUON CAN LOC.
  -- Quyet dinh nay do duoc o bai SQL vs NoSQL - loc theo cot + btree nhanh hon 10 lan
  -- so voi loc theo truong trong JSONB, ke ca khi JSONB da co GIN.
  event   VARCHAR(40) NOT NULL,

  -- Ai gay ra. NULL duoc: dang nhap that bai thi chua biet la ai.
  -- CO Y KHONG DAT KHOA NGOAI toi users - xem khoi ghi chu ben duoi.
  actor_id BIGINT,

  at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Phan THAY DOI theo tung loai su kien. Dang nhap that bai co `email` va `ip`;
  -- chuyen tien bi tu choi co `from`, `to`, `amount`, `reason`. Lam bang cot thuong thi
  -- hoac moi loai mot bang, hoac mot bang rat rong toan NULL - da can nhac o bai buoi 12.
  payload JSONB       NOT NULL,

  -- JSONB nhan ca so, chuoi, mang lam gia tri goc. Rang buoc nay bat no phai la mot
  -- OBJECT, neu khong thi payload->>'reason' se im lang tra ve NULL o moi dong.
  CONSTRAINT ck_audit_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

-- ⭐ VI SAO KHONG CO KHOA NGOAI TOI users(id) - hai ly do, ca hai deu do duoc:
--
-- 1. KHOA. Hom 28/08 do duoc: INSERT mot dong co khoa ngoai bat Postgres lay khoa
--    FOR KEY SHARE tren dong cha. Nhat ky nay duoc ghi bang REQUIRES_NEW NGAY GIUA luc
--    transaction ngoai dang giu khoa tren cac dong lien quan - them khoa ngoai vao day la
--    tu dung lai dung cai bay da mat ca buoi de go ra.
--
-- 2. DO BEN. Nhat ky kiem toan phai song LAU HON thu no ghi ve. Xoa mot nguoi dung ma
--    keo theo mat sach dau vet cua nguoi do la dieu te nhat mot nhat ky co the lam - va
--    khoa ngoai thi hoac chan viec xoa, hoac (voi ON DELETE CASCADE) xoa luon nhat ky.
--
-- Doi lai: actor_id co the tro toi mot user khong con ton tai. Chap nhan co chu dich -
-- day la mot BAN GHI LICH SU, no noi ve luc do chu khong noi ve bay gio.

-- Truy van thuc te luon la "cac su kien loai X, moi nhat truoc" hoac "cua nguoi Y".
-- Gom ca `at DESC` vao index de khoi moc them node Sort - bai hoc buoi 5.
CREATE INDEX ix_audit_event_at ON audit_log (event, at DESC);
CREATE INDEX ix_audit_actor_at ON audit_log (actor_id, at DESC);
