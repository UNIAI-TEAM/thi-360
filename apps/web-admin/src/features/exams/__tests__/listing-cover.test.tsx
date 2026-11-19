import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  COVER_PATH,
  COVER_URL,
  LISTING_PATH,
  listingServer,
  listingWire,
  NEW_COVER_ASSET,
  renderListingTab,
} from "./listing-fixtures";
import { EXAM_ID } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => `/exams/${EXAM_ID}/listing`,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

beforeEach(() => {
  window.history.replaceState(null, "", `/exams/${EXAM_ID}/listing`);
});

/** Chọn tệp mà không để trình duyệt lọc theo `accept`: test kiểm chính phần kiểm tra của web và của máy chủ. */
const userWithoutAccept = () => userEvent.setup({ applyAccept: false });
const fileInput = async () => {
  await screen.findByRole("img", { name: "Ảnh bìa bài thi" });
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("không có ô chọn tệp");
  return input;
};
const currentCover = () => screen.getByRole("img", { name: "Ảnh bìa bài thi" });
const small = (name: string, type: string) => new File(["x"], name, { type });
const coverRejection = (code: string) => ({
  status: 422,
  body: { code: "EXAM_LISTING_INVALID", message: "x", fieldErrors: [{ field: "file", code, message: "server text" }] },
});

describe("ảnh bìa: kiểm ngay trên web", () => {
  it("sai định dạng (gif): lỗi rõ kèm tên tệp, không gọi API, ảnh đang dùng được giữ nguyên", async () => {
    const server = listingServer().install();
    const user = userWithoutAccept();
    renderListingTab();

    await user.upload(await fileInput(), small("bia-hsa-thang-11.gif", "image/gif"));

    expect(
      await screen.findByText("Tệp bia-hsa-thang-11.gif không đúng định dạng. Chọn ảnh JPG, PNG hoặc WEBP. Ảnh đang dùng được giữ nguyên."),
    ).toBeInTheDocument();
    expect(server.calls("POST", COVER_PATH)).toHaveLength(0);
    expect(currentCover()).toHaveAttribute("src", COVER_URL);
    // Không có thay đổi nào để lưu.
    expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled();
  });

  it("quá 2 MB: từ chối ngay trên web, không gọi API", async () => {
    const server = listingServer().install();
    const user = userWithoutAccept();
    renderListingTab();

    const big = new File([new Uint8Array(2 * 1024 * 1024 + 1)], "bia-lon.png", { type: "image/png" });
    await user.upload(await fileInput(), big);

    expect(await screen.findByText(/Ảnh lớn hơn 2 MB\. Chọn ảnh nhỏ hơn\./)).toBeInTheDocument();
    expect(server.calls("POST", COVER_PATH)).toHaveLength(0);
    expect(currentCover()).toHaveAttribute("src", COVER_URL);
  });

  it("đúng 2 MB vẫn được gửi lên", async () => {
    const server = listingServer()
      .on("POST", COVER_PATH, { status: 201, body: { assetId: NEW_COVER_ASSET, url: `${COVER_PATH}/${NEW_COVER_ASSET}` } })
      .install();
    const user = userWithoutAccept();
    renderListingTab();

    const exact = new File([new Uint8Array(2 * 1024 * 1024)], "bia-2mb.webp", { type: "image/webp" });
    await user.upload(await fileInput(), exact);
    await waitFor(() => expect(server.calls("POST", COVER_PATH)).toHaveLength(1));
  });

  it("không có loại tệp (trình duyệt không nhận ra): theo đuôi tệp", async () => {
    const server = listingServer()
      .on("POST", COVER_PATH, { status: 201, body: { assetId: NEW_COVER_ASSET, url: `${COVER_PATH}/${NEW_COVER_ASSET}` } })
      .install();
    const user = userWithoutAccept();
    renderListingTab();

    await user.upload(await fileInput(), small("anh.JPG", ""));
    await waitFor(() => expect(server.calls("POST", COVER_PATH)).toHaveLength(1));

    await user.upload(await fileInput(), small("anh.bmp", ""));
    expect(await screen.findByText(/Tệp anh\.bmp không đúng định dạng/)).toBeInTheDocument();
    expect(server.calls("POST", COVER_PATH)).toHaveLength(1);
  });
});

describe("ảnh bìa: lỗi do máy chủ trả", () => {
  it.each([
    ["TOO_LARGE", /Ảnh lớn hơn 2 MB\. Chọn ảnh nhỏ hơn\./],
    ["UNSUPPORTED_TYPE", /Tệp bia\.png không đúng định dạng/],
    ["DIMENSIONS_TOO_SMALL", /Cạnh ngắn của ảnh nhỏ hơn 480 px\. Chọn ảnh lớn hơn\./],
    ["DIMENSIONS_TOO_LARGE", /Ảnh rộng hoặc cao hơn 4\.096 px\. Chọn ảnh nhỏ hơn\./],
    ["CORRUPT", /Tệp bia\.png bị hỏng hoặc không đọc hết được/],
    ["EMPTY", /Tệp bia\.png trống/],
  ])("%s: hiện lỗi rõ bằng chữ của web và giữ ảnh đang dùng", async (code, message) => {
    const server = listingServer().on("POST", COVER_PATH, coverRejection(code)).install();
    const user = userWithoutAccept();
    renderListingTab();

    await user.upload(await fileInput(), small("bia.png", "image/png"));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.queryByText("server text")).not.toBeInTheDocument();
    expect(server.calls("POST", COVER_PATH)).toHaveLength(1);
    expect(currentCover()).toHaveAttribute("src", COVER_URL);
    expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled();
  });

  it("413 từ tầng web (không có thân lỗi): báo quá lớn", async () => {
    listingServer().on("POST", COVER_PATH, { status: 413 }).install();
    const user = userWithoutAccept();
    renderListingTab();
    await user.upload(await fileInput(), small("bia.png", "image/png"));
    expect(await screen.findByText(/Ảnh lớn hơn 2 MB/)).toBeInTheDocument();
  });

  it("lỗi không rõ (500): báo chung, thử lại được", async () => {
    listingServer().on("POST", COVER_PATH, { status: 500, body: { code: "INTERNAL", message: "x" } }).install();
    const user = userWithoutAccept();
    renderListingTab();
    await user.upload(await fileInput(), small("bia.png", "image/png"));
    expect(await screen.findByText(/Không tải được ảnh lên\. Thử lại\./)).toBeInTheDocument();
  });
});

describe("ảnh bìa: đổi và bỏ", () => {
  it("tải ảnh mới lên: thẻ xem trước đổi ngay, chưa gắn vào bài cho tới khi Lưu; Lưu gửi assetId mới", async () => {
    const server = listingServer()
      .on("POST", COVER_PATH, { status: 201, body: { assetId: NEW_COVER_ASSET, url: `${COVER_PATH}/${NEW_COVER_ASSET}` } })
      .on("PUT", LISTING_PATH, { status: 200, body: listingWire({ revision: 8, coverAssetId: NEW_COVER_ASSET, coverUrl: `${COVER_PATH}/${NEW_COVER_ASSET}` }) })
      .install();
    const user = userWithoutAccept();
    renderListingTab();

    await user.upload(await fileInput(), small("bia-moi.webp", "image/webp"));

    await waitFor(() => expect(currentCover()).toHaveAttribute("src", `${COVER_PATH}/${NEW_COVER_ASSET}`));
    // Tải lên là multipart một phần `file` mang đúng loại ảnh đã chọn.
    const upload = String(server.calls("POST", COVER_PATH)[0]!.body);
    expect(upload).toContain('name="file"');
    expect(upload).toContain("Content-Type: image/webp");
    expect(server.calls("PUT", LISTING_PATH)).toHaveLength(0);
    expect(document.querySelector('[data-testid="listing-card-preview"] img')).toHaveAttribute("src", `${COVER_PATH}/${NEW_COVER_ASSET}`);

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(1));
    expect(server.calls("PUT", LISTING_PATH)[0]!.body).toMatchObject({ coverAssetId: NEW_COVER_ASSET, expectedRevision: 7 });
  });

  it("Bỏ ảnh: về ảnh mặc định, Lưu gửi không có coverAssetId (máy chủ xóa ảnh)", async () => {
    const server = listingServer()
      .on("PUT", LISTING_PATH, { status: 200, body: listingWire({ revision: 8, coverAssetId: undefined, coverUrl: undefined }) })
      .install();
    const user = userEvent.setup();
    renderListingTab();

    await user.click(await screen.findByRole("button", { name: "Bỏ ảnh" }));
    expect(screen.queryByRole("img", { name: "Ảnh bìa bài thi" })).not.toBeInTheDocument();
    expect(screen.getByText("Chưa có ảnh bìa")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bỏ ảnh" })).not.toBeInTheDocument();
    expect(screen.getByText("Chọn ảnh")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(server.calls("PUT", LISTING_PATH)).toHaveLength(1));
    expect(server.calls("PUT", LISTING_PATH)[0]!.body).not.toHaveProperty("coverAssetId");
  });

  it("422 COVER_NOT_FOUND khi lưu: báo ảnh đã chọn không còn dùng được", async () => {
    listingServer()
      .on("PUT", LISTING_PATH, {
        status: 422,
        body: { code: "EXAM_LISTING_INVALID", message: "x", fieldErrors: [{ field: "coverAssetId", code: "COVER_NOT_FOUND", message: "server text" }] },
      })
      .install();
    const user = userEvent.setup();
    renderListingTab();
    await user.click(await screen.findByRole("button", { name: "Bỏ ảnh" }));
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    expect(await screen.findByText("Ảnh bìa đã chọn không còn dùng được. Chọn lại ảnh rồi lưu.")).toBeInTheDocument();
  });

  it("bài chưa có ảnh: hiện ảnh mặc định, không có nút Bỏ ảnh; thiếu ảnh chỉ là cảnh báo ở ô kiểm tra", async () => {
    listingServer(
      listingWire({
        coverAssetId: undefined,
        coverUrl: undefined,
        checkIssues: [{ key: "LISTING:LISTING_COVER_MISSING", code: "LISTING_COVER_MISSING", severity: "WARNING", field: "coverAssetId" }],
      }),
    ).install();
    renderListingTab();

    expect(await screen.findByText("Chưa có ảnh bìa")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bỏ ảnh" })).not.toBeInTheDocument();
    expect(screen.getByText(/Chưa có ảnh bìa; thẻ dùng ảnh mặc định\. Không chặn công bố\./)).toBeInTheDocument();
    expect(screen.queryByText("Giá bán, số lượt, mô tả đủ để công bố")).toBeInTheDocument();
  });
});
