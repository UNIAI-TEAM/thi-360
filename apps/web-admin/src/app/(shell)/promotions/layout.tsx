import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function PromotionsLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="PROMOTION.VIEW">{children}</PermissionGate>;
}
