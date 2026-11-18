"use client";

import { Button, cn, focusRing, IconButton } from "@thi360/ui";
import { Pause, Play, Volume2, VolumeX } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import type { PreviewAttachment } from "../review-types";

/** Nguồn phát: API tải tài nguyên có sẵn (quyền `EXAM_VIEW` đã đủ, spec 6). Cùng gốc với trang nên cookie phiên đi theo. */
const ASSET_PATH = "/api/v1/admin/question-assets";
const SEEK_STEP_SECONDS = 5;
/** Sau khi tua, vị trí thật lệch quá mức này so với đích thì coi là tệp không tua được. */
const SEEK_TOLERANCE_SECONDS = 2;

/** Chỉ các tệp MIME `audio/*`; tệp đính kèm khác (PDF, ảnh) không có trình phát. */
export function audioAttachments(attachments: readonly PreviewAttachment[] | undefined): PreviewAttachment[] {
  return (attachments ?? []).filter((attachment) => attachment.mime.toLowerCase().startsWith("audio/"));
}

/** Mỗi lần chỉ một trình phát chạy: phát tệp mới thì tệp đang phát tạm dừng. */
let playing: HTMLAudioElement | null = null;

function claimPlayback(element: HTMLAudioElement): void {
  if (playing !== null && playing !== element) playing.pause();
  playing = element;
}

function releasePlayback(element: HTMLAudioElement): void {
  if (playing === element) playing = null;
}

function clock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, "0")}`;
}

function parts(seconds: number): { minutes: number; seconds: number } {
  const whole = Math.max(0, Math.floor(seconds));
  return { minutes: Math.floor(whole / 60), seconds: whole % 60 };
}

type Status = "loading" | "ready" | "error";

/**
 * Trình phát audio của đề thử (bản vẽ `SinhThuAudio2B`, R-143): nút riêng thay cho điều khiển gốc của trình duyệt vì
 * chữ của điều khiển gốc đổi theo ngôn ngữ trình duyệt. Mọi nút dùng được bằng bàn phím với vòng focus của kit; thanh vị
 * trí là `input[type=range]` nên mũi tên, Home, End do trình duyệt xử lý. `name` là câu hoặc bộ câu mà tệp thuộc về.
 */
export function PreviewAudioPlayer({
  attachment,
  name,
  position,
}: {
  attachment: PreviewAttachment;
  name: string;
  /** Chùm có nhiều tệp: số thứ tự và tổng, hiện "Tệp 1/2". */
  position?: { index: number; total: number };
}) {
  const t = useTranslations("exams.preview.audio");
  const audio = useRef<HTMLAudioElement>(null);
  const seekTarget = useRef<number | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [isPlaying, setIsPlaying] = useState(false);
  const [muted, setMuted] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(0);
  const [canSeek, setCanSeek] = useState(true);
  const [live, setLive] = useState("");

  useEffect(() => {
    const element = audio.current;
    return () => {
      if (element) {
        element.pause();
        releasePlayback(element);
      }
    };
  }, []);

  const readMetadata = (element: HTMLAudioElement) => {
    const known = Number.isFinite(element.duration) && element.duration > 0;
    if (known) setDuration(element.duration);
    setCanSeek(element.seekable.length > 0);
    setStatus((value) => (value === "error" ? value : "ready"));
  };

  const toggle = () => {
    const element = audio.current;
    if (!element) return;
    if (element.paused) {
      // `play()` bị `pause()` ngắt báo `AbortError`: đó không phải lỗi tệp. Lỗi tệp tới qua sự kiện `error`.
      void Promise.resolve(element.play()).catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "NotSupportedError") setStatus("error");
      });
    } else {
      element.pause();
    }
  };

  const seek = (next: number) => {
    const element = audio.current;
    if (!element || !canSeek) return;
    const target = Math.max(0, Math.min(duration, next));
    seekTarget.current = target;
    element.currentTime = target;
    setCurrent(target);
  };

  const retry = () => {
    setStatus("loading");
    setCanSeek(true);
    audio.current?.load();
  };

  const group = t("group", { name });
  const spoken = t("spoken", {
    currentMinutes: parts(current).minutes,
    currentSeconds: parts(current).seconds,
    totalMinutes: parts(duration).minutes,
    totalSeconds: parts(duration).seconds,
  });

  return (
    <div role="group" aria-label={group} aria-busy={status === "loading"} className="flex flex-col gap-2">
      <audio
        ref={audio}
        hidden
        preload="metadata"
        src={`${ASSET_PATH}/${encodeURIComponent(attachment.id)}`}
        onLoadedMetadata={(event) => readMetadata(event.currentTarget)}
        onDurationChange={(event) => readMetadata(event.currentTarget)}
        onCanPlay={(event) => readMetadata(event.currentTarget)}
        onTimeUpdate={(event) => setCurrent(event.currentTarget.currentTime)}
        onSeeked={(event) => {
          const target = seekTarget.current;
          seekTarget.current = null;
          if (target !== null && Math.abs(event.currentTarget.currentTime - target) > SEEK_TOLERANCE_SECONDS) {
            setCanSeek(false);
            setCurrent(event.currentTarget.currentTime);
          }
        }}
        onPlay={(event) => {
          claimPlayback(event.currentTarget);
          setIsPlaying(true);
          setLive(t("playing", { name }));
        }}
        onPause={(event) => {
          releasePlayback(event.currentTarget);
          setIsPlaying(false);
          setLive(t("paused", { name }));
        }}
        onEnded={(event) => {
          releasePlayback(event.currentTarget);
          setIsPlaying(false);
        }}
        onVolumeChange={(event) => setMuted(event.currentTarget.muted)}
        onError={() => {
          setIsPlaying(false);
          setStatus("error");
        }}
      />
      <span role="status" className="sr-only">
        {live}
      </span>
      {status === "error" ? (
        <div role="alert" className="flex flex-wrap items-center gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2.5 text-body text-text-secondary">
          <span>
            <strong className="font-semibold text-danger">{t("errorTitle", { file: attachment.name })}</strong> {t("errorNote")}
          </span>
          <Button variant="secondary" size="sm" onClick={retry}>
            {t("retry")}
          </Button>
        </div>
      ) : null}
      {status !== "error" && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-border bg-subtle px-3 py-2.5">
          <IconButton
            size="lg"
            aria-label={isPlaying ? t("pause", { name }) : t("play", { name })}
            icon={isPlaying ? <Pause /> : <Play />}
            onClick={toggle}
            className="rounded-full bg-primary-fill text-on-primary hover:bg-primary-fill-hover"
          />
          <Button variant="secondary" size="lg" disabled={!canSeek} onClick={() => seek(current - SEEK_STEP_SECONDS)}>
            {t("back")}
          </Button>
          <Button variant="secondary" size="lg" disabled={!canSeek} onClick={() => seek(current + SEEK_STEP_SECONDS)}>
            {t("forward")}
          </Button>
          <input
            type="range"
            min={0}
            max={Math.max(duration, 1)}
            step={SEEK_STEP_SECONDS}
            value={Math.min(current, Math.max(duration, 1))}
            disabled={!canSeek || duration <= 0}
            aria-label={t("position")}
            aria-valuetext={spoken}
            onChange={(event) => seek(Number(event.target.value))}
            className={cn("h-11 min-w-36 flex-[1_1_12rem] cursor-pointer accent-primary-600 disabled:cursor-not-allowed disabled:opacity-60", focusRing)}
          />
          <span className="shrink-0 text-body text-text-secondary tabular-nums">{`${clock(current)} / ${clock(duration)}`}</span>
          <IconButton
            size="lg"
            aria-label={muted ? t("unmute", { name }) : t("mute", { name })}
            icon={muted ? <VolumeX /> : <Volume2 />}
            onClick={() => {
              if (audio.current) audio.current.muted = !audio.current.muted;
            }}
            className="border border-border bg-surface"
          />
          <span className="basis-full text-caption text-text-muted wrap-anywhere">
            {position ? `${t("fileOf", { index: position.index, total: position.total })} · ` : ""}
            {attachment.name}
          </span>
          {status === "loading" && <span className="basis-full text-caption text-text-muted">{t("loading")}</span>}
          {!canSeek && status !== "loading" && <span className="basis-full text-caption text-text-muted">{t("noSeek")}</span>}
        </div>
      )}
    </div>
  );
}

/** Các trình phát của một câu hoặc một bộ: mỗi tệp `audio/*` một trình phát xếp dọc. Không có tệp audio thì không hiện gì. */
export function PreviewAudioList({ attachments, name }: { attachments: readonly PreviewAttachment[] | undefined; name: string }) {
  const files = audioAttachments(attachments);
  if (files.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      {files.map((file, index) => (
        <PreviewAudioPlayer
          key={file.id}
          attachment={file}
          name={name}
          position={files.length > 1 ? { index: index + 1, total: files.length } : undefined}
        />
      ))}
    </div>
  );
}
