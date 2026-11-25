export interface AndroidAssetLinkStatement {
  readonly relation: readonly ["delegate_permission/common.handle_all_urls"];
  readonly target: {
    readonly namespace: "android_app";
    readonly package_name: string;
    readonly sha256_cert_fingerprints: readonly string[];
  };
}

export interface AppleAppSiteAssociation {
  readonly applinks: {
    readonly details: readonly {
      readonly appIDs: readonly [string];
      readonly components: readonly [
        {
          readonly "/": "/reset-password";
        },
      ];
    }[];
  };
}

function parseAndroidCertFingerprints(raw: string | undefined): string[] {
  if (!raw) return [];

  const seen = new Set<string>();
  const fingerprints: string[] = [];

  for (const segment of raw.split(",")) {
    const normalized = segment.trim().toUpperCase();
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    fingerprints.push(normalized);
  }

  return fingerprints;
}

export function buildAndroidAssetLinks(
  env: NodeJS.ProcessEnv = process.env,
): AndroidAssetLinkStatement[] {
  const packageName = env.THI360_ANDROID_PACKAGE?.trim() ?? "";
  const fingerprints = parseAndroidCertFingerprints(env.THI360_ANDROID_CERT_SHA256);

  if (!packageName || fingerprints.length === 0) {
    return [];
  }

  return [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
  ];
}

export function buildAppleAppSiteAssociation(
  env: NodeJS.ProcessEnv = process.env,
): AppleAppSiteAssociation {
  const appId = env.THI360_IOS_APP_ID?.trim() ?? "";

  if (!appId) {
    return { applinks: { details: [] } };
  }

  return {
    applinks: {
      details: [
        {
          appIDs: [appId],
          components: [{ "/": "/reset-password" }],
        },
      ],
    },
  };
}
