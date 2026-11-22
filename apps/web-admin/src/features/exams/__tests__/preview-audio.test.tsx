import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { focusRing } from "@thi360/ui";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { ExamPreviewPaper } from "../components/exam-preview-paper";
import { generatePreview } from "../review-api";
import type { PreviewAttachment, PreviewPaper } from "../review-types";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";
import { block, paperOf, partOf, question, set, single } from "./preview-paper-fixtures";

// Nhà máy của `vi.mock` được nâng lên đầu tệp nên không dùng được import tĩnh cho dữ liệu mẫu (cùng cách các test khác).
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const FOCUS_CLASSES = focusRing.split(" ");

/* jsdom không phát tiếng: giả `play`, `pause`, `paused`, `currentTime` đủ để kiểm tra giao diện và thứ tự thao tác. */
const playingElements = new WeakSet<HTMLMediaElement>();
const positions = new WeakMap<HTMLMediaElement, number>();
const playLog: HTMLMediaElement[] = [];

beforeAll(() => {
  Object.defineProperty(HTMLMediaElement.prototype, "paused", {
    configurable: true,
    get(this: HTMLMediaElement) {
      return !playingElements.has(this);
    },
  });
  Object.defineProperty(HTMLMediaElement.prototype, "currentTime", {
    configurable: true,
    get(this: HTMLMediaElement) {
      return positions.get(this) ?? 0;
    },
    set(this: HTMLMediaElement, value: number) {
      positions.set(this, value);
    },
  });
  HTMLMediaElement.prototype.play = function play(this: HTMLMediaElement) {
    playingElements.add(this);
    playLog.push(this);
    fireEvent.play(this);
    return Promise.resolve();
  };
  HTMLMediaElement.prototype.pause = function pause(this: HTMLMediaElement) {
    if (!playingElements.delete(this)) return;
    fireEvent.pause(this);
  };
  HTMLMediaElement.prototype.load = vi.fn();
});

afterEach(() => {
  playLog.length = 0;
});

const MP3: PreviewAttachment = { id: "asset-1", name: "cau-1-nha-ga.mp3", mime: "audio/mpeg" };
const MP3_SET: PreviewAttachment = { id: "asset-2", name: "chum-phong-kham.mp3", mime: "audio/mpeg" };
const PDF: PreviewAttachment = { id: "asset-3", name: "de-bai.pdf", mime: "application/pdf" };

function audioOf(group: HTMLElement): HTMLAudioElement {
  const element = group.querySelector("audio");
  if (!element) throw new Error("no audio element");
  return element;
}

/** Báo cho trình phát biết siêu dữ liệu đã đọc xong: `ranges` là các khoảng `seekable` ([start, end]); `false` = rỗng. */
function loaded(element: HTMLAudioElement, duration = 100, ranges: Array<[number, number]> | false = [[0, duration]]) {
  Object.defineProperty(element, "duration", { configurable: true, value: duration });
  const list = ranges === false ? [] : ranges;
  Object.defineProperty(element, "seekable", {
    configurable: true,
    value: { length: list.length, start: (index: number) => list[index]![0], end: (index: number) => list[index]![1] },
  });
  fireEvent.loadedMetadata(element);
}

function renderPaper(paper: PreviewPaper, locale?: "en") {
  return renderWithProviders(<ExamPreviewPaper paper={paper} />, locale ? { locale, messages: en } : undefined);
}

const listening = paperOf([
  partOf({
    lastNumber: 5,
    blocks: [
      block({ blockId: "single", blockName: "Single", items: [single(question(1, { attachments: [MP3, PDF] })), single(question(2))] }),
      block({
        blockId: "sets",
        blockName: "Sets",
        items: [set("s1", [question(3), question(4), question(5)], { attachments: [MP3_SET] })],
      }),
    ],
  }),
]);

describe("trình phát audio trong đề xem trước", () => {
  it("câu đơn có tệp audio có trình phát với nguồn là API tài nguyên, không dùng điều khiển gốc; tệp PDF không có trình phát", async () => {
    renderPaper(listening);

    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    expect(element).toHaveAttribute("src", "/api/v1/admin/question-assets/asset-1");
    expect(element).not.toHaveAttribute("controls");
    expect(within(player).getByText("cau-1-nha-ga.mp3")).toBeInTheDocument();
    expect(document.querySelectorAll("audio")).toHaveLength(2);
    expect(screen.queryByText("de-bai.pdf")).not.toBeInTheDocument();
    expect(screen.getAllByText("Có audio")).toHaveLength(2);
  });

  it("chùm có audio ở câu gốc: trình phát nằm ở đầu chùm, trước bài đọc, và câu con không có trình phát riêng", async () => {
    renderPaper(listening);

    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 3–5" });
    const article = player.closest("article");
    expect(article).not.toBeNull();
    expect(within(article!).getAllByRole("group", { name: /^Trình phát audio/ })).toHaveLength(1);
    expect(article!.querySelectorAll("audio")).toHaveLength(1);
    const passage = within(article!).getByText("Passage of s1");
    expect(player.compareDocumentPosition(passage) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("dùng được bằng bàn phím: Tab tới nút Phát, Enter phát, Space tạm dừng, mọi điều khiển có vòng focus của kit", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    loaded(audioOf(player));

    const play = within(player).getByRole("button", { name: "Phát audio · câu 1" });
    for (const control of [
      play,
      within(player).getByRole("button", { name: "Lùi 5 giây" }),
      within(player).getByRole("button", { name: "Tiến 5 giây" }),
      within(player).getByRole("slider", { name: "Vị trí phát" }),
      within(player).getByRole("button", { name: "Tắt tiếng · câu 1" }),
    ]) {
      for (const name of FOCUS_CLASSES) expect(control).toHaveClass(name);
    }

    while (document.activeElement !== play) await user.tab();
    await user.keyboard("{Enter}");
    expect(within(player).getByRole("button", { name: "Tạm dừng audio · câu 1" })).toHaveFocus();
    expect(within(player).getByRole("status")).toHaveTextContent("Đang phát câu 1");

    await user.keyboard(" ");
    expect(within(player).getByRole("button", { name: "Phát audio · câu 1" })).toBeInTheDocument();
    expect(within(player).getByRole("status")).toHaveTextContent("Đã tạm dừng câu 1");
  });

  it("tua: Tiến và Lùi 5 giây, thanh vị trí mang thời gian đọc được; không vượt hai đầu", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    loaded(element, 100);
    const slider = within(player).getByRole("slider", { name: "Vị trí phát" });
    expect(slider).toHaveAttribute("aria-valuetext", "0 phút 0 giây trên 1 phút 40 giây");

    await user.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    expect(element.currentTime).toBe(5);
    expect(slider).toHaveAttribute("aria-valuetext", "0 phút 5 giây trên 1 phút 40 giây");
    expect(within(player).getByText("0:05 / 1:40")).toBeInTheDocument();

    await user.click(within(player).getByRole("button", { name: "Lùi 5 giây" }));
    await user.click(within(player).getByRole("button", { name: "Lùi 5 giây" }));
    expect(element.currentTime).toBe(0);

    fireEvent.change(slider, { target: { value: "95" } });
    expect(element.currentTime).toBe(95);
    await user.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    await user.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    expect(element.currentTime).toBe(100);
  });

  it("mỗi lần chỉ một trình phát chạy: phát tệp thứ hai thì tệp thứ nhất tạm dừng", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const first = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const second = screen.getByRole("group", { name: "Trình phát audio · câu 3–5" });
    loaded(audioOf(first));
    loaded(audioOf(second));

    await user.click(within(first).getByRole("button", { name: "Phát audio · câu 1" }));
    expect(within(first).getByRole("button", { name: "Tạm dừng audio · câu 1" })).toBeInTheDocument();

    await user.click(within(second).getByRole("button", { name: "Phát audio · câu 3–5" }));

    expect(within(second).getByRole("button", { name: "Tạm dừng audio · câu 3–5" })).toBeInTheDocument();
    expect(within(first).getByRole("button", { name: "Phát audio · câu 1" })).toBeInTheDocument();
  });

  it("chùm có hai tệp audio: hai trình phát xếp dọc, ghi Tệp 1/2 và Tệp 2/2, phát tệp này thì tệp kia dừng", async () => {
    const user = userEvent.setup();
    const second: PreviewAttachment = { id: "asset-9", name: "phan-hai.mp3", mime: "audio/mp3" };
    renderPaper(
      paperOf([partOf({ lastNumber: 2, blocks: [block({ items: [set("s9", [question(1), question(2)], { attachments: [MP3_SET, second] })] })] })]),
    );
    const players = await screen.findAllByRole("group", { name: "Trình phát audio · câu 1–2" });
    expect(players).toHaveLength(2);
    expect(within(players[0]!).getByText(/Tệp 1\/2/)).toBeInTheDocument();
    expect(within(players[1]!).getByText(/Tệp 2\/2/)).toBeInTheDocument();
    for (const player of players) loaded(audioOf(player));

    await user.click(within(players[0]!).getByRole("button", { name: /^Phát audio/ }));
    await user.click(within(players[1]!).getByRole("button", { name: /^Phát audio/ }));

    expect(within(players[0]!).getByRole("button", { name: /^Phát audio/ })).toBeInTheDocument();
    expect(within(players[1]!).getByRole("button", { name: /^Tạm dừng audio/ })).toBeInTheDocument();
  });

  it("tắt tiếng và bật tiếng đổi nhãn nút và trạng thái của tệp", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);

    await user.click(within(player).getByRole("button", { name: "Tắt tiếng · câu 1" }));
    expect(element.muted).toBe(true);
    fireEvent.volumeChange(element);

    expect(within(player).getByRole("button", { name: "Bật tiếng · câu 1" })).toBeInTheDocument();
  });

  it("lỗi phát: báo lỗi nêu tên tệp, đề vẫn xem được, Thử lại tải lại tệp", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);

    fireEvent.error(element);

    const alert = within(player).getByRole("alert");
    expect(alert).toHaveTextContent("Không phát được cau-1-nha-ga.mp3.");
    expect(alert).toHaveTextContent("Đề thử vẫn xem được.");
    expect(screen.getByText("Stem 1")).toBeInTheDocument();
    expect(within(player).queryByRole("button", { name: /^Phát audio/ })).not.toBeInTheDocument();

    await user.click(within(alert).getByRole("button", { name: "Thử lại" }));

    expect(HTMLMediaElement.prototype.load).toHaveBeenCalled();
    expect(within(player).queryByRole("alert")).not.toBeInTheDocument();
    expect(within(player).getByRole("button", { name: "Phát audio · câu 1" })).toBeInTheDocument();
  });

  it("tệp chưa tua được: khóa thanh vị trí, Lùi và Tiến, ghi 'Tệp này chỉ phát từ đầu', nút Phát vẫn dùng được", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    loaded(audioOf(player), 100, false);

    expect(within(player).getAllByText("Tệp này chỉ phát từ đầu")).toHaveLength(2);
    expect(within(player).getByRole("slider", { name: "Vị trí phát" })).toBeDisabled();
    expect(within(player).getByRole("button", { name: "Lùi 5 giây" })).toBeDisabled();
    expect(within(player).getByRole("button", { name: "Tiến 5 giây" })).toBeDisabled();
    await user.click(within(player).getByRole("button", { name: "Phát audio · câu 1" }));
    expect(within(player).getByRole("button", { name: "Tạm dừng audio · câu 1" })).toBeInTheDocument();
  });

  it("máy chủ không hỗ trợ Range (seekable chỉ có [0,0]): chế độ chỉ phát từ đầu, điều khiển khóa kèm lý do, phát vẫn được", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    loaded(audioOf(player), 2.088, [[0, 0]]);

    expect(within(player).getAllByText("Tệp này chỉ phát từ đầu")).toHaveLength(2);
    const back = within(player).getByRole("button", { name: "Lùi 5 giây" });
    const forward = within(player).getByRole("button", { name: "Tiến 5 giây" });
    const slider = within(player).getByRole("slider", { name: "Vị trí phát" });
    for (const control of [back, forward, slider]) {
      expect(control).toBeDisabled();
      expect(control).toHaveAccessibleDescription("Tệp này chỉ phát từ đầu");
    }
    await user.click(within(player).getByRole("button", { name: "Phát audio · câu 1" }));
    expect(within(player).getByRole("button", { name: "Tạm dừng audio · câu 1" })).toBeInTheDocument();
  });

  it("duration vô hạn khi máy chủ trả luồng không độ dài: chế độ chỉ phát từ đầu, không hiện tổng 0:00 hay Infinity", async () => {
    const user = userEvent.setup();
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    loaded(audioOf(player), Infinity, [[0, Infinity]]);

    expect(within(player).getAllByText("Tệp này chỉ phát từ đầu")).toHaveLength(2);
    expect(within(player).getByText("0:00 / --:--")).toBeInTheDocument();
    expect(within(player).queryByText(/Infinity|NaN/)).not.toBeInTheDocument();
    expect(within(player).getByRole("slider", { name: "Vị trí phát" })).toHaveAttribute("aria-valuetext", "0 phút 0 giây");
    expect(within(player).getByRole("button", { name: "Tiến 5 giây" })).toBeDisabled();
    await user.click(within(player).getByRole("button", { name: "Phát audio · câu 1" }));
    expect(within(player).getByRole("button", { name: "Tạm dừng audio · câu 1" })).toBeInTheDocument();
  });

  it("tua lệch đích hơn 0,3 giây (đích 5, dừng ở 4): chế độ chỉ phát từ đầu và khóa điều khiển tua", async () => {
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    loaded(element, 100);

    fireEvent.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    positions.set(element, 4);
    fireEvent.seeked(element);

    expect(within(player).getAllByText("Tệp này chỉ phát từ đầu")).toHaveLength(2);
    expect(within(player).getByRole("slider", { name: "Vị trí phát" })).toBeDisabled();
    expect(within(player).getByRole("button", { name: "Tiến 5 giây" })).toBeDisabled();
    expect(within(player).getByRole("button", { name: "Lùi 5 giây" })).toBeDisabled();
  });

  it("chuyển sang chỉ phát từ đầu khi focus đang ở điều khiển tua: focus về Phát, vùng trạng thái đọc lý do", () => {
    renderPaper(listening);
    const player = screen.getByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    loaded(element, 100);

    const forward = within(player).getByRole("button", { name: "Tiến 5 giây" });
    forward.focus();
    fireEvent.click(forward);
    positions.set(element, 4);
    fireEvent.seeked(element);

    expect(within(player).getByRole("button", { name: "Phát audio · câu 1" })).toHaveFocus();
    expect(within(player).getByRole("status")).toHaveTextContent("Tệp này chỉ phát từ đầu");
    expect(document.activeElement).not.toBe(document.body);
  });

  it("chuyển sang chỉ phát từ đầu khi focus ở nút khác: không đổi chỗ focus", () => {
    renderPaper(listening);
    const player = screen.getByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    loaded(element, 100);

    const mute = within(player).getByRole("button", { name: "Tắt tiếng · câu 1" });
    mute.focus();
    fireEvent.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    positions.set(element, 4);
    fireEvent.seeked(element);

    expect(mute).toHaveFocus();
  });

  it("máy chủ tua đúng: không báo chỉ phát từ đầu, thanh vị trí dùng được, Tiến cộng 5 giây", async () => {
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    const element = audioOf(player);
    loaded(element, 300, [[0, 300]]);

    fireEvent.click(within(player).getByRole("button", { name: "Tiến 5 giây" }));
    positions.set(element, 5.05);
    fireEvent.seeked(element);

    expect(within(player).queryByText("Tệp này chỉ phát từ đầu")).not.toBeInTheDocument();
    const slider = within(player).getByRole("slider", { name: "Vị trí phát" });
    expect(slider).toBeEnabled();
    expect(within(player).getByRole("button", { name: "Tiến 5 giây" })).toBeEnabled();
    expect(within(player).getByRole("button", { name: "Lùi 5 giây" })).toBeEnabled();
    expect(element.currentTime).toBeCloseTo(5.05, 5);
  });

  it("thanh vị trí dùng accent-primary-fill như các điều khiển gốc khác", async () => {
    renderPaper(listening);
    const player = await screen.findByRole("group", { name: "Trình phát audio · câu 1" });
    loaded(audioOf(player));

    expect(within(player).getByRole("slider", { name: "Vị trí phát" })).toHaveClass("accent-primary-fill");
  });

  it("bản en: chế độ chỉ phát từ đầu báo bằng tiếng Anh và kèm lý do ở điều khiển bị khóa", async () => {
    renderPaper(listening, "en");
    const player = await screen.findByRole("group", { name: "Audio player · question 1" });
    loaded(audioOf(player), Infinity, [[0, Infinity]]);

    expect(within(player).getAllByText("This file can only be played from the start")).toHaveLength(2);
    expect(within(player).getByRole("button", { name: "Forward 5 seconds" })).toHaveAccessibleDescription(
      "This file can only be played from the start",
    );
    expect(within(player).getByRole("slider", { name: "Playback position" })).toHaveAttribute(
      "aria-valuetext",
      "0 minutes 0 seconds",
    );
  });

  it("bản en: nhãn trình phát và thời gian đọc bằng tiếng Anh", async () => {
    renderPaper(listening, "en");
    const player = await screen.findByRole("group", { name: "Audio player · question 1" });
    loaded(audioOf(player), 100);

    expect(within(player).getByRole("button", { name: "Play audio · question 1" })).toBeInTheDocument();
    expect(within(player).getByRole("button", { name: "Back 5 seconds" })).toBeInTheDocument();
    expect(within(player).getByRole("slider", { name: "Playback position" })).toHaveAttribute(
      "aria-valuetext",
      "0 minutes 0 seconds of 1 minutes 40 seconds",
    );
  });

  it("đề không có tệp đính kèm: không có trình phát", () => {
    renderPaper(paperOf([partOf({ blocks: [block({ items: [single(question(1))] })] })]));

    expect(document.querySelectorAll("audio")).toHaveLength(0);
    expect(screen.queryByText("Có audio")).not.toBeInTheDocument();
  });
});

describe("đọc tệp đính kèm và phân đoạn từ API sinh thử", () => {
  it("chép tệp đính kèm của câu đơn, của câu gốc của chùm và phân đoạn của phần; máy chủ cũ không gửi thì rỗng", async () => {
    const wireQuestion = (number: number, attachments?: unknown) => ({ number, questionId: `q${number}`, code: `Q${number}`, type: "SINGLE_CHOICE", attachments });
    new FakeServer()
      .on("POST", `${EXAM_PATH}/preview`, {
        status: 200,
        body: {
          examId: EXAM_ID,
          status: "GENERATED",
          seed: 1,
          generatedAt: "2026-11-18T00:00:00Z",
          path: { pathId: "default", choices: [] },
          compliance: [
            { criterion: "GRADE:UNCLASSIFIED", scope: "SECTION:sec-1", required: null, actual: 3, enforcement: "REFERENCE", basis: "PROMPT", note: "UNCLASSIFIED" },
            { criterion: "GRADE:GRADE_12", scope: "SECTION:sec-1", required: 50, actual: 47, enforcement: "REFERENCE", basis: "PROMPT" },
          ],
          paper: {
            parts: [
              {
                partId: "p1",
                displaySegments: [{ code: "P1", name: "Part I", fromPosition: 1, toPosition: 2, commandCount: 8, maxPoints: 12 }],
                blocks: [
                  {
                    blockId: "b1",
                    items: [
                      { kind: "QUESTION", question: wireQuestion(1, [{ id: "a1", name: "one.mp3", mime: "audio/mpeg" }]) },
                      { kind: "SET", setId: "s1", attachments: [{ id: "a2", name: "set.mp3", mime: "audio/mpeg" }], children: [wireQuestion(2)] },
                    ],
                  },
                ],
              },
              { partId: "p2", blocks: [] },
            ],
          },
        },
      })
      .install();

    const result = await generatePreview({ examId: EXAM_ID, target: { kind: "EXAM" }, seed: 1 });

    if (result.status !== "GENERATED") throw new Error("expected GENERATED");
    expect(result.compliance.map((row) => row.required)).toEqual([null, 50]);
    expect(result.compliance[0]).toMatchObject({ criterion: "GRADE:UNCLASSIFIED", actual: 3, note: "UNCLASSIFIED" });
    const [first, second] = result.paper.parts;
    expect(first?.displaySegments).toEqual([
      { code: "P1", name: "Part I", fromPosition: 1, toPosition: 2, commandCount: 8, maxPoints: 12 },
    ]);
    expect(second?.displaySegments).toEqual([]);
    const [one, setItem] = first?.blocks[0]?.items ?? [];
    expect(one?.kind === "QUESTION" && one.question.attachments).toEqual([{ id: "a1", name: "one.mp3", mime: "audio/mpeg" }]);
    expect(setItem?.kind === "SET" && setItem.attachments).toEqual([{ id: "a2", name: "set.mp3", mime: "audio/mpeg" }]);
    expect(setItem?.kind === "SET" && setItem.children[0]?.attachments).toEqual([]);
  });
});

describe("hàng tiêu đề câu trong đề thử (V-7, V-8)", () => {
  it("chip 'Có audio' đứng ngay sau số câu, trước chip điểm và mã câu; chùm đặt chip ngay sau tên bộ", () => {
    renderPaper(listening);

    const questionArticle = screen.getByRole("heading", { name: "Câu 1" }).closest("article");
    expect(questionArticle).not.toBeNull();
    const metadataRow = questionArticle!.firstElementChild;
    expect(metadataRow?.children[0]).toHaveTextContent("Có audio");
    expect(metadataRow?.children[1]).toHaveTextContent(/điểm/);
    expect(metadataRow?.children[2]).toHaveTextContent("Q1 · v1");

    const setRow = screen.getByRole("heading", { name: "Bộ 1 · 3 câu liền nhau" }).parentElement;
    expect(setRow?.children[1]).toHaveTextContent("Có audio");
    expect(setRow?.children[2]).toHaveTextContent("SET-s1");
  });

  it("mã dài của câu và của chùm được xuống dòng trong hàng tiêu đề wrap, không tràn ngang ở khung hẹp", () => {
    const longCode = "SEED-TASK-READING_COMPREHENSION-G11-NB-46";
    renderPaper(
      paperOf([
        partOf({
          blocks: [
            block({
              items: [single(question(1, { code: longCode })), set("s1", [question(2)], { code: `SET-${longCode}` })],
            }),
          ],
        }),
      ]),
    );

    const code = screen.getByText(`${longCode} · v1`);
    expect(code).toHaveClass("whitespace-normal", "wrap-anywhere");
    expect(code.parentElement).toHaveClass("flex-wrap", "min-w-0");

    const setCode = screen.getByText(`SET-${longCode} · v1`);
    expect(setCode).toHaveClass("whitespace-normal", "wrap-anywhere");
    expect(setCode.parentElement).toHaveClass("flex-wrap", "min-w-0");
  });
});
