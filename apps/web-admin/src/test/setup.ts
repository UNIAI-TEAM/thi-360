import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach } from "vitest";

configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
});

// jsdom thiếu các API mà Radix và sonner gọi.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub;

if (typeof Element !== "undefined") {
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
}

if (typeof window !== "undefined") {
  window.matchMedia ??= (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// jsdom không đo layout: ProseMirror/TipTap gọi getClientRects khi scrollToSelection/paste.
const emptyDomRectList = {
  length: 0,
  item: () => null,
  [Symbol.iterator]: function* () {},
} as unknown as DOMRectList;

if (typeof Range !== "undefined") {
  Range.prototype.getClientRects ??= () => emptyDomRectList;
  Range.prototype.getBoundingClientRect ??= () => new DOMRect();
}
if (typeof Element !== "undefined") {
  Element.prototype.getClientRects ??= () => emptyDomRectList;
}
