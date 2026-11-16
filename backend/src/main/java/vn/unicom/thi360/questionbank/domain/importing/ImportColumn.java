package vn.unicom.thi360.questionbank.domain.importing;

/**
 * Một cột của tệp nhập (mirror {@code ImportColumn} của web).
 *
 * @param key khóa ổn định của cột trong một lần đọc ({@code c0}, {@code c1}…); tên cột có thể
 *            trùng nên không dùng làm khóa
 * @param header tên cột ở dòng 1 của tệp
 * @param example giá trị ở dòng dữ liệu đầu tiên (dòng 2), hiện ở cột "Ví dụ" trên màn
 */
public record ImportColumn(String key, String header, String example) {}
