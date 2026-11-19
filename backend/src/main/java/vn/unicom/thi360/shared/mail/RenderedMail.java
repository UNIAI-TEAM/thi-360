package vn.unicom.thi360.shared.mail;

/** Thư đã dựng: tiêu đề (dòng đầu của tệp {@code .txt}), HTML, văn bản (không gồm dòng tiêu đề). */
public record RenderedMail(String subject, String html, String text) {
}
