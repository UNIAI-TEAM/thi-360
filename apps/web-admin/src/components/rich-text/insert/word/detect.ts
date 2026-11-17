const WORD_NAMESPACE = /urn:schemas-microsoft-com:office:word/i;
const WORD_GENERATOR = /<meta\b(?=[^>]*\bname\s*=\s*["']?generator["']?)(?=[^>]*\bcontent\s*=\s*["']?\s*Microsoft\s+Word)[^>]*>/i;

/** HTML trên clipboard có phải của Word không: namespace `urn:schemas-microsoft-com:office:word` hoặc meta Generator. */
export function isWordHtml(html: string | null | undefined): boolean {
  if (!html) return false;
  return WORD_NAMESPACE.test(html) || WORD_GENERATOR.test(html);
}
