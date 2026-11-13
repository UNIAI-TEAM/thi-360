import type { Session } from "./types";

/** Phiên giả khi `NEXT_PUBLIC_DEV_FAKE_SESSION=1`: đủ 13 quyền của đợt 1 (plan B6). */
export const FAKE_SESSION: Session = {
  id: "00000000-0000-4000-8000-000000000002",
  username: "ops.admin",
  fullName: "Vận hành",
  realm: "THI360",
  access: "MANAGE",
  permissions: [
    "USER.VIEW",
    "USER.MODIFY",
    "USER.ASSIGN",
    "USER.LEARNING_VIEW",
    "CLASS.VIEW",
    "CLASS.MODIFY",
    "ROLE.VIEW",
    "ROLE.MODIFY",
    "PERMISSION.VIEW",
    "CATALOG.VIEW",
    "CATALOG.MODIFY",
    "AUDIT.VIEW",
    "APPEARANCE.MODIFY",
  ],
};
