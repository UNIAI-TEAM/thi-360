import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import { PermissionGate } from "../permission-gate";
import { SessionProvider } from "../session-provider";
import type { Session } from "../types";

vi.mock("next/navigation", () => ({ usePathname: () => "/grading" }));

const LEGACY_GRADING_MOCK_ENV = ["NEXT_PUBLIC", "THI360", "GRADING", "MOCK"].join("_");

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("PermissionGate (AC-9)", () => {
  it("đang tải phiên thì không hiện nội dung lẫn trang 403", () => {
    renderWithProviders(
      <SessionProvider loadSession={() => Promise.withResolvers<Session>().promise}>
        <PermissionGate permission="GRADING.VIEW">
          <p>Protected content</p>
        </PermissionGate>
      </SessionProvider>,
    );

    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Bạn không có quyền vào trang này" })).not.toBeInTheDocument();
  });

  it("có đủ quyền thì hiện nội dung con", async () => {
    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["GRADING.VIEW"],
        })}
      >
        <PermissionGate permission="GRADING.VIEW">
          <p>Protected grading content</p>
        </PermissionGate>
      </SessionProvider>,
    );

    expect(await screen.findByText("Protected grading content")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Bạn không có quyền vào trang này" })).not.toBeInTheDocument();
  });

  it("thiếu quyền thì luôn hiện trang 403 kể cả khi biến môi trường mock cũ vẫn đặt true", async () => {
    vi.stubEnv(LEGACY_GRADING_MOCK_ENV, "true");

    renderWithProviders(
      <SessionProvider
        loadSession={async () => ({
          ...TEST_ADMIN_SESSION,
          permissions: ["USER.VIEW"],
        })}
      >
        <PermissionGate permission="GRADING.VIEW">
          <p>Protected grading content</p>
        </PermissionGate>
      </SessionProvider>,
    );

    expect(await screen.findByRole("heading", { name: "Bạn không có quyền vào trang này" })).toBeInTheDocument();
    expect(screen.queryByText("Protected grading content")).not.toBeInTheDocument();
  });
});
