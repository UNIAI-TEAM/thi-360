import type { QueryKey } from "@tanstack/react-query";

/**
 * `placeholderData` cho danh sách có lọc, phân trang, sắp xếp. Khóa truy vấn của các danh sách này kết
 * thúc bằng đối tượng tham số; khi chỉ tham số đổi thì giữ dữ liệu của truy vấn trước trên màn hình cho
 * tới khi dữ liệu mới về, để bảng không bị thay bằng khung xương rồi nhảy lại. Khi phần đứng trước tham
 * số đổi (lớp khác, nhóm danh mục khác, quyền khác) thì không giữ, vì đó là dữ liệu của đối tượng khác.
 */
export function keepPreviousWhileSameScope(queryKey: QueryKey) {
  const scope = JSON.stringify(queryKey.slice(0, -1));
  return <TData>(
    previousData: TData | undefined,
    previousQuery: { queryKey: QueryKey } | undefined,
  ): TData | undefined => {
    if (!previousQuery || previousQuery.queryKey.length !== queryKey.length) return undefined;
    return JSON.stringify(previousQuery.queryKey.slice(0, -1)) === scope ? previousData : undefined;
  };
}
