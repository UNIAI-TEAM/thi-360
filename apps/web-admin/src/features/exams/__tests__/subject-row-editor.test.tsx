import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { BlockMatrixEditor } from "../components/block-matrix-editor";
import { patchSubjectFormEntry } from "../subject-forms/registry";
import { boot, checkCalls, openStructure, putCalls, sectionsOf, wireBlock, wireSection, type Wire } from "./subject-form-harness";
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
const edit = async (user: UserEvent, id: string, name: string) => {
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

const countField = () => screen.getByRole("textbox", { name: "Số câu" });
const percentField = () => screen.getByRole("textbox", { name: "Tỷ lệ (%)" });
const typeInto = async (user: UserEvent, field: HTMLElement, text: string) => {
  await user.clear(field);
  await user.type(field, text);
};
/** Mục 40 câu không khóa: hai dòng (20 câu đơn, 20 câu), một môn SP2E Toán. */
const sp2eForty = (rows: Wire[] = [wireBlock("r1", { name: "Dong mot", questionCount: 20 }), wireBlock("r2", { name: "Dong hai", questionCount: 20 })]) =>
  oneSection(wireSection("sec-toan", "TOAN", rows, { questionCount: 40 }));

describe("Danh sách dòng: nhập theo tỷ lệ ở màn sửa dòng (R-9, FTM AC 30, AC 54 d–g)", () => {
  it("percentStoresCount: mục 40 câu không khóa, dòng câu đơn nhập 30% hiện '12 câu' trước khi lưu, lưu 12; GET lại và tải lại hiện số, không giữ %", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "SP2E", parts: sp2eForty() });
    const first = openStructure("?section=sec-toan");
    await first.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "r1", "Dong mot");
    await typeInto(user, percentField(), "30");
    expect(screen.getByText("30% của 40 câu = 12 câu")).toBeInTheDocument();
    expect(countField()).toHaveValue("12");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = blockOf(putCalls(server)[0]!.body, "sec-toan", "r1");
    expect(saved.questionCount).toBe(12);
    // Chỉ lưu số câu: không có trường tỷ lệ nào ở thân PUT của dòng.
    expect(JSON.stringify(saved)).not.toMatch(/percent/i);
    first.unmount();

    const reopened = openStructure("?section=sec-toan");
    await reopened.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "r1", "Dong mot");
    expect(countField()).toHaveValue("12");
    expect(percentField()).toHaveValue("");
  });

  it("percentStoresCount: mẫu H-SCA Toán thật bài PART: mẫu số là số câu của mục chứa dòng (mục 25 câu, 40% hiện '10 câu')", async () => {
    const user = userEvent.setup();
    const template = {
      active: true,
      scope: "SUBJECT",
      timing: "SHARED",
      buildMode: "STANDARD",
      isDefault: true,
      publishable: true,
      fixedCounts: false,
      fixedBlockCounts: true,
      omittedLocked: false,
      kindCode: "HSCA",
      maxScore: 10,
      code: "HSCA_TOAN",
      structure: { parts: [] },
    };
    const rows = oneSection(wireSection("sec-toan", "TOAN", [wireBlock("r1", { name: "Trac nghiem", questionCount: 25, templateGroupCode: "HSCA_TOAN_B1" })], { questionCount: 25 }));
    boot({ kind: "HSCA", parts: rows, templates: [template], examOverrides: { scope: "PART", templateCode: "HSCA_TOAN" } });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "r1", "Trac nghiem");
    await typeInto(user, percentField(), "40");
    expect(screen.getByText("40% của 25 câu = 10 câu")).toBeInTheDocument();
    expect(countField()).toHaveValue("10");
  });

  it("rowPercentBelowHundred: một dòng 30% (dưới 100%) lưu được, hiện đúng mẫu số, không cần cả mục đủ 100%", async () => {
    const user = userEvent.setup();
    const { server } = boot({ kind: "SP2E", parts: sp2eForty([wireBlock("r1", { name: "Dong mot", questionCount: 40 })]) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "r1", "Dong mot");
    await typeInto(user, percentField(), "30");
    expect(screen.getByText("30% của 40 câu = 12 câu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(blockOf(putCalls(server)[0]!.body, "sec-toan", "r1").questionCount).toBe(12);
  });

  it("kỳ không cho nhập % (THPTQG) không có ô Tỷ lệ ở màn sửa dòng: % chỉ đọc, một ô số câu", async () => {
    const user = userEvent.setup();
    boot({ kind: "THPTQG", parts: oneSection(wireSection("sec-x", "LY", [wireBlock("r1", { name: "Dong mot", questionCount: 20 })])) });
    openStructure("?section=sec-x");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "r1", "Dong mot");
    expect(screen.queryByRole("textbox", { name: "Tỷ lệ (%)" })).toBeNull();
    expect(screen.getByRole("textbox", { name: /Số câu cần lấy/ })).toHaveValue("20");
  });

  it("wholeSetsOnly: dòng chùm cỡ 4, 25% ra 10 câu báo lỗi tại dòng khi bản nháp chưa đổi; Lưu, Kiểm tra, Tiếp tục không gửi request, focus về ô; bỏ thay đổi thì lưu lại được", async () => {
    const user = userEvent.setup();
    const rows = [wireBlock("s1", { name: "Don", questionCount: 28 }), wireBlock("set1", { name: "Chum", questionType: "QUESTION_SET", setCount: 3, setSize: 4, questionCount: 12 })];
    const { server } = boot({ kind: "SP2E", parts: sp2eForty(rows) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "set1", "Chum");
    await typeInto(user, percentField(), "25");
    expect(await screen.findByText("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu")).toBeInTheDocument();
    // Bản nháp chưa đổi nhưng còn nhập thô: có thay đổi chưa lưu.
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    expect(screen.getAllByRole("textbox", { name: /Số chùm/ })[0]).toHaveValue("3");

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="set1"] [data-form-field="COUNT"]')).toContainElement(document.activeElement as HTMLElement));

    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    await edit(user, "set1", "Chum");
    await typeInto(user, percentField(), "30");
    expect(screen.getByText("30% của 40 câu = 12 câu")).toBeInTheDocument();
    await typeInto(user, percentField(), "20");
    expect(screen.queryByText(/không chia được thành chùm/)).toBeNull();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(blockOf(putCalls(server)[0]!.body, "sec-toan", "set1")).toMatchObject({ setCount: 2, setSize: 4, questionCount: 8 });
  });

  it("rawPercentSurvivesClosingEditor: giữ chữ 25% và lỗi chùm sau khi đóng và mở lại", async () => {
    const user = userEvent.setup();
    const rows = [wireBlock("s1", { questionCount: 28 }), wireBlock("set1", { name: "Chum", questionType: "QUESTION_SET", setCount: 3, setSize: 4, questionCount: 12 })];
    const { server } = boot({ kind: "SP2E", parts: sp2eForty(rows) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "set1", "Chum");
    await typeInto(user, percentField(), "25");
    await user.click(screen.getByRole("button", { name: "Về danh sách" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(percentField()).toHaveValue("25"));
    expect(percentField()).toHaveFocus();
    expect(screen.getByText("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu")).toBeInTheDocument();
    await typeInto(user, percentField(), "20");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });

  it("wholeSetsOnly: nhập 10 câu không chia được chùm 4 thì báo đúng 10 và chặn lưu", async () => {
    const user = userEvent.setup();
    const rows = [wireBlock("s1", { questionCount: 28 }), wireBlock("set1", { name: "Chum", questionType: "QUESTION_SET", setCount: 3, setSize: 4, questionCount: 12 })];
    const { server } = boot({ kind: "SP2E", parts: sp2eForty(rows) });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "set1", "Chum");
    await typeInto(user, countField(), "10");
    expect(screen.getByText("10 câu không chia được thành chùm 4 câu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
  });
});

describe("Danh sách dòng: giữ lỗi tách khi đóng màn sửa", () => {
  it("splitInvalidInputSurvivesClosingEditor: không gửi PUT và khôi phục giá trị sai để sửa", async () => {
    const user = userEvent.setup();
    const row = wireBlock("read", { name: "Doc", questionType: "QUESTION_SET", setCount: 4, setSize: 10, questionCount: 40 });
    const { server } = boot({ kind: "HSCA", parts: oneSection(wireSection("sec-anh", "ANH", [row])) });
    openStructure("?section=sec-anh");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "read", "Doc");
    await user.click(screen.getByRole("button", { name: "Tách dòng" }));
    const first = screen.getByRole("textbox", { name: "Số chùm của dòng con 1" });
    await typeInto(user, first, "0");
    await user.click(screen.getByRole("button", { name: "Về danh sách" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Số chùm của dòng con 1" })).toHaveValue("0"));
    await typeInto(user, screen.getByRole("textbox", { name: "Số chùm của dòng con 1" }), "2");
    await user.click(screen.getByRole("button", { name: /^Tách$/ }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
  });
});

describe("Danh sách dòng: tách và lưu", () => {
  it("splitOnlyWhereAllowed: tách Đọc 4 chùm, giữ mã dòng đầu và shuffleUnits false trong PUT", async () => {
    const user = userEvent.setup();
    const row = wireBlock("read", { name: "Doc", questionType: "QUESTION_SET", setCount: 4, setSize: 10, questionCount: 40, shuffleUnits: false, templateGroupCode: "READ" });
    const { server } = boot({ kind: "HSCA", parts: oneSection(wireSection("sec-anh", "ANH", [row], { audioRequirement: "FORBIDDEN" })) });
    openStructure("?section=sec-anh");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "read", "Doc");
    await user.click(screen.getByRole("button", { name: "Tách dòng" }));
    const selector = screen.getByRole("combobox", { name: "Số dòng sau khi tách" });
    await user.click(selector);
    expect(screen.queryByRole("option", { name: "5 dòng" })).toBeNull();
    await user.click(screen.getByRole("option", { name: "4 dòng" }));
    await user.click(screen.getByRole("button", { name: /^Tách$/ }));
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const saved = sectionsOf(putCalls(server)[0]!.body)["sec-anh"]!;
    const blocks = saved.blocks as Wire[];
    expect(blocks).toHaveLength(4);
    expect(blocks[0]).toMatchObject({ id: "read", templateGroupCode: "READ" });
    expect(blocks.map((item) => [item.setCount, item.setSize, item.questionCount, item.shuffleUnits])).toEqual(Array(4).fill([1, 10, 10, false]));
    expect(saved.audioRequirement).toBe("FORBIDDEN");
  });

  it("positiveChildren: sau khi tách, số chùm 0 chặn lưu và hướng về dòng sai", async () => {
    const user = userEvent.setup();
    const row = wireBlock("read", { name: "Doc", questionType: "QUESTION_SET", setCount: 4, setSize: 10, questionCount: 40 });
    const { server } = boot({ kind: "HSCA", parts: oneSection(wireSection("sec-anh", "ANH", [row])) });
    openStructure("?section=sec-anh");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "read", "Doc");
    await user.click(screen.getByRole("button", { name: "Tách dòng" }));
    await user.click(screen.getByRole("combobox", { name: "Số dòng sau khi tách" }));
    await user.click(screen.getByRole("option", { name: "4 dòng" }));
    await user.click(screen.getByRole("button", { name: /^Tách$/ }));
    const count = screen.getByRole("textbox", { name: /Số chùm/ });
    await typeInto(user, count, "0");
    await user.click(screen.getByRole("button", { name: /^Lưu$/ }));
    expect(putCalls(server)).toHaveLength(0);
    expect(await screen.findByText("Số câu cần lấy phải lớn hơn 0.")).toBeInTheDocument();
  });

  it.each([
    [{ questionType: "QUESTION_SET", questionCount: 12, setCount: null, setSize: null }, "Dòng không có cỡ chùm cố định"],
    [{ questionType: "ESSAY", questionCount: 3, omitted: true }, "Dòng đang tạm bỏ qua"],
    [{ questionType: "QUESTION_SET", questionCount: 12, setCount: 4, setSize: 3, slots: [{ index: 1, tagCodes: ["T"] }] }, "Dòng có điều kiện riêng; bỏ điều kiện trước khi tách"],
  ])("splitBlockedWithConstraints: khóa tách kèm lý do", async (fields, reason) => {
    const user = userEvent.setup();
    const row = wireBlock("read", { name: "Doc", ...fields });
    boot({ kind: "HSCA", parts: oneSection(wireSection("sec-anh", "ANH", [row])) });
    openStructure("?section=sec-anh");
    await screen.findByRole("heading", { name: "Các dòng lấy câu" });
    await edit(user, "read", "Doc");
    expect(screen.getByRole("button", { name: "Tách dòng" })).toBeDisabled();
    expect(screen.getByText(reason)).toBeInTheDocument();
  });

  it("compact matrix editor hides competency switch, table, status and issues even when byCompetency is present", () => {
    const axes = { grades: ["GRADE_12"], difficulties: ["RECOGNITION"] };
    const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
    renderWithProviders(
      <BlockMatrixEditor
        block={{
          id: "blk-c",
          name: "Row",
          questionType: "SINGLE_CHOICE",
          questionCount: 10,
          setCount: null,
          setSize: null,
          requiredPointsPerQuestion: null,
          knowledgeScope: null,
          tagCodes: [],
          childAnswerType: null,
          templateGroupCode: null,
          answerTypeLocked: false,
          matrix: {
            mode: "SEPARATE",
            unit: "COUNT",
            cells: [],
            byGrade: [],
            byDifficulty: [{ code: "RECOGNITION", value: 10 }],
            byCompetency: [{ code: "VACT-TD-LOGIC", value: 4 }],
          },
        }}
        axes={axes}
        labels={labels}
        competencies={[]}
        available={undefined}
        availableState="loading"
        dimensions={["DIFFICULTY"]}
        compact
        onChange={() => undefined}
      />,
    );
    expect(screen.queryByRole("switch", { name: "Phân bổ theo năng lực" })).toBeNull();
    expect(screen.queryByRole("table", { name: "Theo năng lực" })).toBeNull();
    expect(screen.queryByText(/Theo năng lực:/)).toBeNull();
    expect(screen.queryByText(/Tổng Theo năng lực/)).toBeNull();
  });
});
