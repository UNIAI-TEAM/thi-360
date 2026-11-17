import type { ReactNode } from "react";
import { PermissionGate } from "@/features/session/permission-gate";

export default function ExamsLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="EXAM.VIEW">{children}</PermissionGate>;
}
