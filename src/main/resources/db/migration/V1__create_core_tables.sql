-- V1: 3 table core cua vi dien tu.
-- Luat: file nay da chay thi KHONG duoc sua nua - ke ca comment. Doi schema thi viet V2.

-- ===== users =====
CREATE TABLE users (
  id            BIGSERIAL    PRIMARY KEY,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(100) NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- UNIQUE thuong tren email la chua du: Postgres phan biet hoa thuong, nen
-- Tudeptrai@vieted.com va tudeptrai@vieted.com se thanh HAI tai khoan. Ep duy nhat tren
-- LOWER(email) thi chung dung nhau. Index nay cung phuc vu luon cau tim
-- WHERE LOWER(email) = LOWER(?) luc dang nhap.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

-- ===== wallets =====
CREATE TABLE wallets (
  id         BIGSERIAL     PRIMARY KEY,
  -- UNIQUE = moi nguoi dung dung MOT vi (quyet dinh thu hep pham vi, xem ROADMAP).
  -- UNIQUE tu sinh ra index nen KHONG can CREATE INDEX rieng cho user_id.
  user_id    BIGINT        NOT NULL UNIQUE REFERENCES users (id),
  -- NUMERIC(19,2) chu khong bao gio FLOAT/DOUBLE: 0.1 + 0.2 = 0.30000000000000004.
  balance    NUMERIC(19,2) NOT NULL DEFAULT 0,
  -- Danh cho optimistic locking - dung o buoi sau trong tuan.
  version    INTEGER       NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
  -- Hang rao cuoi cung: du code tru tien co sai the nao, so du khong am duoc.
  CONSTRAINT ck_wallets_balance_not_negative CHECK (balance >= 0)
);

-- ===== transactions =====
CREATE TABLE transactions (
  id              BIGSERIAL     PRIMARY KEY,
  -- NULL duoc: nap tien khong co vi nguon, tien di tu ngoai he thong vao.
  from_wallet_id  BIGINT        REFERENCES wallets (id),
  to_wallet_id    BIGINT        REFERENCES wallets (id),
  amount          NUMERIC(19,2) NOT NULL,
  type            VARCHAR(20)   NOT NULL,
  -- Giao dich THAT BAI van phai luu lai, neu khong thi lich su giao dich noi doi.
  status          VARCHAR(20)   NOT NULL,
  -- Chong bam chuyen tien hai lan - dung o Week 4. Cot tao san tu bay gio vi
  -- them cot UNIQUE vao bang da co du lieu thi phien hon nhieu.
  -- Cho phep NULL, va Postgres cho NHIEU dong cung NULL trong unique index
  -- (theo chuan SQL, NULL = NULL khong phai TRUE ma la "khong biet").
  idempotency_key VARCHAR(64)   UNIQUE,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

  CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
  CONSTRAINT ck_transactions_type   CHECK (type   IN ('DEPOSIT', 'TRANSFER')),
  CONSTRAINT ck_transactions_status CHECK (status IN ('SUCCESS', 'FAILED')),
  -- Doc thanh loi: nap tien thi khong co nguon nhung phai co dich;
  -- chuyen tien thi phai du hai dau va hai dau phai khac nhau.
  CONSTRAINT ck_transactions_endpoints CHECK (
    (type = 'DEPOSIT'  AND from_wallet_id IS NULL     AND to_wallet_id IS NOT NULL)
    OR
    (type = 'TRANSFER' AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL
                       AND from_wallet_id <> to_wallet_id)
  )
);

-- Khoa ngoai KHONG tu sinh index (khac voi UNIQUE) nen phai tu danh.
-- Gom ca created_at DESC vi cau truy van luon la "giao dich cua vi X, moi nhat truoc":
-- Postgres vua loc vua lay san dung thu tu, khoi phai sort lai.
CREATE INDEX ix_transactions_from_created ON transactions (from_wallet_id, created_at DESC);
CREATE INDEX ix_transactions_to_created   ON transactions (to_wallet_id,   created_at DESC);
