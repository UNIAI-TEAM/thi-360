import { describe, expect, it } from "vitest";
import { ContentApiError } from "@/features/content/api-error";
import { getQuestion } from "../api";
import {
  createQuestion,
  duplicateQuestion,
  getQuestionUsage,
  listQuestionVersions,
  restoreQuestionVersion,
  updateQuestion,
} from "../editor-api";
import { toEditorValues, toSaveRequest } from "../editor-form";
import { deriveVersionStates, diffSnapshots, servingVersion, toSnapshot } from "../editor-versioning";
import type { UpdateQuestionRequest } from "../editor-types";
import { withQuestionsServer } from "./api-fixtures";

withQuestionsServer({
  usage: {
    "q-0003": {
      examIds: ["ex-1", "ex-2", "ex-3"],
      ledger: [
        { examId: "ex-1", kind: "ATTEMPT", versionNo: 1 },
        { examId: "ex-2", kind: "ATTEMPT", versionNo: 2 },
      ],
    },
  },
});

async function editRequest(id: string, change: (request: UpdateQuestionRequest) => void): Promise<UpdateQuestionRequest> {
  const question = await getQuestion(id);
  const request: UpdateQuestionRequest = { ...toSaveRequest(toEditorValues(question)), expectedRevision: question.revision };
  change(request);
  return request;
}

describe("trạng thái phiên bản", () => {
  it("câu Sẵn sàng: bản mới nhất là hiện hành, bản cũ đã thay thế", () => {
    const states = deriveVersionStates(
      [
        { version: 1, readyAt: "2026-01-01" },
        { version: 2, readyAt: "2026-02-01" },
      ],
      { version: 2, status: "READY" },
    );
    expect([...states]).toEqual([
      [2, "CURRENT"],
      [1, "SUPERSEDED"],
    ]);
    expect(servingVersion(states)).toBe(2);
  });

  it("câu đang có bản Nháp: bản Sẵn sàng gần nhất vẫn hiện hành", () => {
    const states = deriveVersionStates(
      [
        { version: 1, readyAt: "2026-01-01" },
        { version: 2, readyAt: "2026-02-01" },
        { version: 3, readyAt: null },
      ],
      { version: 3, status: "DRAFT" },
    );
    expect(states.get(3)).toBe("WORKING");
    expect(states.get(2)).toBe("CURRENT");
    expect(states.get(1)).toBe("SUPERSEDED");
  });

  it("câu Ngừng sử dụng không còn bản hiện hành", () => {
    const states = deriveVersionStates([{ version: 1, readyAt: "2026-01-01" }], { version: 1, status: "RETIRED" });
    expect(states.get(1)).toBe("RETIRED");
    expect(servingVersion(states)).toBeNull();
  });

  it("so sánh bỏ qua id dòng và khoảng trắng lời giải", async () => {
    const question = await getQuestion("q-0001");
    const before = toSnapshot(question);
    const after = toSnapshot({ ...question, solution: `${question.solution ?? ""}  `, gradeCode: "G12" });
    expect(diffSnapshots(before, after).map((change) => change.field)).toEqual(["gradeCode"]);
    const renamed = toSnapshot({
      ...question,
      body:
        question.body.type === "SINGLE_CHOICE"
          ? { ...question.body, options: question.body.options.map((option, index) => ({ ...option, id: `x-${index}` })) }
          : question.body,
    });
    expect(diffSnapshots(before, renamed)).toEqual([]);
  });
});

describe("API soạn câu hỏi (nhánh thật, client giả)", () => {
  it("tạo câu mới luôn ở trạng thái Nháp, phiên bản 1, có một mục lịch sử", async () => {
    const source = await getQuestion("q-0001");
    const created = await createQuestion(toSaveRequest(toEditorValues(source)));
    expect(created).toMatchObject({ status: "DRAFT", version: 1, usedInExamCount: 0, type: "SINGLE_CHOICE" });
    expect(created.code).toMatch(/^CH-\d{6}$/);
    expect(created.id).not.toBe(source.id);
    const versions = await listQuestionVersions(created.id);
    expect(versions.versions.map((entry) => [entry.version, entry.state])).toEqual([[1, "WORKING"]]);
  });

  it("sửa câu Sẵn sàng tạo phiên bản Nháp mới, bản cũ vẫn hiện hành", async () => {
    const request = await editRequest("q-0021", (r) => {
      r.content = "Đề bài đã sửa";
    });
    const { question, versionCreated } = await updateQuestion("q-0021", request);
    expect(versionCreated).toBe(true);
    expect(question).toMatchObject({ version: 3, workingVersion: 4, status: "READY", content: "Đề bài đã sửa" });

    const versions = await listQuestionVersions("q-0021");
    expect(versions.versions.map((entry) => [entry.version, entry.state])).toEqual([
      [4, "WORKING"],
      [3, "CURRENT"],
      [2, "SUPERSEDED"],
      [1, "SUPERSEDED"],
    ]);
    const [latest, serving] = versions.versions;
    expect(diffSnapshots(serving!.snapshot, latest!.snapshot).map((change) => change.field)).toEqual(["content"]);
  });

  it("sửa bản Nháp ghi đè tại chỗ, không tạo phiên bản", async () => {
    const request = await editRequest("q-0006", (r) => {
      r.content = "Bản nháp mới";
    });
    const { question, versionCreated } = await updateQuestion("q-0006", request);
    expect(versionCreated).toBe(false);
    expect(question.version).toBe(2);
    const versions = await listQuestionVersions("q-0006");
    // Câu Nháp chưa từng Sẵn sàng không có bản đang phục vụ: bản 1 đã được thay thế.
    expect(versions.versions.map((entry) => entry.state)).toEqual(["WORKING", "SUPERSEDED"]);
    expect(versions.versions[0]?.snapshot.content).toBe("Bản nháp mới");
  });

  it("báo 412 QUESTION_REVISION_CONFLICT khi revision đã đổi và 422 khi đổi dạng câu", async () => {
    const stale = await editRequest("q-0001", (r) => {
      r.expectedRevision = 99;
    });
    await expect(updateQuestion("q-0001", stale)).rejects.toMatchObject({ status: 412, code: "QUESTION_REVISION_CONFLICT" });
    const retyped = await editRequest("q-0001", (r) => {
      r.type = "MULTIPLE_CHOICE";
      if (r.body.type === "SINGLE_CHOICE") r.body = { ...r.body, type: "MULTIPLE_CHOICE", scoringRule: "ALL_OR_NOTHING" };
    });
    await expect(updateQuestion("q-0001", retyped)).rejects.toMatchObject({ status: 422, code: "QUESTION_TYPE_IMMUTABLE" });
  });

  it("khôi phục phiên bản cũ chép thành bản Nháp mới", async () => {
    const before = await listQuestionVersions("q-0021");
    const oldest = before.versions[before.versions.length - 1]!;
    const { question, versionCreated } = await restoreQuestionVersion("q-0021", {
      version: oldest.version,
      expectedRevision: (await getQuestion("q-0021")).revision,
    });
    expect(versionCreated).toBe(true);
    expect(question).toMatchObject({ status: "READY", workingVersion: before.latestVersion + 1 });
    expect(question.content).toBe(oldest.snapshot.content);
    const after = await listQuestionVersions("q-0021");
    expect(after.latestVersion).toBe(before.latestVersion + 1);

    await expect(
      restoreQuestionVersion("q-0021", { version: 42, expectedRevision: (await getQuestion("q-0021")).revision }),
    ).rejects.toBeInstanceOf(ContentApiError);
  });

  it("nhân bản câu chùm tạo câu Nháp mới với câu con mới", async () => {
    const source = await getQuestion("q-0027");
    const copy = await duplicateQuestion("q-0027");
    expect(copy).toMatchObject({ status: "DRAFT", version: 1, usedInExamCount: 0, type: "QUESTION_SET" });
    expect(copy.children).toHaveLength(source.children.length);
    expect(copy.children.every((child) => !source.children.some((original) => original.id === child.id))).toBe(true);
    if (copy.body.type === "QUESTION_SET") expect(copy.body.childIds).toEqual(copy.children.map((child) => child.id));
    expect((await getQuestion(copy.id)).code).toBe(copy.code);
  });

  it("nơi đang dùng ổn định giữa các lần đọc và khớp số bài thi của câu", async () => {
    const question = await getQuestion("q-0003");
    const first = await getQuestionUsage("q-0003");
    const second = await getQuestionUsage("q-0003");
    expect(second).toEqual(first);
    expect(first.examCount).toBe(question.usedInExamCount);
    expect(first.exams).toHaveLength(question.usedInExamCount);
    const total = first.exams.flatMap((exam) => exam.attemptsByVersion).reduce((sum, entry) => sum + entry.attemptCount, 0);
    expect(total).toBe(first.attemptCount);

    const unused = await getQuestionUsage("q-0002");
    expect(unused.exams).toEqual([]);
  });
});
