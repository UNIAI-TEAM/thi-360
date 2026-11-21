export const BUNDLE_AUDIT_ACTIONS = [
  "BUNDLE_CREATED",
  "BUNDLE_UPDATED",
  "BUNDLE_PUBLISHED",
  "BUNDLE_DISCONTINUED",
] as const;

export type BundleAuditAction = (typeof BUNDLE_AUDIT_ACTIONS)[number];
