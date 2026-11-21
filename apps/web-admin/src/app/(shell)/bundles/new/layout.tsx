import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function NewBundleLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="EXAM.MODIFY">{children}</PermissionGate>;
}
