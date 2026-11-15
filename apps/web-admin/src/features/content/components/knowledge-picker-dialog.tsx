"use client";

import {
  Badge,
  Button,
  Checkbox,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  IconButton,
  Input,
  Select,
  Skeleton,
} from "@thi360/ui";
import { ChevronDown, ChevronRight, CircleMinus, Info, Search } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState, type ReactNode } from "react";
import {
  countSelectableLeaves,
  filterKnowledgeTree,
  indexKnowledge,
  knowledgePath,
  normalizePrimary,
  type KnowledgeIndexEntry,
} from "../knowledge-tree";
import { useContentReference } from "../queries";
import type { KnowledgeLevel, KnowledgeNode, KnowledgeRef } from "../types";

const ALL = "__all__";

export interface KnowledgePickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  value: readonly KnowledgeRef[];
  onApply: (value: KnowledgeRef[]) => void;
  defaultSubjectCode?: string | null;
  defaultGradeCode?: string | null;
  /** Câu hỏi: chọn một kiến thức chính. Phạm vi khối của bài thi: tắt. */
  withPrimary?: boolean;
  /** Cấp được tick. Câu hỏi chỉ cấp Kiến thức; phạm vi khối bài thi cho cả Chương và Bài. */
  selectableLevels?: readonly KnowledgeLevel[];
  /** Câu mô tả dưới tiêu đề, ví dụ "Câu hỏi đang soạn: Toán · Lớp 11". */
  description?: string;
  /** Ghi chú cuối cột "Đã chọn". Mặc định (khi `withPrimary`): một câu chỉ được chọn một lần trong cùng đề. */
  selectionNote?: string | null;
  /** Tên nút áp dụng, mặc định "Áp dụng (n)". Nhận số mục đang chọn. */
  applyLabel?: (count: number) => string;
}

/**
 * Hộp thoại chọn kiến thức (MAPID) theo cây danh mục Cấu trúc kiến thức (canvas `ChonKienThuc`). Dùng chung cho
 * bộ lọc Ngân hàng câu hỏi, soạn câu hỏi và phạm vi khối câu hỏi của bài thi. Chỉ hiện nút đang hoạt động.
 *
 * Bố cục: hàng lọc (môn, khối, tìm), cây bên trái (Chương › Bài › Kiến thức, mỗi nhánh có nút mở/thu và số kiến
 * thức bên dưới), cột "Đã chọn" bên phải (đường dẫn, chọn kiến thức chính, bỏ chọn). Khung hẹp xếp chồng hai cột.
 */
export function KnowledgePickerDialog(props: KnowledgePickerDialogProps) {
  const t = useTranslations("content.knowledgePicker");
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent size="xl" closeLabel={t("close")}>
        {props.open && <PickerBody {...props} />}
      </DialogContent>
    </Dialog>
  );
}

/** Nhánh mở sẵn khi mở hộp thoại: tổ tiên của các mục đã chọn; chưa chọn gì thì nhánh đầu tiên. */
function initialExpanded(
  value: readonly KnowledgeRef[],
  index: Map<string, KnowledgeIndexEntry>,
  tree: readonly KnowledgeNode[],
): Set<string> {
  const expanded = new Set<string>();
  for (const ref of value) {
    for (const ancestor of index.get(ref.nodeId)?.ancestors ?? []) expanded.add(ancestor.id);
  }
  if (expanded.size === 0) {
    let node: KnowledgeNode | undefined = tree[0];
    while (node && node.level !== "KNOWLEDGE") {
      expanded.add(node.id);
      node = node.children[0];
    }
  }
  return expanded;
}

function PickerBody({
  onOpenChange,
  value,
  onApply,
  defaultSubjectCode,
  defaultGradeCode,
  withPrimary = true,
  selectableLevels = ["KNOWLEDGE"],
  description,
  selectionNote,
  applyLabel,
}: KnowledgePickerDialogProps) {
  const t = useTranslations("content.knowledgePicker");
  const referenceQuery = useContentReference();
  const reference = referenceQuery.data;
  const [subjectCode, setSubjectCode] = useState<string>(defaultSubjectCode ?? ALL);
  const [gradeCode, setGradeCode] = useState<string>(defaultGradeCode ?? ALL);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<KnowledgeRef[]>(() =>
    withPrimary ? normalizePrimary(value) : value.map((ref) => ({ ...ref })),
  );
  const [expanded, setExpanded] = useState<ReadonlySet<string> | null>(null);
  const hintId = useId();
  const treeHeadingId = useId();
  const selectedHeadingId = useId();

  const index = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const subjectFilter = subjectCode === ALL ? null : subjectCode;
  const gradeFilter = gradeCode === ALL ? null : gradeCode;
  const tree = useMemo(
    () =>
      filterKnowledgeTree(reference?.knowledgeTree ?? [], {
        subjectCode: subjectFilter,
        gradeCode: gradeFilter,
        query,
      }),
    [reference, subjectFilter, gradeFilter, query],
  );
  // Cây chỉ có sau khi danh mục về: tính nhánh mở sẵn một lần ở lần đầu có dữ liệu.
  const openSet = expanded ?? (reference ? initialExpanded(value, index, tree) : new Set<string>());
  // Đang tìm thì mở hết các nhánh còn lại để thấy ngay nút khớp.
  const searching = query.trim() !== "";
  const selectedIds = new Set(selected.map((ref) => ref.nodeId));

  const toggle = (node: KnowledgeNode) => {
    setSelected((current) => {
      const next = current.some((ref) => ref.nodeId === node.id)
        ? current.filter((ref) => ref.nodeId !== node.id)
        : [...current, { nodeId: node.id, code: node.code, name: node.name, primary: false }];
      return withPrimary ? normalizePrimary(next) : next;
    });
  };
  const remove = (nodeId: string) =>
    setSelected((current) => {
      const next = current.filter((item) => item.nodeId !== nodeId);
      return withPrimary ? normalizePrimary(next) : next;
    });
  const makePrimary = (nodeId: string) =>
    setSelected((current) => current.map((ref) => ({ ...ref, primary: ref.nodeId === nodeId })));
  const toggleBranch = (id: string) =>
    setExpanded(() => {
      const next = new Set(openSet);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const subjectOptions = [
    { value: ALL, label: t("allSubjects") },
    ...(reference?.subjects ?? []).map((option) => ({ value: option.code, label: option.name })),
  ];
  const gradeOptions = [
    { value: ALL, label: t("allGrades") },
    ...(reference?.grades ?? []).map((option) => ({ value: option.code, label: option.name })),
  ];
  const scopeLabel = [
    subjectFilter ? subjectOptions.find((option) => option.value === subjectFilter)?.label : t("allSubjects"),
    gradeFilter ? gradeOptions.find((option) => option.value === gradeFilter)?.label : t("allGrades"),
  ].join(" · ");
  const note = selectionNote === undefined ? (withPrimary ? t("examOnceNote") : null) : selectionNote;

  const renderNode = (node: KnowledgeNode, depth: number): ReactNode => {
    // Đã chọn một môn thì bỏ cấp môn, cây bắt đầu từ Chương như canvas.
    if (node.level === "SUBJECT" && subjectFilter) return node.children.map((child) => renderNode(child, depth));
    const selectable = selectableLevels.includes(node.level);
    const hasChildren = node.children.length > 0;
    const isOpen = searching || openSet.has(node.id);
    const checked = selectedIds.has(node.id);
    const leafCount = hasChildren ? countSelectableLeaves(node) : 0;
    const codeChip = (
      <span className="shrink-0 rounded-sm bg-subtle px-1.5 py-0.5 text-caption font-semibold whitespace-nowrap text-text">
        {node.code}
      </span>
    );
    return (
      <li key={node.id} className="flex flex-col">
        <div
          className={cn(
            "flex min-h-11 items-center gap-1.5 rounded-lg pe-2",
            checked && "bg-primary-50",
          )}
          style={{ paddingInlineStart: `${depth * 1.5}rem` }}
        >
          {hasChildren && !searching ? (
            <IconButton
              aria-expanded={isOpen}
              aria-label={isOpen ? t("collapse", { name: `${node.code} ${node.name}` }) : t("expand", { name: `${node.code} ${node.name}` })}
              tooltip={isOpen ? t("collapseTooltip") : t("expandTooltip")}
              onClick={() => toggleBranch(node.id)}
              icon={isOpen ? <ChevronDown /> : <ChevronRight />}
            />
          ) : (
            <span aria-hidden className="w-10 shrink-0" />
          )}
          {selectable ? (
            <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5 py-1.5">
              <Checkbox checked={checked} onCheckedChange={() => toggle(node)} />
              {codeChip}{" "}
              <span className={cn("min-w-0 flex-1 line-clamp-2 wrap-anywhere text-text", hasChildren && "font-medium")}>
                {node.name}
              </span>
            </label>
          ) : (
            <span className="flex min-w-0 flex-1 items-center gap-2.5 py-1.5">
              {codeChip}{" "}
              <span className="min-w-0 flex-1 line-clamp-2 font-medium wrap-anywhere text-text">{node.name}</span>
            </span>
          )}
          {hasChildren && (
            <span className="shrink-0 text-caption whitespace-nowrap text-text-muted tabular-nums">
              {t("leafCount", { count: leafCount })}
            </span>
          )}
        </div>
        {hasChildren && isOpen && (
          <ul className="m-0 list-none p-0">{node.children.map((child) => renderNode(child, depth + 1))}</ul>
        )}
      </li>
    );
  };

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t("title")}</DialogTitle>
        <DialogDescription>{description ?? t("description")}</DialogDescription>
      </DialogHeader>

      <search className="grid gap-3 sm:grid-cols-[1fr_1fr_2fr]">
        <FormField label={t("subject")}>
          {(controlProps) => (
            <Select {...controlProps} options={subjectOptions} value={subjectCode} onValueChange={setSubjectCode} />
          )}
        </FormField>
        <FormField label={t("grade")}>
          {(controlProps) => (
            <Select {...controlProps} options={gradeOptions} value={gradeCode} onValueChange={setGradeCode} />
          )}
        </FormField>
        <FormField label={t("search")}>
          {(controlProps) => (
            <Input
              {...controlProps}
              type="search"
              leadingIcon={<Search />}
              value={query}
              placeholder={t("searchPlaceholder")}
              onChange={(event) => setQuery(event.target.value)}
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
            />
          )}
        </FormField>
      </search>

      <div className="grid min-h-0 gap-4 md:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
        <section aria-labelledby={treeHeadingId} className="flex min-h-64 min-w-0 flex-col rounded-xl border border-border">
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b border-border bg-subtle px-3.5 py-2.5">
            <h3 id={treeHeadingId} className="text-label font-semibold text-text-secondary">
              {t("treeTitle")} <span className="font-normal text-text-muted">· {t("treeLevels")}</span>
            </h3>
            <span className="text-caption text-text-muted">{scopeLabel}</span>
          </div>
          {referenceQuery.isPending ? (
            <div role="status" aria-label={t("loading")} className="flex flex-col gap-2 p-3">
              <Skeleton className="h-9" />
              <Skeleton className="h-9" />
              <Skeleton className="h-9" />
              <Skeleton className="h-9" />
            </div>
          ) : referenceQuery.isError ? (
            <div role="alert" className="flex flex-col items-start gap-2 p-3.5 text-body text-danger">
              <p>{t("loadError")}</p>
              <Button variant="secondary" size="sm" onClick={() => void referenceQuery.refetch()}>
                {t("retry")}
              </Button>
            </div>
          ) : tree.length === 0 ? (
            <p className="p-3.5 text-body text-text-muted">{t("empty")}</p>
          ) : (
            <ul className="scroll-shadows-y m-0 max-h-96 list-none overflow-auto p-1.5">
              {tree.map((node) => renderNode(node, 0))}
            </ul>
          )}
          <p className="mt-auto border-t border-border px-3.5 py-2 text-caption text-text-muted">{t("sampleNote")}</p>
        </section>

        <section aria-labelledby={selectedHeadingId} className="flex min-w-0 flex-col rounded-xl border border-border">
          <h3
            id={selectedHeadingId}
            className="border-b border-border bg-subtle px-3.5 py-2.5 text-label font-semibold text-text-secondary"
          >
            {t("selectedTitle", { count: selected.length })}
          </h3>
          <fieldset className="scroll-shadows-y m-0 flex max-h-96 min-w-0 flex-col gap-2.5 overflow-auto border-0 p-3.5">
            <legend className="sr-only">{withPrimary ? t("primary") : t("selectedLegend")}</legend>
            {withPrimary && (
              <p id={hintId} className="text-caption text-text-muted">
                {t("primaryHint")}
              </p>
            )}
            {selected.length === 0 && <p className="text-body text-text-muted">{t("noneSelectedHint")}</p>}
            {selected.map((ref) => {
              const entry = index.get(ref.nodeId);
              const path = entry ? knowledgePath(entry) : "";
              return (
                <div key={ref.nodeId} className="flex items-start gap-2 rounded-lg border border-border py-2.5 ps-3 pe-1.5">
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <div className="font-semibold wrap-break-word text-text">
                      {ref.code} {ref.name}
                    </div>
                    {path && <div className="text-caption text-text-muted">{path}</div>}
                    {withPrimary && (
                      <label className="flex min-h-9 cursor-pointer items-center gap-2 text-label">
                        <input
                          type="radio"
                          name="primaryKnowledge"
                          checked={ref.primary}
                          onChange={() => makePrimary(ref.nodeId)}
                          aria-describedby={hintId}
                          className="size-4 accent-primary-fill"
                        />
                        {t("primary")}
                        {ref.primary && <Badge>{t("primaryBadge")}</Badge>}
                      </label>
                    )}
                  </div>
                  <IconButton
                    tone="danger"
                    aria-label={t("remove", { code: `${ref.code} ${ref.name}` })}
                    tooltip={t("removeTooltip")}
                    onClick={() => remove(ref.nodeId)}
                    icon={<CircleMinus />}
                  />
                </div>
              );
            })}
          </fieldset>
          {note && (
            <p className="mt-auto flex items-start gap-2 border-t border-border px-3.5 py-2 text-caption text-text-muted">
              <Info aria-hidden className="mt-0.5 size-3.5 shrink-0" />
              <span>{note}</span>
            </p>
          )}
        </section>
      </div>

      <DialogFooter>
        <Button variant="secondary" onClick={() => onOpenChange(false)}>
          {t("cancel")}
        </Button>
        <Button
          onClick={() => {
            onApply(withPrimary ? normalizePrimary(selected) : selected.map((ref) => ({ ...ref, primary: false })));
            onOpenChange(false);
          }}
        >
          {applyLabel ? applyLabel(selected.length) : t("apply", { count: selected.length })}
        </Button>
      </DialogFooter>
    </>
  );
}
