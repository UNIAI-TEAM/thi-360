"use client";

import { Card } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { QuestionVersion } from "../editor-types";
import { diffSnapshots, SNAPSHOT_FIELDS, type QuestionVersionChange } from "../editor-versioning";
import { describeBody } from "./question-detail-body";

/**
 * Khối "So sánh vA → vB" của canvas `CauHoiPhienBan`: từng trường thay đổi với giá trị Trước / Sau, ghi chú ảnh hưởng
 * ma trận khi đổi khối lớp hoặc mức độ, và dòng "Không đổi: …" cho các trường còn lại.
 */
export function QuestionVersionsCompare({ before, after }: { before: QuestionVersion | null; after: QuestionVersion }) {
  const t = useTranslations("questions.versions.compare");
  const tBody = useTranslations("questions.detail.body");
  const tType = useTranslations("content.questionType");
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);

  if (!before) {
    return (
      <Card padding="none" className="p-4 sm:p-5">
        <section aria-labelledby="question-compare-title" className="flex flex-col gap-2">
          <h2 id="question-compare-title" className="text-h3 font-semibold text-text">
            {t("titleSingle")}
          </h2>
          <p className="text-body text-text-muted">{t("single")}</p>
        </section>
      </Card>
    );
  }

  const changes = diffSnapshots(before.snapshot, after.snapshot);
  const changed = new Set(changes.map((change) => change.field));
  const unchanged = SNAPSHOT_FIELDS.filter(
    (field) =>
      !changed.has(field) &&
      (field !== "children" || after.snapshot.children.length > 0) &&
      (field !== "competencyCode" || Boolean(after.snapshot.competencyCode)),
  );
  const affectsMatrix = changed.has("gradeCode") || changed.has("difficultyCode");

  const render = (change: QuestionVersionChange, side: "before" | "after"): ReactNode => {
    switch (change.field) {
      case "subjectCode":
        return lookup.subject(change[side]);
      case "gradeCode":
        return lookup.grade(change[side]);
      case "difficultyCode":
        return lookup.difficulty(change[side]);
      case "competencyCode": {
        const code = change[side];
        if (!code) return t("none");
        return referenceQuery.data?.competencies.find((option) => option.code === code)?.name ?? code;
      }
      case "points":
        return String(change[side]);
      case "content":
        return <span className="whitespace-pre-wrap">{change[side]}</span>;
      case "solution":
        return change[side] ? <span className="whitespace-pre-wrap">{change[side]}</span> : t("none");
      case "tagCodes":
        return change[side].length ? change[side].map(lookup.tag).join(", ") : t("none");
      case "knowledge":
        return change[side].length ? (
          <ul className="m-0 flex list-none flex-col gap-0.5 p-0">
            {change[side].map((ref) => (
              <li key={ref.nodeId}>
                {ref.code} · {ref.name}
                {ref.primary ? ` (${t("primary")})` : ""}
              </li>
            ))}
          </ul>
        ) : (
          t("none")
        );
      case "body":
        return (
          <ul className="m-0 flex list-none flex-col gap-0.5 p-0">
            {describeBody(change[side], tBody).map((line, index) => (
              <li key={index}>{line}</li>
            ))}
          </ul>
        );
      case "children":
        return change[side].length ? (
          <ol className="m-0 flex list-none flex-col gap-0.5 p-0">
            {change[side].map((child, index) => (
              <li key={child.id}>
                {t("child", { number: index + 1, type: tType(child.type), preview: child.contentPreview })}
              </li>
            ))}
          </ol>
        ) : (
          t("none")
        );
    }
  };

  return (
    <Card padding="none" className="p-4 sm:p-5">
      <section aria-labelledby="question-compare-title" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 id="question-compare-title" className="text-h3 font-semibold text-text">
            {t("title", { from: before.version, to: after.version })}
          </h2>
          <p className="text-body text-text-muted">{t("description", { version: after.version })}</p>
        </div>
        {changes.length === 0 ? (
          <p className="text-body text-text">{t("noChanges")}</p>
        ) : (
          changes.map((change) => (
            <div key={change.field} className="flex flex-col gap-2 border-t border-border pt-3">
              <h3 className="text-label font-semibold text-text">{t(`fields.${change.field}`)}</h3>
              <dl className="m-0 grid gap-2 @lg:grid-cols-2">
                <div className="flex flex-col gap-1 rounded-lg border border-border bg-subtle p-3">
                  <dt className="text-caption font-semibold text-text-muted">{t("before")}</dt>
                  <dd className="m-0 text-body wrap-break-word text-text">{render(change, "before")}</dd>
                </div>
                <div className="flex flex-col gap-1 rounded-lg border border-primary-600 bg-primary-50 p-3">
                  <dt className="text-caption font-semibold text-primary-text">{t("after")}</dt>
                  <dd className="m-0 text-body wrap-break-word text-text">{render(change, "after")}</dd>
                </div>
              </dl>
              {(change.field === "difficultyCode" || (change.field === "gradeCode" && !changed.has("difficultyCode"))) &&
                affectsMatrix && (
                  <p className="text-caption text-text-secondary">
                    {t("matrixImpact", {
                      grade: lookup.grade(after.snapshot.gradeCode),
                      difficulty: lookup.difficulty(after.snapshot.difficultyCode),
                      version: after.version,
                    })}
                  </p>
                )}
            </div>
          ))
        )}
        {unchanged.length > 0 && changes.length > 0 && (
          <p className="border-t border-border pt-3 text-caption text-text-muted">
            {t("unchanged", { fields: unchanged.map((field) => t(`fieldsShort.${field}`)).join(", ") })}
          </p>
        )}
      </section>
    </Card>
  );
}
