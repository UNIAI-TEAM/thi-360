"use client";

import {
  Badge,
  Button,
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
import { ChevronDown, ChevronRight, CircleMinus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState, type ReactNode } from "react";
import { filterKnowledgeTree, indexKnowledge, knowledgePath, normalizePrimary } from "../knowledge-tree";
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
}

/**
 * Hộp thoại chọn kiến thức (MAPID) theo cây danh mục Cấu trúc kiến thức (canvas `ChonKienThuc`). Dùng chung cho
 * bộ lọc Ngân hàng câu hỏi, soạn câu hỏi và phạm vi khối câu hỏi của bài thi. Chỉ hiện nút đang hoạt động.
 */
export function KnowledgePickerDialog(props: KnowledgePickerDialogProps) {
  const t = useTranslations("content.knowledgePicker");
  return (
    <Dialog open={props.open} onOpenChange={props.onOpenChange}>
      <DialogContent size="xl" closeLabel={t("cancel")}>
        {props.open && <PickerBody {...props} />}
      </DialogContent>
    </Dialog>
  );
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
}: KnowledgePickerDialogProps) {
  const t = useTranslations("content.knowledgePicker");
  const referenceQuery = useContentReference();
  const reference = referenceQuery.data;
  const [subjectCode, setSubjectCode] = useState<string>(defaultSubjectCode ?? ALL);
  const [gradeCode, setGradeCode] = useState<string>(defaultGradeCode ?? ALL);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<KnowledgeRef[]>(() => normalizePrimary(value));
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set());
  const hintId = useId();

  const index = useMemo(() => indexKnowledge(reference?.knowledgeTree ?? []), [reference]);
  const tree = useMemo(
    () =>
      filterKnowledgeTree(reference?.knowledgeTree ?? [], {
        subjectCode: subjectCode === ALL ? null : subjectCode,
        gradeCode: gradeCode === ALL ? null : gradeCode,
        query,
      }),
    [reference, subjectCode, gradeCode, query],
  );
  const selectedIds = new Set(selected.map((ref) => ref.nodeId));

  const toggle = (node: KnowledgeNode) => {
    setSelected((current) =>
      current.some((ref) => ref.nodeId === node.id)
        ? normalizePrimary(current.filter((ref) => ref.nodeId !== node.id))
        : normalizePrimary([...current, { nodeId: node.id, code: node.code, name: node.name, primary: false }]),
    );
  };
  const makePrimary = (nodeId: string) =>
    setSelected((current) => current.map((ref) => ({ ...ref, primary: ref.nodeId === nodeId })));
  const toggleBranch = (id: string) =>
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const subjectOptions = [
    { value: ALL, label: t("subject") },
    ...(reference?.subjects ?? []).map((option) => ({ value: option.code, label: option.name })),
  ];
  const gradeOptions = [
    { value: ALL, label: t("allGrades") },
    ...(reference?.grades ?? []).map((option) => ({ value: option.code, label: option.name })),
  ];

  const renderNode = (node: KnowledgeNode, depth: number): ReactNode => {
    if (node.level === "SUBJECT") return node.children.map((child) => renderNode(child, depth));
    const selectable = selectableLevels.includes(node.level);
    const hasChildren = node.children.length > 0;
    const open = !collapsed.has(node.id);
    return (
      <li key={node.id} className="flex flex-col">
        <div className="flex min-h-10 items-center gap-2" style={{ paddingInlineStart: `${depth * 1.25}rem` }}>
          {hasChildren ? (
            <IconButton
              aria-expanded={open}
              aria-label={open ? t("collapse", { name: node.name }) : t("expand", { name: node.name })}
              onClick={() => toggleBranch(node.id)}
              icon={open ? <ChevronDown aria-hidden /> : <ChevronRight aria-hidden />}
            />
          ) : (
            <span aria-hidden className="w-10 shrink-0" />
          )}
          <label className={cn("flex min-w-0 flex-1 items-center gap-2.5", selectable && "cursor-pointer")}>
            {selectable && (
              <input
                type="checkbox"
                checked={selectedIds.has(node.id)}
                onChange={() => toggle(node)}
                className="size-4 shrink-0 accent-primary-fill"
              />
            )}
            <span className="shrink-0 rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
              {node.code}
            </span>
            <span className={cn("min-w-0 flex-1 truncate", !selectable && "font-medium")}>{node.name}</span>
          </label>
        </div>
        {hasChildren && open && <ul className="m-0 list-none p-0">{node.children.map((c) => renderNode(c, depth + 1))}</ul>}
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
              value={query}
              placeholder={t("searchPlaceholder")}
              onChange={(event) => setQuery(event.target.value)}
              autoComplete="off"
              spellCheck={false}
            />
          )}
        </FormField>
      </search>

      <div className="grid min-h-0 gap-4 md:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
        <section aria-label={t("treeTitle")} className="flex min-h-64 flex-col rounded-xl border border-border">
          <h3 className="border-b border-border bg-subtle px-3.5 py-2.5 text-label font-semibold text-text-secondary">
            {t("treeTitle")}
          </h3>
          {referenceQuery.isPending ? (
            <div className="flex flex-col gap-2 p-3">
              <Skeleton className="h-8" />
              <Skeleton className="h-8" />
              <Skeleton className="h-8" />
            </div>
          ) : referenceQuery.isError ? (
            <div className="flex flex-col items-start gap-2 p-3.5 text-body text-danger">
              <p>{t("loadError")}</p>
              <Button variant="secondary" size="sm" onClick={() => void referenceQuery.refetch()}>
                {t("retry")}
              </Button>
            </div>
          ) : tree.length === 0 ? (
            <p className="p-3.5 text-body text-text-muted">{t("empty")}</p>
          ) : (
            <ul className="m-0 max-h-96 list-none overflow-auto p-1.5">{tree.map((node) => renderNode(node, 0))}</ul>
          )}
        </section>

        <section aria-label={t("selectedTitle", { count: selected.length })} className="flex flex-col rounded-xl border border-border">
          <h3 className="border-b border-border bg-subtle px-3.5 py-2.5 text-label font-semibold text-text-secondary">
            {t("selectedTitle", { count: selected.length })}
          </h3>
          <fieldset className="m-0 flex min-w-0 flex-col gap-2.5 border-0 p-3.5">
            <legend className="sr-only">{t("primary")}</legend>
            {withPrimary && (
              <p id={hintId} className="text-caption text-text-muted">
                {t("primaryHint")}
              </p>
            )}
            {selected.length === 0 && <p className="text-body text-text-muted">{t("noneSelected")}</p>}
            {selected.map((ref) => {
              const entry = index.get(ref.nodeId);
              return (
                <div key={ref.nodeId} className="flex items-start gap-2 rounded-lg border border-border py-2.5 pr-1.5 pl-3">
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <div className="font-semibold wrap-break-word text-text">
                      {ref.code} {ref.name}
                    </div>
                    {entry && <div className="text-caption text-text-muted">{knowledgePath(entry)}</div>}
                    {withPrimary && (
                      <label className="flex min-h-9 items-center gap-2 text-label">
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
                    aria-label={t("remove", { code: ref.code })}
                    onClick={() =>
                      setSelected((current) => normalizePrimary(current.filter((item) => item.nodeId !== ref.nodeId)))
                    }
                    icon={<CircleMinus aria-hidden />}
                  />
                </div>
              );
            })}
          </fieldset>
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
          {t("apply", { count: selected.length })}
        </Button>
      </DialogFooter>
    </>
  );
}
