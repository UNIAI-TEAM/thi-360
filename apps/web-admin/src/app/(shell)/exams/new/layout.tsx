import type { ReactNode } from "react";
import { PermissionGate } from "@/features/session/permission-gate";

/** Tạo bài thi cần `EXAM.MODIFY` (TaoBaiThi2B, trạng thái "Thiếu quyền tạo bài thi"); `EXAM.VIEW` đã kiểm ở khung `/exams`. */
export default function NewExamLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="EXAM.MODIFY">{children}</PermissionGate>;
}
