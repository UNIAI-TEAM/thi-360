import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import type { AttemptPartStateView } from "./mappers";
import { PartNavigation } from "./part-navigation";

const SAMPLE_PARTS: AttemptPartStateView[] = [
  { ord: 1, partId: "p-1", partName: "Phần 1 · Toán học", state: "OPEN" },
  { ord: 2, partId: "p-2", partName: "Phần 2 · Văn học", state: "OPEN" },
  { ord: 3, partId: "p-3", partName: "Phần 3 · Khoa học", state: "AWAITING_CHOICE" },
  { ord: 4, partId: "p-4", partName: "Phần 4 · Tổng hợp", state: "CLOSED" },
];

describe("PartNavigation (AC-34)", () => {
  it("FREE: render tablist và cho nhảy tự do giữa các phần OPEN, khóa phần chưa mở", async () => {
    const user = userEvent.setup();
    const onSelectPart = vi.fn();

    renderWithProviders(
      <PartNavigation
        parts={SAMPLE_PARTS}
        currentPartOrd={1}
        navigationMode="FREE"
        choiceMap={{ 3: { name: "Khoa học · Vật lý + Hóa" } }}
        onSelectPart={onSelectPart}
      />,
    );

    const tablist = screen.getByRole("tablist");
    expect(tablist).toBeInTheDocument();

    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(4);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(tabs[0]).toHaveClass("after:border-b-2", "pointer-coarse:min-h-11", "pointer-coarse:min-w-11");
    expect(tabs[1]).toBeEnabled();
    expect(tabs[2]).toBeDisabled();
    expect(tabs[3]).toBeDisabled();

    // Part 3 shows resolved choice title and keeps data-exam-content
    expect(screen.getByText("Khoa học · Vật lý + Hóa")).toHaveAttribute("data-exam-content", "");

    // Clicking an OPEN tab invokes onSelectPart(2)
    await user.click(tabs[1]!);
    expect(onSelectPart).toHaveBeenCalledWith(2);

    // ArrowRight on focused tab moves selection and focus to next OPEN part
    tabs[0]!.focus();
    await user.keyboard("{ArrowRight}");
    expect(onSelectPart).toHaveBeenCalledWith(2);
    expect(tabs[1]).toHaveFocus();
  });

  it("SEQUENTIAL: chỉ render stepper không bấm nhảy (no tablist / no tab)", async () => {
    const user = userEvent.setup();
    const onSelectPart = vi.fn();

    renderWithProviders(
      <PartNavigation
        parts={[
          { ord: 1, partId: "p-1", partName: "Phần 1 · Toán học", state: "CLOSED" },
          { ord: 2, partId: "p-2", partName: "Phần 2 · Ngữ văn", state: "OPEN" },
          { ord: 3, partId: "p-3", partName: "Phần 3 · Tự chọn", state: "PENDING" },
        ]}
        currentPartOrd={2}
        navigationMode="SEQUENTIAL"
        onSelectPart={onSelectPart}
      />,
    );

    expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
    expect(screen.queryAllByRole("tab")).toHaveLength(0);

    const currentStep = screen.getByRole("listitem", { current: "step" });
    expect(currentStep).toHaveTextContent("Phần 2 · Ngữ văn");

    // Clicking list items in SEQUENTIAL mode never calls onSelectPart
    await user.click(screen.getByText("Phần 1 · Toán học"));
    await user.click(screen.getByText("Phần 3 · Tự chọn"));
    expect(onSelectPart).not.toHaveBeenCalled();
  });
});
