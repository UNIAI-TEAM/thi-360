import { describe, expect, it } from "vitest";
import { ContentApiError } from "@/features/content/api-error";
import { withIdempotencyKey, resetIdempotencyKeys } from "@/features/content/idempotency";
import { fromApiDetail, fromApiSummary } from "../api-mapping";
import { resolveFormPath, toFormPath } from "../editor-field-errors";
import { emptyChild, emptyEditorValues, formatPoints } from "../editor-form";
import { fromApiReadiness } from "../readiness-mapping";
import { plainQuestionPreview } from "../question-summary";

const summary = {
  id: "q1",
  code: "CH-000001",
  type: "SINGLE_CHOICE" as const,
  status: "READY" as const,
  contentPreview: "x",
  points: 1,
  knowledge: [],
  tagCodes: [],
  hasSolution: false,
  version: 2,
  revision: 5,
  usedInExamCount: 0,
  createdAt: "2026-11-17T00:00:00Z",
  updatedAt: "2026-11-17T00:00:00Z",
};

describe("đổi phản hồi API câu hỏi sang kiểu của web", () => {
  it("bản đang phục vụ là version, bản Nháp riêng là workingVersion; câu chưa từng Sẵn sàng lấy bản Nháp", () => {
    expect(fromApiSummary({ ...summary, workingVersion: 3 })).toMatchObject({ version: 2, workingVersion: 3 });
    expect(fromApiSummary({ ...summary, status: "DRAFT", version: 0, workingVersion: 1 })).toMatchObject({
      version: 1,
      workingVersion: null,
    });
    expect(fromApiSummary({ ...summary, subjectCode: "MATH" }).subjectCode).toBe("MATH");
    expect(fromApiSummary(summary).subjectCode).toBe("");
  });

  it("chi tiết: lỗi sai thường gặp ghép thành một ô, câu con và id câu con của bộ", () => {
    const detail = fromApiDetail({
      summary: { ...summary, type: "QUESTION_SET", subjectCode: "MATH" },
      content: "<p>Đoạn</p>",
      commonMistakes: ["<p>a</p>", "<p>b</p>"],
      attachmentIds: [],
      body: { type: "QUESTION_SET", passage: "<p>Đoạn</p>", children: [] } as never,
      children: [{ summary: { ...summary, id: "c1", subjectCode: "PHYSICS" }, content: "x", commonMistakes: [], attachmentIds: [], body: { type: "SINGLE_CHOICE", options: [] } as never, children: [], hasDraft: false }],
      hasDraft: false,
    });
    expect(detail.subjectCode).toBe("MATH");
    expect(detail.body).toMatchObject({ type: "QUESTION_SET", childIds: ["c1"] });
    expect(detail.children[0]).toMatchObject({ id: "c1", subjectCode: "PHYSICS", body: { type: "SINGLE_CHOICE", shuffleOptions: false } });
  });

  it("điều kiện Sẵn sàng: nhóm theo điều kiện, vị trí tính từ 1 và câu con", () => {
    const result = fromApiReadiness({
      ready: false,
      errors: [
        { code: "OPTION_EMPTY", path: "body.options[2].content" },
        { code: "MATCHING_SIDE_EMPTY", path: "body.right[0].content" },
        { code: "NO_CORRECT_OPTION", path: "body.children[1].body.options" },
        { code: "SOMETHING_NEW", path: "x" },
      ],
      warnings: [{ code: "PRIMARY_KNOWLEDGE_MISSING", path: "knowledge" }],
    });
    const byRule = Object.fromEntries(result.rules.map((rule) => [rule.rule, rule.issues]));
    expect(byRule.CONTENT).toEqual([
      { code: "OPTION_EMPTY", field: "content", position: 3 },
      { code: "MATCH_RIGHT_EMPTY", field: "content", position: 1 },
    ]);
    expect(byRule.ANSWER).toEqual([{ code: "NO_CORRECT_OPTION", field: "answers", child: 2 }]);
    expect(byRule.STRUCTURE).toEqual([{ code: "READINESS_OTHER", field: "structure", value: "SOMETHING_NEW" }]);
    expect(result.failedCount).toBe(3);
    expect(result.warnings).toEqual([{ code: "PRIMARY_KNOWLEDGE_MISSING", field: "knowledge" }]);
  });
});

describe("lỗi 422 theo trường", () => {
  it("đường dẫn của máy chủ đổi sang tên trường của biểu mẫu", () => {
    expect(toFormPath("body.options[0].content")).toBe("body.options.0.content");
    expect(toFormPath("body.children[1].body.blanks[0].answers")).toBe("children.1.body.blanks.0.answers");
    expect(toFormPath("body.children[0].content")).toBe("children.0.content");
    expect(toFormPath("commonMistakes[0]")).toBe("commonMistakes");
    expect(toFormPath("body.type")).toBe("body");
  });
});

describe("lỗi 422: đường dẫn có chỗ hiện trên biểu mẫu", () => {
  const values: Record<string, unknown> = {
    type: "MATCHING",
    "body.left": [{ rightId: "" }, { rightId: "r2" }, { rightId: "r3" }],
    "children.0.type": "CLOZE",
  };
  const get = (path: string) => values[path];

  it("body.pairs[n] chỉ đếm các mục trái đã chọn đáp án nên đổi về dòng trái tương ứng", () => {
    expect(resolveFormPath("body.pairs[0].rightId", get)).toBe("body.left.1.rightId");
    expect(resolveFormPath("body.pairs[1].leftId", get)).toBe("body.left.2.rightId");
    expect(resolveFormPath("body.pairs[1].points", get)).toBe("body.left.2.points");
    expect(resolveFormPath("body.pairs", get)).toBe("body.left");
  });

  it("đường dẫn không có ô hay dòng lỗi (sai dạng câu, trường không vẽ) trả null", () => {
    expect(resolveFormPath("body.options[0].content", get)).toBeNull();
    expect(resolveFormPath("body.type", get)).toBeNull();
    expect(resolveFormPath("competencyCode", get)).toBeNull();
    expect(resolveFormPath("body.children[0].body.left[0].content", get)).toBeNull();
  });

  it("đoạn dẫn, đáp án ngắn và kiến thức đổi về ô của biểu mẫu", () => {
    expect(resolveFormPath("body.children[0].body.passage", get)).toBe("children.0.content");
    expect(resolveFormPath("knowledge[0].nodeId", get)).toBe("knowledge");
    expect(resolveFormPath("content", get)).toBe("content");
  });
});

describe("khóa idempotency", () => {
  it("thử lại sau lỗi dùng lại khóa, lệnh mới sau khi thành công có khóa mới", async () => {
    resetIdempotencyKeys();
    const seen: string[] = [];
    await expect(
      withIdempotencyKey("create:a", async (key) => {
        seen.push(key);
        throw new Error("mạng");
      }),
    ).rejects.toThrow();
    await withIdempotencyKey("create:a", async (key) => void seen.push(key));
    await withIdempotencyKey("create:a", async (key) => void seen.push(key));
    expect(seen[1]).toBe(seen[0]);
    expect(seen[2]).not.toBe(seen[0]);
  });
});

describe("khóa idempotency: quy tắc mayHaveRun", () => {
  it("giữ khóa sau lỗi mạng, 5xx, 429, 408; bỏ khóa sau 4xx chắc chắn", async () => {
    const attempt = async (scope: string, failure: unknown) => {
      const seen: string[] = [];
      for (let index = 0; index < 2; index += 1) {
        await withIdempotencyKey(scope, async (key) => {
          seen.push(key);
          throw failure;
        }).catch(() => undefined);
      }
      return seen;
    };
    resetIdempotencyKeys();
    for (const [index, failure] of [new TypeError("fetch failed"), new ContentApiError(503), new ContentApiError(429), new ContentApiError(408)].entries()) {
      const [first, retry] = await attempt(`kept:${index}`, failure);
      expect(retry).toBe(first);
    }
    for (const [index, status] of [400, 403, 404, 409, 422].entries()) {
      const [first, retry] = await attempt(`dropped:${index}`, new ContentApiError(status));
      expect(retry).not.toBe(first);
    }
  });
});

describe("điểm theo ngôn ngữ", () => {
  it("vi dùng dấu phẩy, en dùng dấu chấm", () => {
    expect(formatPoints(0.25)).toBe("0,25");
    expect(formatPoints(0.25, "vi")).toBe("0,25");
    expect(formatPoints(0.25, "en")).toBe("0.25");
    expect(formatPoints(1, "en")).toBe("1");
  });

  it("điểm mặc định của ý Đúng/Sai và cặp ghép theo ngôn ngữ (F-04)", () => {
    expect(emptyEditorValues("TRUE_FALSE", "en").body.statements.map((row) => row.points)).toEqual(["0.5", "0.5"]);
    expect(emptyEditorValues("TRUE_FALSE", "en").body.scoreTable).toEqual(["0.5", "1"]);
    expect(emptyEditorValues("MATCHING", "en").body.left.map((row) => row.points)).toEqual(["0.5", "0.5"]);
    expect(emptyEditorValues("TRUE_FALSE", "vi").body.statements.map((row) => row.points)).toEqual(["0,5", "0,5"]);
    expect(emptyChild("TRUE_FALSE", "en").body.statements.map((row) => row.points)).toEqual(["0.5", "0.5"]);
  });
});

describe("tóm tắt câu trên danh sách (F-06)", () => {
  it("chỗ trống [[id]] thành ___ và bỏ cặp dấu công thức \\(…\\), \\[…\\]", () => {
    expect(plainQuestionPreview("Điền [[new-b-1]] vào chỗ trống.")).toBe("Điền ___ vào chỗ trống.");
    expect(plainQuestionPreview("Tính \\(x^2\\) và \\[\\int_0^1 x\\] của hàm.")).toBe("Tính x^2 và \\int_0^1 x của hàm.");
    // Dấu đóng không có dấu mở (không cân) giữ nguyên.
    expect(plainQuestionPreview("ngoặc \\) lẻ")).toBe("ngoặc \\) lẻ");
    expect(fromApiSummary({ ...summary, contentPreview: "Điền [[b1]] \\(\\frac{1}{2}\\)." }).contentPreview).toBe(
      "Điền ___ \\frac{1}{2}.",
    );
  });
});
