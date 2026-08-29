-- V7: ho so KYC (gia lap) - upload anh CCCD.
--
-- ===== KIEU LAI: cot thuong + JSONB, va ranh gioi da DO chu khong doan =====
--
-- Buoi 12 (SQL vs NoSQL) do hai cach luu canh nhau TRONG CUNG mot Postgres. Ket luan dung
-- lai o day, khong thiet ke lai:
--
--   COT THUONG cho thu LUON CO va LUON DUNG DE LOC.
--     user_id, status, created_at - moi cau truy van deu cham vao chung, va chung can index.
--     Nhet vao JSONB thi moi lan loc la mot phep giai JSON.
--
--   JSONB cho thu HINH DANG CO THE DOI.
--     Metadata anh: kich thuoc, mime, ma bam, ten file goc. Hom nay ba truong, mai co the
--     them "so trang", "do phan giai", "diem OCR". Moi truong moi la mot ALTER TABLE neu
--     chung la cot - va ALTER TABLE tren bang lon la mot buoc van hanh, khong phai mot dong
--     migration.
--
-- ⚠️ Va KHONG luu anh o day. Bang nay chi giu METADATA. Anh that thi len object storage
-- (S3/R2) - day la ban GIA LAP nen khong co anh nao duoc luu lai o dau ca, chi con lai ma
-- bam SHA-256 de doi chieu.
--
-- Ly do khong nhet anh vao Postgres du bytea lam duoc: moi lan doc mot dong la keo ca vai
-- MB qua ket noi, backup phinh theo so anh, va cache cua Postgres bi day day boi thu khong
-- ai truy van.

CREATE TABLE kyc_submissions (
  id           BIGSERIAL     PRIMARY KEY,
  user_id      BIGINT        NOT NULL REFERENCES users (id),

  -- PENDING / APPROVED / REJECTED. VARCHAR chu khong ENUM cua Postgres: them mot gia tri vao
  -- ENUM la mot ALTER TYPE khoa bang, con VARCHAR + CHECK thi doi luc nao cung duoc.
  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',

  -- Metadata anh. Xem ghi chu dau file ve ranh gioi cot/JSONB.
  metadata     JSONB         NOT NULL DEFAULT '{}'::jsonb,

  -- Ly do tu choi, chi co khi status = REJECTED.
  reject_reason VARCHAR(200),

  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  reviewed_at  TIMESTAMPTZ,

  CONSTRAINT ck_kyc_status
      CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),

  -- Cung mot rang buoc voi audit_log: JSONB nhan ca mot con so hay mot chuoi lam gia tri
  -- goc ('5'::jsonb la hop le), nen phai noi ro day PHAI la mot object.
  CONSTRAINT ck_kyc_metadata_object
      CHECK (jsonb_typeof(metadata) = 'object'),

  -- Ly do tu choi thi bat buoc phai co khi tu choi, va khong duoc co khi khong tu choi.
  -- Mot "REJECTED" khong ly do la mot dong nguoi truc khong tra loi duoc khach hang.
  CONSTRAINT ck_kyc_reason_matches_status
      CHECK ((status = 'REJECTED') = (reject_reason IS NOT NULL))
);

-- ⭐ MOI NGUOI CHI DUOC CO MOT HO SO DANG CHO.
--
-- Unique index CO DIEU KIEN: rang buoc chi ap dung cho dong PENDING. Nguoi bi tu choi phai
-- nop lai duoc, va nguoi da duyet co the nop lai khi doi giay to - nen khong the dat UNIQUE
-- tren rieng user_id.
--
-- Va day la hang rao o TANG DATABASE, khong phai mot cau if trong service. Hai request nop
-- cung luc thi cau if o service cho ca hai di qua (ca hai deu doc thay "chua co dong nao"),
-- con index nay thi tu choi dong thu hai du chuyen gi xay ra.
CREATE UNIQUE INDEX ux_kyc_one_pending_per_user
    ON kyc_submissions (user_id)
    WHERE status = 'PENDING';

-- Xem ho so cua minh, moi nhat truoc.
CREATE INDEX ix_kyc_user_created ON kyc_submissions (user_id, created_at DESC);
