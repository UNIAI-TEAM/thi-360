import { cn } from "@thi360/ui";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

const DEFAULT_STAGE_CLASS =
  "relative isolate flex min-h-screen flex-col items-center gap-4 overflow-clip bg-page p-4 sm:p-6 md:justify-center";

export const AUTH_BRAND_PANEL_CLASS =
  "relative isolate flex min-w-0 flex-1 flex-col gap-6 overflow-hidden bg-primary-50 p-6 sm:p-10 md:border-r md:border-border/60 md:p-12";

const AMBIENT_WASH_STYLE = {
  backgroundImage: [
    "radial-gradient(560px 420px at 28% 22%, color-mix(in srgb, var(--color-primary-500) 20%, transparent), transparent 70%)",
    "radial-gradient(560px 440px at 74% 78%, color-mix(in srgb, var(--color-primary-600) 18%, transparent), transparent 70%)",
  ].join(", "),
} as const;

const MESH_PRIMARY_STYLE = {
  backgroundColor: "color-mix(in srgb, var(--color-primary-200) 70%, var(--color-primary-500) 16%)",
} as const;

const MESH_ACCENT_STYLE = {
  backgroundColor: "color-mix(in srgb, var(--color-primary-200) 68%, var(--color-accent-500) 24%)",
} as const;

/**
 * Lop nen trang xac thuc (Phuong an 2 — Geometric Orbital Rings + Soft Mesh, UNI-2775):
 * - Chi dung bien token ngu nghia tu `tokens.css` (`--color-primary-*`, `--color-accent-500`, `--color-page`) de tu
 *   thich ung voi moi bang mau he thong (Xanh la mac dinh, Indigo...) va ca hai che do Sang / Toi.
 * - Dat gradient/SVG trong lop trang tri `aria-hidden` co `-z-10` thay vi dat `background-image` len `<main>`, giu
 *   `<main>` co nen dac `bg-page` va moi khung SVG nam gon trong `[0, 100%]` (`viewBox="170 0 350 520"`) de khong bao
 *   gio phinh `scrollWidth` o kho hep 320px-390px.
 */
export function AuthBackdrop() {
  return (
    <div
      aria-hidden="true"
      data-testid="auth-backdrop"
      className="pointer-events-none absolute inset-0 -z-10 select-none overflow-hidden"
    >
      <div className="absolute inset-0" style={AMBIENT_WASH_STYLE} />
      <div
        className="absolute left-[max(0px,calc(50%-540px))] top-[6%] h-[420px] w-[min(440px,75vw)] rounded-full opacity-55 blur-3xl"
        style={MESH_PRIMARY_STYLE}
      />
      <div
        className="absolute bottom-[6%] right-[max(0px,calc(50%-520px))] h-[380px] w-[min(400px,75vw)] rounded-full opacity-50 blur-3xl"
        style={MESH_ACCENT_STYLE}
      />
      <svg
        viewBox="170 0 350 520"
        fill="none"
        className="absolute left-0 top-1/2 h-[360px] w-[min(210px,45vw)] -translate-y-1/2 text-primary-500 opacity-15 sm:h-[520px] sm:w-[min(350px,45vw)] sm:opacity-22"
      >
        <circle cx="260" cy="260" r="240" stroke="currentColor" strokeWidth="1.2" strokeDasharray="6 8" />
        <circle cx="260" cy="260" r="185" stroke="currentColor" strokeWidth="1.2" />
        <circle cx="260" cy="260" r="125" stroke="currentColor" strokeWidth="1" opacity="0.6" />
        <circle cx="412" cy="148" r="8" fill="currentColor" opacity="0.5" />
        <circle cx="385" cy="360" r="5" fill="currentColor" opacity="0.4" />
      </svg>
      <svg
        viewBox="170 0 350 520"
        fill="none"
        className="absolute right-0 top-1/2 h-[360px] w-[min(210px,45vw)] -translate-y-1/2 rotate-180 text-primary-500 opacity-15 sm:h-[520px] sm:w-[min(350px,45vw)] sm:opacity-22"
      >
        <circle cx="260" cy="260" r="240" stroke="currentColor" strokeWidth="1.2" strokeDasharray="6 8" />
        <circle cx="260" cy="260" r="185" stroke="currentColor" strokeWidth="1.2" />
        <circle cx="260" cy="260" r="125" stroke="currentColor" strokeWidth="1" opacity="0.6" />
        <circle cx="412" cy="148" r="8" fill="currentColor" opacity="0.5" />
      </svg>
    </div>
  );
}

/**
 * Lop trang tri ben trong cot thuong hieu cua trang Dang nhap / Dang ky: chuyen sac cheo nhe `primary-50 -> primary-100`
 * kem song truu tuong SVG ban trong suot o day cot.
 */
export function AuthBrandDecor() {
  return (
    <div
      aria-hidden="true"
      data-testid="auth-brand-decor"
      className="pointer-events-none absolute inset-0 -z-10 select-none overflow-hidden"
    >
      <div className="absolute inset-0 bg-linear-to-br from-primary-50 via-primary-50 to-primary-100" />
      <svg
        viewBox="0 0 500 150"
        preserveAspectRatio="none"
        fill="none"
        className="absolute inset-x-0 bottom-0 h-36 w-full text-primary-600 opacity-15"
      >
        <path d="M0 95 C130 40, 260 140, 500 55 L500 150 L0 150 Z" fill="currentColor" opacity="0.5" />
        <path d="M0 118 C180 75, 320 145, 500 88 L500 150 L0 150 Z" fill="currentColor" opacity="0.7" />
      </svg>
    </div>
  );
}

export function AuthPageStage({
  children,
  className,
  ...props
}: ComponentPropsWithoutRef<"main"> & { children: ReactNode }) {
  return (
    <main className={cn(DEFAULT_STAGE_CLASS, className)} {...props}>
      {children}
      <AuthBackdrop />
    </main>
  );
}
