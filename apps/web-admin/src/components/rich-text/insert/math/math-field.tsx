"use client";

import type { MathfieldElement } from "mathlive";
import { useEffect, useImperativeHandle, useRef, type Ref } from "react";

/** Thư mục phông chép từ gói `mathlive` vào `public/` (gói Next không tải được phông theo đường mặc định). */
export const MATHLIVE_FONTS_DIRECTORY = "/mathlive/fonts/";

export interface MathFieldHandle {
  /** Đặt cả nội dung ô trực quan (không phát `onLatexChange`: nguồn của thay đổi là ô mã LaTeX). */
  setLatex: (latex: string) => void;
  /** Chèn mẫu LaTeX tại con trỏ, con trỏ nhảy tới ô giữ chỗ đầu tiên của mẫu. */
  insert: (latex: string) => void;
  focus: () => void;
}

export interface MathFieldProps {
  /** Nội dung ban đầu (mở lại công thức để sửa). */
  initialLatex: string;
  /** `aria-label` của ô trực quan. */
  ariaLabel: string;
  /** Người dùng sửa ô trực quan: `latex-expanded` mới của ô. */
  onLatexChange: (latex: string) => void;
  /** `ready` khi ô đã dựng xong, `failed` khi không tải được `mathlive`. */
  onStatusChange: (status: "ready" | "failed") => void;
  className?: string;
  ref?: Ref<MathFieldHandle>;
}

/**
 * Ô soạn trực quan bằng `mathlive` (spec 4.2). Thư viện chỉ tải khi ô được dựng (dynamic import), không vào gói chung
 * của trang. Ô là phần tử tùy biến `<math-field>` tạo bằng mã, nên phần tử và thuộc tính của nó không đi qua JSX.
 */
export function MathField({ initialLatex, ariaLabel, onLatexChange, onStatusChange, className, ref }: MathFieldProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const fieldRef = useRef<MathfieldElement | null>(null);
  // Chỉ đọc lúc dựng ô; các callback đi qua ref để effect dựng ô chạy đúng một lần.
  const initialRef = useRef(initialLatex);
  const labelRef = useRef(ariaLabel);
  const onLatexChangeRef = useRef(onLatexChange);
  const onStatusChangeRef = useRef(onStatusChange);
  // Đang ghi vào ô từ bên ngoài: bỏ qua sự kiện `input` do chính lần ghi đó phát ra.
  const writingRef = useRef(false);
  useEffect(() => {
    labelRef.current = ariaLabel;
    onLatexChangeRef.current = onLatexChange;
    onStatusChangeRef.current = onStatusChange;
    fieldRef.current?.setAttribute("aria-label", ariaLabel);
  });

  useImperativeHandle(
    ref,
    () => ({
      setLatex(latex) {
        const field = fieldRef.current;
        if (!field) return;
        writingRef.current = true;
        try {
          field.value = latex;
        } finally {
          writingRef.current = false;
        }
      },
      insert(latex) {
        const field = fieldRef.current;
        if (!field) return;
        field.insert(latex, { format: "latex", selectionMode: "placeholder", focus: true });
        // `insert` phát `input` khi không bị tắt thông báo; đọc lại để không phụ thuộc vào đó.
        onLatexChangeRef.current(field.getValue("latex-expanded"));
      },
      focus() {
        fieldRef.current?.focus();
      },
    }),
    [],
  );

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let disposed = false;
    let field: MathfieldElement | null = null;
    const onInput = () => {
      if (writingRef.current || !field) return;
      onLatexChangeRef.current(field.getValue("latex-expanded"));
    };
    import("mathlive").then(
      ({ MathfieldElement: Element }) => {
        if (disposed) return;
        Element.fontsDirectory = MATHLIVE_FONTS_DIRECTORY;
        Element.soundsDirectory = null;
        field = new Element();
        // Bảng nút của hộp thay bàn phím ảo; trình đơn tiếng Anh của mathlive không dùng (`menuItems` chỉ đặt được
        // sau khi ô đã gắn vào trang: trước đó mathlive báo "Mathfield not mounted").
        field.mathVirtualKeyboardPolicy = "manual";
        // Mặc định mathlive nhảy ra khỏi ô mũ ngay sau một chữ số (`smartSuperscript`), nên gõ 2^12 thành 2¹2. Tắt để
        // mũ nhiều chữ số gõ liền được; ra khỏi ô mũ bằng Tab hoặc phím mũi tên phải (UNI-2878).
        field.smartSuperscript = false;
        field.setAttribute("aria-label", labelRef.current);
        field.addEventListener("input", onInput);
        host.append(field);
        field.menuItems = [];
        fieldRef.current = field;
        writingRef.current = true;
        try {
          field.value = initialRef.current;
        } finally {
          writingRef.current = false;
        }
        field.focus();
        onStatusChangeRef.current("ready");
      },
      () => {
        if (!disposed) onStatusChangeRef.current("failed");
      },
    );
    return () => {
      disposed = true;
      field?.removeEventListener("input", onInput);
      field?.remove();
      fieldRef.current = null;
    };
  }, []);

  return <div ref={hostRef} className={className} />;
}
