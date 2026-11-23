"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EXAMS_QUERY_KEYS, LISTING_QUERY_KEYS } from "../keys";
import { getExamListing, updateExamListing, uploadExamCover } from "./api";
import type { ExamListing, UpdateListingRequest } from "./types";

export function useExamListing(examId: string) {
  return useQuery({
    queryKey: LISTING_QUERY_KEYS.detail(examId),
    queryFn: () => getExamListing(examId),
    refetchOnWindowFocus: false,
  });
}

export function useSaveExamListing(examId: string, { onSaved }: { onSaved?: (saved: ExamListing) => void } = {}) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateListingRequest) => updateExamListing(examId, request),
    onSuccess: async (saved: ExamListing) => {
      onSaved?.(saved);
      queryClient.setQueryData(LISTING_QUERY_KEYS.detail(examId), saved);
      await queryClient.invalidateQueries({
        queryKey: EXAMS_QUERY_KEYS.all,
        predicate: (query) => query.queryKey[1] !== "listing",
      });
    },
  });
}

/** Tải ảnh bìa lên; chưa đổi gì ở bài thi cho tới khi bấm Lưu. */
export function useUploadExamCover(examId: string) {
  return useMutation({ mutationFn: (file: File) => uploadExamCover(examId, file) });
}
