export interface SupportContact {
  phone: string;
  email: string;
  hours: string;
}

/**
 * Kênh hỗ trợ lấy từ biến môi trường (TM 11.2, C7): biến trống thì trống, không bịa số liên hệ. Phải viết đúng tên
 * `process.env.NEXT_PUBLIC_*` để Next thay giá trị lúc build cho cả mã chạy ở trình duyệt.
 */
export function readSupportContact(): SupportContact {
  return {
    phone: process.env.NEXT_PUBLIC_SUPPORT_PHONE?.trim() ?? "",
    email: process.env.NEXT_PUBLIC_SUPPORT_EMAIL?.trim() ?? "",
    hours: process.env.NEXT_PUBLIC_SUPPORT_HOURS?.trim() ?? "",
  };
}

/** Có kênh liên hệ nào được cấu hình không. */
export function hasSupportContact(contact: SupportContact): boolean {
  return Boolean(contact.phone || contact.email || contact.hours);
}
