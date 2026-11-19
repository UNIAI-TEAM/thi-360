import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UI_STRINGS_VI } from "../../messages/ui-strings";
import type { KitQuestion } from "../types";
import { cloze, Live, matching, trueFalse } from "./fixtures";

const t = UI_STRINGS_VI.question;

describe("Kết quả từng ý có chữ cho trình đọc màn hình (R1-19)", () => {
  it("Đúng/Sai: mỗi hàng báo Đúng/Sai của ý, không chỉ bằng icon và màu", () => {
    const { container } = render(
      <Live
        question={trueFalse}
        mode="review"
        initial={{ statements: { s1: true, s2: true } }}
        review={{
          outcome: "PARTIAL",
          earned: 0.25,
          max: 0.5,
          gradingDetail: {
            s1: { key: "s1", outcome: "CORRECT", earned: 0.25, max: 0.25 },
            s2: { key: "s2", outcome: "WRONG", earned: 0, max: 0.25 },
          },
        }}
      />,
    );
    const rows = [...container.querySelectorAll("tbody tr")];
    expect(rows.map((row) => row.querySelector(".sr-only")?.textContent)).toEqual([t.outcomes.CORRECT, t.outcomes.WRONG]);
  });

  it("Điền khuyết: ô trong đoạn và hàng bảng đều có chữ kết quả", async () => {
    const { container } = render(
      <Live
        question={cloze}
        mode="review"
        initial={{ blanks: { b1: "100", b2: "5" } }}
        review={{
          outcome: "PARTIAL",
          earned: 1,
          max: 2,
          gradingDetail: {
            b1: { key: "b1", outcome: "CORRECT", earned: 1, max: 1 },
            b2: { key: "b2", outcome: "WRONG", earned: 0, max: 1 },
          },
        }}
      />,
    );
    await waitFor(() => expect(container.querySelectorAll("[data-cloze-slot] .sr-only")).toHaveLength(2));
    expect([...container.querySelectorAll("tbody tr")].map((row) => row.querySelector(".sr-only")?.textContent)).toEqual([
      t.outcomes.CORRECT,
      t.outcomes.WRONG,
    ]);
  });

  it("Ghép đôi: mỗi hàng báo kết quả của cặp", () => {
    const { container } = render(
      <Live
        question={matching}
        mode="review"
        initial={{ pairs: { l1: "r1", l2: "r1" } }}
        review={{
          outcome: "PARTIAL",
          earned: 0.5,
          max: 1,
          gradingDetail: {
            l1: { key: "l1", outcome: "CORRECT", earned: 0.5, max: 0.5 },
            l2: { key: "l2", outcome: "WRONG", earned: 0, max: 0.5 },
          },
        }}
      />,
    );
    const rows = [...container.querySelectorAll("tbody tr")];
    expect(rows.map((row) => row.querySelector(".sr-only")?.textContent)).toEqual([t.outcomes.CORRECT, t.outcomes.WRONG]);
  });
});

describe("Ghép đôi: điểm tối đa của cặp khi thiếu grading_detail.max (R1-20)", () => {
  it("lấy điểm của cặp, không mặc định 1", () => {
    const quarter: KitQuestion = {
      ...matching,
      body: {
        ...matching.body,
        pairs: [
          { leftId: "l1", rightId: "r1", points: 0.25 },
          { leftId: "l2", rightId: "r2", points: 0.25 },
        ],
      } as KitQuestion["body"],
    };
    render(
      <Live
        question={quarter}
        mode="review"
        initial={{ pairs: { l1: "r1", l2: "r1" } }}
        review={{
          outcome: "PARTIAL",
          earned: 0.25,
          max: 0.5,
          gradingDetail: {
            l1: { key: "l1", outcome: "CORRECT", earned: 0.25 },
            l2: { key: "l2", outcome: "WRONG", earned: 0 },
          },
        }}
      />,
    );
    expect(screen.getByText(t.points("0,25", "0,25"))).toBeInTheDocument();
    expect(screen.getByText(t.points("0", "0,25"))).toBeInTheDocument();
  });
});
