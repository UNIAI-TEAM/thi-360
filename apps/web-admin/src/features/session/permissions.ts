/** Một mã quyền, hoặc danh sách mã mà chỉ cần có một trong số đó. */
export type PermissionRequirement = string | readonly string[];

/** Danh sách rỗng không cấp quyền cho ai: thiếu cấu hình thì ẩn, không mở. */
export function hasPermission(
  granted: ReadonlySet<string>,
  required: PermissionRequirement,
): boolean {
  if (typeof required === "string") return granted.has(required);
  return required.some((code) => granted.has(code));
}
