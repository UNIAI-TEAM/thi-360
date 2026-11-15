import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CodeWithBreaks } from "./components/simple-items-panel";

describe("CodeWithBreaks", () => {
  it("cho xuống dòng sau dấu _ bằng <wbr>, chữ sao chép ra vẫn đúng mã gốc", () => {
    const { container } = render(
      <span>
        <CodeWithBreaks code="QDA_QD01" />
      </span>,
    );
    expect(container.textContent).toBe("QDA_QD01");
    expect(container.querySelectorAll("wbr")).toHaveLength(1);
    expect(container.innerHTML).toBe("<span>QDA_<wbr>QD01</span>");
  });

  it("mã không có dấu _ giữ nguyên", () => {
    const { container } = render(<CodeWithBreaks code="TOAN" />);
    expect(container.innerHTML).toBe("TOAN");
  });
});
