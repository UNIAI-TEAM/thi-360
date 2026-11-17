import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "@thi360/ui";
import { describe, expect, it, vi } from "vitest";
import { fail, fakeApi } from "@/test/fake-api";
import { QuestionEditorView } from "../components/question-editor-view";
import { renderEn } from "./editor-test-kit";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001/edit",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const ASSETS = "/api/v1/admin/question-assets";
const png = () => new File([new Uint8Array([137, 80, 78, 71])], "hinh.png", { type: "image/png" });

async function pickImage(user: ReturnType<typeof userEvent.setup>) {
  renderEn(
    <>
      <QuestionEditorView mode="edit" questionId="q-0001" />
      <Toaster containerAriaLabel="Notifications" closeButtonAriaLabel="Close" />
    </>,
  );
  await screen.findByRole("textbox", { name: /^Question text/ });
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  if (!input) throw new Error("The editor has no image input");
  await user.upload(input, png());
}

describe("trình soạn câu hỏi: ảnh bị máy chủ từ chối", () => {
  it("413 hiện thông báo ảnh quá 5 MB bằng chữ đã dịch", async () => {
    fakeApi.post(ASSETS, fail(413));
    await pickImage(userEvent.setup());
    expect(await screen.findByText("The image is larger than 5 MB.")).toBeInTheDocument();
    expect(fakeApi.calls.filter((call) => call.path === ASSETS)).toHaveLength(1);
  });

  it("422 ASSET_TYPE_NOT_ALLOWED và 429 hiện thông báo riêng", async () => {
    fakeApi.post(ASSETS, fail(422, { code: "ASSET_TYPE_NOT_ALLOWED", message: "type" }));
    const user = userEvent.setup();
    await pickImage(user);
    expect(await screen.findByText("Only PNG, JPEG or WebP images are accepted.")).toBeInTheDocument();

    fakeApi.post(ASSETS, fail(429, { code: "RATE_LIMITED", message: "slow down" }));
    await user.upload(document.querySelector<HTMLInputElement>('input[type="file"]')!, png());
    await waitFor(() => expect(screen.getByText(/You uploaded too many files/)).toBeInTheDocument());
  });
});
