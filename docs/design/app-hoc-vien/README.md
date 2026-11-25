# Thiết kế app học viên native · khung và xác thực (UNI-2785)

Bản xem được: https://claude.ai/artifact/3Rb8TDT9QUgb3SWcHF2MnD (trang riêng tư trên claude.ai). Vẽ ngày 2026-11-25, người yêu cầu duyệt cùng ngày. Đặc tả: `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md`.

Cách đọc tệp `*.dc.html`:

- Lấy bố cục, tên trường, câu chữ, trạng thái, dữ liệu mẫu.
- **Không chép style.** `m3.css` và `ios.css` chỉ để vẽ bản xem; mã thật dùng thành phần gốc của từng nền tảng (Material 3 trên Android, thành phần hệ thống trên iOS).
- Đặc tả thắng bản vẽ khi hai bên khác nhau.
- Mọi màn có Tweaks `dark` để xem chế độ tối. Màu trong bản vẽ là bộ mặc định `thi360-green`; app thật lấy màu theo cấu hình Tùy chỉnh giao diện (đặc tả R-12).
- Logo trong bản vẽ là hình tạm.

## Màn nào ứng với yêu cầu nào

Tiền tố `A-` là Android, `I-` là iOS. `Main` là màn Đăng nhập của Android.

| Yêu cầu | Android | iOS |
|---|---|---|
| R-4 Đăng nhập | `Main` (Tweaks `showError`), `A-Tablet-Login` | `I-Login` (Tweaks `showError`), `I-Pad-Login`, `I-Duo-Cover-Login`, `I-Duo-Open-Login` |
| R-5 Chỉ học viên | `A-RoleNotice` | `I-RoleNotice` |
| R-7 Đăng ký có OTP | `A-Register`, `A-Otp` | `I-Register`, `I-Otp` |
| R-8 Quên và đặt lại mật khẩu | `A-Forgot` (Tweaks `sent`), `A-NewPassword` | `I-Forgot` (Tweaks `sent`), `I-NewPassword` |
| R-9 Bắt đổi và đổi mật khẩu | `A-NewPassword` (Tweaks `forced`), `A-Fold-Account` | `I-NewPassword` (Tweaks `forced`), `I-Duo-Open-Account` |
| R-10 Sinh trắc học | `A-BioEnable`, `A-BioUnlock` | `I-BioEnable`, `I-BioUnlock` |
| R-2 Khung, R-11 Tài khoản | `A-Home`, `A-Account`, `A-Fold-Account`, `A-Tablet-Home` | `I-Home`, `I-Account`, `I-Pad-Account`, `I-Duo-Open-Account` |

## Kích thước đã vẽ

| Loại máy | Kích thước |
|---|---|
| Điện thoại (cả hai nền tảng) | 390×844 |
| Android màn hình gập đang mở | 840×720 |
| Android tablet | 1280×800 |
| iPad | 1180×820 |
| iPhone Duo gập (màn ngoài) | 466×678 |
| iPhone Duo mở (màn trong) | 952×670 trong bản vẽ; đo trên simulator Xcode 27.1 là 669×951 điểm (951×669 khi cạnh dài nằm ngang) |

## Chưa vẽ

Màn chọn ngôn ngữ, hộp thoại Điều khoản sử dụng, hộp xác nhận đăng xuất, bản tiếng Anh, trạng thái đang tải và mất mạng. Các trạng thái này mô tả trong đặc tả (R-11, R-13).

## Chỗ bản vẽ khác đặc tả

- Nền và bề mặt trong bản vẽ dùng màu trung tính của từng hệ điều hành (Android có sắc xanh rất nhạt); đặc tả (AC-78, quyết định D-21) dùng đúng màu trung tính của web.
