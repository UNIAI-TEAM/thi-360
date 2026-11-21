import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { ReconciliationReportCard } from "../components/reconciliation-report-card";
import { makeReconciliationLatest, renderOrdersUi } from "./order-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
withRealApi();

describe("ReconciliationReportCard (AC-22)", () => {
  beforeEach(() => {
    fakeApi.reset();
  });

  it("hiện đủ 4 số đếm và ngày giờ tạo báo cáo khi available = true", async () => {
    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () =>
      ok(makeReconciliationLatest()),
    );

    renderOrdersUi(<ReconciliationReportCard />);

    expect(await screen.findByText(/Đối soát thanh toán hằng ngày/)).toBeInTheDocument();
    expect(screen.getByText(/20\/11\/2026/)).toBeInTheDocument();
    expect(screen.getByText("Đã thu tiền chưa cấp quyền")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("Giao dịch treo quá hạn")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("Khoản thu thừa chưa hoàn")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("Tiền về muộn chờ xử lý")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
  });

  it("hiện trạng thái bình tĩnh khi chưa có báo cáo (available = false)", async () => {
    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () =>
      ok({ available: false }),
    );

    renderOrdersUi(<ReconciliationReportCard />);

    expect(await screen.findByText("Chưa chạy đối soát ngày")).toBeInTheDocument();
  });

  it("hiện cảnh báo nội tuyến và cho thử lại khi API báo cáo lỗi 500", async () => {
    const user = userEvent.setup();
    let calls = 0;
    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () => {
      calls += 1;
      if (calls === 1) {
        return fail(500, { code: "INTERNAL_ERROR", message: "boom" });
      }
      return ok(makeReconciliationLatest());
    });

    renderOrdersUi(<ReconciliationReportCard />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không tải được báo cáo đối soát gần nhất.");

    await user.click(screen.getByRole("button", { name: "Thử tải lại" }));

    await waitFor(() => {
      expect(screen.getByText(/20\/11\/2026/)).toBeInTheDocument();
    });
    expect(calls).toBe(2);
  });
});
