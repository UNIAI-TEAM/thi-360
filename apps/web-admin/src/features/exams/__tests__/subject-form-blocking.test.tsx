import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  boot,
  checkCalls,
  openStructure,
  putCalls,
  qdaParts,
  sectionsOf,
  wireBlock,
  wireScience,
  wireSection,
  wirePart,
  withoutMappingDefaults,
  type Wire,
} from "./subject-form-harness";
import { wireQdaScience } from "./subject-matrix-harness";

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

const FILL_INPUT_NAME = /^Số câu của .*(Điền đáp án|Trả lời ngắn|dien)/i;
const SETS_INPUT_NAME = /^Số chùm của /;
const lyFillInput = () => screen.findByRole("textbox", { name: FILL_INPUT_NAME });
const lySetsInput = () => screen.findByRole("textbox", { name: SETS_INPUT_NAME });
/** Làm bản nháp bẩn ở môn Toán (MD) mà không đụng môn khác: bấm "Khai chỉ tiêu" đầu tiên. */
async function dirtyToan(user: UserEvent) {
  await user.click((await screen.findAllByRole("button", { name: "Khai chỉ tiêu" }))[0]!);
  expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
}

describe("Chặn lưu của MT/DS (FTM R-15)", () => {
  it("(a) lỗi ở môn DS chưa mở: Lưu không gửi PUT, đưa tới dòng lỗi và đặt focus", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ vanR2: 0 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    expect(putCalls(server)).toHaveLength(0);
    expect(window.location.search).toBe("?section=sec-van");
  });

  it("(a) Lưu và chuyển (khi rời bước 2): hộp thoại ở lại, báo lỗi, không PUT, focus về dòng lỗi", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ vanR2: 0 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Quay lại" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent(/TOAN|Toán/);
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Còn lỗi cần sửa trước khi lưu hoặc kiểm tra.");
    expect(putCalls(server)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    expect(window.location.search).toBe("?section=sec-van");
  });

  it("(a) Kiểm tra cấu trúc và Tiếp tục: không gửi PUT hay POST /check, báo lỗi và focus về dòng lỗi", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ vanR2: 0 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    const quick = await screen.findByTestId("structure-quick-check-result");
    await user.click(within(quick).getAllByRole("button", { name: "Đến vị trí cần sửa" })[0]!);
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("(b) nhập thô sai khi bản nháp sạch: Kiểm tra, Tiếp tục không gửi request, focus về ô", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    const input = await lyFillInput();
    await user.clear(input);
    await user.type(input, "abc");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(input).toHaveFocus());
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Lưu nháp và thoát" }));
    expect(putCalls(server)).toHaveLength(0);
  });

  it("(c) đóng dòng không làm mất lỗi nhập thô; Hủy thay đổi xóa đúng lỗi đó và Kiểm tra cấu trúc hợp lệ", async () => {
    const user = userEvent.setup();
    const sptParts = [wirePart([wireSection("sec-van", "VAN", [wireBlock("van-r1", { name: "Van dong 1", questionCount: 5 }), wireBlock("van-r2", { name: "Van dong 2", questionCount: 5 })])])];
    const { server } = boot({ kind: "SPT", parts: sptParts });
    openStructure("?section=sec-van");
    await user.click(await screen.findByRole("button", { name: /Sửa dòng Van dong 1/ }));
    const field = screen.getByRole("textbox", { name: "Số câu" });
    await user.clear(field);
    await user.type(field, "x");
    await user.click(screen.getByRole("button", { name: "Về danh sách" }));
    expect(screen.queryByRole("heading", { name: /Sửa dòng: Van dong 1/ })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(document.querySelector('[data-form-row-editor="van-r1"]')).toContainElement(document.activeElement as HTMLElement));
    expect(putCalls(server)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(await screen.findByTestId("structure-quick-check-result")).toHaveTextContent("Cấu trúc hợp lệ");
    expect(checkCalls(server)).toHaveLength(0);
  });

  it("(d) nháp trộn: mục MD và mục bật-mà-lùi-MD không bị luật MT/DS xét; mục DS bên cạnh có lỗi thì bị chặn", async () => {
    const user = userEvent.setup();
    const hoaUnfit = wireSection("sec-hoa", "HOA", [
      wireBlock("sec-hoa-single", { name: "sec-hoa don", questionCount: 13 }),
      wireBlock("sec-hoa-set", { name: "sec-hoa chum", questionType: "QUESTION_SET", setCount: 3, setSize: 3, questionCount: 9 }),
      wireBlock("sec-hoa-fill", { name: "sec-hoa dien", questionType: "SHORT_ANSWER", questionCount: 1 }),
    ]);
    const parts = qdaParts({ toanFirst: 0 });
    parts[0]!.sections = (parts[0]!.sections as Wire[]).map((section) => (section.id === "sec-hoa" ? hoaUnfit : section));
    const { server } = boot({ parts });
    openStructure("?section=sec-hoa");
    expect(await screen.findByTestId("full-form-notice")).toBeInTheDocument();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));

    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /VAN/ }));
    await user.click(await screen.findByRole("button", { name: /Sửa dòng Van dong 2/ }));
    const field = screen.getByRole("textbox", { name: /^Số câu/ });
    await user.clear(field);
    await user.type(field, "0");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(checkCalls(server)).toHaveLength(0);
    expect(putCalls(server)).toHaveLength(1);
  });

  it("(e) sửa đúng rồi lưu được; môn khác không đổi trong thân PUT", async () => {
    const user = userEvent.setup();
    const original = qdaParts({ vanR2: 0 });
    const { server } = boot({ parts: original });
    openStructure("?block=van-r2");
    const input = await screen.findByRole("textbox", { name: /^Số câu/ });
    await user.clear(input);
    await user.type(input, "6");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body);
    expect((sent["sec-van"] as { blocks: Array<{ id: string; questionCount: number }> }).blocks[1]).toMatchObject({ id: "van-r2", questionCount: 6 });
    const before = sectionsOf({ parts: original });
    for (const id of ["sec-ly", "sec-hoa", "sec-toan"]) expect(withoutMappingDefaults(sent[id])).toEqual(withoutMappingDefaults(before[id]));
  });

  it("(a) môn Hóa hợp lệ ở cạnh môn Lý lỗi: chỉ Lý bị chặn; sửa Lý thì lưu được", async () => {
    const user = userEvent.setup();
    const parts = qdaParts();
    const { server } = boot({ parts });
    openStructure("?section=sec-ly");
    const input = await lySetsInput();
    await user.clear(input);
    await user.type(input, "3");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.clear(input);
    await user.type(input, "2");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(withoutMappingDefaults(sectionsOf(putCalls(server)[0]!.body)["sec-hoa"])).toEqual(withoutMappingDefaults(wireScience("sec-hoa", "HOA")));
  });
});

/**
 * CR-2 AC-7, AC-9 (FTM AC 35 phần QDA, AC 59): luật MT thật của `matrix-blocking.ts` (giới hạn R-12) trên `StructureEditor` thật, với màn MT
 * thật.
 */
describe("Chặn lưu của MT thật: giới hạn R-12 (CR-2)", () => {
  const science = (id: string, subject: string, sets = 1) => wireQdaScience(id, subject, { sets });
  const typeSets = async (user: UserEvent, value: string) => {
    const input = await screen.findByRole("textbox", { name: SETS_INPUT_NAME });
    await user.clear(input);
    await user.type(input, value);
  };

  it("matrixLimitsBlockAllSaves: Lý 2 → 3 chùm rồi rời bước: Lưu và chuyển, Tiếp tục không gửi request, focus về ô Số chùm của Lý", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: [wirePart([science("sec-ly", "LY", 2), science("sec-hoa", "HOA")])] });
    openStructure("?section=sec-ly");
    await typeSets(user, "3");
    expect(await screen.findByText("Số chùm từ 1 đến 2")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Quay lại" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Còn lỗi cần sửa trước khi lưu hoặc kiểm tra.");
    expect(putCalls(server)).toHaveLength(0);
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.getByRole("textbox", { name: SETS_INPUT_NAME })).toHaveFocus());
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("lỗi của môn MT chưa mở chặn Lưu từ môn khác và đưa tới ô lỗi; sửa đúng thì lưu được, môn khác không đổi", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: [wirePart([science("sec-ly", "LY"), science("sec-hoa", "HOA")])] });
    openStructure("?section=sec-ly");
    await typeSets(user, "3");
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /HOA/ }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(window.location.search).toBe("?section=sec-ly"));
    expect(putCalls(server)).toHaveLength(0);
    await typeSets(user, "2");
    await waitFor(() => expect(screen.queryByText("Số chùm từ 1 đến 2")).toBeNull());
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body);
    expect((sent["sec-ly"]!.blocks as Wire[])[1]).toMatchObject({ setCount: 2, questionCount: 6 });
    expect((sent["sec-hoa"]!.blocks as Wire[])[1]).toMatchObject({ setCount: 1, questionCount: 3 });
  });

  it("legacyViolationOpensDefault: bài QDA cũ lưu nháp 3 chùm ở Lý mở MD kèm dòng chữ; Hóa vẫn lưu được; Lưu gửi Lý như đã nạp", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: [wirePart([science("sec-ly", "LY", 3), science("sec-hoa", "HOA")])] });
    openStructure("?section=sec-ly");
    expect(await screen.findByTestId("full-form-notice")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: SETS_INPUT_NAME })).toBeNull();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /HOA/ }));
    const fill = await screen.findByRole("textbox", { name: FILL_INPUT_NAME });
    await user.clear(fill);
    await user.type(fill, "2");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body);
    expect((sent["sec-ly"]!.blocks as Wire[])[1]).toMatchObject({ setCount: 3, setSize: 3, questionCount: 9 });
    expect((sent["sec-hoa"]!.blocks as Wire[])[2]).toMatchObject({ questionCount: 2 });
  });
});
