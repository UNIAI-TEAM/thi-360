/**
 * Shared public contact information used consistently across the student footer
 * and legal documents (Privacy Policy and General Transaction Terms).
 */
export const SITE_CONTACT = {
  email: "hsaeducation.jsc@gmail.com",
  hotline: "0988.371.194",
  hotlineTel: "0988371194",
  supportPhone: "0961.167.606",
  supportPhoneTel: "0961167606",
} as const;

/** Optional verified online.gov.vn registration URL; badge renders only when configured. */
export function moitRegistrationUrl(): string | null {
  const configured = process.env.NEXT_PUBLIC_THI360_MOIT_URL?.trim();
  return configured ? configured : null;
}
