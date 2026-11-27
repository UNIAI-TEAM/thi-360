import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EXAM_ID } from "./fake-exams-server";
import { findFormTargetElement, focusFormTarget, formTarget, MATRIX_MAPID_RULES_FIELD } from "../subject-forms/navigation";
import { boot, checkCalls, openStructure, putCalls, qdaParts } from "./subject-form-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

beforeEach(() => {
  routerMock.push.mockReset();
});

describe("Điều hướng theo biểu mẫu (FTM R-15)", () => {
  it("MD giữ đích cũ: ?block= của môn MD mở màn nhóm, không mở màn MT/DS", async () => {
    boot();
    openStructure("?block=toan-b1");
    expect(await screen.findByRole("textbox", { name: "Tên nhóm" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Cấu trúc thành phần câu hỏi|Các dòng lấy câu/ })).toBeNull();
  });

  it("điều hướng lỗi từ bước 4 (?block=toan-b1&from=review) mở màn nhóm MD và hiện nút Lưu và quay lại kiểm tra", async () => {
    boot();
    openStructure("?block=toan-b1&from=review");
    expect(await screen.findByRole("textbox", { name: "Tên nhóm" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Lưu và quay lại kiểm tra" })).toBeInTheDocument();
  });

  it("khung không lộ nút thêm, xóa, đổi thứ tự nhóm khi mở MT hoặc DS; màn MD vẫn có", async () => {
    const user = userEvent.setup();
    boot();
    openStructure("?section=sec-ly");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa nhóm|Lên|Xuống/ })).toBeNull();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /VAN/ }));
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa nhóm|Lên|Xuống/ })).toBeNull();
    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
  });
});

/**
 * CR-3 AC-13 (FTM AC 59 cho DS) và R1-8 trên Danh sách dòng thật: ?block=, bấm lỗi kiểm tra và lỗi chặn lưu mở đúng màn sửa dòng của đúng dòng và đặt
 * focus vào đó, kể cả khi môn DS vừa được vẽ (màn sửa dòng vẽ sau khung nên màn tự đặt focus, không nhờ bước focus một lần của khung).
 */
describe("Điều hướng tới dòng của Danh sách dòng thật (CR-3 AC-13)", () => {
  const editorOf = (name: RegExp) => screen.findByRole("heading", { name: new RegExp("Sửa dòng: " + name.source) });

  it("rowTargets: ?block= của nhóm thuộc môn DS mở màn sửa đúng dòng và focus nằm trong màn sửa", async () => {
    boot();
    openStructure("?block=van-r2");
    const heading = await editorOf(/Van dong 2/);
    await waitFor(() => expect(heading).toHaveFocus());
    expect(screen.queryByRole("button", { name: /Xóa nhóm|Đưa nhóm/ })).toBeNull();
    expect(document.querySelector('[data-form-row-editor="van-r2"]')).not.toBeNull();
  });

  it("rowTargets: Kiểm tra cấu trúc khi dòng DS có lỗi mở đúng dòng từ Đến vị trí cần sửa và đặt focus vào màn sửa", async () => {
    const user = userEvent.setup();
    boot({ parts: qdaParts({ vanR2: 0 }) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 2, name: /TOAN/ });
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    const quick = await screen.findByTestId("structure-quick-check-result");
    await user.click(within(quick).getAllByRole("button", { name: "Đến vị trí cần sửa" })[0]!);
    await editorOf(/Van dong 2/);
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
  });

  it("rowTargets: lỗi chặn lưu của một dòng DS không gửi request, mở đúng dòng và đặt focus; chọn dòng khác thì màn sửa chuyển dòng và focus theo", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ vanR2: 0 }) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 2, name: /TOAN/ });
    await user.click((await screen.findAllByRole("button", { name: "Khai chỉ tiêu" }))[0]!);
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await editorOf(/Van dong 2/);
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    expect(checkCalls(server)).toHaveLength(0);
    expect(putCalls(server)).toHaveLength(0);
    await user.click(within(document.querySelector<HTMLElement>('[data-form-row="van-r1"]')!).getByRole("button", { name: /Sửa dòng/ }));
    expect(await editorOf(/Van dong 1/)).toHaveFocus();
  });
});

/**
 * CR-2 AC-9 (FTM AC 59 cho MT): `?block=`, bấm lỗi kiểm tra và tóm tắt của nhóm thuộc môn MT thật vào đúng ô của dòng thành phần và đặt focus,
 * cả khi đi qua Lưu và chuyển; không mở editor nhóm đầy đủ.
 */
describe("Đích MT thật (CR-2 AC-9)", () => {
  it.each([
    ["sec-ly-set", /^Số chùm của /],
    ["sec-ly-fill", /^Số câu của .*dien/i],
    ["sec-ly-single", /^Số câu của .*don/i],
  ])("matrixTargets: ?block=%s mở môn MT và đặt focus vào ô tương ứng, không mở màn nhóm", async (blockId, labelPattern) => {
    boot();
    openStructure(`?block=${blockId}`);
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name: labelPattern })).toHaveFocus());
    expect(screen.queryByRole("textbox", { name: "Tên nhóm" })).toBeNull();
  });

  it("matrixTargets: điều hướng ?block=sec-hoa-set&from=review vào ô Số chùm, không mở màn nhóm", async () => {
    boot();
    openStructure("?block=sec-hoa-set&from=review");
    await waitFor(() => expect(screen.getByRole("textbox", { name: /^Số chùm của / })).toHaveFocus());
    expect(screen.queryByRole("textbox", { name: "Tên nhóm" })).toBeNull();
  });

  it("matrixTargets: sửa bẩn ở MT rồi bấm Lưu và quay lại kiểm tra (?from=review) gửi PUT và quay về /review?recheck=1", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly&from=review");
    const fill = await screen.findByRole("textbox", { name: /^Số câu của .*dien/i });
    await user.clear(fill);
    await user.type(fill, "2");
    await user.click(screen.getByRole("button", { name: "Lưu và quay lại kiểm tra" }));
    await waitFor(() => expect(server.calls("PUT", `/api/v1/admin/exams/${EXAM_ID}/structure`)).toHaveLength(1));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review?recheck=1`));
  });

  it("khung không lộ nút thêm, xóa, đổi thứ tự nhóm khi mở MT thật", async () => {
    boot();
    openStructure("?section=sec-ly");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa nhóm|Lên|Xuống/ })).toBeNull();
  });
});

/**
 * CR-2H AC-10 (FTM B9, R-15): ?block= của HSA Toán/Hóa mở MT và đặt focus vào ô vai trò; lỗi MAPID_RULE_CONFLICT
 * bấm "Đi tới quy tắc" mở HSA MT và đặt focus đúng dòng `[data-field="mapid-rule-<id>"]`.
 */
describe("Đích HSA MT và quy tắc MAPID (CR-2H AC-10)", () => {

  const hsaParts = () => [
    {
      id: "part-1",
      order: 1,
      name: "Phần 1",
      durationMinutes: 75,
      questionCount: 67,
      questionCountOnPaper: 67,
      questionCountRequired: 67,
      participation: { mode: "ALL" },
      options: [],
      sections: [
        {
          id: "sec-toan",
          subjectCode: "TOAN",
          questionCount: 50,
          mapidMode: "MATRIX",
          mapidRules: [
            { id: "r1", type: "COUNT", groupId: "sec-toan-fill", nodeIds: ["kn-0D1"], comparator: "EXACT", count: 3 },
            { id: "r2", type: "COUNT", groupId: "sec-toan-fill", nodeIds: ["kn-0D1"], comparator: "EXACT", count: 4 },
          ],
          blocks: [
            { id: "sec-toan-single", name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 32, requiredPointsPerQuestion: 1 },
            { id: "sec-toan-fill", name: "Điền đáp án", questionType: "SHORT_ANSWER", questionCount: 15, requiredPointsPerQuestion: 1 },
            { id: "sec-toan-set", name: "Chùm cuối", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, requiredPointsPerQuestion: 1 },
          ],
        },
        {
          id: "sec-hoa",
          subjectCode: "HOA",
          questionCount: 17,
          blocks: [
            { id: "sec-hoa-single", name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 13, requiredPointsPerQuestion: 1 },
            { id: "sec-hoa-set", name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, requiredPointsPerQuestion: 1 },
            { id: "sec-hoa-fill", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 1, requiredPointsPerQuestion: 1 },
          ],
        },
      ],
    },
  ];

  it.each([
    ["sec-toan-set", "Số chùm của Chùm cuối"],
    ["sec-toan-fill", "Số câu của Điền đáp án"],
    ["sec-toan-single", "Số câu của Trắc nghiệm độc lập"],
    ["sec-hoa-set", "Số chùm của Chùm"],
    ["sec-hoa-fill", "Số câu của Trả lời ngắn"],
  ])("hsaMatrixTargets: ?block=%s mở HSA MT và đặt focus vào ô %s, không mở màn nhóm", async (blockId, name) => {
    boot({ kind: "HSA", parts: hsaParts() });
    openStructure(`?block=${blockId}`);
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name })).toHaveFocus());
    expect(screen.queryByRole("textbox", { name: "Tên nhóm" })).toBeNull();
  });

  it("mapidRuleTarget: mở ?section=sec-toan&rule=r1 đặt focus đúng dòng quy tắc MAPID trong HSA Toán MT", async () => {
    boot({ kind: "HSA", parts: hsaParts() });
    openStructure("?section=sec-toan&rule=r1");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId("mapid-rule-row-r1")).toHaveFocus());
  });

  it("formTarget & focusFormTarget: MAPID_RULE_CONFLICT và MAPID_RULE_GROUP_MISSING đặt focus trực tiếp vào dòng quy tắc hoặc fallback MAPID_RULES", () => {
    const target = formTarget({ form: "MATRIX", notice: false }, { sectionId: "sec-toan", issueCode: "MAPID_RULE_CONFLICT", ruleId: "r1" });
    expect(target).toEqual({ kind: "MATRIX_FIELD", field: { sectionId: "sec-toan", name: MATRIX_MAPID_RULES_FIELD, ruleId: "r1" } });

    const groupMissing = formTarget({ form: "MATRIX", notice: false }, { sectionId: "sec-toan", issueCode: "MAPID_RULE_GROUP_MISSING" });
    expect(groupMissing).toEqual({ kind: "MATRIX_FIELD", field: { sectionId: "sec-toan", name: MATRIX_MAPID_RULES_FIELD } });

    const host = document.createElement("div");
    const section = document.createElement("div");
    section.setAttribute("data-form-field", MATRIX_MAPID_RULES_FIELD);
    const table = document.createElement("div");
    table.setAttribute("data-testid", "mapid-rules-table");
    const row = document.createElement("div");
    row.setAttribute("data-field", "mapid-rule-r1");
    row.tabIndex = -1;
    const childBtn = document.createElement("button");
    row.appendChild(childBtn);
    table.appendChild(row);
    section.appendChild(table);
    host.appendChild(section);
    document.body.appendChild(host);
    try {
      expect(findFormTargetElement(host, target!)).toBe(row);
      expect(focusFormTarget(host, target!)).toBe(true);
      expect(document.activeElement).toBe(row);

      row.remove();
      expect(findFormTargetElement(host, target!)).toBe(table);
      expect(focusFormTarget(host, target!)).toBe(true);
      expect(document.activeElement).toBe(table);

      table.remove();
      expect(findFormTargetElement(host, groupMissing!)).toBe(section);
      expect(focusFormTarget(host, groupMissing!)).toBe(true);
      expect(document.activeElement).toBe(section);
    } finally {
      host.remove();
    }
  });

  it("MAPID_RULES fallback focuses MapidModeField radio when mapidMode === 'ALL' (mapid-rules-table absent)", async () => {
    const parts = hsaParts();
    (parts[0]!.sections[0] as { mapidMode: string }).mapidMode = "ALL";
    boot({ kind: "HSA", parts });
    openStructure("?section=sec-toan");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
    expect(screen.queryByTestId("mapid-rules-table")).not.toBeInTheDocument();
    const target = formTarget({ form: "MATRIX", notice: false }, { sectionId: "sec-toan", issueCode: "MAPID_RULE_CONFLICT" })!;
    expect(focusFormTarget(document, target)).toBe(true);
    expect(screen.getByTestId("mapid-mode-all")).toHaveFocus();
  });
});
