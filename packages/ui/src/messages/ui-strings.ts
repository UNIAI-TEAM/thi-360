import type { PaginationLabels } from "../components/pagination";
import type { PasswordFieldLabels } from "../components/password-field";

export type UiLocale = "vi" | "en";

export interface UiStrings {
  common: {
    close: string;
    cancel: string;
    confirm: string;
  };
  pagination: PaginationLabels;
  passwordField: PasswordFieldLabels;
  emptyState: {
    defaultTitle: string;
  };
  toaster: {
    containerAriaLabel: string;
    closeButtonAriaLabel: string;
  };
  themeToggle: {
    switchToDark: string;
    switchToLight: string;
  };
  localeSwitcher: {
    label: string;
    options: Record<UiLocale, string>;
  };
}

export const UI_STRINGS_VI: UiStrings = {
  common: {
    close: "Đóng",
    cancel: "Hủy",
    confirm: "Xác nhận",
  },
  pagination: {
    nav: "Phân trang",
    previous: "Trang trước",
    next: "Trang sau",
    rowsPerPage: "Số dòng mỗi trang",
    page: (page: number) => `Trang ${page}`,
    range: ({ from, to, total }) => `${from}–${to} / ${total}`,
  },
  passwordField: {
    showPassword: "Hiện mật khẩu",
    hidePassword: "Ẩn mật khẩu",
    generate: "Tạo ngẫu nhiên",
    rulesLabel: "Điều kiện mật khẩu",
    ruleLength: "Từ 8 đến 64 ký tự",
    ruleUpper: "Có ít nhất một chữ hoa",
    ruleLower: "Có ít nhất một chữ thường",
    ruleDigit: "Có ít nhất một chữ số",
    ruleSpecial: "Có ít nhất một ký tự đặc biệt",
  },
  emptyState: {
    defaultTitle: "Không có dữ liệu",
  },
  toaster: {
    containerAriaLabel: "Thông báo",
    closeButtonAriaLabel: "Đóng thông báo",
  },
  themeToggle: {
    switchToDark: "Chuyển sang chế độ tối",
    switchToLight: "Chuyển sang chế độ sáng",
  },
  localeSwitcher: {
    label: "Chọn ngôn ngữ",
    options: {
      vi: "Tiếng Việt",
      en: "English",
    },
  },
};

export const UI_STRINGS_EN: UiStrings = {
  common: {
    close: "Close",
    cancel: "Cancel",
    confirm: "Confirm",
  },
  pagination: {
    nav: "Pagination",
    previous: "Previous page",
    next: "Next page",
    rowsPerPage: "Rows per page",
    page: (page: number) => `Page ${page}`,
    range: ({ from, to, total }) => `${from}–${to} / ${total}`,
  },
  passwordField: {
    showPassword: "Show password",
    hidePassword: "Hide password",
    generate: "Generate random",
    rulesLabel: "Password requirements",
    ruleLength: "8 to 64 characters",
    ruleUpper: "At least one uppercase letter",
    ruleLower: "At least one lowercase letter",
    ruleDigit: "At least one digit",
    ruleSpecial: "At least one special character",
  },
  emptyState: {
    defaultTitle: "No data",
  },
  toaster: {
    containerAriaLabel: "Notifications",
    closeButtonAriaLabel: "Close toast",
  },
  themeToggle: {
    switchToDark: "Switch to dark mode",
    switchToLight: "Switch to light mode",
  },
  localeSwitcher: {
    label: "Change language",
    options: {
      vi: "Tiếng Việt",
      en: "English",
    },
  },
};

export const UI_STRINGS_BY_LOCALE: Record<UiLocale, UiStrings> = {
  vi: UI_STRINGS_VI,
  en: UI_STRINGS_EN,
};
