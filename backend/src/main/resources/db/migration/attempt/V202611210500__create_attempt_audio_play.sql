-- AT-6 (UNI-2727): server-side MP3 duration cache and per-attempt audio play tracking (BS §17.2, §17.4).
-- Additive on existing AT-1..AT-4 data; questionbank.question_asset is immutable per asset_id.

CREATE TABLE attempt.audio_asset_duration (
    asset_id uuid PRIMARY KEY,
    duration_ms bigint NOT NULL CHECK (duration_ms > 0),
    computed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE attempt.attempt_audio_play (
    id uuid PRIMARY KEY,
    attempt_id uuid NOT NULL REFERENCES attempt.attempt (id) ON DELETE CASCADE,
    part_id uuid NOT NULL REFERENCES attempt.attempt_part (id) ON DELETE CASCADE,
    asset_id uuid NOT NULL,
    duration_ms bigint NOT NULL CHECK (duration_ms > 0),
    play_count integer NOT NULL DEFAULT 0 CHECK (play_count >= 0 AND play_count <= 2),
    play_id uuid,
    last_request_id text,
    state text NOT NULL CHECK (state IN ('IDLE', 'PLAYING', 'ENDED')),
    end_reason text CHECK (end_reason IS NULL OR end_reason IN ('FINISHED', 'CLOSED')),
    position_ms bigint NOT NULL DEFAULT 0 CHECK (position_ms >= 0),
    anchor_at timestamptz,
    anchor_position_ms bigint NOT NULL DEFAULT 0 CHECK (anchor_position_ms >= 0),
    segment_excess_ms bigint NOT NULL DEFAULT 0 CHECK (segment_excess_ms >= 0),
    tolerance_used_ms bigint NOT NULL DEFAULT 0 CHECK (tolerance_used_ms >= 0),
    lease_until timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempt_audio_play_attempt_asset UNIQUE (attempt_id, asset_id),
    CONSTRAINT chk_attempt_audio_play_position_le_duration CHECK (position_ms <= duration_ms),
    CONSTRAINT chk_attempt_audio_play_anchor_le_duration CHECK (anchor_position_ms <= duration_ms)
);

CREATE INDEX idx_attempt_audio_play_attempt_part
    ON attempt.attempt_audio_play (attempt_id, part_id);
