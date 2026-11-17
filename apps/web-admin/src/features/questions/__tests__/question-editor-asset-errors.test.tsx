import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { describe, expect, it, vi } from "vitest";
import { fail, fakeApi } from "@/test/fake-api";
import { QuestionEditorView } from "../components/question-editor-view";
import { pasteImageFile, renderEn } from "./editor-test-kit";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001/edit",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const ASSETS = "/api/v1/admin/question-assets";
const png = () => new File([new Uint8Array([137, 80, 78, 71])], "hinh.png", { type: "image/png" });

async function renderEditor() {
  renderEn(
    <>
      <QuestionEditorView mode="edit" questionId="q-0001" />
      <Toaster containerAriaLabel="Notifications" closeButtonAriaLabel="Close" />
    </>,
  );
  return screen.findByRole("textbox", { name: /^Question text/ });
}

describe("trình soạn câu hỏi: ảnh dán vào ô nội dung bị máy chủ từ chối (toast)", () => {
  it("413 hiện thông báo ảnh quá 5 MB bằng chữ đã dịch", async () => {
    fakeApi.post(ASSETS, fail(413));
    pasteImageFile(await renderEditor(), png());
    expect(await screen.findByText("The image is larger than 5 MB.")).toBeInTheDocument();
    expect(fakeApi.calls.filter((call) => call.path === ASSETS)).toHaveLength(1);
  });

  it("422 ASSET_TYPE_NOT_ALLOWED và 429 hiện thông báo riêng", async () => {
    fakeApi.post(ASSETS, fail(422, { code: "ASSET_TYPE_NOT_ALLOWED", message: "type" }));
    const textbox = await renderEditor();
    pasteImageFile(textbox, png());
    expect(await screen.findByText("Only PNG, JPEG or WebP images are accepted.")).toBeInTheDocument();

    fakeApi.post(ASSETS, fail(429, { code: "RATE_LIMITED", message: "slow down" }));
    pasteImageFile(textbox, png());
    await waitFor(() => expect(screen.getByText(/You uploaded too many files/)).toBeInTheDocument());
  });
});

describe("trình soạn câu hỏi: ảnh chọn trong hộp Hình bị máy chủ từ chối (thông báo trong hộp)", () => {
  it("nút Hình mở hộp, 413 hiện thông báo ảnh quá 5 MB và nút Chèn hình vẫn khóa", async () => {
    fakeApi.post(ASSETS, fail(413));
    const user = userEvent.setup();
    await renderEditor();
    // Không còn ô chọn ảnh nằm sẵn trong trình soạn (ô đính kèm là ô khác): chỉ có ở hộp Hình.
    expect(document.querySelector('input[type="file"]:not([aria-label="Choose attachments"])')).toBeNull();

    await user.click(screen.getAllByRole("button", { name: "Image" })[0]!);
    await user.upload(await screen.findByLabelText("Choose image file"), png());

    expect(await screen.findByRole("alert")).toHaveTextContent("The image is larger than 5 MB.");
    expect(screen.getByRole("button", { name: "Insert image" })).toBeDisabled();
    expect(fakeApi.calls.filter((call) => call.path === ASSETS)).toHaveLength(1);
  });
});
