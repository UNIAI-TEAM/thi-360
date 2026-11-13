# TASK — Thi 360

> Cập nhật: 2026-11-13 · Trạng thái sống nằm trên UniAI (project Thi 360). File này là bản chụp để đọc nhanh trong repo; khi lệch thì UniAI đúng.
> Chi tiết từng task (hợp đồng, thứ tự sóng, trọng số): `docs/plans/2026-11-13-base-port-pbi-01-02.md`. Phụ thuộc trong bảng dưới theo Phần C của plan.

## Đợt 1 — Port nền + PBI-01 + PBI-02

Issue cha: **UNI-2537** · Thi 360 · Port nền + PBI-01 Tài khoản & phân quyền + PBI-02 Danh mục dùng chung

| Task | Issue | Việc | Phụ thuộc | Trạng thái |
|---|---|---|---|---|
| T01 | UNI-2538 | Nền · Khởi tạo monorepo, CI, môi trường dev (Docker Compose) | — | in_review (đã merge vào `develop` ngày 2026-11-13; người chuyển `done`) |
| T02 | UNI-2539 | Nền · Khung backend modular monolith (Spring Boot, Flyway, OpenAPI, lỗi chuẩn) | T01 | in_review (đã merge vào `integration/dot-1` qua PR #4 ngày 2026-11-13) |
| T03 | UNI-2540 | Nền · Xác thực: Keycloak, đăng nhập/refresh/đăng xuất, phiên, chặn đăng nhập sai | T02 | in_review (đã merge vào `integration/dot-1` qua PR #6 ngày 2026-11-13) |
| T04 | UNI-2541 | PBI-01 · Phân quyền: bảng quyền, vai trò gán quyền, gán vai trò và quyền cho user, người dùng theo quyền, gán hàng loạt (API + engine) | T03 | in_review (đã merge vào `integration/dot-1` qua PR #9 ngày 2026-11-13) |
| T05 | UNI-2542 | Nền · Nhật ký thao tác: bảng chỉ ghi thêm, outbox, worker, API tra cứu | T02 | in_review (đã merge vào `integration/dot-1` qua PR #8 ngày 2026-11-13) |
| T06 | UNI-2543 | Nền · Design system (`packages/ui`) và khung web admin | T01 | in_review (đã merge vào `integration/dot-1` qua PR #5 ngày 2026-11-13) |
| T06b | UNI-2559 | Nền · Tương phản chữ/nền chế độ sáng >= 4,5:1 (con của UNI-2543, ngoài điểm plan) | T06 | in_review (đã merge vào `integration/dot-1` qua PR #7 ngày 2026-11-13) |
| T07 | UNI-2544 | PBI-01 · API quản lý tài khoản | T03, T04, T05 | todo |
| T08 | UNI-2545 | PBI-01 · UI phân quyền: một menu bốn tab (Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt) | T04, T06, T18 | todo |
| T09 | UNI-2546 | PBI-01 · UI quản lý tài khoản | T06, T07, T18 | todo |
| T10 | UNI-2547 | PBI-01 · Lớp học: API + UI | T07, T09 | todo |
| T11 | UNI-2548 | PBI-01 · Cấp tài khoản hàng loạt | T07, T10 | todo |
| T12 | UNI-2549 | PBI-01 · Hồ sơ học viên (khung) | T09, T10 | todo |
| T13 | UNI-2550 | PBI-02 · Danh mục: schema, dữ liệu mặc định, API nhóm và giá trị, API lựa chọn | T04, T05 | in_progress |
| T14 | UNI-2551 | PBI-02 · Mã tổ hợp có phiên bản, nơi đang dùng, quy tắc ngừng và xóa | T13 | todo |
| T15 | UNI-2552 | PBI-02 · UI Cấu hình chung | T06, T13, T14, T18 | todo |
| T17 | UNI-2555 | Nền · Tùy chỉnh giao diện toàn hệ thống (mục trong menu chính, port từ reap) | T04, T05, T06 | todo |
| T18 | UNI-2556 | Nền · Đăng nhập một nơi (SSO) và điều hướng theo vai trò | T03, T04, T06 | todo |
| T19 | UNI-2561 | Nền · Đa ngôn ngữ: tiếng Việt mặc định, thêm tiếng Anh (hai web, `packages/ui`, nội dung backend sinh) | T17, T18 | todo |
| T16 | UNI-2553 | Kiểm thử nghiệm thu nền, PBI-01 và kiểm thử bảo mật gọi thẳng API | T02–T15, T17, T18, T19 | todo |

### Thứ tự làm

```
Sóng 1:  T02 · T06
Sóng 2:  T03 · T05
Sóng 3:  T04
Sóng 4:  T18 · T07 · T17
Sóng 5:  T08 · T09 · T10 (API)
Sóng 6:  T10 (UI) · T11 · T12
Sóng 7:  T16
Đa ngôn ngữ: T19 (sau T17, T18)
```

**Danh mục mở lại (2026-11-14, M.Trường):** T13, T14, T15 (PBI-02 Danh mục dùng chung) từng hoãn ngày 2026-11-13, nay làm trong đợt chạy này. Chi tiết: plan mục A7.

## Backlog

| Issue | Việc | Ghi chú |
|---|---|---|
| UNI-2554 | Đăng nhập Google và gộp tài khoản theo email | Quy tắc đã chốt, xem spec mục 4.5. Cần báo BA cập nhật SRS |

## Các đợt sau (chưa tạo issue)

| Đợt | PBI | Ghi chú |
|---|---|---|
| 2 · Nội dung | 03 Ngân hàng câu hỏi, 04 Bài thi và ma trận | Làm mới, rủi ro cao nhất |
| 3 · Thi | 10–13 Làm bài, lưu bài, chấm, kết quả | Port một phần từ `lms-exam` |
| 4 · Thương mại | 05, 06, 07–09 | Port nhiều từ `lms-exam/commercial` và `exam-portal` |
| 5 · AI | 14–18 | Làm mới |
| 6 · Mobile | 19–23 | React Native |
| 7 · Vận hành | 24–26 | Port `commercial/support`, phần xuất báo cáo |
