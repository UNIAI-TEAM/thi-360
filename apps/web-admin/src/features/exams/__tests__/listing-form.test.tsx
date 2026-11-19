import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  COVER_URL,
  examServer,
  LISTING_PATH,
  listingServer,
  listingWire,
  publishedWire,
  renderListingTab,
  VIEW_ONLY,
} from "./listing-fixtures";
import { EXAM_ID, EXAM_PATH, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => `/exams/${EXAM_ID}/listing`,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

beforeEach(() => {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/listing`);
});

const price = () => screen.findByRole("textbox", { name: /Giá bán/ });
const attempts = () => screen.findByRole("spinbutton", { name: /Số lượt mỗi lần mua/ });
const preview = () => within(screen.getByTestId("listing-card-preview"));
const saveButton = () => screen.getByRole("button", { name: "Lưu" });

describe("tab Thông tin bán: xem trước thẻ", () => {
  it("thẻ đổi theo dữ liệu đang nhập và dùng giá gốc", async () => {
    listingServer().install();
    const user = userEvent.setup();
    renderListingTab();

    const priceInput = await price();
    expect(priceInput).toHaveValue("249.000");
    expect(preview().getByText("249.000 đ")).toBeInTheDocument();
    expect(preview().getByText("HSA exam 1")).toBeInTheDocument();
    expect(preview().getByText(/câu · \d+ phút · 3 lượt/)).toBeInTheDocument();

    await user.clear(priceInput);
    await user.type(priceInput, "150000");
    expect(priceInput).toHaveValue("150.000");
    expect(preview().getByText("150.000 đ")).toBeInTheDocument();
    expect(preview().queryByText("249.000 đ")).not.toBeInTheDocument();

    const summary = screen.getByRole("textbox", { name: "Mô tả ngắn trên thẻ" });
    await user.clear(summary);
    await user.type(summary, "Đề mới");
    expect(preview().getByText("Đề mới")).toBeInTheDocument();

    const attemptsInput = await attempts();
    await user.clear(attemptsInput);
    await user.type(attemptsInput, "5");
    expect(preview().getByText(/· 5 lượt/)).toBeInTheDocument();
  });

  it("ô giá trống: thẻ ghi chưa có giá thay vì 0 đ; ảnh bìa lấy từ bản nháp", async () => {
    listingServer(listingWire({ listPriceVnd: undefined })).install();
    const user = userEvent.setup();
    renderListingTab();

    const priceInput = await price();
    expect(priceInput).toHaveValue("");
    expect(preview().getByText("Chưa có giá")).toBeInTheDocument();
    await user.type(priceInput, "50000");
    expect(preview().getByText("50.000 đ")).toBeInTheDocument();
    expect(document.querySelector(`[data-testid="listing-card-preview"] img`)).toHaveAttribute("src", COVER_URL);
  });
});

describe("tab Thông tin bán: dải giá đang bán", () => {
  const strip = () => screen.queryByRole("note", { name: /Giá đang bán/ });

  it("bản đang bán khác bản làm việc: hiện giá đang bán, các trường khác và lối so sánh", async () => {
    listingServer(
      listingWire({ published: publishedWire({ listPriceVnd: 199000, attemptsGranted: 2, resultView: "SCORE" }) }),
    ).install();
    renderListingTab();

    const note = await screen.findByRole("note", { name: "Giá đang bán: 199.000 đ · Sẽ đổi khi công bố" });
    expect(note).toHaveTextContent("giá bán (199.000 đ → 249.000 đ)");
    expect(note).toHaveTextContent("số lượt (2 → 3)");
    expect(note).toHaveTextContent("mức xem kết quả");
    expect(note).toHaveTextContent("Học viên vẫn mua theo bản v2 cho tới khi công bố lại.");
    expect(within(note).getByRole("link", { name: "So sánh với bản v2" })).toHaveAttribute("href", `/exams/${EXAM_ID}/versions`);
  });

  it("hai bản giống nhau: không hiện dải", async () => {
    listingServer(listingWire({ published: publishedWire() })).install();
    renderListingTab();
    await price();
    expect(strip()).not.toBeInTheDocument();
  });

  it("chưa công bố hoặc phiên bản đang phục vụ có trước thông tin bán: không hiện dải", async () => {
    listingServer(listingWire({ published: undefined })).install();
    const first = renderListingTab();
    await price();
    expect(strip()).not.toBeInTheDocument();
    first.unmount();

    listingServer(listingWire({ published: { versionNo: 1, publishedAt: "2026-11-01T00:00:00Z" } })).install();
    renderListingTab();
    await price();
    expect(strip()).not.toBeInTheDocument();
  });

  it("chỉ so theo bản đã lưu: nhập giá mới chưa lưu không đổi dải", async () => {
    listingServer(listingWire({ published: publishedWire({ listPriceVnd: 199000 }) })).install();
    const user = userEvent.setup();
    renderListingTab();
    const priceInput = await price();
    await user.clear(priceInput);
    await user.type(priceInput, "300000");
    expect(strip()).toHaveTextContent("giá bán (199.000 đ → 249.000 đ)");
  });
});

describe("tab Thông tin bán: lưu", () => {
  it("gửi expectedRevision đang có và đủ trường; bản mới về thì lần lưu sau dùng revision mới", async () => {
    const server = listingServer()
      .on("PUT", LISTING_PATH, (request) => ({
        status: 200,
        body: listingWire({ ...(request.body as object), revision: 8, listPriceVnd: 300000 }),
      }))
      .install();
    const user = userEvent.setup();
    renderListingTab();

    expect(await screen.findByRole("button", { name: "Lưu" })).toBeDisabled();
    const priceInput = await price();
    await user.clear(priceInput);
    await user.type(priceInput, "300000");
    await user.click(saveButton());

    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(1));
    expect(server.calls("PUT", LISTING_PATH)[0]!.body).toMatchObject({
      expectedRevision: 7,
      listPriceVnd: 300000,
      attemptsGranted: 3,
      resultView: "SCORE_AND_ANSWERS",
      benefitAiExplanation: true,
      benefitPractice: false,
      description: "Exam description shown on the detail page.",
      coverAssetId: "33333333-3333-3333-3333-333333333333",
    });

    await waitFor(() => expect(saveButton()).toBeDisabled());
    await user.clear(await attempts());
    await user.type(await attempts(), "4");
    await user.click(saveButton());
    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(2));
    expect(server.calls("PUT", LISTING_PATH)[1]!.body).toMatchObject({ expectedRevision: 8, attemptsGranted: 4 });
  });

  it("giá và số lượt xóa trống vẫn lưu nháp được (gửi không có hai trường đó)", async () => {
    const server = listingServer().on("PUT", LISTING_PATH, { status: 200, body: listingWire() }).install();
    const user = userEvent.setup();
    renderListingTab();
    await user.clear(await price());
    await user.clear(await attempts());
    await user.click(saveButton());
    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(1));
    const body = server.calls("PUT", LISTING_PATH)[0]!.body as Record<string, unknown>;
    expect(body).not.toHaveProperty("listPriceVnd");
    expect(body).not.toHaveProperty("attemptsGranted");
  });

  it("số lượt ngoài 1 đến 100: báo ở ô, không gọi PUT", async () => {
    const server = listingServer().install();
    const user = userEvent.setup();
    renderListingTab();
    const input = await attempts();
    await user.clear(input);
    await user.type(input, "120");
    await user.click(saveButton());

    expect(await screen.findByText("Số lượt từ 1 đến 100.")).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(server.calls("PUT", LISTING_PATH)).toHaveLength(0);
  });

  it("lỗi theo ô: 422 EXAM_LISTING_INVALID gắn lỗi vào đúng ô theo fieldErrors", async () => {
    listingServer()
      .on("PUT", LISTING_PATH, {
        status: 422,
        body: {
          code: "EXAM_LISTING_INVALID",
          message: "x",
          fieldErrors: [
            { field: "attemptsGranted", code: "OUT_OF_RANGE", message: "server text" },
            { field: "summary", code: "TOO_LONG", message: "server text" },
          ],
        },
      })
      .install();
    const user = userEvent.setup();
    renderListingTab();
    await user.type(await price(), "0");
    await user.click(saveButton());

    const attemptsInput = await attempts();
    await waitFor(() => expect(attemptsInput).toHaveAttribute("aria-invalid", "true"));
    expect(screen.getByText("Số lượt từ 1 đến 100.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Mô tả ngắn trên thẻ" })).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Dài tối đa 300 ký tự.")).toBeInTheDocument();
    // Ô không có lỗi thì không bị đánh dấu; chữ của máy chủ không in ra.
    expect(await price()).not.toHaveAttribute("aria-invalid");
    expect(screen.queryByText("server text")).not.toBeInTheDocument();
  });

  it("412: nạp lại bản mới, giữ thông báo trên màn tới khi đóng; lưu thành công cũng xóa thông báo", async () => {
    const server = examServer()
      .on("GET", LISTING_PATH, { status: 200, body: listingWire() }, { status: 200, body: listingWire({ revision: 9, listPriceVnd: 280000 }) })
      .on("PUT", LISTING_PATH, { status: 412, body: { code: "EXAM_REVISION_CONFLICT", message: "x" } }, { status: 200, body: listingWire({ revision: 10 }) })
      .install();
    const user = userEvent.setup();
    renderListingTab();
    const priceInput = await price();
    await user.clear(priceInput);
    await user.type(priceInput, "111000");
    await user.click(saveButton());

    expect(await screen.findByText("Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất.")).toBeInTheDocument();
    // Dữ liệu mới đã về: ô giá hiện giá của máy chủ, giá đang nhập bị bỏ.
    await waitFor(() => expect(screen.getByRole("textbox", { name: /Giá bán/ })).toHaveValue("280.000"));
    expect(server.calls("GET", LISTING_PATH).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất.")).toBeInTheDocument();
    expect(screen.getByText(/Thay đổi của bạn chưa được lưu/)).toBeInTheDocument();

    // Sửa lại và lưu bằng revision mới: thành công thì thông báo biến mất.
    const again = screen.getByRole("textbox", { name: /Giá bán/ });
    await user.clear(again);
    await user.type(again, "290000");
    await user.click(saveButton());
    await waitFor(() => expect(screen.queryByText("Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất.")).not.toBeInTheDocument());
    expect(server.calls("PUT", LISTING_PATH)[1]!.body).toMatchObject({ expectedRevision: 9 });
  });

  it("412: người dùng đóng thông báo được", async () => {
    listingServer()
      .on("PUT", LISTING_PATH, { status: 412, body: { code: "EXAM_REVISION_CONFLICT", message: "x" } })
      .install();
    const user = userEvent.setup();
    renderListingTab();
    await user.type(await price(), "0");
    await user.click(saveButton());
    await screen.findByText("Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất.");
    await user.click(screen.getByRole("button", { name: "Đóng thông báo" }));
    expect(screen.queryByText("Bài thi vừa được người khác sửa. Đã nạp lại bản mới nhất.")).not.toBeInTheDocument();
  });

  it("lỗi khác (500): báo chung, giữ giá trị đang nhập", async () => {
    listingServer().on("PUT", LISTING_PATH, { status: 500, body: { code: "INTERNAL", message: "x" } }).install();
    const user = userEvent.setup();
    renderListingTab();
    await user.type(await price(), "0");
    await user.click(saveButton());
    expect(await screen.findByText("Chưa lưu được thông tin bán. Thử lại.")).toBeInTheDocument();
    expect(await price()).toHaveValue("2.490.000");
  });
});

describe("tab Thông tin bán: gợi ý giá", () => {
  it("mức tối thiểu lấy từ API, không viết cứng 5.000", async () => {
    listingServer(listingWire({ minPriceVnd: 12000 })).install();
    renderListingTab();
    await price();
    expect(screen.getByText(/không thấp hơn 12\.000 đ/)).toBeInTheDocument();
    expect(screen.queryByText(/5\.000/)).not.toBeInTheDocument();
  });

  it("API không gửi mức tối thiểu: gợi ý không nêu số", async () => {
    listingServer(listingWire({ minPriceVnd: undefined })).install();
    renderListingTab();
    await price();
    expect(screen.getByText(/Lớn hơn 0\./)).toBeInTheDocument();
    expect(screen.queryByText(/không thấp hơn/)).not.toBeInTheDocument();
  });
});

describe("tab Thông tin bán: chỉ xem", () => {
  it("thiếu EXAM.MODIFY: mọi ô khóa, ẩn Lưu, Đổi ảnh, Bỏ ảnh; dải giá và thẻ xem trước vẫn hiện", async () => {
    listingServer(listingWire({ published: publishedWire({ listPriceVnd: 199000 }) })).install();
    renderListingTab({ permissions: VIEW_ONLY });

    const form = await screen.findByRole("form", { name: "Thông tin bán" });
    await waitFor(() => expect(within(form).getByRole("textbox", { name: /Giá bán/ })).toBeDisabled());
    for (const box of within(form).getAllByRole("textbox")) expect(box).toBeDisabled();
    for (const box of within(form).getAllByRole("checkbox")) expect(box).toBeDisabled();
    for (const radio of within(form).getAllByRole("radio")) expect(radio).toBeDisabled();
    expect(within(form).getByRole("spinbutton", { name: /Số lượt/ })).toBeDisabled();

    expect(screen.queryByRole("button", { name: "Lưu" })).not.toBeInTheDocument();
    expect(screen.queryByText("Đổi ảnh")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bỏ ảnh" })).not.toBeInTheDocument();
    expect(document.querySelector('input[type="file"]')).toBeNull();
    expect(screen.getByText(/Bạn chỉ có quyền xem thông tin bán/)).toBeInTheDocument();

    expect(screen.getByRole("note", { name: /Giá đang bán: 199\.000 đ/ })).toBeInTheDocument();
    expect(screen.getByTestId("listing-card-preview")).toBeInTheDocument();
  });

  it("có EXAM.MODIFY: không hiện ghi chú chỉ xem, ô nhập mở", async () => {
    listingServer().install();
    renderListingTab();
    expect(await price()).toBeEnabled();
    expect(screen.queryByText(/Bạn chỉ có quyền xem thông tin bán/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bỏ ảnh" })).toBeInTheDocument();
  });
});

describe("tab Thông tin bán: trạng thái tải", () => {
  it("đang tải: khung xương báo bận, xong thì hiện biểu mẫu", async () => {
    const server = listingServer().hold("GET", LISTING_PATH).install();
    renderListingTab();

    const busy = await screen.findByLabelText("Đang tải thông tin bán");
    expect(busy).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByRole("form")).not.toBeInTheDocument();

    server.release("GET", LISTING_PATH);
    expect(await screen.findByRole("form", { name: "Thông tin bán" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Đang tải thông tin bán")).not.toBeInTheDocument();
  });

  it("lỗi tải: thông báo kèm nút Thử lại, bấm thì tải lại và hiện biểu mẫu", async () => {
    const server = examServer()
      .on("GET", LISTING_PATH, { status: 500, body: { code: "INTERNAL", message: "x" } }, { status: 200, body: listingWire() })
      .install();
    const user = userEvent.setup();
    renderListingTab();

    expect(await screen.findByText("Không tải được thông tin bán")).toBeInTheDocument();
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("form", { name: "Thông tin bán" })).toBeInTheDocument();
    expect(server.calls("GET", LISTING_PATH)).toHaveLength(2);
  });

  it("không tải được bài thi: cũng hiện lỗi kèm Thử lại", async () => {
    new FakeServer()
      .on("GET", EXAM_PATH, { status: 500, body: { code: "INTERNAL", message: "x" } })
      .on("GET", LISTING_PATH, { status: 200, body: listingWire() })
      .install();
    renderListingTab();
    expect(await screen.findByText("Không tải được thông tin bán")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeInTheDocument();
  });
});
