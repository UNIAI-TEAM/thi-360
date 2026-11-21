import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function BundlesLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="EXAM.VIEW">{children}</PermissionGate>;
}
