import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import type { AttemptPrepareResponse, AttemptStateResponse } from "./api";
import { PrepareView } from "./prepare-view";
import { PreparingScreen } from "./preparing-screen";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/prepare/ent-hsca-vsat",
}));

const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

function makePrepareFixture(overrides: Partial<AttemptPrepareResponse> = {}): AttemptPrepareResponse {
  return {
    entitlementId: "ent-hsca-anh",
    examId: "exam-hsca-anh",
    examCode: "HSCA-ANH-01",
    examName: "Đề H-SCA Tiếng Anh tổng hợp",
    examKind: {
      id: "ek-hsca",
      code: "HSCA",
      nameVi: "Đánh giá năng lực H-SCA",
      nameEn: "H-SCA Assessment",
    },
    scope: "SUBJECT",
    subject: {
      id: "sub-anh",
      code: "ANH",
      nameVi: "Tiếng Anh",
      nameEn: "English",
    },
    buildMode: "STANDARD",
    timing: "SHARED",
    durationMinutes: 60,
    navigationMode: "FREE",
    attemptsLeft: 3,
    servingStatus: "PUBLISHED",
    omittedGroups: [],
    parts: [
      {
        partCode: "P1",
        ord: 0,
        name: "Phần thi Tiếng Anh",
        required: true,
        choiceTiming: "NONE",
        durationMinutes: 60,
        questionCount: 40,
        maxPoints: 100,
      },
    ],
    afterStartChoices: [],
    answerFormats: ["SINGLE_CHOICE", "CLOZE"],
    listeningPolicy: { maxPlays: 2, seekable: false },
    ...overrides,
  };
}

describe("màn chuẩn bị H-SCA & V-SAT (AC-8, BS §17.1, §17.4)", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("H-SCA Tiếng Anh có audio: hiển thị số câu, phạm vi, thời lượng, answerFormats và listeningPolicy", async () => {
    renderWithProviders(
      <PrepareView
        entitlementId="ent-hsca-anh"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () => makePrepareFixture()}
      />,
    );

    const heading = await screen.findByRole("heading", { level: 1, name: "Đề H-SCA Tiếng Anh tổng hợp" });
    expect(heading).toHaveAttribute("data-exam-content");
    expect(screen.getByText("Đánh giá năng lực H-SCA")).toBeInTheDocument();
    expect(screen.getByText("Bài thi theo môn · Tiếng Anh")).toBeInTheDocument();

    const sidebar = screen.getByRole("complementary", { name: "Sẵn sàng làm bài" });
    expect(sidebar).toHaveTextContent("40 câu");
    expect(sidebar).toHaveTextContent("60 phút");
    expect(sidebar).toHaveTextContent("100 điểm");

    expect(screen.getByText("Trắc nghiệm một đáp án")).toBeInTheDocument();
    expect(screen.getByText("Điền khuyết")).toBeInTheDocument();

    expect(screen.getByText("Chính sách nghe")).toBeInTheDocument();
    expect(screen.getByText(/Mỗi tệp âm thanh được nghe tối đa 2 lần/)).toBeInTheDocument();
    expect(screen.getByText(/Không có thanh tua, không tạm dừng và không đổi tốc độ phát/)).toBeInTheDocument();
  });

  it("H-SCA Hóa học / Toán (không audio): hiển thị đủ thông số và KHÔNG hiển thị listeningPolicy", async () => {
    renderWithProviders(
      <PrepareView
        entitlementId="ent-hsca-hoa"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepareFixture({
            entitlementId: "ent-hsca-hoa",
            examCode: "HSCA-HOA-01",
            examName: "Đề H-SCA Hóa học",
            subject: { id: "sub-hoa", code: "HOA", nameVi: "Hóa học", nameEn: "Chemistry" },
            parts: [
              {
                partCode: "P1",
                ord: 0,
                name: "Hóa học",
                required: true,
                choiceTiming: "NONE",
                durationMinutes: 60,
                questionCount: 30,
                maxPoints: 100,
              },
            ],
            answerFormats: ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER"],
            listeningPolicy: undefined,
          })
        }
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đề H-SCA Hóa học" })).toBeInTheDocument();
    expect(screen.getByText("Bài thi theo môn · Hóa học")).toBeInTheDocument();
    const sidebar = screen.getByRole("complementary", { name: "Sẵn sàng làm bài" });
    expect(sidebar).toHaveTextContent("30 câu");
    expect(sidebar).toHaveTextContent("60 phút");
    expect(screen.getByText("Trắc nghiệm một đáp án")).toBeInTheDocument();
    expect(screen.getByText("Trắc nghiệm nhiều đáp án")).toBeInTheDocument();
    expect(screen.getByText("Trả lời ngắn")).toBeInTheDocument();
    expect(screen.queryByText("Chính sách nghe")).not.toBeInTheDocument();
  });

  it("V-SAT Tiếng Anh (không audio): hiện 25 câu, 60 phút, phạm vi, 4 answerFormats và KHÔNG hiển thị listeningPolicy", async () => {
    renderWithProviders(
      <PrepareView
        entitlementId="ent-vsat-anh"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepareFixture({
            entitlementId: "ent-vsat-anh",
            examCode: "VSAT-ANH-01",
            examName: "Đề V-SAT Tiếng Anh",
            examKind: { id: "ek-vsat", code: "VSAT", nameVi: "Đánh giá đầu vào V-SAT", nameEn: "V-SAT" },
            subject: { id: "sub-anh", code: "ANH", nameVi: "Tiếng Anh", nameEn: "English" },
            durationMinutes: 60,
            parts: [
              {
                partCode: "P1",
                ord: 0,
                name: "V-SAT Tiếng Anh",
                required: true,
                choiceTiming: "NONE",
                durationMinutes: 60,
                questionCount: 25,
                maxPoints: 150,
              },
            ],
            answerFormats: ["TRUE_FALSE", "SINGLE_CHOICE", "MATCHING", "CLOZE"],
            listeningPolicy: undefined,
          })
        }
      />,
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đề V-SAT Tiếng Anh" })).toBeInTheDocument();
    expect(screen.getByText("Bài thi theo môn · Tiếng Anh")).toBeInTheDocument();
    const sidebar = screen.getByRole("complementary", { name: "Sẵn sàng làm bài" });
    expect(sidebar).toHaveTextContent("25 câu");
    expect(sidebar).toHaveTextContent("60 phút");
    expect(sidebar).toHaveTextContent("150 điểm");
    expect(screen.getByText("Đúng/Sai")).toBeInTheDocument();
    expect(screen.getByText("Trắc nghiệm một đáp án")).toBeInTheDocument();
    expect(screen.getByText("Ghép đôi")).toBeInTheDocument();
    expect(screen.getByText("Điền khuyết")).toBeInTheDocument();
    expect(screen.queryByText("Chính sách nghe")).not.toBeInTheDocument();
  });

  it("giao diện en không lọt chữ giao diện tiếng Việt ngoài vùng data-exam-content", async () => {
    const { container } = renderWithProviders(
      <PrepareView
        entitlementId="ent-en-hsca"
        loadCurrentSession={async () => STUDENT_SESSION}
        loadPrepare={async () =>
          makePrepareFixture({
            answerFormats: [
              "SINGLE_CHOICE",
              "MULTIPLE_CHOICE",
              "TRUE_FALSE",
              "SHORT_ANSWER",
              "MATCHING",
              "CLOZE",
              "ESSAY",
              "QUESTION_SET",
            ],
            listeningPolicy: { maxPlays: 2, seekable: false },
          })
        }
      />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByRole("heading", { level: 1, name: "Đề H-SCA Tiếng Anh tổng hợp" })).toBeInTheDocument();
    expect(screen.getByText("H-SCA Assessment")).toBeInTheDocument();
    expect(screen.getByText("Subject exam · English")).toBeInTheDocument();
    expect(screen.getByText("Listening policy")).toBeInTheDocument();
    expect(screen.getByText(/Each audio file can be played up to 2 times/)).toBeInTheDocument();
    expect(screen.getByText("True/False")).toBeInTheDocument();
    expect(screen.getByText("Matching")).toBeInTheDocument();
    expect(screen.getByText("Fill in the blank")).toBeInTheDocument();

    // Strip data-exam-content elements and ensure zero Vietnamese diacritics exist in UI chrome
    const clone = container.cloneNode(true) as HTMLElement;
    for (const examNode of clone.querySelectorAll("[data-exam-content]")) {
      examNode.remove();
    }
    expect(VIETNAMESE_DIACRITIC_RE.test(clone.textContent ?? "")).toBe(false);
  });

  it("PreparingScreen hiển thị cảnh báo thiếu/lỗi tệp audio (AUDIO_ASSET_MISSING, AUDIO_ASSET_INVALID), hoàn lượt và nút Thử lại", async () => {
    vi.useFakeTimers();

    for (const [reason, expectedMsg] of [
      ["AUDIO_ASSET_MISSING", viMessages.attempt.abortReasons.AUDIO_ASSET_MISSING],
      ["AUDIO_ASSET_INVALID", viMessages.attempt.abortReasons.AUDIO_ASSET_INVALID],
    ] as const) {
      const onRetry = vi.fn();
      const pollState = vi.fn(async (): Promise<AttemptStateResponse> => ({
        attemptId: `att-${reason}`,
        state: "ABORTED",
        abortReason: reason,
        retryable: true,
        serverNow: "2026-11-22T01:00:01Z",
      }));

      const view = renderWithProviders(
        <PreparingScreen
          attemptId={`att-${reason}`}
          examName="Đề H-SCA Tiếng Anh"
          pollState={pollState}
          onReady={vi.fn()}
          onRetry={onRetry}
        />,
      );

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1000);
      });

      const alertBox = screen.getByRole("alert");
      expect(alertBox).toHaveTextContent(expectedMsg);
      expect(alertBox).toHaveTextContent(viMessages.attempt.waiting.failedRefundNote);

      const retryBtn = screen.getByRole("button", { name: "Thử lại" });
      expect(retryBtn).toBeEnabled();
      vi.useRealTimers();
      const user = userEvent.setup();
      await user.click(retryBtn);
      expect(onRetry).toHaveBeenCalledTimes(1);
      vi.useFakeTimers();

      view.unmount();
    }
  });
});
