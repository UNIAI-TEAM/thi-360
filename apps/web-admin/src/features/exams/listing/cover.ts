import { ExamsApiError } from "../review-api";
import { COVER_IMAGE_MAX_BYTES } from "../create-exam-schema";

export const COVER_ACCEPT = "image/jpeg,image/png,image/webp";
const COVER_MIME_TYPES: readonly string[] = ["image/jpeg", "image/png", "image/webp"];
const COVER_EXTENSIONS = /\.(jpe?g|png|webp)$/i;

/** Khóa chữ dưới `exams.listing.cover.errors` (vi và en cùng khóa). */
export type CoverErrorKey =
  | "type"
  | "tooLarge"
  | "empty"
  | "dimensionsSmall"
  | "dimensionsLarge"
  | "corrupt"
  | "forbidden"
  | "generic";

export function coverFileProblem(file: Pick<File, "name" | "type" | "size">): CoverErrorKey | null {
  const typeOk = file.type ? COVER_MIME_TYPES.includes(file.type) : COVER_EXTENSIONS.test(file.name);
  if (!typeOk) return "type";
  if (file.size > COVER_IMAGE_MAX_BYTES) return "tooLarge";
  if (file.size === 0) return "empty";
  return null;
}

const SERVER_CODES: Readonly<Record<string, CoverErrorKey>> = {
  EMPTY: "empty",
  TOO_LARGE: "tooLarge",
  UNSUPPORTED_TYPE: "type",
  UNREADABLE: "type",
  DIMENSIONS_TOO_LARGE: "dimensionsLarge",
  DIMENSIONS_TOO_SMALL: "dimensionsSmall",
  CORRUPT: "corrupt",
};

export function coverErrorKeyOf(error: unknown): CoverErrorKey {
  if (error instanceof ExamsApiError) {
    if (error.status === 413) return "tooLarge";
    if (error.status === 403) return "forbidden";
    const reason = error.fieldErrors.find((item) => item.field === "file")?.code;
    if (reason && SERVER_CODES[reason]) return SERVER_CODES[reason];
  }
  return "generic";
}
