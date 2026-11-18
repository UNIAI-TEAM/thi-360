"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { MAX_CHANGE_LINES, type StructureChange } from "./exam-structure-changes";

/** Nơi người dùng đang chuyển tới: câu đầu của hộp thoại đổi theo đích (bản vẽ `HopThoaiChuaLuu2B`). */
export type LeaveDestination =
  | { kind: "item"; name: string }
  | { kind: "tab"; tab: string }
  | { kind: "general" }
  | { kind: "page" };

export interface ExamStructureLeaveDialogProps {
  open: boolean;
  /** Đang lưu ("Lưu và chuyển"): khóa các nút và không đóng được hộp thoại. */
  saving: boolean;
  /** Lỗi của lần lưu vừa rồi; hộp thoại ở lại và hiện lỗi (R-05). */
  error: string | null;
  /** Môn hoặc phần đang mở, hiện trong tiêu đề; `null` khi không có mục nào đang mở. */
  itemName: string | null;
  destination: LeaveDestination;
  /** Thay đổi chưa lưu so với bản đã lưu; hiện tối đa `MAX_CHANGE_LINES` dòng. */
  changes: readonly StructureChange[];
  onSaveAndGo: () => void;
  onDiscard: () => void;
  onStay: () => void;
}

/**
 * Hộp thoại ba nút khi còn thay đổi chưa lưu (R-05, bản vẽ `HopThoaiChuaLuu2B`): **Lưu và chuyển**, **Bỏ thay đổi**,
 * **Ở lại**. Tiêu đề gọi tên môn hoặc phần đang mở, câu đầu nói đích đến, rồi liệt kê thay đổi. Hiện khi chuyển môn hoặc
 * phần, quay lại bước 1, đổi tab và rời trang trong ứng dụng; đóng tab hoặc tải lại trang dùng hộp thoại của trình
 * duyệt. "Lưu và chuyển" lỗi thì ở lại và hiện lỗi.
 */
export function ExamStructureLeaveDialog({
  open,
  saving,
  error,
  itemName,
  destination,
  changes,
  onSaveAndGo,
  onDiscard,
  onStay,
}: ExamStructureLeaveDialogProps) {
  const t = useTranslations("exams.structure.leave");
  const format = useFormatter();
  const shown = changes.slice(0, MAX_CHANGE_LINES);
  const value = (input: number | null) => (input === null ? t("change.empty") : format.number(input, { maximumFractionDigits: 2 }));
  const line = (change: StructureChange): string => {
    switch (change.kind) {
      case "partRenamed":
        return t("change.partRenamed", { from: change.from, to: change.to });
      case "partNumber":
        return t(`change.partNumber.${change.field}`, { part: change.part, from: value(change.from), to: value(change.to) });
      case "partSettings":
        return t("change.partSettings", { part: change.part });
      case "sectionCount":
        return t("change.sectionCount", { subject: change.subject, from: value(change.from), to: value(change.to) });
      case "sectionSettings":
        return t("change.sectionSettings", { subject: change.subject });
      case "blockAdded":
        return t("change.blockAdded", { subject: change.subject, name: change.name });
      case "blockRemoved":
        return t("change.blockRemoved", { subject: change.subject, name: change.name });
      case "blockRenamed":
        return t("change.blockRenamed", { from: change.from, to: change.to });
      case "blockCount":
        return t("change.blockCount", { name: change.name, from: value(change.from), to: value(change.to) });
      case "blockSettings":
        return t("change.blockSettings", { name: change.name });
    }
  };
  return (
    <Dialog open={open} onOpenChange={(next) => (!next && !saving ? onStay() : undefined)}>
      <DialogContent size="md" closeLabel={t("close")} data-testid="structure-leave-dialog">
        <DialogHeader>
          <DialogTitle>{itemName ? t("title", { name: itemName }) : t("titleGeneric")}</DialogTitle>
          <DialogDescription>
            {t(`destination.${destination.kind}`, {
              name: destination.kind === "item" ? destination.name : "",
              tab: destination.kind === "tab" ? destination.tab : "",
            })}{" "}
            {t("changesIntro")}
          </DialogDescription>
        </DialogHeader>
        {shown.length > 0 && (
          <ul className="flex list-disc flex-col gap-1 pl-5 text-body text-text-secondary" data-testid="structure-leave-changes">
            {shown.map((change, index) => (
              <li key={index}>{line(change)}</li>
            ))}
            {changes.length > shown.length && <li className="list-none">{t("changesMore", { count: changes.length - shown.length })}</li>}
          </ul>
        )}
        {error && (
          <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" disabled={saving} onClick={onStay}>
            {t("stay")}
          </Button>
          <Button type="button" variant="danger" disabled={saving} onClick={onDiscard}>
            {t("discard")}
          </Button>
          <Button type="button" loading={saving} onClick={onSaveAndGo}>
            {t("saveAndGo")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
