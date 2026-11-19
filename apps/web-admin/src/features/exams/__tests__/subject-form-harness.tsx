import { useState } from "react";
import { renderWithProviders } from "@/test/render";
import { ExamStructureView } from "../components/exam-structure-view";
import { registerFormBlockingRuleForTest } from "../subject-forms/blocking";
import { registerSubjectFormRenderer, type SubjectFormRendererProps } from "../subject-forms/form-renderers";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

/**
 * Hạ tầng cho các test `subject-form-*` trên `StructureEditor` thật (CR-1): bài dựng bằng dữ liệu wire viết tay, máy chủ giả ghi lại
 * mọi request, và chỗ cắm kiểm thử của sản phẩm (cờ `enabled`, màn MT/DS thử, luật chặn lưu thử). Sản phẩm giữ mọi cờ tắt; MT và DS
 * thật thuộc CR-2, CR-3.
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

/** Bài QDA: Lý, Hóa (MT thử), Toán (MD vì cờ tắt), Văn (DS thử). `lySingle`, `toanFirst` đổi số câu nhóm đầu của Lý, Toán. */
export function qdaParts({ lySingle = 14, toanFirst = 10 }: { lySingle?: number; toanFirst?: number } = {}): Wire[] {
  return [
    wirePart([
      wireScience("sec-ly", "LY", lySingle),
      wireScience("sec-hoa", "HOA"),
      wireSection("sec-toan", "TOAN", [wireBlock("toan-b1", { name: "Toan nhom", questionCount: toanFirst })]),
      wireSection("sec-van", "VAN", [wireBlock("van-r1", { name: "Van dong 1", questionCount: 5 }), wireBlock("van-r2", { name: "Van dong 2", questionCount: 5 })]),
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

/* ------------------------------ màn MT, DS và luật chặn lưu thử ------------------------------ */

/** Ô nhập số của một nhóm: số nguyên ghi vào bản nháp, chữ khác ghi vào kho nhập thô (chưa ghi được vào bản nháp). */
function useBlockNumber(props: SubjectFormRendererProps, blockId: string, field: string) {
  const { context, onChange, rawInputs } = props;
  const block = context.section.blocks.find((item) => item.id === blockId)!;
  const [text, setText] = useState(String(block.questionCount ?? 0));
  const change = (next: string) => {
    setText(next);
    if (/^\d+$/.test(next)) {
      rawInputs.clear(context.section.id, blockId, field);
      onChange((section) => ({ ...section, blocks: section.blocks.map((item) => (item.id === blockId ? { ...item, questionCount: Number(next) } : item)) }));
    } else {
      rawInputs.set({ sectionId: context.section.id, blockId, field, text: next, code: "INVALID_NUMBER" });
    }
  };
  return { text, change };
}

function MatrixInput(props: SubjectFormRendererProps) {
  const first = props.context.section.blocks[0]!;
  const { text, change } = useBlockNumber(props, first.id, "STRUCTURE");
  return <input data-form-field="STRUCTURE" aria-label={`MT ${props.context.section.subjectCode} cau don`} value={text} onChange={(event) => change(event.target.value)} />;
}

/** Màn MT thử: một ô `data-form-field="STRUCTURE"` cho nhóm đầu. */
export function TestMatrixForm(props: SubjectFormRendererProps) {
  return (
    <section aria-label={`MT ${props.context.section.subjectCode}`}>
      <h2>{`MT ${props.context.section.subjectCode}`}</h2>
      <MatrixInput {...props} />
      {props.headerAction}
    </section>
  );
}

function RowEditor(props: SubjectFormRendererProps & { blockId: string; name: string }) {
  const { text, change } = useBlockNumber(props, props.blockId, "count");
  const [open, setOpen] = useState(false);
  return (
    <div data-form-row={props.blockId}>
      <span>{props.name}</span>
      <button type="button" onClick={() => setOpen(!open)}>
        {open ? `Dong ${props.name}` : `Sua ${props.name}`}
      </button>
      {open && <input aria-label={`DS ${props.name} so luong`} value={text} onChange={(event) => change(event.target.value)} />}
    </div>
  );
}

/** Màn DS thử: mỗi nhóm một dòng `data-form-row`; ô số lượng chỉ có khi mở dòng, gõ chữ thì ghi nhập thô. Đóng dòng bỏ ô nhưng không bỏ nhập thô. */
export function TestRowsForm(props: SubjectFormRendererProps) {
  return (
    <section aria-label={`DS ${props.context.section.subjectCode}`}>
      <h2>{`DS ${props.context.section.subjectCode}`}</h2>
      {props.context.section.blocks.map((block) => (
        <RowEditor key={block.id} {...props} blockId={block.id} name={block.name} />
      ))}
      {props.headerAction}
    </section>
  );
}

/** Luật chặn lưu thử: nhóm đầu của môn MT đúng 13 câu là lỗi (giới hạn R-12 thật thuộc CR-2). */
const single13Rule = (section: { id: string; blocks: Array<{ id: string; questionCount: number | null }> }) => {
  const first = section.blocks[0];
  return first?.questionCount === 13 ? [{ code: "SINGLE_13", sectionId: section.id, blockId: first.id, field: "STRUCTURE" }] : [];
};

/**
 * Bật MT cho QDA Lý, Hóa và DS cho QDA Văn, đăng ký màn và luật thử. `renderers: false` chỉ bật cờ (dạng bật mà chưa có màn vẽ).
 * Trả hàm gỡ tất cả (dùng trong `afterEach`).
 */
export function installForms({ renderers = true }: { renderers?: boolean } = {}): () => void {
  const restores = [
    patchSubjectFormEntry("QDA", "LY", { enabled: true }),
    patchSubjectFormEntry("QDA", "HOA", { enabled: true }),
    patchSubjectFormEntry("QDA", "VAN", { enabled: true }),
    ...(renderers ? [registerSubjectFormRenderer("MATRIX", TestMatrixForm), registerSubjectFormRenderer("ROWS", TestRowsForm)] : []),
    registerFormBlockingRuleForTest((section, context) => (context.form === "MATRIX" ? single13Rule(section) : [])),
  ];
  return () => {
    while (restores.length) restores.pop()!();
  };
}

/** Các môn trong thân `PUT` theo `id`. */
export function sectionsOf(body: unknown): Record<string, Wire> {
  const parts = (body as { parts: Array<{ sections?: Wire[] }> }).parts;
  return Object.fromEntries(parts.flatMap((part) => part.sections ?? []).map((section) => [section.id as string, section]));
}
