import type { Session } from "@/features/session/types";

export const TEST_ADMIN_SESSION: Session = {
  id: "00000000-0000-4000-8000-000000000002",
  username: "ops.admin",
  fullName: "Vận hành",
  realm: "APP",
  access: "MANAGE",
  homeUrl: "http://localhost:3001",
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
