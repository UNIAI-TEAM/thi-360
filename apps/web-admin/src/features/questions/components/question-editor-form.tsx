"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, ConfirmDialog, LiveStatus, notify, PageHeader, Textarea } from "@thi360/ui";
import { Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { FormProvider, useForm, useFormContext, useWatch } from "react-hook-form";
import { MockDataBanner } from "@/components/mock-data-banner";
import type { ContentReference, QuestionType } from "@/features/content/types";
import { MockApiError } from "@/lib/mock/content-mock";
import { emptyEditorValues, toEditorValues, toSaveRequest, type QuestionEditorValues } from "../editor-form";
import { useCreateQuestion, useUpdateQuestion } from "../editor-queries";
import { CONTENT_MAX, questionEditorSchema, SOLUTION_MAX } from "../editor-schema";
import { editCreatesNewVersion } from "../editor-versioning";
import type { QuestionDetail } from "../types";
import { QuestionBodyEditor } from "./question-editor-body";
import { ClassificationSection } from "./question-editor-classification";
import { QuestionSetEditor } from "./question-editor-set";
import { EditorAnnounceProvider, EditorSection, FieldErrorText, useEditorError } from "./question-editor-shared";

export type QuestionEditorFormProps =
  | { mode: "create"; type: QuestionType; question?: undefined; reference: ContentReference }
  | { mode: "edit"; question: QuestionDetail; type?: undefined; reference: ContentReference };

const BODY_TITLE_KEY: Record<QuestionType, string> = {
  SINGLE_CHOICE: "options",
  MULTIPLE_CHOICE: "options",
  TRUE_FALSE: "statements",
  FILL_IN: "blanks",
  DRAG_DROP: "dragDrop",
  QUESTION_SET: "children",
  ESSAY: "essay",
};

/**
 * Biểu mẫu soạn câu hỏi theo dạng (canvas `CauHoi*Form`): khung chung (Phân loại, Đề bài, Lời giải) và khối đáp án
 * theo dạng. Câu mới lưu ở trạng thái Nháp; sửa câu Sẵn sàng tạo phiên bản mới (thông báo ở đầu biểu mẫu).
 * Trình soạn thảo đầy đủ (canvas trang 07) chưa làm: đề bài, lời giải, đoạn dẫn là ô nhập nhiều dòng.
 */
export function QuestionEditorForm(props: QuestionEditorFormProps) {
  const t = useTranslations("questions.editor");
  const tType = useTranslations("content.questionType");
  const format = useFormatter();
  const router = useRouter();
  const formId = useId();
  const [liveMessage, setLiveMessage] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const question = props.question ?? null;
  const type = question?.type ?? props.type ?? "SINGLE_CHOICE";

  const methods = useForm<QuestionEditorValues>({
    resolver: zodResolver(questionEditorSchema),
    defaultValues: question ? toEditorValues(question) : emptyEditorValues(type),
    mode: "onTouched",
    shouldFocusError: false,
  });
  const { handleSubmit, formState } = methods;

  const createMutation = useCreateQuestion();
  const updateMutation = useUpdateQuestion(question?.id ?? "");
  const pending = createMutation.isPending || updateMutation.isPending;

  const leaveHref = question ? `/questions/${encodeURIComponent(question.id)}` : "/questions";

  const showError = (error: unknown) => {
    const code = error instanceof MockApiError ? error.code : undefined;
    setSubmitError(code && t.has(`saveErrors.${code}`) ? t(`saveErrors.${code}`) : t("saveErrors.generic"));
  };

  const onValid = (values: QuestionEditorValues) => {
    setSubmitError(null);
    const request = toSaveRequest(values);
    if (!question) {
      createMutation.mutate(request, {
        onSuccess: (created) => {
          notify.success(t("toast.created", { code: created.code }));
          router.push(`/questions/${encodeURIComponent(created.id)}`);
        },
        onError: showError,
      });
      return;
    }
    updateMutation.mutate(
      { ...request, expectedVersion: question.version },
      {
        onSuccess: ({ question: saved, versionCreated }) => {
          notify.success(
            versionCreated
              ? t("toast.newVersion", { code: saved.code, version: saved.version })
              : t("toast.updated", { code: saved.code }),
          );
          router.push(`/questions/${encodeURIComponent(saved.id)}`);
        },
        onError: showError,
      },
    );
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    const form = event.currentTarget;
    const onInvalid = () => {
      setLiveMessage(t("invalidSubmit"));
      // Đợi câu con có lỗi mở ra rồi mới tìm ô sai đầu tiên theo thứ tự trên màn hình.
      requestAnimationFrame(() => {
        form.querySelector<HTMLElement>('[aria-invalid="true"], [data-editor-error]')?.focus();
      });
    };
    void handleSubmit(onValid, onInvalid)(event);
  };

  const cancel = () => {
    if (formState.isDirty) setConfirmLeave(true);
    else router.push(leaveHref);
  };

  const description = question
    ? t("editDescription", {
        type: tType(question.type),
        code: question.code,
        version: question.version,
        updatedAt: format.dateTime(new Date(question.updatedAt), "dateTime"),
      })
    : t("createDescription", { type: tType(type) });

  return (
    <FormProvider {...methods}>
      <EditorAnnounceProvider announce={setLiveMessage}>
        <div className="flex flex-col gap-5">
          <PageHeader
            title={question ? t("editTitle") : t("createTitle")}
            description={description}
            actions={
              <>
                {!question && (
                  <Button asChild variant="link">
                    <Link href="/questions/new">{t("changeType")}</Link>
                  </Button>
                )}
                <Button type="button" variant="secondary" onClick={cancel} disabled={pending}>
                  {t("cancel")}
                </Button>
                <Button type="submit" form={formId} loading={pending}>
                  {question ? t("save") : t("saveDraft")}
                </Button>
              </>
            }
          />
          <MockDataBanner />
          {question && <VersionNotice question={question} />}
          {submitError && (
            <p role="alert" className="rounded-lg border border-danger bg-danger-bg px-3.5 py-2.5 text-body text-danger">
              {submitError}
            </p>
          )}
          <form
            id={formId}
            noValidate
            aria-label={question ? t("editTitle") : t("createTitle")}
            onSubmit={submit}
            className="flex flex-col gap-4"
          >
            <ClassificationSection reference={props.reference} />
            <ContentSection formId={formId} type={type} />
            {type === "QUESTION_SET" ? (
              <ChildrenSection formId={formId} />
            ) : (
              <EditorSection title={t(`bodyTitle.${BODY_TITLE_KEY[type]}`)} titleId={`${formId}-body`}>
                <QuestionBodyEditor
                  type={type}
                  prefix="body"
                  contentPath="content"
                  contentFieldId={`${formId}-content`}
                />
              </EditorSection>
            )}
            {type !== "QUESTION_SET" && <SolutionSection formId={formId} />}
          </form>
          <LiveStatus message={liveMessage} />
          <ConfirmDialog
            open={confirmLeave}
            onOpenChange={setConfirmLeave}
            title={t("leave.title")}
            description={t("leave.description")}
            confirmLabel={t("leave.confirm")}
            cancelLabel={t("leave.stay")}
            onConfirm={() => {
              setConfirmLeave(false);
              router.push(leaveHref);
            }}
          />
        </div>
      </EditorAnnounceProvider>
    </FormProvider>
  );
}

function ContentSection({ formId, type }: { formId: string; type: QuestionType }) {
  const t = useTranslations("questions.editor");
  const { register } = useFormContext<QuestionEditorValues>();
  const error = useEditorError("content");
  const key = type === "QUESTION_SET" ? "passage" : type === "TRUE_FALSE" ? "sharedContent" : "content";
  const hintKey =
    type === "FILL_IN"
      ? "fillIn"
      : type === "TRUE_FALSE"
        ? "trueFalse"
        : type === "QUESTION_SET"
          ? "passage"
          : type === "ESSAY"
            ? "essay"
            : "default";
  const titleId = `${formId}-content-title`;
  return (
    <EditorSection title={t(`contentTitle.${key}`)} titleId={titleId} required>
      <div className="flex flex-col gap-1.5">
        <Textarea
          id={`${formId}-content`}
          rows={type === "QUESTION_SET" ? 8 : 4}
          maxLength={CONTENT_MAX}
          aria-labelledby={titleId}
          aria-required
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${formId}-content-error` : `${formId}-content-hint`}
          {...register("content")}
        />
        {!error && (
          <p id={`${formId}-content-hint`} className="text-caption text-text-muted">
            {t(`contentHint.${hintKey}`)}
          </p>
        )}
        <FieldErrorText id={`${formId}-content-error`} message={error} />
      </div>
    </EditorSection>
  );
}

function ChildrenSection({ formId }: { formId: string }) {
  const t = useTranslations("questions.editor");
  const { control } = useFormContext<QuestionEditorValues>();
  const children = useWatch({ control, name: "children" }) ?? [];
  return (
    <EditorSection
      title={t("bodyTitle.children")}
      titleId={`${formId}-children`}
      badge={<span className="text-caption text-text-muted">{t("childCount", { count: children.length })}</span>}
    >
      <QuestionSetEditor />
    </EditorSection>
  );
}

function SolutionSection({ formId }: { formId: string }) {
  const t = useTranslations("questions.editor");
  const { register } = useFormContext<QuestionEditorValues>();
  const error = useEditorError("solution");
  const titleId = `${formId}-solution-title`;
  return (
    <EditorSection title={t("solutionTitle")} titleId={titleId} optionalLabel={t("optional")}>
      <div className="flex flex-col gap-1.5">
        <Textarea
          rows={4}
          maxLength={SOLUTION_MAX}
          aria-labelledby={titleId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${formId}-solution-error` : `${formId}-solution-hint`}
          {...register("solution")}
        />
        {!error && (
          <p id={`${formId}-solution-hint`} className="text-caption text-text-muted">
            {t("solutionHint")}
          </p>
        )}
        <FieldErrorText id={`${formId}-solution-error`} message={error} />
      </div>
    </EditorSection>
  );
}

/**
 * Thông báo phiên bản khi sửa (UniNote PBI-03 mục 7.3, canvas `CauHoiPhienBan`): câu Sẵn sàng hoặc Ngừng sử dụng
 * thì lưu tạo phiên bản mới ở trạng thái Nháp; câu Nháp thì sửa tại chỗ.
 */
function VersionNotice({ question }: { question: QuestionDetail }) {
  const t = useTranslations("questions.editor.versionNotice");
  const next = question.version + 1;
  const createsVersion = editCreatesNewVersion(question);
  const message = !createsVersion
    ? question.version > 1
      ? t("draftOverwrite", { version: question.version })
      : null
    : question.status === "RETIRED"
      ? t("retired", { version: question.version, next })
      : t("ready", { version: question.version, next });
  if (!message) return null;
  return (
    <div role="note" className="flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-2.5 text-body text-info">
      <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col gap-1">
        {createsVersion && <strong className="font-semibold">{t("title", { next })}</strong>}
        <span>{message}</span>
        {createsVersion && question.usedInExamCount > 0 && (
          <span>{t("usage", { count: question.usedInExamCount })}</span>
        )}
      </div>
    </div>
  );
}
