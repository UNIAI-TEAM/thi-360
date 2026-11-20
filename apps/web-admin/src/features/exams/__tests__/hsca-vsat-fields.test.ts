import type { components } from "@thi360/api-client";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { updateExamStructure } from "../api";
import { mapExamTemplate, mapParts, toApiParts } from "../api-mapping";
import { partsFromInput } from "../create-parts";
import { initialParts, partInputsFromValues } from "../create-exam-schema";
import type { ExamPart } from "../types";
import { EXAMS_FIXTURE } from "./exam-fixture";
import { toWireTemplate } from "./fake-exams-backend";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

/**
 * R-146: web admin đọc, giữ và gửi lại nguyên vẹn các trường mới của cấu trúc H-SCA, V-SAT (`audioRequirement`,
 * `displaySegments`, `ChildCondition.answerType` và `itemShape`, `ItemShape.options`) ở ba đường: tải rồi lưu bước 2,
 * dữ liệu của bảy kỳ thi cũ (không sinh thêm trường), tạo bài từ mẫu có sửa bảng tổng quan.
 */

vi.mock("@/features/content/api", () => ({ getContentReference: async () => ({ combinations: [] }) }));

type ApiPart = components["schemas"]["ExamPart"];
type ApiTemplate = components["schemas"]["ExamTemplateResponse"];

/**
 * Đường dẫn của mọi trường mới của hợp đồng H-SCA, V-SAT còn có trong `value`: `audioRequirement` và `displaySegments` ở
 * bất kỳ đâu, `answerType` trong điều kiện câu con, `options` trong hình dạng câu. (`options` của phần là danh sách lựa chọn
 * cũ, không tính.)
 */
function newFieldPaths(value: unknown, path: string[] = []): string[] {
  if (Array.isArray(value)) return value.flatMap((item, index) => newFieldPaths(item, [...path, String(index)]));
  if (value === null || typeof value !== "object") return [];
  return Object.entries(value).flatMap(([key, item]) => {
    const here = [...path, key];
    const parent = path.at(-1);
    const grandparent = path.at(-2);
    const isNew =
      key === "audioRequirement" ||
      key === "displaySegments" ||
      (key === "answerType" && grandparent === "childConditions") ||
      (key === "options" && parent === "itemShape");
    return [...(isNew ? [here.join(".")] : []), ...newFieldPaths(item, here)];
  });
}

/** Nhóm chùm trộn dạng của Tiếng Anh V-SAT (câu 8–15): vị trí 1–2 Đúng/Sai bốn ý, 3–8 chọn một bốn phương án. */
const MIXED_SET_BLOCK = {
  id: "blk-reading",
  name: "Bài đọc dùng chung – câu 8–15",
  questionType: "QUESTION_SET",
  questionCount: 8,
  setCount: 1,
  setSize: 8,
  requiredPointsPerQuestion: 6,
  childConditions: [
    { from: 1, to: 2, nodeIds: [], answerType: "TRUE_FALSE", itemShape: { statements: 4 } },
    { from: 3, to: 8, nodeIds: [], answerType: "SINGLE_CHOICE", itemShape: { options: 4 } },
  ],
};

const SINGLE_BLOCK = {
  id: "blk-single",
  name: "Chọn một",
  questionType: "SINGLE_CHOICE",
  questionCount: 8,
  requiredPointsPerQuestion: 1,
  itemShape: { options: 4 },
};

const DISPLAY_SEGMENTS = [
  { code: "I", name: "Phần I", fromPosition: 1, toPosition: 9, commandCount: 36, maxPoints: 54 },
  { code: "II", name: "Phần II", fromPosition: 10, toPosition: 15, commandCount: 6, maxPoints: 36 },
];

/** Phần của H-SCA/V-SAT như máy chủ trả: điều kiện audio ở môn, phân đoạn ở phần, điều kiện câu con ở nhóm. */
const NEW_PART = {
  id: "part-1",
  order: 1,
  name: "Nghe và đọc",
  durationMinutes: 60,
  questionCount: 16,
  questionCountOnPaper: 16,
  questionCountRequired: 16,
  participation: { mode: "ALL" },
  displaySegments: DISPLAY_SEGMENTS,
  sections: [
    {
      id: "sec-1",
      subjectCode: "ANH",
      questionCount: 16,
      audioRequirement: "REQUIRED",
      blocks: [SINGLE_BLOCK, MIXED_SET_BLOCK],
    },
  ],
} as unknown as ApiPart;

const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
let server: FakeServer;

beforeEach(() => {
  server = new FakeServer().install();
});

describe("lưu bước 2 giữ trường mới (R-146, FE-6 dòng 7)", () => {
  it("nạp cấu trúc, sửa tên một nhóm, lưu: thân yêu cầu còn nguyên bốn trường và đọc lại không đổi", async () => {
    server.on("PUT", STRUCTURE_PATH, { status: 200, body: examDetailWire({ revision: 8 }) });
    const loaded = mapParts([NEW_PART]);
    const edited: ExamPart[] = loaded.map((part) => ({
      ...part,
      sections: part.sections.map((section) => ({
        ...section,
        blocks: section.blocks.map((block) => (block.id === "blk-single" ? { ...block, name: "Chọn một (đã đổi tên)" } : block)),
      })),
    }));

    await updateExamStructure(EXAM_ID, { parts: edited, expectedRevision: 7 });

    const [request] = server.calls("PUT", STRUCTURE_PATH);
    // Thân yêu cầu do chính `updateExamStructure` dựng theo `UpdateExamStructureRequest` của máy chủ.
    const body = request!.body as { parts: ApiPart[] };
    const [sent] = body.parts;
    expect(sent!.displaySegments).toEqual(DISPLAY_SEGMENTS);
    const [section] = sent!.sections!;
    expect(section!.audioRequirement).toBe("REQUIRED");
    const [single, mixed] = section!.blocks!;
    expect(single).toMatchObject({ name: "Chọn một (đã đổi tên)", itemShape: { options: 4 } });
    expect(mixed!.childConditions).toEqual(MIXED_SET_BLOCK.childConditions);

    // Đọc lại thân đã gửi cho ra đúng cấu trúc đã sửa, không mất, không thêm trường nào.
    expect(mapParts(body.parts)).toEqual(edited);
  });

  it("điều kiện câu con chỉ có dạng (không nodeIds) vẫn giữ dạng và hình dạng", () => {
    const wire = {
      ...NEW_PART,
      sections: [
        {
          ...NEW_PART.sections![0]!,
          blocks: [
            {
              ...MIXED_SET_BLOCK,
              childConditions: [{ from: 1, to: 8, answerType: "SINGLE_CHOICE", itemShape: { options: 4 } }],
            },
          ],
        },
      ],
    } as unknown as ApiPart;
    const [sent] = toApiParts(mapParts([wire]));
    expect(sent!.sections![0]!.blocks![0]!.childConditions).toEqual([
      { from: 1, to: 8, nodeIds: [], answerType: "SINGLE_CHOICE", itemShape: { options: 4 } },
    ]);
  });
});

describe("bảy kỳ thi cũ không sinh thêm trường (FE-6 dòng 8)", () => {
  const OLD = EXAMS_FIXTURE.exams.filter((exam) => exam.parts.length > 0);

  it("dữ liệu mẫu có đủ bài của các kỳ thi cũ", () => {
    expect(new Set(OLD.map((exam) => exam.kind)).size).toBeGreaterThanOrEqual(5);
  });

  it.each(OLD.map((exam) => [exam.code, exam] as const))("%s: cấu trúc qua api-mapping không sinh trường mới và ổn định sau hai lượt", (_code, exam) => {
    const sent = toApiParts(mapParts(toApiParts(exam.parts)));
    expect(newFieldPaths(sent)).toEqual([]);
    expect(toApiParts(mapParts(sent))).toEqual(sent);
  });

  it("mẫu của các kỳ thi cũ qua mapExamTemplate không có điều kiện audio hay phân đoạn", () => {
    for (const template of EXAMS_FIXTURE.templates) {
      const mapped = mapExamTemplate(toWireTemplate(template) as unknown as ApiTemplate);
      expect(newFieldPaths(mapped.parts), template.code).toEqual([]);
    }
  });
});

describe("tạo bài từ mẫu có sửa bảng tổng quan giữ trường mới (create-parts, FE-6 dòng 9)", () => {
  const WIRE_TEMPLATE = {
    active: true,
    publishable: true,
    fixedCounts: false,
    fixedBlockCounts: true,
    omittedLocked: false,
    buildMode: "STANDARD",
    isDefault: true,
    code: "VSAT_ANH",
    kindCode: "VSAT",
    scope: "SUBJECT",
    subjectCode: "ANH",
    timing: "SHARED",
    durationMinutes: 60,
    structure: { parts: [NEW_PART] },
    parts: [],
  } as unknown as ApiTemplate;

  it("mẫu mang cờ khóa dòng, điều kiện audio, phân đoạn và điều kiện câu con", () => {
    const template = mapExamTemplate(WIRE_TEMPLATE);
    expect(template.fixedBlockCounts).toBe(true);
    expect(template.omittedLocked).toBe(false);
    const part = template.parts[0]!;
    expect(part.displaySegments).toEqual(DISPLAY_SEGMENTS);
    expect(part.sections![0]!.audioRequirement).toBe("REQUIRED");
    expect(part.sections![0]!.blocks![1]!.childConditions).toEqual(MIXED_SET_BLOCK.childConditions);
    expect(part.sections![0]!.blocks![0]!.itemShape).toEqual({ options: 4 });
  });

  it("sửa số câu của phần và môn ở bảng rồi dựng cấu trúc: còn audio, phân đoạn, điều kiện câu con, số phương án", () => {
    const template = mapExamTemplate(WIRE_TEMPLATE);
    const values = initialParts({
      templates: [template],
      kind: "VSAT",
      scope: "SUBJECT",
      partCode: "",
      combinations: [],
      scopeName: (code) => code,
      template,
      subjectCode: "ANH",
    });
    const edited = values.map((part) => ({
      ...part,
      questionCount: 14,
      sections: part.sections.map((section) => ({ ...section, questionCount: 14 })),
    }));

    const inputs = partInputsFromValues(edited, { timing: "SHARED", pointsByPart: false });
    const [built] = toApiParts(partsFromInput(inputs, [], "ANH"));

    expect(built!.questionCount).toBe(14);
    expect(built!.displaySegments).toEqual(DISPLAY_SEGMENTS);
    const [section] = built!.sections!;
    expect(section!.questionCount).toBe(14);
    expect(section!.audioRequirement).toBe("REQUIRED");
    const [single, mixed] = section!.blocks!;
    expect(single!.itemShape).toEqual({ options: 4 });
    expect(mixed!.childConditions).toEqual(MIXED_SET_BLOCK.childConditions);
  });

  it("mẫu không có trường mới thì cấu trúc dựng ra cũng không có", () => {
    const plain = {
      ...WIRE_TEMPLATE,
      structure: {
        parts: [
          {
            id: "p1",
            order: 1,
            name: "Phần 1",
            durationMinutes: 60,
            questionCount: 10,
            participation: { mode: "ALL" },
            sections: [{ id: "s1", subjectCode: "MATH", questionCount: 10, blocks: [{ name: "Chọn một", questionType: "SINGLE_CHOICE", questionCount: 10 }] }],
          },
        ],
      },
    } as unknown as ApiTemplate;
    const template = mapExamTemplate(plain);
    const values = initialParts({
      templates: [template],
      kind: "VSAT",
      scope: "SUBJECT",
      partCode: "",
      combinations: [],
      scopeName: (code) => code,
      template,
      subjectCode: "MATH",
    });
    const built = toApiParts(partsFromInput(partInputsFromValues(values, { timing: "PER_PART", pointsByPart: false }), [], "MATH"));
    expect(newFieldPaths(built)).toEqual([]);
  });
});
