import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  boot,
  checkCalls,
  installForms,
  openStructure,
  putCalls,
  qdaParts,
  sectionsOf,
  wireScience,
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
 * CR-1 AC-9 (FTM AC 59 phần lưu), trên `StructureEditor` thật với luật thử đăng ký qua `blocking.ts`: lỗi ở môn MT chưa mở chặn Lưu,
 * Lưu và chuyển, Kiểm tra, Tiếp tục; nhập thô sai khi bản nháp sạch chặn Kiểm tra, Tiếp tục; mục MD không bị xét; sửa đúng rồi lưu được.
 */
let restore: () => void;
beforeEach(() => {
  routerMock.push.mockReset();
  restore = installForms();
});
afterEach(() => restore());

const lyInput = () => screen.findByRole("textbox", { name: "MT LY cau don" });
/** Làm bản nháp bẩn ở môn Toán (MD) mà không đụng môn khác: bấm "Khai chỉ tiêu" đầu tiên. */
async function dirtyToan(user: UserEvent) {
  await user.click((await screen.findAllByRole("button", { name: "Khai chỉ tiêu" }))[0]!);
  expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
}

describe("Chặn lưu của MT/DS (FTM R-15)", () => {
  it("(a) lỗi ở môn MT chưa mở: Lưu không gửi PUT, đưa tới ô lỗi và đặt focus", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ lySingle: 13 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(lyInput()).resolves.toHaveFocus());
    expect(putCalls(server)).toHaveLength(0);
    expect(window.location.search).toBe("?section=sec-ly");
  });

  it("(a) Lưu và chuyển: hộp thoại ở lại, báo lỗi, không PUT, focus về ô lỗi", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ lySingle: 13 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /HOA/ }));
    const dialog = await screen.findByRole("dialog");
    // Hộp thoại vẫn gọi đúng mục sẽ rời (TOAN), không gọi môn của lỗi.
    expect(dialog).toHaveTextContent(/TOAN|Toán/);
    await user.click(within(dialog).getByRole("button", { name: "Lưu và chuyển" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Còn lỗi cần sửa trước khi lưu hoặc kiểm tra.");
    expect(putCalls(server)).toHaveLength(0);
    // "Ở lại": đóng hộp thoại rồi đưa focus tới ô lỗi của môn MT.
    await user.click(within(dialog).getByRole("button", { name: "Ở lại" }));
    await waitFor(() => expect(lyInput()).resolves.toHaveFocus());
    expect(window.location.search).toBe("?section=sec-ly");
  });

  it("(a) Kiểm tra toàn bài và Tiếp tục: không gửi PUT hay kiểm tra, focus về ô lỗi", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ lySingle: 13 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(lyInput()).resolves.toHaveFocus());
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    // Bản nháp còn bẩn: hộp thoại ba nút; chọn Ở lại rồi dùng Tiếp tục.
    await user.click(await screen.findByRole("button", { name: "Ở lại" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(lyInput()).resolves.toHaveFocus());
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
  });

  it("(b) nhập thô sai khi bản nháp sạch: Kiểm tra, Tiếp tục không gửi request, focus về ô", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-ly");
    const input = await lyInput();
    await user.clear(input);
    await user.type(input, "abc");
    expect(await screen.findByText("Có thay đổi chưa lưu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(input).toHaveFocus());
    expect(putCalls(server)).toHaveLength(0);
    expect(checkCalls(server)).toHaveLength(0);
    expect(routerMock.push).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
  });

  it("(c) đóng dòng không làm mất lỗi nhập thô; Hủy thay đổi xóa đúng lỗi đó và Kiểm tra chạy lại được", async () => {
    const user = userEvent.setup();
    const { server } = boot();
    openStructure("?section=sec-van");
    await user.click(await screen.findByRole("button", { name: "Sua Van dong 1" }));
    const field = screen.getByRole("textbox", { name: "DS Van dong 1 so luong" });
    await user.clear(field);
    await user.type(field, "x");
    await user.click(screen.getByRole("button", { name: "Dong Van dong 1" }));
    expect(screen.queryByRole("textbox", { name: "DS Van dong 1 so luong" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(document.querySelector('[data-form-row="van-r1"]')).toContainElement(document.activeElement as HTMLElement));
    expect(checkCalls(server)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Hủy thay đổi" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Hủy thay đổi" }));
    await waitFor(() => expect(screen.queryByText("Có thay đổi chưa lưu")).toBeNull());
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    await waitFor(() => expect(checkCalls(server)).toHaveLength(1));
  });

  it("(d) nháp trộn: mục MD có dữ liệu mà luật MT sẽ coi là sai không bị chặn; mục DS bên cạnh có lỗi thì bị chặn", async () => {
    const user = userEvent.setup();
    const { server } = boot({ parts: qdaParts({ toanFirst: 13 }) });
    openStructure("?section=sec-toan");
    await dirtyToan(user);
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));

    // Một lỗi nhập thô ở DS bên cạnh chặn lưu, dù mục Toán (MD) vẫn mang 13.
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /VAN/ }));
    await user.click(await screen.findByRole("button", { name: "Sua Van dong 2" }));
    const field = screen.getByRole("textbox", { name: "DS Van dong 2 so luong" });
    await user.clear(field);
    await user.type(field, "y");
    await user.click(screen.getByRole("button", { name: "Kiểm tra toàn bài" }));
    expect(checkCalls(server)).toHaveLength(0);
    expect(putCalls(server)).toHaveLength(1);
  });

  it("(e) sửa đúng rồi lưu được; môn khác không đổi trong thân PUT", async () => {
    const user = userEvent.setup();
    const original = qdaParts({ lySingle: 13 });
    const { server } = boot({ parts: original });
    openStructure("?section=sec-ly");
    const input = await lyInput();
    await user.clear(input);
    await user.type(input, "14");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const sent = sectionsOf(putCalls(server)[0]!.body);
    expect((sent["sec-ly"] as { blocks: Array<{ id: string; questionCount: number }> }).blocks[0]).toMatchObject({ id: "sec-ly-single", questionCount: 14 });
    const before = sectionsOf({ parts: original });
    for (const id of ["sec-hoa", "sec-toan", "sec-van"]) expect(withoutMappingDefaults(sent[id])).toEqual(withoutMappingDefaults(before[id]));
  });

  it("(a) môn Hóa hợp lệ ở cạnh môn Lý lỗi: chỉ Lý bị chặn; sửa Lý thì lưu được", async () => {
    const user = userEvent.setup();
    const parts = qdaParts();
    const { server } = boot({ parts });
    openStructure("?section=sec-ly");
    const input = await lyInput();
    await user.clear(input);
    await user.type(input, "13");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(putCalls(server)).toHaveLength(0);
    await user.clear(input);
    await user.type(input, "12");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    expect(withoutMappingDefaults(sectionsOf(putCalls(server)[0]!.body)["sec-hoa"])).toEqual(withoutMappingDefaults(wireScience("sec-hoa", "HOA")));
  });
});
