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
| `primary-600` | `#059669` | Màu thương hiệu: gradient, thanh dọc, viền focus, chấm màu. Không dùng làm chữ hay nền nút có chữ |
| `primary-700` | `#047857` | Thanh trên cùng, chữ xanh dưới 14px trên nền trắng |
| `primary-800` | `#065F46` | Đầu tối của gradient, chữ trên nền xanh nhạt |
| `primary-900` | `#064E3B` | Tiêu đề lớn trên nền sáng |
| `primary-text` | `#047857` (tối: `#10B981`) | **Chữ màu chính**: liên kết, tab/mục nav/trang đang chọn, nút nhạt, icon trên nền `primary-50/100/200`. Sáng: 5,21:1 trên `primary-50`, 4,99:1 trên `primary-100`. Tối: đạt từ 6,05:1 trên `subtle` đến 7,30:1 trên nền trang |
| `primary-fill` | `#047857` | **Nền hành động chính**: nút chính, chip lọc đang chọn, ô logo, checkbox/switch bật. Đi với `on-primary` trắng (5,48:1) |
| `primary-fill-hover` | `#065F46` | Nút chính khi hover (trắng 7,68:1) |
| `primary-fill-active` | `#064E3B` | Nút chính khi bấm (trắng 9,72:1) |
| `on-primary` | `#FFFFFF` | Chữ và icon trên `primary-fill` và nút nguy hiểm |

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

## 4. Khoảng cách, bo góc, viền, bóng

- **Lưới khoảng cách:** bội số của 4px. Khoảng đệm trong thẻ 16–24px. Khoảng cách giữa các khối 24–32px (admin), 48–64px (web học viên).
- **Chiều rộng nội dung:** web học viên tối đa 1170px; admin giãn theo màn hình, lề 24px.
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
- Đang xử lý: hiện spinner, khóa nút, giữ nguyên chiều rộng.

### 5.2 Ô nhập, chọn

- Cao 40px (admin) hoặc 48px (web học viên, trang đăng nhập); bo góc 10–12px; nền trắng; viền `border`.
- Focus: viền `primary-600` và vòng sáng `0 0 0 3px rgba(5,150,105,.15)`.
- Nhãn đặt phía trên, 13/20 đậm 500, màu `text-secondary`. Có thể có icon dẫn đầu 16px màu `text-placeholder`.
- Lỗi: viền `danger` (`#B91C1C`), dòng lỗi 12.5px màu `danger` ngay dưới ô. Giữ nguyên dữ liệu người dùng đã nhập.

### 5.3 Chip và huy hiệu

- **Chip lọc:** dạng viên thuốc, cao 40px (web học viên) hoặc 32px (admin), viền `border`, có chấm màu 8px phía trước. Đang chọn: nền `primary-fill`, chữ `on-primary`.
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
- Thanh tab luôn **một hàng**; không đủ chỗ thì cuộn ngang, không xuống dòng.

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
- **Phạm vi màn hình:** web admin thiết kế cho 768–1920px (máy tính và tablet); điện thoại chưa thuộc phạm vi. Web học viên phải dùng được từ 360px.
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

### 5.10 Dialog, drawer, thông báo

- **Dialog:** bo góc 24px, `shadow-xl`; nút căn phải, nút chính ở ngoài cùng bên phải. Ba cỡ của `DialogContent`: `md` 480px (biểu mẫu ngắn, xác nhận), `lg` 720px (bảng chọn, biểu mẫu vừa), `xl` 920px (biểu mẫu dài chia hai cột).
- **Biểu mẫu trong dialog phải hiện đủ mà không cuộn ở 1366×768 và 1280×720.** Một cột không đủ thì dùng cỡ `xl` và chia hai cột từ bề rộng `md`: cột trái là nhóm trường chính (ví dụ thông tin đăng nhập), cột phải là nhóm còn lại; thứ tự trong DOM (và thứ tự Tab) đi hết cột trái rồi sang cột phải; màn hình hẹp xếp lại một cột và cuộn trong dialog. Danh sách quy tắc dài (quy tắc mật khẩu) xếp hai cột (`PasswordField rulesColumns={2}`). Tiêu đề và hàng nút luôn dính mép trên/dưới, nút đóng (×) luôn bấm được.
- **Dialog xác nhận hành động nhạy cảm** (khóa, xóa, reset, ngừng sử dụng): nêu rõ đối tượng và số bản ghi bị ảnh hưởng, có ô **lý do** khi quy tắc yêu cầu, nút xác nhận kiểu Nguy hiểm.
- **Drawer:** trượt từ phải, rộng 520px, dùng cho chi tiết (nơi đang dùng, lịch sử thay đổi).
- **Toast:** góc trên phải, tự ẩn sau 4 giây; lỗi không tự ẩn.

### 5.11 Bố cục hai khu vực (màn Cấu hình chung)

Trái 264px: danh sách nhóm trong thẻ trắng, có ô tìm kiếm; mỗi dòng cao 56px gồm ô icon 32px theo kiểu nhóm (danh sách, cây, tổ hợp), tên đậm 600, mã 12.5px `text-muted` ở dòng phụ, số giá trị căn phải; dòng đang chọn nền `primary-50` kèm thanh dọc 3px `primary-600`. Danh sách chia hai phần có tiêu đề kiểu `overline`: **Hệ thống** (kèm hình khóa) và **Tùy chỉnh**. Tiêu đề thẻ bên phải có chip loại: Hệ thống (nền `bg-subtle`, có hình khóa) hoặc Tùy chỉnh (nền `primary-50`). Phải: thẻ giá trị của nhóm đang chọn. Khi chưa chọn nhóm, khu vực phải hiện hướng dẫn và ẩn nút thêm.

**Nhóm kiểu cây** (spec mục 9.10): thẻ phải chia hai phần, cây ở giữa và ô chi tiết 340px có viền trái.

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
- **Bố cục nhiều cột chia theo bề rộng vùng chứa, không theo bề rộng màn hình:** vùng nội dung của admin hẹp hơn màn hình 248px vì có sidebar. Đặt `@container` lên khối chứa và dùng biến thể container của Tailwind (`@5xl:` …) cho các cột bên trong; chọn ngưỡng sao cho cột chứa bảng còn đủ rộng cho bảng của nó. Dưới ngưỡng thì xếp chồng.
- **Đường dẫn trang** viết bằng tiếng Anh, trùng tên thư mục feature và tên tài nguyên của API (`/users`, `/classes`, `/catalog`, `/rbac`, `/appearance`, `/login`, `/change-password`). Phần có nội dung và dữ liệu riêng là **trang con** (`/users/bulk-import`, `/rbac/roles`), không phải giá trị query. Query chỉ dùng cho bộ lọc và lựa chọn trong trang (`?permission=USER.VIEW`, `?group=SUBJECT`), tên và giá trị cũng không dùng tiếng Việt không dấu.
- **Trạng thái đang chọn, đang mở, lỗi** không truyền đạt chỉ bằng màu hay nền: luôn có thêm hình (gạch chân, thanh dọc, icon) hoặc chữ, vẽ bằng `border`/`outline` để còn thấy ở chế độ tương phản cao.

## 6. Icon và hình ảnh

- Icon: **lucide-react**, nét 1.75–2px, 16px trong nút, 18px trong menu, 20px độc lập.
- Ô icon: vuông 32–40px bo góc 10px, nền `primary-50`, icon `primary-text`.
- Ảnh thẻ bài thi tỉ lệ 16:9, bo hai góc trên theo thẻ.

## 7. Chuyển động

- Chuyển trạng thái màu, viền, bóng: 150ms `ease-out`.
- Dialog và drawer: 200ms.
- Đổi trang con trong nhóm tab điều hướng: nội dung mới hiện dần và trượt lên 4px trong 200ms `ease-out` (`animate-route-in`, khai báo ở `tokens.css`); tiêu đề trang và thanh tab đứng yên. Không chuyển focus bằng mã: focus ở lại liên kết vừa bấm.
- Bảng đang tải lại: phần thân mờ còn 60% trong 150ms, không đổi kích thước.
- Tôn trọng `prefers-reduced-motion`: mọi hiệu ứng trên kèm `motion-reduce:animate-none` / `motion-reduce:transition-none`.

## 8. Truy cập và ngôn ngữ

- Độ tương phản: mọi chữ trên nền đặc hoặc nền nhạt phải đạt AA (4,5:1) ở cả hai chế độ sáng/tối; `packages/ui/src/styles/tokens.test.ts` liệt kê từng cặp và kiểm. Chữ màu chính dùng `primary-text`, nền nút chính dùng `primary-fill`, không dùng `primary-600` cho hai việc này. Không dùng `text-muted` cho chữ dưới 12px.
- **Quyết định 2026-11-13 (UNI-2559, M.Trường chọn phương án 2 và màu nhấn A):** bản trước giữ `primary-600` làm nền nút chính để đúng trang tham khảo, nhưng đo thực tế chữ trắng trên `#059669` chỉ 3,77:1, chữ `primary-600` trên `primary-50/100` chỉ 3,58/3,43:1 và chữ trắng trên `#F59223` chỉ 2,33:1, đều dưới AA cho chữ 14px. Đổi thành: chữ màu chính và nền nút chính `#047857` (5,21/4,99:1 trên nền nhạt, 5,48:1 với chữ trắng), hover `#065F46`, bấm `#064E3B`; chữ trên nút nhấn `#1A1A1F` (7,44:1), nền `#F59223` giữ nguyên; chữ lỗi `#B91C1C` (nền `#FEF2F2` 5,9:1; `#DC2626` chỉ 4,41:1). Bảng token dưới đây thắng các bản vẽ `docs/design/*.dc.html` ở những màu này.
- Mọi điều khiển dùng được bằng bàn phím, có vòng focus nhìn thấy được. Nút chỉ có icon phải có `aria-label`.
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
