"use client";

import { Badge, Card, Chip, Skeleton } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, type ReactNode } from "react";
import { indexKnowledge, knowledgeName, knowledgePath } from "@/features/content/knowledge-tree";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { RichTextView } from "@/components/rich-text";
import { useQuestion } from "../queries";
import type { QuestionDetail } from "../types";
import { QuestionBodyView } from "./question-detail-body";

/**
 * Trang con Nội dung của chi tiết câu hỏi (canvas `CauHoi*ChiTiet`): đề bài và đáp án theo dạng, lời giải, khối
 * Thông tin dạng `<dl>`. Trạng thái tải và lỗi do `QuestionDetailLayout` lo; trang này chỉ dựng khi đã có câu.
 */
export function QuestionDetailContent({ questionId }: { questionId: string }) {
  const questionQuery = useQuestion(questionId);
  if (!questionQuery.data) return <Skeleton className="h-80" />;
  return <DetailContent question={questionQuery.data} />;
}

function DetailContent({ question }: { question: QuestionDetail }) {
  const t = useTranslations("questions.detail");
  const isSet = question.type === "QUESTION_SET";
  return (
    <div className="@container">
      <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex min-w-0 flex-col gap-4">
          <DetailSection title={isSet ? t("sections.passage") : t("sections.content")}>
            <QuestionBodyView question={question} />
          </DetailSection>
          {!isSet && (
            <DetailSection title={t("sections.solution")}>
              {question.solution ? (
                <RichTextView className="text-body text-text" html={question.solution} />
              ) : (
                <p className="text-body text-text">{t("noSolution")}</p>
              )}
            </DetailSection>
          )}
        </div>
        <InfoSection question={question} />
      </div>
    </div>
  );
}

function DetailSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card padding="none" className="p-4 sm:p-5">
      <section className="flex flex-col gap-3">
        <h2 className="text-h3 font-semibold text-text">{title}</h2>
        {children}
      </section>
    </Card>
  );
}

function InfoSection({ question }: { question: QuestionDetail }) {
  const t = useTranslations("questions.detail");
  const tType = useTranslations("content.questionType");
  const format = useFormatter();
  const referenceQuery = useContentReference();
  const lookup = useReferenceLookup(referenceQuery.data);
  const index = useMemo(() => indexKnowledge(referenceQuery.data?.knowledgeTree ?? []), [referenceQuery.data]);
  const knowledge = [...question.knowledge].sort((a, b) => Number(b.primary) - Number(a.primary));

  return (
    <DetailSection title={t("sections.info")}>
      <dl className="m-0 flex flex-col gap-3 text-body">
        <InfoItem label={t("info.code")}>{question.code}</InfoItem>
        <InfoItem label={t("info.type")}>{tType(question.type)}</InfoItem>
        <InfoItem label={t("info.subject")}>{lookup.subject(question.subjectCode)}</InfoItem>
        <InfoItem label={t("info.grade")}>{lookup.grade(question.gradeCode)}</InfoItem>
        <InfoItem label={t("info.difficulty")}>{lookup.difficulty(question.difficultyCode)}</InfoItem>
        {question.competencyCode && (
          <InfoItem label={t("info.competency")}>
            {referenceQuery.data?.competencies.find((option) => option.code === question.competencyCode)?.name ??
              question.competencyCode}
          </InfoItem>
        )}
        <InfoItem label={t("info.points")}>
          {question.type === "QUESTION_SET"
            ? t("info.pointsTotal", { points: question.points, count: question.children.length })
            : format.number(question.points)}
        </InfoItem>
        <InfoItem label={t("info.knowledge")}>
          {knowledge.length === 0 ? (
            <span className="text-text-muted">{t("info.noKnowledge")}</span>
          ) : (
            <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
              {knowledge.map((ref) => {
                const entry = index.get(ref.nodeId);
                return (
                  <li key={ref.nodeId} className="flex flex-col">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <span className="font-medium">
                        {ref.code} · {knowledgeName(index, ref)}
                      </span>
                      {ref.primary && <Badge>{t("info.primary")}</Badge>}
                    </span>
                    {entry && <span className="text-caption text-text-muted">{knowledgePath(entry)}</span>}
                  </li>
                );
              })}
            </ul>
          )}
        </InfoItem>
        <InfoItem label={t("info.tags")}>
          {question.tagCodes.length === 0 ? (
            <span className="text-text-muted">{t("info.noTags")}</span>
          ) : (
            <span className="flex flex-wrap gap-1.5">
              {question.tagCodes.map((code) => (
                <Chip key={code} variant="tag" className="h-6">
                  {lookup.tag(code)}
                </Chip>
              ))}
            </span>
          )}
        </InfoItem>
        <InfoItem label={t("info.version")}>{t("info.versionValue", { version: question.version })}</InfoItem>
        {question.sourceCode && <InfoItem label={t("info.sourceCode")}>{question.sourceCode}</InfoItem>}
        <InfoItem label={t("info.createdBy")}>
          {t("info.byAt", {
            name: question.createdBy.name,
            at: format.dateTime(new Date(question.createdAt), "date"),
          })}
        </InfoItem>
        <InfoItem label={t("info.updatedAt")}>{format.dateTime(new Date(question.updatedAt), "dateTime")}</InfoItem>
      </dl>
    </DetailSection>
  );
}

function InfoItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption font-medium text-text-muted">{label}</dt>
      <dd className="m-0 wrap-break-word text-text">{children}</dd>
    </div>
  );
}
