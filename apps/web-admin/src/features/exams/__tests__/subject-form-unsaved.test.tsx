import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { examTabHref } from "../editor-tabs";
import { EXAM_ID, examDetailWire } from "./fake-exams-server";
import {
  boot,
  checkCalls,
  openStructure,
  putCalls,
  sectionsOf,
  STRUCTURE,
  wirePart,
  wireScience,
} from "./subject-form-harness";

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

const template = (code: string, year: number) => ({
  code,
  kindCode: "QDA",
  scope: "FULL",
  timing: "PER_PART",
  label: `QDA ${year}`,
  active: true,
  isDefault: year === 2026,
  structureYear: year,
  matrixYear: year,
  buildMode: "STANDARD",
  publishable: true,
  fixedCounts: false,
  structure: { parts: [] },
  parts: [],
});
const TEMPLATES = [template("QDA_2026", 2026), template("QDA_2025", 2025)];

const FILL_INPUT_NAME = /^Số câu của .*(Điền đáp án|Trả lời ngắn|dien)/i;
const fillInput = () => screen.findByRole("textbox", { name: FILL_INPUT_NAME });

/** Mở môn Lý (MT thật) và gõ chữ sai vào Điền đáp án: bản nháp vẫn sạch, chỉ có nhập thô. */
async function typeBadValue(user: UserEvent) {
  const input = await fillInput();
  await user.clear(input);
  await user.type(input, "abc");
  expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
  return input;
}

describe("Thay đổi chưa lưu gồm nhập thô (FTM R-15)", () => {
  it("tải lại hoặc đóng tab (beforeunload) hỏi khi chỉ còn nhập thô; bản nháp sạch không hỏi", async () => {
    const user = userEvent.setup();
    boot();
    openStructure("?section=sec-ly");
    await fillInput();
    const clean = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);
    await typeBadValue(user);
    const dirty = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(dirty);
    expect(dirty.defaultPrevented).toBe(true);
  });

  it("chuyển tab của bài hỏi; Ở lại giữ chữ đã gõ", async () => {
    const user = userEvent.setup();
    boot({ examOverrides: { id: EXAM_ID } });
    openStructure("?section=sec-ly");
    const input = await typeBadValue(user);
    const link = document.createElement("a");
    link.href = examTabHref(EXAM_ID, "review");
    link.textContent = "Kiem tra";
    document.body.append(link);
    await user.click(link);
    const dialog = await screen.findByRole("dialog");
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(input).toHaveValue("abc");
    link.remove();
  });

  it("chuyển mục (môn khác) trong bước 2 không hỏi và giữ nguyên chữ đã gõ khi quay lại", async () => {
    const user = userEvent.setup();
    boot();
    openStructure("?section=sec-ly");
    await typeBadValue(user);
    const nav = await screen.findByRole("navigation", { name: "Cấu trúc" });
    await user.click(within(nav).getByRole("button", { name: /TOAN/ }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(window.location.search).toBe("?section=sec-toan");
    await user.click(within(nav).getByRole("button", { name: /LY/ }));
    expect(await fillInput()).toHaveValue("abc");
  });

  it("đổi mẫu ở StructureVersionPicker hỏi với ghi chú mất thay đổi chưa lưu; Hủy giữ chữ đã gõ", async () => {
    const user = userEvent.setup();
    boot({ templates: TEMPLATES, examOverrides: { templateCode: "QDA_2026", structureYear: 2026, matrixYear: 2026 } });
    openStructure("?section=sec-ly");
    const input = await typeBadValue(user);
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click(await screen.findByRole("option", { name: /QDA 2025/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Thay đổi chưa lưu cũng mất\./)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Hủy" }));
    expect(input).toHaveValue("abc");
  });

  it("Hủy thay đổi xóa nhập thô, ghim lại biểu mẫu; sau đó Kiểm tra cấu trúc hợp lệ", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    await typeBadValue(user);
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(await screen.findByTestId("structure-quick-check-result")).not.toHaveTextContent("Cấu trúc hợp lệ");
    expect(checkCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    expect(await fillInput()).toHaveValue("1");
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(await screen.findByTestId("structure-quick-check-result")).toHaveTextContent("Cấu trúc hợp lệ");
    expect(checkCalls(server)).toHaveLength(0);
  });

  it("nạp mẫu đã xác nhận xóa nhập thô và ghim lại biểu mẫu theo cấu trúc mới", async () => {
    const user = userEvent.setup();
    const { server, state } = boot({ templates: TEMPLATES, examOverrides: { templateCode: "QDA_2026", structureYear: 2026, matrixYear: 2026 } });
    const fresh = [wirePart([wireScience("sec-new-ly", "LY")])];
    server.on("POST", `${STRUCTURE}/template`, () => {
      state.parts = fresh;
      state.revision += 1;
      return { status: 200, body: examDetailWire({ kind: "QDA", revision: state.revision, templateCode: "QDA_2025", parts: fresh as never } as never) };
    });
    openStructure("?section=sec-ly");
    await typeBadValue(user);
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click(await screen.findByRole("option", { name: /QDA 2025/ }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /LY/ }));
    expect(await fillInput()).toHaveValue("1");
    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    expect(await screen.findByTestId("structure-quick-check-result")).toHaveTextContent("Cấu trúc hợp lệ");
    expect(checkCalls(server)).toHaveLength(0);
  });

  it("dữ liệu nền tải lại không thay bản nháp đang sửa khi còn nhập thô; bản nháp sạch thì nhận dữ liệu mới", async () => {
    const user = userEvent.setup();
    const { server, state } = boot();
    const view = openStructure("?section=sec-ly");
    await fillInput();
    state.parts = [{ ...(state.parts[0] as object), name: "Part tu may chu" }];
    await act(() => view.client.invalidateQueries());
    expect(await screen.findAllByText(/Part tu may chu/)).not.toHaveLength(0);

    await typeBadValue(user);
    state.parts = [{ ...(state.parts[0] as object), name: "Part lan hai" }];
    const loadsBefore = server.calls("GET", STRUCTURE).length;
    await act(() => view.client.invalidateQueries());
    await waitFor(() => expect(server.calls("GET", STRUCTURE).length).toBeGreaterThan(loadsBefore));
    expect(screen.queryAllByText(/Part lan hai/)).toHaveLength(0);
    expect(screen.getByRole("textbox", { name: FILL_INPUT_NAME })).toHaveValue("abc");
  });

  it("giá trị chưa hợp lệ không bao giờ vào thân PUT: sửa đúng thì PUT mang số, không mang chữ đã gõ", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    const input = await typeBadValue(user);
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.clear(input);
    await user.type(input, "2");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(JSON.stringify(putCalls(server)[0]!.body)).not.toContain("abc");
    const ly = sectionsOf(putCalls(server)[0]!.body)["sec-ly"] as { blocks: Array<{ id: string; questionCount: number }> };
    expect(ly.blocks.find((block) => block.id === "sec-ly-fill")?.questionCount).toBe(2);
    expect(JSON.stringify(sectionsOf(putCalls(server)[0]!.body)["sec-hoa"])).not.toContain("abc");
  });
});
