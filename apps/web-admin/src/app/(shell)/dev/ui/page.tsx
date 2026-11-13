import { notFound } from "next/navigation";
import { DevUiGallery } from "./dev-ui-gallery";

/** Chỉ mở ngoài production (plan D/T06). */
export default function DevUiPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <DevUiGallery />;
}
