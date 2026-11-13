/** Phạm vi truy cập của tài khoản: `MANAGE` được vào trang quản trị (spec mục 15.5). */
export type Access = "MANAGE" | "EXAM";

/** Thân phản hồi `GET /api/v1/me` (plan T03). */
export interface Session {
  id: string;
  username: string;
  fullName: string;
  realm: string;
  access: Access;
  permissions: string[];
}
