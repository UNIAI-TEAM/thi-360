"use client";

import { Button, Card, EmptyState, PageHeader, Skeleton } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useState } from "react";
import { useContentReference } from "@/features/content/queries";
import type { QuestionType } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { useQuestion } from "../queries";
import { QuestionEditorForm } from "./question-editor-form";

export type QuestionEditorViewProps = { mode: "create"; type: QuestionType } | { mode: "edit"; questionId: string };

/** Trang `/questions/new?type=…` và `/questions/[id]/edit`: tải danh mục (và câu hỏi khi sửa) rồi dựng biểu mẫu. */
export function QuestionEditorView(props: QuestionEditorViewProps) {
  return props.mode === "create" ? <CreateView type={props.type} /> : <EditView questionId={props.questionId} />;
}

function CreateView({ type }: { type: QuestionType }) {
  const t = useTranslations("questions.editor");
  const referenceQuery = useContentReference();
  if (referenceQuery.isPending) return <EditorSkeleton title={t("createTitle")} />;
  if (referenceQuery.isError) {
    return (
      <EditorError
        title={t("createTitle")}
        heading={t("loadErrorTitle")}
        description={t("loadErrorDescription")}
        onRetry={() => void referenceQuery.refetch()}
      />
    );
  }
  return <QuestionEditorForm mode="create" type={type} reference={referenceQuery.data} />;
}

function EditView({ questionId }: { questionId: string }) {
  const [reloadToken, setReloadToken] = useState(0);
  const t = useTranslations("questions.editor");
  const questionQuery = useQuestion(questionId);
  const referenceQuery = useContentReference();
  if (questionQuery.isPending || referenceQuery.isPending) return <EditorSkeleton title={t("editTitle")} />;
  if (questionQuery.isError && getErrorStatus(questionQuery.error) === 404) {
    return (
      <EditorError
        title={t("editTitle")}
        heading={t("notFoundTitle")}
        description={t("notFoundDescription")}
        backHref="/questions"
        backLabel={t("backToList")}
      />
    );
  }
  if (questionQuery.isError || referenceQuery.isError) {
    return (
      <EditorError
        title={t("editTitle")}
        heading={t("loadErrorTitle")}
        description={t("loadErrorDescription")}
        onRetry={() => {
          if (questionQuery.isError) void questionQuery.refetch();
          if (referenceQuery.isError) void referenceQuery.refetch();
        }}
      />
    );
  }
  const question = questionQuery.data;
  return (
    <QuestionEditorForm
      key={`${question.id}-${question.version}-${reloadToken}`}
      mode="edit"
      question={question}
      reference={referenceQuery.data}
      onReload={async () => {
        // Xung đột 412: nạp lại câu mới nhất rồi dựng lại biểu mẫu từ bản đó (bỏ phần đã nhập, theo lựa chọn của người dùng).
        await questionQuery.refetch();
        setReloadToken((token) => token + 1);
      }}
    />
  );
}

function EditorSkeleton({ title }: { title: string }) {
  const t = useTranslations("questions.editor");
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={title} />
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-40" />
        <Skeleton className="h-36" />
        <Skeleton className="h-56" />
      </div>
    </div>
  );
}

function EditorError({
  title,
  heading,
  description,
  onRetry,
  backHref,
  backLabel,
}: {
  title: string;
  heading: string;
  description: string;
  onRetry?: () => void;
  backHref?: string;
  backLabel?: string;
}) {
  const t = useTranslations("questions.editor");
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title={title} />
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={heading}
          description={description}
          action={
            backHref ? (
              <Button asChild variant="secondary">
                <Link href={backHref}>{backLabel}</Link>
              </Button>
            ) : (
              <Button variant="secondary" onClick={onRetry}>
                {t("retry")}
              </Button>
            )
          }
        />
      </Card>
    </div>
  );
}
