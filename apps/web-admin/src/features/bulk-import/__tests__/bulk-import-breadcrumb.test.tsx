import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AppShell } from "@/features/shell/app-shell";
import { SessionProvider } from "@/features/session/session-provider";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { defaultBulkImportApiDeps } from "../api";
import { BulkImportHeader } from "../components/bulk-import-header";

vi.mock("next/navigation", () => ({
  usePathname: () => "/users/bulk-import",
}));

describe("breadcrumb của trang Cấp hàng loạt", () => {
  it("hiện Người dùng › Tài khoản (liên kết) › Cấp hàng loạt, menu Tài khoản đang chọn", async () => {
    renderWithProviders(
      <SessionProvider loadSession={async () => TEST_ADMIN_SESSION}>
        <AppShell skipLabel="Chuyển tới nội dung chính">
          <BulkImportHeader deps={defaultBulkImportApiDeps} />
        </AppShell>
      </SessionProvider>,
    );

    const breadcrumb = await screen.findByRole("navigation", { name: "Vị trí hiện tại" });
    await waitFor(() => expect(breadcrumb).toHaveTextContent("Cấp hàng loạt"));
    expect(breadcrumb).toHaveTextContent("Người dùng");
    expect(within(breadcrumb).getByRole("link", { name: "Tài khoản" })).toHaveAttribute(
      "href",
      "/users",
    );
    expect(within(breadcrumb).getByText("Cấp hàng loạt")).toHaveAttribute("aria-current", "page");
    const nav = screen.getByRole("navigation", { name: "Menu chính" });
    expect(within(nav).getByRole("link", { name: "Tài khoản" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});
