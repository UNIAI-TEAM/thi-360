import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { MyExamCardData } from "@/features/my-exams/api";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { CheckingExamCard, MyExamCard } from "./my-exam-card";

function makeCard(overrides: Partial<MyExamCardData> = {}): MyExamCardData {
  return {
    entitlementId: "ent-1",
    code: "HSA-FULL-01",
    name: "Đề thi thử ĐGNL HSA số 3",
    examKind: { code: "HSA", nameVi: "Đánh giá năng lực HSA", nameEn: "HSA Aptitude Test" },
    format: "FULL",
    scopeLabel: null,
    buildMode: "STANDARD",
    coverUrl: null,
    questionCount: { min: 150, max: 150 },
    durationMinutes: { min: 195, max: 195 },
    attemptsLeft: 2,
    attemptsTotal: 3,
    saleState: "ON_SALE",
    state: "CAN_START",
    activeAttemptId: null,
    activeAttemptState: null,
    resultAttemptId: null,
    hasPendingFulfillment: false,
    pendingOrderCode: null,
    actions: ["START", "BUY_MORE"],
    ...overrides,
  };
}

describe("MyExamCard · 8 tình huống TM 12 và AC-16", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("1. IN_PROGRESS (còn lượt, có kết quả & lịch sử, đang bán): hiện Tiếp tục, Xem kết quả, Lịch sử, Mua thêm lượt", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          state: "IN_PROGRESS",
          attemptsLeft: 1,
          activeAttemptId: "att-open-1",
          resultAttemptId: "att-res-1",
          actions: ["CONTINUE", "VIEW_RESULT", "HISTORY", "BUY_MORE"],
        })}
      />,
    );

    expect(screen.getByText("Đang làm")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att-open-1");
    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute("href", "/attempts/att-res-1/result");
    expect(screen.getByRole("link", { name: "Lịch sử" })).toHaveAttribute("href", "/history?examId=HSA-FULL-01");
    expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toHaveAttribute("href", "/checkout/HSA-FULL-01");
  });

  it("2. CAN_START (đang bán, có kết quả & lịch sử): Bắt đầu tới /prepare/<entitlementId> và số lượt từ entitlement", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          entitlementId: "ent-99",
          state: "CAN_START",
          attemptsLeft: 5,
          resultAttemptId: "att-res-2",
          actions: ["START", "VIEW_RESULT", "HISTORY", "BUY_MORE"],
        })}
      />,
    );

    expect(screen.getByText("Có thể bắt đầu")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Bắt đầu" })).toHaveAttribute("href", "/prepare/ent-99");
    expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toHaveAttribute("href", "/checkout/HSA-FULL-01");
  });

  it("3. IN_PROGRESS với attemptsLeft = 0: vẫn hiện Tiếp tục kèm ghi chú lượt cuối", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          state: "IN_PROGRESS",
          attemptsLeft: 0,
          activeAttemptId: "att-last",
          actions: ["CONTINUE"],
        })}
      />,
    );

    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att-last");
    expect(screen.getByText("Bạn đang làm lượt cuối cùng của bài này.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Mua thêm lượt" })).toBeNull();
  });

  it("4. EXHAUSTED (còn bán): hiện Hết lượt, Mua lại, Xem kết quả và Lịch sử", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          state: "EXHAUSTED",
          attemptsLeft: 0,
          resultAttemptId: "att-done",
          actions: ["VIEW_RESULT", "HISTORY", "REBUY"],
        })}
      />,
    );

    expect(screen.getByText("Hết lượt")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mua lại" })).toHaveAttribute("href", "/checkout/HSA-FULL-01");
    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute("href", "/attempts/att-done/result");
    expect(screen.getByRole("link", { name: "Lịch sử" })).toHaveAttribute("href", "/history?examId=HSA-FULL-01");
  });

  it("5. EXHAUSTED + ngừng bán: ghi chú ngừng bán, giữ Xem kết quả/Lịch sử, không có nút mua", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          state: "EXHAUSTED",
          saleState: "DISCONTINUED",
          attemptsLeft: 0,
          resultAttemptId: "att-done",
          actions: ["VIEW_RESULT", "HISTORY"],
        })}
      />,
    );

    expect(screen.getByText("Hết lượt")).toBeInTheDocument();
    expect(screen.getByText(/Bài thi đã ngừng bán, không mua thêm được/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem kết quả" })).toHaveAttribute("href", "/attempts/att-done/result");
    expect(screen.getByRole("link", { name: "Lịch sử" })).toHaveAttribute("href", "/history?examId=HSA-FULL-01");
    expect(screen.queryByRole("link", { name: /Mua/ })).toBeNull();
  });

  it("6. CAN_START + ngừng bán: Bắt đầu hoạt động bình thường, không có Mua thêm lượt", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          entitlementId: "ent-disc",
          state: "CAN_START",
          saleState: "DISCONTINUED",
          attemptsLeft: 1,
          actions: ["START"],
        })}
      />,
    );

    expect(screen.getByRole("link", { name: "Bắt đầu" })).toHaveAttribute("href", "/prepare/ent-disc");
    expect(screen.getByText("Bài thi đã ngừng bán. Bạn vẫn dùng được các lượt còn lại.")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Mua thêm lượt" })).toBeNull();
  });

  it("7. Đã có quyền nhưng hasPendingFulfillment = true: hiện mã đơn đang kiểm tra thay vì Mua thêm lượt, và hiện chip Bài ôn", () => {
    renderWithProviders(
      <MyExamCard
        card={makeCard({
          buildMode: "REVIEW",
          hasPendingFulfillment: true,
          pendingOrderCode: "DH261117TOPUP1",
          actions: ["START"],
        })}
      />,
    );

    expect(screen.getByText("Bài ôn")).toBeInTheDocument();
    expect(screen.getByText(/DH261117TOPUP1/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Mua thêm lượt" })).toBeNull();
  });

  it("8. AC-16 thẻ Đang kiểm tra: hiện mã đơn, tên bài trong data-exam-content, liên hệ hỗ trợ, nút Tải lại, không có nút mua", async () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "1900 1234");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "hotro@thi360.vn");
    const onReload = vi.fn();

    const { unmount } = renderWithProviders(
      <CheckingExamCard
        card={makeCard({
          entitlementId: null,
          state: "CHECKING",
          attemptsLeft: 0,
          attemptsTotal: 0,
          pendingOrderCode: "DH261117Q4N8T2",
          hasPendingFulfillment: true,
          actions: ["CHECKING"],
        })}
        onReload={onReload}
        reloading={false}
      />,
    );

    expect(screen.getByText("Đang kiểm tra")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" })).toHaveAttribute("data-exam-content");
    expect(screen.getByText(/DH261117Q4N8T2/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem đơn" })).toHaveAttribute("href", "/account/orders/DH261117Q4N8T2");
    expect(screen.getByText(/1900 1234 · hotro@thi360.vn/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mua|Thanh toán/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /Mua|Thanh toán/ })).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Tải lại" }));
    expect(onReload).toHaveBeenCalledTimes(1);
    unmount();

    // Khi biến hỗ trợ trống: rơi về thông báo đang cập nhật, không bịa số điện thoại giả.
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "");
    const noSupport = renderWithProviders(
      <CheckingExamCard
        card={makeCard({
          entitlementId: null,
          state: "CHECKING",
          pendingOrderCode: "DH261117EMPTY",
          actions: ["CHECKING"],
        })}
        onReload={vi.fn()}
        reloading={false}
      />,
    );
    expect(screen.getByText(/Thông tin liên hệ đang được cập nhật\./)).toBeInTheDocument();
    noSupport.unmount();

    // Khi pendingOrderCode trống: không ghép "cho đơn ." rỗng và ẩn nút Xem đơn (R1-17).
    renderWithProviders(
      <CheckingExamCard
        card={makeCard({
          entitlementId: null,
          state: "CHECKING",
          pendingOrderCode: null,
          actions: ["CHECKING"],
        })}
        onReload={vi.fn()}
        reloading={false}
      />,
    );
    expect(screen.queryByText(/cho đơn \./)).toBeNull();
    expect(screen.getByText(/Đã nhận thanh toán cho đơn hàng của bạn/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Xem đơn" })).toBeNull();
  });

  it("song ngữ en (pickName) và data-exam-content: dùng nameEn khi có, rơi về nameVi khi nameEn rỗng; PART bọc data-exam-content", () => {
    const first = renderWithProviders(
      <MyExamCard
        card={makeCard({
          name: "Đề thi thử SPT môn Toán",
          examKind: { code: "SPT", nameVi: "Đánh giá năng lực ĐHSP Hà Nội", nameEn: "HNUE SPT" },
          format: "SUBJECT",
          scopeLabel: { nameVi: "Toán", nameEn: "Mathematics" },
        })}
      />,
      { locale: "en", messages: enMessages },
    );

    const kindChip = screen.getByText("HNUE SPT");
    expect(kindChip.closest("[data-exam-content]")).toBeNull();
    expect(kindChip).toHaveClass("min-h-6", "py-0.5");
    expect(kindChip).not.toHaveClass("h-6");
    const subjectLabel = screen.getByText("Mathematics");
    expect(subjectLabel.closest("[data-exam-content]")).toBeNull();
    expect(screen.getByRole("heading", { name: "Đề thi thử SPT môn Toán" })).toHaveAttribute("data-exam-content");
    first.unmount();

    renderWithProviders(
      <MyExamCard
        card={makeCard({
          name: "Luyện phần Tư duy định lượng",
          examKind: { code: "CUSTOM", nameVi: "Kỳ thi riêng", nameEn: "" },
          format: "PART",
          scopeLabel: { nameVi: "Tư duy định lượng", nameEn: "" },
        })}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(screen.getByText("Kỳ thi riêng").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Tư duy định lượng")).toHaveAttribute("data-exam-content");
  });
});
