const ADMIN_ASSET_URL_RE =
  /(?:https?:\/\/[^/"'\s<>]+)?\/api\/v1\/admin\/question-assets\/([0-9a-fA-F-]+)([?#][^\s"'<>]*)?/gi;
const ADMIN_ASSET_RESIDUAL_RE =
  /(?:https?:\/\/[^/"'\s<>]+)?\/api\/v1\/admin\/question-assets[^\s"'<>]*/gi;
const ASSET_ID_VALID_RE = /^[0-9a-zA-Z_-]+$/;

export function toAttemptAssetUrl(attemptId: string, assetId: string): string {
  return `/api/v1/me/attempts/${encodeURIComponent(attemptId)}/assets/${encodeURIComponent(assetId)}`;
}

function rewriteAdminUrlValue(rawUrl: string, attemptId: string): string {
  const trimmed = rawUrl.trim();
  if (!trimmed) return "";

  let replaced = false;
  ADMIN_ASSET_URL_RE.lastIndex = 0;
  const rewritten = trimmed.replace(
    ADMIN_ASSET_URL_RE,
    (_full, assetId: string, suffix: string | undefined) => {
      replaced = true;
      return `${toAttemptAssetUrl(attemptId, assetId)}${suffix ?? ""}`;
    },
  );

  if (replaced) {
    ADMIN_ASSET_RESIDUAL_RE.lastIndex = 0;
    return rewritten.replace(ADMIN_ASSET_RESIDUAL_RE, "");
  }

  ADMIN_ASSET_RESIDUAL_RE.lastIndex = 0;
  if (ADMIN_ASSET_RESIDUAL_RE.test(trimmed)) {
    return "";
  }
  return trimmed;
}

function rewriteHtmlViaRegex(html: string, attemptId: string): string {
  let out = html.replace(
    /<img\b([^>]*)>/gi,
    (fullTag, attrs: string) => {
      const assetIdMatch = attrs.match(/\bdata-asset-id\s*=\s*(?:"([^"]*)"|'([^']*)')/i);
      const assetId = (assetIdMatch?.[1] ?? assetIdMatch?.[2] ?? "").trim();
      const srcMatch = attrs.match(/\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')/i);
      const currentSrc = (srcMatch?.[1] ?? srcMatch?.[2] ?? "").trim();

      if (!currentSrc && assetId && ASSET_ID_VALID_RE.test(assetId)) {
        const targetUrl = toAttemptAssetUrl(attemptId, assetId);
        if (srcMatch) {
          return fullTag.replace(
            /\bsrc\s*=\s*(?:"[^"]*"|'[^']*')/i,
            `src="${targetUrl}"`,
          );
        }
        return `<img src="${targetUrl}"${attrs}>`;
      }
      return fullTag;
    },
  );

  ADMIN_ASSET_URL_RE.lastIndex = 0;
  out = out.replace(
    ADMIN_ASSET_URL_RE,
    (_full, assetId: string, suffix: string | undefined) =>
      `${toAttemptAssetUrl(attemptId, assetId)}${suffix ?? ""}`,
  );
  ADMIN_ASSET_RESIDUAL_RE.lastIndex = 0;
  out = out.replace(ADMIN_ASSET_RESIDUAL_RE, "");
  return out;
}

export function rewriteQuestionHtmlAssets(
  html: string | null | undefined,
  attemptId: string,
): string {
  if (!html) return "";
  if (!attemptId) return html;

  if (typeof DOMParser === "undefined") {
    return rewriteHtmlViaRegex(html, attemptId);
  }

  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, "text/html");
  for (const img of [...doc.body.querySelectorAll("img")]) {
    const rawSrc = img.getAttribute("src") ?? "";
    const trimmedSrc = rawSrc.trim();
    if (trimmedSrc) {
      const rewrittenSrc = rewriteAdminUrlValue(trimmedSrc, attemptId);
      if (rewrittenSrc !== rawSrc) {
        img.setAttribute("src", rewrittenSrc);
      }
    } else {
      const dataAssetId = (img.getAttribute("data-asset-id") ?? "").trim();
      if (dataAssetId && ASSET_ID_VALID_RE.test(dataAssetId)) {
        img.setAttribute("src", toAttemptAssetUrl(attemptId, dataAssetId));
      }
    }
  }

  for (const el of [...doc.body.querySelectorAll("*")]) {
    for (const attr of [...el.attributes]) {
      const val = attr.value;
      if (/\/api\/v1\/admin\/question-assets/i.test(val)) {
        el.setAttribute(attr.name, rewriteAdminUrlValue(val, attemptId));
      }
    }
  }

  // Final defense-in-depth pass in case a malformed admin path appeared outside standard attributes.
  const serialized = doc.body.innerHTML;
  ADMIN_ASSET_URL_RE.lastIndex = 0;
  const afterIds = serialized.replace(
    ADMIN_ASSET_URL_RE,
    (_full, assetId: string, suffix: string | undefined) =>
      `${toAttemptAssetUrl(attemptId, assetId)}${suffix ?? ""}`,
  );
  ADMIN_ASSET_RESIDUAL_RE.lastIndex = 0;
  return afterIds.replace(ADMIN_ASSET_RESIDUAL_RE, "");
}

export function sanitizeAttemptAttachmentUrl(
  url: string | null | undefined,
  attemptId: string,
  assetId?: string | null,
): string {
  const cleanAssetId = assetId?.trim() ?? "";
  if (cleanAssetId && ASSET_ID_VALID_RE.test(cleanAssetId)) {
    return toAttemptAssetUrl(attemptId, cleanAssetId);
  }
  const raw = url?.trim() ?? "";
  if (!raw) return "";

  ADMIN_ASSET_URL_RE.lastIndex = 0;
  const adminMatch = ADMIN_ASSET_URL_RE.exec(raw);
  if (adminMatch?.[1]) {
    return `${toAttemptAssetUrl(attemptId, adminMatch[1])}${adminMatch[2] ?? ""}`;
  }

  const expectedPrefix = `/api/v1/me/attempts/${encodeURIComponent(attemptId)}/assets/`;
  const rawExpectedPrefix = `/api/v1/me/attempts/${attemptId}/assets/`;
  if (raw.startsWith(expectedPrefix) || raw.startsWith(rawExpectedPrefix)) {
    return raw;
  }

  return "";
}

export function containsAdminAssetPath(value: string | null | undefined): boolean {
  if (!value) return false;
  return /\/api\/v1\/admin\/question-assets/i.test(value);
}
