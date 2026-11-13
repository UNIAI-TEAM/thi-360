import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StatusChip, statusTones } from "./status-chip";

describe("StatusChip", () => {
  it("năm sắc thái đều hiện chữ và mỗi sắc thái một cặp màu riêng", () => {
    render(
      <>
        {statusTones.map((tone) => (
          <StatusChip key={tone} tone={tone}>
            {`Trạng thái ${tone}`}
          </StatusChip>
        ))}
      </>,
    );
    const colorPairs = statusTones.map((tone) => {
      const chip = screen.getByText(`Trạng thái ${tone}`);
      expect(chip).toHaveAttribute("data-tone", tone);
      return `${chip.className.match(/bg-\S+-bg/)?.[0]} ${chip.className.match(/text-(success|warning|danger|info|neutral)/)?.[0]}`;
    });
    expect(new Set(colorPairs).size).toBe(5);
    expect(colorPairs).not.toContain("undefined undefined");
  });

  it("chấm màu giấu khỏi trình đọc màn hình, chữ vẫn là tên truy cập", () => {
    render(<StatusChip tone="danger">Bị khóa</StatusChip>);
    const chip = screen.getByText("Bị khóa");
    expect(chip).toHaveTextContent("Bị khóa");
    expect(chip.querySelector("[aria-hidden]")).not.toBeNull();
  });
});
