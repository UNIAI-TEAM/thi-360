import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ExamListingView } from "@/features/exams/listing/listing-tab";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderBuyersView } from "../components/order-buyers-view";
import {
  makeBuyerRow,
  makeBuyersResponse,
  makeOrderListResponse,
  makeOrderSummary,
  renderOrdersUi,
  resetMockNavigation,
} from "./order-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./order-fixtures");
  return fixtures.createNextNavigationMock();
});
withRealApi();

const EXAM_ID = "019a0000-0000-7000-8000-000000000201";

function mockAdminExamAndListing() {
  fakeApi.get("/api/v1/admin/exams/{id}", () =>
    ok({
      id: EXAM_ID,
      code: "IELTS-01",
      name: "IELTS Academic 01",
      kind: "HSA",
      scope: "FULL",
      status: "PUBLISHED",
      revision: 1,
      servingVersion: 1,
      hasUnpublishedChanges: false,
      questionCount: 10,
      durationMinutes: 60,
      hasEnglishOption: false,
      combinationCount: 0,
      timing: "SHARED",
      practiceScoring: "SUM",
      hasEssay: false,
      check: { status: "CHECKED", issueCount: 0 },
      attemptCount: 0,
      updatedAt: "2026-11-21T00:00:00Z",
      updatedBy: { id: "u1", username: "admin", fullName: "Admin" },
      yearsConfirmed: true,
      parts: [],
      versions: [],
    }),
  );
  fakeApi.get("/api/v1/admin/exams/{examId}/listing", () =>
    ok({
      examId: EXAM_ID,
      revision: 1,
      hasUnpublishedChanges: false,
      listPriceVnd: 299000,
      attemptsGranted: 3,
      benefitAiExplanation: true,
      benefitStrengthWeakness: false,
      benefitPractice: false,
      resultView: "SCORE_AND_ANSWERS",
      checkIssues: [],
    }),
  );
}

describe("OrderBuyersView & Exam buyers link (AC-5, AC-6)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders/buyers", "");
  });

  it("đếm đúng số tài khoản riêng biệt, gộp nhiều đơn của cùng người mua và hiển thị tổng đã trả + lượt còn lại (AC-5 một tài khoản mua hai đơn)", async () => {
    fakeApi.get("/api/v1/admin/exams", () =>
      ok({
        items: [{ id: EXAM_ID, code: "IELTS-01", name: "IELTS Academic 01" }],
      }),
    );
    fakeApi.get("/api/v1/admin/exams/{id}/buyers", () =>
      ok(makeBuyersResponse([makeBuyerRow()], { totalBuyers: 1, totalItems: 1 })),
    );

    resetMockNavigation("/orders/buyers", `?exam=${EXAM_ID}`);
    renderOrdersUi(<OrderBuyersView />);

    expect(await screen.findByText("Nguyen Van An")).toBeInTheDocument();
    expect(screen.getByText("Số tài khoản đã mua:")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("2 đơn đã trả")).toBeInTheDocument();
    expect(screen.getByText("5 / 6")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "DH-261121-0002" })).toHaveAttribute(
      "href",
      "/orders/DH-261121-0002",
    );
    expect(screen.getByRole("link", { name: "DH-261120-0001" })).toHaveAttribute(
      "href",
      "/orders/DH-261120-0001",
    );
  });

  it("hiện trạng thái rỗng và lỗi có nút thử lại; không gọi /admin/exams khi thiếu EXAM.VIEW", async () => {
    const user = userEvent.setup();
    let calledAdminExams = false;
    let calls = 0;

    fakeApi.get("/api/v1/admin/exams", () => {
      calledAdminExams = true;
      return fail(403, { code: "FORBIDDEN" });
    });
    fakeApi.get("/api/v1/admin/orders", () =>
      ok(makeOrderListResponse([makeOrderSummary({ examId: EXAM_ID })])),
    );
    fakeApi.get("/api/v1/admin/exams/{id}/buyers", () => {
      calls += 1;
      if (calls === 1) return fail(500, { code: "INTERNAL_ERROR", message: "err" });
      return ok(makeBuyersResponse([], { totalBuyers: 0, totalItems: 0 }));
    });

    resetMockNavigation("/orders/buyers", `?exam=${EXAM_ID}`);
    renderOrdersUi(<OrderBuyersView />, { permissions: ["ORDER.VIEW"] });

    expect(await screen.findByText("Không tải được danh sách người mua")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Chưa có người mua")).toBeInTheDocument();
    expect(calledAdminExams).toBe(false);
    expect(screen.queryByRole("link", { name: /Mở trang bài thi/ })).not.toBeInTheDocument();
  });

  it("hiện liên kết Người mua từ tab Thông tin bán chỉ khi có quyền ORDER.VIEW (AC-6 liên kết từ bài thi)", async () => {
    mockAdminExamAndListing();

    const { unmount } = renderOrdersUi(<ExamListingView examId={EXAM_ID} />, {
      permissions: ["EXAM.VIEW", "ORDER.VIEW"],
    });

    const link = await screen.findByRole("link", { name: /Người mua/ });
    expect(link).toHaveAttribute("href", `/orders/buyers?exam=${EXAM_ID}`);
    unmount();

    renderOrdersUi(<ExamListingView examId={EXAM_ID} />, {
      permissions: ["EXAM.VIEW", "EXAM.MODIFY"],
    });

    await waitFor(() => {
      expect(screen.queryByRole("link", { name: /Người mua/ })).not.toBeInTheDocument();
    });
  });

  it("chế độ en: dùng số ít '1 paid order' và số nhiều '2 paid orders' cùng data-exam-content trên option động (D-1, D-6)", async () => {
    const user = userEvent.setup();
    fakeApi.get("/api/v1/admin/exams", () =>
      ok({
        items: [{ id: EXAM_ID, code: "DS-HSA-01", name: "HSA Đề thi thử 01" }],
      }),
    );
    fakeApi.get("/api/v1/admin/exams/{id}/buyers", () =>
      ok(
        makeBuyersResponse(
          [
            makeBuyerRow({ userId: "u-single", username: "single.user", purchaseCount: 1 }),
            makeBuyerRow({ userId: "u-multi", username: "multi.user", purchaseCount: 2 }),
          ],
          { totalBuyers: 2, totalItems: 2 },
        ),
      ),
    );

    resetMockNavigation("/orders/buyers", `?exam=${EXAM_ID}`);
    renderOrdersUi(<OrderBuyersView />, { locale: "en" });

    expect(await screen.findByText("1 paid order")).toBeInTheDocument();
    expect(screen.getByText("2 paid orders")).toBeInTheDocument();

    const trigger = screen.getByRole("combobox", { name: "Exam" });
    await user.click(trigger);

    await waitFor(() => {
      const optionMarker = document.querySelector('[role="option"] [data-exam-content]');
      expect(optionMarker).not.toBeNull();
      expect(optionMarker).toHaveTextContent("HSA Đề thi thử 01 (DS-HSA-01)");
    });
  });

  it("khung hẹp (< 640px): ListCard hiện các liên kết mã đơn bấm được với relative z-1 (D-7)", async () => {
    const rectSpy = vi
      .spyOn(HTMLElement.prototype, "getBoundingClientRect")
      .mockReturnValue({
        width: 350,
        height: 400,
        top: 0,
        left: 0,
        bottom: 400,
        right: 350,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      });

    try {
      fakeApi.get("/api/v1/admin/exams", () =>
        ok({
          items: [{ id: EXAM_ID, code: "DS-HSA-01", name: "HSA Test 01" }],
        }),
      );
      fakeApi.get("/api/v1/admin/exams/{id}/buyers", () =>
        ok(makeBuyersResponse([makeBuyerRow()], { totalBuyers: 1, totalItems: 1 })),
      );

      resetMockNavigation("/orders/buyers", `?exam=${EXAM_ID}`);
      renderOrdersUi(<OrderBuyersView />);

      const orderLink1 = await screen.findByRole("link", { name: "DH-261121-0002" });
      const orderLink2 = screen.getByRole("link", { name: "DH-261120-0001" });
      expect(orderLink1).toHaveAttribute("href", "/orders/DH-261121-0002");
      expect(orderLink2).toHaveAttribute("href", "/orders/DH-261120-0001");
      expect(orderLink1.className).toContain("relative");
      expect(orderLink1.className).toContain("z-1");
    } finally {
      rectSpy.mockRestore();
    }
  });
});
