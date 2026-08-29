-- V8: nhat ky doi soat (reconciliation) - job cuoi ngay.
--
-- ===== BAT BIEN CUA HE THONG, viet ra thanh SQL =====
--
-- Tien chi VAO he thong bang mot duong duy nhat: giao dich DEPOSIT thanh cong. Chuyen tien la
-- luan chuyen NOI BO - tru ben nay cong ben kia, tong khong doi. Giao dich FAILED khong lam
-- tien chay.
--
-- Nen bat bien la:
--
--   SUM(wallets.balance) = SUM(transactions.amount WHERE type='DEPOSIT' AND status='SUCCESS')
--
-- Cau do la TOAN BO gia tri cua viec doi soat. Job chi la thu chay no moi ngay.
--
-- ⚠️ Va job nay KHONG duoc tu sua so du khi lech. Mot batch lang le "chinh lai cho khop" la
-- thu xoa mat bang chung: sau do khong ai biet lech bao nhieu, tu bao gio, vi sao. Job ghi
-- lai va bao dong; nguoi that quyet dinh sua gi.

CREATE TABLE reconciliation_runs (
  id            BIGSERIAL     PRIMARY KEY,

  -- NGAY nghiep vu, khong phai thoi diem chay. Chay lai lan hai trong cung ngay thi ghi de
  -- ket qua chu khong sinh dong moi - xem unique index duoi.
  business_date DATE          NOT NULL,

  status        VARCHAR(20)   NOT NULL,

  -- Ba con so cua bat bien. NUMERIC(19,2) khop voi wallets.balance - lech kieu o day thi
  -- chinh cai job di tim sai so lai la thu tao ra sai so.
  wallet_total  NUMERIC(19,2) NOT NULL,
  ledger_total  NUMERIC(19,2) NOT NULL,
  drift         NUMERIC(19,2) NOT NULL,

  -- Chi tiet: danh sach vi lech, moi vi so du ghi so va so tinh lai. Hinh dang se doi khi
  -- them kiem tra moi, nen JSONB - dung ranh gioi da do o buoi 12.
  details       JSONB         NOT NULL DEFAULT '{}'::jsonb,

  ran_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  duration_ms   BIGINT        NOT NULL DEFAULT 0,

  CONSTRAINT ck_recon_status CHECK (status IN ('OK', 'DRIFT')),
  CONSTRAINT ck_recon_details_object CHECK (jsonb_typeof(details) = 'object'),

  -- drift phai dung bang hieu hai con so kia. Rang buoc nay chan mot loai loi rat kho thay:
  -- code tinh drift mot dang roi ghi mot dang khac.
  CONSTRAINT ck_recon_drift_matches CHECK (drift = wallet_total - ledger_total),

  -- Va status phai khop voi drift. Mot dong "OK" ma drift khac 0 la dong nguy hiem nhat
  -- trong ca bang: no bao yen trong khi khong yen.
  CONSTRAINT ck_recon_status_matches_drift CHECK ((status = 'OK') = (drift = 0))
);

-- ⭐ MOI NGAY DUNG MOT DONG.
--
-- Job co the chay lai: server restart, deploy giua chung, hoac nguoi van hanh bam tay. Khong
-- co rang buoc nay thi mot ngay co the co ba dong voi ba ket qua khac nhau, va khong ai biet
-- dong nao la that.
CREATE UNIQUE INDEX ux_recon_one_per_day ON reconciliation_runs (business_date);

-- Xem lich su, moi nhat truoc.
CREATE INDEX ix_recon_ran_at ON reconciliation_runs (ran_at DESC);
