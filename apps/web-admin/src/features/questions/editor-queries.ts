"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QUESTIONS_QUERY_KEYS } from "./api";
import { defaultQuestionEditorApiDeps, type QuestionEditorApiDeps } from "./editor-api";
import type { RestoreQuestionVersionRequest, SaveQuestionRequest, UpdateQuestionRequest } from "./editor-types";
import type { QuestionDetail } from "./types";

/**
 * Khóa con của chi tiết câu hỏi: làm mới `QUESTIONS_QUERY_KEYS.detail(id)` (sau khi sửa, đổi trạng thái) thì lịch sử
 * phiên bản và nơi đang dùng cũng được làm mới.
 */
export const QUESTION_EDITOR_QUERY_KEYS = {
  versions: (id: string) => [...QUESTIONS_QUERY_KEYS.detail(id), "versions"] as const,
  usage: (id: string) => [...QUESTIONS_QUERY_KEYS.detail(id), "usage"] as const,
};

export function useQuestionVersions(id: string, deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  return useQuery({
    queryKey: QUESTION_EDITOR_QUERY_KEYS.versions(id),
    queryFn: () => deps.listQuestionVersions(id),
  });
}

export function useQuestionUsage(id: string, deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  return useQuery({
    queryKey: QUESTION_EDITOR_QUERY_KEYS.usage(id),
    queryFn: () => deps.getQuestionUsage(id),
  });
}

/** Ghi câu vào bộ đệm chi tiết rồi làm mới danh sách và các truy vấn con của câu đó. */
function useStoreSavedQuestion() {
  const queryClient = useQueryClient();
  return async (question: QuestionDetail) => {
    queryClient.setQueryData(QUESTIONS_QUERY_KEYS.detail(question.id), question);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: QUESTIONS_QUERY_KEYS.lists() }),
      queryClient.invalidateQueries({ queryKey: QUESTION_EDITOR_QUERY_KEYS.versions(question.id) }),
      queryClient.invalidateQueries({ queryKey: QUESTION_EDITOR_QUERY_KEYS.usage(question.id) }),
    ]);
  };
}

export function useCreateQuestion(deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  const store = useStoreSavedQuestion();
  return useMutation({
    mutationFn: (request: SaveQuestionRequest) => deps.createQuestion(request),
    onSuccess: (question) => store(question),
  });
}

export function useUpdateQuestion(id: string, deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  const store = useStoreSavedQuestion();
  return useMutation({
    mutationFn: (request: UpdateQuestionRequest) => deps.updateQuestion(id, request),
    onSuccess: (response) => store(response.question),
  });
}

export function useDuplicateQuestion(deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  const store = useStoreSavedQuestion();
  return useMutation({
    mutationFn: (id: string) => deps.duplicateQuestion(id),
    onSuccess: (question) => store(question),
  });
}

export function useRestoreQuestionVersion(id: string, deps: QuestionEditorApiDeps = defaultQuestionEditorApiDeps) {
  const store = useStoreSavedQuestion();
  return useMutation({
    mutationFn: (request: RestoreQuestionVersionRequest) => deps.restoreQuestionVersion(id, request),
    onSuccess: (response) => store(response.question),
  });
}
