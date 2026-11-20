"use client";

import { StatusChip, type StatusTone } from "@thi360/ui";
import { useTranslations } from "next-intl";

import type { PromotionState, PromotionStatus } from "../types";

export function resolvePromotionBadgeKey(
  status: PromotionStatus,
  state: PromotionState,
): "STOPPED" | "RUNNING" | "UPCOMING" | "ENDED" {
  if (state === "ENDED") return "ENDED";
  if (status === "STOPPED") return "STOPPED";
  return state;
}

const TONE_MAP: Record<"STOPPED" | "RUNNING" | "UPCOMING" | "ENDED", StatusTone> = {
  RUNNING: "success",
  UPCOMING: "info",
  ENDED: "neutral",
  STOPPED: "warning",
};

export function PromotionStatusChip({
  status,
  state,
}: {
  status: PromotionStatus;
  state: PromotionState;
}) {
  const t = useTranslations("promotions.states");
  const key = resolvePromotionBadgeKey(status, state);
  return <StatusChip tone={TONE_MAP[key]}>{t(key)}</StatusChip>;
}
