"use server";

import { revalidatePath } from "next/cache";

export async function revalidateAppearance(): Promise<void> {
  try {
    revalidatePath("/", "layout");
  } catch {
    // Bỏ qua khi chạy ngoài bối cảnh request Next.js (ví dụ Vitest).
  }
}
