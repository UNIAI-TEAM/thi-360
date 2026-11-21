import type { BundlesListQueryParams } from "./types";

export const bundleKeys = {
  all: ["bundles"] as const,
  lists: () => [...bundleKeys.all, "list"] as const,
  list: (params: BundlesListQueryParams) => [...bundleKeys.lists(), params] as const,
  details: () => [...bundleKeys.all, "detail"] as const,
  detail: (id: string) => [...bundleKeys.details(), id] as const,
  candidatesBase: () => [...bundleKeys.all, "candidates"] as const,
  candidates: (examKind: string) => [...bundleKeys.candidatesBase(), examKind] as const,
  examKinds: () => [...bundleKeys.all, "exam-kinds"] as const,
};
