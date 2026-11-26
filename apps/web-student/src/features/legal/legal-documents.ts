import privacyEn from "../../../../../contracts/mobile/legal/privacy.en.json";
import privacyVi from "../../../../../contracts/mobile/legal/privacy.vi.json";
import termsEn from "../../../../../contracts/mobile/legal/terms.en.json";
import termsVi from "../../../../../contracts/mobile/legal/terms.vi.json";
import type { LegalDocument, LegalDocumentId, LegalDocumentLocale } from "./legal-document";

const LEGAL_DOCUMENTS: Record<LegalDocumentId, Record<LegalDocumentLocale, LegalDocument>> = {
  terms: {
    vi: termsVi as LegalDocument,
    en: termsEn as LegalDocument,
  },
  privacy: {
    vi: privacyVi as LegalDocument,
    en: privacyEn as LegalDocument,
  },
};

export const TERMS_DOCUMENT_VERSION: string = LEGAL_DOCUMENTS.terms.vi.version;

export function getLegalDocument(id: LegalDocumentId, locale: string): LegalDocument {
  const normalized: LegalDocumentLocale = locale.toLowerCase().startsWith("en") ? "en" : "vi";
  return LEGAL_DOCUMENTS[id][normalized];
}
