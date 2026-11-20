import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function NewPromotionLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="PROMOTION.MODIFY">{children}</PermissionGate>;
}
