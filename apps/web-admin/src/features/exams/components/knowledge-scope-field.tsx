"use client";

import { Button, Checkbox, cn, focusRing, IconButton } from "@thi360/ui";
import { CircleMinus, ListTree } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { ContentReference, KnowledgeRef } from "@/features/content/types";
import type { KnowledgeScope, MapidMode } from "../types";

const EMPTY_SCOPE: KnowledgeScope = { nodeIds: [] };

interface KnowledgeScopeFieldProps {
  /** Phạm vi riêng; `null` hoặc `nodeIds` rỗng: "Toàn môn" (nhóm kế thừa phạm vi của môn, môn không giới hạn MAPID). */
  value: KnowledgeScope | null;
  onChange: (scope: KnowledgeScope | null) => void;
  /**
   * Chỉ nhóm có: phạm vi của môn mà nhóm kế thừa khi nhóm chưa chọn nút nào (B9). Bỏ trống: môn, không có gì để kế thừa.
   * Nút đã chọn khớp câu gắn nút đó hoặc nút con của nó (D23); câu khớp khi có ít nhất một mã đã chọn (2B 3.2).
   */
  inherited?: { scope: KnowledgeScope | null };
  reference: ContentReference | undefined;
  subjectCode: string;
  /** Tên môn hoặc nhóm, cho mô tả hộp chọn. */
  name: string;
  /** Mỏ neo của điều khiển đầu tiên (`data-field`), xem `block-form-fields.ts`. */
  anchor?: string;
  /** SP2E và SPT gọi lựa chọn thứ hai là "Theo mạch kiến thức" thay cho "Theo chủ đề". */
  strand?: boolean;
  /**
   * Số tăng dần: mỗi lần tăng, trường chuyển sang "Theo chủ đề" và mở hộp chọn (menu "Thêm điều kiện" → MAPID).
   * Mở hộp chọn không đổi `value` (R1-3): hủy giữ nguyên.
   */
  openRequest?: number;
  /**
   * Nhóm chùm và phạm vi của môn: ô "Mọi câu con thuộc chủ đề" (`knowledgeScope.everyChildInScope`). Chỉ hiện khi đã chọn
   * MAPID ở "Theo chủ đề"; Toàn môn không có chủ đề nào để câu con thuộc về. Bỏ trống (nhóm câu đơn): không có ô.
   */
  everyChild?: { checked: boolean; onChange: (checked: boolean) => void; anchor?: string };
}

/**
 * Phạm vi sau khi đặt lại danh sách MAPID: mọi đường làm rỗng `nodeIds` (chọn "Toàn môn", bỏ chip cuối, áp dụng hộp chọn
 * với 0 mục) đi qua đây. Còn MAPID: giữ nguyên các khóa khác. Hết MAPID: giữ lọc năng lực (R1-4) và quy về `null` khi không
 * còn gì. Cờ `everyChildInScope` khác nhau theo cấp: phạm vi của môn giữ cờ (đã đặt thì còn qua lưu và tải lại dù ô đang
 * ẩn); phạm vi của nhóm (`blockLevel`) bỏ cờ, vì máy chủ coi mọi phạm vi nhóm khác `null` là ghi đè phạm vi của môn, nên
 * `{ nodeIds: [], everyChildInScope }` làm nhóm mất kế thừa.
 */
function withNodeIds(scope: KnowledgeScope | null, nodeIds: string[], blockLevel: boolean): KnowledgeScope | null {
  if (nodeIds.length > 0) return { ...(scope ?? EMPTY_SCOPE), nodeIds };
  const kept: Omit<KnowledgeScope, "nodeIds"> = {
    ...(scope?.competencyCodes?.length ? { competencyCodes: scope.competencyCodes } : {}),
    ...(!blockLevel && scope?.everyChildInScope !== undefined ? { everyChildInScope: scope.everyChildInScope } : {}),
  };
  return Object.keys(kept).length > 0 ? { nodeIds: [], ...kept } : null;
}

/**
 * Phạm vi kiến thức của môn hoặc của nhóm lấy câu (2B 15.3): hai lựa chọn "Toàn môn" và "Theo chủ đề". Toàn môn: nhóm
 * dùng phạm vi của môn, môn không giới hạn MAPID. Theo chủ đề: chọn nhiều MAPID qua hộp chọn kiến thức (có tìm kiếm).
 * Không có công tắc "khớp tất cả / khớp một": câu khớp khi có ít nhất một mã đã chọn.
 */
export function KnowledgeScopeField({
  value,
  onChange,
  inherited,
  reference,
  subjectCode,
  name,
  anchor,
  strand = false,
  openRequest = 0,
  everyChild,
}: KnowledgeScopeFieldProps) {
  const everyChildId = useId();
  const t = useTranslations("exams.scopeField");
  const radioName = useId();
  const [pickerOpen, setPickerOpen] = useState(false);
  const hasOwn = (value?.nodeIds.length ?? 0) > 0;
  // "Theo chủ đề" chưa chọn mã nào vẫn là một lựa chọn đang mở, nên cần nhớ riêng với `value`.
  const [topicMode, setTopicMode] = useState(hasOwn);
  const [lastRequest, setLastRequest] = useState(openRequest);
  if (openRequest !== lastRequest) {
    setLastRequest(openRequest);
    setTopicMode(true);
    setPickerOpen(true);
  }
  const topic = topicMode || hasOwn;

  const knowledgeIndex = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const own: KnowledgeRef[] = (value?.nodeIds ?? []).map((nodeId) => {
    const node = knowledgeIndex.get(nodeId)?.node;
    return { nodeId, code: node?.code ?? nodeId, name: node?.name ?? "", primary: false };
  });
  const inheritedCount = inherited?.scope?.nodeIds.length ?? 0;
  // Chỉ nhóm truyền `inherited` (phạm vi của môn mà nhóm kế thừa); môn không có.
  const blockLevel = inherited !== undefined;

  const choose = (nextTopic: boolean) => {
    setTopicMode(nextTopic);
    if (!nextTopic && hasOwn) onChange(withNodeIds(value, [], blockLevel));
  };
  const options = [
    { key: "all", checked: !topic, label: t("all"), onSelect: () => choose(false) },
    { key: "topic", checked: topic, label: t(strand ? "strand" : "topic"), onSelect: () => choose(true) },
  ];

  return (
    <fieldset className="flex flex-col gap-3 rounded-md border border-border p-4">
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option, index) => (
          <label
            key={option.key}
            className={cn(
              "flex min-h-10 cursor-pointer items-center gap-2.5 rounded-md border px-3.5 transition-colors duration-150 motion-reduce:transition-none",
              option.checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
            )}
          >
            <input
              type="radio"
              name={radioName}
              checked={option.checked}
              onChange={option.onSelect}
              data-field={index === 0 ? anchor : undefined}
              className={cn("size-4 shrink-0 accent-primary-fill", focusRing)}
            />
            <span className={cn("font-semibold", option.checked ? "text-primary-text" : "text-text")}>{option.label}</span>
          </label>
        ))}
      </div>
      {topic ? (
        <div className="flex flex-wrap items-center gap-2">
          {own.length === 0 && <span className="text-body text-text-muted">{t("topicEmpty")}</span>}
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
                onClick={() => {
                  const rest = (value?.nodeIds ?? []).filter((id) => id !== ref.nodeId);
                  // Bỏ chip cuối thì dữ liệu về phạm vi của môn (nodeIds rỗng): giao diện về "Toàn môn" theo, không kẹt ở "Theo chủ đề" trống.
                  if (rest.length === 0) setTopicMode(false);
                  onChange(withNodeIds(value, rest, blockLevel));
                }}
              />
            </span>
          ))}
          <Button type="button" variant="secondary" size="sm" onClick={() => setPickerOpen(true)}>
            <ListTree />
            <span>{t("pick")}</span>
          </Button>
          <p className="basis-full text-caption text-text-muted">{t("topicHint")}</p>
          {everyChild && own.length > 0 && (
            <div className="flex basis-full items-start gap-2">
              <Checkbox
                id={everyChildId}
                data-field={everyChild.anchor}
                aria-describedby={`${everyChildId}-hint`}
                checked={everyChild.checked}
                onCheckedChange={(checked) => everyChild.onChange(checked === true)}
              />
              <div className="flex flex-col">
                <label htmlFor={everyChildId} className="cursor-pointer text-body font-medium text-text">
                  {t("everyChild")}
                </label>
                <span id={`${everyChildId}-hint`} className="text-caption text-text-muted">
                  {t("everyChildHint")}
                </span>
              </div>
            </div>
          )}
        </div>
      ) : (
        <p className="flex flex-col text-body">
          {inherited ? (
            <>
              <span className="font-medium text-text">{t("inheritsSection")}</span>
              <span className="text-caption text-text-muted">{inheritedCount > 0 ? t("inheritsCount", { count: inheritedCount }) : t("none")}</span>
            </>
          ) : (
            <span className="text-text-muted">{t("none")}</span>
          )}
        </p>
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
          onChange(withNodeIds(value, refs.map((ref) => ref.nodeId), blockLevel));
          setPickerOpen(false);
        }}
      />
    </fieldset>
  );
}

interface MapidModeFieldProps {
  mode: MapidMode;
  onModeChange: (mode: MapidMode) => void;
  /** Số quy tắc đang nằm trong bản nháp; ở "Toàn bài" hiện ghi chú "N quy tắc tạm ngừng áp dụng". */
  ruleCount: number;
  /** Số quy tắc trỏ tới nhóm đã xóa; ở "Toàn bài" bảng bị ẩn nên báo ở đây rằng phải chuyển lại để sửa. */
  invalidCount: number;
  anchor?: string;
}

/**
 * Phạm vi kiến thức của môn HSA dùng ma trận (UNI-1704 v2): "Không giới hạn MAPID" hoặc "Theo ma trận MAPID".
 * Chế độ Không giới hạn MAPID giữ quy tắc trong bản nháp nhưng không áp dụng. Thay cho `KnowledgeScopeField` ở các môn này.
 */
export function MapidModeField({ mode, onModeChange, ruleCount, invalidCount, anchor }: MapidModeFieldProps) {
  const t = useTranslations("exams.mapidRules.mode");
  const radioName = useId();
  const options = [
    { key: "all", value: "ALL" as const, label: t("all") },
    { key: "matrix", value: "MATRIX" as const, label: t("matrix") },
  ];
  return (
    <fieldset className="flex flex-col gap-3 rounded-md border border-border p-4">
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option, index) => {
          const checked = mode === option.value;
          return (
            <label
              key={option.key}
              className={cn(
                "flex min-h-10 cursor-pointer items-center gap-2.5 rounded-md border px-3.5 transition-colors duration-150 motion-reduce:transition-none",
                checked ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
              )}
            >
              <input
                type="radio"
                name={radioName}
                checked={checked}
                onChange={() => onModeChange(option.value)}
                data-testid={`mapid-mode-${option.key}`}
                data-field={index === 0 ? anchor : undefined}
                className={cn("size-4 shrink-0 accent-primary-fill", focusRing)}
              />
              <span className={cn("font-semibold", checked ? "text-primary-text" : "text-text")}>{option.label}</span>
            </label>
          );
        })}
      </div>
      <p className="text-caption text-text-muted">{t(mode === "ALL" ? "allHint" : "matrixHint")}</p>
      {mode === "ALL" && ruleCount > 0 && (
        <div data-testid="mapid-paused-note" className="flex flex-col gap-0.5 text-body">
          <span className="font-medium text-text">{t("paused", { count: ruleCount })}</span>
          <span className="text-caption text-text-muted">{t("pausedHint")}</span>
          {invalidCount > 0 && <span className="text-caption text-danger">{t("pausedInvalid", { count: invalidCount })}</span>}
        </div>
      )}
    </fieldset>
  );
}
