# Bắt buộc xác minh OTP email khi tự đăng ký (TM Q13 — `UNI-2773`)

- **Ngày chốt:** 2026-11-22
- **Issue:** `UNI-2773`
- **Thay thế:** Giả định tạm `E18` và mục `5.1` của `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` đối với luồng tự đăng ký học viên.

## 1. Mục tiêu

Ngăn chặn việc người lạ nhập địa chỉ email của người khác trên màn tự đăng ký (`/register`) để chiếm chỗ email dù không sở hữu hộp thư:

- Chưa xác minh đúng mã OTP 6 chữ số gửi qua email thì **chưa tạo dòng trong `identity.user_account` và chưa tạo user trên Keycloak**.
- Nếu người dùng bỏ dở bước nhập OTP, cả `username` và `email` đều không bị giữ chỗ.
- Khi xác nhận OTP hợp lệ, tài khoản được tạo ngay với `email_verified_at = now()` và `emailVerified = true` trên Keycloak, gán vai trò `STUDENT`, mở phiên cookie `HttpOnly` và chuyển hướng theo `callbackUrl` hợp lệ (hoặc `/home`).

## 2. Hợp đồng API công khai (`AuthController`)

Ba đường dẫn công khai cùng đi qua `RateLimitFilter.RegistrationRoute` (giới hạn E19 theo IP: 30 yêu cầu/giờ, 100 yêu cầu/ngày; miễn kiểm tra CSRF và bỏ qua cookie phiên cũ hết hạn):

| Method & Path | Request | Response thành công | Lỗi chính |
|---|---|---|---|
| `POST /api/v1/auth/register` | `RegisterRequest { username, fullName, email, password, acceptTerms, termsVersion, callbackUrl?, registrationId? }` | `200 OK` `RegisterStartResponse { registrationId, email, resendAfterSeconds }` | `400 VALIDATION_FAILED` (trả đủ lỗi theo trường), `409 USERNAME_TAKEN` / `EMAIL_TAKEN`, `429 RATE_LIMITED` (kèm `Retry-After` và `retryAfter`) |
| `POST /api/v1/auth/register/resend` | `RegisterResendRequest { registrationId }` | `200 OK` `RegisterStartResponse { registrationId, email, resendAfterSeconds }` | `422 EMAIL_CODE_EXPIRED` (phiên chờ không tồn tại, đã hoàn tất hoặc quá 24 giờ), `429 RATE_LIMITED` (chưa đủ 60 giây hoặc quá 5 mã/24 giờ cho email đó) |
| `POST /api/v1/auth/register/verify` | `RegisterVerifyRequest { registrationId, code }` | `201 Created` `RegisterResponse { redirectTo }` + cookie `HttpOnly` (`thi360_at`, `thi360_rt`, `XSRF-TOKEN`) | `422 EMAIL_CODE_INVALID` (kèm `remainingAttempts`), `422 EMAIL_CODE_EXPIRED` (mã quá 10 phút, đã dùng hoặc sai quá 5 lần), `409 USERNAME_TAKEN` / `EMAIL_TAKEN` (tranh chấp đồng thời), `502 IDENTITY_PROVIDER_ERROR` |

### Sửa thông tin ở Bước 1 (`registrationId` tùy chọn)

- Khi học viên đang ở Bước 2 bấm **"Quay lại sửa thông tin"** (ví dụ sửa `username`, `fullName` hoặc `password` nhưng giữ nguyên `email`), giao diện gửi lại `POST /api/v1/auth/register` kèm `registrationId` hiện có:
  - Nếu phiên chờ còn hạn, chưa hoàn tất, cùng `email_normalized` và đang trong thời gian chờ gửi lại (< 60 giây), máy chủ cập nhật thông tin trên phiên chờ hiện có và giữ nguyên mã OTP vừa gửi mà không báo `429`.
  - Nếu đã qua 60 giây hoặc đổi sang `email` khác, máy chủ cấp mã OTP mới (vẫn tôn trọng trần 5 mã/24 giờ theo `email_normalized`).

## 3. Lưu trữ & Bảo mật (`identity`)

- **Bảng `identity.pending_registration`:**
  - Chỉ lưu mật khẩu dạng mã hóa đối xứng `AES-256-GCM` với IV ngẫu nhiên 96-bit cho từng lần ghi, khóa dẫn xuất `SHA-256("identity.pending_registration:" || THI360_TOKEN_PEPPER)`; xóa bản mã ngay sau khi hoàn tất tạo tài khoản (`password_ciphertext = ''`).
  - Mã OTP 6 chữ số chỉ lưu dạng `HMAC-SHA256` (`EmailCodeHasher` gắn với `registrationId` và `VERIFY`), hạn 10 phút, tối đa 5 lần nhập sai (`attempts < 5`), dùng một lần (`code_used_at`).
- **Bảng `identity.pending_registration_send`:**
  - Ghi thời điểm mỗi lần phát mã OTP theo `email_normalized`, khóa tư vấn `pg_advisory_xact_lock` theo `email_normalized` để áp đúng `EmailCodeSendPolicy` (chờ 60 giây giữa hai lần gửi và tối đa 5 mã trong 24 giờ trượt).
- **Hoàn tất tạo tài khoản (Bước 2):**
  - Giữ nguyên cơ chế hai pha `PROVISIONING -> Keycloak -> ACTIVE` cùng `ProvisioningReconcilerJob` của PBI-01 / ID-1 để không bao giờ rò rỉ tài khoản mồ côi khi tiến trình sự cố giữa chừng.
  - Ghi `email_verified_at` ngay trên dòng tài khoản, `emailVerified = true` trên Keycloak, sự kiện kiểm toán `USER_SELF_REGISTERED`.

## 4. Giao diện Web Học viên (`apps/web-student`)

- Trang `/register` gồm 2 bước trên cùng đường dẫn (giữ nguyên `callbackUrl`):
  1. **Bước 1:** Biểu mẫu Họ và tên, Tên đăng nhập, Email, Mật khẩu, Xác nhận mật khẩu, Điều khoản sử dụng.
  2. **Bước 2:** Màn nhập mã OTP 6 chữ số gửi tới email vừa khai báo, đếm ngược gửi lại 60 giây, cảnh báo khi chạm trần 5 lần/ngày, báo số lần thử còn lại khi nhập sai mã, nút **"Quay lại sửa thông tin"** giữ nguyên dữ liệu biểu mẫu Bước 1. Khi xác nhận thành công (`201`), điều hướng sang `callbackUrl` hợp lệ hoặc `/home`.
- Tương thích ngược: giữ nguyên `/verify-email` và mục xác minh/đổi email trong `/account/profile` cho tài khoản do quản trị viên tạo hoặc nhập Excel chưa có `email_verified_at`.
