# DESIGN — Thi 360

> Cập nhật: 2026-11-13 · Tham khảo phong cách: https://hsavnu.edu.vn (đã xem trang chủ, Thi thử, Khóa học, chi tiết khóa học, Tin tức, Đăng nhập). Số liệu dưới đây đo trực tiếp từ các trang đó.
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
- Thanh tab luôn **một hàng**; không đủ chỗ thì cuộn ngang ngay trong thanh, không xuống dòng (không truyền `flex-wrap`).
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
- Menu chính đợt 1: **Tổng quan** (Bảng điều khiển) · **Người dùng** (Tài khoản, Lớp học) · **Nội dung** (Cấu hình chung) · **Hệ thống** (Phân quyền, Tùy chỉnh giao diện, Nhật ký). Chức năng phụ của một mục là nút hoặc tab bên trong màn đó, không thành mục menu riêng: Cấp hàng loạt là nút trong màn Tài khoản; Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt là bốn tab của Phân quyền.

### 5.9 Bảng (admin)

- Nằm trong thẻ trắng bo góc 12px. Hàng tiêu đề nền `#FAFAFB`, chữ 13px đậm 600 màu `text-muted`.
- Dòng cao 48px, viền dưới `border`, hover nền `bg-subtle`. Dòng đang chọn nền `primary-50`.
- **Cột Thao tác ưu tiên nút icon, không dùng nút chữ** (chốt 2026-11-13). Mỗi nút là một icon lucide 18px trong vùng bấm 40–44px, căn phải, có **tooltip** hiện tên thao tác khi rê chuột hoặc focus bàn phím, và có `aria-label` nêu rõ thao tác kèm đối tượng ("Sửa vai trò Vận hành").
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
