import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { UiStringsProvider } from "@thi360/ui";
import { APP_TIME_ZONE, INTL_FORMATS } from "@thi360/web-kit/preferences";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { ADMIN_SESSION, STUDENT_SESSION } from "@/test/storefront-fixtures";
import { ApiStatusError } from "@/lib/api/client";
import type {
  AttemptPrepareResponse,
  AttemptStarter,
  AttemptStateResponse,
  ChoiceOptionWithMetrics,
  StartAttemptResponse,
} from "./api";
import { PrepareView } from "./prepare-view";

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  pathname: "/prepare/ent-1",
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
  usePathname: () => mocks.pathname,
}));

function makePrepare(overrides: Partial<AttemptPrepareResponse> = {}): AttemptPrepareResponse {
  return {
    entitlementId: "ent-1",
    examId: "exam-1",
    examCode: "HSA-FULL-01",
    examName: "Đề luyện tập HSA FULL số 01",
    examKind: {
      id: "kind-1",
      code: "HSA",
      nameVi: "Đánh giá năng lực HSA",
      nameEn: "HSA Aptitude Test",
    },
    scope: "FULL",
    buildMode: "STANDARD",
    grantSource: {
      source: "PURCHASE",
      orderCode: "DH261112K7Q4M2",
      grantedAt: "2026-11-12T03:00:00Z",
    },
    instructions: "<p>Đọc kỹ đề trước khi chọn đáp án.</p>",
    timing: "PER_PART",
    durationMinutes: 195,
    navigationMode: "SEQUENTIAL",
    attemptsLeft: 2,
    servingStatus: "PUBLISHED",
    omittedGroups: [],
    parts: [
      {
        partCode: "P1",
        ord: 0,
        name: "Phần 1 · Toán học và Xử lí số liệu",
        required: true,
        choiceTiming: "NONE",
        durationMinutes: 75,
        questionCount: 50,
        maxPoints: 50,
      },
      {
        partCode: "P2",
        ord: 1,
        name: "Phần 2 · Văn học – Ngôn ngữ",
        required: true,
        choiceTiming: "NONE",
        durationMinutes: 60,
        questionCount: 50,
        maxPoints: 50,
      },
      {
        partCode: "P3",
        ord: 2,
        name: "Phần 3 · Khoa học hoặc Tiếng Anh",
        required: false,
        choiceTiming: "AFTER_START",
        durationMinutes: 60,
        questionCount: 50,
        maxPoints: 50,
      },
    ],
    afterStartChoices: [
      {
        partCode: "P3",
        ord: 2,
        partName: "Phần 3 · Khoa học hoặc Tiếng Anh",
        kind: "BRANCH",
        options: [
          {
            code: "KH",
            name: "Khoa học",
            kind: "SUBJECT_CHOICE",
            chooseSubjects: 3,
            countsByPick: [17, 17, 16],
            subjects: [
              { code: "LY", nameVi: "Vật lí", nameEn: "Physics" },
              { code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry" },
              { code: "SINH", nameVi: "Sinh học", nameEn: "Biology" },
              { code: "SU", nameVi: "Lịch sử", nameEn: "" },
              { code: "DIA", nameVi: "Địa lí", nameEn: "Geography" },
            ],
          },
          {
            code: "ANH",
            name: "Tiếng Anh",
            kind: "FIXED",
            subjects: [{ code: "ANH", nameVi: "Tiếng Anh", nameEn: "English" }],
          },
        ],
      },
    ],
    answerFormats: ["SINGLE_CHOICE", "SHORT_ANSWER", "QUESTION_SET"],
    listeningPolicy: { maxPlays: 2, seekable: false },
    ...overrides,
  };
}

describe("PrepareView", () => {
  beforeEach(() => {
    mocks.push.mockReset();
    mocks.replace.mockReset();
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.stubEnv("NEXT_PUBLIC_SUPPORT_PHONE", "1900 1234");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("hiển thị đầy đủ thông tin chuẩn bị (LT 5.1, BS 17.1) và trạng thái 404 (AC-1)", async () => {
    const prepare = makePrepare({
      omittedGroups: [{ name: "Nhóm Viết tự luận", points: 10 }],
    });

    const firstRender = renderWithProviders(
      <PrepareView
        entitlementId="ent-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => prepare}
      />,
    );

    const heading = await screen.findByRole("heading", { level: 1, name: "Đề luyện tập HSA FULL số 01" });
    expect(heading).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Đánh giá năng lực HSA")).not.toHaveAttribute("data-exam-content");
    expect(screen.getByText("Bài thi FULL")).toBeInTheDocument();

    // Grant source PURCHASE dd/MM + orderCode
    expect(screen.getByText("Mua ngày 12/11, đơn DH261112K7Q4M2")).toBeInTheDocument();
    expect(screen.getByText("2 lượt")).toBeInTheDocument();

    // Timing, parts, formats, listeningPolicy, omittedGroups, afterStartChoices, instructions
    expect(screen.getByText("Tính giờ từng phần")).toBeInTheDocument();
    expect(screen.getByText("Phần 1 · Toán học và Xử lí số liệu")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Trắc nghiệm một đáp án")).toBeInTheDocument();
    expect(screen.getByText("Trả lời ngắn")).toBeInTheDocument();
    expect(screen.getByText("Chùm câu hỏi")).toBeInTheDocument();
    expect(screen.getByText(/nghe tối đa 2 lần/)).toBeInTheDocument();
    expect(screen.getByText("Nhóm Viết tự luận")).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Lựa chọn sẽ gặp trong bài")).toBeInTheDocument();
    expect(screen.getByText(/tối đa 15 phút/)).toBeInTheDocument();
    expect(screen.getByText("Đọc kỹ đề trước khi chọn đáp án.")).toBeInTheDocument();

    firstRender.unmount();

    // Compensation grant source + 404 state
    const compRender = renderWithProviders(
      <PrepareView
        entitlementId="ent-2"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepare({
            entitlementId: "ent-2",
            grantSource: { source: "COMPENSATION", grantedAt: "2026-11-15T00:00:00Z" },
            listeningPolicy: undefined,
            navigationMode: "FREE",
            timing: "SHARED",
          })
        }
      />,
    );
    expect(await screen.findByText("Cấp bù")).toBeInTheDocument();
    expect(screen.getByText("Một đồng hồ chung, chuyển phần tự do")).toBeInTheDocument();
    expect(screen.queryByText("Chính sách nghe")).toBeNull();
    compRender.unmount();

    renderWithProviders(
      <PrepareView
        entitlementId="other-ent"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => {
          throw new ApiStatusError(404, "Not found", { code: "NOT_FOUND" });
        }}
      />,
    );

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Không tìm thấy bài thi trong tài khoản đang đăng nhập",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Về Bài thi của tôi" })).toHaveAttribute("href", "/home");
  });

  it("chặn tài khoản quản trị không gọi prepare API", async () => {
    const loadPrepare = vi.fn();
    renderWithProviders(
      <PrepareView
        entitlementId="ent-1"
        loadCurrentSession={async () => ADMIN_SESSION}
        loadPrepare={loadPrepare}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Tài khoản quản trị" })).toBeInTheDocument();
    expect(loadPrepare).not.toHaveBeenCalled();
  });

  it("locale en dùng pickName cho danh mục (fallback nameVi khi nameEn trống) và gắn data-exam-content đúng chỗ (A7.7)", async () => {
    const prepare = makePrepare({
      scope: "SUBJECT",
      buildMode: "REVIEW",
      subject: {
        id: "sub-1",
        code: "SU",
        nameVi: "Lịch sử",
        nameEn: "",
      },
      examKind: {
        id: "kind-1",
        code: "HSA",
        nameVi: "Đánh giá năng lực HSA",
        nameEn: "HSA Aptitude Test",
      },
      preChoice: {
        partCode: "P1",
        kind: "ORIENTATION",
        options: [
          {
            code: "CS",
            name: "Khoa học máy tính",
            kind: "ORIENTATION",
            subjects: [
              { code: "TIN_CS", nameVi: "Khoa học máy tính", nameEn: "Computer Science" },
              { code: "LICH_SU_VI", nameVi: "Lịch sử địa phương", nameEn: "" },
            ],
          },
        ],
      },
      afterStartChoices: [],
    });

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <NextIntlClientProvider
        locale="en-GB"
        messages={enMessages}
        timeZone={APP_TIME_ZONE}
        formats={INTL_FORMATS}
      >
        <UiStringsProvider locale="en">
          <QueryClientProvider client={client}>
            <PrepareView
              entitlementId="ent-en"
              loadCurrentSession={async () => STUDENT_SESSION}
              loadPrepare={async () => prepare}
            />
          </QueryClientProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>,
    );

    // examKind renders English nameEn, NOT wrapped in data-exam-content
    const kindBadge = await screen.findByText("HSA Aptitude Test");
    expect(kindBadge).not.toHaveAttribute("data-exam-content");

    // Empty nameEn on subject falls back to nameVi
    expect(screen.getByText("Subject exam · Lịch sử")).toBeInTheDocument();
    expect(screen.getByText("Review exam")).toBeInTheDocument();
    expect(screen.getByText("Computer Science · Lịch sử địa phương")).toBeInTheDocument();

    // Exam content retains stored text and carries data-exam-content
    const h1 = screen.getByRole("heading", { level: 1, name: "Đề luyện tập HSA FULL số 01" });
    expect(h1).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Khoa học máy tính")).toHaveAttribute("data-exam-content");
    expect(screen.getByRole("button", { name: "Start exam" })).toBeEnabled();
  });

  it("chọn mã BCA và định hướng Tin học: khóa Start khi chưa chọn, đổi lựa chọn cập nhật số liệu theo đường, 1 lựa chọn thì điền sẵn (AC-2)", async () => {
    const user = userEvent.setup();
    const onStartAttempt = vi.fn<
      (body: { entitlementId: string; preChoice?: { optionCode: string } }, key: string) => Promise<StartAttemptResponse>
    >(async () => ({
      attemptId: "att-new-1",
      state: "STARTING",
      resumed: false,
      retryable: false,
    }));

    const optionCs: ChoiceOptionWithMetrics = {
      code: "CS",
      name: "Khoa học máy tính",
      kind: "ORIENTATION",
      subjects: [],
      questionCount: 28,
      durationMinutes: 50,
      maxPoints: 10,
    };
    const optionIct: ChoiceOptionWithMetrics = {
      code: "ICT",
      name: "Tin học ứng dụng",
      kind: "ORIENTATION",
      subjects: [],
      questionCount: 30,
      durationMinutes: 55,
      maxPoints: 12,
    };

    const informaticsPrepare = makePrepare({
      examCode: "THPT-TIN-01",
      examName: "Đề luyện tập THPTQG Tin học",
      scope: "SUBJECT",
      timing: "SHARED",
      durationMinutes: 50,
      parts: [
        {
          partCode: "P1",
          ord: 0,
          name: "Phần chung và định hướng",
          required: false,
          choiceTiming: "PRE_START",
          durationMinutes: 50,
          questionCount: 28,
          maxPoints: 10,
        },
      ],
      afterStartChoices: [],
      preChoice: {
        partCode: "P1",
        kind: "ORIENTATION",
        options: [optionCs, optionIct],
      },
    });

    const multiRender = renderWithProviders(
      <PrepareView
        entitlementId="ent-tin"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => informaticsPrepare}
        onStartAttempt={onStartAttempt}
      />,
    );

    const startBtn = await screen.findByRole("button", { name: "Bắt đầu bài thi" });
    expect(startBtn).toBeDisabled();
    expect(screen.getByText("Vui lòng chọn một phương án trước khi bắt đầu.")).toBeInTheDocument();

    // Select ICT option and verify dynamic metrics update in header, path summary, and sidebar
    await user.click(screen.getByRole("radio", { name: /Tin học ứng dụng/ }));
    expect(startBtn).toBeEnabled();

    const sidebar = screen.getByRole("complementary", { name: "Sẵn sàng làm bài" });
    expect(within(sidebar).getByText("30 câu")).toBeInTheDocument();
    expect(within(sidebar).getByText("55 phút")).toBeInTheDocument();
    expect(within(sidebar).getByText("12 điểm")).toBeInTheDocument();
    expect(screen.getByText("Đường ICT")).toBeInTheDocument();

    // Switch to CS option and verify metrics update immediately
    await user.click(screen.getByRole("radio", { name: /Khoa học máy tính/ }));
    expect(within(sidebar).getByText("28 câu")).toBeInTheDocument();
    expect(within(sidebar).getByText("50 phút")).toBeInTheDocument();
    expect(within(sidebar).getByText("10 điểm")).toBeInTheDocument();

    await user.click(startBtn);
    await waitFor(() => expect(onStartAttempt).toHaveBeenCalledTimes(1));
    expect(onStartAttempt.mock.calls[0]?.[0]).toEqual({
      entitlementId: "ent-tin",
      preChoice: { optionCode: "CS" },
    });
    expect(await screen.findByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeInTheDocument();
    multiRender.unmount();

    // Single option preselected automatically
    const singlePrepare = makePrepare({
      preChoice: {
        partCode: "P3",
        kind: "COMBINATION",
        options: [
          {
            code: "CA2",
            name: "Hóa học",
            kind: "COMBINATION",
            subjects: [{ code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry" }],
          },
        ],
      },
    });
    renderWithProviders(
      <PrepareView
        entitlementId="ent-bca-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => singlePrepare}
      />,
    );

    const radio = await screen.findByRole("radio", { name: /CA2/ });
    expect(radio).toBeChecked();
    expect(screen.getByRole("button", { name: "Bắt đầu bài thi" })).toBeEnabled();
  });

  it("lựa chọn dạng SUBJECT_CHOICE (chọn N trong M môn) khóa Start tới khi chọn đủ N môn", async () => {
    const user = userEvent.setup();
    const onStartAttempt = vi.fn<AttemptStarter>(async () => ({
      attemptId: "att-subj",
      state: "IN_PROGRESS" as const,
      resumed: false,
      retryable: false,
    }));
    const navigate = vi.fn();

    const prepare = makePrepare({
      scope: "PART",
      afterStartChoices: [],
      preChoice: {
        partCode: "P3",
        kind: "SUBJECT_CHOICE",
        preselectedCode: "KH",
        options: [
          {
            code: "KH",
            name: "Khoa học",
            kind: "SUBJECT_CHOICE",
            chooseSubjects: 3,
            subjects: [
              { code: "LY", nameVi: "Vật lí", nameEn: "Physics" },
              { code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry" },
              { code: "SINH", nameVi: "Sinh học", nameEn: "Biology" },
              { code: "SU", nameVi: "Lịch sử", nameEn: "History" },
              { code: "DIA", nameVi: "Địa lí", nameEn: "Geography" },
            ],
          },
        ],
      },
    });

    renderWithProviders(
      <PrepareView
        entitlementId="ent-hsa-part"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => prepare}
        onStartAttempt={onStartAttempt}
        navigate={navigate}
      />,
    );

    const startBtn = await screen.findByRole("button", { name: "Bắt đầu bài thi" });
    expect(startBtn).toBeDisabled();
    expect(screen.getByText("Đã chọn 0/3")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Vật lí" }));
    await user.click(screen.getByRole("checkbox", { name: "Hóa học" }));
    expect(startBtn).toBeDisabled();

    await user.click(screen.getByRole("checkbox", { name: "Sinh học" }));
    expect(screen.getByText("Đã chọn 3/3")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Lịch sử" })).toBeDisabled();
    expect(startBtn).toBeEnabled();

    await user.click(startBtn);
    await waitFor(() => expect(onStartAttempt).toHaveBeenCalledTimes(1));
    expect(onStartAttempt.mock.calls[0]?.[0]).toEqual({
      entitlementId: "ent-hsa-part",
      preChoice: { optionCode: "KH", subjectCodes: ["LY", "HOA", "SINH"] },
    });
    expect(navigate).toHaveBeenCalledWith("/attempts/att-subj");
  });

  it("mở lại trang khi openAttempt đang STARTING thì vào thẳng PreparingScreen và không gọi startAttempt (AC-5)", async () => {
    const onStartAttempt = vi.fn();
    const pollAttemptState = vi.fn<() => Promise<AttemptStateResponse>>(async () => ({
      attemptId: "att-starting",
      state: "STARTING",
      serverNow: "2026-11-21T13:00:00Z",
    }));

    renderWithProviders(
      <PrepareView
        entitlementId="ent-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepare({
            openAttempt: { attemptId: "att-starting", state: "STARTING" },
          })
        }
        onStartAttempt={onStartAttempt}
        pollAttemptState={pollAttemptState}
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeInTheDocument();
    expect(onStartAttempt).not.toHaveBeenCalled();
  });

  it("mở lại trang khi openAttempt đang IN_PROGRESS hiện nút Tiếp tục tới /attempts/<id>", async () => {
    renderWithProviders(
      <PrepareView
        entitlementId="ent-1"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepare({
            openAttempt: { attemptId: "att-inprog", state: "IN_PROGRESS" },
          })
        }
      />,
    );

    const continueLink = await screen.findByRole("link", { name: "Tiếp tục" });
    expect(continueLink).toHaveAttribute("href", "/attempts/att-inprog");
    expect(screen.queryByRole("button", { name: "Bắt đầu bài thi" })).toBeNull();
  });

  it("hiển thị thông báo lỗi riêng cho NO_ATTEMPTS_LEFT, CHOICE_NOT_ALLOWED, NO_SERVING_OPTION và không vào màn chờ (AC-6)", async () => {
    const user = userEvent.setup();

    for (const [code, expectedText] of [
      ["NO_ATTEMPTS_LEFT", viMessages.prepare.startErrors.NO_ATTEMPTS_LEFT],
      ["CHOICE_NOT_ALLOWED", viMessages.prepare.startErrors.CHOICE_NOT_ALLOWED],
      ["NO_SERVING_OPTION", viMessages.prepare.startErrors.NO_SERVING_OPTION],
    ] as const) {
      const view = renderWithProviders(
        <PrepareView
          entitlementId="ent-err"
          loadCurrentSession={async () => STUDENT_SESSION}
          loadPrepare={async () => makePrepare({ afterStartChoices: [] })}
          onStartAttempt={async () => {
            throw new ApiStatusError(409, code, { code });
          }}
        />,
      );

      await user.click(await screen.findByRole("button", { name: "Bắt đầu bài thi" }));
      expect(await screen.findByText(expectedText)).toBeInTheDocument();
      expect(screen.queryByRole("heading", { level: 1, name: "Đang chuẩn bị đề" })).toBeNull();
      view.unmount();
    }

    // Verify zero attempts left + empty preChoice options states
    const exhaustedView = renderWithProviders(
      <PrepareView
        entitlementId="ent-0"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepare({
            attemptsLeft: 0,
            grantSource: undefined,
            servingStatus: "PUBLISHED",
          })
        }
      />,
    );
    expect(await screen.findByText("Bạn đã dùng hết lượt của bài này.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toHaveAttribute(
      "href",
      "/checkout/HSA-FULL-01",
    );
    expect(screen.getByRole("button", { name: "Bắt đầu bài thi" })).toBeDisabled();
    exhaustedView.unmount();

    renderWithProviders(
      <PrepareView
        entitlementId="ent-empty-opt"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepare({
            preChoice: { partCode: "P3", kind: "COMBINATION", options: [] },
          })
        }
      />,
    );
    expect(await screen.findByText("Các mã bạn đã mua hiện không còn được phục vụ.")).toBeInTheDocument();
    expect(screen.getByText(/1900 1234/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bắt đầu bài thi" })).toBeDisabled();
  });
});
