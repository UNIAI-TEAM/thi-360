"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { QUESTIONS_QUERY_KEYS } from "@/features/questions/api";
import {
  defaultQuestionImportApiDeps,
  QUESTION_IMPORT_QUERY_KEYS,
  type QuestionImportApiDeps,
} from "./api";
import type { CommitImportParams, ValidateImportParams } from "./types";

export function useImportHistory(deps: QuestionImportApiDeps = defaultQuestionImportApiDeps) {
  return useQuery({
    queryKey: QUESTION_IMPORT_QUERY_KEYS.history(),
    queryFn: () => deps.listImportHistory(),
  });
}

export function useDownloadQuestionTemplate(deps: QuestionImportApiDeps = defaultQuestionImportApiDeps) {
  return useMutation({ mutationFn: () => deps.downloadQuestionTemplate() });
}

export function useUploadQuestionFile(deps: QuestionImportApiDeps = defaultQuestionImportApiDeps) {
  return useMutation({ mutationFn: (file: File) => deps.uploadQuestionFile(file) });
}

export function useValidateImport(deps: QuestionImportApiDeps = defaultQuestionImportApiDeps) {
  return useMutation({ mutationFn: (params: ValidateImportParams) => deps.validateImport(params) });
}

export function useCommitImport(deps: QuestionImportApiDeps = defaultQuestionImportApiDeps) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (params: CommitImportParams) => deps.commitImport(params),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUESTIONS_QUERY_KEYS.all });
      void queryClient.invalidateQueries({ queryKey: QUESTION_IMPORT_QUERY_KEYS.history() });
    },
  });
}
