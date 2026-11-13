import { beforeEach, describe, expect, it, vi } from "vitest";
import { revalidateAppearance } from "./actions";

const mocks = vi.hoisted(() => ({
  cookies: vi.fn(),
  revalidatePath: vi.fn(),
  revalidateTag: vi.fn(),
}));

vi.mock("next/headers", () => ({
  cookies: mocks.cookies,
}));

vi.mock("next/cache", () => ({
  revalidatePath: mocks.revalidatePath,
  revalidateTag: mocks.revalidateTag,
}));

function makeJar(entries: Record<string, string>) {
  return {
    get: (name: string) =>
      Object.hasOwn(entries, name) ? { name, value: entries[name]! } : undefined,
  };
}

beforeEach(() => {
  mocks.cookies.mockReset();
  mocks.revalidatePath.mockReset();
  mocks.revalidateTag.mockReset();
});

describe("revalidateAppearance", () => {
  it("bỏ qua revalidation khi không có cookie phiên (kể cả khi chỉ có cookie xsrf)", async () => {
    mocks.cookies.mockResolvedValueOnce(makeJar({}));
    await revalidateAppearance();

    mocks.cookies.mockResolvedValueOnce(makeJar({ thi360_xsrf: "xsrf-only" }));
    await revalidateAppearance();

    mocks.cookies.mockResolvedValueOnce(makeJar({ thi360_at: "   " }));
    await revalidateAppearance();

    expect(mocks.revalidateTag).not.toHaveBeenCalled();
    expect(mocks.revalidatePath).not.toHaveBeenCalled();
  });

  it("gọi revalidateTag và revalidatePath khi có cookie phiên thi360_at hoặc thi360_ctx", async () => {
    mocks.cookies.mockResolvedValueOnce(makeJar({ thi360_at: "access-jwt" }));
    await revalidateAppearance();

    mocks.cookies.mockResolvedValueOnce(makeJar({ thi360_ctx: "signed-ctx" }));
    await revalidateAppearance();

    expect(mocks.revalidateTag).toHaveBeenCalledTimes(2);
    expect(mocks.revalidateTag).toHaveBeenNthCalledWith(1, "appearance", "max");
    expect(mocks.revalidatePath).toHaveBeenCalledTimes(2);
    expect(mocks.revalidatePath).toHaveBeenNthCalledWith(1, "/", "layout");
  });
});
