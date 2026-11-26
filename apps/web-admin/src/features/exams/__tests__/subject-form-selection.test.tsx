import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { selectAllSubjectForms, selectSubjectForm } from "../subject-forms/selection";
import { block, part, reviewExam, reviewFixtureTemplates, scienceSection, section, setBlock, standardExam } from "./subject-form-builders";
import { boot, openStructure, putCalls, wireBlock, wirePart, wireScience, wireSection } from "./subject-form-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

describe("Thứ tự chọn biểu mẫu (FTM R-1)", () => {
  it("reviewNeverUsesSubjectForms: bài ôn theo buildMode không bao giờ ra MT/DS, kể cả kỳ lạ, bài ôn nhiều nhóm", () => {
    const single = section("s1", "LY", [block("b")]);
    const multi = section("s2", "LY", [block("a"), setBlock("c", 1, 3)]);
    const parts = [part("p", [single, multi, section("s3", "TOAN", [block("x")])])];
    expect(selectSubjectForm(reviewExam("QDA"), single, parts)).toEqual({ form: "REVIEW", notice: false });
    expect(selectSubjectForm(reviewExam("QDA"), multi, parts)).toEqual({ form: "DEFAULT", notice: false });
    expect(selectSubjectForm(reviewExam("TEST_NEW"), single, parts)).toEqual({ form: "REVIEW", notice: false });
    const spt = [part("p", [section("t", "TOAN", [block("x")])])];
    expect(selectSubjectForm(reviewExam("SPT"), spt[0]!.sections[0]!, spt)).toEqual({ form: "REVIEW", notice: false });
  });

  it("mọi mẫu bài ôn của fixture ra ON hoặc MD, không bao giờ MT/DS", () => {
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

  it("cặp đăng ký và khớp dữ liệu chọn đúng dạng biểu mẫu", () => {
    const target = scienceSection("s", "LY");
    const parts = [part("p", [target])];
    expect(selectSubjectForm(standardExam("HSA"), target, parts)).toEqual({ form: "MATRIX", notice: false });
    const rowsTarget = section("s2", "VAN", [block("b")]);
    expect(selectSubjectForm(standardExam("QDA"), rowsTarget, [part("p", [rowsTarget])])).toEqual({ form: "ROWS", notice: false });
  });

  it("dạng đăng ký mà không khớp ra MD kèm dòng chữ và lý do", () => {
    const violating = section("s", "LY", [block("a", { questionCount: 14 }), setBlock("c", 3, 3), block("f", { questionType: "SHORT_ANSWER", questionCount: 1 })]);
    expect(selectSubjectForm(standardExam("QDA"), violating, [part("p", [violating])])).toEqual({ form: "DEFAULT", notice: true, reason: "LIMITS_VIOLATED" });
  });

  it("bí danh chọn cùng biểu mẫu với mã canonical; mã gốc của môn không đổi", () => {
    const target = section("s", "GDKTPL", [block("b")]);
    expect(selectSubjectForm(standardExam("SPT"), target, [part("p", [target])])).toEqual({ form: "ROWS", notice: false });
    expect(target.subjectCode).toBe("GDKTPL");
  });

  it("môn không có trong bản nháp ra MD; selectAllSubjectForms phủ môn ở sections, subjectPool, phương án", () => {
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
  /** Lý có 3 chùm: vi phạm giới hạn R-12 ngay khi nạp, nên MD kèm dòng chữ; Hóa khớp mở MT; MATH không đăng ký mở MD không dòng chữ. */
  const violating = () => [
    wirePart([
      wireSection("sec-ly", "LY", [
        wireBlock("ly-s", { questionCount: 14 }),
        wireBlock("ly-c", { name: "Chum", questionType: "QUESTION_SET", setCount: 3, setSize: 3, questionCount: 9 }),
        wireBlock("ly-f", { questionType: "SHORT_ANSWER", questionCount: 1 }),
      ]),
      wireScience("sec-hoa", "HOA"),
      wireSection("sec-math", "MATH", [wireBlock("t", { questionCount: 10 })]),
    ]),
  ];

  it("notFittingFallsBackWithNotice: dạng không khớp vẽ MD kèm một dòng chữ; môn khớp ra MT; môn không đăng ký ra MD không dòng chữ", async () => {
    const user = userEvent.setup();
    boot({ parts: violating() });
    openStructure("?section=sec-ly");
    expect(await screen.findByTestId("full-form-notice")).toHaveTextContent("Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được.");
    expect(screen.getByTestId("full-form-notice")).toHaveAttribute("data-reason", "LIMITS_VIOLATED");
    expect(screen.getByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /HOA/ }));
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /MATH|Toán/ }));
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
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /LY/ }));
    expect(await screen.findByTestId("full-form-notice")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeNull();
    first.unmount();
    openStructure("?section=sec-ly");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
  });
});
