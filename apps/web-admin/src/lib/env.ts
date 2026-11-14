const DEV_WEB_URL = "http://localhost:3000";

export function webUrl(): string {
  const configured = process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  return (configured || DEV_WEB_URL).replace(/\/+$/, "");
}
