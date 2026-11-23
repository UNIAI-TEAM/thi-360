export const DISPLAY_NUMBER_FORMAT = { maximumFractionDigits: 2 } as const;

export function roundForDisplay(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
