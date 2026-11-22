# Đặc tả kỹ thuật — Giao bài thi cho Lớp học (`UNI-2774`, TM §15 Q10)

> Ngày: 2026-11-22
> Issue: `UNI-2774`
> Phụ thuộc: Quản lý lớp học (`classroom`), Xuất bản bài thi (`exam.spi.PublishedExamPort`), Quyền làm bài nguồn `CLASS` (`commerce.entitlement`, quy tắc C12 / C15 / E21 / E25)

## 1. Mục tiêu và quyết định chốt

Hoàn thiện luồng quản trị viên / giáo viên có quyền `CLASS.MODIFY` giao một hoặc nhiều bài thi đang công bố (`PUBLISHED`) cho một lớp học đang hoạt động (`ACTIVE`), cấp quyền làm bài nguồn `CLASS` cho các học viên thuộc lớp, hỗ trợ tự động cấp cho học viên vào lớp sau, và thu hồi các lượt chưa dùng khi thu hồi đợt giao bài hoặc khi học viên rời lớp.

Các điểm chốt nghiệp vụ:

1. **Ai được xem / giao / thu hồi:**
   - Xem danh sách đợt giao bài của lớp: quyền `CLASS.VIEW`.
   - Tra cứu bài thi khả dụng để giao, giao bài thi cho lớp, bật/tắt tự động cấp thành viên mới, thu hồi đợt giao bài: quyền `CLASS.MODIFY`.
2. **Điều kiện giao bài:**
   - Lớp tồn tại, chưa xóa mềm và ở trạng thái `ACTIVE` (lớp `INACTIVE` trả `409 CLASS_INACTIVE`).
   - Bài thi ở trạng thái `PUBLISHED` trên `PublishedExamPort.findServing(examId)` (bài chưa công bố hoặc `DISCONTINUED` trả `409 EXAM_NOT_PUBLISHED`). Cho phép giao cả bài thi `PUBLISHED` chưa bật bán lẻ thương mại (`listing == null`).
   - Số lượt cấp mỗi học viên (`attemptsGranted`): số nguyên `1..100`.
   - Mức xem kết quả sau nộp bài (`resultView`): `SCORE`, `SCORE_AND_ANSWERS` hoặc `FULL_SOLUTION` (mặc định trên giao diện lấy từ `listing.resultView()` của phiên bản đang phục vụ; nếu bài thi chưa có `listing` thì mặc định `SCORE`).
   - Cờ `autoGrantNewMembers`: mặc định `true`.
   - Ghi chú giao bài (`note`): tùy chọn, tối đa 500 ký tự.
   - Bắt buộc header `Idempotency-Key` trên lệnh `POST /exam-assignments` (thiếu trả `400 IDEMPOTENCY_KEY_REQUIRED`, dùng lại khác thân trả `409 IDEMPOTENCY_KEY_REUSED`).
3. **Cấp quyền làm bài nguồn `CLASS`:**
   - Mỗi học viên hiện hữu đang hoạt động trong lớp nhận một dòng `commerce.entitlement_grant` (`kind = 'CLASS'`, `class_assignment_id = assignment.id`) gắn vào dòng `commerce.entitlement` có `(user_id, exam_id, source = 'CLASS', source_ref = class_id)`.
   - Ràng buộc duy nhất `(class_assignment_id, entitlement_id)` bảo đảm mỗi đợt giao bài chỉ cấp tối đa một lần cho mỗi học viên trong lớp (idempotent). Nếu quản trị viên tạo một đợt giao bài mới cho cùng bài thi trên cùng lớp, các lượt mới được cộng dồn vào `entitlement` nguồn `CLASS` của lớp đó theo FIFO (`seq`, quy tắc E21).
   - Snapshot trên `class_exam_assignment` và `entitlement_grant`: `exam_version_no` mới nhất đang phục vụ, `option_codes` là toàn bộ mã lựa chọn đang phục vụ của bài thi (để học viên được chọn mọi tổ hợp/nhánh đang phục vụ của đề), `benefits` chụp từ cấu hình niêm yết hiện hành (hoặc `{}` nếu chưa có niêm yết), và `result_view` theo lựa chọn của người giao.
4. **Tự động đồng bộ khi biến động thành viên lớp:**
   - Khi thêm học viên vào lớp (`POST /classes/{id}/members`, gán lớp lúc tạo tài khoản / nhập Excel hàng loạt, hoặc chuyển lớp sang lớp đích): với mọi đợt giao `ACTIVE` có `auto_grant_new_members = true` của lớp đích, hệ thống tự động cấp `entitlement_grant` cho học viên mới (bỏ qua an toàn nếu học viên đã từng được cấp ở đợt giao đó).
   - Khi loại học viên khỏi lớp (`DELETE /classes/{id}/members/{userId}`), chuyển lớp khỏi lớp nguồn, hoặc dọn thành viên khi xóa tài khoản: thu hồi toàn bộ lượt chưa dùng (`revokeUnused`) trên các `entitlement_grant` nguồn `CLASS` thuộc lớp nguồn của học viên đó; các lượt đang làm dở (`RESERVED`) hoặc đã nộp (`USED`) được giữ nguyên theo nguyên tắc C15.
   - Khi thu hồi một đợt giao bài (`POST /exam-assignments/{assignmentId}/revoke`) hoặc xóa mềm lớp học (`DELETE /classes/{id}`): đánh dấu đợt giao `REVOKED` và thu hồi toàn bộ lượt chưa dùng của đợt giao đó trên mọi học viên theo nguyên tắc C15.

## 2. Thiết kế dữ liệu & Ranh giới Module

### 2.1 Bảng dữ liệu (`commerce`)

Migration `V202611220200__class_exam_assignment.sql`:

- Bảng mới `commerce.class_exam_assignment`:
  - `id uuid PRIMARY KEY`
  - `class_id uuid NOT NULL` (tham chiếu logic tới `classroom.class_room.id`, không FK xuyên schema theo RULES 4)
  - `exam_id uuid NOT NULL`
  - `exam_version_no integer NOT NULL`
  - `exam_code text NOT NULL`, `exam_name text NOT NULL`
  - `attempts_granted integer NOT NULL CHECK (attempts_granted BETWEEN 1 AND 100)`
  - `result_view text NOT NULL CHECK (result_view IN ('SCORE', 'SCORE_AND_ANSWERS', 'FULL_SOLUTION'))`
  - `option_codes text[] NOT NULL DEFAULT '{}'`
  - `benefits jsonb NOT NULL DEFAULT '{}'::jsonb`
  - `auto_grant_new_members boolean NOT NULL DEFAULT true`
  - `note text`
  - `status text NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED'))`
  - `assigned_by uuid NOT NULL`, `assigned_at timestamptz NOT NULL`
  - `revoked_by uuid`, `revoked_at timestamptz`, `revoke_reason text`
  - `updated_at timestamptz NOT NULL`, `version bigint NOT NULL DEFAULT 0`
- Mở rộng `commerce.entitlement_grant`:
  - Thêm cột `class_assignment_id uuid REFERENCES commerce.class_exam_assignment (id)`
  - Ràng buộc duy nhất `uq_entitlement_grant_class_assignment UNIQUE (class_assignment_id, entitlement_id)`
  - Mở rộng `kind` nhận `'PURCHASE'`, `'COMPENSATION'`, `'CLASS'` và cập nhật `ck_entitlement_grant_kind_fields`.

### 2.2 Ranh giới Module (`ModularityTest`)

- Tạo gói mở `vn.unicom.thi360.classroom.spi` (`@NamedInterface("spi")`):
  - `ClassroomLookupPort`: tra thông tin lớp (`ClassroomInfo`: `id`, `code`, `name`, `active`, `deleted`) và danh sách `userId` thành viên đang hoạt động trong lớp.
  - Sự kiện nội bộ trong cùng transaction: `ClassMembersAdded`, `ClassMemberRemoved`, `ClassDeleted`.
- Module `commerce` phụ thuộc một chiều vào `classroom.spi` (chiều `commerce -> classroom.spi`, `classroom` không phụ thuộc `commerce`, bảo toàn DAG không chu trình).

## 3. Hợp đồng API

1. `GET /api/v1/admin/classes/{classId}/exam-assignments` (`CLASS.VIEW`): danh sách các đợt giao bài của lớp (mới nhất đứng đầu), kèm thống kê số học viên đã nhận quyền và số lượt đã dùng / còn lại / đã thu hồi.
2. `GET /api/v1/admin/classes/{classId}/assignable-exams` (`CLASS.MODIFY`): danh sách các bài thi đang ở trạng thái `PUBLISHED` để chọn giao (mã, tên, kỳ thi, hình thức, phiên bản, mức xem kết quả mặc định).
3. `POST /api/v1/admin/classes/{classId}/exam-assignments` (`CLASS.MODIFY`, bắt buộc `Idempotency-Key`): giao một hoặc nhiều `examIds` cho lớp, cấp quyền ngay cho học viên hiện hữu và ghi audit `CLASS_EXAM_ASSIGNED`.
4. `PATCH /api/v1/admin/classes/{classId}/exam-assignments/{assignmentId}` (`CLASS.MODIFY`): bật/tắt `autoGrantNewMembers` trên đợt giao đang `ACTIVE`, ghi audit `CLASS_EXAM_ASSIGNMENT_UPDATED`.
5. `POST /api/v1/admin/classes/{classId}/exam-assignments/{assignmentId}/revoke` (`CLASS.MODIFY`): thu hồi đợt giao bài, thu hồi các lượt chưa dùng, ghi audit `CLASS_EXAM_ASSIGNMENT_REVOKED`.
6. `GET /api/v1/me/exams`: trả cả thẻ bài thi từ quyền nguồn `PURCHASE` và nguồn `CLASS`; bổ sung trường tùy chọn nullable `source` (`PURCHASE` | `CLASS` | `null`), `classId`, `className` trên `MyExamCard`. Thẻ nguồn `CLASS` hiển thị nhãn **Bài theo lớp · \<Tên lớp\>** và không hiển thị nút mua lẻ trực tiếp trên thẻ quyền lớp; trên màn chuẩn bị `/prepare/[entitlementId]`, `grantSource.source = CLASS` hiển thị rõ nguồn bài giao theo lớp.
