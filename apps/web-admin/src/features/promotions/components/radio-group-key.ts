import type { KeyboardEvent } from "react";

/**
 * Roving tabindex + Arrow-key handler for custom `role="radiogroup"` controls.
 */
export function handleRadioGroupKeyDown<T extends string>(
  event: KeyboardEvent<HTMLButtonElement>,
  options: readonly T[],
  currentIndex: number,
  onSelect: (value: T) => void,
) {
  let delta = 0;
  if (event.key === "ArrowRight" || event.key === "ArrowDown") {
    delta = 1;
  } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
    delta = -1;
  } else {
    return;
  }
  event.preventDefault();
  const nextIndex = (currentIndex + delta + options.length) % options.length;
  const nextValue = options[nextIndex];
  if (nextValue === undefined) return;
  onSelect(nextValue);
  const group = event.currentTarget.closest('[role="radiogroup"]');
  const buttons = group?.querySelectorAll<HTMLButtonElement>('[role="radio"]:not(:disabled)');
  buttons?.[nextIndex]?.focus();
}
