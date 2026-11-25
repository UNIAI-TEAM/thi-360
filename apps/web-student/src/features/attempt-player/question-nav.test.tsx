import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { QuestionNav, type QuestionNavItem } from "./question-nav";

const SAMPLE_ITEMS: QuestionNavItem[] = [
  { itemId: "q-16", displayNo: 16, unitIndex: 0, completion: "complete", flagged: false, unsaved: false, isCurrent: false },
  { itemId: "q-17", displayNo: 17, unitIndex: 1, completion: "partial", flagged: true, unsaved: true, isCurrent: true },
  { itemId: "q-18", displayNo: 18, unitIndex: 2, completion: "blank", flagged: false, unsaved: false, isCurrent: false },
  { itemId: "q-19", displayNo: 19, unitIndex: 3, completion: "complete", flagged: true, unsaved: true, isCurrent: false },
];

describe("QuestionNav (AC-8, AC-16)", () => {
  it("hiển thị đánh số liên tục, phân biệt rõ đang đứng/đã làm/một phần/chưa làm, badge chờ lưu và cờ xem lại", async () => {
    const user = userEvent.setup();
    const onSelectUnit = vi.fn();

    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={1}
        totalUnits={4}
        currentDisplayNumber={17}
        totalDisplayItems={4}
        onSelectUnit={onSelectUnit}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={true}
        canNext={true}
      />,
    );

    const btn16 = screen.getByRole("button", { name: /^16,/ });
    const btn17 = screen.getByRole("button", { name: /^17,/ });
    const btn18 = screen.getByRole("button", { name: /^18,/ });
    const btn19 = screen.getByRole("button", { name: /^19,/ });

    // Current marker
    expect(btn17).toHaveAttribute("aria-current", "step");
    expect(btn16).not.toHaveAttribute("aria-current");

    // 3 completion levels
    expect(btn16).toHaveAttribute("data-completion", "complete");
    expect(btn17).toHaveAttribute("data-completion", "partial");
    expect(btn18).toHaveAttribute("data-completion", "blank");

    // Unsaved queue badge separate from completion status (AC-16)
    expect(btn16).toHaveAttribute("data-unsaved", "false");
    expect(btn17).toHaveAttribute("data-unsaved", "true");
    expect(btn19).toHaveAttribute("data-unsaved", "true");
    expect(btn19.getAttribute("aria-label")).toContain("chưa lưu");
    expect(btn16.getAttribute("aria-label")).toContain("đã lưu");

    // Flagged marker
    expect(btn17).toHaveAttribute("data-flagged", "true");
    expect(btn18).toHaveAttribute("data-flagged", "false");

    // Legend shows distinct labels
    expect(screen.getByText("Đang xem")).toBeInTheDocument();
    expect(screen.getByText("Chưa lưu")).toBeInTheDocument();
    expect(screen.getByText("Trả lời một phần")).toBeInTheDocument();

    // Filter Unanswered keeps blank + partial (17, 18), excludes answered (16, 19)
    await user.click(screen.getByRole("button", { name: "Chưa làm" }));
    expect(screen.queryByRole("button", { name: /^16,/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^17,/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^18,/ })).toBeInTheDocument();

    // Filter Flagged keeps 17 and 19
    await user.click(screen.getByRole("button", { name: "Đánh dấu" }));
    expect(screen.queryByRole("button", { name: /^18,/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^17,/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^19,/ })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^19,/ }));
    expect(onSelectUnit).toHaveBeenCalledWith(3);
  });

  it("chỉ render một bản DOM QuestionNavGrid dù mở ngăn điều hướng mobile", async () => {
    const user = userEvent.setup();
    const onSelectUnit = vi.fn();

    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={0}
        totalUnits={4}
        currentDisplayNumber={16}
        totalDisplayItems={4}
        onSelectUnit={onSelectUnit}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={false}
        canNext={true}
      />,
    );

    // Desktop sidebar holds single instance of question 16 button
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);

    // Opening mobile drawer unmounts desktop grid and mounts modal dialog grid (still single DOM)
    await user.click(screen.getByRole("button", { name: /Danh sách câu 16\/4/ }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);

    // Clicking an item inside dialog closes drawer and invokes onSelectUnit
    await user.click(screen.getByRole("button", { name: /^18,/ }));
    expect(onSelectUnit).toHaveBeenCalledWith(2);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /^16,/ })).toHaveLength(1);
  });

  it("render children trong cột trái ngay trên thanh điều hướng gọn và giữ aside ở cột phải (VIS-R3-3)", () => {
    const { container } = renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={1}
        totalUnits={4}
        currentDisplayNumber={17}
        totalDisplayItems={4}
        onSelectUnit={vi.fn()}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={true}
        canNext={true}
        footer={<p>Gợi ý phím tắt</p>}
      >
        <div data-testid="question-card">Thân câu hỏi</div>
      </QuestionNav>,
    );

    const layoutRow = container.firstElementChild as HTMLElement;
    expect(layoutRow).toHaveClass("lg:flex-row");

    const leftColumn = layoutRow.children[0] as HTMLElement;
    const aside = screen.getByRole("complementary", { name: "Danh sách câu hỏi" });
    expect(layoutRow.children).toHaveLength(2);
    expect(layoutRow.children[1]).toBe(aside);

    const card = screen.getByTestId("question-card");
    const controls = screen.getByRole("region", { name: "Điều hướng câu hỏi" });
    expect(leftColumn).toContainElement(card);
    expect(leftColumn).toContainElement(controls);
    expect(leftColumn).toContainElement(screen.getByText("Gợi ý phím tắt"));

    // Compact mobile trigger keeps full accessible name while showing compact label; Prev/Next buttons removed (UNI-2813)
    const gridBtn = screen.getByRole("button", { name: "Danh sách câu 17/4" });
    expect(gridBtn).toHaveTextContent("17/4");
    expect(controls).toHaveClass("rounded-xl", "border", "flex", "items-center", "justify-between", "lg:hidden");
    expect(screen.queryByRole("button", { name: "Câu trước" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Câu sau" })).not.toBeInTheDocument();
  });

  it("đổi màu ô/viền đỏ (danger tokens) cho câu đánh dấu cần xem lại ở cả 3 mức hoàn thành và chú thích (UNI-2794)", () => {
    const items: QuestionNavItem[] = [
      { itemId: "q-1", displayNo: 1, unitIndex: 0, completion: "complete", flagged: false, unsaved: false, isCurrent: false },
      { itemId: "q-2", displayNo: 2, unitIndex: 1, completion: "blank", flagged: true, unsaved: false, isCurrent: false },
      { itemId: "q-3", displayNo: 3, unitIndex: 2, completion: "partial", flagged: true, unsaved: false, isCurrent: true },
      { itemId: "q-4", displayNo: 4, unitIndex: 3, completion: "complete", flagged: true, unsaved: false, isCurrent: false },
    ];

    const { container } = renderWithProviders(
      <QuestionNav
        items={items}
        currentUnitIndex={2}
        totalUnits={4}
        currentDisplayNumber={3}
        totalDisplayItems={4}
        onSelectUnit={vi.fn()}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={true}
        canNext={true}
      />,
    );

    const btnUnflaggedAnswered = screen.getByRole("button", { name: /^1,/ });
    const btnFlaggedBlank = screen.getByRole("button", { name: /^2,/ });
    const btnFlaggedPartial = screen.getByRole("button", { name: /^3,/ });
    const btnFlaggedComplete = screen.getByRole("button", { name: /^4,/ });

    // Unflagged answered tile keeps primary green tokens
    expect(btnUnflaggedAnswered).toHaveClass("border-primary-600", "bg-primary-fill", "text-on-primary");
    expect(btnUnflaggedAnswered).not.toHaveClass("border-danger");

    // Blank + flagged: red tinted cell & solid red border
    expect(btnFlaggedBlank).toHaveClass("border-danger", "bg-danger-bg", "text-danger");
    expect(btnFlaggedBlank).not.toHaveClass("bg-surface");

    // Partial + flagged: red tinted cell & dashed red border + current focus ring
    expect(btnFlaggedPartial).toHaveClass("border-danger", "border-dashed", "bg-danger-bg", "text-danger", "ring-2");
    expect(btnFlaggedPartial).not.toHaveClass("bg-primary-50");

    // Complete + flagged: solid danger red cell & white text/icon
    expect(btnFlaggedComplete).toHaveClass("border-danger", "bg-danger-solid", "text-on-danger");
    expect(btnFlaggedComplete).not.toHaveClass("bg-primary-fill");

    // Legend swatch matches danger red cell + border + Flag icon
    const legendSwatch = container.querySelector('dt[data-legend="flagged"]');
    expect(legendSwatch).not.toBeNull();
    expect(legendSwatch).toHaveClass("border-danger", "bg-danger-bg", "text-danger");
    expect(legendSwatch?.querySelector("svg")).not.toBeNull();
  });

  it("ghim cột Danh sách câu hỏi khi cuộn trên màn rộng kèm cuộn dọc nội bộ và báo onSelectItem (UNI-2793)", async () => {
    const user = userEvent.setup();
    const onSelectUnit = vi.fn();
    const onSelectItem = vi.fn();

    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        currentUnitIndex={0}
        totalUnits={4}
        currentDisplayNumber={16}
        totalDisplayItems={4}
        onSelectUnit={onSelectUnit}
        onSelectItem={onSelectItem}
        onPrev={vi.fn()}
        onNext={vi.fn()}
        canPrev={false}
        canNext={true}
      >
        <div>Nội dung bài làm dài</div>
      </QuestionNav>,
    );

    const aside = screen.getByRole("complementary", { name: "Danh sách câu hỏi" });
    expect(aside).toHaveClass(
      "lg:sticky",
      "lg:top-[var(--attempt-sticky-top,7rem)]",
      "lg:w-[282px]",
      "min-[1440px]:w-[300px]",
      "lg:max-h-[calc(100dvh-var(--attempt-sticky-top,7rem)-1.5rem)]",
      "lg:overflow-y-auto",
      "scroll-shadows-y",
    );
    expect(aside.parentElement).toHaveClass("gap-5", "min-[1440px]:gap-6");

    await user.click(screen.getByRole("button", { name: /^19,/ }));
    expect(onSelectUnit).toHaveBeenCalledWith(3);
    expect(onSelectItem).toHaveBeenCalledWith(expect.objectContaining({ itemId: "q-19", unitIndex: 3 }));
  });

  it("nhóm theo môn học không gắn data-exam-content khi tên môn đã dịch theo giao diện (UNI-2813)", () => {
    renderWithProviders(
      <QuestionNav
        items={SAMPLE_ITEMS}
        segments={[
          { code: "LY-0", name: "Vật lí", fromPosition: 16, toPosition: 17 },
          { code: "CUSTOM-1", name: "Chuyên đề riêng", fromPosition: 18, toPosition: 19 },
        ]}
        segmentsAreExamContent={false}
        examContentSegmentCodes={new Set(["CUSTOM-1"])}
        currentUnitIndex={0}
        totalUnits={4}
        currentDisplayNumber={16}
        totalDisplayItems={4}
        onSelectUnit={vi.fn()}
      />,
    );

    const aside = screen.getByRole("complementary", { name: "Danh sách câu hỏi" });
    expect(aside.querySelector('[role="group"] > div')).not.toHaveAttribute("data-exam-content");
    expect(screen.getByText("Vật lí")).not.toHaveAttribute("data-exam-content");
    expect(screen.getByText("Chuyên đề riêng")).toHaveAttribute("data-exam-content", "");
  });
});
