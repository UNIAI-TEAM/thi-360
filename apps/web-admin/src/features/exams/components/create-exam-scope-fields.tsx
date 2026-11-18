"use client";

import { FormField, Select } from "@thi360/ui";
import { Info, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import type { CatalogOption, ExamKindOption } from "@/features/content/types";
import {
  hasDedicatedParts,
  hasLockedOmittedPart,
  kindOptionFor,
  latestTemplate,
  partCodesForKind,
  partNeedsSubject,
  type ScopeSelection,
  scopesForKind,
  subjectAudioParts,
  subjectPartOptions,
  templateCountBySubject,
} from "../create-exam-schema";
import type { ExamBuildMode, ExamScope, ExamTemplate } from "../types";
import { ChoiceCardGroup, type ChoiceCardOption, ScopeField, useKindLabel, usePartNames } from "./create-exam-fields";

/** Giá trị thẻ "Nghe và Đọc" trong nhóm phạm vi tách theo audio; thẻ còn lại mang mã phần (`P<thứ tự>`). */
const WHOLE_SUBJECT = "SUBJECT";

/**
 * Kỳ thi, phạm vi, môn, phần và cách dựng bài của bước 1 (2B 15.1, H4, K8; G2c `TaoBaiThiHscaVsat2B`):
 * - phạm vi chỉ gồm các phạm vi hồ sơ kỳ thi cho phép (`ScopeField`);
 * - kỳ thi `templateBy = SUBJECT` (SP2E, SPT) chọn môn trước rồi mới chọn phần khi phạm vi là Theo phần;
 * - kỳ thi `templateBy = SUBJECT` dùng một đồng hồ chung cho cả môn (H-SCA, V-SAT, `timing = SHARED`) đặt môn trước phạm
 *   vi, gọi phạm vi cả môn là "Cả môn"; môn tách phần theo audio (H-SCA Tiếng Anh) thay hai thẻ bằng Nghe, Đọc, Nghe và
 *   Đọc; phần viết đã lược (`omittedLocked`) và phần không có bài riêng không có trong danh sách phần;
 * - hai thẻ `build_mode` chỉ hiện khi phạm vi là Theo môn;
 * - không chọn phiên bản cấu trúc ở bước 1 (K8): phiên bản mặc định do máy chủ nạp.
 * Component chỉ báo thay đổi qua `onChange`; trang quyết định nạp lại ngay hay hỏi xác nhận.
 */
export function CreateExamScopeFields({
  kinds,
  subjects,
  templates,
  selection,
  errors,
  disabled,
  onChange,
}: {
  kinds: readonly ExamKindOption[];
  subjects: readonly CatalogOption[];
  templates: readonly ExamTemplate[];
  selection: ScopeSelection;
  errors: { partCode?: string; subjectCode?: string };
  disabled?: boolean;
  onChange: (change: Partial<ScopeSelection>) => void;
}) {
  const t = useTranslations("exams.create.step1");
  const tCreate = useTranslations("exams.create");
  const kindLabel = useKindLabel();
  const partNames = usePartNames();
  const kindOption = kindOptionFor(kinds, selection.kind);
  const scopes = scopesForKind(kinds, selection.kind);
  // Một đồng hồ chung cho cả môn: bài cả môn và bài theo phần của cùng một môn, môn chọn trước phạm vi.
  const subjectLeads =
    kindOption?.templateBy === "SUBJECT" && kindOption.timing === "SHARED" && scopes.includes("SUBJECT") && scopes.includes("PART");
  const subjectFirst = selection.scope === "PART" && partNeedsSubject(kinds, selection.kind);
  const showSubject = selection.scope === "SUBJECT" || subjectFirst;
  const audioParts = subjectLeads && selection.subjectCode ? subjectAudioParts(templates, selection.kind, selection.subjectCode) : [];
  const partOptions = subjectFirst
    ? subjectPartOptions(templates, selection.kind, selection.subjectCode).map((option) => ({ value: option.code, label: option.name }))
    : selection.scope === "PART"
      ? partCodesForKind(templates, kinds, selection.kind).map((code) => ({ value: code, label: partNames.partName(code) }))
      : [];
  const counts = templateCountBySubject(templates, selection.kind);
  /*
   * Chỉ chào môn mà API có mẫu cho phạm vi đang chọn (F-06): Theo môn = có mẫu Theo môn; môn trước phần (SP2E, SPT
   * Theo phần, R2-02) = mẫu Theo môn mặc định STANDARD có phần. Kỳ thi chưa có mẫu nào cho phạm vi thì chào hết để
   * người tạo dựng bảng trống.
   */
  const offers = new Set(
    subjects
      .filter((subject) =>
        subjectFirst
          ? subjectPartOptions(templates, selection.kind, subject.code).length > 0
          : counts.has(subject.code),
      )
      .map((subject) => subject.code),
  );
  const subjectOptions = subjects
    .filter((subject) => offers.size === 0 || offers.has(subject.code))
    .map((subject) => ({ value: subject.code, label: subject.name }));
  const noTemplate =
    offers.size === 0 ||
    !latestTemplate(templates, { kind: selection.kind, scope: "SUBJECT", partCode: "", subjectCode: selection.subjectCode, buildMode: selection.buildMode ?? "STANDARD" });
  const subjectName = subjects.find((subject) => subject.code === selection.subjectCode)?.name ?? selection.subjectCode;
  // Môn mà mẫu mặc định STANDARD không công bố được (H26, THPTQG Ngữ văn): báo rõ bài chỉ lưu nháp, tạo vẫn được.
  const notPublishable =
    Boolean(selection.subjectCode) &&
    latestTemplate(templates, {
      kind: selection.kind,
      scope: "SUBJECT",
      partCode: "",
      subjectCode: selection.subjectCode,
      buildMode: "STANDARD",
    })?.publishable === false;
  const buildModes: Array<{ value: ExamBuildMode; label: string; description: string }> = [
    {
      value: "STANDARD",
      label: t("buildModeStandard", { kind: kindLabel(selection.kind) }),
      description: t("buildModeStandardHint"),
    },
    { value: "REVIEW", label: t("buildModeReview"), description: t("buildModeReviewHint") },
  ];

  /** Phạm vi của kỳ thi một đồng hồ chung: Cả môn, Theo phần; môn tách theo audio thì Nghe, Đọc, Nghe và Đọc. */
  const sharedClockScopes = (): { value: string; options: ChoiceCardOption<string>[]; pick: (value: string) => void } => {
    if (audioParts.length > 0) {
      const total = audioParts.reduce((sum, option) => sum + option.questionCount, 0);
      return {
        value: selection.scope === "SUBJECT" ? WHOLE_SUBJECT : selection.partCode,
        options: [
          ...audioParts.map((option) => ({
            value: option.code,
            label: option.audio === "REQUIRED" ? t("skillListening") : t("skillReading"),
            description: t(option.audio === "REQUIRED" ? "skillListeningHint" : "skillReadingHint", { count: option.questionCount }),
          })),
          { value: WHOLE_SUBJECT, label: t("skillBoth"), description: t("skillBothHint", { count: total }) },
        ],
        pick: (value) => onChange(value === WHOLE_SUBJECT ? { scope: "SUBJECT" } : { scope: "PART", partCode: value }),
      };
    }
    const all: Array<ChoiceCardOption<ExamScope>> = [
      { value: "SUBJECT", label: t("scopeWhole"), description: t("scopeWholeHint") },
      { value: "PART", label: t("scopeByPart"), description: t("scopeByPartHint") },
    ];
    return {
      value: selection.scope,
      options: all.filter((option) => scopes.includes(option.value)),
      pick: (value) => onChange({ scope: value as ExamScope }),
    };
  };

  const subjectField = showSubject && (
    <div data-field="subjectCode" className="flex flex-col gap-3">
      <FormField
        label={t("subject")}
        required
        hint={subjectFirst && !subjectLeads ? t("subjectBeforePartHint") : t("subjectHint", { count: counts.size })}
        error={errors.subjectCode}
      >
        {(controlProps) => (
          <Select
            {...controlProps}
            name="subjectCode"
            value={selection.subjectCode || undefined}
            disabled={disabled}
            placeholder={t("subjectPlaceholder")}
            onValueChange={(subjectCode) => onChange({ subjectCode })}
            options={subjectOptions}
          />
        )}
      </FormField>
      {Boolean(selection.subjectCode) && notPublishable && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-md border border-warning/40 bg-warning-bg px-3 py-2.5 text-body text-text-secondary"
        >
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
          <span className="flex flex-col gap-0.5">
            <span>{t("notPublishableNotice", { subject: subjectName })}</span>
            <span className="text-caption">{t("notPublishableDraft")}</span>
          </span>
        </p>
      )}
      {Boolean(selection.subjectCode) && noTemplate && !subjectFirst && (
        <p className="flex items-start gap-2 rounded-md border border-dashed border-border px-3 py-2.5 text-body text-text-secondary">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-text-muted" />
          {t("noTemplate", { subject: subjectName })}
        </p>
      )}
    </div>
  );

  const scopeField = (() => {
    if (!subjectLeads) {
      return (
        <ScopeField
          kinds={kinds}
          kind={selection.kind}
          value={selection.scope}
          disabled={disabled}
          onChange={(scope) => onChange({ scope })}
        />
      );
    }
    const group = sharedClockScopes();
    return (
      <ChoiceCardGroup
        legend={tCreate("scope")}
        name="scope"
        value={group.value}
        required
        disabled={disabled}
        options={group.options}
        onChange={group.pick}
      />
    );
  })();

  // Môn tách theo audio chọn phần bằng chính các thẻ phạm vi, nên không có nhóm Phần thi riêng.
  const showPartGroup = selection.scope === "PART" && audioParts.length === 0;
  const notices = [
    subjectLeads && Boolean(selection.subjectCode) && hasLockedOmittedPart(templates, selection.kind, selection.subjectCode)
      ? t("omittedPartNotice")
      : null,
    subjectLeads &&
    selection.scope === "PART" &&
    Boolean(selection.subjectCode) &&
    hasDedicatedParts(templates, selection.kind, selection.subjectCode)
      ? t("dedicatedPartsNotice", { subject: subjectName })
      : null,
  ].filter((text): text is string => text !== null);

  return (
    <div className="flex flex-col gap-4">
      {subjectLeads ? (
        <>
          {subjectField}
          {scopeField}
        </>
      ) : (
        <>
          {scopeField}
          {subjectField}
        </>
      )}
      {showPartGroup && (
        <div data-field="partCode" className="flex flex-col gap-1">
          {partOptions.length > 0 ? (
            <ChoiceCardGroup
              legend={t("part")}
              name="partCode"
              value={selection.partCode}
              required
              disabled={disabled}
              // V-ACT và THPTQG theo bản vẽ 2B dùng thẻ; các kỳ thi cũ giữ nút viên thuốc của ma trận.
              variant={selection.kind === "VACT" || selection.kind === "THPTQG" || subjectLeads ? "card" : "pill"}
              hint={t("partHint")}
              options={partOptions}
              onChange={(partCode) => onChange({ partCode })}
            />
          ) : (
            <p className="flex items-start gap-2 rounded-md border border-dashed border-border px-3 py-2.5 text-body text-text-secondary">
              <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-text-muted" />
              {t("noPart")}
            </p>
          )}
          {errors.partCode && (
            <p role="alert" className="text-caption text-danger">
              {errors.partCode}
            </p>
          )}
        </div>
      )}
      {notices.map((text) => (
        <p key={text} role="note" className="flex items-start gap-2 rounded-md border border-border bg-subtle px-3 py-2.5 text-body text-text-secondary">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-text-muted" />
          {text}
        </p>
      ))}
      {selection.scope === "SUBJECT" && (
        <ChoiceCardGroup
          legend={t("buildModeLegend")}
          name="buildMode"
          value={selection.buildMode ?? "STANDARD"}
          required
          disabled={disabled}
          options={buildModes}
          onChange={(buildMode) => onChange({ buildMode })}
        />
      )}
    </div>
  );
}
