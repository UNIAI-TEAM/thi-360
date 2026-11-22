"use client";

import { useEffect, useRef, useState } from "react";

/** Detects horizontal overflow on a scrollable table wrapper so keyboard focus/region roles activate only when needed. */
export function useInlineOverflow(active: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  const [overflows, setOverflows] = useState(false);

  useEffect(() => {
    if (!active) return;
    const scroller = ref.current;
    if (!scroller || typeof ResizeObserver === "undefined") return;
    const measure = () => setOverflows(scroller.scrollWidth - scroller.clientWidth > 1);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild);
    return () => observer.disconnect();
  }, [active]);

  return [ref, active && overflows] as const;
}
