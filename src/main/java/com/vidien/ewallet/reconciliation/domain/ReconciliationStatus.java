/*
 * FEATURE  : Đối soát — job cuối ngày đối chiếu số dư với sổ cái
 * VAI TRÒ  : Kết quả một lượt đối soát. Khớp CHECK constraint trong V8.
 * LIÊN QUAN: ReconciliationRun · V8__create_reconciliation_runs.sql
 */
package com.vidien.ewallet.reconciliation.domain;

/**
 * ⚠️ Chi co HAI gia tri, va co y khong co "ERROR".
 *
 * <p>
 * Job do soat chet giua chung thi <b>khong ghi dong nao ca</b> - transaction rollback. Ghi mot
 * dong "ERROR" nghe co ve day du hon, nhung no tao ra mot dong <b>khong tra loi duoc cau hoi
 * chinh</b>: hom do so du co khop khong? Khong biet. Va mot dong "khong biet" nam canh nhung
 * dong "OK" thi rat de bi doc luot thanh "khong sao".
 *
 * <p>
 * Khong co dong nao cho mot ngay = <b>job khong chay</b>, va do la mot cau hoi khac han, danh
 * cho nguoi van hanh chu khong phai cho ke toan.
 */
public enum ReconciliationStatus {
    OK,
    DRIFT
}
