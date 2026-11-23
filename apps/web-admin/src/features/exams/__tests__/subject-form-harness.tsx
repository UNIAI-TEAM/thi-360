import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

/**
 * Hạ tầng cho các test `subject-form-*` trên `StructureEditor` thật: bài dựng bằng dữ liệu wire viết tay và máy chủ giả ghi lại
 * mọi request.
 */
export const STRUCTURE = `${EXAM_PATH}/structure`;
export const CHECK = `${EXAM_PATH}/check`;
export const TEMPLATES = "/api/v1/admin/exam-templates";

export type Wire = Record<string, unknown>;

export const wireBlock = (id: string, over: Wire = {}): Wire => ({ id, name: id, questionType: "SINGLE_CHOICE", questionCount: 10, requiredPointsPerQuestion: 1, ...over });

export const wireSection = (id: string, subjectCode: string, blocks: Wire[], over: Wire = {}): Wire => ({
  id,
  subjectCode,
  questionCount: blocks.reduce((total, block) => total + ((block.questionCount as number | null) ?? 0), 0),
  blocks,
  ...over,
});

/** Phần như máy chủ trả: luôn có `questionCountOnPaper`, `questionCountRequired` và `options` (rỗng khi không có phương án). */
export const wirePart = (sections: Wire[], over: Wire = {}): Wire => {
  const total = sections.reduce((sum, section) => sum + ((section.questionCount as number | null) ?? 0), 0);
  return {
    id: "part-1",
    order: 1,
    name: "Part 1",
    durationMinutes: 60,
    questionCount: total,
    questionCountOnPaper: total,
    questionCountRequired: total,
    participation: { mode: "ALL" },
    options: [],
    sections,
    ...over,
  };
};

/** Môn Khoa học đúng như mẫu QDA: 14 câu đơn, 1 chùm × 3, 1 câu điền; `single` đổi số câu nhóm đơn. */
export const wireScience = (id: string, subjectCode: string, single = 14): Wire =>
  wireSection(id, subjectCode, [
    wireBlock(`${id}-single`, { name: `${id} don`, questionCount: single }),
    wireBlock(`${id}-set`, { name: `${id} chum`, questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3 }),
    wireBlock(`${id}-fill`, { name: `${id} dien`, questionType: "SHORT_ANSWER", questionCount: 1 }),
  ]);

/** Bài QDA: Lý, Hóa (MT), Toán (MD vì có tag riêng ở nhóm đơn), Văn (DS). */
export function qdaParts({ lySingle = 14, toanFirst = 10, vanR2 = 5 }: { lySingle?: number; toanFirst?: number; vanR2?: number } = {}): Wire[] {
  return [
    wirePart([
      wireScience("sec-ly", "LY", lySingle),
      wireScience("sec-hoa", "HOA"),
      wireSection("sec-toan", "TOAN", [wireBlock("toan-b1", { name: "Toan nhom", questionCount: toanFirst, tagCodes: ["MD"] })]),
      wireSection("sec-van", "VAN", [wireBlock("van-r1", { name: "Van dong 1", questionCount: 5 }), wireBlock("van-r2", { name: "Van dong 2", questionCount: vanR2 })]),
    ]),
  ];
}

export interface ServerState {
  parts: Wire[];
  revision: number;
  examQuotas: unknown[];
}

export interface BootOptions {
  kind?: string;
  parts?: Wire[];
  templates?: unknown[];
  examOverrides?: Wire;
  checkBody?: Wire;
}

const validCheck = {
  examId: EXAM_ID,
  status: "VALID",
  stale: false,
  checkedAt: "2026-11-17T01:00:00Z",
  durationSeconds: 3,
  timeLimitSeconds: 30,
  issueCount: 0,
  partCount: 1,
  totalPoints: 0,
  printedQuestions: 10,
  paths: [],
  groups: [],
};

/** Máy chủ giả có trạng thái: GET trả bản đã lưu gần nhất, PUT ghi lại thân và tăng `revision`. */
export function boot({ kind = "QDA", parts = qdaParts(), templates = [], examOverrides = {}, checkBody = validCheck }: BootOptions = {}) {
  const state: ServerState = { parts, revision: 7, examQuotas: [] };
  const server = new FakeServer()
    .on("GET", EXAM_PATH, () => ({ status: 200, body: examDetailWire({ kind, revision: state.revision, ...examOverrides } as never) }))
    .on("GET", TEMPLATES, { status: 200, body: templates })
    .on("GET", STRUCTURE, () => ({ status: 200, body: { examId: EXAM_ID, revision: state.revision, parts: state.parts, examQuotas: state.examQuotas } }))
    .on("PUT", STRUCTURE, (request) => {
      const body = request.body as { parts: Wire[]; examQuotas?: unknown[] };
      state.parts = body.parts;
      state.examQuotas = body.examQuotas ?? [];
      state.revision += 1;
      return { status: 200, body: examDetailWire({ kind, revision: state.revision } as never) };
    })
    .on("POST", CHECK, { status: 200, body: checkBody })
    .install();
  return { server, state };
}

export function openStructure(search = "") {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={EXAM_ID} />);
}

export const putCalls = (server: FakeServer) => server.calls("PUT", STRUCTURE);
export const checkCalls = (server: FakeServer) => server.calls("POST", CHECK);
/** Request gửi tới máy chủ, theo `METHOD đường-dẫn`, để so hai lần mở (chọn biểu mẫu không gọi request). */
export const requestLog = (server: FakeServer) => server.requests.map((request) => `${request.method} ${request.url.pathname}`);

/** Các môn trong thân `PUT` theo `id`. */
export function sectionsOf(body: unknown): Record<string, Wire> {
  const parts = (body as { parts: Array<{ sections?: Wire[] }> }).parts;
  return Object.fromEntries(parts.flatMap((part) => part.sections ?? []).map((section) => [section.id as string, section]));
}

/**
 * Cặp thiếu trường / `null` mà `api-mapping.ts` đã chuẩn hóa như vậy hôm nay (plan B4): wire có thể bỏ trường, màn luôn điền
 * `tagCodes: []`, `answerTypeLocked: false` rồi gửi lại; ma trận của wire mẫu (fixture B8) bỏ `cells`, bản đã nạp luôn có `cells: []`
 * (CR-2). Gọi tên ở đây để so hai wire không loại trừ ngầm điều gì khác.
 */
export const MAPPING_DEFAULTED_FIELDS: Readonly<Record<string, unknown>> = { tagCodes: [], answerTypeLocked: false, cells: [] };

/** Bỏ `null`, `undefined` và các trường mặc định có tên ở `MAPPING_DEFAULTED_FIELDS` để so hai wire theo nghĩa. */
export function withoutMappingDefaults(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutMappingDefaults);
  if (value === null || typeof value !== "object") return value;
  const entries = Object.entries(value as Wire)
    .filter(([key, item]) => item !== null && item !== undefined && !(key in MAPPING_DEFAULTED_FIELDS && JSON.stringify(item) === JSON.stringify(MAPPING_DEFAULTED_FIELDS[key])))
    .map(([key, item]) => [key, withoutMappingDefaults(item)] as const);
  return Object.fromEntries(entries);
}
