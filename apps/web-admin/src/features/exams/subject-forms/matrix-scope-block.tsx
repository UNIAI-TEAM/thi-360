"use client";

import { Button } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { KnowledgeScopeField } from "../components/knowledge-scope-field";
import { TopicQuotaTable, type TopicQuotaServerError } from "../components/topic-quota-table";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MATRIX_FIELDS } from "./matrix-blocking";

/**
 * Phạm vi và chỉ tiêu chủ đề của MT ngoài HSA (FTM R-6). Phạm vi kiến thức (Toàn môn / Theo chủ đề) luôn hiện; nút "Thêm chỉ tiêu chủ đề"
 * mới hiện bảng chỉ tiêu (bài đã có dòng thì bảng hiện sẵn). V-ACT Tiếng Việt: bảng mạch (các dòng chỉ tiêu, có cột độ khó trong mạch)
 * là phần chính nên luôn hiện, dòng chưa gắn MAPID hiện "Chờ mapping" (H31). HSA dùng Quy tắc MAPID của HSA-D (R-5), không vẽ ở đây.
 */
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
    <section aria-label={t("title")} className="flex flex-col gap-4 rounded-md border border-border p-4">
      <div className="flex flex-col gap-0.5">
        <h3 className="text-title font-semibold text-text">{t("title")}</h3>
        <p className="text-caption text-text-muted">{t("hint")}</p>
      </div>
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
    </section>
  );
}
