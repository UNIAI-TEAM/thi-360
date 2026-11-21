import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import BundlesLayout from "@/app/(shell)/bundles/layout";
import NewBundleLayout from "@/app/(shell)/bundles/new/layout";
import { fakeApi, ok, withRealApi } from "@/test/fake-api";

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

describe("Bundle Permissions (AC-8)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetBundleNavigation("/bundles", "");
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
    fakeApi.get("/api/v1/admin/bundles", () =>
      ok(makeBundleListPage([makeBundleSummary({ status: "PUBLISHED" })])),
    );
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "PUBLISHED" })),
    );
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
  });

  it("thiếu EXAM.VIEW: mọi đường dẫn /bundles/** hiện trang 403", async () => {
    renderBundlesUi(
      <BundlesLayout>
        <p>Allowed content</p>
      </BundlesLayout>,
      { permissions: ["ORDER.VIEW", "PROMOTION.VIEW"] },
    );

    expect(await screen.findByText(/403/)).toBeInTheDocument();
    expect(screen.queryByText("Allowed content")).toBeNull();
  });

  it("chỉ có EXAM.VIEW: danh sách và chi tiết ở chế độ chỉ đọc; /bundles/new bị từ chối 403", async () => {
    // /bundles list: no Create, no Publish/Discontinue menu
    const { unmount } = renderBundlesUi(<BundleListView />, {
      permissions: ["EXAM.VIEW"],
    });
    await screen.findByText("Bộ đề SP2E khối A00");
    expect(screen.queryByRole("link", { name: "Tạo bộ đề" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Thao tác khác/ })).toBeNull();
    unmount();

    // /bundles/[id]: inputs disabled, no Save/Publish/Discontinue
    const { unmount: unmountEdit } = renderBundlesUi(
      <BundleEditPage bundleId={BUNDLE_ID_1} />,
      { permissions: ["EXAM.VIEW"] },
    );
    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    expect(screen.getByText(/Bạn chỉ có quyền xem bộ đề/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Tên bộ/)).toBeDisabled();
    expect(screen.getByLabelText(/Mã bộ/)).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Công bố/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "Ngừng bán" })).toBeNull();
    unmountEdit();

    // /bundles/new layout: 403 when missing EXAM.MODIFY
    renderBundlesUi(
      <BundlesLayout>
        <NewBundleLayout>
          <p>Create bundle page</p>
        </NewBundleLayout>
      </BundlesLayout>,
      { permissions: ["EXAM.VIEW"] },
    );
    expect(await screen.findByText(/403/)).toBeInTheDocument();
    expect(screen.queryByText("Create bundle page")).toBeNull();
  });

  it("EXAM.VIEW + EXAM.MODIFY (thiếu EXAM.PUBLISH): tạo và sửa được nhưng không thấy Công bố và Ngừng bán", async () => {
    const { unmount } = renderBundlesUi(<BundleListView />, {
      permissions: ["EXAM.VIEW", "EXAM.MODIFY"],
    });
    await screen.findByText("Bộ đề SP2E khối A00");
    expect(screen.getByRole("link", { name: "Tạo bộ đề" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Thao tác khác/ })).toBeNull();
    unmount();

    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />, {
      permissions: ["EXAM.VIEW", "EXAM.MODIFY"],
    });
    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    expect(screen.getByLabelText(/Tên bộ/)).not.toBeDisabled();
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Công bố/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "Ngừng bán" })).toBeNull();
  });

  it("EXAM.VIEW + EXAM.PUBLISH (thiếu EXAM.MODIFY): biểu mẫu chỉ đọc nhưng thấy nút Công bố và Ngừng bán", async () => {
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />, {
      permissions: ["EXAM.VIEW", "EXAM.PUBLISH"],
    });

    await screen.findByRole("heading", { name: "Bộ đề SP2E khối A00" });
    expect(screen.getByLabelText(/Tên bộ/)).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).toBeNull();
    expect(screen.getByRole("button", { name: "Công bố lại" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ngừng bán" })).toBeInTheDocument();
  });
});
