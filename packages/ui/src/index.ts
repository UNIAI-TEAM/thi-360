import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Gộp class Tailwind, class viết sau thắng class viết trước. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
