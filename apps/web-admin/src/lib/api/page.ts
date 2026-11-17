/** Dạng phân trang của backend (`PageResponse*`, trang tính từ 0), dùng chung cho các kiểu danh sách của màn hình. */
export interface ApiPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
