import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import { ThinkingSubsections } from "./thinking-subsections";

const TWO_SUBSECTIONS = [
  {
    sectionId: "sec-logic",
    questionCount: 12,
    correctCount: 10,
    totalCount: 12,
    correctRate: 83.33,
  },
  {
    sectionId: "sec-science",
    questionCount: 18,
    correctCount: 12,
    totalCount: 18,
    correctRate: 66.67,
  },
];

describe("ThinkingSubsections · AC-22", () => {
  it("không render nút mở rộng khi không có subsections hoặc danh sách rỗng", () => {
    const emptyUndefined = renderWithProviders(
      <ThinkingSubsections componentCode="VIETNAMESE" subsections={undefined} />,
    );
    expect(emptyUndefined.container).toBeEmptyDOMElement();
    emptyUndefined.unmount();

    const emptyArray = renderWithProviders(
      <ThinkingSubsections componentCode="TOAN" subsections={[]} />,
    );
    expect(emptyArray.container).toBeEmptyDOMElement();
  });

  it("mở/đóng 2 tiểu mục 12 câu và 18 câu bằng click và bàn phím (Enter/Space) trên locale vi", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ThinkingSubsections componentCode="SCIENTIFIC_THINKING" subsections={TWO_SUBSECTIONS} />,
      { locale: "vi", messages: viMessages },
    );

    const toggle = screen.getByRole("button", { name: /Xem riêng 2 tiểu mục/i });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    const panelId = toggle.getAttribute("aria-controls");
    expect(panelId).toBeTruthy();
    expect(screen.queryByText("Logic và phân tích số liệu")).not.toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const list = document.getElementById(panelId!);
    expect(list).toBeInTheDocument();
    expect(screen.getByText("Logic và phân tích số liệu")).toBeInTheDocument();
    expect(screen.getByText("12 câu")).toBeInTheDocument();
    expect(screen.getByText(/10\/12 câu đúng · 83,33%/)).toBeInTheDocument();
    expect(screen.getByText("Suy luận khoa học")).toBeInTheDocument();
    expect(screen.getByText("18 câu")).toBeInTheDocument();
    expect(screen.getByText(/12\/18 câu đúng · 66,67%/)).toBeInTheDocument();

    toggle.focus();
    await user.keyboard("{Enter}");
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("Logic và phân tích số liệu")).not.toBeInTheDocument();

    await user.keyboard(" ");
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("Logic và phân tích số liệu")).toBeInTheDocument();
  });

  it("hiển thị đúng bản dịch tiếng Anh trên locale en", async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <ThinkingSubsections componentCode="SCIENTIFIC_THINKING" subsections={TWO_SUBSECTIONS} />,
      { locale: "en", messages: enMessages },
    );

    const toggle = screen.getByRole("button", { name: /Show 2 subsections/i });
    await user.click(toggle);

    expect(screen.getByText("Logic and data analysis")).toBeInTheDocument();
    expect(screen.getByText("12 questions")).toBeInTheDocument();
    expect(screen.getByText(/10\/12 correct · 83\.33%/)).toBeInTheDocument();
    expect(screen.getByText("Scientific reasoning")).toBeInTheDocument();
    expect(screen.getByText("18 questions")).toBeInTheDocument();
    expect(screen.getByText(/12\/18 correct · 66\.67%/)).toBeInTheDocument();
  });
});
