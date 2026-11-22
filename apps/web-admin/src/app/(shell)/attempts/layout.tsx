import type { ReactNode } from "react";
import { PermissionGate } from "@/features/session/permission-gate";

export default function AttemptsLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="ATTEMPT.VIEW">{children}</PermissionGate>;
}
