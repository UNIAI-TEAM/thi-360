import {
  expect,
  request as playwrightRequest,
  test,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
  type Request,
  type Response,
} from "@playwright/test";
import {
  API_URL,
  advanceTestClock,
  answerCurrentUnit,
  ensureDsReady,
  getAttemptStateApi,
  getMyExams,
  getTestClockNow,
  purchaseExamViaUi,
  resetTestClock,
  resolveActiveDeadlineIso,
  startAttemptFromPrepare,
  waitForSavedStatus,
  type AttemptStateApiResponse,
} from "./helpers/attempt-fixtures";
import { adminApi } from "./helpers/editor-qa";
import { WEB_URL, createVerifiedAccount, submitLoginForm, type E2eAccount } from "./helpers/mail";

interface AudioPlayApiPayload {
  assetId?: string;
  playId?: string | null;
  playsUsed: number;
  playsLeft: number;
  positionMs: number;
  durationMs: number;
  state: "IDLE" | "PLAYING" | "ENDED" | "EXHAUSTED";
  endReason?: string | null;
}

interface AudioStopLog {
  pauseCalls: number;
  srcCleared: boolean;
  endedAtPause: boolean | null;
  currentTimeAtPause: number | null;
}

interface AudioDomSnapshot {
  exists: boolean;
  hasControls: boolean;
  src: string | null;
  readyState: number;
  paused: boolean;
  ended: boolean;
  currentTime: number;
}

interface AudioNetworkTracker {
  starts: AudioPlayApiPayload[];
  resumes: AudioPlayApiPayload[];
  ends: AudioPlayApiPayload[];
  startRequests: number;
  dispose: () => void;
}

interface PrepareApiPayload {
  examId: string;
}

interface QuestionSummaryItem {
  id: string;
  code: string;
}

interface QuestionDetailPayload {
  content?: string;
  body?: {
    answers?: string[];
  };
}

interface ReviewItemApiPayload {
  itemId: string;
  outcome?: string;
  earnedPoints?: number | string | null;
  pointsMax?: number | string | null;
}

interface AttemptReviewApiPayload {
  items?: ReviewItemApiPayload[];
}

interface AttemptResultApiPayload {
  state?: string;
  displayScore?: number | null;
  statistics?: {
    totalCount?: number;
    blankCount?: number;
  };
  segments?: Array<{
    code?: string;
    name?: string;
    partCode?: string;
    fromPosition?: number;
    toPosition?: number;
    score?: number | null;
  }>;
}

interface AttemptStateItemPayload {
  itemId: string;
  displayNo?: number | null;
  questionType?: string;
  content?: string | null;
}

interface AttemptDisplaySegmentPayload {
  code?: string;
  name?: string;
  fromPosition?: number;
  toPosition?: number;
  commandCount?: number;
  maxPoints?: number;
}

interface AttemptStateWithDetailsPayload extends AttemptStateApiResponse {
  audioPlays?: AudioPlayApiPayload[];
  parts: Array<
    AttemptStateApiResponse["parts"][number] & {
      items?: AttemptStateItemPayload[];
      displaySegments?: AttemptDisplaySegmentPayload[];
    }
  >;
}

const SYMBOL_TOKENS_ORDERED = ["2+", "3+", "2-", "3-", "->", "+", "-", "(", ")", "2", "3", "4"] as const;
const SUBMIT_BUTTON_NAME = "Nộp bài";
const AUDIO_REGION_NAME = "Trình phát audio";
const SYMBOL_GROUP_NAME = "Thanh chèn ký hiệu";

function stripHtmlTags(html: string): string {
  return html
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&amp;/gi, "&")
    .replace(/&#39;/gi, "'")
    .replace(/&quot;/gi, '"')
    .replace(/\s+/g, " ")
    .trim();
}

function trackAudioNetwork(page: Page, attemptId: string): AudioNetworkTracker {
  const starts: AudioPlayApiPayload[] = [];
  const resumes: AudioPlayApiPayload[] = [];
  const ends: AudioPlayApiPayload[] = [];
  let startRequests = 0;

  const baseMarker = `/attempts/${attemptId}/audio/`;

  const onRequest = (req: Request) => {
    if (req.method() !== "POST") return;
    const url = req.url();
    if (url.includes(baseMarker) && url.endsWith("/plays")) {
      startRequests += 1;
    }
  };

  const onResponse = async (res: Response) => {
    if (res.request().method() !== "POST" || res.status() !== 200) return;
    const url = res.url();
    if (!url.includes(baseMarker)) return;
    const json = (await res.json().catch(() => null)) as AudioPlayApiPayload | null;
    if (!json) return;
    if (url.endsWith("/plays")) {
      starts.push(json);
    } else if (url.endsWith("/resume")) {
      resumes.push(json);
    } else if (url.endsWith("/end")) {
      ends.push(json);
    }
  };

  page.on("request", onRequest);
  page.on("response", onResponse);

  return {
    starts,
    resumes,
    ends,
    get startRequests() {
      return startRequests;
    },
    dispose: () => {
      page.off("request", onRequest);
      page.off("response", onResponse);
    },
  };
}

async function installAudioStopLogScript(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const win = window as Window & { __thi360AudioStopLog?: AudioStopLog };
    win.__thi360AudioStopLog = {
      pauseCalls: 0,
      srcCleared: false,
      endedAtPause: null,
      currentTimeAtPause: null,
    };

    const origPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function pauseWithLog(this: HTMLMediaElement): void {
      if (this.getAttribute("src")) {
        const log = win.__thi360AudioStopLog!;
        log.pauseCalls += 1;
        if (log.endedAtPause === null) {
          log.endedAtPause = Boolean(this.ended);
          log.currentTimeAtPause = Number.isFinite(this.currentTime) ? this.currentTime : 0;
        }
      }
      origPause.call(this);
    };

    const origRemoveAttribute = HTMLMediaElement.prototype.removeAttribute;
    HTMLMediaElement.prototype.removeAttribute = function removeAttributeWithLog(
      this: HTMLMediaElement,
      qualifiedName: string,
    ): void {
      if (qualifiedName.toLowerCase() === "src" && Boolean(this.getAttribute("src"))) {
        win.__thi360AudioStopLog!.srcCleared = true;
      }
      origRemoveAttribute.call(this, qualifiedName);
    };
  });
}

async function readAudioStopLog(page: Page): Promise<AudioStopLog | null> {
  return page.evaluate(() => {
    const win = window as Window & { __thi360AudioStopLog?: AudioStopLog };
    return win.__thi360AudioStopLog ?? null;
  });
}

async function startPlayAndCaptureAssetId(page: Page, attemptId: string): Promise<string> {
  const audioRegion = page.getByRole("region", { name: AUDIO_REGION_NAME });
  const playResPromise = page.waitForResponse(
    (res) =>
      res.url().includes(`/attempts/${attemptId}/audio/`) &&
      res.url().endsWith("/plays") &&
      res.request().method() === "POST" &&
      res.status() === 200,
  );
  await audioRegion.getByRole("button", { name: "Nghe" }).click();
  const playRes = await playResPromise;
  const assetId = /\/audio\/([^/]+)\/plays$/.exec(playRes.url())?.[1] ?? "";
  expect(assetId).not.toBe("");
  return assetId;
}

async function setupHscaMiniAttempt(
  browser: Browser,
  prefix: string,
): Promise<{
  account: E2eAccount;
  context: BrowserContext;
  page: Page;
  attemptId: string;
}> {
  const { account, cookies } = await createVerifiedAccount(prefix);
  const context = await browser.newContext();
  await context.addCookies(cookies);
  const page = await context.newPage();
  page.setDefaultTimeout(20_000);
  await installAudioStopLogScript(page);

  const { entitlementId } = await purchaseExamViaUi(page, "DS-HSCA-ANH-MINI");
  const attemptId = await startAttemptFromPrepare(page, entitlementId);
  await expect(page.locator('header [role="timer"]')).toBeVisible({ timeout: 25_000 });
  const audioRegion = page.getByRole("region", { name: AUDIO_REGION_NAME });
  await expect(audioRegion).toBeVisible({ timeout: 20_000 });
  await expect(audioRegion.getByRole("button", { name: "Nghe" })).toBeEnabled({ timeout: 20_000 });
  await expect(page.locator("audio[controls]")).toHaveCount(0);

  return { account, context, page, attemptId };
}

async function waitForAudioActivelyPlaying(page: Page, timeout = 15_000): Promise<AudioDomSnapshot> {
  await page.waitForFunction(
    () => {
      const el = document.querySelector("audio");
      return Boolean(el && el.readyState >= 2 && !el.paused && !el.ended && el.currentTime > 0);
    },
    null,
    { timeout },
  );
  return readAudioSnapshot(page);
}

async function readAudioSnapshot(page: Page): Promise<AudioDomSnapshot> {
  return page.evaluate(() => {
    const el = document.querySelector("audio");
    if (!el) {
      return {
        exists: false,
        hasControls: false,
        src: null,
        readyState: 0,
        paused: true,
        ended: false,
        currentTime: 0,
      };
    }
    return {
      exists: true,
      hasControls: el.hasAttribute("controls"),
      src: el.getAttribute("src"),
      readyState: el.readyState,
      paused: el.paused,
      ended: el.ended,
      currentTime: el.currentTime,
    };
  });
}

async function ensureResumedPlaybackIfAutoplayBlocked(page: Page): Promise<void> {
  const resumeBtn = page.getByRole("button", { name: "Phát lại" });
  const playingIndicator = page.waitForFunction(
    () => {
      const el = document.querySelector("audio");
      return Boolean(el && el.readyState >= 2 && !el.paused && !el.ended && el.currentTime > 0);
    },
    null,
    { timeout: 8_000 },
  );
  const autoplayButton = resumeBtn.waitFor({ state: "visible", timeout: 8_000 });

  await Promise.race([playingIndicator, autoplayButton]).catch(() => undefined);
  if (await resumeBtn.isVisible().catch(() => false)) {
    await resumeBtn.click();
  }
}

async function submitOrFinishPartViaHeaderDialog(
  page: Page,
  expectedAction?: "Nộp bài" | "Hoàn thành phần",
): Promise<"Nộp bài" | "Hoàn thành phần"> {
  const submitBtn = page.getByRole("button", { name: "Nộp bài" });
  const finishBtn = page.getByRole("button", { name: "Hoàn thành phần" });
  const targetBtn = expectedAction
    ? page.getByRole("button", { name: expectedAction })
    : submitBtn.or(finishBtn).first();
  await targetBtn.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  const confirmSubmit = dialog.getByRole("button", { name: "Nộp bài" });
  const confirmFinish = dialog.getByRole("button", { name: "Hoàn thành phần" });
  if (expectedAction === "Nộp bài" || (!expectedAction && (await confirmSubmit.isVisible().catch(() => false)))) {
    await confirmSubmit.click();
    return "Nộp bài";
  }
  await confirmFinish.click();
  return "Hoàn thành phần";
}

async function submitExamAndOpenResult(page: Page, attemptId: string): Promise<void> {
  await submitOrFinishPartViaHeaderDialog(page, "Nộp bài");
  await expect(page.getByRole("heading", { name: "Lượt thi đã đóng" })).toBeVisible({ timeout: 20_000 });
  const viewResultLink = page.getByRole("link", { name: "Xem kết quả" });
  await expect(viewResultLink).toBeVisible({ timeout: 10_000 });
  await viewResultLink.click();
  await page.waitForURL(new RegExp(`/attempts/${attemptId}/result$`), { timeout: 25_000 });
}

async function advancePartsUntilQuestionType(
  page: Page,
  attemptId: string,
  questionType: string,
): Promise<AttemptStateWithDetailsPayload> {
  for (let round = 0; round < 5; round += 1) {
    const state = (await getAttemptStateApi(page.request, attemptId)) as AttemptStateWithDetailsPayload;
    const matched = (state.parts ?? []).some((part) =>
      (part.items ?? []).some((item) => item.questionType === questionType),
    );
    if (matched) return state;

    const action = await submitOrFinishPartViaHeaderDialog(page, "Hoàn thành phần");
    expect(action).toBe("Hoàn thành phần");
    await expect
      .poll(
        async () => {
          const nextState = (await getAttemptStateApi(page.request, attemptId)) as AttemptStateWithDetailsPayload;
          return (nextState.parts ?? []).some((part) =>
            (part.items ?? []).some((item) => item.questionType === questionType),
          )
            ? "found"
            : nextState.parts.find((p) => (p.items ?? []).length > 0)?.ord ?? "none";
        },
        { timeout: 15_000 },
      )
      .not.toBe("none");
  }
  return (await getAttemptStateApi(page.request, attemptId)) as AttemptStateWithDetailsPayload;
}

async function setE2eAudioFault(examId: string, active: boolean): Promise<void> {
  const api = await playwrightRequest.newContext({ baseURL: API_URL });
  try {
    const url = `/api/v1/test/audio-faults/${encodeURIComponent(examId)}`;
    const res = active ? await api.post(url) : await api.delete(url);
    expect(res.status(), `${active ? "POST" : "DELETE"} ${url}: ${await res.text()}`).toBe(200);
  } finally {
    await api.dispose();
  }
}

async function expectAudioStoppedImmediately(page: Page): Promise<void> {
  const snap1 = await readAudioSnapshot(page);
  const stopLog = await readAudioStopLog(page);
  if (snap1.exists) {
    expect(snap1.paused, "Audio must be paused immediately on stop trigger").toBe(true);
    expect(snap1.ended, "Audio stop must come from trigger before natural end").toBe(false);
    expect(snap1.src, "Audio src must be stripped immediately on stop trigger").toBeNull();
  }
  expect(stopLog, "Audio teardown log must exist on window.__thi360AudioStopLog").not.toBeNull();
  expect(stopLog!.pauseCalls, "Teardown must explicitly call audio.pause()").toBeGreaterThan(0);
  expect(stopLog!.srcCleared, "Teardown must clear <audio src>").toBe(true);
  expect(stopLog!.endedAtPause, "Audio must be stopped before reaching natural ended").toBe(false);
  expect(stopLog!.currentTimeAtPause ?? 0, "Audio must have started playing before stop").toBeGreaterThan(0);
  await page.waitForTimeout(600);
  const snap2 = await readAudioSnapshot(page);
  if (snap1.exists && snap2.exists) {
    expect(snap2.paused).toBe(true);
    expect(snap2.currentTime).toBeLessThanOrEqual(snap1.currentTime + 0.05);
  }
}

async function resolveHoaIonAnswerMap(): Promise<Map<string, string>> {
  const { api, headers } = await adminApi();
  try {
    const allItems: QuestionSummaryItem[] = [];
    for (let pageNo = 0; pageNo < 5; pageNo += 1) {
      const listRes = await api.get("/api/v1/admin/questions", {
        headers,
        params: { subjectCode: "HOA", type: "SHORT_ANSWER", page: pageNo, size: 100 },
      });
      expect(listRes.status(), `GET admin HOA questions p${pageNo}: ${await listRes.text()}`).toBe(200);
      const listJson = (await listRes.json()) as { items?: QuestionSummaryItem[] };
      const batch = listJson.items ?? [];
      allItems.push(...batch);
      if (batch.length < 100) break;
    }

    const map = new Map<string, string>();
    const details = await Promise.all(
      allItems.map(async (item) => {
        const res = await api.get(`/api/v1/admin/questions/${encodeURIComponent(item.id)}`, { headers });
        expect(res.status()).toBe(200);
        return (await res.json()) as QuestionDetailPayload;
      }),
    );
    for (const detail of details) {
      const prompt = stripHtmlTags(detail.content ?? "");
      const ans = detail.body?.answers?.[0]?.trim();
      if (prompt && ans) {
        map.set(prompt, ans);
      }
    }
    return map;
  } finally {
    await api.dispose();
  }
}

async function waitForResultScored(request: APIRequestContext, attemptId: string): Promise<AttemptResultApiPayload> {
  let latest: AttemptResultApiPayload = {};
  await expect
    .poll(
      async () => {
        latest = await getAttemptResultApi(request, attemptId);
        return latest.state ?? "";
      },
      { timeout: 25_000, message: `Lượt thi ${attemptId} phải chuyển sang trạng thái SCORED` },
    )
    .toBe("SCORED");
  return latest;
}

async function getAttemptReviewApi(request: APIRequestContext, attemptId: string): Promise<AttemptReviewApiPayload> {
  const res = await request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/review`);
  expect(res.status(), `GET /review: ${await res.text()}`).toBe(200);
  return (await res.json()) as AttemptReviewApiPayload;
}

async function getAttemptResultApi(request: APIRequestContext, attemptId: string): Promise<AttemptResultApiPayload> {
  const res = await request.get(`${WEB_URL}/api/v1/me/attempts/${encodeURIComponent(attemptId)}/result`);
  expect(res.status(), `GET /result: ${await res.text()}`).toBe(200);
  return (await res.json()) as AttemptResultApiPayload;
}

function tokenizeIonFormula(formula: string): Array<{ kind: "symbol" | "text"; value: string }> {
  const tokens: Array<{ kind: "symbol" | "text"; value: string }> = [];
  let i = 0;
  while (i < formula.length) {
    const sym = SYMBOL_TOKENS_ORDERED.find((candidate) => formula.startsWith(candidate, i));
    if (sym) {
      tokens.push({ kind: "symbol", value: sym });
      i += sym.length;
    } else {
      tokens.push({ kind: "text", value: formula[i]! });
      i += 1;
    }
  }
  return tokens;
}

async function openMobileNavAndJump(page: Page, displayNo: number): Promise<void> {
  await page.getByRole("button", { name: /^Danh sách câu/ }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog
    .locator("button[data-completion]")
    .filter({ has: page.locator(`:text-is("${displayNo}")`) })
    .or(dialog.getByRole("button", { name: new RegExp(`^${displayNo}(?:,|\\s|$)`) }))
    .first()
    .click();
  await expect(dialog).toBeHidden({ timeout: 10_000 });
}

test.describe("FS-7 E2E · H-SCA audio, ký hiệu ion & V-SAT phân đoạn", () => {
  test.setTimeout(180_000);

  test.beforeAll(async ({ browser }) => {
    await ensureDsReady(browser);
  });

  test("AC-5 nghe hai lần: DS-HSCA-ANH-MINI F5 x4 khi đang phát giữ đúng 1 lần dùng /resume, tối đa 2 lần, nộp SCORED", async ({
    browser,
  }) => {
    const { context, page, attemptId } = await setupHscaMiniAttempt(browser, "ac5");
    const tracker = trackAudioNetwork(page, attemptId);

    try {
      const audioRegion = page.getByRole("region", { name: AUDIO_REGION_NAME });
      await expect(audioRegion.getByText("Còn 2/2 lần nghe", { exact: true })).toBeVisible();

      const playBtn = audioRegion.getByRole("button", { name: "Nghe" });
      await expect(playBtn).toBeEnabled();

      // Seeded hsca-en-listening-*.mp3 clips are ~2.09s (< 10s progressIntervalMs),
      // so we react as soon as active playback begins and verify 4 reload/resume cycles cleanly.
      const play1ResponsePromise = page.waitForResponse(
        (res) =>
          res.url().includes(`/attempts/${attemptId}/audio/`) &&
          res.url().endsWith("/plays") &&
          res.request().method() === "POST" &&
          res.status() === 200,
      );
      await playBtn.click();
      const play1Res = await play1ResponsePromise;
      const playedAssetId = /\/audio\/([^/]+)\/plays$/.exec(play1Res.url())?.[1] ?? "";
      expect(playedAssetId).not.toBe("");
      const play1Json = (await play1Res.json()) as AudioPlayApiPayload & { assetId?: string };
      expect(play1Json.playsUsed).toBe(1);
      expect(play1Json.playsLeft).toBe(1);
      expect(play1Json.state).toBe("PLAYING");

      await waitForAudioActivelyPlaying(page);

      for (let reloadNo = 1; reloadNo <= 4; reloadNo += 1) {
        const prevResumeCount = tracker.resumes.length;
        await page.reload({ waitUntil: "domcontentloaded" });
        await expect(audioRegion).toBeVisible({ timeout: 15_000 });
        await expect(page.locator("audio[controls]")).toHaveCount(0);

        await expect
          .poll(() => tracker.resumes.length, {
            timeout: 10_000,
            message: `Reload #${reloadNo} phải gọi POST /resume`,
          })
          .toBeGreaterThan(prevResumeCount);

        await ensureResumedPlaybackIfAutoplayBlocked(page);

        expect(tracker.startRequests, `Reload #${reloadNo} không được gọi lại POST /plays`).toBe(1);
      }

      // Let play #1 complete and send POST /end.
      await expect
        .poll(() => tracker.ends.length, {
          timeout: 15_000,
          message: "Lần nghe 1 phải kết thúc qua POST /end",
        })
        .toBeGreaterThanOrEqual(1);

      expect(tracker.ends[0]?.playsUsed).toBe(1);
      expect(tracker.ends[0]?.playsLeft).toBe(1);
      expect(tracker.ends[0]?.state).toBe("ENDED");

      const stateAfterPlay1 = (await getAttemptStateApi(page.request, attemptId)) as AttemptStateWithDetailsPayload;
      const assetStateAfterPlay1 = (stateAfterPlay1.audioPlays ?? []).find(
        (entry) => (entry as AudioPlayApiPayload & { assetId?: string }).assetId === playedAssetId,
      );
      expect(assetStateAfterPlay1?.playsUsed).toBe(1);

      await expect(audioRegion.getByText("Còn 1/2 lần nghe", { exact: true })).toBeVisible();
      const playBtn2 = audioRegion.getByRole("button", { name: "Nghe" });
      await expect(playBtn2).toBeEnabled();

      // Play #2 consumes the second allowed play.
      const play2ResponsePromise = page.waitForResponse(
        (res) =>
          res.url().includes(`/attempts/${attemptId}/audio/`) &&
          res.url().endsWith("/plays") &&
          res.request().method() === "POST" &&
          res.status() === 200,
      );
      await playBtn2.click();
      const play2Json = (await (await play2ResponsePromise).json()) as AudioPlayApiPayload;
      expect(play2Json.playsUsed).toBe(2);
      expect(play2Json.playsLeft).toBe(0);

      await waitForAudioActivelyPlaying(page);
      await expect
        .poll(() => tracker.ends.length, {
          timeout: 15_000,
          message: "Lần nghe 2 phải kết thúc qua POST /end",
        })
        .toBe(2);

      expect(tracker.ends[1]?.playsUsed).toBe(2);
      expect(tracker.ends[1]?.playsLeft).toBe(0);
      expect(tracker.startRequests).toBe(2);

      await expect(audioRegion.getByText("Đã hết lượt nghe (0/2)", { exact: true })).toBeVisible();
      await expect(audioRegion.getByText("Bạn đã dùng hết số lần nghe cho tệp audio này.", { exact: true })).toBeVisible();
      await expect(audioRegion.getByRole("button", { name: "Nghe" })).toBeDisabled();

      await answerCurrentUnit(page);
      await waitForSavedStatus(page);
      await submitOrFinishPartViaHeaderDialog(page, "Hoàn thành phần");
      await expect(page.getByRole("button", { name: SUBMIT_BUTTON_NAME })).toBeVisible({ timeout: 20_000 });
      await submitExamAndOpenResult(page, attemptId);

      const result = await waitForResultScored(page.request, attemptId);
      expect(result.state).toBe("SCORED");
    } finally {
      tracker.dispose();
      await context.close();
    }
  });

  test("AC-6 mất mạng khi nghe: offline tạm dừng + banner chờ kết nối, online tự /resume giữ nguyên 1 lần nghe", async ({
    browser,
  }) => {
    const { context, page, attemptId } = await setupHscaMiniAttempt(browser, "ac6");
    const tracker = trackAudioNetwork(page, attemptId);

    try {
      const audioRegion = page.getByRole("region", { name: AUDIO_REGION_NAME });
      await audioRegion.getByRole("button", { name: "Nghe" }).click();
      await waitForAudioActivelyPlaying(page);
      expect(tracker.starts.length).toBe(1);

      // Go offline during active playback.
      // Seeded audio is ~2.09s (< 10s progressIntervalMs), so PUT /progress is not expected before completion.
      await context.setOffline(true);
      await expect(
        audioRegion.getByText("Mất kết nối mạng, đang chờ kết nối lại để phát tiếp."),
      ).toBeVisible({ timeout: 8_000 });

      const offlineSnap = await readAudioSnapshot(page);
      expect(offlineSnap.paused).toBe(true);
      expect(offlineSnap.ended).toBe(false);

      // Restore network: online listener triggers POST /resume automatically without opening a 2nd play.
      await context.setOffline(false);

      await expect
        .poll(() => tracker.resumes.length, {
          timeout: 12_000,
          message: "Online lại phải gọi POST /resume mà không mở lượt mới",
        })
        .toBeGreaterThanOrEqual(1);

      const autoplayNotice = audioRegion.getByText(
        "Trình duyệt chặn tự động phát. Bấm Phát lại để tiếp tục mà không mất lượt nghe.",
      );
      if (await autoplayNotice.isVisible().catch(() => false)) {
        await audioRegion.getByRole("button", { name: "Phát lại" }).click();
      }

      expect(tracker.startRequests).toBe(1);
      expect(tracker.resumes.at(-1)?.playsUsed).toBe(1);
      expect(tracker.resumes.at(-1)?.playsLeft).toBe(1);

      await expect
        .poll(() => tracker.ends.length, {
          timeout: 15_000,
          message: "Phát tiếp sau khi online phải hoàn thành lượt 1",
        })
        .toBe(1);

      expect(tracker.startRequests).toBe(1);
      expect(tracker.ends[0]?.playsUsed).toBe(1);
      expect(tracker.ends[0]?.playsLeft).toBe(1);
    } finally {
      await context.setOffline(false).catch(() => undefined);
      tracker.dispose();
      await context.close();
    }
  });

  test("AC-7 dừng phát tức thì ở cả 3 nhánh: hoàn thành phần (đóng phần), hết giờ TestClock và phiên khác chiếm quyền", async ({
    browser,
  }) => {
    await resetTestClock();
    try {
      // Branch (a): Closing active part during active playback stops audio immediately
      {
        const { context, page, attemptId } = await setupHscaMiniAttempt(browser, "ac7a");
        try {
          const playedAssetId = await startPlayAndCaptureAssetId(page, attemptId);
          await waitForAudioActivelyPlaying(page);

          await submitOrFinishPartViaHeaderDialog(page, "Hoàn thành phần");
          await expectAudioStoppedImmediately(page);
          await expect(page.getByRole("button", { name: SUBMIT_BUTTON_NAME })).toBeVisible({ timeout: 20_000 });

          const stateAfterClose = (await getAttemptStateApi(
            page.request,
            attemptId,
          )) as AttemptStateWithDetailsPayload;
          const closedPlay = (stateAfterClose.audioPlays ?? []).find((entry) => entry.assetId === playedAssetId);
          expect(closedPlay?.state).toBe("ENDED");
          expect(closedPlay?.endReason).toBe("CLOSED");
        } finally {
          await context.close();
        }
      }

      // Branch (b): Timer expires (TIME_UP) during active playback
      {
        const { context, page, attemptId } = await setupHscaMiniAttempt(browser, "ac7b");
        try {
          const state = await getAttemptStateApi(page.request, attemptId);
          const deadlineIso = resolveActiveDeadlineIso(state);
          expect(deadlineIso).not.toBeNull();
          const nowMs = Date.parse(await getTestClockNow());
          const remainingSec = Math.floor((Date.parse(deadlineIso!) - nowMs) / 1000);
          expect(remainingSec).toBeGreaterThan(15);

          // Leave ~12s before deadline so page reloads and claims session cleanly, then start Play right at 00:02.
          await advanceTestClock(remainingSec - 12);
          await page.reload({ waitUntil: "domcontentloaded" });

          const timer = page.locator('header [role="timer"]');
          await expect(timer).toBeVisible({ timeout: 15_000 });
          const audioRegion = page.getByRole("region", { name: AUDIO_REGION_NAME });
          await expect(audioRegion.getByRole("button", { name: "Nghe" })).toBeEnabled({ timeout: 10_000 });

          await expect
            .poll(async () => (await timer.innerText()).trim(), { timeout: 15_000, intervals: [50] })
            .toMatch(/00:02$/);

          const playedAssetId = await startPlayAndCaptureAssetId(page, attemptId);
          await waitForAudioActivelyPlaying(page, 4_000);

          await expect(
            page
              .getByText(/Đã hết giờ làm bài/i)
              .or(page.getByRole("heading", { name: /Hết giờ|Đã hết giờ/i }))
              .first(),
          ).toBeVisible({ timeout: 10_000 });
          await expectAudioStoppedImmediately(page);

          await expect
            .poll(
              async () => {
                const closedState = (await getAttemptStateApi(
                  page.request,
                  attemptId,
                )) as AttemptStateWithDetailsPayload;
                const entry = (closedState.audioPlays ?? []).find((item) => item.assetId === playedAssetId);
                return entry ? { state: entry.state, endReason: entry.endReason } : null;
              },
              { timeout: 20_000, message: "TIME_UP phải chuyển audioPlays sang ENDED với endReason=CLOSED" },
            )
            .toEqual({ state: "ENDED", endReason: "CLOSED" });
        } finally {
          await resetTestClock();
          await context.close();
        }
      }

      // Branch (c): Another session takes over (ATTEMPT_SESSION_REVOKED) during active playback
      {
        const { account, context: contextA, page: pageA, attemptId } = await setupHscaMiniAttempt(browser, "ac7c");
        const contextB = await browser.newContext();
        try {
          const pageB = await contextB.newPage();
          pageB.setDefaultTimeout(20_000);
          await pageB.goto(`${WEB_URL}/login?callbackUrl=${encodeURIComponent(`/attempts/${attemptId}`)}`);
          await submitLoginForm(pageB, account.username, account.password);

          const takeoverBtnB = pageB.getByRole("button", { name: "Tiếp tục tại đây" });
          await expect(takeoverBtnB).toBeVisible({ timeout: 20_000 });

          // Start audio on contextA while takeover dialog is already armed on contextB.
          const audioRegionA = pageA.getByRole("region", { name: AUDIO_REGION_NAME });
          await audioRegionA.getByRole("button", { name: "Nghe" }).click();
          await waitForAudioActivelyPlaying(pageA);

          const sessionTakeoverPromise = pageB.waitForResponse(
            (res) =>
              res.url().includes(`/attempts/${attemptId}/session`) &&
              res.request().method() === "POST" &&
              res.status() === 200,
          );
          await takeoverBtnB.click();
          await sessionTakeoverPromise;

          // Trigger server revocation detection from contextA right away.
          await answerCurrentUnit(pageA);

          await expect(
            pageA.getByRole("heading", { name: "Lượt thi đã được tiếp tục ở thiết bị khác." }),
          ).toBeVisible({ timeout: 15_000 });
          await expectAudioStoppedImmediately(pageA);
          await expect(
            pageB.getByRole("button", { name: /^(Nộp bài|Hoàn thành phần)$/ }),
          ).toBeVisible({ timeout: 15_000 });
        } finally {
          await contextB.close();
          await contextA.close();
        }
      }
    } finally {
      await resetTestClock();
    }
  });

  test("AC-11 nhập ion trên điện thoại 390x844: SymbolInputBar nút >= 44x44, giữ caret, chấm CORRECT", async ({
    browser,
  }) => {
    const promptToAnswer = await resolveHoaIonAnswerMap();
    expect(promptToAnswer.size).toBeGreaterThan(0);

    const { cookies } = await createVerifiedAccount("ac11");
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    await context.addCookies(cookies);
    const page = await context.newPage();
    page.setDefaultTimeout(20_000);

    try {
      const { entitlementId } = await purchaseExamViaUi(page, "DS-HSCA-HOA");
      const attemptId = await startAttemptFromPrepare(page, entitlementId);
      await expect(page.locator('header [role="timer"]')).toBeVisible({ timeout: 25_000 });

      const state = await advancePartsUntilQuestionType(page, attemptId, "SHORT_ANSWER");
      let targetItemId = "";
      let targetDisplayNo = 1;
      let targetAnswer = "";

      for (const part of state.parts ?? []) {
        for (const item of part.items ?? []) {
          if (item.questionType !== "SHORT_ANSWER" || !item.displayNo) continue;
          const plainPrompt = stripHtmlTags(item.content ?? "");
          const ans = promptToAnswer.get(plainPrompt);
          if (ans && (ans.includes("+") || ans.includes("-"))) {
            targetItemId = item.itemId;
            targetDisplayNo = item.displayNo;
            targetAnswer = ans;
            break;
          }
        }
        if (targetItemId) break;
      }

      expect(targetItemId, "Phải tìm thấy ít nhất 1 câu trả lời ngắn có công thức ion trong DS-HSCA-HOA").not.toBe("");
      expect(targetAnswer).not.toBe("");

      await openMobileNavAndJump(page, targetDisplayNo);

      const symbolBar = page.getByRole("group", { name: SYMBOL_GROUP_NAME });
      await expect(symbolBar).toBeVisible({ timeout: 15_000 });

      const barButtons = symbolBar.locator("button");
      const buttonCount = await barButtons.count();
      expect(buttonCount).toBeGreaterThanOrEqual(10);
      for (let idx = 0; idx < buttonCount; idx += 1) {
        const box = await barButtons.nth(idx).boundingBox();
        expect(box, `Nút ký hiệu #${idx} phải có kích thước hiển thị`).not.toBeNull();
        expect(box!.width).toBeGreaterThanOrEqual(44);
        expect(box!.height).toBeGreaterThanOrEqual(44);
      }

      const input = page.locator('main fieldset[data-attempt-question-fieldset] input[id$="-input"]');
      await expect(input).toBeVisible();

      // Verify SymbolInputBar inserts at current caret position and leaves caret immediately after inserted text.
      await input.click();
      await input.fill("X+");
      await input.press("ArrowLeft");
      await symbolBar.locator('button[data-symbol-insert="2"]').click();
      await expect(input).toHaveValue("X2+");
      const caretAfterMiddleInsert = await input.evaluate((el) => (el as HTMLInputElement).selectionStart);
      expect(caretAfterMiddleInsert).toBe(2);

      // Clear and enter the real expected ion formula using SymbolInputBar clicks + keyboard text.
      await input.fill("");
      await input.focus();
      let symbolClicksUsed = 0;
      for (const token of tokenizeIonFormula(targetAnswer)) {
        if (token.kind === "symbol") {
          const btn = symbolBar.locator(`button[data-symbol-insert="${token.value}"]`);
          await expect(btn).toBeVisible();
          await btn.click();
          symbolClicksUsed += 1;
        } else {
          await page.keyboard.type(token.value);
        }
      }
      expect(symbolClicksUsed).toBeGreaterThan(0);
      await expect(input).toHaveValue(targetAnswer);

      await waitForSavedStatus(page);
      await submitExamAndOpenResult(page, attemptId);
      await waitForResultScored(page.request, attemptId);

      const reviewPayload = await getAttemptReviewApi(page.request, attemptId);
      const graded = (reviewPayload.items ?? []).find((item) => item.itemId === targetItemId);
      expect(graded).toBeDefined();
      expect(graded?.outcome).toBe("CORRECT");
      expect(Number(graded?.earnedPoints ?? 0)).toBeGreaterThan(0);

      await page.goto(`${WEB_URL}/attempts/${attemptId}/review`);
      const part3Tab = page.getByRole("link", { name: /Phần 3|Điền đáp án/ });
      await expect(part3Tab).toBeVisible({ timeout: 20_000 });
      await part3Tab.click();
      const reviewCard = page.locator(`#review-item-${targetItemId}`);
      await expect(reviewCard).toBeVisible({ timeout: 20_000 });
      await expect(reviewCard.getByText("Đúng", { exact: true })).toBeVisible();
    } finally {
      await context.close();
    }
  });

  test("AC-13 V-SAT phân đoạn: DS-VSAT-ANH 4 displaySegments, 1 timer, badge câu, SHARED passage Q8-Q15, nộp đủ 25 câu SCORED", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("ac13");
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    await context.addCookies(cookies);
    const page = await context.newPage();
    page.setDefaultTimeout(20_000);

    try {
      const { entitlementId } = await purchaseExamViaUi(page, "DS-VSAT-ANH");
      const attemptId = await startAttemptFromPrepare(page, entitlementId);
      await expect(page.locator('header [role="timer"]')).toBeVisible({ timeout: 25_000 });

      const initialAttemptState = (await getAttemptStateApi(
        page.request,
        attemptId,
      )) as AttemptStateWithDetailsPayload;
      const displaySegments = initialAttemptState.parts[0]?.displaySegments ?? [];
      expect(displaySegments).toHaveLength(4);
      expect(displaySegments.map((seg) => [seg.fromPosition, seg.toPosition])).toEqual([
        [1, 9],
        [10, 15],
        [16, 20],
        [21, 25],
      ]);

      // Strictly one countdown timer on the page.
      await expect(page.locator('[role="timer"]')).toHaveCount(1);

      const sidebar = page.locator('aside[aria-label="Danh sách câu hỏi"]');
      await expect(sidebar).toBeVisible();
      for (const segName of ["Phần I", "Phần II", "Phần III", "Phần IV"]) {
        await expect(sidebar.getByText(segName, { exact: true })).toBeVisible();
      }

      // Check segment header + shared passage between Q8-9 (Phần I) and Q10-15 (Phần II).
      await expect(page.locator("[data-segment-code=\"P1\"]")).toBeVisible();
      await expect(page.locator("[data-segment-header]")).toContainText("Phần I");

      const jumpDesktopQuestion = async (qNo: number) => {
        await sidebar
          .locator("button[data-completion]")
          .filter({ has: page.locator(`:text-is("${qNo}")`) })
          .or(sidebar.getByRole("button", { name: new RegExp(`^${qNo}(?:,|\\s|$)`) }))
          .first()
          .click();
      };

      await jumpDesktopQuestion(8);
      await expect(page.locator('[data-segment-code="P1"]')).toBeVisible();
      const passageUnitA = await page
        .locator('main fieldset[data-attempt-question-fieldset] [data-exam-content]')
        .first()
        .innerText();
      expect(passageUnitA.trim().length).toBeGreaterThan(20);

      await jumpDesktopQuestion(10);
      await expect(page.locator('[data-segment-code="P2"]')).toBeVisible();
      await expect(page.locator("[data-segment-header]")).toContainText("Phần II");
      const passageUnitB = await page
        .locator('main fieldset[data-attempt-question-fieldset] [data-exam-content]')
        .first()
        .innerText();
      expect(passageUnitB.trim()).toBe(passageUnitA.trim());

      await jumpDesktopQuestion(16);
      await expect(page.locator('[data-segment-code="P3"]')).toBeVisible();
      await expect(page.locator("[data-segment-header]")).toContainText("Phần III");

      await jumpDesktopQuestion(21);
      await expect(page.locator('[data-segment-code="P4"]')).toBeVisible();
      await expect(page.locator("[data-segment-header]")).toContainText("Phần IV");

      // Walk through all units from Q1 to Q25 and fill every unit.
      await jumpDesktopQuestion(1);
      const nextBtn = page.getByRole("button", { name: "Câu sau" });
      for (let step = 0; step < 30; step += 1) {
        await answerCurrentUnit(page, "answer");
        if (await nextBtn.isEnabled()) {
          await nextBtn.click();
        } else {
          break;
        }
      }

      await waitForSavedStatus(page);
      await submitExamAndOpenResult(page, attemptId);

      const segmentsSection = page.locator('section[aria-labelledby="result-segments-title"]');
      await expect(segmentsSection).toBeVisible({ timeout: 20_000 });
      for (const segName of ["Phần I", "Phần II", "Phần III", "Phần IV"]) {
        await expect(segmentsSection.getByText(segName, { exact: true })).toBeVisible();
      }

      const result = await waitForResultScored(page.request, attemptId);
      expect(result.state).toBe("SCORED");
      expect(result.segments).toHaveLength(4);
      expect(result.statistics?.totalCount).toBe(25);
      expect(result.displayScore).not.toBeNull();
      expect(result.displayScore).toBeDefined();
      expect(Number.isFinite(Number(result.displayScore))).toBe(true);
      const segSum = (result.segments ?? []).reduce((acc, s) => acc + Number(s.score ?? 0), 0);
      expect(Math.abs(segSum - Number(result.displayScore))).toBeLessThanOrEqual(0.001);
    } finally {
      await context.close();
    }
  });

  test("AC-14 thiếu tệp audio: fault injection -> ABORTED + hoàn lượt + Thử lại, gỡ lỗi -> IN_PROGRESS", async ({
    browser,
  }) => {
    const { cookies } = await createVerifiedAccount("ac14");
    const context = await browser.newContext();
    await context.addCookies(cookies);
    const page = await context.newPage();
    page.setDefaultTimeout(20_000);

    let faultedExamId = "";
    try {
      const { entitlementId } = await purchaseExamViaUi(page, "DS-HSCA-ANH-MINI");
      const prepRes = await page.request.get(`${WEB_URL}/api/v1/me/entitlements/${encodeURIComponent(entitlementId)}/prepare`);
      expect(prepRes.status(), `GET /prepare: ${await prepRes.text()}`).toBe(200);
      const prepJson = (await prepRes.json()) as PrepareApiPayload;
      faultedExamId = prepJson.examId;
      expect(faultedExamId).toBeTruthy();

      const beforeExams = await getMyExams(page);
      const beforeLeft = beforeExams.find((e) => e.entitlementId === entitlementId)?.attemptsLeft;
      expect(beforeLeft).toBeGreaterThan(0);

      await page.goto(`${WEB_URL}/prepare/${encodeURIComponent(entitlementId)}`);
      const startBtn = page.getByRole("button", { name: "Bắt đầu bài thi" });
      await expect(startBtn).toBeVisible({ timeout: 15_000 });

      await setE2eAudioFault(faultedExamId, true);

      await startBtn.click();

      const alert = page.locator('main [role="alert"]');
      await expect(alert).toContainText("Thiếu tệp âm thanh cần thiết cho đề thi.", { timeout: 25_000 });
      await expect(alert).toContainText("Lượt đã được trả lại, bạn chưa mất lượt nào. Đồng hồ chưa chạy.");

      const retryBtn = page.getByRole("button", { name: "Thử lại" });
      await expect(retryBtn).toBeVisible();

      const afterAbortExams = await getMyExams(page);
      const afterAbortLeft = afterAbortExams.find((e) => e.entitlementId === entitlementId)?.attemptsLeft;
      expect(afterAbortLeft).toBe(beforeLeft);

      await setE2eAudioFault(faultedExamId, false);
      faultedExamId = "";

      await retryBtn.click();
      await page.waitForURL(/\/attempts\/[0-9a-f-]+$/, { timeout: 25_000 });
      await expect(
        page.getByRole("button", { name: /^(Nộp bài|Hoàn thành phần)$/ }),
      ).toBeVisible({ timeout: 25_000 });
      await expect(page.getByRole("region", { name: AUDIO_REGION_NAME })).toBeVisible({ timeout: 20_000 });
    } finally {
      if (faultedExamId) {
        await setE2eAudioFault(faultedExamId, false).catch(() => undefined);
      }
      await context.close();
    }
  });
});
