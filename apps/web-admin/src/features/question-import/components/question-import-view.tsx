"use client";

import { Button, Card, EmptyState, LiveStatus, notify, PageHeader, Skeleton } from "@thi360/ui";
import { Download, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useRef, useState } from "react";
import { useContentReference } from "@/features/content/queries";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { defaultQuestionImportApiDeps, importApiErrorCode, type QuestionImportApiDeps } from "../api";
import { saveBlob } from "../files";
import { useDownloadQuestionTemplate, useValidateImport } from "../queries";
import type {
  ColumnMapping,
  DuplicatePolicy,
  ImportHistoryEntry,
  ImportUpload,
  ImportValidation,
} from "../types";
import { CheckStep, writeTabToUrl } from "./check-step";
import { ImportHistorySection } from "./import-history-section";
import { type ImportStepNumber, ImportStepper } from "./import-stepper";
import { MappingStep } from "./mapping-step";
import { ResultStep } from "./result-step";
import { UploadStep } from "./upload-step";

export interface QuestionImportViewProps {
  deps?: Partial<QuestionImportApiDeps>;
}

/**
 * Nhập câu hỏi từ tệp, bốn bước: tải tệp, ánh xạ cột, kiểm tra và xem trước, kết quả (UNI-2594). Trạng thái của
 * luồng giữ trong màn (tệp đã chọn không sống qua lần tải lại trang); đổi bước thì focus về tiêu đề bước mới.
 */
export function QuestionImportView({ deps }: QuestionImportViewProps) {
  const t = useTranslations("questionImport");
  const apiDeps = useMemo<QuestionImportApiDeps>(() => ({ ...defaultQuestionImportApiDeps, ...deps }), [deps]);
  useSetBreadcrumbLeaf(t("breadcrumb"));

  const referenceQuery = useContentReference();
  const templateMutation = useDownloadQuestionTemplate(apiDeps);
  const validateMutation = useValidateImport(apiDeps);

  const [step, setStep] = useState<ImportStepNumber>(1);
  const [upload, setUpload] = useState<ImportUpload | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping>({});
  const [policy, setPolicy] = useState<DuplicatePolicy>("SKIP");
  const [validation, setValidation] = useState<ImportValidation | null>(null);
  const [result, setResult] = useState<ImportHistoryEntry | null>(null);
  const [validateError, setValidateError] = useState<string | null>(null);
  const [liveMessage, setLiveMessage] = useState("");
  const headingRef = useRef<HTMLHeadingElement>(null);
  const focusHeading = useRef(false);

  useEffect(() => {
    if (!focusHeading.current) return;
    focusHeading.current = false;
    headingRef.current?.focus();
  }, [step]);

  function goTo(next: ImportStepNumber) {
    focusHeading.current = true;
    setStep(next);
  }

  function restart() {
    setUpload(null);
    setMapping({});
    setPolicy("SKIP");
    setValidation(null);
    setResult(null);
    setValidateError(null);
    validateMutation.reset();
    writeTabToUrl(null);
    goTo(1);
  }

  function handleUploaded(next: ImportUpload) {
    setUpload(next);
    setMapping(next.suggestedMapping);
    setValidation(null);
    setValidateError(null);
    setLiveMessage(t("live.uploaded", { fileName: next.fileName, rows: next.rowCount }));
    goTo(2);
  }

  function handleMappingSubmit(nextMapping: ColumnMapping, nextPolicy: DuplicatePolicy) {
    if (!upload) return;
    setMapping(nextMapping);
    setPolicy(nextPolicy);
    setValidateError(null);
    validateMutation.mutate(
      { uploadId: upload.id, mapping: nextMapping },
      {
        onSuccess: (next) => {
          setValidation(next);
          setLiveMessage(
            t("live.validated", {
              valid: next.summary.valid,
              errors: next.summary.error,
              duplicates: next.summary.duplicate,
            }),
          );
          goTo(3);
        },
        onError: (error) => {
          setValidateError(
            importApiErrorCode(error) === "IMPORT_SOURCE_GONE" ? t("mapping.sourceGone") : t("mapping.validateError"),
          );
        },
      },
    );
  }

  function handleCommitted(next: ImportHistoryEntry) {
    setResult(next);
    setLiveMessage(t("live.committed", { count: next.createdCount + next.newVersionCount }));
    goTo(4);
  }

  function downloadTemplate() {
    templateMutation.mutate(undefined, {
      onSuccess: ({ blob, filename }) => {
        saveBlob(blob, filename);
        setLiveMessage(t("live.templateDownloaded"));
      },
      onError: () => notify.error(t("downloadTemplateError")),
    });
  }

  const description =
    step > 1 && upload
      ? upload.sheetName
        ? t("fileLineSheet", { fileName: upload.fileName, sheet: upload.sheetName })
        : upload.fileName
      : t("description");

  let body;
  if (referenceQuery.isPending) {
    body = <ImportSkeleton />;
  } else if (referenceQuery.isError) {
    body = (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          headingLevel={2}
          title={t("loadError.title")}
          description={t("loadError.description")}
          action={
            <Button variant="secondary" onClick={() => void referenceQuery.refetch()}>
              {t("loadError.retry")}
            </Button>
          }
        />
      </Card>
    );
  } else if (step === 2 && upload) {
    body = (
      <MappingStep
        upload={upload}
        initialMapping={mapping}
        initialPolicy={policy}
        headingRef={headingRef}
        submitting={validateMutation.isPending}
        submitError={validateError}
        onChangeFile={restart}
        onSubmit={handleMappingSubmit}
      />
    );
  } else if (step === 3 && upload && validation) {
    body = (
      <CheckStep
        deps={apiDeps}
        upload={upload}
        mapping={mapping}
        policy={policy}
        validation={validation}
        reference={referenceQuery.data}
        headingRef={headingRef}
        onBack={() => goTo(2)}
        onCommitted={handleCommitted}
        onAnnounce={setLiveMessage}
      />
    );
  } else if (step === 4 && result) {
    body = (
      <div className="flex flex-col gap-6">
        <ResultStep
          result={result}
          reference={referenceQuery.data}
          headingRef={headingRef}
          onRestart={restart}
          onAnnounce={setLiveMessage}
        />
        <ImportHistorySection
          deps={apiDeps}
          reference={referenceQuery.data}
          onDownloaded={() => setLiveMessage(t("live.errorsDownloaded"))}
        />
      </div>
    );
  } else {
    body = (
      <div className="flex flex-col gap-6">
        <UploadStep deps={apiDeps} headingRef={headingRef} onUploaded={handleUploaded} />
        <ImportHistorySection
          deps={apiDeps}
          reference={referenceQuery.data}
          onDownloaded={() => setLiveMessage(t("live.errorsDownloaded"))}
        />
      </div>
    );
  }

  return (
    <div className="@container flex flex-col gap-4">
      <PageHeader
        title={t("title")}
        description={description}
        actions={
          <Button variant="secondary" loading={templateMutation.isPending} onClick={downloadTemplate}>
            <Download />
            <span>{t("downloadTemplate")}</span>
          </Button>
        }
      />
      <ImportStepper currentStep={step} />
      {body}
      <LiveStatus message={liveMessage} />
    </div>
  );
}

function ImportSkeleton() {
  const common = useTranslations("common");
  return (
    <div role="status" aria-label={common("loading")} className="flex flex-col gap-4">
      <div className="grid gap-4 @4xl:grid-cols-12">
        <Skeleton className="h-80 w-full @4xl:col-span-5" />
        <Skeleton className="h-80 w-full @4xl:col-span-7" />
      </div>
      <Skeleton className="h-48 w-full" />
    </div>
  );
}
