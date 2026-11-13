export const CTX_COOKIE_NAME = "thi360_ctx";
const DEV_CTX_SECRET = "ctx-dev-only-change-me-32-bytes!!";
const MIN_SECRET_BYTES = 32;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const INTEGER_PATTERN = /^\d+$/;
const BASE64URL_PATTERN = /^[A-Za-z0-9_-]+$/;

export type CtxRealm = "APP" | "MASTER";
export type CtxAccess = "MANAGE" | "EXAM";

export interface CtxClaims {
  userId: string;
  realm: CtxRealm;
  access: CtxAccess;
  version: number;
  expiresAt: number;
}

export interface VerifyCtxOptions {
  secret?: string;
  nowMs?: number;
  nodeEnv?: string;
}

const encoder = new TextEncoder();
let cachedKeyEntry: { secret: string; key: Promise<CryptoKey> } | null = null;

function importHmacKey(secret: string): Promise<CryptoKey> {
  if (cachedKeyEntry && cachedKeyEntry.secret === secret) {
    return cachedKeyEntry.key;
  }
  const key = globalThis.crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"],
  );
  cachedKeyEntry = { secret, key };
  return key;
}

function decodeBase64Url(input: string): Uint8Array<ArrayBuffer> | null {
  if (!input || !BASE64URL_PATTERN.test(input)) return null;
  const padded = input.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (input.length % 4)) % 4);
  try {
    const binary = atob(padded);
    const bytes = new Uint8Array(new ArrayBuffer(binary.length));
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  } catch {
    return null;
  }
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function resolveCtxSecret(
  explicitSecret?: string,
  nodeEnv: string | undefined = process.env.NODE_ENV,
): string | null {
  const raw = explicitSecret !== undefined ? explicitSecret : process.env.THI360_CTX_SECRET;
  const trimmed = raw?.trim() ?? "";
  if (trimmed.length > 0) {
    return encoder.encode(trimmed).length >= MIN_SECRET_BYTES ? trimmed : null;
  }
  return nodeEnv === "production" ? null : DEV_CTX_SECRET;
}

export async function signCtxCookie(claims: CtxClaims, secret: string): Promise<string> {
  if (encoder.encode(secret).length < MIN_SECRET_BYTES) {
    throw new Error("THI360_CTX_SECRET must be at least 32 UTF-8 bytes");
  }
  const payload = `${claims.userId}.${claims.realm}.${claims.access}.${claims.version}.${claims.expiresAt}`;
  const key = await importHmacKey(secret);
  const sigBuffer = await globalThis.crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  return `${payload}.${encodeBase64Url(new Uint8Array(sigBuffer))}`;
}

export async function verifyCtxCookie(
  token: string | undefined | null,
  options: VerifyCtxOptions = {},
): Promise<CtxClaims | null> {
  if (!token || !token.trim()) return null;

  const secret = resolveCtxSecret(options.secret, options.nodeEnv);
  if (!secret) return null;

  const lastDot = token.lastIndexOf(".");
  if (lastDot <= 0 || lastDot === token.length - 1) return null;

  const payload = token.slice(0, lastDot);
  const signaturePart = token.slice(lastDot + 1);
  const sigBytes = decodeBase64Url(signaturePart);
  if (!sigBytes || sigBytes.length !== 32) return null;

  let verified = false;
  try {
    const key = await importHmacKey(secret);
    verified = await globalThis.crypto.subtle.verify("HMAC", key, sigBytes, encoder.encode(payload));
  } catch {
    return null;
  }
  if (!verified) return null;

  const parts = payload.split(".");
  if (parts.length !== 5) return null;

  const [userId, realm, access, versionRaw, expiresAtRaw] = parts;
  if (!userId || !UUID_PATTERN.test(userId)) return null;
  if (realm !== "APP" && realm !== "MASTER") return null;
  if (access !== "MANAGE" && access !== "EXAM") return null;
  if (!versionRaw || !INTEGER_PATTERN.test(versionRaw)) return null;
  if (!expiresAtRaw || !INTEGER_PATTERN.test(expiresAtRaw)) return null;

  const version = Number(versionRaw);
  const expiresAt = Number(expiresAtRaw);
  if (!Number.isSafeInteger(version) || !Number.isSafeInteger(expiresAt)) return null;

  const nowMs = options.nowMs ?? Date.now();
  if (nowMs > expiresAt * 1000) return null;

  return { userId, realm, access, version, expiresAt };
}
