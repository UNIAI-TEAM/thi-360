import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function OrdersLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="ORDER.VIEW">{children}</PermissionGate>;
}
