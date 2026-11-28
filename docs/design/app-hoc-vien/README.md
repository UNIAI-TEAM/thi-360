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

---

# Đợt 2 · Cổng thi (UNI-2820)

Bản xem được: https://claude.ai/artifact/BFQsVBxf4sNzfQ2VY8jrbr (trang riêng tư trên claude.ai; gồm cả 28 màn của đợt khung và xác thực). Vẽ ngày 2026-11-26, người yêu cầu duyệt cùng ngày (quyết định D-9 của đặc tả). Đặc tả: `docs/specs/2026-11-26-app-hoc-vien-cong-thi.md`. Số yêu cầu (R-n) ở phần này là số của đặc tả đợt 2.

Cách đọc tệp giữ như phần trên: lấy bố cục, câu chữ, trạng thái và dữ liệu mẫu; không chép style; đặc tả thắng bản vẽ.

## Màn nào ứng với yêu cầu nào (đợt 2)

Mỗi màn có ở cả hai nền tảng với cùng tên, tiền tố `A-` hoặc `I-`, trừ các màn rộng ghi riêng.

| Nhóm yêu cầu | Màn (bỏ tiền tố) | Màn rộng |
|---|---|---|
| R-9 đến R-31 Danh mục và chi tiết bài thi | `Catalog`, `CatalogFilter`, `ExamDetail`, `BundleDetail` | `A-Tablet-Catalog`, `I-Pad-Catalog` |
| R-32 đến R-48 Mua, thanh toán, đơn hàng | `Checkout`, `PayResult`, `Orders`, `OrderDetail` | — |
| R-49 đến R-70 Bài thi của tôi, Lịch sử | `MyExams`, `History`, `HistoryFilter` | `A-Fold-MyExams`, `I-Duo-Open-MyExams` |
| R-71 đến R-105 Chuẩn bị và khung làm bài | `Prepare`, `Starting`, `PartChoice`, `Player`, `PlayerNav`, `PlayerDialogs` | `A-Fold-Player`, `A-Tablet-Player`, `I-Pad-Player`, `I-Duo-Open-Player` |
| R-106 đến R-142, R-177 Nội dung và dạng câu | `Q-Choice`, `Q-TrueFalse`, `Q-ShortAnswer`, `Q-Cloze`, `Q-Matching`, `Q-Essay`, `Q-Audio`, `Q-Set`, `Q-Rich`, `Q-Review` | — |
| R-143 đến R-164 Kết quả và xem lại | `Result`, `ResultBreakdown`, `Review` | `A-Tablet-Review`, `I-Pad-Review` |
| R-165 đến R-176 Hồ sơ trong Tài khoản | `AccountV2`, `Profile`, `ProfileEmail`, `VerifyEmail` | `A-Fold-Profile`, `I-Pad-Profile` |
| R-183 Xóa tài khoản | `DeleteAccount` (Tweaks `state`: sẵn sàng, đã điền, hai trường hợp chặn, hộp xác nhận, sai mật khẩu) | — |
| R-179 Biểu tượng và logo | tệp nguồn ở `brand/` (`thi360-icon.png` dùng cho app; logo trong các bản vẽ vẫn là hình tạm) | — |

`AccountV2` thay `Account` của đợt trước (thêm dòng Hồ sơ và Đơn của tôi). Các trạng thái của một màn nằm trong Tweaks của màn đó (ví dụ `state`, `access`, `dialog`, `closed`); Tweaks `scroll` chỉ để xem phần dưới của màn dài, không phải trạng thái.

## Chưa vẽ (đợt 2)

Mỗi màn chỉ có tối đa hai Tweaks ngoài `dark`, nên phần lớn màn chưa có bản vẽ cho đang tải, lỗi tải, không tìm thấy (404), mất mạng và các lỗi của từng thao tác. Các trạng thái đó làm theo tiêu chí của đặc tả (R-5 cho bốn trạng thái chung và tiêu chí riêng của từng màn). Cũng chưa vẽ: màn chờ ở trạng thái thất bại, thanh đầu thu gọn của màn làm bài, chế độ khóa của câu hỏi, ảnh đang tải và ảnh lỗi, bản xem lại của các dạng Trả lời ngắn, Điền khuyết, Ghép đôi, Tự luận, bản tiếng Anh.

## Chỗ bản vẽ khác đặc tả (đợt 2)

- Nền và bề mặt: như đợt trước, app dùng màu trung tính của web (`bgPage`, `bgSurface`), không dùng sắc xanh nhạt của bản vẽ Android hay xám của bản vẽ iOS. App không có nền gradient.
- Màu trạng thái (thành công, cảnh báo, thông tin) trong bản vẽ là giá trị gần đúng; app lấy đúng token của web (R-6).
- `A-Fold-Player` vẽ ba cột liền nhau, không chừa dải bản lề; `I-Duo-Open-Player` đặt danh sách câu và ngữ liệu ở nửa trái, thanh đầu và câu hỏi ở nửa phải. Hai cách đặt này đã được duyệt (D-9).
- `Review` trên điện thoại vẽ lưới câu thành một dải cuộn ngang kèm nút trước và sau (đã duyệt, D-9).
- `ProfileEmail` vẽ Đổi email thành một màn riêng; app làm theo đặc tả: biểu mẫu mở ngay trong màn Hồ sơ như web (D-25).
  - Bổ sung đối chiếu thực thi (ĐT 681): bản vẽ `ProfileEmail` vẽ biểu mẫu đổi email thành màn riêng; app theo đặc tả, biểu mẫu mở ngay trong khối Email của màn Hồ sơ (không có tuyến riêng).
- Bảng câu hỏi (`PlayerNav`, `Fold-Player`, `Tablet-Player`, `Pad-Player`, `Duo-Open-Player`): ô của câu được đánh dấu đã vẽ lại theo web (D-29), tô đỏ theo ba mức hoàn thành, hình cờ nhỏ ở góc dưới phải, chấm "chưa lưu" ở góc trên phải. Màu đỏ trong bản vẽ là giá trị token của web lúc vẽ; app lấy từ `StatusPalette` và `BrandPalette`.
- Lớp phủ mờ sau tấm chọn ảnh ở `Profile` dùng màu đen 30–32%, không có trong bảng màu; app dùng lớp phủ chuẩn của hệ điều hành.
- Câu chữ trong bản vẽ có thể lệch vài chữ so với đặc tả; chuỗi của app lấy theo đặc tả và `apps/web-student/src/messages/vi.json`.
