import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { ExamVersionsView } from "../components/exam-versions-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

vi.mock("next/navigation", () => ({
  usePathname: () => "/exams/ex/versions",
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

/**
 * F6 (UNI-2686): bài QDA FULL có phần lấy lựa chọn từ danh mục tổ hợp. Kiểm tra và máy chủ dùng id đường
 * `<partId>:<mã tổ hợp>`; hộp thoại công bố lọc đường của kiểm tra theo `workingCopy.unpublishedPaths`,
 * nên danh sách đó phải mang đúng các id này.
 */
const PART = "5b0ac7f0-1c6d-5a38-8f2e-000000000001";
const COMBINATIONS = [
  ["QD01", "Toán, Lý, Hóa"],
  ["QD02", "Toán, Lý, Anh"],
  ["QD03", "Toán, Hóa, Sinh"],
  ["QD04", "Văn, Sử, Địa"],
  ["QD05", "Văn, Toán, Anh"],
] as const;
const choice = ([code, name]: readonly [string, string]) => ({
  partId: PART,
  optionId: `${PART}:${code}`,
  kind: "COMBINATION",
  code,
  name,
});
const pathRef = (combination: readonly [string, string]) => ({
  pathId: `${PART}:${combination[0]}`,
  choices: [choice(combination)],
});
const PUBLISH_PATH = `${EXAM_PATH}/publish`;

let server: FakeServer;

beforeEach(() => {
  window.history.replaceState(null, "", "/");
  server = new FakeServer()
    .on("GET", EXAM_PATH, {
      status: 200,
      body: examDetailWire({
        kind: "QDA",
        code: "QDA-F-001",
        name: "QDA exam",
        check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
      }),
    })
    .on("GET", `${EXAM_PATH}/check`, {
      status: 200,
      body: {
        examId: EXAM_ID,
        status: "VALID",
        stale: false,
        checkedAt: "2026-11-17T01:00:00Z",
        durationSeconds: 3,
        timeLimitSeconds: 30,
        issueCount: 0,
        paths: COMBINATIONS.map((combination) => ({ ...pathRef(combination), result: "VALID", issues: [] })),
      },
    })
    .on("GET", `${EXAM_PATH}/versions`, {
      status: 200,
      body: {
        examId: EXAM_ID,
        workingCopy: {
          editedBy: { id: "u1", name: "Cô Lan" },
          savedAt: "2026-11-17T02:00:00Z",
          check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
          unpublishedPaths: COMBINATIONS.map(pathRef),
        },
        changes: [],
        items: [],
      },
    })
    .on("POST", PUBLISH_PATH, {
      status: 200,
      body: { examId: EXAM_ID, version: 1, publishedPathIds: [], keptPaths: [] },
    })
    .install();
});

async function openPublishDialog() {
  const user = userEvent.setup();
  renderWithProviders(<ExamVersionsView examId={EXAM_ID} />);
  await user.click(await screen.findByRole("button", { name: "Công bố" }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findAllByRole("checkbox");
  return { user, dialog };
}

describe("hộp thoại công bố với đường là tổ hợp", () => {
  it("liệt kê cả năm đường tổ hợp và chọn sẵn các đường hợp lệ", async () => {
    const { dialog } = await openPublishDialog();

    const boxes = within(dialog).getAllByRole("checkbox");
    expect(boxes).toHaveLength(5);
    for (const [code, name] of COMBINATIONS) {
      expect(within(dialog).getByRole("checkbox", { name: `${code} · ${name}` })).toBeChecked();
    }
    expect(within(dialog).getByRole("button", { name: "Công bố 5 đường lựa chọn" })).toBeInTheDocument();
  });

  it("công bố đúng các đường đã chọn, id theo <partId>:<mã>", async () => {
    const { user, dialog } = await openPublishDialog();

    await user.click(within(dialog).getByRole("checkbox", { name: /^QD04/ }));
    await user.click(within(dialog).getByRole("checkbox", { name: /^QD05/ }));
    await user.click(within(dialog).getByRole("button", { name: "Công bố 3 đường lựa chọn" }));

    await waitFor(() => expect(server.calls("POST", PUBLISH_PATH)).toHaveLength(1));
    expect(server.calls("POST", PUBLISH_PATH)[0]!.body).toEqual({
      pathIds: [`${PART}:QD01`, `${PART}:QD02`, `${PART}:QD03`],
      expectedRevision: 7,
    });
  });
});

describe("nút công bố theo số đường đã chọn (F-PUBLISH0)", () => {
  it("không chọn đường nào: nút khóa; chọn một đường: nút mở", async () => {
    const { user, dialog } = await openPublishDialog();
    for (const [code] of COMBINATIONS) await user.click(within(dialog).getByRole("checkbox", { name: new RegExp(`^${code}`) }));

    expect(within(dialog).getByRole("button", { name: "Công bố 0 đường lựa chọn" })).toBeDisabled();

    await user.click(within(dialog).getByRole("checkbox", { name: /^QD02/ }));
    expect(within(dialog).getByRole("button", { name: "Công bố 1 đường lựa chọn" })).toBeEnabled();
  });

  it("đang công bố: nút aria-disabled và bấm thêm không gửi lần hai", async () => {
    server.hold("POST", PUBLISH_PATH);
    const { user, dialog } = await openPublishDialog();
    const button = within(dialog).getByRole("button", { name: "Công bố 5 đường lựa chọn" });
    expect(button).not.toHaveAttribute("aria-disabled");

    await user.click(button);
    await waitFor(() => expect(server.calls("POST", PUBLISH_PATH)).toHaveLength(1));
    await waitFor(() => expect(button).toHaveAttribute("aria-disabled", "true"));

    await user.click(button);
    expect(server.calls("POST", PUBLISH_PATH)).toHaveLength(1);
    server.release("POST", PUBLISH_PATH);
  });
});
