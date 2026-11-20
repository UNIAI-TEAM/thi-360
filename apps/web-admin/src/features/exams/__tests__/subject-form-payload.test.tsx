import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getExamStructure, updateExamStructure } from "../api.http";
import { selectAllSubjectForms } from "../subject-forms/selection";
import { EXAM_ID } from "./fake-exams-server";
import { sectionGroups } from "../components/exam-structure-draft";
import { patchSubjectFormEntry, subjectFormFor } from "../subject-forms/registry";
import { block, part, scienceSection, standardExam, standardFixtureTemplates } from "./subject-form-builders";
import { bcaLiteratureSection, hsaLiteratureSection, oneSection, qdaLiteratureSection, thptqgLiteratureSection, thptqgPhysicsParts } from "./subject-row-builders";
import standardTemplates from "./fixtures/exam-templates-dot3.json";
import {
  boot,
  installForms,
  openStructure,
  putCalls,
  requestLog,
  sectionsOf,
  STRUCTURE,
  TEMPLATES,
  type Wire,
  wireBlock,
  wirePart,
  wireScience,
  wireSection,
  withoutMappingDefaults,
} from "./subject-form-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-1 AC-6, AC-10 (FTM AC 6, AC 55; plan B4): thân `PUT /structure` bằng bản đã nạp, chặn ở `api.http.ts`, và chọn biểu mẫu không gọi
 * request. Loại trừ khi so wire, gọi tên: `null`/thiếu trường và hai mặc định `tagCodes: []`, `answerTypeLocked: false` mà
 * `api-mapping.ts` điền (`MAPPING_DEFAULTED_FIELDS`); trường `mode` của phần do `toApiParts` bỏ (suy ra từ `participation`).
 */
let restore: () => void;
beforeEach(() => {
  routerMock.push.mockReset();
  restore = installForms();
});
afterEach(() => restore());

/** Khác biệt hai wire theo đường dẫn, sau khi bỏ các điểm loại trừ có tên. */
function diffWire(before: unknown, after: unknown, path = "$"): string[] {
  const a = withoutMappingDefaults(before);
  const b = withoutMappingDefaults(after);
  if (JSON.stringify(a) === JSON.stringify(b)) return [];
  if (Array.isArray(a) && Array.isArray(b) && a.length === b.length) return a.flatMap((item, index) => diffWire(item, b[index], `${path}[${index}]`));
  if (a && b && typeof a === "object" && typeof b === "object" && !Array.isArray(a) && !Array.isArray(b)) {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].flatMap((key) => diffWire((a as Wire)[key], (b as Wire)[key], `${path}.${key}`));
  }
  return [`${path}: ${JSON.stringify(a)} -> ${JSON.stringify(b)}`];
}

const matrix = (enforcement: "REFERENCE" | "REQUIRED") => ({
  mode: "SEPARATE",
  unit: "COUNT",
  basis: "QUESTION",
  cells: [],
  byGrade: [{ code: "GRADE_12", value: 20 }],
  byDifficulty: [{ code: "RECOGNITION", value: 20 }],
  gradeEnforcement: enforcement,
  difficultyEnforcement: enforcement,
});

/** HSA FULL có đủ trường B4 gọi tên: `options`, `choiceGroup`, `audioRequirement`, `durationMinutes`, `displaySegments`, `subjectPool`. */
function hsaFull(): Wire[] {
  return [
    wirePart(
      [
        wireSection("sec-toan", "TOAN", [wireBlock("b-single", { questionType: null, questionCount: 30, tagCodes: ["TOAN_X"] }), wireBlock("b-set", { questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, shuffleUnits: false, slots: [{ index: 1, tagCodes: ["S1"] }], childConditions: [{ from: 1, to: 2, nodeIds: ["n1"] }] })], {
          matrix: matrix("REFERENCE"),
          answerQuotas: { unit: "COUNT", items: [{ answerType: "SINGLE_CHOICE", value: 30 }, { answerType: "QUESTION_SET", value: 3 }] },
          topicQuotas: [{ id: "tq1", name: "Ham so", nodeIds: ["n1"], count: 5, enforcement: "REFERENCE", basis: "QUESTION" }],
          knowledgeScope: { nodeIds: ["n1", "n2"] },
        }),
      ],
      { id: "part-1", name: "Toan", durationMinutes: 75, displaySegments: [{ code: "S1", name: "Phan 1", fromPosition: 1, toPosition: 33, commandCount: 33, maxPoints: 33 }] },
    ),
    wirePart([], {
      id: "part-2",
      order: 2,
      name: "Khoa hoc",
      durationMinutes: 60,
      questionCount: 50,
      questionCountOnPaper: 50,
      questionCountRequired: 50,
      participation: { mode: "CHOICE", choose: 1 },
      options: [
        {
          id: "opt-sci",
          kind: "SUBJECT_CHOICE",
          code: "SCIENCE",
          chooseSubjects: 3,
          countsByPick: [17, 17, 16],
          sections: [
            wireSection("sec-ly", "LY", [wireBlock("ly-b", { questionCount: 17 })], { questionCount: null, choiceGroup: "NATURAL", matrix: matrix("REFERENCE") }),
            wireSection("sec-su", "SU", [wireBlock("su-b", { questionCount: 17 })], { questionCount: null, choiceGroup: "SOCIAL" }),
          ],
        },
      ],
    }),
    wirePart([wireSection("sec-listen", "ANH", [wireBlock("l-b", { questionCount: 10 })], { audioRequirement: "REQUIRED" }), wireSection("sec-read", "ANH", [wireBlock("r-b", { questionCount: 10 })], { audioRequirement: "FORBIDDEN" })], { id: "part-3", order: 3, name: "Anh", durationMinutes: 45 }),
  ];
}

describe("Thân PUT không đổi (FTM AC 6, plan B4)", () => {
  it("defaultFormSaveIsIdentity: HSA FULL mở rồi Lưu không sửa gì: thân PUT bằng bản đã nạp, chỉ khác ở chuẩn hóa HSA-C có tên", async () => {
    const user = userEvent.setup();
    const original = hsaFull();
    const { server, state } = boot({ kind: "HSA", parts: original });
    openStructure("?part=part-1");
    await user.click(await screen.findByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const body = putCalls(server)[0]!.body as { parts: Wire[]; examQuotas: unknown[]; expectedRevision: number };
    expect(body.expectedRevision).toBe(7);
    expect(body.examQuotas).toEqual([]);
    // Chuẩn hóa HSA-C (đã có từ trước, FTM mục 2): Tham khảo có số sang Bắt buộc, ở ma trận môn và chỉ tiêu chủ đề; không thêm gì khác.
    expect(diffWire(original, body.parts).toSorted()).toEqual(
      [
        "$[0].sections[0].matrix.difficultyEnforcement: \"REFERENCE\" -> \"REQUIRED\"",
        "$[0].sections[0].matrix.gradeEnforcement: \"REFERENCE\" -> \"REQUIRED\"",
        "$[0].sections[0].topicQuotas[0].enforcement: \"REFERENCE\" -> \"REQUIRED\"",
        "$[1].options[0].sections[0].matrix.difficultyEnforcement: \"REFERENCE\" -> \"REQUIRED\"",
        "$[1].options[0].sections[0].matrix.gradeEnforcement: \"REFERENCE\" -> \"REQUIRED\"",
      ].toSorted(),
    );
    // Các trường B4 gọi tên có mặt trong thân gửi lên.
    expect(sectionsOf(body)["sec-listen"]).toMatchObject({ audioRequirement: "REQUIRED" });
    expect(sectionsOf(body)["sec-read"]).toMatchObject({ audioRequirement: "FORBIDDEN" });
    expect(body.parts[0]).toMatchObject({ durationMinutes: 75, displaySegments: [{ code: "S1", fromPosition: 1, toPosition: 33 }] });
    const option = (body.parts[1]!.options as Wire[])[0]!;
    expect((option.sections as Wire[]).map((section) => section.choiceGroup)).toEqual(["NATURAL", "SOCIAL"]);
    // Sau khi lưu, GET lại và so lần nữa: bản đã lưu giống thân đã gửi.
    expect(diffWire(body.parts, state.parts)).toEqual([]);
    await waitFor(() => expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled());

    // Sửa một trường do người dùng nhập: chỉ trường đó đổi.
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    await user.clear(points);
    await user.type(points, "55");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(2));
    expect(diffWire(body.parts, (putCalls(server)[1]!.body as { parts: Wire[] }).parts)).toEqual(["$[0].maxPoints: undefined -> 55"]);
  });

  it("defaultFormSaveIsIdentity ở api.http.ts: bài MD, H-SCA Anh (Nghe REQUIRED, Đọc FORBIDDEN) và SPT mã GDKTPL giữ nguyên wire qua tải rồi lưu", async () => {
    const cases: Array<{ name: string; kind: string; parts: Wire[]; check: (body: Wire[]) => void }> = [
      { name: "MD QDA", kind: "QDA", parts: [wirePart([wireScience("sec-ly", "LY"), wireSection("sec-toan", "TOAN", [wireBlock("t", { questionCount: 10 })], { matrix: matrix("REQUIRED") })])], check: () => undefined },
      {
        name: "H-SCA Anh",
        kind: "HSCA",
        parts: [wirePart([wireSection("sec-nghe", "ANH", [wireBlock("n", { questionCount: 10 })], { audioRequirement: "REQUIRED" }), wireSection("sec-doc", "ANH", [wireBlock("d", { questionType: "QUESTION_SET", setCount: 4, setSize: 10, questionCount: 40 })], { audioRequirement: "FORBIDDEN" })])],
        check: (parts) => {
          const sections = (parts[0]!.sections as Wire[]).map((section) => section.audioRequirement);
          expect(sections).toEqual(["REQUIRED", "FORBIDDEN"]);
        },
      },
      {
        name: "SPT GDKTPL",
        kind: "SPT",
        parts: [wirePart([wireSection("sec-kt", "GDKTPL", [wireBlock("k", { questionCount: 20 })])])],
        check: (parts) => expect((parts[0]!.sections as Wire[])[0]!.subjectCode).toBe("GDKTPL"),
      },
    ];
    for (const item of cases) {
      const { server } = boot({ kind: item.kind, parts: item.parts });
      const loaded = await getExamStructure(EXAM_ID);
      await updateExamStructure(EXAM_ID, { parts: loaded.parts, examQuotas: loaded.examQuotas ?? [], expectedRevision: loaded.revision });
      const sent = putCalls(server)[0]!.body as { parts: Wire[] };
      expect(diffWire(item.parts, sent.parts), item.name).toEqual([]);
      item.check(sent.parts);
    }
  });

  it("SPT mã GDKTPL mở DS (bí danh tra ECON_LAW); lưu sau khi sửa dòng vẫn gửi mã GDKTPL, môn khác không đổi", async () => {
    const user = userEvent.setup();
    const parts = [wirePart([wireSection("sec-kt", "GDKTPL", [wireBlock("k-1", { name: "Dong KT", questionCount: 20 })]), wireSection("sec-toan", "TOAN", [wireBlock("t", { questionCount: 10 })])])];
    const { server } = boot({ kind: "SPT", parts });
    const undo = patchSubjectFormEntry("SPT", "ECON_LAW", { enabled: true });
    openStructure("?section=sec-kt");
    expect(await screen.findByRole("heading", { name: "DS GDKTPL" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sua Dong KT" }));
    const field = screen.getByRole("textbox", { name: "DS Dong KT so luong" });
    await user.clear(field);
    await user.type(field, "18");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body);
    expect(sent["sec-kt"]).toMatchObject({ subjectCode: "GDKTPL" });
    const seeded = parts[0]!.sections as Wire[];
    expect(diffWire(seeded[0], sent["sec-kt"])).toEqual(["$.blocks[0].questionCount: 20 -> 18"]);
    expect(diffWire(seeded[1], sent["sec-toan"])).toEqual([]);
    undo();
  });
});

describe("Thân PUT của Danh sách dòng thật (CR-3 AC-12, FTM AC 6, AC 40)", () => {
  const cases: ReadonlyArray<{ name: string; kind: string; parts: () => Wire[]; section: string; row: string }> = [
    { name: "HSA Văn", kind: "HSA", parts: () => oneSection(hsaLiteratureSection()), section: "sec-van", row: "van-5" },
    { name: "QDA Văn", kind: "QDA", parts: () => oneSection(qdaLiteratureSection()), section: "sec-van", row: "van-1" },
    { name: "BCA Văn", kind: "BCA", parts: () => oneSection(bcaLiteratureSection()), section: "sec-van", row: "essay-1" },
    { name: "THPTQG Lý Phần II", kind: "THPTQG", parts: thptqgPhysicsParts, section: "sec-p2", row: "p2-tf" },
  ];

  it.each(cases)("rowFormSaveIsIdentity: $name mở bằng DS thật, sửa tên một dòng rồi Lưu: thân PUT chỉ khác ở tên, lưu rồi GET lại bằng thân đã gửi", async ({ kind, parts, section, row }) => {
    restore();
    const user = userEvent.setup();
    const original = parts();
    const { server, state } = boot({ kind, parts: original });
    openStructure(`?section=${section}`);
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await user.click(document.querySelector<HTMLElement>(`[data-form-row="${row}"] button`)!);
    await user.type(await screen.findByRole("textbox", { name: /Tên nhóm/ }), "X");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const body = putCalls(server)[0]!.body as { parts: Wire[] };
    const diff = diffWire(original, body.parts);
    expect(diff).toHaveLength(1);
    expect(diff[0]).toMatch(/\.name: ".*" -> ".*X"$/);
    expect(diffWire(body.parts, state.parts)).toEqual([]);
  });

  it("THPTQG Văn (chỉ đọc): không có nút Sửa nên không có gì để sửa, và Kiểm tra không gửi PUT khi bản nháp sạch", async () => {
    restore();
    const { server } = boot({ kind: "THPTQG", parts: oneSection(thptqgLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(document.querySelector('[data-form-row="essay-1"] button')).toBeNull();
    expect(putCalls(server)).toHaveLength(0);
  });

  it("noRowFormRequests: mở môn DS thật và mở dòng chỉ gọi GET (tải bài, cấu trúc, mẫu, danh mục)", async () => {
    restore();
    const user = userEvent.setup();
    const { server } = boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await user.click(document.querySelector<HTMLElement>('[data-form-row="van-1"] button')!);
    await screen.findByRole("heading", { name: /Sửa dòng:/ });
    expect([...new Set(requestLog(server).map((request) => request.split(" ")[0]))]).toEqual(["GET"]);
  });
});

describe("Chọn biểu mẫu không gọi request (FTM mục 9)", () => {
  it("selectionMakesNoRequests: tính biểu mẫu của mọi môn là hàm thuần, không đụng fetch", () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const parts = [part("p", [scienceSection("a", "LY"), scienceSection("b", "HOA"), { ...scienceSection("c", "TOAN"), blocks: [block("x")] }])];
    const pins = selectAllSubjectForms(standardExam("QDA"), parts);
    expect(Object.keys(pins)).toEqual(["a", "b", "c"]);
    expect(Object.values(pins).map((pin) => pin.form)).toEqual(["MATRIX", "MATRIX", "DEFAULT"]);
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });

  it("selectionMakesNoRequests: mở màn Cấu trúc với mọi dạng bật gọi đúng các request như khi mọi dạng tắt", async () => {
    const opened = async () => {
      const { server } = boot();
      const view = openStructure("?exam=1");
      await screen.findByRole("heading", { level: 2, name: /Tổng quan bài/ });
      await waitFor(() => expect(server.calls("GET", `${STRUCTURE}/summary`).length).toBeGreaterThan(0));
      const log = requestLog(server).toSorted();
      view.unmount();
      return log;
    };
    const withForms = await opened();
    // Gỡ chỗ cắm: bảng đăng ký của sản phẩm, mọi dạng tắt.
    restore();
    const withoutForms = await opened();
    expect(withForms).toEqual(withoutForms);
    expect(withForms).toContain(`GET ${STRUCTURE}`);
    expect(withForms.some((line) => line.includes(TEMPLATES))).toBe(true);
  });
});

/**
 * CR-2 AC-8 (FTM AC 6, AC 40 cho MT; plan B4): mở môn MT thật của mọi mẫu chuẩn QDA, BCA trong fixture rồi Lưu không sửa gì thì thân `PUT`
 * (chặn ở `api.http.ts`) bằng bản đã nạp, so toàn bộ wire với các loại trừ có tên của `diffWire`. Cờ và màn của sản phẩm.
 */
describe("Thân PUT không đổi với MT thật (CR-2 AC-8)", () => {
  beforeEach(() => restore());
  const MATRIX_KINDS = ["QDA", "BCA", "VACT"];

  it("matrixFormSaveIsIdentity: mọi mẫu MT của QDA, BCA, V-ACT mở môn MT đầu tiên không ghi gì vào bản nháp, và wire qua tải rồi lưu không đổi", async () => {
    const covered: string[] = [];
    const mapped = new Map(standardFixtureTemplates().map((template) => [template.code, template]));
    for (const template of standardTemplates.templates.filter((item) => MATRIX_KINDS.includes(item.kindCode))) {
      const target = mapped
        .get(template.code)!
        .parts.flatMap((item) => sectionGroups(item).flatMap((group) => group.sections))
        .find((candidate) => {
          const entry = subjectFormFor(template.kindCode, candidate.subjectCode);
          return entry?.enabled === true && entry.form === "MATRIX";
        });
      if (!target) continue;
      const wireParts = template.structure.parts as unknown as Wire[];
      const { server } = boot({ kind: template.kindCode, parts: wireParts });
      const view = openStructure(`?section=${target.id}`);
      await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" });
      // Mở MT không ghi gì vào bản nháp: nút Lưu vẫn khóa, không có "thay đổi chưa lưu".
      expect(screen.getByRole("button", { name: "Lưu" }), template.code).toBeDisabled();
      expect(screen.queryByText("Có thay đổi chưa lưu"), template.code).toBeNull();
      view.unmount();
      // Thân PUT bằng bản đã nạp, chặn ở `api.http.ts` (không đổi gì trên đường tải rồi lưu).
      const loaded = await getExamStructure(EXAM_ID);
      await updateExamStructure(EXAM_ID, { parts: loaded.parts, examQuotas: loaded.examQuotas ?? [], expectedRevision: loaded.revision });
      const sent = putCalls(server)[0]!.body as { parts: Wire[] };
      expect(diffWire(wireParts, sent.parts), template.code).toEqual([]);
      covered.push(template.code);
    }
    expect(covered).toEqual(expect.arrayContaining(["QDA_FULL", "QDA_PART_MATH", "QDA_PART_SCIENCE", "QDA_SUBJECT_LY", "BCA_FULL", "BCA_PART_TN_BAT_BUOC", "BCA_SUBJECT_SU", "VACT_FULL", "VACT_SUBJECT_TOAN", "VACT_SUBJECT_VIETNAMESE"]));
  }, 180_000);

  it("matrixFormSaveIsIdentity: sửa một ô của MT thì chỉ trường đó đổi trong thân PUT (QDA Lý từ mẫu: Điền 1 → 2)", async () => {
    const user = userEvent.setup();
    const template = standardTemplates.templates.find((item) => item.code === "QDA_SUBJECT_LY")!;
    const wireParts = template.structure.parts as unknown as Wire[];
    const { server } = boot({ kind: "QDA", parts: wireParts });
    const sectionId = (wireParts[0]!.sections as Wire[])[0]!.id as string;
    openStructure(`?section=${sectionId}`);
    const fill = await screen.findByRole("textbox", { name: "Điền đáp án" });
    await user.clear(fill);
    await user.type(fill, "2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = putCalls(server)[0]!.body as { parts: Wire[] };
    expect(diffWire(wireParts, sent.parts)).toEqual(["$[0].sections[0].blocks[2].questionCount: 1 -> 2"]);
  });

  it("vactFlexibleKeepsNulls: V-ACT Toán từ mẫu, sửa tổng câu con 18 → 15: thân PUT chỉ đổi `questionCount` của nhóm chùm, `setCount`, `setSize` vẫn null", async () => {
    const user = userEvent.setup();
    const template = standardTemplates.templates.find((item) => item.code === "VACT_SUBJECT_TOAN")!;
    const wireParts = template.structure.parts as unknown as Wire[];
    const { server } = boot({ kind: "VACT", parts: wireParts });
    const sectionId = (wireParts[0]!.sections as Wire[])[0]!.id as string;
    openStructure(`?section=${sectionId}`);
    const children = await screen.findByLabelText("Câu con trong chùm");
    await user.clear(children);
    await user.type(children, "15");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = putCalls(server)[0]!.body as { parts: Wire[] };
    expect(diffWire(wireParts, sent.parts)).toEqual(["$[0].sections[0].blocks[1].questionCount: 18 -> 15"]);
    const set = ((sent.parts[0]!.sections as Wire[])[0]!.blocks as Wire[])[1]!;
    expect(set.setCount ?? null).toBeNull();
    expect(set.setSize ?? null).toBeNull();
  });
});

/**
 * CR-2H AC-9 (FTM AC 6, AC 40, B4, B7 cho HSA): mọi mẫu HSA dùng MT mở MT không dirty nháp, qua tải rồi lưu không đổi wire;
 * HSA có dữ liệu cũ chuyển đổi chỉ qua HSA-D `convertLegacySection`; tắt cờ HSA:TOAN mở MD không banner cảnh báo.
 */
describe("Thân PUT và chuyển đổi HSA MT thật (CR-2H AC-9, B7)", () => {
  beforeEach(() => restore());

  it("hsaMatrixSaveIsIdentity: mọi mẫu chuẩn HSA (FULL, PART, SUBJECT của TOAN, LY, HOA, SINH, SU, DIA) mở MT không dirty và qua get->put giữ nguyên wire", async () => {
    const covered: string[] = [];
    const mapped = new Map(standardFixtureTemplates().map((template) => [template.code, template]));
    for (const template of standardTemplates.templates.filter((item) => item.kindCode === "HSA")) {
      const targets = mapped
        .get(template.code)!
        .parts.flatMap((item) => sectionGroups(item).flatMap((group) => group.sections))
        .filter((candidate) => {
          const entry = subjectFormFor("HSA", candidate.subjectCode);
          return entry?.enabled === true && entry.form === "MATRIX";
        });
      if (targets.length === 0) continue;
      const wireParts = template.structure.parts as unknown as Wire[];
      const { server } = boot({ kind: "HSA", parts: wireParts });
      const view = openStructure(`?section=${targets[0]!.id}`);
      await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" });
      expect(screen.getByRole("button", { name: "Lưu" }), template.code).toBeDisabled();
      expect(screen.queryByText("Có thay đổi chưa lưu"), template.code).toBeNull();
      expect(screen.queryByTestId("full-form-notice"), template.code).toBeNull();
      view.unmount();

      const loaded = await getExamStructure(EXAM_ID);
      await updateExamStructure(EXAM_ID, { parts: loaded.parts, examQuotas: loaded.examQuotas ?? [], expectedRevision: loaded.revision });
      const sent = putCalls(server)[0]!.body as { parts: Wire[] };
      expect(diffWire(wireParts, sent.parts), template.code).toEqual([]);
      covered.push(template.code);
    }
    expect(covered).toEqual(
      expect.arrayContaining([
        "HSA_FULL",
        "HSA_PART_MATH",
        "HSA_PART_SCIENCE",
        "HSA_SUBJECT_TOAN",
        "HSA_SUBJECT_LY",
        "HSA_SUBJECT_HOA",
        "HSA_SUBJECT_SINH",
        "HSA_SUBJECT_SU",
        "HSA_SUBJECT_DIA",
      ]),
    );
  }, 180_000);

  it("hsaLegacyOnlyHsaDConversion: HSA Toán dữ liệu cũ chuyển đổi được mở MT (không về MD), lưu nguyên quy tắc HSA-D tạo ra không tự biến đổi thêm", async () => {
    const user = userEvent.setup();
    const legacy = [
      wirePart([
        wireSection(
          "sec-toan",
          "TOAN",
          [
            wireBlock("b1", { name: "Đơn", questionType: "SINGLE_CHOICE", questionCount: 32, templateGroupCode: "HSA_MATH_SINGLE" }),
            wireBlock("b2", { name: "Điền", questionType: "SHORT_ANSWER", questionCount: 15, templateGroupCode: "HSA_MATH_SINGLE" }),
            wireBlock("b3", { name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, templateGroupCode: "HSA_MATH_CONTEXT" }),
          ],
          {
            knowledgeScope: { nodeIds: ["kn-0D1"], everyChildInScope: true },
            topicQuotas: [{ id: "tq1", name: "Hàm số", nodeIds: ["kn-0D1-1"], count: 5, enforcement: "REQUIRED", basis: "QUESTION" }],
          },
        ),
      ]),
    ];
    const { server } = boot({ kind: "HSA", parts: legacy });
    openStructure("?section=sec-toan");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeInTheDocument();
    expect(screen.getByTestId("mapid-rules-table")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!;
    expect(saved.mapidMode).toBe("MATRIX");
    expect((saved.mapidRules as Wire[]).length).toBeGreaterThanOrEqual(2);
    expect(saved.knowledgeScope ?? null).toBeNull();
    expect((saved.topicQuotas as unknown[] | null) ?? []).toEqual([]);
  });

  it("B7 cờ enabled: khi tắt HSA:TOAN mở MD không full-form-notice; bật lại mở MT không full-form-notice", async () => {
    const template = standardTemplates.templates.find((item) => item.code === "HSA_SUBJECT_TOAN")!;
    const wireParts = template.structure.parts as unknown as Wire[];
    const sectionId = (wireParts[0]!.sections as Wire[])[0]!.id as string;

    const undo = patchSubjectFormEntry("HSA", "TOAN", { enabled: false });
    try {
      boot({ kind: "HSA", parts: wireParts });
      const offView = openStructure(`?section=${sectionId}`);
      expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
      expect(screen.queryByTestId("full-form-notice")).toBeNull();
      offView.unmount();
    } finally {
      undo();
    }

    boot({ kind: "HSA", parts: wireParts });
    openStructure(`?section=${sectionId}`);
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
  });
});
