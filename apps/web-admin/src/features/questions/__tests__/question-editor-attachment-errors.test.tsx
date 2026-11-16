import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { fail, fakeApi, ok } from "@/test/fake-api";
import { QuestionEditorView } from "../components/question-editor-view";
import { renderEn } from "./editor-test-kit";

vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001/edit",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const ASSETS = "/api/v1/admin/question-assets";
const pdf = () => new File([new Uint8Array([37, 80, 68, 70])], "scan.pdf", { type: "application/pdf" });

async function pickAttachment() {
  const user = userEvent.setup();
  renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
  await screen.findByRole("textbox", { name: /^Question text/ });
  await user.upload(screen.getByLabelText("Choose attachments"), pdf());
}

describe("trình soạn câu hỏi: tệp đính kèm bị máy chủ từ chối", () => {
  it.each([
    { status: 413, body: undefined, text: "scan.pdf: the file is larger than 20 MB." },
    { status: 415, body: undefined, text: "scan.pdf: this file type is not supported." },
    { status: 422, body: { code: "ASSET_TYPE_NOT_ALLOWED", message: "signature" }, text: "scan.pdf: this file type is not supported." },
    {
      status: 429,
      body: { code: "RATE_LIMITED", message: "slow down" },
      text: "scan.pdf: you uploaded too many files in a short time (up to 60 files per 10 minutes). Try again later.",
    },
    { status: 500, body: { code: "INTERNAL", message: "boom" }, text: "scan.pdf: the upload failed." },
  ])("$status hiện lý do riêng cạnh tên tệp", async ({ status, body, text }) => {
    fakeApi.post(ASSETS, fail(status, body));
    await pickAttachment();
    expect(await screen.findByRole("alert")).toHaveTextContent(text);
    expect(fakeApi.calls.filter((call) => call.path === ASSETS)).toHaveLength(1);
  });

  it("tệp được nhận thì không có lỗi và có thông báo đã thêm", async () => {
    fakeApi.post(ASSETS, ok({ id: "as-9", fileName: "scan.pdf", size: 4 }, { status: 201 }));
    await pickAttachment();
    expect(await screen.findByText("scan.pdf")).toBeInTheDocument();
    expect(screen.queryByText(/the upload failed/)).not.toBeInTheDocument();
  });
});
