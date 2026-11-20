import type { ReactNode } from "react";

import { PermissionGate } from "@/features/session/permission-gate";

export default function EditPromotionLayout({ children }: { children: ReactNode }) {
  return <PermissionGate permission="PROMOTION.MODIFY">{children}</PermissionGate>;
}
