import { type OverviewRow, sectionCompetency } from "./create-exam-overview";

/**
 * Tiểu mục V-ACT phân biệt bằng năng lực: hai mục cùng môn SCIENTIFIC_THINKING (H24) lấy tên theo mã năng lực
 * (R-109: không hiện mã thô). Năng lực không thuộc bảng này rơi về tên môn của mục.
 */
const VACT_COMPETENCY_PART: Record<string, string> = { "VACT-TD-LOGIC": "LOGIC", "VACT-TD-SL": "REASONING" };
/** Phần nhiều tiểu mục của V-ACT: mã năng lực các mục cùng tiền tố thì phần lấy tên thông điệp (Sử dụng ngôn ngữ, Tư duy khoa học). */
const VACT_COMPETENCY_GROUP: Array<[prefix: string, part: string]> = [
  ["VACT-NN-", "LANGUAGE"],
  ["VACT-TD-", "THINKING"],
];

/** Mục của phần (mẫu hoặc cấu trúc đã lưu): đủ để suy ra năng lực và môn. */
type LabelSection = Parameters<typeof sectionCompetency>[0] & { subjectCode: string };

export interface OverviewLabelContext {
  vact: boolean;
  /** Tên theo thông điệp (`partName`) của mã môn hoặc mã tên phần (LOGIC, REASONING, LANGUAGE, THINKING). */
  partName: (code: string) => string;
  /** Tên môn trong danh mục; `undefined` khi danh mục không có mã đó. */
  subjectOf: (code: string) => string | undefined;
  /** Các mục của phần ứng với dòng phần: bước 1 lấy từ mẫu, Thông tin chung lấy từ cấu trúc đã lưu. */
  partSections: (row: OverviewRow) => readonly LabelSection[];
  /** H-SCA Tiếng Anh: tên Nghe, Đọc của dòng phần; bỏ trống khi không tách phần theo audio. */
  audioLabel?: (row: OverviewRow) => string | undefined;
}

/**
 * Tên dòng theo ngôn ngữ đang chọn, dựng từ mã năng lực và mã môn (không lấy tên tiếng Việt lưu trong mẫu): mục cùng môn
 * tách tên theo năng lực, mục thường theo tên môn. Dòng phần một mục lấy tên của mục đó; phần nhiều mục lấy tên nhóm năng
 * lực. Không có khóa thì để `undefined`: giữ tên đang có của dòng. Dùng chung bảng tổng quan ở bước 1 và Thông tin chung.
 */
export function overviewRowLabel(context: OverviewLabelContext, row: OverviewRow): string | undefined {
  if (row.kind === "section") {
    return context.partName((row.competencyCode && VACT_COMPETENCY_PART[row.competencyCode]) || row.subjectCode);
  }
  if (row.kind !== "part") return undefined;
  const audio = context.audioLabel?.(row);
  if (audio) return audio;
  if (!context.vact) return undefined;
  const sections = context.partSections(row);
  if (sections.length === 1) {
    const only = sections[0]!;
    const competency = sectionCompetency(only);
    return (competency && VACT_COMPETENCY_PART[competency] && context.partName(VACT_COMPETENCY_PART[competency])) || context.subjectOf(only.subjectCode);
  }
  const groups = new Set(
    sections.map((section) => {
      const competency = sectionCompetency(section) ?? "";
      return VACT_COMPETENCY_GROUP.find(([prefix]) => competency.startsWith(prefix))?.[1];
    }),
  );
  const [group] = groups;
  return groups.size === 1 && group ? context.partName(group) : undefined;
}
