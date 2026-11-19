import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => `/exams/${EXAM_ID}/review`,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const CHECK_PATH = `${EXAM_PATH}/check`;

type ListingIssue = { code: string; severity?: "ERROR" | "WARNING"; field: string; minPriceVnd?: number };

const issue = (code: string, field: string, extra: Partial<ListingIssue> = {}) => ({
  key: `LISTING:${code}`,
  code,
  severity: "ERROR" as const,
  group: "LISTING",
  field,
  ...extra,
});

/** Kết quả kiểm tra đạt: tám nhóm cấu hình trong `groups[]` và nhóm Thông tin bán ở trường riêng `listingCheck`. */
function checkWire(listing: ReturnType<typeof issue>[] | null, extra: Record<string, unknown> = {}) {
  const status = listing === null ? undefined : listing.some((item) => item.severity === "ERROR") ? "FAIL" : listing.length > 0 ? "WARN" : "PASS";
  return {
    ...checkResultWire("VALID"),
    groups: ["INFO", "STRUCTURE", "MATRIX", "SOURCE", "QUESTION_SET", "OVERLAP", "POINTS", "DISPLAY"].map((group) => ({ group, status: "PASS", issues: [] })),
    ...(listing === null ? {} : { listingCheck: { group: "LISTING", status, issues: listing } }),
    ...extra,
  };
}

function renderCheck(wire: unknown) {
  new FakeServer().on("GET", CHECK_PATH, { status: 200, body: wire }).install();
  return renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);
}

const listingSection = async () => {
  await screen.findByRole("heading", { name: "Kết quả theo đường lựa chọn" });
  const section = document.getElementById("review-listing");
  if (!section) throw new Error("không có nhóm Thông tin bán");
  return within(section);
};

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("tab Kiểm tra: nhóm Thông tin bán", () => {
  it.each([
    ["LISTING_PRICE_MISSING", "listPriceVnd", "Chưa nhập giá bán.", "Giá bán"],
    ["LISTING_PRICE_ZERO", "listPriceVnd", "Giá bán phải lớn hơn 0 đồng. Bài thi chỉ miễn phí qua voucher.", "Giá bán"],
    ["LISTING_ATTEMPTS_MISSING", "attemptsGranted", "Chưa nhập số lượt mỗi lần mua.", "Số lượt mỗi lần mua"],
    ["LISTING_DESCRIPTION_MISSING", "description", "Chưa có mô tả.", "Mô tả"],
  ])("%s là lỗi chặn: hiện đúng hàng của nhóm, nhóm báo 1 lỗi", async (code, field, text, rowName) => {
    renderCheck(checkWire([issue(code, field)]));
    const group = await listingSection();

    expect(group.getByRole("heading", { name: "Thông tin bán" })).toBeInTheDocument();
    expect(group.getByText("1 lỗi")).toBeInTheDocument();
    const row = group.getByText(rowName, { selector: "span.font-semibold" }).closest("li")!;
    expect(within(row).getByText(text)).toBeInTheDocument();
    expect(within(row).getByText("Lỗi")).toBeInTheDocument();
    // Các hàng còn lại đạt.
    expect(group.getAllByText("Đạt")).toHaveLength(3);
  });

  it("LISTING_PRICE_BELOW_MIN nêu mức tối thiểu máy chủ trả, không viết cứng", async () => {
    renderCheck(checkWire([issue("LISTING_PRICE_BELOW_MIN", "listPriceVnd", { minPriceVnd: 12000 })]));
    const group = await listingSection();
    expect(group.getByText("Giá bán thấp hơn mức tối thiểu 12.000 đ của cổng thanh toán.")).toBeInTheDocument();
  });

  it("thiếu ảnh bìa chỉ là cảnh báo: không chặn công bố", async () => {
    renderCheck(checkWire([issue("LISTING_COVER_MISSING", "coverAssetId", { severity: "WARNING" })]));
    const group = await listingSection();

    expect(group.getByText("1 cảnh báo")).toBeInTheDocument();
    const row = group.getByText("Ảnh bìa", { selector: "span.font-semibold" }).closest("li")!;
    expect(within(row).getByText("Cảnh báo")).toBeInTheDocument();
    expect(within(row).getByText("Chưa có ảnh bìa; thẻ dùng ảnh mặc định. Không chặn công bố.")).toBeInTheDocument();
    expect(group.queryByText("Lỗi")).not.toBeInTheDocument();
    // Banner tổng vẫn cho công bố.
    expect(screen.getByText("Công bố được. Không có lỗi chặn.")).toBeInTheDocument();
  });

  it("đủ điều kiện: cả bốn hàng Đạt, nhóm Đạt", async () => {
    renderCheck(checkWire([]));
    const group = await listingSection();
    expect(group.getAllByText("Đạt")).toHaveLength(5);
    expect(group.getByText("Đã có ảnh bìa.")).toBeInTheDocument();
    expect(group.getByRole("link", { name: /Sửa thông tin bán/ })).toHaveAttribute("href", `/exams/${EXAM_ID}/listing`);
  });

  it("đọc từ listingCheck, không qua groups[]: nhóm có ở ô các nhóm kiểm tra dù groups[] chỉ có tám nhóm", async () => {
    renderCheck(checkWire([issue("LISTING_PRICE_MISSING", "listPriceVnd")]));
    const heading = await screen.findByRole("heading", { name: "Các nhóm kiểm tra" });
    const tiles = within(heading.closest("section")!).getAllByRole("listitem");
    expect(tiles).toHaveLength(9);
    expect(tiles.at(-1)).toHaveTextContent("Thông tin bán");
    expect(tiles.at(-1)).toHaveTextContent("Chưa nhập giá bán.");
    expect(tiles.slice(0, 8).map((tile) => tile.textContent).join(" ")).not.toContain("Chưa nhập giá bán.");
  });
});

describe("tab Kiểm tra: cảnh báo Có câu chấm tay", () => {
  const manual = {
    key: "manual-1",
    code: "MANUAL_GRADING_REQUIRED",
    severity: "WARNING",
    group: "STRUCTURE",
    partName: "Phần 1 · Ngữ văn",
    subjectCode: "VAN",
    blockId: "blk-1",
    blockName: "Ngữ văn · Tự luận",
    affectedCodes: ["CA1", "CA2", "CA3", "CA4"],
  };
  const withManual = (listing: ReturnType<typeof issue>[] = []) =>
    checkWire(listing, {
      groups: [{ group: "STRUCTURE", status: "WARN", issues: [manual] }],
    });

  it("hiện tiêu đề, câu mô tả, phần, môn, khối, các mã bị ảnh hưởng và lối mở khối ở Cấu trúc", async () => {
    renderCheck(withManual());
    const heading = await screen.findByRole("heading", { name: "Có câu chấm tay" });
    const section = within(heading.closest("section")!);

    expect(section.getByText("Cảnh báo · không chặn công bố")).toBeInTheDocument();
    expect(section.getByText("Bài có câu tự luận. Điểm tổng hiện sau khi người chấm chấm xong.")).toBeInTheDocument();
    const table = section.getByRole("table", { name: "Phần, môn và khối có câu tự luận" });
    const row = within(table).getByText("Phần 1 · Ngữ văn").closest("tr")!;
    expect(within(row).getByText("Ngữ văn · Tự luận")).toBeInTheDocument();
    expect(within(row).getByText("Chấm tay")).toBeInTheDocument();
    expect(within(row).getByText("CA1, CA2, CA3, CA4")).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "Mở khối Ngữ văn · Tự luận ở Cấu trúc và ma trận" })).toHaveAttribute(
      "href",
      `/exams/${EXAM_ID}/structure?block=blk-1`,
    );
  });

  it("nằm sau phần Lỗi và cảnh báo, trước nhóm Thông tin bán và kết quả theo đường (vị trí của bản vẽ)", async () => {
    renderCheck(withManual());
    const manualHeading = await screen.findByRole("heading", { name: "Có câu chấm tay" });
    const findings = screen.getByRole("heading", { name: "Lỗi và cảnh báo" });
    const listing = screen.getByRole("heading", { name: "Thông tin bán", level: 2 });
    const paths = screen.getByRole("heading", { name: "Kết quả theo đường lựa chọn" });
    const before = (a: HTMLElement, b: HTMLElement) => Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);
    expect(before(findings, manualHeading)).toBe(true);
    expect(before(manualHeading, listing)).toBe(true);
    expect(before(listing, paths)).toBe(true);
  });

  it("cảnh báo không chặn công bố: banner vẫn là Công bố được", async () => {
    renderCheck(withManual([]));
    await screen.findByRole("heading", { name: "Có câu chấm tay" });
    expect(screen.getByText("Công bố được. Không có lỗi chặn.")).toBeInTheDocument();
  });

  it("bài không có câu tự luận: không hiện mục này", async () => {
    renderCheck(checkWire([]));
    await listingSection();
    expect(screen.queryByRole("heading", { name: "Có câu chấm tay" })).not.toBeInTheDocument();
  });
});
