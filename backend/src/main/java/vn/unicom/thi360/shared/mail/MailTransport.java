package vn.unicom.thi360.shared.mail;

/**
 * Gửi một thư qua SMTP. Gọi ngoài mọi transaction; ném ngoại lệ khi gửi lỗi. Mọi thời gian chờ của lần
 * gửi phải lấy từ {@code deadline} tại lúc dùng, và không bắt đầu SMTP khi nó đã hết
 * ({@link SendDeadline.ExpiredException}).
 */
interface MailTransport {

    void send(String to, String subject, String html, String text, SendDeadline deadline) throws Exception;
}
