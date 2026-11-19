import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamVersionsView } from "../components/exam-versions-view";
import { checkResultWire, EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => `/exams/${EXAM_ID}/versions`,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const CHECK_PATH = `${EXAM_PATH}/check`;
const PUBLISH_PATH = `${EXAM_PATH}/publish`;

const listingIssue = (code: string, field: string, minPriceVnd?: number) => ({
  key: `LISTING:${code}`,
  code,
  severity: "ERROR",
  group: "LISTING",
  field,
  ...(minPriceVnd === undefined ? {} : { minPriceVnd }),
});
const listingCheck = (...issues: ReturnType<typeof listingIssue>[]) => ({
  group: "LISTING",
  status: issues.length > 0 ? "FAIL" : "PASS",
  issues,
});

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("tab Kiểm tra: lỗi của listingCheck", () => {
  const renderCheck = (server: FakeServer) => {
    server.install();
    return renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />);
  };

  it("lỗi hiện ở nhóm Thông tin bán và chặn công bố ở banner tổng, kèm lối sửa", async () => {
    renderCheck(
      new FakeServer().on("GET", CHECK_PATH, {
        status: 200,
        body: {
          ...checkResultWire("VALID"),
          listingCheck: listingCheck(listingIssue("LISTING_PRICE_MISSING", "listPriceVnd"), listingIssue("LISTING_ATTEMPTS_MISSING", "attemptsGranted")),
        },
      }),
    );

    const banner = await screen.findByText("Chưa công bố được: Thông tin bán có 2 lỗi.");
    expect(screen.queryByText("Công bố được. Không có lỗi chặn.")).not.toBeInTheDocument();
    expect(within(banner.closest("div")!).getByRole("link", { name: "Sửa thông tin bán" })).toHaveAttribute("href", `/exams/${EXAM_ID}/listing`);

    const group = within(document.getElementById("review-listing")!);
    expect(group.getByText("2 lỗi")).toBeInTheDocument();
    expect(group.getByText("Chưa nhập giá bán.")).toBeInTheDocument();
    expect(group.getByText("Chưa nhập số lượt mỗi lần mua.")).toBeInTheDocument();
  });

  it("lỗi cấu hình đứng trước: banner nêu lỗi cấu hình, nhóm Thông tin bán vẫn báo lỗi của nó", async () => {
    renderCheck(
      new FakeServer().on("GET", CHECK_PATH, {
        status: 200,
        body: {
          ...checkResultWire("INVALID"),
          groups: [
            {
              group: "INFO",
              status: "FAIL",
              issues: [{ key: "info", code: "INFO_MISSING", severity: "ERROR", group: "INFO" }],
            },
          ],
          listingCheck: listingCheck(listingIssue("LISTING_DESCRIPTION_MISSING", "description")),
        },
      }),
    );
    expect(await screen.findByText("Chưa công bố được: 1 lỗi chặn công bố.")).toBeInTheDocument();
    expect(within(document.getElementById("review-listing")!).getByText("Chưa có mô tả.")).toBeInTheDocument();
  });

  it("máy chủ không tính được nhóm: hiện lỗi kèm Thử lại, bấm thì tải lại kết quả; các nhóm khác vẫn dùng được", async () => {
    const server = new FakeServer().on(
      "GET",
      CHECK_PATH,
      { status: 200, body: checkResultWire("VALID") },
      { status: 200, body: { ...checkResultWire("VALID"), listingCheck: listingCheck() } },
    );
    const user = userEvent.setup();
    renderCheck(server);

    expect(await screen.findByText("Không tính được nhóm Thông tin bán")).toBeInTheDocument();
    expect(within(document.getElementById("review-listing")!).getByText("Không tải được kết quả kiểm tra thông tin bán.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Kết quả theo đường lựa chọn" })).toBeInTheDocument();

    await user.click(within(document.getElementById("review-listing")!).getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Thông tin bán", level: 2 })).toBeInTheDocument();
    expect(screen.queryByText("Không tính được nhóm Thông tin bán")).not.toBeInTheDocument();
    expect(server.calls("GET", CHECK_PATH)).toHaveLength(2);
  });
});

const PART = "5b0ac7f0-1c6d-5a38-8f2e-000000000001";
const choice = (code: string, name: string) => ({ partId: PART, optionId: `${PART}:${code}`, kind: "COMBINATION", code, name });
const pathOf = (code: string, name: string) => ({ pathId: `${PART}:${code}`, choices: [choice(code, name)] });
const PATHS = [pathOf("QD04", "Văn, Sử, Địa"), pathOf("QD05", "Văn, Toán, Anh")];

function publishServer(publish: { status: number; body: unknown }, check: Record<string, unknown> = {}) {
  return new FakeServer()
    .on("GET", EXAM_PATH, {
      status: 200,
      body: examDetailWire({ kind: "QDA", code: "QDA-F-001", name: "QDA exam", check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" } }),
    })
    .on("GET", CHECK_PATH, {
      status: 200,
      body: { ...checkResultWire("VALID"), paths: PATHS.map((path) => ({ ...path, result: "VALID", issues: [] })), ...check },
    })
    .on("GET", `${EXAM_PATH}/versions`, {
      status: 200,
      body: {
        examId: EXAM_ID,
        workingCopy: {
          editedBy: { id: "u1", name: "Cô Lan" },
          savedAt: "2026-11-17T02:00:00Z",
          check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
          unpublishedPaths: PATHS,
        },
        changes: [],
        items: [],
      },
    })
    .on("POST", PUBLISH_PATH, publish)
    .install();
}

async function submitPublish() {
  const user = userEvent.setup();
  renderWithProviders(<ExamVersionsView examId={EXAM_ID} />);
  await user.click(await screen.findByRole("button", { name: "Công bố" }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findAllByRole("checkbox");
  await user.click(within(dialog).getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ }));
  return dialog;
}

const rejection = (code: string, fieldErrors: unknown[]) => ({ status: 422, body: { code, message: "x", fieldErrors } });

describe("hộp thoại công bố: lỗi LISTING_*", () => {
  it.each([
    ["LISTING_PRICE_MISSING", "listPriceVnd", "Chưa nhập giá bán."],
    ["LISTING_PRICE_ZERO", "listPriceVnd", "Giá bán phải lớn hơn 0 đồng. Bài thi chỉ miễn phí qua voucher."],
    ["LISTING_ATTEMPTS_MISSING", "attemptsGranted", "Chưa nhập số lượt mỗi lần mua."],
    ["LISTING_DESCRIPTION_MISSING", "description", "Chưa có mô tả."],
  ])("%s: hiện đúng lỗi, không nhầm với lỗi đường lựa chọn, kèm lối sửa thông tin bán", async (code, field, text) => {
    publishServer(rejection(code, [{ field, code, message: "server text" }]));
    const dialog = await submitPublish();

    const alert = await within(dialog).findByRole("alert");
    expect(within(alert).getByText("Thông tin bán chưa đủ để công bố:")).toBeInTheDocument();
    expect(within(alert).getByText(text)).toBeInTheDocument();
    expect(within(alert).queryByText(/Có đường lựa chọn không còn hợp lệ/)).not.toBeInTheDocument();
    expect(within(alert).queryByText("server text")).not.toBeInTheDocument();
    expect(within(alert).getByRole("link", { name: "Sửa thông tin bán" })).toHaveAttribute("href", `/exams/${EXAM_ID}/listing`);
  });

  it("nhiều lỗi một lần: mỗi lỗi một dòng; giá thấp hơn mức tối thiểu lấy mức từ nhóm Thông tin bán của lần kiểm tra", async () => {
    publishServer(
      rejection("LISTING_PRICE_BELOW_MIN", [
        { field: "listPriceVnd", code: "LISTING_PRICE_BELOW_MIN", message: "server text" },
        { field: "description", code: "LISTING_DESCRIPTION_MISSING", message: "server text" },
      ]),
      { listingCheck: listingCheck(listingIssue("LISTING_PRICE_BELOW_MIN", "listPriceVnd", 12000)) },
    );
    const dialog = await submitPublish();

    const alert = await within(dialog).findByRole("alert");
    const lines = within(alert).getAllByRole("listitem").map((item) => item.textContent);
    expect(lines).toEqual(["Giá bán thấp hơn mức tối thiểu 12.000 đ của cổng thanh toán.", "Chưa có mô tả."]);
  });

  it("lỗi không có fieldErrors: vẫn hiện lỗi theo mã của lỗi", async () => {
    publishServer(rejection("LISTING_PRICE_MISSING", []));
    const dialog = await submitPublish();
    expect(await within(dialog).findByText("Chưa nhập giá bán.")).toBeInTheDocument();
  });
});

describe("hộp thoại công bố: PUBLISH_ALL_PATHS_REQUIRED", () => {
  it("nêu lý do và tên từng đường chặn, kèm lối kiểm tra lại", async () => {
    publishServer(
      rejection("PUBLISH_ALL_PATHS_REQUIRED", [
        { field: "pathIds", code: "PATH_SERVING_INVALID", message: `${PART}:QD04` },
        { field: "pathIds", code: "PATH_VALID_NOT_SELECTED", message: `${PART}:QD05` },
        { field: "pathIds", code: "PATH_SERVING_MISSING_FROM_CHECK", message: `${PART}:QD99` },
      ]),
    );
    const dialog = await submitPublish();

    const alert = await within(dialog).findByRole("alert");
    expect(within(alert).getByText("Bài này có lựa chọn sau khi bắt đầu thi nên phải công bố mọi đường cùng lúc.")).toBeInTheDocument();
    expect(within(alert).getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "QD04 · Văn, Sử, Địa: đang phục vụ nhưng không còn hợp lệ. Sửa cấu hình rồi kiểm tra lại.",
      "QD05 · Văn, Toán, Anh: hợp lệ nhưng chưa được chọn. Chọn đường này để công bố.",
      "Một đường lựa chọn: đang phục vụ nhưng không còn trong lần kiểm tra. Kiểm tra lại cấu hình.",
    ]);
    expect(within(alert).queryByText(/Có đường lựa chọn không còn hợp lệ/)).not.toBeInTheDocument();
    expect(within(alert).getByRole("link", { name: "Kiểm tra lại" })).toBeInTheDocument();
    expect(within(alert).queryByRole("link", { name: "Sửa thông tin bán" })).not.toBeInTheDocument();
  });

  it("chỉ còn đường hợp lệ chưa chọn: không gợi ý kiểm tra lại", async () => {
    publishServer(
      rejection("PUBLISH_ALL_PATHS_REQUIRED", [{ field: "pathIds", code: "PATH_VALID_NOT_SELECTED", message: `${PART}:QD05` }]),
    );
    const dialog = await submitPublish();
    const alert = await within(dialog).findByRole("alert");
    await waitFor(() => expect(within(alert).getAllByRole("listitem")).toHaveLength(1));
    expect(within(alert).queryByRole("link", { name: "Kiểm tra lại" })).not.toBeInTheDocument();
  });

  it("422 khác vẫn dùng câu cũ về đường không còn hợp lệ", async () => {
    publishServer(rejection("PATH_NOT_VALID", []));
    const dialog = await submitPublish();
    expect(await within(dialog).findByText(/Có đường lựa chọn không còn hợp lệ/)).toBeInTheDocument();
  });
});
