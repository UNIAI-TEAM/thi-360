import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

const EXPECTED_VI_MESSAGES = {
  acknowledge: "Tôi hiểu tài khoản bị xóa sẽ không khôi phục được.",
  blockedAttempt: "Bạn đang có lượt thi chưa nộp. Hãy nộp bài rồi quay lại.",
  blockedOrder:
    "Bạn có đơn hàng đang được kiểm tra. Hãy chờ bài thi được cấp hoặc liên hệ hỗ trợ rồi quay lại.",
  blockedPendingPayment:
    "Bạn có đơn hàng đang chờ thanh toán. Hãy hủy đơn hoặc chờ đơn hết hạn rồi quay lại.",
  confirmAction: "Xóa tài khoản",
  confirmCancel: "Hủy",
  confirmTitle: "Xóa tài khoản vĩnh viễn?",
  deletedNotice: "Tài khoản của bạn đã được xóa.",
  explanation:
    "Khi xóa tài khoản, bạn sẽ bị đăng xuất khỏi mọi thiết bị và không đăng nhập lại được. Họ tên, email, số điện thoại và ảnh đại diện của bạn bị xóa khỏi hồ sơ. Bài làm của bạn được giữ ở dạng không còn gắn với bạn. Đơn hàng, thanh toán, nhật ký hệ thống và thư đã gửi được lưu theo quy định của pháp luật và thời hạn lưu trữ.",
  failed: "Chưa xóa được tài khoản. Vui lòng thử lại.",
  openMyExams: "Bài thi của tôi",
  openMyOrders: "Đơn của tôi",
  passwordLabel: "Mật khẩu hiện tại",
  pendingGradingWarning:
    "Bạn có bài đang chờ chấm. Xóa tài khoản thì bạn sẽ không xem được kết quả.",
  submit: "Xóa tài khoản",
  throttled: "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau.",
  title: "Xóa tài khoản",
  unusedAttemptsWarning:
    "Bạn còn {n} lượt thi chưa dùng. Các lượt này sẽ mất và không được hoàn tiền.",
  wrongPassword: "Mật khẩu hiện tại chưa đúng.",
} as const;

describe("account.deletion.* i18n parity (AC-15)", () => {
  it("vi.json và en.json có cùng tập khóa account.deletion.*, đúng nguyên văn tiếng Việt, giữ {n} và đúng deletedNotice tiếng Anh", () => {
    const viDeletion = vi.account.deletion;
    const enDeletion = en.account.deletion;

    expect(Object.keys(viDeletion).sort()).toEqual(Object.keys(EXPECTED_VI_MESSAGES).sort());
    expect(Object.keys(enDeletion)).toEqual(Object.keys(viDeletion));
    expect(viDeletion).toEqual(EXPECTED_VI_MESSAGES);

    expect(viDeletion.unusedAttemptsWarning).toContain("{n}");
    expect(enDeletion.unusedAttemptsWarning).toContain("{n}");
    expect(enDeletion.deletedNotice).toBe("Your account has been deleted.");

    for (const [key, value] of Object.entries(enDeletion)) {
      expect(value.trim().length, `en.account.deletion.${key} non-empty`).toBeGreaterThan(0);
      expect(VIETNAMESE_DIACRITIC_RE.test(value), `en.account.deletion.${key} has Vietnamese`).toBe(false);
    }
  });
});
