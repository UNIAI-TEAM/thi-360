import { describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";
import { FULL_DETAIL } from "@/test/storefront-fixtures";
import ExamPage, { generateMetadata } from "./page";

const mocks = vi.hoisted(() => ({ lookup: vi.fn(), notFound: vi.fn() }));

vi.mock("@/features/storefront/server-api", () => ({ loadProductOnServer: mocks.lookup }));
vi.mock("next/navigation", () => ({
  notFound: () => {
    mocks.notFound();
    throw new Error("NEXT_NOT_FOUND");
  },
}));
vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: string) => (key: string) => {
    const block = namespace.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], viMessages);
    return String(key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], block));
  },
}));

const params = (code: string) => ({ params: Promise.resolve({ code }) });

describe("/exams/[code]", () => {
  it("metadata riêng từng sản phẩm: tiêu đề là tên bài, mô tả là tóm tắt (AC-17)", async () => {
    mocks.lookup.mockResolvedValueOnce({ status: "ok", product: FULL_DETAIL });

    await expect(generateMetadata(params("HSA-FULL-01"))).resolves.toEqual({
      title: FULL_DETAIL.name,
      description: FULL_DETAIL.summary,
    });
    expect(mocks.lookup).toHaveBeenCalledWith("HSA-FULL-01");
  });

  it("mã ngừng bán và mã không có có tiêu đề riêng", async () => {
    mocks.lookup.mockResolvedValueOnce({ status: "gone", product: { code: "X", name: "X" } });
    await expect(generateMetadata(params("X"))).resolves.toEqual({ title: "Bài thi đã ngừng bán" });

    mocks.lookup.mockResolvedValueOnce({ status: "notFound" });
    await expect(generateMetadata(params("NOPE"))).resolves.toEqual({ title: "Không tìm thấy bài thi" });
  });

  it("mã không có: gọi notFound() để trả HTTP 404 (AC-9)", async () => {
    mocks.lookup.mockResolvedValue({ status: "notFound" });

    await expect(ExamPage(params("NOPE"))).rejects.toThrow("NEXT_NOT_FOUND");
    expect(mocks.notFound).toHaveBeenCalledTimes(1);
  });

  it("mã ngừng bán: dựng trang trạng thái, không gọi notFound() (mã HTTP 200, plan B8)", async () => {
    mocks.lookup.mockResolvedValue({ status: "gone", product: { code: "QDA-FULL-01", name: "QDA FULL 01" } });

    await expect(ExamPage(params("QDA-FULL-01"))).resolves.toBeTruthy();
    expect(mocks.notFound).not.toHaveBeenCalled();
  });

  it("mã trên URL được giải mã trước khi tra", async () => {
    mocks.lookup.mockResolvedValueOnce({ status: "notFound" });

    await generateMetadata(params("A%20B"));

    expect(mocks.lookup).toHaveBeenCalledWith("A B");
  });
});
