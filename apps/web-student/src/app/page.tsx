import { cn } from "@thi360/ui";

export default function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <section
        className={cn("w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-xl")}
      >
        <p className="text-[13px] font-semibold uppercase tracking-[0.08em] text-primary-600">
          Thi 360
        </p>
        <h1 className="mt-2 text-[22px] font-bold leading-[30px] text-primary-900">Thi 360 — Luyện thi HSA, QDA</h1>
        <p className="mt-2 text-text-muted">
          Khung ứng dụng đã sẵn sàng. Chức năng sẽ được thêm ở các task tiếp theo.
        </p>
      </section>
    </main>
  );
}
