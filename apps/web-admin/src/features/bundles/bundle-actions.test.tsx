import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, type FakeResult, ok, withRealApi } from "@/test/fake-api";

import {
  BUNDLE_ID_1,
  EXAM_KIND_OPTIONS,
  makeBundleDetail,
  makeBundleListPage,
  makeBundleSummary,
  makeCandidate,
  renderBundlesUi,
  resetBundleNavigation,
} from "./test/bundle-fixtures";
import { BundleEditPage } from "./components/bundle-form-page";
import { BundleListView } from "./components/bundle-list-view";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./test/bundle-fixtures");
  return fixtures.createBundleNextNavMock();
});
withRealApi();

describe("Bundle Publish & Discontinue Actions (AC-7)", () => {
  beforeEach(() => {
    fakeApi.reset();
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
  });

  it("công bố thành công từ trang chi tiết và giữ nguyên trạng thái khi máy chủ từ chối (hiện lỗi trong dialog)", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let current = makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 2 });
    fakeApi.get("/api/v1/admin/bundles/{id}", () => ok(current));

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        return fail(422, { code: "LISTING_PRICE_BELOW_MIN" });
      }
      current = makeBundleDetail({
        id: BUNDLE_ID_1,
        status: "PUBLISHED",
        saleState: "ON_SALE",
        servingVersion: 1,
        revision: 3,
      });
      return ok(current);
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });

    // Open publish confirm dialog
    await user.click(screen.getByRole("button", { name: "Công bố" }));
    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    expect(dialog).toHaveTextContent("Bộ đề SP2E khối A00");

    // 1st attempt rejected by server -> error rendered inside dialog, status remains DRAFT
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("Giá bộ đề thấp hơn mức tối thiểu cho phép của cổng thanh toán.");
    expect(screen.getByText("Nháp")).toBeInTheDocument();

    // 2nd attempt succeeds -> dialog closes, status updates to Đang bán
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(screen.getByText("Đang bán")).toBeInTheDocument();
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 2 }]);
  });

  it("ngừng bán từ danh sách gửi expectedRevision và hiện lỗi trong ConfirmDialog nếu máy chủ từ chối", async () => {
    resetBundleNavigation("/bundles", "");
    let listRow = makeBundleSummary({
      id: BUNDLE_ID_1,
      status: "PUBLISHED",
      saleState: "ON_SALE",
      revision: 5,
    });
    fakeApi.get("/api/v1/admin/bundles", () => ok(makeBundleListPage([listRow])));

    let discCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/discontinue", (call) => {
      discCalls += 1;
      sentBodies.push(call.options?.body);
      if (discCalls === 1) {
        return fail(409, { code: "BUNDLE_ALREADY_DISCONTINUED" });
      }
      listRow = makeBundleSummary({
        id: BUNDLE_ID_1,
        status: "DISCONTINUED",
        saleState: "DISCONTINUED",
        revision: 6,
      });
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DISCONTINUED", revision: 6 }));
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    const listTable = screen.getByRole("table", { name: "Danh sách bộ đề" });
    await user.click(
      screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }),
    );
    await user.click(await screen.findByRole("menuitem", { name: "Ngừng bán" }));

    const dialog = await screen.findByRole("dialog", { name: "Ngừng bán bộ đề?" });
    expect(dialog).toHaveTextContent("Bộ đề SP2E khối A00");

    // Server error keeps status PUBLISHED & renders error in dialog
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề đã ngừng bán, không thể chỉnh sửa hoặc công bố lại.",
    );
    expect(within(listTable).getByText("Đang bán")).toBeInTheDocument();

    // Retry succeeds -> closes & status updates
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(await within(listTable).findByText("Ngừng bán")).toBeInTheDocument();
    expect(sentBodies).toEqual([{ expectedRevision: 5 }, { expectedRevision: 5 }]);
  });

  it("R1-9/10: công bố gặp 412 tự nạp lại chi tiết để cập nhật expectedRevision khi thử lại", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let detailCalls = 0;
    let current = makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 2 });
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      return ok(current);
    });

    const sentBodies: unknown[] = [];
    let publishCalls = 0;
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        current = makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 5 });
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      current = makeBundleDetail({
        id: BUNDLE_ID_1,
        status: "PUBLISHED",
        saleState: "ON_SALE",
        servingVersion: 1,
        revision: 6,
      });
      return ok(current);
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    await user.click(screen.getByRole("button", { name: "Công bố" }));
    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    await waitFor(() => {
      expect(detailCalls).toBeGreaterThanOrEqual(2);
    });

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 5 }]);
    expect(screen.getByText("Đang bán")).toBeInTheDocument();
  });

  it("R1-9/11: ngừng bán gặp 412 tự nạp lại để cập nhật expectedRevision khi thử lại", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let detailCalls = 0;
    let current = makeBundleDetail({
      id: BUNDLE_ID_1,
      status: "PUBLISHED",
      saleState: "ON_SALE",
      revision: 5,
    });
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      return ok(current);
    });

    const sentBodies: unknown[] = [];
    let discCalls = 0;
    fakeApi.post("/api/v1/admin/bundles/{id}/discontinue", (call) => {
      discCalls += 1;
      sentBodies.push(call.options?.body);
      if (discCalls === 1) {
        current = makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        });
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      current = makeBundleDetail({
        id: BUNDLE_ID_1,
        status: "DISCONTINUED",
        saleState: "DISCONTINUED",
        revision: 9,
      });
      return ok(current);
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    await user.click(screen.getByRole("button", { name: "Ngừng bán" }));
    const dialog = await screen.findByRole("dialog", { name: "Ngừng bán bộ đề?" });

    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    await waitFor(() => {
      expect(detailCalls).toBeGreaterThanOrEqual(2);
    });

    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 5 }, { expectedRevision: 8 }]);
    expect(screen.getByText("Ngừng bán")).toBeInTheDocument();
  });

  it("R1-28: khi biểu mẫu có thay đổi chưa lưu, nút Công bố bị khóa kèm chú thích và mở lại sau khi lưu", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let current = makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 2 });
    fakeApi.get("/api/v1/admin/bundles/{id}", () => ok(current));
    fakeApi.put("/api/v1/admin/bundles/{id}", () => {
      current = makeBundleDetail({
        id: BUNDLE_ID_1,
        name: "Tên mới đã sửa",
        status: "DRAFT",
        revision: 3,
      });
      return ok(current);
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    const publishBtn = await screen.findByRole("button", { name: "Công bố" });
    expect(publishBtn).not.toBeDisabled();

    const nameInput = screen.getByLabelText(/Tên bộ/);
    await user.clear(nameInput);
    await user.type(nameInput, "Tên mới đã sửa");

    expect(publishBtn).toBeDisabled();
    expect(screen.getByText("Lưu thay đổi trước khi công bố")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Công bố" })).not.toBeDisabled();
    });
    expect(screen.queryByText("Lưu thay đổi trước khi công bố")).toBeNull();
  });

  it("R2-6: công bố từ danh sách gặp 412 (còn trên trang) tự cập nhật expectedRevision và thành công khi thử lại", async () => {
    resetBundleNavigation("/bundles", "");
    let listRow = makeBundleSummary({
      id: BUNDLE_ID_1,
      status: "DRAFT",
      saleState: null,
      revision: 2,
    });
    fakeApi.get("/api/v1/admin/bundles", () => ok(makeBundleListPage([listRow])));
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 5 })),
    );

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        listRow = makeBundleSummary({
          id: BUNDLE_ID_1,
          status: "DRAFT",
          saleState: null,
          revision: 5,
        });
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      listRow = makeBundleSummary({
        id: BUNDLE_ID_1,
        status: "PUBLISHED",
        saleState: "ON_SALE",
        revision: 6,
      });
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 6,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );

    await waitFor(() => {
      expect(sentBodies).toHaveLength(1);
    });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 5 }]);
    const listTable = screen.getByRole("table", { name: "Danh sách bộ đề" });
    expect(await within(listTable).findByText("Đang bán")).toBeInTheDocument();
  });

  it("R2-6: ngừng bán từ danh sách gặp 412 (còn trên trang) tự cập nhật expectedRevision và thành công khi thử lại", async () => {
    resetBundleNavigation("/bundles", "");
    let listRow = makeBundleSummary({
      id: BUNDLE_ID_1,
      status: "PUBLISHED",
      saleState: "ON_SALE",
      revision: 5,
    });
    fakeApi.get("/api/v1/admin/bundles", () => ok(makeBundleListPage([listRow])));
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        }),
      ),
    );

    let discCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/discontinue", (call) => {
      discCalls += 1;
      sentBodies.push(call.options?.body);
      if (discCalls === 1) {
        listRow = makeBundleSummary({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        });
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      listRow = makeBundleSummary({
        id: BUNDLE_ID_1,
        status: "DISCONTINUED",
        saleState: "DISCONTINUED",
        revision: 9,
      });
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "DISCONTINUED",
          saleState: "DISCONTINUED",
          revision: 9,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Ngừng bán" }));

    const dialog = await screen.findByRole("dialog", { name: "Ngừng bán bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );

    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 5 }, { expectedRevision: 8 }]);
    const listTable = screen.getByRole("table", { name: "Danh sách bộ đề" });
    expect(await within(listTable).findByText("Ngừng bán")).toBeInTheDocument();
  });

  it("R2-7/8: công bố từ danh sách gặp 412 khi sửa đồng thời đẩy bộ đề khỏi bộ lọc (list refetch trả rỗng) vẫn lấy revision qua getBundle và thử lại thành công", async () => {
    resetBundleNavigation("/bundles", "status=DRAFT");
    let listCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      listCalls += 1;
      if (listCalls === 1) {
        return ok(
          makeBundleListPage([
            makeBundleSummary({
              id: BUNDLE_ID_1,
              status: "DRAFT",
              saleState: null,
              revision: 2,
            }),
          ]),
        );
      }
      return ok(makeBundleListPage([], { totalItems: 0, totalPages: 0 }));
    });

    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 7 }));
    });

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    await waitFor(() => {
      expect(detailCalls).toBe(1);
      expect(listCalls).toBeGreaterThanOrEqual(2);
    });

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 7 }]);
  });

  it("R2-7/8: ngừng bán từ danh sách gặp 412 khi sửa đồng thời đẩy bộ đề khỏi bộ lọc (list refetch trả rỗng) vẫn lấy revision qua getBundle và thử lại thành công", async () => {
    resetBundleNavigation("/bundles", "q=BO-SP2E-001");
    let listCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      listCalls += 1;
      if (listCalls === 1) {
        return ok(
          makeBundleListPage([
            makeBundleSummary({
              id: BUNDLE_ID_1,
              status: "PUBLISHED",
              saleState: "ON_SALE",
              revision: 5,
            }),
          ]),
        );
      }
      return ok(makeBundleListPage([], { totalItems: 0, totalPages: 0 }));
    });

    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          code: "BO-SP2E-RENAMED",
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 9,
        }),
      );
    });

    let discCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/discontinue", (call) => {
      discCalls += 1;
      sentBodies.push(call.options?.body);
      if (discCalls === 1) {
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          code: "BO-SP2E-RENAMED",
          status: "DISCONTINUED",
          saleState: "DISCONTINUED",
          revision: 10,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Ngừng bán" }));

    const dialog = await screen.findByRole("dialog", { name: "Ngừng bán bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    await waitFor(() => {
      expect(detailCalls).toBe(1);
      expect(listCalls).toBeGreaterThanOrEqual(2);
    });

    await user.click(within(dialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 5 }, { expectedRevision: 9 }]);
  });

  it("R3-1/2/4: công bố từ danh sách gặp 412 + list rỗng + getBundle 500 khóa nút xác nhận khi đang tải và hiện lỗi nạp lại (không hiện câu đã nạp lại) tới khi Thử lại thành công", async () => {
    resetBundleNavigation("/bundles", "status=DRAFT");
    let listCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      listCalls += 1;
      if (listCalls === 1) {
        return ok(
          makeBundleListPage([
            makeBundleSummary({
              id: BUNDLE_ID_1,
              status: "DRAFT",
              saleState: null,
              revision: 2,
            }),
          ]),
        );
      }
      return ok(makeBundleListPage([], { totalItems: 0, totalPages: 0 }));
    });

    let resolveFirstGet!: (result: FakeResult) => void;
    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      if (detailCalls === 1) {
        return new Promise<FakeResult>((resolve) => {
          resolveFirstGet = resolve;
        });
      }
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 7 }));
    });

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));

    // While getBundle is in flight (R3-4): Confirm is disabled & loading, status shows refreshing, no false "reloaded" claim
    const refreshingStatus = await within(dialog).findByRole("status");
    expect(refreshingStatus).toHaveTextContent("Đang nạp lại bản mới nhất");
    const confirmBtn = within(dialog).getByRole("button", { name: "Công bố" });
    expect(confirmBtn).toBeDisabled();
    expect(confirmBtn).toHaveAttribute("aria-busy", "true");
    expect(within(dialog).queryByText(/Bản mới nhất đã được nạp lại/)).toBeNull();

    // Resolve getBundle with 500 -> refresh-failed alert appears, Confirm stays disabled, no false "reloaded" copy (R3-1/R3-2)
    await act(async () => {
      resolveFirstGet(fail(500, { code: "INTERNAL_ERROR" }));
    });

    const errorAlert = await within(dialog).findByRole("alert");
    expect(errorAlert).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất",
    );
    expect(within(dialog).queryByText(/Bản mới nhất đã được nạp lại/)).toBeNull();
    expect(within(dialog).getByRole("button", { name: "Công bố" })).toBeDisabled();

    // Click Retry inside the dialog -> getBundle returns revision 7 -> alert clears to reloaded state, Confirm re-enables
    await user.click(within(errorAlert).getByRole("button", { name: "Thử lại" }));

    await waitFor(() => {
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
      );
    });
    expect(within(dialog).getByRole("button", { name: "Công bố" })).not.toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 7 }]);
  });

  it("R3-1/2: ngừng bán từ danh sách và công bố từ trang chi tiết khi 412 + refetch lỗi hiện cảnh báo nạp lại lỗi và khôi phục khi bấm Thử lại", async () => {
    // 1. Discontinue from BundleListView: 412 + list empty + getBundle 500 -> Retry succeeds with revision 9
    resetBundleNavigation("/bundles", "q=BO-SP2E-001");
    let listCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      listCalls += 1;
      if (listCalls === 1) {
        return ok(
          makeBundleListPage([
            makeBundleSummary({
              id: BUNDLE_ID_1,
              status: "PUBLISHED",
              saleState: "ON_SALE",
              revision: 5,
            }),
          ]),
        );
      }
      return ok(makeBundleListPage([], { totalItems: 0, totalPages: 0 }));
    });
    let detailCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      detailCalls += 1;
      if (detailCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 9,
        }),
      );
    });
    let discCalls = 0;
    const discBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/discontinue", (call) => {
      discCalls += 1;
      discBodies.push(call.options?.body);
      if (discCalls === 1) return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "DISCONTINUED",
          saleState: "DISCONTINUED",
          revision: 10,
        }),
      );
    });

    const user = userEvent.setup();
    const { unmount } = renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Ngừng bán" }));

    const listDialog = await screen.findByRole("dialog", { name: "Ngừng bán bộ đề?" });
    await user.click(within(listDialog).getByRole("button", { name: "Ngừng bán" }));

    const listAlert = await within(listDialog).findByRole("alert");
    expect(listAlert).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất",
    );
    expect(within(listDialog).getByRole("button", { name: "Ngừng bán" })).toBeDisabled();

    await user.click(within(listAlert).getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(within(listDialog).getByRole("alert")).toHaveTextContent(
        "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
      );
    });
    await user.click(within(listDialog).getByRole("button", { name: "Ngừng bán" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(discBodies).toEqual([{ expectedRevision: 5 }, { expectedRevision: 9 }]);
    unmount();

    // 2. Publish from BundleEditPage: 412 + detail refetch 500 -> dialog shows refresh failed & blocks Confirm until Retry succeeds
    fakeApi.reset();
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");

    let editDetailCalls = 0;
    let failEditDetail = false;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      editDetailCalls += 1;
      if (editDetailCalls === 1) {
        return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 2 }));
      }
      if (failEditDetail) {
        return fail(500, { code: "INTERNAL_ERROR" });
      }
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 6 }));
    });
    let editPublishCalls = 0;
    const editPubBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      editPublishCalls += 1;
      editPubBodies.push(call.options?.body);
      if (editPublishCalls === 1) {
        failEditDetail = true;
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 7,
        }),
      );
    });

    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);
    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    await user.click(screen.getByRole("button", { name: "Công bố" }));

    const editDialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(editDialog).getByRole("button", { name: "Công bố" }));

    const editDialogAlert = await within(editDialog).findByRole("alert");
    expect(editDialogAlert).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất",
    );
    expect(within(editDialog).queryByText(/Bản mới nhất đã được nạp lại/)).toBeNull();
    expect(within(editDialog).getByRole("button", { name: "Công bố" })).toBeDisabled();

    failEditDetail = false;
    await user.click(within(editDialogAlert).getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(within(editDialog).getByRole("alert")).toHaveTextContent(
        "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
      );
    });
    await user.click(within(editDialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(editPubBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 6 }]);
  });

  it("R3-6: công bố từ danh sách gặp 412 và getBundle trả về DISCONTINUED thì đóng dialog và thông báo qua LiveStatus", async () => {
    resetBundleNavigation("/bundles", "");
    fakeApi.get("/api/v1/admin/bundles", () =>
      ok(
        makeBundleListPage([
          makeBundleSummary({
            id: BUNDLE_ID_1,
            status: "DRAFT",
            saleState: null,
            revision: 2,
          }),
        ]),
      ),
    );
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "DISCONTINUED",
          saleState: "DISCONTINUED",
          revision: 6,
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", () =>
      fail(412, { code: "BUNDLE_REVISION_CONFLICT" }),
    );

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(screen.getByRole("status")).toHaveTextContent(
      "Bộ đề đã ngừng bán nên không thể chỉnh sửa thêm.",
    );
  });

  it("R3-5 & R3-8: công bố từ danh sách gặp 412 khi list refetch vẫn trả revision cũ (2) nhưng getBundle trả revision mới (5) thì thử lại gửi expectedRevision 5, và response trễ không làm tụt revision", async () => {
    resetBundleNavigation("/bundles", "");
    fakeApi.get("/api/v1/admin/bundles", () =>
      ok(
        makeBundleListPage([
          makeBundleSummary({
            id: BUNDLE_ID_1,
            status: "DRAFT",
            saleState: null,
            revision: 2,
          }),
        ]),
      ),
    );
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 5 })),
    );

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 6,
        }),
      );
    });

    const user = userEvent.setup();
    const { unmount } = renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 5 }]);
    unmount();

    // R3-5: list invalidation resolves first with revision 7 while slower getBundle resolves with older revision 5 -> retry sends 7 (never regresses)
    fakeApi.reset();
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
    let monoListCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      monoListCalls += 1;
      return ok(
        makeBundleListPage([
          makeBundleSummary({
            id: BUNDLE_ID_1,
            status: "DRAFT",
            saleState: null,
            revision: monoListCalls === 1 ? 2 : 7,
          }),
        ]),
      );
    });
    let resolveSlowGet!: (res: FakeResult) => void;
    fakeApi.get(
      "/api/v1/admin/bundles/{id}",
      () =>
        new Promise<FakeResult>((resolve) => {
          resolveSlowGet = resolve;
        }),
    );
    let monoPublishCalls = 0;
    const monoBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      monoPublishCalls += 1;
      monoBodies.push(call.options?.body);
      if (monoPublishCalls === 1) return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 8,
        }),
      );
    });

    renderBundlesUi(<BundleListView />);
    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const monoDialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(monoDialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(monoListCalls).toBeGreaterThanOrEqual(2);
    });
    await act(async () => {
      resolveSlowGet(ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DRAFT", revision: 5 })));
    });
    expect(await within(monoDialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    await user.click(within(monoDialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(monoBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 7 }]);
  });

  it("R3-9: công bố từ danh sách gặp 412 khi getBundle trả 500 nhưng list refetch trả revision 5 thì tự khôi phục không unhandled rejection và thử lại gửi expectedRevision 5", async () => {
    resetBundleNavigation("/bundles", "");
    let listCalls = 0;
    fakeApi.get("/api/v1/admin/bundles", () => {
      listCalls += 1;
      return ok(
        makeBundleListPage([
          makeBundleSummary({
            id: BUNDLE_ID_1,
            status: "DRAFT",
            saleState: null,
            revision: listCalls === 1 ? 2 : 5,
          }),
        ]),
      );
    });
    fakeApi.get("/api/v1/admin/bundles/{id}", () => fail(500, { code: "INTERNAL_ERROR" }));

    let publishCalls = 0;
    const sentBodies: unknown[] = [];
    fakeApi.post("/api/v1/admin/bundles/{id}/publish", (call) => {
      publishCalls += 1;
      sentBodies.push(call.options?.body);
      if (publishCalls === 1) {
        return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          revision: 6,
        }),
      );
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleListView />);

    await screen.findByText("Bộ đề SP2E khối A00");
    await user.click(screen.getByRole("button", { name: "Thao tác khác với BO-SP2E-001" }));
    await user.click(await screen.findByRole("menuitem", { name: "Công bố" }));

    const dialog = await screen.findByRole("dialog", { name: "Công bố bộ đề?" });
    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Bộ đề vừa được người khác cập nhật. Bản mới nhất đã được nạp lại.",
    );
    expect(within(dialog).getByRole("button", { name: "Công bố" })).not.toBeDisabled();

    await user.click(within(dialog).getByRole("button", { name: "Công bố" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(sentBodies).toEqual([{ expectedRevision: 2 }, { expectedRevision: 5 }]);
  });
});
