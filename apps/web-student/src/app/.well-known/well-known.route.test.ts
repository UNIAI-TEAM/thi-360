// @vitest-environment node
import { describe, expect, it, vi } from "vitest";
import { GET as getAppleAppSiteAssociation } from "./apple-app-site-association/route";
import { GET as getAssetLinks } from "./assetlinks.json/route";

describe("GET /.well-known/assetlinks.json", () => {
  it("trả đúng câu khai báo Android App Links với một vân tay SHA-256", async () => {
    vi.stubEnv("THI360_ANDROID_PACKAGE", "vn.unicom.thi360");
    vi.stubEnv("THI360_ANDROID_CERT_SHA256", "AA:BB:CC:DD");

    const response = getAssetLinks();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toBe("no-cache");
    expect(await response.json()).toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "vn.unicom.thi360",
          sha256_cert_fingerprints: ["AA:BB:CC:DD"],
        },
      },
    ]);
  });

  it("tách nhiều vân tay, chuẩn hóa chữ hoa, bỏ mục rỗng và lọc trùng giữ thứ tự", async () => {
    vi.stubEnv("THI360_ANDROID_PACKAGE", "  vn.unicom.thi360  ");
    vi.stubEnv("THI360_ANDROID_CERT_SHA256", " aa:bb:11 , , CC:DD:22, aa:bb:11, ee:ff:33 , ");

    const response = getAssetLinks();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(await response.json()).toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "vn.unicom.thi360",
          sha256_cert_fingerprints: ["AA:BB:11", "CC:DD:22", "EE:FF:33"],
        },
      },
    ]);
  });

  it.each([
    ["thiếu package", "", "AA:BB:CC:DD"],
    ["package chỉ có khoảng trắng", "   ", "AA:BB:CC:DD"],
    ["thiếu vân tay", "vn.unicom.thi360", ""],
    ["vân tay chỉ có dấu phẩy và khoảng trắng", "vn.unicom.thi360", " , , "],
  ])("trả 200 mảng rỗng khi %s", async (_label, pkg, cert) => {
    vi.stubEnv("THI360_ANDROID_PACKAGE", pkg);
    vi.stubEnv("THI360_ANDROID_CERT_SHA256", cert);

    const response = getAssetLinks();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toBe("no-cache");
    expect(await response.json()).toEqual([]);
  });
});

describe("GET /.well-known/apple-app-site-association", () => {
  it("trả đúng appIDs và chỉ khai đường dẫn /reset-password khi đặt THI360_IOS_APP_ID", async () => {
    vi.stubEnv("THI360_IOS_APP_ID", "  TEAM123456.vn.unicom.thi360  ");

    const response = getAppleAppSiteAssociation();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toBe("no-cache");
    expect(await response.json()).toEqual({
      applinks: {
        details: [
          {
            appIDs: ["TEAM123456.vn.unicom.thi360"],
            components: [{ "/": "/reset-password" }],
          },
        ],
      },
    });
  });

  it.each([
    ["biến rỗng", ""],
    ["biến chỉ có khoảng trắng", "   "],
  ])("trả 200 details rỗng khi %s", async (_label, appId) => {
    vi.stubEnv("THI360_IOS_APP_ID", appId);

    const response = getAppleAppSiteAssociation();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/json");
    expect(response.headers.get("cache-control")).toBe("no-cache");
    expect(await response.json()).toEqual({ applinks: { details: [] } });
  });
});
