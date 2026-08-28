-- V5: doi refresh_tokens.token_hash tu CHAR(64) sang VARCHAR(64).
--
-- V4 (viet sang nay) khai CHAR(64) voi ly do "SHA-256 viet hex luon dung 64 ky tu". Do dai
-- co dinh that, nhung CHAR trong Postgres co hai tinh chat khong ai muon o mot cot dung de
-- TRA CUU BANG BAM:
--
--   1. No DEM KHOANG TRANG cho du 64 ky tu. Hom nay moi gia tri deu dung 64 nen khong thay,
--      nhung ngay nao co ai luu mot bam ngan hon - doi thuat toan chang han - thi gia tri
--      trong bang khac gia tri code gui len, va cau WHERE khong khop ma khong loi nao bao.
--
--   2. So sanh CHAR BO QUA khoang trang cuoi. Nghia la 'abc' = 'abc   ' tra ve TRUE. Voi mot
--      token thi do la mot khe ho: hai chuoi khac nhau duoc coi la mot.
--
-- VARCHAR(64) khong co ca hai tinh chat do. Khong ton them mot byte nao - Postgres luu char
-- va varchar giong het nhau ben trong, khac biet chi nam o luat so sanh va dem.
--
-- 📌 Loi nay lo ra la nho `spring.jpa.hibernate.ddl-auto=validate`. Hibernate tu choi khoi
-- dong voi thong bao:
--
--   wrong column type in column [token_hash]: found [bpchar], but expecting [varchar(64)]
--
-- Voi `ddl-auto=update` thi Hibernate se TU DOI kieu cot tren production, khong hoi ai va
-- khong ghi lai o dau. Do la ly do dat `validate`.
--
-- An toan: moi gia tri hien tai dung 64 ky tu nen khong co khoang trang nao de mat.

ALTER TABLE refresh_tokens
  ALTER COLUMN token_hash TYPE VARCHAR(64);
