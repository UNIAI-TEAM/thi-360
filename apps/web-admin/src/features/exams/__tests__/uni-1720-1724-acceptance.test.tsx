import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import { renderWithProviders } from "@/test/render";
import { ExamReviewView } from "../components/exam-review-view";
import { ExamStructureView } from "../components/exam-structure-view";
import { ExamListingView } from "../listing/listing-tab";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";
import { listingServer, listingWire, LISTING_PATH } from "./listing-fixtures";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
const nav = vi.hoisted(() => ({ pathname: "/exams/ex-1/review", search: "" }));
vi.mock("next/navigation", () => ({
  usePathname: () => nav.pathname,
  useSearchParams: () => new URLSearchParams(nav.search),
  useRouter: () => router,
}));

function adminSession(permissions: string[]) {
  return async () => ({
    id: "u-admin",
    realm: "APP" as const,
    username: "admin",
    fullName: "Admin",
    access: "MANAGE" as const,
    permissions,
    homeUrl: "/exams",
  });
}

beforeEach(() => {
  router.push.mockReset();
  router.replace.mockReset();
  nav.pathname = `/exams/${EXAM_ID}/review`;
  nav.search = "";
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/review`);
});

describe("UNI-1721: bỏ 3 khối thông tin dư ở màn Kiểm tra cấu hình", () => {
  it("không hiện dòng thời gian/giới hạn xử lý, không hiện dải tóm tắt <dl> trùng lặp và không hiện thẻ Ba kết quả kiểm tra", async () => {
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ check: { status: "VALID", issueCount: 0 } }) })
      .on("GET", `${EXAM_PATH}/check`, {
        status: 200,
        body: {
          ...checkResultWire("VALID"),
          listingCheck: { group: "LISTING", status: "PASS", issues: [] },
        },
      })
      .install();

    renderWithProviders(<ExamReviewView examId={EXAM_ID} />);
    await screen.findByRole("heading", { name: "Kết quả theo đường lựa chọn" });

    expect(screen.queryByText(/giới hạn xử lý/)).not.toBeInTheDocument();
    expect(screen.queryByText("Ba kết quả kiểm tra")).not.toBeInTheDocument();
    expect(screen.queryByText("Số câu mỗi đường")).not.toBeInTheDocument();
    expect(screen.queryByRole("term", { name: "Đường lựa chọn" })).not.toBeInTheDocument();
  });
});

describe("UNI-1720 §6: kịch bản nghiệm thu luồng 4 bước & lưu nháp thống nhất", () => {
  it("Kịch bản 1: sửa Toán (51/50), sang Ngữ văn không hỏi rời, quay lại Toán còn nguyên, Kiểm tra cấu trúc báo vượt 1 câu không gọi PUT, Tiếp tục lưu 1 lần sang bước 3", async () => {
    const user = userEvent.setup();
    const parts = [
      {
        id: "p-math",
        order: 1,
        name: "Phần 1 · Toán học",
        durationMinutes: 75,
        questionCount: 50,
        questionCountOnPaper: 50,
        questionCountRequired: 50,
        participation: { mode: "ALL" },
        options: [],
        sections: [
          {
            id: "sec-math",
            subjectCode: "TOAN",
            questionCount: 50,
            blocks: [
              { id: "b-math-1", name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 32, requiredPointsPerQuestion: 1 },
              { id: "b-math-2", name: "Trả lời ngắn", questionType: "SHORT_ANSWER", questionCount: 15, requiredPointsPerQuestion: 1 },
              { id: "b-math-3", name: "Chùm", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, requiredPointsPerQuestion: 1 },
            ],
          },
        ],
      },
      {
        id: "p-van",
        order: 2,
        name: "Phần 2 · Ngữ văn",
        durationMinutes: 60,
        questionCount: 50,
        questionCountOnPaper: 50,
        questionCountRequired: 50,
        participation: { mode: "ALL" },
        options: [],
        sections: [
          {
            id: "sec-van",
            subjectCode: "VAN",
            questionCount: 50,
            blocks: [{ id: "b-van-1", name: "Đọc hiểu", questionType: "SINGLE_CHOICE", questionCount: 50, requiredPointsPerQuestion: 1 }],
          },
        ],
      },
    ];
    const server = new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ kind: "HSA", revision: 5 }) })
      .on("GET", "/api/v1/admin/exam-templates", { status: 200, body: [] })
      .on("GET", `${EXAM_PATH}/structure`, { status: 200, body: { examId: EXAM_ID, revision: 5, parts, examQuotas: [] } })
      .on("PUT", `${EXAM_PATH}/structure`, { status: 200, body: examDetailWire({ kind: "HSA", revision: 6 }) })
      .install();

    window.history.replaceState(null, "", `/exams/${EXAM_ID}/structure?section=sec-math`);
    renderWithProviders(<ExamStructureView examId={EXAM_ID} />);

    const singleInput = await screen.findByRole("textbox", { name: "Số câu của Trắc nghiệm độc lập" });
    await user.clear(singleInput);
    await user.type(singleInput, "33");

    const navEl = screen.getByRole("navigation", { name: "Cấu trúc" });
    await user.click(within(navEl).getAllByRole("button", { name: /Ngữ văn|VAN/ }).at(-1)!);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: /Khối lớp/ })).not.toBeInTheDocument();

    await user.click(within(navEl).getAllByRole("button", { name: /Toán|TOAN/ }).at(-1)!);
    expect(await screen.findByRole("textbox", { name: "Số câu của Trắc nghiệm độc lập" })).toHaveValue("33");

    await user.click(screen.getByRole("button", { name: "Kiểm tra cấu trúc" }));
    const quick = await screen.findByTestId("structure-quick-check-result");
    expect(quick).toHaveTextContent(/đang vượt 1 câu/);
    expect(within(quick).getByRole("button", { name: "Đến vị trí cần sửa" })).toBeInTheDocument();
    expect(server.calls("PUT", `${EXAM_PATH}/structure`)).toHaveLength(0);
    expect(server.calls("POST", `${EXAM_PATH}/check`)).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: "Tiếp tục: Thông tin bán" }));
    await waitFor(() => expect(server.calls("PUT", `${EXAM_PATH}/structure`)).toHaveLength(1));
    await waitFor(() => expect(router.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/listing`));
  });

  it("Kịch bản 2: cấu trúc hợp lệ + thiếu 3 thông tin bán -> Bước 4 báo đúng 3 lỗi, khóa Công bố bài thi, mở Xem trước đề và gắn link sửa từng ô", async () => {
    const listingIssues = [
      { key: "LISTING:PRICE", code: "LISTING_PRICE_MISSING", severity: "ERROR", group: "LISTING", field: "listPriceVnd" },
      { key: "LISTING:ATTEMPTS", code: "LISTING_ATTEMPTS_MISSING", severity: "ERROR", group: "LISTING", field: "attemptsGranted" },
      { key: "LISTING:DESC", code: "LISTING_DESCRIPTION_MISSING", severity: "ERROR", group: "LISTING", field: "description" },
    ];
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ status: "DRAFT", servingVersion: undefined, check: { status: "VALID", issueCount: 0 } }) })
      .on("GET", `${EXAM_PATH}/check`, {
        status: 200,
        body: {
          ...checkResultWire("VALID"),
          listingCheck: { group: "LISTING", status: "FAIL", issues: listingIssues },
        },
      })
      .install();

    renderWithProviders(
      <SessionProvider loadSession={adminSession(["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"])}>
        <ExamReviewView examId={EXAM_ID} />
      </SessionProvider>,
    );

    expect(await screen.findByText("Cấu trúc hợp lệ · Thông tin bán có 3 lỗi · Chưa thể công bố")).toBeInTheDocument();
    const previewButtons = screen.getAllByRole("button", { name: "Xem trước đề" });
    expect(previewButtons.length).toBeGreaterThanOrEqual(1);
    for (const btn of previewButtons) expect(btn).toBeEnabled();
    expect(screen.getByRole("button", { name: "Công bố bài thi" })).toBeDisabled();

    const listingGroup = within(document.getElementById("review-listing")!);
    const fixLinks = listingGroup.getAllByRole("link", { name: /^Đến chỗ sửa / });
    expect(fixLinks.map((link) => link.getAttribute("href"))).toEqual([
      `/exams/${EXAM_ID}/listing?field=listPriceVnd&from=review`,
      `/exams/${EXAM_ID}/listing?field=attemptsGranted&from=review`,
      `/exams/${EXAM_ID}/listing?field=description&from=review`,
    ]);
  });

  it("Kịch bản 4: đi từ lỗi Bước 4 sang Bước 3 (?field=listPriceVnd&from=review) focus ô Giá bán, hiện Lưu và quay lại kiểm tra và quay về /review?recheck=1 tự kiểm tra lại", async () => {
    const user = userEvent.setup();
    const server = listingServer(listingWire({ listPriceVnd: undefined }))
      .on("PUT", LISTING_PATH, { status: 200, body: listingWire({ revision: 8, listPriceVnd: 199000 }) })
      .install();

    nav.pathname = `/exams/${EXAM_ID}/listing`;
    nav.search = "field=listPriceVnd&from=review";
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/listing?field=listPriceVnd&from=review`);

    const view = renderWithProviders(
      <SessionProvider loadSession={adminSession(["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"])}>
        <ExamListingView examId={EXAM_ID} />
      </SessionProvider>,
    );

    const priceInput = await screen.findByRole("textbox", { name: /Giá bán/ });
    await waitFor(() => expect(priceInput).toHaveFocus());
    await user.type(priceInput, "199000");

    const backBtn = screen.getByRole("button", { name: "Lưu và quay lại kiểm tra" });
    await user.click(backBtn);
    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(1));
    await waitFor(() => expect(router.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review?recheck=1`));
    view.unmount();

    // Bước 4 nhận ?recheck=1: tự gọi POST /check và bật nút Công bố bài thi khi hợp lệ.
    let rechecked = false;
    const reviewServer = new FakeServer()
      .on("GET", EXAM_PATH, () => ({
        status: 200,
        body: examDetailWire({
          status: "DRAFT",
          servingVersion: undefined,
          check: { status: rechecked ? "VALID" : "NEEDS_RECHECK", issueCount: 0 },
        }),
      }))
      .on("GET", `${EXAM_PATH}/check`, () => ({
        status: 200,
        body: { ...checkResultWire("VALID"), stale: !rechecked, listingCheck: { group: "LISTING", status: "PASS", issues: [] } },
      }))
      .on("POST", `${EXAM_PATH}/check`, () => {
        rechecked = true;
        return {
          status: 200,
          body: { ...checkResultWire("VALID"), stale: false, listingCheck: { group: "LISTING", status: "PASS", issues: [] } },
        };
      })
      .install();

    nav.pathname = `/exams/${EXAM_ID}/review`;
    nav.search = "recheck=1";
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/review?recheck=1`);

    renderWithProviders(
      <SessionProvider loadSession={adminSession(["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"])}>
        <ExamReviewView examId={EXAM_ID} />
      </SessionProvider>,
    );

    await waitFor(() => expect(reviewServer.calls("POST", `${EXAM_PATH}/check`)).toHaveLength(1));
    await waitFor(() => expect(screen.getByRole("button", { name: "Công bố bài thi" })).toBeEnabled());
  });

  it("Bước 3 vẫn điều hướng sau khi PUT tăng revision (remount ListingForm) và GET /exams/{id} invalidation trả chậm", async () => {
    const user = userEvent.setup();
    let examCalls = 0;
    listingServer(listingWire({ revision: 7, listPriceVnd: undefined }))
      .on("GET", EXAM_PATH, async () => {
        examCalls += 1;
        if (examCalls > 1) await new Promise((resolve) => setTimeout(resolve, 40));
        return { status: 200, body: examDetailWire({ revision: examCalls > 1 ? 8 : 7 }) };
      })
      .on("PUT", LISTING_PATH, { status: 200, body: listingWire({ revision: 8, updatedAt: "2026-11-26T10:00:00Z", listPriceVnd: 199000 }) })
      .install();

    nav.pathname = `/exams/${EXAM_ID}/listing`;
    nav.search = "";
    window.history.replaceState(null, "", `/exams/${EXAM_ID}/listing`);

    renderWithProviders(
      <SessionProvider loadSession={adminSession(["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"])}>
        <ExamListingView examId={EXAM_ID} />
      </SessionProvider>,
    );

    const priceInput = await screen.findByRole("textbox", { name: /Giá bán/ });
    await user.type(priceInput, "199000");
    await user.click(screen.getByRole("button", { name: "Tiếp tục: Kiểm tra và xem trước" }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith(`/exams/${EXAM_ID}/review`));
  });
});
