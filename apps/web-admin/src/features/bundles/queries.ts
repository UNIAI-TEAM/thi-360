import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { keepPreviousWhileSameScope } from "@/lib/api/keep-previous";

import {
  createBundle,
  discontinueBundle,
  fetchExamKindOptions,
  getBundle,
  listBundleCandidates,
  listBundles,
  publishBundle,
  updateBundle,
} from "./api";
import { bundleKeys } from "./keys";
import type { BundlesListQueryParams, CreateBundleRequest, UpdateBundleRequest } from "./types";

export function useBundlesListQuery(params: BundlesListQueryParams) {
  const key = bundleKeys.list(params);
  return useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listBundles(params, signal),
    placeholderData: keepPreviousWhileSameScope(key),
  });
}

export function useBundleDetailQuery(id: string, enabled = true) {
  return useQuery({
    queryKey: bundleKeys.detail(id),
    queryFn: ({ signal }) => getBundle(id, signal),
    enabled: Boolean(id) && enabled,
  });
}

export function useBundleCandidatesQuery(examKind: string, enabled = true) {
  return useQuery({
    queryKey: bundleKeys.candidates(examKind),
    queryFn: ({ signal }) => listBundleCandidates(examKind, signal),
    enabled: Boolean(examKind) && enabled,
  });
}

export function useExamKindOptionsQuery(enabled = true) {
  return useQuery({
    queryKey: bundleKeys.examKinds(),
    queryFn: ({ signal }) => fetchExamKindOptions(signal),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateBundleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateBundleRequest) => createBundle(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: bundleKeys.lists() });
    },
  });
}

export function useUpdateBundleMutation(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateBundleRequest) => updateBundle(id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(bundleKeys.detail(id), updated);
      void queryClient.invalidateQueries({ queryKey: bundleKeys.lists() });
    },
  });
}

export function usePublishBundleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, expectedRevision }: { id: string; expectedRevision: number }) =>
      publishBundle(id, expectedRevision),
    onSuccess: (updated, variables) => {
      queryClient.setQueryData(bundleKeys.detail(variables.id), updated);
      void queryClient.invalidateQueries({ queryKey: bundleKeys.lists() });
    },
  });
}

export function useDiscontinueBundleMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, expectedRevision }: { id: string; expectedRevision: number }) =>
      discontinueBundle(id, expectedRevision),
    onSuccess: (updated, variables) => {
      queryClient.setQueryData(bundleKeys.detail(variables.id), updated);
      void queryClient.invalidateQueries({ queryKey: bundleKeys.lists() });
    },
  });
}
