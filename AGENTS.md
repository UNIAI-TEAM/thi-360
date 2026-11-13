# Thi 360 — hướng dẫn cho agent

File này chỉ đường. Nội dung nằm ở các file dưới đây; đọc đúng file cần cho việc đang làm.

| Cần biết | Đọc |
|---|---|
| Đang ở đâu, làm gì tiếp | `PROGRESS.md` |
| Task và mã issue UniAI | `TASK.md` |
| **Quy tắc và những việc không được làm** | `RULES.md` |
| Sản phẩm là gì, cho ai | `PRD.md` |
| Công nghệ, thư mục, luồng dữ liệu | `ARCHITECTURE.md` |
| Màu, chữ, thành phần giao diện | `DESIGN.md` |
| Đặc tả và kế hoạch đợt đang làm | `docs/specs/`, `docs/plans/` |

Luôn áp dụng:

- Không issue, không code. Mở phiên bằng `uniai issue get UNI-nnn --output json`.
- Không commit, push, mở PR khi người chưa yêu cầu. Không tự chuyển issue sang `done`.
- Mỗi lần dừng: bình luận `[agent]` trên issue và cập nhật `PROGRESS.md`.
