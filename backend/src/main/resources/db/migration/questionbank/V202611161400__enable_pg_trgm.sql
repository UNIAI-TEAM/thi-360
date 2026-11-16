-- BE-1 (UNI-2607): phần mở rộng trigram cho tìm kiếm câu hỏi (spec D16, plan B1).
-- Trên môi trường thật, nếu tài khoản ứng dụng không có quyền tạo extension thì DBA tạo trước
-- (xem README, mục Triển khai); khi đã tồn tại, lệnh này là no-op.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
