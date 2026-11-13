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
| `primary-600` | `#059669` | **Màu chính**: nút chính, liên kết, số liệu nổi bật, footer |
| `primary-700` | `#047857` | Trạng thái hover của nút chính, thanh trên cùng |
| `primary-800` | `#065F46` | Đầu tối của gradient, chữ trên nền xanh nhạt |
| `primary-900` | `#064E3B` | Tiêu đề lớn trên nền sáng |

### 2.2 Màu nhấn

| Token | Hex | Dùng cho |
|---|---|---|
| `accent-500` | `#F59223` | Nút hành động thi ("Vào thi ngay"), tối đa một nút trên mỗi thẻ |
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

### 2.4 Màu trạng thái

Trang tham khảo không có đủ bộ màu trạng thái cho trang quản trị, nên phần này là bổ sung.

| Trạng thái | Chữ | Nền | Ví dụ |
|---|---|---|---|
| Thành công | `#047857` | `#ECFDF5` | Hoạt động, Thành công |
| Cảnh báo | `#B45309` | `#FFFBEB` | Chưa kích hoạt, Đang xử lý |
| Lỗi / nguy hiểm | `#DC2626` | `#FEF2F2` | Bị khóa, Thất bại, nút Xóa |
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
| Chính | `primary-600`, hover `primary-700` | trắng | không | Hành động chính của màn (một nút) |
| Phụ | trắng | `text-secondary` | `border` | Hủy, Quay lại, Đăng nhập |
| Viền xanh | trắng | `primary-800` | `primary-200` | Hành động phụ có nhấn |
| Nhạt | `primary-100` | `primary-600` | không | Hành động trong thẻ |
| Thi | `accent-500` | trắng | không | Bắt đầu / tiếp tục thi |
| Nguy hiểm | `#DC2626` | trắng | không | Xóa, Khóa (trong dialog xác nhận) |
| Chữ | trong suốt | `primary-600` | không | Liên kết trong dòng, "Xem tất cả →" |

- Cỡ: `sm` 36px, `md` 40px (mặc định), `lg` 48px, `xl` 52px (nút gửi ở trang đăng nhập).
- Bo góc 12px, chữ 14/20 đậm 600, đệm ngang 16px (`md`) hoặc 28px (`lg`).
- Icon lucide 16–18px đặt trước chữ; mũi tên đặt sau chữ.
- Đang xử lý: hiện spinner, khóa nút, giữ nguyên chiều rộng.

### 5.2 Ô nhập, chọn

- Cao 40px (admin) hoặc 48px (web học viên, trang đăng nhập); bo góc 10–12px; nền trắng; viền `border`.
- Focus: viền `primary-600` và vòng sáng `0 0 0 3px rgba(5,150,105,.15)`.
- Nhãn đặt phía trên, 13/20 đậm 500, màu `text-secondary`. Có thể có icon dẫn đầu 16px màu `text-placeholder`.
- Lỗi: viền `#DC2626`, dòng lỗi 12.5px màu `#DC2626` ngay dưới ô. Giữ nguyên dữ liệu người dùng đã nhập.

### 5.3 Chip và huy hiệu

- **Chip lọc:** dạng viên thuốc, cao 40px (web học viên) hoặc 32px (admin), viền `border`, có chấm màu 8px phía trước. Đang chọn: nền `primary-600`, chữ trắng.
- **Chip nhỏ:** cao 32px, chữ 12.5px, nền `primary-50`, viền và chữ `primary-600`.
- **Chip trạng thái:** cao 24px, bo tròn, dùng cặp màu ở mục 2.4, có chấm màu.
- **Huy hiệu đếm:** viên thuốc, viền `border`, chữ `text-muted` (ví dụ "3 đề").

### 5.4 Thẻ

- Nền trắng, viền `border`, bo góc 12–16px, đệm 16–24px.
- Web học viên: thẻ bài thi có ảnh phía trên, tiêu đề 14/19 đậm 600 chữ hoa, dòng thông tin kèm icon, nút rộng hết thẻ. Hover dùng `shadow-lg`.
- Admin: thẻ chỉ dùng để gom nhóm, không có hiệu ứng hover.

### 5.5 Tab

- **Tab phân đoạn** (đăng nhập/đăng ký, chuyển chế độ xem): thanh nền `bg-subtle` bo góc 12px, cao 48px; tab đang chọn nền trắng, `shadow-sm`, chữ `primary-600` đậm 600.
- **Tab nội dung** (chi tiết tài khoản): tab đang chọn nền `primary-50`, chữ `primary-600`, bo góc 10px, cao 40px; tab khác chữ `#54545A`.

### 5.6 Tiêu đề khối

Thanh dọc 4px màu `primary-600` (hoặc màu của nhóm) bên trái, tiêu đề `h3`, huy hiệu đếm, liên kết "Xem tất cả →" căn phải.

### 5.7 Thanh điều hướng (web học viên)

- Thanh trên cùng cao 32px, nền `primary-700`, chữ trắng 13px.
- Thanh menu cao 64px, nền trắng, viền dưới `border`; mục menu 14/20 đậm 500 màu `text-secondary`, mục đang chọn màu `primary-600`.
- Footer nền `primary-600`, chữ trắng.

### 5.8 Khung trang admin

```
┌──────────┬───────────────────────────────────────────────┐
│ Logo     │ Breadcrumb                      🔔  Avatar ▾  │  topbar 56px; menu avatar: Hồ sơ, Đổi mật khẩu, Đăng xuất
│          ├───────────────────────────────────────────────┤
│ Sidebar  │ Tiêu đề trang                 [Nút chính]     │
│ 248px    │ Thanh lọc (thẻ trắng, bo 12px)                │
│ nền      │ ┌───────────────────────────────────────────┐ │
│ trắng    │ │ Bảng                                      │ │
│          │ └───────────────────────────────────────────┘ │
│          │ Phân trang                                    │
└──────────┴───────────────────────────────────────────────┘
```

- Sidebar: mục cao 40px, bo góc 8px, icon lucide 18px; mục đang chọn nền `primary-50`, chữ và icon `primary-600`, đậm 600. Tên nhóm menu dùng kiểu `overline`.
- Nền vùng nội dung `bg-page`.
- Menu và nút chỉ hiện khi người dùng có quyền tương ứng.
- Menu chính đợt 1: **Tổng quan** (Bảng điều khiển) · **Người dùng** (Tài khoản, Lớp học) · **Nội dung** (Cấu hình chung) · **Hệ thống** (Phân quyền, Tùy chỉnh giao diện, Nhật ký). Chức năng phụ của một mục là nút hoặc tab bên trong màn đó, không thành mục menu riêng: Cấp hàng loạt là nút trong màn Tài khoản; Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt là bốn tab của Phân quyền.

### 5.9 Bảng (admin)

- Nằm trong thẻ trắng bo góc 12px. Hàng tiêu đề nền `#FAFAFB`, chữ 13px đậm 600 màu `text-muted`.
- Dòng cao 48px, viền dưới `border`, hover nền `bg-subtle`. Dòng đang chọn nền `primary-50`.
- Cột thao tác căn phải, dùng nút icon 32px có tooltip; thao tác nguy hiểm nằm trong menu "⋯".
- Phía trên bảng: tổng số bản ghi theo bộ lọc. Phía dưới: phân trang và chọn số dòng mỗi trang.
- Trạng thái rỗng: icon, một câu giải thích, nút hành động (nếu có quyền).

### 5.10 Dialog, drawer, thông báo

- **Dialog:** bo góc 24px, `shadow-xl`, rộng 480px (biểu mẫu ngắn) hoặc 720px; nút căn phải, nút chính ở ngoài cùng bên phải.
- **Dialog xác nhận hành động nhạy cảm** (khóa, xóa, reset, ngừng sử dụng): nêu rõ đối tượng và số bản ghi bị ảnh hưởng, có ô **lý do** khi quy tắc yêu cầu, nút xác nhận kiểu Nguy hiểm.
- **Drawer:** trượt từ phải, rộng 520px, dùng cho chi tiết (nơi đang dùng, lịch sử thay đổi).
- **Toast:** góc trên phải, tự ẩn sau 4 giây; lỗi không tự ẩn.

### 5.11 Bố cục hai khu vực (màn Cấu hình chung)

Trái 360px: danh sách nhóm trong thẻ trắng, có ô tìm kiếm và tổng số; dòng đang chọn nền `primary-50` kèm thanh dọc 3px `primary-600`. Phải: thẻ giá trị của nhóm đang chọn. Khi chưa chọn nhóm, khu vực phải hiện hướng dẫn và ẩn nút Thêm giá trị.

### 5.12 Trang đăng nhập

Khung giữa trang, bo góc 24px, `shadow-xl`, chia đôi: trái nền `hero-light` có logo, khẩu hiệu, ba ý chính; phải là biểu mẫu với tab phân đoạn, ô nhập cao 48px có icon, nút chính `xl`. Nút "Đăng nhập với Google" (kiểu Phụ, cao 48px) chỉ hiện khi tính năng được bật.

## 6. Icon và hình ảnh

- Icon: **lucide-react**, nét 1.75–2px, 16px trong nút, 18px trong menu, 20px độc lập.
- Ô icon: vuông 32–40px bo góc 10px, nền `primary-50`, icon `primary-600`.
- Ảnh thẻ bài thi tỉ lệ 16:9, bo hai góc trên theo thẻ.

## 7. Chuyển động

- Chuyển trạng thái màu, viền, bóng: 150ms `ease-out`.
- Dialog và drawer: 200ms.
- Tôn trọng `prefers-reduced-motion`.

## 8. Truy cập và ngôn ngữ

- Độ tương phản: chữ trắng trên `primary-600` đạt khoảng 3,8:1, đủ cho chữ lớn nhưng **chưa đạt AA (4,5:1) cho chữ 14px**. Đã chốt (2026-11-13) giữ nút chính `primary-600` để đúng phong cách tham khảo. Chữ xanh trên nền trắng dùng `primary-700` khi cỡ chữ dưới 14px. Không dùng `text-muted` cho chữ dưới 12px.
- Mọi điều khiển dùng được bằng bàn phím, có vòng focus nhìn thấy được. Nút chỉ có icon phải có `aria-label`.
- Không truyền đạt trạng thái chỉ bằng màu: chip trạng thái luôn có chữ.
- Ngôn ngữ mặc định tiếng Việt, chuỗi hiển thị nằm trong file dịch (`next-intl`). Ngày `dd/MM/yyyy`, số dùng dấu chấm phân tách nghìn.

## 9. Tùy chỉnh giao diện

Giữ bộ cấu hình giao diện như reap, áp dụng **toàn hệ thống** (web admin và web học viên dùng chung một cấu hình). Chi tiết ở spec mục 13.

- **Lối vào:** một mục trong **menu chính** của trang quản trị (nhóm "Hệ thống" → "Tùy chỉnh giao diện"), chỉ hiện với người có quyền `APPEARANCE_MANAGE`. Không đặt trong menu avatar và **không dùng nút nổi** ở mép màn hình như reap.
- **Trang tùy chỉnh:** cột trái là các nhóm tùy chọn, cột phải là vùng xem trước (nút, ô nhập, chip, bảng, thẻ, sidebar thu nhỏ).

| Nhóm | Tùy chọn |
|---|---|
| Bảng màu | Lưới thẻ xem trước các bảng có sẵn; bảng đang dùng có viền `primary-600` và dấu tích. Mặc định là "Thi 360 Xanh lá" (toàn bộ giá trị ở mục 2). Có "Tự tạo": chọn màu chính và màu nhấn, hệ thống sinh dải 50–900 |
| Chế độ | Sáng, tối (tab phân đoạn) |
| Bố cục admin | Vị trí sidebar, kiểu đầy đủ/thu gọn, hiện/ẩn toolbar và footer, độ rộng nội dung |
| Kích thước | Bo góc và chiều cao điều khiển |

- Thanh dưới cùng: **Khôi phục mặc định** (kiểu Phụ) và **Lưu và áp dụng** (kiểu Chính). Thay đổi chưa lưu chỉ hiện ở vùng xem trước.
- Khi màu tự chọn làm chữ khó đọc (tương phản dưới 4,5:1), hiện cảnh báo ngay cạnh ô chọn màu; hệ thống tự chọn màu chữ trắng hoặc đen đọc được hơn trên nền màu chính.
- Vì màu đổi được, thành phần **chỉ dùng token** (`primary-600`, `text`, `border`…), không viết mã màu. Chế độ tối định nghĩa lại các token trung tính; không viết kiểu riêng cho chế độ tối trong thành phần.

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
  --color-accent-500: #F59223;
  --color-accent-700: #B45309;
  --color-text: #11181C;
  --color-text-secondary: #3A3A42;
  --color-text-muted: #6B6B72;
  --color-page: #F7F8F9;
  --color-subtle: #F1F2F4;
  --color-border: #E8E8EC;
  --color-danger: #DC2626;
  --radius-md: 10px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --radius-2xl: 24px;
}
```
