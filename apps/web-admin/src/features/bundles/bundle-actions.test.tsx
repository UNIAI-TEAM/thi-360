import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

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
});
