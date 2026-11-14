const DEV_WEB_URL = "http://localhost:3000";
const DEV_ADMIN_URL = "http://localhost:3001";

export function webUrl(): string {
  const configured = process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  return (configured || DEV_WEB_URL).replace(/\/+$/, "");
}

export function adminUrl(): string {
  const configured = process.env.NEXT_PUBLIC_THI360_ADMIN_URL?.trim();
  return (configured || DEV_ADMIN_URL).replace(/\/+$/, "");
}
