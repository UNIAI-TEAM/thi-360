import type { KeyboardEvent } from "react";

/** Moves selection across enabled radio items using arrow/Home/End keys. */
export function handleRadioGroupKeyDown<T extends string>(
  event: KeyboardEvent<HTMLElement>,
  current: T,
  options: readonly { value: T; disabled?: boolean }[],
  onChange: (next: T) => void,
) {
  const enabled = options.filter((opt) => !opt.disabled);
  if (enabled.length === 0) return;

  const currentIdx = enabled.findIndex((opt) => opt.value === current);
  let targetIdx = -1;

  if (event.key === "ArrowDown" || event.key === "ArrowRight") {
    targetIdx = currentIdx === -1 ? 0 : (currentIdx + 1) % enabled.length;
  } else if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
    targetIdx = currentIdx === -1 ? enabled.length - 1 : (currentIdx - 1 + enabled.length) % enabled.length;
  } else if (event.key === "Home") {
    targetIdx = 0;
  } else if (event.key === "End") {
    targetIdx = enabled.length - 1;
  }

  if (targetIdx === -1) return;
  event.preventDefault();
  onChange(enabled[targetIdx]!.value);
}
