import type { components } from "@thi360/api-client";
import { loadIdToCode, type CatalogGroupCode } from "@/features/content/catalog-lookup";
import { indexKnowledge } from "@/features/content/knowledge-tree";
import type { KnowledgeNode, KnowledgeRef, PersonRef, QuestionStatus } from "@/features/content/types";
import { joinCommonMistakes } from "./api-mapping";
import type {
  QuestionSnapshot,
  QuestionVersion,
  QuestionVersionList,
  QuestionVersionState,
  QuestionUsage,
} from "./editor-types";
import type { QuestionDetail } from "./types";
import { asWireBody, fromWireBody, type WireChild } from "./wire-body";

/**
 * Lịch sử phiên bản. Bản chụp của máy chủ lưu id giá trị danh mục (môn, khối, độ khó, dạng bài, năng lực, nhãn) và id
 * nút kiến thức; web so sánh và hiển thị theo mã, nên đổi ngược qua `GET /catalog/options` và cây kiến thức.
 */

type ApiVersionItem = components["schemas"]["QuestionVersionItem"];
type ApiSnapshot = components["schemas"]["QuestionSnapshot"];

const NO_PERSON: PersonRef = { id: "", name: "" };
const STATES: readonly QuestionVersionState[] = ["WORKING", "CURRENT", "SUPERSEDED", "RETIRED"];

/** Tra mã theo id của từng nhóm danh mục. */
export interface SnapshotLookups {
  codeOf: (group: CatalogGroupCode, id: string | undefined) => string | null;
  knowledgeOf: (itemId: string, primary: boolean) => KnowledgeRef;
}

export async function loadSnapshotLookups(knowledgeTree: readonly KnowledgeNode[]): Promise<SnapshotLookups> {
  const groups: CatalogGroupCode[] = ["SUBJECT", "GRADE", "QUESTION_LEVEL", "TASK_TYPE", "COMPETENCY", "QUESTION_TAG"];
  const maps = new Map(await Promise.all(groups.map(async (group) => [group, await loadIdToCode(group)] as const)));
  const tree = indexKnowledge([...knowledgeTree]);
  return {
    codeOf: (group, id) => (id ? (maps.get(group)?.get(id) ?? null) : null),
    knowledgeOf: (itemId, primary) => {
      const node = tree.get(itemId)?.node;
      return { nodeId: itemId, code: node?.code ?? "", name: node?.name ?? "", primary };
    },
  };
}

/** Câu con trong bản chụp của bộ: chỉ có phần ghi đè, phần còn lại kế thừa của bộ. */
function snapshotChild(
  child: WireChild,
  parent: Pick<QuestionDetail, "subjectCode" | "gradeCode" | "difficultyCode" | "knowledge" | "status">,
  lookups: SnapshotLookups,
): QuestionDetail {
  const override = child.knowledgeItemIds;
  return {
    id: child.id ?? "",
    code: "",
    type: child.type,
    status: parent.status,
    contentPreview: "",
    subjectCode: parent.subjectCode,
    gradeCode: lookups.codeOf("GRADE", child.gradeItemId ?? undefined) ?? parent.gradeCode,
    difficultyCode: lookups.codeOf("QUESTION_LEVEL", child.levelItemId ?? undefined) ?? parent.difficultyCode,
    points: child.points ?? 1,
    knowledge: override ? override.map((id, index) => lookups.knowledgeOf(id, index === 0)) : parent.knowledge,
    competencyCode: null,
    taskTypeCode: null,
    tagCodes: [],
    hasSolution: Boolean(child.solution),
    version: 1,
    revision: 0,
    usedInExamCount: 0,
    sourceCode: null,
    createdBy: NO_PERSON,
    createdAt: "",
    updatedAt: "",
    content: child.content ?? "",
    body: child.body ? fromWireBody(child.body) : { type: child.type, options: [], shuffleOptions: false },
    solution: child.solution ?? null,
    commonMistakes: null,
    attachmentIds: [],
    children: [],
  } as QuestionDetail;
}

export function fromApiSnapshot(snapshot: ApiSnapshot, status: QuestionStatus, lookups: SnapshotLookups): QuestionSnapshot {
  const wire = snapshot.body ? asWireBody(snapshot.body) : null;
  const subjectCode = lookups.codeOf("SUBJECT", snapshot.subjectItemId) ?? "";
  const gradeCode = lookups.codeOf("GRADE", snapshot.gradeItemId) ?? "";
  const difficultyCode = lookups.codeOf("QUESTION_LEVEL", snapshot.levelItemId) ?? "";
  const knowledge = (snapshot.knowledge ?? []).flatMap((ref) =>
    "itemId" in ref ? [lookups.knowledgeOf(String(ref.itemId), ref.primary)] : [lookups.knowledgeOf(ref.nodeId, ref.primary)],
  );
  const wireChildren = wire?.type === "QUESTION_SET" ? (wire.children ?? []) : [];
  const parent = { subjectCode, gradeCode, difficultyCode, knowledge, status };
  const children = wireChildren.map((child) => snapshotChild(child, parent, lookups));
  return {
    subjectCode,
    gradeCode,
    difficultyCode,
    competencyCode: lookups.codeOf("COMPETENCY", snapshot.competencyItemId),
    taskTypeCode: lookups.codeOf("TASK_TYPE", snapshot.taskTypeItemId),
    points: snapshot.points ?? 1,
    knowledge,
    tagCodes: (snapshot.tagItemIds ?? []).flatMap((id) => lookups.codeOf("QUESTION_TAG", id) ?? []),
    content: snapshot.content ?? "",
    body: wire
      ? fromWireBody(wire, children.map((child) => child.id))
      : { type: "SINGLE_CHOICE", options: [], shuffleOptions: false },
    solution: snapshot.solution ?? null,
    commonMistakes: joinCommonMistakes(snapshot.commonMistakes ?? []),
    attachmentIds: snapshot.attachmentIds ?? [],
    children,
  };
}

/**
 * Danh sách phiên bản kèm trạng thái và số bài thi của câu (hai nơi khác của API). `attemptCount` của từng phiên bản là
 * số bài thi đã có dòng sổ `ATTEMPT` cho phiên bản đó (chưa có số lượt thi thật tới đợt lượt thi).
 */
export function fromApiVersions(
  questionId: string,
  versions: readonly ApiVersionItem[],
  context: { status: QuestionStatus; usage: QuestionUsage },
  lookups: SnapshotLookups,
): QuestionVersionList {
  const attemptsOf = (versionNo: number) =>
    context.usage.exams.reduce(
      (total, exam) => total + exam.attemptsByVersion.filter((entry) => entry.version === versionNo).length,
      0,
    );
  const items: QuestionVersion[] = versions.map((item) => ({
    version: item.versionNo,
    state: (STATES as readonly string[]).includes(item.state) ? (item.state as QuestionVersionState) : "SUPERSEDED",
    savedAt: item.savedAt,
    savedBy: item.savedBy ?? NO_PERSON,
    attemptCount: attemptsOf(item.versionNo),
    snapshot: fromApiSnapshot(item.snapshot, context.status, lookups),
  }));
  return {
    questionId,
    latestVersion: Math.max(0, ...items.map((item) => item.version)),
    status: context.status,
    examCount: context.usage.examCount,
    attemptCount: context.usage.attemptCount,
    versions: items,
  };
}
