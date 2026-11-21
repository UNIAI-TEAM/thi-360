import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { OrderAttentionStrip } from "../components/order-attention-strip";
import type { OrderAttentionType } from "../types";
import { makeAttentionSummary, renderOrdersUi } from "./order-fixtures";

describe("OrderAttentionStrip (AC-3)", () => {
  it("hiện đủ 6 nhóm chú ý và chuyển đổi aria-pressed / border-primary-600 khi bấm", async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn<(next: "" | OrderAttentionType) => void>();

    const summary = makeAttentionSummary({
      lateSuccessPending: 2,
      paidUnfulfilled: 1,
      paymentUnknownOver24h: 3,
      duplicateUnrefunded: 4,
      needsReview: 5,
      refundUnknown: 1,
      totalAttentionOrders: 16,
    });

    const { rerender } = renderOrdersUi(
      <OrderAttentionStrip
        summary={summary}
        isLoading={false}
        isError={false}
        activeAttention=""
        onSelectAttention={onSelect}
      />,
    );

    const lateBtn = screen.getByRole("button", { name: /Tiền về muộn chờ xử lý/ });
    const paidBtn = screen.getByRole("button", { name: /Đã thu tiền chưa cấp quyền/ });
    const unknownBtn = screen.getByRole("button", { name: /Giao dịch treo quá 24 giờ/ });
    const dupBtn = screen.getByRole("button", { name: /Thanh toán dư chưa hoàn/ });
    const reviewBtn = screen.getByRole("button", { name: /Đơn cần rà soát/ });
    const refundUnknownBtn = screen.getByRole("button", { name: /Hoàn tiền chưa rõ kết quả/ });

    for (const btn of [lateBtn, paidBtn, unknownBtn, dupBtn, reviewBtn, refundUnknownBtn]) {
      expect(btn).toHaveAttribute("aria-pressed", "false");
      expect(btn.querySelector("svg")).toBeNull();
    }

    expect(lateBtn).toHaveTextContent("2");
    expect(paidBtn).toHaveTextContent("1");
    expect(unknownBtn).toHaveTextContent("3");
    expect(dupBtn).toHaveTextContent("4");
    expect(reviewBtn).toHaveTextContent("5");
    expect(refundUnknownBtn).toHaveTextContent("1");

    await user.click(dupBtn);
    expect(onSelect).toHaveBeenCalledWith("DUPLICATE_UNREFUNDED");

    rerender(
      <OrderAttentionStrip
        summary={summary}
        isLoading={false}
        isError={false}
        activeAttention="DUPLICATE_UNREFUNDED"
        onSelectAttention={onSelect}
      />,
    );

    const activeDupBtn = screen.getByRole("button", { name: /Thanh toán dư chưa hoàn/ });
    expect(activeDupBtn).toHaveAttribute("aria-pressed", "true");
    expect(activeDupBtn.className).toContain("border-primary-600");
    expect(activeDupBtn.querySelector("svg")).not.toBeNull();

    await user.click(activeDupBtn);
    expect(onSelect).toHaveBeenCalledWith("");
  });

  it("hiện thông báo ổn định khi totalAttentionOrders = 0", () => {
    renderOrdersUi(
      <OrderAttentionStrip
        summary={makeAttentionSummary({ totalAttentionOrders: 0 })}
        isLoading={false}
        isError={false}
        activeAttention=""
        onSelectAttention={vi.fn()}
      />,
    );

    expect(
      screen.getByText("Không có đơn cần xử lý. Hệ thống thanh toán và cấp quyền đang ổn định."),
    ).toBeInTheDocument();
  });
});
