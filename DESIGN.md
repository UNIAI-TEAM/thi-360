# DESIGN — Thi 360

> Cập nhật: 2026-11-22 (đã cập nhật hết đợt 3 · QA UNI-2744) · Tham khảo phong cách: https://hsavnu.edu.vn (đã xem trang chủ, Thi thử, Khóa học, chi tiết khóa học, Tin tức, Đăng nhập). Số liệu dưới đây đo trực tiếp từ các trang đó.
>
> Chỉ tham khảo **phong cách**. Không dùng logo, tên, ảnh hay nội dung của HSA Education. Thi 360 cần logo riêng.

## 1. Phong cách

Sáng, sạch, thân thiện với học sinh. Nền trắng và xám rất nhạt, một màu xanh lá đậm làm chủ đạo, cam dùng tiết kiệm cho hành động "vào thi". Bo góc lớn, viền mảnh, bóng đổ nhẹ. Chữ Be Vietnam Pro, tiêu đề đậm.

- **Web học viên:** thoáng, có vùng hero nền xanh, thẻ bài thi có ảnh, nút lớn.
- **Web admin:** cùng token nhưng **dày hơn**: không có hero, bảng là thành phần chính, chiều cao điều khiển 36–40px, ít bóng đổ.

## 2. Màu sắc

### 2.1 Màu chủ đạo (xanh lá)

| Token | Hex | Dùng cho |
|---|---|---|
| `primary-50` | `#ECFDF5` | Nền mục đang chọn, nền chip, nền tab đang chọn |
| `primary-100` | `#DCFCE7` | Nền nút phụ màu xanh, nền hero sáng |
| `primary-200` | `#A7E3C2` | Viền nút viền xanh |
| `primary-500` | `#10B981` | Điểm nhấn trong gradient |
| `primary-600` | `#059669` | Màu thương hiệu: gradient, thanh dọc, viền focus, chấm màu, **viền thẻ/chip/ô đang chọn** (đạt 3:1 với nền ở cả hai chế độ; bộ màu khác ở chế độ tối được nâng sáng tới 3:1). Không dùng làm chữ hay nền nút có chữ |
| `primary-700` | `#047857` | Thanh trên cùng, chữ xanh dưới 14px trên nền trắng |
| `primary-800` | `#065F46` | Đầu tối của gradient, chữ trên nền xanh nhạt |
| `primary-900` | `#064E3B` | Tiêu đề lớn trên nền sáng |
| `primary-text` | `#047857` (tối: `#10B981`) | **Chữ màu chính**: liên kết, tab/mục nav/trang đang chọn, nút nhạt, icon trên nền `primary-50/100/200`. Sáng: 5,21:1 trên `primary-50`, 4,99:1 trên `primary-100`. Tối: đạt từ 6,05:1 trên `subtle` đến 7,30:1 trên nền trang |
| `primary-fill` | `#047857` (tối: `#55B38F`) | **Nền hành động chính**: nút chính, chip lọc đang chọn, ô logo, checkbox/switch bật. Sáng: đi với `on-primary` trắng (5,48:1). Tối: nền sáng chữ tối (7,03:1), nền đạt 6:1 với nền trang/thẻ/nền phụ (mục 8) |
| `primary-fill-hover` | `#065F46` (tối: `#62C09B`) | Nút chính khi hover: sáng thì đậm thêm một bậc (trắng 7,68:1), tối thì sáng thêm một bậc |
| `primary-fill-active` | `#064E3B` (tối: `#6FCDA8`) | Nút chính khi bấm (sáng: trắng 9,72:1) |
| `on-primary` | `#FFFFFF` (tối: `#11181C`) | Chữ và icon trên `primary-fill` (nút chính, chip lọc đang chọn, ô logo, dấu tích, nút tròn của switch đang bật) |
| `on-danger` | `#FFFFFF` | Chữ trên nút nguy hiểm `danger-solid`, ở cả hai chế độ |
| `switch-thumb` | `#FFFFFF` | Nút tròn của switch khi tắt, ở cả hai chế độ |

### 2.2 Màu nhấn

| Token | Hex | Dùng cho |
|---|---|---|
| `accent-500` | `#F59223` | Nút hành động thi ("Vào thi ngay"), tối đa một nút trên mỗi thẻ |
| `on-accent` | `#1A1A1F` | Chữ trên `accent-500` (7,44:1; trắng chỉ đạt 2,33:1) |
| `accent-700` | `#B45309` | Chữ trên nền cam nhạt, nhãn nhóm |
| `amber-500` | `#F59E0B` | Sao đánh giá, cảnh báo |
| `yellow-400` | `#FFD836` | Nhãn "HOT", huy hiệu |

### 2.3 Màu trung tính

| Token | Hex | Dùng cho |
|---|---|---|
| `text` | `#11181C` | Chữ chính |
| `text-secondary` | `#3A3A42` | Chữ menu, nhãn nút phụ |
| `text-muted` | `#6B6B72` | Mô tả, chú thích |
| `text-placeholder` | `#A6A6AE` | Placeholder |
| `bg-page` | `#F7F8F9` | Nền trang |
| `bg-surface` | `#FFFFFF` | Thẻ, bảng, dialog |
| `bg-subtle` | `#F1F2F4` | Nền thanh tab, dòng bảng khi hover |
| `border` | `#E8E8EC` | Viền mặc định |
| `border-strong` | `#D4D4D8` | Viền khi hover |
| `border-primary` | `#D4EEE0` | Viền ô nhập trên nền xanh nhạt |
| `scroll-shadow` | đen 14% (sáng), đen 50% (tối) | Bóng mờ báo vùng cuộn còn nội dung (lớp `scroll-shadows-y`, mục 5.13) |

### 2.4 Màu trạng thái

Trang tham khảo không có đủ bộ màu trạng thái cho trang quản trị, nên phần này là bổ sung.

| Trạng thái | Chữ | Nền | Ví dụ |
|---|---|---|---|
| Thành công | `#047857` | `#ECFDF5` | Hoạt động, Thành công |
| Cảnh báo | `#B45309` | `#FFFBEB` | Chưa kích hoạt, Đang xử lý |
| Lỗi / nguy hiểm | `#B91C1C` | `#FEF2F2` | Bị khóa, Thất bại, nút Xóa |
| Thông tin | `#0E7490` | `#ECFEFF` | Hệ thống, ghi chú |
| Trung tính | `#54545A` | `#F1F2F4` | Ngừng sử dụng, Nháp |

### 2.5 Gradient

| Tên | Giá trị | Dùng cho |
|---|---|---|
| `hero-dark` | `linear-gradient(115deg, #065F46, #059669)` | Dải tiêu đề trang (web học viên) |
| `hero-light` | `radial-gradient(820px 480px at 90% -12%, #DCFCE7 0, #F0FDF4 62%)` | Nền hero trang chủ, nửa trái trang đăng nhập |
| `badge-gold` | `linear-gradient(to bottom right, #FBBF24, #FFD836)` | Huy hiệu |

Web admin không dùng gradient, trừ trang đăng nhập.

## 3. Chữ

**Font:** Be Vietnam Pro (Google Fonts, có đủ dấu tiếng Việt), trọng lượng 400, 500, 600, 700. Dự phòng: `system-ui, sans-serif`. Số trong bảng dùng `font-variant-numeric: tabular-nums`.

| Token | Cỡ / dòng | Đậm | Dùng cho |
|---|---|---|---|
| `display` | 44 / 50 | 700 | Tiêu đề hero (chỉ web học viên) |
| `h1` | 30 / 38 | 700 | Tiêu đề trang |
| `h2` | 27 / 40 | 700 | Tiêu đề khối lớn (web học viên) |
| `h3` | 18 / 28 | 700 | Tiêu đề khối, tiêu đề dialog |
| `title` | 16 / 24 | 600 | Tiêu đề thẻ |
| `body` | 14 / 21 | 400 | Chữ mặc định |
| `label` | 14 / 20 | 500–600 | Nút, menu, nhãn trường |
| `caption` | 12.5 / 19 | 500 | Chip, chú thích |
| `overline` | 13 / 20, chữ hoa, giãn chữ 0.08em | 600 | Nhãn nhỏ phía trên tiêu đề khối |

Tiêu đề trang ở web admin dùng 22 / 30, đậm 700.

- Token cỡ chữ khai báo bằng `rem` để theo cỡ chữ người dùng đặt trong trình duyệt; không đặt cỡ chữ trên `html`.
- Ô nhập ở web học viên dùng chữ 16px trên màn hẹp và màn cảm ứng (lớp `touchFieldText`), để iOS không phóng to trang khi focus.

## 4. Khoảng cách, bo góc, viền, bóng

- **Lưới khoảng cách:** bội số của 4px. Khoảng đệm trong thẻ 16–24px. Khoảng cách giữa các khối 24–32px (admin), 48–64px (web học viên).
- **Chiều rộng nội dung:** web học viên tối đa 1170px; admin giãn theo màn hình, lề 24px (16px dưới 640px, mục 5.8).
- **Viền:** 1px, màu `border`.

| Bo góc | Giá trị | Dùng cho |
|---|---|---|
| `radius-sm` | 8px | Mục menu, ô nhỏ |
| `radius-md` | 10px | Ô nhập, tab |
| `radius-lg` | 12px | Nút, thẻ, thanh lọc |
| `radius-xl` | 16px | Thẻ lớn, drawer |
| `radius-2xl` | 24px | Dialog, khung đăng nhập |
| `radius-full` | 9999px | Chip, huy hiệu, avatar |

| Bóng | Giá trị | Dùng cho |
|---|---|---|
| `shadow-xs` | `0 1px 2px rgba(0,0,0,.05)` | Thẻ tĩnh, ô nhập |
| `shadow-sm` | `0 1px 4px rgba(0,0,0,.10)` | Tab đang chọn |
| `shadow-md` | `0 6px 18px rgba(20,20,30,.12)` | Menu thả, popover |
| `shadow-lg` | `0 14px 34px rgba(20,20,30,.14)` | Thẻ khi hover (web học viên) |
| `shadow-xl` | `0 24px 60px rgba(20,20,30,.08)` | Dialog, khung đăng nhập |
| `shadow-primary` | `0 8px 20px rgba(5,150,105,.25)` | Nút chính cỡ lớn |

## 5. Thành phần

### 5.1 Nút

| Kiểu | Nền | Chữ | Viền | Dùng cho |
|---|---|---|---|---|
| Chính | `primary-fill`, hover `primary-fill-hover`, bấm `primary-fill-active` | `on-primary` (trắng) | không | Hành động chính của màn (một nút) |
| Phụ | trắng | `text-secondary` | `border` | Hủy, Quay lại, Đăng nhập |
| Viền xanh | trắng | `primary-800` | `primary-200` | Hành động phụ có nhấn |
| Nhạt | `primary-100`, hover `primary-200` | `primary-text`, hover `primary-800` | không | Hành động trong thẻ |
| Thi | `accent-500` | `on-accent` (`#1A1A1F`) | không | Bắt đầu / tiếp tục thi |
| Nguy hiểm | `#DC2626` | trắng | không | Xóa, Khóa (trong dialog xác nhận) |
| Chữ | trong suốt | `primary-text` | không | Liên kết trong dòng, "Xem tất cả →" |

- Cỡ: `sm` 36px, `md` 40px (mặc định), `lg` 48px, `xl` 52px (nút gửi ở trang đăng nhập).
- Bo góc 12px, chữ 14/20 đậm 600, đệm ngang 16px (`md`) hoặc 28px (`lg`).
- Icon lucide 16–18px đặt trước chữ; mũi tên đặt sau chữ.
- Đang xử lý (`loading`): hiện spinner, khóa nút, giữ nguyên chiều rộng **và giữ nguyên tên nút** (trình đọc màn hình đọc "đang xử lý" qua `aria-busy`, không đổi chữ).
- Vùng bấm tối thiểu 44×44px trên màn cảm ứng (`pointer-coarse:`), mở rộng bằng phần tử giả, không đổi kích thước nhìn thấy.

### 5.2 Ô nhập, chọn

- Cao 40px (admin) hoặc 48px (web học viên, trang đăng nhập); bo góc 10–12px; nền trắng; viền `border`.
- Focus: viền `primary-text` và vòng sáng `0 0 0 3px rgba(5,150,105,.15)`.
- **Kiểm tra khi rời ô** (`mode: "onTouched"`), không báo lỗi theo từng phím; lỗi biến mất ngay khi giá trị hợp lệ trở lại. `maxLength` lấy từ cùng schema với phần kiểm tra.
- Gửi biểu mẫu lỗi (cả lỗi do máy chủ trả về) thì **focus về ô sai đầu tiên** (`setError(..., { shouldFocus: true })`).
- Gợi ý cho trình duyệt và bàn phím điện thoại: `autoComplete` đúng nghĩa (`username`, `current-password`, `new-password`); ô tên đăng nhập `autoCapitalize="none"`, `autoCorrect="off"`, `spellCheck={false}`; `enterKeyHint` next/done. Ô nhập dữ liệu của **người khác** (admin tạo tài khoản) dùng `autoComplete="off"` để trình duyệt không điền thông tin của chính admin.
- Chọn một trong vài giá trị là **nhóm radio** có tên, không dùng `Tabs` cho việc chọn giá trị.
- Chọn tệp là **một điều khiển**: ô `input type="file"` ẩn nằm trong `label` làm vùng thả, có vòng focus; hiện đủ tên tệp và nút bỏ tệp.
- Nhãn đặt phía trên, 13/20 đậm 500, màu `text-secondary`. Có thể có icon dẫn đầu 16px màu `text-placeholder`.
- Lỗi: viền `danger` (`#B91C1C`), dòng lỗi 12.5px màu `danger` ngay dưới ô. Giữ nguyên dữ liệu người dùng đã nhập.

### 5.3 Chip và huy hiệu

- **Chip lọc:** dạng viên thuốc, cao 40px (web học viên) hoặc 32px (admin), viền `border`, có chấm màu 8px phía trước. Đang chọn: nền `primary-fill`, chữ `on-primary`.
- **Chip chỉ để đọc** (môn của một tổ hợp, nhãn trong bảng) dùng dạng tĩnh `Chip variant="tag"` với màu trung tính, không dùng chip lọc bấm được.
- **Chip nhỏ:** cao 32px, chữ 12.5px, nền `primary-50`, viền `primary-600`, chữ `primary-text`.
- **Chip trạng thái:** cao 24px, bo tròn, dùng cặp màu ở mục 2.4, có chấm màu.
- **Huy hiệu đếm:** viên thuốc, viền `border`, chữ `text-muted` (ví dụ "3 đề").

### 5.4 Thẻ

- Nền trắng, viền `border`, bo góc 12–16px, đệm 16–24px.
- Web học viên: thẻ bài thi có ảnh phía trên, tiêu đề 14/19 đậm 600 chữ hoa, dòng thông tin kèm icon, nút rộng hết thẻ. Hover dùng `shadow-lg`.
- Admin: thẻ chỉ dùng để gom nhóm, không có hiệu ứng hover.

### 5.5 Tab

Có ba kiểu, chọn theo việc tab làm gì chứ không theo hình dáng:

| Kiểu | Dùng khi | Thành phần (`packages/ui`) |
|---|---|---|
| Tab phân đoạn | Chuyển chế độ trong cùng một khối (đăng nhập/đăng ký, chế độ xem) | `Tabs variant="segmented"` |
| Tab nội dung | Đổi phần nội dung **trong cùng một trang**, không có đường dẫn riêng (các tab ở chi tiết tài khoản) | `Tabs variant="content"` |
| Tab điều hướng | Mỗi tab là **một trang con có đường dẫn riêng** (bốn trang con của Phân quyền: `/rbac/roles`, `/rbac/permissions`, `/rbac/users`, `/rbac/bulk-assign`) | `NavTabs` + `NavTab` bọc `<Link>` |

- **Tab phân đoạn:** thanh nền `bg-subtle` bo góc 12px, cao 48px; tab đang chọn nền trắng, `shadow-sm`, chữ `primary-text`.
- **Tab nội dung và tab điều hướng** trông giống nhau: tab đang chọn nền `primary-50`, chữ `primary-text`, bo góc 10px, cao 40px; tab khác chữ `#54545A`.
- **Dấu hiệu đang chọn (mọi kiểu):** ngoài màu phải có **gạch chân 2px** vẽ bằng `border` của phần tử giả `::after`, chỉ tồn tại ở tab đang chọn. Không dùng nền hay `box-shadow` làm dấu hiệu duy nhất (chế độ tương phản cao của hệ điều hành bỏ cả hai), không để viền trong suốt ở tab thường (chế độ đó tô lại thành gạch chân).
- **Độ đậm chữ không đổi theo trạng thái** (luôn 500): đổi độ đậm làm tab đổi bề rộng và đẩy các tab bên cạnh.
- **Tab điều hướng là liên kết, không phải `role="tablist"`:** nằm trong `<nav>` có tên, mục đang mở mang `aria-current="page"`. Nhờ vậy mở được ở tab trình duyệt mới và nút Back đúng nghĩa. Tiêu đề trang và thanh tab đặt ở `layout.tsx` của nhóm route để không dựng lại khi đổi trang con; phần nội dung đổi trang dùng hiệu ứng `animate-route-in` (mục 7).
- Thanh tab luôn **một hàng**; không đủ chỗ thì cuộn ngang ngay trong thanh, không xuống dòng (không truyền `flex-wrap`). Danh sách tab có đệm 4px bù bằng lề âm để vùng cuộn không cắt vòng focus, và thanh cuộn mảnh báo còn tab bị che (`NavTabs` và `TabsList` cùng mẫu).
- **Tab nội dung ghi vào `?tab=`** bằng `history.replaceState` để tải lại trang và gửi đường dẫn vẫn mở đúng tab; tab mặc định không ghi tham số.

### 5.6 Tiêu đề khối

Thanh dọc 4px màu `primary-600` (hoặc màu của nhóm) bên trái, tiêu đề `h3`, huy hiệu đếm, liên kết "Xem tất cả →" căn phải.

### 5.7 Thanh điều hướng (web học viên)

- Thanh trên cùng cao 32px, nền `primary-700`, chữ trắng 13px.
- Thanh menu cao 64px, nền trắng, viền dưới `border`; mục menu 14/20 đậm 500 màu `text-secondary`, mục đang chọn màu `primary-text`.
- Footer nền `primary-fill`, chữ `on-primary`.

### 5.8 Khung trang admin

```
┌──────────┬───────────────────────────────────────────────┐
│ Logo     │ Breadcrumb                 🔔 ☀ 文  Avatar ▾  │  topbar 56px; ☀ chuyển sáng/tối, 文 chọn ngôn ngữ (theo từng người); menu avatar: Hồ sơ, Đổi mật khẩu, Đăng xuất
│          ├───────────────────────────────────────────────┤
│ Sidebar  │ Tiêu đề trang                 [Nút chính]     │
│ 248px    │ Thanh lọc (thẻ trắng, bo 12px)                │
│ nền      │ ┌───────────────────────────────────────────┐ │
│ trắng    │ │ Bảng                                      │ │
│          │ └───────────────────────────────────────────┘ │
│          │ Phân trang                                    │
└──────────┴───────────────────────────────────────────────┘
```

- Sidebar: mục cao 40px, bo góc 8px, icon lucide 18px; mục đang chọn nền `primary-50`, chữ và icon `primary-text`, đậm 600. Tên nhóm menu dùng kiểu `overline`.
- Nền vùng nội dung `bg-page`.
- **Phạm vi màn hình:** web admin dùng được từ 360px tới 1920px (điện thoại, tablet, máy tính; UNI-2586, 2026-11-15); máy tính vẫn là nơi dùng chính. Web học viên cũng từ 360px. Không màn nào được làm cả trang cuộn ngang: nội dung rộng (bảng) cuộn trong khung của nó (mục 5.9).
- **Từ 1024px (`lg`):** sidebar 248px cố định bên trái như hình trên, không có nút menu.
- **Dưới 1024px:** sidebar ẩn. Đầu thanh trên có nút icon menu (lucide `Menu`, 40px, vùng bấm 44px trên màn cảm ứng, `aria-label` "Mở menu điều hướng", `aria-expanded`, `aria-controls`) mở **ngăn menu** trượt từ trái (`DrawerContent side="left"`, rộng tối đa 288px) chứa cùng danh sách với sidebar (`NavigationGroups`, không viết danh sách thứ hai). Ngăn đóng khi bấm một mục (kể cả trang đang mở), khi đổi trang, bằng Esc, bấm nền mờ hoặc nút ×, và tự đóng khi cửa sổ nới tới 1024px; focus về nút menu. Mục menu cao 44px trên màn cảm ứng.
- **Lề:** thanh trên và vùng nội dung đệm 16px (`px-4`, `p-4`) dưới 640px (`sm`), 24px từ 640px. Cột nội dung co theo cửa sổ (`min-w-0`), không có bề rộng tối thiểu. Bề rộng dùng được cho nội dung của một màn: 360px → 328px, 390px → 358px; 640–1023px → bề rộng cửa sổ − 48px; từ 1024px → bề rộng cửa sổ − 248px − 48px.
- **Thanh trên dưới 640px** phải vừa một hàng ở 360px: nút menu, breadcrumb chỉ còn trang hiện tại (`Breadcrumb collapseOnNarrow`, cắt bằng dấu ba chấm nếu vẫn dài), nút sáng/tối, nút ngôn ngữ, menu avatar chỉ còn avatar (tên đăng nhập hiện ở dòng đầu của menu). Từ 640px breadcrumb đủ các mục và menu avatar có tên đăng nhập kèm mũi tên.
- **Tiêu đề trang** (`PageHeader`): dưới 640px nút của màn xuống dưới tiêu đề và mô tả, căn trái, tự xuống dòng khi không đủ một hàng; từ 640px nút nằm cùng hàng với tiêu đề, căn phải. Màn chỉ truyền nút qua `actions`, không tự bọc thêm hàng.
- Menu và nút chỉ hiện khi người dùng có quyền tương ứng.
- Menu chính đợt 1: **Tổng quan** (Bảng điều khiển) · **Người dùng** (Tài khoản, Lớp học) · **Nội dung** (Cấu hình chung) · **Hệ thống** (Phân quyền, Tùy chỉnh giao diện; Nhật ký là tab trong chi tiết tài khoản).
- Bổ sung qua đợt 2 & đợt 3 (`apps/web-admin/src/lib/navigation.ts`):
  - **Nội dung:** Ngân hàng câu hỏi (`/questions`, `QUESTION.VIEW`), Bài thi (`/exams`, `EXAM.VIEW`), **Chấm tự luận** (`/grading`, `GRADING.VIEW`), Cấu hình chung (`/catalog`, `CATALOG.VIEW`).
  - **Thương mại:** Khuyến mãi (`/promotions`, `PROMOTION.VIEW`), Đơn hàng (`/orders`, `ORDER.VIEW`), Bộ đề (`/bundles`, `EXAM.VIEW`).
  - **Học viên:** Lượt thi (`/attempts`, `ATTEMPT.VIEW`).
  - Chức năng phụ vẫn là nút hoặc tab trong trang chi tiết: Cấp hàng loạt là nút trong Tài khoản; Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt là bốn tab của Phân quyền; Thông tin bán (`/exams/[id]/listing`) là tab trong khung soạn bài thi; Hồ sơ học tập (`?tab=learning`) là tab trong chi tiết tài khoản (`/users/[id]`).

### 5.9 Bảng (admin)

- Nằm trong thẻ trắng bo góc 12px. Hàng tiêu đề nền `#FAFAFB`, chữ 13px đậm 600 màu `text-muted`.
- Dòng cao 48px, viền dưới `border`, hover nền `bg-subtle`. Dòng đang chọn nền `primary-50`.
- **Cột Thao tác ưu tiên nút icon, không dùng nút chữ** (chốt 2026-11-13). Mỗi nút là một icon lucide 18px trong vùng bấm 40–44px, căn phải, có **tooltip** hiện tên thao tác khi rê chuột hoặc khi người dùng đưa focus tới bằng phím điều hướng (Tab, mũi tên, Home/End), và có `aria-label` nêu rõ thao tác kèm đối tượng ("Sửa vai trò Vận hành").
- **Tooltip không tự mở khi focus bị trả về** (UNI-2588): đóng hộp thoại, ngăn hay menu thì focus về nút đã mở nó, nhưng tooltip của nút không hiện; sau khi bấm hoặc chạm cũng vậy. `Tooltip` của kit tự lo việc này (ghi nhớ thao tác gần nhất là phím điều hướng hay không), màn không phải làm gì thêm.
- Icon dùng thống nhất: bút chì = Sửa, thùng rác = Xóa, mắt = Xem/Chi tiết, hai mũi tên ngược chiều = Chuyển, người có dấu trừ = Loại khỏi lớp, vòng tròn có dấu trừ = Gỡ quyền. Thao tác nguy hiểm dùng màu `danger` (`#B91C1C`); thao tác bị khóa hiện mờ và tooltip nêu lý do.
- Tối đa ba icon trên một dòng; nhiều hơn thì gom phần còn lại vào menu "⋯".
- Phía trên bảng: tổng số bản ghi theo bộ lọc. Phía dưới: phân trang và chọn số dòng mỗi trang.
- Trạng thái rỗng: icon, một câu giải thích, nút hành động (nếu có quyền).
- **Đang tải:** khung chờ (`loading`) chỉ dùng cho **lần tải đầu**. Khi đổi bộ lọc, trang hoặc sắp xếp, bảng **giữ dữ liệu đang hiện**, làm mờ nhẹ và báo `aria-busy` (`refreshing`) cho tới khi dữ liệu mới về; không thay bằng khung chờ, để chiều cao bảng và vị trí cột không nhảy. Truy vấn danh sách dùng `placeholderData: keepPreviousWhileSameScope(queryKey)` (`apps/web-admin/src/lib/api/keep-previous.ts`): chỉ giữ khi tham số lọc đổi, đổi đối tượng (lớp khác, nhóm khác, quyền khác) thì hiện khung chờ như lần đầu.
- **Bảng rộng hơn khung:** cuộn ngang trong khung của bảng, không làm cả trang cuộn ngang. Thành phần `Table` tự biến khung thành vùng có tên, nhận focus bàn phím (vòng focus nằm trong khung) khi và chỉ khi bảng thực sự tràn.
- **Bảng nhiều cột:** ghim cột nhận diện (đầu) và cột Thao tác (cuối) bằng `stickyColumns={{ start: true, end: true }}`; ô ghim có nền đặc nên nội dung cuộn qua không lộ ra dưới. Cột ít quan trọng **ẩn theo bề rộng vùng chứa**: đặt `@container` lên phần tử cha của bảng, đánh dấu cột bằng `data-col` và ẩn bằng biến thể container. Chọn cột hiện theo bề rộng thẻ chứa bảng, không theo bề rộng màn hình, để không ô nào nằm dưới cột Thao tác.
  - Mỗi cột giữ bề rộng tối thiểu; tiêu đề cột tối đa hai dòng. Cột bị ẩn thì dữ liệu của nó hiện thành **dòng tóm tắt** (tối đa hai dòng) dưới tên ở cột đầu, bản đầy đủ có trong ngăn chi tiết.
  - Cột Thao tác chỉ ghim từ ngưỡng đủ rộng; dưới ngưỡng, bảng cuộn ngang trong khung (ở trên).
- **Bảng ở khung hẹp thành danh sách thẻ** (UNI-2587, 2026-11-15). Khi khung chứa bảng hẹp hơn ngưỡng, `Table` hiện danh sách thẻ thay cho bảng: truyền `cards={{ render, sortLabels }}` (mặc định ngưỡng 640px của khung, đổi bằng `breakpoint`). `Table` tự đo khung và chỉ dựng một trong hai dạng, nên nội dung không lặp trong DOM. Mọi bảng của web admin có dạng thẻ.
  - **Thành phần dùng chung** (`@thi360/ui`, `list-card.tsx`), không màn nào tự viết thẻ: `cards.render` trả về `ListCard` (`title`, `actions`, `caption`, `status`, `tags`, `meta`, `children` cho nội dung thêm như lý do lỗi). Tiêu đề là `ListCardTitleLink` (truyền `linkComponent={Link}` của `next/link`, như `Breadcrumb`) khi dòng có trang chi tiết, `ListCardTitleButton` khi chi tiết mở trong ngăn hoặc hộp thoại, hoặc chữ thường. Chip phân loại chỉ để đọc là `ListCardTag` (trung tính, cao 24px). Bảng tự viết bằng `<table>` (cột tệp mẫu, lỗi nhập Excel, tab Quyền, bảng giá trị kéo thả của Cấu hình chung) dùng `useCardMode(true, ngưỡng)` để đo khung, rồi `CardList` + `CardListItem` (+ `CardListSkeletonItem` khi tải) + `ListCard`.
  - **Bố cục một thẻ** (đệm 16px, các thẻ ngăn bằng đường kẻ, nằm trong thẻ trắng chứa bảng như cũ):
    1. Hàng đầu: **tiêu đề** (tên hoặc mã chính của dòng, đậm 600, tối đa hai dòng; là liên kết khi dòng có trang chi tiết) và bên phải là **thao tác** (nút "⋯" mở menu, hoặc tối đa hai nút icon).
    2. Dòng phụ chữ `caption` màu `text-muted`: định danh thứ hai (họ tên, mã, email).
    3. Hàng chip: chip trạng thái **luôn hiện** (kể cả Hoạt động), sau đó các chip phân loại (vai trò, nguồn, kiểu); tự xuống dòng.
    4. Nội dung thêm nếu có (lý do lỗi màu `danger`, số đếm), rồi thông tin thêm dạng "Nhãn: giá trị", nối bằng " · ", chữ `caption`.
  - **Cả thẻ bấm được** để mở chi tiết: liên kết tiêu đề có `after:absolute after:inset-0` (thẻ đã là `relative`), tên truy cập vẫn chỉ là tiêu đề; nút, menu, ô chọn trong thẻ có `relative z-1` để nằm trên vùng bấm đó. Thẻ không thêm nút "Xem"/"Chi tiết" riêng khi tiêu đề đã mở chi tiết (hai điểm dừng Tab cho một việc).
  - **Chọn dòng:** ô chọn ở đầu thẻ; ô "Chọn tất cả" có nhãn nhìn thấy được ở hàng trên danh sách. **Sắp xếp:** ô chọn "Sắp xếp theo" ở cùng hàng, mỗi cột sắp xếp được có "tăng dần"/"giảm dần" (tên cột lấy từ `header` chuỗi hoặc `meta.label`).
  - Tải, tải lại, rỗng, lỗi giống bảng: thẻ khung xương theo `loadingRows`, giữ thẻ cũ làm mờ khi tải lại.
  - **Chọn ngưỡng:** dạng thẻ khi khung không đủ chỗ cho các cột chính của bảng mà không cuộn ngang. Mặc định 640px; bảng nằm trong khung hẹp sẵn trên máy tính (cột phải của Cấu hình chung, cột phải của Người dùng theo quyền) chọn ngưỡng theo bề rộng khung đó. Giữa ngưỡng thẻ và bề rộng đủ cho mọi cột, bảng vẫn ẩn bớt cột phụ bằng `@max-[…]:[&_:is(th,td):has([data-col=x])]:hidden`.
  - Cột chữ có thể xuống dòng phải có bề rộng tối thiểu để bảng cuộn trong khung thay vì ép còn một chữ mỗi dòng. Thông báo rỗng và lỗi của bảng luôn nằm giữa phần nhìn thấy của khung.
- Khung chờ lần đầu có số dòng bằng số dòng mỗi trang (`loadingRows={pageSize}`). Cột chưa sắp xếp không mang `aria-sort="none"`.
- **Phân trang** tự xuống dòng khi không đủ một hàng.
- **Thanh thao tác hàng loạt** (khi có dòng được chọn) nằm trong chỗ **giữ sẵn** phía trên bảng, ẩn bằng `invisible` + `inert` khi chưa chọn, không chèn rồi gỡ khỏi DOM (bảng không nhảy). Thanh là `role="group"` có tên, không dùng `role="toolbar"`. Khung hẹp (dưới 40rem): hai hàng, hàng trên số dòng đã chọn, "Bỏ chọn" và nút trợ giúp chỉ có icon, hàng dưới các nút thao tác trên một hàng cuộn ngang; chiều cao chỗ giữ sẵn không đổi theo việc có chọn hay không.
- **Tìm kiếm trong danh sách** chờ người dùng ngừng gõ 300ms mới gửi; xóa ô tìm thì áp dụng ngay. Trong lúc chờ kết quả mới vẫn giữ kết quả cũ (mờ nhẹ, `aria-busy`).
- **Mất kết nối:** truy vấn bị tạm dừng vì offline thì hiện trạng thái "Không có kết nối" kèm nút Thử lại, không hiện dòng và tổng số của bộ lọc cũ như thể là kết quả mới.

### 5.10 Dialog, drawer, thông báo

- **Dialog:** bo góc 24px, `shadow-xl`; nút căn phải, nút chính ở ngoài cùng bên phải. Ba cỡ của `DialogContent`: `md` 480px (biểu mẫu ngắn, xác nhận), `lg` 720px (bảng chọn, biểu mẫu vừa), `xl` 920px (biểu mẫu dài chia hai cột).
- **Biểu mẫu trong dialog phải hiện đủ mà không cuộn ở 1366×768 và 1280×720.** Một cột không đủ thì dùng cỡ `xl` và chia hai cột từ bề rộng `md`: cột trái là nhóm trường chính (ví dụ thông tin đăng nhập), cột phải là nhóm còn lại; thứ tự trong DOM (và thứ tự Tab) đi hết cột trái rồi sang cột phải; màn hình hẹp xếp lại một cột và cuộn trong dialog. Danh sách quy tắc dài (quy tắc mật khẩu) xếp hai cột (`PasswordField rulesColumns={2}`). Tiêu đề và hàng nút luôn dính mép trên/dưới, nút đóng (×) luôn bấm được.
- **Dialog xác nhận hành động nhạy cảm** (khóa, xóa, reset, ngừng sử dụng): nêu rõ đối tượng và số bản ghi bị ảnh hưởng (hàng loạt: nêu tối đa 5 tên rồi "và N khác"), có ô **lý do** khi quy tắc yêu cầu, nút xác nhận kiểu Nguy hiểm. Nút xác nhận **luôn bấm được**: bấm khi thiếu lý do thì ô lý do báo lỗi và nhận focus, không khóa nút (người dùng không biết vì sao nút bị khóa).
- **Lỗi của thao tác trong dialog hiện ngay trong dialog** (`ConfirmDialog error`, `role="alert"`), không bắn toast nằm sau lớp phủ.
- **Focus đầu tiên khi mở:** dialog biểu mẫu focus vào ô bắt buộc đầu tiên; ô đó còn đang tải dữ liệu thì focus vào chính dialog rồi chuyển sang ô khi dữ liệu về. Drawer chi tiết focus vào tab đang chọn.
- **Dialog có dữ liệu đã nhập** hỏi lại trước khi đóng bằng Esc, bấm ra ngoài hoặc Hủy.
- **Focus trả về** nút đã mở dialog/drawer: `Dialog` và `Drawer` của kit tự làm, không cần tự viết. Khi nút đó đã biến mất (dòng bị xóa, thanh chọn đã ẩn), chuyển focus có chủ đích tới một đích có tên (ô chọn tất cả, ô tìm kiếm, tiêu đề bước mới), không để focus rơi về `<body>`.
- **Drawer:** trượt từ phải, rộng 520px, dùng cho chi tiết (nơi đang dùng, lịch sử thay đổi); tiêu đề dính mép trên, hàng nút đặt trong `DrawerFooter` dính mép dưới, phần giữa cuộn. `DrawerContent side`: `right` (mặc định, chi tiết) hoặc `left` (ngăn menu điều hướng của admin dưới 1024px, mục 5.8); drawer trượt ra từ đúng cạnh đó (chỉ trong `motion-safe:`, hiệu ứng `drawer-in`/`drawer-out` và `drawer-in-left`/`drawer-out-left`) và bo góc 16px ở cạnh trong, phía nội dung trang.
- **Màn hẹp (dưới 640px):** dialog và drawer đệm 16px thay vì 24px; dialog rộng bằng cửa sổ trừ 32px. Hàng nút của `DialogFooter`/`DrawerFooter` xuống dòng khi không đủ chỗ, vẫn căn phải. Biểu mẫu hai cột của cỡ `xl` xếp một cột dưới 768px (`grid-cols-1 md:grid-cols-2`).
- **Toast:** góc trên phải, tự ẩn sau 4 giây; lỗi không tự ẩn. Toast có tông theo trạng thái (thành công, lỗi, cảnh báo, thông tin).

### 5.11 Bố cục hai khu vực (màn Cấu hình chung)

Trái 264px: danh sách nhóm trong thẻ trắng, có ô tìm kiếm; mỗi dòng cao 56px gồm ô icon 32px theo kiểu nhóm (danh sách, cây, tổ hợp), tên đậm 600, mã 12.5px `text-muted` ở dòng phụ, số giá trị căn phải; dòng đang chọn nền `primary-50` kèm thanh dọc 3px `primary-600`. Danh sách chia hai phần có tiêu đề kiểu `overline`: **Hệ thống** (kèm hình khóa) và **Tùy chỉnh**. Tiêu đề thẻ bên phải có chip loại: Hệ thống (nền `bg-subtle`, có hình khóa) hoặc Tùy chỉnh (nền `primary-50`). Phải: thẻ giá trị của nhóm đang chọn. Khi chưa chọn nhóm, khu vực phải hiện hướng dẫn và ẩn nút thêm.

**Nhóm kiểu cây** (spec mục 9.10): thẻ phải chia hai phần, cây ở giữa và ô chi tiết 340px có viền trái.

- **Màn hẹp:** khi thẻ chứa hẹp hơn 672px, hai khu vực xếp chồng và danh sách nhóm bên trái thay bằng **một ô chọn** "Danh mục đang xem" (nhóm Hệ thống trước), để thẻ giá trị nằm ngay dưới; từ 672px trở lên là danh sách 264px như trên. Bảng giá trị ở thẻ dưới 416px là danh sách thẻ (mục 5.9): cột đổi thứ tự (lên, tay kéo, xuống) ở đầu thẻ khi được sắp xếp; tiêu đề gồm chip mã (xuống dòng sau dấu `_`) và tên, bấm tên mở ngăn chi tiết; Sửa/Ngừng/Xóa trong menu "⋯"; dòng phụ là tên tiếng Anh (hoặc mã thi và các môn của tổ hợp); chip trạng thái rồi chip phiên bản; cuối cùng là giá trị.
- **Cây ở khung hẹp** (cây hẹp hơn 22rem): thụt 12px mỗi cấp thay vì 28px, tên một dòng riêng (tối đa hai dòng), mã, trạng thái, số nút xuống dòng thứ hai; nút "+" thêm cấp con ẩn đi, thao tác đó vẫn có trong menu "⋯".
- **Bàn phím trên cây** (`TreeView`): cả cây là **một điểm dừng Tab**, đi giữa các dòng bằng phím mũi tên; nút trong dòng không nằm trong thứ tự Tab. Shift+F10 hoặc phím menu mở thao tác của dòng (`onRowMenu`), có một dòng gợi ý nhìn thấy được nói điều này, đóng menu thì focus về lại dòng. Tên dòng đọc cả mã lẫn tên; tên dài xuống tối đa hai dòng.
- **Kéo thả** để sắp xếp: chỉ tay nắm mang `touch-none select-none` và vòng focus của kit (phần còn lại của dòng vẫn cuộn trang bằng cảm ứng); khi bật giảm chuyển động thì không có hiệu ứng trượt.

- Dòng của cây cao tối thiểu 44px, thụt 28px mỗi cấp, đường kẻ dọc 1px `border` nối cấp; nút mở/đóng 28px (mũi tên xoay 90° khi mở); mã trong ô nền `bg-subtle` bo 6px, chữ 12.5px đậm 600, không xuống dòng; số nút lá bên dưới căn phải, màu `text-muted`.
- Cấp phân biệt bằng chữ: cấp gốc 15px đậm 600, cấp giữa 14px đậm 500, nút lá 14px thường màu `#3A3A42`. Không gắn nhãn cấp trên từng dòng.
- Chỉ hiện chip trạng thái khi khác Hoạt động. Nút đã ngừng dùng chữ `text-muted`.
- Ô chi tiết: tên cấp kiểu `overline`, tiêu đề 18px, đường dẫn 12.5px, danh sách thông tin dạng nhãn trên, giá trị dưới; nút thao tác xếp dọc, nút xóa kiểu nguy hiểm ở cuối.

### 5.12 Trang đăng nhập

Khung giữa trang, bo góc 24px, `shadow-xl`, chia đôi: trái nền `hero-light` có logo, khẩu hiệu, ba ý chính; phải là biểu mẫu với tab phân đoạn, ô nhập cao 48px có icon, nút chính `xl`. Nút "Đăng nhập với Google" (kiểu Phụ, cao 48px) chỉ hiện khi tính năng được bật.

### 5.13 Quy ước dùng chung cho mọi màn

Các quy ước dưới đây rút từ đợt rà soát theo bộ hướng dẫn modern-web-guidance (2026-11-14). Màn mới và màn sửa đều áp dụng; đã có thành phần hoặc lớp tiện ích thì dùng lại, không tự viết bản khác.

- **Chữ dài:** tên, nhãn có thể dài thì cho xuống **tối đa hai dòng** (`line-clamp-2`), không cắt một dòng bằng dấu ba chấm khi người dùng không còn cách nào đọc đủ. Không dựa vào thuộc tính `title` để lộ phần bị cắt (bàn phím và màn hình cảm ứng không tới được). Chỉ cắt một dòng ở nơi chật cố định (ô bảng, mã) và khi nội dung đầy đủ có ở chỗ khác trên cùng màn hình.
- **Vùng cuộn dọc có chiều cao giới hạn** (danh sách chọn, cột bên): thêm lớp `scroll-shadows-y` lên chính phần tử cuộn để có bóng mờ báo còn nội dung ở phía còn cuộn được. Lớp này là CSS thuần (`packages/ui/src/styles/utilities.css`), màu lấy từ token `scroll-shadow`. Mục con trong vùng cuộn để nền trong suốt ở trạng thái thường.
- **Thanh cuộn của trang:** trang luôn giữ chỗ cho thanh cuộn dọc (`scrollbar-gutter: stable` trong `globals.css` của hai web), để nội dung dài/ngắn hay việc mở dialog không làm trang lệch ngang.
- **Bố cục nhiều cột chia theo bề rộng vùng chứa, không theo bề rộng màn hình:** từ 1024px vùng nội dung của admin hẹp hơn màn hình 248px vì có sidebar, dưới 1024px thì bằng màn hình trừ lề (mục 5.8). Đặt `@container` lên khối chứa và dùng biến thể container của Tailwind (`@5xl:` …) cho các cột bên trong; chọn ngưỡng sao cho cột chứa bảng còn đủ rộng cho bảng của nó. Dưới ngưỡng thì xếp chồng.
- **Đường dẫn trang** viết bằng tiếng Anh, trùng tên thư mục feature và tên tài nguyên của API (`/users`, `/classes`, `/catalog`, `/rbac`, `/appearance`, `/login`, `/change-password`). Phần có nội dung và dữ liệu riêng là **trang con** (`/users/bulk-import`, `/rbac/roles`), không phải giá trị query. Query chỉ dùng cho bộ lọc và lựa chọn trong trang (`?permission=USER.VIEW`, `?group=SUBJECT`), tên và giá trị cũng không dùng tiếng Việt không dấu.
- **Trạng thái đang chọn, đang mở, lỗi** không truyền đạt chỉ bằng màu hay nền: luôn có thêm hình (gạch chân, thanh dọc, icon) hoặc chữ, vẽ bằng `border`/`outline` để còn thấy ở chế độ tương phản cao.

Bổ sung sau đợt nâng cấp các màn (UNI-2581, 2026-11-15):

- **Thanh lọc ở khung hẹp** (đặt `@container` lên thẻ lọc): ô tìm kiếm chiếm cả hàng; các ô chọn xếp hai cột (một cột khi thẻ hẹp hơn khoảng 300px); ô đánh dấu và nút Xóa bộ lọc chung một hàng tự xuống dòng. Từ bề rộng mà cả dải vừa một hàng, trở lại dải `flex-wrap` như trên máy tính. DOM và thứ tự Tab giống nhau ở mọi bề rộng. Hiện có hai bản: `FILTER_FORM`/`FILTER_SEARCH`/`FILTER_SELECT`/`FILTER_TOGGLES` (`features/users/components/list-filters.ts`, Tài khoản và Lớp học) và `FILTER_GRID` (`features/rbac/styles.ts`); màn mới dùng bản của Tài khoản, khi có màn thứ ba thì chuyển thành thành phần chung của kit.
- **Mã và định danh dài** (mã quyền, mã danh mục) xuống dòng ở bất kỳ ký tự nào (`wrap-anywhere`), không cắt bằng dấu ba chấm.
- **Lưới tự chia cột** dùng `minmax(min(X, 100%), 1fr)` để ô co lại khi vùng chứa hẹp hơn X, không tràn.
- **Thanh bước ở khung hẹp:** bước đang làm có tên và giãn ra, các bước khác chỉ còn số (tên vẫn có cho trình đọc màn hình).
- **Phần tử không tự truy vấn được chính nó:** `@container` đặt trên phần tử cha của khối cần đổi bố cục.

- **Vòng focus** luôn lấy từ `focusRing` / `focusRingInset` của kit (xuất từ `@thi360/ui`), không viết tay `focus-visible:ring-*`. Tắt viền ngoài bằng `outline-hidden`, không bằng `outline-none` (mục 8).
- **Thông báo cho trình đọc màn hình:** kết quả của thao tác không đồng bộ (số kết quả sau khi lọc/tìm, sắp xếp, số dòng chọn, kết quả kiểm tra tên đăng nhập, bắt đầu/kết thúc một tác vụ dài) đi qua **một** `LiveStatus` cho mỗi khu vực, luôn có mặt trong DOM, chỉ đổi `message`. Không làm bộ đếm tiến độ đọc lên từng bước.
- **Tiến độ** dùng `<progress>` gốc của trình duyệt, tô bằng token.
- **Mỗi trang có tiêu đề tab riêng** theo mẫu "‹trang› · Thi 360" (trang chi tiết: "‹đối tượng› · ‹mục› · Thi 360").
- **Một `<main>` mỗi trang.** Trang trạng thái (403, 404) hiện trong khung admin dùng `StatusPage embedded`; `EmptyState` chọn cấp tiêu đề bằng `headingLevel` cho đúng thứ bậc của trang.
- **Lỗi lưu dưới dạng mã hoặc khóa dịch**, dịch lúc hiển thị, để lỗi đang hiện đổi theo ngôn ngữ vừa chọn.
- **Nút bật/tắt** nêu trạng thái **một lần**: hoặc nhãn hành động đổi theo trạng thái ("Chuyển sang chế độ tối"), hoặc nhãn cố định kèm `aria-pressed`, không dùng cả hai. Thành phần chung dùng kiểu nhãn đổi theo trạng thái.
- **Vùng xem trước chỉ để nhìn** (xem trước giao diện) mang `inert` và `aria-hidden`, không nhận focus.
- **Trang đổi mật khẩu** mở từ menu tài khoản dùng `?source=account-menu` để hiện lối "Quay lại"; luồng bắt buộc đổi mật khẩu không có lối này.

### 5.14 Màn đợt 2: Ngân hàng câu hỏi, Bài thi và Hàng chờ chấm (API thật)

Từ QA đợt 2 (UNI-2621) Ngân hàng câu hỏi, Nhập câu hỏi và Bài thi chạy trên API thật; tới đợt 3 (FA-4), Hàng chờ chấm (`/grading`) cũng đã nối API thật, toàn bộ cờ và lớp dữ liệu mẫu đã được gỡ bỏ hoàn toàn.

- **Bảo vệ theo quyền:** các trang nằm sau `PermissionGate` (`features/session/permission-gate.tsx`) ở `layout.tsx`: `/questions` cần `QUESTION.VIEW`, `/exams` cần `EXAM.VIEW`, `/grading` cần `GRADING.VIEW`. Thiếu quyền thì hiện trang 403.
- **Dữ liệu:** giao diện chỉ gọi hook trong `features/<x>/queries.ts`; hook gọi `features/<x>/api.ts` qua `@thi360/api-client`, kiểu trong `types.ts` là bí danh của client; mọi lệnh ghi câu hỏi và cấu trúc gửi `expectedRevision` (lệch thì trả 412 `QUESTION_REVISION_CONFLICT` hoặc `EXAM_REVISION_CONFLICT`); kiểm thử đơn vị dùng client giả (`src/test/fake-api.ts`, dự án vitest `fake-client`).
- **Bốn trạng thái:** mỗi màn có đủ bốn trạng thái tải đầu, rỗng, lỗi, có dữ liệu (kèm trạng thái ngoại tuyến theo mục 5.9).
- **Mã quyền:** menu và trang dùng `QUESTION.VIEW`, `EXAM.VIEW`, `GRADING.VIEW`; các thao tác ghi kiểm tra theo `QUESTION.MODIFY`, `EXAM.MODIFY`, `EXAM.PUBLISH`, `GRADING.MODIFY` (backend trả 403 khi thiếu quyền).
- **Trang soạn bài thi** `/exams/[id]` có khung chung `ExamEditorLayout` (tiêu đề, chip trạng thái, dải tóm tắt `<dl>`, `NavTabs` năm trang con: `/exams/[id]`, `/structure`, `/listing`, `/review`, `/versions`). Trang con đọc bài thi bằng `useExam(id)`.
- **Chọn kiến thức (MAPID):** dùng `KnowledgePickerDialog` (`features/content/components`) cho mọi nơi chọn nút của cây Cấu trúc kiến thức; câu hỏi chỉ tick cấp Kiến thức và có một kiến thức chính, phạm vi khối của bài thi cho tick cả Chương và Bài (`selectableLevels`, `withPrimary={false}`).

Quy ước rút ra khi làm các màn đợt 2 (UNI-2590), áp dụng cho mọi màn sau:

- **Tạo đối tượng có nhiều phần** (cấu trúc, phân bổ, nhiều quy tắc phụ thuộc nhau, ví dụ Tạo bài thi `/exams/new`) dùng **trang riêng**, không dùng hộp thoại: các khối đánh số theo thứ tự nhập, cột **Tóm tắt** bên phải dính khi cuộn (xuống dưới biểu mẫu khi khung hẹp) gồm các con số tính trực tiếp và danh sách "Cần xử lý" mà mỗi mục đưa focus tới ô lỗi. Hộp thoại chỉ cho biểu mẫu ngắn (mục 5.10).
- **Đổi lựa chọn làm mất phần đã nhập** (nạp lại mẫu khi đổi kỳ thi, phạm vi, phần, môn hoặc phiên bản năm của cấu trúc): hỏi xác nhận, nêu rõ phần sẽ bị thay. Ở trang Tạo bài thi hỏi ngay trong trang; ở Thông tin chung và bước Cấu trúc dùng hộp thoại liệt kê các phần sẽ nạp lại (`ReloadStructureDialog`) và nói rõ thay đổi chưa lưu cũng mất; chưa sửa gì thì nạp luôn.
- **Danh sách có trạng thái** (Ngân hàng câu hỏi): dải trạng thái kèm số lượng mỗi trạng thái đứng đầu thanh lọc; bộ lọc ít dùng gom vào "Bộ lọc khác"; bộ lọc đang áp dụng hiện thành chip "Đang lọc" bỏ được từng cái, kèm "Xóa tất cả bộ lọc". Mọi bộ lọc nằm trên URL.
- **Trang chi tiết có trang con** (câu hỏi: Nội dung, Xem trước, Nơi đang dùng, Lịch sử phiên bản; bài thi: năm trang con) dùng layout chung của đoạn đường dẫn (nhóm route như `questions/[id]/(detail)/layout.tsx`) và `NavTabs`; trang Sửa nằm ngoài nhóm để không có thanh tab.
- **Luồng nhiều bước** (Nhập câu hỏi từ tệp, Cấp tài khoản hàng loạt): đổi bước thì focus về tiêu đề bước; lỗi chặn bước tiếp hiện thành bảng tóm tắt lỗi nhận focus; báo cáo lỗi tải về là CSV tạo ở trình duyệt.
- **Cột của `Table` phải ổn định** (`useMemo` hoặc khai báo ngoài component): mảng `columns` mới ở mỗi lần render làm bảng dựng lại ô, mất focus và hỏng việc trả focus khi đóng hộp thoại. Trạng thái của dòng (đang chạy, đang chọn) đưa vào ô qua context hoặc dữ liệu dòng, không qua closure của `columns`.

Quy ước thêm ở đợt 7 kỳ thi và chấm tự luận (UNI-2599):

- **Câu chấm tay:** câu và khối dạng Tự luận mang nhãn "Chấm tay" (icon kèm chữ, không chỉ màu) ở mọi nơi hiện câu: soạn, chi tiết, khối của cấu trúc, xem trước, phiên bản. Lượt thi còn câu chưa chấm ghi "Chờ chấm" (tiếng Anh "Awaiting grading"; mã trạng thái là `PENDING_REVIEW`), có điểm tự luận rồi mới ghi "Đã chấm" (`SCORED` trên `web-student` `/result`, `/history` và tab Hồ sơ học tập admin; chip trạng thái `SCORED` trên bảng quản trị `/attempts` của `web-admin` ghi "Đã có điểm").
- **Chờ chuyên gia xác nhận:** số liệu chờ chuyên gia (số câu tổ hợp QDA, BCA do tổ hợp sinh hoặc mới sửa, năm cấu trúc khác năm ma trận) hiện chip trạng thái "Chờ chuyên gia xác nhận" kèm nút "Xác nhận"; xác nhận xong chip đổi thành "Chuyên gia đã xác nhận". Kiểm tra cấu hình chặn công bố khi còn mục chờ. Cách chia 17–17–16 của phương án Khoa học HSA đã có quy định (B10) nên không mang chip này.
- **Thời lượng chung:** bài dùng một thời lượng cho cả bài thì phần không có ô thời lượng; chỗ hiện thời lượng phần ghi chữ chỉ đọc "… phút, dùng chung cả bài", danh sách ghi "… phút chung".
- **Bảng điểm sửa được** (bảng điểm câu Đúng/Sai, tiêu chí chấm): mỗi ô là một ô nhập số nhận dấu phẩy hoặc dấu chấm như ô Điểm; lỗi hiện ngay dưới ô sai (không phải số, nhỏ hơn ô trước, ô cuối khác điểm của câu), dòng tổng nói rõ đã khớp chưa.
- **Chọn tệp:** không để lộ nút chọn tệp gốc của trình duyệt (chữ theo ngôn ngữ trình duyệt). Ẩn `input type="file"` bằng `sr-only` + `peer`, dùng `label` mang `buttonVariants` của kit làm nút, kèm dòng tên tệp đã chọn hoặc "Chưa chọn …"; vòng focus lấy từ `peer-focus-visible`.
- **Màn chấm** (`/grading/[attemptId]`): đề và bài làm bên trái, tiêu chí chấm bên phải, xếp chồng khi khung hẹp (`@4xl`); thanh câu hỏi có điểm tạm từng câu; hoàn tất chấm hỏi xác nhận trong trang và đưa focus tới ô thiếu điểm đầu tiên.

Quy ước thêm ở ma trận theo môn và bước 1 theo phạm vi (UNI-2613):

- **Bước 1 Tạo bài thi** chọn Phạm vi bài thi (Toàn bài, Theo phần, Theo môn; kỳ thi chỉ có một phạm vi thì hiện một dòng chữ thay nhóm radio), rồi Phần thi (Theo phần) hoặc Môn (Theo môn), có Hướng dẫn làm bài (trình soạn `RichTextEditor`, không nhận ảnh, mục 5.15). Không có ô chọn mẫu hay năm: hệ thống nạp mẫu mới nhất và không hiện dòng "đã nạp mẫu", năm cấu trúc, năm ma trận, chip phiên bản hay hộp "Lưu ý của mẫu"; phiên bản năm chọn ở bước Cấu trúc. `TemplateNotes` chỉ còn cảnh báo năm cấu trúc khác năm ma trận (kèm ô xác nhận chuyên môn ở Thông tin chung). Tab Thông tin chung có lại bảng "Cấu trúc tổng quan" sửa được như bước 1, dựng từ cấu trúc đã lưu (`ExamOverviewTable`, cùng `validateOverview`): Lưu ghi cấu trúc trước (`PUT /exams/{id}/structure`, `expectedRevision` của cấu trúc) rồi mới ghi thông tin (`PATCH /exams/{id}`); PUT lỗi thì không gửi gì khác và giữ giá trị đã gõ, PATCH lỗi sau PUT thành công thì báo phần nào đã lưu. Bảng không tự đổi số câu của khối lấy câu hay ma trận (bước Cấu trúc báo lệch); mẫu khóa số câu thì ô số câu chỉ đọc; đang chờ xác nhận đổi phạm vi thì bảng hiện số của mẫu mới, chỉ đọc.
- **Bước Cấu trúc:** ô "Phiên bản cấu trúc" (`StructureVersionPicker`) nằm trong thẻ tổng hợp, chỉ hiện khi có mẫu khớp bài; một phiên bản thì khóa ô. Mỗi phần có Quy tắc tham gia (làm tất cả, hoặc chọn k phương án), Số câu trên đề, Số câu phải làm (mặc định bằng số câu trên đề) và Điểm tối đa của phần để trống được.
- **Biểu mẫu theo môn (`subject-forms`):** `selection.ts` chọn dạng hiển thị theo thứ tự ưu tiên: bài `buildMode === "REVIEW"` khớp `rendersReviewPanel` mở `ReviewSubjectPanel` (`ON`, không hiện thông báo), section thiếu hoặc ngoài bảng ánh xạ mở `MD` (`ExamStructureSubjectPanel` + `BlockEditor`, không hiện thông báo), cặp `(kindCode, subjectCode)` bị tắt mở `MD`, cặp đã bật kiểm tra `fitsForm` — không vừa thì lùi về `MD` kèm `FullFormNotice`, đạt hết mới mở biểu mẫu gọn (`MT` hoặc `DS`). `registry.ts` đăng ký 75 cặp chuẩn trên 9 kỳ thi (`HSA`/`QDA`/`BCA` mỗi loại 6 `MT` + 2 `DS`; `VACT` 2 `MT` + 2 `DS`; `SP2E` 9 `DS`; `SPT` 12 `DS`; `THPTQG` 12 `DS`; `HSCA` 6 `DS`; `VSAT` 8 `DS`, toàn bộ đang bật) và 4 alias khi tra cứu (`TIN`→`INFORMATICS`, `GDKTPL`→`ECON_LAW`, `CNCN`→`TECH_INDUSTRY`, `CNNN`→`TECH_AGRICULTURE`, chỉ áp dụng khi kỳ thi có mã chuẩn tương ứng và không sửa `subjectCode` đã lưu). Lựa chọn biểu mẫu được ghim theo phiên tại `ExamStructureView` (`applyEffectivePins` sau `normalizeHsaParts`), chỉ tính lại khi mở/tải lại đề, bấm Hủy thay đổi (hoặc Bỏ thay đổi trong hộp thoại rời môn, `discardAll`) hoặc xác nhận đổi phiên bản cấu trúc (`StructureVersionPicker`). `raw-input-store.ts` giữ trạng thái đang gõ (số đang nhập dở, ô không hợp lệ); `hasUnsavedChanges = draftDirty || rawEntries.length > 0` chi phối `beforeunload`, `useUnsavedChangesGuard`, hộp thoại rời môn (`ExamStructureLeaveDialog`), khóa nút Hủy thay đổi/Lưu và chặn refetch nền ghi đè bản nháp (chỉ `draftDirty` điều khiển `describeStructureChanges` và gửi `PUT`). Mọi thao tác Lưu, Lưu và chuyển, tự động lưu, Kiểm tra và Tiếp tục gọi `formBlockingIssues` quét toàn bộ môn `MT`/`DS` (kể cả môn đang đóng, trừ `DEFAULT`/`REVIEW`); khi có lỗi, `navigation.ts` (`formTarget` + `focusFormTarget`, dùng `ROW_TARGET_EVENT` để mở trình sửa dòng `DS`) điều hướng và đưa focus tới ô lỗi đầu tiên (nếu phát sinh từ hộp thoại rời môn thì chờ bấm "Ở lại" qua `pendingBlockedIssue`). Tên/chuỗi do người dùng hoặc mẫu đặt được bọc `ExamName` / `[data-exam-content]` (kèm `aria-labelledby` tại `labelBeforeName`/`richNodes` để tách khỏi nhãn giao diện).
- **Biểu mẫu Ma trận môn (`MT`, `[data-form-kind="MATRIX"]`):** dựng bởi `matrix-form.tsx` cho `QDA`, `BCA`, `VACT` (`TOAN`, `VIETNAMESE`) và `matrix-form-hsa.tsx` cho `HSA` (`TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`). Biểu mẫu gồm: (a) `MatrixStructureBlock` (`[data-form-field="STRUCTURE"]`, `writeStructure` cập nhật theo `id` nhóm không tạo/xóa nhóm dù số câu về 0 và chỉ tính lại `answerQuotas` theo đúng dạng đã lưu khi sửa cấu trúc): với `HSA`/`QDA`/`BCA` (`twoColumns = false`), các ô sửa gồm `Trắc nghiệm`, `Số chùm` (cố định `setSize`), `Điền đáp án`, kèm `Trắc nghiệm đơn` tự tính chỉ đọc; với `V-ACT` (`twoColumns = true`), hiển thị hai cột `Số câu` và `Tỷ lệ (%)` cho `Trắc nghiệm đơn` (khóa chỉ đọc ở `V-ACT` Tiếng Việt `FULL 30`) và `Câu con trong chùm` linh hoạt (`setCount=null, setSize=null` ở `V-ACT` Toán), kèm `Trắc nghiệm (gồm câu con)` tự tính chỉ đọc; nhóm chùm ở `QDA`/`BCA`/`VACT` có `MatrixSetSource` ("Nguồn chùm") với `+ Thêm điều kiện` thêm `MAPID`/`TAGS`; `HSA`/`QDA` Khoa học áp giới hạn R-12 (`setCount` 1–2 cho 3 câu con/chùm; `shortAnswerMin` = 1 với `LY`/`HOA`/`SINH` và 0 với `SU`/`DIA`); (b) `MatrixDistributionBlock` cho Khối lớp và Độ khó theo mức áp dụng từng kỳ thi (`HSA` `FIXED_REQUIRED` chip `"Bắt buộc"`, `QDA` `DECLARED_REQUIRED` `"Bắt buộc"`/`"Chưa khai báo"` + `"Đã khai x/y"`, `BCA` `FIXED_REFERENCE` `"Tham khảo"`, `VACT` `REFERENCE_WITH_APPLY` `"Chỉ đối chiếu"` + công tắc `"Áp dụng khi sinh đề"` và 2 cột `Số câu`/`Tỷ lệ (%)` lưu `unit: "PERCENT"` cùng `percentsAreComplete`/`apportionByPercent`; V-ACT Tiếng Việt ẩn hàng Khối lớp mặc định trừ khi đã khai, đang nhập dở `GRADE:` hoặc chọn `"Khối lớp"` từ `+ Thêm điều kiện`, còn V-ACT Toán hiện `"Liên lớp"` `GRADE_MIXED`); (c) `MatrixScopeBlock` (`KnowledgeScopeField` + nút `"Thêm chỉ tiêu chủ đề"` mở `TopicQuotaTable`; riêng khi đã có dòng chỉ tiêu hoặc ở `V-ACT` Tiếng Việt thì `TopicQuotaTable` hiển thị sẵn). Riêng `MatrixFormHsa` nhúng nguyên `MapidModeField` (`"Không giới hạn MAPID"` / `"Theo ma trận MAPID"`), `MapidRulesTable` (`[data-testid="mapid-rules-table"]`) và `MapidRuleDialog`, đặt `"Phạm vi nguồn"` dưới từng vai trò (`SINGLE` dưới `ReadOnlyCount`, `SET`/`FILL` dưới ô nhập) và chỉ lùi về `KnowledgeScopeField` + `TopicQuotaTable` khi `mapidViewMode(section) === null`; với `HSA` `SU` và `DIA` (AD-98, AD-99), đặt `[data-testid="hsa-strand-section"]` trước khối cấu trúc `[data-structure-variant="compact"]`, thêm `[data-testid="hsa-mapid-allocation"]` (đếm an toàn chồng lấn và ghi chú thứ tự ưu tiên) ở chế độ `MATRIX`, giữ hàng Khối lớp kèm chip `"Chưa áp dụng"` và dòng `"Không bắt buộc với môn này."` khi `byGrade` trống, và riêng `DIA` có khối chùm hiển thị `[data-testid="hsa-dia-material-section"]` đọc/ghi `reference.tags` ↔ `set.tagCodes` bằng `BlockTagPicker` (thu gọn khi trống kèm `"Chưa áp dụng"` và nút `"Thêm điều kiện tư liệu"`, giữ mở khi bỏ chọn tag cuối đã tải lại tới khi bấm `"Bỏ điều kiện tư liệu"`, chuyển focus khi mở/bỏ theo mục 5.10).
- **Biểu mẫu Danh sách dòng lấy câu (`DS`, `[data-subject-form="ROWS"]`):** `row-list-form.tsx` phục vụ các môn Văn & Anh (`HSA`/`QDA`/`BCA`), Anh & Tư duy khoa học (`VACT`), và toàn bộ `SP2E`, `SPT`, `THPTQG`, `HSCA`, `VSAT`. Bảng gọn trong `ScrollableTable` gồm 5 cột (`Thứ tự`, `Tên` `min-w-44` kèm phụ đề dạng/badge/`AudioChip`/`LockReason`, `Số lượng` `[data-row-figures]` gồm số ý Đúng/Sai, thang điểm và icon khóa, `Điều kiện`, `Thao tác` nút `Sửa` mở `[data-form-row-editor]` bên dưới bảng với `aria-expanded` và trả focus qua `rAF`; riêng dạng tự luận chỉ đọc như `THPTQG:VAN` hiện `[data-read-only-notice]` `"Tự luận – tạm bỏ qua. Phần này không có ô sửa."` với 4 cột không có `Thao tác`, trong khi `BCA:VAN` vẫn mở dòng tự luận để sửa MAPID/tag). Dòng hỗ trợ tiêu đề đoạn (`[data-form-segment]`, `ExamName`), danh sách slot từng chùm (`[data-form-slots]`), `[data-template-facts]`, `BlockSwitches` (trộn nếu `shuffleEditable`, bỏ qua trừ khi `omittedLocked`), nhập `%` bằng `CountPercentInput` (`COUNT_OR_PERCENT` trên tổng số câu của phần cho `VACT`/`SP2E`/`SPT`/`HSCA`/`VSAT`, cho phép tổng < 100%, kiểm nguyên chùm) và trình đơn `+ Thêm điều kiện` (`BlockConditions`: `TAGS`, `MAPID (lọc)` → `knowledgeScope`, `Phân bổ theo MAPID` → `topicQuotas`, `Độ khó`/`Khối lớp` khi không bị R-11 ẩn → `block.matrix`, `Điều kiện từng chùm` → `slots`, `Theo vị trí câu con` → `childConditions` cho sửa ở `SP2E:ANH` và khóa mẫu ở `VSAT:ANH`). `RowSplitPanel` chỉ bật cho `HSCA:ANH` và `VACT:SCIENTIFIC_THINKING`, ẩn khi `units < 2`: chia theo các phần tử nguyên dương có tổng bằng số đơn vị gốc, dòng con đầu giữ `id` và `templateGroupCode`, sao chép `shuffleUnits` (kể cả `false`) cùng thông số chấm/dạng/chùm và giữ nguyên `audioRequirement` cấp phần.
- **Phân bổ đối chiếu (`ReferenceBlock`, `[data-reference-block]`):** đặt ở đáy biểu mẫu `DS` trong `<details>` mặc định đóng (focusRing trên `<summary>`, tự mở khi `visiblePercentIssues.length > 0`). Mức áp dụng theo kỳ: `HSA` dùng `FIXED_REQUIRED` (giữ chip `"Tham khảo"` / `FIXED_REFERENCE` khi dữ liệu đang lưu `REFERENCE`, ví dụ `HSA:ANH`), `QDA` và `THPTQG` dùng `DECLARED_REQUIRED`, `BCA` dùng `FIXED_REFERENCE`, còn `VACT`/`SP2E`/`SPT`/`HSCA`/`VSAT` mặc định `REFERENCE_WITH_APPLY` (chip `"Chưa khai báo"` đến khi có ít nhất 1 ô mới hiện `"Chỉ đối chiếu"`/`"Bắt buộc"` kèm công tắc `"Áp dụng khi sinh đề"`, xóa hết ô thì đặt lại `REFERENCE`). Môn tính theo lệnh (`PROMPT`, như `VSAT:TOAN`) và bài đa môn dùng `examQuotas` khóa công tắc áp dụng với lý do `LockReason` (`promptBasis`/`multiSubject`), dùng số lệnh làm mẫu số và hiện `"Chưa phân loại: N lệnh"` khi cần. Ô `%` lưu `unit: "PERCENT"` và chặn lưu nếu tổng ≠ 100% (`MATRIX_PERCENT`). Với `VSAT` có hàng Khối lớp (R-10), `+ Thêm điều kiện` trong `ReferenceBlock` mở `CombinedPreview` để xem trước trước khi xác nhận chuyển `SEPARATE` → `COMBINED` ở mức phần (`PART_LEVEL`), còn nút `"Bỏ kết hợp"` trong `CombinedTable` mở `CombinedPreview` để chuyển ngược `COMBINED` → `SEPARATE`. Với các kỳ cho nhập tỷ lệ (`VACT`/`SP2E`/`SPT`/`HSCA`/`VSAT`), `ReferenceBlock` cũng hiển thị `TopicQuotaTable` (lọc bỏ các chỉ tiêu `PROMPT`/`REFERENCE`) bên dưới `ReferenceQuotas`. Theo R-11 (ẩn khối lớp cho `HSA:ANH`; `SP2E`/`SPT`/`THPTQG` ở `SU`, `DIA`, `ECON_LAW`/`GDKTPL`, `ANH`; `VSAT` ở `DIA`, `ANH`, `VAN`), mọi biểu mẫu `MT`/`DS`, `TopicQuotaTable` (`hideGrade`), `ReferenceBlock`, `ReferenceQuotas` và `RowEditor` đều ẩn trường Khối lớp nhưng bảo toàn giá trị khối lớp đã lưu khi ghi (màn Review, Xem trước và `MD` vẫn hiển thị).
- **Trạng thái khóa và lý do (`LockReason`, `EnforcementControl`):** mọi điều khiển bị khóa trên biểu mẫu theo môn gắn text giải thích qua `aria-describedby` (`LockReason`: `templateTotal`, `rowCounts`, `answerType`, `omitted`, `rowHasConditions`, `rowNoSetSize`, `rowOmitted`, `promptBasis`, `multiSubject`; ở `RowSplitPanel` khóa theo thứ tự ưu tiên `rowCounts` > `answerType` > `rowOmitted` > `rowNoSetSize` > `rowHasConditions`), bổ sung cho năm tình huống khóa của `MD` nêu ở các mục dưới đây.
- **Thông báo lùi `MD` và danh sách đầy đủ các trường hợp còn dùng `MD` (`ExamStructureSubjectPanel` + `BlockEditor`):** khi một cặp chuẩn bị `fitsForm` từ chối, giao diện ghim về `MD` cho phiên đó và hiện `FullFormNotice` (`[data-testid="full-form-notice"]`, `data-reason`) với nguyên văn: `"Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được."` Toàn bộ các trường hợp còn mở `MD`: (1) bài `buildMode === "REVIEW"` khi `!rendersReviewPanel(section, true)` (bài ôn đa nhóm cũ; bài ôn chuẩn mở `ReviewSubjectPanel`, không hiện `FullFormNotice`); (2) cặp `(kindCode, subjectCode)` ngoài 75 cặp chuẩn + 4 alias (kỳ thi/môn do quản trị viên thêm mới hoặc mã CL cũ — mở `MD`, không hiện `FullFormNotice`); (3) cặp chuẩn còn lỗi `fitsForm` theo R-2 (mở `MD` kèm `FullFormNotice`): `COMBINED_MATRIX` (trừ `VSAT` cho phép `COMBINED` ở mức phần), `COUNT_NULL`, `PERCENT_ENTRY`, `COUNT_BASIS`, `GRADE_REQUIRED_HIDDEN`, `ROLE_DUPLICATE`, `ROLE_UNKNOWN`, `BLOCK_MATRIX`, `BLOCK_TOPIC_QUOTAS` (ngoài `HSA`), `POSITION_CONDITION`, `SET_CONDITION`, `BLOCK_SCOPE_OR_TAGS`, `ANSWER_QUOTAS_SHAPE`, `SET_SIZE_MISMATCH`, `LIMITS_VIOLATED`, `ROW_CONDITION_UNSUPPORTED` (`childConditions` ngoài `SP2E:ANH`/`VSAT:ANH` hoặc `byCompetency` khác rỗng trên `DS`).
- **Đã phân bổ và còn thiếu** đứng cạnh từng chỉ tiêu (cột "Đã phân bổ", "Còn thiếu"; thiếu hiện màu cảnh báo kèm số, không chỉ màu). Câu con của chùm trừ vào chỉ tiêu dạng trả lời theo dạng của câu con; nhóm chưa xác định dạng được ghi riêng ("nhóm chưa xác định dạng") và lấp phần chỉ tiêu còn thiếu. Chỉ tiêu của môn Khoa học (HSA): mẫu khai mặc định theo số câu (`COUNT`) ở mức 17 câu (2B H3), và ô khai dạng trả lời vẫn cho đổi đơn vị Số câu hoặc Phần trăm (`setUnit` quy đổi theo số câu của môn, máy chủ giữ nguyên đơn vị đã lưu); "khai theo số câu" là mặc định của mẫu, không phải việc giao diện cấm. Mức 16 câu chỉ là lựa chọn của Sinh thử khi admin chọn một môn của phương án chọn môn (ô chọn số câu của môn ở tab Xem trước); cách chia 17–17–16 trên đề của học viên do máy chủ quyết định lúc sinh đề (2B H3), không qua ô này. Nút chọn trường hợp 16/17 và gợi ý khai theo % của màn môn chỉ còn hiện khi môn chọn có số câu rỗng và `countsByPick` có nhiều giá trị khác nhau (dữ liệu cũ); mẫu và dữ liệu mẫu đều ghi 17 nên không hiện.
- **Phương án chọn môn (HSA Khoa học):** một thẻ có tên phương án, "Học viên chọn 3 trong 5 môn", quy tắc chia 17–17–16 theo nhóm môn ở dạng chỉ đọc kèm ví dụ, bảng 5 môn có nút "Soạn môn". Không liệt kê 10 tổ hợp, không có hộp thoại sinh tổ hợp; QDA, BCA giữ màn tổ hợp gắn từ danh mục.
- **Điểm tạm chờ BA** (spec mục 12: môn nhận 16 câu khi cả ba môn tự nhiên, tính nút con của MAPID) nằm ở hàm riêng (`subject-choice.ts`, `subject-matrix.ts`), không rải trong giao diện.

Quy ước thêm ở FE-3b (giao diện ngân hàng câu hỏi theo UniNote 2026-11-16):

- **Xem trước câu hỏi** (`features/questions/preview`, tab Xem trước và mục "Xem trước" của menu dòng): câu hiện như học viên thấy trong **khung web** (cửa sổ trình duyệt, nội dung tối đa `max-w-3xl`) hoặc **khung điện thoại** (360px, `w-90`), chuyển bằng `Tabs` kiểu `segmented`. Bề rộng nội dung đo bằng container query của khung (`@container`), không theo cửa sổ admin. Phương án giữ thứ tự gốc (đảo thứ tự chỉ có ở lượt thi).
- **Chấm thử:** nút "Chấm thử" mới hiện đáp án đúng và điểm; đổi đáp án sau khi chấm thì kết quả cũ ẩn tới lần chấm sau. Mỗi ý (ô trống, mệnh đề, cặp ghép, câu con) ghi Đúng, Sai hoặc Bỏ trống bằng chữ kèm icon, không chỉ màu; điểm hiển thị làm tròn nửa lên 2 chữ số ở bước cuối. Câu Tự luận không có điểm tự động: hiện "Chờ chấm" và bảng đối chiếu bài làm thử với hướng dẫn chấm, tiêu chí và bài mẫu.
- **Cột nhiều dòng của tệp nhập** (Lựa chọn, Lựa chọn phải, Đáp án, Cách chấm, Tiêu chí chấm): mỗi mục một dòng trong ô; thiếu đáp án chỉ cảnh báo (câu vào Nháp), tham chiếu sai hoặc cách chấm sai là lỗi chặn dòng.

Quy ước thêm ở cờ Giữ thứ tự phương án (UNI-2648; đặc tả `docs/specs/2026-11-17-giu-thu-tu-phuong-an.md`):

- **Khối phương án của trình soạn** (`question-editor-choice.tsx`, Trắc nghiệm 1 đáp án, Nhiều đáp án và câu con cùng dạng): dưới danh sách phương án có `Checkbox` "Giữ thứ tự phương án" (mặc định không chọn; chọn nghĩa là `shuffleOptions = false`) kèm dòng gợi ý `text-caption text-text-muted` thụt `ps-7` như ô "Dùng lại lựa chọn" của câu Ghép đôi. Đúng/Sai, Ghép đôi và các dạng khác không có ô này. Mở lại câu giữ nguyên trạng thái.
- **Cảnh báo dưới phương án:** mã `OPTION_REFERS_TO_OTHERS` của kiểm tra Sẵn sàng hiện ngay dưới phương án gây cảnh báo, khung `warning-bg` viền `warning/30` chữ `warning` kèm icon `TriangleAlert` (không chỉ màu) và nút `secondary` cỡ `sm` "Bật giữ thứ tự" bật cờ và báo qua vùng thông báo. Cảnh báo không chặn Sẵn sàng, tự ẩn khi cờ đã bật; trang sửa lấy danh sách cảnh báo một lần khi mở, nên cảnh báo mới chỉ cập nhật sau khi lưu và mở lại.
- **Tệp nhập:** cột tùy chọn "Giữ thứ tự phương án" nằm cuối bảng cột của tệp mẫu; bản xem trước dòng Trắc nghiệm hiện "Giữ thứ tự phương án: Có/Không", và dòng dạng khác có điền cột nhận cảnh báo `KEEP_OPTION_ORDER_IGNORED` (khung `warning`, như các cảnh báo dòng khác).

Quy ước thêm ở đợt 2B, bài thi bước 2, kiểm tra và sinh thử (UNI-2662 đến UNI-2665, UNI-2677; bản vẽ `docs/design/dot-2b/`):

- **Số thập phân do máy chủ tính** (chỉ tiêu, số đạt, điểm: 1,25 câu, 2,5 điểm) hiện tối đa 2 chữ số thập phân, không đệm số 0 cuối, dấu phân cách theo ngôn ngữ (`DISPLAY_NUMBER_FORMAT`, `features/exams/display-number.ts`). Không tự làm tròn bằng `toFixed`.
- **Bảng Yêu cầu / Thực tế / Chênh lệch** (tab Xem trước, `ExamPreviewCompliance`): Chênh lệch bằng Thực tế trừ Yêu cầu; âm viết bằng dấu trừ U+2212 (`−2`, không dùng dấu gạch nối), dương có `+`, bằng 0 không dấu; ba cột số căn phải. Cột Kết quả là `StatusChip` luôn có chữ: Đạt (`success`), "Lệch · tham khảo" (`warning`, không chặn), "Lệch · bắt buộc" (`danger`). Chỉ tiêu chủ đề ghi "ít nhất N" ở cột Yêu cầu, thực tế nhiều hơn vẫn là Đạt (K6). Dòng chỉ tiêu `REFERENCE` chưa gắn MAPID ghi "Chưa có dữ liệu đối chiếu" ở Thực tế và Chênh lệch, kèm chip trung tính "Chờ mapping", và không tính là lệch. Phạm vi áp dụng của "Chờ mapping": giao diện tra chỉ tiêu theo id trong **cấu trúc đang soạn** của bài (`useExamStructure` theo `examId`, `buildTopicQuotaIndex`), không theo cấu trúc của phiên bản đã công bố. Khi xem trước một phiên bản đã công bố, trạng thái chờ mapping phản ánh cấu trúc đang soạn: gắn hoặc xóa MAPID của chỉ tiêu đó ở bản nháp sau khi công bố làm chip hiện hoặc mất, kể cả với phiên bản cũ.
- **Khóa kèm lý do hiện thành chữ.** Điều khiển bị khóa vì luật (không vì thiếu quyền hay đang lưu) hiện lý do bằng chữ ngay cạnh nó, không để lý do chỉ ở tooltip. Cách gắn lý do vào điều khiển hiện có năm dạng, chưa thống nhất:
  - **Nút "Bắt buộc" của bảng phân bổ** khóa khi đơn vị đếm không phải câu hoặc chỉ tiêu trải nhiều môn: lý do là dòng `text-caption text-text-muted` dưới nhóm nút, nối với `fieldset` bằng `aria-describedby` (`EnforcementToggle`, `lockRequiredReason`).
  - **Số câu của màn môn** (`ExamStructureSubjectPanel`) khi mẫu khóa số câu: lý do là gợi ý của `FormField` (`countField.lockedHint`), nên được mô tả bằng `aria-describedby` do `FormField` cấp.
  - **Ô số câu của phần ở bước Cấu trúc** (`LockedCount`): ô `readonly` + `aria-readonly`, vẫn nằm trong thứ tự Tab, kèm icon khóa và chữ "Mẫu đã khóa số câu" ngay cạnh; chữ khóa là phần tử kề, không phải mô tả `aria-describedby` của ô (mô tả của ô vẫn là gợi ý `FormField`).
  - **Bảng số câu theo môn trong phần** không phải ô nhập: số câu là chữ tĩnh kèm icon khóa và chữ ẩn cho trình đọc màn hình ("Mẫu đã khóa số câu"), không nằm trong thứ tự Tab.
  - **Bước 1** (bảng tổng quan số câu): các ô số câu của bảng là `readonly` + `aria-readonly`, nền `subtle`, vẫn trong thứ tự Tab; lý do là một dòng chip có icon khóa phía trên bảng ("Mẫu đã khóa số câu"), không nối `aria-describedby` với từng ô (`aria-describedby` của ô chỉ dùng cho lỗi).
  Dạng nên dùng cho điều khiển mới: nối lý do bằng `aria-describedby` như hai dạng đầu. Ba dạng còn lại là hiện trạng của mã; nối thêm mô tả là việc của giao diện, không phải quy ước đã có.
- **Huy hiệu trạng thái môn và phần** (`SECTION_STATE_TONES`): "Đã kiểm tra" (`success`), "Cần kiểm tra lại" (`warning`), "Chưa hoàn tất" (`danger`), đặt cạnh tên môn hoặc phần. Phần lấy trạng thái xấu nhất của các môn trong phần (`worstSectionState`).
- **Còn thay đổi chưa lưu ở bước Cấu trúc:** chuyển môn, phần, tab hoặc rời trang trong ứng dụng hiện hộp thoại ba nút (`ExamStructureLeaveDialog`, cỡ `md`): Ở lại (Phụ), Bỏ thay đổi (Nguy hiểm), Lưu và chuyển (Chính, `loading` khi lưu). Tiêu đề gọi tên mục đang mở, nội dung liệt kê thay đổi; lưu lỗi thì hộp thoại ở lại và hiện lỗi. Đóng tab hoặc tải lại trang dùng hộp thoại của trình duyệt (`beforeunload`).

Quy ước thêm ở đợt 2B bổ sung H-SCA, V-SAT và sinh thử (FE-6..8, FIX-AUDIO; UNI-2695, UNI-2696, UNI-2698):

- **Phần có điều kiện audio** (`audioRequirement` của môn): `REQUIRED` và `FORBIDDEN` đều hiện `StatusChip` kèm icon tai nghe (`AudioChip`, `block-row-metrics.tsx`; `info` "Có audio" / `neutral` "Không có audio"), cùng dòng ghi chú điều kiện ("Điều kiện audio đặt theo phần, không đổi ở từng dòng"). `ANY` hoặc bỏ trống là phần không phân biệt: không chip. Bảng nhóm của phần audio thêm cột audio, số lượng, tổng câu và nguồn khả dụng; nguồn khả dụng của từng nhóm được đếm với cùng điều kiện audio của môn (`blockAvailableInput`).
- **Nhóm tạm bỏ qua (`omitted`, chỉ Tự luận):** màn nhóm có công tắc "Tạm bỏ qua" dạng ô viền có nhãn + mô tả, nối `aria-labelledby`/`aria-describedby` (`block-switches.tsx`); bật nghĩa là nhóm không vào đề, không tính tổng câu/điểm, và khi kiểm tra cấu hình máy chủ bỏ qua nhóm đó (không đếm nguồn, không so dòng khóa) — tắt mới đưa nhóm vào đề. Nhóm đang bỏ qua mang `Badge` "Tự luận – tạm bỏ qua" ở màn nhóm (`block-editor.tsx`) và hiện trong bảng tổng quan thành dòng chữ mờ `text-text-muted` có gạch đầu dòng và chú thích "tạm bỏ qua", các cột điểm, tỷ lệ, lệnh ghi "không tính" (`exam-overview-table.tsx`, `ExamOverviewRow` `kind = "omitted"`). Mẫu `omitted_locked` (phần viết H-SCA, V-SAT Ngữ văn): **bước 1 Phạm vi** chỉ lọc khi chọn phạm vi "Theo phần" — các phần chỉ gồm nhóm đã lược của mẫu không được chào làm lựa chọn (`create-exam-schema.ts`, `isAllOmittedPart`), nên không tạo được bài riêng cho phần đó. **Bước 2 Cấu trúc** của bài cả môn tạo từ `HSCA_SUBJECT_VAN`/`VSAT_SUBJECT_VAN` vẫn liệt kê các phần/nhóm viết `omitted` của mẫu (chúng nằm trong cấu trúc đã nạp), và màn nhóm của chúng mở công tắc Tạm bỏ qua ở trên. Khóa có hai lớp. **Khóa giao diện:** với mẫu `omittedLocked`, công tắc bị khóa (`disabled`) khi nhóm đang tạm bỏ qua là bản sao của một nhóm mà chính mẫu đã lược — cùng nguồn gốc với bộ kiểm tra: trùng id với nhóm đã lược của mẫu, hoặc trùng `templateGroupCode` khi mã đó chỉ có ở một nhóm của mẫu (`isTemplateOmittedGroup`, đọc từ các phần của mẫu đã nạp). Nhóm có mã nhóm mẫu nhưng mẫu không lược (Đọc hiểu của H-SCA Văn đổi sang Tự luận) không bị khóa. Công tắc khóa kèm dòng lý do `text-caption text-text-muted` nối `aria-describedby` cạnh nhãn và mô tả ("Mẫu đã khóa nhóm này: nhóm đã lược của mẫu không bật lại được"); `ExamStructureView` truyền mẫu qua `BlockEditor` xuống `BlockSwitches`. Nhóm đã bật (bản nháp lưu trước khi có khóa) vẫn gạt được để sửa lại bằng cách tắt; mẫu không đặt cờ (SP2E) và nhóm thêm tay không bị khóa. **Rào lúc kiểm tra:** máy chủ vẫn nhận diện nhóm theo nguồn gốc (id hoặc `templateGroupCode`) và kiểm tra cấu hình báo `OMITTED_BLOCK_LOCKED` chặn kích hoạt/công bố, cho ghi qua API và bản nháp cũ.
- **Hàng khóa của nhóm** (`fixed_block_counts`, bản vẽ `KhoaDong2B`): vùng nhóm hiện icon `Lock` + `StatusChip` trung tính "Mẫu đã khóa dòng" kèm hai dòng chữ ("Bài cả môn giữ nguyên dạng và số lượng của dòng theo mẫu…", "Nguồn câu, phạm vi kiến thức và chỉ tiêu trong dòng vẫn sửa được") theo đúng quy ước khóa kèm lý do ở trên; các ô số của nhóm khóa chỉ đọc.
- **Trình phát audio của đề thử** (`exam-preview-audio.tsx`, bản vẽ `SinhThuAudio2B`): nút riêng thay cho điều khiển gốc của trình duyệt (chữ điều khiển gốc đổi theo ngôn ngữ trình duyệt) — nút tròn Phát/Tạm dừng, hai nút "Lùi 5 giây"/"Tiến 5 giây", thanh vị trí `input[type=range]` (mũi tên, Home, End do trình duyệt xử lý), đồng hồ `mm:ss / mm:ss`, nút tắt tiếng, dòng tên tệp (nhiều tệp thì thêm "Tệp i/n · ", một tệp chỉ có tên). Mỗi tệp `audio/*` một trình phát xếp dọc (`PreviewAudioList`), tệp khác MIME không có trình phát; một lúc chỉ một trình phát chạy (phát tệp mới tạm dừng tệp đang chạy). Tệp không tua được — nhận biết bằng tín hiệu của trình duyệt: không có khoảng `seekable` nào có `end > start` (rỗng hay chỉ `[[0,0]]`, kể cả khi `duration` hữu hạn như 2,088 giây), `duration` không hữu hạn, hoặc sau khi tua vị trí thực lệch đích quá 0,3 giây — thì chỉ hai nút tua và thanh vị trí tắt kèm chú thích "Tệp này chỉ phát từ đầu" nối `aria-describedby`; Phát/Tạm dừng, đồng hồ và nút tắt tiếng vẫn dùng được. Client không đọc phản hồi Range của HTTP. Lỗi tệp hiện hộp `role=alert` ("Không phát được <tệp>. Đề thử vẫn xem được.") kèm nút "Thử lại"; phát/tạm dừng báo qua vùng `role=status` ẩn (`sr-only`) để trình đọc màn hình nghe được.
- **Phân đoạn hiển thị** (`displaySegments`, H40): đề thử có phân đoạn khai báo chia khối theo từng `<section>` một phân đoạn, tiêu đề `h4` gồm tên phân đoạn + "· câu a–b · N lệnh · M điểm" (`segmentRange`, `sliceBySegments`, `exam-preview-paper.tsx`); số câu trên đề là `firstNumber + fromPosition − 1` đến `firstNumber + toPosition − 1` (phần không đánh từ 1 thì cả hai đầu đều lệch, ví dụ phần `firstNumber = 10`). Câu được gán vào phân đoạn chứa vị trí của nó, không cần khớp khối. Chùm bị ranh giới cắt giữa chừng hiện ở cả hai phân đoạn: đề lặp lại ngữ liệu/tiêu đề chùm, mỗi phân đoạn chỉ liệt kê câu con của riêng nó (V-SAT Tiếng Anh hiện chung bài đọc ở cả phân đoạn I và II). Không đổi số câu. Phần không khai phân đoạn giữ nguyên cách hiện cũ, không tự suy phân đoạn. Phân đoạn chỉ để trình bày: không tham gia sinh đề, không đổi chấm.

### 5.15 Editor and preview (rich text)

Trình soạn và khung xem nội dung HTML (UNI-2617, mở rộng ở UNI-2638): `RichTextEditor` và `RichTextView` ở `components/rich-text`, dùng cho Hướng dẫn làm bài của bài thi, nội dung, phương án, ngữ liệu và lời giải của câu hỏi. Đặc tả: `docs/specs/2026-11-17-trinh-soan-noi-dung.md`; bản vẽ: `docs/design/dot-2/SoanThao*.dc.html`.

- **Khung soạn** dùng token của ô nhập (mục 5.2): viền `border`, focus `primary-text`; có lỗi thì viền `danger` và `aria-invalid`, kèm dòng lỗi của `FormField`. Nhãn đi qua `aria-label` của vùng soạn vì `label for` không gắn được vào vùng `contenteditable`. Chiều cao tối thiểu 7rem, tối đa 28rem rồi cuộn trong khung.
- **Thanh công cụ** nằm trên khung soạn (`role="toolbar"`, tên "Định dạng nội dung"), nút icon lucide 18px kèm `aria-label` và tooltip (`IconButton`). Thứ tự: **Hoàn tác, Làm lại**; nhóm **Văn bản** (Đậm, Nghiêng, Gạch chân, Gạch ngang, Chỉ số dưới, Chỉ số trên, Danh sách chấm, Danh sách số); nhóm **Chèn** lấy từ registry `insert/registry.ts` rồi lọc theo `fieldContext` (Công thức, Hóa học, Ký hiệu, Bảng, Hình, Đoạn trích; thêm Gạch chân đánh nhãn ở đề bài câu Trắc nghiệm 1 đáp án, Chỗ trống ở ngữ liệu câu chùm). Sơ đồ lai chỉ có trong menu `/`; "Dán từ Word" là nút của form soạn câu, không phải của thanh công cụ. Nút không dùng được (không có gì để hoàn tác, định dạng bị schema chặn) mang `aria-disabled` nhưng vẫn focus được. Khi khung hẹp (đo ở 390px) các nhóm nút xuống dòng trong thanh (không cuộn ngang, trang không tràn ngang).
- **Phím trên thanh công cụ** (roving tabindex): cả thanh là **một điểm dừng Tab**; mũi tên trái/phải đi giữa các nút và quấn quanh hai đầu, Home/End về nút đầu/cuối, Tab rời thanh vào vùng soạn. Nút định dạng và nút Chèn là nút bật/tắt nên có `aria-pressed` (đang bật là `true`); Hoàn tác và Làm lại là nút hành động, không có `aria-pressed`; nút Chèn bật khi node hoặc mark đang chọn thuộc mục đó (bấm lúc này mở lại hộp để sửa, không chèn mới).
- **Khối atom trong vùng soạn** (bảng số liệu, sơ đồ lai, hình có chú thích, đoạn trích): công thức bên trong (`span.math-inline`, `span.math-block`, `\ce{…}`) được dựng bằng KaTeX + mhchem như ở `RichTextView` (cùng `math-render.ts`), công thức lỗi hiện mã nguồn trong khung `math-error`. Chỉ DOM của node view đổi; thuộc tính `html` và HTML phát ra giữ nguyên chữ LaTeX gốc.
- **`fieldContext`:** prop của `RichTextEditor` (`stem-single-choice`, `set-passage`, `instructions`, `default`) quyết định mục Chèn nào hiện; nơi gọi truyền theo ô (đề bài Trắc nghiệm 1 đáp án, ngữ liệu câu chùm, Hướng dẫn làm bài của bài thi). `allowImages={false}` luôn bỏ mục Hình.
- **Menu `/`:** gõ `/` ở đầu dòng hoặc sau khoảng trắng mở danh sách các mục của registry (cùng bộ lọc `fieldContext`, thêm Sơ đồ lai) có ô lọc; mỗi mục có tên và một dòng mô tả. Esc đóng, Enter chọn, mũi tên di chuyển. Gõ `$…$` ra công thức cùng dòng, `$$…$$` ra công thức khối, `\$` giữ ký tự `$`.
- **Khung "đang chọn":** chọn một node hoặc mark có mục trong registry thì hiện một dải ngay dưới thanh công cụ (nền `subtle`, viền dưới `border`) do mục đó khai báo (`selectionPanel`); công thức có ô mã LaTeX sửa trực tiếp, Trong dòng/Dòng riêng, Sửa, Xóa công thức. Nhấp đúp node, bấm nút cùng loại trên thanh công cụ hoặc nút Sửa trong khung đều mở lại hộp của mục (`openForSelection`).
- **Hộp chèn** dùng chung `InsertDialogShell` (`Dialog` của kit, tiêu đề, mô tả, Hủy/Chèn; rộng 980–1020px ở màn rộng, toàn màn dưới 640px) và `SymbolGrid` (nút 40×40, `aria-label` có ở cả hai ngôn ngữ). "Dùng gần đây" lưu theo từng người ở `localStorage`, mọi lỗi lưu trữ bị bỏ qua. Mục chưa có hộp thật hiện hộp tạm "Đang làm". Mỗi hộp một tiểu mục dưới đây; lane làm hộp nào thì chỉ sửa tiểu mục của hộp đó.
- **Cảnh báo công thức:** công thức có dấu mở hoặc đóng không khớp hiện dòng chữ màu `warning` ("Công thức có dấu mở hoặc đóng không khớp", `role="alert"`) ngay dưới khung soạn; chỉ cảnh báo, không chặn nhập hay lưu.
- **Khung xem:** nơi chỉ đọc HTML (xem trước đề, chi tiết câu hỏi) dùng `RichTextView`, không bao giờ `dangerouslySetInnerHTML` trực tiếp; view lọc lại theo cùng danh sách cho phép rồi dựng công thức bằng KaTeX sau khi trang đã gắn vào trình duyệt. Xem trước đề của bài thi hiển thị đoạn dẫn của chùm, nội dung câu, phương án và lời giải bằng `RichTextView`; tiêu chí chấm, đáp án ngắn và các nhãn là chữ thuần nên giữ thẻ văn bản.

#### 5.15.1 Hộp Công thức toán

Bản vẽ `SoanThaoCongThucToan`; lưu thành `span.math-inline` hoặc `span.math-block`.

- **Bố cục:** hai cột trên màn rộng (3 : 2, cột phải ngăn bằng viền trái), một cột dưới 1024px; hộp rộng 1000px, toàn màn dưới 640px (`InsertDialogShell`). Cột trái: hàng tab nhóm mẫu (`Tabs` kiểu `content`, tám tab: Thường dùng, Phân số/căn, Mũ/chỉ số, Giới hạn/tích phân, Hệ/ma trận, Hình học/vectơ, Tập hợp/logic, Hy Lạp; tự cuộn ngang khi hẹp), lưới nút mẫu, ô "Soạn trực quan", ô "Mã LaTeX". Cột phải: "Xem trước" kèm nút Trong dòng/Dòng riêng, "Dùng gần đây", dòng ghi chú cuối.
- **Lưới nút mẫu:** 8 cột ở màn rộng (6 ở màn vừa, 4 ở màn hẹp), nút cao 56px, viền `border`, nền `surface`, rê chuột `subtle` + `border-strong`; mặt nút là chính công thức dựng bằng KaTeX, ô giữ chỗ là ô vuông rỗng; mỗi nút có `aria-label` và tooltip vi/en. Không dùng `SymbolGrid` (nút 40×40 không đủ chỗ cho giới hạn, tích phân, ma trận). Lưới tắt khi ô soạn trực quan chưa tải xong.
- **Ô soạn trực quan:** `mathlive` (chỉ tải khi mở hộp), cao tối thiểu 150px, chữ 28px, viền `border`, focus `primary-text` + vòng 3px như ô nhập; bàn phím ảo và trình đơn riêng của mathlive tắt (nút mẫu thay bàn phím). Gợi ý phím một dòng dưới ô (Tab, `/`, `^`, `_`); tải lỗi thì ô ẩn lời gợi ý và hiện thông báo, ô mã LaTeX vẫn dùng được.
- **Ô mã LaTeX:** `Textarea` hai dòng, chữ mono, đồng bộ hai chiều với ô trực quan. Nhãn trạng thái (`StatusChip`) ở góc phải nhãn ô: Hợp lệ (success), Lỗi cú pháp (danger), Còn ô giữ chỗ (warning), trống thì không nhãn. Dưới ô là một dòng chữ `danger` nêu lý do ("Thiếu dấu đóng", "Lệnh không hỗ trợ: \…", "Công thức chưa dựng được", hoặc nhắc điền ô giữ chỗ). Chèn tắt khi lỗi, trống hoặc còn ô giữ chỗ; mở lại để sửa đổi nhãn nút thành "Cập nhật công thức".
- **Xem trước:** nền `subtle`, bo 12px; đặt công thức vào câu đang soạn, tối đa 80 ký tự chữ mỗi bên điểm chèn; Dòng riêng căn giữa trên một dòng.
- **Dùng gần đây:** tối đa 8 công thức của người dùng (`localStorage`), nút cao 44px, bấm để chèn vào ô trực quan; không có phiên hoặc lỗi lưu trữ thì mục này không hiện.
- **Khung "Công thức đang chọn":** ô mã LaTeX sửa trực tiếp (viền `danger` khi không dựng được), nút Sửa mở hộp này, Trong dòng/Dòng riêng (đổi kiểu mà công thức vẫn được chọn, khung không biến mất), Xóa công thức (`danger`) ở mép phải.

#### 5.15.2 Hộp Công thức hóa học

Ô "Gõ nhanh" theo cú pháp mhchem: chuỗi gõ chính là nội dung `\ce{…}`, không có bộ chuyển đổi. Bốn nhóm nút `SymbolGrid` (Mũi tên, Trạng thái, Điện tích, Khác: ΔrH°, nồng độ `[ ]`, liên kết đôi/ba, đồng vị) chèn đoạn mhchem tại con trỏ của ô Gõ nhanh; riêng nút ΔrH° ghi LaTeX `\Delta_r H^\circ_{298} = ` vào ô "Ghi sau phương trình" vì đó là công thức riêng. "Trên mũi tên" và "Dưới mũi tên" ghép vào mũi tên đầu tiên theo `->[trên][dưới]` (chỉ ô dưới thì `->[][dưới]`; không có mũi tên thì điều kiện bị bỏ). "Ghi sau phương trình" (LaTeX) là một công thức cùng dòng riêng đặt sau phương trình cách một khoảng trắng; phương trình ở dạng Dòng riêng thì ghi sau nằm ở đoạn ngay dưới. Trong dòng / Dòng riêng như hộp toán, mặc định Trong dòng. Xem trước dựng bằng `renderMath` (KaTeX + mhchem); phương trình hoặc ghi sau không dựng được thì ô báo lỗi (`role="alert"`, `aria-invalid`) và Chèn tắt. Dùng gần đây (danh sách `chemistry`, theo người dùng) lưu nội dung `\ce{…}` kèm điều kiện. Mở lại (công thức `\ce{…}` đang chọn): hộp tự đọc node đang chọn, tách điều kiện của mũi tên đầu tiên về hai ô Trên/Dưới, "Ghi sau" để trống (công thức riêng đứng nguyên), nút chính đổi thành "Cập nhật công thức" và thay đúng node đó. Chọn công thức toán thường rồi chèn hóa học thì chèn sau nó, không thay nó (bản vẽ `SoanThaoHoaHoc`).

#### 5.15.3 Hộp Ký hiệu và đơn vị

Ô tìm ở đầu hộp, rồi các nhóm `SymbolGrid` theo thứ tự bản vẽ: Dùng gần đây (chỉ khi đã dùng), Chữ Hy Lạp, Toán tử và quan hệ, Mũi tên, Đơn vị đo, Sinh học, Phiên âm IPA; mỗi nút 40×40 có `aria-label` là tên ký hiệu ở ngôn ngữ đang dùng. Ô tìm không phân biệt hoa/thường và dấu, khớp tên ở cả tiếng Việt lẫn tiếng Anh ("omega", "độ C", "mũi tên") và chính ký tự; khi đang tìm chỉ hiện nhóm còn ký hiệu khớp, không có thì hiện "Không có ký hiệu nào khớp". Bấm ký hiệu chèn chữ Unicode thường vào con trỏ (thay vùng chọn chữ; đang chọn một node thì chèn sau node đó); đơn vị m/s², W/m² và mục sinh học F₁, F₂, Xᴬ, Xᵃ chèn bằng `sup`/`sub` thật, chữ gõ tiếp sau đó không thừa kế chỉ số. Hộp không đóng và không lấy focus khỏi hộp sau mỗi lần bấm; nút Đóng ở chân hộp. Dùng gần đây lưu id ký hiệu (danh sách `symbols`, theo người dùng). Ký hiệu là chữ thường nên không có khung "đang chọn" và không mở lại (bản vẽ `SoanThaoKyHieu`).

#### 5.15.4 Hộp Bảng số liệu

Bản vẽ `SoanThaoBang`. Hàng trên: Tên bảng và Đơn vị (hai cột từ `sm`); hàng nút `role="toolbar"` (Thêm hàng, Thêm cột, Xóa hàng, Xóa cột, Gộp ô, Tách ô; nút `secondary` cỡ `sm`) rồi ba `Checkbox` "Hàng đầu là tiêu đề", "Cột đầu là tiêu đề", "Căn phải cột số"; lưới ô là một `table` có tên, mỗi ô là `Input` không viền và không bo (ô tiêu đề nền `subtle`, chữ đậm; ô trong vùng chọn nhiều ô nền `primary-50`), cuộn trong khung viền `border`; dưới lưới là ô Nguồn và các dòng gợi ý (dán từ Excel, chọn nhiều ô, giới hạn). Thêm/xóa hàng cột tác động lên ô đang chọn; Thêm hàng/cột tắt ở 30 hàng, 15 cột; dán vượt giới hạn thì cắt phần thừa và báo bằng dòng `role="status"` màu `warning`. Enter trong ô xuống ô dưới, không gửi biểu mẫu; mũi tên đổi ô (trái/phải chỉ khi con trỏ ở mép chữ); chọn nhiều ô bằng Shift+bấm hoặc Alt+Shift+mũi tên rồi Gộp ô; vùng gộp không vắt qua ranh giới hàng/cột tiêu đề. Ô chứa định dạng nội tuyến (`sub`, `sup`, công thức) hiện mã HTML của ô bằng chữ đơn cách (`font-mono`) để sửa mà không mất định dạng. Ghi chú đơn vị và nguồn lưu `p.figure-note`/`p.figure-source` bắt đầu bằng nhãn "Đơn vị:" và "Nguồn:" (hoặc "Unit:" và "Source:") của ngôn ngữ người soạn. Nút chính là Chèn bảng (Cập nhật bảng khi mở lại bảng đang chọn), tắt khi bảng chưa có chữ nào.

#### 5.15.5 Hộp Hình

Hộp hai cột (bản vẽ `SoanThaoHinh`): trái là Xem trước (ảnh theo độ rộng đã chọn, chú thích) cùng nút Chọn ảnh / Thay ảnh và Xóa ảnh, dòng "Nhận PNG, JPG, WebP." và lỗi tải lên (413, 422, 429...) ngay dưới; phải là Mô tả thay thế (bắt buộc, có dấu `*` và câu hướng dẫn cho trình đọc màn hình), Chú thích dưới hình, Nguồn, Độ rộng (`Tabs` kiểu `segmented`: Nhỏ / Vừa / Rộng hết khung) và ô "Cho phép học viên phóng to hình khi thi" (`Checkbox`). Nút Chèn hình tắt khi chưa có ảnh, đang tải lên hoặc mô tả trống; mở lại một hình đang chọn thì nút là Cập nhật hình. Ảnh tải qua `POST /question-assets` (nút Hình trên thanh công cụ không còn tải thẳng); lưu thành `figure.image` với `data-width` và `data-zoom`, dòng nguồn `p.figure-source` bắt đầu bằng "Nguồn: ". Không có ở ô `allowImages={false}`; dán hoặc thả tệp ảnh vào vùng soạn vẫn tải thẳng.

#### 5.15.6 Hộp Đoạn trích

Bản vẽ `SoanThaoDoanTrich`, hai cột từ `lg` (một cột ở màn hẹp). Trái: nhóm radio "Kiểu đoạn trích" (Thơ, Văn xuôi, Tư liệu; chọn một giá trị nên là nhóm radio, mục 5.2), Nội dung (`Textarea`, bắt buộc, có dấu `*`), hai `Checkbox` "Giữ nguyên ngắt dòng" và "Đánh số dòng, 5 dòng một lần" (tắt khi không phải Thơ), Tác giả, Tác phẩm, Ghi chú nguồn. Phải: Xem trước dựng bằng chính `RichTextView` (cột số dòng bên trái, chỉ hiện số 5, 10, 15…, đếm liên tục qua các khổ) kèm câu "Văn xuôi và tư liệu lịch sử dùng cùng khối này, không ngắt dòng và không đánh số." Dòng trống tách khổ hoặc đoạn; thơ giữ ngắt dòng thì mỗi dòng là một `br`, tắt thì mỗi khổ là một dòng liền. Chú thích cuối là `footer` dạng "(Tác giả, <cite>Tác phẩm</cite>)", ghi chú nguồn là `p.figure-source`. Nút chính là Chèn đoạn trích (Cập nhật đoạn trích khi mở lại), tắt khi nội dung trống.

#### 5.15.7 Gạch chân đánh nhãn và Chỗ trống

Bản vẽ `SoanThaoTiengAnh`; chỉ có ở `fieldContext` `stem-single-choice` (gạch chân) và `set-passage` (chỗ trống).

- **Hộp Gạch chân đánh nhãn:** hiện cụm đang chọn (gạch chân) và nhóm nút nhãn A–H (ô 40×40, radio ẩn, đang chọn viền `primary-600` nền `primary-50`). Nhãn mặc định là chữ kế tiếp chưa dùng, câu dưới nhóm nút nói rõ; nhãn mà cụm khác đang dùng bị khóa (đọc là "{nhãn}, đã dùng ở cụm khác") vì hai cụm cùng nhãn chặn Sẵn sàng; hết A–H thì báo và tắt nút. Chưa bôi đen (hoặc vùng chọn trải nhiều đoạn) thì nút chính tắt kèm hướng dẫn. Mở lại trên cụm đã gạch: giữ nhãn, nút chính là Đổi nhãn, có Bỏ gạch chân. Gạch chân thường trong vùng chọn bị thay bằng nhãn.
- **Khung "Cụm đang chọn: nhãn C"** (dải `subtle` dưới thanh công cụ như khung công thức): Đổi nhãn mở lại hộp, Bỏ gạch chân gỡ nhãn khỏi cả cụm.
- **Phương án tự lấy** (Trắc nghiệm 1 đáp án, `question-editor-choice.tsx`): ô chọn "Phương án tự lấy theo các cụm gạch chân" hiện khi đề bài có cụm đánh nhãn; không lưu thành trường. Bật khi tạo cụm thứ hai trong câu mới; mở câu đã lưu thì bật chỉ khi mọi phương án đúng chữ của cụm cùng nhãn. Bật: phương án n lấy chữ thuần của nhãn n, hiện dạng khung `subtle` kèm icon khóa "theo cụm gạch chân" thay cho ô soạn, theo kịp khi sửa cụm; dòng `warning` dưới danh sách nêu nhãn thiếu hoặc thừa. Nhiều đáp án không có ô này.
- **Hộp Chỗ trống:** chèn `span.blank` gắn câu con đầu tiên chưa có chỗ trống, kèm xem trước "(số) ____" theo Đánh số từ; hết câu con trống thì báo "Thêm câu con trước" và tắt nút. Mở lại trên chỗ trống đang chọn: danh sách radio các câu con còn trống (kể cả câu con của chính nó) và Xóa chỗ trống. Chip chỗ trống là hộp nét đứt màu `info` (như `rich-text.css`).
- **Câu chùm** (`question-editor-set.tsx`): ô "Đánh số từ" (1–200) và bảng "Chỗ trống và câu con" (chỗ trống, câu con · dạng, đáp án, nút Sửa mở câu con) ở đầu khối câu con; chỗ trống trỏ tới câu con không có hoặc trùng số hiện chữ `danger`. Đổi thứ tự hoặc xóa câu con thì ngữ liệu tự đánh lại `data-blank` và báo qua vùng thông báo.

#### 5.15.8 Hộp Sơ đồ lai

Lưới thế hệ, mỗi hàng một thế hệ: P (chọn ♂/♀/Không ghi và kiểu gen cho từng bên, dấu × giữa hai bên), G (giao tử hai bên), F₁ (tỉ lệ kiểu gen và tỉ lệ kiểu hình tùy chọn); "Thêm thế hệ" thêm F₂, F₃…, từ F₂ trở đi có nút xóa thế hệ. Chữ trong ô là chữ thường; `_` và `^` tạo chỉ số dưới/trên (`F_1`, `X^A`, nhiều ký tự thì `_{12}`), hai nút Chỉ số dưới/Chỉ số trên chèn `_`/`^` tại con trỏ ô đang gõ. Xem trước hiện từng hàng dạng "P: …". Lưu thành `figure.cross` đúng mẫu `cross-diagram`: hai bên của P cách nhau ` × `, hai bên của G và tỉ lệ kiểu gen/kiểu hình của một thế hệ cách nhau ` ; ` trong cùng một ô; hàng P và G chỉ có khi có chữ, thế hệ con tính đến thế hệ cuối có chữ. Mở lại (chọn sơ đồ trong nội dung, node `crossDiagram`): hộp tự đọc node đang chọn, đọc ngược các hàng P, G, F<n> về lưới, nút chính đổi thành "Cập nhật sơ đồ lai" và sửa đúng node đó; hàng có nhãn khác P/G/F<n> bị bỏ và thẻ lạ trong ô chỉ giữ chữ. Chèn mới: con trỏ nằm trong ô bảng hay mục danh sách thì sơ đồ vào sau khối cấp đầu chứa nó, đang chọn một node khác thì vào ngay sau node đó (bản vẽ `SoanThaoSinhHoc`).

#### 5.15.9 Dán từ Word

Hộp riêng (`insert/word/`) mở từ nút "Dán từ Word" của form soạn câu hoặc khi dán nội dung Word vào ô đề bài; `onRichPaste` nhận `clipboardData` trước bước lọc HTML (bản vẽ `SoanThaoDanTuWord`). Dùng `InsertDialogShell` (rộng 980–1020px, toàn màn dưới 640px), không có hàng nút Hủy/Chèn của khung: chân hộp là `DialogFooter` riêng.

- **Mở hộp.** Nội dung dán đã là HTML Word (namespace `urn:schemas-microsoft-com:office:word` hoặc meta Generator) thì hộp mở thẳng ở bước xem lại; bấm nút ở đầu form thì hộp mở ở **vùng dán** (khung nét đứt `bg-subtle`, `border-border-strong`, `role="textbox"`, Ctrl+V) và dòng cảnh báo `warning` khi nội dung dán không phải từ Word. HTML không phải Word luôn đi đường dán cũ của ô.
- **Thanh tóm tắt** ba `StatusChip` có chữ (không chỉ màu): `success` "N công thức đã đổi đúng", `warning` "N công thức đã tự sửa, chờ xác nhận", `danger` "N công thức cần gõ lại" (gồm ảnh công thức và công thức có phần tử Word ngoài tập con); dưới là dòng `caption` liệt kê bốn lỗi tự sửa được.
- **Hai cột** (`lg` trở lên; dưới đó xếp dọc): trái "Nội dung đã dán" trong khung `border-border` `surface`, các phần Đề bài / Phương án (A. đậm + nội dung) / Lời giải tiêu đề `overline`; công thức dựng bằng KaTeX + mhchem; **chỗ tự sửa chờ xác nhận có nền `warning-bg` và viền `warning`**, ảnh công thức là viên `danger-bg` "ảnh công thức", công thức đã gõ lại nền `success-bg`, công thức đã bỏ gạch ngang. Ô chọn "Tách theo nhãn A., B., C., D. và “Lời giải”" ở đầu cột (mặc định bật với Trắc nghiệm 1 đáp án và Nhiều đáp án, ẩn với dạng khác). Phải: "Chỗ cần xem lại" kèm ô "Chỉ hiện mục chưa xử lý" (mặc định bật), mỗi mục một thẻ `rounded-xl` đánh số, tên ô (Đề bài, Phương án A, Lời giải), `StatusChip` trạng thái. Mục tự sửa có "Sau khi đổi từ Word" → "Đã sửa thành", lý do, nút **Chấp nhận** (`soft`, dấu ✓) và **Sửa tay** (ô LaTeX có xem trước; Enter lưu, không gửi cả hộp). Mục cần gõ lại có lý do, chữ ngay trước chỗ đó, nút **Gõ lại công thức** (mở hộp công thức toán, chèn đúng chỗ) và **Bỏ** (hỏi xác nhận). Dưới là danh sách "Ảnh chưa đưa vào" (ảnh thường không đọc được từ clipboard).
- **Chân hộp.** Dòng ghi chú `caption` ("Mục chưa xử lý vẫn được đánh dấu sau khi đưa vào biểu mẫu"; khi còn công thức cần gõ lại thì đổi thành lý do khóa nút), nút Hủy (`secondary`), **Chấp nhận mọi chỗ tự sửa** (`soft`, tắt khi không còn chỗ chờ), **Đưa vào biểu mẫu** (`primary`, **tắt khi còn công thức cần gõ lại chưa xử lý**).
- **Sau khi đưa vào biểu mẫu.** Ô trống thì điền, ô đã có nội dung thì `ConfirmDialog` hỏi ghi đè kèm danh sách ô. Chỗ tự sửa chưa Chấp nhận vẫn được đánh dấu trong ô bằng decoration của ProseMirror (cùng nền `warning-bg`, viền `warning`, `title` giải thích); dấu không vào HTML, mất khi tải lại trang, tự mất khi người soạn sửa công thức, và không chặn lưu.
- **Số nối xem trước với thẻ.** Mỗi chỗ cần xem lại có một số thứ tự cố định (theo thứ tự trong nội dung, không đổi khi lọc "Chỉ hiện mục chưa xử lý"): viên tròn 20px chữ 11px đậm, nền `warning` (chỗ tự sửa chờ xác nhận) hoặc `danger` (ảnh công thức chưa xử lý), chữ `surface` để đủ tương phản ở cả hai chế độ; hiện cạnh chỗ đó trong xem trước và ở đầu thẻ tương ứng (thẻ đã xử lý dùng viên trung tính). Chip "đã đổi đúng" có biểu tượng dấu tích (lucide `Check`, 14px, nét 2.6) thay cho chấm.
- **Dấu trong ô soạn** dùng cờ `!` của Tailwind (`bg-warning-bg!`, `outline-2!`, `outline-warning!`) vì `.rich-text-editor-content .rich-text-math` đặt nền và viền chip công thức bằng hai lớp và sẽ che dấu nếu không ép; viền `warning` 2px (tương phản ≥ 3:1 ở cả hai chế độ), chữ vẫn `text` trên `warning-bg` (≥ 4.5:1). Sau khi dán xong, focus chuyển tới tiêu đề "Chỗ cần xem lại" thay vì để rơi về khung hộp.

### 5.16 Thành phần và màn hình Đợt 3 (Thương mại, Bộ đề, Màn làm bài, Chấm tự luận & Làm thử)

- **Bộ thành phần câu hỏi dùng chung (`@thi360/ui/question`, `packages/ui/src/question/`):**
  - `Question` hỗ trợ 8 dạng câu hỏi ở cả hai chế độ `answer` (làm bài) và `review` (xem lại chỉ đọc): Trắc nghiệm 1 đáp án (`SingleChoiceQuestion`), Nhiều đáp án (`MultipleChoiceQuestion`), Đúng/Sai (`TrueFalseQuestion`), Trả lời ngắn (`ShortAnswerQuestion`), Điền khuyết (`ClozeQuestion`), Ghép đôi (`MatchingQuestion`), Tự luận (`EssayQuestion`: `wordLimit` là số chữ tối thiểu kèm bộ đếm `role="status"` theo `countWords` và cảnh báo không chặn nộp; có thể gắn trình soạn Tiptap qua `renderEditor`), Câu chùm (`QuestionSet`).
  - `QuestionFrame`: hỗ trợ `headingLevel` `1..6`, đánh số câu liên tục suốt bài, vùng nội dung đề gắn `data-exam-content`, cờ đánh dấu xem lại, chip `Chấm tay`, `OutcomeChip` và `ReviewPoints`.
  - `SymbolInputBar`: thanh chèn ký tự/ion/mũi tên (`DEFAULT_ION_SYMBOLS`, `shouldShowSymbolInput`) cho câu trả lời ngắn hóa học/toán trên thiết bị di động và máy tính.
  - `SegmentHeader` (`apps/web-student/src/features/attempt-player/segments/segment-header.tsx`): tiêu đề phân đoạn hiển thị `displaySegments` cho H-SCA, V-SAT (tên phân đoạn, khoảng câu, số lệnh, điểm tối đa; không có đồng hồ riêng theo phân đoạn).
  - `AttemptAudioPlayer` & `createTrialAudioAdapter`: phát audio tối đa 2 lần (`maxPlays`), không tua/không tạm dừng/không đổi tốc độ ở lượt thật; đồng bộ adapter cục bộ (`createTrialAudioAdapter`) cho màn Sinh thử / Làm thử admin.
  - `RichTextView` (`@thi360/ui/rich-text`, KaTeX + mhchem nạp động khi cần) được đưa về `packages/ui` dùng chung giữa `web-admin` và `web-student`.
- **Màn hình học viên & công khai (`apps/web-student`):**
  - Trang giới thiệu (`/`), Danh mục (`/` mục `#exams`, lọc theo kỳ thi/hình thức gồm Bộ đề/môn, nhãn `"Bài ôn"` cho `REVIEW`), Chi tiết sản phẩm/bộ đề (`/exams/[code]`, `ExamGoneView` cho HTTP/API `410 PRODUCT_DISCONTINUED`, `BundleUnavailableView` cho trạng thái `UNAVAILABLE` của bộ đề), Thanh toán (`/checkout/[code]` áp dụng voucher, luồng đơn 0 đồng, `/checkout/result`), Bài thi của tôi (`/home`, `groupMyExams` nhóm thẻ theo bộ chống trùng và dẫn chiếu tới bộ chính), Đơn của tôi (`/account/orders`, `/account/orders/[code]`), Hồ sơ & tài khoản (`/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/account/profile`).
  - **Màn chuẩn bị và làm bài (`/prepare/[entitlementId]`, `/attempts/[id]`):** `PrepareView` / `PreparingScreen` và `AttemptPlayer`, `QuestionNav` (gồm cụm phân đoạn `displaySegments`, bộ lọc tất cả/chưa làm/đánh dấu), đồng hồ theo phần hoặc chung (`server-clock.ts`), `PartNavigation` chuyển phần tuần tự `SEQUENTIAL` (ngoại lệ `THPTQG` chuyển tự do `FREE` giữa các phần theo BS 4), `PartChoiceView` chọn phần sau & chờ sinh đề nền (`STARTING`/`GENERATING`), lưu bản nháp IndexedDB ngoại tuyến (`attempt-draft/db.ts`, `AttemptSaveQueue`) & đồng bộ khi nối lại, hộp thoại tiếp quản phiên (`TakeoverDialog` / `SessionEvictedView`) và chế độ chỉ xem ở tab thứ hai (`ReadonlyTabBanner` / `AttemptTabLock`).
  - **Màn kết quả, xem lại và lịch sử (`/attempts/[id]/result`, `/attempts/[id]/review`, `/history`):** nhãn `"Chờ chấm"` (`PENDING_REVIEW`) khi còn tự luận chưa chấm, `"Đã chấm"` (`SCORED` trên `web-student`, còn `"Đã có điểm"` là nhãn chip trạng thái của `web-admin` `/attempts`), trạng thái `"Đúng một phần"` (`PARTIAL`), `SegmentBreakdownSection` cho H-SCA/V-SAT (bảng trên màn rộng, `CardList` trên khung hẹp), hiển thị kết quả V-ACT (`"Điểm luyện tập"` trên thang điểm cấu hình như `1.200` + 4 thành phần + `ThinkingSubsections` tách riêng tiểu mục 12/18 câu Tư duy khoa học), tải ảnh lời giải qua endpoint bảo vệ (`/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}`, chỉ mở với `FULL_SOLUTION`).
- **Màn hình quản trị Đợt 3 (`apps/web-admin`):**
  - Tab Thông tin bán (`/exams/[id]/listing`, `ExamListingView` + ảnh bìa + cảnh báo chấm tay `MANUAL_GRADING_REQUIRED`), Khuyến mãi (`/promotions`), Bộ đề (`/bundles`), Đơn hàng, người mua, hoàn tiền & đối soát (`/orders`, `/orders/[code]`, `/orders/buyers`, `OrderAttentionStrip` 6 loại gồm khoản thu thừa `DUPLICATE_UNREFUNDED` & thanh toán báo thành công muộn `LATE_SUCCESS_PENDING`), Hồ sơ học tập (`/users/[id]?tab=learning`, `LearningProfileTab` nối Quyền sử dụng và Lịch sử lượt thi).
  - Chấm tự luận, Vận hành lượt & Làm thử: `GradingListView` (bộ lọc + số đếm theo trạng thái), `GradingItemView` (chấm ẩn danh, tiêu chí chấm, đếm chữ, lưu nháp/hoàn tất/mở lại), `AttemptListView` / `AttemptDetailView` (tra cứu lượt, chấm lại `GRADING_ERROR`, đánh dấu hỏng `FAULT_CLOSED`, cấp bù 1 lượt với `ATTEMPT.COMPENSATE`), `PreviewTrialView` ("Làm thử như học viên" tại `/exams/[id]/preview/[previewId]/try`, từ màn Sinh thử trong `/exams/[id]/review`, dùng chung `@thi360/ui/question` + `AttemptAudioPlayer` cho đủ 9 kỳ thi).

## 6. Icon và hình ảnh

- Icon: **lucide-react**, nét 1.75–2px, 16px trong nút, 18px trong menu, 20px độc lập.
- Ô icon: vuông 32–40px bo góc 10px, nền `primary-50`, icon `primary-text`.
- Ảnh thẻ bài thi tỉ lệ 16:9, bo hai góc trên theo thẻ.

## 7. Chuyển động

- Chuyển trạng thái màu, viền, bóng: 150ms `ease-out`.
- Dialog và drawer: 200ms.
- Đổi trang con trong nhóm tab điều hướng: nội dung mới hiện dần và trượt lên 4px trong 200ms `ease-out` (`animate-route-in`, khai báo ở `tokens.css`); tiêu đề trang và thanh tab đứng yên. Không chuyển focus bằng mã: focus ở lại liên kết vừa bấm.
- Bảng đang tải lại: phần thân mờ còn 60% trong 150ms, không đổi kích thước.
- Tôn trọng `prefers-reduced-motion`: mọi hiệu ứng trên kèm `motion-reduce:animate-none` / `motion-reduce:transition-none`. Riêng phần tử có hiệu ứng gắn với `data-[state=…]` (dialog, drawer, menu) thì viết `motion-safe:data-[state=open]:animate-*`, không viết `motion-reduce:animate-none` (biến thể theo trạng thái thắng và hiệu ứng vẫn chạy). Spinner của nút đang xử lý quay chậm lại (6 giây một vòng) thay vì dừng hẳn, để vẫn thấy là đang chạy.

## 8. Truy cập và ngôn ngữ

- Độ tương phản: mọi chữ trên nền đặc hoặc nền nhạt phải đạt AA (4,5:1) ở cả hai chế độ sáng/tối; `packages/ui/src/styles/tokens.test.ts` liệt kê từng cặp và kiểm. Chữ màu chính dùng `primary-text`, nền nút chính dùng `primary-fill`, không dùng `primary-600` cho hai việc này. Không dùng `text-muted` cho chữ dưới 12px.
- **Quyết định 2026-11-13 (UNI-2559, M.Trường chọn phương án 2 và màu nhấn A):** bản trước giữ `primary-600` làm nền nút chính để đúng trang tham khảo, nhưng đo thực tế chữ trắng trên `#059669` chỉ 3,77:1, chữ `primary-600` trên `primary-50/100` chỉ 3,58/3,43:1 và chữ trắng trên `#F59223` chỉ 2,33:1, đều dưới AA cho chữ 14px. Đổi thành: chữ màu chính và nền nút chính `#047857` (5,21/4,99:1 trên nền nhạt, 5,48:1 với chữ trắng), hover `#065F46`, bấm `#064E3B`; chữ trên nút nhấn `#1A1A1F` (7,44:1), nền `#F59223` giữ nguyên; chữ lỗi `#B91C1C` (nền `#FEF2F2` 5,9:1; `#DC2626` chỉ 4,41:1). Bảng token dưới đây thắng các bản vẽ `docs/design/*.dc.html` ở những màu này.
- **Thành phần không phải chữ đạt 3:1** (WCAG 1.4.11; quyết định 2026-11-15, UNI-2562, M.Trường chọn phương án b): nền nút chính, checkbox/switch đang bật, viền thẻ/chip/ô đang chọn đạt 3:1 với nền trang, nền thẻ và nền phụ ở cả hai chế độ, với mọi bộ phối màu. Ở chế độ tối `deriveTokens` (`darkModeUiColors`) nâng độ sáng `primary-fill` và `primary-600` (giữ sắc độ, tông) tới khi đạt; chữ trên nút giữ trắng nếu trắng vẫn đạt 4,5:1 ở cả thường/hover/bấm, không thì nền sáng thêm tới khi chữ tối `#11181C` đạt 7:1 và dùng chữ tối (cả 9 bộ có sẵn đều thành nền sáng chữ tối). Mục menu đang mở đã có thanh dọc màu `primary-text` nên không phụ thuộc nền `primary-50`. `tokens.test.ts` và `theme.test.ts` kiểm các cặp này.
- Mọi điều khiển dùng được bằng bàn phím, có vòng focus nhìn thấy được. Nút chỉ có icon phải có `aria-label`. Tắt viền ngoài mặc định bằng `outline-hidden` (ở chế độ tương phản cao vẫn còn viền), **không dùng `outline-none`**.
- Vùng bấm tối thiểu 44×44px trên màn cảm ứng (mục 5.1).
- Không truyền đạt trạng thái chỉ bằng màu: chip trạng thái luôn có chữ.
- Ngôn ngữ mặc định tiếng Việt, có thêm tiếng Anh (quyết định 2026-11-14). Thanh trên cùng có hai nút icon 36px ngay bên trái avatar: chuyển sáng/tối (mặt trời khi đang sáng, mặt trăng khi đang tối) và chọn ngôn ngữ (icon dịch, mở danh sách Tiếng Việt / English); cả hai có `aria-label` và tooltip, lưu theo từng người bằng cookie. Trang đăng nhập và landing có hai nút này ở góc trên phải. Chuỗi hiển thị nằm trong file dịch (`next-intl`), hai tệp `vi.json` và `en.json` cùng bộ khóa. Ngày `dd/MM/yyyy`, số dùng dấu chấm phân tách nghìn.
- Chỉ chữ giao diện là song ngữ. Nội dung đề (câu hỏi, tên bài, tên phần, mục, nhóm, phân đoạn lấy từ mẫu hoặc do admin gõ) hiện đúng như đã lưu, kể cả khi giao diện ở tiếng Anh (quyết định 2026-11-19). Vùng hiện nội dung đề gắn `data-exam-content` để phép kiểm lẫn ngôn ngữ bỏ qua. Tên lấy từ danh mục dùng chung (kỳ thi, môn…) vẫn song ngữ: giao diện tiếng Anh hiện tên tiếng Anh nếu có, chưa có thì tên tiếng Việt (`pickName`).
- Cách dùng `data-exam-content` (FS-4): gắn trên phần tử bao nội dung do người soạn hoặc người học nhập (đề bài, phương án, ngữ liệu, gợi ý `inputHint`, câu trả lời hiện lại), không gắn trên nhãn giao diện và không bọc cả màn. Thành phần của `@thi360/ui/question` đã gắn sẵn; màn dùng `RichTextView` cho nội dung đề tự bọc. `auditMixedLanguage` của `e2e/tests/helpers/visual-matrix.ts` bỏ qua cả cây con của thuộc tính này (trang `en` có chữ Việt có dấu, trang `vi` có từ giao diện tiếng Anh, ở ngoài vùng đó là lỗi); phép kiểm tương phản và tràn ngang vẫn tính cả vùng này.

## 9. Tùy chỉnh giao diện

Giữ bộ cấu hình giao diện như reap, áp dụng **toàn hệ thống** (web admin và web học viên dùng chung một cấu hình). Chi tiết ở spec mục 13.

- **Lối vào:** một mục trong **menu chính** của trang quản trị (nhóm "Hệ thống" → "Tùy chỉnh giao diện"), chỉ hiện với người có quyền `APPEARANCE.MODIFY`. Không đặt trong menu avatar và **không dùng nút nổi** ở mép màn hình như reap.
- **Trang tùy chỉnh:** cột trái là các nhóm tùy chọn, cột phải là vùng xem trước (nút, ô nhập, chip, bảng, thẻ, sidebar thu nhỏ).

| Nhóm | Tùy chọn |
|---|---|
| Chế độ mặc định | Sáng, tối (tab phân đoạn). Nằm trên cùng; chú thích: "Áp dụng cho người chưa tự chọn chế độ ở thanh trên cùng" |
| Bộ phối màu | 9 thẻ bộ cố định để chọn nhanh (mỗi thẻ: hai ô màu, tên, dải thu nhỏ nút chính và nút nhấn); thẻ đang dùng có viền `primary-600` và dấu tích. Thẻ thứ mười "Tự tùy chỉnh" mở hai ô Màu chính và Màu nhấn (bảng chọn màu + ô nhập mã). Danh sách bộ, cách suy ra token và các kiểm tra: spec mục 13.1 |

- **Người chưa tự chọn sáng/tối** dùng chế độ mặc định ở trên (cài sẵn: **Sáng**). Không tự theo chế độ sáng/tối của hệ điều hành (`prefers-color-scheme`); người dùng đổi bằng nút sáng/tối ở thanh trên, lựa chọn lưu bằng cookie (quyết định 2026-11-15, S-09).

Không có tùy chọn bố cục và kích thước: sidebar, toolbar, bo góc, chiều cao điều khiển cố định theo tài liệu này.

- Thanh dưới cùng: **Khôi phục mặc định** (kiểu Phụ) và **Lưu và áp dụng** (kiểu Chính). Thay đổi chưa lưu chỉ hiện ở vùng xem trước.
- Khi màu tự chọn làm chữ khó đọc (tương phản dưới 4,5:1), hiện cảnh báo ngay cạnh ô chọn màu; hệ thống tự chọn màu chữ trắng hoặc đen đọc được hơn trên nền màu chính.
- Vì màu đổi được, thành phần **chỉ dùng token** (`primary-text`, `primary-fill`, `text`, `border`…), không viết mã màu. Chế độ tối định nghĩa lại các token trung tính; không viết kiểu riêng cho chế độ tối trong thành phần.

## 10. Cài đặt token

Token khai báo một lần trong `packages/ui/src/styles/tokens.css` bằng `@theme` của Tailwind 4, hai app cùng nhập. Không viết mã màu trực tiếp trong thành phần. Giá trị dưới đây là bảng màu mặc định; cấu hình giao diện đang áp dụng ghi đè các biến này trên thẻ `<html>` khi render.

```css
@theme {
  --font-sans: "Be Vietnam Pro", system-ui, sans-serif;
  --color-primary-50: #ECFDF5;
  --color-primary-100: #DCFCE7;
  --color-primary-200: #A7E3C2;
  --color-primary-500: #10B981;
  --color-primary-600: #059669;
  --color-primary-700: #047857;
  --color-primary-800: #065F46;
  --color-primary-900: #064E3B;
  --color-primary-text: #047857;
  --color-primary-fill: #047857;
  --color-primary-fill-hover: #065F46;
  --color-primary-fill-active: #064E3B;
  --color-on-primary: #FFFFFF;
  --color-accent-500: #F59223;
  --color-accent-700: #B45309;
  --color-on-accent: #1A1A1F;
  --color-text: #11181C;
  --color-text-secondary: #3A3A42;
  --color-text-muted: #6B6B72;
  --color-page: #F7F8F9;
  --color-subtle: #F1F2F4;
  --color-border: #E8E8EC;
  --color-danger: #B91C1C;
  --radius-md: 10px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-2xl: 24px;
}
```

## 11. App học viên

Quy ước giao diện cho hai ứng dụng native dành riêng cho học viên cổng thi Thi 360 (đợt khung và xác thực, `UNI-2785`). Nguồn ràng buộc: đặc tả `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` (R-1, R-2, R-12, R-13, mục 9), kế hoạch `docs/plans/2026-11-25-app-hoc-vien-khung-xac-thuc.md` (mục B1.4, B1.7) và bản thiết kế đã duyệt tại `docs/design/app-hoc-vien/README.md`.

### 11.1 Thành phần gốc theo nền tảng và bản thiết kế tham chiếu

- **Android (`apps/android/`):** Kotlin + Jetpack Compose + Material 3; hỗ trợ từ Android 8.0 (API 26); định danh `vn.unicom.thi360`, tên hiển thị tạm `Thi 360`.
- **iOS (`apps/ios/`):** Swift + SwiftUI theo kiểu iOS thuần; hỗ trợ từ iOS 17.0; định danh `vn.unicom.thi360`, tên hiển thị tạm `Thi 360`.
- **Bản thiết kế tham chiếu:** 28 màn `*.dc.html` trong `docs/design/app-hoc-vien/` (bảng ánh xạ từng màn theo yêu cầu nằm ở `docs/design/app-hoc-vien/README.md`). Hai tệp `m3.css` và `ios.css` chỉ dùng để xem bản vẽ, **không** chép style sang mã thật; mỗi app dùng thành phần gốc của hệ điều hành.
- **Đặc tả thắng bản vẽ khi có khác biệt:** nền, bề mặt, viền và chữ trung tính dùng đúng token trung tính của web ở từng chế độ (AC-78, quyết định D-21), không dùng sắc xanh nhạt mặc định của bảng màu hệ thống trong bản vẽ Android.
- **Màn và trạng thái chưa vẽ nhưng bắt buộc theo đặc tả (R-11, R-13):** màn chọn ngôn ngữ, hộp thoại Điều khoản sử dụng (dùng bản tạm của web), hộp xác nhận đăng xuất, bản giao diện tiếng Anh, khung chờ đang tải, thông báo lỗi kèm nút "Thử lại", và trạng thái mất kết nối (khi lỗi hoặc mất mạng, các mục cài đặt lưu trên máy gồm ngôn ngữ, giao diện sáng/tối, sinh trắc học vẫn dùng được).

### 11.2 Nguồn màu, bảng `Palette` và chế độ sáng/tối

- **Nguồn màu duy nhất (R-12, B1.4, D-36):** route công khai của web học viên `GET {THI360_WEB_URL}/mobile/appearance-tokens.json`. Route đọc `GET /api/v1/appearance` và chạy `deriveTokens` qua `packages/ui/src/theme/mobile-tokens.ts`; **không** port công thức suy ra màu sang Kotlin hay Swift.
  - Phản hồi `200` gồm: `version`, `mode` (`"light"` | `"dark"`), `presetId`, `light` (`Palette`), `dark` (`Palette`).
  - Bộ đệm và chu kỳ cập nhật: `ETag` theo `version`, `Cache-Control: no-cache`, trả `304 Not Modified` khi `If-None-Match` khớp. App nạp lại khi mở app hoặc đưa app từ nền trở lại sau ít nhất **60 giây** (AC-72), áp ngay ở mọi màn khi `version` đổi mà không cần đăng nhập lại.
  - Xử lý lỗi: route trả `503` khi backend lỗi hoặc khi `presetId` không có trong danh sách bộ màu của web; khi gặp `503`, lỗi mạng hoặc JSON sai hình, app giữ bảng màu đang dùng và không rơi về mặc định một cách im lặng (AC-77).
- **Bảng `Palette` (17 màu định dạng `#RRGGBB`, đọc qua đối tượng `BrandPalette` trong app — B1.7):**

| Nhóm | Trường trong `Palette` | Token web tương ứng | Quy tắc giá trị |
|---|---|---|---|
| Thương hiệu (7 màu) | `primaryFill` | `primary-fill` | Trùng `deriveTokens` cho 9 bộ cố định và `custom` ở cả sáng và tối (AC-71) |
| Thương hiệu | `onPrimary` | `on-primary` | Trùng `deriveTokens` (sáng/tối) |
| Thương hiệu | `primaryText` | `primary-text` | Trùng `deriveTokens` (sáng/tối) |
| Thương hiệu | `primaryContainer` | `primary-50` | Trùng `deriveTokens` (sáng/tối) |
| Thương hiệu | `primaryContainerStrong` | `primary-100` | Trùng `deriveTokens` (sáng/tối) |
| Thương hiệu | `accent` | `accent-500` | Trùng `deriveTokens` (sáng/tối) |
| Thương hiệu | `onAccent` | `on-accent` | Trùng `deriveTokens` (sáng/tối) |
| Trung tính (8 màu) | `bgPage` | `bg-page` (`--color-page`) | Sáng `#F7F8F9` (mục 2.3) · Tối `#0F1418` (`packages/ui/src/styles/tokens.css`), không đổi theo bộ màu (AC-78) |
| Trung tính | `bgSurface` | `bg-surface` (`--color-surface`) | Sáng `#FFFFFF` · Tối `#171D22`, không đổi theo bộ màu |
| Trung tính | `bgSubtle` | `bg-subtle` (`--color-subtle`) | Sáng `#F1F2F4` · Tối `#1E262C`, không đổi theo bộ màu |
| Trung tính | `text` | `text` (`--color-text`) | Sáng `#11181C` · Tối `#E6EAED`, không đổi theo bộ màu |
| Trung tính | `textSecondary` | `text-secondary` (`--color-text-secondary`) | Sáng `#3A3A42` · Tối `#C5CCD2`, không đổi theo bộ màu |
| Trung tính | `textMuted` | `text-muted` (`--color-text-muted`) | Sáng `#6B6B72` · Tối `#A9B2BA`, không đổi theo bộ màu |
| Trung tính | `border` | `border` (`--color-border`) | Sáng `#E8E8EC` · Tối `#2A3238`, không đổi theo bộ màu |
| Trung tính | `borderStrong` | `border-strong` (`--color-border-strong`) | Sáng `#D4D4D8` · Tối `#3A444C`, không đổi theo bộ màu |
| Lỗi / nguy hiểm (2 màu) | `dangerText` | `danger` (`--color-danger`) | Sáng `#B91C1C` · Tối `#F87171` |
| Lỗi / nguy hiểm | `dangerBg` | `danger-bg` (`--color-danger-bg`) | Sáng `#FEF2F2` · Tối `#2E1517` |

- **Độ tương phản (mục 8, AC-76):** chữ đạt tối thiểu **4,5:1** (WCAG AA) và thành phần không phải chữ đạt tối thiểu **3:1** (WCAG 1.4.11) với cả 9 bộ cố định và cấu hình `custom` ở hai chế độ sáng/tối, kiểm tĩnh trên cùng tập cặp chữ–nền của `packages/ui/src/styles/tokens.test.ts`.
- **Mặc định và ngoại tuyến (AC-75):** lần mở đầu tiên chưa lấy được cấu hình từ máy chủ dùng bộ mặc định `thi360-green` chế độ `light` (khớp tệp hợp đồng `contracts/mobile/appearance/tokens-thi360-green.json`). Khi mất mạng ở các lần sau, app giữ bảng màu đã lưu gần nhất trên máy.
- **Chế độ sáng/tối (D-20, AC-73, AC-74):** người dùng chưa tự chọn trên máy (`AppPreferences.themeMode` trống) dùng `mode` của cấu hình admin, **không theo chế độ sáng/tối của hệ điều hành**. Khi người dùng chọn Sáng hoặc Tối trong mục Tài khoản, lựa chọn `AppPreferences.themeMode` ghi đè `mode` của admin, lưu trên máy và giữ nguyên qua các lần tắt app và đăng xuất (như cookie `thi360_theme` trên web).

### 11.3 Khung điều hướng và bố cục theo bề rộng

- **Bốn mục sau đăng nhập:** **Trang chủ**, **Bài thi của tôi**, **Lịch sử** và **Tài khoản**. Đợt đầu, ba mục đầu là màn giữ chỗ chỉ hiện tiêu đề mục và dòng `"Chưa có nội dung"`, không gọi API nghiệp vụ (AC-10); mục **Tài khoản** hiển thị thông tin chỉ đọc từ `GET /api/v1/me/profile` cùng các cài đặt trên máy (R-11).
- **Quy tắc đáp ứng (R-2, AC-6..11, AC-13):** kiểu điều hướng và số cột phụ thuộc bề rộng cửa sổ khả dụng / size class, không phụ thuộc tên máy. Xoay máy, gập/mở máy hoặc đổi kích thước cửa sổ phải giữ đúng màn hiện tại, mục đang chọn và dữ liệu đang nhập dở (AC-11). Khi chạy chia đôi màn hình trên tablet/iPad ở bề rộng hẹp, bố cục tự lùi về 1 cột kèm thanh điều hướng dưới và vẫn dùng đủ chức năng (AC-13).

| Nền tảng | Bề rộng cửa sổ / size class | Thanh điều hướng | Bố cục nội dung |
|---|---|---|---|
| Android | `< 600dp` (bề rộng hẹp, gồm chia đôi hẹp) | Thanh điều hướng dưới (bottom bar) | 1 cột (AC-6, AC-13) |
| Android | `600dp` đến `< 840dp` | Thanh điều hướng bên trái (navigation rail) | 1 cột (AC-7) |
| Android | `>= 840dp` (màn hình gập mở, tablet) | Thanh điều hướng bên trái (navigation rail) | Mục Tài khoản 2 cột: danh sách bên trái, chi tiết bên phải (AC-7) |
| iOS | `horizontalSizeClass == compact` (iPhone, màn ngoài iPhone Duo, iPad chia đôi hẹp) | Thanh tab dưới (tab bar) | 1 cột (AC-6, AC-13) |
| iOS (iPad) | `horizontalSizeClass == regular` | Sidebar bên trái | Hai cột (sidebar trái + vùng nội dung bên phải; mục Tài khoản hiện trong vùng nội dung cạnh sidebar) (AC-8) |
| iOS (iPhone `regular` / iPhone Duo mở) | `regular × regular` (đo trên simulator Xcode 27.1: màn trong `669×951pt`, ngang `951×669pt`; màn ngoài `466×678pt` compact) | Hai nửa; thanh tab nằm ở nửa trái | Nửa trái hiện danh sách Tài khoản kèm thanh tab, nửa phải hiện chi tiết mục đang chọn; gập về màn ngoài quay lại 1 cột (AC-9, D-17) |

- **Ma trận kích thước kiểm thử tương thích (đặc tả mục 9):** mỗi kích thước kiểm ở cả chế độ sáng và tối cho các màn Đăng nhập, Đăng ký, Khóa, Trang chủ, Tài khoản; không tràn ngang, không cắt nội dung:
  - **Android:** `360×640dp`, `412×915dp`, màn hình gập mở `840×720dp`, tablet `1280×800dp`.
  - **iOS:** iPhone `390×844pt`, iPad `1180×820pt` và `820×1180pt`, iPhone Duo màn ngoài `466×678pt` và màn trong `669×951pt` (`951×669pt` khi cạnh dài nằm ngang).

### 11.4 Quy tắc xoay màn hình

- **Khóa chiều dọc (AC-12, D-8):** áp dụng cho điện thoại — Android có bề rộng nhỏ nhất (`sw`) `< 600dp`; iPhone có bề rộng `compact`, kể cả màn ngoài iPhone Duo.
- **Xoay tự do cả hai chiều:** áp dụng cho tablet Android (`sw >= 600dp`), iPad, màn hình gập Android khi mở và màn trong iPhone Duo.

### 11.5 Ngôn ngữ

- **Mặc định tiếng Việt (`vi`)** ở lần mở đầu tiên, bất kể ngôn ngữ hệ điều hành (AC-79, D-22).
- **Song ngữ `vi` / `en` cùng bộ khóa (AC-81):**
  - Android: cặp tệp `res/values/strings_*.xml` và `res/values-en/strings_*.xml`.
  - iOS: các String Catalog `*.xcstrings` (`Shell.xcstrings`, `Auth.xcstrings`, `Recovery.xcstrings`, `Account.xcstrings`, `InfoPlist.xcstrings`).
  - Không lẫn ngôn ngữ trên mọi màn.
- **Đổi tức thì và lưu bền (AC-80):** đổi tại màn Đăng nhập hoặc mục Tài khoản (`AppPreferences.locale`), chữ giao diện đổi ngay, lưu trên máy và giữ qua lần tắt app và đăng xuất.
- **Câu chữ và thông báo lỗi:** câu chữ luồng xác thực lấy theo khóa web học viên trong `apps/web-student/src/messages/vi.json`. Tên sinh trắc học theo thiết bị: Android ghi `"vân tay hoặc khuôn mặt"`; iOS ghi `"Face ID"` hoặc `"Touch ID"` đúng loại phần cứng (AC-66). Mã lỗi máy chủ chưa định nghĩa hiện câu chung thân thiện của màn đó, không lộ mã kỹ thuật (AC-83).

### 11.6 Truy cập, vùng bấm và cỡ chữ

- **Vùng bấm tối thiểu (đặc tả mục 9):** `48dp` trên Android và `44pt` trên iOS.
- **Trình đọc màn hình:** mọi điều khiển có nhãn rõ ràng cho **TalkBack** (Android) và **VoiceOver** (iOS), thứ tự đọc theo thứ tự nhìn; không truyền đạt trạng thái chỉ bằng màu.
- **Phóng cỡ chữ hệ thống:** mức `130%` không cắt hay chồng chữ; mức `200%` mọi màn vẫn cuộn được và dùng được.
- **Che nội dung khi khóa sinh trắc học (AC-58):** màn khóa che hoàn toàn nội dung app, và ảnh xem trước trong trình chuyển ứng dụng không để lộ nội dung bên trong.
