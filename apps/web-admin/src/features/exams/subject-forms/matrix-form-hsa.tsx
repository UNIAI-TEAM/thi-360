"use client";

import { Button, Chip } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import type { ContentReference } from "@/features/content/types";
import { BlockTagPicker } from "../components/block-tag-picker";
import { KnowledgeScopeField, MapidModeField } from "../components/knowledge-scope-field";
import { MapidRulesTable, useMapidRuleText } from "../components/mapid-rules-table";
import { TopicQuotaTable } from "../components/topic-quota-table";
import { deletedGroupRules, groupEffectiveRules, mapidViewMode, ruleCount } from "../mapid-rules";
import type { ExamSection, QuestionBlock } from "../types";
import type { MatrixBlockProps, MatrixKindSlotOptions } from "./matrix-block-props";
import { roleBlocks, structureOf } from "./matrix-form-model";
import { MATRIX_MAPID_RULES_FIELD } from "./navigation";

function isHsaStrandSubject(subjectCode: string): boolean {
  return subjectCode === "SU" || subjectCode === "DIA";
}

/**
 * Tuỳ chọn vị trí theo môn trong HSA (AD-98):
 * - Sử, Địa đặt Mạch kiến thức trước Cấu trúc câu gọn (`placement: "before-structure"`, `structureVariant: "compact"`).
 * - Toán, Lý, Hóa, Sinh giữ thứ tự chuẩn (`placement: "after-structure"`, `structureVariant: "default"`).
 */
export function hsaMatrixSlotOptions(section: ExamSection): MatrixKindSlotOptions {
  return isHsaStrandSubject(section.subjectCode)
    ? { placement: "before-structure", structureVariant: "compact" }
    : { placement: "after-structure", structureVariant: "default" };
}

/** Số câu đã phân bổ vào các quy tắc MAPID của môn HSA (theo đúng quy ước đếm của HSA-D `ruleCount`). */
function hsaAllocatedRuleQuestions(section: ExamSection): number {
  return (section.mapidRules ?? []).reduce((sum, rule) => {
    if (rule.type === "COUNT") return sum + ruleCount(section, rule);
    if (rule.type === "ONLY_IN_GROUP" && (rule.count != null || rule.percent != null)) return sum + ruleCount(section, rule);
    if (rule.type === "PRESENT") return sum + 1;
    return sum;
  }, 0);
}

/** Phạm vi nguồn hiệu lực của từng nhóm vai trò dưới ô vai trò trong `MatrixStructureBlock` (FTM R-5, AC-6). */
export function HsaRoleScopeFooter({ section, block, reference }: { section: ExamSection; block: QuestionBlock; reference: ContentReference }) {
  const tHsa = useTranslations("exams.subjectForm.matrix.hsa");
  const tGroups = useTranslations("exams.structure.subject.groups");
  const ruleText = useMapidRuleText(reference);
  if (mapidViewMode(section) !== "MATRIX") return null;
  const effective = groupEffectiveRules(section, block.id);
  return (
    <div className="flex flex-col gap-0.5 pt-1 text-caption text-text-muted">
      <span className="font-medium text-text-secondary">{tHsa("roleScopeLabel")}</span>
      {effective.length === 0 ? (
        <span data-testid={`mapid-group-scope-${block.id}`}>{tGroups("scopeNone")}</span>
      ) : (
        <ul data-testid={`mapid-group-scope-${block.id}`} className="flex flex-col gap-0.5 text-text">
          {effective.map((item) => (
            <li key={item.rule.id}>{ruleText.short(section, item)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Khối điều kiện tư liệu tuỳ chọn của HSA Địa lí (AD-99): đọc nhãn động từ danh mục, ghi vào `set.tagCodes`. */
function DiaMaterialSection({
  section,
  setBlock,
  subjectName,
  reference,
  onChange,
}: {
  section: ExamSection;
  setBlock: QuestionBlock;
  subjectName: string;
  reference: ContentReference;
  onChange: MatrixBlockProps["onChange"];
}) {
  const tHsa = useTranslations("exams.subjectForm.matrix.hsa");
  const [openSectionId, setOpenSectionId] = useState<string | null>(null);
  const expanded = openSectionId === section.id || setBlock.tagCodes.length > 0;
  const updateTags = (tagCodes: string[]) =>
    onChange((current) => ({
      ...current,
      blocks: current.blocks.map((block) => (block.id === setBlock.id ? { ...block, tagCodes } : block)),
    }));

  return (
    <section
      aria-label={tHsa("material.title")}
      data-testid="hsa-dia-material-section"
      className="flex flex-col gap-3 rounded-md border border-border p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-0.5">
          <h4 className="text-label font-semibold text-text">{tHsa("material.title")}</h4>
          <p className="text-caption text-text-muted">{tHsa("material.hint")}</p>
        </div>
        {!expanded ? (
          <Chip variant="tag" className="w-fit">
            {tHsa("notApplied")}
          </Chip>
        ) : (
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="hsa-dia-material-clear"
            onClick={() => {
              setOpenSectionId(null);
              if (setBlock.tagCodes.length > 0) updateTags([]);
            }}
          >
            {tHsa("material.remove")}
          </Button>
        )}
      </div>
      {expanded ? (
        <div data-testid="hsa-dia-material-picker">
          <BlockTagPicker value={setBlock.tagCodes} onChange={updateTags} options={reference.tags} scopeName={subjectName} />
        </div>
      ) : (
        <div>
          <Button type="button" variant="secondary" size="sm" data-testid="hsa-dia-material-add" onClick={() => setOpenSectionId(section.id)}>
            <Plus />
            <span>{tHsa("material.add")}</span>
          </Button>
        </div>
      )}
    </section>
  );
}

/**
 * Chỗ gắn riêng của HSA trong Ma trận môn (CR-2H):
 * - Nhúng nguyên vẹn HSA-D/HSA-G (`MapidModeField` + `MapidRulesTable`) cho mọi môn HSA khi `mapidMode !== null`.
 * - Đổi sang fallback `KnowledgeScopeField` + `TopicQuotaTable` chỉ khi bài cũ không chuyển đổi được (`mapidMode === null`).
 * - Sử, Địa (AD-98): bọc trong khối Mạch kiến thức, đếm số câu đã phân bổ / còn tự do.
 * - Địa (AD-99): thêm khối điều kiện tư liệu tuỳ chọn ghi `set.tagCodes` từ `reference.tags`.
 */
export function MatrixFormHsa({ section, kindCode, subjectName, total, axes, labels, reference, onChange }: MatrixBlockProps) {
  const tHsa = useTranslations("exams.subjectForm.matrix.hsa");
  const tScope = useTranslations("exams.subjectForm.matrix.scope");
  const mode = mapidViewMode(section);
  const strand = isHsaStrandSubject(section.subjectCode);
  const setBlock = roleBlocks(section).set;
  const subjectTotal = total ?? structureOf(section).total;
  const allocated = hsaAllocatedRuleQuestions(section);
  const over = allocated - subjectTotal;

  const mapidContent =
    mode !== null ? (
      <div data-form-field={MATRIX_MAPID_RULES_FIELD} className="flex flex-col gap-4">
        <MapidModeField
          mode={mode}
          onModeChange={(nextMode) => onChange((current) => ({ ...current, mapidMode: nextMode }))}
          ruleCount={section.mapidRules?.length ?? 0}
          invalidCount={deletedGroupRules(section).length}
        />
        {strand && mode === "MATRIX" && (
          <div data-testid="hsa-mapid-allocation" className="flex flex-col gap-1 text-caption">
            <p className="font-medium text-text">
              {subjectTotal > 0 || total !== null
                ? tHsa("allocationCount", { allocated, total: subjectTotal, free: Math.max(0, subjectTotal - allocated) })
                : tHsa("allocationUnknown", { allocated })}
            </p>
            {over > 0 && (
              <p role="alert" className="text-danger">
                {tHsa("allocationOver", { over })}
              </p>
            )}
            <p className="text-text-muted">{tHsa("orderNote")}</p>
          </div>
        )}
        {mode === "MATRIX" && (
          <MapidRulesTable
            section={section}
            groups={section.blocks}
            subjectName={subjectName}
            reference={reference}
            onChange={(rules) => onChange((current) => ({ ...current, mapidRules: rules }))}
          />
        )}
      </div>
    ) : (
      <div className="flex flex-col gap-4 rounded-md border border-border p-4">
        <div className="flex flex-col gap-0.5">
          <h3 className="text-title font-semibold text-text">{tScope("title")}</h3>
          <p className="text-caption text-text-muted">{tScope("hint")}</p>
        </div>
        <KnowledgeScopeField
          value={section.knowledgeScope}
          onChange={(knowledgeScope) => onChange((current) => ({ ...current, knowledgeScope }))}
          reference={reference}
          subjectCode={section.subjectCode}
          name={subjectName}
          everyChild={{
            checked: section.knowledgeScope?.everyChildInScope ?? false,
            onChange: (checked) =>
              onChange((current) => ({
                ...current,
                knowledgeScope: current.knowledgeScope
                  ? { ...current.knowledgeScope, everyChildInScope: checked }
                  : { nodeIds: [], everyChildInScope: checked },
              })),
          }}
        />
        <TopicQuotaTable
          id={section.id}
          quotas={section.topicQuotas ?? []}
          onChange={(topicQuotas) => onChange((current) => ({ ...current, topicQuotas }))}
          total={total ?? 0}
          levelName={subjectName}
          subjectCode={section.subjectCode}
          reference={reference}
          axes={axes}
          labels={labels}
          serverError={null}
        />
      </div>
    );

  const strandBlock = strand ? (
    <section
      aria-label={tHsa("strandTitle", { subject: subjectName })}
      data-testid="hsa-strand-section"
      className="flex flex-col gap-4 rounded-md border border-border p-4"
    >
      <h3 className="text-title font-semibold text-text">{tHsa("strandTitle", { subject: subjectName })}</h3>
      {mapidContent}
    </section>
  ) : (
    mapidContent
  );

  const showMaterial = kindCode === "HSA" && section.subjectCode === "DIA" && setBlock !== null;
  if (!showMaterial) return strandBlock;

  return (
    <div className="flex flex-col gap-6">
      {strandBlock}
      <DiaMaterialSection section={section} setBlock={setBlock} subjectName={subjectName} reference={reference} onChange={onChange} />
    </div>
  );
}
