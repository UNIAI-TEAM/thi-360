import { screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { accessRow, ADMIN_SESSION, FULL_DETAIL, STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { ExamAccessResult } from "./exam-access";
import { ExamDetailView } from "./exam-detail-view";
import { ExamGoneView, ExamNotFoundView } from "./exam-status-views";

const mocks = vi.hoisted(() => ({
  session: { current: null as unknown },
  access: vi.fn<(codes: readonly string[]) => Promise<ExamAccessResult>>(),
}));

vi.mock("next/navigation", () => ({ usePathname: () => "/exams/HSA-FULL-01" }));
vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => mocks.session.current };
});

/** Lời gọi `exam-access` đi qua `loadExamAccess` (api-client); `mocks.access` quyết định phản hồi. */
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

describe("ExamDetailView", () => {
  it("hiện tên, cấu trúc, lựa chọn, cách chấm, quyền sau khi mua và giá (TM 11.3)", async () => {
    renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

    const title = screen.getByRole("heading", { level: 1, name: FULL_DETAIL.name });
    expect(title).toHaveAttribute("data-exam-content");
    expect(screen.getByText("HSA-FULL-01")).toBeInTheDocument();

    const structure = screen.getByRole("table", { name: "Các phần theo thứ tự làm bài" });
    expect(within(structure).getByRole("rowheader", { name: "Toan hoc va Xu li so lieu" })).toBeInTheDocument();
    expect(within(structure).getByText("Bắt buộc")).toBeInTheDocument();
    expect(within(structure).getByText("Tự chọn")).toBeInTheDocument();
    expect(screen.getByText("150 câu · 195 phút")).toBeInTheDocument();

    const choices = screen.getByRole("region", { name: "Phần được chọn trong phạm vi mua" });
    expect(within(choices).getByText(/Chọn 3 trong 3 môn/)).toBeInTheDocument();
    expect(within(choices).getByText("Vat li")).toBeInTheDocument();
    expect(within(choices).getByText(/17 \/ 17 \/ 16/)).toBeInTheDocument();

    const how = screen.getByRole("region", { name: "Cách làm và cách chấm" });
    expect(how).toHaveTextContent("Trắc nghiệm một đáp án: 40, Trả lời ngắn: 10");
    expect(how).toHaveTextContent("150 điểm");
    expect(how).toHaveTextContent("195 phút · Tính giờ từng phần. Đồng hồ tính theo máy chủ.");
    expect(how).toHaveTextContent("Làm tuần tự");

    const rights = screen.getByRole("region", { name: "Quyền lợi sử dụng" });
    expect(rights).toHaveTextContent("3 lượt thi mỗi lần mua");
    expect(rights).toHaveTextContent("Điểm, đáp án và lời giải");
    expect(rights).toHaveTextContent("Ngay sau khi chấm xong");
    expect(rights).toHaveTextContent("AI cho phan 1 va 2");

    const aside = screen.getByRole("complementary");
    expect(within(aside).getByText("199.000 đ")).toBeInTheDocument();
    expect(within(aside).getByText("249.000 đ").closest("s")).not.toBeNull();
    expect(within(aside).getByText("Giảm 20%")).toBeInTheDocument();
    // Ô khuyến mãi: tên chương trình, chip mức giảm, giá gốc và ngày kết thúc cùng một khối (UNI-2888).
    const promo = within(aside).getByText(/Uu dai thang 11/).closest("p")?.parentElement;
    expect(promo).toHaveTextContent("-20%");
    expect(promo).toHaveTextContent("Kết thúc 30/11");
    expect(within(aside).getByText("Đã gồm thuế, phí")).toBeInTheDocument();
    expect(within(aside).getByText("150 câu · 195 phút mỗi lượt")).toBeInTheDocument();
  });

  it("mục Thời gian hiển thị cả thời lượng và cách đồng hồ chạy cho bài đồng hồ chung (UNI-2796)", () => {
    const sharedClock = {
      ...FULL_DETAIL,
      durationMinutes: { min: 60, max: 60 },
      scoring: { ...FULL_DETAIL.scoring, timing: "SHARED" as const },
    };
    renderWithProviders(<ExamDetailView product={sharedClock} />);

    const how = screen.getByRole("region", { name: "Cách làm và cách chấm" });
    expect(how).toHaveTextContent("60 phút · Một đồng hồ chung cho cả bài. Đồng hồ tính theo máy chủ.");
  });

  it("bài có tự luận: ghi chấm tay, điểm tổng sau khi người chấm xong; nhóm tạm bỏ qua ghi 'Không có phần tự luận'", () => {
    const withEssay = {
      ...FULL_DETAIL,
      parts: [
        { ...FULL_DETAIL.parts[0]!, answerTypes: [{ questionType: "ESSAY", questionCount: 1 }] },
        FULL_DETAIL.parts[1]!,
      ],
      omittedEssayGroups: [{ name: "Viet doan van", points: 2 }],
    };
    renderWithProviders(<ExamDetailView product={withEssay} />);

    const rights = screen.getByRole("region", { name: "Quyền lợi sử dụng" });
    expect(rights).toHaveTextContent("Sau khi người chấm chấm xong");
    expect(rights).not.toHaveTextContent("Ngay sau khi chấm xong");
    const how = screen.getByRole("region", { name: "Cách làm và cách chấm" });
    expect(how).toHaveTextContent("Phần tự luận chấm tay");
    expect(how).toHaveTextContent("Không có phần tự luận: Viet doan van · 2 điểm");
  });

  it("tự luận chỉ nằm trong môn của một lựa chọn: vẫn ghi 'sau khi người chấm xong' và số câu từng lựa chọn", () => {
    const optionEssay = {
      ...FULL_DETAIL,
      choices: [
        {
          partName: "Phan 3",
          options: [
            {
              code: "KH",
              kind: "SUBJECT_CHOICE",
              name: "Khoa hoc",
              chooseSubjects: 3,
              countsByPick: [17, 17, 16],
              answerTypes: [],
              subjectAnswerTypes: [
                {
                  subject: { code: "LY", nameVi: "Vat li", nameEn: "Physics" },
                  answerTypes: [
                    { questionType: "SINGLE_CHOICE", questionCount: 15 },
                    { questionType: "ESSAY", questionCount: 2 },
                  ],
                },
              ],
              subjects: [{ code: "LY", nameVi: "Vat li", nameEn: "Physics" }],
            },
          ],
        },
      ],
    };
    renderWithProviders(<ExamDetailView product={optionEssay} />);

    const rights = screen.getByRole("region", { name: "Quyền lợi sử dụng" });
    expect(rights).toHaveTextContent("Sau khi người chấm chấm xong");
    const choices = screen.getByRole("region", { name: "Phần được chọn trong phạm vi mua" });
    expect(choices).toHaveTextContent("Tự luận: 2");
  });

  it("môn của lựa chọn nhiều hơn số môn được chọn: số câu theo từng môn, không cộng cả bể (R2-2)", () => {
    const pooled = {
      ...FULL_DETAIL,
      choices: [
        {
          partName: "Phan 3",
          options: [
            {
              code: "KH",
              kind: "SUBJECT_CHOICE",
              name: "Khoa hoc",
              chooseSubjects: 1,
              countsByPick: [15, 10],
              answerTypes: [],
              subjectAnswerTypes: [
                {
                  subject: { code: "LY", nameVi: "Vat li", nameEn: "Physics" },
                  answerTypes: [{ questionType: "SINGLE_CHOICE", questionCount: 15 }],
                },
                {
                  subject: { code: "HOA", nameVi: "Hoa hoc", nameEn: "Chemistry" },
                  answerTypes: [{ questionType: "SINGLE_CHOICE", questionCount: 10 }],
                },
              ],
              subjects: [
                { code: "LY", nameVi: "Vat li", nameEn: "Physics" },
                { code: "HOA", nameVi: "Hoa hoc", nameEn: "Chemistry" },
              ],
            },
          ],
        },
      ],
    };
    renderWithProviders(<ExamDetailView product={pooled} />);

    const choices = screen.getByRole("region", { name: "Phần được chọn trong phạm vi mua" });
    expect(choices).toHaveTextContent("Vat li: Trắc nghiệm một đáp án: 15");
    expect(choices).toHaveTextContent("Hoa hoc: Trắc nghiệm một đáp án: 10");
    expect(choices).not.toHaveTextContent("25");
  });

  it("nhóm QUESTION_SET có childAnswerType ESSAY: ghi 'sau khi người chấm xong'", () => {
    const childEssay = {
      ...FULL_DETAIL,
      parts: [
        {
          ...FULL_DETAIL.parts[0]!,
          answerTypes: [
            { questionType: "SINGLE_CHOICE", questionCount: 40 },
            { questionType: "QUESTION_SET", childAnswerType: "ESSAY", questionCount: 4 },
          ],
        },
        FULL_DETAIL.parts[1]!,
      ],
    };
    renderWithProviders(<ExamDetailView product={childEssay} />);

    const rights = screen.getByRole("region", { name: "Quyền lợi sử dụng" });
    expect(rights).toHaveTextContent("Sau khi người chấm chấm xong");
    expect(rights).not.toHaveTextContent("Ngay sau khi chấm xong");
  });

  it("mô tả, mục tiêu, đối tượng là nội dung đề (data-exam-content), không có thời hạn sử dụng", () => {
    const { container } = renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

    expect(screen.getByText("Lam quen ap luc thoi gian")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Hoc sinh lop 12")).toHaveAttribute("data-exam-content");
    expect(container.textContent).not.toMatch(/thời hạn|hạn sử dụng/i);
  });

  it("chưa cấu hình kênh hỗ trợ: 'Thông tin liên hệ đang được cập nhật', không bịa số", () => {
    renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

    const policy = screen.getByRole("region", { name: "Giá và chính sách" });
    expect(policy).toHaveTextContent("Thông tin liên hệ đang được cập nhật.");
    expect(policy).not.toHaveTextContent("Gửi yêu cầu qua");
  });

  it("có cấu hình kênh hỗ trợ thì ghi kênh liên hệ", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "0900 000 000");
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_EMAIL", "ho-tro@example.test");
    renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

    expect(screen.getByRole("region", { name: "Giá và chính sách" })).toHaveTextContent(
      "Gửi yêu cầu qua: 0900 000 000 · ho-tro@example.test",
    );
  });

  it("chỉ cấu hình giờ hỗ trợ: vẫn ghi 'đang được cập nhật' cho kênh, giờ nằm ở dòng riêng (R1-8)", () => {
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_HOURS", "8:00–17:00");
    renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

    const policy = screen.getByRole("region", { name: "Giá và chính sách" });
    expect(policy).toHaveTextContent("Thông tin liên hệ đang được cập nhật.");
    expect(policy).toHaveTextContent("Giờ hỗ trợ: 8:00–17:00");
    expect(policy).not.toHaveTextContent("Gửi yêu cầu qua");
  });

  it("giao diện tiếng Anh: không lẫn chữ giao diện tiếng Việt", () => {
    const { container } = renderWithProviders(<ExamDetailView product={FULL_DETAIL} />, {
      locale: "en",
      messages: enMessages,
    });

    expect(screen.getByRole("region", { name: "Exam structure" })).toBeInTheDocument();
    expect(screen.getByText("Required")).toBeInTheDocument();
    // Ngoài vùng nội dung đề không được còn chữ tiếng Việt có dấu.
    const clone = container.cloneNode(true) as HTMLElement;
    clone.querySelectorAll("[data-exam-content]").forEach((node) => node.remove());
    expect(clone.textContent).not.toMatch(/[ăâđêôơưàáạảãèéẹẻẽìíịỉĩòóọỏõùúụủũỳýỵỷỹ]/i);
  });

  describe("khung Giá bán theo quyền (AC-4, AC-5, AC-6, AC-11)", () => {
    it("khách: Mua bài thi tới /login kèm callbackUrl đi tới xác nhận mua; không gọi exam-access", async () => {
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      const buy = await screen.findByRole("link", { name: "Mua bài thi" });
      expect(buy).toHaveAttribute("href", `/login?callbackUrl=${encodeURIComponent("/checkout/HSA-FULL-01?v=3&p=199000")}`);
      expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
        "href",
        `/register?callbackUrl=${encodeURIComponent("/checkout/HSA-FULL-01?v=3&p=199000")}`,
      );
      expect(mocks.access).not.toHaveBeenCalled();
    });

    it("học viên có lượt: Vào bài thi tới /prepare/<entitlementId>, gọi exam-access đúng một lần với mã sản phẩm", async () => {
      mocks.session.current = STUDENT_SESSION;
      mocks.access.mockResolvedValue({
        kind: "rows",
        rows: [accessRow({ state: "CAN_START", attemptsLeft: 2, entitlementId: "ent-7" })],
      });
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      const enter = await screen.findByRole("link", { name: "Vào bài thi" });
      expect(enter).toHaveAttribute("href", "/prepare/ent-7");
      expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toBeInTheDocument();
      expect(mocks.access).toHaveBeenCalledTimes(1);
      expect(mocks.access).toHaveBeenCalledWith(["HSA-FULL-01"]);
    });

    it("học viên chưa có quyền: Mua bài thi tới /checkout/<code>?v=&p=", async () => {
      mocks.session.current = STUDENT_SESSION;
      mocks.access.mockResolvedValue({ kind: "rows", rows: [] });
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      expect(await screen.findByRole("link", { name: "Mua bài thi" })).toHaveAttribute(
        "href",
        "/checkout/HSA-FULL-01?v=3&p=199000",
      );
    });

    it("quản trị viên (phiên MANAGE): không có nút chính và không gọi exam-access", async () => {
      mocks.session.current = ADMIN_SESSION;
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      expect(await screen.findByText(/không có nút mua hay vào bài thi/)).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /Mua|Vào bài thi/ })).toBeNull();
      expect(mocks.access).not.toHaveBeenCalled();
    });

    it("API exam-access trả 403: coi như quản trị viên, không có nút chính", async () => {
      mocks.session.current = STUDENT_SESSION;
      mocks.access.mockResolvedValue({ kind: "forbidden" });
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      expect(await screen.findByText(/không có nút mua hay vào bài thi/)).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: /Mua|Vào bài thi/ })).toBeNull();
    });

    it("lỗi tải quyền: hiện Tải lại quyền thay vì nút Mua", async () => {
      mocks.session.current = STUDENT_SESSION;
      mocks.access.mockRejectedValue(new Error("boom"));
      renderWithProviders(<ExamDetailView product={FULL_DETAIL} />);

      expect(await screen.findByRole("button", { name: "Tải lại quyền" }, { timeout: 4000 })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Mua bài thi" })).toBeNull();
    });
  });
});

describe("trang ngừng bán và 404 (AC-8, AC-9)", () => {
  const GONE = { code: "QDA-FULL-01", name: "De thi thu QDA FULL so 01" };

  it("mã ngừng bán: 'Bài thi đã ngừng bán', tên và mã, nút về danh mục; khách không có nút Bài thi của tôi", async () => {
    renderWithProviders(<ExamGoneView product={GONE} />);

    expect(screen.getByRole("heading", { level: 1, name: "Bài thi đã ngừng bán" })).toBeInTheDocument();
    expect(screen.getByText(GONE.name)).toHaveAttribute("data-exam-content");
    expect(screen.getByText("(QDA-FULL-01)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
    // Chờ phiên tải xong (khách) rồi mới khẳng định không có nút.
    await screen.findByRole("link", { name: "Về danh mục bài thi" });
    expect(screen.queryByRole("link", { name: "Bài thi của tôi" })).toBeNull();
  });

  it("đã đăng nhập: có thêm nút Bài thi của tôi tới /home", async () => {
    mocks.session.current = STUDENT_SESSION;
    renderWithProviders(<ExamGoneView product={GONE} />);

    expect(await screen.findByRole("link", { name: "Bài thi của tôi" })).toHaveAttribute("href", "/home");
  });

  it("404: 'Không tìm thấy bài thi' kèm nút về danh mục", () => {
    renderWithProviders(<ExamNotFoundView />);

    expect(screen.getByRole("heading", { name: "Không tìm thấy bài thi" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về danh mục bài thi" })).toHaveAttribute("href", "/#exams");
  });
});
