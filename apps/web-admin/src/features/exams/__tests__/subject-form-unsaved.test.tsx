import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { examTabHref } from "../editor-tabs";
import { EXAM_ID, examDetailWire } from "./fake-exams-server";
import {
  boot,
  checkCalls,
  installForms,
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

/**
 * CR-1 AC-12 (FTM AC 59 phần thay đổi chưa lưu), trên `StructureEditor` thật: bản nháp sạch chỉ có nhập thô lỗi mà chuyển tab, tải lại,
 * chuyển mục, đổi mẫu đều phải hỏi; Ở lại giữ chữ đã gõ; Hủy thay đổi và nạp mẫu đã xác nhận xóa nhập thô và ghim lại biểu mẫu; dữ liệu
 * nền tải lại không thay bản nháp đang sửa; giá trị chưa hợp lệ không vào thân `PUT`.
 */
let restore: () => void;
beforeEach(() => {
  routerMock.push.mockReset();
  restore = installForms();
});
afterEach(() => restore());

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

/** Mở môn Lý (MT thử) và gõ chữ sai: bản nháp vẫn sạch, chỉ có nhập thô. */
async function typeBadValue(user: UserEvent) {
  const input = await screen.findByRole("textbox", { name: "MT LY cau don" });
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
    await screen.findByRole("textbox", { name: "MT LY cau don" });
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

  it("chuyển mục (môn khác) hỏi bằng hộp thoại ba nút; Ở lại giữ chữ đã gõ", async () => {
    const user = userEvent.setup();
    boot();
    openStructure("?section=sec-ly");
    const input = await typeBadValue(user);
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "Lưu và chuyển" })).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    expect(input).toHaveValue("abc");
    expect(window.location.search).toBe("?section=sec-ly");
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

  it("Hủy thay đổi xóa nhập thô, ghim lại biểu mẫu; sau đó Kiểm tra không còn lỗi từ id cũ", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    await typeBadValue(user);
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    expect(checkCalls(server)).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    expect(await screen.findByRole("textbox", { name: "MT LY cau don" })).toHaveValue("14");
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(checkCalls(server)).toHaveLength(1));
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
    // Môn của mẫu mới được ghim lại: vẫn MT (cờ bật), không còn lỗi từ id cũ nên Kiểm tra chạy.
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /LY/ }));
    expect(await screen.findByRole("textbox", { name: "MT LY cau don" })).toHaveValue("14");
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(checkCalls(server)).toHaveLength(1));
  });

  it("dữ liệu nền tải lại không thay bản nháp đang sửa khi còn nhập thô; bản nháp sạch thì nhận dữ liệu mới", async () => {
    const user = userEvent.setup();
    const { server, state } = boot();
    const view = openStructure("?section=sec-ly");
    await screen.findByRole("textbox", { name: "MT LY cau don" });
    state.parts = [{ ...(state.parts[0] as object), name: "Part tu may chu" }];
    await act(() => view.client.invalidateQueries());
    expect(await screen.findAllByText(/Part tu may chu/)).not.toHaveLength(0);

    // Còn nhập thô: lần tải lại kế tiếp không thay bản nháp.
    await typeBadValue(user);
    state.parts = [{ ...(state.parts[0] as object), name: "Part lan hai" }];
    const loadsBefore = server.calls("GET", STRUCTURE).length;
    await act(() => view.client.invalidateQueries());
    await waitFor(() => expect(server.calls("GET", STRUCTURE).length).toBeGreaterThan(loadsBefore));
    expect(screen.queryAllByText(/Part lan hai/)).toHaveLength(0);
    expect(screen.getByRole("textbox", { name: "MT LY cau don" })).toHaveValue("abc");
  });

  it("giá trị chưa hợp lệ không bao giờ vào thân PUT: sửa đúng thì PUT mang số, không mang chữ đã gõ", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    const input = await typeBadValue(user);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.clear(input);
    await user.type(input, "12");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(JSON.stringify(putCalls(server)[0]!.body)).not.toContain("abc");
    const ly = sectionsOf(putCalls(server)[0]!.body)["sec-ly"] as { blocks: Array<{ id: string; questionCount: number }> };
    expect(ly.blocks.find((block) => block.id === "sec-ly-single")?.questionCount).toBe(12);
    expect(JSON.stringify(sectionsOf(putCalls(server)[0]!.body)["sec-hoa"])).not.toContain("abc");
  });
});
