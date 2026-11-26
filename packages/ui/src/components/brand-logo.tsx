"use client";

import { useId, type ImgHTMLAttributes, type SVGProps } from "react";
import { cn } from "../lib/cn";

export interface BrandLogoProps extends SVGProps<SVGSVGElement> {
  /** Optional accessible label; when omitted the mark is decorative (`aria-hidden`). */
  label?: string;
}

export interface BrandWordmarkProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "width" | "height"> {
  /** Accessible name (pass empty string when adjacent text already names the brand). */
  alt: string;
  /** Asset path for the horizontal full lockup image. Defaults to `/logo-full.png`. */
  src?: string;
}

const BLUE_PATH =
  "M578 349 L573 434 L573 460 L594 442 L595 362 Z M194 323 L174 332 L146 348 L122 364 L99 382 L62 419 L49 436 L38 454 L28 476 L22 502 L23 518 L28 534 L36 547 L47 558 L61 568 L75 575 L95 582 L136 589 L202 588 L247 582 L270 577 L246 576 L216 572 L193 567 L163 557 L137 543 L118 526 L109 513 L102 495 L100 483 L101 460 L108 435 L118 413 L133 389 L146 372 L163 353 Z M600 161 L601 199 L659 188 L659 185 Z M588 156 L509 123 L391 70 L378 70 L204 157 L83 213 L80 217 L80 222 L83 225 L204 287 L210 273 L216 264 L235 247 L257 233 L286 218 L333 200 L381 188 L431 181 L482 180 L518 185 L446 191 L408 198 L364 210 L326 224 L302 235 L270 253 L242 273 L238 280 L207 375 L164 518 L159 541 L177 549 L225 541 L281 541 L317 548 L344 559 L353 565 L357 565 L372 560 L389 548 L417 534 L455 522 L478 519 L526 492 L484 492 L464 495 L441 501 L403 517 L377 533 L361 546 L344 535 L322 525 L295 517 L256 512 L220 513 L198 516 L174 522 L173 519 L190 466 L207 422 L226 382 L227 385 L210 439 L194 500 L195 501 L217 496 L238 494 L259 494 L284 497 L324 509 L363 530 L376 519 L397 505 L424 491 L444 483 L461 478 L494 473 L517 473 L534 475 L545 345 L547 338 L558 345 L550 441 L549 478 L562 468 L566 385 L572 333 L549 327 L547 325 L549 307 L535 306 L501 309 L468 320 L444 334 L413 361 L449 341 L471 333 L499 327 L521 327 L522 330 L512 408 L509 447 L472 451 L454 456 L430 466 L404 482 L390 493 L374 509 L362 526 L353 510 L340 497 L314 481 L291 473 L270 469 L245 468 L231 470 L230 465 L265 329 L288 314 L328 295 L360 284 L404 275 L425 273 L454 273 L475 275 L505 281 L538 293 L529 221 L589 201 Z";

const GOLD_PATH =
  "M431 382 L426 378 L349 338 L340 340 L336 345 L332 384 L324 434 L325 445 L331 448 L336 447 L426 397 L432 390 Z M671 370 L663 356 L650 342 L634 331 L621 325 L636 342 L642 354 L645 366 L645 379 L641 396 L635 410 L623 429 L610 445 L594 461 L568 483 L525 512 L472 540 L425 560 L373 578 L303 596 L232 606 L157 606 L195 618 L234 625 L269 628 L324 627 L369 621 L407 613 L451 600 L501 580 L533 564 L577 537 L607 514 L635 486 L657 456 L666 439 L672 423 L675 410 L676 392 Z M590 157 L590 216 L584 223 L584 233 L587 238 L580 254 L572 299 L580 301 L618 300 L620 299 L612 258 L608 245 L604 238 L607 232 L607 223 L605 219 L600 215 L599 160 Z";

export function BrandLogo({ className, label, ...props }: BrandLogoProps) {
  const id = useId();
  const blueId = `${id}-blue`;
  const goldId = `${id}-gold`;
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 700 700"
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={cn("size-9 shrink-0", className)}
      {...props}
    >
      <defs>
        <linearGradient id={blueId} x1="0" y1="0" x2="0.25" y2="1">
          <stop stopColor="#007ec7" />
          <stop offset="0.38" stopColor="#003b85" />
          <stop offset="0.65" stopColor="#00a9ed" />
          <stop offset="1" stopColor="#003573" />
        </linearGradient>
        <linearGradient id={goldId} x1="1" y1="0" x2="0" y2="1">
          <stop stopColor="#ffd000" />
          <stop offset="0.55" stopColor="#ff9b00" />
          <stop offset="1" stopColor="#ee4b00" />
        </linearGradient>
      </defs>
      <path fill={`url(#${blueId})`} fillRule="evenodd" d={BLUE_PATH} />
      <path fill={`url(#${goldId})`} fillRule="evenodd" d={GOLD_PATH} />
    </svg>
  );
}

/**
 * Full horizontal Thi 360 brand lockup (icon + wordmark). Adds a subtle light backing under
 * `[data-theme="dark"]` so the navy lettering stays crisp on dark surfaces.
 */
export function BrandWordmark({ alt, src = "/logo-full.png", className, ...props }: BrandWordmarkProps) {
  return (
    <img
      src={src}
      alt={alt}
      width={1927}
      height={610}
      decoding="async"
      aria-hidden={alt === "" ? true : undefined}
      className={cn(
        "h-9 w-auto shrink-0 object-contain [[data-theme='dark']_&]:rounded-lg [[data-theme='dark']_&]:bg-white/95 [[data-theme='dark']_&]:px-2 [[data-theme='dark']_&]:py-0.5",
        className,
      )}
      {...props}
    />
  );
}
