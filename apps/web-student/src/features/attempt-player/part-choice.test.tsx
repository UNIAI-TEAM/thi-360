import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import type { AfterStartChoiceView } from "./api";
import { PART_CHOICE_ERROR_CODES, PartChoiceView } from "./part-choice-view";
import { createServerClockAnchor } from "./server-clock";

describe("PartChoiceView (AC-10, AC-11, AC-12)", () => {
  it("HSA: cho chọn tối đa 3/5 môn, đếm trực tiếp 'Đã chọn n/3', khóa checkbox thừa và chỉ bật Xác nhận khi đủ 3", async () => {
    const user = userEvent.setup();
    const onConfirmChoice = vi.fn(async () => undefined);
    const clock = createServerClockAnchor(new Date().toISOString());

    const afterStartChoice: AfterStartChoiceView = {
      ord: 3,
      partCode: "SCIENCE",
      partName: "Phần 3 · Khoa học",
      options: [
        {
          code: "HSA_SCI_5",
          name: "Chọn 3 trong 5 môn",
          chooseSubjects: 3,
          countsByPick: [17, 17, 16],
          subjects: [
            { code: "PHY", nameVi: "Vật lí", nameEn: "Physics" },
            { code: "CHE", nameVi: "Hóa học", nameEn: "Chemistry" },
            { code: "BIO", nameVi: "Sinh học", nameEn: "Biology" },
            { code: "HIS", nameVi: "Lịch sử", nameEn: "History" },
            { code: "GEO", nameVi: "Địa lí", nameEn: "Geography" },
          ],
        },
      ],
    };

    renderWithProviders(
      <PartChoiceView
        part={{ ord: 3, partId: "p-3", partName: "Phần 3 · Khoa học", state: "AWAITING_CHOICE" }}
        afterStartChoice={afterStartChoice}
        locale="vi"
        serverClock={clock}
        onConfirmChoice={onConfirmChoice}
        onRetryGeneration={vi.fn(async () => undefined)}
      />,
    );

    // Single option preselected by default, never auto-confirmed
    expect(onConfirmChoice).not.toHaveBeenCalled();
    expect(screen.getByText("Số câu mỗi môn: 17/17/16")).toBeInTheDocument();
    expect(screen.getByText("Đã chọn 0/3")).toBeInTheDocument();

    const confirmBtn = screen.getByRole("button", { name: "Xác nhận chọn phần 3" });
    expect(confirmBtn).toBeDisabled();

    await user.click(screen.getByRole("checkbox", { name: "Vật lí" }));
    await user.click(screen.getByRole("checkbox", { name: "Hóa học" }));
    expect(screen.getByText("Đã chọn 2/3")).toBeInTheDocument();
    expect(confirmBtn).toBeDisabled();

    await user.click(screen.getByRole("checkbox", { name: "Sinh học" }));
    expect(screen.getByText("Đã chọn 3/3")).toBeInTheDocument();
    expect(confirmBtn).toBeEnabled();

    // Unselected 4th and 5th checkboxes are blocked
    expect(screen.getByRole("checkbox", { name: "Lịch sử" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Địa lí" })).toBeDisabled();

    await user.click(confirmBtn);
    expect(onConfirmChoice).toHaveBeenCalledWith("HSA_SCI_5", ["PHY", "CHE", "BIO"]);
  });

  it("QDA: hiển thị mã, tên và môn theo thứ tự qua pickName theo locale, chỉ hiện Xác nhận khi chọn xong", async () => {
    const user = userEvent.setup();
    const onConfirmChoice = vi.fn(async () => undefined);
    const clock = createServerClockAnchor(new Date().toISOString());

    const afterStartChoice: AfterStartChoiceView = {
      ord: 3,
      options: [
        {
          code: "Q01",
          name: "Tổ hợp Q01",
          subjects: [
            { code: "PHY", nameVi: "Vật lí", nameEn: "Physics" },
            { code: "CHE", nameVi: "Hóa học", nameEn: "Chemistry" },
          ],
        },
        {
          code: "Q02",
          name: "Tổ hợp Q02",
          subjects: [
            { code: "BIO", nameVi: "Sinh học", nameEn: "Biology" },
            { code: "HIS", nameVi: "Lịch sử", nameEn: "History" },
          ],
        },
      ],
    };

    renderWithProviders(
      <PartChoiceView
        part={{ ord: 3, partId: "p-3", state: "AWAITING_CHOICE" }}
        afterStartChoice={afterStartChoice}
        locale="en"
        serverClock={clock}
        onConfirmChoice={onConfirmChoice}
        onRetryGeneration={vi.fn(async () => undefined)}
      />,
      { locale: "en", messages: enMessages },
    );

    expect(screen.getByText("Q01")).toBeInTheDocument();
    expect(screen.getByText("1. Physics")).toBeInTheDocument();
    expect(screen.getByText("2. Chemistry")).toBeInTheDocument();

    const confirmBtn = screen.getByRole("button", { name: "Confirm section 3 choice" });
    expect(confirmBtn).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: /Q02/ }));
    expect(confirmBtn).toBeEnabled();
    await user.click(confirmBtn);
    expect(onConfirmChoice).toHaveBeenCalledWith("Q02", undefined);
  });

  it("render trạng thái GENERATING, GENERATION_FAILED/GEN_FAILED với nút thử lại và ánh xạ đủ mã lỗi", async () => {
    const onRetry = vi.fn(async () => undefined);
    const clock = createServerClockAnchor(new Date().toISOString());

    const view = renderWithProviders(
      <PartChoiceView
        part={{ ord: 3, partId: "p-3", state: "GENERATING" }}
        afterStartChoice={null}
        locale="vi"
        serverClock={clock}
        onConfirmChoice={vi.fn(async () => undefined)}
        onRetryGeneration={onRetry}
      />,
    );

    expect(screen.getByRole("status")).toHaveTextContent("Đang chuẩn bị bộ câu hỏi cho phần thi");

    view.rerender(
      <PartChoiceView
        part={{ ord: 3, partId: "p-3", state: "GENERATION_FAILED" }}
        afterStartChoice={null}
        locale="vi"
        serverClock={clock}
        onConfirmChoice={vi.fn(async () => undefined)}
        onRetryGeneration={onRetry}
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Chưa chuẩn bị được phần thi");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(onRetry).toHaveBeenCalledTimes(1);

    for (const code of PART_CHOICE_ERROR_CODES) {
      view.rerender(
        <PartChoiceView
          part={{ ord: 3, partId: "p-3", state: "AWAITING_CHOICE" }}
          afterStartChoice={{ ord: 3, options: [{ code: "Q01", name: "Opt 1" }] }}
          locale="vi"
          serverClock={clock}
          errorCode={code}
          onConfirmChoice={vi.fn(async () => undefined)}
          onRetryGeneration={onRetry}
        />,
      );
      expect(screen.getByRole("alert")).not.toBeEmptyDOMElement();
    }
  });
});
