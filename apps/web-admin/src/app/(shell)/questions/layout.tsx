import type { ReactNode } from "react";
import { PermissionGate } from "@/features/session/permission-gate";

export default function QuestionsLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="QUESTION.VIEW">{children}</PermissionGate>;
}
