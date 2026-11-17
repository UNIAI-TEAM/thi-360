import type { ReactNode } from "react";
import { PermissionGate } from "@/features/session/permission-gate";

export default function GradingLayout({ children }: { children: ReactNode }) {
  return (
    <PermissionGate permission="GRADING.VIEW" flag="grading">
      {children}
    </PermissionGate>
  );
}
