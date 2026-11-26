export type LegalDocumentId = "terms" | "privacy";
export type LegalDocumentLocale = "vi" | "en";
export type LegalDocumentStatus = "draft" | "published";

export type LegalHeadingBlock = {
  readonly type: "heading";
  readonly level: 2 | 3;
  readonly text: string;
};

export type LegalParagraphBlock = {
  readonly type: "paragraph";
  readonly text: string;
};

export type LegalListBlock = {
  readonly type: "list";
  readonly ordered: boolean;
  readonly items: readonly string[];
};

export type LegalTableBlock = {
  readonly type: "table";
  readonly headers: readonly string[];
  readonly rows: readonly (readonly string[])[];
};

export type LegalBlock = LegalHeadingBlock | LegalParagraphBlock | LegalListBlock | LegalTableBlock;

export interface LegalDocument {
  readonly id: LegalDocumentId;
  readonly locale: LegalDocumentLocale;
  readonly version: string;
  readonly status: LegalDocumentStatus;
  readonly contentHash: string;
  readonly effectiveDate: string | null;
  readonly title: string;
  readonly note?: string;
  readonly blocks: readonly LegalBlock[];
}

export const ENGLISH_LEGAL_TRANSLATION_NOTE =
  "This English version is a translation. If it differs from the Vietnamese version, the Vietnamese version prevails.";

export function formatLegalEffectiveDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match) return isoDate;
  return `${match[3]}/${match[2]}/${match[1]}`;
}
