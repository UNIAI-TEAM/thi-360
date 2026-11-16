import { beforeEach, describe, expect, it, vi } from "vitest";
import { createExam, discontinueExam, updateExamInfo, updateExamStructure } from "../api";
import { ExamsApiError } from "../review-api";
import type { CreateExamPartInput } from "../types";
import { EXAM_ID, EXAM_PATH, examDetailWire, FIXED_PART, FakeServer } from "./fake-exams-server";

// Danh mục tổ hợp là dữ liệu của FE-1 (Nội dung); bài lẻ không dùng tổ hợp nên chỉ cần danh sách rỗng.
vi.mock("@/features/content/api", () => ({ getContentReference: async () => ({ combinations: [] }) }));

let server: FakeServer;

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_THI360_CONTENT_MOCK", "false");
  server = new FakeServer().install();
});

async function rejected(promise: Promise<unknown>): Promise<ExamsApiError> {
  const error = await promise.then(
    () => undefined,
    (caught: unknown) => caught,
  );
  expect(error).toBeInstanceOf(ExamsApiError);
  return error as ExamsApiError;
}

const STRUCTURE_PATH = `${EXAM_PATH}/structure`;
const REVISION_CONFLICT = { status: 412, body: { code: "EXAM_REVISION_CONFLICT", message: "stale" } };

describe("lưu cấu trúc (nhánh thật)", () => {
  it("gửi expectedRevision đang hiển thị, bỏ null và mode của phần; trả bài với revision mới", async () => {
    server.on("PUT", STRUCTURE_PATH, { status: 200, body: examDetailWire({ revision: 8 }) });
    const exam = await updateExamStructure(EXAM_ID, {
      expectedRevision: 7,
      parts: [
        {
          id: "part-1",
          order: 1,
          name: "Phần 1",
          durationMinutes: null,
          questionCount: 10,
          questionCountOnPaper: 10,
          questionCountRequired: 10,
          participation: { mode: "ALL", choose: null },
          maxPoints: null,
          mode: "FIXED",
          sections: [],
          options: [],
        },
      ],
    });

    expect(exam.revision).toBe(8);
    const [request] = server.calls("PUT", STRUCTURE_PATH);
    expect(request!.body).toMatchObject({ expectedRevision: 7, parts: [{ name: "Phần 1", participation: { mode: "ALL" } }] });
    expect(JSON.stringify(request!.body)).not.toMatch(/null|"mode":"FIXED"/);
  });

  it("412 EXAM_REVISION_CONFLICT mang status và mã để màn báo bài đã được sửa ở nơi khác", async () => {
    server.on("PUT", STRUCTURE_PATH, REVISION_CONFLICT);
    const error = await rejected(updateExamStructure(EXAM_ID, { expectedRevision: 6, parts: [] }));
    expect(error).toMatchObject({ status: 412, code: "EXAM_REVISION_CONFLICT" });
  });

  it("422 EXAM_STRUCTURE_INVALID giữ danh sách lỗi theo trường", async () => {
    server.on("PUT", STRUCTURE_PATH, {
      status: 422,
      body: {
        code: "EXAM_STRUCTURE_INVALID",
        fieldErrors: [{ field: "parts[0].durationMinutes", code: "NEGATIVE" }],
      },
    });
    const error = await rejected(updateExamStructure(EXAM_ID, { expectedRevision: 7, parts: [] }));
    expect(error).toMatchObject({ status: 422, code: "EXAM_STRUCTURE_INVALID" });
    expect(error.fieldErrors).toEqual([{ field: "parts[0].durationMinutes", code: "NEGATIVE" }]);
  });
});

describe("sửa thông tin chung (nhánh thật)", () => {
  const request = {
    name: "HSA đề 1 (sửa)",
    code: "HSA-F-001",
    kind: "HSA",
    scope: "FULL" as const,
    description: null,
    expectedRevision: 7,
  };

  it("PATCH gửi expectedRevision; mô tả để trống gửi chuỗi rỗng để xóa", async () => {
    server.on("PATCH", EXAM_PATH, { status: 200, body: examDetailWire({ name: request.name, revision: 8 }) });
    const exam = await updateExamInfo(EXAM_ID, request);

    expect(exam).toMatchObject({ name: "HSA đề 1 (sửa)", revision: 8 });
    expect(server.calls("PATCH", EXAM_PATH)[0]!.body).toMatchObject({ expectedRevision: 7, description: "" });
  });

  it("412 và 422 theo trường được giữ nguyên mã", async () => {
    server.on(
      "PATCH",
      EXAM_PATH,
      REVISION_CONFLICT,
      { status: 422, body: { code: "EXAM_MAX_SCORE_INVALID", fieldErrors: [{ field: "maxScore", code: "POSITIVE" }] } },
      { status: 409, body: { code: "EXAM_CODE_TAKEN" } },
    );
    expect(await rejected(updateExamInfo(EXAM_ID, request))).toMatchObject({ status: 412, code: "EXAM_REVISION_CONFLICT" });
    const invalid = await rejected(updateExamInfo(EXAM_ID, request));
    expect(invalid).toMatchObject({ status: 422, code: "EXAM_MAX_SCORE_INVALID" });
    expect(invalid.fieldErrors).toEqual([{ field: "maxScore", code: "POSITIVE" }]);
    expect(await rejected(updateExamInfo(EXAM_ID, request))).toMatchObject({ status: 409, code: "EXAM_CODE_TAKEN" });
  });
});

describe("tạo bài từ mẫu (nhánh thật)", () => {
  const base = { name: "HSA đề 2", code: "HSA-F-002", kind: "HSA", scope: "FULL" as const, description: null };
  const edited: CreateExamPartInput[] = [
    {
      name: "Phần 1 đã sửa",
      questionCount: 12,
      durationMinutes: 45,
      mode: "FIXED",
      subjectCode: "MATH",
      englishOption: false,
      combinations: [],
    },
  ];

  it("chưa sửa cấu trúc: chỉ POST, có Idempotency-Key, không gửi cấu trúc", async () => {
    server.on("POST", "/api/v1/admin/exams", { status: 201, body: examDetailWire({ revision: 1, parts: [FIXED_PART] }) });
    const exam = await createExam(base);

    expect(exam.revision).toBe(1);
    expect(server.requests).toHaveLength(1);
    const [post] = server.requests;
    expect(post!.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(post!.body).not.toHaveProperty("parts");
  });

  it("đã sửa cấu trúc: POST rồi PUT cấu trúc với revision của bài vừa tạo", async () => {
    server
      .on("POST", "/api/v1/admin/exams", { status: 201, body: examDetailWire({ revision: 3 }) })
      .on("PUT", STRUCTURE_PATH, { status: 200, body: examDetailWire({ revision: 4 }) });
    const exam = await createExam({ ...base, parts: edited });

    expect(server.requests.map((item) => `${item.method} ${item.url.pathname}`)).toEqual([
      "POST /api/v1/admin/exams",
      `PUT ${STRUCTURE_PATH}`,
    ]);
    expect(exam.revision).toBe(4);
    expect(server.requests[1]!.body).toMatchObject({
      expectedRevision: 3,
      parts: [{ name: "Phần 1 đã sửa", questionCount: 12, durationMinutes: 45, sections: [{ subjectCode: "MATH" }] }],
    });
  });

  it("lưu cấu trúc lỗi thì báo lỗi của bước đó; bài đã tạo không bị tạo lại", async () => {
    server
      .on("POST", "/api/v1/admin/exams", { status: 201, body: examDetailWire({ revision: 3 }) })
      .on("PUT", STRUCTURE_PATH, { status: 422, body: { code: "EXAM_STRUCTURE_INVALID" } });

    expect(await rejected(createExam({ ...base, parts: edited }))).toMatchObject({ status: 422, code: "EXAM_STRUCTURE_INVALID" });
    expect(server.calls("POST", "/api/v1/admin/exams")).toHaveLength(1);
  });
});

describe("ngừng bán từ danh sách (nhánh thật)", () => {
  it("đọc revision hiện tại của bài rồi gửi Idempotency-Key và expectedRevision đó", async () => {
    server
      .on("GET", EXAM_PATH, { status: 200, body: examDetailWire({ status: "PUBLISHED", revision: 9, servingVersion: 2 }) })
      .on("POST", `${EXAM_PATH}/discontinue`, { status: 200, body: examDetailWire({ status: "DISCONTINUED", revision: 10 }) });

    // Dòng danh sách không có revision: không gửi `expectedRevision`, client đọc revision hiện tại rồi gửi số đó.
    const exam = await discontinueExam(EXAM_ID);

    expect(exam.status).toBe("DISCONTINUED");
    const [post] = server.calls("POST", `${EXAM_PATH}/discontinue`);
    expect(post!.body).toEqual({ expectedRevision: 9 });
    expect(post!.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
  });
});
