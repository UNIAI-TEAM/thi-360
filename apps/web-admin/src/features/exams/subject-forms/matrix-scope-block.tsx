"use client";

import { Button } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { KnowledgeScopeField } from "../components/knowledge-scope-field";
import { TopicQuotaTable, type TopicQuotaServerError } from "../components/topic-quota-table";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MATRIX_FIELDS } from "./matrix-blocking";

export function MatrixScopeBlock({ section, subjectName, total, axes, labels, reference, onChange, topicError }: MatrixBlockProps & { topicError: TopicQuotaServerError }) {
  const t = useTranslations("exams.subjectForm.matrix.scope");
  const quotas = section.topicQuotas ?? [];
  const [requested, setRequested] = useState(false);
  const strands = section.subjectCode === "VIETNAMESE";
  const showTable = quotas.length > 0 || requested || strands;
  const quotaRef = useRef<HTMLDivElement>(null);
  // Nút mở bảng bị thay bằng chính bảng: đưa tiêu điểm về nút "Thêm chỉ tiêu chủ đề" của bảng để người dùng bàn phím không mất chỗ (R1-3).
  const revealTable = () => {
    setRequested(true);
    requestAnimationFrame(() => quotaRef.current?.querySelector<HTMLElement>("[data-topic-add]")?.focus());
  };

  return (
    <section aria-label={t("title")} className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface">
      <div className="flex flex-col gap-0.5 border-b border-border bg-subtle/40 px-4 py-3">
        <div className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-caption font-semibold text-primary"
          >
            3
          </span>
          <h3 className="text-title font-semibold text-text">{t("title")}</h3>
        </div>
        <p className="text-caption text-text-muted">{t("hint")}</p>
      </div>
      <div className="flex flex-col gap-4 p-4">
        <div data-form-field={MATRIX_FIELDS.scope}>
          <KnowledgeScopeField
            value={section.knowledgeScope}
            onChange={(knowledgeScope) => onChange((current) => ({ ...current, knowledgeScope }))}
            reference={reference}
            subjectCode={section.subjectCode}
            name={subjectName}
          />
        </div>
        <div ref={quotaRef} data-form-field={MATRIX_FIELDS.topicQuotas} className="flex flex-col gap-3">
          {showTable ? (
            <TopicQuotaTable
              id={section.id}
              quotas={quotas}
              onChange={(next) => onChange((current) => ({ ...current, topicQuotas: next }))}
              total={total ?? 0}
              levelName={subjectName}
              subjectCode={section.subjectCode}
              reference={reference}
              axes={axes}
              labels={labels}
              serverError={topicError}
            />
          ) : (
            <div className="flex flex-col items-start gap-1">
              <Button type="button" variant="secondary" size="sm" onClick={revealTable}>
                <Plus />
                <span>{t("addQuota")}</span>
              </Button>
              <p className="text-caption text-text-muted">{t("addQuotaHint")}</p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
