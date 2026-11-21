import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { fakeApi, ok, withRealApi } from "@/test/fake-api";
import { GradingItemSkeleton, GradingItemView } from "../components/grading-item-view";
import {
  EXAM_KIND_CATALOG_FIXTURES,
  GRADER_1_ID,
  ITEM_1_ID,
  makeDetail,
  makeEssayGrade,
  renderGradingUi,
} from "./grading-fixtures";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => `/grading/${ITEM_1_ID}`,
  useSearchParams: () => new URLSearchParams(window.location.search),
}));
vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});

withRealApi();

describe("GradingItemView word count & content isolation", () => {
  it("số chữ", async () => {
    // Nội dung bài làm có thẻ HTML, khoảng trắng không ngắt, chữ NFD (a\u0300 -> NFC), công thức toán và ký hiệu đứng riêng.
    // Chỉ các cụm chữ/số thật ("Bai", "viet", "ngan") mới được tính -> 3 chữ < wordLimit (500).
    const essayHtml =
      '<p>Ba\u0300i&nbsp;vi\u00ea\u0301t</p><p><span class="math-inline" data-latex="x^2">x^2</span> + — ng\u0103\u0301n</p>';

    let finalizedDone = false;
    const finalizedGrade = makeEssayGrade({
      state: "FINAL",
      points: 7,
      version: 3,
      criteria: [
        { id: "c1", points: 3 },
        { id: "c2", points: 4 },
      ],
    });
    fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));
    fakeApi.get("/api/v1/admin/grading/items/{itemId}", () =>
      ok(
        makeDetail({
          state: finalizedDone ? "DONE" : "IN_PROGRESS",
          answerHtml: essayHtml,
          wordLimit: 500,
          wordCount: 999,
          activeGrade: finalizedDone
            ? finalizedGrade
            : makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }),
        }),
      ),
    );
    fakeApi.put(
      "/api/v1/admin/grading/items/{itemId}",
      ok(
        makeEssayGrade({
          graderId: GRADER_1_ID,
          version: 2,
          points: 7,
          criteria: [
            { id: "c1", points: 3 },
            { id: "c2", points: 4 },
          ],
        }),
      ),
    );
    fakeApi.post("/api/v1/admin/grading/items/{itemId}/finalize", () => {
      finalizedDone = true;
      return ok({
        attemptId: "att-1",
        attemptState: "SCORED",
        grade: finalizedGrade,
      });
    });

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />);

    // Tính số chữ bằng countWords của @thi360/ui/question (bỏ HTML, công thức, ký hiệu), hiện cạnh wordLimit.
    const wordCount = await screen.findByTestId("essay-word-count");
    expect(wordCount).toHaveTextContent("3 / tối thiểu 500 chữ");
    expect(screen.getByText("Chưa đủ số chữ tối thiểu")).toBeInTheDocument();

    // Bài viết dưới số chữ tối thiểu vẫn lưu nháp và chốt điểm được bình thường (AC-33).
    await userEvent.type(screen.getByLabelText(/Tiêu chí 1\./), "3");
    await userEvent.type(screen.getByLabelText(/Tiêu chí 2\./), "4");
    await userEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
    expect(fakeApi.calls.some((c) => c.method === "PUT")).toBe(true);

    await userEvent.click(screen.getByRole("button", { name: "Hoàn tất chấm" }));
    const confirmRegion = await screen.findByRole("region", { name: "Hoàn tất chấm câu tự luận?" });
    await userEvent.click(within(confirmRegion).getByRole("button", { name: "Xác nhận hoàn tất" }));
    expect(await screen.findByRole("heading", { level: 2, name: "Đã chốt điểm" })).toBeInTheDocument();
  });

  it("bọc data-exam-content đúng trên nội dung đề/bài làm và dùng pickName cho kỳ thi ở locale en", async () => {
    fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));
    fakeApi.get(
      "/api/v1/admin/grading/items/{itemId}",
      ok(
        makeDetail({
          state: "IN_PROGRESS",
          examName: "Đề thi Văn mẫu",
          partName: "Phần Tự luận",
          examKindCode: "BCA",
          passage: "<p>Đoạn ngữ liệu văn học</p>",
          content: "<p>Phân tích nhân vật</p>",
          answerHtml: "<p>Bài làm học viên</p>",
          gradingGuide: "<p>Hướng dẫn chấm chi tiết</p>",
          sampleAnswer: "<p>Đáp án mẫu tham khảo</p>",
          rubric: [{ id: "c1", description: "Luận điểm chính xác", points: 10 }],
          activeGrade: makeEssayGrade({ graderId: GRADER_1_ID, version: 1 }),
          history: [
            makeEssayGrade({
              id: "h-1",
              state: "FINAL",
              graderName: "Giám khảo cũ",
              points: 6,
              finalizedAt: "2026-11-19T10:00:00Z",
              reopenedReason: "Cần chấm lại",
            }),
          ],
        }),
      ),
    );

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />, { locale: "en" });

    // Catalog name (examKindCode) dùng pickName và KHÔNG bọc data-exam-content.
    const kindText = await screen.findByText("Public Security Admission (BCA)");
    expect(kindText.closest("[data-exam-content]")).toBeNull();

    // Toàn bộ nội dung đề/bài làm/hướng dẫn/tiêu chí/đáp án mẫu phải nằm trong [data-exam-content].
    for (const contentText of [
      "Đề thi Văn mẫu",
      "Phần Tự luận",
      "Đoạn ngữ liệu văn học",
      "Phân tích nhân vật",
      "Bài làm học viên",
      "Hướng dẫn chấm chi tiết",
      "Đáp án mẫu tham khảo",
      "Luận điểm chính xác",
    ]) {
      expect(screen.getByText(contentText).closest("[data-exam-content]")).not.toBeNull();
    }

    // Nhãn UI không bọc data-exam-content.
    expect(screen.getByText("Prompt").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Grading guide").closest("[data-exam-content]")).toBeNull();
    expect(screen.getByText("Grading history")).toBeInTheDocument();
    const reopenedReasonEl = screen.getByText("Cần chấm lại");
    expect(reopenedReasonEl.closest("[data-exam-content]")).not.toBeNull();
    expect(reopenedReasonEl.closest("p")).toHaveTextContent("Reopen reason: Cần chấm lại");
  });

  it("hiển thị tối đa 4 chữ số thập phân và GradingItemSkeleton có aria-busy (R1-23, R1-40)", async () => {
    fakeApi.get("/api/v1/catalog/options", ok(EXAM_KIND_CATALOG_FIXTURES));
    fakeApi.get(
      "/api/v1/admin/grading/items/{itemId}",
      ok(
        makeDetail({
          state: "DONE",
          pointsMax: 10,
          rubric: [{ id: "c1", description: "Tiêu chí 1", points: 5 }],
          activeGrade: makeEssayGrade({
            state: "FINAL",
            points: 3.1234,
            criteria: [{ id: "c1", points: 3.1234 }],
            version: 2,
          }),
        }),
      ),
    );

    renderGradingUi(<GradingItemView itemId={ITEM_1_ID} />, { locale: "en" });
    expect(await screen.findByText("3.1234 / 10")).toBeInTheDocument();
    expect(screen.getByText("3.1234 / 5")).toBeInTheDocument();

    const { container } = renderGradingUi(<GradingItemSkeleton label="Loading item" />, {
      locale: "en",
    });
    expect(container.querySelector('[aria-busy="true"][aria-label="Loading item"]')).not.toBeNull();
  });
});
