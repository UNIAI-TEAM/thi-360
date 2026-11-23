import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type * as ContentApi from "@/features/content/api";
import { getContentReferenceFixture as getContentReference } from "@/test/fixtures/content-reference";
import { boot, openStructure, wireBlock, wirePart, wireSection, type Wire } from "./subject-form-harness";

/**
 * UNI-2780 (R-09, R-13, AC-14, B-05, S1-7): nút "+ Thêm nhóm" chỉ hiển thị khi môn đang dùng màn mặc định (MD fallback)
 * với `rowsLocked === false` và `buildMode !== "REVIEW"`. Ẩn ở cả `ExamStructurePartPanel` lẫn `ExamStructureSubjectPanel`
 * khi môn thuộc MATRIX (HSA), ROWS (THPTQG), bài ôn (REVIEW, kể cả rơi về MD khi >= 2 nhóm), hoặc mẫu khóa dòng (`rowsLocked = true`).
 */

vi.mock("@/features/content/api", async (importOriginal) => ({
  ...(await importOriginal<typeof ContentApi>()),
  getContentReference,
  defaultContentApiDeps: { getContentReference },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const ADD_GROUP = /Thêm nhóm/;

function lockedTemplate(code: string, kindCode: string): Wire {
  return {
    code,
    kindCode,
    scope: "SUBJECT",
    timing: "PER_PART",
    label: "Mẫu khóa dòng",
    active: true,
    isDefault: true,
    buildMode: "STANDARD",
    publishable: true,
    fixedCounts: false,
    fixedBlockCounts: true,
    structure: { parts: [] },
    parts: [],
  };
}

function hsaMatrixSection(id = "sec-1"): Wire {
  return wireSection(id, "TOAN", [
    wireBlock("b-single", { name: "Trắc nghiệm đơn", questionType: "SINGLE_CHOICE", questionCount: 32 }),
    wireBlock("b-set", { name: "Câu chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3 }),
    wireBlock("b-fill", { name: "Điền đáp án", questionType: "SHORT_ANSWER", questionCount: 15 }),
  ]);
}

function thptqgRowsSection(id = "sec-1"): Wire {
  return wireSection(id, "TOAN", [
    wireBlock("b-1", { name: "Phần I", questionType: "SINGLE_CHOICE", questionCount: 12 }),
    wireBlock("b-2", { name: "Phần II", questionType: "TRUE_FALSE", questionCount: 4, itemShape: { statements: 4 } }),
    wireBlock("b-3", { name: "Phần III", questionType: "SHORT_ANSWER", questionCount: 6 }),
  ]);
}

function mdFallbackSection(id = "sec-1"): Wire {
  // Nhóm đơn mang `itemShape` không khớp MT/DS -> rơi về MD (`DEFAULT`), hoặc kỳ/môn không đăng ký.
  return wireSection(id, "MATH", [wireBlock("b-md", { name: "Nhóm MD", questionType: "SINGLE_CHOICE", questionCount: 10 })]);
}

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("R-09 / AC-14: chỉ hiện '+ Thêm nhóm' ở MD fallback không khóa dòng", () => {
  it("MATRIX (HSA): ẩn '+ Thêm nhóm' ở cả PartPanel và màn môn", async () => {
    boot({ kind: "HSA", parts: [wirePart([hsaMatrixSection()], { name: "Tư duy định lượng" })] });
    const partView = openStructure("?part=part-1");
    expect(await screen.findByRole("heading", { name: "Tư duy định lượng" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    partView.unmount();

    openStructure("?section=sec-1");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc câu" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
  });

  it("ROWS (THPTQG): ẩn '+ Thêm nhóm' ở cả PartPanel và màn môn", async () => {
    boot({ kind: "THPTQG", parts: [wirePart([thptqgRowsSection()], { name: "Đề Toán THPTQG" })] });
    const partView = openStructure("?part=part-1");
    expect(await screen.findByRole("heading", { name: "Đề Toán THPTQG" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    partView.unmount();

    openStructure("?section=sec-1");
    expect(await screen.findByRole("heading", { name: "Các dòng lấy câu" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
  });

  it("REVIEW (H42): ẩn '+ Thêm nhóm' ở cả PartPanel, màn bài ôn 1 nhóm và màn môn MD >= 2 nhóm của bài ôn", async () => {
    // 1 nhóm: màn bài ôn rút gọn
    boot({
      kind: "HSA",
      parts: [wirePart([wireSection("sec-1", "MATH", [wireBlock("b-1", { questionCount: 10 })])], { name: "Phần bài ôn" })],
      examOverrides: { buildMode: "REVIEW", questionCount: 10 },
    });
    const oneBlockPart = openStructure("?part=part-1");
    expect(await screen.findByRole("heading", { name: "Phần bài ôn" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    oneBlockPart.unmount();

    const oneBlockSubject = openStructure("?section=sec-1");
    expect(await screen.findByRole("heading", { level: 2, name: /Bài ôn/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    oneBlockSubject.unmount();

    // 2 nhóm trong bài ôn -> rơi về ExamStructureSubjectPanel (MD) nhưng vẫn là REVIEW nên không cho '+ Thêm nhóm'
    boot({
      kind: "HSA",
      parts: [
        wirePart(
          [wireSection("sec-1", "MATH", [wireBlock("b-1", { questionCount: 5 }), wireBlock("b-2", { questionCount: 5 })])],
          { name: "Phần ôn 2 nhóm" },
        ),
      ],
      examOverrides: { buildMode: "REVIEW", questionCount: 10 },
    });
    const twoBlockPart = openStructure("?part=part-1");
    expect(await screen.findByRole("heading", { name: "Phần ôn 2 nhóm" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    twoBlockPart.unmount();

    openStructure("?section=sec-1");
    expect(await screen.findByRole("heading", { name: "Nhóm lấy câu" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
  });

  it("MD rowsLocked=true (mẫu fixedBlockCounts phạm vi SUBJECT): ẩn '+ Thêm nhóm' ở cả PartPanel và SubjectPanel", async () => {
    boot({
      kind: "VSAT",
      parts: [wirePart([mdFallbackSection()], { name: "Phần V-SAT khóa dòng" })],
      templates: [lockedTemplate("VSAT_LOCKED", "VSAT")],
      examOverrides: { scope: "SUBJECT", templateCode: "VSAT_LOCKED", buildMode: "STANDARD" },
    });
    const partView = openStructure("?part=part-1");
    expect(await screen.findByRole("heading", { name: "Phần V-SAT khóa dòng" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
    partView.unmount();

    openStructure("?section=sec-1");
    expect(await screen.findByRole("heading", { name: "Nhóm lấy câu" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: ADD_GROUP })).not.toBeInTheDocument();
  });

  it("MD rowsLocked=false (R-13): hiện '+ Thêm nhóm' và thêm nhóm được ở cả PartPanel lẫn SubjectPanel", async () => {
    const user = userEvent.setup();
    boot({
      kind: "QDA",
      parts: [wirePart([mdFallbackSection()], { name: "Phần MD mở" })],
      examOverrides: { scope: "SUBJECT", buildMode: "STANDARD" },
    });

    const partView = openStructure("?part=part-1");
    const partAddButton = await screen.findByRole("button", { name: ADD_GROUP });
    await user.click(partAddButton);
    expect(await screen.findByRole("textbox", { name: /^Tên nhóm/ })).toBeInTheDocument();
    partView.unmount();

    boot({
      kind: "QDA",
      parts: [wirePart([mdFallbackSection()], { name: "Phần MD mở" })],
      examOverrides: { scope: "SUBJECT", buildMode: "STANDARD" },
    });
    openStructure("?section=sec-1");
    const subjectAddButton = await screen.findByRole("button", { name: ADD_GROUP });
    await user.click(subjectAddButton);
    expect(await screen.findByRole("textbox", { name: /^Tên nhóm/ })).toBeInTheDocument();
  });
});
