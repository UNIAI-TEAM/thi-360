import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import { OrderDetailView } from "./components/order-detail-view";
import { OrderListView } from "./components/order-list-view";
import {
  makeAttentionSummary,
  makeGrant,
  makeOrderDetail,
  makeOrderListResponse,
  makeOrderSummary,
  renderOrdersUi,
  resetMockNavigation,
} from "./__tests__/order-fixtures";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./__tests__/order-fixtures");
  return fixtures.createNextNavigationMock();
});
withRealApi();

describe("Bundle orders display in /orders and /orders/[code] (AC-9)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetMockNavigation("/orders", "");
    fakeApi.get("/api/v1/admin/reconciliation-reports/latest", () => ok({ available: false }));
    fakeApi.get("/api/v1/admin/orders/attention", () => ok(makeAttentionSummary()));
    fakeApi.get("/api/v1/admin/exams", () => ok({ items: [] }));
  });

  it("/orders hiện nhãn Bộ đề và danh sách bài trong đơn bộ (cả bảng và dạng thẻ khung hẹp)", async () => {
    const bundleOrder = makeOrderSummary({
      orderId: "ord-bundle-1",
      code: "DH-BUNDLE-001",
      buyerUsername: "student01",
      productType: "BUNDLE",
      bundleId: "bundle-1",
      examId: undefined,
      productCode: "BO-SP2E-001",
      productName: "Bộ đề SP2E khối A00",
      items: [
        { position: 2, examId: "e2", examCode: "SP2E-LY-01", examName: "SP2E Vật lí – Đề 1" },
        { position: 1, examId: "e1", examCode: "SP2E-TOAN-01", examName: "SP2E Toán – Đề 1" },
      ],
    });
    const examOrder = makeOrderSummary({
      orderId: "ord-exam-1",
      code: "DH-EXAM-001",
      productType: "EXAM",
      productCode: "SP2E-HOA-01",
      productName: "SP2E Hóa học – Đề 1",
    });

    fakeApi.get("/api/v1/admin/orders", () =>
      ok(makeOrderListResponse([bundleOrder, examOrder])),
    );

    const { unmount } = renderOrdersUi(<OrderListView />);
    await screen.findByText("DH-BUNDLE-001");
    const table = screen.getByRole("table", { name: "Bảng danh sách đơn hàng" });
    expect(within(table).getByText("Bộ đề")).toBeInTheDocument();
    expect(within(table).getByText("Bộ đề SP2E khối A00")).toHaveAttribute("data-exam-content");
    expect(within(table).getByText("2 bài thi")).toBeInTheDocument();
    const firstExam = within(table).getByText("SP2E Toán – Đề 1");
    expect(firstExam).toHaveAttribute("data-exam-content");
    const secondExam = within(table).getByText("SP2E Vật lí – Đề 1");
    expect(secondExam).toHaveAttribute("data-exam-content");
    expect(
      firstExam.compareDocumentPosition(secondExam) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    const buyerCell = within(table).getByText("student01");
    expect(buyerCell).toHaveClass("wrap-break-word");
    expect(buyerCell).not.toHaveClass("wrap-anywhere");
    expect(buyerCell.parentElement).toHaveClass("min-w-[7rem]");
    unmount();

    // Card mode at 358px in English locale
    installContainerWidth(PHONE_CONTENT_WIDTH);
    renderOrdersUi(<OrderListView />, { locale: "en" });
    await screen.findByText("DH-BUNDLE-001");
    const list = screen.getByRole("list", { name: "Orders list table" });
    expect(within(list).getByText("Bundle")).toBeInTheDocument();
    expect(within(list).getByText("2 exams")).toBeInTheDocument();
    expect(within(list).getByText("SP2E Toán – Đề 1")).toHaveAttribute("data-exam-content");
  });

  it("/orders/[code] hiện nhãn Bộ đề, bảng Bài thi trong bộ đề (vị trí, mã, phiên bản, lượt, mức xem) và ánh xạ nhiều quyền thi", async () => {
    resetMockNavigation("/orders/DH-BUNDLE-001", "");
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          orderId: "ord-bundle-1",
          code: "DH-BUNDLE-001",
          productType: "BUNDLE",
          bundleId: "bundle-1",
          bundleVersionNo: 2,
          examId: undefined,
          examVersionNo: undefined,
          productCode: "BO-SP2E-001",
          productName: "Bộ đề SP2E khối A00",
          attemptsGranted: 2,
          items: [
            {
              id: "oi-2",
              position: 2,
              examId: "exam-2",
              examCode: "SP2E-LY-01",
              examName: "SP2E Vật lí – Đề 1",
              examVersionNo: 3,
              attemptsGranted: 2,
              optionCodes: [],
              benefits: {},
              resultView: "FULL_SOLUTION",
            },
            {
              id: "oi-1",
              position: 1,
              examId: "exam-1",
              examCode: "SP2E-TOAN-01",
              examName: "SP2E Toán – Đề 1",
              examVersionNo: 1,
              attemptsGranted: 2,
              optionCodes: [],
              benefits: {},
              resultView: "SCORE_AND_ANSWERS",
            },
          ],
          grants: [
            makeGrant({
              grantId: "g-1",
              entitlementId: "ent-1",
              examId: "exam-1",
              attempts: 2,
              attemptsLeft: 2,
            }),
            makeGrant({
              grantId: "g-2",
              entitlementId: "ent-2",
              examId: "exam-2",
              attempts: 2,
              attemptsLeft: 1,
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-BUNDLE-001" />);

    expect(await screen.findByText("Đơn hàng DH-BUNDLE-001")).toBeInTheDocument();
    expect(screen.getByText("Bộ đề")).toBeInTheDocument();
    expect(screen.getByText("BO-SP2E-001 · v2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở bộ đề" })).toHaveAttribute(
      "href",
      "/bundles/bundle-1",
    );

    const itemsTable = screen.getByRole("table", { name: "Bài thi trong bộ đề" });
    expect(within(itemsTable).getByText("SP2E Toán – Đề 1")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("SP2E-TOAN-01")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("v1")).toBeInTheDocument();
    expect(within(itemsTable).getByText("Điểm và đáp án")).toBeInTheDocument();

    expect(within(itemsTable).getByText("SP2E Vật lí – Đề 1")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("SP2E-LY-01")).toHaveAttribute("data-exam-content");
    expect(within(itemsTable).getByText("v3")).toBeInTheDocument();
    expect(within(itemsTable).getByText("Điểm, đáp án và lời giải")).toBeInTheDocument();

    const grantsTable = screen.getByRole("table", { name: "Quyền thi được cấp" });
    expect(within(grantsTable).getByText("ent-1")).toBeInTheDocument();
    expect(within(grantsTable).getByText("ent-2")).toBeInTheDocument();
    expect(within(grantsTable).getByText("SP2E Toán – Đề 1")).toBeInTheDocument();
    expect(within(grantsTable).getByText("SP2E Vật lí – Đề 1")).toBeInTheDocument();
  });

  it("R1-1/2/3: đơn mua lẻ EXAM có 1 phần tử items không hiện bảng Bài thi trong bộ đề và không lặp tên bài ở dòng quyền thi", async () => {
    resetMockNavigation("/orders/DH-EXAM-PROD-1", "");
    fakeApi.get("/api/v1/admin/orders/{code}", () =>
      ok(
        makeOrderDetail({
          orderId: "ord-exam-prod-1",
          code: "DH-EXAM-PROD-1",
          productType: "EXAM",
          bundleId: undefined,
          bundleVersionNo: undefined,
          examId: "exam-standalone-1",
          examVersionNo: 1,
          productCode: "IELTS-AC-01",
          productName: "IELTS Academic Practice Test 01",
          items: [
            {
              id: "oi-standalone-1",
              position: 1,
              examId: "exam-standalone-1",
              examCode: "IELTS-AC-01",
              examName: "IELTS Academic Practice Test 01",
              examVersionNo: 1,
              attemptsGranted: 3,
              optionCodes: [],
              benefits: {},
              resultView: "FULL",
            },
          ],
          grants: [
            makeGrant({
              grantId: "g-standalone-1",
              entitlementId: "ent-standalone-1",
              examId: "exam-standalone-1",
              attempts: 3,
              attemptsLeft: 3,
            }),
          ],
        }),
      ),
    );

    renderOrdersUi(<OrderDetailView code="DH-EXAM-PROD-1" />);

    expect(await screen.findByText("Đơn hàng DH-EXAM-PROD-1")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Bài thi trong bộ đề" })).toBeNull();
    expect(screen.queryByRole("table", { name: "Bài thi trong bộ đề" })).toBeNull();

    const grantsTable = screen.getByRole("table", { name: "Quyền thi được cấp" });
    expect(within(grantsTable).getByText("ent-standalone-1")).toBeInTheDocument();
    expect(within(grantsTable).queryByText("IELTS Academic Practice Test 01")).toBeNull();
    expect(within(grantsTable).queryByText("(IELTS-AC-01)")).toBeNull();
  });

  it("R1-14: người dùng chỉ có ORDER.VIEW vẫn thấy bài thi thuộc đơn bộ đề trong bộ lọc Bài thi", async () => {
    const user = userEvent.setup();
    let calledAdminExams = false;
    fakeApi.get("/api/v1/admin/exams", () => {
      calledAdminExams = true;
      return fail(403, { code: "FORBIDDEN" });
    });
    fakeApi.get("/api/v1/admin/orders", () =>
      ok(
        makeOrderListResponse([
          makeOrderSummary({
            orderId: "ord-bundle-filter",
            code: "DH-BUNDLE-FILTER",
            productType: "BUNDLE",
            bundleId: "bundle-1",
            examId: undefined,
            productCode: "BO-SP2E-001",
            productName: "Bộ đề SP2E khối A00",
            items: [
              {
                position: 1,
                examId: "exam-bundle-1",
                examCode: "SP2E-TOAN-01",
                examName: "SP2E Toán – Đề 1",
              },
              {
                position: 2,
                examId: "exam-bundle-2",
                examCode: "SP2E-LY-01",
                examName: "SP2E Vật lí – Đề 1",
              },
            ],
          }),
        ]),
      ),
    );

    renderOrdersUi(<OrderListView />, { permissions: ["ORDER.VIEW"] });

    await screen.findByText("DH-BUNDLE-FILTER");
    await user.click(screen.getByRole("button", { name: /^Bộ lọc/ }));
    const examCombo = screen.getByRole("combobox", { name: "Bài thi" });
    await user.click(examCombo);

    expect(
      await screen.findByRole("option", { name: "SP2E Toán – Đề 1 (SP2E-TOAN-01)" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "SP2E Vật lí – Đề 1 (SP2E-LY-01)" }),
    ).toBeInTheDocument();
    expect(calledAdminExams).toBe(false);
  });
});
