import { readFileSync } from "node:fs";
import { join } from "node:path";
import { screen, waitFor } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import type { Editor } from "@tiptap/core";
import { describe, expect, it, vi, type Mock } from "vitest";
import type * as QuestionsApi from "@/features/questions/api";
import { ApiStatusError } from "@/lib/api/query-client";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { IMAGE_FIGURE_NODE } from "../../../extensions";
import { RichTextEditor } from "../../../rich-text-editor";
import { parseFigure } from "../figure-html";

vi.mock("@/features/questions/api", async (importOriginal) => {
  const actual = await importOriginal<typeof QuestionsApi>();
  return { ...actual, uploadQuestionAsset: vi.fn() };
});
import { uploadQuestionAsset } from "@/features/questions/api";

interface Sample {
  name: string;
  html: string;
}
const fixture = JSON.parse(
  readFileSync(join(process.cwd(), "../../docs/specs/fixtures/rich-text-samples.json"), "utf8"),
) as { samples: Sample[] };
const FIGURE_HTML = fixture.samples.find((sample) => sample.name === "image-figure")!.html;
const EXPECTED = (() => {
  const holder = document.createElement("div");
  holder.innerHTML = FIGURE_HTML;
  const figure = holder.querySelector("figure")!;
  return parseFigure({ html: figure.innerHTML, width: figure.dataset.width, zoom: figure.dataset.zoom });
})();
const ASSET = EXPECTED.asset!;
const OTHER_ASSET = { id: "0c1d2e3f-0c1d-4c1d-8c1d-0c1d2e3f4a5b", url: "/api/v1/admin/question-assets/0c1d2e3f-0c1d-4c1d-8c1d-0c1d2e3f4a5b" };
const png = () => new File([new Uint8Array([137, 80, 78, 71])], "chart.png", { type: "image/png" });

function renderEditor(props: Partial<Parameters<typeof RichTextEditor>[0]> = {}) {
  const onChange = vi.fn();
  renderWithProviders(<RichTextEditor value="" onChange={onChange} {...props} />, { locale: "en", messages: en });
  return onChange;
}

function tiptap(): Editor {
  const el = document.querySelector<HTMLElement & { editor?: Editor }>(".ProseMirror");
  if (!el?.editor) throw new Error("Editor is not ready");
  return el.editor;
}

const lastHtml = (onChange: Mock) => onChange.mock.lastCall?.[0] as string;
const fileInput = () => screen.getByLabelText("Choose image file");
const insertButton = () => screen.getByRole("button", { name: /^(Insert|Update) image$/ });

async function openDialog(user: UserEvent) {
  await user.click(screen.getByRole("button", { name: "Image" }));
  await screen.findByRole("dialog");
}

async function selectFigure(user: UserEvent) {
  await waitFor(() => expect(tiptap()).toBeTruthy());
  let pos = -1;
  tiptap().state.doc.descendants((node, at) => {
    if (node.type.name === IMAGE_FIGURE_NODE) pos = at;
  });
  tiptap().commands.setNodeSelection(pos);
  await waitFor(() => expect(screen.getByRole("button", { name: "Image" })).toHaveAttribute("aria-pressed", "true"));
  await openDialog(user);
}

async function fillFigure(user: UserEvent) {
  await user.type(screen.getByLabelText(/^Alternative text/), EXPECTED.alt);
  await user.type(screen.getByLabelText("Caption below the image"), EXPECTED.caption);
  await user.type(screen.getByLabelText("Source"), EXPECTED.source);
}

describe("image dialog", () => {
  it("opens from the toolbar Image button without any direct upload", async () => {
    vi.mocked(uploadQuestionAsset).mockClear();
    const user = userEvent.setup();
    renderEditor();
    expect(document.querySelector('input[type="file"]')).toBeNull();

    await openDialog(user);

    expect(screen.getByRole("heading", { name: "Image" })).toBeInTheDocument();
    expect(uploadQuestionAsset).not.toHaveBeenCalled();
  });

  it("keeps Insert disabled until an image is uploaded and the alternative text is filled, then emits the fixture HTML", async () => {
    vi.mocked(uploadQuestionAsset).mockResolvedValue({ id: ASSET.id, url: ASSET.url, fileName: "chart.png", size: 4 });
    const user = userEvent.setup();
    const onChange = renderEditor();
    await openDialog(user);

    expect(insertButton()).toBeDisabled();
    await user.upload(fileInput(), png());
    expect(await screen.findByRole("button", { name: "Replace image" })).toBeInTheDocument();
    expect(uploadQuestionAsset).toHaveBeenLastCalledWith(expect.any(File), "IMAGE");
    // Có ảnh mà thiếu mô tả: vẫn khóa.
    expect(insertButton()).toBeDisabled();

    await fillFigure(user);
    expect(insertButton()).toBeEnabled();
    await user.click(insertButton());

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(lastHtml(onChange)).toBe(FIGURE_HTML);
  });

  it("treats a blank alternative text as missing", async () => {
    vi.mocked(uploadQuestionAsset).mockResolvedValue({ id: ASSET.id, url: ASSET.url, fileName: "chart.png", size: 4 });
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);
    await user.upload(fileInput(), png());
    await screen.findByRole("button", { name: "Replace image" });

    await user.type(screen.getByLabelText(/^Alternative text/), "   ");

    expect(insertButton()).toBeDisabled();
  });

  it("stores the chosen width and zoom flag", async () => {
    vi.mocked(uploadQuestionAsset).mockResolvedValue({ id: ASSET.id, url: ASSET.url, fileName: "chart.png", size: 4 });
    const user = userEvent.setup();
    const onChange = renderEditor();
    await openDialog(user);
    await user.upload(fileInput(), png());
    await screen.findByRole("button", { name: "Replace image" });
    await user.type(screen.getByLabelText(/^Alternative text/), "Chart");

    expect(screen.getByRole("tab", { name: "Medium" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("checkbox", { name: /enlarge the image/ })).toBeChecked();
    await user.click(screen.getByRole("tab", { name: "Full width" }));
    await user.click(screen.getByRole("checkbox", { name: /enlarge the image/ }));
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain('data-width="full" data-zoom="false"'));
    expect(lastHtml(onChange)).toContain('alt="Chart"');
    // Không chú thích và nguồn thì không có figcaption và p.figure-source.
    expect(lastHtml(onChange)).not.toContain("figcaption");
    expect(lastHtml(onChange)).not.toContain("figure-source");
  });

  it("reopens a stored figure with all its state and rewrites it identically when nothing changes", async () => {
    const user = userEvent.setup();
    const stored = FIGURE_HTML.replace('data-width="medium" data-zoom="true"', 'data-width="small" data-zoom="false"');
    const onChange = renderEditor({ value: stored });
    await selectFigure(user);

    expect(screen.getByLabelText(/^Alternative text/)).toHaveValue(EXPECTED.alt);
    expect(screen.getByLabelText("Caption below the image")).toHaveValue(EXPECTED.caption);
    expect(screen.getByLabelText("Source")).toHaveValue(EXPECTED.source);
    expect(screen.getByRole("tab", { name: "Small" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("checkbox", { name: /enlarge the image/ })).not.toBeChecked();
    expect(screen.getByRole("img", { name: EXPECTED.alt })).toHaveAttribute("src", ASSET.url);

    await user.click(insertButton());
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    // Không đổi gì thì không có thay đổi để phát; nếu có phát thì đúng nguyên văn.
    const html = onChange.mock.lastCall ? lastHtml(onChange) : stored;
    expect(html).toBe(stored);
    expect(tiptap().getHTML()).toContain(stored);
  });

  it("replaces the image of a stored figure in place and keeps the other fields", async () => {
    vi.mocked(uploadQuestionAsset).mockResolvedValue({ ...OTHER_ASSET, fileName: "other.png", size: 4 });
    const user = userEvent.setup();
    const onChange = renderEditor({ value: `<p>before</p>${FIGURE_HTML}` });
    await selectFigure(user);

    await user.upload(fileInput(), png());
    await waitFor(() => expect(screen.getByRole("img", { name: EXPECTED.alt })).toHaveAttribute("src", OTHER_ASSET.url));
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain(`data-asset-id="${OTHER_ASSET.id}"`));
    const html = lastHtml(onChange);
    expect(html).toBe(FIGURE_HTML.replaceAll(ASSET.id, OTHER_ASSET.id).replace(/^/, "<p>before</p>"));
    expect(html.match(/<figure/g)).toHaveLength(1);
  });

  it("removes the image: Insert is disabled again until another image is chosen", async () => {
    const user = userEvent.setup();
    renderEditor({ value: FIGURE_HTML });
    await selectFigure(user);
    expect(insertButton()).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "Remove image" }));

    expect(insertButton()).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Remove image" })).toBeNull();
    expect(screen.getByRole("button", { name: "Choose image" })).toBeInTheDocument();
    // Mô tả vẫn còn để dùng cho ảnh thay thế.
    expect(screen.getByLabelText(/^Alternative text/)).toHaveValue(EXPECTED.alt);
  });

  it("keeps inline markup of an untouched caption and rebuilds it once edited", async () => {
    const user = userEvent.setup();
    const marked = FIGURE_HTML.replace("<figcaption>Bi", "<figcaption><sub>2</sub>Bi");
    const onChange = renderEditor({ value: marked });
    await selectFigure(user);

    await user.type(screen.getByLabelText("Source"), "!");
    await user.click(insertButton());

    await waitFor(() => expect(lastHtml(onChange)).toContain("<figcaption><sub>2</sub>Bi"));
  });

  it("shows the translated message when the upload is rejected", async () => {
    vi.mocked(uploadQuestionAsset).mockRejectedValue(new ApiStatusError(413, "too large"));
    const user = userEvent.setup();
    renderEditor();
    await openDialog(user);

    await user.upload(fileInput(), png());

    expect(await screen.findByText("The image is larger than 5 MB.")).toBeInTheDocument();
    expect(insertButton()).toBeDisabled();
  });

  it("is not offered when the field is the exam instructions", () => {
    renderEditor({ fieldContext: "instructions", allowImages: false });

    expect(screen.queryByRole("button", { name: "Image" })).toBeNull();
  });
});
