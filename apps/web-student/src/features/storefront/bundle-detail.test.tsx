import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { BundleDetailView } from "./bundle-detail-view";
import type { ExamAccessResult } from "./exam-access";
import { ExamGoneView } from "./exam-status-views";
import type { ProductDetail } from "./types";

const mocks = vi.hoisted(() => ({
  session: { current: null as unknown },
  access: vi.fn<(codes: readonly string[]) => Promise<ExamAccessResult>>(),
}));

vi.mock("next/navigation", () => ({ usePathname: () => "/exams/BO-SP2E-001" }));
vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => mocks.session.current };
});

const BUNDLE_DETAIL: ProductDetail = {
  code: "BO-SP2E-001",
  name: "Bộ đề SP2E khối A00",
  summary: "Luyện đủ ba môn khối A00 theo cấu trúc 2026.",
  description: "Mỗi bài thi giữ đồng hồ, lượt và kết quả riêng.",
  versionNo: 2,
  attemptsGranted: 2,
  buildMode: "STANDARD",
  productType: "BUNDLE",
  format: "BUNDLE",
  examKind: { code: "SP2E", nameVi: "ĐHSP Hà Nội 2", nameEn: "HNUE2" },
  listPrice: 349000,
  price: 299000,
  promotion: { name: "Ưu đãi đầu mùa", endsAt: "2026-11-30T16:59:00Z" },
  questionCount: { min: 96, max: 96 },
  durationMinutes: { min: 210, max: 210 },
  resultView: "SCORE_AND_ANSWERS",
  benefits: { aiExplanation: true, strengthWeakness: true, practice: false },
  choices: [],
  omittedEssayGroups: [],
  parts: [],
  scoring: {},
  items: [
    {
      examId: "ex-math",
      code: "SP2E-TOAN-01",
      name: "SP2E Toán – Đề luyện số 1",
      scope: "SUBJECT",
      subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
      buildMode: "STANDARD",
      questionCount: { min: 40, max: 40 },
      durationMinutes: { min: 90, max: 90 },
      maxPoints: { min: 10, max: 10 },
      attemptsGranted: 2,
      resultView: "SCORE_AND_ANSWERS",
      benefits: {
        aiExplanation: true,
        strengthWeakness: true,
        practice: false,
        note: "Có gợi ý phần Hình học",
      },
      versionNo: 1,
    },
    {
      examId: "ex-phys",
      code: "SP2E-LY-01",
      name: "SP2E Vật lí – Ôn tập chương",
      scope: "PART",
      partCode: "P1",
      subjectName: { nameVi: "Vật lí", nameEn: "" },
      buildMode: "REVIEW",
      questionCount: { min: 28, max: 28 },
      durationMinutes: { min: 60, max: 60 },
      maxPoints: { min: 10, max: 10 },
      attemptsGranted: 3,
      resultView: "FULL_SOLUTION",
      benefits: {
        aiExplanation: false,
        strengthWeakness: false,
        practice: true,
      },
      versionNo: 1,
    },
  ],
};

async function stubbedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const request = input instanceof Request ? input : new Request(input, init);
  expect(new URL(request.url).pathname).toBe("/api/v1/me/exam-access/query");
  const body: { codes: string[] } = await request.json();
  const result = await mocks.access(body.codes);
  if (result.kind === "forbidden") return new Response("{}", { status: 403 });
  return new Response(JSON.stringify({ items: result.rows }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  mocks.session.current = null;
  mocks.access.mockReset();
  vi.unstubAllEnvs();
  vi.stubGlobal("fetch", vi.fn(stubbedFetch));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("BundleDetailView (AC-3, AC-4, AC-11, AC-12, AC-17, A7.7)", () => {
  it("AC-11 & AC-17: hiện tên bộ, banner hero-dark chữ trắng, từng bài với môn/câu/thời lượng/điểm/quyền lợi và nhãn Bài ôn", async () => {
    const { container } = renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} />);

    expect(screen.getByRole("heading", { level: 1, name: BUNDLE_DETAIL.name })).toHaveAttribute("data-exam-content");
    expect(screen.getByText(BUNDLE_DETAIL.summary!)).toHaveAttribute("data-exam-content");
    expect(screen.getByText(BUNDLE_DETAIL.description!)).toHaveAttribute("data-exam-content");
    expect(screen.getByText("BO-SP2E-001")).toBeInTheDocument();

    const banner = container.querySelector(".text-white");
    expect(banner).not.toBeNull();
    expect(banner).not.toHaveClass("text-on-primary");

    const itemsSection = screen.getByRole("region", { name: "Các bài trong bộ" });
    const examLinks = within(itemsSection).getAllByRole("link");
    expect(examLinks[0]).toHaveTextContent("SP2E Toán – Đề luyện số 1");
    expect(examLinks[0]).toHaveAttribute("href", "/exams/SP2E-TOAN-01");
    expect(examLinks[0]).toHaveAttribute("data-exam-content");
    expect(examLinks[1]).toHaveTextContent("SP2E Vật lí – Ôn tập chương");
    expect(examLinks[1]).toHaveAttribute("href", "/exams/SP2E-LY-01");

    // Nhãn Bài ôn chỉ hiện ở bài có buildMode REVIEW
    expect(within(itemsSection).getAllByText("Bài ôn")).toHaveLength(1);

    // Thông số & quyền lợi từng bài (R-103)
    expect(within(itemsSection).getByText("Toán học")).toBeInTheDocument();
    expect(within(itemsSection).getByText("40 câu")).toBeInTheDocument();
    expect(within(itemsSection).getByText("90 phút")).toBeInTheDocument();
    expect(within(itemsSection).getAllByText("10 điểm")).toHaveLength(2);
    expect(within(itemsSection).getByText("Điểm và đáp án")).toBeInTheDocument();
    expect(within(itemsSection).getByText("Điểm, đáp án và lời giải")).toBeInTheDocument();
    expect(within(itemsSection).getByText("Có gợi ý phần Hình học")).toHaveAttribute("data-exam-content");

    // Khách: luôn có nút Mua bộ đề dẫn tới /login kèm callbackUrl checkout
    const aside = screen.getByRole("complementary");
    const buyLink = await within(aside).findByRole("link", { name: "Mua bộ đề" });
    expect(buyLink).toHaveAttribute(
      "href",
      `/login?callbackUrl=${encodeURIComponent("/checkout/BO-SP2E-001?v=2&p=299000")}`,
    );
    expect(within(aside).getByText("299.000 đ")).toBeInTheDocument();
    expect(within(aside).getByText("349.000 đ").closest("s")).not.toBeNull();
  });

  it("AC-11 & AC-12: học viên đã có lượt của bài trong bộ vẫn có nút Mua bộ đề và hiện thông báo cộng thêm lượt", async () => {
    mocks.session.current = STUDENT_SESSION;
    mocks.access.mockResolvedValue({
      kind: "rows",
      rows: [
        {
          code: "BO-SP2E-001",
          state: "NONE",
          attemptsLeft: 0,
          entitlementId: null,
          activeAttemptId: null,
          hasPendingFulfillment: false,
          pendingOrderCode: null,
          items: [
            {
              examId: "ex-math",
              code: "SP2E-TOAN-01",
              name: "SP2E Toán – Đề luyện số 1",
              state: "CAN_START",
              attemptsLeft: 2,
              entitlementId: "ent-1",
              activeAttemptId: null,
              hasPendingFulfillment: false,
              pendingOrderCode: null,
            },
            {
              examId: "ex-phys",
              code: "SP2E-LY-01",
              name: "SP2E Vật lí – Ôn tập chương",
              state: "CHECKING",
              attemptsLeft: 0,
              entitlementId: null,
              activeAttemptId: null,
              hasPendingFulfillment: true,
              pendingOrderCode: "DH261121PEND",
            },
          ],
        },
      ],
    });

    renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} />);

    const buyLink = await screen.findByRole("link", { name: "Mua bộ đề" });
    expect(buyLink).toHaveAttribute("href", "/checkout/BO-SP2E-001?v=2&p=299000");

    const notice = await screen.findByText(/Bạn đã có 2 lượt bài/);
    expect(notice).toHaveTextContent("Bạn đã có 2 lượt bài SP2E Toán – Đề luyện số 1; mua bộ sẽ cộng thêm lượt");
    expect(within(notice).getByText("SP2E Toán – Đề luyện số 1")).toHaveAttribute("data-exam-content");

    expect(screen.getByText(/DH261121PEND/)).toBeInTheDocument();
    expect(mocks.access).toHaveBeenCalledWith(["BO-SP2E-001"]);
  });

  it("AC-3: bộ UNAVAILABLE khóa nút Mua bộ đề, không có link checkout và hiện cảnh báo đúng nguyên văn", () => {
    mocks.session.current = STUDENT_SESSION;
    renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} unavailable />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(alert).toHaveTextContent("Một bài trong bộ đang được cập nhật. Bạn vẫn mua được từng bài lẻ.");

    const disabledBtn = screen.getByRole("button", { name: "Mua bộ đề" });
    expect(disabledBtn).toBeDisabled();
    expect(disabledBtn).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByRole("link", { name: "Mua bộ đề" })).toBeNull();
    expect(mocks.access).not.toHaveBeenCalled();
  });

  it("AC-3 (runtime saleState UNAVAILABLE): nhận saleState=UNAVAILABLE từ payload cũng khóa mua", () => {
    const unavailableProduct = { ...BUNDLE_DETAIL, saleState: "UNAVAILABLE" } as ProductDetail;
    renderWithProviders(<BundleDetailView product={unavailableProduct} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Bộ đề tạm thời chưa bán được.");
    expect(screen.getByRole("button", { name: "Mua bộ đề" })).toBeDisabled();
  });

  it("AC-4: bộ đã ngừng bán dùng ExamGoneView của FS-1", async () => {
    renderWithProviders(<ExamGoneView product={{ code: "BO-OLD-01", name: "Bộ đề SP2E đã ngừng" }} />);

    expect(screen.getByRole("heading", { level: 1, name: "Bài thi đã ngừng bán" })).toBeInTheDocument();
    expect(screen.getByText("Bộ đề SP2E đã ngừng")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("(BO-OLD-01)")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
  });

  it("quản trị viên không có nút mua; lỗi tải quyền hiện nút Tải lại quyền", async () => {
    mocks.session.current = ADMIN_SESSION;
    const adminView = renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} />);
    expect(await screen.findByText(/không có nút mua hay vào bài thi/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Mua bộ đề" })).toBeNull();
    adminView.unmount();

    mocks.session.current = STUDENT_SESSION;
    mocks.access.mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce({ kind: "rows", rows: [] });
    renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} />);

    const reloadBtn = await screen.findByRole("button", { name: "Tải lại quyền" });
    await userEvent.click(reloadBtn);
    expect(await screen.findByRole("link", { name: "Mua bộ đề" })).toBeInTheDocument();
  });

  it("Plan A7.7: giao diện en dùng pickName (nameEn khi có, rơi về nameVi khi rỗng) và không lẫn tiếng Việt ngoài data-exam-content", async () => {
    mocks.session.current = STUDENT_SESSION;
    mocks.access.mockResolvedValue({
      kind: "rows",
      rows: [
        {
          code: "BO-SP2E-001",
          state: "NONE",
          attemptsLeft: 0,
          entitlementId: null,
          activeAttemptId: null,
          hasPendingFulfillment: false,
          pendingOrderCode: null,
          items: [
            {
              examId: "ex-math",
              code: "SP2E-TOAN-01",
              name: "SP2E Toán – Đề luyện số 1",
              state: "CAN_START",
              attemptsLeft: 1,
              entitlementId: "ent-1",
              activeAttemptId: null,
              hasPendingFulfillment: false,
              pendingOrderCode: null,
            },
          ],
        },
      ],
    });

    const { container } = renderWithProviders(<BundleDetailView product={BUNDLE_DETAIL} />, {
      locale: "en",
      messages: enMessages,
    });

    expect(await screen.findByRole("link", { name: "Buy bundle" })).toBeInTheDocument();
    // nameEn khác nameVi -> hiện nameEn; nằm ngoài data-exam-content
    expect(screen.getByText("HNUE2").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Mathematics").closest("[data-exam-content]")).toBeNull();
    // nameEn rỗng -> rơi về nameVi; đặt vào allow-list của test nếu có dấu, kiểm tra đúng nhánh fallback
    expect(screen.getByText("Vật lí").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Review exam")).toBeInTheDocument();
    expect(screen.getByText(/You already have 1 attempt for/)).toBeInTheDocument();

    // Ngoài data-exam-content và ngoài tên môn fallback "Vật lí" (chỉ có dữ liệu test giả định không dấu hoặc ngoại lệ fallback), không còn chữ giao diện tiếng Việt.
    const clone = container.cloneNode(true) as HTMLElement;
    clone.querySelectorAll("[data-exam-content]").forEach((node) => node.remove());
    const textWithoutFallbackSubject = (clone.textContent ?? "").replaceAll("Vật lí", "");
    expect(textWithoutFallbackSubject).not.toMatch(/[ăâđêôơưàáạảãèéẹẻẽìíịỉĩòóọỏõùúụủũỳýỵỷỹ]/i);
  });
});
