"use client";

import { Button, IconButton } from "@thi360/ui";
import { CircleMinus, ListTree } from "lucide-react";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference, KnowledgeRef } from "@/features/content/types";
import type { KnowledgeScope } from "../types";
import { ChoiceCardGroup } from "./create-exam-fields";

const EMPTY_SCOPE: KnowledgeScope = { nodeIds: [], match: "ANY" };

export interface KnowledgeScopeFieldProps {
  /** Phạm vi riêng; `null` hoặc `nodeIds` rỗng: nhóm kế thừa phạm vi của môn, môn không giới hạn MAPID. */
  value: KnowledgeScope | null;
  onChange: (scope: KnowledgeScope | null) => void;
  /**
   * Chỉ nhóm có: phạm vi của môn mà nhóm kế thừa khi nhóm chưa chọn nút nào (B9). Bỏ trống: môn, không có gì để kế thừa.
   * Nút đã chọn khớp câu gắn nút đó hoặc nút con của nó (D23), nên không có công tắc "cấp dưới".
   */
  inherited?: { scope: KnowledgeScope | null };
  reference: ContentReference | undefined;
  subjectCode: string;
  /** Tên môn hoặc nhóm, cho tên nhóm radio và mô tả hộp chọn. */
  name: string;
  fieldId: string;
}

/**
 * Phạm vi MAPID của môn hoặc của nhóm lấy câu (spec 5.2): nhóm chưa chọn nút nào hiện "Theo phạm vi của môn"; môn và
 * nhóm cùng trống hiện "Không giới hạn MAPID". Phạm vi riêng của nhóm thay hẳn phạm vi của môn, không giao với nó.
 * Mở hộp chọn không đổi `value` (R1-3): hủy/Esc giữ nguyên kế thừa, chỉ áp dụng mới đặt phạm vi riêng.
 */
export function KnowledgeScopeField({ value, onChange, inherited, reference, subjectCode, name, fieldId }: KnowledgeScopeFieldProps) {
  const t = useTranslations("exams.scopeField");
  const [pickerOpen, setPickerOpen] = useState(false);
  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const refsOf = (scope: KnowledgeScope | null): KnowledgeRef[] =>
    (scope?.nodeIds ?? []).map((nodeId) => {
      const node = knowledgeIndex.get(nodeId)?.node;
      return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
    });
  const own = refsOf(value);
  const inheritedCount = inherited?.scope?.nodeIds.length ?? 0;

  return (
    <fieldset className="flex flex-col gap-3 rounded-md border border-border p-4">
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      {value === null || value.nodeIds.length === 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="flex flex-col text-body">
            {inherited ? (
              <>
                <span className="font-medium text-text">{t("inheritsSection")}</span>
                <span className="text-caption text-text-muted">
                  {inheritedCount > 0 ? t("inheritsCount", { count: inheritedCount }) : t("none")}
                </span>
              </>
            ) : (
              <span className="text-text-muted">{t("none")}</span>
            )}
          </p>
          <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
            <ListTree />
            <span>{inherited ? t("setOwn") : t("pick")}</span>
          </Button>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            {own.map((ref) => (
              <span
                key={ref.nodeId}
                className="inline-flex max-w-full items-center gap-1 rounded-full border border-border bg-subtle py-0.5 pr-0.5 pl-3 text-label text-text"
              >
                <span className="min-w-0 wrap-anywhere">
                  <span className="font-semibold">{ref.code}</span> {ref.name}
                </span>
                <IconButton
                  icon={<CircleMinus />}
                  className="size-8"
                  aria-label={t("remove", { code: ref.code, name: ref.name })}
                  onClick={() => onChange({ ...value, nodeIds: value.nodeIds.filter((id) => id !== ref.nodeId) })}
                />
              </span>
            ))}
            <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
              <ListTree />
              <span>{t("pick")}</span>
            </Button>
            {inherited && (
              <Button type="button" variant="link" size="sm" onClick={() => onChange(null)}>
                {t("useSection")}
              </Button>
            )}
          </div>
          <ChoiceCardGroup
            variant="pill"
            legend={t("match")}
            name={`${fieldId}-match`}
            value={value.match}
            onChange={(match) => onChange({ ...value, match })}
            options={[
              { value: "ANY", label: t("matchAny") },
              { value: "ALL", label: t("matchAll") },
            ]}
          />
        </>
      )}
      <KnowledgePickerDialog
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        value={own}
        selectableLevels={["CHAPTER", "LESSON", "KNOWLEDGE"]}
        withPrimary={false}
        defaultSubjectCode={subjectCode}
        description={t("pickerDescription", { name })}
        onApply={(refs) => {
          onChange({ ...(value ?? EMPTY_SCOPE), nodeIds: refs.map((ref) => ref.nodeId) });
          setPickerOpen(false);
        }}
      />
    </fieldset>
  );
}
