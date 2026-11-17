"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, ConfirmDialog, LiveStatus, notify, PageHeader } from "@thi360/ui";
import { Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState, type FormEvent } from "react";
import { FormProvider, useForm, useFormContext, useWatch } from "react-hook-form";
import type { ContentReference, QuestionType } from "@/features/content/types";
import { apiErrorCode, apiFieldErrors, isRevisionConflict } from "@/features/content/api-error";
import { getErrorStatus } from "@/lib/api/query-client";
import { applyServerFieldErrors, serverErrorKey } from "../editor-field-errors";
import { emptyEditorValues, toEditorValues, toSaveRequest, type QuestionEditorValues } from "../editor-form";
import { useCreateQuestion, useUpdateQuestion } from "../editor-queries";
import { useUiLocale } from "../use-ui-locale";
import { questionEditorSchema } from "../editor-schema";
import { editCreatesNewVersion } from "../editor-versioning";
import type { QuestionDetail } from "../types";
import { QuestionBodyEditor } from "./question-editor-body";
import { ClassificationSection } from "./question-editor-classification";
import { AttachmentsSection, CommonMistakesSection } from "./question-editor-extras";
import { QuestionSetEditor } from "./question-editor-set";
import { RichTextField } from "./question-editor-rich-field";
import { EditorAnnounceProvider, EditorSection, FieldErrorText, useEditorError } from "./question-editor-shared";

interface EditorFormCommon {
  reference: ContentReference;
  /** Nạp lại câu hỏi mới nhất và dựng lại biểu mẫu (lời mời ở thông báo xung đột 412); chỉ khi sửa. */
  onReload?: () => void | Promise<unknown>;
}

export type QuestionEditorFormProps =
  | (EditorFormCommon & { mode: "create"; type: QuestionType; question?: undefined })
  | (EditorFormCommon & { mode: "edit"; question: QuestionDetail; type?: undefined });

const BODY_TITLE_KEY: Record<QuestionType, string> = {
  SINGLE_CHOICE: "options",
  MULTIPLE_CHOICE: "options",
  TRUE_FALSE: "statements",
  SHORT_ANSWER: "shortAnswer",
  CLOZE: "blanks",
  MATCHING: "matching",
  QUESTION_SET: "children",
  ESSAY: "essay",
};

/** Dòng gợi ý dưới ô Đề bài theo dạng câu (`questions.editor.contentHint.*`). */
const CONTENT_HINT_KEY: Record<QuestionType, string> = {
  SINGLE_CHOICE: "default",
  MULTIPLE_CHOICE: "default",
  TRUE_FALSE: "trueFalse",
  SHORT_ANSWER: "shortAnswer",
  CLOZE: "fillIn",
  MATCHING: "matching",
  QUESTION_SET: "passage",
  ESSAY: "essay",
};

/**
 * Biểu mẫu soạn câu hỏi theo dạng (canvas `CauHoi*Form`): khung chung (Phân loại, Đề bài, Lời giải) và khối đáp án
 * theo dạng. Câu mới lưu ở trạng thái Nháp; sửa câu Sẵn sàng tạo phiên bản mới (thông báo ở đầu biểu mẫu).
 * Đề bài, lời giải, đoạn dẫn và các ô nội dung HTML khác dùng `RichTextEditor` (UNI-2617).
 */
export function QuestionEditorForm(props: QuestionEditorFormProps) {
  const t = useTranslations("questions.editor");
  const tType = useTranslations("content.questionType");
  const format = useFormatter();
  const router = useRouter();
  const formId = useId();
  // Một lần mở biểu mẫu tạo là một ý định lưu: các lần bấm Lưu và thử lại của nó dùng chung khóa idempotency.
  const [saveIntentId] = useState(() => crypto.randomUUID());
  const [liveMessage, setLiveMessage] = useState("");
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const question = props.question ?? null;
  const type = question?.type ?? props.type ?? "SINGLE_CHOICE";

  const locale = useUiLocale();
  const methods = useForm<QuestionEditorValues>({
    resolver: zodResolver(questionEditorSchema),
    defaultValues: question ? toEditorValues(question, locale) : emptyEditorValues(type, locale),
    mode: "onTouched",
    shouldFocusError: false,
  });
  const { handleSubmit, formState } = methods;

  const createMutation = useCreateQuestion();
  const updateMutation = useUpdateQuestion(question?.id ?? "");
  const pending = createMutation.isPending || updateMutation.isPending;

  const leaveHref = question ? `/questions/${encodeURIComponent(question.id)}` : "/questions";

  const focusFirstInvalid = () => {
    // Đợi câu con có lỗi mở ra rồi mới tìm ô sai đầu tiên theo thứ tự trên màn hình.
    requestAnimationFrame(() => {
      document
        .getElementById(formId)
        ?.querySelector<HTMLElement>('[aria-invalid="true"], [data-editor-error]')
        ?.focus();
    });
  };

  /** 412: người khác đã sửa câu này; 400/422: lỗi theo trường đặt lên ô tương ứng; còn lại: thông báo chung theo mã. */
  const showError = (error: unknown) => {
    if (isRevisionConflict(error)) {
      setSubmitError(null);
      setConflict(true);
      setLiveMessage(t("conflict.title"));
      return;
    }
    const status = getErrorStatus(error);
    // Máy chủ gửi `fieldErrors` ở cả 400 lẫn 422 `VALIDATION_FAILED`: cứ có lỗi theo trường là đặt lên ô (F-02).
    const fieldErrors = status === 400 || status === 422 ? apiFieldErrors(error) : [];
    const { applied, unmatched } = applyServerFieldErrors(fieldErrors, methods.setError, (path) =>
      methods.getValues(path as never),
    );
    if (applied > 0 || unmatched.length > 0) {
      // Lỗi không có ô trên biểu mẫu (cặp ghép, kiến thức...) vẫn phải đọc được: ghi vào dải thông báo theo mã.
      const unmatchedText = [...new Set(unmatched.map((item) => t(`errors.${serverErrorKey(item.code)}`)))];
      setSubmitError([...(applied > 0 ? [t("saveErrors.fieldErrors")] : []), ...unmatchedText].join(" "));
      setLiveMessage(t("invalidSubmit"));
      if (applied > 0) focusFirstInvalid();
      return;
    }
    const code = apiErrorCode(error);
    setSubmitError(code && t.has(`saveErrors.${code}`) ? t(`saveErrors.${code}`) : t("saveErrors.generic"));
  };

  const onValid = (values: QuestionEditorValues) => {
    setSubmitError(null);
    const request = toSaveRequest(values);
    if (!question) {
      createMutation.mutate({ ...request, saveIntentId }, {
        onSuccess: (created) => {
          notify.success(t("toast.created", { code: created.code }));
          router.push(`/questions/${encodeURIComponent(created.id)}`);
        },
        onError: showError,
      });
      return;
    }
    updateMutation.mutate(
      { ...request, expectedRevision: question.revision },
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

  const reload = () => {
    setReloading(true);
    void Promise.resolve(props.onReload?.()).finally(() => setReloading(false));
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
          {question && <VersionNotice question={question} />}
          {conflict && (
            <div
              role="alert"
              className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-warning/30 bg-warning-bg px-3.5 py-2.5 text-body text-warning"
            >
              <div className="flex flex-col gap-1">
                <strong className="font-semibold">{t("conflict.title")}</strong>
                <span>{t("conflict.description")}</span>
              </div>
              {props.onReload && (
                <Button type="button" variant="secondary" size="sm" loading={reloading} onClick={reload}>
                  {t("conflict.reload")}
                </Button>
              )}
            </div>
          )}
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
              <ChildrenSection formId={formId} reference={props.reference} />
            ) : (
              <EditorSection title={t(`bodyTitle.${BODY_TITLE_KEY[type]}`)} titleId={`${formId}-body`}>
                <QuestionBodyEditor type={type} prefix="body" contentPath="content" />
              </EditorSection>
            )}
            {type !== "QUESTION_SET" && <SolutionSection formId={formId} />}
            {type !== "QUESTION_SET" && <CommonMistakesSection formId={formId} />}
            <AttachmentsSection formId={formId} />
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
  const error = useEditorError("content");
  const key = type === "QUESTION_SET" ? "passage" : type === "TRUE_FALSE" ? "sharedContent" : "content";
  const hintKey = CONTENT_HINT_KEY[type];
  const titleId = `${formId}-content-title`;
  return (
    <EditorSection title={t(`contentTitle.${key}`)} titleId={titleId} required>
      <div className="flex flex-col gap-1.5">
        <RichTextField
          id={`${formId}-content`}
          aria-labelledby={titleId}
          aria-required
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${formId}-content-error` : `${formId}-content-hint`}
          name="content"
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

function ChildrenSection({ formId, reference }: { formId: string; reference: ContentReference }) {
  const t = useTranslations("questions.editor");
  const { control } = useFormContext<QuestionEditorValues>();
  const children = useWatch({ control, name: "children" }) ?? [];
  return (
    <EditorSection
      title={t("bodyTitle.children")}
      titleId={`${formId}-children`}
      badge={<span className="text-caption text-text-muted">{t("childCount", { count: children.length })}</span>}
    >
      <QuestionSetEditor reference={reference} />
    </EditorSection>
  );
}

function SolutionSection({ formId }: { formId: string }) {
  const t = useTranslations("questions.editor");
  const error = useEditorError("solution");
  const titleId = `${formId}-solution-title`;
  return (
    <EditorSection title={t("solutionTitle")} titleId={titleId} optionalLabel={t("optional")}>
      <div className="flex flex-col gap-1.5">
        <RichTextField
          aria-labelledby={titleId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${formId}-solution-error` : `${formId}-solution-hint`}
          name="solution"
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
  // Có bản Nháp riêng của câu đã Sẵn sàng: lưu ghi đè bản đó; câu Nháp: ghi đè khi đã qua bản 1.
  const draftVersion = question.workingVersion ?? question.version;
  const message = !createsVersion
    ? draftVersion > 1
      ? t("draftOverwrite", { version: draftVersion })
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
