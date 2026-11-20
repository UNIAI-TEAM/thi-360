import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { mapExamDetail } from "../api-mapping";
import { ExamReviewCheck } from "../components/exam-review-check";
import { ExamReviewView } from "../components/exam-review-view";
import { ExamVersionsView } from "../components/exam-versions-view";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer, FIXED_PART } from "./fake-exams-server";
import { HSA_001_CHECK } from "./review-fixtures";

vi.mock("next/navigation", () => ({
  usePathname: () => `/exams/${EXAM_ID}/review`,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

/**
 * Bản `en`: tên phần, khối, lựa chọn do mẫu đặt là nội dung đề (DESIGN.md mục 8) nên nằm trong `[data-exam-content]`,
 * còn nhãn giao diện ghép cùng câu ("Block", "Location", "Open") ở ngoài vùng đó. Phép kiểm lẫn ngôn ngữ của E2E bỏ
 * qua cả cây con của thuộc tính này, nên chữ Việt có dấu ở ngoài là lỗi.
 */
const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i;
const UI_WORDS = /\b(Block|Location|Open|Generate|Deep check|Go to|Publish)\b/;

/** Chữ nhìn thấy, chữ ẩn dùng cho `aria-labelledby` và thuộc tính chữ, có dấu tiếng Việt, ngoài mọi vùng exam-content. */
function vietnameseOutsideExamContent(root: HTMLElement): string[] {
  const leaks: string[] = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const text = node.textContent ?? "";
    if (VIETNAMESE.test(text) && !node.parentElement?.closest("[data-exam-content]")) leaks.push(text.trim());
  }
  for (const element of root.querySelectorAll("[aria-label],[title],[placeholder],[alt]")) {
    if (element.closest("[data-exam-content]")) continue;
    for (const name of ["aria-label", "title", "placeholder", "alt"]) {
      const value = element.getAttribute(name);
      if (value && VIETNAMESE.test(value)) leaks.push(`${name}=${value}`);
    }
  }
  return leaks;
}

function examContentTexts(root: HTMLElement): string[] {
  return [...root.querySelectorAll("[data-exam-content]")].map((element) => element.textContent ?? "");
}

const CHECK_PATH = `${EXAM_PATH}/check`;

/** Kết quả kiểm tra HSA đã chụp (đường "Khoa học", "Tiếng Anh", khối và phần tiếng Việt) kèm cảnh báo chấm tay. */
const CHECK_WITH_MANUAL = {
  ...HSA_001_CHECK,
  groups: (HSA_001_CHECK.groups ?? []).map((group, index) =>
    index === 0
      ? {
          ...group,
          status: "WARN",
          issues: [
            {
              key: "MANUAL_GRADING_REQUIRED|blk-22",
              code: "MANUAL_GRADING_REQUIRED",
              severity: "WARNING",
              group: group.group,
              partId: "part-25",
              partName: "Tự luận Ngữ văn",
              subjectCode: "MATH",
              blockId: "blk-22",
              blockName: "Nghị luận xã hội từ 500 chữ",
            },
          ],
        }
      : group,
  ),
};

beforeEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("tab Kiểm tra bản en: tên của mẫu nằm trong data-exam-content", () => {
  async function renderCheck() {
    new FakeServer().on("GET", CHECK_PATH, { status: 200, body: CHECK_WITH_MANUAL }).install();
    const view = renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />, {
      locale: "en",
      messages: en,
    });
    await screen.findByRole("heading", { name: "Details by block" });
    return view;
  }

  it("không còn chữ Việt có dấu ngoài vùng nội dung đề, kể cả trong thuộc tính", async () => {
    const { container } = await renderCheck();
    expect(vietnameseOutsideExamContent(container)).toEqual([]);
  });

  it("tên nằm riêng trong vùng nội dung, nhãn giao diện đi cùng câu thì không", async () => {
    const { container } = await renderCheck();
    const names = examContentTexts(container);

    // Khối "Môn · tên khối", phần, tên đường và dòng vị trí: chỉ phần tên của mẫu nằm trong vùng.
    expect(names).toContain("Nghị luận xã hội từ 500 chữ");
    expect(names).toContain("Tự luận Ngữ văn");
    expect(names).toContain("Khoa học");
    expect(names).toContain("Tiếng Anh");
    expect(names.filter((text) => UI_WORDS.test(text))).toEqual([]);

    // Câu giao diện "Location: …" và "Block … has manually graded questions" giữ nguyên chữ ngoài vùng.
    const sentence = screen.getAllByText(/manually graded questions/, { selector: "p" })[0]!;
    expect(sentence.textContent).toContain("Block");
    expect(sentence.querySelector("[data-exam-content]")?.textContent).toBe("Nghị luận xã hội từ 500 chữ");
  });

  it("điều khiển nêu tên bằng aria-labelledby: tên truy cập đủ câu, tên đối tượng trong vùng nội dung", async () => {
    await renderCheck();
    const fix = screen.getAllByRole("link", { name: /^Go to the matrix row to fix for/ })[0]!;
    expect(fix).toHaveAccessibleName("Go to the matrix row to fix for Khoa học");
    expect(fix.getAttribute("aria-label")).toBe("Go to the matrix row to fix for");
    expect(screen.getAllByRole("link", { name: /^Open block / }).length).toBeGreaterThan(0);
  });
});

/** Nhóm có vấn đề gắn `optionId`: câu "Phần (Lựa chọn): …" và "Lựa chọn: …" dựng từ `useIssueWithPath`. */
const CHECK_WITH_OPTION_ISSUES = {
  ...CHECK_WITH_MANUAL,
  groups: (CHECK_WITH_MANUAL.groups ?? []).map((group) => {
    const issue = (code: string, extra: Record<string, unknown>) => ({
      key: `${code}|${group.group}`,
      code,
      severity: "UNCONFIRMED",
      group: group.group,
      partId: "part-33",
      ...extra,
    });
    if (group.group === "STRUCTURE") {
      // Câu mở đầu bằng tên phần, tên phần không chứa tên lựa chọn: "Tự luận Ngữ văn (Tiếng Anh): …".
      return { ...group, status: "WARN", issues: [issue("OPTION_UNCONFIRMED", { partName: "Tự luận Ngữ văn", optionId: "opt-30" })] };
    }
    if (group.group === "MATRIX") {
      // Câu không mở đầu bằng tên phần: "Khoa học: Block …".
      return {
        ...group,
        status: "WARN",
        issues: [issue("CELL_SHORTAGE", { subjectCode: "MATH", blockId: "blk-1", blockName: "Hàm số bậc hai", optionId: "opt-sci-1", required: 4, available: 2 })],
      };
    }
    return group;
  }),
};

describe("tab Kiểm tra bản en: vấn đề có optionId", () => {
  it("tên phần và tên lựa chọn nằm trong vùng nội dung, câu giao diện ở ngoài", async () => {
    new FakeServer().on("GET", CHECK_PATH, { status: 200, body: CHECK_WITH_OPTION_ISSUES }).install();
    const { container } = renderWithProviders(<ExamReviewCheck exam={mapExamDetail(examDetailWire())} onPreview={() => {}} active />, {
      locale: "en",
      messages: en,
    });
    await screen.findByRole("heading", { name: "Details by block" });

    // Thẻ nhóm dựng câu bằng `useIssueWithPath`; danh sách lỗi bên dưới dùng `useIssueNode` nên không ở trong phép kiểm này.
    const groups = within(document.querySelector<HTMLElement>('section[aria-labelledby="review-groups-title"]')!);
    const partSentences = groups.getAllByText(/the per-subject counts of this option await expert confirmation/, { selector: "p" });
    expect(partSentences.length).toBeGreaterThan(0);
    for (const sentence of partSentences) {
      expect(sentence.textContent).toMatch(/^Tự luận Ngữ văn \(Tiếng Anh\): /);
      expect([...sentence.querySelectorAll("[data-exam-content]")].map((node) => node.textContent)).toEqual(["Tự luận Ngữ văn", "Tiếng Anh"]);
    }

    const pathSentences = groups.getAllByText((_, element) => element?.tagName === "P" && /^Khoa học: /.test(element.textContent ?? ""));
    expect(pathSentences.length).toBeGreaterThan(0);
    for (const sentence of pathSentences) expect(sentence.querySelector("[data-exam-content]")?.textContent).toBe("Khoa học");
    expect(vietnameseOutsideExamContent(container)).toEqual([]);
  });
});

describe("tab Kiểm tra bản en: ô Đường lựa chọn của Đề xem trước", () => {
  it("tên đường đang chọn và các lựa chọn nằm trong vùng nội dung, chữ giao diện ở ngoài", async () => {
    new FakeServer()
      .on("GET", EXAM_PATH, {
        status: 200,
        body: examDetailWire({ parts: [{ ...FIXED_PART, participation: { mode: "CHOICE", choose: 1 } }] as never }),
      })
      .on("GET", CHECK_PATH, { status: 200, body: CHECK_WITH_MANUAL })
      .install();
    const { container } = renderWithProviders(<ExamReviewView examId={EXAM_ID} />, { locale: "en", messages: en });
    await screen.findByRole("heading", { name: "Details by block" });

    // Tab Đề xem trước giữ trong DOM (ẩn) khi đang ở tab Kiểm tra: kiểm lẫn ngôn ngữ vẫn quét nó.
    const combobox = screen.getByRole("combobox", { name: "Choice path", hidden: true });
    expect(combobox.querySelector("[data-exam-content]")?.textContent).toMatch(/^(Khoa học|Tiếng Anh)$/);
    expect(vietnameseOutsideExamContent(container)).toEqual([]);

    const user = userEvent.setup();
    await user.click(screen.getByRole("tab", { name: "Preview paper" }));
    await user.click(screen.getByRole("combobox", { name: "Choice path" }));
    const options = within(await screen.findByRole("listbox")).getAllByRole("option");
    const texts = options.map((option) => option.textContent);
    expect(texts).toContain("Khoa học (cannot generate)");
    expect(options.every((option) => option.querySelector("[data-exam-content]"))).toBe(true);
    expect(vietnameseOutsideExamContent(document.body)).toEqual([]);
  });
});

describe("hộp thoại công bố bản en", () => {
  it("tên đường lựa chọn và tên bài nằm trong vùng nội dung đề", async () => {
    const paths = (HSA_001_CHECK.paths ?? []).map((path) => ({ pathId: path.pathId, choices: path.choices }));
    new FakeServer()
      .on("GET", EXAM_PATH, {
        status: 200,
        body: examDetailWire({ name: "Đề thi thử đợt một", check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" } }),
      })
      .on("GET", CHECK_PATH, { status: 200, body: HSA_001_CHECK })
      .on("GET", `${EXAM_PATH}/versions`, {
        status: 200,
        body: {
          examId: EXAM_ID,
          workingCopy: {
            editedBy: { id: "u1", name: "Cô Lan" },
            savedAt: "2026-11-17T02:00:00Z",
            check: { status: "VALID", issueCount: 0, checkedAt: "2026-11-17T01:00:00Z" },
            unpublishedPaths: paths,
          },
          changes: [],
          items: [],
        },
      })
      .install();
    const user = userEvent.setup();
    renderWithProviders(<ExamVersionsView examId={EXAM_ID} />, { locale: "en", messages: en });
    await user.click(await screen.findByRole("button", { name: /^Publish/ }));
    const dialog = await screen.findByRole("dialog");
    await within(dialog).findAllByRole("checkbox");

    expect(vietnameseOutsideExamContent(dialog)).toEqual([]);
    const names = examContentTexts(dialog);
    expect(names).toContain("Đề thi thử đợt một");
    expect(names).toContain("Khoa học");
    expect(names).toContain("Tiếng Anh");
    expect(names.filter((text) => UI_WORDS.test(text))).toEqual([]);
    // Nhãn của ô chọn vẫn là tên đường.
    expect(within(dialog).getByRole("checkbox", { name: "Tiếng Anh" })).toBeInTheDocument();
  });
});
