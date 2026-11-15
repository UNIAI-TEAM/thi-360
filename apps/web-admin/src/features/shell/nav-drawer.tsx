"use client";

import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
  IconButton,
} from "@thi360/ui";
import { Menu } from "lucide-react";
import { useTranslations } from "next-intl";
import { usePathname } from "next/navigation";
import { useEffect, useId, useState } from "react";
import { BrandMark, NavigationGroups } from "./sidebar";

/** Cùng ngưỡng `lg` của Tailwind (64rem = 1024px): từ đây sidebar cố định thay cho ngăn menu. */
const PERSISTENT_SIDEBAR_QUERY = "(min-width: 64rem)";

/**
 * Nút mở menu ở đầu thanh trên và ngăn menu trượt từ trái, chỉ dùng dưới `lg` (DESIGN 5.8). Ngăn menu dùng lại
 * danh sách của sidebar. Đóng khi bấm một mục, khi đổi trang, bằng Esc, bấm nền mờ hoặc nút ×; `Drawer` của kit
 * trả focus về nút mở. Cửa sổ nới rộng tới `lg` khi ngăn đang mở thì ngăn tự đóng, vì sidebar đã hiện.
 */
export function NavDrawer() {
  const t = useTranslations("shell");
  const pathname = usePathname();
  const contentId = useId();
  const [open, setOpen] = useState(false);
  const [openedAt, setOpenedAt] = useState(pathname);

  // Đổi trang bằng cách khác bấm mục menu (nút Lùi của trình duyệt): đóng ngăn ngay trong lần render đó.
  if (open && pathname !== openedAt) {
    setOpen(false);
  }

  useEffect(() => {
    if (!open) return;
    const query = window.matchMedia(PERSISTENT_SIDEBAR_QUERY);
    const onChange = (event: MediaQueryListEvent) => {
      if (event.matches) setOpen(false);
    };
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, [open]);

  function onOpenChange(next: boolean) {
    if (next) setOpenedAt(pathname);
    setOpen(next);
  }

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <IconButton
        icon={<Menu />}
        aria-label={t("navDrawer.open")}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? contentId : undefined}
        onClick={() => onOpenChange(true)}
        // Màn cảm ứng: nút nhìn 40px, vùng bấm nới ra 44×44 bằng phần tử giả (DESIGN mục 8).
        className="relative pointer-coarse:after:absolute pointer-coarse:after:-inset-0.5 lg:hidden"
      />
      <DrawerContent
        id={contentId}
        side="left"
        closeLabel={t("navDrawer.close")}
        aria-describedby={undefined}
        className="max-w-72 px-3 py-4 sm:px-3 sm:py-4"
      >
        <DrawerHeader className="pb-2">
          <DrawerTitle className="sr-only">{t("navDrawer.title")}</DrawerTitle>
          <BrandMark />
        </DrawerHeader>
        <nav aria-label={t("menuLabel")} className="flex flex-col gap-0.5">
          <NavigationGroups onNavigate={() => setOpen(false)} />
        </nav>
      </DrawerContent>
    </Drawer>
  );
}
