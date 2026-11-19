import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { boot, openStructure, putCalls, sectionsOf, wireBlock, wireSection, type Wire } from "./subject-form-harness";
import { oneSection, qdaLiteratureSection } from "./subject-row-builders";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/** CR-3a AC-2, AC-6 (FTM AC 21, 25, 52b) và bàn phím của "Thêm điều kiện": màn sửa dòng thật trên `StructureEditor` thật. */
const restores: Array<() => void> = [];
afterEach(() => {
  while (restores.length) restores.pop()!();
});

const rowOf = (id: string) => document.querySelector<HTMLElement>(`[data-form-row="${id}"]`)!;
const edit = async (user: ReturnType<typeof userEvent.setup>, id: string, name: string) => {
  await user.click(within(rowOf(id)).getByRole("button", { name: new RegExp(`Sửa dòng ${name}`) }));
  await screen.findByRole("heading", { name: new RegExp(`Sửa dòng: ${name}`) });
};
const blockOf = (body: unknown, sectionId: string, blockId: string) => (sectionsOf(body)[sectionId]!.blocks as Wire[]).find((item) => item.id === blockId)!;

describe("Danh sách dòng: màn sửa dòng", () => {
  it("switchToSetAndAddDifficulty: đổi Câu đơn sang Câu chùm hiện Số chùm, Số câu con; Thêm điều kiện → Độ khó ghi block.matrix SEPARATE theo số câu", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "van-4", "Mot ngu lieu mot cau");
    expect(screen.queryByRole("textbox", { name: /Số chùm/ })).toBeNull();
    await user.click(screen.getByRole("radio", { name: "Câu chùm" }));
    expect(screen.getByRole("textbox", { name: /Số chùm/ })).toHaveValue("1");
    expect(screen.getByRole("textbox", { name: /Số câu con mỗi chùm/ })).toHaveValue("10");
    const setCount = screen.getByRole("textbox", { name: /Số chùm/ });
    await user.clear(setCount);
    await user.type(setCount, "2");
    const setSize = screen.getByRole("textbox", { name: /Số câu con mỗi chùm/ });
    await user.clear(setSize);
    await user.type(setSize, "5");

    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    await user.click(await screen.findByRole("menuitem", { name: /^Độ khó/ }));
    const cells = await screen.findAllByRole("textbox", { name: /· số câu/ });
    await user.clear(cells[0]!);
    await user.type(cells[0]!, "6");
    await user.clear(cells[1]!);
    await user.type(cells[1]!, "4");
    // Mục Độ khó không có lựa chọn Kết hợp/Riêng, Số câu/Tỷ lệ, mức áp dụng (R-13).
    expect(screen.queryByRole("radio", { name: /Kết hợp|Riêng từng chiều|Tỷ lệ/ })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = blockOf(putCalls(server)[0]!.body, "sec-van", "van-4");
    expect(saved).toMatchObject({ questionType: "QUESTION_SET", setCount: 2, setSize: 5, questionCount: 10 });
    expect(saved.matrix).toMatchObject({ mode: "SEPARATE", unit: "COUNT", byGrade: [] });
    const byDifficulty = (saved.matrix as { byDifficulty: Array<{ value: number }> }).byDifficulty;
    expect(byDifficulty.map((item) => item.value).slice(0, 2)).toEqual([6, 4]);
    expect(byDifficulty.reduce((total, item) => total + item.value, 0)).toBe(10);
    expect(saved.matrix).not.toHaveProperty("cells.0");
  });

  it("filterVersusAllocation: MAPID (lọc) ghi knowledgeScope, Phân bổ theo MAPID ghi topicQuotas; hai mục khác nhau, hai trường khác nhau", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "van-1", "Tu khac nhom nghia");
    await user.click(screen.getByRole("button", { name: "Thêm điều kiện" }));
    expect(await screen.findByRole("menuitem", { name: /^MAPID \(lọc\)/ })).toBeEnabled();
    expect(screen.getByRole("menuitem", { name: /^Phân bổ theo MAPID/ })).toBeEnabled();
    await user.click(screen.getByRole("menuitem", { name: /^Phân bổ theo MAPID/ }));
    await user.click(await screen.findByRole("button", { name: /Thêm chỉ tiêu chủ đề/ }));
    // Chỉ mục Phân bổ theo MAPID đang mở; mục lọc chưa thêm nên không có phạm vi kiến thức của dòng.
    expect(document.querySelector('[data-condition="topicQuotas"]')).not.toBeNull();
    // R-9: HSA, QDA, BCA, THPTQG không nhập tỷ lệ, bảng chỉ tiêu không có nút Số câu / Tỷ lệ (AD-89 D2).
    expect(screen.queryByRole("radio", { name: "Tỷ lệ" })).toBeNull();
    expect(document.querySelector('[data-condition="scope"]')).toBeNull();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = blockOf(putCalls(server)[0]!.body, "sec-van", "van-1");
    expect(saved.topicQuotas).toHaveLength(1);
    expect(saved.knowledgeScope == null || (saved.knowledgeScope as { nodeIds: string[] }).nodeIds.length === 0).toBe(true);
  });

  it("dòng đã có MAPID lọc và phân bổ MAPID hiện hai mục mở sẵn; gỡ mục lọc không đụng phân bổ", async () => {
    const user = userEvent.setup();
    const row = wireBlock("r1", {
      name: "Dong 1",
      questionCount: 10,
      knowledgeScope: { nodeIds: ["n1", "n2"] },
      topicQuotas: [{ id: "q1", name: "Chu de", nodeIds: ["n1"], count: 3, enforcement: "REQUIRED" }],
    });
    const { server } = boot({ kind: "QDA", parts: oneSection(wireSection("sec-van", "VAN", [row])) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    expect(within(rowOf("r1")).getByText("MAPID: 2 · Phân bổ MAPID: 1 dòng")).toBeInTheDocument();
    await edit(user, "r1", "Dong 1");
    expect(document.querySelector('[data-condition="scope"]')).not.toBeNull();
    expect(document.querySelector('[data-condition="topicQuotas"]')).not.toBeNull();
    await user.click(screen.getByRole("button", { name: "Bỏ điều kiện MAPID (lọc)" }));
    expect(document.querySelector('[data-condition="scope"]')).toBeNull();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = blockOf(putCalls(server)[0]!.body, "sec-van", "r1");
    expect(saved.topicQuotas).toHaveLength(1);
    expect(saved.knowledgeScope == null).toBe(true);
  });

  it("menu Thêm điều kiện đi bằng bàn phím: Enter mở, mũi tên chọn, Enter thêm điều kiện", async () => {
    const user = userEvent.setup();
    boot({ kind: "QDA", parts: oneSection(qdaLiteratureSection()) });
    openStructure("?section=sec-van");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "van-1", "Tu khac nhom nghia");
    screen.getByRole("button", { name: "Thêm điều kiện" }).focus();
    await user.keyboard("{Enter}");
    const menu = await screen.findByRole("menu");
    expect(within(menu).getAllByRole("menuitem").length).toBeGreaterThanOrEqual(5);
    await user.keyboard("{ArrowDown}{ArrowDown}{Enter}");
    await waitFor(() => expect(document.querySelector("[data-condition]")).not.toBeNull());
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("sp2eMathPartTwoSwitch: SP2E Toán Phần II đổi được Câu đơn ↔ Câu chùm, giữ tổng câu", async () => {
    const user = userEvent.setup();
    restores.push(patchSubjectFormEntry("SP2E", "TOAN", { enabled: true }));
    const { server } = boot({ kind: "SP2E", parts: oneSection(wireSection("sec-toan", "TOAN", [wireBlock("p2", { name: "Phan II", questionCount: 6 })])) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "p2", "Phan II");
    await user.click(screen.getByRole("radio", { name: "Câu chùm" }));
    await user.click(screen.getByRole("radio", { name: "Câu đơn" }));
    expect(screen.queryByRole("textbox", { name: /Số chùm/ })).toBeNull();
    await user.click(screen.getByRole("radio", { name: "Câu chùm" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(blockOf(putCalls(server)[0]!.body, "sec-toan", "p2")).toMatchObject({ questionType: "QUESTION_SET", setCount: 1, setSize: 6, questionCount: 6 });
  });
});
