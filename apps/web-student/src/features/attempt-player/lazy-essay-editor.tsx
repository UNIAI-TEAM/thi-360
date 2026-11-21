"use client";

import { Skeleton } from "@thi360/ui";
import type { EssayEditorProps } from "@thi360/ui/question";
import { useTranslations } from "next-intl";
import dynamic from "next/dynamic";

function EssayEditorFallback({ minHeightClass = "min-h-48" }: { minHeightClass?: string }) {
  const t = useTranslations("attempt.editor");
  return (
    <div
      role="status"
      aria-live="polite"
      className="space-y-2 rounded-lg border border-border-strong bg-surface p-3.5"
    >
      <div className="flex items-center gap-2 border-b border-border pb-2">
        <Skeleton className="h-7 w-7 rounded-md" />
        <Skeleton className="h-7 w-7 rounded-md" />
        <Skeleton className="h-7 w-7 rounded-md" />
        <Skeleton className="h-7 w-7 rounded-md" />
      </div>
      <div className={minHeightClass}>
        <p className="text-caption text-text-muted">{t("loading")}</p>
      </div>
    </div>
  );
}

const DynamicEssayEditor = dynamic(
  () => import("./essay-editor").then((mod) => mod.EssayEditor),
  {
    ssr: false,
    loading: () => <EssayEditorFallback />,
  },
);

export function LazyEssayEditor(props: EssayEditorProps) {
  return <DynamicEssayEditor {...props} />;
}
