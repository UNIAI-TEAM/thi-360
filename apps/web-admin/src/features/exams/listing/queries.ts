"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EXAMS_QUERY_KEYS } from "../keys";
import { getExamListing, updateExamListing, uploadExamCover } from "./api";
import type { ExamListing, UpdateListingRequest } from "./types";

/** Nằm dưới nhóm `exams`: mọi thao tác ghi của bài thi làm mới luôn cả thông tin bán. */
export const LISTING_QUERY_KEYS = {
  detail: (examId: string) => ["exams", "listing", examId] as const,
};

/**
 * Bản làm việc của thông tin bán. Không tự tải lại khi quay lại cửa sổ: biểu mẫu đang sửa không bị đè, và hai người
 * cùng sửa được báo bằng 412 lúc lưu (`expectedRevision`).
 */
export function useExamListing(examId: string) {
  return useQuery({
    queryKey: LISTING_QUERY_KEYS.detail(examId),
    queryFn: () => getExamListing(examId),
    refetchOnWindowFocus: false,
  });
}

/**
 * Lưu thông tin bán. `onSaved` chạy trước khi bản mới ghi vào bộ đệm: bản mới làm biểu mẫu (khóa theo `revision`) dựng
 * lại và observer của mutation ngắt, nên callback đưa vào `mutate()` sẽ không chạy nữa. Xong thì bản mới ghi thẳng vào
 * bộ đệm của tab, các truy vấn khác của bài thi (chi tiết, kiểm tra, phiên bản, danh sách: `revision` và cờ "bản sửa
 * chưa công bố" đã đổi) tải lại.
 */
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
