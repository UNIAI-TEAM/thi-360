import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";
import {
  QuestionAssetApiError,
  questionAssetErrorMessageKey,
  questionAssetErrorReason,
  uploadQuestionAsset,
} from "../asset-upload";

withRealApi();

const ASSETS = "/api/v1/admin/question-assets";
const png = () => new File([new Uint8Array([137, 80, 78, 71])], "hinh.png", { type: "image/png" });

describe("tải tài nguyên câu hỏi (nhánh thật, client giả)", () => {
  it("gửi multipart với kind trên query và đọc phản hồi, thiếu url thì dùng đường dẫn tải xuống", async () => {
    fakeApi.post(ASSETS, ok({ id: "as-1", fileName: "hinh.png", size: 4 }, { status: 201 }));
    const asset = await uploadQuestionAsset(png(), "IMAGE");
    expect(asset).toEqual({ id: "as-1", url: "/api/v1/admin/question-assets/as-1", fileName: "hinh.png", size: 4 });

    const options = fakeApi.calls[0]!.options as { params: { query: { kind: string } }; bodySerializer: () => FormData };
    expect(options.params.query.kind).toBe("IMAGE");
    expect((options.bodySerializer().get("file") as File).name).toBe("hinh.png");
  });

  it("413, ASSET_TOO_LARGE, 422 loại tệp, 429, 403 và lỗi khác đổi thành lý do và khóa chữ đúng theo kind", async () => {
    const cases = [
      { response: fail(413), reason: "TOO_LARGE", image: "imageTooLarge", attachment: "attachmentTooLarge" },
      {
        response: fail(422, { code: "ASSET_TOO_LARGE" }),
        reason: "TOO_LARGE",
        image: "imageTooLarge",
        attachment: "attachmentTooLarge",
      },
      {
        response: fail(422, { code: "ASSET_TYPE_NOT_ALLOWED" }),
        reason: "TYPE_NOT_ALLOWED",
        image: "imageTypeNotAllowed",
        attachment: "attachmentTypeNotAllowed",
      },
      { response: fail(429, { code: "RATE_LIMITED" }), reason: "RATE_LIMITED", image: "rateLimited", attachment: "rateLimited" },
      { response: fail(403, { code: "FORBIDDEN" }), reason: "FORBIDDEN", image: "forbidden", attachment: "forbidden" },
      { response: fail(500, { code: "INTERNAL" }), reason: "UNKNOWN", image: "uploadFailed", attachment: "uploadFailed" },
    ];
    for (const { response, reason, image, attachment } of cases) {
      fakeApi.post(ASSETS, response);
      const failure = await uploadQuestionAsset(png(), "IMAGE").catch((error: unknown) => error);
      expect(failure).toBeInstanceOf(QuestionAssetApiError);
      expect(questionAssetErrorReason(failure)).toBe(reason);
      expect(questionAssetErrorMessageKey(failure, "IMAGE")).toBe(image);
      expect(questionAssetErrorMessageKey(failure, "ATTACHMENT")).toBe(attachment);
    }
  });

  it("mọi khóa chữ lỗi tải có đủ ở tiếng Việt và tiếng Anh, giới hạn nêu đúng 5 MB và 20 MB", () => {
    const keys = [
      "imageTooLarge",
      "attachmentTooLarge",
      "imageTypeNotAllowed",
      "attachmentTypeNotAllowed",
      "rateLimited",
      "forbidden",
      "uploadFailed",
    ] as const;
    for (const messages of [vi, en]) {
      const errors: Record<string, string> = messages.questions.assets.errors;
      expect(Object.keys(errors).sort()).toEqual([...keys].sort());
      for (const key of keys) expect(errors[key]?.trim(), key).toBeTruthy();
    }
    expect(en.questions.assets.errors.imageTooLarge).toContain("5 MB");
    expect(vi.questions.assets.errors.imageTooLarge).toContain("5 MB");
    expect(en.questions.assets.errors.attachmentTooLarge).toContain("20 MB");
    expect(vi.questions.assets.errors.attachmentTooLarge).toContain("20 MB");
  });
});
