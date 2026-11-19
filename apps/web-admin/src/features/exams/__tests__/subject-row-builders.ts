import { wireBlock, wirePart, wireSection, type Wire } from "./subject-form-harness";

/**
 * Dữ liệu wire viết tay cho các test Danh sách dòng (CR-3): đúng hình dạng mẫu chuẩn của từng kỳ (HSA, QDA, BCA, THPTQG), dựng tại chỗ
 * không dùng chung DB. Nhãn chùm và tên dòng là nội dung đề (không song ngữ).
 */

/** Dòng "Đọc hiểu" 5 chùm × 5 có nhãn từng chùm, công tắc đảo chùm do mẫu cho (HSA, QDA nhóm 5). */
const passageRow = (id: string, tags: readonly string[]): Wire =>
  wireBlock(id, {
    name: "Doc hieu",
    questionType: "QUESTION_SET",
    setCount: 5,
    setSize: 5,
    questionCount: 25,
    childAnswerType: "SINGLE_CHOICE",
    shuffleEditable: true,
    shuffleUnits: true,
    templateGroupCode: `${id}-code`,
    slots: tags.map((tag, offset) => ({ index: offset + 1, tagCodes: [tag] })),
  });

const singleRow = (id: string, name: string, count: number, tag: string): Wire => wireBlock(id, { name, questionCount: count, tagCodes: [tag], templateGroupCode: `${id}-code` });

/** HSA Văn: 4 dòng câu đơn rồi nhóm 5 (5 chùm có loại văn bản, công tắc đảo). 50 câu. */
export function hsaLiteratureSection(id = "sec-van"): Wire {
  return wireSection(id, "VAN", [
    singleRow("van-1", "Tu khac nhom nghia", 5, "VAN_TU_KHAC_NHOM"),
    singleRow("van-2", "Tu sai", 5, "VAN_TU_SAI"),
    singleRow("van-3", "Cap tu dien", 5, "VAN_CAP_TU_DIEN"),
    singleRow("van-4", "Mot ngu lieu mot cau", 10, "VAN_NGU_LIEU_MOT_CAU"),
    passageRow("van-5", ["VAN_NGHI_LUAN_XA_HOI", "VAN_THONG_TIN", "VAN_KHOA_HOC", "VAN_THO", "VAN_VAN_XUOI"]),
  ]);
}

/** QDA Văn: 5 dòng theo thứ tự, dòng 5 là một nhóm 5 chùm × 5 với nhãn từng chùm. 50 câu. */
export function qdaLiteratureSection(id = "sec-van"): Wire {
  return wireSection(id, "VAN", [
    singleRow("van-1", "Tu khac nhom nghia", 5, "VAN_TU_KHAC_NHOM"),
    singleRow("van-2", "Cap tu dien", 5, "VAN_CAP_TU_DIEN"),
    singleRow("van-3", "Tu sai", 5, "VAN_TU_SAI"),
    singleRow("van-4", "Mot ngu lieu mot cau", 10, "VAN_NGU_LIEU_MOT_CAU"),
    passageRow("van-5", ["VAN_THONG_TIN_NGHI_LUAN", "VAN_NGHI_LUAN", "VAN_THO", "VAN_THIEU_NHI", "VAN_TRUYEN"]),
  ]);
}

/** BCA Văn: một dòng tự luận, sửa được MAPID. */
export function bcaLiteratureSection(id = "sec-van"): Wire {
  return wireSection(id, "VAN", [wireBlock("essay-1", { name: "Nghi luan xa hoi", questionType: "ESSAY", questionCount: 1, requiredPointsPerQuestion: 25, tagCodes: ["VAN_NGHI_LUAN_XA_HOI"], templateGroupCode: "BCA_VAN_ESSAY" })]);
}

/** THPTQG Văn: chỉ một dòng Tự luận đã lược (K7). */
export function thptqgLiteratureSection(id = "sec-van"): Wire {
  return wireSection(id, "VAN", [wireBlock("essay-1", { name: "Tu luan", questionType: "ESSAY", questionCount: 1, omitted: true, templateGroupCode: "THPT_VAN_ESSAY" })], { questionCount: 0 });
}

/** THPTQG Lý: Phần I 18 câu, Phần II 4 câu Đ/S (16 ý, thang 4 điểm), Phần III 6 câu điền; mỗi phần một mục. */
export function thptqgPhysicsParts(): Wire[] {
  const part = (id: string, order: number, section: Wire) => wirePart([section], { id, order, name: `Phan ${order}` });
  return [
    part("part-1", 1, wireSection("sec-p1", "LY", [wireBlock("p1-single", { name: "Trac nghiem", questionCount: 18, templateGroupCode: "THPT_LY_P1" })])),
    part(
      "part-2",
      2,
      wireSection("sec-p2", "LY", [
        wireBlock("p2-tf", {
          name: "Dung sai",
          questionType: "TRUE_FALSE",
          questionCount: 4,
          requiredPointsPerQuestion: 1,
          itemShape: { statements: 4 },
          expectedScoring: { trueFalseTable: [0.1, 0.25, 0.5, 1] },
          answerTypeLocked: true,
          templateGroupCode: "THPT_LY_P2",
        }),
      ]),
    ),
    part("part-3", 3, wireSection("sec-p3", "LY", [wireBlock("p3-fill", { name: "Tra loi ngan", questionType: "SHORT_ANSWER", questionCount: 6, templateGroupCode: "THPT_LY_P3" })])),
  ];
}

/** Bài một phần với đúng một môn. */
export const oneSection = (section: Wire): Wire[] => [wirePart([section])];
