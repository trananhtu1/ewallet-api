-- V9: Nguoi nhan da luu.
--
-- Nguoi dung luu san mot vi hay chuyen toi, dat cho no mot cai ten de nho
-- ("Me", "Tien nha"), roi lan sau khong phai go lai so vi.
--
-- ⭐ Bang nay co HAI khoa ngoai, va do khong phai ngau nhien: day la bang dau
-- tien trong project co quan he N-1 that su ve phia doc. Muon lay danh sach
-- nguoi nhan kem TEN CHU VI thi phai cham sang hai bang khac - va do chinh la
-- cho sinh ra van de N+1 neu doc sai cach. Xem BeneficiaryRepository.

CREATE TABLE beneficiaries (
  id                BIGSERIAL    PRIMARY KEY,

  -- Chu so dia chi. ON DELETE CASCADE: xoa nguoi dung thi so dia chi cua ho
  -- di theo, khong de lai dong mo coi.
  owner_user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

  -- Vi duoc luu.
  --
  -- ⚠️ KHONG dat ON DELETE CASCADE o day, va cung khong cho xoa vi: mot cai vi
  -- co lich su giao dich thi khong bao gio duoc bien mat. Rang buoc nay ton tai
  -- de chan viec luu mot so vi khong ton tai, chu khong de dieu khien viec xoa.
  wallet_id         BIGINT       NOT NULL REFERENCES wallets(id),

  -- Ten do NGUOI LUU tu dat, khong phai ten that cua chu vi.
  --
  -- Co y: ten that co the doi, va quan trong hon - hien ten that cua nguoi khac
  -- cho mot ai do chi vi ho go dung so vi la mot duong ro ri danh tinh. Ai luu
  -- thi tu dat ten, va chi minh ho thay.
  label             VARCHAR(60)  NOT NULL,

  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

  -- Khong cho luu mot vi hai lan trong cung mot so dia chi.
  CONSTRAINT ux_beneficiary_owner_wallet UNIQUE (owner_user_id, wallet_id),

  -- Khong cho tu luu chinh minh - da co man chuyen tien chan viec chuyen cho
  -- chinh vi minh, thi so dia chi cung khong nen chua no.
  -- (Kiem o tang service vi o day chua biet vi nao thuoc ve ai.)
  CONSTRAINT ck_beneficiary_label_not_blank CHECK (length(trim(label)) > 0)
);

-- Truy van duy nhat cua bang nay: "lay het nguoi nhan cua toi, moi truoc".
-- Index phu dung thu tu do luon nen Postgres doc thang, khong phai sap xep lai.
CREATE INDEX ix_beneficiaries_owner ON beneficiaries (owner_user_id, created_at DESC);
