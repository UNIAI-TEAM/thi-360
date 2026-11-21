import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { UiStringsProvider } from "../../components/ui-strings";
import { UI_STRINGS_EN, UI_STRINGS_VI } from "../../messages/ui-strings";
import { AttemptAudioPlayer, AudioPlayer } from "./audio-player";
import { createTrialAudioAdapter } from "./trial-adapter";
import { AudioAdapterError, type AudioPlayAdapter, type AudioPlaybackSnapshot } from "./types";

const tVi = UI_STRINGS_VI.question.audioPlayer;
const tEn = UI_STRINGS_EN.question.audioPlayer;

function makeSnapshot(overrides: Partial<AudioPlaybackSnapshot> = {}): AudioPlaybackSnapshot {
  return {
    playId: null,
    playsUsed: 0,
    playsLeft: 2,
    durationMs: 60_000,
    positionMs: 0,
    state: "IDLE",
    endReason: null,
    ...overrides,
  };
}

function makeAdapter(overrides: Partial<AudioPlayAdapter> = {}): AudioPlayAdapter {
  let seq = 0;
  return {
    openPlay: vi.fn(async () => {
      seq += 1;
      return makeSnapshot({
        playId: `play-${seq}`,
        playsUsed: seq,
        playsLeft: Math.max(0, 2 - seq),
        state: "PLAYING",
      });
    }),
    resumePlay: vi.fn(async (playId: string) =>
      makeSnapshot({
        playId,
        playsUsed: 1,
        playsLeft: 1,
        state: "PLAYING",
      }),
    ),
    reportProgress: vi.fn(async (playId: string, positionMs: number) =>
      makeSnapshot({
        playId,
        playsUsed: 1,
        playsLeft: 1,
        positionMs,
        state: "PLAYING",
      }),
    ),
    endPlay: vi.fn(async (playId: string) =>
      makeSnapshot({
        playId,
        playsUsed: 1,
        playsLeft: 1,
        positionMs: 60_000,
        state: "ENDED",
        endReason: "FINISHED",
      }),
    ),
    resolveStreamUrl: vi.fn((playId: string) => `/api/v1/me/attempts/att-1/assets/asset-1?playId=${playId}`),
    reloadState: vi.fn(async () => makeSnapshot({ state: "ENDED", playsUsed: 1, playsLeft: 1, endReason: "FINISHED" })),
    ...overrides,
  };
}

function mockAudioMethods(options?: { playImpl?: () => Promise<void> }) {
  const playSpy = vi
    .spyOn(HTMLMediaElement.prototype, "play")
    .mockImplementation(options?.playImpl ?? (async () => {}));
  const pauseSpy = vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
  const loadSpy = vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
  return { playSpy, pauseSpy, loadSpy };
}

beforeEach(() => {
  vi.restoreAllMocks();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("chính sách nghe", () => {
  it("không có thanh tua, đổi tốc độ, nút tạm dừng hay native controls; hiện tên tệp trong vùng nội dung đề", () => {
    mockAudioMethods();
    const adapter = makeAdapter();
    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        fileName="listening-part-1.mp3"
        initialState={makeSnapshot()}
        adapter={adapter}
      />,
    );

    const audio = container.querySelector("audio");
    expect(audio).not.toBeNull();
    expect(audio).not.toHaveAttribute("controls");
    expect(audio).toHaveAttribute("preload", "none");

    expect(container.querySelector('input[type="range"]')).toBeNull();
    expect(screen.queryByRole("slider")).toBeNull();
    expect(screen.queryByRole("button", { name: /tạm dừng|pause|tốc độ|speed|1\.5x|2x/i })).toBeNull();
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeInTheDocument();

    const fileNameEl = screen.getByText("listening-part-1.mp3");
    expect(fileNameEl.closest("[data-exam-content]")).not.toBeNull();
    expect(screen.getByText(tVi.playsRemaining(2, 2)).closest("[data-exam-content]")).toBeNull();
    expect(AudioPlayer).toBe(AttemptAudioPlayer);
  });

  it("hiện đúng số lần còn lại và khi hết lượt thì nút Nghe bị khóa kèm lý do qua aria-describedby", () => {
    mockAudioMethods();
    const adapter = makeAdapter();
    const { rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot({ playsUsed: 1, playsLeft: 1 })}
        adapter={adapter}
      />,
    );
    expect(screen.getByText(tVi.playsRemaining(1, 2))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: tVi.play })).toBeEnabled();

    rerender(
      <AttemptAudioPlayer
        assetId="asset-2"
        initialState={makeSnapshot({
          playId: "play-2",
          playsUsed: 2,
          playsLeft: 0,
          positionMs: 60_000,
          state: "ENDED",
          endReason: "FINISHED",
        })}
        adapter={adapter}
      />,
    );

    const playBtn = screen.getByRole("button", { name: tVi.play });
    expect(playBtn).toBeDisabled();
    const describedBy = playBtn.getAttribute("aria-describedby");
    expect(describedBy).toBeTruthy();
    const reasonEl = document.getElementById(describedBy!);
    expect(reasonEl).not.toBeNull();
    expect(reasonEl!.textContent).toContain(tVi.exhaustedReason);
    expect(screen.getByText(tVi.playsExhaustedBadge(2))).toBeInTheDocument();
  });

  it("hỗ trợ tiếng Anh đầy đủ qua UiStringsProvider", () => {
    mockAudioMethods();
    const adapter = makeAdapter();
    render(
      <UiStringsProvider locale="en">
        <AttemptAudioPlayer
          assetId="asset-en"
          initialState={makeSnapshot({ playsUsed: 2, playsLeft: 0, state: "ENDED", endReason: "FINISHED" })}
          adapter={adapter}
        />
      </UiStringsProvider>,
    );
    expect(screen.getByText(tEn.playsExhaustedBadge(2))).toBeInTheDocument();
    const playBtn = screen.getByRole("button", { name: tEn.play });
    expect(playBtn).toBeDisabled();
    expect(screen.getByText(tEn.exhaustedReason)).toBeInTheDocument();
  });

  it("bấm Nghe hai lần liên tiếp chỉ gửi một requestId", async () => {
    mockAudioMethods();
    let resolveOpen!: (snap: AudioPlaybackSnapshot) => void;
    const adapter = makeAdapter({
      openPlay: vi.fn(
        () =>
          new Promise<AudioPlaybackSnapshot>((resolve) => {
            resolveOpen = resolve;
          }),
      ),
    });

    render(<AttemptAudioPlayer assetId="asset-1" initialState={makeSnapshot()} adapter={adapter} />);
    const playBtn = screen.getByRole("button", { name: tVi.play });
    fireEvent.click(playBtn);
    fireEvent.click(playBtn);

    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
    const requestId = (adapter.openPlay as ReturnType<typeof vi.fn>).mock.calls[0]![0];
    expect(typeof requestId).toBe("string");
    expect(requestId.length).toBeGreaterThan(0);

    await act(async () => {
      resolveOpen(makeSnapshot({ playId: "play-1", playsUsed: 1, playsLeft: 1, state: "PLAYING" }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
  });

  it("gửi lại openPlay sau khi mất phản hồi giữ nguyên requestId cũ", async () => {
    mockAudioMethods();
    const openPlay = vi
      .fn<AudioPlayAdapter["openPlay"]>()
      .mockRejectedValueOnce(new AudioAdapterError("NETWORK_ERROR", "Timeout"))
      .mockResolvedValueOnce(makeSnapshot({ playId: "play-1", playsUsed: 1, playsLeft: 1, state: "PLAYING" }));
    const adapter = makeAdapter({ openPlay });

    render(<AttemptAudioPlayer assetId="asset-1" initialState={makeSnapshot()} adapter={adapter} />);

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByText(tVi.playErrorRetry)).toBeInTheDocument());

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });

    expect(openPlay).toHaveBeenCalledTimes(2);
    expect(openPlay.mock.calls[0]![0]).toBe(openPlay.mock.calls[1]![0]);
  });
});

describe("khôi phục", () => {
  it("tải lại khi đang phát gọi resumePlay với cùng playId, không gọi openPlay, tua tới positionMs", async () => {
    const { playSpy } = mockAudioMethods();
    const adapter = makeAdapter({
      resumePlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 18_500,
          durationMs: 60_000,
          state: "PLAYING",
        }),
      ),
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot({
          playId: "play-resume-1",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 18_500,
          durationMs: 60_000,
          state: "PLAYING",
        })}
        adapter={adapter}
      />,
    );

    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledWith("play-resume-1"));
    expect(adapter.openPlay).not.toHaveBeenCalled();

    const audio = container.querySelector("audio")!;
    await waitFor(() => expect(playSpy).toHaveBeenCalled());
    expect(audio.getAttribute("src")).toContain("play-resume-1");
    expect(audio.currentTime).toBeCloseTo(18.5, 2);
  });

  it("AUDIO_PLAY_IN_PROGRESS chuyển sang resumePlay(err.playId) mà không mở lần nghe thứ hai", async () => {
    mockAudioMethods();
    const adapter = makeAdapter({
      openPlay: vi.fn().mockRejectedValueOnce(
        new AudioAdapterError("AUDIO_PLAY_IN_PROGRESS", "Play active", { playId: "play-active" }),
      ),
      resumePlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 7_000,
          state: "PLAYING",
        }),
      ),
    });

    const { container } = render(
      <AttemptAudioPlayer assetId="asset-1" initialState={makeSnapshot()} adapter={adapter} />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });

    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledWith("play-active"));
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
    const audio = container.querySelector("audio")!;
    expect(audio.currentTime).toBeCloseTo(7, 2);
  });

  it("AUDIO_SEEK_REJECTED kéo currentTime về positionMs máy chủ trả và chống tua tay / tốc độ", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockAudioMethods();
    const adapter = makeAdapter({
      reportProgress: vi.fn().mockRejectedValueOnce(
        new AudioAdapterError("AUDIO_SEEK_REJECTED", "Seek rejected", { positionMs: 12_000 }),
      ),
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot()}
        adapter={adapter}
        progressIntervalMs={10_000}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    audio.playbackRate = 2;
    fireEvent(audio, new Event("ratechange"));
    expect(audio.playbackRate).toBe(1);

    audio.currentTime = 35;
    fireEvent(audio, new Event("timeupdate"));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    await waitFor(() => expect(adapter.reportProgress).toHaveBeenCalledWith("play-1", 35_000));
    await waitFor(() => expect(audio.currentTime).toBeCloseTo(12, 2));
    expect(screen.getByText(/00:12 \/ 01:00/)).toBeInTheDocument();

    audio.currentTime = 50;
    fireEvent(audio, new Event("seeking"));
    expect(audio.currentTime).toBeCloseTo(12, 2);
  });

  it("AUDIO_PLAY_STALE dừng phát, nạp lại trạng thái và không gửi tiếp progress hay end cho playId cũ", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { pauseSpy } = mockAudioMethods();
    const reloadState = vi.fn(async () =>
      makeSnapshot({ playId: "play-1", playsUsed: 1, playsLeft: 1, positionMs: 10_000, state: "ENDED", endReason: "FINISHED" }),
    );
    const adapter = makeAdapter({
      reportProgress: vi.fn().mockRejectedValueOnce(new AudioAdapterError("AUDIO_PLAY_STALE", "Stale")),
      reloadState,
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot()}
        adapter={adapter}
        progressIntervalMs={10_000}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    audio.currentTime = 10;
    fireEvent(audio, new Event("timeupdate"));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });

    await waitFor(() => expect(reloadState).toHaveBeenCalledTimes(1));
    expect(pauseSpy).toHaveBeenCalled();
    expect(audio.getAttribute("src")).toBeNull();

    await act(async () => {
      fireEvent(audio, new Event("ended"));
      await vi.advanceTimersByTimeAsync(20_000);
    });
    expect(adapter.endPlay).not.toHaveBeenCalled();
    expect(adapter.reportProgress).toHaveBeenCalledTimes(1);
  });

  it("phản hồi progress đến muộn sau khi chuyển sang lần nghe mới không ghi đè trạng thái mới", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockAudioMethods();
    let resolveStaleProgress!: (snap: AudioPlaybackSnapshot) => void;
    const onStateChange = vi.fn();

    const reportProgress = vi
      .fn<AudioPlayAdapter["reportProgress"]>()
      .mockImplementationOnce(
        () =>
          new Promise<AudioPlaybackSnapshot>((resolve) => {
            resolveStaleProgress = resolve;
          }),
      );
    const openPlay = vi
      .fn<AudioPlayAdapter["openPlay"]>()
      .mockResolvedValueOnce(makeSnapshot({ playId: "play-1", playsUsed: 1, playsLeft: 1, state: "PLAYING" }))
      .mockResolvedValueOnce(makeSnapshot({ playId: "play-2", playsUsed: 2, playsLeft: 0, state: "PLAYING" }));

    const adapter = makeAdapter({ openPlay, reportProgress });
    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot()}
        adapter={adapter}
        progressIntervalMs={5_000}
        onStateChange={onStateChange}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    audio.currentTime = 5;
    fireEvent(audio, new Event("timeupdate"));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    expect(reportProgress).toHaveBeenCalledWith("play-1", 5_000);

    await act(async () => {
      fireEvent(audio, new Event("ended"));
    });
    await waitFor(() => expect(adapter.endPlay).toHaveBeenCalledWith("play-1"));

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(adapter.openPlay).toHaveBeenCalledTimes(2));

    const callsBeforeLateResolve = onStateChange.mock.calls.length;
    await act(async () => {
      resolveStaleProgress(makeSnapshot({ playId: "play-1", playsUsed: 1, playsLeft: 1, positionMs: 5_000, state: "PLAYING" }));
    });

    expect(onStateChange.mock.calls.length).toBe(callsBeforeLateResolve);
    expect(screen.getByText(tVi.playsRemaining(0, 2))).toBeInTheDocument();
  });
});

describe("trình duyệt từ chối phát", () => {
  it("audio.play() bị từ chối 4 lần liên tiếp: hiện Phát lại, mỗi lần bấm gọi resumePlay, không gọi openPlay, playsUsed giữ nguyên 1", async () => {
    const playSpy = vi
      .spyOn(HTMLMediaElement.prototype, "play")
      .mockRejectedValueOnce(new DOMException("Autoplay blocked", "NotAllowedError"))
      .mockRejectedValueOnce(new DOMException("Autoplay blocked", "NotAllowedError"))
      .mockRejectedValueOnce(new DOMException("Autoplay blocked", "NotAllowedError"))
      .mockRejectedValueOnce(new DOMException("Autoplay blocked", "NotAllowedError"))
      .mockResolvedValueOnce(undefined);
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});

    const adapter = makeAdapter({
      resumePlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 9_000,
          state: "PLAYING",
        }),
      ),
    });

    render(
      <AttemptAudioPlayer
        assetId="asset-1"
        initialState={makeSnapshot({
          playId: "play-1",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 9_000,
          state: "PLAYING",
        })}
        adapter={adapter}
      />,
    );

    for (let click = 1; click <= 4; click += 1) {
      await waitFor(() => expect(screen.getByRole("button", { name: tVi.resume })).toBeEnabled());
      expect(screen.getByText(tVi.autoplayBlocked)).toBeInTheDocument();
      expect(screen.getByText(tVi.playsRemaining(1, 2))).toBeInTheDocument();

      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: tVi.resume }));
      });
    }

    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    expect(playSpy).toHaveBeenCalledTimes(5);
    expect(adapter.resumePlay).toHaveBeenCalledTimes(5);
    expect(adapter.openPlay).not.toHaveBeenCalled();
    expect(screen.getByText(tVi.playsRemaining(1, 2))).toBeInTheDocument();
  });
});

describe("gián đoạn dài và phần đóng", () => {
  it("quay lại sau gián đoạn dài (30+ phút) khi phần còn mở: gọi resumePlay, không gọi openPlay, phát từ positionMs đã lưu", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { playSpy } = mockAudioMethods();
    const adapter = makeAdapter({
      resumePlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 24_000,
          durationMs: 90_000,
          state: "PLAYING",
        }),
      ),
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-long"
        initialState={makeSnapshot({
          playId: "play-long",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 24_000,
          durationMs: 90_000,
          state: "PLAYING",
        })}
        adapter={adapter}
      />,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(35 * 60_000);
    });

    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledWith("play-long"));
    expect(adapter.openPlay).not.toHaveBeenCalled();
    expect(playSpy).toHaveBeenCalled();
    expect(container.querySelector("audio")!.currentTime).toBeCloseTo(24, 2);
  });

  it.each(["PART_CLOSED", "TIME_UP"] as const)(
    "nhận %s từ API: dừng phát ngay, khóa nút Nghe kèm lý do và gọi onClosed",
    async (code) => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const { pauseSpy } = mockAudioMethods();
      const onClosed = vi.fn();
      const adapter = makeAdapter({
        reportProgress: vi.fn().mockRejectedValueOnce(new AudioAdapterError(code, code)),
      });

      const { container } = render(
        <AttemptAudioPlayer
          assetId="asset-close"
          initialState={makeSnapshot()}
          adapter={adapter}
          progressIntervalMs={5_000}
          onClosed={onClosed}
        />,
      );

      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: tVi.play }));
      });
      await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

      await act(async () => {
        await vi.advanceTimersByTimeAsync(5_000);
      });

      await waitFor(() => expect(onClosed).toHaveBeenCalledWith(code));
      expect(pauseSpy).toHaveBeenCalled();
      expect(container.querySelector("audio")!.getAttribute("src")).toBeNull();

      const playBtn = screen.getByRole("button", { name: tVi.play });
      expect(playBtn).toBeDisabled();
      const describedBy = playBtn.getAttribute("aria-describedby");
      expect(describedBy).toBeTruthy();
      const expectedText = code === "PART_CLOSED" ? tVi.partClosedReason : tVi.timeUpReason;
      expect(document.getElementById(describedBy!)!.textContent).toContain(expectedText);
    },
  );

  it("closedReason hoặc stopSignal từ bên ngoài dừng phát ngay lập tức", async () => {
    const { pauseSpy, loadSpy } = mockAudioMethods();
    const adapter = makeAdapter();

    const { container, rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-stop"
        initialState={makeSnapshot()}
        adapter={adapter}
        stopSignal={0}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    expect(audio.getAttribute("src")).toBeTruthy();

    rerender(
      <AttemptAudioPlayer
        assetId="asset-stop"
        initialState={makeSnapshot()}
        adapter={adapter}
        stopSignal={1}
        closedReason="EVICTED"
      />,
    );

    expect(pauseSpy).toHaveBeenCalled();
    expect(loadSpy).toHaveBeenCalled();
    expect(audio.getAttribute("src")).toBeNull();
    const playBtn = screen.getByRole("button", { name: tVi.play });
    expect(playBtn).toBeDisabled();
    expect(screen.getByText(tVi.evictedReason)).toBeInTheDocument();
  });

  it("mất mạng khi đang nghe rồi có mạng lại: tạm dừng chờ kết nối, khi có mạng lại gọi resumePlay không tốn lượt", async () => {
    const { pauseSpy } = mockAudioMethods();
    const adapter = makeAdapter();

    const { rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-offline"
        initialState={makeSnapshot()}
        adapter={adapter}
        isOnline={true}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);

    rerender(
      <AttemptAudioPlayer
        assetId="asset-offline"
        initialState={makeSnapshot()}
        adapter={adapter}
        isOnline={false}
      />,
    );

    expect(pauseSpy).toHaveBeenCalled();
    expect(screen.getByText(tVi.offlineWaiting)).toBeInTheDocument();

    rerender(
      <AttemptAudioPlayer
        assetId="asset-offline"
        initialState={makeSnapshot()}
        adapter={adapter}
        isOnline={true}
      />,
    );

    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledWith("play-1"));
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
    expect(screen.getByText(tVi.playsRemaining(1, 2))).toBeInTheDocument();

    // Also verifies browser window offline -> online events trigger pause and resumePlay.
    await act(async () => {
      window.dispatchEvent(new Event("offline"));
    });
    expect(screen.getByText(tVi.offlineWaiting)).toBeInTheDocument();
    await act(async () => {
      window.dispatchEvent(new Event("online"));
    });
    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledTimes(2));
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
  });
});

describe("chế độ làm thử và ca biên", () => {
  it("createTrialAudioAdapter đếm tối đa 2 lần trong trình duyệt với 0 fetch, khóa lần 3, resume không tốn lượt", async () => {
    mockAudioMethods();
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const adapter = createTrialAudioAdapter({
      assetUrl: "/api/v1/admin/question-assets/trial-1",
      maxPlays: 2,
      durationMs: 15_000,
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="trial-1"
        initialState={{
          playId: null,
          playsUsed: 0,
          playsLeft: 2,
          durationMs: 15_000,
          positionMs: 0,
          state: "IDLE",
          endReason: null,
        }}
        adapter={adapter}
      />,
    );

    const audio = container.querySelector("audio")!;

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    expect(audio.getAttribute("src")).toBe("/api/v1/admin/question-assets/trial-1");
    expect(screen.getByText(tVi.playsRemaining(1, 2))).toBeInTheDocument();

    await act(async () => {
      fireEvent(audio, new Event("ended"));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.play })).toBeEnabled());

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    expect(screen.getByText(tVi.playsRemaining(0, 2))).toBeInTheDocument();

    await act(async () => {
      fireEvent(audio, new Event("ended"));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.play })).toBeDisabled());
    expect(screen.getByText(tVi.playsExhaustedBadge(2))).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();

    const secondAdapter = createTrialAudioAdapter({ assetUrl: "/trial.mp3", maxPlays: 2, durationMs: 10_000 });
    const first = await secondAdapter.openPlay("req-1");
    expect(await secondAdapter.openPlay("req-1")).toEqual(first);
    const resumed = await secondAdapter.resumePlay(first.playId!);
    expect(resumed.playsUsed).toBe(1);
    await secondAdapter.endPlay(first.playId!);
    const second = await secondAdapter.openPlay("req-2");
    await secondAdapter.endPlay(second.playId!);
    await expect(secondAdapter.openPlay("req-3")).rejects.toMatchObject({ code: "AUDIO_PLAYS_EXHAUSTED" });
  });

  it("audio ngắn (< 10s) kết thúc sạch qua endPlay trước tick progress đầu tiên", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockAudioMethods();
    const adapter = makeAdapter({
      openPlay: vi.fn(async () =>
        makeSnapshot({
          playId: "play-short",
          playsUsed: 1,
          playsLeft: 1,
          durationMs: 4_000,
          state: "PLAYING",
        }),
      ),
      endPlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          durationMs: 4_000,
          positionMs: 4_000,
          state: "ENDED",
          endReason: "FINISHED",
        }),
      ),
    });

    const { container } = render(
      <AttemptAudioPlayer
        assetId="asset-short"
        initialState={makeSnapshot({ durationMs: 4_000 })}
        adapter={adapter}
        progressIntervalMs={10_000}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
      fireEvent(container.querySelector("audio")!, new Event("ended"));
    });

    await waitFor(() => expect(adapter.endPlay).toHaveBeenCalledWith("play-short"));
    expect(adapter.endPlay).toHaveBeenCalledTimes(1);
    expect(adapter.reportProgress).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000);
    });
    expect(adapter.reportProgress).not.toHaveBeenCalled();
  });

  it("ATTEMPT_SESSION_REVOKED và UNAUTHORIZED dừng phát ngay và gọi callback tương ứng", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { pauseSpy } = mockAudioMethods();
    const onSessionRevoked = vi.fn();
    const onUnauthorized = vi.fn();

    const adapterRevoked = makeAdapter({
      reportProgress: vi.fn().mockRejectedValueOnce(new AudioAdapterError("ATTEMPT_SESSION_REVOKED", "Revoked")),
    });

    const { unmount } = render(
      <AttemptAudioPlayer
        assetId="asset-revoked"
        initialState={makeSnapshot()}
        adapter={adapterRevoked}
        progressIntervalMs={5_000}
        onSessionRevoked={onSessionRevoked}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    await waitFor(() => expect(onSessionRevoked).toHaveBeenCalledTimes(1));
    expect(pauseSpy).toHaveBeenCalled();
    unmount();

    const adapterUnauth = makeAdapter({
      openPlay: vi.fn().mockRejectedValueOnce(new AudioAdapterError("UNAUTHORIZED", "401")),
    });
    render(
      <AttemptAudioPlayer
        assetId="asset-unauth"
        initialState={makeSnapshot()}
        adapter={adapterUnauth}
        onUnauthorized={onUnauthorized}
      />,
    );
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(onUnauthorized).toHaveBeenCalledTimes(1));
  });
});

describe("hardening review-fe-r1 (FE-R1-01..06, FE-R1-11)", () => {
  it("FE-R1-01: React StrictMode tự động resumePlay ở epoch sống sót và chỉ gọi audio.play() một lần", async () => {
    const { playSpy } = mockAudioMethods();
    const adapter = makeAdapter({
      resumePlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 12_000,
          durationMs: 60_000,
          state: "PLAYING",
        }),
      ),
    });

    const { container } = render(
      <StrictMode>
        <AttemptAudioPlayer
          assetId="asset-strict"
          initialState={makeSnapshot({
            playId: "play-strict",
            playsUsed: 1,
            playsLeft: 1,
            positionMs: 12_000,
            durationMs: 60_000,
            state: "PLAYING",
          })}
          adapter={adapter}
        />
      </StrictMode>,
    );

    await waitFor(() => expect(playSpy).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());
    expect(adapter.openPlay).not.toHaveBeenCalled();
    expect(container.querySelector("audio")!.getAttribute("src")).toContain("play-strict");
  });

  it("FE-R1-01: mạng chập chờn offline -> online khi resumePlay cũ còn treo vẫn khôi phục ở epoch mới", async () => {
    const { playSpy } = mockAudioMethods();
    let resolveSlowResume!: (snap: AudioPlaybackSnapshot) => void;
    const resumePlay = vi
      .fn<AudioPlayAdapter["resumePlay"]>()
      .mockImplementationOnce(
        () =>
          new Promise<AudioPlaybackSnapshot>((resolve) => {
            resolveSlowResume = resolve;
          }),
      )
      .mockImplementationOnce(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 14_000,
          state: "PLAYING",
        }),
      );
    const adapter = makeAdapter({ resumePlay });

    const { rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-flap"
        initialState={makeSnapshot({
          playId: "play-flap",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 14_000,
          state: "PLAYING",
        })}
        adapter={adapter}
        isOnline={true}
      />,
    );

    await waitFor(() => expect(resumePlay).toHaveBeenCalledTimes(1));

    rerender(
      <AttemptAudioPlayer
        assetId="asset-flap"
        initialState={makeSnapshot({
          playId: "play-flap",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 14_000,
          state: "PLAYING",
        })}
        adapter={adapter}
        isOnline={false}
      />,
    );

    rerender(
      <AttemptAudioPlayer
        assetId="asset-flap"
        initialState={makeSnapshot({
          playId: "play-flap",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 14_000,
          state: "PLAYING",
        })}
        adapter={adapter}
        isOnline={true}
      />,
    );

    await waitFor(() => expect(resumePlay).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(playSpy).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    // Resolving the stale epoch 1 promise later must NOT clobber active playback or call play() again.
    await act(async () => {
      resolveSlowResume(
        makeSnapshot({
          playId: "play-flap",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 14_000,
          state: "PLAYING",
        }),
      );
    });

    expect(playSpy).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled();
  });

  it("FE-R1-02: sự kiện error trên <audio> chuyển sang Phát lại, dừng progress timer và gọi resumePlay không gọi openPlay; phân biệt NotSupportedError với NotAllowedError", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { playSpy, loadSpy } = mockAudioMethods();
    const adapter = makeAdapter();

    const { container, unmount } = render(
      <AttemptAudioPlayer
        assetId="asset-media-err"
        initialState={makeSnapshot()}
        adapter={adapter}
        progressIntervalMs={5_000}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    Object.defineProperty(audio, "error", { value: { code: 4 }, configurable: true });
    await act(async () => {
      fireEvent(audio, new Event("error"));
    });

    expect(screen.getByRole("button", { name: tVi.resume })).toBeEnabled();
    expect(screen.getByText(tVi.playErrorRetry)).toBeInTheDocument();

    // Progress timer stopped on media error.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(adapter.reportProgress).not.toHaveBeenCalled();

    // Clicking Phát lại reloads the errored <audio> (FE-R2-01) before play() without consuming another play.
    loadSpy.mockClear();
    playSpy.mockClear();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.resume }));
    });
    await waitFor(() => expect(adapter.resumePlay).toHaveBeenCalledWith("play-1"));
    await waitFor(() => expect(playSpy).toHaveBeenCalledTimes(1));
    expect(loadSpy).toHaveBeenCalledTimes(1);
    expect(loadSpy.mock.invocationCallOrder[0]).toBeLessThan(playSpy.mock.invocationCallOrder[0]!);
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
    unmount();

    // NotSupportedError on audio.play() must show playErrorRetry, NOT autoplayBlocked.
    mockAudioMethods({
      playImpl: async () => {
        throw new DOMException("unsupported source", "NotSupportedError");
      },
    });
    const secondAdapter = makeAdapter();
    render(
      <AttemptAudioPlayer
        assetId="asset-unsupported"
        initialState={makeSnapshot()}
        adapter={secondAdapter}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });

    await waitFor(() => expect(screen.getByRole("button", { name: tVi.resume })).toBeEnabled());
    expect(screen.getByText(tVi.playErrorRetry)).toBeInTheDocument();
    expect(screen.queryByText(tVi.autoplayBlocked)).not.toBeInTheDocument();
  });

  it("FE-R2-01: networkState === 3 khi resume cùng URL gọi load() trước play(); NotAllowedError thường không gọi load() thừa", async () => {
    const { playSpy, loadSpy } = mockAudioMethods();
    const adapter = makeAdapter();

    const view1 = render(
      <AttemptAudioPlayer
        assetId="asset-network-no-source"
        initialState={makeSnapshot()}
        adapter={adapter}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = view1.container.querySelector("audio")!;
    Object.defineProperty(audio, "error", { value: null, configurable: true });
    Object.defineProperty(audio, "networkState", { value: 3, configurable: true });
    await act(async () => {
      fireEvent(audio, new Event("error"));
    });

    loadSpy.mockClear();
    playSpy.mockClear();
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.resume }));
    });
    await waitFor(() => expect(playSpy).toHaveBeenCalledTimes(1));
    expect(loadSpy).toHaveBeenCalledTimes(1);
    expect(loadSpy.mock.invocationCallOrder[0]).toBeLessThan(playSpy.mock.invocationCallOrder[0]!);
    expect(adapter.openPlay).toHaveBeenCalledTimes(1);
    view1.unmount();

    // Normal NotAllowedError retry with error === null & networkState !== 3 must NOT call load() redundantly.
    const { playSpy: retryPlaySpy, loadSpy: retryLoadSpy } = mockAudioMethods({
      playImpl: vi
        .fn<() => Promise<void>>()
        .mockRejectedValueOnce(new DOMException("Autoplay blocked", "NotAllowedError"))
        .mockResolvedValueOnce(undefined),
    });
    const retryAdapter = makeAdapter();
    const view2 = render(
      <AttemptAudioPlayer
        assetId="asset-autoplay-no-reload"
        initialState={makeSnapshot()}
        adapter={retryAdapter}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.resume })).toBeEnabled());

    const retryAudio = view2.container.querySelector("audio")!;
    Object.defineProperty(retryAudio, "error", { value: null, configurable: true });
    Object.defineProperty(retryAudio, "networkState", { value: 2, configurable: true });
    retryLoadSpy.mockClear();
    retryPlaySpy.mockClear();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.resume }));
    });
    await waitFor(() => expect(retryPlaySpy).toHaveBeenCalledTimes(1));
    expect(retryLoadSpy).not.toHaveBeenCalled();
    expect(retryAdapter.openPlay).toHaveBeenCalledTimes(1);
    view2.unmount();
  });

  it("FE-R1-03: bỏ qua initialState cũ (IDLE hoặc positionMs lùi) khi đang phát", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { pauseSpy } = mockAudioMethods();
    const adapter = makeAdapter();

    const { container, rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-mono"
        initialState={makeSnapshot()}
        adapter={adapter}
        progressIntervalMs={5_000}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    const audio = container.querySelector("audio")!;
    audio.currentTime = 10;
    fireEvent(audio, new Event("timeupdate"));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    await waitFor(() => expect(adapter.reportProgress).toHaveBeenCalledWith("play-1", 10_000));

    audio.currentTime = 18;
    fireEvent(audio, new Event("timeupdate"));
    expect(screen.getByText(/00:18 \/ 01:00/)).toBeInTheDocument();

    // Stale IDLE snapshot must NOT interrupt playback or reset elapsedMs.
    rerender(
      <AttemptAudioPlayer
        assetId="asset-mono"
        initialState={makeSnapshot({ playId: null, playsUsed: 0, playsLeft: 2, positionMs: 0, state: "IDLE" })}
        adapter={adapter}
        progressIntervalMs={5_000}
      />,
    );
    expect(pauseSpy).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled();
    expect(screen.getByText(/00:18 \/ 01:00/)).toBeInTheDocument();

    // Regressed positionMs for the same playId must NOT jump elapsedMs backwards.
    rerender(
      <AttemptAudioPlayer
        assetId="asset-mono"
        initialState={makeSnapshot({
          playId: "play-1",
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 4_000,
          state: "PLAYING",
        })}
        adapter={adapter}
        progressIntervalMs={5_000}
      />,
    );
    expect(pauseSpy).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled();
    expect(screen.getByText(/00:18 \/ 01:00/)).toBeInTheDocument();
  });

  it("FE-R1-05: endPlay trả endReason CLOSED khóa trình phát + gọi onClosed; reportProgress trả ENDED dừng audio ngay", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { pauseSpy } = mockAudioMethods();
    const onClosed1 = vi.fn();
    const closedEndAdapter = makeAdapter({
      endPlay: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 60_000,
          state: "ENDED",
          endReason: "CLOSED",
        }),
      ),
    });

    const view1 = render(
      <AttemptAudioPlayer
        assetId="asset-end-closed"
        initialState={makeSnapshot()}
        adapter={closedEndAdapter}
        onClosed={onClosed1}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    pauseSpy.mockClear();
    await act(async () => {
      fireEvent(view1.container.querySelector("audio")!, new Event("ended"));
    });

    await waitFor(() => expect(onClosed1).toHaveBeenCalledWith("PART_CLOSED"));
    expect(pauseSpy).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: tVi.play })).toBeDisabled();
    expect(screen.getByText(tVi.partClosedReason)).toBeInTheDocument();
    view1.unmount();

    const onClosed2 = vi.fn();
    const endedProgressAdapter = makeAdapter({
      reportProgress: vi.fn(async (playId: string) =>
        makeSnapshot({
          playId,
          playsUsed: 1,
          playsLeft: 1,
          positionMs: 60_000,
          state: "ENDED",
          endReason: "FINISHED",
        }),
      ),
    });

    const view2 = render(
      <AttemptAudioPlayer
        assetId="asset-prog-ended"
        initialState={makeSnapshot()}
        adapter={endedProgressAdapter}
        progressIntervalMs={5_000}
        onClosed={onClosed2}
      />,
    );

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: tVi.play }));
    });
    await waitFor(() => expect(screen.getByRole("button", { name: tVi.playing })).toBeDisabled());

    pauseSpy.mockClear();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });

    await waitFor(() => expect(pauseSpy).toHaveBeenCalled());
    expect(view2.container.querySelector("audio")!.getAttribute("src")).toBeNull();
    expect(screen.getByRole("button", { name: tVi.play })).toBeEnabled();
    expect(onClosed2).not.toHaveBeenCalled();
  });

  it("FE-R1-06 & FE-R1-11: fileName xuống tối đa 2 dòng không truncate, và initializing ẩn banner tạm khóa trong lúc claim session", () => {
    mockAudioMethods();
    const adapter = makeAdapter();
    const { rerender } = render(
      <AttemptAudioPlayer
        assetId="asset-ui"
        fileName="very-long-listening-file-name-for-hsca-audio-exam.mp3"
        initialState={makeSnapshot()}
        adapter={adapter}
        disabled={true}
        initializing={true}
      />,
    );

    const fileEl = screen.getByText("very-long-listening-file-name-for-hsca-audio-exam.mp3");
    expect(fileEl).toHaveClass("line-clamp-2", "wrap-anywhere");
    expect(fileEl).not.toHaveClass("truncate");

    expect(screen.getByRole("button", { name: tVi.play })).toBeDisabled();
    expect(screen.queryByText(tVi.disabledReason)).not.toBeInTheDocument();

    rerender(
      <AttemptAudioPlayer
        assetId="asset-ui"
        fileName="very-long-listening-file-name-for-hsca-audio-exam.mp3"
        initialState={makeSnapshot()}
        adapter={adapter}
        disabled={true}
        initializing={false}
      />,
    );
    expect(screen.getByText(tVi.disabledReason)).toBeInTheDocument();
  });
});
