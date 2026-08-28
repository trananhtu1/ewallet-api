-- V4: refresh token.
--
-- ⭐ VI SAO CAN BANG NAY, khi da co JWT roi:
--
-- JWT tu kiem duoc ma khong phai hoi ai - do la ly do no nhanh, VA cung dung la ly do
-- KHONG THU HOI DUOC no. Bam "Dang xuat" ma token con han thi no van dung duoc neu ai do
-- da sao chep ra. Da ghi ro dieu nay o bai OAuth2 (buoi 11).
--
-- Cach chua khong phai lam JWT thu hoi duoc - lam vay la vut bo dung cai uu diem cua no.
-- Cach chua la GHEP HAI LOAI TOKEN, moi loai lam mot viec:
--
--   access token  = JWT, song 15 PHUT, khong cham database, KHONG thu hoi duoc
--   refresh token = chuoi ngau nhien, song 7 NGAY, kiem o bang nay, THU HOI DUOC
--
-- Khe ho con lai la 15 phut, doi lay viec moi request khong phai hoi database. Do la
-- danh doi co y, khong phai bo sot.

CREATE TABLE refresh_tokens (
  id         BIGSERIAL   PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users (id),

  -- Luu BAM chu khong luu token. Cung ly do voi mat khau: database ro ri thi ke cam duoc
  -- du lieu KHONG dang nhap duoc.
  --
  -- Nhung dung SHA-256 chu khong dung BCrypt, va khac biet nam o ENTROPY:
  --   mat khau  = nguoi nghi ra, doan duoc -> can BCrypt CHAM CO Y de dò thu tốn thời gian
  --   token nay = 32 byte ngau nhien tu SecureRandom -> khong ai dò được, chỉ cần bam nhanh
  -- Dung BCrypt o day la tu lam cham moi lan lam moi token ma khong duoc them gi.
  --
  -- CHAR(64) vi SHA-256 viet dang hex la dung 64 ky tu.
  token_hash CHAR(64)    NOT NULL UNIQUE,

  -- ⭐ Ca chuoi xoay vong dung CHUNG mot family_id. Dang nhap lan dau sinh mot family moi;
  -- moi lan doi token thi token moi VAN o family cu. Nho vay khi phat hien ro ri thi thu
  -- hoi duoc CA CHUOI chu khong phai tung cai - xem RefreshTokenService.
  family_id  UUID        NOT NULL,

  issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,

  -- Da dung de doi lay token moi. Mot refresh token chi duoc dung DUNG MOT LAN.
  used_at    TIMESTAMPTZ,

  -- Bi thu hoi: dang xuat, hoac ca family bi giet vi phat hien dung lai.
  revoked_at TIMESTAMPTZ
);

-- Tra cuu luon la "tim theo bam" luc doi token.
CREATE INDEX ix_refresh_family ON refresh_tokens (family_id);
-- Va "tim het cua nguoi nay" luc dang xuat het thiet bi.
CREATE INDEX ix_refresh_user   ON refresh_tokens (user_id, issued_at DESC);

-- 📌 Bang nay CO khoa ngoai toi users, khac voi audit_log co y khong co.
-- Ly do khac nhau: audit_log duoc ghi bang REQUIRES_NEW NGAY GIUA luc mot transaction khac
-- dang giu khoa tren cac dong lien quan - do la cho INSERT co khoa ngoai bi chan (do duoc
-- hom 28/08). Con bang nay chi duoc ghi trong luong dang nhap / doi token, luc do khong ai
-- giu khoa gi ca. Khoa ngoai o day la loi: token cua mot user khong ton tai la vo nghia.
