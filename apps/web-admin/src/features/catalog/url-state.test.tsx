import { act, renderHook } from "@testing-library/react";
import { useMemo, useSyncExternalStore } from "react";
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import { applyCatalogSelectionPatch, buildCatalogHref, useCatalogUrlState } from "./url-state";

const nav = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  const state = {
    pathname: "/catalog",
    search: "",
    setSearch(search: string) {
      state.search = search;
      for (const listener of listeners) listener();
    },
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    getSnapshot: () => state.search,
  };
  return state;
});

vi.mock("next/navigation", () => ({
  usePathname: () => nav.pathname,
  useSearchParams: () => {
    const search = useSyncExternalStore(nav.subscribe, nav.getSnapshot, nav.getSnapshot);
    return useMemo(() => new URLSearchParams(search), [search]);
  },
}));

describe("applyCatalogSelectionPatch", () => {
  it("đổi nhóm thì bỏ node, giữ tham số khác", () => {
    expect(applyCatalogSelectionPatch("group=A&node=n1&x=1", { group: "B" })).toBe("group=B&x=1");
  });

  it("chọn và bỏ chọn node giữ nhóm", () => {
    expect(applyCatalogSelectionPatch("group=A", { node: "n1" })).toBe("group=A&node=n1");
    expect(applyCatalogSelectionPatch("group=A&node=n1", { node: null })).toBe("group=A");
  });

  it("bỏ chọn nhóm xóa cả node", () => {
    expect(applyCatalogSelectionPatch("group=A&node=n1", { group: null })).toBe("");
  });

  it("buildCatalogHref không để dấu ? thừa", () => {
    expect(buildCatalogHref("/catalog", "")).toBe("/catalog");
    expect(buildCatalogHref("/catalog", "group=A")).toBe("/catalog?group=A");
  });
});

describe("useCatalogUrlState", () => {
  let replaceState: MockInstance<History["replaceState"]>;

  beforeEach(() => {
    nav.search = "";
    replaceState = vi.spyOn(window.history, "replaceState").mockImplementation(() => undefined);
  });

  afterEach(() => {
    window.history.replaceState(null, "", "/");
  });

  it("khởi tạo từ ?group= và ?node=", () => {
    nav.search = "group=KNOWLEDGE&node=n1";
    const { result } = renderHook(() => useCatalogUrlState());
    expect(result.current.group).toBe("KNOWLEDGE");
    expect(result.current.node).toBe("n1");
  });

  // VIS-1: vào trang đã có ?group= rồi bấm nhóm khác phải đổi ngay, không phụ thuộc việc
  // useSearchParams() có cập nhật hay không (điều hướng RSC của Next bị bỏ qua).
  it("đổi nhóm sau khi tải với ?group= mà useSearchParams không cập nhật", () => {
    nav.search = "group=SUBJECT";
    const { result } = renderHook(() => useCatalogUrlState());

    act(() => result.current.select({ group: "EXAM" }));

    expect(result.current.group).toBe("EXAM");
    expect(nav.search).toBe("group=SUBJECT");
    expect(replaceState).toHaveBeenCalledWith(null, "", "/catalog?group=EXAM");
  });

  it("chọn nút cây rồi bỏ chọn khi searchParams vẫn cũ", () => {
    nav.search = "group=KNOWLEDGE";
    const { result } = renderHook(() => useCatalogUrlState());

    act(() => result.current.select({ node: "n1" }));
    expect(result.current.node).toBe("n1");
    expect(replaceState).toHaveBeenLastCalledWith(
      null,
      "",
      "/catalog?group=KNOWLEDGE&node=n1",
    );

    act(() => result.current.select({ node: null }));
    expect(result.current.node).toBeNull();
    expect(replaceState).toHaveBeenLastCalledWith(null, "", "/catalog?group=KNOWLEDGE");
  });

  it("không ghi URL khi chọn lại đúng giá trị đang chọn", () => {
    nav.search = "group=SUBJECT";
    const { result } = renderHook(() => useCatalogUrlState());
    act(() => result.current.select({ group: "SUBJECT" }));
    expect(replaceState).not.toHaveBeenCalled();
  });

  it("theo searchParams đổi từ bên ngoài (back/forward của Next)", () => {
    nav.search = "group=SUBJECT&node=n1";
    const { result } = renderHook(() => useCatalogUrlState());

    act(() => nav.setSearch("group=EXAM"));

    expect(result.current.group).toBe("EXAM");
    expect(result.current.node).toBeNull();
  });

  it("theo popstate dù searchParams chưa kịp cập nhật", () => {
    nav.search = "group=SUBJECT";
    const { result } = renderHook(() => useCatalogUrlState());
    act(() => result.current.select({ group: "EXAM" }));

    replaceState.mockRestore();
    window.history.replaceState(null, "", "/catalog?group=SUBJECT");
    act(() => {
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    expect(result.current.group).toBe("SUBJECT");
  });

  it("bỏ qua tiếng vọng muộn của chính mình, không kéo lựa chọn lùi lại", () => {
    nav.search = "group=SUBJECT";
    const { result } = renderHook(() => useCatalogUrlState());

    act(() => result.current.select({ group: "EXAM" }));
    act(() => result.current.select({ group: "KNOWLEDGE" }));
    expect(result.current.group).toBe("KNOWLEDGE");

    act(() => nav.setSearch("group=EXAM"));
    expect(result.current.group).toBe("KNOWLEDGE");
    act(() => nav.setSearch("group=KNOWLEDGE"));
    expect(result.current.group).toBe("KNOWLEDGE");

    act(() => nav.setSearch("group=SUBJECT"));
    expect(result.current.group).toBe("SUBJECT");
  });
});
