import { describe, expect, it } from "vitest";
import {
  containsAdminAssetPath,
  rewriteQuestionHtmlAssets,
  sanitizeAttemptAttachmentUrl,
  toAttemptAssetUrl,
} from "./attempt-assets";
import { buildQuestionUnits, toKitQuestion } from "./mappers";

describe("attempt-assets & mappers asset guard (AC-31)", () => {
  const attemptId = "att-123";
  const assetId = "11111111-2222-3333-4444-555555555555";
  const secondAssetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

  it("chuyển mọi đường dẫn ảnh admin (tương đối, tuyệt đối, data-asset-id) sang đường dẫn của lượt", () => {
    const html = [
      `<p>Xem hình <img src="/api/v1/admin/question-assets/${assetId}" alt="Hinh 1"></p>`,
      `<p>Ảnh khác <img src="https://admin.thi360.local/api/v1/admin/question-assets/${secondAssetId}?size=lg" alt="Hinh 2"></p>`,
      `<p>Thiếu src <img data-asset-id="${assetId}" alt="Hinh 3"></p>`,
    ].join("");

    const sanitized = rewriteQuestionHtmlAssets(html, attemptId);

    expect(sanitized).toContain(toAttemptAssetUrl(attemptId, assetId));
    expect(sanitized).toContain(`${toAttemptAssetUrl(attemptId, secondAssetId)}?size=lg`);
    expect(containsAdminAssetPath(sanitized)).toBe(false);
    expect(sanitized).not.toContain("/api/v1/admin/question-assets");
  });

  it("loại bỏ đường dẫn admin sai định dạng để không bao giờ lọt URL gốc", () => {
    const malicious =
      '<p><img src="/api/v1/admin/question-assets/not-valid/../../secret" data-poster="http://localhost:8080/api/v1/admin/question-assets/"></p>';
    const sanitized = rewriteQuestionHtmlAssets(malicious, attemptId);

    expect(containsAdminAssetPath(sanitized)).toBe(false);
    expect(sanitized).not.toContain("/api/v1/admin/question-assets");
  });

  it("chuẩn hóa URL tệp đính kèm và toàn bộ các trường HTML khi ánh xạ AttemptItemView", () => {
    expect(
      sanitizeAttemptAttachmentUrl(
        `https://cdn.example/api/v1/admin/question-assets/${assetId}`,
        attemptId,
        assetId,
      ),
    ).toBe(`/api/v1/me/attempts/${attemptId}/assets/${assetId}`);

    expect(
      sanitizeAttemptAttachmentUrl(
        `https://cdn.example/api/v1/admin/question-assets/${assetId}`,
        attemptId,
      ),
    ).toBe(`/api/v1/me/attempts/${attemptId}/assets/${assetId}`);

    expect(sanitizeAttemptAttachmentUrl("/api/v1/admin/question-assets/", attemptId)).toBe("");

    const kitQ = toKitQuestion(
      {
        itemId: "item-1",
        displayNo: 1,
        questionType: "SINGLE_CHOICE",
        content: `<p>Stem <img src="/api/v1/admin/question-assets/${assetId}"></p>`,
        questionBody: {
          type: "SINGLE_CHOICE",
          options: [
            {
              id: "opt-1",
              content: `<p>Opt <img data-asset-id="${secondAssetId}"></p>`,
            },
          ],
        },
      },
      attemptId,
    );

    expect(containsAdminAssetPath(kitQ.stem)).toBe(false);
    expect(kitQ.stem).toContain(`/api/v1/me/attempts/${attemptId}/assets/${assetId}`);
    if (kitQ.body.type === "SINGLE_CHOICE") {
      expect(containsAdminAssetPath(kitQ.body.options[0]?.content)).toBe(false);
      expect(kitQ.body.options[0]?.content).toContain(
        `/api/v1/me/attempts/${attemptId}/assets/${secondAssetId}`,
      );
    }

    const units = buildQuestionUnits(
      [
        {
          itemId: "set-1",
          questionType: "QUESTION_SET",
          content: "",
          questionBody: {
            type: "QUESTION_SET",
            passage: `<p>Passage <img src="https://api.thi360.vn/api/v1/admin/question-assets/${assetId}"></p>`,
          },
          attachments: [
            {
              assetId,
              fileName: "audio.mp3",
              mime: "audio/mpeg",
              url: `/api/v1/admin/question-assets/${assetId}`,
            },
          ],
        },
        {
          itemId: "child-1",
          parentItemId: "set-1",
          displayNo: 10,
          questionType: "TRUE_FALSE",
          content: "Chọn đúng sai",
          questionBody: {
            type: "TRUE_FALSE",
            statements: [
              { id: "s1", content: `<img src="/api/v1/admin/question-assets/${secondAssetId}">` },
            ],
          },
        },
      ],
      attemptId,
    );

    expect(units).toHaveLength(1);
    const setUnit = units[0]!;
    if (setUnit.kitQuestion.body.type === "QUESTION_SET") {
      expect(containsAdminAssetPath(setUnit.kitQuestion.body.passage)).toBe(false);
      expect(setUnit.kitQuestion.body.passage).toContain(
        `/api/v1/me/attempts/${attemptId}/assets/${assetId}`,
      );
    }
    expect(setUnit.attachments[0]?.url).toBe(`/api/v1/me/attempts/${attemptId}/assets/${assetId}`);
    const childKit = setUnit.childKitQuestions[0]!;
    if (childKit.body.type === "TRUE_FALSE") {
      expect(containsAdminAssetPath(childKit.body.statements[0]?.content)).toBe(false);
      expect(childKit.body.statements[0]?.content).toContain(
        `/api/v1/me/attempts/${attemptId}/assets/${secondAssetId}`,
      );
    }
  });
});
