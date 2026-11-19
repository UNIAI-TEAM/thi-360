package vn.unicom.thi360.shared.mail;

/** Gửi một thư qua SMTP. Gọi ngoài mọi transaction; ném ngoại lệ khi gửi lỗi. */
interface MailTransport {

    void send(String to, String subject, String html, String text) throws Exception;
}
