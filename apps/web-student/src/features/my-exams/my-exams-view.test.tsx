import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { MyExamsResponse } from "@/features/my-exams/api";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import { MyExamsView } from "./my-exams-view";

const CHECKING_CARD: MyExamsResponse["items"][number] = {
  entitlementId: null,
  code: "HSA-FULL-04",
  name: "Đề thi thử ĐGNL HSA số 4",
  examKind: { code: "HSA", nameVi: "HSA", nameEn: "HSA" },
  format: "FULL",
  scopeLabel: null,
  buildMode: "STANDARD",
  coverUrl: null,
  questionCount: { min: 150, max: 150 },
  durationMinutes: { min: 195, max: 195 },
  attemptsLeft: 0,
  attemptsTotal: 0,
  saleState: "ON_SALE",
  state: "CHECKING",
  activeAttemptId: null,
  activeAttemptState: null,
  resultAttemptId: null,
  hasPendingFulfillment: true,
  pendingOrderCode: "DH261117Q4N8T2",
  actions: ["CHECKING"],
};

const OWNED_CARD: MyExamsResponse["items"][number] = {
  entitlementId: "ent-owned-1",
  code: "HSA-FULL-03",
  name: "Đề thi thử ĐGNL HSA số 3",
  examKind: { code: "HSA", nameVi: "HSA", nameEn: "HSA" },
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
};

describe("MyExamsView · 4 trạng thái AC-17 & AC-18", () => {
  it("đang tải hiện khung giữ chỗ có aria-busy", () => {
    renderWithProviders(<MyExamsView session={STUDENT_SESSION} loadExams={() => new Promise(() => {})} />);

    expect(screen.getByRole("heading", { level: 1, name: "Bài thi của tôi" })).toBeInTheDocument();
    const skeleton = screen.getByRole("status", { name: "Đang tải" });
    expect(skeleton).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("Bạn chưa có bài thi nào")).toBeNull();
  });

  it("danh sách rỗng hiện 'Bạn chưa có bài thi nào' kèm nút Khám phá bài thi tới /#exams", async () => {
    renderWithProviders(<MyExamsView session={STUDENT_SESSION} loadExams={async () => ({ items: [] })} />);

    expect(await screen.findByRole("heading", { name: "Bạn chưa có bài thi nào" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Khám phá bài thi" })).toHaveAttribute("href", "/#exams");
  });

  it("có dữ liệu hiện mục Bài đã mua, số lượng, nút Khám phá bài thi cạnh tiêu đề và khối Đang kiểm tra ở trên", async () => {
    renderWithProviders(
      <MyExamsView
        session={STUDENT_SESSION}
        loadExams={async () => ({ items: [CHECKING_CARD, OWNED_CARD] })}
      />,
    );

    expect(await screen.findByRole("heading", { name: "Bài đã mua" })).toBeInTheDocument();
    expect(screen.getByText("2 bài")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Khám phá bài thi" })).toHaveAttribute("href", "/#exams");

    const checkingHeading = screen.getByRole("heading", { name: "Đang kiểm tra" });
    const ownedCardHeading = screen.getByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" });
    expect(checkingHeading.compareDocumentPosition(ownedCardHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.getByText(/DH261117Q4N8T2/)).toBeInTheDocument();
  });

  it("lỗi tải khi chưa có dữ liệu không bao giờ hiện 'Bạn chưa có bài thi nào' và có nút Thử lại", async () => {
    const loader = vi
      .fn<() => Promise<MyExamsResponse>>()
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ items: [OWNED_CARD] });

    renderWithProviders(<MyExamsView session={STUDENT_SESSION} loadExams={loader} />);

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Không tải được danh sách bài thi");
    expect(screen.queryByText("Bạn chưa có bài thi nào")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" })).toBeInTheDocument();
  });

  it("lỗi khi tải lại lúc đang có dữ liệu cũ: giữ thẻ cũ và hiện dải cảnh báo 'Dữ liệu chưa được cập nhật' kèm Thử lại", async () => {
    const reloadLoader = vi
      .fn<() => Promise<MyExamsResponse>>()
      .mockResolvedValueOnce({ items: [CHECKING_CARD, OWNED_CARD] })
      .mockRejectedValueOnce(new Error("timeout"))
      .mockResolvedValueOnce({ items: [OWNED_CARD] });

    renderWithProviders(<MyExamsView session={STUDENT_SESSION} loadExams={reloadLoader} />);
    expect(await screen.findByRole("heading", { name: "Đề thi thử ĐGNL HSA số 4" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Tải lại" }));

    const staleAlert = await screen.findByRole("alert");
    expect(staleAlert).toHaveTextContent("Dữ liệu chưa được cập nhật.");
    expect(screen.getByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" })).toBeInTheDocument();
    expect(screen.queryByText("Bạn chưa có bài thi nào")).toBeNull();

    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("không trùng React key khi hai thẻ Đang kiểm tra dùng chung pendingOrderCode (R1-17, R1-36)", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      const secondChecking = {
        ...CHECKING_CARD,
        code: "HSA-FULL-05",
        name: "Đề thi thử ĐGNL HSA số 5",
      };
      renderWithProviders(
        <MyExamsView
          session={STUDENT_SESSION}
          loadExams={async () => ({ items: [CHECKING_CARD, secondChecking] })}
        />,
      );

      expect(await screen.findByRole("heading", { name: "Đề thi thử ĐGNL HSA số 4" })).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "Đề thi thử ĐGNL HSA số 5" })).toBeInTheDocument();
      expect(screen.getByText("2 bài")).toBeInTheDocument();
      const duplicateKeyCalls = errorSpy.mock.calls.filter((args) =>
        args.some((arg) => typeof arg === "string" && arg.includes("same key")),
      );
      expect(duplicateKeyCalls).toHaveLength(0);
    } finally {
      errorSpy.mockRestore();
    }
  });
});
