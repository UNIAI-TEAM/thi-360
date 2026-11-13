"use server";

import { revalidatePath, revalidateTag } from "next/cache";
import { cookies } from "next/headers";

const SESSION_COOKIES = ["thi360_ctx", "thi360_at"] as const;

export async function revalidateAppearance(): Promise<void> {
  try {
    const jar = await cookies();
    const hasSession = SESSION_COOKIES.some((name) => {
      const value = jar.get(name)?.value;
      return typeof value === "string" && value.trim().length > 0;
    });
    if (!hasSession) return;
    revalidateTag("appearance", "max");
    revalidatePath("/", "layout");
  } catch {
    // Bỏ qua khi chạy ngoài bối cảnh request Next.js (ví dụ Vitest).
  }
}
