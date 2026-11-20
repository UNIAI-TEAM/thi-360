"use client";

import { Button, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ContentReference } from "@/features/content/types";
import { groupEffectiveRules, revealMapidRulesTable } from "../mapid-rules";
import type { ExamSection, MapidMode } from "../types";
import { useMapidRuleText } from "./mapid-rules-table";

export interface BlockMapidRulesProps {
  /** Môn chứa nhóm (đã có các nhóm và `mapidRules` hiện tại). */
  section: ExamSection;
  blockId: string;
  mode: MapidMode;
  reference: ContentReference | undefined;
  /** Chuyển sang màn môn, nơi có bảng Quy tắc MAPID. */
  onOpenSection: () => void;
  /** Mỏ neo `data-field` của trường Phạm vi kiến thức, để kết quả kiểm tra vẫn cuộn tới đây. */
  anchor?: string;
}

/**
 * Thay cho "Phạm vi kiến thức" và "Chỉ tiêu chủ đề" của nhóm khi môn HSA dùng ma trận MAPID (UNI-1704 v2): máy chủ bỏ qua hai mục
 * đó ở cả hai chế độ, nên màn chỉ nói nhóm đang chịu quy tắc nào của môn và dẫn tới bảng Quy tắc MAPID để sửa.
 */
export function BlockMapidRules({ section, blockId, mode, reference, onOpenSection, anchor }: BlockMapidRulesProps) {
  const t = useTranslations("exams.mapidRules.group");
  const text = useMapidRuleText(reference);
  const effective = mode === "MATRIX" ? groupEffectiveRules(section, blockId) : [];

  const open = () => {
    onOpenSection();
    void revealMapidRulesTable();
  };

  return (
    <div
      data-testid="mapid-group-rules"
      data-field={anchor}
      tabIndex={anchor ? -1 : undefined}
      className={`flex flex-col gap-2 rounded-md border border-border p-4 ${focusRing}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-label font-semibold text-text">{t("title")}</h3>
          <p className="text-caption text-text-muted">{t("managed")}</p>
        </div>
        <Button type="button" variant="secondary" size="sm" onClick={open}>
          {t("open")}
        </Button>
      </div>
      {mode === "ALL" ? (
        <p className="text-body text-text-secondary">{t("allMode")}</p>
      ) : effective.length === 0 ? (
        <p className="text-body text-text-secondary">{t("none")}</p>
      ) : (
        <ul className="flex flex-col gap-1 text-body text-text">
          {effective.map((item) => (
            <li key={item.rule.id}>{text.short(section, item)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
