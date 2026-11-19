import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { accessRow } from "@/test/storefront-fixtures";
import { renderWithProviders } from "@/test/render";
import { AccessAction } from "./access-action";
import type { AccessView } from "./exam-access";

const PRODUCT = { code: "HSA-FULL-01", versionNo: 3, price: 199000 };
const CHECKOUT = "/checkout/HSA-FULL-01?v=3&p=199000";

function renderAction(view: AccessView, variant: "card" | "detail" = "detail", onReload = vi.fn()) {
  renderWithProviders(<AccessAction product={PRODUCT} view={view} variant={variant} onReload={onReload} />);
  return onReload;
}

const ready = (overrides: Parameters<typeof accessRow>[0]): AccessView => ({ kind: "ready", row: accessRow(overrides) });

describe("AccessAction · bảng nút TM 11.3", () => {
  it("khách: Mua bài thi dẫn tới /login kèm callbackUrl là đường dẫn xác nhận mua, còn v và p", () => {
    renderAction({ kind: "guest" });

    const href = screen.getByRole("link", { name: "Mua bài thi" }).getAttribute("href")!;
    expect(href).toBe(`/login?callbackUrl=${encodeURIComponent(CHECKOUT)}`);
    expect(new URL(href, "http://x").searchParams.get("callbackUrl")).toBe(CHECKOUT);
  });

  it("đang tải quyền: giữ chỗ cố định, chưa có nút Mua (AC-5)", () => {
    renderAction({ kind: "loading" });

    expect(screen.getByRole("status", { name: "Đang tải quyền" })).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("link", { name: /Mua/ })).toBeNull();
    expect(screen.queryByRole("button")).toBeNull();
  });

  it("lỗi tải quyền: báo lỗi và có nút Tải lại quyền, vẫn chưa có nút Mua (AC-5)", async () => {
    const onReload = renderAction({ kind: "error" });

    expect(screen.getByRole("alert")).toHaveTextContent("Chưa xác định được quyền của tài khoản.");
    expect(screen.queryByRole("link", { name: /Mua/ })).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Tải lại quyền" }));
    expect(onReload).toHaveBeenCalledTimes(1);
  });

  it("quản trị viên: không có nút chính, ở trang chi tiết có ghi chú", () => {
    renderAction({ kind: "admin" });

    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText(/không có nút mua hay vào bài thi/)).toBeInTheDocument();
  });

  it("NONE: Mua bài thi tới /checkout/<code>?v=&p=", () => {
    renderAction(ready({ state: "NONE" }));

    expect(screen.getByRole("link", { name: "Mua bài thi" })).toHaveAttribute("href", CHECKOUT);
  });

  it("CAN_START: Vào bài thi tới /prepare/<entitlementId>, kèm Mua thêm lượt và số lượt còn lại", () => {
    renderAction(ready({ state: "CAN_START", attemptsLeft: 2, entitlementId: "ent-1" }));

    expect(screen.getByRole("link", { name: "Vào bài thi" })).toHaveAttribute("href", "/prepare/ent-1");
    expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toHaveAttribute("href", CHECKOUT);
    expect(screen.getByText("Còn 2 lượt")).toBeInTheDocument();
  });

  it("IN_PROGRESS: Tiếp tục tới lượt đang làm, vẫn có Mua thêm lượt kể cả khi hết lượt", () => {
    renderAction(ready({ state: "IN_PROGRESS", attemptsLeft: 0, activeAttemptId: "att-1" }));

    expect(screen.getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att-1");
    expect(screen.getByRole("link", { name: "Mua thêm lượt" })).toBeInTheDocument();
  });

  it("EXHAUSTED: Mua lại", () => {
    renderAction(ready({ state: "EXHAUSTED" }));

    expect(screen.getByRole("link", { name: "Mua lại" })).toHaveAttribute("href", CHECKOUT);
    expect(screen.getByText("Bạn đã dùng hết lượt của bài thi này.")).toBeInTheDocument();
  });

  it("CHECKING: nút Đang kiểm tra không bấm được, hiện mã đơn, không đề nghị mua", () => {
    renderAction(ready({ state: "CHECKING", pendingOrderCode: "DH261117AB12CD" }));

    expect(screen.getByRole("button", { name: "Đang kiểm tra" })).toBeDisabled();
    expect(screen.getByText(/DH261117AB12CD/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Mua/ })).toBeNull();
  });

  it("CAN_START kèm hasPendingFulfillment: hiện đang kiểm tra kèm mã đơn, không đề nghị thanh toán lại (AC-7)", () => {
    renderAction(
      ready({
        state: "CAN_START",
        attemptsLeft: 1,
        entitlementId: "ent-1",
        hasPendingFulfillment: true,
        pendingOrderCode: "DH261117Q4N8T2",
      }),
    );

    expect(screen.getByRole("link", { name: "Vào bài thi" })).toHaveAttribute("href", "/prepare/ent-1");
    expect(screen.getByText(/DH261117Q4N8T2/)).toHaveTextContent("đang được kiểm tra");
    expect(screen.queryByRole("link", { name: "Mua thêm lượt" })).toBeNull();
    expect(screen.queryByRole("link", { name: /Mua/ })).toBeNull();
  });

  it.each<[string, AccessView]>([
    ["khách", { kind: "guest" }],
    ["quản trị viên", { kind: "admin" }],
    ["NONE", ready({ state: "NONE" })],
    ["CHECKING", ready({ state: "CHECKING", pendingOrderCode: "DH1" })],
    ["EXHAUSTED", ready({ state: "EXHAUSTED" })],
  ])("thẻ ở danh mục có thêm nút phụ Xem chi tiết ở mọi trạng thái: %s", (_name, view) => {
    renderAction(view, "card");

    expect(screen.getByRole("link", { name: "Xem chi tiết" })).toHaveAttribute("href", "/exams/HSA-FULL-01");
  });

  it("trang chi tiết không lặp nút Xem chi tiết", () => {
    renderAction({ kind: "guest" }, "detail");

    expect(screen.queryByRole("link", { name: "Xem chi tiết" })).toBeNull();
  });
});
