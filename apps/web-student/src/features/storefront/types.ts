import type { components } from "@thi360/api-client";

export type ProductCard = components["schemas"]["ProductCard"];
export type ProductDetail = components["schemas"]["ProductDetail"];
export type ProductPage = components["schemas"]["PageResponseProductCard"];
export type StorefrontFilters = components["schemas"]["StorefrontFilters"];
export type IntRange = components["schemas"]["IntRange"];
export type DecimalRange = components["schemas"]["DecimalRange"];
export type LocalizedName = components["schemas"]["LocalizedName"];
export type PromotionBadge = components["schemas"]["PromotionBadge"];
export type DetailPart = components["schemas"]["DetailPart"];
export type DetailChoiceGroup = components["schemas"]["DetailChoiceGroup"];
export type DetailChoiceOption = components["schemas"]["DetailChoiceOption"];
export type AnswerTypeGroup = components["schemas"]["AnswerTypeGroup"];
export type OmittedEssayGroup = components["schemas"]["OmittedEssayGroup"];

/** Kết quả tải dữ liệu công khai ở máy chủ: lỗi tạm thời không làm sập trang, chỉ thành trạng thái lỗi. */
export type Loaded<T> = { status: "ok"; data: T } | { status: "error" };

export type ProductLookup =
  | { status: "ok"; product: ProductDetail }
  | { status: "gone"; product: { code: string; name: string } }
  | { status: "notFound" };
