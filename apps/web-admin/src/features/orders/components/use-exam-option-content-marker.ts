"use client";

import { useEffect, useLayoutEffect, useRef } from "react";

const useIsomorphicLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;
const STATIC_OPTION_VALUES = new Set(["", "__ALL__"]);

function syncDynamicExamOptions(container: HTMLElement | null) {
  if (!container) return;
  for (const option of container.querySelectorAll<HTMLOptionElement>("select option")) {
    if (STATIC_OPTION_VALUES.has(option.value)) {
      option.removeAttribute("data-exam-content");
    } else {
      option.setAttribute("data-exam-content", "");
    }
  }
}

/**
 * Mark Radix Select's hidden native `<option>` nodes with `data-exam-content` for dynamic exam
 * choices only, leaving static `"All exams"` / placeholder `<option>` nodes unmarked so
 * mixed-language audits do not flag seeded Vietnamese exam titles in `en` mode while still
 * checking UI chrome labels.
 */
export function useExamOptionContentMarker(depsKey: string) {
  const containerRef = useRef<HTMLDivElement>(null);

  useIsomorphicLayoutEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    syncDynamicExamOptions(container);

    if (typeof MutationObserver === "undefined") return;
    const observer = new MutationObserver(() => {
      syncDynamicExamOptions(container);
    });
    observer.observe(container, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [depsKey]);

  return containerRef;
}
