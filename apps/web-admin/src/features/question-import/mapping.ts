import {
  type ColumnMapping,
  IGNORE_COLUMN,
  type ImportColumn,
  type ImportField,
  IMPORT_FIELDS,
  REQUIRED_IMPORT_FIELDS,
  UNMAPPED_COLUMN,
} from "./types";

/** Chữ "đ" không tách dấu khi chuẩn hóa NFD nên thay riêng (viết bằng mã ký tự để mã nguồn không có chữ có dấu). */
const D_WITH_STROKE = new RegExp(`[${String.fromCharCode(0x110, 0x111)}]`, "g");

/**
 * So khớp tên cột không phân biệt hoa thường, dấu tiếng Việt và ký tự phân cách: "Mã nguồn", "ma_nguon", "MA NGUON"
 * cùng thành "ma nguon".
 */
export function normalizeHeader(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(D_WITH_STROKE, "d")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

/** Ánh xạ gợi ý: tên cột khớp tên gọi của một trường; mỗi trường chỉ gợi ý cho cột khớp đầu tiên. */
export function suggestMapping(
  columns: readonly ImportColumn[],
  fieldAliases: Record<ImportField, readonly string[]>,
  ignoreHeaders: readonly string[] = [],
): ColumnMapping {
  const aliasToField = new Map<string, ImportField>();
  for (const field of IMPORT_FIELDS) {
    for (const alias of [field, ...(fieldAliases[field] ?? [])]) {
      const key = normalizeHeader(alias);
      if (key && !aliasToField.has(key)) aliasToField.set(key, field);
    }
  }
  const ignored = new Set(ignoreHeaders.map(normalizeHeader));
  const taken = new Set<ImportField>();
  const mapping: ColumnMapping = {};
  for (const column of columns) {
    const key = normalizeHeader(column.header);
    const field = aliasToField.get(key);
    if (field && !taken.has(field)) {
      taken.add(field);
      mapping[column.key] = field;
    } else if (ignored.has(key)) {
      mapping[column.key] = IGNORE_COLUMN;
    } else {
      mapping[column.key] = UNMAPPED_COLUMN;
    }
  }
  return mapping;
}

export interface MappingCheck {
  /** Trường bắt buộc chưa có cột nào ánh xạ vào. */
  missingRequired: ImportField[];
  /** Trường được hai cột trở lên ánh xạ vào (lỗi). */
  duplicateFields: ImportField[];
  /** Cột đang ánh xạ vào một trường bị trùng. */
  duplicateColumnKeys: Set<string>;
  mappedCount: number;
  unmappedCount: number;
  ignoredCount: number;
  /** Trường đã có cột, kèm khóa cột đầu tiên. */
  columnByField: Map<ImportField, string>;
  valid: boolean;
}

export function checkMapping(columns: readonly ImportColumn[], mapping: ColumnMapping): MappingCheck {
  const columnsByField = new Map<ImportField, string[]>();
  let unmappedCount = 0;
  let ignoredCount = 0;
  for (const column of columns) {
    const target = mapping[column.key] ?? UNMAPPED_COLUMN;
    if (target === UNMAPPED_COLUMN) unmappedCount += 1;
    else if (target === IGNORE_COLUMN) ignoredCount += 1;
    else columnsByField.set(target, [...(columnsByField.get(target) ?? []), column.key]);
  }
  const duplicateFields: ImportField[] = [];
  const duplicateColumnKeys = new Set<string>();
  const columnByField = new Map<ImportField, string>();
  let mappedCount = 0;
  for (const field of IMPORT_FIELDS) {
    const keys = columnsByField.get(field);
    if (!keys) continue;
    mappedCount += keys.length;
    columnByField.set(field, keys[0]!);
    if (keys.length > 1) {
      duplicateFields.push(field);
      keys.forEach((key) => duplicateColumnKeys.add(key));
    }
  }
  const missingRequired = REQUIRED_IMPORT_FIELDS.filter((field) => !columnsByField.has(field));
  return {
    missingRequired,
    duplicateFields,
    duplicateColumnKeys,
    mappedCount,
    unmappedCount,
    ignoredCount,
    columnByField,
    valid: missingRequired.length === 0 && duplicateFields.length === 0,
  };
}

export function isRequiredField(field: ImportField): boolean {
  return (REQUIRED_IMPORT_FIELDS as readonly ImportField[]).includes(field);
}
