import { screen, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamPreviewPaper, sliceBySegments } from "../components/exam-preview-paper";
import type { PreviewPaper, PreviewPart } from "../review-types";
import { block, paperOf, partOf, question, segment, set, single } from "./preview-paper-fixtures";

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, index) => from + index);

/** Tiếng Anh V-SAT FULL (spec 5.2): một phần, bốn nhóm, bài đọc 8–15 bị ranh giới Phần I và II cắt giữa chùm. */
function englishVsat(withSegments = true): PreviewPart {
  return partOf({
    partId: "p-en",
    name: "English",
    lastNumber: 25,
    points: 150,
    maxPoints: 150,
    displaySegments: withSegments
      ? [
          segment("P1", "Phần I", 1, 9, 36, 54),
          segment("P2", "Phần II", 10, 15, 6, 36),
          segment("P3", "Phần III", 16, 20, 20, 30),
          segment("P4", "Phần IV", 21, 25, 5, 30),
        ]
      : undefined,
    blocks: [
      block({ blockId: "tf", blockName: "True/False", questionType: "TRUE_FALSE", items: range(1, 7).map((n) => single(question(n))) }),
      block({
        blockId: "shared",
        blockName: "Shared passage 8-15",
        questionType: "QUESTION_SET",
        setCount: 1,
        setSize: 8,
        firstNumber: 8,
        lastNumber: 15,
        questionCount: 8,
        items: [set("read", range(8, 15).map((n) => question(n)), { passage: "<p>The shared reading text</p>" })],
      }),
      block({ blockId: "match", blockName: "Matching", questionType: "MATCHING", items: range(16, 20).map((n) => single(question(n))) }),
      block({
        blockId: "cloze",
        blockName: "Cloze",
        questionType: "CLOZE",
        items: [set("cloze-1", range(21, 25).map((n) => question(n)), { passage: "<p>The cloze text</p>" })],
      }),
    ],
  });
}

function renderPaper(paper: PreviewPaper) {
  return renderWithProviders(<ExamPreviewPaper paper={paper} />);
}

async function expandAll(user: UserEvent) {
  for (const button of screen.queryAllByRole("button", { name: /^Hiện tất cả/ })) await user.click(button);
}

describe("phân đoạn hiển thị Phần I–IV (R-133, H40)", () => {
  it("có bốn tiêu đề Phần I–IV với dải câu, số lệnh và điểm khai báo", () => {
    renderPaper(paperOf([englishVsat()]));

    const headings = screen.getAllByRole("heading", { level: 4 }).filter((heading) => /^Phần /.test(heading.textContent ?? ""));
    expect(headings.map((heading) => heading.textContent?.replace(/\s+/g, " "))).toEqual([
      "Phần I · câu 1–9 · 36 lệnh · 54 điểm",
      "Phần II · câu 10–15 · 6 lệnh · 36 điểm",
      "Phần III · câu 16–20 · 20 lệnh · 30 điểm",
      "Phần IV · câu 21–25 · 5 lệnh · 30 điểm",
    ]);
  });

  it("câu 8, 9 nằm dưới Phần I, câu 10–15 dưới Phần II, bài đọc hiện ở cả hai phần", async () => {
    renderPaper(paperOf([englishVsat()]));

    const first = screen.getByRole("region", { name: /^Phần I ·/ });
    const second = screen.getByRole("region", { name: /^Phần II ·/ });
    for (const n of [8, 9]) expect(within(first).getByRole("heading", { name: `Câu ${n}` })).toBeInTheDocument();
    for (const n of range(10, 15)) expect(within(second).getByRole("heading", { name: `Câu ${n}` })).toBeInTheDocument();
    expect(within(first).queryByRole("heading", { name: "Câu 10" })).not.toBeInTheDocument();
    expect(within(second).queryByRole("heading", { name: "Câu 9" })).not.toBeInTheDocument();
    expect(within(first).getByText("The shared reading text")).toBeInTheDocument();
    expect(within(second).getByText("The shared reading text")).toBeInTheDocument();
    expect(screen.getAllByText("The shared reading text")).toHaveLength(2);
  });

  it("số câu liền từ 1 đến 25, mỗi số đúng một lần, không đánh lại theo phân đoạn", async () => {
    const user = userEvent.setup();
    renderPaper(paperOf([englishVsat()]));
    await expandAll(user);

    const numbers = screen
      .getAllByRole("heading", { name: /^Câu \d+$/ })
      .map((heading) => Number(heading.textContent?.replace("Câu ", "")));

    expect(numbers).toEqual(range(1, 25));
  });

  it("khối bị cắt đổi dải câu theo phân đoạn; khối nằm trọn trong phân đoạn giữ nguyên", () => {
    renderPaper(paperOf([englishVsat()]));

    const first = screen.getByRole("region", { name: /^Phần I ·/ });
    const second = screen.getByRole("region", { name: /^Phần II ·/ });
    expect(within(first).getByText(/Khối .*True\/False/)).toBeInTheDocument();
    expect(within(first).getByText(/câu 8–9 · 2 câu/)).toBeInTheDocument();
    expect(within(second).getByText(/câu 10–15 · 6 câu/)).toBeInTheDocument();
    expect(within(second).queryByText(/True\/False/)).not.toBeInTheDocument();
  });

  it("bài đọc hiện ở hai phân đoạn không làm trùng mã nhận dạng trong trang", () => {
    const { container } = renderPaper(paperOf([englishVsat()]));

    const ids = [...container.querySelectorAll("[id]")].map((element) => element.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("sliceBySegments: bộ bị cắt giữ câu con của từng phân đoạn, bộ nguyên vẹn giữ nguyên tham chiếu", () => {
    const part = englishVsat();

    const slices = sliceBySegments(part, false);

    expect(slices?.map((slice) => slice.segment.code)).toEqual(["P1", "P2", "P3", "P4"]);
    const cut = slices?.[1]?.blocks[0];
    expect(cut?.blockId).toBe("shared");
    expect(cut?.items[0]?.kind === "SET" && cut.items[0].children.map((child) => child.number)).toEqual(range(10, 15));
    expect(slices?.[3]?.blocks[0]).toBe(part.blocks[3]);
  });
});

describe("đề không có phân đoạn hiển thị", () => {
  it("hiện như trước: không có tiêu đề phân đoạn, và `displaySegments` rỗng giống hệt không có", () => {
    const plain = renderPaper(paperOf([englishVsat(false)]));
    const withoutHeadings = plain.container.innerHTML;
    plain.unmount();
    const empty = renderPaper(paperOf([{ ...englishVsat(false), displaySegments: [] }]));

    expect(sliceBySegments(englishVsat(false), false)).toBeNull();
    expect(sliceBySegments({ ...englishVsat(false), displaySegments: [] }, false)).toBeNull();
    expect(screen.queryByRole("region", { name: /^Phần / })).not.toBeInTheDocument();
    expect(screen.getByText(/Khối .*True\/False/)).toBeInTheDocument();
    expect(screen.getByText(/1 bộ × 8 câu · câu 8–15/)).toBeInTheDocument();
    expect(screen.getAllByText("The shared reading text")).toHaveLength(1);
    expect(empty.container.innerHTML).toBe(withoutHeadings);
  });
});
