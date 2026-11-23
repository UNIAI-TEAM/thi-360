"use client";

import { ConfirmDialog } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

export function useUnsavedChangesGuard(hasUnsavedChanges: boolean) {
  const router = useRouter();
  const [pendingHref, setPendingHref] = useState<string | null>(null);
  const bypass = useRef(false);

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (bypass.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [hasUnsavedChanges]);

  useEffect(() => {
    if (!hasUnsavedChanges) return;
    const handleClick = (event: MouseEvent) => {
      if (bypass.current || event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const target = event.target;
      if (!(target instanceof Element)) return;
      const anchor = target.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement)) return;
      if (anchor.hasAttribute("download") || anchor.target === "_blank") return;
      const raw = anchor.getAttribute("href");
      if (!raw || raw.startsWith("#")) return;
      let url: URL;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }
      if (url.origin !== window.location.origin) return;
      const next = `${url.pathname}${url.search}${url.hash}`;
      if (url.pathname === window.location.pathname) return;
      event.preventDefault();
      event.stopPropagation();
      setPendingHref(next);
    };
    document.addEventListener("click", handleClick, true);
    return () => document.removeEventListener("click", handleClick, true);
  }, [hasUnsavedChanges]);

  return {
    pendingHref,
    stay: () => setPendingHref(null),
    leave: () => {
      if (!pendingHref) return;
      bypass.current = true;
      const href = pendingHref;
      setPendingHref(null);
      router.push(href);
    },
  };
}

export function UnsavedChangesDialog({ guard }: { guard: ReturnType<typeof useUnsavedChangesGuard> }) {
  const t = useTranslations("exams.structure.unsaved");
  return (
    <ConfirmDialog
      open={guard.pendingHref !== null}
      onOpenChange={(open) => {
        if (!open) guard.stay();
      }}
      title={t("title")}
      description={t("description")}
      confirmLabel={t("leave")}
      cancelLabel={t("stay")}
      onConfirm={guard.leave}
    />
  );
}
