"use client";

import { Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { BlockTagPicker } from "../components/block-tag-picker";
import { KnowledgeScopeField } from "../components/knowledge-scope-field";
import type { KnowledgeScope, QuestionBlock } from "../types";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MATRIX_FIELDS } from "./matrix-blocking";
import { roleBlocks } from "./matrix-form-model";

type SourceCondition = "MAPID" | "TAGS";

const hasScope = (scope: KnowledgeScope | null) => (scope?.nodeIds.length ?? 0) > 0;

/**
 * "Nguồn chùm" của MT (FTM R-3; QDA, BCA, V-ACT): mục trong "Thêm điều kiện" của dòng Câu chùm, sửa MAPID (`knowledgeScope`) và nhãn
 * (`tagCodes`) của riêng nhóm câu chùm. Có sẵn giá trị thì mục hiện mở. Phạm vi môn và nguồn của nhóm câu đơn không đổi theo; chỉ đúng
 * hai trường này của nhóm chùm được ghi.
 */
export function MatrixSetSource({ section, subjectName, reference, onChange }: MatrixBlockProps) {
  const t = useTranslations("exams.subjectForm.matrix.setSource");
  const set = roleBlocks(section).set;
  const [shown, setShown] = useState<ReadonlySet<SourceCondition>>(() => new Set([...(hasScope(set?.knowledgeScope ?? null) ? (["MAPID"] as const) : []), ...((set?.tagCodes.length ?? 0) > 0 ? (["TAGS"] as const) : [])]));
  const [openRequest, setOpenRequest] = useState(0);
  if (!set) return null;

  const patchSet = (patch: Partial<QuestionBlock>) =>
    onChange((current) => ({ ...current, blocks: current.blocks.map((block) => (block.id === set.id ? { ...block, ...patch } : block)) }));
  const add = (kind: SourceCondition) => {
    setShown((current) => new Set(current).add(kind));
    if (kind === "MAPID") setOpenRequest((count) => count + 1);
  };
  const remove = (kind: SourceCondition) => {
    setShown((current) => {
      const next = new Set(current);
      next.delete(kind);
      return next;
    });
    patchSet(kind === "MAPID" ? { knowledgeScope: null } : { tagCodes: [] });
  };
  const kinds: SourceCondition[] = ["MAPID", "TAGS"];
  const name = set.name || t("groupName");

  return (
    <div data-form-field={MATRIX_FIELDS.setSource} className="flex flex-col gap-3 rounded-md border border-border bg-subtle/40 p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h4 className="text-label font-semibold text-text">{t("title")}</h4>
          <p className="text-caption text-text-muted">{t("hint")}</p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="secondary" size="sm">
              <Plus />
              <span>{t("add")}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent aria-label={t("menuLabel")} className="w-72 max-w-[calc(100vw-2rem)]">
            {kinds.map((kind) => (
              <DropdownMenuItem key={kind} disabled={shown.has(kind)} className="h-auto min-h-10 flex-col items-start gap-0 py-1.5" onSelect={() => add(kind)}>
                <span>{t(`kind.${kind}`)}</span>
                <span className="text-caption font-normal text-text-muted">{shown.has(kind) ? t("added") : t(`kindHint.${kind}`)}</span>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      {shown.size === 0 && <p className="text-body text-text-muted">{t("empty")}</p>}
      {shown.has("MAPID") && (
        <div className="flex flex-col gap-2" data-condition="MAPID">
          <KnowledgeScopeField
            value={set.knowledgeScope}
            onChange={(knowledgeScope) => patchSet({ knowledgeScope })}
            inherited={{ scope: section.knowledgeScope }}
            reference={reference}
            subjectCode={section.subjectCode}
            name={`${subjectName} · ${name}`}
            openRequest={openRequest}
          />
          <Button type="button" variant="link" size="sm" className="self-start" aria-label={t("removeAria", { kind: t("kind.MAPID") })} onClick={() => remove("MAPID")}>
            {t("remove")}
          </Button>
        </div>
      )}
      {shown.has("TAGS") && (
        <div className="flex flex-col gap-2" data-condition="TAGS">
          <h5 className="text-label font-semibold text-text">{t("kind.TAGS")}</h5>
          <BlockTagPicker value={set.tagCodes} onChange={(tagCodes) => patchSet({ tagCodes })} options={reference.tags} scopeName={name} />
          <Button type="button" variant="link" size="sm" className="self-start" aria-label={t("removeAria", { kind: t("kind.TAGS") })} onClick={() => remove("TAGS")}>
            {t("remove")}
          </Button>
        </div>
      )}
    </div>
  );
}
