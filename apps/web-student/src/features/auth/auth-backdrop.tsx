import { cn } from "@thi360/ui";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

const DEFAULT_STAGE_CLASS =
  "relative isolate flex min-h-screen flex-col items-center gap-4 overflow-clip bg-page p-4 sm:p-6 md:justify-center";

export const AUTH_BRAND_PANEL_CLASS =
  "relative isolate flex min-w-0 flex-1 flex-col gap-6 overflow-hidden bg-primary-50 p-6 pb-24 sm:p-10 sm:pb-28 md:border-r md:border-border/60 md:p-12 md:pb-28";

/**
 * Dynamic studio lighting variables derived 100% from semantic tokens (`--color-primary-*`, `--color-accent-500`,
 * `--color-surface`, `--color-page`, `--color-switch-thumb`, `--color-border-strong`) with `color-mix(in oklch, ...)`.
 * - Zero hardcoded hex/rgb/hsl values so every system preset and `/appearance` custom palette recolors all 5 layers.
 * - Dark-mode overrides remap inverted text tokens (`primary-700/800/900`) so 3D glass, crystal facets, wireframes,
 *   mesh aura, and silk ribbons preserve true studio depth and crisp specular highlights in both Light and Dark modes.
 */
const STUDIO_PALETTE_CLASS = cn(
  "[--auth-bg-deep:color-mix(in_oklch,var(--color-primary-800)_68%,var(--color-primary-600))]",
  "[--auth-bg-core:color-mix(in_oklch,var(--color-primary-600)_62%,var(--color-primary-500))]",
  "[--auth-bg-bright:color-mix(in_oklch,var(--color-primary-500)_66%,var(--color-primary-200))]",
  "[--auth-bg-iridescent:color-mix(in_oklch,var(--color-primary-500)_46%,var(--color-accent-500)_54%)]",
  "[--auth-bg-luminous:color-mix(in_oklch,var(--color-accent-500)_54%,var(--color-primary-100)_46%)]",
  "[--auth-bg-frost:color-mix(in_oklch,var(--color-primary-100)_58%,var(--color-surface))]",
  "[--auth-bg-spec:var(--color-surface)]",
  "[--auth-bg-skylight:color-mix(in_srgb,var(--color-surface)_72%,transparent)]",
  "[--auth-bg-wire-deep:color-mix(in_oklch,var(--color-primary-700)_58%,var(--color-border-strong))]",
  "[--auth-bg-wire-mid:color-mix(in_oklch,var(--color-primary-500)_48%,var(--color-border-strong))]",
  "[--auth-bg-wire-glint:color-mix(in_oklch,var(--color-accent-500)_42%,var(--color-surface))]",
  "[--auth-bg-floor-top:color-mix(in_srgb,var(--color-surface)_76%,var(--color-page))]",
  "[--auth-bg-floor-bot:color-mix(in_oklch,var(--color-page)_84%,var(--color-primary-100)_16%)]",
  "[[data-theme=dark]_&]:[--auth-bg-deep:color-mix(in_oklch,var(--color-primary-600)_56%,var(--color-page))]",
  "[[data-theme=dark]_&]:[--auth-bg-core:color-mix(in_oklch,var(--color-primary-500)_68%,var(--color-primary-600))]",
  "[[data-theme=dark]_&]:[--auth-bg-bright:color-mix(in_oklch,var(--color-primary-500)_58%,var(--color-primary-700))]",
  "[[data-theme=dark]_&]:[--auth-bg-luminous:color-mix(in_oklch,var(--color-accent-500)_60%,var(--color-primary-700)_40%)]",
  "[[data-theme=dark]_&]:[--auth-bg-frost:color-mix(in_oklch,var(--color-primary-700)_30%,var(--color-surface))]",
  "[[data-theme=dark]_&]:[--auth-bg-spec:color-mix(in_srgb,var(--color-switch-thumb)_84%,var(--color-primary-900))]",
  "[[data-theme=dark]_&]:[--auth-bg-skylight:color-mix(in_oklch,var(--color-primary-500)_18%,transparent)]",
  "[[data-theme=dark]_&]:[--auth-bg-wire-deep:color-mix(in_oklch,var(--color-primary-600)_68%,var(--color-border-strong))]",
  "[[data-theme=dark]_&]:[--auth-bg-wire-mid:color-mix(in_oklch,var(--color-primary-700)_52%,var(--color-border-strong))]",
  "[[data-theme=dark]_&]:[--auth-bg-wire-glint:color-mix(in_oklch,var(--color-accent-500)_52%,var(--color-switch-thumb))]",
  "[[data-theme=dark]_&]:[--auth-bg-floor-top:color-mix(in_srgb,var(--color-surface)_72%,var(--color-page))]",
  "[[data-theme=dark]_&]:[--auth-bg-floor-bot:color-mix(in_oklch,var(--color-page)_76%,var(--color-primary-50)_24%)]",
);

const STUDIO_SKYLIGHT_STYLE = {
  backgroundImage: [
    "radial-gradient(62% 50% at 50% 22%, var(--auth-bg-skylight), transparent 76%)",
    "radial-gradient(48% 40% at 44% 34%, color-mix(in oklch, var(--color-primary-500) 14%, transparent), transparent 74%)",
    "radial-gradient(48% 42% at 58% 66%, color-mix(in oklch, var(--color-accent-500) 12%, transparent), transparent 74%)",
  ].join(", "),
} as const;

const STUDIO_FLOOR_SURFACE_STYLE = {
  backgroundImage: "linear-gradient(180deg, var(--auth-bg-floor-top) 0%, var(--auth-bg-floor-bot) 100%)",
} as const;

const STUDIO_HORIZON_SHADOW_STYLE = {
  backgroundImage:
    "linear-gradient(180deg, transparent 0%, color-mix(in srgb, var(--auth-bg-deep) 9%, transparent) 100%)",
} as const;

const STUDIO_HORIZON_LINE_STYLE = {
  backgroundImage:
    "linear-gradient(90deg, transparent 2%, color-mix(in srgb, var(--auth-bg-spec) 75%, var(--color-border-primary)) 20%, var(--auth-bg-spec) 50%, color-mix(in srgb, var(--auth-bg-spec) 75%, var(--color-border-primary)) 80%, transparent 98%)",
} as const;

const FLOOR_AURA_REFLECTION_STYLE = {
  backgroundImage: [
    "radial-gradient(46% 80% at 41% 18%, color-mix(in oklch, var(--auth-bg-core) 62%, transparent), transparent 74%)",
    "radial-gradient(50% 84% at 59% 16%, color-mix(in oklch, var(--auth-bg-luminous) 68%, transparent), transparent 76%)",
  ].join(", "),
} as const;

const FLOOR_SIDE_REFLECTION_LEFT_STYLE = {
  backgroundImage:
    "radial-gradient(60% 65% at 32% 18%, color-mix(in oklch, var(--auth-bg-bright) 34%, transparent), transparent 78%)",
} as const;

const FLOOR_SIDE_REFLECTION_RIGHT_STYLE = {
  backgroundImage:
    "radial-gradient(62% 68% at 68% 18%, color-mix(in oklch, var(--auth-bg-iridescent) 36%, transparent), transparent 78%)",
} as const;

const BRAND_PANEL_WASH_STYLE = {
  backgroundImage:
    "linear-gradient(152deg, color-mix(in srgb, var(--color-primary-50) 82%, var(--color-surface)) 0%, var(--color-primary-50) 54%, var(--color-primary-100) 100%)",
} as const;

const BRAND_SILK_MASK_STYLE = {
  WebkitMaskImage: "linear-gradient(180deg, transparent 0%, black 26%)",
  maskImage: "linear-gradient(180deg, transparent 0%, black 26%)",
} as const;

/**
 * Auth page 3D studio backdrop (Option 2 — 3D Gyroscopic Orbits, Sculpted Glass & Faceted Crystals, Dual-Tone Mesh Aura,
 * and Studio Horizon Floor, UNI-2775).
 * - Uses exclusively semantic CSS tokens so every preset and custom palette in `/appearance` dynamically recolors
 *   all 5 layers across Light and Dark modes.
 * - Contained inside an `aria-hidden` `-z-10` layer with `overflow-hidden` so `<main>` keeps solid `bg-page` for
 *   deterministic WCAG contrast checks and never causes horizontal overflow at 360px-1440px.
 */
export function AuthBackdrop({ className }: { className?: string } = {}) {
  return (
    <div
      aria-hidden="true"
      data-testid="auth-backdrop"
      className={cn(
        "pointer-events-none inset-0 -z-10 select-none overflow-clip",
        className ?? "absolute",
        STUDIO_PALETTE_CLASS,
      )}
    >
      <div className="absolute inset-0" style={STUDIO_SKYLIGHT_STYLE} />

      {/* Layer A: Center-Back Saturated Dual-Tone Mesh Aura directly behind the auth card */}
      <svg
        data-layer="center-mesh-aura"
        viewBox="0 0 1200 900"
        fill="none"
        preserveAspectRatio="xMidYMid slice"
        className="absolute left-1/2 top-1/2 h-full w-[min(1280px,100%)] -translate-x-1/2 -translate-y-1/2 opacity-95 [[data-theme=dark]_&]:opacity-80"
      >
        <defs>
          <filter id="auth-bg-velvet-blur" x="-25%" y="-25%" width="150%" height="150%">
            <feGaussianBlur stdDeviation="26" />
          </filter>
          <filter id="auth-bg-halo-blur" x="-35%" y="-35%" width="170%" height="170%">
            <feGaussianBlur stdDeviation="54" />
          </filter>

          <radialGradient id="auth-bg-aura-upper-left" cx="46%" cy="44%" r="56%">
            <stop offset="0%" stopColor="var(--auth-bg-deep)" stopOpacity="0.94" />
            <stop offset="48%" stopColor="var(--auth-bg-core)" stopOpacity="0.84" />
            <stop offset="80%" stopColor="var(--auth-bg-bright)" stopOpacity="0.38" />
            <stop offset="100%" stopColor="var(--auth-bg-bright)" stopOpacity="0" />
          </radialGradient>

          <radialGradient id="auth-bg-aura-upper-right" cx="52%" cy="46%" r="58%">
            <stop offset="0%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.95" />
            <stop offset="46%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.74" />
            <stop offset="80%" stopColor="var(--auth-bg-bright)" stopOpacity="0.36" />
            <stop offset="100%" stopColor="var(--color-primary-100)" stopOpacity="0" />
          </radialGradient>

          <radialGradient id="auth-bg-aura-lower-left" cx="48%" cy="54%" r="56%">
            <stop offset="0%" stopColor="var(--auth-bg-deep)" stopOpacity="0.92" />
            <stop offset="54%" stopColor="var(--auth-bg-core)" stopOpacity="0.74" />
            <stop offset="100%" stopColor="var(--auth-bg-bright)" stopOpacity="0" />
          </radialGradient>

          <radialGradient id="auth-bg-aura-lower-right" cx="52%" cy="50%" r="58%">
            <stop offset="0%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.94" />
            <stop offset="50%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.74" />
            <stop offset="86%" stopColor="var(--auth-bg-bright)" stopOpacity="0.32" />
            <stop offset="100%" stopColor="var(--auth-bg-bright)" stopOpacity="0" />
          </radialGradient>

          <linearGradient id="auth-bg-aura-bridge" x1="340" y1="220" x2="860" y2="730" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-deep)" stopOpacity="0.58" />
            <stop offset="48%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.52" />
            <stop offset="100%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.62" />
          </linearGradient>
        </defs>

        <g filter="url(#auth-bg-halo-blur)">
          <ellipse cx="590" cy="468" rx="345" ry="285" fill="url(#auth-bg-aura-bridge)" />
        </g>

        <g filter="url(#auth-bg-velvet-blur)">
          {/* Upper-left deep primary dome rising above the card */}
          <ellipse
            cx="484"
            cy="232"
            rx="188"
            ry="154"
            transform="rotate(-15 484 232)"
            fill="url(#auth-bg-aura-upper-left)"
          />
          {/* Upper-right luminous accent-primary dome */}
          <ellipse
            cx="676"
            cy="248"
            rx="216"
            ry="174"
            transform="rotate(16 676 248)"
            fill="url(#auth-bg-aura-upper-right)"
          />
          {/* Lower-left rich primary plume emerging below the card onto the studio horizon */}
          <ellipse
            cx="504"
            cy="748"
            rx="162"
            ry="106"
            transform="rotate(9 504 748)"
            fill="url(#auth-bg-aura-lower-left)"
          />
          {/* Lower-right iridescent plume emerging below the right half of the card */}
          <ellipse
            cx="666"
            cy="742"
            rx="188"
            ry="114"
            transform="rotate(-11 666 742)"
            fill="url(#auth-bg-aura-lower-right)"
          />
        </g>
      </svg>

      {/* Layer B: Studio Horizon Floor Plane & Mirrored Reflections (bottom ~18% of viewport) */}
      <div
        data-layer="studio-floor"
        className="absolute inset-x-0 bottom-0 h-[18vh] max-h-[185px] min-h-[115px]"
      >
        <div className="absolute inset-x-0 -top-6 h-6" style={STUDIO_HORIZON_SHADOW_STYLE} />
        <div className="absolute inset-0" style={STUDIO_FLOOR_SURFACE_STYLE} />
        <div className="absolute inset-x-0 top-0 h-px opacity-90" style={STUDIO_HORIZON_LINE_STYLE} />
        <div
          className="absolute left-1/2 -top-2 h-28 w-[min(680px,86vw)] -translate-x-1/2 opacity-90 blur-2xl [[data-theme=dark]_&]:opacity-65"
          style={FLOOR_AURA_REFLECTION_STYLE}
        />
        <div
          className="absolute left-0 top-0 h-20 w-[min(280px,34vw)] opacity-70 blur-xl"
          style={FLOOR_SIDE_REFLECTION_LEFT_STYLE}
        />
        <div
          className="absolute right-0 top-0 h-20 w-[min(300px,36vw)] opacity-75 blur-xl"
          style={FLOOR_SIDE_REFLECTION_RIGHT_STYLE}
        />
      </div>

      {/* Layer C & D (Left Flank): 3D Tilted Gyroscopic Orbital Wireframes + Frosted Glass Pebbles & Disc */}
      <svg
        data-layer="orbital-sculpture-left"
        viewBox="0 0 300 820"
        fill="none"
        className="absolute left-0 top-[49%] h-[min(780px,90vh)] w-[min(145px,36vw)] -translate-y-1/2 opacity-45 sm:w-[min(210px,30vw)] sm:opacity-75 md:opacity-95 lg:w-[min(255px,19.5vw)] xl:w-[min(288px,20vw)]"
      >
        <defs>
          <filter id="auth-bg-shadow-l" x="-25%" y="-25%" width="160%" height="160%">
            <feDropShadow dx="4" dy="12" stdDeviation="10" floodColor="var(--auth-bg-deep)" floodOpacity="0.22" />
          </filter>
          <filter id="auth-bg-contact-l" x="-40%" y="-80%" width="180%" height="260%">
            <feGaussianBlur stdDeviation="7" />
          </filter>

          <linearGradient id="auth-bg-wire-l1" x1="20" y1="60" x2="265" y2="760" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.55" />
            <stop offset="28%" stopColor="var(--auth-bg-spec)" stopOpacity="0.95" />
            <stop offset="56%" stopColor="var(--auth-bg-wire-deep)" stopOpacity="0.72" />
            <stop offset="84%" stopColor="var(--auth-bg-wire-glint)" stopOpacity="0.78" />
            <stop offset="100%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.45" />
          </linearGradient>

          <linearGradient id="auth-bg-wire-l2" x1="0" y1="110" x2="235" y2="730" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-wire-deep)" stopOpacity="0.5" />
            <stop offset="38%" stopColor="var(--auth-bg-wire-glint)" stopOpacity="0.85" />
            <stop offset="68%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.65" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.8" />
          </linearGradient>

          <linearGradient id="auth-bg-pebble-rim-l" x1="68" y1="158" x2="188" y2="284" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.96" />
            <stop offset="48%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.82" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.86" />
          </linearGradient>

          <linearGradient id="auth-bg-pebble-face-l" x1="64" y1="166" x2="180" y2="282" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.86" />
            <stop offset="36%" stopColor="var(--auth-bg-frost)" stopOpacity="0.78" />
            <stop offset="72%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.76" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.72" />
          </linearGradient>

          <linearGradient id="auth-bg-prism-top-l" x1="-12" y1="378" x2="56" y2="424" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.9" />
            <stop offset="65%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.92" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.98" />
          </linearGradient>

          <linearGradient id="auth-bg-prism-face-l" x1="-12" y1="404" x2="42" y2="514" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-frost)" stopOpacity="0.9" />
            <stop offset="56%" stopColor="var(--auth-bg-bright)" stopOpacity="0.84" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.92" />
          </linearGradient>

          <linearGradient id="auth-bg-disc-rim-l" x1="62" y1="528" x2="176" y2="640" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.96" />
            <stop offset="45%" stopColor="var(--auth-bg-bright)" stopOpacity="0.88" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.9" />
          </linearGradient>

          <linearGradient id="auth-bg-disc-face-l" x1="60" y1="534" x2="168" y2="642" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.88" />
            <stop offset="42%" stopColor="var(--auth-bg-frost)" stopOpacity="0.86" />
            <stop offset="78%" stopColor="var(--auth-bg-bright)" stopOpacity="0.84" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.78" />
          </linearGradient>
        </defs>

        {/* Soft blurred studio floor contact shadow under left gyroscopic wireframe */}
        <ellipse
          cx="64"
          cy="754"
          rx="66"
          ry="8"
          fill="var(--auth-bg-deep)"
          opacity="0.2"
          filter="url(#auth-bg-contact-l)"
        />

        {/* Back 3D tilted gyroscopic orbits */}
        <ellipse
          cx="-18"
          cy="416"
          rx="256"
          ry="362"
          transform="rotate(-32 -18 416)"
          stroke="url(#auth-bg-wire-l1)"
          strokeWidth="1.75"
        />
        <ellipse
          cx="-18"
          cy="416"
          rx="222"
          ry="350"
          transform="rotate(-14 -18 416)"
          stroke="url(#auth-bg-wire-l2)"
          strokeWidth="1.5"
        />
        <ellipse
          cx="-18"
          cy="416"
          rx="186"
          ry="338"
          transform="rotate(11 -18 416)"
          stroke="url(#auth-bg-wire-l1)"
          strokeWidth="1.35"
          opacity="0.85"
        />

        {/* (L1) Upper-left tilted frosted-glass rounded-triangle pebble lens */}
        <g filter="url(#auth-bg-shadow-l)">
          <path
            d="M 85 166 C 110 144, 180 176, 187 219 C 193 255, 150 288, 110 274 C 69 260, 60 189, 85 166 Z"
            fill="url(#auth-bg-pebble-rim-l)"
          />
          <path
            d="M 79 171 C 104 149, 174 181, 181 224 C 187 260, 144 293, 104 279 C 63 265, 54 194, 79 171 Z"
            fill="url(#auth-bg-pebble-face-l)"
            stroke="var(--auth-bg-spec)"
            strokeWidth="1.3"
          />
          {/* Internal folded glass refraction planes */}
          <path
            d="M 82 176 C 108 164, 148 184, 174 222 L 112 276 C 78 258, 66 204, 82 176 Z"
            fill="var(--auth-bg-spec)"
            opacity="0.28"
          />
          <path
            d="M 80 175 L 128 226 L 106 278"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.9"
            opacity="0.58"
          />
          <line x1="128" y1="226" x2="180" y2="224" stroke="var(--auth-bg-spec)" strokeWidth="0.85" opacity="0.5" />
        </g>

        {/* (L2) Mid-left angled 3D glass prism pillow */}
        <g filter="url(#auth-bg-shadow-l)">
          <polygon points="-14,376 52,406 38,424 -14,400" fill="url(#auth-bg-prism-top-l)" />
          <polygon points="-14,400 38,424 10,512 -14,500" fill="url(#auth-bg-prism-face-l)" />
          <polygon points="52,406 58,419 22,522 10,512 38,424" fill="var(--auth-bg-core)" opacity="0.86" />
          <polyline
            points="-14,400 38,424 52,406"
            stroke="var(--auth-bg-spec)"
            strokeWidth="1.35"
            strokeLinejoin="round"
          />
          <line x1="38" y1="424" x2="10" y2="512" stroke="var(--auth-bg-spec)" strokeWidth="1.15" opacity="0.9" />
        </g>

        {/* (L3) Lower-left tilted circular 3D glass disc intersecting the orbits */}
        <g filter="url(#auth-bg-shadow-l)">
          <ellipse
            cx="121"
            cy="581"
            rx="55"
            ry="58"
            transform="rotate(-18 121 581)"
            fill="url(#auth-bg-disc-rim-l)"
          />
          <ellipse
            cx="113"
            cy="586"
            rx="54"
            ry="57"
            transform="rotate(-18 113 586)"
            fill="url(#auth-bg-disc-face-l)"
            stroke="var(--auth-bg-spec)"
            strokeWidth="1.35"
          />
          <ellipse
            cx="113"
            cy="586"
            rx="44"
            ry="47"
            transform="rotate(-18 113 586)"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.9"
            opacity="0.68"
          />
          <path
            d="M 76 560 C 96 538, 134 540, 150 562 L 92 624 C 72 606, 66 578, 76 560 Z"
            fill="var(--auth-bg-spec)"
            opacity="0.32"
          />
        </g>

        {/* Front intersecting 3D gyroscopic orbits passing over glass sculptures */}
        <ellipse
          cx="-18"
          cy="416"
          rx="240"
          ry="356"
          transform="rotate(28 -18 416)"
          stroke="url(#auth-bg-wire-l1)"
          strokeWidth="1.85"
        />
        <ellipse
          cx="-17"
          cy="415"
          rx="240"
          ry="356"
          transform="rotate(28 -17 415)"
          stroke="var(--auth-bg-spec)"
          strokeWidth="0.65"
          opacity="0.7"
        />
        <ellipse
          cx="-18"
          cy="416"
          rx="152"
          ry="330"
          transform="rotate(52 -18 416)"
          stroke="url(#auth-bg-wire-l2)"
          strokeWidth="1.4"
        />
      </svg>

      {/* Layer C & D (Right Flank): 3D Gyroscopic Wireframes + Iridescent Kidney Pebble, Faceted Crystals & Pearl */}
      <svg
        data-layer="orbital-sculpture-right"
        viewBox="0 0 320 820"
        fill="none"
        className="absolute right-0 top-[49%] h-[min(780px,90vh)] w-[min(150px,38vw)] -translate-y-1/2 opacity-45 sm:w-[min(220px,32vw)] sm:opacity-75 md:opacity-95 lg:w-[min(270px,20.5vw)] xl:w-[min(306px,21.2vw)]"
      >
        <defs>
          <filter id="auth-bg-shadow-r" x="-25%" y="-25%" width="160%" height="160%">
            <feDropShadow dx="-5" dy="14" stdDeviation="10" floodColor="var(--auth-bg-deep)" floodOpacity="0.24" />
          </filter>
          <filter id="auth-bg-contact-r" x="-45%" y="-85%" width="190%" height="270%">
            <feGaussianBlur stdDeviation="6.5" />
          </filter>

          <linearGradient id="auth-bg-wire-r1" x1="300" y1="60" x2="48" y2="760" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.55" />
            <stop offset="26%" stopColor="var(--auth-bg-spec)" stopOpacity="0.96" />
            <stop offset="54%" stopColor="var(--auth-bg-wire-deep)" stopOpacity="0.74" />
            <stop offset="82%" stopColor="var(--auth-bg-wire-glint)" stopOpacity="0.82" />
            <stop offset="100%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.48" />
          </linearGradient>

          <linearGradient id="auth-bg-wire-r2" x1="316" y1="115" x2="74" y2="735" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-wire-deep)" stopOpacity="0.52" />
            <stop offset="36%" stopColor="var(--auth-bg-wire-glint)" stopOpacity="0.88" />
            <stop offset="68%" stopColor="var(--auth-bg-wire-mid)" stopOpacity="0.68" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.84" />
          </linearGradient>

          {/* Upper-right kidney glass pebble gradients */}
          <linearGradient id="auth-bg-pebble-rim-r" x1="164" y1="168" x2="276" y2="322" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.98" />
            <stop offset="36%" stopColor="var(--auth-bg-deep)" stopOpacity="0.88" />
            <stop offset="72%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.92" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.9" />
          </linearGradient>

          <linearGradient id="auth-bg-pebble-face-r" x1="152" y1="184" x2="266" y2="312" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-core)" stopOpacity="0.78" />
            <stop offset="38%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.88" />
            <stop offset="74%" stopColor="var(--auth-bg-frost)" stopOpacity="0.9" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.96" />
          </linearGradient>

          {/* Mid-right 7-facet 3D crystal polyhedron gradients */}
          <linearGradient id="auth-bg-crys-f1" x1="248" y1="342" x2="296" y2="434" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-bright)" stopOpacity="0.92" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.94" />
          </linearGradient>

          <linearGradient id="auth-bg-crys-f2" x1="278" y1="342" x2="326" y2="418" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-core)" stopOpacity="0.95" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.98" />
          </linearGradient>

          <linearGradient id="auth-bg-crys-f3" x1="228" y1="412" x2="278" y2="468" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.98" />
            <stop offset="100%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.9" />
          </linearGradient>

          <linearGradient id="auth-bg-crys-f4" x1="276" y1="382" x2="324" y2="504" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.98" />
            <stop offset="48%" stopColor="var(--auth-bg-frost)" stopOpacity="0.94" />
            <stop offset="82%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.92" />
            <stop offset="100%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.9" />
          </linearGradient>

          <linearGradient id="auth-bg-crys-f5" x1="228" y1="434" x2="288" y2="496" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-deep)" stopOpacity="0.98" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.94" />
          </linearGradient>

          <linearGradient id="auth-bg-crys-f6" x1="230" y1="468" x2="298" y2="526" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.92" />
            <stop offset="55%" stopColor="var(--color-accent-500)" stopOpacity="0.78" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.94" />
          </linearGradient>

          {/* Lower-right smaller 6-facet crystal gem gradients */}
          <linearGradient id="auth-bg-gem-top" x1="176" y1="582" x2="254" y2="628" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-bright)" stopOpacity="0.9" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.98" />
          </linearGradient>

          <linearGradient id="auth-bg-gem-right" x1="214" y1="606" x2="260" y2="668" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.96" />
            <stop offset="60%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.9" />
            <stop offset="100%" stopColor="var(--auth-bg-frost)" stopOpacity="0.88" />
          </linearGradient>

          {/* Glossy 3D pearl sphere off-center radial gradient */}
          <radialGradient id="auth-bg-pearl-grad" cx="50%" cy="50%" r="54%" fx="32%" fy="28%">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="1" />
            <stop offset="28%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.95" />
            <stop offset="68%" stopColor="var(--auth-bg-core)" stopOpacity="0.92" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.96" />
          </radialGradient>
        </defs>

        {/* Studio floor contact shadows under right wireframe, crystal gem, and pearl */}
        <g filter="url(#auth-bg-contact-r)">
          <ellipse cx="246" cy="756" rx="76" ry="12" fill="var(--auth-bg-deep)" opacity="0.22" />
          <ellipse cx="216" cy="712" rx="38" ry="8.5" fill="var(--auth-bg-deep)" opacity="0.26" />
          <ellipse cx="154" cy="692" rx="17" ry="5.2" fill="var(--auth-bg-deep)" opacity="0.28" />
        </g>

        {/* Back 3D tilted gyroscopic orbits */}
        <ellipse
          cx="338"
          cy="416"
          rx="262"
          ry="364"
          transform="rotate(32 338 416)"
          stroke="url(#auth-bg-wire-r1)"
          strokeWidth="1.8"
        />
        <ellipse
          cx="338"
          cy="416"
          rx="226"
          ry="352"
          transform="rotate(14 338 416)"
          stroke="url(#auth-bg-wire-r2)"
          strokeWidth="1.5"
        />
        <ellipse
          cx="338"
          cy="416"
          rx="188"
          ry="340"
          transform="rotate(-11 338 416)"
          stroke="url(#auth-bg-wire-r1)"
          strokeWidth="1.35"
          opacity="0.86"
        />

        {/* (R1) Upper-right sculpted organic kidney/pebble 3D glass lens */}
        <g filter="url(#auth-bg-shadow-r)">
          <path
            d="M 192 172 C 234 158, 276 190, 271 241 C 267 286, 244 323, 201 319 C 160 315, 153 274, 161 231 C 167 196, 170 180, 192 172 Z"
            fill="url(#auth-bg-pebble-rim-r)"
          />
          <path
            d="M 184 176 C 225 162, 266 193, 262 243 C 258 287, 236 323, 194 319 C 154 315, 146 275, 154 233 C 160 199, 162 184, 184 176 Z"
            fill="url(#auth-bg-pebble-face-r)"
            stroke="var(--auth-bg-spec)"
            strokeWidth="1.35"
          />
          <path
            d="M 188 186 C 221 175, 254 200, 251 241 C 248 278, 229 309, 196 306 C 164 303, 158 270, 164 235 C 169 207, 171 193, 188 186 Z"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.85"
            opacity="0.7"
          />
          <path
            d="M 182 186 C 208 176, 238 192, 244 222 C 222 228, 190 256, 172 296 C 154 274, 156 226, 182 186 Z"
            fill="var(--auth-bg-spec)"
            opacity="0.34"
          />
        </g>

        {/* (R2) Mid-right sharp 7-facet 3D crystal polyhedron */}
        <g data-crystal="primary-polyhedron" filter="url(#auth-bg-shadow-r)">
          <polygon points="296,342 248,412 278,434" fill="url(#auth-bg-crys-f1)" />
          <polygon points="296,342 278,434 326,376" fill="url(#auth-bg-crys-f2)" />
          <polygon points="248,412 226,468 278,434" fill="url(#auth-bg-crys-f3)" />
          <polygon points="278,434 326,376 326,504 288,492" fill="url(#auth-bg-crys-f4)" />
          <polygon points="226,468 278,434 288,492" fill="url(#auth-bg-crys-f5)" />
          <polygon points="226,468 288,492 298,526" fill="url(#auth-bg-crys-f6)" />
          <polygon points="288,492 326,504 298,526" fill="var(--auth-bg-deep)" opacity="0.92" />
          {/* Specular crystal facet ridges */}
          <polyline
            points="296,342 278,434 288,492 298,526"
            stroke="var(--auth-bg-spec)"
            strokeWidth="1.05"
            strokeLinejoin="round"
          />
          <polyline
            points="248,412 278,434 326,376"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.95"
            strokeLinejoin="round"
          />
          <line x1="226" y1="468" x2="278" y2="434" stroke="var(--auth-bg-spec)" strokeWidth="0.95" />
        </g>

        {/* (R3) Lower-right smaller 6-facet crystal gem */}
        <g data-crystal="secondary-gem" filter="url(#auth-bg-shadow-r)">
          <polygon points="216,582 176,612 214,628" fill="var(--auth-bg-core)" opacity="0.88" />
          <polygon points="216,582 214,628 254,606" fill="url(#auth-bg-gem-top)" />
          <polygon points="176,612 186,666 214,628" fill="var(--auth-bg-deep)" opacity="0.95" />
          <polygon points="214,628 254,606 258,652" fill="url(#auth-bg-gem-right)" />
          <polygon points="186,666 214,628 222,676" fill="var(--auth-bg-wire-mid)" opacity="0.9" />
          <polygon points="214,628 258,652 222,676" fill="var(--auth-bg-frost)" opacity="0.92" />
          <polyline
            points="216,582 214,628 222,676"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.95"
            strokeLinejoin="round"
          />
          <polyline
            points="176,612 214,628 254,606"
            stroke="var(--auth-bg-spec)"
            strokeWidth="0.9"
            strokeLinejoin="round"
          />
        </g>

        {/* (R4) Glossy 3D specular pearl sphere */}
        <g filter="url(#auth-bg-shadow-r)">
          <circle cx="154" cy="662" r="14.5" fill="url(#auth-bg-pearl-grad)" />
          <circle cx="154" cy="662" r="14" stroke="var(--auth-bg-spec)" strokeWidth="0.7" opacity="0.72" />
          <ellipse
            cx="149"
            cy="656"
            rx="3.8"
            ry="2.3"
            transform="rotate(-28 149 656)"
            fill="var(--auth-bg-spec)"
            opacity="0.96"
          />
        </g>

        {/* Front intersecting 3D gyroscopic orbits */}
        <ellipse
          cx="338"
          cy="416"
          rx="244"
          ry="358"
          transform="rotate(-28 338 416)"
          stroke="url(#auth-bg-wire-r1)"
          strokeWidth="1.85"
        />
        <ellipse
          cx="337"
          cy="415"
          rx="244"
          ry="358"
          transform="rotate(-28 337 415)"
          stroke="var(--auth-bg-spec)"
          strokeWidth="0.65"
          opacity="0.72"
        />
        <ellipse
          cx="338"
          cy="416"
          rx="154"
          ry="332"
          transform="rotate(-52 338 416)"
          stroke="url(#auth-bg-wire-r2)"
          strokeWidth="1.4"
        />
      </svg>
    </div>
  );
}

/**
 * Layer E: Multi-layered silk ribbon waves inside the left brand column of Login / Register cards (UNI-2775).
 * - Keeps the upper ~72% text zone clean (`primary-50` -> `primary-100`) so logo, heading, subtitle, and all 3 feature
 *   rows maintain >= 4.5:1 WCAG AA contrast across all presets, custom palettes, and Light/Dark modes.
 * - Renders sweeping overlapping silk ribbons + delicate `1px` luminous contour hairlines across the bottom ~28%.
 */
export function AuthBrandDecor() {
  return (
    <div
      aria-hidden="true"
      data-testid="auth-brand-decor"
      className={cn(
        "pointer-events-none absolute inset-0 -z-10 select-none overflow-hidden",
        STUDIO_PALETTE_CLASS,
      )}
    >
      <div className="absolute inset-0" style={BRAND_PANEL_WASH_STYLE} />
      <svg
        viewBox="0 0 520 190"
        preserveAspectRatio="none"
        fill="none"
        style={BRAND_SILK_MASK_STYLE}
        className="absolute inset-x-0 bottom-0 h-28 w-full sm:h-36 md:h-40 [[data-theme=dark]_&]:opacity-80"
      >
        <defs>
          <linearGradient id="auth-decor-silk-veil" x1="0" y1="40" x2="520" y2="190" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-bright)" stopOpacity="0.2" />
            <stop offset="52%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.28" />
            <stop offset="100%" stopColor="var(--auth-bg-core)" stopOpacity="0.34" />
          </linearGradient>

          <linearGradient id="auth-decor-silk-mid" x1="20" y1="78" x2="500" y2="185" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.58" />
            <stop offset="50%" stopColor="var(--auth-bg-core)" stopOpacity="0.54" />
            <stop offset="100%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.66" />
          </linearGradient>

          <linearGradient id="auth-decor-silk-right" x1="520" y1="68" x2="40" y2="190" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-core)" stopOpacity="0.72" />
            <stop offset="48%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.62" />
            <stop offset="100%" stopColor="var(--auth-bg-deep)" stopOpacity="0.78" />
          </linearGradient>

          <linearGradient id="auth-decor-silk-crest" x1="0" y1="96" x2="420" y2="190" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-deep)" stopOpacity="0.88" />
            <stop offset="52%" stopColor="var(--auth-bg-core)" stopOpacity="0.76" />
            <stop offset="100%" stopColor="var(--auth-bg-iridescent)" stopOpacity="0.68" />
          </linearGradient>

          <linearGradient id="auth-decor-silk-hairline" x1="0" y1="90" x2="520" y2="140" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="var(--auth-bg-spec)" stopOpacity="0.65" />
            <stop offset="48%" stopColor="var(--auth-bg-luminous)" stopOpacity="0.85" />
            <stop offset="100%" stopColor="var(--auth-bg-spec)" stopOpacity="0.72" />
          </linearGradient>
        </defs>

        {/* Gossamer upper silk veil */}
        <path
          d="M 0 88 C 128 38, 286 138, 520 48 L 520 190 L 0 190 Z"
          fill="url(#auth-decor-silk-veil)"
        />
        {/* Sweeping left-to-right iridescent ribbon */}
        <path
          d="M 0 110 C 145 64, 308 164, 520 82 L 520 190 L 0 190 Z"
          fill="url(#auth-decor-silk-mid)"
        />
        {/* Counter-curving right-to-left primary silk fold */}
        <path
          d="M 0 144 C 168 172, 336 86, 520 104 L 520 190 L 0 190 Z"
          fill="url(#auth-decor-silk-right)"
        />
        {/* Rich velvet bottom-left corner crest */}
        <path
          d="M 0 98 C 96 112, 214 176, 396 190 L 0 190 Z"
          fill="url(#auth-decor-silk-crest)"
        />

        {/* Delicate 1px luminous silk wave contour hairlines */}
        <path
          d="M 0 110 C 145 64, 308 164, 520 82"
          stroke="url(#auth-decor-silk-hairline)"
          strokeWidth="1.15"
        />
        <path
          d="M 0 144 C 168 172, 336 86, 520 104"
          stroke="url(#auth-decor-silk-hairline)"
          strokeWidth="1"
          opacity="0.8"
        />
        <path
          d="M 0 98 C 96 112, 214 176, 396 190"
          stroke="var(--auth-bg-spec)"
          strokeWidth="0.9"
          opacity="0.55"
        />
      </svg>
    </div>
  );
}

export function AuthPageStage({
  children,
  className,
  ...props
}: ComponentPropsWithoutRef<"main"> & { children: ReactNode }) {
  return (
    <main className={cn(DEFAULT_STAGE_CLASS, className)} {...props}>
      {children}
      <AuthBackdrop />
    </main>
  );
}
