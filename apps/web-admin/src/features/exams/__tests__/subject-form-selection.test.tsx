import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { listSubjectFormPairs, patchSubjectFormEntry } from "../subject-forms/registry";
import { selectAllSubjectForms, selectSubjectForm } from "../subject-forms/selection";
import { block, part, reviewExam, reviewFixtureTemplates, scienceSection, section, setBlock, standardExam } from "./subject-form-builders";
import { boot, installForms, openStructure, putCalls, wireBlock, wirePart, wireScience, wireSection } from "./subject-form-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

/**
 * FTM R-1 (CR-1 AC-3): thứ tự chọn biểu mẫu. Hàm thuần: không gọi request. Ca trên màn thật (ghim trong phiên, dòng chữ) nằm ở
 * khối "Chọn trên màn" cuối tệp.
 */
const restores: Array<() => void> = [];
const enable = (kind: string, subject: string) => restores.push(patchSubjectFormEntry(kind, subject, { enabled: true }));
afterEach(() => {
  while (restores.length) restores.pop()!();
});

describe("Thứ tự chọn biểu mẫu (FTM R-1)", () => {
  it("reviewNeverUsesSubjectForms: bài ôn theo buildMode không bao giờ ra MT/DS, kể cả dạng bật, kỳ lạ, bài ôn nhiều nhóm", () => {
    enable("QDA", "LY");
    enable("SPT", "TOAN");
    const single = section("s1", "LY", [block("b")]);
    const multi = section("s2", "LY", [block("a"), setBlock("c", 1, 3)]);
    const parts = [part("p", [single, multi, section("s3", "TOAN", [block("x")])])];
    expect(selectSubjectForm(reviewExam("QDA"), single, parts)).toEqual({ form: "REVIEW", notice: false });
    expect(selectSubjectForm(reviewExam("QDA"), multi, parts)).toEqual({ form: "DEFAULT", notice: false });
    expect(selectSubjectForm(reviewExam("TEST_NEW"), single, parts)).toEqual({ form: "REVIEW", notice: false });
    const spt = [part("p", [section("t", "TOAN", [block("x")])])];
    expect(selectSubjectForm(reviewExam("SPT"), spt[0]!.sections[0]!, spt)).toEqual({ form: "REVIEW", notice: false });
  });

  it("mọi mẫu bài ôn của fixture ra ON hoặc MD, không bao giờ MT/DS, dù mọi dạng đang bật", () => {
    for (const { kindCode, subjectCode } of listSubjectFormPairs()) restores.push(patchSubjectFormEntry(kindCode, subjectCode, { enabled: true }));
    const templates = reviewFixtureTemplates();
    expect(templates.length).toBeGreaterThan(0);
    for (const template of templates) {
      for (const p of template.parts) {
        for (const target of p.sections) {
          const picked = selectSubjectForm(reviewExam(template.kindCode), target, template.parts);
          expect(["REVIEW", "DEFAULT"], `${template.code} ${target.subjectCode}`).toContain(picked.form);
          expect(picked.notice).toBe(false);
        }
      }
    }
  });

  it("unknownPairUsesDefault: kỳ lạ, môn lạ, mã cũ ra MD không dòng chữ", () => {
    const target = section("s", "TOAN", [block("b")]);
    const parts = [part("p", [target])];
    expect(selectSubjectForm(standardExam("TEST_NEW"), target, parts)).toEqual({ form: "DEFAULT", notice: false });
    const legacy = section("s2", "MATH", [block("b")]);
    expect(selectSubjectForm(standardExam("HSA"), legacy, [part("p", [legacy])])).toEqual({ form: "DEFAULT", notice: false });
    const newSubject = section("s3", "NEW_SUBJECT", [block("b")]);
    expect(selectSubjectForm(standardExam("HSA"), newSubject, [part("p", [newSubject])])).toEqual({ form: "DEFAULT", notice: false });
  });

  it("dạng tắt ra MD không dòng chữ; bật thì ra dạng của mục", () => {
    const target = scienceSection("s", "LY");
    const parts = [part("p", [target])];
    expect(selectSubjectForm(standardExam("HSA"), target, parts)).toEqual({ form: "MATRIX", notice: false });
    const undo = patchSubjectFormEntry("HSA", "LY", { enabled: false });
    restores.push(undo);
    expect(selectSubjectForm(standardExam("HSA"), target, parts)).toEqual({ form: "DEFAULT", notice: false });
    undo();
    expect(selectSubjectForm(standardExam("HSA"), target, parts)).toEqual({ form: "MATRIX", notice: false });
  });

  it("dạng bật mà không khớp ra MD kèm dòng chữ và lý do", () => {
    enable("QDA", "LY");
    const violating = section("s", "LY", [block("a", { questionCount: 14 }), setBlock("c", 3, 3), block("f", { questionType: "SHORT_ANSWER", questionCount: 1 })]);
    expect(selectSubjectForm(standardExam("QDA"), violating, [part("p", [violating])])).toEqual({ form: "DEFAULT", notice: true, reason: "LIMITS_VIOLATED" });
  });

  it("bí danh chọn cùng biểu mẫu với mã canonical; mã gốc của môn không đổi", () => {
    enable("SPT", "ECON_LAW");
    const target = section("s", "GDKTPL", [block("b")]);
    expect(selectSubjectForm(standardExam("SPT"), target, [part("p", [target])])).toEqual({ form: "ROWS", notice: false });
    expect(target.subjectCode).toBe("GDKTPL");
  });

  it("môn không có trong bản nháp ra MD; selectAllSubjectForms phủ môn ở sections, subjectPool, phương án", () => {
    enable("QDA", "LY");
    const loose = scienceSection("loose", "LY");
    expect(selectSubjectForm(standardExam("QDA"), loose, [part("p", [])])).toEqual({ form: "DEFAULT", notice: false });
    const inSections = scienceSection("a", "LY");
    const inPool = scienceSection("b", "LY");
    const inOption = scienceSection("c", "LY");
    const parts = [
      part("p1", [inSections]),
      part("p2", [], { codeSource: { source: "GLOBAL_LIST", branch: null }, subjectPool: [inPool] }),
      part("p3", [], { mode: "CHOICE", participation: { mode: "CHOICE", choose: 1 }, options: [{ id: "o", kind: "SUBJECT", code: "LY", name: null, sections: [inOption], chooseSubjects: null, countsByPick: null }] }),
    ];
    const pins = selectAllSubjectForms(standardExam("QDA"), parts);
    expect(Object.keys(pins).toSorted()).toEqual(["a", "b", "c"]);
    expect(Object.values(pins).every((pin) => pin.form === "MATRIX")).toBe(true);
  });
});

describe("Chọn trên màn: dòng chữ và ghim trong phiên (FTM R-2; CR-1 AC-5)", () => {
  let uninstall: () => void;
  beforeEach(() => {
    uninstall = installForms();
  });
  afterEach(() => uninstall());

  /** Lý có 3 chùm: vi phạm giới hạn R-12 ngay khi nạp, nên MD kèm dòng chữ dù MT đang bật. */
  const violating = () => [
    wirePart([
      wireSection("sec-ly", "LY", [
        wireBlock("ly-s", { questionCount: 14 }),
        wireBlock("ly-c", { name: "Chum", questionType: "QUESTION_SET", setCount: 3, setSize: 3, questionCount: 9 }),
        wireBlock("ly-f", { questionType: "SHORT_ANSWER", questionCount: 1 }),
      ]),
      wireScience("sec-hoa", "HOA"),
      wireSection("sec-toan", "TOAN", [wireBlock("t", { questionCount: 10 })]),
    ]),
  ];

  it("notFittingFallsBackWithNotice: dạng bật mà không khớp vẽ MD kèm một dòng chữ; môn khớp ra MT; môn dạng tắt ra MD không dòng chữ", async () => {
    const user = userEvent.setup();
    boot({ parts: violating() });
    openStructure("?section=sec-ly");
    expect(await screen.findByTestId("full-form-notice")).toHaveTextContent("Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được.");
    expect(screen.getByTestId("full-form-notice")).toHaveAttribute("data-reason", "LIMITS_VIOLATED");
    expect(screen.getByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /HOA/ }));
    expect(await screen.findByRole("heading", { name: "MT HOA" })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
  });

  it("formStableWithinSession: sửa ở MD cho tới khi khớp không đổi biểu mẫu trong phiên; lưu rồi mở lại thì ra MT", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: violating() });
    const first = openStructure("?block=ly-c");
    const sets = await screen.findByRole("textbox", { name: /^Số chùm/ });
    await user.clear(sets);
    await user.type(sets, "2");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    // Bản đã lưu khớp giới hạn, nhưng biểu mẫu của phiên này đã ghim: vẫn MD, dòng chữ còn.
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /LY/ }));
    expect(await screen.findByTestId("full-form-notice")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "MT LY" })).toBeNull();
    first.unmount();
    // Tải lại (mở lại bài): tính lại theo cấu trúc đã lưu, nay khớp nên mở MT.
    openStructure("?section=sec-ly");
    expect(await screen.findByRole("heading", { name: "MT LY" })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
  });
});
