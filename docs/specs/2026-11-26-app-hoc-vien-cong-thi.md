# Đặc tả: App học viên native (Android, iOS) — port cổng thi

> Trạng thái: đã duyệt (cổng G1, 2026-11-26); bản 5 làm rõ bốn chỗ khi viết kế hoạch, không đổi yêu cầu nào (AC-295, AC-366, AC-652, mục 8) và thêm giả định A-118, chờ xác nhận cùng kế hoạch; bản 6 đổi cách hiện trạng thái trên dải phần theo quyết định D-10 (AC-340, AC-341) ; bản 7 thêm tên hiển thị "Thi360", biểu tượng, logo và hai văn bản Điều kiện giao dịch chung, Chính sách bảo mật thông tin (D-11 đến D-15; R-178 đến R-181), chờ duyệt cùng kế hoạch ; bản 8 thêm chức năng học viên tự xóa tài khoản (D-16 đến D-24; R-182 đến R-184), chờ duyệt cùng kế hoạch · Bản: 10 · Người viết: agent (Fable 5.2) · Người rà soát: omp `devin/swe-2-1` mức max ; bản 9 áp các câu trả lời của người yêu cầu cho những điểm chờ duyệt cùng kế hoạch (D-25 đến D-33): tên sản phẩm trong thư của backend lấy từ biến môi trường, bộ mẫu công thức dựng từ danh mục nghiên cứu thay cho dữ liệu demo, ô câu đánh dấu tô màu như web, hai văn bản phát hành không ghi bản nháp, nhãn tiếng Việt cho lý do tự xóa ở web admin ; người yêu cầu duyệt bản 9 cùng kế hoạch bản 8 ngày 2026-11-26 (cổng G2, D-34) ; bản 10 đối chiếu lại với `develop` tại `04a9457b` (UNI-2828 đã thêm hai trang văn bản và logo tạm vào web): hai trang đó được thay nội dung bằng bản đã duyệt (D-35), thêm AC-788, sửa khung thay ảnh bìa ở màn chi tiết bài thi; chờ duyệt
> Theo dõi: UNI-2820 · Nối tiếp: UNI-2785 (khung và xác thực)

## 1. Tóm tắt

Học viên làm được trên app mọi việc đang làm được trên web học viên: xem danh mục và chi tiết bài thi, mua bằng VNPAY, quản lý đơn hàng, xem bài thi của mình, làm bài (bảy dạng câu, câu chùm, phần nghe, tự luận), xem kết quả, xem lại bài, xem lịch sử và sửa hồ sơ. Nguồn của mọi hành vi là web học viên đang chạy trên `develop`. Nội dung câu hỏi và công thức được vẽ native trên từng nền tảng; iOS tối thiểu nâng lên 18.

## 2. Nguồn và thử nghiệm

| Nguồn hoặc thử nghiệm | Bản / ngày / lệnh | Bản lưu hoặc kết quả đo |
| --- | --- | --- |
| Yêu cầu và quyết định của người yêu cầu (M.Trường) | 2026-11-26, trong phiên làm việc | Mục 10; bản đầy đủ ở thư mục phiên `D:/.Vietants_Project/.thi360-dev/mobile-port/decisions.md` |
| Web học viên, backend và hai app | `origin/develop` tại `6b8fc1e6` (2026-11-26); đối chiếu lại với `origin/develop` tại `04a9457b` tối 2026-11-26 (bản 10) | Mục 4; ghi chú đọc mã ở thư mục phiên `intake.md`. Số dòng trong các trích dẫn mã là của `6b8fc1e6`, trừ chỗ bản 10 sửa; bảy thay đổi trên `develop` giữa hai mốc và chỗ đặc tả đổi theo ghi ở mục 7 (dòng "Thay đổi trên `develop` sau khi viết đặc tả") |
| Đặc tả, kế hoạch và báo cáo nghiệm thu đợt khung và xác thực | `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` (bản 6), `docs/acceptance/app-hoc-vien.md` | Các quyết định và quy ước của đợt đó vẫn áp dụng, trừ chỗ mục 10 ghi thay |
| Quy ước giao diện app | `DESIGN.md` mục 11; bản thiết kế 28 màn ở `docs/design/app-hoc-vien/` | Chưa có bản thiết kế cho các màn của đợt này; làm sau vòng rà soát đầu (D-7) |
| Thư viện vẽ công thức | Đọc README của dự án ngày 2026-11-26, chưa chạy thử: SwaTex `https://github.com/PhraseHQ/SwaTex`, RaTeX `https://github.com/erweixin/RaTeX` | SwaTex: Swift thuần, iOS 18 trở lên, tự nhận hỗ trợ 1007/1008 mục trong bảng lệnh của KaTeX và mhchem. RaTeX: lõi Rust, bản Android `io.github.erweixin:ratex-android` 0.1.16 (2026-11-16), Android API 21 trở lên, tự nhận hỗ trợ hơn 99,5% cú pháp KaTeX và mhchem |
| Thử nghiệm | Không chạy | Người yêu cầu quyết định không đo trước khi chốt (D-5) |

## 3. Phạm vi

- **Trong phạm vi:**
  - Mục Trang chủ: danh mục bài thi, chi tiết bài thi, chi tiết bộ đề.
  - Mua: báo giá, khuyến mãi, voucher, tạo đơn, thanh toán VNPAY, quay lại app sau thanh toán, đơn 0 đồng.
  - Đơn hàng: danh sách, chi tiết, thanh toán tiếp, hủy.
  - Mục Bài thi của tôi, gồm bài được giao theo lớp.
  - Chuẩn bị, bắt đầu và tiếp tục lượt thi; làm bài với bảy dạng câu và câu chùm; phần nghe; tự luận; lưu bài, khôi phục lượt; chọn phần; kết thúc phần; nộp bài.
  - Kết quả, xem lại bài, mục Lịch sử.
  - Hồ sơ trong mục Tài khoản: sửa họ tên và số điện thoại, ảnh đại diện, xác minh và đổi email.
  - Vẽ native nội dung câu hỏi (HTML trong danh sách cho phép, công thức LaTeX, hóa học mhchem).
  - Nâng mức iOS tối thiểu của app lên 18.
  - Bổ sung hợp đồng API mobile cho các endpoint app dùng trong đợt này.
  - Tên hiển thị "Thi360" thay cho "Thi 360" ở hai app, web học viên, web admin và các câu chữ của backend mà người dùng đọc (D-15).
  - Biểu tượng và logo mới của hai app (D-11).
  - Điều kiện giao dịch chung và Chính sách bảo mật thông tin: hai trang công khai trên web học viên và phần hiển thị trong hai app (D-11, D-14).
  - Học viên tự xóa tài khoản: API mới ở backend, màn trong hai app và trên web học viên (D-16 đến D-20).
- **Ngoài phạm vi:**
  - Thanh toán qua Apple hoặc Google; phát hành lên cửa hàng ứng dụng.
  - Đăng nhập Google (UNI-2554); thông báo đẩy; chế độ ngoại tuyến đầy đủ (làm bài khi không có mạng từ đầu tới cuối).
  - Tính năng AI (PBI 14–18); các khối giới thiệu của trang `/` trên web (hero, lý do, các bước, câu hỏi thường gặp); trang `/dev/question-kit`.
  - Xem danh mục khi chưa đăng nhập (app luôn đòi đăng nhập).
  - Trình soạn câu hỏi của admin: không đổi cách kiểm công thức.
  - Chặn chụp màn hình, phát hiện rời app hay các biện pháp chống gian lận khác (web không có).
- **Không được đổi:**
  - Hành vi của web học viên và web admin, trừ bốn việc ở cuối danh sách "Trong phạm vi": tên hiển thị, hai trang văn bản mới, hộp Điều khoản ở trang Đăng ký và phần Xóa tài khoản ở trang Hồ sơ. Logo và biểu tượng của hai web giữ nguyên (D-14).
  - Hợp đồng của các endpoint hiện có (hai endpoint xóa tài khoản là endpoint mới); lệnh xóa người dùng của admin và các điều kiện chặn của nó; luồng thanh toán của web (`/checkout/result` trên web vẫn chạy như trước cho người dùng web).
  - Luồng xác thực, phiên, sinh trắc học, cấu hình giao diện của app từ đợt trước, trừ mức iOS tối thiểu.

## 4. Hiện trạng

### 4.0 Chung

- **App mới có khung và xác thực.** Ba mục Trang chủ, Bài thi của tôi, Lịch sử là màn giữ chỗ ghi "Chưa có nội dung" và không gọi API nghiệp vụ (`docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` AC-10; Android `apps/android/app/src/main/java/vn/unicom/thi360/shell/PlaceholderScreen.kt`).
- **Hợp đồng API mobile chỉ có 11 đường dẫn**, đều thuộc xác thực, `/api/v1/me` và `/api/v1/me/profile` (`contracts/mobile/openapi-mobile.json`); tệp do `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java` xuất và được so trong `.github/workflows/backend.yml`. Hai app sinh client từ tệp này (`apps/android/app/build.gradle.kts`, `apps/ios/Thi360/Core/Network/OpenAPI/openapi.json`).
- **Backend nhận Bearer cho mọi endpoint học viên.** Chuỗi bảo mật xác minh JWT (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java:91,129-131`); request có `Authorization: Bearer` được miễn kiểm XSRF (`shared/security/CsrfFilter.java:51-54,82-90`); phiên phải còn trong Redis (`shared/security/SessionFilter.java:66-80`). Các đường `GET /api/v1/public/**` không cần đăng nhập (`SecurityConfig.java:111`).
- **iOS đang đặt mức tối thiểu 17.0** (`apps/ios/project.yml:6,19,40,82,106`; `apps/ios/Thi360Tests/BundleInfoTests.swift`); tài liệu ghi iOS 17 ở `ARCHITECTURE.md:47`, `DESIGN.md:586`, `README.md:85,200`, `apps/ios/README.md:3,11`.
- **Bảng màu app có 17 màu**, chưa có màu thành công, cảnh báo, thông tin (`DESIGN.md` mục 11.2). Web có các token này ở hai chế độ (`packages/ui/src/styles/tokens.css:59-68,237-244`).
- **Công thức hợp lệ là công thức KaTeX vẽ được.** Trình soạn của admin dựng thử bằng KaTeX 0.19.1 rồi mới cho lưu (`apps/web-admin/src/components/rich-text/insert/math/latex-check.ts:24-31`); backend chỉ kiểm lớp của thẻ và dấu ngoặc (`backend/src/main/java/vn/unicom/thi360/questionbank/domain/QuestionHtmlSanitizer.java:109-110`), không có danh sách lệnh LaTeX cho phép.

### 4.1 Danh mục và chi tiết bài thi (mục Trang chủ của app)

Đường dẫn web tính từ `apps/web-student/src/`, backend từ `backend/src/main/java/vn/unicom/thi360/`.

- Trang `/` của web dựng khối "Chọn bài thi" ở máy chủ cùng các khối giới thiệu; điều kiện lọc đọc từ URL, hai lời gọi `filters` và `products` chạy song song (`app/page.tsx:16-40`). Dữ liệu cache 60 giây ở máy chủ web, hết giờ chờ 5 giây (`features/storefront/server-api.ts:8-9,11-21`).
- Điều kiện danh mục gồm `q`, `kind`, `format` (FULL, PART, SUBJECT, BUNDLE), `subject`, `part`, `sort` (NEWEST mặc định, PRICE_ASC, PRICE_DESC), `page`; cỡ trang 12; `part` bị bỏ khi không có `kind`; giá trị lạ về mặc định; API đếm trang từ 0 (`features/storefront/catalog-query.ts:5-10,51-64,86-96`). "Có điều kiện" gồm cả `sort` khác NEWEST (`catalog-query.ts:81-83`).
- Ô tìm áp dụng sau 400 ms ngừng gõ và đưa về trang 1 (`features/storefront/catalog-section.tsx:35,388-397`). Backend bỏ dấu, đổi `đ` thành `d`, gộp khoảng trắng, cắt `q` ở 100 ký tự, tìm chuỗi con trong tên và mã (`commerce/storefront/application/StorefrontQueryService.java:55,137-139`; `commerce/storefront/domain/StorefrontSearchText.java:19-39`).
- Chip kỳ thi lấy `catalogKinds` (mọi kỳ thi đang hoạt động, kể cả kỳ chưa có sản phẩm), rỗng thì lấy `kinds`; nhãn chip là mã kỳ thi; đổi kỳ thi xóa `part`, giữ `subject` (`catalog-section.tsx:408-411,508-535`; `StorefrontQueryService.java:82-92`).
- Ô Hình thức chỉ liệt kê hình thức có sản phẩm đang bán, theo thứ tự FULL, PART, SUBJECT, BUNDLE (`catalog-section.tsx:413-419`; `StorefrontQueryService.java:96-102`). Ô Phần/môn gộp các phần (lọc theo kỳ thi đang chọn, nhãn `mã kỳ · tên phần` khi chưa chọn kỳ) rồi các môn; chọn phần thì đặt luôn kỳ thi của phần và xóa môn; chọn môn thì xóa phần (`catalog-section.tsx:420-444`). Mã môn không có trong danh mục hệ thống trả trang rỗng (`StorefrontQueryService.java:141-147`).
- "Xóa bộ lọc" đưa mọi điều kiện về mặc định, kể cả sắp xếp và ô tìm (`catalog-section.tsx:468-472,597-604`). Đổi điều kiện về trang 1; số trang vượt trang cuối thì chuyển về trang cuối (`catalog-section.tsx:447-454`).
- Kết quả có năm trạng thái: đang tải (5 khung thẻ), lỗi kèm "Thử lại", danh mục rỗng (ẩn cả khối lọc), không có kết quả kèm "Xóa bộ lọc", có dữ liệu; lỗi của `filters` hoặc `products` đều thành trạng thái lỗi (`catalog-section.tsx:49-73,302-347,456-466,501`). Web hiện thẻ trên một hàng cuộn ngang kèm phân trang số (`catalog-section.tsx:75-283`).
- Thẻ sản phẩm: ảnh bìa (bài lẻ có `coverUrl`) hoặc bìa mặc định theo hình thức, chip mã kỳ thi, chip hình thức, chip phần/môn, chip "Bài ôn" khi `buildMode = REVIEW`, tên (bấm mở chi tiết), dòng số câu · phút · lượt, giá, khung nút (`features/storefront/product-card.tsx:15-43,60-149,151-209`). Bộ đề luôn dùng bìa mặc định (`product-card.tsx:169`).
- Giá: có khuyến mãi và giá gốc lớn hơn giá bán thì hiện giá gốc gạch ngang, "Giảm {percent}%" (làm tròn, tối thiểu 1) và "Kết thúc dd/MM" theo giờ `Asia/Ho_Chi_Minh` (`features/storefront/product-price.tsx:24-47`; `features/storefront/format.ts:27-34,49-52`; `packages/web-kit/src/preferences.ts:17`). Khoảng số viết "48–50" khi các đường khác nhau (`format.ts:42-46`).
- Quyền của người xem: một lời gọi `POST /api/v1/me/exam-access/query` cho cả trang mã đang hiện; khi chưa có kết quả thì giữ chỗ, không hiện nút Mua; mã không có dòng trả về coi như NONE (`features/storefront/exam-access.ts:16-27,37-45,51-80`; `catalog-section.tsx:87`). Web không tự tải lại khi quay lại cửa sổ (`providers/query-provider.tsx:72`).
- Backend: tối đa 48 mã mỗi lời gọi, vượt thì 400 `VALIDATION_FAILED`; mã trùng chỉ trả một dòng (`commerce/entitlement/access/api/ExamAccessQueryRequest.java:11-15`; `shared/api/GlobalExceptionHandler.java:73-95`; `commerce/entitlement/access/application/ExamAccessService.java:65-69`); trạng thái theo thứ tự IN_PROGRESS, CAN_START, CHECKING, EXHAUSTED, NONE (`commerce/entitlement/access/domain/ExamAccessState.java:27-39`); mã bộ đề luôn trả NONE ở dòng của bộ kèm `items` theo từng bài (`ExamAccessService.java:86-91`); tài khoản không phải học viên `ACTIVE` nhận 403 (`commerce/entitlement/access/application/ExamLearnerGate.java:31-41`).
- Nút theo trạng thái quyền (`features/storefront/access-action.tsx:56-189`): IN_PROGRESS → "Tiếp tục" (tới lượt `activeAttemptId`) + "Mua thêm lượt"; CAN_START → "Còn # lượt" + "Vào bài thi" (tới chuẩn bị theo `entitlementId`) + "Mua thêm lượt"; EXHAUSTED → nút mua (thẻ) hoặc ghi chú + "Mua lại" (chi tiết); CHECKING → nút "Đang kiểm tra" bị khóa + mã đơn; NONE → "Mua bài thi" hoặc "Mua bộ đề". `hasPendingFulfillment` ẩn "Mua thêm lượt" và hiện dòng đơn đang kiểm tra (`access-action.tsx:100-110`). Mua đi tới `/checkout/{code}?v={versionNo}&p={price}` (`access-action.tsx:20-22`).
- `GET /api/v1/public/storefront/products/{code}`: 404 `PRODUCT_NOT_FOUND` khi không có hoặc chưa công bố, 410 `PRODUCT_DISCONTINUED` với thân `{code, message, fieldErrors, traceId, product}` trong đó `product` chỉ có hai trường `code` và `name`, không có trường chi tiết nào khác (`commerce/storefront/api/ProductGoneResponse.java:8-19`); web chỉ hiện tên và mã đó, trường nào thiếu thì dùng mã trên đường dẫn, và không gọi thêm API nào (`server-api.ts:71-74`; `exam-status-views.tsx:75-114`); bản ghi không phải `ON_SALE` và không phải `DISCONTINUED` (tức bộ đề `UNAVAILABLE`) cũng trả 404 (`commerce/storefront/application/StorefrontQueryService.java:211-225`; `commerce/storefront/api/StorefrontExceptionAdvice.java:34-47`; `commerce/storefront/domain/SaleState.java:3-8`). Chi tiết không có trường `saleState` (`commerce/storefront/api/ProductDetail.java:8-41`).
- Web vẫn có nhánh "bộ đề tạm chưa bán được" cho 409 `PRODUCT_UNAVAILABLE` hoặc `saleState = UNAVAILABLE` (`features/storefront/server-api.ts:54-70`; `app/exams/[code]/page.tsx:49-54`; `features/storefront/exam-status-views.tsx:136-158`; `features/storefront/bundle-buy-panel.tsx:34-36,98-113`); với backend hiện nay nhánh này không xảy ra. Lỗi khác của màn chi tiết rơi vào trang lỗi chung (`server-api.ts:75`; `app/error.tsx:22-39`).
- Màn chi tiết bài thi gồm: ảnh bìa hoặc khung có logo (trước `04a9457b` là chữ "360") + mã kỳ thi, chip, mã sản phẩm, tên, tóm tắt, mô tả, mục tiêu, đối tượng (văn bản thuần), bảng cấu trúc, các lựa chọn, cách làm và cách chấm, quyền lợi, giá và chính sách, khung Giá bán (`features/storefront/exam-detail-view.tsx:134-446`; `features/storefront/buy-panel.tsx:13-69`).
- Màn chi tiết bộ đề gồm: dải bìa "Bộ đề", chip, tên, tóm tắt, mô tả, danh sách bài trong bộ (mỗi bài bấm được sang chi tiết bài), cách dùng bộ đề, giá và chính sách, khung Giá bộ; khung Giá bộ luôn có "Mua bộ đề" và liệt kê bài đã có lượt và bài có đơn đang kiểm tra (`features/storefront/bundle-detail-view.tsx:15-254`; `bundle-buy-panel.tsx:39-47,152-215`).
- Màn 410 có "Về danh mục bài thi" và, khi đã đăng nhập, "Bài thi của tôi"; màn 404 có "Về danh mục bài thi" (`exam-status-views.tsx:75-133`).
- Kênh hỗ trợ trong phần chính sách đọc từ biến môi trường `NEXT_PUBLIC_SUPPORT_PHONE`, `_EMAIL`, `_HOURS` của web, không có API (`features/storefront/support.ts:11-17`; `exam-detail-view.tsx:119-131`).
- `coverUrl` là đường dẫn tương đối `/api/v1/public/storefront/covers/{assetId}`, công khai, không ký (`commerce/storefront/application/StorefrontProjector.java:66,119`); ảnh jpg, png hoặc webp (`exam/application/ExamCoverService.java:34-36`). Các endpoint công khai trả `Cache-Control: public, max-age=60` kèm `ETag`, 304 khi `If-None-Match` khớp (`commerce/storefront/api/StorefrontPublicController.java:41,107-120`); giới hạn 120 lời gọi mỗi phút mỗi IP, vượt thì 429 `RATE_LIMITED` kèm `Retry-After` (`backend/src/main/resources/application.yml:127-128`; `shared/security/RateLimitFilter.java:181-190`). `size` lớn hơn 48 bị cắt về 48 (`StorefrontQueryService.java:53-54,130-135`).
- App hôm nay: mục Trang chủ là màn giữ chỗ không gọi API (`docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` AC-10). Hợp đồng `contracts/mobile/openapi-mobile.json` chưa có endpoint storefront và exam-access; danh sách thao tác của bản lọc nằm ở `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java:196`.

### 4.2 Mua bài thi, thanh toán VNPAY và đơn hàng

- Màn Xác nhận mua của web ở `/checkout/[code]?v=&p=`; `v` là phiên bản và `p` là giá học viên đã thấy ở trang chi tiết, chỉ nhận số nguyên không âm (`v` ≥ 1) (`apps/web-student/src/app/checkout/[code]/page.tsx:22-30`, `features/checkout/checkout-view.tsx:53-57,97-98`).
- Màn này gọi ba API: chi tiết sản phẩm `GET /api/v1/public/storefront/products/{code}` (404 → không tìm thấy, 410 → ngừng bán, 409 `PRODUCT_UNAVAILABLE` → tạm ngừng bán) (`features/checkout/api.ts:72-103`), báo giá `POST /api/v1/me/price-quote` (`api.ts:105-124`), và quyền đang có `POST /api/v1/me/exam-access/query` (`checkout-view.tsx:224-229`).
- Báo giá trả `listPrice`, `promotion` (ưu đãi tự động), `voucher`, `discountApplied`, `voucherNotBetter`, `finalPrice`, `versionNo`, `attemptsGranted`, `changed` (`backend/src/main/java/vn/unicom/thi360/commerce/pricing/PriceQuote.java:9-21`). Lỗi: 404 `PRODUCT_NOT_FOUND`, 410 `PRODUCT_DISCONTINUED`, 422 `VOUCHER_INVALID`/`VOUCHER_EXPIRED`/`VOUCHER_EXHAUSTED`/`VOUCHER_USER_LIMIT`/`VOUCHER_NOT_APPLICABLE`/`VOUCHER_NOT_ELIGIBLE`, 429 `RATE_LIMITED` (`pricing/PriceQuoteController.java:48-52`).
- Kiểm mã voucher bị giới hạn 10 lần mỗi phút và 50 lần mỗi 24 giờ cho mỗi tài khoản; báo giá không kèm mã không bị tính (`pricing/PriceQuoteController.java:59-61`, `pricing/VoucherQuoteRateLimiter.java:25-30`, `backend/src/main/resources/application.yml:238-241`).
- Web hiện dải "Giá hoặc quyền lợi đã thay đổi" khi báo giá có `changed`, khi `versionNo` khác `v`, hoặc khi giá cuối khác `p` mà chưa áp mã (`checkout-view.tsx:233-244,802-822`).
- Ô mã giảm giá: bỏ trống thì báo lỗi, không gọi API; áp mã thì gửi báo giá kèm `voucherCode` và `seenVersionNo`; gỡ mã thì gửi báo giá không mã kèm `seenPrice` và `seenVersionNo`; gõ lại thì xóa lỗi (`features/checkout/voucher-box.tsx:100-211,256-261`).
- Phần tóm tắt: Giá gốc, dòng ưu đãi tự động khi `discountVnd > 0`, dòng mã giảm giá khi `discountVnd > 0`, Thành tiền; giá 0 kèm voucher hiện "0 đ (đã áp dụng voucher)"; nút đổi chữ theo giá 0 hay không (`checkout-view.tsx:1170-1242`).
- Dải quyền đang có: lỗi tải quyền, đơn đang kiểm tra (`state = CHECKING` hoặc `hasPendingFulfillment`) khóa nút mua, đã có lượt (`CAN_START`/`IN_PROGRESS`) thì có nút vào bài và "Mua thêm lượt"; bộ đề liệt kê bài đã có lượt và bài đang chờ (`checkout-view.tsx:278-280,444-620`).
- Tạo đơn `POST /api/v1/me/orders` với header `Idempotency-Key` và thân `{code, expectedAccountId, expectedAmountDue, expectedVersionNo, voucherCode?}`; khóa giữ nguyên khi lỗi mạng, 408, 429, 5xx và chỉ thay khi lỗi 4xx xác định (`checkout-view.tsx:325-340`, `features/orders/format.ts:40-54`). Phản hồi `next` là `REDIRECT` (kèm `paymentUrl`), `FULFILLED` hoặc `CHECKING` (`packages/api-client/openapi.json:54445`, `commerce/order/application/OrderService.java:221-234,574-582`).
- Lỗi khi tạo đơn: 409 `ACCOUNT_CHANGED`, `PRICE_CHANGED` (kèm báo giá mới), `PRODUCT_UNAVAILABLE`, `IDEMPOTENCY_KEY_REUSED`; 410 `PRODUCT_DISCONTINUED`; 422 `VOUCHER_*` và `PAYMENT_ATTEMPTS_EXCEEDED` (tối đa 99 lần thanh toán mỗi đơn) (`order/api/OrderController.java:54-68`, `order/api/OrderExceptionAdvice.java:37-59`, `OrderService.java:198-200,305-308,650-654`, `order/application/OrderCodeGenerator.java:23`). Web xử lý ở `checkout-view.tsx:355-429`.
- Đơn chờ thanh toán của cùng sản phẩm được dùng lại (tạo lần thanh toán mới) khi giá không đổi, hoặc bị thay bằng đơn mới (đơn cũ thành `REPLACED`) khi giá khác (`OrderService.java:294-332,348-355,523-538`).
- Đơn chờ giữ giá 15 phút; mỗi lần thanh toán VNPAY có hạn 15 phút nhưng không quá hạn đơn; tiến trình quét hết hạn chạy mỗi 1 phút (`OrderService.java:91,165,650-660`, `application.yml:225,235-236`, `order/expiry/OrderExpiryWorker.java:28-29`).
- Web chuyển trang sang `paymentUrl` bằng `window.location.assign` (`checkout-view.tsx:73,350-352`). Ở chế độ giả lập, `paymentUrl` là đường dẫn tương đối `/mock-vnpay?...` nếu không đặt `mockPayUrl` (`commerce/payment/gateway/MockVnpayGateway.java:64`); trang `/mock-vnpay` do backend phục vụ (`MockVnpayController.java:65`).
- Backend luôn đặt `vnp_ReturnUrl = {thi360.web-url}/checkout/result` cho cả tạo đơn và thanh toán tiếp (`OrderService.java:314,580,603,674-686`). Cổng thật chuyển nguyên giá trị này cho VNPAY (`commerce/payment/vnpay/RealVnpayGateway.java:131,155`). Cổng giả lập chấp nhận đường dẫn tương đối bắt đầu bằng `/checkout/result` hoặc `/checkout/result/`, còn với URL tuyệt đối chỉ so scheme, host, port với `web-url`, không so đường dẫn (`MockVnpayController.java:234-279`). Lựa chọn "Không trả lời" của trang giả lập quay về `vnp_ReturnUrl` với mã 24 mà không gửi IPN (`MockVnpayController.java:153-156`).
- Trang `/checkout/result` gửi mọi tham số `vnp_*` tới `GET /api/v1/public/payments/vnpay/return` để lấy `{orderCode, paymentState}` (422 `VNPAY_SIGNATURE_INVALID`, 404 `ORDER_NOT_FOUND`); lời gọi này không đổi trạng thái đơn (`features/checkout/checkout-result-view.tsx:37-45,103-123`, `commerce/payment/api/VnpayReturnController.java:36-49`, `commerce/payment/application/VnpayReturnService.java:64-105`).
- Sau đó web đọc `GET /api/v1/me/orders/{code}` và hỏi lại mỗi 2 giây, tối đa 60 giây, khi đơn `PAID` hoặc `PENDING_PAYMENT` chưa có lần thanh toán thất bại (`checkout-result-view.tsx:34-35,146-204`). Hết 60 giây thì đổi sang câu "Đang kiểm tra thanh toán" và nút "Kiểm tra lại ngay" đếm lại 60 giây (`checkout-result-view.tsx:229-233,637-703`).
- Việc cấp bài thi chỉ do IPN từ máy chủ VNPAY tới máy chủ Thi360 quyết định, không phụ thuộc trang quay về (`commerce/payment/api/VnpayIpnController.java:42-48`).
- IPN tìm lần thanh toán theo đúng `vnp_TxnRef` của nó, không so với lần thanh toán mới nhất của đơn; thanh toán tiếp không hủy lần thanh toán đang mở trước đó ở VNPAY (`payment/application/VnpayIpnService.java:97-109`, `OrderService.java:650-672`). Lần thanh toán đầu tiên thành công của một đơn `PENDING_PAYMENT` được tính, dù là lần cũ hơn, và đơn sang `PAID` rồi được cấp; đơn đã `EXPIRED`, `CANCELLED` hoặc `REPLACED` nhận tiền muộn cũng được tính và cấp. Lần thành công thứ hai của cùng đơn không cấp thêm: được ghi `SUCCEEDED` với `duplicate = true` (thu thừa) (`payment/application/PaymentOutcomeApplier.java:198,272-345,451`; test `backend/src/test/java/vn/unicom/thi360/commerce/payment/PaymentCountingRulesIT.java:149-207`, `VnpayIpnConcurrencyIT.java:111-131`, `LatePaymentIT.java:62,100,369`). Khoản thu thừa chưa hoàn được đưa vào báo cáo đối soát; học viên không có thao tác hoàn tiền (`commerce/reconciliation/application/ReconciliationService.java:78-93,144-152`).
- Dải "đang kiểm tra" của quyền chỉ tính đơn `PAID` (đã thu tiền, chưa cấp): `pendingOrderCode` và `hasPendingFulfillment` lấy từ các đơn `state = 'PAID'`; đơn `PENDING_PAYMENT` không khóa nút mua (`order/infrastructure/PurchaseOrderRepository.java:87-94`, `entitlement/access/application/ExamAccessService.java:74-75,94-101`).
- Sau khi lệnh tạo đơn trả `PRICE_CHANGED`, web hiện báo giá mới nhưng mọi lần hỏi giá sau đó vẫn lấy giá trị từ Giá đã thấy ban đầu, và mỗi lời gọi chỉ gửi đúng các trường của nó: tải lại báo giá và gỡ mã gửi `seenPrice` và `seenVersionNo`, áp mã chỉ gửi `seenVersionNo` (không gửi `seenPrice`); dải giá đổi giữ nguyên tới khi đổi sản phẩm hoặc đổi tài khoản (`checkout-view.tsx:113,124-131,169-194,236-244,290-297,374-378`, `voucher-box.tsx:114-118,171-175`).
- Web đổi lỗi của thanh toán tiếp và hủy đơn thành khóa chuỗi theo `mapOrderActionError`: 429 hoặc `RATE_LIMITED` → `RATE_LIMITED`; 410 hoặc `PRODUCT_DISCONTINUED` → `PRODUCT_DISCONTINUED`; 404 `PRODUCT_NOT_FOUND` → `PRODUCT_NOT_FOUND`; 404 khác hoặc `ORDER_NOT_FOUND` → `ORDER_NOT_FOUND`; `ORDER_EXPIRED`, `ORDER_NOT_PENDING`, `PAYMENT_ATTEMPTS_EXCEEDED` giữ nguyên; còn lại → `generic` (`features/orders/format.ts:83-98`). Nút "Tạo đơn mới" chỉ có ở danh sách và chi tiết đơn, khi lỗi là `ORDER_EXPIRED` hoặc `PAYMENT_ATTEMPTS_EXCEEDED` (`orders-view.tsx:312-315`, `order-detail-view.tsx:246-249`); màn mua và màn kết quả chỉ hiện câu lỗi (`checkout-view.tsx:410-414,434`, `checkout-result-view.tsx:206-227`).
- Hành vi nền tảng (không kiểm được bằng repo): trên iOS, liên kết mở app (universal link) không mở app khi trình duyệt đi tới nó bằng chuyển hướng phía máy chủ (VNPAY chuyển về `vnp_ReturnUrl` là kiểu này); theo tài liệu Apple, chạm vào liên kết cùng tên miền với trang đang xem trong Safari cũng mở trong Safari. iOS (mức tối thiểu của app nay là 18, D-5) có phiên đăng nhập web do hệ điều hành cung cấp nhận một liên kết `https` trả về khớp host và đường dẫn, tự đóng khi trang đi tới liên kết đó và trao liên kết cho app. Trên Android, Custom Tab kèm intent-filter App Links đã xác minh nhận được chuyển hướng tới đường dẫn đã khai. Dự án iOS hôm nay còn đặt mức tối thiểu 17.0 (`apps/ios/project.yml:19,40`) và chỉ khai `applinks` (`apps/ios/Thi360/Thi360.entitlements:7`).
- Danh sách đơn `GET /api/v1/me/orders?page&size=20`, mới nhất trước (`features/orders/api.ts:12,24-39`, `order/api/OrderController.java:43-44,70-79`); chi tiết `GET /api/v1/me/orders/{code}` (404 `ORDER_NOT_FOUND`) (`OrderService.java:247-255`); thanh toán tiếp `POST /api/v1/me/orders/{code}/pay` (404 `ORDER_NOT_FOUND`/`PRODUCT_NOT_FOUND`, 409 `ORDER_NOT_PENDING`/`ORDER_EXPIRED`/`PRODUCT_UNAVAILABLE`, 410 `PRODUCT_DISCONTINUED`); hủy `POST /api/v1/me/orders/{code}/cancel` (409 `ORDER_NOT_PENDING`); hai lệnh sau nhận `Idempotency-Key` (`order/api/OrderController.java:90-114`, `OrderService.java:585-648`).
- Bảy trạng thái đơn và màu nhãn: `PENDING_PAYMENT`, `PAID` (vàng), `FULFILLED` (xanh), `EXPIRED`, `REPLACED`, `CANCELLED` (xám), `REFUNDED` (xanh dương); bốn trạng thái giao dịch `CREATED`, `SUCCEEDED`, `FAILED`, `UNKNOWN` (`features/orders/format.ts:56-71`, `order/domain/OrderState.java:4-12`). Học viên không có thao tác hoàn tiền.
- Đơn 0 đồng được tạo ở trạng thái `PAID` rồi cấp ngay (`order/domain/PurchaseOrder.java:242-269,306-335`, `OrderService.java:225-234`).
- Liên kết mở app hiện chỉ khai `/reset-password`: tệp `apple-app-site-association` và `assetlinks.json` (`apps/web-student/src/features/mobile/deep-links.ts:16,61-80`, `app/.well-known/apple-app-site-association/route.ts:1-13`), intent-filter Android (`apps/android/app/src/main/AndroidManifest.xml:28-36`), bộ đọc liên kết của hai app chỉ nhận đúng host web và đúng đường dẫn đó (`apps/android/app/src/main/java/vn/unicom/thi360/feature/recovery/ResetLink.kt:25-39`, `apps/ios/Thi360/Features/Recovery/ResetLink.swift:33-67`, `apps/ios/Thi360/Shell/AppRouter.swift:62-85`, `apps/ios/Thi360/App/Thi360App.swift:45-50`). Liên kết đặt lại mật khẩu bỏ qua màn khóa (`apps/android/app/src/main/java/vn/unicom/thi360/shell/AppNavHost.kt:350`).
- Phiên đăng nhập từ app được ghi kênh `MOBILE` trên máy chủ (`shared/security/SessionStore.java:282,538-544`, `identity/application/AuthService.java:358`).
- Hợp đồng API mobile hiện chưa có đường dẫn nào về mua bán (`contracts/mobile/openapi-mobile.json`, chỉ có `auth/*`, `/me`, `/me/profile`).
- Liên hệ hỗ trợ trên màn mua và màn kết quả lấy từ biến môi trường `NEXT_PUBLIC_SUPPORT_*`, trống thì hiện `landing.support.pending` (`features/storefront/support.ts:11-17`, `features/orders/format.ts:184-189`).

### 4.3 Bài thi của tôi và Lịch sử

**Bài thi của tôi (web `/home`)**

- Trang `/home` dựng `StudentHomeView`: tải phiên; tài khoản `MANAGE` thấy thông báo quản trị, tài khoản `EXAM` thấy `MyExamsView` (`app/home/page.tsx:10-12`, `features/home/student-home-view.tsx:40-82`).
- Web gọi `GET /api/v1/me/exams` một lần mỗi khi mở trang, không có tham số, không phân trang; trả `{ items: MyExamCard[] }` (`features/my-exams/api.ts:11-18`, `features/my-exams/my-exams-view.tsx:37-40`; `commerce/entitlement/access/api/MyExamsController.java:17,29-33`, `MyExamsResponse.java:5-10`; `packages/api-client/openapi.json:54306`, `:11408`, `:11538`).
- Chỉ tài khoản realm APP, `access = EXAM`, `ACTIVE`, không xóa, không phải quản trị nền tảng được gọi; còn lại nhận 403 `FORBIDDEN`; không có phiên là 401 (`commerce/entitlement/access/application/ExamLearnerGate.java:31-41`).
- Thứ tự backend trả: mọi thẻ "Đang kiểm tra" (đơn đã thu tiền chưa cấp quyền, kể cả khi bài đã có quyền) trước, rồi mỗi quyền dùng nguồn `PURCHASE` hoặc `CLASS` một thẻ, quyền tạo sau đứng trước (`commerce/entitlement/access/application/MyExamsService.java:138-191`; `commerce/entitlement/access/infrastructure/ExamAccessReadRepository.java:95-103`).
- Quyền theo lớp đã bị thu hồi hết mà chưa từng có lượt nào thì không có thẻ (`MyExamsService.java:158-164`). Thẻ theo lớp không có `bundles`, không có `pendingOrderCode`, và không bao giờ có `REBUY`/`BUY_MORE` (`MyExamsService.java:176-178,212`).
- Trường của thẻ: `entitlementId`, `code`, `name`, `examKind {code,nameVi,nameEn}`, `format`, `scopeLabel`, `buildMode`, `coverUrl`, `questionCount {min,max}`, `durationMinutes {min,max}`, `attemptsLeft`, `attemptsTotal`, `saleState` (`ON_SALE`/`DISCONTINUED`/`UNAVAILABLE`), `state` (`IN_PROGRESS`/`CAN_START`/`CHECKING`/`EXHAUSTED`), `activeAttemptId`, `activeAttemptState`, `resultAttemptId`, `hasPendingFulfillment`, `pendingOrderCode`, `actions[]`, `bundles[] {bundleId,code,name}`, `entitlementSource`, `classId`, `className`. Thẻ không có id bài thi dạng UUID (`commerce/entitlement/access/api/MyExamCard.java:16-75`, `MyExamBundleRef.java:5-11`).
- `actions` do backend tính: `CHECKING` khi `state = CHECKING`; `CONTINUE` khi có lượt đang mở, nếu không thì `START` khi `attemptsLeft > 0`; `VIEW_RESULT` khi có lượt có kết quả; `HISTORY` khi quyền đã từng có lượt hoặc `state = EXHAUSTED`; khi đang bán và không có đơn chờ cấp: `REBUY` nếu `EXHAUSTED`, `BUY_MORE` nếu còn lượt (`commerce/entitlement/access/domain/MyExamAction.java:36-61`). `state` theo thứ tự ưu tiên: lượt mở → `IN_PROGRESS`, còn lượt → `CAN_START`, đơn chờ cấp và chưa có quyền → `CHECKING`, còn lại `EXHAUSTED` (`commerce/entitlement/access/domain/ExamAccessState.java:27-39`).
- Web tách thẻ `CHECKING` ra trước, phần còn lại nhóm theo bộ đề: thẻ không có `bundles` là "mua lẻ"; thẻ có `bundles` nằm đầy đủ trong bộ `bundles[0]`, các bộ khác chỉ nhận một dòng dẫn chiếu; nhóm xếp theo lần đầu gặp trong danh sách (`features/my-exams/my-exams-view.tsx:44-46`, `features/my-exams/group-my-exams.ts:29-67`). Backend xếp `bundles` của một bài theo mã bộ tăng dần, nên `bundles[0]` là bộ có mã nhỏ nhất, không phải bộ mua gần nhất như chú thích ở web (`ExamAccessReadRepository.java:43-62`, `group-my-exams.ts:23-27`).
- Bố cục khi có thẻ: tiêu đề phần "Bài đã mua" kèm số thẻ và nút "Khám phá bài thi" tới `/#exams`; nhóm "Đang kiểm tra"; các nhóm bộ đề (tên, số bài, mã bộ, "Xem bộ đề", thẻ, dòng dẫn chiếu, chú thích); nhóm mua lẻ, chỉ có tiêu đề "Bài mua lẻ" khi có ít nhất một nhóm bộ đề (`my-exams-view.tsx:141-282`).
- Thẻ "Đang kiểm tra": loại kỳ thi, chip "Đang kiểm tra", tên, một trong bốn câu theo có/không mã đơn và có/không kênh hỗ trợ, nút "Xem đơn" tới `/account/orders/{code}` khi có mã đơn, nút "Tải lại" gọi lại `/me/exams` (`features/my-exams/my-exam-card.tsx:26-77`). Kênh hỗ trợ lấy từ biến môi trường `NEXT_PUBLIC_SUPPORT_*` lúc build, không từ API (`features/storefront/support.ts:11-17`).
- Thẻ bài thi: chip loại kỳ thi, chip nguồn ("Bài theo lớp · {className}" / "Bài theo lớp" / "Đã mua"), chip "Bài ôn" khi `buildMode = REVIEW`, chip tình trạng; tên tối đa 2 dòng; chip hình thức và phạm vi; dòng số câu và phút mỗi lượt; "Lượt còn lại"; các nút và ghi chú theo `actions`, nguồn và `saleState` (`my-exam-card.tsx:79-238`). Ảnh bìa `coverUrl` không hiện trên thẻ.
- Đích của nút: Tiếp tục `/attempts/{activeAttemptId}`, Bắt đầu `/prepare/{entitlementId}`, Mua lại và Mua thêm lượt `/checkout/{code}`, Xem kết quả `/attempts/{resultAttemptId}/result`, Lịch sử `/history?examId={code}` (`my-exam-card.tsx:160-234`). Vì `code` không phải UUID, trang Lịch sử bỏ tham số này và hiện lịch sử không lọc (`features/attempt-history/history-query.ts:46-49`; test `features/attempt-history/history-view.test.tsx:282-283`, `features/my-exams/my-exam-card.test.tsx:57`).
- Lỗi khi chưa có dữ liệu: khối lỗi kèm "Thử lại"; lỗi khi đã có dữ liệu: dải cảnh báo "Dữ liệu chưa được cập nhật." kèm giờ tải gần nhất `HH:mm` theo giờ `Asia/Ho_Chi_Minh` và "Thử lại" (`my-exams-view.tsx:15-23,80-124`). Lỗi mạng và 5xx được thử lại tự động tối đa 2 lần, 4xx không thử lại (`providers/query-provider.tsx:30-34,72`). Web không có trạng thái mất mạng riêng cho trang này.

**Lịch sử (web `/history`)**

- Trang `/history` dựng `AttemptHistoryView` (`app/history/page.tsx:13-19`). Tài khoản `MANAGE` thấy thông báo quản trị; học viên thấy bộ lọc và danh sách (`features/attempt-history/history-view.tsx:369-385`).
- Web gọi `GET /api/v1/me/attempts` với `q`, `kind`, `scope`, `examId`, `from`, `to`, `page` (đếm từ 0), `size = 20`; trả `PageResponse` `{items, page, size, totalItems, totalPages}` (`features/attempt-history/api.ts:7,29-51`, `history-query.ts:95-111`; `attempt/api/AttemptResultController.java:33-46`, `shared/api/PageResponse.java:9-34`; `packages/api-client/openapi.json:50111`, `:12061`, `:1335`).
- Backend: `size` mặc định 20, kẹp trong 1–100; `page` âm thành 0; loại các lượt `STARTING` và `ABORTED`; `q` so khớp không phân biệt hoa thường một phần tên bài; `kind` khớp đúng `examKindCode`; `scope` khớp đúng; `examId` là UUID; `from`/`to` dạng `yyyy-MM-dd` tính theo ngày `Asia/Ho_Chi_Minh`, `to` lấy hết ngày đó; sắp `startedAt` giảm dần rồi `createdAt`, `id` giảm dần (`attempt/application/AttemptHistoryService.java:48-50,86-96,138-176,254-272`).
- Lỗi backend: không phải học viên đang hoạt động → 403 `ATTEMPT_ACCESS_FORBIDDEN` (`attempt/application/AttemptAccessGate.java:26-38`); `examId` không phải UUID → 400 `VALIDATION_FAILED` (`shared/api/GlobalExceptionHandler.java:98-108`); ngày sai dạng → 422 `INVALID_DATE_FILTER` (`AttemptHistoryService.java:268-269`, `GlobalExceptionHandler.java:63-71`). Web không bao giờ gửi giá trị sai: nó lọc `examId` không phải UUID, ngày sai dạng, trang ngoài 1–10 000 trước khi gửi (`history-query.ts:7-8,39-72`).
- Mỗi dòng có `attemptId`, `examId`, `examName`, `examKindCode`, `scope`, `partCode`, `subjectCode`, `choices`, `attemptNo`, `startedAt`, `state`, `regrading`, `rawScore`, `displayScore`, `maxScore`, `scaleMax`, `action` (`CONTINUE` khi `IN_PROGRESS`, còn lại `VIEW_RESULT`), `faultFlag`, `compensated`; điểm chỉ có khi `SCORED` hoặc `PENDING_REVIEW` đang chấm lại (`attempt/api/AttemptHistoryItem.java:13-45`, `AttemptHistoryService.java:184-228`).
- Bộ lọc: ô tìm theo tên (tự áp sau 300 ms ngừng gõ, hoặc ngay khi gửi form), chọn kỳ thi (lấy từ `GET /api/v1/catalog/options?group=EXAM`), chọn phạm vi (`FULL`/`PART`/`SUBJECT`), từ ngày, đến ngày (hai ô ràng buộc lẫn nhau), nút "Xóa bộ lọc" khi có điều kiện; đổi điều kiện nào cũng về trang 1; lọc theo bài (`examId`) chỉ đến từ URL và hiện thành chip có nút bỏ (`history-view.tsx:72-74,163-218,385-527`, `history-query.ts:91-93`). Trạng thái lọc nằm trên URL (`history-view.tsx:151-155,174`).
- Tên môn và tên kỳ thi lấy từ `GET /api/v1/catalog/options?group=SUBJECT|EXAM`, giữ 300 s; không tìm thấy thì hiện mã (`history-view.tsx:229-241,253-271`; `features/attempt-result/api.ts:39-50`, `features/attempt-result/format.ts:94-113`; `catalog/api/CatalogPublicController.java:25-29`).
- Dòng hiện: tên bài, dòng lỗi hệ thống hoặc cấp bù, chip trạng thái, phạm vi kèm chi tiết, nhãn "Điểm luyện tập" (V-ACT) và "Điểm trước khi chấm lại", điểm, lựa chọn, "Lần n" và giờ bắt đầu `HH:mm, dd/MM/yyyy` theo `Asia/Ho_Chi_Minh` (`history-view.tsx:84-132,669-757,794-895`; `features/attempt-result/format.ts:32-58,122-146`).
- Thao tác dòng: `CONTINUE` → `/attempts/{attemptId}`, `VIEW_RESULT` → `/attempts/{attemptId}/result` (`history-view.tsx:108-111`).
- Phân trang số trang khi `totalPages > 1`; khi đổi trang hoặc bộ lọc, danh sách cũ vẫn hiện mờ tới khi có dữ liệu mới (`history-view.tsx:245-251,658-666,901-910`).
- Trạng thái: đang tải, lỗi (không tự thử lại: `retry: false`), dữ liệu cũ khi tải lại lỗi, mất mạng (thay danh sách bằng khối "Không có kết nối"), ba kiểu rỗng (`history-view.tsx:245-251,529-656`).

**App hiện nay**

- Android: mục Bài thi của tôi và Lịch sử dựng `PlaceholderTabScreen` với "Chưa có nội dung" / "Nội dung mục này sẽ được bổ sung ở đợt sau." (`apps/android/app/src/main/java/vn/unicom/thi360/shell/AppNavHost.kt:831-842`, `shell/PlaceholderScreen.kt:86-168`, `apps/android/app/src/main/res/values/strings_shell.xml:9-10`). Test `apps/android/app/src/test/java/vn/unicom/thi360/PlaceholderScreensTest.kt:47-101` khẳng định màn giữ chỗ.
- iOS: `.myExams`, `.history` dựng `PlaceholderDestinationView` (tiêu đề, "Chưa có nội dung", câu `shell.placeholder.myExamsDescription` / `shell.placeholder.historyDescription`); ở bố cục hai nửa của iPhone Duo, nửa trái là `duoLeftOverviewPane`, nửa phải là màn giữ chỗ (`apps/ios/Thi360/Shell/AdaptiveShellView.swift:186-219,240-256`, `apps/ios/Thi360/Shell/PlaceholderDestinationView.swift:26-102`, `apps/ios/Thi360/Shell/ShellLayout.swift:30-37`). Test `apps/ios/Thi360Tests/PlaceholderScreensTests.swift:38-62` khẳng định màn giữ chỗ và không gọi mạng.
- Hợp đồng mobile `contracts/mobile/openapi-mobile.json` chưa có `/api/v1/me/exams`, `/api/v1/me/attempts`, `/api/v1/catalog/options` (chỉ có 11 đường dẫn xác thực và `/me`, `:421`).

### 4.4 Chuẩn bị, bắt đầu lượt thi và khung làm bài

Quy ước đường dẫn trong phần này: tệp web tính từ `apps/web-student/src/`; tệp backend tính từ `backend/src/main/java/vn/unicom/thi360/`; `application.yml` là `backend/src/main/resources/application.yml`.

**Màn chuẩn bị (`/prepare/{entitlementId}`)**

- Web gọi `GET /api/v1/me/entitlements/{entitlementId}/prepare`; lỗi 404 không thử lại, lỗi khác thử lại 1 lần rồi hiện lỗi (`features/attempt-prepare/api.ts:32-41`, `features/attempt-prepare/prepare-view.tsx:153-158`). Backend chỉ cho học viên (`realm = APP`, `access = EXAM`, tài khoản `ACTIVE`), ngược lại 403 `ATTEMPT_ACCESS_FORBIDDEN` (`attempt/application/AttemptAccessGate.java:26-37`); quyền dùng không thuộc tài khoản trả 404 `NOT_FOUND` (`commerce/entitlement/application/EntitlementPortImpl.java:62-67`); bài không còn phục vụ trả 404 `EXAM_NOT_FOUND` (`attempt/application/AttemptPrepareService.java:127-129`). Trước khi trả, lượt `IN_PROGRESS` quá hạn được đóng (`AttemptPrepareService.java:115-125`).
- Ba trạng thái lỗi của màn: 404 → "Không tìm thấy bài thi trong tài khoản đang đăng nhập" kèm nút về Bài thi của tôi; lỗi khác → "Không tải được thông tin chuẩn bị bài thi" kèm "Về Bài thi của tôi" và "Thử lại" (`prepare-view.tsx:425-460`). Khi đang tải hiện khung chờ (`prepare-view.tsx:367-391`). Tài khoản `MANAGE` thấy thông báo quản trị (`prepare-view.tsx:409-423`).
- Nội dung: nhãn kỳ thi, phạm vi (`FULL`/`PART`/`SUBJECT`, kèm tên môn), nhãn "Bài ôn" khi `buildMode = REVIEW`, tên bài, dòng tóm tắt số phần · số câu · số phút · số điểm (`prepare-view.tsx:536-543,601-623`); khối Cấu trúc bài thi với kiểu đồng hồ (FREE / PER_PART / SHARED), danh sách phần có số câu, phút, điểm, nhãn Bắt buộc/Tự chọn, dạng câu hỏi, chính sách nghe, nhóm tự luận bị lược (`prepare-view.tsx:545-550,804-911`); khối "Lựa chọn sẽ gặp trong bài" (`prepare-view.tsx:913-974`); khối Hướng dẫn làm bài gồm HTML hướng dẫn của bài (không ảnh) và 4 ghi chú cố định (`prepare-view.tsx:976-989`); cột "Sẵn sàng làm bài" với số lượt còn lại, nguồn lượt, số câu, tổng thời gian, điểm tối đa (`prepare-view.tsx:992-1037`). Số liệu lấy theo phương án đang chọn, nếu không có thì cộng theo phần (`features/attempt-prepare/metrics.ts:10-57`).
- Lựa chọn trước khi bắt đầu (`preChoice`): chọn một phương án; tự chọn sẵn `preselectedCode` hoặc phương án duy nhất (`prepare-view.tsx:91-102`); phương án `SUBJECT_CHOICE` có `chooseSubjects > 0` đòi chọn đúng N môn, đủ N thì các môn còn lại bị khóa (`prepare-view.tsx:476-492,726-771`); không còn phương án nào thì hiện cảnh báo và khóa nút (`prepare-view.tsx:555,631-645`). Đổi phương án xóa danh sách môn đã chọn (`prepare-view.tsx:472-475`).
- Nút "Bắt đầu bài thi" chỉ bấm được khi không có lượt `IN_PROGRESS`, `attemptsLeft > 0`, còn phương án và lựa chọn đã đủ (`prepare-view.tsx:564-569`); thiếu lựa chọn thì hiện gợi ý dưới nút (`prepare-view.tsx:1129-1135`). Có lượt `IN_PROGRESS` thì thay bằng thông báo và nút "Tiếp tục" mở thẳng màn làm bài (`prepare-view.tsx:1039-1056`). Hết lượt thì hiện "Bạn đã dùng hết lượt của bài này." với 3 câu giải thích theo nguồn và trạng thái bán, kèm "Mua thêm lượt" khi bài còn bán và không phải lượt của lớp (`prepare-view.tsx:1059-1086`).

**Bắt đầu và chờ chuẩn bị đề**

- Mỗi lần bấm Bắt đầu web tạo một `Idempotency-Key` mới (UUID) và gửi `POST /api/v1/me/attempts` `{entitlementId, preChoice?}`; cờ chặn bấm lần hai khi đang gửi (`prepare-view.tsx:215-245`). Backend bắt buộc khóa: thiếu khóa trả 400 `IDEMPOTENCY_KEY_REQUIRED` (`shared/idempotency/IdempotentCommand.java:136,149-158`), dù `packages/api-client/openapi.json` khai header này `required: false`; cùng khóa khác thân trả 409 `IDEMPOTENCY_KEY_REUSED` (`shared/idempotency/IdempotencyService.java:85-92`). Nếu đã có lượt `STARTING` hoặc `IN_PROGRESS` của cùng bài, backend trả lại lượt đó (`resumed = true`, 200); lượt mới trả 202 `STARTING` (`attempt/application/AttemptStartService.java:86-87,355-364,396-413`).
- Lỗi bắt đầu: 409 `NO_ATTEMPTS_LEFT` (`EntitlementPortImpl.java:86`), 409 `NO_SERVING_OPTION`, 422 `CHOICE_NOT_ALLOWED` (`attempt/application/AttemptPathResolver.java:147,162,201`), 422 `SUBJECTS_INVALID` (`AttemptPathResolver.java:863-888`). Web hiện câu `prepare.startErrors.<code>`, 429 hoặc `RATE_LIMITED` thành `RATE_LIMITED`, mã khác thành `generic` (`prepare-view.tsx:55-61,104-114,1088-1105`). Backend hiện không giới hạn tần suất `POST /me/attempts` (`shared/security/RateLimitFilter.java:128-137` chỉ có nhóm công khai, đăng nhập, tải lên, đặt lại mật khẩu).
- Sau khi gọi được, web lưu "ngữ cảnh bắt đầu" (`entitlementId`, `preChoice`, tên bài, phần đầu, thời lượng) theo người dùng và lượt (`prepare-view.tsx:247-254`, `features/attempt-draft/start-context.ts:82-103`). `IN_PROGRESS` → mở màn làm bài; `ABORTED` → màn thất bại; `STARTING` → màn chờ (`prepare-view.tsx:256-275`). Mở lại màn chuẩn bị khi lượt đang `STARTING` cũng vào màn chờ (`prepare-view.tsx:290-294`).
- Màn chờ hỏi `GET /api/v1/me/attempts/{id}` mỗi 1 giây; từ giây thứ 90 chuyển sang thông báo "Hệ thống đang bận" và hỏi mỗi 5 giây, không dừng; lỗi mạng bị bỏ qua và hỏi tiếp (`features/attempt-prepare/preparing-screen.tsx:10-12,100-158`). `ABORTED` → màn "Chưa chuẩn bị được đề" với lý do theo `attempt.abortReasons` và nút "Thử lại"; trạng thái khác `STARTING` → mở màn làm bài (`preparing-screen.tsx:124-138,310-361`). Thanh trên ghi "Chưa tính giờ" (`preparing-screen.tsx:194-197`).
- "Thử lại" dùng lại `preChoice` đã gửi, hoặc trong ngữ cảnh đã lưu, hoặc phương án tự chọn được khi không cần chọn môn; không có thì quay về form chuẩn bị (`prepare-view.tsx:307-337`). Ở màn làm bài, thử lại không có ngữ cảnh thì về Bài thi của tôi; `CHOICE_NOT_ALLOWED`/`SUBJECTS_INVALID` thì về màn chuẩn bị (`features/attempt-player/attempt-player.tsx:1567-1620`).
- Backend tự hủy lượt kẹt `STARTING` quá 2 phút (`ABORTED STALE_STARTING`, trả lượt), quét mỗi 30 giây (`application.yml:197-208`; `attempt/application/AttemptStaleWorker.java:136`); hàng đợi sinh đề 4 luồng, 32 chỗ, chờ tối đa 60 giây rồi hủy `GENERATION_BUSY` (`application.yml:198-201`, `AttemptStartService.java:497-505`).

**Mở màn làm bài (`/attempts/{id}`)**

- Khi mở, web đọc đồng thời trạng thái lượt, bản nháp và vị trí đã lưu trên máy (`attempt-player.tsx:757-770`). Khi tải hiện "Đang chuẩn bị đề"; 404 hoặc `ATTEMPT_NOT_FOUND` → "Không tìm thấy lượt thi"; lỗi khác → "Không tải được lượt thi" kèm "Thử lại" (`attempt-player.tsx:822-835,1515-1565`). `STARTING`/`ABORTED` → dùng lại màn chờ (`attempt-player.tsx:1567-1637`). `GRADING`, `PENDING_REVIEW`, `SCORED`, `GRADING_ERROR` → màn "Lượt thi đã đóng" với tiêu đề theo lý do (`SUBMITTED`, `TIME_UP`, `CHOICE_TIMEOUT`) và nút "Xem kết quả", "Về Bài thi của tôi"; đồng thời xóa bản nháp của lượt (`attempt-player.tsx:174-206,221,546-552,1639-1680`).
- 401 ở bất kỳ lời gọi nào: web làm mới phiên đăng nhập một lần cho mọi lời gọi đang chờ, thành công thì nhận lại phiên sửa và gửi tiếp; thất bại thì chuyển sang đăng nhập kèm đường quay lại (`attempt-player.tsx:578-608`).

**Phiên sửa bài**

- Sau khi tải, web nhận phiên sửa `POST /api/v1/me/attempts/{id}/session` `{sessionId?, takeover?}`, gửi kèm `sessionId` đã lưu ở `localStorage` theo người dùng và lượt (`features/attempt-player/edit-session.ts:311-346`, `features/attempt-draft/edit-session-storage.ts:8-22`). Backend: lượt chưa có phiên → nhận phiên mới; cùng `sessionId` → nối lại; khác `sessionId` mà không `takeover` → 409 `ATTEMPT_OPEN_ELSEWHERE` kèm `lastSeenAt`; có `takeover = true` → lấy phiên, phiên cũ mất quyền (`attempt/application/EditSessionService.java:107-157`; `attempt/api/AttemptExceptionAdvice.java:37-48`). Lượt đã đóng → 409 `TIME_UP` hoặc `ATTEMPT_NOT_OPEN` (`EditSessionService.java:100-105`). Phiên sửa không tự hết hạn theo thời gian: phiên kia dù im lặng bao lâu, máy mới vẫn phải xác nhận tiếp quản (`EditSessionService.java:139-141`).
- Nhịp giữ phiên `POST …/heartbeat` (header `X-Attempt-Session`) mỗi 30 giây (`edit-session.ts:14,434-439`); phản hồi có `serverNow` và `state`; `state` khác `IN_PROGRESS` → tải lại; 409 `ATTEMPT_SESSION_REVOKED`/`ATTEMPT_OPEN_ELSEWHERE` → bị đẩy ra; 409 `TIME_UP`/`ATTEMPT_NOT_OPEN` → tải lại; lỗi mạng bị bỏ qua (`edit-session.ts:369-398`). Mọi lệnh sửa kiểm phiên: sai hoặc thiếu `X-Attempt-Session` → 409 `ATTEMPT_SESSION_REVOKED` (`EditSessionService.java:202-212`).
- Hộp thoại tiếp quản có tên bài, thời điểm phiên kia hoạt động lần cuối, 2 ghi chú, nút "Hủy" (về Bài thi của tôi) và "Tiếp tục tại đây" (`features/attempt-player/takeover-dialog.tsx:35-87`, `attempt-player.tsx:2488-2507`). Bị đẩy ra thì cả màn thay bằng "Lượt thi đã được tiếp tục ở thiết bị khác." với "Về Bài thi của tôi" và "Tiếp tục tại đây" (`takeover-dialog.tsx:94-133`, `attempt-player.tsx:1682-1701`).
- Lần nhận phiên đầu tiên trên một trình duyệt không gửi `sessionId` và máy chủ tự sinh (`edit-session.ts:318-326`; `EditSessionService.java:107-109`); nếu request có `sessionId` mà lượt chưa có phiên thì máy chủ dùng đúng khóa đó (`EditSessionService.java:109`). Nhận phiên lỗi mạng hoặc 5xx: web đặt trạng thái `error`, không thử lại, không báo gì; nút Nộp bài vẫn bấm được nhưng không gửi vì chưa có phiên (`edit-session.ts:364-365`, `attempt-player.tsx:1116-1123,1940-1944`).
- Web còn khóa theo tab trình duyệt (BroadcastChannel, chờ 120 ms) để chỉ một tab sửa được, tab kia "Chỉ xem" (`edit-session.ts:15,47-221`, `takeover-dialog.tsx:139-157`).

**Khung làm bài**

- Thanh trên: tên bài, danh sách phần, đồng hồ, ô trạng thái lưu, nút "Hoàn thành phần" hoặc "Nộp bài", nút thu gọn; thu gọn thì chỉ còn tên phần đang làm (`attempt-player.tsx:2193-2377`). Mất mạng hiện dải "Mất kết nối. Câu trả lời mới nhất chưa được lưu. Thời gian làm bài vẫn tiếp tục." (`attempt-player.tsx:2357-2365`).
- Đồng hồ neo theo giờ máy chủ: lấy `serverNow` của mọi phản hồi trạng thái, nhận phiên, nhịp, lưu, rồi cộng thời gian trôi theo đồng hồ đơn điệu của máy (`features/attempt-player/server-clock.ts:13-55`; `attempt-player.tsx:497-502,670-674,709-713`). `SHARED` đếm về `deadlineAt` của lượt, `PER_PART` về `deadlineAt` của phần đang làm (`attempt-player.tsx:975-976`); cập nhật mỗi 1 giây; còn ≤ 60 giây thì đổi màu nguy hiểm (`attempt-player.tsx:980-1003,1780`); dạng `mm:ss` hoặc `h:mm:ss`, làm tròn giây lên (`server-clock.ts:64-80`). Về 0: dừng âm thanh, ghi lý do `TIME_UP`, sau 500 ms tải lại trạng thái (`attempt-player.tsx:987-994`).
- Danh sách phần: `SEQUENTIAL` chỉ hiển thị trạng thái từng phần, không bấm được; `FREE` là các tab, chỉ phần `OPEN` bấm được; tên phần ưu tiên tên phương án đã chọn (`features/attempt-player/part-navigation.tsx:57-63,86-179`). `FREE` chỉ cho bài kỳ thi `THPTQG` dùng đồng hồ chung hoặc chỉ có một phần (`attempt/application/AttemptNavigationPolicy.java:49-70`).
- Bảng câu hỏi: mỗi câu lá một ô với trạng thái đã trả lời / làm dở / chưa trả lời, đánh dấu, chưa lưu (chấm), đang xem; bộ lọc Tất cả / Chưa làm / Đánh dấu; dòng "{answered}/{total} câu đã trả lời"; chú giải 6 mục (`features/attempt-player/question-nav.tsx:57-294`). Màn hẹp hơn 1024 px có thanh "{current}/{total}" mở bảng trong hộp thoại; màn rộng có cột bảng cố định bên phải (`question-nav.tsx:318-418`). Mức hoàn thành tính theo loại câu (`features/attempt-player/mappers.ts:273-366`).
- Gom nhóm: phần có `displaySegments` thì gom theo đoạn; không có thì gom theo môn khi có từ 2 mã môn (hoặc lựa chọn có từ 2 môn), tiêu đề "Môn {name}", "Câu {from}–{to}", "{count} câu" (`features/attempt-player/segments/segment-units.ts:283-330`; `attempt-player.tsx:1163-1264,2414-2478`). Tên môn lấy từ `attempt.subjects.codes`, nếu mã lạ thì từ danh sách môn của bài qua màn chuẩn bị (`attempt-player.tsx:1185-1230`, `features/attempt-player/api.ts:303-355`).
- Cả phần đang làm trải trên một trang cuộn; cuộn tới đâu thì câu đang xem đổi theo; bấm ô trong bảng thì cuộn tới câu đó (`attempt-player.tsx:1316-1428`). Phím tắt 1–4, ←/→, N/P, F chỉ có trên web (`attempt-player.tsx:1430-1513`).
- Vị trí: mỗi lần đổi câu web lưu `{partId, itemId}` trên máy và gửi lên máy chủ; khi mở lại ưu tiên `lastPosition` của máy chủ, sau đó tới bản trên máy (`features/attempt-player/save-queue.ts:417-443`; `attempt-player.tsx:232-258,779-802`). Backend bỏ qua vị trí trỏ vào phần đã đóng hoặc câu ngoài phạm vi (`attempt/application/AnswerService.java:423-470`).

**Chọn phần sau khi bắt đầu và sinh phần**

- Đóng phần trước một phần tự chọn thì phần đó sang `AWAITING_CHOICE` với hạn chọn 15 phút (`attempt/application/AwaitingChoicePartTransitionHook.java:19,26`). Màn chọn hiện đếm ngược theo giờ máy chủ, còn ≤ 30 giây thì đổi màu và hiện cảnh báo; hết hạn thì khóa nút (`features/attempt-player/part-choice-view.tsx:106-132,184-192,244-265`). Hết hạn ở máy: ghi lý do `CHOICE_TIMEOUT`, tải lại sau 500 ms, chưa đổi thì cứ 2 giây tải lại (`attempt-player.tsx:1016-1055`). Danh sách phương án lấy từ `afterStartChoices` của màn chuẩn bị, tìm `entitlementId` qua ngữ cảnh bắt đầu hoặc `GET /api/v1/me/exams` (`features/attempt-player/api.ts:266-301`).
- Xác nhận `POST …/parts/{partId}/choice` (`Idempotency-Key` bắt buộc, `X-Attempt-Session`), thân `{optionCode ≤ 64 ký tự, subjectCodes ≤ 10}` (`attempt/api/AttemptChoiceController.java:41-68,104-112`). Khóa giữ nguyên khi gửi lại cùng lựa chọn, đổi khi đổi lựa chọn (`attempt-player.tsx:1796-1799`). Lỗi: `CHOICE_WINDOW_CLOSED`, `CHOICE_LOCKED`, `PART_NOT_AWAITING`, `PART_GENERATING` (409), `CHOICE_NOT_ALLOWED`, `SUBJECTS_INVALID` (422) (`attempt/application/AttemptChoiceService.java:416-630`); web tải lại trạng thái với 4 mã 409 đầu, `ATTEMPT_SESSION_REVOKED` → bị đẩy ra (`attempt-player.tsx:1817-1845`).
- `GENERATING` → "Đang chuẩn bị bộ câu hỏi cho phần thi", tải lại mỗi 1,5 giây (`part-choice-view.tsx:134-153`, `attempt-player.tsx:967-973`). `GENERATION_FAILED` → "Chưa chuẩn bị được phần thi" với "Thử lại" gọi `POST …/parts/{partId}/retry` bằng khóa mới (`part-choice-view.tsx:155-177`, `attempt-player.tsx:1848-1888`); web không tự tải lại ở trạng thái này, còn backend tự thử lại sau 30 giây, 2 phút, 5 phút (`attempt/application/PartGenerationRetryWorker.java:41-43`).

**Lưu bài**

- Hàng đợi lưu theo từng câu, mỗi câu có `seq` tăng dần trên máy (`save-queue.ts:322-392`). Thời điểm gửi: câu chọn (một đáp án, nhiều đáp án, Đúng/Sai, ghép đôi) và cờ đánh dấu gửi ngay; trả lời ngắn và điền khuyết chờ 800 ms sau lần gõ cuối; tự luận chờ 2 giây, tối đa 15 giây kể từ thay đổi đầu chưa gửi; vị trí chờ 400 ms; đổi câu mà còn thay đổi chưa gửi thì gửi ngay (`save-queue.ts:19-23,92-102,363-392,412-443`).
- `PUT …/answers` (header `X-Attempt-Session`) mỗi lần tối đa 50 thay đổi và 50 cờ, vị trí đi ở lô cuối (`save-queue.ts:23,665-705`; `attempt/api/SaveAnswersRequest.java:13,20`). Backend giới hạn 20 lần gọi mỗi 10 giây cho một lượt, quá thì 429 `RATE_LIMITED` kèm `Retry-After` giây, tối thiểu 1 (`attempt/application/AttemptSaveRateLimiter.java:264-265,307-322`; `AttemptExceptionAdvice.java:50-61`). Một câu trả lời tối đa 65 536 byte (`attempt/application/AnswerValueValidator.java:29`).
- Kết quả từng câu: `seq` lớn hơn bản máy chủ và hợp lệ → `SAVED`; bằng → `SAVED` không ghi; nhỏ hơn → `STALE`; ngoài phạm vi → `REJECTED NOT_IN_SCOPE`; phần đã đóng → `REJECTED PART_CLOSED`/`TIME_UP`; không hợp lệ → `REJECTED INVALID_ANSWER` (`AnswerService.java:305-418`). Web: `STALE` → tải lại và đối chiếu; `TIME_UP`/`PART_CLOSED` → khóa câu và tải lại; lý do khác → trạng thái "Chưa lưu · Dữ liệu không hợp lệ" (`save-queue.ts:721-775`).
- Lỗi cả lời gọi: 409 `ATTEMPT_SESSION_REVOKED`/`ATTEMPT_OPEN_ELSEWHERE` → bị đẩy ra; 409 `TIME_UP`/`ATTEMPT_NOT_OPEN`/`PART_CLOSED` → tải lại; 429 → chờ `Retry-After` (không có thì theo nấc lùi); 5xx → "Máy chủ bận"; lỗi khác → "Lỗi kết nối"; nấc lùi 1, 2, 4, 8, 16, 30 giây rồi giữ 30 giây, đặt lại khi gửi được hoặc có mạng lại (`save-queue.ts:25,832-891`). Ô trạng thái: "Đã lưu", "Đang lưu...", "Chưa lưu · {lý do}" (`attempt-player.tsx:2000-2014,2284-2297`).
- Mất mạng thì không gửi; có mạng lại thì đặt lại nấc lùi, đọc lại trạng thái, đối chiếu rồi gửi phần còn lại (`save-queue.ts:463-514`; `attempt-player.tsx:741-755`). Đóng trang khi còn thay đổi chưa lưu thì trình duyệt hỏi lại (`attempt-player.tsx:747-751`).
- Bản nháp: mỗi thay đổi ghi vào IndexedDB `thi360-attempt-drafts` theo `userId:attemptId:itemId` (`features/attempt-draft/db.ts:3-46`, `features/attempt-draft/draft-store.ts:21-87`); được máy chủ xác nhận thì xóa (`save-queue.ts:945-951`). Khi mở lại: bản nháp có `seq` lớn hơn `answerSeq` của máy chủ thì thắng và được gửi lại, ngược lại dùng bản máy chủ; cờ nháp chỉ thắng khi khác cờ máy chủ (`save-queue.ts:168-240`). Lượt đóng hoặc nộp xong thì xóa nháp của lượt (`attempt-player.tsx:551,1966`); đăng xuất xóa nháp của mọi lượt của mọi tài khoản trên trình duyệt (`features/session/api.ts:67-78`, `draft-store.ts:185-193`) và không hỏi lại hay cảnh báo dù còn thay đổi chưa lưu (`features/home/student-user-menu.tsx:42-50`, `features/auth/change-password-view.tsx:209-219`). Lượt `ABORTED` cũng bị xóa nháp và ngữ cảnh bắt đầu (`attempt-player.tsx:546-551`, `draft-store.ts:128-133`), nên "Thử lại" ở màn làm bài của lượt đó không còn ngữ cảnh và về Bài thi của tôi (`attempt-player.tsx:1573-1577`).

**Hoàn thành phần, nộp bài, đóng lượt**

- Hoàn thành phần (`SEQUENTIAL`, không phải phần cuối): hộp thoại với số câu chưa trả lời, số câu làm dở, ghi chú phần sau; xác nhận thì hủy các lần chờ gửi và gửi `POST …/parts/{partId}/finish` `{finalAnswers}` gồm mọi câu chưa lưu của phần, tối đa 500 (`features/attempt-player/finish-part-dialog.tsx:28-112`; `attempt-player.tsx:1783-1786,1890-1938`; `save-queue.ts:24,516-536`). Backend: `FREE` → 409 `PART_FINISH_NOT_ALLOWED`; phần cuối → 409 `ATTEMPT_PART_IS_LAST`; phần đã đóng → 200 như cũ; hết giờ → 409 `TIME_UP`; không mở → 409 `PART_CLOSED` (`attempt/application/AttemptLifecycleService.java:385-418`; `attempt/application/AttemptGuard.java:126-137`). Endpoint này không nhận `Idempotency-Key`; gửi lại an toàn vì phần đã đóng trả 200 như cũ (`attempt/api/AttemptLifecycleController.java:29-36`).
- Nộp bài: hộp thoại với số câu đã làm, làm dở, bỏ trống, đánh dấu, số câu thiếu theo phần, số chữ bài viết và cảnh báo chưa đủ chữ (`features/attempt-player/submit-confirm-dialog.tsx:51-200`; `attempt-player.tsx:1711-1769`). Gửi `POST …/submit` với `Idempotency-Key` giữ nguyên tới khi thành công và `{finalAnswers}` tối đa 500 (`attempt-player.tsx:1940-1993`). Backend: lượt đã đóng → 200 trạng thái đã ghi; `SEQUENTIAL` còn phần chưa xong → 409 `PART_NOT_LAST` (`AttemptLifecycleService.java:458-535`). Nút Nộp bài: `FREE` luôn có khi lượt đang làm; `SEQUENTIAL` chỉ ở phần cuối đang mở (`attempt-player.tsx:1787-1789`).
- Gửi hoàn thành phần hoặc nộp bài mà mất kết nối trước khi có phản hồi: hộp thoại đổi sang "Chưa xác nhận …" với nút "Kiểm tra trạng thái lượt"; có mạng lại thì tự kiểm tra (`attempt-player.tsx:866-941`, `finish-part-dialog.tsx:42-51`).
- Máy chủ tự đóng phần và lượt quá hạn mỗi 10 giây (`attempt/application/AttemptDeadlineWorker.java:131`) và mỗi lần đọc trạng thái (`attempt/application/AttemptStateService.java:204-206`, `AttemptGuard.java:72-75`); lệnh sửa gặp hạn đã qua nhận 409 `TIME_UP` (`AttemptGuard.java:132-137,151-163`).

**Hợp đồng mobile**

- `contracts/mobile/openapi-mobile.json` hiện chỉ có các đường xác thực, `/api/v1/me` và `/api/v1/me/profile`; chưa có đường nào của phần này.

### 4.5 Nội dung câu hỏi, các dạng câu và cách trả lời

- **Nội dung câu hỏi là chuỗi HTML trong một danh sách đóng.** Thẻ được phép: `p br strong em u s sub sup ul ol li table thead tbody tr th td span img figure figcaption blockquote footer cite` (`packages/ui/src/rich-text/html-contract.ts:11-36`; backend giống hệt `backend/src/main/java/vn/unicom/thi360/questionbank/domain/QuestionHtmlSanitizer.java:75-78`). Thuộc tính và giá trị đóng: `span.math-inline|math-block|blank` với `data-blank` 1–20, `u[data-label]` một chữ A–H, `p.figure-note|figure-source` chỉ trong `figure`/`blockquote`, `td.num`, `th[scope=col|row]`, `colspan`/`rowspan` số nguyên, `figure.data-table|image|cross`, `figure.image` bắt buộc `data-width=small|medium|full` và `data-zoom=true|false`, `blockquote.quote` bắt buộc `data-kind=poem|prose|source` và `data-line-numbers=0|5` (khác `0` chỉ khi `poem`); `img` mang `src`, `alt`, `width`, `height`, `data-asset-id`; `style` và `on*` cấm mọi nơi (`html-contract.ts:41-97`). Luật cấu trúc: `figure`/`blockquote` chỉ ở cấp đầu; `figure.image` = một `img` rồi `figcaption` tùy chọn rồi `p.figure-source`; `figure.data-table` = đúng một `table` kèm `figcaption`, `p.figure-note`, `p.figure-source`; `figure.cross` = đúng một `table`; `blockquote` = một hay nhiều `p` thường, `footer` tùy chọn, `p.figure-source` cuối; `figcaption` trong `figure`, `footer` trong `blockquote.quote`, `cite` trong `footer`; `span.blank` không có con (`html-contract.ts:166-266`). Ô bảng trong `figure` chỉ chứa `strong em sub sup br` (`QuestionHtmlSanitizer.java:118-120`).
- **Web lọc lại khi hiển thị** dù backend đã lọc khi lưu (`packages/ui/src/rich-text/rich-text-view.tsx:77`): thẻ ngoài danh sách bị mở bọc, giữ con; `script style template iframe object embed` bị bỏ cả nội dung; chú thích HTML bị bỏ; thuộc tính sai bị bỏ; `figure`/`blockquote` sai thuộc tính, `span.blank` sai và phần tử sai cấu trúc bị bỏ cả khối (`packages/ui/src/rich-text/sanitize.ts:47-128`, `html-contract.ts:38-39`).
- **Kiểu hiển thị trên web** (`packages/ui/src/rich-text/rich-text.css`): bảng có viền `border-strong`, ô tiêu đề nền `table-head` chữ đậm 600, `td.num` canh phải chữ số đều (`:31-49,92-96`); ảnh tối đa bề rộng cột (`:51-54`), `figure.image` canh giữa, `small` 40 %, `medium` 70 % (`:79-90`); `figure` có khung viền bo góc, `figcaption` đậm canh giữa, `figure-note`/`figure-source` chữ nhỏ màu `text-muted` (`:57-77`); đoạn trích có nền, cột số dòng 24px (`:99-134`); chỗ trống là chip viền đứt màu `info` (`:137-149`); gạch chân có nhãn màu `primary-text` với chữ nhãn nhỏ đậm ngay dưới cụm gạch (`:152-167`); công thức khối canh giữa (`:182-186`). Bảng không có khung cuộn riêng: `max-width: 100%` (`:31-35`).
- **Chỗ trống của ngữ liệu chùm** `span.blank[data-blank=n]` hiện chữ `(blankNumberStart + n − 1) ____` (`rich-text-view.tsx:24-30`). **Đoạn thơ** `data-line-numbers="5"`: mỗi dòng (ngắt bằng `br`) một hàng, số dòng ghi ở dòng 5, 10, 15…, đếm liên tục qua các khổ, không đếm `p.figure-source` (`rich-text-view.tsx:36-67`).
- **Ảnh** trong HTML của lượt thi được trỏ về `GET /api/v1/me/attempts/{attemptId}/assets/{assetId}`: lấy `data-asset-id` khi `src` rỗng, đổi đường admin `/api/v1/admin/question-assets/{id}` sang đường của lượt (`apps/web-student/src/features/attempt-player/attempt-assets.ts:7-9,71-118`). Backend chỉ trả tệp khi lượt thuộc học viên, phần chứa câu đang `OPEN` hoặc `CLOSED`, câu trong phạm vi và tài nguyên nằm ở trường học viên được xem (đề, phương án, ý, ngữ liệu, `inputHint`, tệp đính kèm; không bao giờ lời giải hay hướng dẫn chấm); sai thì `404 NOT_FOUND` (`backend/.../attempt/application/AttemptAssetService.java:158-199,228-290,372-406`). Phản hồi có `Cache-Control: private, no-store` (`attempt/api/AttemptAssetController.java:73-78`). Ảnh trong lời giải ở màn xem lại được backend viết sẵn sang `/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}` (`attempt/application/AttemptResultService.java:761-770`). Web hiện ảnh bằng thẻ `img` thường: không có khung chờ, không có trạng thái lỗi riêng, không phóng to được; `data-zoom` được lưu nhưng không nơi nào đọc (tìm `zoom` trong `packages/ui/src` và `apps/web-student/src` chỉ thấy `html-contract.ts:46`).
- **Công thức** viết theo ba cách: `<span class="math-inline">\(…\)</span>`, `<span class="math-block">\[…\]</span>`, và cặp `\( \)` / `\[ \]` gõ thẳng trong chữ; span không có cặp dấu thì cả chữ trong span là LaTeX; cặp dấu không khớp giữ nguyên là chữ (`packages/ui/src/rich-text/math-render.ts:62-123`). Web dựng bằng KaTeX 0.19.1 (`packages/ui/package.json:33`) với mhchem cho `\ce`, `\pu` (`math-render.ts:4-25`), `throwOnError: true`; lỗi thì hiện mã nguồn kèm cặp dấu trong khung cảnh báo `math-error`: viền và nền `warning`, chữ đơn cách (`math-render.ts:52-60`, `rich-text.css:170-179`). Trình soạn admin chỉ cho lưu công thức KaTeX dựng được (`apps/web-admin/src/components/rich-text/insert/math/latex-check.ts:24-35`).
- **Ràng buộc tích hợp đã biết của hai bộ vẽ công thức** (đọc từ README của dự án ngày 2026-11-26, chưa thử; `D:/.Vietants_Project/.thi360-dev/mobile-port/intake.md:110-111`): RaTeX cho Android (`io.github.erweixin:ratex-android` 0.1.16, JNI + Kotlin + Canvas, minSdk 21) vẽ qua API của Android View và chữ có định dạng (`RaTeXView`, `RaTeXSpan` cho `TextView`), không có thành phần Compose chính thức, trong khi app Android viết bằng Jetpack Compose (`DESIGN.md` mục 11); SwaTex cho iOS (0.5.0–0.6.0, Swift thuần) cần iOS 18 trở lên. Cả hai đều dưới bản 1.0 (RaTeX sửa lỗi parser bị tấn công từ chối dịch vụ ở 0.1.11), nên phiên bản dùng phải được ghim. Bản AAR 0.1.14 của RaTeX từng có lỗi căn trang 16 KB, loại lỗi chỉ lộ ra trên máy hoặc ảnh máy ảo ARM64 dùng trang 16 KB.
- **Bộ mẫu có sẵn:** `docs/specs/fixtures/rich-text-samples.json` có 43 mẫu (12 hợp lệ: công thức inline và khối, mhchem, bảng số liệu, bảng gộp ô có công thức, ảnh, thơ có số dòng, văn xuôi có nguồn, gạch chân có nhãn, ngữ liệu có chỗ trống, sơ đồ lai, bảng thường cũ, đoạn trích có tác giả và nguồn; 31 mẫu sai kèm vi phạm mong đợi). `apps/web-student/src/app/dev/question-kit/question-samples.json` có 11 câu mẫu đủ 8 dạng, mỗi câu kèm câu trả lời và kết quả chấm.
- **Tám dạng câu** `SINGLE_CHOICE, MULTIPLE_CHOICE, SHORT_ANSWER, CLOZE, QUESTION_SET, TRUE_FALSE, MATCHING, ESSAY` (`packages/ui/src/question/types.ts:10-18`); thân câu học viên nhận không có đáp án (`packages/api-client/openapi.json` schema `AttemptQuestionBody` và các schema `SingleChoice`, `Cloze`, `QuestionSet`…; `packages/ui/src/question/wire-parsers.ts:200-412`). `optionOrder` của từng câu là thứ tự hiển thị phương án (hoặc cột phải của Ghép đôi); chữ cái A, B… gán theo vị trí (`packages/ui/src/question/helpers.ts:3-34`, `choice-question.tsx:38,57-60`, `matching.tsx:52`).
- **Giá trị gửi lên** (`apps/web-student/src/features/attempt-player/mappers.ts:128-181`): `SINGLE_CHOICE {optionId}` hoặc không có `optionId` khi bỏ chọn; `MULTIPLE_CHOICE {optionIds[]}`; `SHORT_ANSWER {text}` đúng như gõ; `CLOZE {blanks:{id: chữ}}`; `TRUE_FALSE {statements:{id: boolean}}` chỉ ý đã chọn; `MATCHING {pairs:{leftId: rightId}}` chỉ cặp đã ghép; `ESSAY {html, wordCount}`; `QUESTION_SET` không có giá trị riêng, mỗi câu con lưu riêng (`mappers.ts:132,183-197`).
- **Backend kiểm từng câu trả lời** (`backend/.../attempt/application/AnswerValueValidator.java`): JSON tối đa 65 536 byte (`:29,237-243`); id phương án, ô, ý, mục phải có trong thân câu, `optionIds` không trùng (`:72-100,115-148`); Ghép đôi không cho hai mục trái cùng một mục phải khi `reuseRight=false` (`:150-177`); chữ không chứa ký tự `\u0000` (`:102-113,127`); Tự luận: HTML tối đa 65 536 byte trước và sau khi lọc theo danh sách chỉ chữ (bỏ `img`), máy chủ tự đếm lại số chữ (`:179-235`). Sai thì câu đó nhận `REJECTED` lý do `INVALID_ANSWER` (`attempt/application/AnswerService.java:399-411`); web hiện "Chưa lưu · Dữ liệu không hợp lệ" (`apps/web-student/src/messages/vi.json:417-428`, khóa `attempt.saveStatus.reasons.rejected`).
- **Chấm Trả lời ngắn và Điền khuyết** so chữ sau khi bỏ khoảng trắng đầu cuối, gộp khoảng trắng, chuẩn hóa NFC và chuyển chữ thường (`backend/.../attempt/domain/grading/ItemGrader.java:606-636`); mọi khác biệt khác (dấu câu, ký tự thay thế) làm sai.
- **Khung câu** (`packages/ui/src/question/question-frame.tsx`): tiêu đề "Câu n" (Điền khuyết nhiều ô: "Câu n–m"), chip tên dạng (Điền khuyết: "Điền khuyết · k"), nút cờ khi làm bài, đề bài, phần điều khiển của dạng câu, khối "Lời giải" khi xem lại (`:50-91,106-122`).
- **Từng dạng khi làm bài:** Một đáp án là nhóm radio, bấm lại phương án đang chọn là bỏ chọn (`packages/ui/src/question/choice-question.tsx:86-101`); Nhiều đáp án là checkbox kèm dòng "Chọn các phương án đúng" (`:54-56,74-85`); Trả lời ngắn là một ô nhập, gợi ý định dạng, bàn phím số cho `INTEGER/DECIMAL/FRACTION`, thanh ký hiệu khi `answerFormat` là `CHEM_FORMULA/CHEM_EQUATION/TEXT` hoặc `answerFormat` trống và `inputHint` (sau khi bỏ dấu) có từ "ion" đứng riêng hay một trong các cụm "cong thuc", "ky hieu", "ki hieu", "formula", "equation", "symbol" (`short-answer.tsx:44-112,141-154`, `symbol-input/symbols.ts:17-75`); Điền khuyết là ô nhập nằm ngay trong đoạn tại mỗi dấu `[[id]]`, đánh số theo thứ tự xuất hiện (`cloze.tsx:17,31-104,144-185`); Đúng/Sai là mỗi ý một hàng radio Đúng/Sai, bấm lại là bỏ chọn (`true-false.tsx:53-137`); Ghép đôi trên màn làm bài là ô chọn ở mỗi mục trái (`matching.tsx:92-139`; màn làm bài không truyền `matchUi` nên dùng mặc định `select`, `apps/web-student/src/features/attempt-player/attempt-player.tsx:2129-2182`); Tự luận là trình soạn TipTap chỉ có đậm, nghiêng, danh sách chấm, danh sách số, hoàn tác, làm lại (`packages/ui/src/essay-editor/tiptap-essay-editor.tsx:138-154,303-353`).
- **Bộ câu hỏi** (`packages/ui/src/question/question-set.tsx:57-110`): dòng "Ngữ liệu chung cho câu a–b", đề chung, khung ngữ liệu, rồi các câu con đầy đủ. Từ 1024px (`lg`) ngữ liệu nằm cột trái (tỉ lệ 7:8), dính ở đỉnh và cuộn riêng, có đầu khung "Câu a–b"; hẹp hơn thì ngữ liệu nằm trên các câu con.
- **Tự luận:** HTML ra là đầu ra TipTap đã lọc, bỏ đoạn rỗng cuối, bài trống là chuỗi rỗng (`tiptap-essay-editor.tsx:57-61,168-175`); đếm chữ: bỏ thẻ, bỏ công thức, NFC, tách theo khoảng trắng, cụm có ít nhất một chữ cái hoặc chữ số là một chữ, ranh giới khối là khoảng trắng (`packages/ui/src/question/count-words.ts:1-48`, cùng luật ở `AnswerValueValidator.java:202-235`); `wordLimit` là mức tối thiểu chỉ để nhắc, không chặn (`essay.tsx:58-62,109-118`).
- **Audio có giới hạn lượt nghe** (`packages/ui/src/question/audio-player/audio-player.tsx`, `apps/web-student/src/features/attempt-player/audio/attempt-audio-adapter.ts`): mở lần nghe `POST /api/v1/me/attempts/{attemptId}/audio/{assetId}/plays {requestId}`, phát tiếp `…/plays/{playId}/resume`, báo tiến độ `PUT …/plays/{playId}/progress {positionMs}` mỗi 10 s, kết thúc `POST …/plays/{playId}/end`, đều có header `X-Attempt-Session` (`attempt-audio-adapter.ts:111-184`, `audio-player.tsx:41`); tải âm thanh `GET …/assets/{assetId}?playId=` (`attempt-audio-adapter.ts:275-277`). Không có thanh tua, không đổi tốc độ, không có nút dừng (`audio-player.tsx:84-88,407-408,779-802`). Máy chủ: tối đa 2 lần nghe mỗi tệp (`backend/.../attempt/domain/AttemptAudioPlay.java:22`, `AttemptStateService.java:224-227`), lease 45 s, dung sai tua 3 s (`application.yml:203-205`, `AttemptAudioProperties.java:8-19`); tải âm thanh chỉ được khi lần nghe đang `PLAYING`, đúng `playId`, lease còn hạn, phần `OPEN` và chưa quá hạn giờ (`AttemptAssetService.java:201-222`); mã lỗi `AUDIO_PLAY_IN_PROGRESS` (kèm `playId`), `AUDIO_PLAYS_EXHAUSTED`, `AUDIO_SEEK_REJECTED` (kèm `positionMs`), `AUDIO_PLAY_STALE`, `PART_CLOSED`, `TIME_UP`, `ATTEMPT_SESSION_REVOKED` (`AttemptAudioService.java:94-231,270-305`, `AttemptAudioController.java:41-95`, `EditSessionService.java:201-212`). Web không xử lý tab ẩn: âm thanh tiếp tục phát khi tab ở nền.
- **Chế độ chỉ đọc có hai mức trên web:** phần đóng hoặc phiên bị khóa thì cả tờ đề nằm trong `fieldset disabled` (`attempt-player.tsx:1123,2408-2413`); màn xem lại dùng `mode="review"` của từng thành phần: không sửa được, hiện câu trả lời, đáp án khi được phép, kết quả từng ý (`choice-question.tsx:102-120`, `short-answer.tsx:113-135`, `cloze.tsx:186-263`, `true-false.tsx:139-221`, `matching.tsx:298-376`, `essay.tsx:129-254`). Bảng kết quả dưới 640px chuyển thành hàng xếp chồng có nhãn cột (`packages/ui/src/question/review-table.tsx:4-92`).
- **Chữ giao diện của các thành phần câu hỏi không nằm trong `vi.json`** mà ở `packages/ui/src/messages/ui-strings.ts` nhóm `question` (tiếng Việt `:190-329`, tiếng Anh `:383-`); web học viên cấp qua `UiStringsProvider` (`apps/web-student/src/providers/preferences-provider.tsx:31`). Nhãn thanh công cụ Tự luận nằm ở `vi.json:309-318` (khóa `attempt.editor.*`), nhãn khối tệp đính kèm ở `vi.json:411` (`attempt.player.attachmentsLabel`).

### 4.6 Kết quả và xem lại bài

- Có ba lối vào màn Kết quả `/attempts/{id}/result`: nút "Xem kết quả" (`attempt.player.closed.viewResult`) trên màn lượt đã đóng của trình làm bài, hiện khi lượt ở `GRADING`, `PENDING_REVIEW`, `SCORED` hoặc `GRADING_ERROR`; sau khi nộp bài, web nạp lại trạng thái rồi hiện màn này, không tự chuyển trang (`apps/web-student/src/features/attempt-player/attempt-player.tsx:221,1639-1672,1955-1968`); nút "Xem kết quả" trên thẻ Bài thi của tôi theo `resultAttemptId` (`features/my-exams/my-exam-card.tsx:203-210`); một dòng Lịch sử có `action` khác `CONTINUE` (`features/attempt-history/history-view.tsx:109-111`).
- Màn Kết quả gọi `GET /api/v1/me/attempts/{attemptId}/result` không tự thử lại khi lỗi, cùng hai lời gọi `GET /api/v1/catalog/options?group=EXAM` và `?group=SUBJECT` để đổi mã kỳ thi, mã môn thành tên. Hai danh mục được giữ 5 phút (300 000 ms) và nếu lỗi thì màn hiện mã thô (`features/attempt-result/api.ts:28-51`, `result-view.tsx:485-513`, `format.ts:104-113`). Web không tự tải lại khi tab được chọn lại, chỉ tải lại khi mở lại màn hoặc khi có mạng lại (`providers/query-provider.tsx:70-74`).
- Khi `state = GRADING`, web hỏi lại mỗi 3 s. Sau 120 s tính từ lúc thấy `GRADING`, web ngừng hỏi và hiện khối hết giờ chờ có nút "Tải lại"; mỗi lần bấm "Tải lại" mở một khoảng chờ 120 s mới (`attempt-result/api.ts:16-17`, `result-view.tsx:499-550,848-900`).
- Backend: `ATTEMPT_NOT_FINISHED` (409) khi lượt ở `STARTING`, `IN_PROGRESS` hoặc `ABORTED`. Ở `GRADING` và `GRADING_ERROR`, phản hồi không có điểm, thống kê hay bảng chi tiết, `reviewAvailable = false` (`backend/.../attempt/application/AttemptResultService.java:152-230`). Lượt hoặc phần không thuộc người gọi trả 404 `NOT_FOUND`; tài khoản không phải học viên trả 403 `ATTEMPT_ACCESS_FORBIDDEN` (`AttemptAccessGate.java:25-35`, `AttemptGuard.java:72`).
- Điểm tổng chỉ hiện khi `SCORED`, hoặc khi `PENDING_REVIEW` có `regrading = true` (khi đó là điểm trước lúc chấm lại). Thang điểm lấy `scaleMax`, không có thì lấy `rawMaxScore`; điểm hiện `displayScore`, không có thì `rawScore` (`format.ts:50-70`, `AttemptResultService.java:239-250`). Số điểm viết tối đa 2 chữ số thập phân theo dấu của ngôn ngữ (`format.ts:28-44`). Giờ viết dạng `HH:mm, dd/MM/yyyy` theo giờ `Asia/Ho_Chi_Minh` (`format.ts:18-38`).
- Kỳ thi duy nhất được xử lý riêng là `VACT`: nhãn "Điểm luyện tập", ghi chú "Không phải điểm V-ACT chính thức", khối "Kết quả theo thành phần" có tiểu mục; không có bảng theo môn, cả ở máy chủ lẫn ở web (`result-view.tsx:765,940-971,1024-1063,1178`, `AttemptResultService.java:258-291`, `ScoreAggregator.java:433-436`). Các khối khác hiện theo dữ liệu, không theo kỳ thi: bảng phân đoạn chỉ có khi đề định nghĩa `displaySegments` (`AttemptSegmentResultBuilder.java:28-36`), bảng môn chỉ có khi câu có `componentCode` hoặc môn (`ScoreAggregator.java:451-465`).
- Dải nhãn lỗi hệ thống hiện khi `compensated = true`, `faultFlag = SYSTEM_FAULT` hoặc `endReason = FAULT_CLOSED`; web không dùng trường `faultMessage` của máy chủ (`result-view.tsx:787-846`).
- Nút xem lại chỉ hiện khi `reviewAvailable = true`, `resultView ≠ SCORE` và `state` là `SCORED` hoặc `PENDING_REVIEW` (`result-view.tsx:790-794,999-1020`). Máy chủ tính `reviewAvailable` theo cùng điều kiện, với `resultView` lấy từ quyền lợi của lượt, không có thì lấy giá trị ghi trên lượt (`AttemptResultService.java:179-180,443-445`, `AttemptReviewSupport.java:32-42`).
- Nút quay lại trên màn Kết quả luôn dẫn về `/history` (`result-view.tsx:557-566`); trên màn Xem lại luôn dẫn về màn Kết quả của lượt (`review-view.tsx:223,230-239`).
- Màn Xem lại gọi `GET /api/v1/me/attempts/{attemptId}/review` một lần, không truyền `part`, không tự thử lại. Gọi xong mới gọi `…/result` (giữ 60 s) để lấy tên đề, phạm vi, lần làm và điểm cho phần đầu màn (`attempt-review/api.ts:18-27`, `review-view.tsx:182-207`). Lỗi máy chủ: 403 `REVIEW_NOT_INCLUDED` khi `resultView` không cho xem lại; 409 `REVIEW_NOT_AVAILABLE` khi lượt không ở `SCORED` hay `PENDING_REVIEW`; 404 `NOT_FOUND` khi `part` không khớp (`AttemptResultService.java:335-352,471-482`, `AttemptResultController.java:59-71`).
- Ở mọi mức cho xem lại, máy chủ trả câu trả lời, kết quả, điểm, chi tiết chấm và đáp án đúng (`expectedAnswer`). Lời giải (`solution`) và nhận xét tự luận (`essayFeedback`) chỉ có khi `FULL_SOLUTION` và câu không ở `PENDING_REVIEW` (`AttemptResultService.java:484-545,679-759`).
- Khi lượt ở `PENDING_REVIEW`, máy chủ chỉ trả các câu đã chấm tự động (`AttemptResultService.java:375-379,447-469`). Web bỏ thêm mọi câu `PENDING_REVIEW` và mọi chùm câu hỏi mà mọi câu con đều chờ chấm (`attempt-review/mapper.ts:404-414,482-484`).
- Ảnh trong đề trỏ tới `/api/v1/me/attempts/{id}/assets/{assetId}`, ảnh trong lời giải trỏ tới `…/solution-assets/{assetId}`, đều là đường dẫn tương đối do máy chủ viết sẵn (`AttemptResultService.java:493,761-804`). Ảnh lời giải trả `Cache-Control: private, no-store` và chỉ được trả khi `resultView = FULL_SOLUTION` và lượt ở `SCORED` hoặc đang chấm lại; còn lại trả 404 (`AttemptSolutionAssetController.java:37-66`, `AttemptSolutionAssetService.java:75-102`). Vì vậy, khi lượt ở `PENDING_REVIEW` và không chấm lại, lời giải vẫn có chữ nhưng ảnh trong lời giải không tải được.
- Danh sách `attachments` của từng câu trong phản hồi xem lại không được web dùng (`attempt-review/mapper.ts:351-360`, `review-view.tsx:593-628`).
- Hợp đồng API mobile hiện chỉ có các đường dẫn xác thực, `/api/v1/me` và `/api/v1/me/profile`, chưa có đường dẫn nào của phần này (`contracts/mobile/openapi-mobile.json`, khóa `paths`).

### 4.7 Hồ sơ trong mục Tài khoản

**Web học viên, trang `/account/profile`**

- Trang chỉ gồm `ProfileView`; tải hồ sơ bằng `GET /api/v1/me/profile` mỗi lần mở trang (`staleTime: 0`). Đang tải thì hiện khung chờ có nhãn `common.loading` "Đang tải"; lỗi (mọi loại, kể cả mất mạng) thì hiện "Không tải được hồ sơ" (`account.profile.errorTitle`), "Đã có lỗi khi tải hồ sơ. Hãy thử lại." (`account.profile.errorDescription`) và nút "Thử lại" (`common.retry`). `apps/web-student/src/app/account/profile/page.tsx:11-16`, `apps/web-student/src/features/account/profile-view.tsx:86-90,108-132`, `apps/web-student/src/features/account/api.ts:103-110`
- Tải xong thì hiện tiêu đề "Hồ sơ" (`account.profile.title`), câu dẫn `account.profile.intro`, rồi bốn khối: Ảnh đại diện, Thông tin cá nhân, Email, Mật khẩu. Khối Mật khẩu chỉ có câu "Đổi mật khẩu ở trang riêng." và nút "Đổi mật khẩu" dẫn tới `/change-password`. `profile-view.tsx:151-188`
- Mỗi lần một thao tác thành công, trang ghi hồ sơ mới vào bộ đệm và làm mới phiên để menu tài khoản ở đầu trang đổi theo họ tên và ảnh. `profile-view.tsx:98-102`
- Băng cảnh báo chỉ hiện khi tài khoản có email và `emailVerified = false`: "**Email chưa xác minh.** Xác minh để dùng email này khôi phục tài khoản và nhận thư về đơn hàng." (`account.profile.unverifiedTitle`, `account.profile.unverifiedBody`) kèm nút "Xác minh email" (`account.profile.verifyEmail`) mở `/verify-email?callbackUrl=/account/profile`. `profile-view.tsx:156-169`
- Khối Ảnh đại diện: có ảnh thì hiện ảnh từ `avatarUrl` (nhãn "Ảnh đại diện hiện tại", `account.profile.avatar.current`), chưa có thì hiện chữ cái đầu tên (nhãn `account.profile.avatar.currentEmpty`); dưới ảnh là họ tên và tên đăng nhập; nút "Tải ảnh lên" nhận `image/jpeg,image/png,image/webp`; nút "Xóa ảnh" chỉ hiện khi đang có ảnh; dòng gợi ý `account.profile.avatar.hint`. Web không xử lý trường hợp ảnh tải lỗi (thẻ `<img>` thường). `profile-view.tsx:42-43,246-302`
- Tải ảnh: tệp quá 5 MB bị chặn ngay ở trình duyệt, hiện `account.profile.avatar.errors.TOO_LARGE_FILE` có tên tệp và dung lượng, không gọi API. Còn lại gửi `PUT /api/v1/me/avatar` dạng multipart, phần tên `file`; thành công thì thay ảnh và hiện thông báo nổi "Đã cập nhật ảnh đại diện." (`account.profile.avatar.uploaded`). Lỗi: 413 hoặc `AVATAR_TOO_LARGE` → `errors.TOO_LARGE`; `AVATAR_DIMENSIONS_EXCEEDED` → `errors.DIMENSIONS_EXCEEDED`; `AVATAR_INVALID_IMAGE` → `errors.INVALID_IMAGE`; lỗi khác hoặc mất mạng → `errors.generic`. Trong lúc gửi, cả hai nút bị khóa. `profile-view.tsx:63-70,206-229,279-319`, `api.ts:168-182`
- Xóa ảnh: không có hộp xác nhận; gọi `DELETE /api/v1/me/avatar`, thành công thì về chữ cái đầu tên và hiện "Đã xóa ảnh đại diện." (`account.profile.avatar.removed`); lỗi bất kỳ → `errors.generic`. `profile-view.tsx:231-244`, `api.ts:184-190`
- Khối Thông tin cá nhân: "Tên đăng nhập" chỉ đọc kèm gợi ý "Tên đăng nhập không đổi được."; "Họ và tên" bắt buộc, cắt khoảng trắng hai đầu, tối đa 200 ký tự; "Số điện thoại (không bắt buộc)" nhận chuỗi rỗng, đúng mẫu `^0\d{9}$`, hoặc đúng số đang lưu (số cũ do quản trị nhập theo quy tắc rộng hơn được giữ khi không sửa). Chưa có số thì hiện gợi ý "Bạn chưa nhập số điện thoại." Nút "Hủy thay đổi" và "Lưu thay đổi" chỉ bấm được khi có ô bị sửa. Khi lưu, web gửi `PATCH /api/v1/me/profile` với `fullName` và chỉ kèm `phone` khi số khác số đang lưu; thành công hiện "Đã lưu thông tin cá nhân." Lỗi trường từ máy chủ: `phone` → "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0."; `fullName` mã `SIZE` → "Họ và tên tối đa 200 ký tự.", mã khác → "Vui lòng nhập họ và tên."; lỗi còn lại → "Không lưu được thay đổi. Vui lòng thử lại." (`account.profile.info.genericError`). `profile-view.tsx:45,47,324-486`, `api.ts:112-119`
- Khối Email: một dòng hiện email kèm nhãn "Đã xác minh" hoặc "Chưa xác minh", hoặc "Tài khoản chưa có email." khi không có email, và nút "Đổi email" bật/tắt biểu mẫu đổi email. `profile-view.tsx:619-643`
- Biểu mẫu đổi email: "Email mới" (bắt buộc, kiểm dạng bằng `^[^\s@]+@[^\s@]+\.[^\s@]+$`), "Mật khẩu hiện tại" (bắt buộc, có nút hiện/ẩn), nút "Hủy" và "Gửi mã xác minh". Gửi `POST /api/v1/me/email/change {newEmail, currentPassword}`; 202 thì đóng biểu mẫu, ghi `pendingEmail` vào hồ sơ đang hiện (không tải lại) và bắt đầu chờ 60 giây. Lỗi: 429 → cảnh báo `changeErrors.RATE_LIMITED`, khóa nút trong `retryAfter` giây (thiếu thì 60); 409 `EMAIL_TAKEN` → "Email này đã thuộc tài khoản khác." dưới ô Email mới; lỗi trường `currentPassword` → "Mật khẩu hiện tại không đúng."; lỗi trường `newEmail` → "Email chưa đúng định dạng."; còn lại → `changeErrors.generic`. `profile-view.tsx:46,488-584,645-708`, `api.ts:146-155`
- Khối chờ email mới hiện khi hồ sơ có `pendingEmail`: tiêu đề "Đang chờ xác minh email mới: {email}", đoạn `account.profile.email.pendingBody`, ô "Mã xác minh" (6 chữ số), nút "Xác nhận" và "Gửi lại mã". "Gửi lại mã" mở lại biểu mẫu đổi email, điền sẵn email đang chờ, ô mật khẩu trống. Xác nhận gửi `POST /api/v1/me/email/change/confirm {code}`; 204 thì email đổi, nhãn "Đã xác minh", khối chờ biến mất, hiện "Đã đổi email." Lỗi: 422 `EMAIL_CODE_INVALID` → "Mã chưa đúng. Bạn còn {count} lần thử." với `remainingAttempts`; `EMAIL_CODE_EXPIRED` → `confirmErrors.EXPIRED`; 409 `EMAIL_TAKEN` → `confirmErrors.EMAIL_TAKEN`; 503 → `confirmErrors.SYNC_FAILED`; còn lại → `confirmErrors.generic`. `profile-view.tsx:586-609,710-778`, `api.ts:157-166`
- Thời gian chờ gửi mã chạy theo đồng hồ thật (`Date.now`), là trạng thái của trang, mất khi rời trang. Chữ đếm ngược `mm:ss` nằm ngoài nút bị khóa và chỉ hiện khi còn dưới 3.600 giây; vùng thông báo cho trình đọc màn hình chỉ đọc một câu lúc bắt đầu ("Có thể gửi lại mã sau {time}.") và một câu lúc hết ("Bạn có thể gửi lại mã bây giờ."). `apps/web-student/src/features/account/cooldown.ts:4,7-11,22-43,49-57`, `profile-view.tsx:703-706,773-785`
- Không có cảnh báo khi rời trang lúc biểu mẫu đang sửa dở. `profile-view.tsx:324-486` (không có `beforeunload`)

**Web học viên, trang `/verify-email` (đích của nút trong băng cảnh báo)**

- Trang tự tải hồ sơ; đang tải thì hiện khung chờ, lỗi thì hiện "Không tải được trang" (`account.verifyEmail.errorLoadTitle`), "Không tải được thông tin tài khoản. Hãy thử lại." và "Thử lại". `apps/web-student/src/features/account/verify-email-view.tsx:68-73,174-210`
- Email đã xác minh thì chỉ hiện "Email của bạn đã được xác minh." và nút "Tiếp tục" về `callbackUrl`. `verify-email-view.tsx:215-236`
- Chưa xác minh thì hiện tiêu đề "Xác minh email", câu "Chúng tôi đã gửi mã 6 số tới **{email}**. Mã có hiệu lực trong 10 phút." (`account.verifyEmail.sentTo`), ô mã, nút "Xác minh", dòng "Chưa nhận được mã?" với nút "Gửi lại mã", nút "Để sau" và chú thích `account.verifyEmail.footnote`. Mở trang **không** gửi mã: chỉ nút "Gửi lại mã" gọi `POST /api/v1/me/email/verification`. `verify-email-view.tsx:132-154,238-346`
- Xác minh gửi `POST /api/v1/me/email/verify {code}`; 204 thì chuyển ngay về `callbackUrl`. Lỗi: mã không đủ 6 chữ số → "Vui lòng nhập mã gồm 6 chữ số." (không gọi API); `EMAIL_CODE_INVALID` → "Mã chưa đúng. Bạn còn {count} lần thử."; `EMAIL_CODE_EXPIRED` → `alerts.codeExpired`; còn lại → `alerts.generic`. `verify-email-view.tsx:85-130`, `api.ts:135-144`
- Gửi lại thành công thì hiện "Đã gửi mã mới. Mã cũ không còn dùng được." và chờ 60 giây; 429 với `retryAfter` ≥ 3.600 giây thì hiện "Bạn đã gửi lại mã quá số lần cho phép. Thử lại sau {time}." (ngày/tháng giờ:phút) thay cho đếm ngược; lỗi khác → `alerts.resendFailed`. `verify-email-view.tsx:47,132-154,264-281,318-332`

**Backend**

- `GET`, `PATCH /api/v1/me/profile` không có tham số id; tài khoản realm `master` nhận 404. `backend/src/main/java/vn/unicom/thi360/identity/api/MeProfileController.java:31-41`, `backend/src/main/java/vn/unicom/thi360/identity/api/ProfileOwner.java:18-24`
- `ProfileResponse` gồm `username`, `fullName`, `email` (có thể null), `emailVerified`, `phone` (có thể null), `avatarAssetId`, `avatarUrl` (đường dẫn tương đối `/api/v1/avatars/{assetId}`, null khi chưa có ảnh), `pendingEmail` (có thể null). `backend/src/main/java/vn/unicom/thi360/identity/api/ProfileResponse.java:7-33`, `backend/src/main/java/vn/unicom/thi360/identity/application/ProfileService.java:46,167-178`
- `PATCH` : trường không gửi thì giữ nguyên; `fullName` cắt khoảng trắng, rỗng → 400 trường `fullName` mã `NOTBLANK`, quá 200 điểm mã → mã `SIZE`; `phone` rỗng thì xóa số, khác rỗng phải khớp `0\d{9}` không thì 400 trường `phone` mã `PHONE_INVALID`. Không có khóa phiên bản: lần lưu sau ghi đè lần trước. Mô tả trong schema `ProfileUpdateRequest` ghi "tối đa 100 ký tự" nhưng mã kiểm 200. `ProfileService.java:44-45,85-147`, `backend/src/main/java/vn/unicom/thi360/identity/api/ProfileUpdateRequest.java:11`, `backend/src/main/java/vn/unicom/thi360/shared/api/GlobalExceptionHandler.java:53-60`
- `PUT /api/v1/me/avatar` (multipart, phần `file`): thiếu hoặc rỗng → 422 `AVATAR_INVALID_IMAGE`; quá 5 MB → 413 `AVATAR_TOO_LARGE`; chữ ký byte không phải JPEG, PNG, WebP (không tin đuôi tệp hay `Content-Type`, nên HEIC bị từ chối) → 422 `AVATAR_INVALID_IMAGE`; cạnh quá 4.096 px → 422 `AVATAR_DIMENSIONS_EXCEEDED`; không giải mã được → 422 `AVATAR_INVALID_IMAGE`. Ảnh nhận được cắt vuông ở giữa và lưu PNG 256 × 256; mỗi lần thay tạo `assetId` mới và xóa ảnh cũ. Thành công trả `ProfileResponse`. Thân quá 21 MB bị chặn trước đó với 413 `UPLOAD_TOO_LARGE`. `backend/src/main/java/vn/unicom/thi360/identity/api/AvatarController.java:52-71`, `backend/src/main/java/vn/unicom/thi360/identity/application/AvatarImageProcessor.java:27-30,42-61,86-114`, `backend/src/main/java/vn/unicom/thi360/identity/api/AvatarExceptionHandler.java:21-24,36-47`, `backend/src/main/java/vn/unicom/thi360/identity/application/AvatarService.java:81-100`, `backend/src/main/resources/application.yml:6-7`, `GlobalExceptionHandler.java:163-170`
- `DELETE /api/v1/me/avatar` trả 204, không có ảnh thì không làm gì (vẫn 204). `AvatarController.java:73-78`, `AvatarService.java:105-122`
- `GET /api/v1/avatars/{assetId}` cần đăng nhập (mọi tài khoản đã đăng nhập xem được), trả `image/png`, `Cache-Control: private, max-age=300`; `assetId` không còn (ảnh đã bị thay hoặc xóa) → 404. `AvatarController.java:80-92`, `AvatarService.java:128-133`
- `POST /api/v1/me/email/verification` → 202; tài khoản không có email → 422 `EMAIL_REQUIRED`. `POST /api/v1/me/email/verify` → 204. `POST /api/v1/me/email/change` → 202. `POST /api/v1/me/email/change/confirm` → 204. `backend/src/main/java/vn/unicom/thi360/identity/api/MeEmailController.java:35-80`, `backend/src/main/java/vn/unicom/thi360/identity/application/EmailCodeVerificationService.java:58-108`
- Mã OTP: 6 chữ số, sống 10 phút, sai tối đa 5 lần; cấp mã mới làm mã cũ cùng mục đích hết hiệu lực. Gửi mã: cách nhau ít nhất 60 giây và tối đa 5 mã mỗi 24 giờ cho mỗi mục đích (xác minh, đổi email); vượt thì 429 `RATE_LIMITED` có header `Retry-After` và trường `retryAfter` (giây, ≥ 1). Mã sai → 422 `EMAIL_CODE_INVALID` có `remainingAttempts`; hết hạn, đã dùng, đã bị thay, sai quá 5 lần hoặc không còn email chờ → 422 `EMAIL_CODE_EXPIRED`. `backend/src/main/java/vn/unicom/thi360/identity/domain/EmailCode.java:22-23,67-78`, `backend/src/main/java/vn/unicom/thi360/identity/domain/EmailCodeSendPolicy.java:14-16,25-37`, `backend/src/main/java/vn/unicom/thi360/identity/application/EmailCodeService.java:69-113`, `backend/src/main/java/vn/unicom/thi360/identity/api/EmailCodeExceptionHandler.java:46-62`, `backend/src/main/java/vn/unicom/thi360/identity/application/EmailCodeResults.java:11-18`
- Đổi email: email mới sai dạng hoặc quá 255 ký tự → 400 trường `newEmail`; email mới trùng email hiện tại hoặc thuộc tài khoản khác → 409 `EMAIL_TAKEN`; mật khẩu sai → 400 `WRONG_OLD_PASSWORD` trường `currentPassword`; Keycloak đang khóa tài khoản vì sai mật khẩu → 429 `LOGIN_THROTTLED` không có `retryAfter`. Email hiện tại giữ nguyên tới khi xác nhận; `pendingEmail` chỉ bị xóa khi xác nhận thành công hoặc bị thay bằng yêu cầu mới, không tự xóa khi mã hết hạn. Xác nhận: email bị tài khoản khác lấy trong lúc chờ → 409 `EMAIL_TAKEN`; Keycloak lỗi → 503 và không có gì thay đổi; thành công thì gửi thư báo tới email cũ. `backend/src/main/java/vn/unicom/thi360/identity/application/EmailChangeService.java:89-120,129-145,152-226`, `backend/src/main/java/vn/unicom/thi360/identity/domain/UserAccount.java:150-154,163-173`, `EmailCodeExceptionHandler.java:64-85`, `GlobalExceptionHandler.java:188-195`
- Tài khoản bị khóa hoặc xóa giữa chừng → 401 ở mọi API `/me/email/**`. `backend/src/main/java/vn/unicom/thi360/identity/application/EmailCodeAccountLoader.java:38-63`

**Hợp đồng API mobile**

- `packages/api-client/openapi.json` có đủ các thao tác: `/api/v1/avatars/{assetId}` get (dòng 49179), `/api/v1/me/avatar` put, delete (53157), `/api/v1/me/email/change` (53441), `/api/v1/me/email/change/confirm` (53583), `/api/v1/me/email/verification` (53735), `/api/v1/me/email/verify` (53867), `/api/v1/me/profile` get, patch (55427). Không thao tác nào có tham số cookie.
- `contracts/mobile/openapi-mobile.json` chỉ có `GET /api/v1/me/profile` trong số đó (dòng 1884); test xuất hợp đồng còn khẳng định `PATCH /me/profile` **không** có trong bản mobile. Tệp mẫu hiện có: `contracts/mobile/api/me-profile-200.json`, `me-profile-200-no-email.json`. `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java:66,196-210`
- Android sinh client từ tệp này lúc build; iOS dùng liên kết `apps/ios/Thi360/Core/Network/OpenAPI/openapi.json` trỏ về cùng tệp; CI so tệp trong repo với bản xuất. `apps/android/app/build.gradle.kts:13`, `.github/workflows/backend.yml:29`

**App hôm nay, mục Tài khoản**

- Android: thẻ hồ sơ chỉ đọc (vòng tròn chữ cái đầu tên, họ tên, tên đăng nhập, email nếu có, nhãn "Email đã xác minh"/"Chưa xác minh"); đang tải, lỗi kèm "Thử lại", mất mạng kèm "Thử lại". Dưới thẻ: nhóm "Bảo mật" (dòng "Đổi mật khẩu", công tắc sinh trắc học), nhóm hiển thị (Ngôn ngữ, Giao diện Sáng/Tối), nút "Đăng xuất" có hộp xác nhận. `apps/android/app/src/main/java/vn/unicom/thi360/feature/account/AccountScreen.kt:440-583,586-804`
- Android: hai cột (≥ 840dp) có ngăn chi tiết cho `CHANGE_PASSWORD`, `LANGUAGE`, `THEME`, mặc định `CHANGE_PASSWORD`; một cột thì "Đổi mật khẩu" mở màn riêng, Ngôn ngữ mở hộp chọn, Giao diện là nút chọn ngay trong dòng. `AccountScreen.kt:173-182,235-279,341-407`, `apps/android/app/src/main/java/vn/unicom/thi360/feature/account/AccountTestTags.kt:3-7`, `apps/android/app/src/main/java/vn/unicom/thi360/shell/AppNavHost.kt:323-325`
- Android: hồ sơ trong app chỉ giữ `username`, `fullName`, `email`, `emailVerified`; tải một lần cho mỗi phiên, tải lại khi đổi phiên. Màn khóa lấy họ tên từ hồ sơ đã tải. `apps/android/app/src/main/java/vn/unicom/thi360/feature/account/AccountProfileGateway.kt:7-46`, `AppNavHost.kt:320-339,586-603`
- iOS: cùng các thành phần (thẻ hồ sơ chỉ đọc, "Đổi mật khẩu", sinh trắc học, Ngôn ngữ và Giao diện chọn ngay trong dòng, "Đăng xuất" có hộp xác nhận); hồ sơ cũng chỉ giữ bốn trường trên và tải một lần mỗi phiên. Bề rộng `regular`: nửa trái là danh sách, nửa phải luôn là màn đổi mật khẩu (chỉ có một lựa chọn chi tiết `changePassword`). `apps/ios/Thi360/Features/Account/AccountView.swift:21-55,80-190,231-289,532-563`, `apps/ios/Thi360/Features/Account/AccountModels.swift:3-26,81-83`, `apps/ios/Thi360/Features/Account/AccountViewModel.swift:16,86-111`, `apps/ios/Thi360/Shell/AdaptiveShellView.swift:163-188,221-243`
- Cả hai app chưa có lối vào Hồ sơ hay Đơn hàng, chưa hiện ảnh đại diện, chưa xin quyền máy ảnh (Android chỉ khai `INTERNET`). `apps/android/app/src/main/AndroidManifest.xml:4`

## 5. Thuật ngữ

| Thuật ngữ | Nghĩa ở đây |
| --- | --- |
| App | Ứng dụng học viên trên Android hoặc iOS; yêu cầu áp cho cả hai trừ khi ghi rõ một nền tảng |
| Web | Web học viên `apps/web-student` |
| Học viên | Tài khoản realm `thi-360` có `access = EXAM` |
| Bề rộng hẹp | Android: bề rộng cửa sổ dưới 600dp. iOS: horizontal size class là compact |
| Bề rộng rộng | Android: từ 600dp. iOS: horizontal size class là regular |
| Hai cột | Bố cục hai vùng cạnh nhau. Android: bề rộng từ 840dp. iOS: bề rộng regular |
| Lượt thi | Một lần làm bài của học viên (`attempt`) |
| Quyền thi | Quyền làm một bài thi mà học viên có được do mua hoặc do lớp giao (`entitlement`) |
| Tờ đề | Vùng hiển thị đề bài, đoạn văn, phương án và ô trả lời của các câu trong một phần |
| Bộ vẽ công thức | Thư viện dựng công thức LaTeX trên app: SwaTex (iOS), RaTeX (Android) |
| Danh mục | Danh sách sản phẩm đang bán ở mục Trang chủ, từ `GET /api/v1/public/storefront/products`. |
| Sản phẩm | Một bài thi lẻ hoặc một bộ đề đang bán; nhận diện bằng `code`. Bộ đề là sản phẩm có `productType = BUNDLE` hoặc `format = BUNDLE` (`format.ts:8-10`). |
| Bộ lọc | Năm điều kiện: tìm, kỳ thi, hình thức, phần/môn, sắp xếp. "Có bộ lọc" khi ít nhất một điều kiện khác mặc định, kể cả sắp xếp khác "Mới công bố". |
| Thẻ | Ô hiện một sản phẩm trong danh mục. |
| Trạng thái quyền | Giá trị `state` của dòng `exam-access` với mã sản phẩm: `IN_PROGRESS`, `CAN_START`, `CHECKING`, `EXHAUSTED`, `NONE`. |
| Đơn đang kiểm tra | Đơn đã thu tiền nhưng chưa cấp quyền xong: `hasPendingFulfillment = true`, mã đơn ở `pendingOrderCode`. |
| Khung nút | Phần cuối thẻ và khung giá của màn chi tiết, chứa nút theo trạng thái quyền. |
| Khung Giá bán / Khung Giá bộ | Khung giá và nút của màn chi tiết bài thi / bộ đề. |
| Màn Xác nhận mua | Màn tương ứng `/checkout/[code]` của web. |
| Màn Kết quả thanh toán | Màn tương ứng `/checkout/result` của web. |
| Báo giá | Phản hồi của `POST /api/v1/me/price-quote`. |
| Giá đã thấy | Phiên bản (`v`) và giá cuối (`p`) mà màn chi tiết bài thi đã hiện, được chuyển sang màn Xác nhận mua (xem mục 6.1). |
| Trang thanh toán | Trang VNPAY (hoặc `/mock-vnpay` ở chế độ giả lập) mà app mở trong Phiên trình duyệt thanh toán, theo D-3. |
| Phiên trình duyệt thanh toán | Phiên trình duyệt do hệ điều hành cung cấp (không phải WebView tự vẽ trong app) mà app dùng để mở Trang thanh toán; khi nền tảng hỗ trợ, phiên này trao Liên kết trả về cho app và tự đóng. |
| Liên kết trả về | URL `https://{host web học viên}/checkout/result/app?vnp_…` mà VNPAY chuyển tới sau khi học viên xong trên Trang thanh toán của một lần thanh toán bắt đầu từ app (xem mục "Thay đổi backend hoặc web cần có"). |
| Trang dự phòng | Trang web `/checkout/result/app`, hiện khi Liên kết trả về không tới được app mà mở ra trong trình duyệt. |
| Đơn đang chờ kết quả | Đơn mà app vừa mở Trang thanh toán cho nó và chưa hiện kết quả cuối (đã cấp, thất bại, hoặc đã đóng). |
| Khóa chống gửi trùng | Giá trị header `Idempotency-Key` gửi kèm lệnh tạo đơn, thanh toán tiếp, hủy đơn. |
| Hỏi lại | Gọi lại `GET /api/v1/me/orders/{code}` để đọc trạng thái mới. |
| Thẻ đang kiểm tra | Thẻ có `state = CHECKING`: đơn đã thu tiền nhưng chưa cấp quyền. |
| Thẻ bài thi | Mọi thẻ không phải thẻ đang kiểm tra. |
| Thẻ theo lớp | Thẻ bài thi có `entitlementSource = CLASS`. |
| Nhóm bộ đề | Khối của một bộ đề (một `bundleId`) xuất hiện trong `bundles` của ít nhất một thẻ bài thi: gồm các thẻ có bộ đó là `bundles[0]` và các dòng dẫn chiếu của thẻ có bộ đó ở vị trí sau. Bộ đề của nhóm là phần tử `MyExamBundleRef` mang `bundleId` đó gặp đầu tiên trong danh sách; tên và mã của nhóm là `name`, `code` của phần tử này (`group-my-exams.ts:33-39`). |
| Dòng dẫn chiếu | Dòng trong một nhóm bộ đề nói bài đó đang hiện ở nhóm khác (bài thuộc nhiều bộ). |
| Ngừng bán | `saleState` khác `ON_SALE` (`DISCONTINUED` hoặc `UNAVAILABLE`). |
| Dòng lịch sử | Một phần tử `AttemptHistoryItem` của `GET /api/v1/me/attempts`. |
| Có điểm | Dòng lịch sử có `displayScore` hoặc `rawScore` và `state = SCORED`, hoặc `state = PENDING_REVIEW` với `regrading = true` (`format.ts:50-58`). |
| Dữ liệu cũ | Danh sách đã tải thành công trước đó, vẫn hiện khi lần tải lại thất bại. |
| Giờ hiển thị | Giờ theo múi `Asia/Ho_Chi_Minh`, không theo múi giờ của máy. |
| Lượt đã đóng | Lượt ở `GRADING`, `PENDING_REVIEW`, `SCORED` hoặc `GRADING_ERROR` |
| Màn chuẩn bị | Màn của một quyền dùng trước khi bắt đầu (web `/prepare/{entitlementId}`) |
| Màn chờ | Màn "Đang chuẩn bị đề" khi lượt `STARTING`, gồm biến thể bận và thất bại |
| Màn làm bài | Màn của một lượt (web `/attempts/{id}`), gồm thanh trên, nội dung phần đang làm và bảng câu hỏi |
| Phiên sửa | Quyền ghi duy nhất của một lượt, có `sessionId`; chỉ máy đang giữ phiên sửa được gửi câu trả lời |
| Tiếp quản | Gọi nhận phiên với `takeover = true` để lấy phiên sửa từ máy khác |
| Bị đẩy ra | Máy mất phiên sửa vì máy khác đã tiếp quản |
| Chỉ đọc | Màn làm bài hiện nội dung nhưng không cho sửa câu trả lời, cờ, lựa chọn |
| Phần chưa mở xong | Phần ở `AWAITING_CHOICE`, `GENERATING` hoặc `GENERATION_FAILED` (như `isChoiceOrGeneratingState` của web, `attempt-player.tsx:223-229`) |
| Câu lá | Câu hỏi có đáp án riêng; câu con của chùm câu là câu lá, chùm câu không phải |
| Thay đổi chưa lưu | Câu có `seq` trên máy lớn hơn `seq` máy chủ đã xác nhận, hoặc cờ khác cờ máy chủ, ở phần đang mở |
| Bản nháp | Bản sao câu trả lời và cờ của câu chưa lưu, ghi trên máy, còn sau khi app bị tắt |
| Đồng hồ máy chủ | Thời gian còn lại tính từ `serverNow` gần nhất cộng thời gian trôi theo đồng hồ đơn điệu của máy |
| Màn rộng | Android từ 840dp; iOS bề rộng regular (như "Hai cột" của đặc tả khung app) |
| Màn hẹp | Mọi trường hợp còn lại |
| Nội dung đề | Một chuỗi HTML máy chủ trả: đề bài, phương án, ý, mục ghép, ngữ liệu, lời giải |
| Danh sách cho phép | Tập thẻ, thuộc tính, giá trị và luật cấu trúc ở `html-contract.ts:11-266` |
| Khung cảnh báo công thức | Hộp nhỏ nằm trong dòng, viền và nền màu cảnh báo, chữ đơn cách, chứa mã LaTeX nguồn kèm cặp dấu `\( \)` hoặc `\[ \]` |
| Bộ mẫu đối chiếu | Tập cố định các mẫu nội dung đề và câu hỏi dùng để so app với web (R-117) |
| Chế độ làm bài | Thành phần nhận nhập và báo giá trị mới |
| Chế độ khóa | Chế độ làm bài nhưng không nhận nhập (phần đã đóng, phiên bị khóa); giá trị đang có vẫn hiện |
| Chế độ xem lại | Thành phần chỉ đọc của màn xem lại: hiện câu trả lời đã nộp, đáp án khi được phép, kết quả |
| Lần nghe | Một lượt phát tệp audio từ đầu tới cuối do máy chủ đếm (`playId`) |
| Lease | Thời hạn máy chủ cho phép tải âm thanh của lần nghe đang phát; mỗi lần phát tiếp hoặc báo tiến độ gia hạn thêm 45 s |
| Tờ đề, Bộ vẽ công thức, Hai cột, Bề rộng hẹp | Như mục 5 của đặc tả |
| Lượt | Một lần làm bài, định danh bằng `attemptId`. |
| Màn Kết quả | Màn tương ứng web `/attempts/{id}/result`. |
| Màn Xem lại | Màn tương ứng web `/attempts/{id}/review`. |
| Mức xem | Giá trị `resultView` của lượt: `SCORE` (chỉ điểm), `SCORE_AND_ANSWERS` (điểm và bài làm kèm đáp án), `FULL_SOLUTION` (thêm lời giải và nhận xét tự luận). |
| Đang chấm lại | Lượt có `regrading = true`. |
| Chờ chấm | `state = PENDING_REVIEW` và không đang chấm lại: còn câu tự luận chưa chấm tay. |
| Bảng câu | Lưới các ô số câu của phần đang xem trên màn Xem lại (web "Danh sách câu"). |
| Nhóm câu | Một mục trên màn Xem lại: một câu đơn, một câu điền khuyết nhiều ô, hoặc một chùm câu hỏi (`QUESTION_SET`) cùng các câu con. |
| Mục Tài khoản | Đích thứ tư của khung app, đã có từ đợt trước. |
| Thẻ tài khoản | Thẻ trên cùng của mục Tài khoản: ảnh đại diện, họ tên, tên đăng nhập, email và nhãn xác minh. |
| Màn Hồ sơ | Màn mới của app tương ứng trang web `/account/profile`. |
| Màn Xác minh email | Màn mới của app tương ứng trang web `/verify-email`, dùng để xác minh email hiện tại. |
| Ngăn chi tiết | Cột bên phải của mục Tài khoản khi app hiện hai cột (Android ≥ 840dp, iOS bề rộng `regular`). |
| Email chờ | Giá trị `pendingEmail` của hồ sơ: email mới đã nhận mã nhưng chưa xác nhận. |
| Thời gian chờ gửi mã | Khoảng app khóa nút gửi mã sau một lần gửi, 60 giây hoặc `retryAfter` giây của 429. |
| Thông báo nổi | Thông báo tự ẩn ở cạnh màn (Snackbar trên Android, thông báo nổi kiểu iOS), tương ứng `notify.success` của web. |
| Ảnh có xác thực | Ảnh lấy từ `GET /api/v1/avatars/{assetId}` kèm `Authorization: Bearer` của phiên. |

## 6. Yêu cầu

Các yêu cầu chia theo bảy phần của cổng thi, sau nhóm yêu cầu chung. Câu chữ hiển thị trong app lấy theo khóa tương ứng trong `apps/web-student/src/messages/vi.json` và `en.json`; mỗi tiêu chí nêu khóa khi có.

### 6.0 Yêu cầu chung

### R-1 Bốn mục có nội dung thật và điều hướng

Bốn mục của khung app hiện nội dung thật: Trang chủ là danh mục bài thi (mục 6.1), Bài thi của tôi (mục 6.3), Lịch sử (mục 6.3), Tài khoản có thêm Hồ sơ (mục 6.7) và Đơn hàng (mục 6.2). Các màn con mở chồng lên mục đang đứng và quay lại đúng mục đó.

- Nguồn: D-1; giả định A-1
- Áp dụng cho: học viên đã đăng nhập
- Tiêu chí chấp nhận:
  - **AC-1** Khi học viên đã đăng nhập mở app, mục Trang chủ hiện danh mục bài thi; không mục nào còn dòng "Chưa có nội dung" của màn giữ chỗ.
  - **AC-2** Khi học viên đang ở một màn con (ví dụ chi tiết bài thi mở từ Trang chủ), chuyển sang mục khác rồi quay lại mục cũ thì thấy lại đúng màn con đó với vị trí cuộn và bộ lọc như lúc rời đi, trong cùng một lần chạy app.
  - **AC-3** Khi học viên vào màn làm bài, thanh điều hướng bốn mục (thanh dưới, thanh bên hoặc sidebar) không hiển thị; khi rời màn làm bài thì thanh hiển thị lại.
  - **AC-4** Khi bề rộng là hẹp, mọi màn của đợt này hiện một cột; khi bề rộng là rộng, khung dùng thanh bên (Android) hoặc sidebar (iOS) như đợt trước và các màn có cặp danh sách–chi tiết hoặc đoạn văn–câu hỏi hiện hai cột theo quy tắc ở mục 5.
  - **AC-5** Khi app chạy trên điện thoại (Android bề rộng nhỏ nhất dưới 600dp, iPhone và màn ngoài iPhone Duo), mọi màn của đợt này, kể cả màn làm bài, chỉ hiển thị chiều dọc; trên tablet, iPad, máy gập mở và màn trong iPhone Duo thì xoay được hai chiều.
  - **AC-6** Khi học viên xoay máy, gập hoặc mở máy, hoặc đổi kích thước cửa sổ ở một màn đang có dữ liệu nhập dở (mã voucher, họ tên, bộ lọc, câu trả lời) thì app giữ nguyên màn, dữ liệu đã nhập và vị trí cuộn.

### R-2 Mức hệ điều hành tối thiểu

App iOS chạy từ iOS 18.0; app Android giữ mức Android 8.0 (API 26).

- Nguồn: D-5
- Áp dụng cho: bản build của hai app
- Tiêu chí chấp nhận:
  - **AC-7** Khi build app iOS ở cả hai cấu hình `dev` và `demo`, mức triển khai tối thiểu của app và của các target test là `18.0`.
  - **AC-8** Khi chạy bộ test đơn vị và test giao diện iOS hiện có của đợt trước sau khi nâng mức, toàn bộ vẫn đạt.
  - **AC-9** Khi build app Android, `minSdk` vẫn là 26.
  - **AC-10** Khi đọc `ARCHITECTURE.md`, `DESIGN.md` mục 11, `README.md` và `apps/ios/README.md`, mọi chỗ nêu mức iOS tối thiểu đều ghi iOS 18.

### R-3 Hợp đồng API mobile

Mọi endpoint app gọi trong đợt này có trong hợp đồng API mobile, và hai app gọi qua client sinh từ hợp đồng đó.

- Nguồn: hiện trạng 4.0; quy tắc "client sinh tự động, không sửa tay" của `RULES.md`
- Áp dụng cho: backend, hai app
- Tiêu chí chấp nhận:
  - **AC-11** Khi xuất hợp đồng bằng `MobileOpenApiExportIT`, tệp `contracts/mobile/openapi-mobile.json` chứa mọi endpoint được nêu trong các yêu cầu của mục 6 và vẫn chứa đủ 11 đường dẫn đang có.
  - **AC-12** *(Từ chối)* Khi backend đổi hình dạng một endpoint trong hợp đồng mà không cập nhật tệp hợp đồng, bước so hợp đồng của CI backend báo đỏ.
  - **AC-13** Khi không có thay đổi backend nào khác ngoài các thay đổi nêu ở mục 7, `packages/api-client/openapi.json` và `packages/api-client/src/schema.d.ts` chỉ khác đúng ở các thay đổi đó; `pnpm typecheck` của hai web vẫn đạt.
  - **AC-14** Khi rà mã hai app, không lời gọi API nghiệp vụ nào được viết tay ngoài client sinh từ hợp đồng, trừ việc tải tệp nhị phân (ảnh, âm thanh).

### R-4 Lời gọi API có xác thực và thao tác gửi lặp

Mọi lời gọi tới endpoint cần đăng nhập đi kèm Bearer và dùng cơ chế tự làm mới phiên của đợt trước. Thao tác có khóa idempotency gửi lại cùng khóa khi thử lại.

- Nguồn: đặc tả đợt trước R-6; `RULES.md` mục 4 (Idempotency); `apps/web-student/src/features/orders/format.ts:44-53`
- Áp dụng cho: mọi màn của đợt này
- Tiêu chí chấp nhận:
  - **AC-15** Khi access token hết hạn trong lúc học viên dùng một màn của đợt này và refresh token còn hạn, app tự làm mới một lần rồi gọi lại; học viên không thấy lỗi và không bị đưa về Đăng nhập.
  - **AC-16** *(Từ chối)* Khi phiên đã hết hoặc bị thu hồi, lời gọi nhận 401 sau khi làm mới thất bại thì app xóa phiên và về màn Đăng nhập kèm dòng "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."; câu trả lời chưa gửi của lượt thi đang làm vẫn nằm trong bản nháp trên máy (xem mục 6.4).
  - **AC-17** Khi một thao tác có header `Idempotency-Key` (tạo đơn, thanh toán tiếp, hủy đơn, bắt đầu lượt thi, chọn phần, thử sinh lại phần, nộp bài) thất bại vì mất mạng, hết thời gian chờ, 408, 429 hoặc lỗi 5xx và học viên thử lại, app gửi lại cùng khóa; khi thao tác bị từ chối bằng lỗi 4xx khác thì lần thao tác kế tiếp dùng khóa mới.
  - **AC-18** *(Giới hạn)* Khi backend trả 429 kèm thời gian chờ, app hiện thông báo quá nhiều yêu cầu và không tự gửi lại trước khi hết thời gian chờ đó.

### R-5 Bốn trạng thái của màn tải dữ liệu

Mỗi màn tải dữ liệu từ máy chủ có đủ trạng thái đang tải, rỗng, lỗi và mất mạng.

- Nguồn: `RULES.md` mục 8 (giao diện có bốn trạng thái); `DESIGN.md` mục 11
- Áp dụng cho: mọi màn danh sách và chi tiết của đợt này
- Tiêu chí chấp nhận:
  - **AC-19** Khi một màn đang chờ dữ liệu lần đầu, màn hiện trạng thái đang tải và không hiện nội dung rỗng.
  - **AC-20** Khi lời gọi lỗi (lỗi 5xx hoặc lỗi không xác định), màn hiện thông báo lỗi kèm nút "Thử lại"; bấm nút thì gọi lại đúng lời gọi đó.
  - **AC-21** Khi máy mất mạng lúc mở một màn chưa có dữ liệu, màn hiện trạng thái mất kết nối kèm nút "Thử lại"; khi màn đã có dữ liệu thì dữ liệu cũ vẫn hiển thị kèm dòng báo chưa cập nhật được.
  - **AC-22** Khi học viên kéo xuống để tải lại ở một màn danh sách, app gọi lại máy chủ và thay dữ liệu đang hiển thị bằng kết quả mới.

### R-6 Màu ngữ nghĩa theo token của web

App có thêm các màu thành công, cảnh báo, thông tin (chữ và nền) ở hai chế độ sáng và tối, và hai màu của ô câu được đánh dấu (nền đỏ đặc và chữ trên nền đỏ đặc, D-29), trùng giá trị token của web. Màu chữ đỏ và nền đỏ nhạt (`--color-danger`, `--color-danger-bg`) app đã có từ đợt trước.

- Nguồn: `packages/ui/src/styles/tokens.css:24,59-68,237-244`; đặc tả đợt trước R-12; quyết định D-29
- Áp dụng cho: mọi màn của đợt này dùng màu trạng thái (kết quả, cảnh báo thời gian, trạng thái đơn hàng)
- Tiêu chí chấp nhận:
  - **AC-23** Khi so các màu thành công, cảnh báo, thông tin và nền tương ứng của app với `--color-success`, `--color-success-bg`, `--color-warning`, `--color-warning-bg`, `--color-info`, `--color-info-bg`, `--color-danger-solid`, `--color-on-danger` trong `tokens.css` thì giá trị trùng nhau ở chế độ sáng và ở chế độ tối (hai token cuối hôm nay có cùng một giá trị cho cả hai chế độ).
  - **AC-24** Khi web đổi giá trị một trong các token đó, phép kiểm tự động của app báo đỏ cho tới khi app cập nhật theo.
  - **AC-25** Khi đo độ tương phản của chữ màu trạng thái trên nền tương ứng ở cả hai chế độ, kể cả chữ `--color-on-danger` trên nền `--color-danger-solid` và chữ `--color-danger` trên nền `--color-danger-bg`, kết quả đạt tối thiểu 4,5:1.

### R-7 Ngôn ngữ

Mọi chuỗi mới có bản tiếng Việt và tiếng Anh với cùng bộ khóa.

- Nguồn: đặc tả đợt trước R-13
- Áp dụng cho: mọi màn của đợt này
- Tiêu chí chấp nhận:
  - **AC-26** Khi so hai tệp chuỗi của mỗi app, hai ngôn ngữ có cùng bộ khóa; không chuỗi hiển thị nào nằm cứng trong mã.
  - **AC-27** Khi đổi ngôn ngữ trong mục Tài khoản, mọi màn của đợt này đổi theo mà không cần đăng nhập lại; tên bài thi, nội dung câu hỏi và dữ liệu do máy chủ trả không đổi.

### R-8 Khóa app với các màn mới

Các màn của đợt này nằm sau màn khóa của app như các màn đợt trước.

- Nguồn: đặc tả đợt trước R-10; giả định A-6
- Áp dụng cho: học viên đã bật sinh trắc học
- Tiêu chí chấp nhận:
  - **AC-28** Khi học viên rời app quá 5 phút rồi quay lại ở bất kỳ màn nào của đợt này, app hiện màn khóa; mở khóa xong thì về đúng màn đang xem với dữ liệu nhập dở còn nguyên.
  - **AC-29** Khi app ở trạng thái khóa hoặc đang ở trình chuyển ứng dụng, nội dung câu hỏi, câu trả lời, đơn hàng và hồ sơ không hiển thị.

### R-178 Tên hiển thị "Thi360"

Tên sản phẩm viết liền là "Thi360" ở mọi chỗ người dùng đọc được: tên app dưới biểu tượng trên máy, mọi chuỗi của hai app, của web học viên, của web admin, của gói giao diện dùng chung, thông báo lỗi và mẫu email của backend. Định danh kỹ thuật không đổi: mã ứng dụng `vn.unicom.thi360`, tên realm `thi-360`, tên gói, tên tệp, tên biến, khóa chuỗi.

Riêng thư do backend gửi (D-26): tên sản phẩm ở tiêu đề, nội dung và chữ ký của mọi mẫu thư lấy từ cấu hình `thi360.mail.product-name` (biến môi trường `THI360_MAIL_PRODUCT_NAME`, mặc định "Thi360"); mẫu thư không ghi cứng tên. Tên và địa chỉ người gửi vẫn lấy từ `THI360_MAIL_FROM` như hôm nay, giá trị mặc định đổi thành "Thi360 <no-reply@thi360.local>". Toàn bộ chỗ backend ghi tên sản phẩm hôm nay nằm trong thư: 38 chỗ ở 18 trong 26 tệp mẫu thư, và giá trị mặc định của người gửi.

- Nguồn: quyết định D-15, D-26; hôm nay tên ghi là "Thi 360" (`apps/ios/Config/Base.xcconfig:7`, `apps/android/app/src/main/res/values/strings_shell.xml`, `apps/web-student/src/messages/vi.json:183`, `backend/src/main/resources/mail/templates/`, `backend/src/main/resources/application.yml:210`, `shared/mail/MailProperties.java:87`)
- Áp dụng cho: hai app, web học viên, web admin, backend
- Tiêu chí chấp nhận:
  - **AC-741** Khi cài app trên Android và iOS, tên dưới biểu tượng là "Thi360"; câu xin quyền Face ID và máy ảnh cũng ghi "Thi360".
  - **AC-742** Khi rà mã nguồn và tài nguyên của hai app, `apps/web-student/src`, `apps/web-admin/src` (kể cả tiêu đề trang), `packages/ui/src`, `backend/src/main/resources` (câu chữ, mẫu email, cấu hình), `backend/src/main/java` và `infra` (kể cả tên hiển thị của realm), không còn chuỗi "Thi 360" nào; phép rà là test tự động báo đỏ khi chuỗi đó xuất hiện lại. Các chỗ đặc tả này trích tên cũ ở phần mô tả hiện trạng là mô tả mã trước khi đổi.
  - **AC-743** *(Giới hạn)* Khi so trước và sau, `applicationId`, bundle identifier, tên realm, đường dẫn API và khóa chuỗi không đổi; người đã cài bản app đợt trước cập nhật lên vẫn giữ phiên đăng nhập.
  - **AC-786** Khi backend chạy mà không đặt `THI360_MAIL_PRODUCT_NAME` và `THI360_MAIL_FROM`, thì mọi loại thư ở cả hai ngôn ngữ ghi "Thi360" ở tiêu đề, nội dung, chữ ký và tên người gửi. Khi đặt `THI360_MAIL_PRODUCT_NAME` thành một tên khác rồi khởi động lại backend, thì mọi loại thư ghi tên đó ở tiêu đề, nội dung và chữ ký mà không phải build lại; tên người gửi theo `THI360_MAIL_FROM`.
  - **AC-787** *(Giới hạn)* Khi rà các mẫu thư và nơi đặt tiêu đề thư của backend, thì không có tên sản phẩm nào ghi cứng (phép rà là test tự động, báo đỏ khi một mẫu thư mới ghi cứng tên); khi `THI360_MAIL_PRODUCT_NAME` được đặt là chuỗi rỗng hoặc chỉ có khoảng trắng thì backend dùng "Thi360".

### R-179 Biểu tượng và logo của app

Biểu tượng app là hình dấu của logo Thi360 (mũ cử nhân, quyển sách có nút phát, vòng cung xanh và cam) trên nền trắng. Trong app, những chỗ đang hiện logo tạm (màn Đăng nhập, màn khóa, màn giữ chỗ) hiện hình dấu đó kèm tên "Thi360" viết bằng chữ của app theo màu chữ của chế độ đang dùng. Hình dấu giữ nguyên màu gốc ở cả sáng và tối, không tô theo bảng màu của cấu hình giao diện. Hình logo có chữ "THI360" (chữ xanh đậm) chỉ dùng trên nền sáng; app không đặt nó trên nền tối.

- Nguồn: quyết định D-11, D-14; từ `04a9457b` hai web đã có logo tạm (`packages/ui/src/components/brand-logo.tsx`, `apps/web-student/public/logo*.{png,svg}`, `apps/web-admin/public/logo*.{png,svg}`), đợt này không đổi logo của web; tệp nguồn của app ở `docs/design/app-hoc-vien/brand/` (`thi360-icon.svg`, `thi360-icon.png` 700×700, `thi360-wordmark.png` 1927×610); hôm nay app dùng hình tạm (Android: `ic_thi360_logo.xml` ở `LoginScreen.kt:747`, `LockScreen.kt:246`, `PlaceholderScreen.kt:78`, và `ic_launcher_foreground.xml`; iOS: một ký hiệu hệ thống cạnh tên app, `AppIcon.appiconset` chưa có ảnh)
- Áp dụng cho: hai app
- Tiêu chí chấp nhận:
  - **AC-744** Khi xem biểu tượng app trên màn hình chính, thì đó là hình dấu Thi360 trên nền trắng. Trên Android biểu tượng là loại thích ứng, toàn bộ hình dấu nằm trong vùng an toàn (hình tròn đường kính 66dp của khung 108dp) nên không bị cắt ở mọi hình dạng mặt nạ; trên iOS ảnh biểu tượng 1024×1024 không có vùng trong suốt.
  - **AC-745** Khi mở màn Đăng nhập và màn khóa ở chế độ sáng và ở chế độ tối, thì thấy hình dấu Thi360 đúng màu gốc và tên "Thi360"; chữ tên đạt tương phản tối thiểu 4,5:1 với nền ở cả hai chế độ.
  - **AC-746** Khi đổi bộ màu trong cấu hình giao diện của admin, màu của hình dấu không đổi.
  - **AC-747** *(Giới hạn)* Khi rà mã hai app, không còn nơi nào dùng hình logo tạm của đợt trước.

### R-180 Hai trang văn bản trên web học viên

Web học viên có hai trang công khai, xem được khi chưa đăng nhập: "Điều kiện giao dịch chung" ở `/dieu-khoan-giao-dich-chung` và "Chính sách bảo mật thông tin" ở `/chinh-sach-bao-mat-thong-tin`. Từ `04a9457b` (UNI-2828) hai trang này đã có trên web, với nội dung nằm trong tệp chuỗi của web (`legal.terms.*`, `legal.privacy.*`) và khác bản đã duyệt ở `docs/legal/`. Đợt này giữ hai đường dẫn, khung trang, đường dẫn điều hướng đầu trang và các liên kết ở chân trang, và thay nội dung bằng bản đã duyệt (D-35): nội dung của hai trang lấy từ hai tệp nội dung có cấu trúc trong `contracts/mobile/legal/` (mỗi văn bản một tệp cho mỗi ngôn ngữ, có `version`, `status`, `effectiveDate`, tiêu đề và các khối tiêu đề mục, đoạn văn, danh sách, bảng); các tệp này dựng từ bản đã duyệt ở `docs/legal/`, trong đó `version` và `status` lấy từ hai dòng "Phiên bản" và "Trạng thái" ở phần ghi chú đầu mỗi tệp nguồn; đổi nội dung thì phải tăng phiên bản, và `published` đòi có ngày có hiệu lực. Bản tiếng Anh là bản dịch của bản tiếng Việt và ghi rõ bản tiếng Việt là bản có giá trị khi hai bản khác nhau. Khi `status` của tệp là `draft`, trang hiện dòng "Bản nháp, chờ đơn vị vận hành xác nhận." ở đầu. Hai văn bản của đợt này được phát hành ngay ở trạng thái `published`, phiên bản `2026-11-26`, có hiệu lực từ 2026-11-26, không có dòng "Bản nháp" (D-32); trạng thái `draft` chỉ còn dùng cho một bản sửa đổi về sau đang chờ duyệt.

Ở trang Đăng ký của web, hộp "Điều khoản sử dụng" với câu giữ chỗ được thay: ô đồng ý ghi "Tôi đã đọc và đồng ý với Điều kiện giao dịch chung và Chính sách bảo mật thông tin của Thi360.", hai tên văn bản là hai liên kết mở hai trang trên ở thẻ mới; `termsVersion` gửi khi đăng ký là `version` của tệp Điều kiện giao dịch chung.

- Nguồn: quyết định D-11, D-12, D-13, D-14, D-32, D-35; hai trang đang có: `apps/web-student/src/app/dieu-khoan-giao-dich-chung/page.tsx`, `apps/web-student/src/app/chinh-sach-bao-mat-thong-tin/page.tsx`, `apps/web-student/src/features/legal/terms-of-service-view.tsx`, `privacy-policy-view.tsx`, `legal-pages.test.tsx`, `apps/web-student/src/proxy.ts:12-13`, `apps/web-student/src/components/public-footer.tsx`, `apps/web-student/src/lib/contact.ts`; `docs/legal/thi360-dieu-kien-giao-dich-chung.md`, `docs/legal/thi360-chinh-sach-bao-mat-thong-tin.md`; hôm nay `apps/web-student/src/messages/vi.json:180-186` là câu giữ chỗ và `register-view.tsx:48` ghi `TERMS_VERSION = "2026-11"`; backend nhận `termsVersion` dài tối đa 64 ký tự và không kiểm giá trị (`RegisterRequest.java:38-39`)
- Áp dụng cho: web học viên; khách chưa đăng nhập và học viên
- Tiêu chí chấp nhận:
  - **AC-748** Khi mở `/dieu-khoan-giao-dich-chung` hoặc `/chinh-sach-bao-mat-thong-tin` lúc chưa đăng nhập, thì trang hiện tiêu đề, ngày có hiệu lực (khi tệp có `effectiveDate`; văn bản còn `draft` chưa có ngày thì không có dòng này) và toàn bộ nội dung của tệp tương ứng theo ngôn ngữ đang chọn, không chuyển sang trang đăng nhập.
  - **AC-749** Khi so nội dung tệp tiếng Việt trong `contracts/mobile/legal/` với bản đã duyệt ở `docs/legal/` (bỏ phần ghi chú đầu tệp), thì từng mục, từng đoạn, từng dòng bảng trùng nhau; phép so là một test tự động.
  - **AC-750** Khi `status = draft` (kiểm bằng một tệp mẫu của test), thì đầu trang có dòng "Bản nháp, chờ đơn vị vận hành xác nhận."; khi `status = published` thì không có dòng đó. Hai tệp văn bản thật của đợt này có `status = published`, `version = 2026-11-26`, `effectiveDate = 2026-11-26`, nên hai trang của web và hai màn của app hiện "Có hiệu lực từ 26/11/2026" và không có dòng "Bản nháp".
  - **AC-751** Khi mở trang Đăng ký của web, thì ô đồng ý ghi đúng câu trên với hai liên kết; bấm mỗi liên kết mở đúng trang ở thẻ mới; đăng ký gửi `termsVersion` bằng `version` của tệp Điều kiện giao dịch chung; không còn chuỗi "[NỘI DUNG ĐIỀU KHOẢN SỬ DỤNG, BẢN TẠM CHỜ ĐƠN VỊ VẬN HÀNH XÁC NHẬN]" ở đâu.
  - **AC-752** *(Từ chối)* Khi chưa tích ô đồng ý mà bấm đăng ký, thì hiện "Bạn cần đồng ý với Điều kiện giao dịch chung và Chính sách bảo mật thông tin để đăng ký." và không gọi API.
  - **AC-788** Khi rà web học viên sau đợt này, thì nội dung hai văn bản chỉ còn một nguồn là `contracts/mobile/legal/`: tệp chuỗi `vi.json`, `en.json` không còn các khóa `legal.terms.*` và `legal.privacy.*` (trừ tiêu đề và nhãn điều hướng mà khung trang còn dùng), không còn thành phần nào ghi cứng nội dung văn bản; các liên kết "Điều kiện giao dịch chung" và "Chính sách bảo mật thông tin" ở chân trang vẫn mở đúng hai trang; hai đường dẫn vẫn mở được khi chưa đăng nhập.
  - **AC-753** Khi rà nội dung hai tệp, mọi địa chỉ web là `thi360.vn`, tên dịch vụ là "Thi360", và thông tin đơn vị vận hành (tên công ty, mã số thuế, hai địa chỉ, điện thoại, email) trùng bản đã duyệt ở `docs/legal/` (D-13).

### R-181 Hai văn bản trong app

App mang sẵn nội dung hai văn bản (cùng các tệp `contracts/mobile/legal/` mà web dùng, đưa vào app lúc build) và vẽ bằng thành phần gốc: tiêu đề, ngày có hiệu lực, tiêu đề mục, đoạn văn, danh sách, bảng; bảng rộng hơn màn thì cuộn ngang trong khung. Màn văn bản mở được khi không có mạng và có nút "Mở trên web" mở trang công khai tương ứng trong trình duyệt của máy. Lối vào: ở màn Đăng ký, ô đồng ý ghi cùng câu với web và hai tên văn bản mở màn văn bản tương ứng; ở mục Tài khoản có hai dòng "Điều kiện giao dịch chung" và "Chính sách bảo mật thông tin". `termsVersion` app gửi khi đăng ký là `version` của tệp Điều kiện giao dịch chung mà app mang theo.

- Nguồn: quyết định D-11, D-14; hôm nay hai app có hộp "Điều khoản sử dụng" với câu giữ chỗ (`strings_recovery.xml:37-43`, `RegistrationState.kt:5`, `RegistrationModels.swift:28`); lựa chọn mang sẵn nội dung là của đặc tả (xem giả định ở mục 11)
- Áp dụng cho: hai app; người chưa đăng nhập (màn Đăng ký) và học viên (mục Tài khoản)
- Tiêu chí chấp nhận:
  - **AC-754** Khi ở màn Đăng ký bấm "Điều kiện giao dịch chung" hoặc "Chính sách bảo mật thông tin", thì mở màn văn bản tương ứng với đủ tiêu đề, ngày có hiệu lực (khi tệp có, như AC-748), dòng "Bản nháp, chờ đơn vị vận hành xác nhận." khi `status = draft`, và mọi mục của tệp; đóng màn thì về màn Đăng ký với dữ liệu đã nhập còn nguyên.
  - **AC-755** Khi máy không có mạng, màn văn bản vẫn mở và hiện đủ nội dung.
  - **AC-756** Khi ở mục Tài khoản, thì có hai dòng mở hai màn văn bản; trên màn rộng chúng hiện ở ngăn chi tiết như các dòng khác của mục.
  - **AC-757** Khi bấm "Mở trên web", thì trình duyệt của máy mở `{địa chỉ web của bản build}/dieu-khoan-giao-dich-chung` hoặc `/chinh-sach-bao-mat-thong-tin`.
  - **AC-758** Khi so `version` và nội dung văn bản trong gói app với tệp trong `contracts/mobile/legal/`, chúng trùng nhau; `termsVersion` app gửi khi đăng ký bằng `version` đó. Phép so là test tự động của mỗi app.
  - **AC-759** Khi văn bản có bảng (bảng hoàn tiền, bảng dữ liệu thu thập) trên điện thoại 360dp và ở cỡ chữ 200%, thì bảng cuộn ngang trong khung, không cắt chữ, phần còn lại của màn không cuộn ngang; trình đọc màn hình đọc được tiêu đề mục như tiêu đề.
  - **AC-760** *(Từ chối)* Khi chưa tích ô đồng ý mà bấm đăng ký trong app, thì hiện cùng câu báo với web (AC-752) và không gọi API.
  - **AC-761** Khi đổi ngôn ngữ app sang tiếng Anh, màn văn bản hiện bản tiếng Anh kèm câu ghi bản tiếng Việt là bản có giá trị.

### R-182 Backend: học viên tự xóa tài khoản

Backend có hai endpoint mới cho chính chủ tài khoản, dùng được bằng phiên web lẫn Bearer của app:

- `GET /api/v1/me/account/deletion-check` trả `{ canDelete, blockers[], unusedAttempts, pendingGrading }`. `blockers` gồm `ATTEMPT_IN_PROGRESS` khi tài khoản có lượt thi đang `STARTING` hoặc `IN_PROGRESS`, `ORDER_BEING_CHECKED` khi có đơn đã trả tiền mà chưa được cấp bài (trạng thái đơn hiện là "Đang kiểm tra"), và `ORDER_PENDING_PAYMENT` khi có đơn ở trạng thái chờ thanh toán (tới khi học viên hủy đơn hoặc hệ thống chuyển đơn sang hết hạn). `unusedAttempts` là tổng số lượt thi còn dùng được của mọi quyền thi của tài khoản; tài khoản chưa có quyền thi nào thì là 0. `pendingGrading` là `true` khi tài khoản có lượt thi đã nộp đang chờ chấm; nó không chặn việc xóa.
- `POST /api/v1/me/account/deletion` với thân `{ currentPassword }`. Backend kiểm mật khẩu như lệnh đổi email (sai thì 400 `WRONG_OLD_PASSWORD` ở trường `currentPassword`; tài khoản đang bị khóa đăng nhập thì 429 `LOGIN_THROTTLED`), kiểm lại các điều kiện chặn (còn thì 409 `ACCOUNT_DELETION_BLOCKED` kèm `blockers`), rồi xóa và trả 204.

Xóa nghĩa là, trong một lần gọi: thu hồi mọi phiên web và app của tài khoản; đánh dấu tài khoản đã xóa với lý do `SELF_DELETED`; thay tên đăng nhập, họ tên, email, số điện thoại và email đang chờ bằng giá trị vô danh không suy ra được người cũ; xóa ảnh đại diện (cả tệp), mã xác minh email và mã đặt lại mật khẩu còn hiệu lực, bản ghi đăng ký ban đầu của tài khoản, và gỡ tên đăng nhập, họ tên, email, số điện thoại khỏi dòng nhập học viên từ tệp của tài khoản (D-23); xóa người dùng trên Keycloak (lỗi thì thử lại bằng cơ chế dọn dẹp đang có); phát sự kiện đã xóa người dùng như lệnh xóa của admin (lớp học gỡ thành viên); ghi nhật ký `USER_SELF_DELETED`. Trước khi gỡ email, backend gửi tới địa chỉ đó thư "Tài khoản Thi360 của bạn đã được xóa" (mẫu thư mới, hai ngôn ngữ).

Được giữ lại sau khi xóa: đơn hàng, thanh toán, hoàn tiền (kể cả tên người mua đã chụp lúc mua, theo nghĩa vụ kế toán); quyền thi (không ai dùng được nữa); lượt thi, câu trả lời, bài viết, điểm, chỉ còn gắn với mã tài khoản vô danh; nhật ký thao tác của hệ thống, kể cả các dòng cũ có tên đăng nhập và email (bảng nhật ký chỉ ghi thêm, D-23).

Tiền về sau khi tài khoản đã xóa (D-22): hệ thống hiện vẫn ghi nhận thanh toán VNPAY về trễ, kể cả cho đơn đã hủy hoặc hết hạn, và tự cấp bài. Từ đợt này, khi một thanh toán được ghi nhận cho đơn của tài khoản đã xóa thì hệ thống không cấp quyền thi, đánh dấu đơn là cần xem xét và ghi lý do, để vận hành hoàn tiền cho khách. Các điều kiện chặn ở trên chỉ giảm khả năng xảy ra trường hợp này, không loại hẳn được: một thanh toán được xác nhận trong lúc lệnh xóa đang chạy vẫn đi vào đường này. Tên đăng nhập và email cũ đăng ký lại được ngay như một tài khoản mới, không khôi phục gì của tài khoản cũ.

Lệnh này không áp các điều kiện chặn của lệnh xóa của admin (`USER_HAS_ORDERS`, `USER_HAS_TRANSACTIONS`); lệnh xóa của admin giữ nguyên hành vi. Chỉ tài khoản học viên dùng được: tài khoản có vai trò quản trị hay giáo viên nhận 403 `ACCOUNT_DELETION_NOT_ALLOWED` ở cả hai endpoint. Trong lúc việc xóa người dùng trên Keycloak còn chờ thử lại, đăng ký lại bằng tên đăng nhập hoặc email cũ có thể bị báo trùng; hết lỗi thì đăng ký được. Thư đã xếp gửi trước đó cho tài khoản (kể cả thư báo đã xóa) được giữ trong hàng thư theo chính sách dọn đang có.

- Nguồn: quyết định D-16 đến D-19; mã hiện có: `identity/application/UserAdminService.java:1025-1300` (xóa mềm của admin, các `AccountDeletionGuard`), `identity/domain/UserAccount.java` (`deleted_at`, `delete_reason`), `identity/application/EmailChangeService.java:191-226` (kiểm mật khẩu), `identity/application/AvatarService.java:105`, `identity/infrastructure/KeycloakIdentityClient.java:322-361`, `shared/mail/MailKind.java`; các chỉ mục duy nhất của tên đăng nhập và email chỉ tính tài khoản chưa xóa
- Áp dụng cho: backend; học viên đã đăng nhập
- Tiêu chí chấp nhận:
  - **AC-762** Khi học viên không có lượt thi đang làm và không có đơn đang kiểm tra gọi `deletion-check`, thì `canDelete = true`, `blockers` rỗng và `unusedAttempts` bằng tổng lượt còn lại của các quyền thi; có lượt `IN_PROGRESS` thì `canDelete = false` và `blockers` có `ATTEMPT_IN_PROGRESS`; có đơn đã trả tiền chưa cấp bài thì có `ORDER_BEING_CHECKED`; có đơn ở trạng thái chờ thanh toán thì có `ORDER_PENDING_PAYMENT`, và sau khi học viên hủy đơn đó hoặc hệ thống chuyển đơn sang hết hạn thì điều kiện chặn này mất; tài khoản chưa có quyền thi nào thì `canDelete = true`, `unusedAttempts = 0`; có lượt đã nộp đang chờ chấm thì `pendingGrading = true` và `canDelete` không đổi.
  - **AC-763** Khi gọi `POST …/deletion` với mật khẩu đúng và không có điều kiện chặn, thì trả 204; sau đó đăng nhập bằng tên đăng nhập và mật khẩu cũ bị từ chối, mọi phiên web và app của tài khoản nhận 401 ở lời gọi kế tiếp, và làm mới phiên bị từ chối.
  - **AC-764** Khi đọc bản ghi tài khoản sau khi xóa, thì tên đăng nhập, họ tên, email, số điện thoại không còn giá trị cũ, không còn ảnh đại diện (bản ghi và tệp), không còn mã xác minh hay mã đặt lại mật khẩu của tài khoản, không còn bản ghi đăng ký ban đầu, và dòng nhập từ tệp của tài khoản không còn tên đăng nhập, họ tên, email, số điện thoại; lý do xóa là `SELF_DELETED`; nhật ký có `USER_SELF_DELETED`.
  - **AC-765** Khi tài khoản đã có đơn hàng, quyền thi và lượt thi đã nộp được xóa, thì lệnh vẫn thành công; các đơn hàng, thanh toán, quyền thi và lượt thi còn nguyên trong cơ sở dữ liệu; API quản trị người dùng trả tài khoản ở trạng thái đã xóa với lý do `SELF_DELETED`, không còn tên và email cũ. Ở màn chi tiết người dùng của web admin, dòng báo tài khoản đã xóa ghi lý do là "Học viên tự xóa tài khoản" (tiếng Anh: "Deleted by the student") thay cho mã `SELF_DELETED` (D-33); lý do do admin nhập khi xóa một tài khoản khác vẫn hiện nguyên văn.
  - **AC-766** *(Từ chối)* Khi mật khẩu sai, thì trả 400 `WRONG_OLD_PASSWORD` ở trường `currentPassword` và tài khoản không đổi gì; khi Keycloak đang khóa đăng nhập của tài khoản thì trả 429 `LOGIN_THROTTLED`.
  - **AC-767** *(Từ chối)* Khi có điều kiện chặn, thì trả 409 `ACCOUNT_DELETION_BLOCKED` kèm `blockers` và tài khoản không đổi gì; khi người gọi có vai trò quản trị hoặc giáo viên thì cả hai endpoint trả 403 `ACCOUNT_DELETION_NOT_ALLOWED`; khi chưa đăng nhập thì 401.
  - **AC-768** Khi xóa thành công, thì đúng một thư "Tài khoản Thi360 của bạn đã được xóa" được xếp gửi tới email cũ theo ngôn ngữ của tài khoản; tài khoản chưa có email thì không gửi thư.
  - **AC-769** Khi một người đăng ký mới bằng tên đăng nhập và email của tài khoản vừa xóa, sau khi người dùng cũ đã được xóa trên Keycloak, thì đăng ký thành công và tài khoản mới không thấy đơn hàng, bài thi hay lịch sử nào của tài khoản cũ.
  - **AC-770** *(Giới hạn)* Khi hai lời gọi `POST …/deletion` của cùng tài khoản tới gần như cùng lúc, thì tài khoản bị xóa đúng một lần, một thư được gửi, và lời gọi thứ hai nhận 401 hoặc 204, không nhận lỗi 5xx và không nhận lỗi sai mật khẩu. Khi xóa người dùng trên Keycloak lỗi, lệnh vẫn trả 204, tài khoản đã bị khóa đăng nhập, và việc xóa trên Keycloak được thử lại sau.
  - **AC-771** Khi admin gọi lệnh xóa người dùng đang có với một tài khoản có đơn hàng, thì vẫn nhận 409 như trước.
  - **AC-785** Khi VNPAY báo thanh toán thành công cho một đơn của tài khoản đã xóa (đơn đang chờ thanh toán, đã hủy, đã hết hạn hoặc đã bị thay thế), thì thanh toán được ghi nhận, không quyền thi nào được cấp, đơn được đánh dấu cần xem xét và hiện ở danh sách đơn cần xem xét của màn quản trị, lịch sử đơn có đúng một dòng ghi việc không cấp bài vì tài khoản đã xóa dù việc cấp bài được thử lại nhiều lần; đơn đã có lý do cần xem xét khác thì lý do đó được giữ; với tài khoản chưa xóa, luồng ghi nhận thanh toán và cấp bài không đổi.

### R-183 Xóa tài khoản trong app

Mục Tài khoản có dòng "Xóa tài khoản" ở cuối danh sách, chữ màu cảnh báo nguy hiểm. Bấm vào mở màn "Xóa tài khoản": app gọi `deletion-check` rồi hiện (1) đoạn giải thích điều gì bị xóa và điều gì được giữ, (2) khi `unusedAttempts > 0`, dòng cảnh báo "Bạn còn {n} lượt thi chưa dùng. Các lượt này sẽ mất và không được hoàn tiền.", và khi `pendingGrading = true`, dòng cảnh báo "Bạn có bài đang chờ chấm. Xóa tài khoản thì bạn sẽ không xem được kết quả." (D-24), (3) khi có điều kiện chặn, khối báo chưa xóa được kèm lý do và việc cần làm, không có ô nhập và nút xóa, (4) khi không bị chặn: ô "Mật khẩu hiện tại", ô tích "Tôi hiểu tài khoản bị xóa sẽ không khôi phục được.", nút "Xóa tài khoản" kiểu nguy hiểm chỉ bấm được khi đã tích và đã nhập mật khẩu. Bấm nút thì hiện hộp xác nhận lần cuối; đồng ý thì app gọi `POST …/deletion`.

Xóa thành công: app xóa mọi dữ liệu của tài khoản trên máy như khi đăng xuất (token, trạng thái sinh trắc học, bản nháp, bộ đệm), về màn Đăng nhập và hiện "Tài khoản của bạn đã được xóa.". Câu chữ của màn là chữ mới, ghi ở giả định mục 11.

- Nguồn: quyết định D-16, D-18, D-19, D-20; yêu cầu của kho ứng dụng về xóa tài khoản trong app; bản vẽ `A-DeleteAccount`, `I-DeleteAccount`
- Áp dụng cho: hai app; học viên đã đăng nhập và đã mở khóa app
- Tiêu chí chấp nhận:
  - **AC-772** Khi mở mục Tài khoản, thì dòng "Xóa tài khoản" nằm sau các dòng khác, tách khỏi "Đăng xuất"; bấm vào mở màn Xóa tài khoản (trên màn rộng: ở ngăn chi tiết).
  - **AC-773** Khi `deletion-check` trả `canDelete = true` và `unusedAttempts = 3`, thì màn có đoạn giải thích, dòng cảnh báo với số 3, ô mật khẩu, ô tích và nút "Xóa tài khoản" đang tắt; tích và nhập mật khẩu thì nút bật; `unusedAttempts = 0` thì không có dòng cảnh báo lượt thi; `pendingGrading = true` thì có thêm dòng cảnh báo bài đang chờ chấm và việc xóa vẫn làm được.
  - **AC-774** *(Từ chối)* Khi `blockers` có `ATTEMPT_IN_PROGRESS`, thì màn hiện "Bạn đang có lượt thi chưa nộp. Hãy nộp bài rồi quay lại." và nút mở Bài thi của tôi; có `ORDER_BEING_CHECKED` thì hiện "Bạn có đơn hàng đang được kiểm tra. Hãy chờ bài thi được cấp hoặc liên hệ hỗ trợ rồi quay lại."; có `ORDER_PENDING_PAYMENT` thì hiện "Bạn có đơn hàng đang chờ thanh toán. Hãy hủy đơn hoặc chờ đơn hết hạn rồi quay lại." và nút mở Đơn của tôi; có nhiều điều kiện thì hiện đủ các câu; mọi trường hợp bị chặn không có ô mật khẩu và nút xóa.
  - **AC-775** Khi bấm "Xóa tài khoản", thì hiện hộp xác nhận "Xóa tài khoản vĩnh viễn?" với nút "Xóa tài khoản" và "Hủy"; "Hủy" đóng hộp và không gọi API; đồng ý thì gọi `POST …/deletion` đúng một lần, nút ở trạng thái đang xử lý và không bấm lần hai được.
  - **AC-776** Khi lệnh trả 204, thì app không còn token, trạng thái sinh trắc học, bản nháp hay bộ đệm nào của tài khoản trên máy, hiện màn Đăng nhập với dòng "Tài khoản của bạn đã được xóa.", và không màn nào của phiên cũ còn mở lại được bằng cử chỉ quay lại.
  - **AC-777** *(Từ chối)* Khi lệnh trả 400 `WRONG_OLD_PASSWORD`, thì ô mật khẩu báo "Mật khẩu hiện tại chưa đúng." và ô được xóa trắng; 429 `LOGIN_THROTTLED` thì báo "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau."; 409 `ACCOUNT_DELETION_BLOCKED` thì màn chuyển sang khối báo của AC-774 theo `blockers` nhận được.
  - **AC-778** *(Từ chối)* Khi mất mạng hoặc máy chủ trả 5xx lúc xóa, thì hiện "Chưa xóa được tài khoản. Vui lòng thử lại.", tài khoản và dữ liệu trên máy còn nguyên, nút bấm lại được; nếu lần bấm lại nhận 401 (lần trước đã xóa xong mà mất phản hồi) thì app xử lý như xóa thành công (AC-776); khi `deletion-check` lỗi thì màn hiện trạng thái lỗi có "Thử lại" (R-5).
  - **AC-779** Khi rà log của bản `demo` trong lúc xóa tài khoản, không có mật khẩu.
  - **AC-780** *(Giới hạn)* Khi tài khoản bị xóa từ thiết bị khác hoặc từ web, thì ở lời gọi kế tiếp app này nhận 401, xóa dữ liệu của tài khoản trên máy và về màn Đăng nhập như khi phiên hết hạn.

### R-184 Xóa tài khoản trên web học viên

Trang Hồ sơ của web học viên có phần "Xóa tài khoản" ở cuối, và có trang `/account/delete` (đòi đăng nhập; chưa đăng nhập thì sang trang đăng nhập rồi quay lại) với cùng nội dung, cùng luồng và cùng câu chữ như màn của app (R-183). Xóa thành công thì web về trang đăng nhập với dòng "Tài khoản của bạn đã được xóa.". Đường dẫn `/account/delete` là địa chỉ khai cho kho ứng dụng làm nơi yêu cầu xóa tài khoản ngoài app. Chính sách bảo mật thông tin (mục quyền của người dùng) nêu cả hai cách.

- Nguồn: quyết định D-20; yêu cầu của Google Play về đường dẫn web để xóa tài khoản; `apps/web-student/src/features/account/profile-view.tsx`
- Áp dụng cho: web học viên; học viên đã đăng nhập
- Tiêu chí chấp nhận:
  - **AC-781** Khi mở `/account/delete` lúc đã đăng nhập, thì trang hiện đoạn giải thích, cảnh báo lượt thi, khối chặn hoặc ô mật khẩu, ô tích và nút như AC-773, AC-774; lúc chưa đăng nhập thì sang trang đăng nhập và quay lại trang này sau khi đăng nhập.
  - **AC-782** Khi xóa thành công trên web, thì phiên web bị xóa, trang đăng nhập hiện "Tài khoản của bạn đã được xóa.", và mở lại một trang cần đăng nhập thì bị đưa về trang đăng nhập.
  - **AC-783** *(Từ chối)* Khi mật khẩu sai, bị khóa đăng nhập, bị chặn hoặc lỗi máy chủ, thì web báo cùng các câu của AC-777, AC-778 và tài khoản không đổi gì; lần bấm lại nhận 401 thì web xử lý như xóa thành công (AC-782).
  - **AC-784** Khi mở phần "Xóa tài khoản" ở trang Hồ sơ, thì có liên kết tới `/account/delete`; khi đọc hai tệp Chính sách bảo mật thông tin, mục quyền của người dùng nêu cách xóa trong ứng dụng và trên website.

### 6.1 Danh mục và chi tiết bài thi (mục Trang chủ của app)

### R-9 Trang chủ là danh mục bài thi

Mục Trang chủ thay màn giữ chỗ bằng danh mục có tiêu đề "Chọn bài thi" (`storefront.catalog.title`), không có các khối giới thiệu. Khi mở, app gọi `GET /api/v1/public/storefront/filters` và `GET /api/v1/public/storefront/products` với `sort=NEWEST`, `page=0`, `size=12`.

- Nguồn: giả định A-1 (mục Trang chủ là phần danh mục của trang `/` web); `app/page.tsx:16-40`; `catalog-query.ts:7,86-96`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-30** Khi học viên vào mục Trang chủ lần đầu sau khi mở app, thì app gọi đúng hai endpoint trên (lời gọi `products` có `sort=NEWEST&page=0&size=12`, không có `q`, `kind`, `format`, `subject`, `part`), hiện tiêu đề "Chọn bài thi" và không còn dòng "Chưa có nội dung".
  - **AC-31** Khi danh mục có dữ liệu, thì trên màn không có khối giới thiệu, vì sao, các bước, câu hỏi thường gặp hay khối hỗ trợ của trang `/` web.

### R-10 Tìm theo tên hoặc mã

Ô tìm có nhãn "Tìm theo tên hoặc mã" (`storefront.catalog.searchLabel`) và chữ gợi ý "Ví dụ: HSA FULL hoặc HSA-FULL-01" (`storefront.catalog.searchPlaceholder`). App gửi `q` (đã bỏ khoảng trắng đầu cuối) sau 400 ms ngừng gõ và tải lại danh mục từ trang đầu với các điều kiện khác giữ nguyên.

- Nguồn: `catalog-section.tsx:35,388-397,547-555`; `catalog-query.ts:43,55`; `StorefrontQueryService.java:137-139`.
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-32** Khi gõ "toan" rồi ngừng 400 ms, thì app gọi `products` một lần với `q=toan&page=0` và hiện các sản phẩm có tên chứa "Toán" (không phân biệt dấu, hoa thường) hoặc có mã chứa "toan".
  - **AC-33** *(Giới hạn)* Khi gõ liên tục 5 ký tự, mỗi ký tự cách nhau dưới 400 ms, thì app chỉ gọi `products` một lần sau ký tự cuối.
  - **AC-34** *(Giới hạn)* Khi xóa hết chữ trong ô tìm, thì sau 400 ms app gọi `products` không có tham số `q`. Khi ô chỉ có khoảng trắng, thì app không gửi `q`.
  - **AC-35** *(Giới hạn)* Khi nhập chuỗi dài hơn 100 ký tự, thì app vẫn gửi và kết quả là kết quả của 100 ký tự đầu (backend cắt); app không báo lỗi.

### R-11 Chọn kỳ thi

Hàng chip kỳ thi có nhóm tên "Kỳ thi" (`storefront.catalog.kindLabel`), chip đầu "Tất cả kỳ thi" (`storefront.catalog.kindAll`), sau đó một chip cho mỗi phần tử của `catalogKinds` theo thứ tự API trả; nếu `catalogKinds` rỗng thì dùng `kinds`. Nhãn chip là mã kỳ thi. Một chip được chọn tại một thời điểm. Chọn chip gửi `kind` (hoặc bỏ `kind` với "Tất cả kỳ thi"), bỏ `part`, giữ `subject`, tải lại từ trang đầu. Không có chip nào khi cả hai danh sách rỗng.

- Nguồn: `catalog-section.tsx:408-411,508-535`; `StorefrontQueryService.java:82-92`.
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-36** Khi `filters` trả `catalogKinds` là `[HSA, TSA]`, thì hàng chip là "Tất cả kỳ thi", "HSA", "TSA" và "Tất cả kỳ thi" đang được chọn.
  - **AC-37** Khi đang lọc phần `P1` của HSA và chọn chip "TSA", thì app gọi `products` với `kind=TSA`, không có `part`, `page=0`; ô Phần/môn về "Tất cả phần, môn".
  - **AC-38** *(Giới hạn)* Khi chọn một kỳ thi trong `catalogKinds` mà chưa có sản phẩm đang bán, thì màn hiện trạng thái không có kết quả của R-17.

### R-12 Lọc theo hình thức

Ô "Hình thức" (`storefront.catalog.formatLabel`) có "Tất cả hình thức" (`storefront.catalog.formatAll`) và các hình thức có trong `filters.formats`, theo thứ tự FULL, PART, SUBJECT, BUNDLE, nhãn theo `storefront.format.*` ("Bài thi FULL", "Bài thi theo phần", "Bài thi theo môn", "Bộ đề"). Dưới khối lọc có dòng "Hình thức:" (`storefront.catalog.formatHintLabel`) kèm `storefront.catalog.formatHint`.

- Nguồn: `catalog-section.tsx:413-419,558-571,607-609`; `StorefrontQueryService.java:96-102`.
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-39** Khi `filters.formats` chỉ có `SUBJECT` và `FULL`, thì ô Hình thức có đúng ba lựa chọn theo thứ tự "Tất cả hình thức", "Bài thi FULL", "Bài thi theo môn".
  - **AC-40** Khi chọn "Bộ đề", thì app gọi `products` với `format=BUNDLE&page=0`, các điều kiện khác giữ nguyên; chọn lại "Tất cả hình thức" thì lời gọi không có `format`.

### R-13 Lọc theo phần hoặc môn

Ô "Phần/môn" (`storefront.catalog.partLabel`) có "Tất cả phần, môn" (`storefront.catalog.partAll`), sau đó các phần của `filters.parts` (chỉ phần của kỳ thi đang chọn nếu có; nhãn là tên phần khi đã chọn kỳ thi, là `mã kỳ thi · tên phần` khi chưa), sau đó các môn của `filters.subjects` (tên theo ngôn ngữ app: `nameVi` hoặc `nameEn`, trống thì lấy tên còn lại). Chọn một phần thì gửi `kind` của phần đó và `part`, bỏ `subject`; chọn một môn thì gửi `subject`, bỏ `part`; chọn "Tất cả phần, môn" thì bỏ cả hai. Mỗi lần chọn tải lại từ trang đầu. Tên phần là nội dung đề, không dịch.

- Nguồn: `catalog-section.tsx:420-444,573-583`; `packages/web-kit/src/i18n/pick-name.ts:14-21`; `StorefrontFilters.java:60-69`.
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-41** Khi chưa chọn kỳ thi và `filters.parts` có phần `P1` "Tư duy định lượng" của HSA, thì ô có dòng "HSA · Tư duy định lượng"; chọn dòng đó thì app gọi `products` với `kind=HSA&part=P1&page=0`, không có `subject`, và chip "HSA" chuyển sang được chọn.
  - **AC-42** Khi đang chọn kỳ thi TSA, thì ô không có phần nào của HSA; các môn vẫn có đủ.
  - **AC-43** Khi đang lọc phần `P1` và chọn môn "Toán" (mã `TOAN`), thì app gọi `products` với `subject=TOAN`, không có `part`, `kind` giữ nguyên.
  - **AC-44** Khi ngôn ngữ app là English, thì môn có `nameEn = "Mathematics"` hiện "Mathematics"; tên phần giữ nguyên chữ API trả.

### R-14 Sắp xếp

Ô "Sắp xếp" (`storefront.catalog.sortLabel`) có ba lựa chọn "Mới công bố", "Giá tăng dần", "Giá giảm dần" (`storefront.catalog.sort.NEWEST|PRICE_ASC|PRICE_DESC`), mặc định "Mới công bố". Đổi lựa chọn gửi `sort` tương ứng và tải lại từ trang đầu. Giá dùng để sắp là giá phải trả sau khuyến mãi (`price`).

- Nguồn: `catalog-query.ts:6,10`; `catalog-section.tsx:585-595`; `StorefrontQueryService.java:167-205`.
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-45** Khi chọn "Giá tăng dần", thì app gọi `products` với `sort=PRICE_ASC&page=0` và giá trên các thẻ đã tải không giảm từ đầu tới cuối danh sách.

### R-15 Xóa bộ lọc và giữ bộ lọc

Nút "Xóa bộ lọc" (`storefront.catalog.clearFilters`) luôn có trong khối lọc; bấm thì ô tìm trống, kỳ thi, hình thức, phần/môn về "Tất cả", sắp xếp về "Mới công bố", và app tải lại danh mục từ trang đầu. Bộ lọc đang chọn và vị trí cuộn được giữ khi chuyển sang mục khác rồi quay lại, khi mở chi tiết rồi quay về, và khi xoay hoặc gập/mở máy.

- Nguồn: `catalog-section.tsx:468-472,597-604`; `catalog-query.ts:29-37`; giữ điều kiện trên URL `catalog-query.ts:1-4` (tương đương của app, xem Giả định).
- Áp dụng cho: học viên; danh mục không rỗng.
- Tiêu chí chấp nhận:
  - **AC-46** Khi đang có `q=toan`, kỳ thi HSA, hình thức PART, sắp xếp "Giá giảm dần", bấm "Xóa bộ lọc", thì ô tìm trống, mọi ô về giá trị "Tất cả"/"Mới công bố" và app gọi `products` với `sort=NEWEST&page=0&size=12`, không có tham số lọc nào.
  - **AC-47** Khi đang lọc HSA và đã cuộn tới thẻ thứ 20, mở chi tiết một thẻ rồi quay lại (hoặc sang mục Lịch sử rồi về Trang chủ), thì danh mục vẫn lọc HSA, vẫn ở thẻ thứ 20 và app không gọi lại `products`.

### R-16 Tải dần danh sách

Danh mục là một danh sách cuộn dọc. App tải từng trang 12 sản phẩm; khi cuộn gần cuối các thẻ đã tải và còn trang (`page + 1 < totalPages`), app gọi trang kế tiếp với cùng điều kiện và nối thẻ mới vào cuối. Phía trên danh sách hiện "Hiển thị {from}–{to} trong {total} bài thi đang mở bán" (`storefront.catalog.showing`) với `from = 1`, `to` = số thẻ đang hiện, `total = totalItems` của lần tải gần nhất. Một sản phẩm đã có trên danh sách không hiện lần hai. Web không có cách tải này: web hiện mỗi lần một trang 12 thẻ trên hàng cuộn ngang và đổi trang bằng nút số trang (`catalog-section.tsx:75-283,474-477`); tải thêm khi cuộn là lựa chọn của app (A-8), không phải hành vi web cần bắt chước.

- Nguồn: `catalog-query.ts:7`; `catalog-section.tsx:253-278,336-341`; `StorefrontQueryService.java:163-164,189-191`; cách hiện danh sách là lựa chọn của app (xem Giả định).
- Áp dụng cho: học viên; danh mục có dữ liệu.
- Tiêu chí chấp nhận:
  - **AC-48** Khi có 30 sản phẩm khớp điều kiện, mở danh mục rồi cuộn tới cuối hai lần, thì app gọi lần lượt `page=0`, `page=1`, `page=2` (mỗi lần `size=12`), danh sách có 30 thẻ và dòng đầu đổi từ "Hiển thị 1–12 trong 30 bài thi đang mở bán" sang "Hiển thị 1–30 trong 30 bài thi đang mở bán".
  - **AC-49** *(Giới hạn)* Khi đã tải trang cuối, cuộn tiếp tới cuối, thì app không gọi thêm trang nào.
  - **AC-50** *(Giới hạn)* Khi đang chờ trang kế tiếp và cuộn chạm cuối thêm lần nữa, thì app không gửi lời gọi thứ hai cho cùng trang.
  - **AC-51** *(Giới hạn)* Khi một sản phẩm mới được công bố giữa lúc tải trang 1 và trang 2 (thứ tự "Mới công bố" đẩy một thẻ đã có sang trang 2), thì thẻ đó vẫn chỉ hiện một lần.
  - **AC-52** *(Giới hạn)* Khi trang kế tiếp trả `items` rỗng (danh mục vừa nhỏ lại), thì app dừng tải thêm, giữ các thẻ đã có và cập nhật `total` theo lần tải đó.
  - **AC-53** *(Từ chối)* Khi tải trang kế tiếp bị lỗi, thì các thẻ đã có giữ nguyên, cuối danh sách hiện "Chưa tải được danh mục bài thi. Bộ lọc của bạn vẫn được giữ." (`storefront.catalog.errorMessage`) kèm nút "Thử lại" (`common.retry`); bấm thì gọi lại đúng trang đó.

### R-17 Trạng thái của danh mục

- Nguồn: `catalog-section.tsx:49-73,302-347,456-466,501`; `RateLimitFilter.java:181-190`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-54** Khi đang chờ lần tải đầu hoặc lần tải lại sau khi đổi bộ lọc, thì vùng kết quả hiện khung giữ chỗ dạng thẻ, có nhãn trợ năng "Đang tải danh mục" (`storefront.catalog.loading`); khối lọc vẫn dùng được.
  - **AC-55** Khi `products` trả `totalItems = 0` và không có bộ lọc, thì màn ẩn toàn bộ khối lọc và hiện "Hiện chưa có bài thi nào đang mở bán" (`storefront.catalog.emptyTitle`).
  - **AC-56** Khi `products` trả `totalItems = 0` và có bộ lọc (kể cả chỉ đổi sắp xếp), thì khối lọc vẫn hiện, vùng kết quả hiện "Không tìm thấy bài thi phù hợp" (`storefront.catalog.noResultsTitle`), "Thử từ khóa khác hoặc bỏ bớt bộ lọc." (`storefront.catalog.noResultsDescription`) và nút "Xóa bộ lọc" làm đúng như AC-46.
  - **AC-57** *(Từ chối)* Khi `filters` hoặc `products` trả lỗi máy chủ (5xx) hoặc 429 `RATE_LIMITED`, thì vùng kết quả hiện "Chưa tải được danh mục bài thi. Bộ lọc của bạn vẫn được giữ." kèm "Thử lại"; bộ lọc đang chọn không đổi; bấm "Thử lại" gọi lại cả hai endpoint với cùng điều kiện.
  - **AC-58** *(Từ chối)* Khi máy mất mạng lúc mở Trang chủ, thì vùng kết quả hiện "Không có kết nối" và "Kiểm tra kết nối mạng rồi thử lại." (`common.offline.title`, `common.offline.description`) kèm "Thử lại"; có mạng lại, bấm "Thử lại" thì tải được danh mục.
  - **AC-59** *(Giới hạn)* Khi đã có danh sách và mất mạng, thì các thẻ đã tải vẫn hiện và bấm được; thao tác cần gọi API (đổi bộ lọc, tải trang kế tiếp) hiện thông báo mất kết nối của AC-58 tại vùng kết quả hoặc cuối danh sách, không xóa các thẻ đang có cho tới khi có kết quả mới.

### R-18 Nội dung thẻ sản phẩm

Mỗi thẻ hiện, theo thứ tự: bìa (R-19); chip mã kỳ thi; chip hình thức (`storefront.format.*`, hình thức lạ thì hiện nguyên mã); chip tên phần hoặc tên môn (`scopeLabel`, theo ngôn ngữ app) khi có; chip "Bài ôn" (`storefront.review`) khi `buildMode = REVIEW`; tên sản phẩm tối đa 2 dòng; dòng thông số nối bằng " · " gồm "{n} câu" (`storefront.units.questions`) khi có `questionCount`, "{n} phút" (`storefront.units.minutes`) khi có `durationMinutes`, và "# lượt" (`storefront.units.attempts`) hoặc với bộ đề "# lượt mỗi bài" (`storefront.units.attemptsPerExam`); giá (R-20); khung nút (R-22). Bấm vào bìa, vào tên hoặc nút "Xem chi tiết" mở màn chi tiết của sản phẩm.

- Nguồn: `product-card.tsx:15-43,151-209`; `format.ts:42-46`.
- Áp dụng cho: mọi thẻ trong danh mục.
- Tiêu chí chấp nhận:
  - **AC-60** Khi thẻ là bài thi theo phần, `examKind.code = HSA`, `scopeLabel.nameVi = "Tư duy định lượng"`, `buildMode = REVIEW`, `questionCount = {min:48,max:50}`, `durationMinutes = {min:75,max:75}`, `attemptsGranted = 2`, thì thẻ có các chip "HSA", "Bài thi theo phần", "Tư duy định lượng", "Bài ôn" và dòng "48–50 câu · 75 phút · 2 lượt".
  - **AC-61** Khi thẻ là bộ đề có `attemptsGranted = 1` và không có `questionCount`, `durationMinutes`, thì dòng thông số là "1 lượt mỗi bài" và không có chip phần/môn.
  - **AC-62** Khi bấm vào tên thẻ, thì app mở màn chi tiết của đúng `code` đó (R-24 hoặc R-26) và gọi `GET /api/v1/public/storefront/products/{code}`.
  - **AC-63** *(Giới hạn)* Khi bấm hai lần liên tiếp thật nhanh vào tên thẻ, thì chỉ một màn chi tiết được mở.

### R-19 Ảnh bìa

Bài thi lẻ có `coverUrl` hiện ảnh tải từ địa chỉ máy chủ API cộng `coverUrl`, không kèm thông tin đăng nhập, tỉ lệ 16:9 trên thẻ. Bộ đề, và bài lẻ không có `coverUrl`, hiện bìa mặc định: nền màu chính, nhãn trên (bộ đề: mã kỳ thi; bài lẻ: tên kỳ thi theo ngôn ngữ app, trống thì mã), nhãn dưới (bộ đề: "Bộ đề"; bài lẻ: mã sản phẩm), và chip "-{percent}%" khi có mức giảm (R-20). Ảnh bìa chỉ để trang trí, không có nhãn trợ năng.

- Nguồn: `product-card.tsx:60-149,168-181`; `StorefrontProjector.java:66,119`; `StorefrontPublicController.java:89-105`.
- Áp dụng cho: mọi thẻ; màn chi tiết bài thi (R-24).
- Tiêu chí chấp nhận:
  - **AC-64** Khi thẻ bài lẻ có `coverUrl = "/api/v1/public/storefront/covers/{id}"`, thì app tải ảnh từ `{địa chỉ API}/api/v1/public/storefront/covers/{id}` và hiện ảnh đó trên thẻ.
  - **AC-65** Khi thẻ là bộ đề có `coverUrl` khác rỗng, thì thẻ vẫn hiện bìa mặc định có "Bộ đề", không tải ảnh.
  - **AC-66** *(Từ chối)* Khi tải ảnh bìa nhận 404 hoặc lỗi mạng, thì thẻ hiện bìa mặc định của bài đó; khung thẻ không đổi kích thước.

### R-20 Giá và khuyến mãi

Giá viết theo `storefront.price` ("{amount, number} đ"; tiếng Anh "{amount, number} VND") với dấu phân cách theo ngôn ngữ app. Khi có `promotion` và `listPrice > price`, hiện thêm giá gốc gạch ngang (nhãn trợ năng "Giá gốc", `storefront.listPriceLabel`), chip "Giảm {percent}%" (`storefront.discount`) với `percent = round((listPrice − price) / listPrice × 100)`, tối thiểu 1, và "Kết thúc {dd/MM}" (`storefront.promoEnds`) theo giờ Việt Nam của `promotion.endsAt`. Không có `promotion`, hoặc `listPrice ≤ price`, thì chỉ hiện giá bán.

- Nguồn: `product-price.tsx:24-47`; `format.ts:27-34,49-52`; `packages/web-kit/src/preferences.ts:17`.
- Áp dụng cho: thẻ, khung Giá bán, khung Giá bộ.
- Tiêu chí chấp nhận:
  - **AC-67** Khi `listPrice = 200000`, `price = 150000`, `promotion.endsAt = 2026-12-31T17:30:00Z`, ngôn ngữ tiếng Việt, thì thẻ hiện "200.000 đ" gạch ngang, "Giảm 25%", "150.000 đ" và "Kết thúc 01/01".
  - **AC-68** *(Giới hạn)* Khi `listPrice = 1000`, `price = 999` có khuyến mãi, thì chip là "Giảm 1%". Khi có `promotion` mà `listPrice = price`, thì không có giá gạch ngang, không có chip giảm và không có "Kết thúc".
  - **AC-69** Khi `price = 0`, thì giá hiện "0 đ".

### R-21 Lấy trạng thái quyền cho các thẻ

Với mỗi trang vừa tải, app gọi `POST /api/v1/me/exam-access/query` một lần với `codes` là mã các thẻ của trang đó (tối đa 12, dưới giới hạn 48). Tới khi có kết quả, khung nút của các thẻ đó hiện khung giữ chỗ có nhãn trợ năng "Đang tải quyền" (`storefront.access.loading`) và không có nút mua hay vào bài. Mã không có dòng trong kết quả được coi là `NONE`. Lỗi của lời gọi này chỉ ảnh hưởng khung nút, không ảnh hưởng nội dung thẻ. App không bao giờ gửi quá 48 mã trong một lời gọi (R-23 chia nhóm 48); nếu backend vẫn trả 400 `VALIDATION_FAILED` hay bất kỳ lỗi 4xx nào khác ngoài 401 và 403, thì khung nút đi theo đường lỗi của AC-72, như web coi mọi lỗi của lời gọi này là lỗi tải quyền (`exam-access.ts:65-73`).

- Nguồn: `exam-access.ts:16-27,37-45,51-80`; `catalog-section.tsx:87`; `access-action.tsx:56-77`; `ExamAccessQueryRequest.java:11-15`.
- Áp dụng cho: học viên; mọi thẻ.
- Tiêu chí chấp nhận:
  - **AC-70** Khi trang đầu có 12 thẻ, thì app gửi đúng một lời gọi `exam-access/query` có 12 mã đó; tải trang thứ hai thì gửi thêm một lời gọi với 12 mã của trang thứ hai.
  - **AC-71** Khi `exam-access/query` chưa trả, thì mọi thẻ đã hiện tên và giá, khung nút là khung giữ chỗ, không có nút "Mua bài thi".
  - **AC-72** *(Từ chối)* Khi `exam-access/query` trả 5xx, 400 `VALIDATION_FAILED` hoặc lỗi mạng, thì khung nút của các thẻ trang đó hiện "Chưa xác định được quyền của tài khoản." (`storefront.access.loadError`), nút "Tải lại quyền" (`storefront.access.reload`) và nút "Xem chi tiết"; bấm "Tải lại quyền" gọi lại `exam-access/query` với cùng danh sách mã.
  - **AC-73** *(Từ chối)* Khi `exam-access/query` trả 401, thì app làm mới phiên và gọi lại như mọi API (theo bản khung của app); làm mới bị từ chối thì về màn Đăng nhập. Cách xử lý 403 theo câu hỏi A-77.

### R-22 Nút theo trạng thái quyền

Khung nút của thẻ và của khung Giá bán theo bảng dưới. Thẻ luôn có thêm nút phụ "Xem chi tiết" (`storefront.access.viewDetail`) ở cuối; khung Giá bán không có. "Mua thêm lượt" (`storefront.access.buyMore`) chỉ hiện khi `hasPendingFulfillment = false`; khi `hasPendingFulfillment = true` thì thay bằng dòng "Đơn mua thêm lượt {orderCode} đang được kiểm tra. Bạn không cần thanh toán lại." (`storefront.access.pendingTopUp`). Nhãn mua là "Mua bộ đề" (`storefront.access.buyBundle`) với bộ đề, "Mua bài thi" (`storefront.access.buy`) với bài lẻ.

| `state` | Thẻ | Khung Giá bán |
|---|---|---|
| `IN_PROGRESS` | "Bạn có một lượt đang làm dở." (`storefront.access.continueNote`), nút "Tiếp tục" (`storefront.access.continue`), "Mua thêm lượt" | như thẻ |
| `CAN_START` | "Còn # lượt" (`storefront.access.attemptsLeft`, theo `attemptsLeft`), nút "Vào bài thi" (`storefront.access.enter`), "Mua thêm lượt" | như thẻ |
| `EXHAUSTED` | nút mua | "Bạn đã dùng hết lượt của bài thi này." (`storefront.access.exhaustedNote`), nút "Mua lại" (`storefront.access.buyAgain`) |
| `CHECKING` | nút "Đang kiểm tra" (`storefront.access.checking`) bị khóa; có `pendingOrderCode` thì thêm "Mã đơn {orderCode}. Bạn không cần thanh toán lại. Nếu vẫn chưa thấy bài, hãy liên hệ hỗ trợ và gửi kèm mã đơn này." (`storefront.access.checkingNote`) | như thẻ |
| `NONE` | nút mua | nút mua |

Đích của nút: "Tiếp tục" mở lượt `activeAttemptId` (xem mục 6.4); "Vào bài thi" mở màn chuẩn bị của `entitlementId` (xem mục 6.4); thiếu id tương ứng thì mở mục Bài thi của tôi. Mọi nút mua ("Mua bài thi", "Mua bộ đề", "Mua thêm lượt", "Mua lại") mở màn xác nhận mua (xem mục 6.2) với `code`, `versionNo` và `price` của sản phẩm đang hiện.

- Nguồn: `access-action.tsx:20-22,99-189`; `exam-access.ts:37-45`; `ExamAccessService.java:86-91`.
- Áp dụng cho: học viên; thẻ và khung Giá bán của bài thi lẻ. Với mã bộ đề, dòng của bộ luôn là `NONE` nên thẻ bộ đề luôn có "Mua bộ đề".
- Tiêu chí chấp nhận:
  - **AC-74** Khi dòng quyền là `CAN_START`, `attemptsLeft = 2`, `entitlementId = E`, `hasPendingFulfillment = false`, thì thẻ hiện "Còn 2 lượt", "Vào bài thi", "Mua thêm lượt", "Xem chi tiết"; bấm "Vào bài thi" mở màn chuẩn bị của `E`.
  - **AC-75** Khi dòng quyền là `IN_PROGRESS`, `activeAttemptId = A`, `hasPendingFulfillment = true`, `pendingOrderCode = "DH001"`, thì thẻ hiện "Bạn có một lượt đang làm dở.", "Tiếp tục", dòng "Đơn mua thêm lượt DH001 đang được kiểm tra. Bạn không cần thanh toán lại." và không có "Mua thêm lượt"; bấm "Tiếp tục" mở lượt `A`.
  - **AC-76** Khi dòng quyền là `EXHAUSTED`, thì thẻ có nút "Mua bài thi"; khung Giá bán của cùng bài có "Bạn đã dùng hết lượt của bài thi này." và nút "Mua lại".
  - **AC-77** *(Từ chối)* Khi dòng quyền là `CHECKING` với `pendingOrderCode = "DH002"`, bấm nút "Đang kiểm tra", thì không có gì xảy ra; dưới nút có dòng "Mã đơn DH002. Bạn không cần thanh toán lại. …". Khi `pendingOrderCode` rỗng, thì không có dòng mã đơn.
  - **AC-78** Khi bấm "Mua bài thi" trên thẻ có `versionNo = 3`, `price = 150000`, thì màn xác nhận mua mở với mã thẻ đó, phiên bản 3 và giá 150000.
  - **AC-79** *(Giới hạn)* Khi `CAN_START` mà `entitlementId` rỗng, bấm "Vào bài thi", thì app mở mục Bài thi của tôi. Khi bấm hai lần liên tiếp thật nhanh vào bất kỳ nút nào ở bảng trên, thì chỉ một màn đích được mở.

### R-23 Làm mới trạng thái quyền và danh mục

Khi màn danh mục hoặc màn chi tiết hiện lại sau một màn khác (quay về từ màn xác nhận mua, kết quả thanh toán, chuẩn bị, làm bài, hoặc chuyển từ mục khác về), app gọi lại `exam-access/query` cho các mã đang có trên màn, theo nhóm tối đa 48 mã, và cập nhật khung nút; nội dung thẻ không tải lại. Kéo xuống ở đầu danh mục tải lại `filters`, trang đầu (`page=0`, `size=12`) với bộ lọc đang chọn, và quyền của 12 mã trang đó. Khi tải lại thành công, danh sách được thay toàn bộ bằng trang đầu: các trang đã tải thêm bị bỏ, danh sách ở vị trí đầu, dòng "Hiển thị …" tính theo trang đầu, và cuộn xuống cuối lại tải `page=1`, `page=2` … theo R-16. Lời gọi trang kế tiếp đang chờ lúc bắt đầu kéo không được nối vào danh sách mới. Khi tải lại lỗi, danh sách cũ và các trang đã tải giữ nguyên (AC-83). Kéo xuống ở màn chi tiết tải lại chi tiết và quyền.

- Nguồn: web tải lại quyền khi trang được dựng lại sau điều hướng (`exam-access.ts:58-62`; `providers/query-provider.tsx:72`); `ExamAccessQueryRequest.java:11-15`; cử chỉ kéo là lựa chọn của app (xem Giả định).
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-80** Khi thẻ bài X đang `NONE`, học viên mua xong bài X (xem mục 6.2) rồi quay về danh mục, thì app gọi lại `exam-access/query` và thẻ X đổi sang khung nút của `CAN_START` hoặc `CHECKING` theo kết quả mới.
  - **AC-81** *(Giới hạn)* Khi đã tải 60 thẻ rồi quay về danh mục từ màn khác, thì app gửi hai lời gọi `exam-access/query` (48 mã và 12 mã), không gọi lại `products`.
  - **AC-82** *(Giới hạn)* Khi học viên dùng hết lượt bài X trên web (máy khác) trong lúc app đang mở danh mục, rồi kéo xuống ở đầu danh mục, thì thẻ X đổi sang khung nút `EXHAUSTED`.
  - **AC-83** *(Giới hạn)* Khi kéo xuống ở đầu danh mục mà lời gọi lỗi, thì danh sách cũ vẫn hiện và có thông báo lỗi của AC-57 hoặc AC-58 kèm "Thử lại".
  - **AC-699** *(Giới hạn)* Khi đã tải ba trang (36 thẻ) của 40 sản phẩm khớp điều kiện, cuộn về đầu rồi kéo xuống, thì app gọi `filters`, `products` với `page=0&size=12` và cùng điều kiện, `exam-access/query` với đúng 12 mã của trang đầu; sau khi có kết quả, danh sách có 12 thẻ, dòng đầu là "Hiển thị 1–12 trong 40 bài thi đang mở bán"; cuộn tới cuối thì app gọi `page=1`.
  - **AC-700** *(Giới hạn)* Khi lời gọi `page=2` đang chờ và học viên kéo xuống ở đầu danh mục, thì sau khi cả hai lời gọi trả về danh sách chỉ có các thẻ của trang đầu mới, không có thẻ nào của `page=2` cũ.
  - **AC-701** *(Giới hạn)* Khi đã tải đúng 48 thẻ rồi quay về danh mục từ màn khác, thì app gửi đúng một lời gọi `exam-access/query` có 48 mã. Khi đã tải 50 thẻ, thì app gửi hai lời gọi, 48 mã và 2 mã; không lời gọi nào có quá 48 mã.

### R-24 Màn chi tiết bài thi

Màn chi tiết của một bài lẻ hiện các khối theo thứ tự dưới đây; khối nào không có dữ liệu thì không hiện. Mọi văn bản lấy từ API (tên, tóm tắt, mô tả, mục tiêu, đối tượng, tên phần, tên lựa chọn, ghi chú) hiện dạng văn bản thuần, không dịch.

1. Đầu trang: ảnh bìa tỉ lệ 16:6 (R-19) hoặc, khi không có ảnh bìa, khung có hình dấu Thi360 (thành phần `BrandMark` của app; web từ `04a9457b` hiện logo ở chỗ này thay cho chữ "360", `exam-detail-view.tsx:184-189`) và mã kỳ thi; chip mã kỳ thi, chip hình thức, chip phần/môn, chip "Bài ôn"; "Mã sản phẩm {code}" (`storefront.detail.codeLabel`); tên; `summary`; `description`; "Mục tiêu" (`storefront.detail.objectives`) với `objectives`; "Đối tượng phù hợp" (`storefront.detail.audience`) với `audience`.
2. "Cấu trúc bài thi" (`storefront.detail.structureTitle`) khi `parts` không rỗng: chip "{questions} câu · {minutes} phút" (`storefront.detail.structureTotal`) khi có cả hai; mỗi phần một dòng gồm Thứ tự, Phần, Số câu, Thời lượng, Điểm tối đa, Tính chất (`storefront.detail.columns.*`; giá trị thiếu là "—"; Tính chất là "Bắt buộc" hoặc "Tự chọn", `storefront.detail.required|optional`).
3. "Phần được chọn trong phạm vi mua" (`storefront.detail.choicesTitle`) khi `choices` không rỗng: theo từng nhóm `partName`, mỗi lựa chọn có tên, "Chọn {count} trong {total} môn" (`storefront.detail.chooseSubjects`) khi có `chooseSubjects`, danh sách môn, số câu theo dạng câu, số câu theo môn (`storefront.detail.subjectAnswerTypes`), "Số câu mỗi môn theo số môn đã chọn: {counts}" (`storefront.detail.countsByPick`).
4. "Cách làm và cách chấm" (`storefront.detail.howTitle`): "Dạng câu hỏi" với số câu theo dạng của các phần cố định (`storefront.detail.answerTypeCount`, tên dạng theo `storefront.detail.answerType.*`, `QUESTION_SET` tính theo `childAnswerType`); "Thang điểm" "{score} điểm" với `scoring.maxScore`, thiếu thì khoảng `maxPoints`; "Thời gian" gồm thời lượng và câu `storefront.detail.how.timingPER_PART` hoặc `timingSHARED`; "Chuyển phần" với `storefront.detail.how.switchingValue`; câu `storefront.detail.how.essayNote` khi có dạng `ESSAY` ở bất kỳ phần hay lựa chọn nào; mỗi `omittedEssayGroups` một dòng "Không có phần tự luận: {tên} · {điểm} điểm".
5. "Quyền lợi sử dụng" (`storefront.detail.rights.title`): "Số lượt" "# lượt thi mỗi lần mua"; "Mức xem kết quả" theo `storefront.detail.rights.resultViews.*` khi có `resultView`; "Thời điểm xem kết quả" là "Sau khi người chấm chấm xong" khi có `ESSAY`, ngược lại "Ngay sau khi chấm xong"; "Giải thích bằng AI", "Đánh giá điểm mạnh, điểm yếu", "Ôn luyện bổ sung" kèm "Có"/"Không"; "Ghi chú: {note}" khi có.
6. "Giá và chính sách" (`storefront.detail.policy.title`): `storefront.detail.policy.priceNote`; dòng hoàn tiền (xem Giả định về câu chữ); dòng kênh hỗ trợ "Gửi yêu cầu qua: {contact}" hoặc "Thông tin liên hệ đang được cập nhật." và "Giờ hỗ trợ: {hours}" (nguồn kênh theo câu hỏi A-76).
7. Khung Giá bán (R-25).

- Nguồn: `exam-detail-view.tsx:39-59,61-117,119-131,134-446`.
- Áp dụng cho: học viên; sản phẩm không phải bộ đề, API trả 200.
- Tiêu chí chấp nhận:
  - **AC-84** Khi mở chi tiết bài có 3 phần (phần 2 `required = false`, phần 3 thiếu `maxPoints`), thì khối "Cấu trúc bài thi" có 3 dòng theo `order`, dòng 2 có "Tự chọn", ô Điểm tối đa của dòng 3 là "—".
  - **AC-85** Khi bài có một nhóm `QUESTION_SET` với `childAnswerType = SINGLE_CHOICE`, 10 câu, và một nhóm `SINGLE_CHOICE` 5 câu, thì "Dạng câu hỏi" là "Trắc nghiệm một đáp án: 15".
  - **AC-86** Khi một lựa chọn trong `choices` có dạng `ESSAY` dù các phần cố định không có, thì khối "Cách làm và cách chấm" có câu "Phần tự luận chấm tay. Điểm tổng có sau khi chấm xong." và "Thời điểm xem kết quả" là "Sau khi người chấm chấm xong".
  - **AC-87** Khi `scoring.maxScore` rỗng và `maxPoints = {min: 150, max: 150}`, thì "Thang điểm" là "150 điểm". Khi `summary`, `description`, `objectives`, `audience` đều rỗng, thì đầu trang chỉ có bìa, chip, mã và tên.
  - **AC-88** *(Từ chối)* Khi đang chờ chi tiết, thì màn hiện khung giữ chỗ; khi lời gọi trả 5xx hoặc 429, thì màn hiện "Đã có lỗi máy chủ" và "Đã xảy ra lỗi không mong muốn khi xử lý yêu cầu. Vui lòng thử lại sau." (`errorPage.title`, `errorPage.description`) kèm "Thử lại" gọi lại chi tiết; mất mạng thì hiện thông báo của AC-58.

### R-25 Khung Giá bán của bài thi

Khung có tiêu đề "Giá bán" (`storefront.detail.priceHeading`), giá theo R-20, dòng "{promotion.name} · Kết thúc {dd/MM}" khi có `promotion`, danh sách "Đã gồm thuế, phí" (`storefront.detail.includesTax`), "# lượt thi mỗi lần mua" (`storefront.detail.attemptsPerPurchase`), "{questions} câu · {minutes} phút mỗi lượt" (`storefront.detail.perAttempt`) khi có cả hai; dưới cùng là khung nút theo R-22 với một lời gọi `exam-access/query` chứa đúng mã của bài. Trạng thái đang tải và lỗi của quyền như AC-71, AC-72, không có nút "Xem chi tiết".

- Nguồn: `buy-panel.tsx:13-69`; `access-action.tsx:47-77`.
- Áp dụng cho: học viên; màn chi tiết bài thi.
- Tiêu chí chấp nhận:
  - **AC-89** Khi bài có `attemptsGranted = 3`, `questionCount = {50,50}`, `durationMinutes = {90,90}` và không có khuyến mãi, thì khung hiện "Đã gồm thuế, phí", "3 lượt thi mỗi lần mua", "50 câu · 90 phút mỗi lượt" và không có dòng khuyến mãi.
  - **AC-90** Khi có `promotion` mà `listPrice = price`, thì khung vẫn hiện dòng "{tên khuyến mãi} · Kết thúc {dd/MM}" nhưng không có giá gạch ngang và chip giảm.
  - **AC-91** Khi mở chi tiết, thì app gửi `exam-access/query` với `codes = [code của bài]` và khung nút theo đúng bảng của R-22 ở cột Khung Giá bán.

### R-26 Màn chi tiết bộ đề

Màn chi tiết bộ đề hiện: dải bìa có mã kỳ thi và "Bộ đề" (`storefront.format.BUNDLE`); chip tên kỳ thi (theo ngôn ngữ app, trống thì mã), chip "Bộ đề", chip "# bài" (`bundle.detail.examCountValue`, đếm `items`), chip "Bài ôn" khi `buildMode = REVIEW`, "Mã sản phẩm {code}"; tên, `summary`, `description`; khối "Các bài trong bộ" (`bundle.detail.itemsTitle`) kèm chip "# bài", mỗi bài một dòng đánh số từ 1 có tên (bấm mở chi tiết của bài đó), chip "Bài ôn", dòng "{tên môn} · {Bài thi theo phần | Bài thi theo môn} · {partCode}" (`bundle.detail.partScope|subjectScope`), số câu, thời lượng, "{điểm} điểm", "Quyền lợi bài này: # lượt" (`bundle.detail.examRightsPrefix`), mức xem kết quả, ba quyền lợi Có/Không, ghi chú; câu `bundle.detail.itemsNote`; khối "Cách dùng bộ đề" (`bundle.detail.howTitle`) với ba dòng `bundle.detail.how1..how3`; khối "Giá và chính sách" như mục 6 của R-24; khung Giá bộ (R-27). Màn bộ đề không tải ảnh bìa. Theo bản thiết kế đã duyệt (D-9), khi lời gọi `exam-access/query` của R-27 có kết quả thì dòng của bài có `attemptsLeft > 0` có thêm chip "Còn # lượt" (`storefront.access.attemptsLeft`) và dòng của bài có `hasPendingFulfillment = true` có thêm chip "Đang kiểm tra" (`storefront.access.checking`); một bài có cả hai thì hiện cả hai chip. Khi quyền đang tải hoặc tải lỗi thì không dòng nào có chip. Web không có hai chip này.

- Nguồn: `bundle-detail-view.tsx:15-254`.
- Áp dụng cho: học viên; sản phẩm là bộ đề, API trả 200.
- Tiêu chí chấp nhận:
  - **AC-92** Khi bộ đề có 4 `items`, thì chip và khối "Các bài trong bộ" đều ghi "4 bài" và có 4 dòng đánh số 1 tới 4 theo thứ tự API trả.
  - **AC-93** Khi một bài trong bộ có `scope = SUBJECT`, `subjectName.nameVi = "Toán"`, không có `partCode`, thì dòng phụ là "Toán · Bài thi theo môn".
  - **AC-94** Khi bấm tên một bài trong bộ, thì app mở màn chi tiết của mã bài đó; nếu bài đó không bán lẻ và API trả 404, thì hiện màn của R-29.
  - **AC-740** Khi dòng quyền của bộ có bài "Toán" `attemptsLeft = 2`, bài "Anh" `hasPendingFulfillment = true` và bài "Văn" `attemptsLeft = 0`, không có đơn đang kiểm tra, thì trong khối "Các bài trong bộ" dòng bài Toán có chip "Còn 2 lượt", dòng bài Anh có chip "Đang kiểm tra", dòng bài Văn không có chip nào trong hai chip đó; khi `exam-access/query` chưa trả hoặc trả lỗi thì không dòng nào có chip.

### R-27 Khung Giá bộ

Khung có tiêu đề "Giá bộ" (`bundle.detail.priceLabel`), giá theo R-20, dòng khuyến mãi như R-25, các dòng "Số bài" / "# bài", "Lượt mỗi bài" / "# lượt", "Tổng số câu" khi có `questionCount`, "Tổng thời lượng" khi có `durationMinutes`, câu "Đã gồm thuế, phí". App gọi `exam-access/query` với mã bộ đề; khi có kết quả, khung luôn có nút "Mua bộ đề" (`bundle.buyLabel`, mở màn xác nhận mua như R-22), sau đó:

- mỗi bài trong `items` của dòng quyền có `attemptsLeft > 0` một dòng "Bạn đã có # lượt bài {tên}; mua bộ sẽ cộng thêm lượt" (`bundle.detail.existingEntitlement`);
- mỗi bài có `hasPendingFulfillment = true` một dòng "Đơn {orderCode} cho bài {tên} đang được kiểm tra. Bạn không cần thanh toán lại." (`bundle.detail.pendingOrder`), hoặc khi thiếu mã đơn "Có đơn đang được kiểm tra cho bài {tên}. Bạn không cần thanh toán lại." (`bundle.detail.pendingOrderNoCode`);
- câu "Trang chi tiết bộ luôn có nút Mua bộ đề, kể cả khi bạn đã có vài bài trong bộ." (`bundle.buyNote`).

Đang tải và lỗi của quyền như AC-71, AC-72.

- Nguồn: `bundle-buy-panel.tsx:15-17,39-47,57-96,114-127,152-215`; `ExamAccessService.java:86-91,106-160`.
- Áp dụng cho: học viên; màn chi tiết bộ đề.
- Tiêu chí chấp nhận:
  - **AC-95** Khi dòng quyền của bộ có `items` với bài "Toán" `attemptsLeft = 2` và bài "Văn" `attemptsLeft = 0`, thì khung có "Mua bộ đề", đúng một dòng "Bạn đã có 2 lượt bài Toán; mua bộ sẽ cộng thêm lượt" và không có dòng cho bài "Văn".
  - **AC-96** Khi bài "Anh" trong bộ có `hasPendingFulfillment = true`, `pendingOrderCode = "DH003"`, thì khung có dòng "Đơn DH003 cho bài Anh đang được kiểm tra. Bạn không cần thanh toán lại." và nút "Mua bộ đề" vẫn bấm được.
  - **AC-97** Khi mọi bài trong bộ có `state = IN_PROGRESS` hoặc `CAN_START`, thì khung không có nút "Tiếp tục" hay "Vào bài thi"; chỉ có "Mua bộ đề" và các dòng thông tin trên.

### R-28 Bài đã ngừng bán

Phản hồi 410 `PRODUCT_DISCONTINUED` của chi tiết chỉ mang `product {code, name}` (Hiện trạng); nó không có dữ liệu chi tiết nào, nên màn 410 chỉ dùng hai trường đó. Khi chi tiết trả 410, màn hiện tông cảnh báo với "MÃ LỖI 410" (`storefront.gone.eyebrow`), "Bài thi đã ngừng bán" (`storefront.gone.title`), "{name} ({code}) không còn mở bán. Bạn có thể chọn bài thi khác trong danh mục." (`storefront.gone.descriptionTail`; `name` và `code` lấy từ `product` của phản hồi, trường nào thiếu hoặc rỗng thì dùng mã sản phẩm đã mở), dòng "Nếu đã mua bài này, quyền và số lượt còn lại của bạn vẫn ở Bài thi của tôi." (`storefront.gone.owned`), nút "Về danh mục bài thi" (`storefront.gone.toCatalog`) và nút "Bài thi của tôi" (`storefront.gone.myExams`, mở mục Bài thi của tôi). "Về danh mục bài thi" đưa về danh mục ở mục Trang chủ với bộ lọc và vị trí cuộn đang có (A-101); ở hai cột thì cột phải về dòng giữ chỗ của A-13. Sau khi nhận 410, app không gọi lại `products/{code}`, không gọi `exam-access/query` và không hiện khối nào của R-24 hay R-26.

- Nguồn: `server-api.ts:71-74`; `exam-status-views.tsx:75-114`; `StorefrontExceptionAdvice.java:34-47`; `ProductGoneResponse.java:8-19`.
- Áp dụng cho: học viên; luôn có nút "Bài thi của tôi" vì app luôn có phiên.
- Tiêu chí chấp nhận:
  - **AC-98** Khi thẻ bài X vẫn còn trên danh mục nhưng bài vừa ngừng bán, bấm vào thẻ, thì màn chi tiết hiện đúng nội dung trên với tên và mã của X; bấm "Bài thi của tôi" thì mục Bài thi của tôi được chọn.
  - **AC-702** *(Từ chối)* Khi chi tiết của X trả 410 với `product = {code: "X", name: "Đề X"}`, thì sau phản hồi đó app không gửi thêm lời gọi `products/X` hay `exam-access/query` nào, và màn không có bìa, bảng cấu trúc, khung giá hay nút mua.
  - **AC-703** Khi đang lọc HSA, mở thẻ X đã ngừng bán rồi bấm "Về danh mục bài thi" trên màn 410, thì mục Trang chủ hiện danh mục vẫn lọc HSA ở vị trí cuộn trước khi mở X.
  - **AC-704** *(Giới hạn)* Khi phản hồi 410 có `product.code = "X"` nhưng `product.name` rỗng hoặc không có, thì câu mô tả là "X (X) không còn mở bán. Bạn có thể chọn bài thi khác trong danh mục."

### R-29 Không tìm thấy sản phẩm

Khi chi tiết trả 404, màn hiện tông trung tính với "MÃ LỖI 404" (`storefront.notFound.eyebrow`), "Không tìm thấy bài thi" (`storefront.notFound.title`), "Đường dẫn không đúng hoặc bài thi chưa được công bố." (`storefront.notFound.description`) và nút "Về danh mục bài thi" (`storefront.notFound.toCatalog`, về mục Trang chủ).

- Nguồn: `server-api.ts:61`; `app/exams/[code]/page.tsx:43`; `exam-status-views.tsx:117-133`; `StorefrontQueryService.java:211-221`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-99** Khi mở chi tiết của một bộ đề đang `UNAVAILABLE` (API trả 404 như hiện nay), thì màn hiện đúng nội dung trên và không có nút mua.

### R-30 Bộ đề tạm thời chưa bán được

Web có sẵn hai cách hiện bộ đề tạm chưa bán được; backend hiện không trả trường hợp này (Hiện trạng). App xử lý giống web để không vỡ khi backend bật lại: (a) chi tiết trả 409 `PRODUCT_UNAVAILABLE` có kèm `product` đủ trường và là bộ đề, hoặc trả 200 với `saleState = UNAVAILABLE`: hiện màn chi tiết bộ đề của R-26, khung Giá bộ thay khung nút bằng cảnh báo "Bộ đề tạm thời chưa bán được." / "Một bài trong bộ đang được cập nhật. Bạn vẫn mua được từng bài lẻ." (`bundle.detail.unavailableTitle`, `bundle.detail.unavailableBody`) và nút "Mua bộ đề" bị khóa, không gọi `exam-access/query`; (b) 409 `PRODUCT_UNAVAILABLE` không kèm `product` đủ trường: hiện màn tông cảnh báo "TẠM NGỪNG BÁN", "Bộ đề tạm thời chưa bán được.", "Một bài trong bộ đang được cập nhật. Bạn vẫn mua được từng bài lẻ." (`bundle.unavailablePage.*`), nút "Mua bộ đề" bị khóa và nút "Về danh mục bài thi".

- Nguồn: `server-api.ts:54-70`; `format.ts:13-24`; `app/exams/[code]/page.tsx:49-54`; `bundle-buy-panel.tsx:34-36,98-113`; `exam-status-views.tsx:136-158`; câu hỏi A-78.
- Áp dụng cho: học viên; chỉ kiểm được bằng phản hồi giả lập.
- Tiêu chí chấp nhận:
  - **AC-100** *(Từ chối)* Khi phản hồi giả lập là 409 `PRODUCT_UNAVAILABLE` kèm `product` là bộ đề đủ trường, thì màn hiện chi tiết bộ đề, cảnh báo trên và nút "Mua bộ đề" bị khóa; app không gọi `exam-access/query`.
  - **AC-101** *(Từ chối)* Khi phản hồi giả lập là 409 `PRODUCT_UNAVAILABLE` không có `product`, thì màn hiện "TẠM NGỪNG BÁN" với hai nút như trên; bấm "Về danh mục bài thi" về mục Trang chủ.

### R-31 Bố cục hai cột

Ở bề rộng hai cột, mục Trang chủ hiện danh mục ở cột trái và chi tiết sản phẩm đang chọn ở cột phải; bấm thẻ (bìa, tên, "Xem chi tiết") thay nội dung cột phải, không mở màn mới. Khi chưa chọn sản phẩm, cột phải hiện dòng giữ chỗ (xem Giả định). Nút đi tiếp (mua, vào bài, tiếp tục) mở màn đích toàn màn hình như ở một cột. Dưới bề rộng hai cột, chi tiết là màn riêng có nút quay lại về danh mục.

- Nguồn: quyết định của đợt khung và xác thực (bố cục theo bề rộng).
- Áp dụng cho: học viên trên tablet, iPad, máy gập đang mở.
- Tiêu chí chấp nhận:
  - **AC-102** Khi Android bề rộng từ 840dp, bấm thẻ X rồi thẻ Y, thì cột phải lần lượt hiện chi tiết X rồi Y, cột trái giữ bộ lọc và vị trí cuộn, thẻ Y được đánh dấu đang chọn.
  - **AC-103** Khi đang hiện chi tiết X ở cột phải và thu bề rộng xuống dưới 840dp (xoay, gập máy, chia đôi màn hình), thì app hiện màn chi tiết X một cột; bấm quay lại thì về danh mục với bộ lọc và vị trí cuộn như trước.
  - **AC-104** Khi đổi bộ lọc mà sản phẩm đang hiện ở cột phải không còn trong kết quả mới, thì cột phải vẫn hiện chi tiết đó cho tới khi chọn thẻ khác.

### 6.2 Mua bài thi, thanh toán VNPAY và đơn hàng

### R-32 Mở màn Xác nhận mua

App mở màn Xác nhận mua từ nút mua của một bài thi hoặc bộ đề, kèm Giá đã thấy, và hiện đủ thông tin trước khi cho thanh toán.

- Nguồn: `checkout-view.tsx:632-1090`, `features/checkout/api.ts:72-103`; quyết định của đợt khung và xác thực (chỉ học viên dùng app).
- Áp dụng cho: học viên đã đăng nhập; sản phẩm là bài lẻ hoặc bộ đề.
- Tiêu chí chấp nhận:
  - **AC-105** Khi học viên bấm mua một sản phẩm đang bán, màn mở với tiêu đề "Xác nhận mua" (`checkout.heading`), khối "Tài khoản nhận bài thi" (`checkout.account.title`) có họ tên, tên đăng nhập và câu `checkout.account.grantNotice` (bộ đề dùng `bundle.checkout.grantNotice` với số bài), khối thông tin sản phẩm có nhãn "Phiên bản v{version}" lấy `versionNo` của Báo giá, số lượt, số câu, thời lượng, quyền lợi AI, điểm mạnh điểm yếu, ôn luyện (Có/Không), và khối "Chính sách hoàn tiền" (`checkout.refund.*`) kèm liên hệ hỗ trợ hoặc "Thông tin liên hệ đang được cập nhật." (`landing.support.pending`).
  - **AC-106** Khi sản phẩm là bộ đề, khối sản phẩm liệt kê từng bài trong bộ với tên, môn, số câu, thời lượng, điểm tối đa, số lượt, cách xem kết quả, dòng "Còn # lượt" cho bài đã có lượt, và câu `bundle.checkout.existingNote` ở cuối.
  - **AC-107** Khi đang tải sản phẩm hoặc phiên, màn hiện khung chờ, không có nút thanh toán bấm được.
  - **AC-108** *(Từ chối)* Khi API sản phẩm trả 404, màn hiện "Không tìm thấy bài thi" (`checkout.notFound.title`) cùng mô tả `checkout.notFound.description` và nút "Về danh mục bài thi" (`checkout.backToCatalog`) đưa về mục Trang chủ.
  - **AC-109** *(Từ chối)* Khi API sản phẩm, Báo giá hoặc lệnh tạo đơn trả 410 `PRODUCT_DISCONTINUED`, màn hiện "Bài thi này hiện đã ngừng bán" (`checkout.gone.title`) với tên sản phẩm trong `checkout.gone.description` và nút "Về danh mục bài thi"; không còn nút thanh toán.
  - **AC-110** *(Từ chối)* Khi API sản phẩm trả 409 `PRODUCT_UNAVAILABLE` không kèm sản phẩm, màn hiện "Bộ đề tạm thời chưa bán được." (`bundle.checkout.unavailableTitle`), câu `bundle.detail.unavailableBody`, nút "Về chi tiết bộ đề" và "Về danh mục bài thi".
  - **AC-111** *(Từ chối)* Khi Báo giá hoặc lệnh tạo đơn trả 409 `PRODUCT_UNAVAILABLE` sau khi đã có sản phẩm, màn giữ thông tin sản phẩm, hiện dải cảnh báo `bundle.checkout.unavailableTitle` + `bundle.checkout.unavailableBody` ("Một bài trong bộ vừa ngừng bán. Chưa có khoản nào bị trừ.") với nút "Về chi tiết bộ đề", và nút thanh toán bị khóa.
  - **AC-112** *(Từ chối)* Khi tải sản phẩm lỗi mạng hoặc 5xx, màn hiện "Không thể tải thông tin thanh toán" (`checkout.loadError.title`), câu "Vui lòng kiểm tra kết nối mạng và thử lại." và nút "Thử lại" (`common.retry`); bấm thì tải lại.

### R-33 Báo giá và tóm tắt thanh toán

- Nguồn: `checkout-view.tsx:186-201,1117-1244`.
- Áp dụng cho: học viên; sản phẩm đang bán.
- Tiêu chí chấp nhận:
  - **AC-113** Khi màn mở, app gọi `POST /api/v1/me/price-quote` với `code`, `seenPrice` và `seenVersionNo` từ Giá đã thấy (bỏ trường nào không có), và khối "Chi tiết thanh toán" (`checkout.summary.title`) hiện "Giá gốc" = `listPrice`, dòng ưu đãi tự động "Ưu đãi tự động ({tên})" (`checkout.summary.promotion`, không có tên thì `checkout.summary.promotionFallback`) với số tiền âm chỉ khi `promotion.discountVnd > 0`, dòng "Mã giảm giá ({code})" chỉ khi `voucher.discountVnd > 0`, và "Thành tiền" = `finalPrice`, số tiền viết dạng `{amount, number} đ` (`storefront.price`).
  - **AC-114** Khi `finalPrice > 0`, nút chính ghi "Thanh toán qua VNPAY" (`checkout.summary.ctaVnpay`); khi `finalPrice = 0` có voucher, Thành tiền ghi "0 đ (đã áp dụng voucher)" (`checkout.summary.freeVoucher`) và nút ghi "Dùng voucher và nhận bài thi" (`checkout.summary.ctaFree`); khi `finalPrice = 0` không voucher, nút ghi "Nhận bài thi miễn phí" (`checkout.summary.ctaFreeNoVoucher`).
  - **AC-115** Khi Báo giá đang tải, khối tóm tắt hiện khung chờ và chưa có nút thanh toán.
  - **AC-116** *(Từ chối)* Khi Báo giá lỗi (mạng, 5xx, 404), khối tóm tắt hiện "Vui lòng kiểm tra kết nối mạng và thử lại." và nút "Thử lại"; bấm thì gọi lại Báo giá. Báo giá không tự thử lại.

### R-34 Giá hoặc quyền lợi đã thay đổi

- Nguồn: `checkout-view.tsx:113,169-194,233-244,290-297,375-379,802-822`, `voucher-box.tsx:117,173-174`.
- Áp dụng cho: học viên; Giá đã thấy khác giá hiện tại.
- Tiêu chí chấp nhận:
  - **AC-117** Khi Báo giá có `changed = true`, hoặc `versionNo` khác phiên bản đã thấy, hoặc (chưa áp mã) `finalPrice` khác giá đã thấy, màn hiện dải "Giá hoặc quyền lợi đã thay đổi" (`checkout.priceChanged.title`) với câu `checkout.priceChanged.descriptionWithSeen` ("…cập nhật lên {latest} (trước đó hiển thị {seen})…") khi biết giá đã thấy và khác giá mới, ngược lại câu `checkout.priceChanged.description`.
  - **AC-118** Khi lệnh tạo đơn trả 409 `PRICE_CHANGED` kèm báo giá mới, màn thay tóm tắt bằng báo giá mới, hiện dải của AC-117 với `{seen}` là Thành tiền vừa gửi, ô mã giảm giá về trạng thái theo báo giá mới, không chuyển sang Trang thanh toán; học viên phải bấm thanh toán lần nữa.
  - **AC-705** *(Giới hạn)* Khi lệnh tạo đơn đã trả 409 `PRICE_CHANGED` và học viên sau đó áp mã, gỡ mã hoặc màn tải lại Báo giá, các trường `seenPrice`, `seenVersionNo` mà AC-113, AC-120, AC-122 gửi vẫn lấy từ Giá đã thấy ban đầu (không thay bằng giá mới vừa thấy), tóm tắt hiện theo báo giá vừa nhận, dải "Giá hoặc quyền lợi đã thay đổi" vẫn hiện tới khi rời màn hoặc đổi tài khoản, và lệnh tạo đơn kế tiếp gửi `expectedAmountDue`, `expectedVersionNo` của báo giá đang hiện.

### R-35 Mã giảm giá

- Nguồn: `voucher-box.tsx:100-343`, `checkout-view.tsx:1092-1115`.
- Áp dụng cho: học viên; màn đã có Báo giá.
- Tiêu chí chấp nhận:
  - **AC-119** Khi ô "Nhập mã giảm giá" (`checkout.voucher.label`, gợi ý "Ví dụ: GIAM20") còn trống hoặc chỉ có khoảng trắng và học viên bấm "Áp dụng", ô hiện lỗi "Vui lòng nhập mã giảm giá." (`checkout.voucher.emptyInput`) và app không gọi API.
  - **AC-120** Khi học viên nhập một mã hợp lệ và bấm "Áp dụng", app gọi Báo giá với `voucherCode` (đã bỏ khoảng trắng đầu cuối) và `seenVersionNo`, cập nhật tóm tắt, và hiện một trong ba câu: `checkout.voucher.appliedCombined` khi `discountApplied = COMBINED`; `checkout.voucher.appliedReplacedPromo` khi báo giá trước có ưu đãi tự động mà báo giá mới không còn; ngược lại `checkout.voucher.applied` ("Đã áp dụng mã {code} (-{discount}).").
  - **AC-121** Khi Báo giá trả `voucherNotBetter = true`, ô hiện thông tin "Mã {code} không giảm nhiều hơn ưu đãi tự động hiện tại nên hệ thống giữ nguyên mức giá tốt nhất." (`checkout.voucher.notBetter`) và tóm tắt giữ mức giá tốt nhất mà Báo giá trả.
  - **AC-122** Khi đã có mã trong Báo giá, ô có nút "Gỡ mã" (`checkout.voucher.remove`); bấm thì app gọi Báo giá không mã kèm `seenPrice` và `seenVersionNo`, ô trống lại và tóm tắt bỏ dòng mã giảm giá.
  - **AC-123** *(Từ chối)* Khi Báo giá trả 422 `VOUCHER_INVALID`, `VOUCHER_EXPIRED`, `VOUCHER_EXHAUSTED`, `VOUCHER_USER_LIMIT`, `VOUCHER_NOT_APPLICABLE` hoặc `VOUCHER_NOT_ELIGIBLE`, ô hiện câu của khóa `checkout.voucher.errors.<code>` (bộ đề với `VOUCHER_NOT_APPLICABLE` dùng `VOUCHER_NOT_APPLICABLE_BUNDLE`), tóm tắt giữ nguyên báo giá trước, mã đã gõ vẫn còn trong ô; gõ thêm ký tự thì lỗi biến mất.
  - **AC-124** *(Giới hạn)* Khi học viên đã kiểm 10 mã trong 1 phút (hoặc 50 mã trong 24 giờ) và kiểm thêm, máy chủ trả 429 và ô hiện "Bạn kiểm tra mã quá nhanh. Vui lòng thử lại sau ít phút." (`checkout.voucher.errors.RATE_LIMITED`); lỗi khác không nhận ra hiện `checkout.voucher.errors.generic`.
  - **AC-125** *(Giới hạn)* Khi một lệnh áp hoặc gỡ mã đang chạy, hoặc màn đang tạo đơn, đang có đơn đang kiểm tra (R-36) hay sản phẩm tạm ngừng bán, ô nhập và các nút của ô mã bị khóa; bấm "Áp dụng" hai lần nhanh chỉ gửi một Báo giá.

### R-36 Quyền đang có và đơn đang kiểm tra trên màn mua

- Nguồn: `checkout-view.tsx:224-229,246-280,444-620`.
- Áp dụng cho: học viên đã có lượt, đang có lượt làm dở, hoặc có đơn đang chờ cấp cho sản phẩm này.
- Tiêu chí chấp nhận:
  - **AC-126** Khi `exam-access/query` cho sản phẩm trả `state = CHECKING` hoặc `hasPendingFulfillment = true`, màn hiện dải "Đang kiểm tra đơn {orderCode}" (`checkout.accessBanner.checkingTitle`), câu `checkout.accessBanner.checkingDescription` và nút "Xem trạng thái thanh toán"; bấm thì mở màn Kết quả thanh toán của `pendingOrderCode` (không có mã thì mở danh sách đơn); nút thanh toán bị khóa.
  - **AC-127** Khi trả `CAN_START` hoặc `IN_PROGRESS`, màn hiện dải `checkout.accessBanner.existingTitle` (hoặc `existingInProgressTitle` khi có lượt làm dở) với số lượt còn, nút "Vào bài thi" hoặc "Tiếp tục" (mở màn chuẩn bị hoặc màn làm bài, xem mục 6.3) và nút "Mua thêm lượt" cuộn tới khối tóm tắt; nút thanh toán vẫn bấm được.
  - **AC-128** Khi sản phẩm là bộ đề, màn hiện một dòng `bundle.detail.existingEntitlement` cho mỗi bài còn lượt và một dòng `bundle.checkout.pendingExamOrder` (hoặc `pendingExamOrderNoCode`) cho mỗi bài đang chờ.
  - **AC-129** *(Từ chối)* Khi `exam-access/query` lỗi hoặc trả quyền bị cấm, màn hiện dải "Chưa kiểm tra được quyền sử dụng" (`checkout.accessBanner.loadErrorTitle`) với câu `loadErrorDescription` và nút "Thử lại"; nút thanh toán bị khóa tới khi tải được quyền.

### R-37 Tạo đơn

- Nguồn: `checkout-view.tsx:307-354`, `features/orders/format.ts:40-54`, `OrderService.java:193-235,294-332`.
- Áp dụng cho: học viên; màn có Báo giá, không bị khóa theo R-32, R-36.
- Tiêu chí chấp nhận:
  - **AC-130** Khi học viên bấm nút thanh toán, app gửi `POST /api/v1/me/orders` có header `Idempotency-Key` (UUID mới) và thân `{code, expectedAccountId = id tài khoản đang đăng nhập, expectedAmountDue = finalPrice, expectedVersionNo = versionNo, voucherCode}` (chỉ có `voucherCode` khi Báo giá có mã); nút ở trạng thái đang xử lý.
  - **AC-131** Khi phản hồi `next = REDIRECT`, app mở Trang thanh toán theo R-39.
  - **AC-132** Khi phản hồi `next = FULFILLED` (đơn 0 đồng đã cấp), app mở màn Kết quả thanh toán của đơn đó ở trạng thái đã cấp (AC-158); khi `next = CHECKING`, app mở màn đó ở trạng thái chờ xác nhận (R-43). Cả hai trường hợp không mở Trang thanh toán.
  - **AC-133** *(Giới hạn)* Khi học viên bấm nút thanh toán hai lần nhanh, app chỉ gửi một yêu cầu tạo đơn.
  - **AC-134** *(Giới hạn)* Khi lệnh tạo đơn hỏng vì mất mạng, quá giờ, 408, 429 hoặc 5xx và học viên bấm lại, app gửi lại cùng Khóa chống gửi trùng; máy chủ trả đúng đơn đã tạo nếu lần trước đã tới nơi, không sinh đơn thứ hai. Sau phản hồi thành công, hoặc lỗi 4xx khác 408 và 429, hoặc khi áp hay gỡ mã, lần bấm sau dùng khóa mới.
  - **AC-135** *(Giới hạn)* Khi học viên đã có một đơn chờ thanh toán của cùng sản phẩm còn hạn và tạo đơn lại với cùng giá, phản hồi trả cùng mã đơn cũ kèm `paymentUrl` mới; khi giá khác, phản hồi trả mã đơn mới và đơn cũ chuyển `REPLACED` (thấy ở danh sách đơn, R-48).

### R-38 Lỗi khi tạo đơn

- Nguồn: `checkout-view.tsx:355-442`, `OrderController.java:57-60`.
- Áp dụng cho: học viên bấm thanh toán.
- Tiêu chí chấp nhận:
  - **AC-136** *(Từ chối)* Khi lệnh tạo đơn trả 422 `VOUCHER_*`, khối tóm tắt hiện câu `checkout.voucher.errors.<code>` (quy tắc bộ đề như AC-123), ô mã trống lại, app gọi lại Báo giá không mã và tóm tắt cập nhật theo.
  - **AC-137** *(Từ chối)* Khi lệnh tạo đơn trả 422 `PAYMENT_ATTEMPTS_EXCEEDED`, khối tóm tắt hiện "Đơn đã vượt quá số lần tạo giao dịch thanh toán. Vui lòng tạo đơn mới." (`orders.errors.PAYMENT_ATTEMPTS_EXCEEDED`), như web không có nút thêm ở màn này; lối ra là hủy đơn chờ đó ở Đơn hàng (AC-709).
  - **AC-138** *(Từ chối)* Khi lệnh tạo đơn trả 429, khối tóm tắt hiện "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại." (`checkout.summary.errorRateLimited`).
  - **AC-139** *(Từ chối)* Khi lệnh tạo đơn trả 409 `ACCOUNT_CHANGED`, app tải lại tài khoản đang đăng nhập; nếu là tài khoản khác tài khoản lúc mở màn, màn hiện dải "Tài khoản đăng nhập đã thay đổi" (`checkout.account.changedTitle`) với câu `changedDescription` và nút "Tiếp tục với {username}"; bấm thì màn tải lại quyền và Báo giá cho tài khoản mới; nếu cùng tài khoản thì hiện lỗi chung của AC-140.
  - **AC-140** *(Từ chối)* Khi lệnh tạo đơn trả lỗi khác (`IDEMPOTENCY_KEY_REUSED`, 5xx, mất mạng, mã không nhận ra), khối tóm tắt hiện "Không thể tạo đơn hàng. Vui lòng thử lại." (`checkout.summary.errorGeneric`), màn giữ nguyên báo giá và nút bấm lại được.
  - **AC-141** *(Từ chối)* Khi lệnh tạo đơn trả 401 mà app làm mới phiên không được, app về màn Đăng nhập theo luật phiên của app; không có đơn nào được hiện là đã tạo.

### R-39 Mở Trang thanh toán

- Nguồn: D-3; `checkout-view.tsx:350-352`, `orders-view.tsx:191-213`, `MockVnpayGateway.java:64`; hành vi nền tảng ở mục "Hiện trạng".
- Áp dụng cho: mọi lệnh trả `paymentUrl` (tạo đơn, thanh toán tiếp).
- Tiêu chí chấp nhận:
  - **AC-142** Khi app nhận `paymentUrl`, app mở URL đó trong Phiên trình duyệt thanh toán (không phải WebView tự vẽ trong app), khai với phiên này Liên kết trả về (host web học viên của bản build, đường dẫn `/checkout/result/app`) là liên kết phải trao lại cho app, và ghi nhận Đơn đang chờ kết quả gồm mã đơn và thời điểm mở. `paymentUrl` tuyệt đối được mở nguyên trạng, không so host với backend hay web (cổng thật trỏ tới tên miền VNPAY); chỉ `paymentUrl` tương đối (chế độ giả lập) được ghép với địa chỉ backend của bản build.
  - **AC-143** *(Từ chối)* Khi hệ điều hành không mở được Trang thanh toán, app ở lại màn hiện tại, hiện lỗi chung của màn đó (`checkout.summary.errorGeneric` hoặc `orders.errors.generic`) và đơn vẫn ở trạng thái chờ thanh toán trong danh sách đơn.

### R-40 Quay lại app bằng Liên kết trả về

Liên kết trả về tới app theo một trong ba đường: Phiên trình duyệt thanh toán trao lại khi trang đi tới nó; hệ điều hành mở app từ liên kết (App Links, universal link); hoặc học viên chạm nút "Mở ứng dụng Thi360" trên Trang dự phòng. Đường nào tới thì app xử lý như nhau. Đường nào không tới thì R-41 vẫn cho học viên thấy kết quả.

- Nguồn: D-3 và quyết định của đợt khung và xác thực (khóa app, phiên); `checkout-result-view.tsx:37-45,103-151`; hành vi nền tảng ở mục "Hiện trạng"; mục "Thay đổi backend hoặc web cần có".
- Áp dụng cho: lần thanh toán bắt đầu từ app (Liên kết trả về có đường dẫn `/checkout/result/app`); máy đã cài app.
- Tiêu chí chấp nhận:
  - **AC-144** Khi app nhận một Liên kết trả về hợp lệ mang tham số `vnp_*` (từ Phiên trình duyệt thanh toán hoặc do hệ điều hành mở app bằng liên kết; trong kiểm thử tự động là đưa liên kết vào app như hệ điều hành đưa), app mở màn Kết quả thanh toán, gửi đúng các tham số `vnp_*` của liên kết tới `GET /api/v1/public/payments/vnpay/return`, rồi đọc đơn có mã `orderCode` trong phản hồi và hiện kết quả theo R-42; Phiên trình duyệt thanh toán không còn che app.
  - **AC-706** *(Kiểm trên thiết bị thật sau khi merge)* Khi chạy bản `demo` có Apple Team ID thật và vân tay Android đã khai trong tệp xác minh, trên một iPhone iOS 18 và một máy Android thật đã cài app, học viên mua một bài, xong trên Trang thanh toán (cổng giả lập hoặc sandbox VNPAY, chọn thành công rồi lặp lại với hủy giao dịch), thì app tự hiện màn Kết quả thanh toán của đơn đó mà học viên không phải chạm gì thêm; ghi lại cho từng nền tảng đường nào đã đưa liên kết về (phiên trình duyệt trao lại hay hệ điều hành mở app). Nếu trên một nền tảng app không tự hiện, ghi lại và kiểm AC-708 trên máy đó.
  - **AC-145** *(Từ chối)* Khi liên kết không phải `https`, không đúng host web học viên của bản build, hoặc đường dẫn khác `/checkout/result/app` (kể cả `/checkout/result` của web), app không mở màn Kết quả thanh toán (bản `dev` iOS chấp nhận thêm `http` như liên kết đặt lại mật khẩu).
  - **AC-146** Khi app đang khóa sinh trắc học lúc nhận liên kết, app hiện màn khóa trước; mở khóa xong thì hiện màn Kết quả thanh toán của liên kết đó.
  - **AC-147** Khi app không có phiên lúc nhận liên kết (phiên đã hết), app hiện màn Đăng nhập; đăng nhập xong thì hiện màn Kết quả thanh toán của liên kết đó; nếu đơn không thuộc tài khoản vừa đăng nhập thì hiện trạng thái của AC-157.
  - **AC-148** *(Giới hạn)* Khi app đã hiện màn Kết quả thanh toán cho Đơn đang chờ kết quả theo R-41 rồi mới nhận liên kết của cùng đơn, app cập nhật màn đó theo liên kết, không mở thêm một màn thứ hai; nhận cùng một liên kết hai lần cũng chỉ có một màn.
  - **AC-149** *(Giới hạn)* Khi app chưa chạy và được mở từ đầu bằng một Liên kết trả về (trong kiểm thử tự động: khởi động app kèm liên kết đó), app đi thẳng tới màn Kết quả thanh toán như AC-144 (sau màn khóa nếu đã bật), không dừng ở mục Trang chủ.

### R-41 Quay lại app khi liên kết không mở app

App luôn biết kết quả thật bằng cách đọc đơn, vì việc cấp bài thi do IPN quyết định. Trang dự phòng luôn dùng được, dù nền tảng có đưa Liên kết trả về tới app hay không.

- Nguồn: D-3; `VnpayIpnController.java:42-48`, `OrderService.java:91,650-660`; hành vi nền tảng ở mục "Hiện trạng".
- Áp dụng cho: có Đơn đang chờ kết quả; Trang dự phòng cho mọi Liên kết trả về mở trong trình duyệt.
- Tiêu chí chấp nhận:
  - **AC-150** Khi học viên quay lại app bằng bất kỳ đường nào mà không kèm Liên kết trả về (đóng Phiên trình duyệt thanh toán, chuyển ứng dụng, mở app từ màn hình chính), app mở màn Kết quả thanh toán cho Đơn đang chờ kết quả, đọc `GET /api/v1/me/orders/{code}` ngay và hiện theo R-42: đơn `FULFILLED` thì đã cấp; lần thanh toán mới nhất `FAILED` thì thất bại; đơn `PAID` thì chờ xác nhận; đơn `PENDING_PAYMENT` với lần thanh toán mới nhất `CREATED` hoặc `UNKNOWN` thì hiện trạng thái "chưa nhận được kết quả" có nút "Tiếp tục thanh toán" (`orders.actions.continuePayment`, gửi lệnh theo R-44) và "Xem chi tiết đơn" (`checkout.result.viewOrder`), câu chữ theo câu hỏi A-79, đồng thời Hỏi lại mỗi 2 giây tối đa 60 giây và chuyển sang trạng thái tương ứng nếu đơn đổi.
  - **AC-707** *(Giới hạn)* Khi màn đang ở trạng thái "chưa nhận được kết quả" và đã Hỏi lại đủ 60 giây mà lần thanh toán mới nhất vẫn `CREATED` hoặc `UNKNOWN`, app ngừng Hỏi lại, màn giữ trạng thái đó cùng nút "Tiếp tục thanh toán" và "Xem chi tiết đơn", không đổi sang câu `checkingTimeoutBody` ("…không cần thanh toán lại") của AC-164; trạng thái chờ xác nhận của R-43 chỉ dùng khi đơn `PAID` hoặc khi màn mở từ Liên kết trả về mà xác minh trả `paymentState` khác `FAILED` (như web).
  - **AC-151** Khi trình duyệt mở Trang dự phòng với các tham số `vnp_*` hợp lệ (kiểm tự động bằng test của web), trang gọi `GET /api/v1/public/payments/vnpay/return` với đúng các tham số đó, hiện kết quả đã xác minh theo `paymentState` (`SUCCEEDED`: tiêu đề "Thanh toán thành công" `checkout.result.guestSuccessHeading`; `FAILED`: "Thanh toán chưa thành công" `guestFailedHeading` kèm lý do `orders.vnpayReasons.<vnp_ResponseCode>`; còn lại: "Đang chờ xác nhận thanh toán" `guestCheckingHeading`) kèm mã đơn, câu nhắc "Quay lại ứng dụng Thi360 để xem bài thi của bạn." và nút "Mở ứng dụng Thi360" có đích là chính URL đang mở (cùng host, đường dẫn `/checkout/result/app` và toàn bộ tham số); trang không có nút đăng nhập web và không đọc đơn. Chữ ký sai hiện "Không xác minh được phản hồi thanh toán" (`invalidSignatureHeading`), không tìm thấy đơn hiện `orders.notFound.title`, cả hai vẫn có câu nhắc và nút mở app.
  - **AC-708** *(Kiểm trên thiết bị thật sau khi merge)* Khi Trang dự phòng đang mở trong trình duyệt của một iPhone iOS 18 và một máy Android thật đã cài bản `demo` (Apple Team ID thật, vân tay đã khai), học viên chạm "Mở ứng dụng Thi360" thì app mở ở màn Kết quả thanh toán của đơn đó như AC-144; nếu nền tảng vẫn mở liên kết trong trình duyệt, ghi lại, và học viên tự chuyển về app thì xảy ra AC-150.
  - **AC-152** *(Giới hạn)* Khi app bị tắt hẳn lúc Trang thanh toán đang mở, rồi học viên tự mở lại app (không qua liên kết) trong vòng 15 phút kể từ lúc mở Trang thanh toán, app mở màn Kết quả thanh toán cho đơn đó như AC-150 (sau màn khóa nếu đã bật); quá 15 phút thì app mở như bình thường và đơn chỉ còn thấy ở danh sách đơn.
  - **AC-153** *(Giới hạn)* Đơn đang chờ kết quả bị bỏ khi màn Kết quả thanh toán hiện đã cấp, thất bại hoặc đã đóng, khi học viên rời màn đó, hoặc khi đăng xuất; đăng nhập bằng tài khoản khác không mở kết quả đơn của tài khoản trước.

### R-42 Màn Kết quả thanh toán

- Nguồn: `checkout-result-view.tsx:47-50,111-172,260-738`.
- Áp dụng cho: học viên; mở từ R-37, R-36, R-40, R-41.
- Tiêu chí chấp nhận:
  - **AC-154** Khi đang xác minh liên kết hoặc đang tải đơn lần đầu, màn hiện khung chờ.
  - **AC-155** *(Từ chối)* Khi xác minh liên kết trả 422 `VNPAY_SIGNATURE_INVALID` hoặc 400, màn hiện "Không xác minh được phản hồi thanh toán" (`checkout.result.invalidSignatureHeading`), câu `invalidSignatureBody`, nút "Xem đơn của tôi" (`viewOrders`) và "Vào Bài thi của tôi" (`viewMyExams`); app không đọc đơn.
  - **AC-156** *(Từ chối)* Khi xác minh trả 404 `ORDER_NOT_FOUND`, màn hiện "Không tìm thấy đơn hàng" (`orders.notFound.title`), câu `checkout.result.verifyNotFoundBody` và nút "Xem đơn của tôi"; khi xác minh lỗi mạng hoặc 5xx, màn hiện "Không thể kiểm tra kết quả thanh toán" (`loadErrorHeading`), câu `loadErrorBody`, nút "Thử lại" và "Xem đơn của tôi".
  - **AC-157** *(Từ chối)* Khi đọc đơn trả 404, màn hiện "Không tìm thấy đơn trong tài khoản đang đăng nhập" (`checkout.result.notOwnedHeading`) với câu `notOwnedBody` có mã đơn và tên đăng nhập, nút "Đăng nhập bằng tài khoản khác" (`orders.notFound.otherAccount`) và "Xem đơn của tôi"; khi đọc đơn lỗi khác và chưa có dữ liệu, màn hiện `loadErrorHeading`, `loadErrorBody` và "Thử lại".
  - **AC-158** Khi đơn `FULFILLED`, màn hiện "Đã cấp bài thi" (`successHeading`), nhãn "Đã cấp bài thi", câu `successFreeBody` khi `amountDueVnd = 0` và `voucherDiscountVnd > 0` (ngược lại `successBody`), tên sản phẩm, mã đơn, số tiền ("0 đ (đã áp dụng voucher)" với đơn miễn phí nhờ voucher), giờ cấp dạng `dd/MM/yyyy HH:mm` giờ Việt Nam, nút "Vào Bài thi của tôi" (mở mục Bài thi của tôi) và "Xem chi tiết đơn"; app không Hỏi lại nữa.
  - **AC-159** Khi đơn `PENDING_PAYMENT` và lần thanh toán mới nhất (theo `attemptSeq`) là `FAILED`, hoặc xác minh trả `paymentState = FAILED` cho đúng `vnp_TxnRef` của lần mới nhất, màn hiện "Thanh toán chưa thành công" (`failedHeading`), câu `failedBody`, lý do theo `orders.vnpayReasons.<responseCode>` (mã 24 hiện "Bạn đã hủy giao dịch trên VNPAY."; mã không có trong danh sách hiện `vnpayReasons.generic`), dòng "Mã giao dịch: {txnRef} · {số tiền}", nút "Thanh toán lại" (R-44) và "Xem chi tiết đơn"; app không Hỏi lại.
  - **AC-160** *(Giới hạn)* Khi liên kết mang `vnp_TxnRef` của một lần thanh toán cũ mà đơn đã có lần thanh toán mới hơn chưa thất bại, màn không hiện thất bại theo liên kết mà hiện theo trạng thái đơn.
  - **AC-161** Khi đơn `EXPIRED`, `CANCELLED`, `REPLACED` hoặc `REFUNDED`, màn hiện "Đơn hàng đã đóng" (`closedHeading`), nhãn trạng thái của `orders.states`, câu `closedBody`; đơn `REPLACED` có `replacedByOrderCode` thì có nút "Xem đơn thay thế {code}" mở chi tiết đơn đó, ngược lại nút "Mua lại bài thi" (`buyAgain`) mở màn Xác nhận mua của `productCode`; luôn có nút "Xem đơn của tôi".

### R-43 Chờ xác nhận và Hỏi lại

- Nguồn: `checkout-result-view.tsx:34-35,168-204,229-233,637-703`.
- Áp dụng cho: đơn `PAID`, hoặc `PENDING_PAYMENT` chưa có lần thanh toán thất bại khi màn mở từ Liên kết trả về có `paymentState` khác `FAILED`, từ AC-132 (`next = CHECKING`) hoặc từ dải đơn đang kiểm tra (AC-126); trên màn Kết quả thanh toán. Đơn `PENDING_PAYMENT` mở từ R-41 không kèm liên kết theo AC-150 và AC-707.
- Tiêu chí chấp nhận:
  - **AC-162** Khi màn ở trạng thái chờ, màn hiện "Đang chờ xác nhận thanh toán" (`checkingHeading`), nhãn "Đang kiểm tra", câu `checkingPending` có mã đơn, tên sản phẩm, mã đơn và số tiền, nút "Kiểm tra lại ngay", "Xem chi tiết đơn", "Vào Bài thi của tôi"; app Hỏi lại mỗi 2 giây, lần sau chỉ bắt đầu khi lần trước xong.
  - **AC-163** Khi đơn đổi sang `FULFILLED`, thất bại hoặc đã đóng trong lúc Hỏi lại, màn chuyển ngay sang trạng thái của AC-158, AC-159 hoặc AC-161 và ngừng Hỏi lại.
  - **AC-164** *(Giới hạn)* Khi đã Hỏi lại đủ 60 giây mà đơn vẫn chờ, app ngừng Hỏi lại, màn đổi sang "Đang kiểm tra thanh toán" (`checkingTimeoutHeading`), câu `checkingTimeoutBody` và câu `checkingTimeoutSupport` có liên hệ hỗ trợ (trống thì `landing.support.pending`); bấm "Kiểm tra lại ngay" thì app đọc đơn ngay và Hỏi lại thêm tối đa 60 giây.
  - **AC-165** *(Từ chối)* Khi một lần Hỏi lại lỗi trong 60 giây, màn giữ dữ liệu đang có, hiện "Tạm thời không kiểm tra được trạng thái mới nhất. Hệ thống vẫn đang tiếp tục thử lại." (`pollErrorNotice`) và tiếp tục Hỏi lại; lỗi sau khi hết 60 giây hiện `pollErrorTimeoutNotice`.
  - **AC-166** *(Giới hạn)* Khi app vào nền trong lúc Hỏi lại, app ngừng gọi API; quay lại app thì app đọc đơn ngay và chỉ Hỏi lại tiếp nếu chưa hết 60 giây tính từ lúc bắt đầu.

### R-44 Thanh toán lại một đơn đang chờ

- Nguồn: `checkout-result-view.tsx:206-227`, `orders-view.tsx:191-213,311-327`, `order-detail-view.tsx:162-183,245-261`, `format.ts:83-98`.
- Áp dụng cho: đơn `PENDING_PAYMENT` phương thức VNPAY; nút "Thanh toán lại" trên màn Kết quả thanh toán, "Tiếp tục thanh toán" ở danh sách và chi tiết đơn.
- Tiêu chí chấp nhận:
  - **AC-167** Khi học viên bấm nút, app gửi `POST /api/v1/me/orders/{code}/pay` có `Idempotency-Key`; nhận `paymentUrl` thì mở Trang thanh toán theo R-39 và đơn này thành Đơn đang chờ kết quả.
  - **AC-168** *(Giới hạn)* Khi bấm hai lần nhanh, hoặc bấm khi một thao tác khác trên cùng danh sách đang chạy, app chỉ gửi một yêu cầu; lỗi mạng, 408, 429, 5xx thì lần bấm lại dùng cùng khóa, các lỗi 4xx khác thì dùng khóa mới.
  - **AC-169** *(Từ chối)* Khi lệnh thanh toán tiếp lỗi, app đổi lỗi thành khóa chuỗi theo bảng của web: 429 hoặc `RATE_LIMITED` → `RATE_LIMITED`; 410 hoặc `PRODUCT_DISCONTINUED` → `PRODUCT_DISCONTINUED`; 404 `PRODUCT_NOT_FOUND` → `PRODUCT_NOT_FOUND`; 404 khác hoặc `ORDER_NOT_FOUND` → `ORDER_NOT_FOUND`; `ORDER_EXPIRED`, `ORDER_NOT_PENDING`, `PAYMENT_ATTEMPTS_EXCEEDED` giữ nguyên; mọi mã khác, kể cả mã không có trong `orders.errors`, mất mạng và 5xx → `generic` ("Không thực hiện được thao tác. Vui lòng thử lại hoặc tạo đơn mới."); màn hiện câu `orders.errors.<khóa>` rồi đọc lại đơn hoặc danh sách. Với `ORDER_EXPIRED` và `PAYMENT_ATTEMPTS_EXCEEDED` ở danh sách và chi tiết đơn có thêm nút "Tạo đơn mới" (`orders.actions.createNew`) mở màn Xác nhận mua của `productCode`; trên màn Kết quả thanh toán, như web, chỉ có câu lỗi cùng nút "Xem chi tiết đơn" sẵn có, từ đó có "Tạo đơn mới"; với `PRODUCT_DISCONTINUED` và `PRODUCT_NOT_FOUND` có nút "Về danh mục bài thi".
  - **AC-709** *(Giới hạn)* Khi một đơn `PENDING_PAYMENT` đã có 99 lần thanh toán (`PAYMENT_ATTEMPTS_EXCEEDED`), đơn vẫn hiện "Hủy đơn" ở danh sách và chi tiết; trong lúc đơn còn chờ và giá không đổi, mua lại cùng sản phẩm (kể cả qua "Tạo đơn mới") dùng lại chính đơn đó nên lệnh tạo đơn trả lại `PAYMENT_ATTEMPTS_EXCEEDED` (AC-137); sau khi học viên hủy đơn đó hoặc đơn hết hạn giữ giá, mua lại tạo đơn mới với mã mới. Đơn chờ này không khóa nút mua ở danh mục và chi tiết bài thi, vì dải "đang kiểm tra" chỉ tính đơn `PAID` (xem mục 6.1).
  - **AC-710** *(Giới hạn)* Khi đơn được tạo trên web rồi học viên bấm "Tiếp tục thanh toán" trong app, `paymentUrl` mới mang `vnp_ReturnUrl` là `{web-url}/checkout/result/app`; khi đơn được tạo trong app rồi thanh toán tiếp trên web, lần thanh toán mới mang `{web-url}/checkout/result`. Mỗi lần thanh toán giữ đường dẫn trả về đã chọn lúc tạo ra nó: hoàn tất trên Trang thanh toán của lần cũ thì về đường dẫn của lần cũ.
  - **AC-711** *(Giới hạn)* Khi học viên mở Trang thanh toán của lần 1, rồi trên thiết bị khác (hoặc trên web) thanh toán tiếp tạo lần 2, rồi trả tiền thành công ở lần 1, máy chủ tính lần 1: đơn sang `PAID` rồi `FULFILLED`, app hiện "Đã cấp bài thi" ở lần đọc đơn kế tiếp, và khối "Giao dịch thanh toán" hiện lần 1 "Thành công", lần 2 vẫn "Khởi tạo" (`orders.detail.payments.states.CREATED`). Liên kết trả về của lần 1 mang `vnp_TxnRef` của lần 1 và màn kết quả hiện theo trạng thái đơn (AC-160).
  - **AC-712** *(Giới hạn)* Khi cả hai lần thanh toán của cùng một đơn đều trả tiền thành công (hai thiết bị cùng trả), bài thi chỉ được cấp một lần (số lượt cộng đúng một lần `attemptsGranted`); trong chi tiết đơn, lần được tính hiện "Thành công", lần còn lại hiện "Thành công" kèm nhãn "Thu trùng" (`orders.detail.payments.duplicate`); app không có thao tác hoàn tiền và không hiện thêm thông báo nào ngoài nhãn đó.
  - **AC-713** *(Giới hạn)* Khi đơn đã `EXPIRED` hoặc `CANCELLED` (hoặc `REPLACED`) mà một lần thanh toán còn mở của nó vẫn trả tiền thành công trên VNPAY, máy chủ vẫn tính và cấp: lần đọc kế tiếp hiện đơn `PAID` rồi `FULFILLED` thay cho trạng thái đã đóng, và bài thi xuất hiện ở mục Bài thi của tôi.
  - **AC-170** *(Giới hạn)* Khi đơn đã được thanh toán hoặc hủy trên thiết bị khác và học viên bấm thanh toán tiếp, máy chủ trả 409 `ORDER_NOT_PENDING`, màn hiện "Đơn không còn ở trạng thái chờ thanh toán." và trạng thái đơn hiện theo dữ liệu vừa đọc lại.

### R-45 Danh sách đơn hàng

- Nguồn: giả định A-1 (Đơn hàng nằm trong Tài khoản); `features/orders/orders-view.tsx:104-119,140-165,256-309,374-617`, `format.ts:25-38`.
- Áp dụng cho: học viên; mục Tài khoản → Đơn hàng.
- Tiêu chí chấp nhận:
  - **AC-171** Khi mở Đơn hàng, app gọi `GET /api/v1/me/orders?page=0&size=20` và hiện tiêu đề "Đơn của tôi" (`orders.pageTitle`), câu `orders.subtitle` có tên đăng nhập, và mỗi đơn một dòng: mã đơn, tên sản phẩm, nhãn "Bộ đề" khi `productType = BUNDLE`, ngày tạo dạng `dd/MM/yyyy HH:mm` giờ Việt Nam, số tiền, nhãn trạng thái `orders.states.<state>`; đơn mới nhất ở đầu.
  - **AC-172** Khi đơn có `paymentMethod = FREE` hoặc `amountDueVnd = 0`, số tiền ghi "0 đ (đã áp dụng voucher)" nếu `voucherDiscountVnd > 0`, ngược lại "0 đ"; đơn trả tiền ghi số tiền phải trả.
  - **AC-173** Khi đơn `PENDING_PAYMENT` có `expiresAt`, dòng hiện "Giữ giá tới {HH:mm}" và hai nút "Tiếp tục thanh toán" (R-44), "Hủy đơn" (R-47); đơn `PAID` hiện "Đã nhận tiền, đang cấp bài thi" và câu `orders.paidCheckingSupport` (không có liên hệ thì `paidCheckingSupportPending`); đơn `FULFILLED` có nút "Vào Bài thi của tôi". Bấm vào dòng mở chi tiết đơn.
  - **AC-174** *(Giới hạn)* Khi học viên chưa có đơn nào, app hiện "Bạn chưa có đơn nào" (`orders.emptyTitle`), câu `emptyDescription` và nút "Khám phá bài thi" mở mục Trang chủ.
  - **AC-175** *(Giới hạn)* Khi có hơn 20 đơn, cuộn tới cuối danh sách thì app tải trang kế tiếp 20 đơn và nối vào; ở trang cuối (`page + 1 = totalPages`) app không gọi thêm.
  - **AC-176** Khi đang tải lần đầu, app hiện khung chờ dạng dòng; *(Từ chối)* khi tải lần đầu lỗi, app hiện "Không tải được danh sách đơn" (`orders.errorTitle`), câu "Kiểm tra kết nối mạng rồi thử lại." và nút "Thử lại".
  - **AC-177** *(Từ chối)* Khi đã có dữ liệu mà tải lại lỗi, app giữ danh sách đang có và hiện dải "Dữ liệu chưa được cập nhật." với câu `orders.staleDescription` có giờ tải lần cuối (`HH:mm`) và nút "Thử lại".
  - **AC-178** Khi học viên quay lại Đơn hàng từ màn khác hoặc app trở lại từ nền, app tải lại trang đầu để trạng thái đơn mới (ví dụ đơn vừa hết hạn sau 15 phút) hiện ra.

### R-46 Chi tiết đơn hàng

- Nguồn: `features/orders/order-detail-view.tsx:222-243,326-840`, `format.ts:100-182,198-206`.
- Áp dụng cho: học viên; đơn thuộc tài khoản đang đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-179** Khi mở một đơn, app gọi `GET /api/v1/me/orders/{code}` và hiện tiêu đề "Đơn {code}", nhãn trạng thái, nhãn phương thức ("VNPAY", "Miễn phí", hoặc "Miễn phí nhờ voucher"), nhãn "Bộ đề" nếu có; khối "Thông tin đơn hàng" gồm tài khoản mua (tên đăng nhập · họ tên), sản phẩm (tên · mã), phiên bản lúc mua `v{n}` (không có thì "—"), giá gốc, khuyến mãi tự động (khi > 0), mã giảm giá và số giảm (khi có), số tiền phải trả, số lượt được cấp, tạo lúc, giữ giá tới, thanh toán lúc, cấp bài thi lúc (trường nào trống thì không hiện), quyền lợi (xem kết quả, AI, điểm mạnh điểm yếu, ôn luyện, ghi chú) và danh sách bài trong đơn khi là bộ đề hoặc có hơn một bài.
  - **AC-180** Khi đơn có giao dịch, khối "Giao dịch thanh toán" liệt kê mỗi lần: số lần hai chữ số (`01`), mã tham chiếu, ngân hàng · loại thẻ (trống thì "—"), số tiền, nhãn trạng thái `orders.detail.payments.states.<state>`, nhãn "Thu trùng" khi `duplicate`, lý do `orders.vnpayReasons.<responseCode>` khi giao dịch chưa thành công và có mã, và thời điểm; không có giao dịch thì hiện "Đơn miễn phí, không phát sinh giao dịch VNPAY." (đơn FREE) hoặc "Chưa có giao dịch thanh toán.".
  - **AC-181** Khi đơn có lịch sử, khối "Lịch sử xử lý" liệt kê mỗi bước: "{từ} → {tới}" (bước đầu ghi "Tạo đơn"), người thực hiện `orders.detail.history.actors.<actor>` (không nhận ra thì "Hệ thống"), lý do theo `orders.detail.history.reasons.<mã>` với phần sau dấu `:` thay vào `{ref}` (mã không nhận ra hiện `reasons.unknown`), thời điểm; không có thì "Chưa có lịch sử xử lý.".
  - **AC-182** Khi đơn `PENDING_PAYMENT`, màn có "Tiếp tục thanh toán" và "Hủy đơn"; đơn `FULFILLED` có "Vào Bài thi của tôi"; đơn `PAID` hiện dải `orders.paidChecking` cùng câu hỗ trợ như AC-173.
  - **AC-183** *(Từ chối)* Khi API trả 404, màn hiện "Không tìm thấy đơn hàng", câu `orders.notFound.description`, nút "Đăng nhập bằng tài khoản khác" và "Quay lại đơn của tôi"; khi lỗi khác lúc chưa có dữ liệu, màn hiện "Không tải được chi tiết đơn hàng" và "Thử lại"; khi đã có dữ liệu mà tải lại lỗi, màn giữ dữ liệu và hiện dải "Dữ liệu chưa được cập nhật." như AC-177.
  - **AC-184** Khi màn đủ rộng cho hai khung (Android ≥ 840dp, iOS độ rộng regular), Đơn hàng hiện danh sách bên trái và chi tiết đơn đang chọn bên phải; chọn đơn khác thì khung phải đổi, danh sách giữ vị trí cuộn; sau khi hủy hoặc thanh toán tiếp từ khung phải, dòng tương ứng ở khung trái cập nhật trạng thái. Ở một cột, chọn đơn mở màn chi tiết riêng có nút quay lại.

### R-47 Hủy đơn

- Nguồn: `orders-view.tsx:215-254,622-641`, `order-detail-view.tsx:185-220,845-864`, `OrderService.java:620-648`.
- Áp dụng cho: đơn `PENDING_PAYMENT`.
- Tiêu chí chấp nhận:
  - **AC-185** Khi học viên bấm "Hủy đơn", app hiện hộp xác nhận "Hủy đơn {code}?" với câu "Đơn chờ thanh toán {code} sẽ bị hủy. Mã giảm giá đã dùng cho đơn này được trả lại.", nút "Hủy đơn" (kiểu nguy hiểm) và "Giữ đơn"; bấm "Giữ đơn" thì đóng hộp, không gọi API.
  - **AC-186** Khi học viên xác nhận, app gửi `POST /api/v1/me/orders/{code}/cancel` có `Idempotency-Key`; thành công thì đóng hộp, hiện thông báo ngắn "Đã hủy đơn {code}." và đơn hiện "Đã hủy" ở cả danh sách và chi tiết.
  - **AC-187** *(Từ chối)* Khi lệnh hủy trả 409 `ORDER_NOT_PENDING` (đơn vừa được thanh toán, hết hạn hoặc hủy ở nơi khác) hoặc lỗi khác, hộp vẫn mở, hiện câu `orders.errors.<khóa>` trong hộp với khóa đổi theo cùng bảng của AC-169 (mã không có trong `orders.errors`, mất mạng, 5xx → `generic`), app đọc lại đơn; bấm xác nhận lại dùng cùng khóa khi lỗi là mất mạng, 408, 429 hoặc 5xx, các lỗi 4xx khác dùng khóa mới.
  - **AC-188** *(Giới hạn)* Khi lệnh hủy đang chạy, hai nút của hộp và nút "Tiếp tục thanh toán" bị khóa, chạm ra ngoài không đóng hộp; bấm xác nhận hai lần chỉ gửi một yêu cầu.

### R-48 Đơn thay thế, hết hạn, đã hủy và hoàn tiền

Các trạng thái này chỉ để xem; học viên không có thao tác nào ngoài những gì nêu ở đây.

- Nguồn: `order-detail-view.tsx:421-431`, `checkout-result-view.tsx:705-738`, `format.ts:56-64`, `orders.states` trong `vi.json:1243-1251`.
- Áp dụng cho: đơn `REPLACED`, `EXPIRED`, `CANCELLED`, `REFUNDED`.
- Tiêu chí chấp nhận:
  - **AC-189** Khi đơn `REPLACED` có `replacedByOrderCode`, chi tiết đơn có nút "Xem đơn thay thế {code}" mở chi tiết đơn mới; lịch sử có dòng "Đã thay thế bằng đơn {mã}".
  - **AC-190** Khi đơn `EXPIRED`, `CANCELLED`, `REFUNDED` hoặc `REPLACED`, danh sách và chi tiết hiện nhãn "Hết hạn", "Đã hủy", "Đã hoàn tiền" (màu thông tin) hoặc "Đã thay thế" (màu trung tính), không có nút thanh toán hay hủy; màn Kết quả thanh toán của các đơn này hiện như AC-161.
  - **AC-191** *(Giới hạn)* Khi một đơn chờ quá hạn giữ giá (15 phút) trong lúc học viên đang xem nó, lần đọc lại kế tiếp (Hỏi lại, kéo làm mới, quay lại màn) hiện "Hết hạn"; bấm "Tiếp tục thanh toán" trên dữ liệu cũ trả `ORDER_EXPIRED` như AC-169.

### 6.3 Bài thi của tôi và Lịch sử

### R-49 Mục Bài thi của tôi thay màn giữ chỗ

Mục Bài thi của tôi hiện danh sách thẻ của tài khoản đang đăng nhập, tải bằng `GET /api/v1/me/exams` mỗi lần mục được mở hoặc quay lại từ màn khác. Đầu màn có tiêu đề "Bài thi của tôi" (`home.pageTitle`) và dòng "Chào {name}. Đây là các bài thi bạn đã mua và số lượt còn lại." (`home.subtitle`), `{name}` là họ tên đã bỏ khoảng trắng hai đầu, trống thì dùng tên đăng nhập. App không có thông báo cho tài khoản quản trị ở mục này: tài khoản khác học viên không vào được khung app (quyết định của đợt khung và xác thực).

- Nguồn: giả định A-1; `my-exams-view.tsx:37-42,50-53`; `student-home-view.tsx:80-82`; `vi.json:929,941`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-192** Khi học viên mở mục Bài thi của tôi thì app gọi đúng một lần `GET /api/v1/me/exams` kèm `Authorization: Bearer`, màn không còn dòng "Chưa có nội dung" và hiện tiêu đề cùng dòng chào có họ tên của tài khoản.
  - **AC-193** Khi tài khoản có họ tên trống và tên đăng nhập `hv01`, mở mục thì dòng chào là "Chào hv01. Đây là các bài thi bạn đã mua và số lượt còn lại."
  - **AC-194** Khi đang ở mục Bài thi của tôi đã có dữ liệu, người dùng sang mục khác (hoặc mở màn làm bài, kết quả, thanh toán) rồi quay lại thì app gọi lại `GET /api/v1/me/exams` và vẫn hiện danh sách cũ trong lúc chờ, không hiện khung xương.
  - **AC-195** Khi ngôn ngữ app là English thì mọi chuỗi của mục lấy từ cùng khóa trong `en.json` (ví dụ `home.examCount` hiện "1 exam" / "3 exams").

### R-50 Tải, lỗi, mất mạng và dữ liệu cũ ở Bài thi của tôi

- Nguồn: `my-exams-view.tsx:15-23,55-124`; `query-provider.tsx:30-34`; `vi.json:740,743-747,914,917,933-935`.
- Áp dụng cho: học viên; mọi trạng thái dữ liệu.
- Tiêu chí chấp nhận:
  - **AC-196** Khi lần tải đầu đang chờ phản hồi thì màn hiện khung xương ba thẻ, có nhãn trợ năng "Đang tải" (`common.loading`).
  - **AC-197** Khi lần tải đầu nhận lỗi mạng hoặc 5xx thì app tự gọi lại tối đa 2 lần; nếu cả 3 lần đều lỗi thì hiện khối "Không tải được danh sách bài thi" (`home.errorTitle`), "Kiểm tra kết nối mạng rồi thử lại." (`home.errorDescription`) và nút "Thử lại" (`common.retry`); bấm nút thì gọi lại `GET /api/v1/me/exams`.
  - **AC-198** *(Từ chối)* Khi phản hồi là 4xx khác 401 thì app không tự gọi lại và hiện khối lỗi của AC-197.
  - **AC-199** Khi đã có danh sách và lần tải lại thất bại thì danh sách cũ giữ nguyên, phía trên có dải cảnh báo "Dữ liệu chưa được cập nhật." (`home.staleTitle`) kèm "Không tải lại được danh sách; đang hiện bản đã tải lúc {time}." (`home.staleDescription`), `{time}` là giờ:phút 24 giờ theo giờ hiển thị của lần tải thành công gần nhất; không biết giờ thì dùng `home.staleDescriptionNoTime`.
  - **AC-200** Khi bấm "Thử lại" trên dải cảnh báo thì nút ở trạng thái đang xử lý tới khi có phản hồi và không gửi được lần hai; thành công thì dải cảnh báo biến mất và danh sách mới thay danh sách cũ.
  - **AC-201** Khi máy không có mạng và chưa có dữ liệu, mở mục thì hiện "Không có kết nối" (`common.offline.title`), "Kiểm tra kết nối mạng rồi thử lại." (`common.offline.description`) và nút "Thử lại"; khi máy không có mạng mà đã có dữ liệu thì hiện dải cảnh báo của AC-199 trên danh sách cũ.
  - **AC-202** *(Giới hạn)* Khi app bị tắt hẳn rồi mở lại thì mục không hiện dữ liệu của lần chạy trước; nó tải mới theo AC-196.

### R-51 Chưa có bài thi

- Nguồn: `my-exams-view.tsx:126-139`; `vi.json:912-913,919`.
- Áp dụng cho: học viên chưa có thẻ nào.
- Tiêu chí chấp nhận:
  - **AC-203** Khi `items` rỗng thì màn hiện "Bạn chưa có bài thi nào" (`home.emptyTitle`), "Chọn bài thi phù hợp trong danh mục để bắt đầu luyện tập." (`home.emptyDescription`) và nút "Khám phá bài thi" (`home.exploreExams`); bấm nút thì app chuyển sang mục Trang chủ (danh mục bài thi).

### R-52 Thứ tự các nhóm trong danh sách

Khi có ít nhất một thẻ, màn có tiêu đề phần "Bài đã mua" (`home.sectionTitle`) kèm số thẻ "{n} bài" (`home.examCount`, đếm mọi thẻ kể cả thẻ đang kiểm tra) và nút "Khám phá bài thi" (chuyển sang mục Trang chủ). Dưới đó, theo thứ tự: nhóm "Đang kiểm tra" (`home.checking.sectionTitle`) gồm các thẻ đang kiểm tra theo thứ tự backend trả; các nhóm bộ đề; nhóm mua lẻ. Một thẻ bài thi có `bundles` rỗng hoặc không có thuộc nhóm mua lẻ; có `bundles` thì thẻ nằm trong nhóm của `bundles[0]` và mỗi bộ khác trong `bundles` (bỏ bộ trùng) nhận một dòng dẫn chiếu. Các nhóm bộ đề xếp theo lần đầu bộ đó xuất hiện trong `bundles` của một thẻ (ở vị trí đầu hay vị trí sau) khi đi theo thứ tự backend trả; thẻ trong nhóm giữ thứ tự backend. Nhóm mua lẻ chỉ có tiêu đề "Bài mua lẻ" (`bundle.home.standaloneHeading`) kèm "{n} bài" khi có ít nhất một nhóm bộ đề.

- Nguồn: `my-exams-view.tsx:44-46,141-173,267-282`; `group-my-exams.ts:29-67`; `MyExamsService.java:138-191`; `vi.json:550,908,918,932`.
- Áp dụng cho: học viên có thẻ.
- Tiêu chí chấp nhận:
  - **AC-204** Khi backend trả lần lượt thẻ A (`CHECKING`), thẻ B (bộ X), thẻ C (không bộ), thẻ D (bộ Y), thẻ E (bộ X) thì màn hiện "Bài đã mua · 5 bài", rồi nhóm "Đang kiểm tra" với A, nhóm X với B và E, nhóm Y với D, rồi "Bài mua lẻ · 1 bài" với C.
  - **AC-205** Khi không thẻ nào có `bundles` thì thẻ bài thi hiện ngay dưới tiêu đề phần, không có tiêu đề "Bài mua lẻ".
  - **AC-206** Khi thẻ F có `bundles = [X, Y]` thì F hiện đầy đủ một lần trong nhóm X; nhóm Y có dòng dẫn chiếu tới X cho F; F không hiện ở nhóm mua lẻ.
  - **AC-207** *(Giới hạn)* Khi chỉ có thẻ đang kiểm tra thì màn có tiêu đề phần, nhóm "Đang kiểm tra" và không có nhóm bộ đề hay nhóm mua lẻ.
  - **AC-208** Khi bề rộng đủ cho nhiều cột thì thẻ trong một nhóm có thể xếp nhiều cột theo hàng, thứ tự đọc từ trái sang phải rồi xuống dòng vẫn đúng thứ tự của AC-204; mục này không có cặp danh sách–chi tiết nên không chia hai khung.

### R-53 Nhóm bộ đề

Mỗi nhóm bộ đề có: tên bộ, nhãn số bài "{n} bài" (số thẻ trong nhóm cộng số dòng dẫn chiếu), mã bộ, nút "Xem bộ đề" (`bundle.home.viewBundle`) mở màn chi tiết bộ đề theo mã bộ (phần danh mục, xem mục 6.1); các thẻ bài thi của nhóm; danh sách dòng dẫn chiếu "Bài {examName} đang hiện ở nhóm {bundleName}" (`bundle.home.crossReference`), `{bundleName}` bấm được; dòng chú thích "Nộp bài một môn chỉ kết thúc lượt của bài đó; các bài khác trong bộ không đổi. Lượt còn lại gồm cả lượt mua lẻ của cùng bài." (`bundle.home.groupFootnote`).

- Nguồn: `my-exams-view.tsx:175-265`; `vi.json:547-552`.
- Áp dụng cho: học viên có thẻ thuộc bộ đề đã mua.
- Tiêu chí chấp nhận:
  - **AC-209** Khi nhóm X có 2 thẻ và 1 dòng dẫn chiếu thì đầu nhóm hiện tên bộ X, nhãn "3 bài" và mã bộ X; cuối nhóm có dòng chú thích `bundle.home.groupFootnote`.
  - **AC-210** Khi bấm "Xem bộ đề" của nhóm X thì app mở màn chi tiết bộ đề có mã bằng `code` của bộ đề của nhóm X (phần tử `MyExamBundleRef` có `bundleId` của X), không lấy theo thẻ đầu tiên trong nhóm.
  - **AC-714** *(Giới hạn)* Khi thẻ F có `bundles = [X, Y]` và không thẻ nào có Y là `bundles[0]` (nhóm Y chỉ có dòng dẫn chiếu), bấm "Xem bộ đề" của nhóm Y thì app mở màn chi tiết bộ đề có mã bằng `code` của Y trong `bundles` của F.
  - **AC-211** Khi bấm tên bộ trong một dòng dẫn chiếu thì danh sách cuộn tới đầu nhóm bộ đó và đầu nhóm nhận tiêu điểm trợ năng.
  - **AC-212** *(Giới hạn)* Khi một nhóm chỉ có dòng dẫn chiếu (mọi bài của bộ đã hiện ở nhóm khác) thì nhóm vẫn hiện, không có thẻ nào, chỉ có các dòng dẫn chiếu và chú thích.

### R-54 Thẻ đang kiểm tra

Thẻ đang kiểm tra hiện: tên loại kỳ thi (`examKind.nameVi` hoặc `nameEn` theo ngôn ngữ, trống thì tên kia, cả hai trống thì `examKind.code`), chip "Đang kiểm tra" (`home.checking.status`), tên bài, một câu thông báo và các nút. Câu thông báo chọn theo hai điều kiện: có `pendingOrderCode` (bỏ khoảng trắng hai đầu, khác rỗng) và có kênh hỗ trợ:

| Có mã đơn | Có kênh hỗ trợ | Khóa |
|---|---|---|
| có | có | `home.checking.message` |
| có | không | `home.checking.messageNoContact`, `{pending}` = `landing.support.pending` |
| không | có | `home.checking.messageNoOrder` |
| không | không | `home.checking.messageNoOrderNoContact`, `{pending}` = `landing.support.pending` |

Cả bốn câu của web có cụm "Tải lại trang sau ít phút" (tiếng Anh "Reload the page in a few minutes"); trong app cụm này là "Tải lại màn hình sau ít phút" ("Reload the screen in a few minutes"), phần còn lại của câu giữ nguyên (xem A-103). `{contact}` là điện thoại và email nối bằng " · ", có giờ hỗ trợ thì thêm " ({giờ})"; chỉ có giờ thì chỉ là giờ. Nút "Xem đơn" (`home.checking.viewOrder`) chỉ có khi có mã đơn, mở chi tiết đơn đó (mục Tài khoản > Đơn hàng). Nút "Tải lại" (`home.checking.reload`) gọi lại `GET /api/v1/me/exams`.

- Nguồn: `my-exam-card.tsx:19-77`; `support.ts:11-17`; `vi.json:902-911,1025`.
- Áp dụng cho: học viên có đơn đã thu tiền chưa cấp quyền.
- Tiêu chí chấp nhận:
  - **AC-213** Khi thẻ có `pendingOrderCode = "DH001"` và app có kênh hỗ trợ thì câu là "Đã nhận thanh toán cho đơn DH001. Bài thi đang được cấp cho tài khoản của bạn, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, liên hệ hỗ trợ qua {contact} và gửi kèm mã đơn." (chuỗi app suy từ `home.checking.message`) với `{contact}` thay bằng kênh hỗ trợ; có nút "Xem đơn" và "Tải lại".
  - **AC-214** Khi thẻ không có mã đơn và app không có kênh hỗ trợ thì câu là "Đã nhận thanh toán cho đơn hàng của bạn. Bài thi đang được cấp cho tài khoản, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, hãy liên hệ hỗ trợ. Thông tin liên hệ đang được cập nhật." (chuỗi app suy từ `home.checking.messageNoOrderNoContact`, `{pending}` = `landing.support.pending`); không có nút "Xem đơn".
  - **AC-215** Khi bấm "Xem đơn" thì app mở màn chi tiết đơn `DH001`.
  - **AC-216** Khi bấm "Tải lại" thì nút ở trạng thái đang xử lý tới khi có phản hồi, bấm thêm không gửi lời gọi thứ hai; khi đơn đã được cấp quyền ở phản hồi mới thì thẻ đang kiểm tra biến mất và thẻ bài thi của bài đó hiện ở nhóm của nó.

### R-55 Nội dung thẻ bài thi

Thẻ bài thi hiện, từ trên xuống:

1. Hàng chip: tên loại kỳ thi (quy tắc chọn tên như R-54); chip nguồn: thẻ theo lớp có `className` khác rỗng → "Bài theo lớp · {className}" (`home.card.classAssigned`), thẻ theo lớp không có tên lớp → "Bài theo lớp" (`home.card.classAssignedNoName`), còn lại → "Đã mua" (`home.card.purchased`); chip "Bài ôn" (`storefront.review`) khi `buildMode = REVIEW`; chip tình trạng: `IN_PROGRESS` "Đang làm", `CAN_START` "Có thể bắt đầu", `EXHAUSTED` "Hết lượt" (`home.status.*`), giá trị khác hiện nguyên mã.
2. Tên bài, tối đa 2 dòng.
3. Chip hình thức: `FULL` "Bài thi FULL", `PART` "Bài thi theo phần", `SUBJECT` "Bài thi theo môn", `BUNDLE` "Bộ đề" (`storefront.format.*`), giá trị khác hiện nguyên mã; có `scopeLabel` thì thêm chip tên phần hoặc môn theo ngôn ngữ.
4. Dòng "{q} câu · {m} phút mỗi lượt" (`storefront.units.questions`, `home.card.minutesPerAttempt`); khoảng `min ≠ max` viết "min–max"; thiếu `questionCount` hoặc `durationMinutes` thì bỏ phần đó; thiếu cả hai thì không có dòng.
5. "Lượt còn lại" (`home.card.attemptsLeft`) và số `attemptsLeft`.
6. Nút và ghi chú theo R-56, R-57.

Thẻ không hiện ảnh bìa.

- Nguồn: `my-exam-card.tsx:13-17,79-157`; `features/storefront/product-card.tsx:15-43`; `features/storefront/format.ts:42-46`; `vi.json:895-901,936-940,1787-1792,1810,1815`.
- Áp dụng cho: thẻ bài thi.
- Tiêu chí chấp nhận:
  - **AC-217** Khi thẻ có `entitlementSource = CLASS`, `className = "12A1"`, `state = CAN_START`, `format = SUBJECT`, `scopeLabel.nameVi = "Toán"`, `questionCount = {50,50}`, `durationMinutes = {90,90}`, `attemptsLeft = 2` thì thẻ hiện chip "Bài theo lớp · 12A1", "Có thể bắt đầu", "Bài thi theo môn", "Toán", dòng "50 câu · 90 phút mỗi lượt" và "Lượt còn lại 2".
  - **AC-218** Khi thẻ mua có `buildMode = REVIEW`, `questionCount = {40,60}`, `durationMinutes = null` thì thẻ hiện chip "Đã mua", "Bài ôn" và dòng "40–60 câu".
  - **AC-219** Khi ngôn ngữ app là English và `scopeLabel.nameEn` trống thì chip phạm vi hiện `scopeLabel.nameVi`.

### R-56 Nút của thẻ bài thi

Nút hiện theo `actions` của backend và các điều kiện dưới, theo thứ tự trên xuống:

| Nút | Khóa | Hiện khi | Đi tới |
|---|---|---|---|
| Tiếp tục | `home.actions.continue` | `actions` có `CONTINUE` và có `activeAttemptId` | màn làm bài của `activeAttemptId` (xem mục 6.4) |
| Bắt đầu | `home.actions.start` | không hiện Tiếp tục, `actions` có `START` và có `entitlementId` | màn chuẩn bị của `entitlementId` (xem mục 6.4) |
| Mua lại | `home.actions.rebuy` | không phải thẻ theo lớp, `saleState = ON_SALE`, `hasPendingFulfillment = false`, `actions` có `REBUY` | màn thanh toán của `code` (xem mục 6.2) |
| Xem kết quả | `home.actions.viewResult` | `actions` có `VIEW_RESULT` và có `resultAttemptId` | màn kết quả của `resultAttemptId` (xem mục 6.6) |
| Lịch sử | `home.actions.history` | `actions` có `HISTORY` | mục Lịch sử theo R-64 |
| Mua thêm lượt | `home.actions.buyMore` | như Mua lại nhưng với `BUY_MORE` | màn thanh toán của `code` (xem mục 6.2) |

Xem kết quả và Lịch sử nằm cùng một hàng khi cả hai cùng hiện. Ghi chú của R-57 nằm giữa Mua lại và hàng Xem kết quả/Lịch sử, trừ ghi chú đơn mua thêm nằm ngay trên Mua thêm lượt. Các nút chỉ chuyển màn; không nút nào gọi API ở mục này.

- Nguồn: `my-exam-card.tsx:108-117,159-234`; `MyExamAction.java:36-61`; `vi.json:882-889`.
- Áp dụng cho: thẻ bài thi.
- Tiêu chí chấp nhận:
  - **AC-220** Khi thẻ mua `IN_PROGRESS` có `actions = [CONTINUE, VIEW_RESULT, HISTORY, BUY_MORE]`, có `activeAttemptId`, `resultAttemptId`, đang bán thì thẻ hiện lần lượt Tiếp tục, hàng Xem kết quả + Lịch sử, Mua thêm lượt; không có Bắt đầu.
  - **AC-221** Khi bấm Tiếp tục thì app mở màn làm bài của `activeAttemptId`; bấm Bắt đầu mở màn chuẩn bị của `entitlementId`; bấm Xem kết quả mở kết quả của `resultAttemptId`; bấm Mua lại hoặc Mua thêm lượt mở màn thanh toán của `code`.
  - **AC-222** *(Từ chối)* Khi `actions` có `CONTINUE` mà `activeAttemptId` rỗng thì không có nút Tiếp tục; nếu `actions` cũng có `START` và có `entitlementId` thì hiện Bắt đầu.
  - **AC-223** *(Từ chối)* Khi thẻ theo lớp có `actions` chứa `REBUY` hoặc `BUY_MORE` thì không hiện Mua lại, Mua thêm lượt.
  - **AC-224** *(Từ chối)* Khi thẻ mua có `saleState = DISCONTINUED` hoặc `hasPendingFulfillment = true` thì không hiện Mua lại, Mua thêm lượt dù `actions` có chúng.
  - **AC-225** *(Giới hạn)* Khi bấm nhanh hai lần vào một nút thì app chỉ mở một màn đích.
  - **AC-226** *(Giới hạn)* Khi lượt đang mở đã được nộp ở máy khác mà thẻ vẫn hiện Tiếp tục, bấm Tiếp tục thì app mở màn làm bài và để màn đó xử lý (xem mục 6.4); quay lại mục thì danh sách tải lại theo AC-194 và thẻ hiện nút theo dữ liệu mới.

### R-57 Ghi chú của thẻ bài thi

| Điều kiện | Khóa | Câu |
|---|---|---|
| `state = IN_PROGRESS` và `attemptsLeft = 0` | `home.notes.lastAttempt` | "Bạn đang làm lượt cuối cùng của bài này." |
| thẻ theo lớp, `EXHAUSTED`, có nút Xem kết quả hoặc Lịch sử | `home.notes.classExhaustedWithHistory` | "Đã hết lượt được giao cho bài thi này. Kết quả và lịch sử vẫn xem được." |
| thẻ theo lớp, `EXHAUSTED`, không có hai nút đó | `home.notes.classExhausted` | "Đã hết lượt được giao cho bài thi này." |
| không theo lớp, ngừng bán, `EXHAUSTED`, có Xem kết quả hoặc Lịch sử | `home.notes.discontinuedExhaustedWithHistory` | "Bài thi đã ngừng bán, không mua thêm được. Kết quả và lịch sử vẫn xem được." |
| không theo lớp, ngừng bán, `EXHAUSTED`, không có hai nút đó | `home.notes.discontinuedExhausted` | "Bài thi đã ngừng bán, không mua thêm được." |
| không theo lớp, ngừng bán, `CAN_START` hoặc `IN_PROGRESS` | `home.notes.discontinuedCanStart` | "Bài thi đã ngừng bán. Bạn vẫn dùng được các lượt còn lại." |
| `hasPendingFulfillment = true` và có `pendingOrderCode` | `home.notes.pendingTopUp` | "Đơn mua thêm lượt {orderCode} đang được kiểm tra. Bạn không cần thanh toán lại." |

Một thẻ có thể có nhiều ghi chú cùng lúc; ghi chú đơn mua thêm hiện bằng màu cảnh báo.

- Nguồn: `my-exam-card.tsx:182-201,222-226`; `vi.json:920-928`.
- Áp dụng cho: thẻ bài thi; thẻ theo lớp; bài ngừng bán.
- Tiêu chí chấp nhận:
  - **AC-227** Khi thẻ theo lớp `EXHAUSTED` có `actions = [HISTORY]` thì thẻ hiện "Đã hết lượt được giao cho bài thi này. Kết quả và lịch sử vẫn xem được.", nút Lịch sử, không có nút mua.
  - **AC-228** Khi thẻ mua `saleState = UNAVAILABLE`, `state = CAN_START`, `actions = [START]` thì thẻ hiện nút Bắt đầu và "Bài thi đã ngừng bán. Bạn vẫn dùng được các lượt còn lại."
  - **AC-229** Khi thẻ mua `IN_PROGRESS`, `attemptsLeft = 0`, `hasPendingFulfillment = true`, `pendingOrderCode = "DH002"` thì thẻ hiện Tiếp tục, "Bạn đang làm lượt cuối cùng của bài này.", "Đơn mua thêm lượt DH002 đang được kiểm tra. Bạn không cần thanh toán lại." và không có Mua thêm lượt.

### R-58 Mục Lịch sử thay màn giữ chỗ

Mục Lịch sử hiện các lượt thi của tài khoản, tải bằng `GET /api/v1/me/attempts?page=0&size=20` khi mở lần đầu (không lọc). Đầu màn có tiêu đề "Lịch sử luyện thi" (`history.pageTitle`), số lượt "{n} lượt" (`history.count`, `n = totalItems`) khi đã có dữ liệu, và dòng "Các lượt thi của bạn, lượt mới nhất ở trên. Chọn Xem kết quả để mở đúng kết quả của từng lượt." (`history.subtitle`). App cũng gọi `GET /api/v1/catalog/options?group=EXAM` và `?group=SUBJECT` để có tên kỳ thi và tên môn; hai danh mục này được dùng lại tối đa 300 s trước khi gọi lại. Mỗi lần quay lại mục, app gọi lại trang lịch sử với bộ lọc đang có.

- Nguồn: giả định A-1; `history-view.tsx:229-251,313-323`; `api.ts:7`; `vi.json:813,852,877`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-230** Khi học viên mở mục Lịch sử lần đầu thì app gọi `GET /api/v1/me/attempts` với `page=0&size=20` và không có tham số lọc, màn không còn "Chưa có nội dung"; khi có phản hồi `totalItems = 7` thì đầu màn hiện "7 lượt".
  - **AC-231** Khi dòng có `subjectCode = "TOAN"` và danh mục `SUBJECT` có `TOAN` tên "Toán" thì dòng hiện "Môn Toán"; khi danh mục không tải được thì dòng hiện "Môn TOAN" và danh sách vẫn hiện.
  - **AC-232** Khi mở lại mục trong vòng 300 s kể từ lần tải danh mục thành công thì app không gọi lại `GET /api/v1/catalog/options`.

### R-59 Tải, lỗi, mất mạng và dữ liệu cũ ở Lịch sử

- Nguồn: `history-view.tsx:245-251,282-284,304-306,529-610,658-666`; `format.ts:32-38`; `vi.json:825-828,849,864-868`.
- Áp dụng cho: học viên; mọi trạng thái dữ liệu.
- Tiêu chí chấp nhận:
  - **AC-233** Khi lần tải đầu đang chờ thì vùng danh sách hiện khung xương, nhãn trợ năng "Đang tải lịch sử luyện thi" (`history.loading`); bộ lọc đã hiện và dùng được.
  - **AC-234** Khi lần tải đầu lỗi thì app không tự gọi lại và hiện "Không tải được lịch sử luyện thi" (`history.error.title`), "Các lượt thi của bạn vẫn được giữ. Hãy thử lại sau ít phút." (`history.error.description`) và nút "Thử lại"; bấm thì gọi lại đúng trang và bộ lọc đó.
  - **AC-235** Khi đã có danh sách và lần tải sau (đổi lọc, nạp thêm, quay lại mục) lỗi thì danh sách cũ giữ nguyên, phía trên có dải "Dữ liệu chưa được cập nhật." (`history.stale.title`) kèm "Không tải lại được dữ liệu; đang hiện bản đã tải lúc {time}." (`history.stale.description`), `{time}` dạng `HH:mm, dd/MM/yyyy` theo giờ hiển thị; không biết giờ thì `history.stale.descriptionNoTime`; nút "Thử lại" gọi lại lần tải vừa lỗi.
  - **AC-236** Khi đã có danh sách và người dùng đổi bộ lọc thì danh sách cũ vẫn hiện, được đánh dấu đang tải (mờ đi) tới khi có trang mới.
  - **AC-237** Khi máy không có mạng lúc app cần tải trang lịch sử thì vùng danh sách hiện "Không có kết nối" (`common.offline.title`), `common.offline.description` và "Thử lại"; bộ lọc vẫn hiện; có mạng lại và bấm "Thử lại" thì app tải lại.

### R-60 Lịch sử trống

- Nguồn: `history-view.tsx:612-656`; `vi.json:814-824`.
- Áp dụng cho: học viên không có dòng nào khớp.
- Tiêu chí chấp nhận:
  - **AC-238** Khi không có bộ lọc nào và `totalItems = 0` thì màn hiện "Bạn chưa có lượt luyện thi nào" (`history.empty.noAttemptsTitle`), `history.empty.noAttemptsDescription` và nút "Bài thi của tôi" (`history.empty.toMyExams`); bấm nút thì app chuyển sang mục Bài thi của tôi.
  - **AC-239** Khi có ít nhất một điều kiện lọc và không có dòng nào thì màn hiện "Chưa có lượt thi phù hợp" (`history.empty.title`), "Thử đổi từ khóa, khoảng ngày hoặc bỏ bớt điều kiện lọc." (`history.empty.description`) và nút "Xóa bộ lọc" (`history.empty.clearFilters`) làm như R-65.

### R-61 Tìm theo tên bài

Ô "Tìm theo tên bài" (`history.filters.q`), gợi ý "Ví dụ: HSA tổng hợp" (`history.filters.qPlaceholder`). Chuỗi đã bỏ khoảng trắng hai đầu được gửi làm `q` sau 300 ms ngừng gõ, hoặc ngay khi người dùng bấm phím tìm của bàn phím; chuỗi rỗng thì không gửi `q`. Mỗi lần đổi `q` thì danh sách về trang đầu.

- Nguồn: `history-view.tsx:74,179-196,391-414`; `history-query.ts:95-111`; `AttemptHistoryService.java:143-146`; `vi.json:842-843`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-240** Khi gõ "hsa" rồi ngừng 300 ms thì app gọi `GET /api/v1/me/attempts` với `q=hsa&page=0&size=20` đúng một lần; các dòng trả về là các lượt có tên bài chứa "hsa" không phân biệt hoa thường.
  - **AC-241** *(Giới hạn)* Khi gõ liên tục "h", "hs", "hsa" cách nhau dưới 300 ms thì app chỉ gọi một lần với `q=hsa`; ký tự đang gõ trong ô không bị thay hay mất khi phản hồi về.
  - **AC-242** Khi gõ "hsa" rồi bấm phím tìm trước 300 ms thì app gọi ngay với `q=hsa` và không gọi thêm lần nữa khi hết 300 ms.
  - **AC-243** *(Giới hạn)* Khi ô chỉ có khoảng trắng thì lời gọi không có tham số `q`.

### R-62 Lọc theo kỳ thi và phạm vi

Chọn "Kỳ thi" (`history.filters.kind`): mục đầu "Tất cả kỳ thi" (`history.filters.kindAll`), tiếp theo mỗi phần tử của `GET /api/v1/catalog/options?group=EXAM` có `code`, nhãn theo quy tắc chọn tên: English thì `nameEn` nếu khác rỗng, không thì `nameVi`; tiếng Việt thì `nameVi` nếu khác rỗng, không thì `nameEn`; cả hai rỗng thì `code`. Chọn "Phạm vi thi" (`history.filters.scope`): "Tất cả phạm vi" (`history.filters.scopeAll`), "Bài thi FULL", "Bài thi theo phần", "Bài thi theo môn" (`history.scopes.*`). Chọn mục "Tất cả" thì bỏ tham số; chọn giá trị thì gửi `kind=<code>` hoặc `scope=FULL|PART|SUBJECT`. Đổi lựa chọn thì về trang đầu.

- Nguồn: `history-view.tsx:254-279,416-452`; `features/attempt-result/format.ts:104-113`; `packages/web-kit/src/i18n/pick-name.ts`; `history-query.ts:10,23-25`; `AttemptHistoryService.java:147-152`; `vi.json:836-847,859-863`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-244** Khi chọn kỳ thi có mã `HSA` thì app gọi với `kind=HSA&page=0&size=20`; chỉ các lượt có `examKindCode = HSA` hiện.
  - **AC-245** Khi chọn "Bài thi theo phần" rồi chọn lại "Tất cả phạm vi" thì lời gọi thứ nhất có `scope=PART`, lời gọi thứ hai không có `scope`.
  - **AC-246** *(Giới hạn)* Khi danh mục `EXAM` không tải được thì chọn Kỳ thi chỉ có "Tất cả kỳ thi", danh sách vẫn tải bình thường.
  - **AC-715** *(Giới hạn)* Khi app ở English và phần tử `EXAM` có `nameVi = "Đánh giá năng lực"`, `nameEn` rỗng thì nhãn là "Đánh giá năng lực"; khi một phần tử có cả `nameVi` và `nameEn` rỗng, `code = "HSA"` thì nhãn là "HSA".

### R-63 Lọc theo khoảng ngày

Hai ô "Từ ngày" (`history.filters.from`) và "Đến ngày" (`history.filters.to`) chọn ngày bằng bộ chọn ngày của hệ điều hành. Ngày gửi dạng `yyyy-MM-dd`; backend hiểu là ngày theo giờ hiển thị, "Đến ngày" lấy hết ngày đó. Bộ chọn "Từ ngày" không cho chọn sau "Đến ngày" đang có, bộ chọn "Đến ngày" không cho chọn trước "Từ ngày" đang có. Mỗi ô bỏ được giá trị riêng. Đổi ngày thì về trang đầu.

- Nguồn: `history-view.tsx:454-482`; `history-query.ts:27-37,50-53`; `AttemptHistoryService.java:156-165,254-272`; `vi.json:838,846`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-247** Khi chọn Từ ngày 01/11/2026 và Đến ngày 20/11/2026 thì lời gọi có `from=2026-11-01&to=2026-11-20`; một lượt bắt đầu 23:30 ngày 20/11/2026 giờ hiển thị có trong danh sách, một lượt bắt đầu 00:10 ngày 21/11/2026 giờ hiển thị không có, bất kể múi giờ của máy.
  - **AC-248** *(Từ chối)* Khi đã chọn Đến ngày 20/11/2026 thì bộ chọn Từ ngày không chọn được ngày sau 20/11/2026; app không bao giờ gửi `from` sau `to`.
  - **AC-249** *(Từ chối)* Khi backend trả 422 `INVALID_DATE_FILTER` (không xảy ra nếu app gửi đúng dạng) thì app hiện lỗi của AC-234 hoặc dải dữ liệu cũ của AC-235.

### R-64 Nút Lịch sử của thẻ

Trong đợt này app làm như web đang làm trên thực tế: nút Lịch sử của thẻ mở mục Lịch sử không lọc theo bài. Trên web, nút dẫn tới `/history?examId={code}`; trang Lịch sử đọc bộ lọc lại từ URL, nên mọi điều kiện lọc cũ mất, và bỏ `examId` vì `code` không phải UUID, kết quả là danh sách không lọc. App cũng vậy: bấm Lịch sử trên thẻ thì chuyển sang mục Lịch sử với bộ lọc rỗng. App không gửi `examId` và không có chip lọc theo bài (`history.examFilter*`) trong đợt này. Chỗ lệch của web đã ghi ở Phụ lục A (xem A-82).

- Nguồn: `my-exam-card.tsx:212-217`; `history-query.ts:46-49`; `history-view.tsx:151-155,163-174`; `AttemptResultController.java:40`; `vi.json:885`.
- Áp dụng cho: thẻ có `actions` chứa `HISTORY`.
- Tiêu chí chấp nhận:
  - **AC-250** Khi mục Lịch sử đang lọc `q=hsa`, `scope=PART` và người dùng bấm Lịch sử trên một thẻ ở Bài thi của tôi thì app chuyển sang mục Lịch sử, ô tìm trống, hai ô chọn về "Tất cả", hai ô ngày trống, không có nút "Xóa bộ lọc", và app gọi `GET /api/v1/me/attempts?page=0&size=20` không có tham số lọc nào, kể cả `examId`.

### R-65 Xóa và giữ bộ lọc

Khi có ít nhất một điều kiện (ô tìm khác rỗng, kỳ thi, phạm vi, từ ngày, đến ngày), bộ lọc có nút "Xóa bộ lọc" (`history.filters.clear`). Bấm thì mọi điều kiện về rỗng, ô tìm trống, danh sách về trang đầu. Bộ lọc giữ nguyên khi chuyển mục rồi quay lại, khi mở màn kết quả hoặc màn làm bài từ một dòng rồi quay lại, khi xoay hoặc gập máy; tắt hẳn app thì bộ lọc về rỗng. Riêng nút Lịch sử của thẻ đưa bộ lọc về rỗng (R-64).

- Nguồn: `history-view.tsx:205-218,484-493`; `history-query.ts:91-93`; quyết định của đợt khung và xác thực (xoay, gập).
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-252** Khi không có điều kiện nào thì không có nút "Xóa bộ lọc"; khi chọn phạm vi PART thì nút hiện.
  - **AC-253** Khi có `q=hsa`, `kind=HSA`, `from` và bấm "Xóa bộ lọc" thì ô tìm trống, hai ô chọn về "Tất cả", hai ô ngày trống và app gọi `page=0&size=20` không có tham số lọc.
  - **AC-254** Khi đang lọc `scope=SUBJECT`, mở kết quả một dòng rồi quay lại thì bộ lọc vẫn là "Bài thi theo môn" và danh sách tải lại với `scope=SUBJECT`.

### R-66 Nội dung một dòng lịch sử

Mỗi dòng hiện:

- Tên bài `examName`.
- Dòng lỗi hệ thống: `compensated = true` → "Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt" (`history.fault.compensated`, màu thông tin); nếu không mà `faultFlag = SYSTEM_FAULT` → "Lượt kết thúc do lỗi hệ thống" (`history.fault.systemFault`, màu cảnh báo).
- Chip trạng thái: `regrading = true` → "Đang chấm lại"; còn lại theo `state`: `SCORED` "Đã chấm", `PENDING_REVIEW` "Chờ chấm", `STARTING` hoặc `IN_PROGRESS` "Đang làm", `GRADING` "Đang chấm", mọi giá trị khác "Đang xử lý kết quả" (`history.states.*`).
- Phạm vi: `history.scopes.<scope>`, giá trị lạ thì hiện nguyên, trống thì "—"; `PART` có `partCode` thì thêm " · Phần {partCode}"; `SUBJECT` có `subjectCode` thì thêm " · Môn {tên môn}" (`history.scopeDetails.*`).
- Nhãn "Điểm luyện tập" (`history.vactPracticeNote`) khi `examKindCode = VACT` và dòng có điểm; nhãn "Điểm trước khi chấm lại" (`history.regradingNote`) khi `regrading = true`.
- "Điểm" (`history.columns.score`) theo R-67.
- "Lựa chọn" (`history.columns.choice`): mỗi phần tử của `choices` có `optionCode` thành một mục; nhãn của mã `ANH`, `SCIENCE`, `ENGLISH`, `KH`, `TA`, `CS`, `ICT` lấy từ `history.choiceOptions.*`, mã khác lấy tên môn trong danh mục (thiếu thì mã); có `subjectCodes` thì thêm ": " và tên các môn nối bằng ", "; các mục nối bằng " · "; không có mục nào thì "—" (`history.noChoice`).
- "Lần làm · Bắt đầu" (`history.columns.attemptStarted`): "Lần {attemptNo}" (`history.attemptNo`) và giờ bắt đầu `HH:mm, dd/MM/yyyy` theo giờ hiển thị, nối bằng " · "; thiếu phần nào thì bỏ phần đó.

- Nguồn: `history-view.tsx:84-132,669-757`; `format.ts:32-38,115-146`; `AttemptHistoryService.java:141`; `vi.json:794-812,832-835,850,854-863,869-876,879`.
- Áp dụng cho: mọi dòng lịch sử.
- Tiêu chí chấp nhận:
  - **AC-255** Khi dòng có `scope = PART`, `partCode = "P1"`, `state = SCORED`, `attemptNo = 2`, `startedAt = 2026-11-20T02:05:00Z` thì dòng hiện "Bài thi theo phần · Phần P1", chip "Đã chấm" và "Lần 2 · 09:05, 20/11/2026" khi máy đặt múi giờ khác `Asia/Ho_Chi_Minh`.
  - **AC-256** Khi dòng có `state = SCORED`, `regrading = true` thì chip là "Đang chấm lại" và có nhãn "Điểm trước khi chấm lại".
  - **AC-257** Khi dòng có `state = GRADING_ERROR` thì chip là "Đang xử lý kết quả".
  - **AC-258** Khi dòng có `compensated = true` và `faultFlag = SYSTEM_FAULT` thì chỉ hiện "Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt".
  - **AC-259** Khi `choices = {"p2": {optionCode: "KH", subjectCodes: ["LY","HOA"]}}` và danh mục có Lý = "Vật lý", Hóa = "Hóa học" thì "Lựa chọn" là "Khoa học: Vật lý, Hóa học"; `choices` rỗng thì là "—".

### R-67 Điểm của dòng lịch sử

Dòng có điểm hiện điểm là `displayScore`, thiếu thì `rawScore`; điểm tối đa là `scaleMax`, thiếu thì `maxScore`; dạng "{điểm}/{tối đa}", không có tối đa thì chỉ "{điểm}". Số viết tối đa 2 chữ số thập phân, bỏ số 0 thừa, dấu thập phân "," khi tiếng Việt và "." khi English. Dòng không có điểm hiện "Chưa có điểm" (`history.noScore`). Điểm 0 thật vẫn hiện "0/…".

- Nguồn: `history-view.tsx:675-691`; `format.ts:41-58`; `AttemptHistoryService.java:184-190`; `vi.json:851`.
- Áp dụng cho: mọi dòng lịch sử.
- Tiêu chí chấp nhận:
  - **AC-260** Khi dòng `SCORED` có `displayScore = 7.25`, `scaleMax = 10` thì điểm là "7,25/10" ở tiếng Việt và "7.25/10" ở English.
  - **AC-261** Khi dòng `SCORED` có `displayScore = null`, `rawScore = 0`, `scaleMax = null`, `maxScore = 40` thì điểm là "0/40".
  - **AC-262** Khi dòng `PENDING_REVIEW` có `regrading = false` thì điểm là "Chưa có điểm"; khi dòng `IN_PROGRESS` thì cũng là "Chưa có điểm".
  - **AC-263** *(Giới hạn)* Khi `displayScore = 8.333333` thì điểm hiện "8,33".

### R-68 Thao tác trên dòng lịch sử

Dòng có `action = CONTINUE` có nút "Tiếp tục" (`history.actions.continue`) mở màn làm bài của `attemptId` (xem mục 6.4); chạm vào phần còn lại của dòng không mở gì. Dòng có `action = VIEW_RESULT` có nút "Xem kết quả" (`history.actions.viewResult`); chạm vào nút hoặc tên bài mở màn kết quả của `attemptId` (xem mục 6.6). Web chỉ có nút "Xem kết quả" ở bố cục bảng (màn rộng); ở bố cục thẻ (màn hẹp) web chỉ cho chạm tên bài. App dùng dạng thẻ ở mọi bề rộng (A-32) nhưng vẫn có nút, vì dòng giới thiệu `history.subtitle` bảo người dùng "Chọn Xem kết quả"; đây là khác biệt có chủ đích so với bố cục thẻ của web.

- Nguồn: `history-view.tsx:108-111,696-752,880-891` (bố cục thẻ: tên bài là liên kết `ListCardTitleLink`, chỉ dòng `CONTINUE` có nút; bố cục bảng: nút ở cột cuối); `AttemptHistoryService.java:192-194`; `vi.json:785-788`.
- Áp dụng cho: mọi dòng lịch sử.
- Tiêu chí chấp nhận:
  - **AC-264** Khi bấm "Tiếp tục" ở dòng `IN_PROGRESS` thì app mở màn làm bài của `attemptId` đó.
  - **AC-265** Khi chạm tên bài ở dòng `SCORED` thì app mở màn kết quả của `attemptId` đó; chạm tên bài ở dòng `IN_PROGRESS` thì không mở màn nào.
  - **AC-266** *(Giới hạn)* Khi bấm nhanh hai lần vào một nút của dòng thì app chỉ mở một màn đích.

### R-69 Nạp thêm trang

Mỗi trang 20 dòng. App hiện trang đầu, khi người dùng cuộn tới cuối danh sách và `page + 1 < totalPages` thì app gọi trang kế tiếp với cùng bộ lọc và nối vào cuối; trong lúc chờ có chỉ báo đang tải ở cuối danh sách. Trang cuối thì không gọi thêm. Dòng có `attemptId` đã hiện thì không hiện lần hai. Phản hồi của một trang chỉ được dùng khi bộ lọc của lời gọi đó còn đúng là bộ lọc đang hiện và số trang của nó là trang kế tiếp đang chờ; phản hồi về trễ của bộ lọc cũ hoặc của danh sách đã bị thay bị bỏ, không nối vào và không hiện dải lỗi.

- Nguồn: `api.ts:7`; `history-query.ts:95-111`; `AttemptHistoryService.java:49-50,90-93`; `PageResponse.java:9-34`; thay số trang ở `history-view.tsx:901-910` (giả định A-28).
- Áp dụng cho: học viên có hơn 20 dòng khớp.
- Tiêu chí chấp nhận:
  - **AC-267** Khi `totalItems = 45` thì sau khi cuộn tới cuối hai lần app đã gọi `page=0`, `page=1`, `page=2` (mỗi lần `size=20`), danh sách có 45 dòng, cuộn tiếp không gọi thêm.
  - **AC-268** *(Giới hạn)* Khi `totalItems = 20` thì app chỉ gọi `page=0`.
  - **AC-269** *(Giới hạn)* Khi giữa hai lần nạp có một lượt mới bắt đầu ở máy khác (dòng của trang trước bị đẩy xuống trang sau) thì danh sách không có hai dòng cùng `attemptId`.
  - **AC-270** *(Giới hạn)* Khi một trang sau trả `items` rỗng dù `totalItems > 0` thì app dừng nạp thêm, giữ các dòng đang có và không hiện trạng thái rỗng.
  - **AC-271** Khi đổi bất kỳ điều kiện lọc nào thì danh sách thay bằng trang đầu của bộ lọc mới (`page=0`).
  - **AC-716** *(Giới hạn)* Khi lời gọi `page=1` của bộ lọc không có điều kiện đang chờ và người dùng chọn phạm vi `PART` (app gọi `scope=PART&page=0`), nếu phản hồi `page=1` cũ về sau phản hồi mới thì danh sách chỉ có các dòng của `scope=PART`, không có dòng nào của trang 1 cũ, và lần nạp thêm kế tiếp gọi `scope=PART&page=1`.

### R-70 Phiên hết hạn và từ chối quyền ở hai mục

- Nguồn: `ExamLearnerGate.java:31-41`; `AttemptAccessGate.java:26-38`; `query-provider.tsx:78-85`; quyết định của đợt khung và xác thực; đặc tả khung `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` AC-33, AC-34, AC-30.
- Áp dụng cho: học viên có phiên hết hạn, bị ngừng hoạt động hoặc bị đổi vai trò.
- Tiêu chí chấp nhận:
  - **AC-272** Khi `GET /api/v1/me/exams` hoặc `GET /api/v1/me/attempts` trả 401 và làm mới phiên thành công thì app gọi lại đúng lời gọi đó và hiện kết quả, người dùng không thấy lỗi.
  - **AC-273** *(Từ chối)* Khi `GET /api/v1/me/exams` trả 403 `FORBIDDEN` hoặc `GET /api/v1/me/attempts` trả 403 `ATTEMPT_ACCESS_FORBIDDEN` thì app không tự gọi lại, hiện khối lỗi của mục (AC-197 hoặc AC-234) và làm mới phiên; nếu làm mới cho thấy tài khoản không còn là học viên hoặc không còn hoạt động thì app xử lý theo đặc tả khung (màn chỉ dành cho học viên hoặc màn Đăng nhập).

### 6.4 Chuẩn bị, bắt đầu lượt thi và khung làm bài

### R-71 Màn chuẩn bị: tải và lỗi

Từ một thẻ "Bắt đầu" hoặc "Vào làm bài" (xem mục 6.3), app mở màn chuẩn bị và gọi `GET /api/v1/me/entitlements/{entitlementId}/prepare`.

- Nguồn: `features/attempt-prepare/prepare-view.tsx:153-158,367-460`; quyết định của đợt khung và xác thực (chỉ học viên dùng app).
- Áp dụng cho: học viên; mọi quyền dùng.
- Tiêu chí chấp nhận:
  - **AC-274** Khi đang chờ phản hồi, mở màn chuẩn bị thì hiện khung chờ và chưa có nút Bắt đầu.
  - **AC-275** *(Từ chối)* Khi API trả 404, mở màn thì hiện "Không tìm thấy bài thi trong tài khoản đang đăng nhập" (`prepare.notFound.title`), "Quyền làm bài không tồn tại hoặc thuộc về tài khoản khác." (`prepare.notFound.description`) và nút "Về Bài thi của tôi" (`prepare.backToMyExams`); app gọi API đúng 1 lần.
  - **AC-276** *(Từ chối)* Khi API lỗi khác 404 hoặc mất mạng, mở màn thì app gọi lại 1 lần, vẫn lỗi thì hiện "Không tải được thông tin chuẩn bị bài thi" (`prepare.errorLoad.title`), "Vui lòng kiểm tra kết nối mạng và thử lại." (`prepare.errorLoad.description`), nút "Về Bài thi của tôi" và "Thử lại" (`common.retry`); bấm "Thử lại" gọi lại API.
  - **AC-277** *(Từ chối)* Khi API trả 403 `ATTEMPT_ACCESS_FORBIDDEN` (ví dụ tài khoản vừa bị ngừng), mở màn thì hiện trạng thái lỗi của AC-276.

### R-72 Màn chuẩn bị: nội dung

- Nguồn: `prepare-view.tsx:536-550,571-623,804-1037`; `features/attempt-prepare/metrics.ts:10-57`.
- Áp dụng cho: học viên; quyền dùng còn hoặc hết lượt.
- Tiêu chí chấp nhận:
  - **AC-278** Khi API trả dữ liệu, mở màn thì thấy nhãn kỳ thi, nhãn phạm vi theo `prepare.scope.FULL|PART|SUBJECT` (kèm " · {tên môn}" khi có `subject`), nhãn "Bài ôn" (`prepare.reviewBadge`) khi `buildMode = REVIEW`, tên bài và dòng tóm tắt "{n} phần · {n} câu · {n} phút · {x} điểm" bỏ các mục không có số.
  - **AC-279** Khi API trả `navigationMode = FREE`, `timing = PER_PART` hoặc `timing = SHARED`, xem khối "Cấu trúc bài thi" thì thấy lần lượt tiêu đề và mô tả của `prepare.timing.FREE`, `prepare.timing.PER_PART`, `prepare.timing.SHARED` (FREE được ưu tiên khi trùng).
  - **AC-280** Khi API trả danh sách phần, xem khối Cấu trúc thì mỗi phần theo thứ tự `ord` có số thứ tự, tên, "{n} câu", "{n} phút", "{x} điểm" (mục nào có số mới hiện) và nhãn "Bắt buộc" hoặc "Tự chọn".
  - **AC-281** Khi API có `answerFormats`, `listeningPolicy`, `omittedGroups`, xem khối Cấu trúc thì thấy "Dạng câu hỏi" với tên theo `prepare.answerFormats.<mã>` (mã lạ hiện nguyên mã), khối "Chính sách nghe" với "Mỗi tệp âm thanh được nghe tối đa {maxPlays} lần." (mặc định 2) và thêm `prepare.listeningPolicy.noSeek` khi `seekable = false`, và danh sách `prepare.omittedGroupsLabel` kèm điểm từng nhóm. Mục rỗng thì không hiện khối.
  - **AC-282** Khi API có `afterStartChoices`, xem màn thì thấy khối "Lựa chọn sẽ gặp trong bài" liệt kê tên phần, mã và tên phương án, "(chọn đúng {count} trong {total} môn)" khi phương án đòi chọn môn, danh sách môn, cùng 2 ghi chú `prepare.afterStartChoices.deadlineNote` và `prepare.afterStartChoices.timerNote`.
  - **AC-283** Khi xem khối "Hướng dẫn làm bài", thì thấy nội dung `instructions` của bài (không có ảnh; cách vẽ HTML xem mục 6.5) khi khác rỗng, rồi 4 ghi chú `prepare.instructions.noteAutoSave`, `noteClock`, `noteOffline`, `noteSingleDevice`.
  - **AC-284** Khi xem khối "Sẵn sàng làm bài", thì thấy "Số lượt còn lại" = "{attemptsLeft} lượt", "Số câu phải làm", "Tổng thời gian", "Điểm tối đa" (không có số thì hiện "—"); khi `attemptsLeft > 0` và có `grantSource` thì thêm "Lượt này dùng từ" theo `prepare.grantSource.compensation` (`COMPENSATION`), `classAssigned`/`classAssignedNoDate` (`CLASS`), `purchase`/`purchaseNoOrder` (mua, có hoặc không mã đơn).
  - **AC-285** Khi đã chọn một phương án ở R-73, xem dòng tóm tắt và khối "Sẵn sàng làm bài" thì số câu, số phút, số điểm lấy theo phương án đó; chưa chọn thì cộng theo các phần (số phút ưu tiên `durationMinutes` của bài).

### R-73 Lựa chọn trước khi bắt đầu

- Nguồn: `prepare-view.tsx:91-102,184-192,472-492,555-562,627-802,1129-1135`.
- Áp dụng cho: quyền dùng có `preChoice` và chưa có lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-286** Khi `preChoice` có `preselectedCode` khớp một phương án, hoặc chỉ có đúng 1 phương án, mở màn thì phương án đó đã được chọn; ngược lại chưa phương án nào được chọn.
  - **AC-287** Khi chọn một phương án có `kind = SUBJECT_CHOICE` và `chooseSubjects = N > 0`, thì hiện khối "Chọn môn tự chọn" với "Đã chọn {selected}/{N}"; khi đã chọn đủ N môn, các môn chưa chọn bị khóa; bỏ một môn thì mở lại.
  - **AC-288** Khi đang chọn môn mà đổi sang phương án khác, thì danh sách môn đã chọn về rỗng và lỗi bắt đầu đang hiện (nếu có) biến mất.
  - **AC-289** *(Từ chối)* Khi chưa chọn phương án, hoặc chọn chưa đủ N môn, xem nút "Bắt đầu bài thi" thì nút bị khóa và dưới nút hiện "Vui lòng chọn một phương án trước khi bắt đầu." (`prepare.preChoice.hintSelectOption`) hoặc "Vui lòng chọn đủ {count} môn trước khi bắt đầu." (`prepare.preChoice.hintSelectSubjects`).
  - **AC-290** *(Từ chối)* Khi `preChoice` có 0 phương án, mở màn thì thay danh sách bằng cảnh báo "Các mã bạn đã mua hiện không còn được phục vụ." (`prepare.noServingOption.title`) kèm `prepare.noServingOption.descriptionWithContact` (có kênh hỗ trợ) hoặc `prepare.noServingOption.contactPending`; nút Bắt đầu bị khóa.

### R-74 Trạng thái không bắt đầu được và lượt đang mở

- Nguồn: `prepare-view.tsx:552-553,1039-1086`.
- Áp dụng cho: học viên; quyền dùng hết lượt hoặc có lượt đang mở.
- Tiêu chí chấp nhận:
  - **AC-291** Khi `openAttempt.state = IN_PROGRESS`, mở màn thì không có khối lựa chọn trước, cột phải hiện "Bạn đang có lượt làm bài chưa hoàn tất. Tiếp tục để làm tiếp, không tạo lượt mới." (`prepare.inProgressBanner`) và nút "Tiếp tục" (`prepare.continueAttempt`); bấm thì mở màn làm bài của lượt đó, không gọi `POST /api/v1/me/attempts`.
  - **AC-292** Khi `openAttempt.state = STARTING`, mở màn thì app vào thẳng màn chờ (R-76) của lượt đó.
  - **AC-293** *(Từ chối)* Khi `attemptsLeft = 0`, mở màn thì hiện "Bạn đã dùng hết lượt của bài này." (`prepare.exhaustedTitle`) kèm `prepare.exhaustedDescriptionClass` (nguồn `CLASS`), `prepare.exhaustedDescription` (`servingStatus = PUBLISHED`) hoặc `prepare.exhaustedDescriptionDiscontinued`; nút Bắt đầu bị khóa.
  - **AC-294** Khi `attemptsLeft = 0`, `servingStatus = PUBLISHED` và nguồn không phải `CLASS`, bấm "Mua thêm lượt" (`prepare.buyMoreAttempts`) thì mở màn thanh toán của mã bài (xem mục 6.2); trường hợp khác không có nút này.

### R-75 Bắt đầu lượt thi

- Nguồn: `prepare-view.tsx:104-114,215-287,1088-1127`; `shared/idempotency/IdempotentCommand.java:136,149-158`; `attempt/application/AttemptStartService.java:355-413`.
- Áp dụng cho: học viên; nút Bắt đầu đang bấm được.
- Tiêu chí chấp nhận:
  - **AC-295** Khi bấm "Bắt đầu bài thi", thì app gửi `POST /api/v1/me/attempts` với header `Idempotency-Key` là một UUID mới cho lần bấm đó (thử lại sau lỗi thuộc loại nêu ở AC-17 gửi lại cùng khóa), thân `{entitlementId}` và thêm `preChoice: {optionCode, subjectCodes?}` khi có lựa chọn (`subjectCodes` chỉ khi phương án đòi chọn môn); nút ở trạng thái đang xử lý.
  - **AC-296** *(Giới hạn)* Khi đang chờ phản hồi của lần bấm đầu, bấm thêm lần nữa thì không có lời gọi thứ hai.
  - **AC-297** Khi phản hồi `state = IN_PROGRESS` (lượt nối lại), thì app mở màn làm bài của `attemptId`; `state = STARTING` thì mở màn chờ; `state = ABORTED` thì mở màn chờ ở biến thể thất bại với `abortReason` của phản hồi.
  - **AC-298** Khi phản hồi thành công, thì app lưu trên máy ngữ cảnh bắt đầu gồm `entitlementId`, `preChoice`, tên bài, nhãn kỳ thi, tên phần đầu và thời lượng phần đầu (thời lượng cả bài khi `SHARED`), gắn với tài khoản và `attemptId`, còn sau khi app bị tắt.
  - **AC-299** *(Từ chối)* Khi API trả `NO_ATTEMPTS_LEFT`, `CHOICE_NOT_ALLOWED`, `NO_SERVING_OPTION`, `SUBJECTS_INVALID`, thì trên nút hiện câu tương ứng của `prepare.startErrors.<mã>` (với `NO_SERVING_OPTION` thêm dòng liên hệ hỗ trợ của AC-290); trả 429 hoặc `RATE_LIMITED` thì hiện `prepare.startErrors.RATE_LIMITED`; mã khác, lỗi 5xx hoặc mất mạng thì hiện "Không thể bắt đầu bài thi lúc này. Vui lòng thử lại." (`prepare.startErrors.generic`). Lựa chọn đã chọn giữ nguyên và nút bấm lại được.

### R-76 Màn chờ chuẩn bị đề

- Nguồn: `features/attempt-prepare/preparing-screen.tsx:10-12,62-365`; `prepare-view.tsx:296-360`; `application.yml:197-208`.
- Áp dụng cho: lượt `STARTING` hoặc vừa `ABORTED`.
- Tiêu chí chấp nhận:
  - **AC-300** Khi màn chờ mở, xem màn thì có thanh trên với tên bài và "Chưa tính giờ" (`attempt.waiting.clockPaused`), khối "Đang chuẩn bị đề" (`attempt.waiting.title`) với `attempt.waiting.description`, "Phần mở đầu" và "Thời gian của phần" hoặc "Thời gian làm bài" (khi `SHARED`) = "{minutes} phút" nếu có, và ghi chú `attempt.waiting.reassureNote` với liên kết "Bài thi của tôi" (câu chữ trên app: xem A-87).
  - **AC-301** Khi màn chờ mở, đợi thì app gọi `GET /api/v1/me/attempts/{id}` 1 giây một lần, không chồng lời gọi; lời gọi lỗi hoặc mất mạng không làm đổi màn và lần sau vẫn gọi.
  - **AC-302** *(Giới hạn)* Khi đã chờ đủ 90 giây mà lượt vẫn `STARTING`, thì màn đổi sang "Sau 90 giây vẫn chưa xong" (`attempt.waiting.busyNoticeLabel`), "Hệ thống đang bận" (`attempt.waiting.busyTitle`), `attempt.waiting.busyDescription` và nút "Về Bài thi của tôi"; từ đó app gọi 5 giây một lần, không dừng.
  - **AC-303** Khi một lần gọi trả trạng thái khác `STARTING` và khác `ABORTED`, thì app ngừng gọi và mở màn làm bài của lượt.
  - **AC-304** *(Từ chối)* Khi một lần gọi trả `ABORTED`, thì app ngừng gọi và hiện "Chuẩn bị đề thất bại", "Chưa chuẩn bị được đề", lý do theo `attempt.abortReasons.<abortReason>` (mã lạ dùng `attempt.abortReasons.generic`), "Lượt đã được trả lại, bạn chưa mất lượt nào. Đồng hồ chưa chạy." (`attempt.waiting.failedRefundNote`), nút "Về Bài thi của tôi" và "Thử lại" (`attempt.waiting.retry`).
  - **AC-305** Khi ở màn thất bại mà biết được lựa chọn trước của lần đầu (trong bộ nhớ hoặc ngữ cảnh bắt đầu đã lưu), hoặc bài không có lựa chọn trước, hoặc có đúng một phương án không đòi chọn môn, bấm "Thử lại" thì app gửi lại R-75 với khóa mới và lựa chọn đó; không suy ra được lựa chọn thì app về màn chuẩn bị, tải lại dữ liệu và để học viên chọn lại.
  - **AC-306** *(Từ chối)* Khi "Thử lại" ở màn thất bại bị từ chối, thì lỗi theo AC-299 hiện dưới nút; mở từ màn làm bài mà nhận `CHOICE_NOT_ALLOWED` hoặc `SUBJECTS_INVALID` thì app về màn chuẩn bị của quyền dùng; không có ngữ cảnh bắt đầu trên máy thì app về Bài thi của tôi.
  - **AC-307** Khi đang ở màn chờ, rời app hoặc tắt app rồi mở lại từ Bài thi của tôi và bấm Tiếp tục, thì app vào lại màn chờ của cùng lượt (không tạo lượt mới); máy chủ hủy lượt kẹt `STARTING` quá 2 phút thành `ABORTED STALE_STARTING`, khi đó màn hiện biến thể thất bại với "Việc chuẩn bị đề bị gián đoạn." (`attempt.abortReasons.STALE_STARTING`).

### R-77 Mở màn làm bài

- Nguồn: `features/attempt-player/attempt-player.tsx:757-852,1515-1680`.
- Áp dụng cho: học viên; mọi trạng thái lượt.
- Tiêu chí chấp nhận:
  - **AC-308** Khi mở màn làm bài, đang chờ dữ liệu thì hiện vòng quay và "Đang chuẩn bị đề" (`attempt.waiting.title`).
  - **AC-309** *(Từ chối)* Khi `GET /api/v1/me/attempts/{id}` trả 404 hoặc mã `ATTEMPT_NOT_FOUND`, thì hiện "Không tìm thấy lượt thi" (`attempt.errors.notFoundTitle`), `attempt.errors.notFoundDescription` và nút "Về Bài thi của tôi", không có nút Thử lại.
  - **AC-310** *(Từ chối)* Khi lời gọi lỗi khác (5xx, 403, mất mạng), thì hiện "Không tải được lượt thi" (`attempt.errors.loadTitle`), `attempt.errors.loadDescription`, nút "Thử lại" (`attempt.errors.retry`) và "Về Bài thi của tôi"; bấm Thử lại thì tải lại toàn bộ màn.
  - **AC-311** Khi lượt ở `STARTING` hoặc `ABORTED`, mở màn làm bài thì hiện màn chờ của R-76 (tên bài lấy từ trạng thái lượt); lượt chuyển sang đang làm thì màn làm bài tự tải lại.
  - **AC-312** Khi lượt đã đóng, mở màn làm bài thì hiện "Đã hết giờ làm bài" (`attempt.closed.headingTimeUp`) nếu lượt đóng vì hết giờ, "Đã quá thời gian chọn phần 3" (`attempt.closed.headingChoiceTimeout`) nếu đóng vì quá hạn chọn, còn lại "Lượt thi đã đóng" (`attempt.closed.heading`); kèm `attempt.closed.description`, nút "Xem kết quả" (mở màn kết quả, xem mục 6.6) và "Về Bài thi của tôi"; bản nháp của lượt trên máy bị xóa.
  - **AC-313** Khi lượt đang làm, mở màn thì app nạp bản nháp trên máy (R-95), rồi mới nhận phiên sửa (R-78); trong lúc chưa nhận xong phiên, câu trả lời vẫn chọn và gõ được (chỉ ghi vào bản nháp, không có lời gọi lưu), các nút Hoàn thành phần, Nộp bài và trình phát âm thanh bị khóa.

### R-78 Nhận phiên sửa và nối lại trên cùng máy

- Nguồn: `features/attempt-player/edit-session.ts:311-367`; `features/attempt-draft/edit-session-storage.ts:8-22`; `attempt/application/EditSessionService.java:64-158`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-314** Khi mở màn làm bài lần đầu trên máy (chưa lưu `sessionId` nào cho tài khoản và lượt này), thì app tự sinh một `sessionId` dạng UUID, lưu nó trên máy theo tài khoản và lượt trước khi gọi, rồi gửi `POST /api/v1/me/attempts/{id}/session` với `sessionId` đó; máy chủ trả cùng `sessionId` và `serverNow`, đồng hồ neo theo `serverNow` đó. Mọi lần gửi lại sau lỗi dùng đúng `sessionId` đã lưu, nên một lần nhận phiên đã thành công ở máy chủ mà mất phản hồi không làm hiện hộp thoại tiếp quản.
  - **AC-315** Khi đã lưu `sessionId` cho lượt này và chưa máy nào tiếp quản, mở lại màn làm bài (kể cả sau khi tắt app hoặc khởi động lại máy) thì app gửi `sessionId` đã lưu, máy chủ trả cùng `sessionId`, không hiện hộp thoại tiếp quản.
  - **AC-316** Khi nhận phiên xong, thì mọi lời gọi lưu, nhịp, hoàn thành phần, nộp bài, chọn phần, thử lại sinh phần đều mang header `X-Attempt-Session` bằng `sessionId` đó; câu trả lời đã sửa trong lúc chờ nhận phiên được gửi ngay.
  - **AC-317** *(Từ chối)* Khi nhận phiên trả 409 `TIME_UP` hoặc `ATTEMPT_NOT_OPEN`, thì app tải lại trạng thái lượt và hiện màn của AC-312.
  - **AC-318** *(Từ chối)* Khi nhận phiên lỗi mạng hoặc 5xx, thì màn làm bài vẫn mở và câu trả lời vẫn sửa được (chỉ ghi vào bản nháp); app không gửi lời gọi lưu, hoàn thành phần, nộp bài, chọn phần hay thử lại sinh phần nào; các nút Hoàn thành phần, Nộp bài bị khóa; ô trạng thái hiện "Chưa lưu · Lỗi kết nối" (`attempt.saveStatus.reasons.network_error`) hoặc "Chưa lưu · Máy chủ bận" (`attempt.saveStatus.reasons.server_error`) với 5xx, hay "Chưa lưu · Mất kết nối" khi máy mất mạng. App gửi lại nhận phiên với cùng `sessionId` sau 1, 2, 4, 8, 16, 30 giây rồi giữ 30 giây mỗi lần, và gửi ngay khi có mạng lại; nhận được phiên thì các nút mở lại và thay đổi chưa lưu được gửi theo AC-316; nhận 409 `ATTEMPT_OPEN_ELSEWHERE` thì hiện hộp thoại của AC-319.

### R-79 Hộp thoại đang mở ở phiên khác và tiếp quản

- Nguồn: `features/attempt-player/takeover-dialog.tsx:35-87`; `attempt-player.tsx:655-669,2488-2507`; `EditSessionService.java:139-157`.
- Áp dụng cho: lượt đang giữ phiên ở máy khác hoặc ở web.
- Tiêu chí chấp nhận:
  - **AC-319** Khi nhận phiên trả 409 `ATTEMPT_OPEN_ELSEWHERE`, thì hiện hộp thoại "Bài thi đang mở ở một phiên khác" (`attempt.takeover.title`) với `attempt.takeover.description`, "Bài thi: {tên bài}", "Phiên kia hoạt động lần cuối: {ngày giờ}" từ `lastSeenAt` theo định dạng ngày giờ của ngôn ngữ đang dùng và theo múi `Asia/Ho_Chi_Minh`, không theo múi giờ của máy (như web: `takeover-dialog.tsx:15-23` dùng múi chung của web đặt ở `i18n/request.ts:28`, `packages/web-kit/src/preferences.ts:17`); không có `lastSeenAt` thì bỏ dòng, `attempt.takeover.noteClock`, `attempt.takeover.noteUnsaved`, nút "Hủy" và "Tiếp tục tại đây" (`attempt.takeover.confirm`); nội dung sau hộp thoại ở trạng thái chỉ đọc, âm thanh dừng.
  - **AC-320** Khi hộp thoại đang hiện, bấm "Tiếp tục tại đây" thì app gửi nhận phiên với `takeover: true` (kèm `sessionId` đã lưu nếu có), hai nút bị khóa trong lúc gửi; thành công thì app lưu `sessionId` mới, tải lại trạng thái lượt, đóng hộp thoại và cho sửa bài.
  - **AC-321** Khi hộp thoại đang hiện, bấm "Hủy" hoặc đóng hộp thoại bằng cử chỉ hệ thống, thì app rời màn làm bài về Bài thi của tôi; không gửi gì lên máy chủ; máy kia vẫn giữ phiên.
  - **AC-322** *(Giới hạn)* Khi máy kia đã im lặng lâu (không còn nhịp), mở lượt trên máy này thì hộp thoại vẫn hiện: phiên sửa không tự hết hạn, học viên phải bấm "Tiếp tục tại đây".
  - **AC-323** Khi hộp thoại hoàn thành phần hoặc nộp bài đang mở mà có hộp thoại tiếp quản, thì hai hộp thoại kia đóng.

### R-80 Nhịp giữ phiên và bị đẩy ra

- Nguồn: `edit-session.ts:14,369-415,434-446`; `features/attempt-player/save-queue.ts:844-849`; `takeover-dialog.tsx:94-133`; `attempt-player.tsx:1682-1701`.
- Áp dụng cho: lượt `IN_PROGRESS`, app đang giữ phiên.
- Tiêu chí chấp nhận:
  - **AC-324** Khi đang giữ phiên và app ở trước mặt người dùng, thì app gửi `POST /api/v1/me/attempts/{id}/heartbeat` 30 giây một lần; mỗi phản hồi neo lại đồng hồ theo `serverNow`.
  - **AC-325** Khi phản hồi nhịp có `state` khác `IN_PROGRESS`, thì app ngừng gửi nhịp, dừng âm thanh, tải lại trạng thái và hiện màn của AC-312.
  - **AC-326** *(Từ chối)* Khi máy khác hoặc web tiếp quản lượt, thì ở lần nhịp hoặc lần lưu kế tiếp nhận 409 `ATTEMPT_SESSION_REVOKED` (hoặc `ATTEMPT_OPEN_ELSEWHERE`), app ngừng gửi mọi thứ, dừng âm thanh và thay màn bằng "Lượt thi đã được tiếp tục ở thiết bị khác." (`attempt.evicted.heading`), `attempt.evicted.description`, nút "Về Bài thi của tôi" và "Tiếp tục tại đây" (`attempt.evicted.takeoverHere`). Thời gian từ lúc bị tiếp quản tới lúc thấy màn này không quá 30 giây khi app đang mở và có mạng.
  - **AC-327** Khi ở màn bị đẩy ra, bấm "Tiếp tục tại đây" thì app gửi nhận phiên với `takeover: true`, nút bị khóa trong lúc gửi; thành công thì tải lại trạng thái và về màn làm bài; máy kia lại bị đẩy ra theo AC-326.
  - **AC-328** *(Từ chối)* Khi chọn phần, thử lại sinh phần, hoàn thành phần hoặc nộp bài nhận `ATTEMPT_SESSION_REVOKED`, thì app đóng hộp thoại đang mở và hiện màn bị đẩy ra.
  - **AC-329** Khi một lần nhịp lỗi mạng hoặc 5xx, thì không có thông báo riêng; lần nhịp sau vẫn gửi đúng lịch.

### R-81 Trạng thái chỉ đọc

- Nguồn: `attempt-player.tsx:1114-1123,2299-2334,2408-2412`; `part-choice-view.tsx:282`.
- Áp dụng cho: màn làm bài.
- Tiêu chí chấp nhận:
  - **AC-330** Khi đang hiện hộp thoại tiếp quản, đã bị đẩy ra, lượt không còn `IN_PROGRESS` hoặc phần đang xem không ở `OPEN`, chạm vào đáp án, ô nhập, nút đánh dấu hoặc lựa chọn phần thì không có gì thay đổi và không có lời gọi lưu.
  - **AC-331** Khi ở trạng thái chỉ đọc vì phiên (tiếp quản, bị đẩy ra), xem thanh trên thì nút Hoàn thành phần, Nộp bài bị khóa và danh sách phần không bấm được; bảng câu hỏi vẫn mở và chuyển câu được.

### R-82 Thanh trên của màn làm bài

- Nguồn: `attempt-player.tsx:2042-2047,2193-2377`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-332** Khi màn làm bài mở, xem thanh trên thì thấy tên bài, danh sách phần (R-84), đồng hồ (R-83), ô trạng thái lưu (R-92), nút "Hoàn thành phần" (`attempt.header.finishPart`) hoặc "Nộp bài" (`attempt.header.submit`) theo R-96, R-97, và nút thu gọn; thanh trên luôn nằm trên cùng khi cuộn nội dung.
  - **AC-333** Khi bấm nút thu gọn (`attempt.header.collapseHeader`), thì tên bài và danh sách phần ẩn, chỉ còn tên phần đang làm (tên phương án đã chọn nếu có), đồng hồ, ô trạng thái lưu và nút; bấm lại (`attempt.header.expandHeader`) thì hiện đủ.
  - **AC-334** Khi mất mạng, xem thanh trên thì dưới thanh có dải "Mất kết nối. Câu trả lời mới nhất chưa được lưu. Thời gian làm bài vẫn tiếp tục." (`attempt.player.offlineBanner`); có mạng lại thì dải biến mất.

### R-83 Đồng hồ theo giờ máy chủ

- Nguồn: `features/attempt-player/server-clock.ts:13-80`; `attempt-player.tsx:975-1003,1771-1780,2255-2282`; `prepare.instructions.noteClock`.
- Áp dụng cho: lượt `IN_PROGRESS` có hạn.
- Tiêu chí chấp nhận:
  - **AC-335** Khi `timing = SHARED`, xem đồng hồ thì nó đếm về `deadlineAt` của lượt; khi `timing = PER_PART` thì đếm về `deadlineAt` của phần đang làm và chỉ hiện khi phần đó `OPEN`. Không có hạn thì không hiện đồng hồ.
  - **AC-336** Khi đồng hồ chạy, xem số thì định dạng là `mm:ss` khi còn dưới 1 giờ, `h:mm:ss` khi từ 1 giờ, giây làm tròn lên, cập nhật mỗi 1 giây; trình đọc màn hình đọc "Cả bài còn: {minutes} phút {seconds} giây" hoặc "Phần {ord} còn: …" (`attempt.timer.sharedRemaining`, `attempt.timer.partRemaining`, `attempt.timer.ariaLabel`, `attempt.timer.ariaLabelShort`).
  - **AC-337** *(Giới hạn)* Khi thời gian còn lại ≤ 60 giây, thì đồng hồ đổi sang màu nguy hiểm.
  - **AC-338** Khi đổi giờ hoặc múi giờ của máy trong lúc làm bài, thì thời gian còn lại không đổi (sai lệch không quá 1 giây so với trước khi đổi).
  - **AC-339** Khi app nhận `serverNow` mới (trạng thái lượt, nhận phiên, nhịp, lưu), thì đồng hồ neo lại theo giá trị đó; sau một lần tải trạng thái, thời gian hiển thị lệch với `deadlineAt − serverNow` không quá 1 giây.

### R-84 Danh sách phần và cách chuyển phần

- Nguồn: `features/attempt-player/part-navigation.tsx:27-179`; `attempt-player.tsx:232-258,554-563,2218-2250`; `attempt/application/AttemptNavigationPolicy.java:49-70`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-340** Khi `navigationMode = SEQUENTIAL`, xem danh sách phần thì mỗi phần theo thứ tự hiện tên (ưu tiên tên phương án đã chọn, rồi `partName`, `partCode`, "#{ord}") và trạng thái của nó; phần đang làm được làm nổi, phần `CLOSED`/`SKIPPED` có dấu xong, phần khác có khóa; chạm vào không chuyển phần. Cách hiện trạng thái theo quyết định D-10, khác web ở hai chỗ: phần `CLOSED` chỉ có dấu tích xanh và tên, không có dòng chữ "Đã hoàn thành"; phần `OPEN` hiện tiến độ của phần đó thay cho chữ "Đang làm", gồm "{answered}/{total} câu" và một thanh tiến độ, trong đó `answered` là số câu lá đã trả lời đủ của phần (cùng cách đếm với AC-344 và AC-345) và `total` là số câu lá của phần. Các trạng thái còn lại (`PENDING`, `AWAITING_CHOICE`, `GENERATING`, `GENERATION_FAILED`, `SKIPPED`) vẫn hiện nhãn `attempt.parts.states.<trạng thái>` như web. Trình đọc màn hình đọc đủ trạng thái của mọi phần: phần `CLOSED` đọc "{tên phần}, Đã hoàn thành" (`attempt.parts.states.CLOSED`), phần `OPEN` đọc "{tên phần}, Đang làm" rồi tiến độ theo `attempt.nav.progressLabel`.
  - **AC-341** Khi `navigationMode = FREE`, chạm một phần `OPEN` khác phần đang xem thì app chuyển sang phần đó, về câu đầu tiên, ghi vị trí mới và gửi lưu ngay; phần không `OPEN` thì không chạm được (phần `CLOSED` có dấu xong, phần còn lại có khóa). Mỗi phần `OPEN`, kể cả phần không đang xem, hiện tiến độ riêng của nó theo AC-340; trả lời thêm một câu thì con số của phần đó đổi ngay, không chờ lưu xong.
  - **AC-342** Khi phần đang xem chuyển khỏi `OPEN` (đóng vì hết giờ, hoàn thành, máy chủ đóng), thì app chuyển sang phần chưa mở xong đầu tiên (kể cả phần `GENERATION_FAILED`, nơi có nút "Thử lại" của AC-366) nếu nó đứng trước phần `OPEN` đầu tiên hoặc không có phần `OPEN` nào; không thì phần `OPEN` đầu tiên; không có cả hai thì phần đầu tiên; và về câu đầu.

### R-85 Bảng câu hỏi

- Nguồn: `features/attempt-player/question-nav.tsx:57-427`; `attempt-player.tsx:1295-1363`; `features/attempt-player/mappers.ts:273-366`; màu ô đánh dấu: `question-nav.tsx:121-161`, `packages/ui/src/styles/tokens.css:24,63-66,241-242` (D-29).
- Áp dụng cho: phần đang xem ở `OPEN` có câu hỏi.
- Tiêu chí chấp nhận:
  - **AC-343** Khi mở bảng câu hỏi, thì mỗi câu lá của phần đang xem có một ô mang số câu; ô thể hiện riêng 3 mức hoàn thành (đã trả lời, trả lời một phần, chưa trả lời), chấm "chưa lưu" khi câu có thay đổi chưa lưu, và viền "đang xem" ở câu hiện tại. Ô của câu được đánh dấu tô màu đỏ như web (D-29) và vẫn phân biệt 3 mức hoàn thành: đã trả lời thì nền `--color-danger-solid`, chữ số `--color-on-danger`; trả lời một phần thì viền `--color-danger` nét đứt, nền `--color-danger-bg`, chữ số `--color-danger`; chưa trả lời thì viền `--color-danger` nét liền, nền `--color-danger-bg`, chữ số `--color-danger`; góc dưới bên phải của ô có hình cờ nhỏ cùng màu với chữ số. Ô không được đánh dấu giữ màu như bản vẽ; trình đọc màn hình đọc "{số}, đang xem, đã trả lời, đã lưu, đánh dấu xem lại" theo các khóa `attempt.nav.state*`.
  - **AC-344** Khi câu điền khuyết, Đúng/Sai hoặc ghép đôi mới điền một số chỗ, xem ô của câu thì ở mức "trả lời một phần"; điền đủ thì "đã trả lời"; câu chọn, trả lời ngắn, tự luận chỉ có 2 mức (tự luận tính theo số chữ > 0).
  - **AC-345** Khi xem bảng, thì đầu bảng có "{answered}/{total} câu đã trả lời" và 3 bộ lọc "Tất cả", "Chưa làm" (gồm cả trả lời một phần), "Đánh dấu"; cuối bảng có chú giải 6 mục `attempt.nav.legendCurrent|legendAnswered|legendPartial|legendBlank|legendUnsaved|legendFlagged`.
  - **AC-346** *(Giới hạn)* Khi bộ lọc không còn câu nào, thì bảng hiện "Không có câu nào khớp bộ lọc." (`attempt.nav.emptyFiltered`).
  - **AC-347** Khi chạm một ô, thì nội dung cuộn tới câu đó, câu đó thành câu đang xem, vị trí được ghi (R-88); trên màn hẹp bảng đóng lại.
  - **AC-348** Khi ở màn hẹp, xem nội dung thì có thanh "{answered}/{total} câu đã trả lời" kèm nút "{current}/{total}" (`attempt.nav.openGridCompact`, nhãn đọc `attempt.nav.openGrid`); chạm nút thì bảng mở thành tấm phủ có tiêu đề "Danh sách câu hỏi" và nút "Đóng danh sách câu" (`attempt.nav.closeGrid`). Khi ở màn rộng, bảng là cột cố định bên cạnh nội dung, không có thanh đó.
  - **AC-349** Khi cuộn nội dung, thì câu đang xem (ô có viền và số "{current}") đổi theo câu nằm ở đầu vùng nội dung.

### R-86 Gom câu theo đoạn hoặc theo môn

- Nguồn: `features/attempt-player/segments/segment-units.ts:283-330`; `attempt-player.tsx:1163-1264,2414-2478`; `question-nav.tsx:86-89,211-250`.
- Áp dụng cho: phần đang xem.
- Tiêu chí chấp nhận:
  - **AC-350** Khi phần có `displaySegments`, xem nội dung và bảng câu hỏi thì câu được gom theo đoạn, mỗi nhóm trong bảng có tên đoạn (hoặc mã), câu không thuộc đoạn nào nằm dưới "Câu hỏi khác" (`attempt.segments.ungrouped`); đầu đoạn trong nội dung xem mục 6.5.
  - **AC-351** Khi phần không có `displaySegments` mà câu thuộc từ 2 mã môn trở lên (hoặc lựa chọn của phần có từ 2 môn), xem nội dung thì mỗi môn liền nhau thành một nhóm với tiêu đề "Môn {name}" (`attempt.subjects.heading`), "Câu {from}–{to}" và "{count} câu"; bảng câu hỏi gom theo cùng các nhóm đó. Chỉ một môn thì không gom.
  - **AC-352** Khi mã môn có trong `attempt.subjects.codes`, thì tên môn lấy theo khóa đó; mã lạ thì app lấy tên `nameVi`/`nameEn` theo ngôn ngữ từ danh sách môn của bài (qua màn chuẩn bị của quyền dùng); không tìm được thì hiện nguyên mã.

### R-87 Đánh dấu xem lại

- Nguồn: `attempt-player.tsx:1286-1293`; `save-queue.ts:394-415`.
- Áp dụng cho: câu lá ở phần `OPEN`, không chỉ đọc.
- Tiêu chí chấp nhận:
  - **AC-353** Khi chạm nút đánh dấu của một câu (vị trí nút xem mục 6.5), thì ô của câu trong bảng đổi ngay sang có cờ, và app gửi `flags: [{itemId, flagged: true}]` ngay, không chờ; chạm lần nữa thì gửi `flagged: false`.
  - **AC-354** Khi bật rồi tắt cờ trước khi lời gọi đầu gửi đi, thì không còn thay đổi cờ chưa lưu cho câu đó (cờ trùng cờ máy chủ).

### R-88 Nhớ vị trí

- Nguồn: `save-queue.ts:417-443`; `attempt-player.tsx:232-258,768,779-802,1333-1363`; `attempt/application/AnswerService.java:423-470`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-355** Khi chuyển câu (bảng câu hỏi, chuyển phần), thì app ghi `{partId, itemId}` trên máy ngay và gửi `position` lên máy chủ sau 400 ms; nếu còn câu trả lời chưa lưu thì gửi tất cả ngay trong cùng lần lưu.
  - **AC-356** Khi mở lại lượt, thì app lấy phần trong `lastPosition` của máy chủ, máy chủ không có `lastPosition` thì lấy phần trong vị trí trên máy; phần lấy được còn `OPEN` hoặc là phần chưa mở xong (kể cả `GENERATION_FAILED`) thì mở phần đó, không thì chọn phần theo AC-342; câu đang xem là `itemId` của máy chủ, nếu không có thì của máy, và nội dung cuộn tới câu đó.
  - **AC-357** Khi học viên làm trên máy A đến câu 12 rồi mở cùng lượt trên máy B (đã tiếp quản), thì máy B mở ở câu 12.
  - **AC-717** *(Giới hạn)* Khi chuyển câu ngay trước khi phần đóng (còn dưới 400 ms), thì `position` gửi sau lúc phần đóng bị máy chủ bỏ qua không báo lỗi (`AnswerService.java:423-470`), nên `lastPosition` của máy chủ vẫn là câu trước đó; mở lại lượt thì phần được chọn theo AC-356 như web.

### R-89 Chọn phần sau khi bắt đầu

Các chuỗi của luồng chọn phần (`attempt.partChoice.title`, `confirmCta`, `deadlineWarning`, `deadlineExpired`, `errors.CHOICE_WINDOW_CLOSED`) cùng `attempt.closed.headingChoiceTimeout` và `attempt.dialogs.finishPart.nextPartChoiceNote` ghi cứng "phần 3"/"thứ ba" như web. App hiện nguyên văn các chuỗi này ở mọi bài, kể cả bài có phần tự chọn không phải phần thứ ba; các tiêu chí dưới đây chỉ kiểm chuỗi hiện ra, không khẳng định câu chữ đúng với vị trí thật của phần.

- Nguồn: `features/attempt-player/part-choice-view.tsx:72-418`; `attempt-player.tsx:948-965,1005-1055,1791-1846`; `features/attempt-player/api.ts:266-301`; `attempt/application/AwaitingChoicePartTransitionHook.java:19,26`.
- Áp dụng cho: phần đang xem ở `AWAITING_CHOICE`.
- Tiêu chí chấp nhận:
  - **AC-358** Khi phần ở `AWAITING_CHOICE`, xem màn thì thay cho câu hỏi là khối "Chọn phần thi thứ ba" (`attempt.partChoice.title`) với `attempt.partChoice.description`, tên phần, đồng hồ đếm về `choiceDeadlineAt` theo giờ máy chủ (nhãn đọc `attempt.partChoice.timerLabel`), danh sách phương án (mã, tên, "Số câu mỗi môn: {counts}" khi có `countsByPick`, danh sách môn khi phương án không đòi chọn môn) và nút "Xác nhận chọn phần 3" (`attempt.partChoice.confirmCta`).
  - **AC-359** Khi app chưa có danh sách phương án, thì app lấy `entitlementId` từ ngữ cảnh bắt đầu trên máy, nếu không có thì từ `GET /api/v1/me/exams` (thẻ có `activeAttemptId` trùng lượt), rồi gọi `GET …/entitlements/{id}/prepare` và dùng `afterStartChoices` của phần cùng `ord` hoặc `partCode`.
  - **AC-718** *(Từ chối)* Khi không lấy được danh sách phương án (không có ngữ cảnh bắt đầu và không thẻ nào trong `GET /api/v1/me/exams` có `activeAttemptId` trùng lượt, một trong hai lời gọi lỗi hoặc mất mạng, màn chuẩn bị trả 404, hoặc `afterStartChoices` không có phần khớp), thì trong khối chọn phần, thay cho danh sách là "Không tải được thông tin chuẩn bị bài thi" (`prepare.errorLoad.title`), `prepare.errorLoad.description`, nút "Thử lại" (`common.retry`) chạy lại AC-359 và nút "Về Bài thi của tôi"; đồng hồ hạn chọn vẫn chạy và AC-363 vẫn áp dụng; không có lời gọi chọn phần nào.
  - **AC-360** Khi chỉ có 1 phương án, thì nó được chọn sẵn; phương án đòi chọn N môn thì hiện khối "Chọn môn tự chọn" với "Đã chọn {selected}/{N}", đủ N thì môn khác bị khóa và hiện "Đã đủ {N} môn. Bỏ chọn một môn để chọn môn khác." (`attempt.partChoice.subjectsLimitReached`); nút xác nhận chỉ bấm được khi lựa chọn đủ.
  - **AC-361** Khi bấm xác nhận, thì app gửi `POST …/parts/{partId}/choice` với `Idempotency-Key`, `X-Attempt-Session` và `{optionCode, subjectCodes?}`; nút và các ô bị khóa trong lúc gửi; thành công thì tải lại trạng thái và phần chuyển sang đang sinh (R-90) hoặc mở.
  - **AC-362** *(Giới hạn)* Khi gửi lại cùng lựa chọn sau một lần lỗi mạng, thì `Idempotency-Key` giữ nguyên; đổi phương án hoặc môn rồi gửi thì dùng khóa mới.
  - **AC-363** *(Giới hạn)* Khi còn ≤ 30 giây tới hạn chọn, thì đồng hồ đổi màu nguy hiểm và hiện "Quá thời gian chọn mà chưa xác nhận thì lượt thi kết thúc, phần 3 được 0 điểm." (`attempt.partChoice.deadlineWarning`); hết hạn thì đồng hồ thay bằng "Đã hết thời gian chọn phần 3." (`attempt.partChoice.deadlineExpired`), nút xác nhận bị khóa, app tải lại trạng thái sau 500 ms và sau đó cứ 2 giây một lần tới khi phần đổi trạng thái; lượt đóng thì hiện màn của AC-312 với tiêu đề "Đã quá thời gian chọn phần 3".
  - **AC-364** *(Từ chối)* Khi máy chủ trả `CHOICE_WINDOW_CLOSED`, `CHOICE_LOCKED`, `PART_NOT_AWAITING`, `PART_GENERATING`, `CHOICE_NOT_ALLOWED`, `SUBJECTS_INVALID`, thì trong khối hiện câu `attempt.partChoice.errors.<mã>` (mã khác: `attempt.partChoice.errors.generic`); với 4 mã đầu app tải lại trạng thái; `CHOICE_WINDOW_CLOSED` thì lý do đóng là quá hạn chọn.

### R-90 Sinh phần và thử lại

- Nguồn: `part-choice-view.tsx:134-177`; `attempt-player.tsx:967-973,1848-1888`; `attempt/application/PartGenerationRetryWorker.java:41-43`.
- Áp dụng cho: phần ở `GENERATING` hoặc `GENERATION_FAILED`.
- Tiêu chí chấp nhận:
  - **AC-365** Khi phần đang xem ở `GENERATING`, thì hiện "Đang chuẩn bị bộ câu hỏi cho phần thi" (`attempt.partChoice.generatingTitle`), `attempt.partChoice.generatingDescription` và tên phần; app tải lại trạng thái 1,5 giây một lần; phần chuyển `OPEN` thì câu hỏi hiện và đồng hồ phần bắt đầu.
  - **AC-366** Khi phần ở `GENERATION_FAILED`, thì hiện "Chưa chuẩn bị được phần thi" (`attempt.partChoice.failedTitle`), `attempt.partChoice.failedDescription` và nút "Thử lại"; bấm thì app gửi `POST …/parts/{partId}/retry` với khóa mới cho lần bấm đó (thử lại sau lỗi thuộc loại nêu ở AC-17 gửi lại cùng khóa), nút quay và bị khóa trong lúc gửi, xong thì tải lại trạng thái.
  - **AC-367** *(Từ chối)* Khi thử lại bị từ chối, thì lỗi hiện theo AC-364.

### R-91 Khi nào gửi thay đổi

- Nguồn: `save-queue.ts:19-25,92-102,322-443,633-812`; `attempt/api/SaveAnswersRequest.java:13,20`; `attempt/application/AttemptSaveRateLimiter.java:264-265`.
- Áp dụng cho: câu lá ở phần `OPEN`, app giữ phiên.
- Tiêu chí chấp nhận:
  - **AC-368** Khi sửa câu trả lời, thì `seq` của câu đó trên máy tăng 1, ô của câu có chấm "chưa lưu" và ô trạng thái hiện "Đang lưu..." (`attempt.saveStatus.saving`) tới khi máy chủ xác nhận.
  - **AC-369** Khi chọn đáp án câu một đáp án, nhiều đáp án, Đúng/Sai hoặc ghép đôi, thì app gửi `PUT /api/v1/me/attempts/{id}/answers` ngay; với trả lời ngắn và điền khuyết, app gửi 800 ms sau lần gõ cuối; với tự luận, 2 giây sau lần gõ cuối và không quá 15 giây kể từ thay đổi đầu tiên chưa gửi dù vẫn gõ liên tục; chữ còn đang được bộ gõ ghép chưa tính là thay đổi để gửi (xem mục 6.5).
  - **AC-370** Khi gửi, thì mỗi phần tử `changes` có `{itemId, seq, answer}` với `seq` là giá trị mới nhất trên máy; bỏ hết đáp án của một câu thì gửi giá trị rỗng của đúng dạng câu như web (`{type: "SINGLE_CHOICE"}` không có `optionId`, `optionIds: []`, `text: ""`, `blanks: {}`, `statements: {}`, `pairs: {}`, `html: ""`; `features/attempt-player/mappers.ts:128-181`), máy chủ nhận giá trị đó là hợp lệ và trả `SAVED` (`attempt/application/AnswerValueValidator.java:50-82`); app không gửi `answer: null`.
  - **AC-371** *(Giới hạn)* Khi có hơn 50 câu hoặc hơn 50 cờ chưa lưu, thì app chia thành nhiều lời gọi, mỗi lời gọi ≤ 50 thay đổi và ≤ 50 cờ, `position` chỉ ở lời gọi cuối; các lời gọi đi lần lượt, không chồng nhau.
  - **AC-372** *(Giới hạn)* Khi một lời gọi lưu đang chờ phản hồi mà học viên sửa thêm câu (ví dụ chọn liên tục 30 đáp án trong 10 giây), thì không bao giờ có hai lời gọi `PUT …/answers` chồng nhau; mọi thay đổi phát sinh trong lúc chờ được gộp vào lời gọi kế tiếp, gửi sau khi lời gọi trước có phản hồi (`save-queue.ts:635-656,802-809`). Máy chủ trả 429 thì xử lý theo AC-378.

### R-92 Kết quả lưu và trạng thái hiển thị

- Nguồn: `save-queue.ts:289-320,716-783,814-830`; `attempt-player.tsx:2000-2014,2284-2297`; `attempt/application/AnswerService.java:305-418`.
- Áp dụng cho: mọi lời gọi lưu.
- Tiêu chí chấp nhận:
  - **AC-373** Khi máy chủ trả `SAVED` cho `seq` bằng `seq` mới nhất trên máy, thì chấm "chưa lưu" của câu biến mất, bản nháp của câu bị xóa; không còn thay đổi chưa lưu nào thì ô trạng thái hiện "Đã lưu" (`attempt.saveStatus.saved`).
  - **AC-374** Khi máy chủ trả `SAVED` cho `seq` cũ hơn vì học viên đã sửa tiếp trong lúc gửi, thì câu vẫn "chưa lưu" và app gửi `seq` mới ở lần kế tiếp.
  - **AC-375** Khi máy chủ trả `STALE` (máy chủ đã có `seq` lớn hơn, ví dụ từ máy khác), thì app tải lại trạng thái lượt và câu đó hiện bản của máy chủ nếu `seq` máy chủ ≥ `seq` trên máy.
  - **AC-376** *(Từ chối)* Khi máy chủ trả `REJECTED` với `INVALID_ANSWER` hoặc `NOT_IN_SCOPE`, thì câu đó không bị gửi lại tới khi học viên sửa nó, ô trạng thái hiện "Chưa lưu · Dữ liệu không hợp lệ" (`attempt.saveStatus.unsaved`, `attempt.saveStatus.reasons.rejected`) tới lần lưu thành công kế tiếp hoặc lần sửa kế tiếp.
  - **AC-377** *(Từ chối)* Khi máy chủ trả `REJECTED` với `TIME_UP` hoặc `PART_CLOSED`, thì câu đó bị khóa không sửa được, dừng âm thanh, app tải lại trạng thái lượt (phần đóng thì chuyển theo AC-342; lượt đóng thì AC-312).

### R-93 Lỗi lưu và thử lại

- Nguồn: `save-queue.ts:25,832-891`; `features/attempt-player/api.ts:57-70,128-145`; `AttemptExceptionAdvice.java:50-61`.
- Áp dụng cho: lời gọi lưu lỗi.
- Tiêu chí chấp nhận:
  - **AC-378** *(Giới hạn)* Khi máy chủ trả 429 kèm `Retry-After: n`, thì ô trạng thái hiện "Chưa lưu · Chờ {n} giây" (`attempt.saveStatus.reasons.rate_limited`) và app gửi lại sau n giây (tối thiểu 1 giây); không có header thì chờ theo nấc lùi của AC-380.
  - **AC-379** *(Từ chối)* Khi máy chủ trả 5xx, thì ô trạng thái hiện "Chưa lưu · Máy chủ bận" (`attempt.saveStatus.reasons.server_error`); lỗi 4xx khác 401, 409, 429 hoặc lỗi kết nối khi máy vẫn báo có mạng thì hiện "Chưa lưu · Lỗi kết nối" (`attempt.saveStatus.reasons.network_error`).
  - **AC-380** *(Giới hạn)* Khi lỗi lặp lại ở AC-379, thì app gửi lại sau 1, 2, 4, 8, 16, 30 giây rồi giữ 30 giây mỗi lần; một lần gửi thành công hoặc có mạng lại thì nấc lùi về 1 giây.
  - **AC-381** *(Từ chối)* Khi máy chủ trả 409 `TIME_UP`, `ATTEMPT_NOT_OPEN` hoặc `PART_CLOSED` cho cả lời gọi, thì app dừng âm thanh và tải lại trạng thái lượt; 409 `ATTEMPT_SESSION_REVOKED` hoặc `ATTEMPT_OPEN_ELSEWHERE` thì ô trạng thái hiện "Chưa lưu · Mở ở nơi khác" (`attempt.saveStatus.reasons.session_conflict`) và app hiện màn bị đẩy ra (AC-326).

### R-94 Mất mạng và có mạng lại

- Nguồn: `save-queue.ts:298-300,355-357,450-514`; `attempt-player.tsx:741-755,2357-2365`; `prepare.instructions.noteOffline`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-382** Khi máy mất mạng, làm tiếp thì vẫn chọn, gõ, đánh dấu, chuyển câu được; không có lời gọi lưu; câu đã sửa có chấm "chưa lưu"; ô trạng thái hiện "Chưa lưu · Mất kết nối" (`attempt.saveStatus.reasons.offline`) khi còn thay đổi chưa lưu; dải mất kết nối của AC-334 hiện; đồng hồ vẫn chạy.
  - **AC-383** Khi có mạng lại, thì app đặt lại nấc lùi, gọi `GET /api/v1/me/attempts/{id}`, đối chiếu từng câu theo R-95, rồi gửi mọi thay đổi còn lại và vị trí; không còn gì chưa lưu thì ô hiện "Đã lưu".
  - **AC-384** Khi mất mạng mà hết giờ, rồi có mạng lại, thì lần đọc trạng thái cho thấy lượt hoặc phần đã đóng; câu thuộc phần đã đóng hiện bản của máy chủ, thay đổi chưa lưu của phần đó bị bỏ, bản nháp của chúng bị xóa.

### R-95 Bản nháp trên máy và bản nào thắng

- Nguồn: `save-queue.ts:168-240,571-631,931-951`; `features/attempt-draft/draft-store.ts:21-193`; `attempt-player.tsx:546-552,1966`; `features/session/api.ts:67-78`; `features/home/student-user-menu.tsx:42-50`; xóa theo tài khoản khi đăng xuất và giữ ngữ cảnh bắt đầu của lượt `ABORTED` là lựa chọn của đặc tả (xem A-107, A-108).
- Áp dụng cho: lượt `IN_PROGRESS`; tài khoản đang đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-385** Khi sửa một câu, thì trước lần gửi đầu tiên app đã ghi bản nháp `{seq, answer, flagged}` của câu vào bộ nhớ của app trên máy, gắn với tài khoản, lượt và câu; tắt hẳn app ngay sau khi sửa thì bản nháp vẫn còn.
  - **AC-386** Khi mở lại lượt mà bản nháp của một câu có `seq` lớn hơn `answerSeq` máy chủ, thì câu hiện bản nháp, có chấm "chưa lưu" và được gửi lên ngay khi nhận phiên xong; `seq` nháp ≤ `answerSeq` thì câu hiện bản của máy chủ và bản nháp bị xóa khi đối chiếu.
  - **AC-387** Khi bản nháp có cờ khác cờ máy chủ, thì cờ nháp được dùng và gửi lên; trùng thì không gửi.
  - **AC-388** Khi câu thuộc phần không còn `OPEN`, thì câu luôn hiện bản của máy chủ, không gửi bản nháp.
  - **AC-389** Khi lượt đã đóng hoặc nộp xong, thì mọi bản nháp, vị trí, `sessionId` và ngữ cảnh bắt đầu của lượt trên máy bị xóa. Khi lượt `ABORTED`, thì bản nháp, vị trí và `sessionId` của lượt bị xóa nhưng ngữ cảnh bắt đầu còn lại (kể cả sau khi tắt app) để "Thử lại" của AC-305, AC-306 dùng được; ngữ cảnh đó bị xóa khi một lần "Thử lại" tạo được lượt mới (ngữ cảnh được lưu sang lượt mới) hoặc khi tài khoản đăng xuất.
  - **AC-390** *(Từ chối)* Khi tài khoản B đăng nhập trên máy có bản nháp của tài khoản A, thì B không thấy và không gửi được bản nháp, vị trí, `sessionId`, ngữ cảnh bắt đầu của A. Khi A đăng xuất (mục Tài khoản theo đặc tả khung app AC-69, hoặc "Đăng nhập bằng tài khoản khác" ở màn khóa theo AC-61), thì app xóa mọi bản nháp, vị trí, `sessionId` và ngữ cảnh bắt đầu của A trên máy; dữ liệu đó của các tài khoản khác trên máy còn nguyên và không bao giờ hiện với tài khoản nào khác chủ của nó. Như web, đăng xuất không có cảnh báo riêng về câu trả lời chưa lưu: app chỉ hỏi xác nhận như đặc tả khung app đã có, không gửi thêm lời gọi lưu, và thay đổi chưa lưu của A mất.

### R-96 Hoàn thành phần

- Nguồn: `features/attempt-player/finish-part-dialog.tsx:28-112`; `attempt-player.tsx:866-892,918-941,1703-1709,1783-1786,1890-1938`; `save-queue.ts:516-563`; `AttemptLifecycleService.java:385-455`.
- Áp dụng cho: `navigationMode = SEQUENTIAL`, phần đang xem `OPEN` và còn phần sau chưa `CLOSED`/`SKIPPED`.
- Tiêu chí chấp nhận:
  - **AC-391** Khi điều kiện áp dụng đúng, xem thanh trên thì có nút "Hoàn thành phần" thay cho Nộp bài; `FREE` hoặc phần cuối thì không có.
  - **AC-392** Khi bấm "Hoàn thành phần", thì hộp thoại "Hoàn thành phần?" hiện `attempt.dialogs.finishPart.warningIrreversible`, tên phần, "Còn {missing}/{total} câu chưa trả lời" (chỉ đếm câu chưa trả lời), "Có {partial} câu đang làm dở" khi > 0, và `attempt.dialogs.finishPart.nextPartChoiceNote` nếu phần sau đang `AWAITING_CHOICE`, ngược lại `attempt.dialogs.finishPart.nextPartOpensNote`; nút "Quay lại làm tiếp" và "Hoàn thành phần".
  - **AC-393** Khi bấm xác nhận, thì app dừng âm thanh, bỏ mọi lần chờ gửi, gửi `POST …/parts/{partId}/finish` với `X-Attempt-Session` (không có `Idempotency-Key`; endpoint không nhận khóa, gửi lại an toàn vì phần đã đóng trả 200 như cũ) và `finalAnswers` gồm mọi câu chưa lưu của phần (`{itemId, seq, answer}`), hai nút bị khóa và hộp thoại không đóng được trong lúc gửi; thành công thì kết quả từng câu được xử lý như R-92, app tải lại trạng thái, đóng hộp thoại và mở phần sau (hoặc màn chọn phần) ở câu đầu.
  - **AC-394** *(Giới hạn)* Khi phần có hơn 500 câu chưa lưu, thì `finalAnswers` chỉ gồm 500 câu đầu theo thứ tự hàng đợi; các câu ngoài 500 không được gửi và mất khi phần đóng. Cùng giới hạn áp cho `finalAnswers` của nộp bài (AC-399), các câu ngoài 500 mất khi lượt đóng. Như web (`save-queue.ts:24,516-539`); máy chủ từ chối cả request nếu quá 500 (`attempt/api/FinishPartRequest.java:12-20`, `attempt/api/SubmitAttemptRequest.java:12-20`).
  - **AC-395** *(Từ chối)* Khi máy chủ trả 4xx khác 401 (ví dụ 409 `TIME_UP`, `PART_CLOSED`, `ATTEMPT_PART_IS_LAST`, `PART_FINISH_NOT_ALLOWED`), thì app tải lại trạng thái và đóng hộp thoại; `ATTEMPT_SESSION_REVOKED` thì hiện màn bị đẩy ra.
  - **AC-396** *(Từ chối)* Khi mất mạng hoặc 5xx trước khi có phản hồi, thì hộp thoại đổi sang "Chưa xác nhận hoàn thành phần" (`attempt.dialogs.finishPart.unconfirmedTitle`) với `attempt.dialogs.finishPart.unconfirmedDesc` và nút "Kiểm tra trạng thái lượt" (`attempt.dialogs.finishPart.reconcile`); bấm nút, hoặc khi có mạng lại, thì app đọc trạng thái: phần đã đóng thì đóng hộp thoại và về câu đầu của phần mới; phần còn mở thì hộp thoại về dạng xác nhận để gửi lại.

### R-97 Nộp bài

- Nguồn: `features/attempt-player/submit-confirm-dialog.tsx:51-200`; `attempt-player.tsx:894-916,1711-1769,1787-1789,1940-1993,2315-2334`; `AttemptLifecycleService.java:458-535`.
- Áp dụng cho: `FREE` khi lượt `IN_PROGRESS`; `SEQUENTIAL` khi phần đang xem là phần cuối và `OPEN`.
- Tiêu chí chấp nhận:
  - **AC-397** Khi bấm "Nộp bài", thì hộp thoại "Nộp bài?" hiện `attempt.dialogs.submitConfirm.description`, 4 ô "{answered}/{total} câu đã làm", "Số câu làm dở: {n}", "Số câu bỏ trống: {n}", "Đánh dấu xem lại: {n}" tính trên mọi câu lá của mọi phần, danh sách "Số câu còn thiếu theo từng phần" với "Còn thiếu {missing}/{total} câu" (thiếu gồm cả làm dở), và nút "Quay lại kiểm tra", "Nộp bài".
  - **AC-398** Khi bài có câu tự luận, thì hộp thoại thêm "Tình trạng bài viết" với "{words} chữ · đã lưu" hoặc "{words} chữ · chưa lưu" (khi có câu tự luận chưa lưu); câu tự luận chưa đạt `wordLimit` thì thêm khối "Bài viết chưa đủ số chữ tối thiểu (vẫn nộp bài được)" liệt kê "Câu {displayNo}: {wordCount}/{wordLimit} chữ — chưa đủ số chữ tối thiểu".
  - **AC-399** Khi bấm xác nhận, thì app dừng âm thanh, bỏ mọi lần chờ gửi, gửi `POST …/submit` với `Idempotency-Key`, `X-Attempt-Session` và `finalAnswers` gồm mọi câu chưa lưu ở các phần đang mở (tối đa 500); thành công thì xóa bản nháp của lượt, tải lại trạng thái và hiện màn "Lượt thi đã đóng" của AC-312.
  - **AC-400** *(Giới hạn)* Khi lần gửi đầu mất kết nối rồi học viên gửi lại, thì `Idempotency-Key` giữ nguyên tới khi có phản hồi thành công; máy chủ đã nộp ở lần đầu thì lần sau trả cùng kết quả, không lỗi.
  - **AC-401** *(Từ chối)* Khi máy chủ trả 409 (ví dụ `PART_NOT_LAST`, `TIME_UP`), thì app tải lại trạng thái và đóng hộp thoại; `ATTEMPT_SESSION_REVOKED` thì hiện màn bị đẩy ra; lỗi khác hoặc mất mạng thì hộp thoại đổi sang "Chưa xác nhận nộp bài" (`attempt.dialogs.submitConfirm.unconfirmedTitle`) với nút "Kiểm tra trạng thái lượt", xử lý như AC-396 (lượt đã đóng thì xóa nháp và hiện màn đã đóng).
  - **AC-402** *(Giới hạn)* Khi đang gửi, bấm "Nộp bài" lần nữa hoặc đóng hộp thoại thì không có tác dụng.

### R-98 Hết giờ và máy chủ đóng lượt

- Nguồn: `attempt-player.tsx:675-683,724-732,980-1003`; `attempt/application/AttemptDeadlineWorker.java:131`; `attempt/application/AttemptGuard.java:72-75,132-137,151-163`.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-403** Khi đồng hồ về 0, thì app dừng âm thanh, khóa câu hỏi, sau 500 ms tải lại trạng thái; `SHARED` hoặc phần cuối thì hiện màn "Đã hết giờ làm bài"; `PER_PART` còn phần sau thì mở phần sau (hoặc màn chọn phần) ở câu đầu.
  - **AC-404** Khi lượt bị đóng ở nơi khác (nộp ở máy khác, máy chủ đóng vì hết giờ), thì trong vòng 30 giây kể từ lúc đó (lần nhịp kế tiếp) hoặc ngay ở lần lưu kế tiếp, app hiện màn đã đóng của AC-312.
  - **AC-405** *(Giới hạn)* Khi đồng hồ máy về 0 nhưng máy chủ chưa đóng, thì lần đọc trạng thái sau 500 ms vẫn cho thấy lượt hoặc phần đã đóng (máy chủ đóng quá hạn mỗi lần đọc), không còn khoảng sửa được sau hạn.

### R-99 Đưa app xuống nền và quay lại

App gửi ngay thay đổi chưa lưu khi rời màn hình, và khi quay lại thì làm mới toàn bộ trước khi cho sửa tiếp.

- Nguồn: lựa chọn của đặc tả (web không có sự kiện tương ứng; xem Giả định); `edit-session.ts:369-398`; `save-queue.ts:463-505`.
- Áp dụng cho: màn làm bài, màn chờ, màn chọn phần.
- Tiêu chí chấp nhận:
  - **AC-406** Khi đưa app xuống nền với thay đổi chưa lưu và có mạng, thì app bỏ các lần chờ gửi và gửi ngay các thay đổi đó; ghi chúng vào bản nháp kể cả khi lời gọi chưa xong.
  - **AC-407** Khi đưa app lên lại sau bất kỳ thời gian nào, thì app gửi nhịp ngay, đọc `GET /api/v1/me/attempts/{id}`, neo lại đồng hồ, đối chiếu và gửi phần chưa lưu; trong lúc đó câu hỏi vẫn hiện; thời gian còn lại hiển thị khớp giờ máy chủ (lệch không quá 1 giây sau khi có phản hồi).
  - **AC-408** Khi ở nền đủ lâu để hết giờ, đưa app lên lại thì app hiện màn đã đóng của AC-312 hoặc phần kế tiếp theo AC-403, không có lời gọi lưu nào được chấp nhận sau hạn.
  - **AC-409** *(Từ chối)* Khi trong lúc app ở nền có máy khác tiếp quản, đưa app lên lại thì nhịp đầu tiên nhận `ATTEMPT_SESSION_REVOKED` và app hiện màn bị đẩy ra; thay đổi chưa lưu không được gửi.
  - **AC-410** Khi app ở nền, thì không gửi nhịp; màn chờ và màn sinh phần ngừng hỏi trạng thái và hỏi lại ngay khi lên lại.

### R-100 Khóa app trong lúc làm bài

- Nguồn: quyết định của đợt khung và xác thực (khóa lại sau 5 phút ở nền); đặc tả khung app AC-58, AC-59.
- Áp dụng cho: học viên đã bật mở khóa sinh trắc học, đang có lượt mở trên màn.
- Tiêu chí chấp nhận:
  - **AC-411** Khi rời app quá 5 phút trong lúc làm bài, quay lại thì màn khóa hiện trước, không lộ câu hỏi và câu trả lời (kể cả ở ảnh xem trước của trình chuyển ứng dụng); đồng hồ thi vẫn chạy ở máy chủ trong lúc khóa.
  - **AC-412** Khi mở khóa thành công, thì app về đúng màn làm bài, đúng câu đang xem và chạy AC-407; thời gian còn lại đã trừ cả thời gian ở màn khóa.
  - **AC-413** Khi lượt hết giờ trong lúc màn khóa hiện, mở khóa thì app hiện màn đã đóng của AC-312 (hoặc phần kế tiếp theo AC-403).
  - **AC-414** *(Từ chối)* Khi ở màn khóa chọn "Đăng nhập bằng tài khoản khác" (đăng xuất theo đặc tả khung app AC-61), thì bản nháp của tài khoản cũ bị xóa theo AC-390 và bản nháp của các tài khoản khác trên máy còn nguyên. Khi chọn "Nhập mật khẩu" (không phải đăng xuất, đặc tả khung app AC-60): đăng nhập tài khoản khác thì bản nháp của tài khoản cũ không bị xóa và không hiện với tài khoản mới; đăng nhập lại đúng tài khoản cũ thì bản nháp chưa lưu vẫn còn và được gửi khi mở lại lượt (AC-386).

### R-101 App bị tắt và mở lại

- Nguồn: `features/attempt-draft/edit-session-storage.ts:8-22`; `draft-store.ts:41-126`; lựa chọn của đặc tả (xem Giả định).
- Áp dụng cho: lượt `STARTING` hoặc `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-415** Khi hệ điều hành tắt tiến trình của app ở nền trong lúc đang ở màn làm bài và cơ chế khôi phục trạng thái mặc định của hệ điều hành khôi phục app (Android: khôi phục trạng thái đã lưu của màn khi tiến trình bị hệ thống thu hồi; iOS: khôi phục trạng thái của scene), mở lại app thì app vào lại màn làm bài của cùng lượt, đúng phần và câu đang xem, câu trả lời chưa lưu còn nguyên (R-95), không hiện hộp thoại tiếp quản (AC-315). Cách kiểm: Android bật "Không giữ hoạt động" trong tùy chọn nhà phát triển hoặc `adb shell am kill <gói>` khi app ở nền; iOS dừng app từ Xcode khi app ở nền.
  - **AC-416** Khi người dùng tự tắt hẳn app (vuốt khỏi trình chuyển ứng dụng, hoặc "Buộc dừng" trên Android) rồi mở lại, thì app mở ở màn đầu bình thường theo hành vi mặc định của hệ điều hành; vào Bài thi của tôi bấm "Tiếp tục" thì vào lượt như AC-415. App không tự xây cơ chế phân biệt hai trường hợp; trên Android API 26–29 và mọi trường hợp hệ điều hành không phân biệt được, hành vi theo cơ chế mặc định đó, và dù mở ở màn nào thì bản nháp vẫn còn trên máy.
  - **AC-417** Khi tắt app ngay lúc đang gửi nộp bài, mở lại lượt thì app đọc trạng thái: đã nộp thì hiện màn đã đóng; chưa nộp thì vẫn làm tiếp với bản nháp còn lại.

### R-102 Xoay, gập/mở và đổi kích thước

- Nguồn: quyết định của đợt khung và xác thực (tablet và máy gập xoay được); đặc tả khung app AC-11.
- Áp dụng cho: màn chuẩn bị, màn chờ, màn làm bài.
- Tiêu chí chấp nhận:
  - **AC-418** Khi xoay tablet, gập hoặc mở máy gập, hoặc đổi kích thước cửa sổ trong lúc làm bài, thì vẫn ở cùng phần và cùng câu đang xem; chữ đang gõ dở trong trả lời ngắn, điền khuyết, tự luận còn nguyên kể cả phần chưa gửi; cờ, bộ lọc bảng câu hỏi, hộp thoại đang mở, lựa chọn phần chưa xác nhận và lựa chọn trên màn chuẩn bị giữ nguyên.
  - **AC-419** Khi bề rộng đổi qua ngưỡng màn rộng, thì bảng câu hỏi đổi giữa cột cố định và tấm phủ theo AC-348; nếu tấm phủ đang mở mà chuyển sang màn rộng thì tấm phủ đóng và cột cố định hiện.
  - **AC-420** Khi xoay hoặc gập trong lúc đang gửi lưu, hoàn thành phần hoặc nộp bài, thì không có lời gọi trùng và kết quả vẫn được xử lý như khi không xoay.

### R-103 Cử chỉ quay lại của hệ thống

- Nguồn: web chỉ có hỏi của trình duyệt khi đóng trang lúc còn thay đổi chưa lưu (`attempt-player.tsx:747-751`); lựa chọn của đặc tả (xem A-42 và A-86).
- Áp dụng cho: màn làm bài, màn chờ.
- Tiêu chí chấp nhận:
  - **AC-421** Khi có hộp thoại hoặc tấm phủ bảng câu hỏi đang mở, dùng cử chỉ quay lại thì nó đóng; hộp thoại đang gửi (hoàn thành phần, nộp bài, tiếp quản) thì không đóng.
  - **AC-422** Khi không có gì đang mở và không còn thay đổi chưa lưu, dùng cử chỉ quay lại thì app rời màn làm bài về màn trước; lượt vẫn mở, đồng hồ vẫn chạy ở máy chủ, không gọi nộp bài.
  - **AC-423** Khi còn thay đổi chưa lưu, dùng cử chỉ quay lại thì app hỏi xác nhận rời màn; chọn ở lại thì không có gì đổi; chọn rời thì app giữ bản nháp trên máy và rời màn.
  - **AC-424** Khi ở màn chờ, dùng cử chỉ quay lại thì app về màn trước và ngừng hỏi trạng thái; lượt vẫn được chuẩn bị ở máy chủ.

### R-104 Cuộc gọi đến và ngắt âm thanh

- Nguồn: lựa chọn của đặc tả; quy tắc nghe xem mục 6.5.
- Áp dụng cho: màn làm bài.
- Tiêu chí chấp nhận:
  - **AC-425** Khi có cuộc gọi đến hoặc ứng dụng khác chiếm âm thanh trong lúc làm bài, thì đồng hồ thi không dừng, câu trả lời và vị trí giữ nguyên; tệp âm thanh đang phát dừng phát và cách tiếp tục tuân theo mục 6.5.
  - **AC-426** Khi cuộc gọi kết thúc và người dùng về app, thì app chạy AC-407 nếu app đã xuống nền.

### R-105 Hết phiên đăng nhập trong lúc làm bài

- Nguồn: `attempt-player.tsx:578-608`; đặc tả khung app AC-33, AC-34.
- Áp dụng cho: lượt `IN_PROGRESS`.
- Tiêu chí chấp nhận:
  - **AC-427** Khi access token hết hạn giữa lúc làm bài, thì app tự làm mới rồi gửi lại lời gọi; nhiều lời gọi cùng lúc chỉ gây một lần làm mới; học viên không thấy gì khác ngoài "Đang lưu...".
  - **AC-428** *(Từ chối)* Khi làm mới phiên đăng nhập thất bại (phiên bị thu hồi), thì app về màn Đăng nhập theo đặc tả khung app; đây không phải đăng xuất của AC-390 nên bản nháp chưa lưu của tài khoản vẫn giữ trên máy; đăng nhập lại cùng tài khoản và mở lượt thì bản nháp được gửi theo AC-386; đăng nhập tài khoản khác thì bản nháp đó không hiện với tài khoản mới và vẫn còn trên máy.

### 6.5 Nội dung câu hỏi, các dạng câu và cách trả lời

### R-106 Danh sách cho phép và phần ngoài danh sách

App chỉ vẽ những gì danh sách cho phép mô tả và xử lý phần ngoài danh sách giống bộ lọc của web: thẻ lạ bị bỏ nhưng chữ bên trong vẫn hiện; nội dung của `script`, `style`, `template`, `iframe`, `object`, `embed` không hiện; thuộc tính lạ bị bỏ qua; khối `figure`/`blockquote`/`span.blank` sai luật không hiện. App không bao giờ chạy mã, mở liên kết ngoài hay tải tài nguyên ngoài máy chủ Thi360 từ nội dung đề.

- Nguồn: `packages/ui/src/rich-text/sanitize.ts:47-128`; `html-contract.ts:38-39,61-97,112-151`; `rich-text-view.tsx:77`
- Áp dụng cho: mọi nội dung đề ở mọi chế độ
- Tiêu chí chấp nhận:
  - **AC-429** Khi app hiện từng mẫu `valid: true` của `docs/specs/fixtures/rich-text-samples.json` thì mọi chữ, bảng, ảnh, đoạn trích và công thức của mẫu đều hiện, không phần nào bị bỏ; công thức nằm trong danh sách miễn trừ của AC-464 hiện dạng khung cảnh báo theo R-116 và vẫn tính là đạt tiêu chí này.
  - **AC-430** *(Từ chối)* Khi nội dung đề là `<p>a <font>b</font> c</p><script>x()</script><p style="color:red" onclick="x()">d</p>` thì app hiện "a b c" và "d" với màu chữ thường, không hiện "x()", và không có hành động nào khi bấm vào "d".
  - **AC-431** *(Từ chối)* Khi app hiện từng mẫu `valid: false` có vi phạm ở `figure` hoặc `blockquote` (không kèm thuộc tính) thì khối đó không hiện; phần nội dung còn lại của mẫu vẫn hiện. Khi mẫu `bad-blank-with-text` hiện thì không có chip chỗ trống và không có chữ "text".
  - **AC-432** *(Giới hạn)* Khi nội dung đề có `img` với `src` là địa chỉ ngoài (`https://…` hoặc `//…`) và không có `data-asset-id` hợp lệ, thì app không gửi request nào tới địa chỉ đó và hiện trạng thái ảnh lỗi của R-109.

### R-107 Đoạn văn, định dạng chữ và danh sách

`p` là đoạn có khoảng cách dưới, `br` là xuống dòng, `strong` đậm, `em` nghiêng, `u` gạch chân, `s` gạch ngang, `sub`/`sup` chỉ số dưới/trên nhỏ hơn và lệch đường chân chữ, `ul` có chấm đầu dòng, `ol` đánh số 1, 2, 3, danh sách lồng thụt vào thêm một bậc. Chữ dài tự xuống dòng trong bề rộng cột, kể cả một từ dài không có khoảng trắng.

- Nguồn: `rich-text.css:7-29`
- Áp dụng cho: mọi nội dung đề
- Tiêu chí chấp nhận:
  - **AC-433** Khi nội dung là `<p>H<sub>2</sub>O và x<sup>2</sup>, <strong>đậm</strong> <em>nghiêng</em> <u>gạch chân</u> <s>gạch ngang</s></p><ol><li>một</li><li>hai</li></ol>` thì app hiện đúng sáu kiểu chữ đó và danh sách có số "1." "2.".
  - **AC-434** *(Giới hạn)* Khi một đoạn chứa chuỗi 300 ký tự không có khoảng trắng trên màn 360dp hoặc 390pt thì chuỗi xuống dòng trong cột, trang không cuộn ngang.

### R-108 Bảng và hình

`figure.data-table` và `figure.cross` hiện trong một khung viền bo góc; `figcaption` đậm canh giữa phía trên bảng; `p.figure-note` và `p.figure-source` chữ nhỏ màu `text-muted` phía dưới. Bảng có viền mọi ô, ô `th` nền màu đầu bảng và chữ đậm, ô `td.num` canh phải với chữ số đều nhau, `colspan`/`rowspan` gộp ô đúng. Bảng thường ngoài `figure` (nội dung cũ) cũng hiện có viền. Bảng rộng hơn cột cuộn ngang trong khung của chính nó; trang và các khối khác không cuộn ngang.

- Nguồn: `rich-text.css:31-49,57-77,92-96`; `DESIGN.md:245`; mẫu `data-table`, `data-table-merged-cell-with-math`, `cross-diagram`, `legacy-plain-table`
- Áp dụng cho: mọi nội dung đề
- Tiêu chí chấp nhận:
  - **AC-435** Khi app hiện mẫu `data-table` thì thấy tiêu đề "TRỊ GIÁ XUẤT, NHẬP KHẨU CỦA HOA KÌ" phía trên bảng, 3 ô tiêu đề cột đậm, 4 ô số canh phải, dòng "Đơn vị…" và dòng "Nguồn…" chữ nhỏ phía dưới.
  - **AC-436** Khi app hiện mẫu `data-table-merged-cell-with-math` thì ô tiêu đề "Giá trị" chiếm 2 cột và công thức trong ô được vẽ như R-114.
  - **AC-437** *(Giới hạn)* Khi bảng có 8 cột số 7 chữ số hiện trên màn 360dp hoặc 390pt thì bảng cuộn ngang trong khung bảng, đề bài phía trên và phương án phía dưới đứng yên, không chữ nào bị cắt.

### R-109 Ảnh trong nội dung đề

App tải ảnh từ máy chủ với quyền của học viên và hiện đúng kích thước theo `figure.image[data-width]`: `small` 40 %, `medium` 70 %, `full` hoặc ảnh ngoài `figure` tối đa 100 % bề rộng cột, giữ tỉ lệ, canh giữa trong `figure`. Trong lúc tải, ảnh có khung chờ đúng tỉ lệ khi `width`/`height` có trong HTML (không có thì khung chờ cao bằng một dòng chữ). Tải lỗi thì khung ảnh hiện biểu tượng ảnh hỏng, chữ `alt` nếu có, và cho bấm để tải lại; nội dung còn lại không đổi. Ảnh được giữ trên máy theo R-177.

- Nguồn: `attempt-assets.ts:7-9,71-118`; `AttemptAssetService.java:158-199,228-290`; `AttemptAssetController.java:73-78`; `AttemptResultService.java:761-770`; `rich-text.css:51-54,79-90`
- Áp dụng cho: học viên có lượt thi; ảnh trong đề, phương án, ý, mục ghép, ngữ liệu (đường `/assets/`) và trong lời giải ở màn xem lại (đường `/solution-assets/`)
- Tiêu chí chấp nhận:
  - **AC-438** Khi câu có `<img data-asset-id="{id}">` với `src` rỗng hoặc là `/api/v1/admin/question-assets/{id}` thì app gọi `GET /api/v1/me/attempts/{attemptId}/assets/{id}` kèm `Authorization: Bearer` và hiện ảnh trả về.
  - **AC-439** Khi app hiện mẫu `image-figure` (`data-width="medium"`) trên màn 390pt thì ảnh rộng 70 % cột, canh giữa, chú thích phía dưới.
  - **AC-440** Khi lời giải ở màn xem lại có `img src="/api/v1/me/attempts/{attemptId}/solution-assets/{id}"` thì app gọi đúng đường đó kèm Bearer.
  - **AC-441** *(Từ chối)* Khi máy chủ trả `404 NOT_FOUND` hoặc mất mạng lúc tải ảnh thì khung ảnh hiện trạng thái lỗi; bấm vào khung thì app gọi lại đúng request đó; khi lần gọi lại thành công thì ảnh hiện.
  - **AC-442** *(Giới hạn)* Khi học viên rời màn rồi tắt hẳn app, thì thư mục dữ liệu và bộ đệm của app không chứa tệp ảnh nào đã tải từ `/assets/` hay `/solution-assets/` (luật chung cho mọi tài nguyên ở R-177).

### R-110 Phóng to ảnh

Ảnh trong `figure.image[data-zoom="true"]` có thể bấm để mở xem toàn màn hình, chụm hai ngón để phóng to tới tối đa 4 lần, kéo để di chuyển, đóng bằng nút đóng hoặc cử chỉ quay lại của hệ thống. Ảnh có `data-zoom="false"` hoặc nằm ngoài `figure.image` không mở được. Web chưa có hành vi này; đây là lựa chọn của đặc tả (xem A-89).

- Nguồn: `html-contract.ts:46,84-88` (cờ `data-zoom` đã có trong nội dung); giả định của phần này
- Áp dụng cho: chế độ làm bài, khóa và xem lại
- Tiêu chí chấp nhận:
  - **AC-443** Khi học viên bấm ảnh của mẫu `image-figure` (`data-zoom="true"`) thì ảnh mở toàn màn hình; chụm phóng to được, tối đa 4 lần; bấm đóng thì quay về đúng vị trí cuộn cũ của tờ đề và câu trả lời không đổi.
  - **AC-444** *(Từ chối)* Khi học viên bấm ảnh có `data-zoom="false"` thì không có gì mở ra.

### R-111 Đoạn trích thơ, văn xuôi, tư liệu

`blockquote.quote` hiện trong khung có nền khác nền thẻ, bo góc, giãn dòng rộng hơn chữ thường. `footer` (tác giả) canh phải màu chữ phụ, `cite` nghiêng, `p.figure-source` chữ nhỏ màu `text-muted` ở cuối. Đoạn thơ có `data-line-numbers="5"` hiện mỗi dòng trên một hàng, cột số bên trái chỉ ghi số ở dòng 5, 10, 15…, đếm liên tục qua các khổ, dòng dài xuống dòng mà không lấn sang cột số.

- Nguồn: `rich-text.css:99-134`; `rich-text-view.tsx:36-67`; mẫu `quote-poem`, `quote-prose-with-source`, `quote-footer-then-source`
- Áp dụng cho: mọi nội dung đề
- Tiêu chí chấp nhận:
  - **AC-445** Khi app hiện một đoạn thơ hai khổ, khổ đầu 4 dòng, khổ sau 4 dòng, `data-line-numbers="5"`, thì số "5" nằm cạnh dòng đầu của khổ sau, không dòng nào khác có số.
  - **AC-446** Khi app hiện mẫu `quote-footer-then-source` thì chữ tác giả canh phải ngay dưới thân đoạn và dòng nguồn là dòng cuối, chữ nhỏ.
  - **AC-447** *(Giới hạn)* Khi đoạn thơ có `data-line-numbers="0"` thì không có cột số dòng.

### R-112 Gạch chân có nhãn và chỗ trống đánh số

`u[data-label="X"]` hiện gạch chân màu chữ chính của thương hiệu và chữ nhãn X nhỏ, đậm, canh giữa ngay dưới cụm gạch; khoảng cách dòng đủ để nhãn không đè dòng dưới. `span.blank[data-blank=n]` hiện chip viền đứt màu thông tin với chữ "(m) ____", m = `blankNumberStart` + n − 1 (`blankNumberStart` của ngữ liệu chùm, thiếu thì 1); chip không bị ngắt giữa dòng.

- Nguồn: `rich-text.css:137-167`; `rich-text-view.tsx:14,24-30`; `question-set.tsx:50,79`; mẫu `underline-labels`, `set-passage-blanks`
- Áp dụng cho: mọi nội dung đề; chỗ trống chủ yếu ở ngữ liệu của Bộ câu hỏi
- Tiêu chí chấp nhận:
  - **AC-448** Khi app hiện mẫu `underline-labels` thì dưới "has been" có chữ "A", dưới "to" có chữ "B", và nhãn không chồng lên chữ của dòng kế tiếp.
  - **AC-449** Khi ngữ liệu chùm có `blankNumberStart = 31` và `<span class="blank" data-blank="2"></span>` thì app hiện chip "(32) ____".

### R-113 Cỡ chữ hệ thống và chế độ sáng/tối

Nội dung đề, công thức, ô nhập và nhãn của thành phần câu hỏi to nhỏ theo cỡ chữ của hệ điều hành; bố cục giãn theo, không cắt chữ, không chồng chữ. Công thức được vẽ ở cỡ bằng cỡ chữ bao quanh nó sau khi đã áp cỡ chữ hệ thống, nên phóng theo cùng tỉ lệ với chữ; công thức dòng hay công thức khối quá rộng ở bất kỳ cỡ chữ nào xử lý theo R-115. Mọi màu của nội dung đề và thành phần câu hỏi lấy theo token của chế độ đang dùng: chữ thường màu `text`, viền bảng `border-strong`, nền ô tiêu đề màu đầu bảng của chế độ đó, chip chỗ trống và khung cảnh báo theo màu thông tin và cảnh báo (phần chung, R-6). Công thức cùng màu với chữ bao quanh. Ảnh giữ nguyên màu ở cả hai chế độ.

- Nguồn: `rich-text.css:2-5` (chỉ dùng token, đúng cả sáng/tối); `packages/ui/src/theme/scale.ts:79-130`; `DESIGN.md:512,621`
- Áp dụng cho: mọi nội dung đề và thành phần câu hỏi
- Tiêu chí chấp nhận:
  - **AC-450** Khi cỡ chữ hệ thống đặt 200 % (Android cài đặt Cỡ chữ lớn nhất kèm phóng to hiển thị; iOS Dynamic Type mức AX3) và app hiện từng mẫu hợp lệ của `rich-text-samples.json` cùng 11 câu của `question-samples.json` trên màn 360dp hoặc 390pt, thì không chữ nào bị cắt hay chồng; chỉ bảng và công thức (dòng hoặc khối) rộng hơn cột được cuộn ngang trong khung riêng.
  - **AC-451** Khi chuyển app sang chế độ tối và hiện mẫu `math-inline-and-block`, `data-table`, `set-passage-blanks` thì chữ, công thức, viền bảng và chip chỗ trống dùng giá trị token tối; đo tương phản chữ trên nền đạt tối thiểu 4,5:1.
  - **AC-719** Khi cỡ chữ hệ thống đổi từ 100 % sang 200 % và app hiện `<p>Biết \(\frac{a}{b}\) thì</p>`, thì chiều cao chữ "B" của "Biết" và chiều cao của chữ "a" trong công thức cùng tăng khoảng 2 lần (mỗi tỉ lệ nằm trong khoảng 1,8–2,2) so với lúc 100 %.
  - **AC-720** *(Giới hạn)* Khi cỡ chữ hệ thống đặt 200 % và công thức dòng `\(a_1 + a_2 + \dots + a_{40} = S\)` hiện trên màn 360dp hoặc 390pt thì công thức nằm trên dòng riêng và cuộn ngang trong khung của chính nó theo R-115; không ký hiệu nào bị cắt hay đè lên dòng trên, dòng dưới, trang không cuộn ngang.
  - **AC-452** *(Giới hạn)* Khi đổi cỡ chữ hệ thống trong lúc đang làm bài rồi quay lại app thì tờ đề vẽ lại theo cỡ mới, câu trả lời đang nhập và vị trí câu đang xem không đổi.

### R-114 Công thức: ba cách viết, công thức dòng, công thức khối, hóa học

App vẽ công thức từ cả ba cách viết của web: span `math-inline`/`math-block` có cặp dấu, span không có cặp dấu (cả chữ là LaTeX), và cặp `\( \)` / `\[ \]` gõ thẳng trong chữ. `\( \)` là công thức dòng nằm trong dòng chữ; `\[ \]` và `math-block` là công thức khối, đứng riêng một dòng, canh giữa. Lệnh mhchem `\ce{…}` và `\pu{…}` được vẽ. Chữ tiếng Việt có dấu trong `\text{…}` hiện đúng dấu. Cặp dấu không khớp (thiếu dấu đóng) để nguyên là chữ, không vẽ.

- Nguồn: `math-render.ts:4-25,62-123`; D-5, D-6; mẫu `math-inline-and-block`, `chemistry-mhchem`
- Áp dụng cho: mọi nội dung đề; nhãn của phương án Ghép đôi trong danh sách chọn
- Tiêu chí chấp nhận:
  - **AC-453** Khi app hiện mẫu `math-inline-and-block` thì có ít nhất một công thức nằm trong dòng chữ và một công thức khối đứng riêng dòng, canh giữa; không có khung cảnh báo công thức.
  - **AC-454** Khi app hiện mẫu `chemistry-mhchem` thì hai phương trình hóa học được vẽ với chỉ số dưới, trạng thái (s), (g) và mũi tên thuận nghịch; không có khung cảnh báo công thức.
  - **AC-455** Khi nội dung là `<p>Cho \(x^2 + 1\) và \[\int_0^1 f(x)\,dx\]</p>` (cặp dấu gõ thẳng, không span) thì app vẽ một công thức dòng và một công thức khối.
  - **AC-456** Khi nội dung là `<p><span class="math-inline">\(\text{Vận tốc } v = \frac{s}{t}\)</span></p>` thì chữ "Vận tốc" hiện đủ dấu trong công thức.
  - **AC-457** *(Giới hạn)* Khi nội dung là `<p>Giá trị \(x + 1 là</p>` (thiếu dấu đóng) thì app hiện nguyên chữ "Giá trị \(x + 1 là", không có công thức và không có khung cảnh báo.

### R-115 Công thức: đường chân chữ, xuống dòng, công thức rộng

Công thức dòng đặt trên cùng đường chân chữ với chữ bao quanh: chữ trước và sau công thức nằm trên một đường chân chung, và đường chân của công thức (đường chân của các ký hiệu không nằm trong phân số, chỉ số hay căn) trùng đường đó; phân số, căn và chỉ số được tăng khoảng cách dòng vừa đủ để không đè dòng trên, dòng dưới. App không ngắt một công thức dòng ra nhiều dòng. Công thức dòng không vừa phần còn lại của dòng thì xuống dòng mới; công thức dòng dài hơn cả bề rộng cột thì nằm trên dòng riêng và cuộn ngang trong khung của chính nó. Công thức khối rộng hơn cột cuộn ngang trong khung của chính nó. Trang không bao giờ cuộn ngang vì công thức. Trên web, công thức dòng cũng không bị ngắt nhưng tràn khỏi cột khi quá dài; khung cuộn là cách riêng của app (bảng "Giả định").

- Nguồn: `rich-text.css:182-186`; KaTeX 0.19.1 (`packages/ui/package.json:33`); giả định của phần này cho công thức rộng (web không có khung cuộn)
- Áp dụng cho: mọi nội dung đề
- Tiêu chí chấp nhận:
  - **AC-458** Khi đoạn `<p>Biết \(x + \frac{a}{b}\) thì</p>` hiện ở cỡ chữ mặc định, đo trên ảnh chụp, thì đường chân của "Biết", của "thì" và của chữ "x" trong công thức lệch nhau không quá 1px; đường kẻ phân số nằm giữa đường chân và đỉnh chữ "x" (kể cả hai đường đó); tử số "a" không chạm dòng chữ phía trên và mẫu số "b" không chạm dòng chữ phía dưới.
  - **AC-459** *(Giới hạn)* Khi công thức dòng `\(a_1 + a_2 + \dots + a_{40} = S\)` hiện trên màn 360dp hoặc 390pt ở cỡ chữ mặc định thì công thức không bị ngắt ra nhiều dòng: nếu không vừa cột thì nó nằm trên dòng riêng và cuộn ngang trong khung riêng; không ký hiệu nào bị cắt, trang không cuộn ngang.
  - **AC-460** *(Giới hạn)* Khi công thức khối là ma trận 1 hàng 12 cột hiện trên màn 360dp hoặc 390pt thì công thức cuộn ngang trong khung riêng; vuốt ngang trên công thức không đổi câu.

### R-116 Công thức bộ vẽ không vẽ được

Khi bộ vẽ công thức của app không vẽ được một công thức (kể cả công thức KaTeX vẽ được), app hiện khung cảnh báo công thức chứa mã nguồn kèm cặp dấu, đúng như web hiện công thức lỗi; phần còn lại của nội dung đề vẫn hiện đủ và app không thoát. Trình đọc màn hình đọc mã nguồn đó. Công thức KaTeX không vẽ được (lỗi thật trong dữ liệu) cũng hiện khung này, như web.

- Nguồn: `math-render.ts:48-60`; `rich-text.css:169-179`; `packages/ui/src/rich-text/__tests__/rich-text-view.test.tsx:36-41`; giả định A-4 của đặc tả
- Áp dụng cho: mọi nội dung đề
- Tiêu chí chấp nhận:
  - **AC-461** Khi nội dung là `<p>Biết <span class="math-inline">\(\broken{syntax}\)</span> thì</p>` thì app hiện "Biết", một khung cảnh báo chứa đúng chữ `\(\broken{syntax}\)` bằng chữ đơn cách, rồi "thì", trên cùng dòng.
  - **AC-462** Khi công thức khối lỗi `\[\frac{1}{\]` hiện thì khung cảnh báo chứa `\[\frac{1}{\]`; các đoạn trước và sau vẫn hiện.
  - **AC-463** *(Giới hạn)* Khi bộ vẽ ném lỗi hoặc không trả kết quả cho một công thức trong câu có 20 công thức thì chỉ công thức đó hiện khung cảnh báo; 19 công thức còn lại được vẽ và học viên vẫn trả lời câu đó được.

### R-117 Bộ mẫu đối chiếu với web

Có một bộ mẫu cố định để so app với web, và mỗi bản build của app phải qua phép so này. Bộ mẫu gồm: 12 mẫu hợp lệ và 31 mẫu sai của `rich-text-samples.json`; 11 câu của `question-samples.json`; một tệp công thức mới; một trạng thái lượt thi 50 câu đủ 8 dạng dùng cho đo hiệu năng ở mục 9 của đặc tả.

Tệp công thức lấy từ hai nguồn, mỗi công thức ghi nguồn của nó (D-27; không lấy gì từ cơ sở dữ liệu của môi trường demo, vì dữ liệu ở đó là dữ liệu thử):

1. **Danh mục công thức** `docs/specs/fixtures/formula-catalogue.json`, là một phần của đặc tả này (bản để đọc, kèm nguồn tra cứu của từng nhóm: `formula-catalogue-math.md` và `formula-catalogue-science.md` cùng thư mục). Danh mục dựng ngày 2026-11-26 từ: Chương trình giáo dục phổ thông 2018 môn Toán, Vật lí, Hóa học lớp 10 đến 12 (Thông tư 32/2018/TT-BGDĐT, kể cả chuyên đề học tập); đề cương các học phần năm đầu đại học (Giải tích, Đại số tuyến tính, Xác suất thống kê, Vật lí đại cương, Hóa đại cương); đề tham khảo HSA 2025, đề minh họa V-ACT 2025 và mô tả cấu trúc bài thi TSA; trang "Supported Functions" của KaTeX và tài liệu mhchem. Danh mục có 225 nhóm với 1.915 công thức mà KaTeX 0.19.1 kèm mhchem vẽ được (507 công thức dùng `\ce` hoặc `\pu`; 8 công thức chỉ vẽ được ở dạng khối), và 93 công thức KaTeX từ chối kèm thông báo lỗi (các công thức bị từ chối không thuộc nhóm nào; 2 trong số đó chỉ bị từ chối ở dạng cùng dòng). Nhóm có hai loại: **nhóm chương trình** (174 nhóm, 1.439 công thức: Toán `M10`, `M11`, `M12`, `MU`, `MX`; Vật lí `P10`, `P11`, `P12`; Hóa học `C10`, `C11`, `C12`; đại học `PU`, `CU`; Sinh học và môn khác `B`, theo đúng tiền tố ghi trong tệp) và **nhóm cú pháp** (51 nhóm, 476 công thức: `SX` cho từng mục của trang lệnh KaTeX, `CH` cho từng mục của tài liệu mhchem). Mọi công thức của danh mục có trong tệp công thức, không thêm, không bớt, không sửa; đổi danh mục là đổi đặc tả. Công thức không gắn nhãn chỉ-khối có mặt ở cả dạng cùng dòng và dạng khối.
2. **Mẫu có sẵn trong repo:** mọi công thức trong `rich-text-samples.json`, `question-samples.json` và các tệp test của `packages/ui` có công thức.

Mỗi công thức trong tệp ghi kết quả KaTeX 0.19.1 kèm mhchem (vẽ được hay không), do một test của web tạo và kiểm, không ghi tay.

Mọi công thức KaTeX vẽ được phải được bộ vẽ của cả hai app vẽ, trừ công thức nằm trong danh sách miễn trừ. Mỗi dòng của danh sách miễn trừ ghi: công thức (mã LaTeX), app nào không vẽ được, lý do, và công thức có vượt chương trình cấp 3 và đại học hay không. Người yêu cầu duyệt danh sách này (D-8); mức phải đạt là công thức thuộc chương trình cấp 3 và đại học, công thức chuyên sâu hơn được miễn. Một công thức thuộc nhóm chương trình của danh mục chỉ được miễn khi người yêu cầu đồng ý rõ cho đúng công thức đó, và sự đồng ý được ghi trong báo cáo nghiệm thu. Một công thức thuộc nhóm cú pháp được miễn khi dòng miễn trừ của nó ghi đủ lý do và người yêu cầu duyệt cả danh sách (D-8). Công thức của nguồn mẫu có sẵn và công thức không thuộc nhóm nào theo luật của nhóm chương trình.

So ảnh bằng mắt bắt buộc cho 43 mẫu HTML và 11 câu mẫu trên cả hai nền tảng, ở cả chế độ sáng và tối. Tệp công thức được kiểm tự động trên cả hai nền tảng (mỗi công thức được vẽ mà không rơi về khung cảnh báo, ở cả dạng cùng dòng và dạng khối) và so ảnh bằng mắt ở chế độ sáng, dạng khối, trên một thiết bị tham chiếu cho mỗi nền tảng. Phép so ảnh công thức làm theo nhóm: mỗi nhóm của danh mục có một tờ ảnh cho mỗi nền tảng, đặt hình của web và hình của app cạnh nhau cho từng công thức của nhóm (225 tờ mỗi nền tảng, cộng một tờ cho nguồn mẫu có sẵn). Hình của web cho các tờ này được chụp một lần bằng Chrome hoặc Chromium trên máy dựng bộ mẫu và lưu cùng bộ mẫu. Các công thức KaTeX từ chối chỉ qua phép kiểm tự động, không có tờ ảnh. Phép kiểm tự động của Android chạy cả trên một ảnh máy ảo hoặc máy thật ARM64 dùng trang bộ nhớ 16 KB. Phiên bản RaTeX và SwaTex dùng trong app được ghim ở một bản cụ thể.

- Nguồn: D-5, D-6, D-8, D-27; `docs/specs/fixtures/formula-catalogue.json` và hai tệp nguồn tra cứu cùng thư mục; `latex-check.ts:24-35`; `intake.md:110-111` (ràng buộc tích hợp, phần Hiện trạng); mục 9 của đặc tả (hiệu năng đo trên bộ mẫu của phần này); giả định về vị trí và khối lượng so ảnh của bộ mẫu
- Áp dụng cho: quy trình kiểm của cả hai app
- Tiêu chí chấp nhận:
  - **AC-464** Khi chạy phép kiểm tự động của mỗi app trên tệp công thức thì mọi công thức có kết quả KaTeX "vẽ được" được bộ vẽ của app vẽ mà không hiện khung cảnh báo, trừ công thức nằm trong danh sách miễn trừ đã được người yêu cầu duyệt; mọi công thức "không vẽ được" hiện khung cảnh báo.
  - **AC-465** Khi chạy phép kiểm tự động trên 31 mẫu sai thì phần bị bỏ khớp với danh sách vi phạm của từng mẫu theo luật của R-106.
  - **AC-466** Khi người rà soát đặt ảnh chụp của web (Chrome hoặc Chromium, bề rộng 390px) và của app (Pixel 7 API 34; iPhone 17) cạnh nhau cho từng mẫu trong 43 mẫu HTML và 11 câu mẫu, ở cả sáng và tối trên cả hai nền tảng (216 cặp), và cho từng công thức của tệp công thức ở chế độ sáng, dạng khối, trên Pixel 7 API 34 và iPhone 17 (xem theo tờ ảnh của từng nhóm), thì với mỗi mẫu: cùng ký hiệu theo cùng thứ tự, cùng cấu trúc phân số, căn, chỉ số, ma trận, cùng số dòng của bảng và đoạn thơ, không ký hiệu nào bị cắt; kết quả từng mẫu ghi trong báo cáo nghiệm thu, riêng công thức thì ghi theo từng nhóm kèm danh sách công thức lệch của nhóm đó.
  - **AC-721** Khi mở tệp công thức thì mỗi công thức có ghi nguồn (danh mục, mẫu có sẵn), và công thức nguồn danh mục ghi mã nhóm cùng loại nhóm (chương trình, cú pháp); tập công thức nguồn danh mục trùng đúng `docs/specs/fixtures/formula-catalogue.json`: đủ 225 nhóm với 1.915 công thức vẽ được, đúng từng mã LaTeX theo từng nhóm, và 93 công thức KaTeX từ chối (không gắn nhóm), không thiếu và không thừa; công thức không gắn nhãn chỉ-khối có cả dạng cùng dòng và dạng khối; mọi công thức của nguồn mẫu có sẵn trong repo đều có mặt. Phép so là test tự động của web, báo đỏ khi tệp công thức và danh mục lệch nhau.
  - **AC-722** *(Từ chối)* Khi danh sách miễn trừ có một công thức thuộc nhóm chương trình của danh mục, hoặc một công thức của nguồn mẫu có sẵn, mà báo cáo nghiệm thu không ghi sự đồng ý rõ của người yêu cầu cho đúng công thức đó, hoặc có một dòng thiếu lý do hay thiếu ghi chú vượt chương trình hay không, thì phép kiểm bộ mẫu không đạt và bản build không được nghiệm thu.
  - **AC-723** Khi chạy phép kiểm tự động của app Android trên tệp công thức bằng một ảnh máy ảo hoặc máy thật ARM64 dùng trang bộ nhớ 16 KB thì app mở được, không thoát, và kết quả giống hệt lần chạy trên Pixel 7 API 34.
  - **AC-724** Khi đọc tệp khai báo phụ thuộc của hai app thì RaTeX và SwaTex được khai đúng một phiên bản cụ thể (không có khoảng phiên bản, không có `+` hay `latest`), và tệp khóa phụ thuộc ghi cùng phiên bản đó.
  - **AC-467** *(Từ chối)* Khi web nâng KaTeX lên bản khác 0.19.1 mà tệp công thức chưa được tạo lại thì test của web báo đỏ.

### R-118 Khung một câu hỏi

Mỗi câu hiện tiêu đề "Câu n" (`question.questionLabel`); câu Điền khuyết nhiều ô hiện "Câu n–m" (`question.questionRangeLabel`) với m = n + số ô − 1; cạnh tiêu đề là chip tên dạng (`question.typeLabels`, ví dụ "Chọn một đáp án", "Điền khuyết · 3"). Chế độ làm bài có nút cờ "Đánh dấu xem lại" / "Bỏ đánh dấu" (`question.flagForReview`, `question.flagRemove`; việc lưu cờ thuộc mục 6.4); khi câu đang được đánh dấu, hình cờ của nút tô đặc màu cảnh báo `--color-warning` như web (`question-frame.tsx:77`), còn ô của câu trong bảng câu hỏi tô màu đỏ theo mục 6.4). Sau đó là đề bài (nếu có), rồi phần trả lời của dạng câu. Nút và ô bấm có vùng bấm tối thiểu 44×44pt (iOS) hoặc 48×48dp (Android).

- Nguồn: `question-frame.tsx:50-122`; `ui-strings.ts:191-200,209-210,288-290`; `DESIGN.md:516`
- Áp dụng cho: mọi dạng câu, mọi chế độ (nút cờ chỉ ở chế độ làm bài)
- Tiêu chí chấp nhận:
  - **AC-468** Khi câu `CLOZE` có `displayNo = 22` và 2 ô thì tiêu đề là "Câu 22–23" và chip là "Điền khuyết · 2".
  - **AC-469** Khi câu `MULTIPLE_CHOICE` có `displayNo = 5` thì tiêu đề "Câu 5" và chip "Chọn một hoặc nhiều đáp án".
  - **AC-470** *(Giới hạn)* Khi câu `CLOZE` có 1 ô thì tiêu đề là "Câu n", không phải khoảng.

### R-119 Thứ tự phương án theo máy chủ

App xếp phương án của Một đáp án, Nhiều đáp án và các mục cột phải của Ghép đôi theo `optionOrder` của câu; mục có id không nằm trong `optionOrder` xếp sau theo thứ tự mảng; `optionOrder` rỗng hoặc thiếu thì theo thứ tự mảng. Chữ cái gán theo vị trí hiển thị: A, B, C… cho phương án, a, b, c… cho cột phải. Ý của Đúng/Sai (a, b, c…) và mục trái của Ghép đôi (1, 2, 3…) theo thứ tự mảng. App không tự đảo; thứ tự giữ nguyên khi tải lại, xoay máy hay mở lại lượt.

- Nguồn: `helpers.ts:3-34`; `choice-question.tsx:38,57-60`; `matching.tsx:51-52`; `true-false.tsx:57-58`; `mappers.ts:89`
- Áp dụng cho: chế độ làm bài, khóa, xem lại
- Tiêu chí chấp nhận:
  - **AC-471** Khi câu có phương án `[o1, o2, o3, o4]` và `optionOrder = [o3, o1, o4, o2]` thì app hiện "A." là nội dung `o3`, "B." là `o1`, "C." là `o4`, "D." là `o2`; chọn "A." thì giá trị gửi có `optionId = "o3"`.
  - **AC-472** *(Giới hạn)* Khi `optionOrder = [o2]` cho câu có 3 phương án thì "A." là `o2`, "B." là `o1`, "C." là `o3`.

### R-120 Một đáp án

Các phương án là một nhóm chọn một: mỗi hàng có nút chọn tròn, chữ cái, nội dung phương án; bấm vào bất kỳ chỗ nào của hàng là chọn; hàng đang chọn có viền màu chính và nền nhạt. Bấm lại hàng đang chọn là bỏ chọn. Giá trị gửi: `{"type":"SINGLE_CHOICE","optionId":"<id>"}`, bỏ chọn thì `{"type":"SINGLE_CHOICE"}`.

- Nguồn: `choice-question.tsx:57-101`; `choice-body.tsx:18-62`; `mappers.ts:135-138`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-473** Khi chưa chọn gì và học viên bấm hàng "B." thì hàng B được đánh dấu chọn và app ghi giá trị `optionId` của B.
  - **AC-474** Khi đang chọn B và học viên bấm hàng "C." thì chỉ C được chọn.
  - **AC-475** *(Giới hạn)* Khi đang chọn B và học viên bấm lại hàng B thì không hàng nào được chọn và giá trị gửi là `{"type":"SINGLE_CHOICE"}` không có `optionId`.

### R-121 Nhiều đáp án

Như R-120 nhưng mỗi hàng có ô vuông chọn, chọn được nhiều hàng, phía trên danh sách có dòng "Chọn các phương án đúng" (`question.chooseMultiple`). Giá trị gửi `{"type":"MULTIPLE_CHOICE","optionIds":[…]}` theo thứ tự học viên chọn, không trùng; bỏ hết thì `optionIds` rỗng.

- Nguồn: `choice-question.tsx:54-56,74-85`; `choice-body.tsx:64-104`; `mappers.ts:139-142`; `AnswerValueValidator.java:85-100`; `ui-strings.ts:213`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-476** Khi học viên bấm "A." rồi "C." thì cả hai được chọn và giá trị gửi là `optionIds` = [id A, id C].
  - **AC-477** *(Giới hạn)* Khi đang chọn A và C và học viên bấm lại A rồi C thì không hàng nào được chọn và giá trị gửi là `{"type":"MULTIPLE_CHOICE","optionIds":[]}`.

### R-122 Trả lời ngắn

Một ô nhập có nhãn "Đáp số" (`question.answerLabel`), chữ chờ "Nhập đáp số" (`question.answerPlaceholder`). Dưới ô là dòng "Hướng dẫn nhập: <inputHint> · <gợi ý định dạng>." rồi "Đáp số được so khớp đúng cách viết, nên hãy viết theo hướng dẫn nhập ở trên." (`question.hintPrefix`, `question.shortAnswerNote`); gợi ý định dạng là "0-9" cho `INTEGER`, "0-9 · ." cho `DECIMAL` và `FRACTION`, không có cho các định dạng khác; không có gợi ý nào thì chỉ hiện câu ghi chú. `INTEGER`, `DECIMAL`, `FRACTION` mở bàn phím số của hệ điều hành, và ngay dưới ô có một hàng 4 phím của app: "-", "/", ",", "."; bấm phím chèn đúng ký tự đó vào vị trí con trỏ (thay vùng đang chọn), con trỏ đứng sau ký tự vừa chèn, ô giữ focus và bàn phím không đóng; mỗi phím có tên cho trình đọc màn hình. Hàng phím này không phải thanh ký hiệu của R-123. Ô nhập không tự sửa chính tả, không tự thay dấu câu (dấu nháy cong, gạch dài, dấu chấm khi gõ hai khoảng trắng), không gợi ý từ. Giá trị gửi `{"type":"SHORT_ANSWER","text":"<đúng chữ trong ô>"}`, không cắt khoảng trắng.

- Nguồn: `short-answer.tsx:44-112,141-154`; `mappers.ts:143-146`; `ItemGrader.java:606-636`; `ui-strings.ts:214-217`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-478** Khi câu có `inputHint = "Số nguyên, không ghi đơn vị"` và `answerFormat = "INTEGER"` thì dưới ô hiện "Hướng dẫn nhập: Số nguyên, không ghi đơn vị · 0-9. Đáp số được so khớp đúng cách viết, nên hãy viết theo hướng dẫn nhập ở trên." và bấm vào ô thì mở bàn phím số.
  - **AC-479** Khi học viên gõ "  56 " thì giá trị gửi là `{"type":"SHORT_ANSWER","text":"  56 "}`.
  - **AC-480** *(Giới hạn)* Khi câu `FRACTION` và học viên nhập "-3/4" bằng bàn phím số mở sẵn và hàng phím dưới ô thì ô chứa đúng "-3/4" mà học viên không phải đổi bàn phím hay chuyển lớp phím của bàn phím hệ thống.
  - **AC-481** *(Giới hạn)* Khi học viên gõ `"CO2" -- x` trên iOS hoặc Android bật sẵn tự sửa và dấu câu thông minh thì ô chứa đúng các ký tự đã gõ: dấu nháy thẳng, hai dấu gạch ngắn.

### R-123 Thanh chèn ký hiệu

Câu Trả lời ngắn có `answerFormat` là `CHEM_FORMULA`, `CHEM_EQUATION` hoặc `TEXT`, hoặc có `inputHint` khớp từ khóa, hiện thanh ký hiệu. Cách khớp giống web: bỏ dấu của `inputHint` (tách dấu rồi bỏ dấu, "đ" thành "d"), không kể hoa thường, rồi tìm "ion" như một từ đứng riêng (không khớp khi "ion" nằm trong từ khác như "cation", "anion") hoặc tìm một trong các cụm "cong thuc", "ky hieu", "ki hieu", "formula", "equation", "symbol" ở bất kỳ đâu. Thanh có 12 nút dưới ô: "+", "−", "(", ")", "²⁺", "³⁺", "²⁻", "³⁻", "₂", "₃", "₄", "→". Bấm nút chèn chữ ASCII tương ứng ("+", "-", "(", ")", "2+", "3+", "2-", "3-", "2", "3", "4", "->") vào đúng vị trí con trỏ (thay vùng đang chọn), con trỏ đứng sau chữ vừa chèn, ô giữ focus và bàn phím không đóng. `INTEGER`, `DECIMAL`, `FRACTION` không có thanh. Mỗi nút có tên cho trình đọc màn hình theo `question.symbolInput.symbolNames`.

- Nguồn: `symbol-input/symbols.ts:17-75` (từ khóa ở `:50`, bỏ dấu ở `:52-57`, thứ tự xét ở `:59-75`); `symbol-input/symbol-input-bar.tsx:41-83`; `short-answer.tsx:47-48,92-98`; `ui-strings.ts:312-328`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-482** Khi ô chứa "SO4" với con trỏ ở cuối và học viên bấm "²⁻" thì ô chứa "SO42-" và con trỏ ở cuối.
  - **AC-483** Khi ô chứa "Fe + O" với con trỏ sau "Fe" và học viên bấm "₂" thì ô chứa "Fe2 + O" và con trỏ đứng ngay sau "2".
  - **AC-484** *(Giới hạn)* Khi câu có `answerFormat = "DECIMAL"` và `inputHint` chứa "công thức" thì không có thanh ký hiệu.
  - **AC-725** *(Giới hạn)* Khi câu không có `answerFormat` thì: với `inputHint = "Viết công thức của ion"` hoặc `"Ghi KÝ HIỆU nguyên tố"` có thanh ký hiệu; với `inputHint = "Dung dịch chứa cation"` hoặc `"anion"` không có thanh ký hiệu.

### R-124 Điền khuyết

Ngữ liệu của câu hiện như R-106 đến R-116; mỗi dấu `[[id]]` trong chữ được thay bằng một ô nhập nằm ngay trong dòng, rộng khoảng 6 ký tự, chữ đậm canh giữa, xuống dòng cùng chữ bao quanh. Ô thứ k (theo thứ tự xuất hiện) có chữ chờ "Ô k" (`question.clozePlaceholder`) và tên cho trình đọc màn hình "Câu (displayNo + k − 1), ô trống k" (`question.clozeBlankAria`). Phía trên ngữ liệu: "Gõ đáp án vào từng ô trong đoạn văn. Mỗi ô là một câu riêng trong danh sách câu hỏi." (`question.clozeHelp`) và bộ đếm "Đã điền f/t ô" (`question.clozeFilled`) cập nhật khi gõ, ô chỉ có khoảng trắng không tính; phía dưới: "Ô bỏ trống tính là chưa trả lời; các ô được chấm riêng." (`question.clozeBlankNote`). Ô nhập theo luật bàn phím của R-122 (không tự sửa, không thay dấu câu). Giá trị gửi `{"type":"CLOZE","blanks":{"<id>":"<chữ>",…}}`, giữ cả ô đã xóa về chuỗi rỗng.

- Nguồn: `cloze.tsx:17,31-104,113-189`; `mappers.ts:147-150`; `ui-strings.ts:218-224`; `AnswerValueValidator.java:115-132`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-485** Khi app hiện câu `demo-cloze` của `question-samples.json` thì có 2 ô trong dòng chữ, đúng tại vị trí `[[b1]]` và `[[b2]]`, chữ chờ "Ô 1" và "Ô 2", công thức `\ce{2H2 + O2 -> 2H2O}` trong cùng đoạn được vẽ, và bộ đếm là "Đã điền 0/2 ô".
  - **AC-486** Khi học viên gõ "100" vào ô 1 thì bộ đếm thành "Đã điền 1/2 ô" và giá trị gửi là `{"type":"CLOZE","blanks":{"b1":"100"}}`.
  - **AC-487** *(Giới hạn)* Khi học viên xóa hết chữ ở ô 1 thì bộ đếm về "Đã điền 0/2 ô" và giá trị gửi là `{"type":"CLOZE","blanks":{"b1":""}}`.
  - **AC-488** *(Giới hạn)* Khi bàn phím mở cho một ô nằm ở nửa dưới màn hẹp thì ô đó cuộn lên nằm trên bàn phím, nhìn thấy được trong lúc gõ.

### R-125 Đúng/Sai

Dòng "Chọn Đúng hoặc Sai cho từng ý" (`question.trueFalseHelp`), rồi mỗi ý một hàng: chữ "a)", "b)"…, nội dung ý, và hai nút chọn "Đúng" / "Sai" (`question.trueOption`, `question.falseOption`); hàng đã chọn có nền khác hàng chưa chọn. Trên Bề rộng hẹp, hai nút xuống dưới nội dung ý khi không đủ chỗ. Bấm lại nút đang chọn là bỏ chọn ý đó. Giá trị gửi `{"type":"TRUE_FALSE","statements":{"<id>":true|false,…}}`, chỉ gồm ý đã chọn.

- Nguồn: `true-false.tsx:53-137`; `mappers.ts:151-160`; `ui-strings.ts:225-227`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-489** Khi câu có 4 ý và học viên chọn "Đúng" ở ý a, "Sai" ở ý c thì giá trị gửi là `statements` = {id a: true, id c: false}.
  - **AC-490** *(Giới hạn)* Khi ý a đang "Đúng" và học viên bấm lại "Đúng" ở ý a thì ý a không còn lựa chọn nào và id của ý a không có trong `statements` gửi đi.

### R-126 Ghép đôi

Dòng luật dùng lựa chọn — "Chọn lựa chọn cho từng mục trong danh sách. Mỗi lựa chọn chỉ dùng một lần: lựa chọn đã ghép bị làm mờ trong danh sách của các mục khác; bỏ ghép để dùng lại." khi `reuseRight=false`, "Một lựa chọn ở cột phải có thể ghép với nhiều mục." khi `true` (`question.matchExclusive`, `question.matchReusable`) — và bộ đếm "Đã ghép d/n mục" (`question.matchDone`). Mỗi mục trái một hàng: số thứ tự, nội dung, chữ "Ghép với", một ô chọn chữ chờ "Chọn…" (`question.matchPlaceholder`). Mở ô chọn hiện danh sách các mục phải theo R-119, mỗi mục có chữ cái và nội dung vẽ đủ công thức; khi `reuseRight=false`, mục phải đã ghép với mục trái khác bị làm mờ và không chọn được. Hàng đã ghép có nút "Bỏ ghép mục n" (`question.matchClear`). Giá trị gửi `{"type":"MATCHING","pairs":{"<leftId>":"<rightId>",…}}`, chỉ gồm cặp đã ghép.

- Nguồn: `matching.tsx:32-157`; `match-select.tsx:24-115`; `mappers.ts:161-170`; `AnswerValueValidator.java:150-177`; `ui-strings.ts:232-243`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-491** Khi câu có 3 mục trái, 4 mục phải, `reuseRight=false`, và học viên ghép mục 1 với "b" thì hàng 1 hiện "b." kèm nội dung, bộ đếm "Đã ghép 1/3 mục", và trong ô chọn của mục 2, mục "b" bị làm mờ, bấm vào không có tác dụng.
  - **AC-492** Khi `reuseRight=true` và mục 1 đã ghép "b" thì ô chọn của mục 2 vẫn chọn được "b"; giá trị gửi có cả hai cặp trỏ tới id của "b".
  - **AC-493** *(Giới hạn)* Khi học viên bấm "Bỏ ghép mục 1" thì hàng 1 về "Chọn…", bộ đếm giảm 1, id mục 1 không có trong `pairs`, và "b" chọn lại được ở các mục khác.
  - **AC-494** Khi mục phải chứa `\(\ce{H2SO4}\)` thì cả trong danh sách chọn và trên hàng đã ghép, công thức được vẽ, không hiện mã nguồn.

### R-127 Bộ câu hỏi: ngữ liệu và câu con trên một cột và hai cột

Bộ câu hỏi hiện dòng "Ngữ liệu chung cho câu a–b" (`question.setPassageCaption`, a và b là số nhỏ nhất và lớn nhất của các câu con), đề chung nếu có, khung ngữ liệu, rồi từng câu con là một câu đầy đủ theo dạng của nó (tiêu đề, chip, nút cờ riêng). Trên Hai cột, như web từ 1024px: ngữ liệu ở vùng trái, có đầu khung "Câu a–b", ghim ở đỉnh vùng hiển thị của tờ đề và cuộn bên trong khung của nó khi cao hơn màn; các câu con ở vùng phải và cuộn cùng tờ đề; ngữ liệu luôn nhìn thấy khi cuộn câu con. Trên một cột: khung ngữ liệu ở trên, các câu con ở dưới, cuộn chung. Tệp đính kèm của bộ và tệp audio của các câu con hiện một lần ở đầu bộ (R-132). Mỗi câu con lưu câu trả lời riêng như một câu đơn.

- Nguồn: `question-set.tsx:48-110`; `mappers.ts:183-266`; `attempt-player.tsx:341-360,2128-2164`; `ui-strings.ts:260-261,289-290`; quyết định của đợt khung và xác thực (bố cục theo bề rộng: Hai cột từ 840dp trên Android, bề rộng regular trên iOS)
- Áp dụng cho: chế độ làm bài, khóa, xem lại
- Tiêu chí chấp nhận:
  - **AC-495** Khi app hiện câu `demo-set` của `question-samples.json` trên điện thoại thì thấy "Ngữ liệu chung cho câu a–b", khung ngữ liệu có bảng số liệu, rồi các câu con bên dưới, cuộn chung một luồng.
  - **AC-496** Khi app hiện cùng câu trên tablet Android từ 840dp hoặc iPad regular thì ngữ liệu ở vùng trái và câu con ở vùng phải; cuộn tờ đề tới câu con cuối thì khung ngữ liệu vẫn ghim ở đỉnh và hiện đầu ngữ liệu; khi ngữ liệu cao hơn màn, cuộn bên trong khung ngữ liệu không làm các câu con di chuyển.
  - **AC-497** *(Giới hạn)* Khi xoay iPad từ ngang sang dọc mà bề rộng vẫn regular thì vẫn hai vùng; khi chia đôi màn hình về compact thì chuyển một cột, câu trả lời đang nhập dở và câu đang xem không đổi.
  - **AC-498** Khi học viên chọn đáp án ở câu con thứ 2 thì app gửi câu trả lời cho id của câu con đó, không gửi gì cho id của bộ.

### R-128 Giới hạn câu trả lời do máy chủ kiểm

App không chặn hay cắt chữ học viên gõ để vừa 65 536 byte; máy chủ kiểm. Câu trả lời vượt giới hạn hoặc sai cấu trúc bị máy chủ trả `REJECTED` lý do `INVALID_ANSWER`; khi đó app hiện trạng thái lưu "Chưa lưu · Dữ liệu không hợp lệ" (`attempt.saveStatus.unsaved`, `attempt.saveStatus.reasons.rejected`) theo mục 6.4, và chữ trong ô không bị xóa. App không bao giờ tạo câu trả lời sai cấu trúc: id luôn lấy từ thân câu, `optionIds` không trùng, Ghép đôi không trùng mục phải khi `reuseRight=false`, chữ không có ký tự `\u0000`.

- Nguồn: `AnswerValueValidator.java:29,72-199,237-243`; `AnswerService.java:399-411`; `vi.json:417-428`
- Áp dụng cho: mọi dạng câu ở chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-499** *(Từ chối)* Khi học viên dán 70 000 ký tự Latin vào ô Trả lời ngắn thì ô chứa đủ 70 000 ký tự, lần lưu nhận `REJECTED INVALID_ANSWER` và dòng trạng thái lưu là "Chưa lưu · Dữ liệu không hợp lệ".
  - **AC-500** *(Giới hạn)* Khi học viên dán chuỗi chứa ký tự `\u0000` vào ô Trả lời ngắn, Điền khuyết hoặc Tự luận thì ký tự đó không có trong giá trị gửi đi.
  - **AC-501** Khi chạy test tự động bấm ngẫu nhiên 1 000 thao tác chọn, bỏ chọn, ghép, bỏ ghép trên câu mẫu của mỗi dạng thì mọi giá trị app tạo ra đều qua bộ kiểm của backend (không lần nào `INVALID_ANSWER`).

### R-129 Tự luận: trình soạn và HTML gửi lên

Ô viết có nhãn "Bài viết của bạn" (`question.essayEditorLabel`), chữ chờ "Viết bài của bạn tại đây" (`question.essayPlaceholder`), cao tối thiểu khoảng 10 dòng, và một thanh công cụ đúng sáu nút: In đậm, In nghiêng, Danh sách dấu đầu dòng, Danh sách đánh số, Hoàn tác, Làm lại (`attempt.editor.*`); nút đang bật có trạng thái nhấn, Hoàn tác/Làm lại mờ khi không có gì để hoàn tác/làm lại. Trong lúc trình soạn chưa sẵn sàng hiện "Đang tải trình soạn bài viết…" (`attempt.editor.loading`). Dưới ô là ghi chú `question.essayNote`. HTML gửi lên chỉ gồm `p`, `br`, `strong`, `em`, `ul`, `ol`, `li` (nội dung mục là `p`), trong chữ, `<`, `>`, `&` luôn được thoát thành `&lt;`, `&gt;`, `&amp;` (các ký tự khác, kể cả `"`, được thoát hay không đều được, vì máy chủ lọc lại và trả bản của nó), không thuộc tính; bài trống hoặc chỉ khoảng trắng gửi `html = ""`, `wordCount = 0`; không có đoạn rỗng thừa ở cuối. Dán chữ từ nơi khác giữ đậm, nghiêng, danh sách và bỏ mọi định dạng khác (ảnh, liên kết, màu, cỡ chữ, gạch chân). Không chèn được ảnh hay tệp.

- Nguồn: `tiptap-essay-editor.tsx:57-61,138-175,303-353`; `html-contract.ts:268-277`; `essay.tsx:80-120`; `vi.json:309-318`; `ui-strings.ts:249-254`; `AnswerValueValidator.java:179-199`; mô tả dạng xuất của `rich-text-samples.json` (`_about`)
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-502** Khi học viên gõ "Mở bài", xuống dòng, bật danh sách đánh số rồi gõ "Ý một" thì giá trị gửi có `html = "<p>Mở bài</p><ol><li><p>Ý một</p></li></ol>"`.
  - **AC-503** Khi app gửi một bài viết rồi tải lại trạng thái lượt (`GET /api/v1/me/attempts/{id}`) thì, với 10 bài mẫu có đậm, nghiêng, hai loại danh sách lồng nhau, xuống dòng trong đoạn, khoảng trắng không ngắt và các ký tự `< > & "`: phân tích `answer.html` máy chủ trả và chuỗi app đã gửi thành cây HTML thì hai cây có cùng thẻ theo cùng thứ tự và cùng chữ trong từng nút (so sau khi giải mã thực thể), `wordCount` máy chủ trả bằng `wordCount` app đã gửi, và mở lại câu đó trong app hiện đúng bài viết đã gõ.
  - **AC-504** *(Giới hạn)* Khi học viên xóa hết chữ thì giá trị gửi là `{"type":"ESSAY","html":"","wordCount":0}`.
  - **AC-505** *(Từ chối)* Khi học viên dán một đoạn từ trình duyệt có ảnh, liên kết và chữ màu đỏ gạch chân thì bài viết chỉ còn chữ (đậm, nghiêng, danh sách nếu có), không ảnh, không liên kết, không màu, không gạch chân.

### R-130 Tự luận: đếm chữ và mức tối thiểu

Dưới ô viết, dòng đếm chữ cập nhật sau mỗi lần sửa: "n chữ" khi câu không có `wordLimit`, "n / tối thiểu m chữ" khi có (`question.essayWordCount`). Khi có `wordLimit` và chưa đủ, dòng cảnh báo màu cảnh báo "Còn k chữ nữa để đạt yêu cầu." (`question.wordsNeeded`); đủ thì "Đã đạt yêu cầu số chữ" (`question.wordLimitMet`) màu thành công. Cảnh báo không chặn nộp. Luật đếm giống web và máy chủ: bỏ thẻ, bỏ công thức, chuẩn hóa NFC, tách theo khoảng trắng (kể cả khoảng trắng không ngắt), cụm có ít nhất một chữ cái hoặc chữ số là một chữ, ranh giới đoạn và mục danh sách là khoảng trắng. `wordCount` gửi lên là số đếm theo luật này.

- Nguồn: `essay.tsx:58-62,109-118`; `count-words.ts:1-48`; `AnswerValueValidator.java:202-235`; `ui-strings.ts:251-259`
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-506** Khi `wordLimit = 200` và bài có 150 chữ thì hiện "150 / tối thiểu 200 chữ" và "Còn 50 chữ nữa để đạt yêu cầu."; nộp bài vẫn được (mục 6.4).
  - **AC-507** Khi bài là `<p>a</p><p>b</p>` thì đếm 2 chữ; khi bài là "Xin chào – thế giới !" thì đếm 4 chữ ("–" và "!" không có chữ cái hay chữ số nên không tính).
  - **AC-508** *(Giới hạn)* Khi chạy bộ 30 bài mẫu (có công thức, gạch nối, số, ký hiệu đứng riêng, khoảng trắng không ngắt, tiếng Việt tổ hợp và dựng sẵn) qua bộ đếm của app và `countWords` của web thì mọi kết quả bằng nhau.

### R-131 Gõ tiếng Việt bằng bộ gõ

Trong mọi ô nhập của phần này (Trả lời ngắn, Điền khuyết, Tự luận), chữ đang được bộ gõ ghép dấu (Telex, VNI, bàn phím tiếng Việt của iOS, Gboard, Laban Key) không bị nhân đôi, không mất ký tự, không nhảy con trỏ. Cụm chữ đang ghép (chưa được bộ gõ chốt) không bao giờ được gửi lên máy chủ: mỗi lần gửi mang toàn bộ chữ đã chốt tại thời điểm gửi, không kèm cụm đang ghép; cụm đó đi theo lần gửi sau khi đã chốt. Mức chờ tối đa 15 giây của nhịp lưu Tự luận (xem mục 6.4) chỉ áp cho chữ đã chốt: tới mốc 15 giây, app gửi chữ đã chốt dù học viên vẫn đang ghép một cụm. Bộ đếm chữ và bộ đếm ô cập nhật theo chữ đã chốt. Hoàn tác trong Tự luận không làm hỏng chữ đang ghép. Bộ gõ bắt buộc khi nghiệm thu: Telex trên Gboard (Android) và bàn phím tiếng Việt Telex của hệ thống (iOS). Laban Key được kiểm bằng tay khi thiết bị kiểm có cài và ghi kết quả vào báo cáo nghiệm thu; kết quả đó không chặn nghiệm thu.

- Nguồn: `tiptap-essay-editor.tsx:138-175` (trình soạn web xử lý ghép chữ của trình duyệt); giả định của phần này (chữ đang ghép, bộ gõ bắt buộc)
- Áp dụng cho: chế độ làm bài
- Tiêu chí chấp nhận:
  - **AC-509** Khi học viên gõ Telex "Tieengs Vieetj cos daaus" trong ô Tự luận bằng Gboard kiểu Telex trên Android và bằng bàn phím tiếng Việt Telex của hệ thống trên iOS, thì ô chứa đúng "Tiếng Việt có dấu" và bộ đếm là "4 chữ". Cùng thao tác bằng Laban Key trên thiết bị có cài được kiểm bằng tay và ghi kết quả trong báo cáo nghiệm thu, không chặn nghiệm thu.
  - **AC-510** *(Giới hạn)* Khi học viên đang gõ dở "Vieej" (chưa chốt) trong ô Điền khuyết thì không có lần lưu nào gửi chuỗi chưa chốt; sau khi chốt, giá trị gửi là "Việ…" đúng như chữ trong ô.
  - **AC-726** *(Giới hạn)* Khi ô Tự luận có thay đổi đã chốt chưa gửi từ 14 giây trước, học viên vẫn gõ liên tục, và ở giây thứ 15 ô chứa "Bài làm tiếng " đã chốt cùng cụm "Vieej" đang ghép, thì trong vòng 1 s sau mốc 15 giây app gửi một giá trị mà `html` có chữ "Bài làm tiếng" và không có chữ nào của cụm đang ghép ("V", "Vie", "Viee", "Vieej", "Việ"); sau khi cụm chốt thành "Việt", lần gửi kế tiếp có "Bài làm tiếng Việt".
  - **AC-511** *(Giới hạn)* Khi học viên gõ "Đường thẳng" vào ô Tự luận, bấm Hoàn tác một lần rồi Làm lại một lần thì ô chứa lại đúng "Đường thẳng".

### R-132 Tệp đính kèm của câu

Tệp đính kèm của câu (khi là Bộ câu hỏi: tệp của bộ cộng tệp audio của các câu con, gộp không trùng theo `assetId`, như web; tệp không phải audio của câu con không hiện, như web) hiện trong một khung "Tệp đính kèm" (`attempt.player.attachmentsLabel`) ở đầu câu, trên tiêu đề. Tệp audio (`mime` bắt đầu bằng `audio/`) hiện trình phát của R-133 đến R-137, có tên tệp phía trên. Tệp khác hiện biểu tượng tệp và tên tệp; bấm vào thì app tải tệp qua `GET /api/v1/me/attempts/{attemptId}/assets/{assetId}` kèm Bearer và mở bằng trình xem tệp của hệ điều hành, không rời màn làm bài; tệp tải về được giữ trên máy theo R-177.

- Nguồn: `attempt-player.tsx:341-360` (chỉ audio của câu con được gộp, `:355-358`), `:2061-2125`; `mappers.ts:56-67`; `wire-parsers.ts:161-176`; `vi.json:411`
- Áp dụng cho: chế độ làm bài và khóa
- Tiêu chí chấp nhận:
  - **AC-512** Khi Bộ câu hỏi có một audio và câu con thứ 2 có cùng audio đó thì khung "Tệp đính kèm" ở đầu bộ chỉ có một trình phát.
  - **AC-513** Khi câu có tệp PDF "bang-so-lieu.pdf" và học viên bấm tên tệp thì tệp mở trong trình xem của hệ điều hành; đóng trình xem thì quay về đúng câu đang làm.
  - **AC-514** *(Từ chối)* Khi tải tệp trả `404 NOT_FOUND` thì app báo không mở được tệp và màn làm bài không đổi.
  - **AC-727** *(Giới hạn)* Khi Bộ câu hỏi không có tệp riêng và câu con thứ 1 có một tệp PDF và một tệp audio thì khung "Tệp đính kèm" ở đầu bộ chỉ có trình phát của tệp audio; tệp PDF của câu con không hiện ở đâu trên tờ đề.

### R-133 Audio: nghe, phát tiếp, hết lượt

Trình phát có một nút chính, nhãn chỉ lượt nghe, thời gian "mm:ss / mm:ss" và thanh tiến độ chỉ để xem. Nhãn lượt: "Còn x/y lần nghe" (`question.audioPlayer.playsRemaining`), hết lượt thì "Đã hết lượt nghe (0/y)" (`playsExhaustedBadge`); y là `audioPolicy.maxPlays` của trạng thái lượt (hiện là 2), x là `playsLeft` máy chủ trả. Nút chính: "Nghe" (`play`) khi chưa phát; "Đang phát…" (`playing`, không bấm được) khi đang phát; "Phát lại" (`resume`) khi lần nghe đang dở mà âm thanh đã dừng. Bấm "Nghe" gọi `POST …/audio/{assetId}/plays` với một `requestId` mới kèm `X-Attempt-Session`, rồi tải âm thanh `GET …/assets/{assetId}?playId=` kèm Bearer và phát từ `positionMs`. Hết tệp thì gọi `POST …/plays/{playId}/end`; nếu còn lượt, nút trở về "Nghe". Bấm "Phát lại" gọi `POST …/plays/{playId}/resume` và phát tiếp từ `positionMs` máy chủ trả, không mất lượt. Mở màn khi máy chủ báo lần nghe đang `PLAYING` (sau khi app bị tắt, sau khi rời màn làm bài, hay mở lại lượt) thì app không tự phát: nút chính là "Phát lại" như R-136, bấm thì gọi `resume`, không mở lần nghe mới. Gửi lặp cùng `requestId` (mạng lỗi rồi bấm lại) không làm tăng số lần đã dùng.

- Nguồn: `audio-player.tsx:387-480,504-541,656-755,820-946`; `attempt-audio-adapter.ts:111-184,275-283`; `AttemptAudioService.java:94-156,198-231`; `AttemptAudioPlay.java:22`; `ui-strings.ts:291-311`
- Áp dụng cho: câu có tệp audio, chế độ làm bài, phần `OPEN`
- Tiêu chí chấp nhận:
  - **AC-515** Khi tệp audio chưa nghe lần nào thì trình phát hiện "Còn 2/2 lần nghe", "00:00 / mm:ss" và nút "Nghe"; bấm "Nghe" thì âm thanh phát, nút thành "Đang phát…", nhãn thành "Còn 1/2 lần nghe".
  - **AC-516** Khi lần nghe thứ hai phát hết thì app gọi `end`, nhãn thành "Đã hết lượt nghe (0/2)", nút "Nghe" mờ, và hiện "Bạn đã dùng hết số lần nghe cho tệp audio này." (`exhaustedReason`).
  - **AC-517** *(Giới hạn)* Khi bấm "Nghe" mà request `plays` mất mạng sau khi máy chủ đã mở lần nghe, rồi học viên bấm lại khi có mạng, thì app gửi lại cùng `requestId` và nhãn chỉ giảm một lượt.
  - **AC-518** *(Giới hạn)* Khi học viên bấm "Nghe" hai lần liên tiếp trong 300 ms thì chỉ có một request `plays`.
  - **AC-519** Khi học viên thoát app trong lúc đang phát ở 01:10 rồi mở lại lượt thi (phiên vẫn của máy này) thì trình phát không tự phát và nút chính là "Phát lại"; bấm "Phát lại" thì app gọi `resume`, phát tiếp từ vị trí máy chủ đã lưu, nhãn lượt không đổi.
  - **AC-520** *(Từ chối)* Khi `plays` trả `409 AUDIO_PLAY_IN_PROGRESS` kèm `playId` thì app gọi `resume` với `playId` đó và phát tiếp, không hiện lỗi.

### R-134 Audio: không tua, tốc độ cố định

Học viên không tua, không đổi tốc độ, không tạm dừng được: trình phát không có thanh kéo, nút tua, nút tốc độ hay nút dừng; thanh tiến độ không nhận chạm. Tốc độ luôn là 1×. Điều khiển phát của hệ điều hành (màn khóa, Trung tâm điều khiển, thông báo phát nhạc, tai nghe Bluetooth, đồng hồ) không có tua, không có đổi tốc độ, không có nút bỏ qua; nếu hệ thống vẫn gửi lệnh tua thì app bỏ qua. Nếu vị trí phát vẫn bị đẩy đi, app trả về vị trí cũ.

- Nguồn: `audio-player.tsx:84-88,407-408,779-802`; `AttemptAudioService.java:159-195`; `AttemptStateResponse.java:112` (`seekable=false`)
- Áp dụng cho: câu có tệp audio
- Tiêu chí chấp nhận:
  - **AC-521** Khi audio đang phát và học viên mở màn khóa (iOS) hoặc thông báo phát nhạc (Android) thì không có thanh tua, nút tua 10 giây, nút tốc độ hay nút bài kế.
  - **AC-522** *(Từ chối)* Khi học viên bấm nút tạm dừng trên tai nghe Bluetooth trong lúc phát thì âm thanh dừng và nút chính thành "Phát lại"; bấm "Phát lại" thì phát tiếp từ vị trí máy chủ đã lưu (có thể sớm hơn chỗ đã dừng tối đa 10 s), và không có cách nào phát tiếp từ vị trí muộn hơn vị trí máy chủ đã lưu.
  - **AC-523** *(Từ chối)* Khi máy chủ trả `409 AUDIO_SEEK_REJECTED` kèm `positionMs` cho một lần báo tiến độ thì app đưa vị trí phát về `positionMs` đó và tiếp tục phát.

### R-135 Audio: báo tiến độ, lease, lỗi máy chủ

Khi đang phát, app báo tiến độ `PUT …/plays/{playId}/progress {positionMs}` mỗi 10 s; vị trí báo không lùi so với vị trí máy chủ đã nhận và không vượt thời lượng. Mỗi lần báo hoặc phát tiếp gia hạn lease 45 s; khi lease đã hết, tải âm thanh bị từ chối và app phát tiếp bằng `resume` (không mở lần nghe mới). Xử lý lỗi theo mã: `AUDIO_PLAYS_EXHAUSTED` → trạng thái hết lượt; `AUDIO_PLAY_STALE` → dừng phát, tải lại trạng thái lượt và hiện đúng lượt còn lại; `PART_CLOSED`, `TIME_UP` → R-137; `ATTEMPT_SESSION_REVOKED` → dừng ngay, xử lý mở nơi khác theo mục 6.4; `401` → theo phần chung (R-4); lỗi mạng hoặc lỗi khác → dừng, hiện "Không thể phát audio lúc này. Bấm lại để thử tiếp." (`playErrorRetry`) và nút "Phát lại" (lần nghe đang dở) hoặc "Nghe" (chưa mở được).

- Nguồn: `audio-player.tsx:41,227-385`; `attempt-audio-adapter.ts:48-83`; `AttemptAudioService.java:159-195,233-328`; `AttemptAssetService.java:201-222`; `application.yml:203-205`
- Áp dụng cho: câu có tệp audio, phần `OPEN`
- Tiêu chí chấp nhận:
  - **AC-524** Khi audio phát liên tục 35 s thì app đã gửi 3 lần `progress` với `positionMs` tăng dần, mỗi lần cách nhau 10 s ± 1 s.
  - **AC-525** *(Từ chối)* Khi `progress` trả `409 AUDIO_PLAY_STALE` thì âm thanh dừng, app gọi `GET /api/v1/me/attempts/{id}` và trình phát hiện nhãn lượt theo `audioPlays` mới.
  - **AC-526** *(Từ chối)* Khi `progress` trả `409 ATTEMPT_SESSION_REVOKED` thì âm thanh dừng trong vòng 1 s và trình phát hiện "Phiên làm bài đã chuyển sang thiết bị khác, audio đã dừng." (`evictedReason`).
  - **AC-527** *(Giới hạn)* Khi lần nghe đang dở và đã quá 45 s không có `progress` hay `resume` (ví dụ máy ở nền), học viên bấm "Phát lại" thì app gọi `resume` rồi tải lại âm thanh thành công; nhãn lượt không đổi.

### R-136 Audio: app vào nền, cuộc gọi, mất mạng

Khi app vào nền, khi có cuộc gọi hoặc ứng dụng khác chiếm âm thanh, khi rút tai nghe, app dừng phát ngay; lần nghe vẫn đang dở trên máy chủ. Quay lại app (hoặc cuộc gọi kết thúc) thì nút chính là "Phát lại" kèm dòng báo âm thanh đã dừng và bấm để nghe tiếp không mất lượt (câu chữ mới, xem A-92); app không tự phát lại. Khi trình phát rời khỏi màn hình vì học viên chuyển sang phần khác (`navigationMode = FREE`, xem mục 6.4) hoặc rời màn làm bài sang mục khác của app mà app vẫn ở nền trước, app dừng phát ngay như khi vào nền; lần nghe vẫn đang dở, quay lại thì nút là "Phát lại" và app không tự phát. Bấm "Nghe" hay "Phát lại" ở một tệp audio khác trong lúc một tệp đang phát thì tệp đang phát không dừng, như web: mỗi tệp có trình phát và lượt nghe riêng, và app không coi trình phát của chính nó là "ứng dụng khác chiếm âm thanh". Bấm "Phát lại" phát tiếp từ vị trí máy chủ đã lưu (lần báo tiến độ gần nhất, sớm hơn tối đa 10 s so với chỗ đã nghe). Mất mạng thì dừng và hiện "Mất kết nối mạng, đang chờ kết nối lại để phát tiếp." (`offlineWaiting`), nút mờ; có mạng lại thì tự phát tiếp bằng `resume`. Khi app khóa sinh trắc học sau 5 phút ở nền thì mở khóa xong vẫn ở trạng thái "Phát lại".

- Nguồn: `audio-player.tsx:454-480,622-654,859-865`; `AttemptAudioService.java:135-156`; `audio-player.tsx:495-502` (web dừng phát khi trình phát bị gỡ khỏi màn); giả định của phần này (web không có hành vi khi tab ở nền; hai tệp phát cùng lúc như web); quyết định của đợt khung và xác thực (khóa app sau 5 phút ở nền)
- Áp dụng cho: câu có tệp audio, phần `OPEN`
- Tiêu chí chấp nhận:
  - **AC-528** Khi audio đang phát ở 00:25 (lần báo gần nhất ở 00:20) và học viên bấm nút Home thì âm thanh dừng trong vòng 1 s; quay lại app thì nút là "Phát lại"; bấm thì phát tiếp từ 00:20 và nhãn lượt không đổi.
  - **AC-529** Khi có cuộc gọi đến trong lúc phát thì âm thanh dừng; sau khi cuộc gọi kết thúc, app không tự phát, nút là "Phát lại".
  - **AC-530** *(Giới hạn)* Khi tắt mạng trong lúc phát thì âm thanh dừng và hiện "Mất kết nối mạng, đang chờ kết nối lại để phát tiếp."; bật mạng lại thì app gọi `resume` và phát tiếp mà không cần bấm.
  - **AC-531** *(Giới hạn)* Khi học viên rút tai nghe có dây trong lúc phát thì âm thanh không phát ra loa ngoài; nút thành "Phát lại".
  - **AC-728** *(Giới hạn)* Khi lượt có `navigationMode = FREE`, audio của phần 1 đang phát và học viên chuyển sang phần 2 (phần 1 vẫn `OPEN`) thì âm thanh dừng trong vòng 1 s; quay lại phần 1 thì nút là "Phát lại", nhãn lượt không đổi, và app không tự phát.
  - **AC-729** *(Giới hạn)* Khi audio đang phát và học viên rời màn làm bài sang tab "Lịch sử" (app vẫn ở nền trước, lượt vẫn mở) thì âm thanh dừng trong vòng 1 s; quay lại màn làm bài thì nút là "Phát lại".
  - **AC-730** *(Giới hạn)* Khi tệp audio A đang phát và học viên bấm "Nghe" ở tệp audio B cùng phần thì app gọi `plays` cho B, cả A và B cùng phát, nhãn lượt của A không đổi và nhãn lượt của B giảm 1.

### R-137 Audio: phần đóng, hết giờ, nộp bài, mở nơi khác

Trình phát dừng ngay và khóa, kèm dòng lý do, khi: phần thi đóng hoặc bị bỏ qua, hoặc đang kết thúc phần — "Phần thi đã đóng, không thể nghe tiếp audio." (`partClosedReason`); hết giờ — "Đã hết giờ làm bài, audio đã dừng phát." (`timeUpReason`); đang nộp hoặc đã nộp — "Bài thi đã nộp, audio đã dừng phát." (`submittedReason`); phiên làm bài chuyển sang thiết bị khác hoặc chờ giành lại — `evictedReason`. Lúc đang nhận phiên làm bài, trình phát mờ; khi bị khóa vì lý do khác, hiện "Trình phát audio đang tạm khóa." (`disabledReason`). Máy chủ trả `PART_CLOSED` hoặc `TIME_UP` cho bất kỳ lời gọi audio nào thì app làm như trên và tải lại trạng thái lượt; `end` trả `endReason = "CLOSED"` cũng là phần đã đóng.

- Nguồn: `attempt-player.tsx:2016-2033,2077-2106`; `audio-player.tsx:286-295,365-377,599-620,837-858`; `AttemptAudioService.java:270-305`
- Áp dụng cho: câu có tệp audio
- Tiêu chí chấp nhận:
  - **AC-532** Khi audio đang phát và đồng hồ của phần về 00:00 thì âm thanh dừng trong vòng 1 s, nút mờ, và hiện "Đã hết giờ làm bài, audio đã dừng phát.".
  - **AC-533** Khi học viên xác nhận kết thúc phần trong lúc audio đang phát thì âm thanh dừng trước khi request kết thúc phần được gửi, và hiện "Phần thi đã đóng, không thể nghe tiếp audio.".
  - **AC-534** *(Từ chối)* Khi `resume` trả `409 PART_CLOSED` thì trình phát khóa với dòng `partClosedReason` và app gọi `GET /api/v1/me/attempts/{id}`.
  - **AC-535** *(Giới hạn)* Khi học viên mở cùng lượt thi trên web và giành phiên trong lúc app đang phát thì âm thanh trên app dừng trong vòng 1 s sau khi app biết phiên đã mất (lần gọi audio hoặc heartbeat kế tiếp) và hiện `evictedReason`.

### R-138 Chế độ khóa

Ở chế độ khóa, mọi thành phần của phần này vẫn hiện nội dung đề và giá trị đang có, nhưng không nhận chạm hay gõ: không chọn, không bỏ chọn, không mở ô chọn Ghép đôi, không mở bàn phím, thanh ký hiệu và thanh công cụ Tự luận mờ; vẫn cuộn, vẫn phóng to ảnh (R-110) và vẫn cuộn bảng, công thức. Khi nào dùng chế độ khóa do mục 6.4 quyết định.

- Nguồn: `attempt-player.tsx:1123,2142,2174,2408-2413`
- Áp dụng cho: tờ đề khi phần đã đóng, lượt đã đóng hoặc phiên bị khóa
- Tiêu chí chấp nhận:
  - **AC-536** Khi câu Một đáp án đang chọn B ở chế độ khóa và học viên bấm hàng C thì B vẫn được chọn và không có request lưu nào.
  - **AC-537** *(Từ chối)* Khi học viên bấm vào ô Tự luận ở chế độ khóa thì bàn phím không mở và chữ không đổi; chọn và sao chép chữ vẫn được.

### R-139 Chế độ xem lại: Một đáp án và Nhiều đáp án

Trong chế độ xem lại, khung câu hiện chip kết quả (`question.outcomes`: "Đúng", "Đúng một phần", "Sai", "Bỏ trống", "Chờ chấm"), mỗi chip có chữ và biểu tượng, và điểm "e / m điểm" (`question.points`) hoặc "Chưa có điểm · tối đa m điểm" khi chưa có điểm (`question.noPointsYet`, `question.maxPoints`); không có nút cờ. Phương án không chọn được. Phương án đã chọn có nhãn "Bạn chọn" (`yourPickTag`) khi đúng hoặc khi không được xem đáp án, "Bạn chọn · Sai" (`yourPickWrongTag`, viền đứt màu lỗi) khi sai; phương án đúng có nhãn "Đáp án đúng" (`correctAnswerTag`, viền màu thành công) khi được xem đáp án, kể cả khi học viên đã chọn nó (khi đó phương án có cả "Bạn chọn" và "Đáp án đúng"); Nhiều đáp án có thêm "Bạn không chọn" (`notPickedTag`) ở phương án đúng chưa chọn. Khi dữ liệu không có khóa đáp án hoặc không được xem đáp án, chỉ hiện "Bạn chọn". Có lời giải (`FULL_SOLUTION`) thì cuối câu có khối "Lời giải" (`solutionLabel`) vẽ như nội dung đề.

- Nguồn: `choice-question.tsx:36-170` (hai nhãn vẽ độc lập ở `:109-120`); `question-frame.tsx:65-104,124-228`; `shared.ts:37-53`; `ui-strings.ts:201-207,262-271`; mẫu `single`, `singleNoKey`, `multiple`
- Áp dụng cho: màn xem lại (mục 6.6)
- Tiêu chí chấp nhận:
  - **AC-538** Khi câu mẫu `single` hiện ở chế độ xem lại với câu trả lời sai thì phương án đã chọn có "Bạn chọn · Sai", phương án đúng có "Đáp án đúng", chip là "Sai", và bấm vào phương án không đổi gì.
  - **AC-539** *(Giới hạn)* Khi câu mẫu `singleNoKey` hiện ở chế độ xem lại thì chỉ có nhãn "Bạn chọn" trên phương án đã chọn, không có "Đáp án đúng" hay "Sai".
  - **AC-540** Khi câu Nhiều đáp án có đáp án A, C và học viên chọn A, B và được xem đáp án thì A có "Bạn chọn" và "Đáp án đúng", B có "Bạn chọn · Sai", C có "Đáp án đúng" và "Bạn không chọn".

### R-140 Chế độ xem lại: Trả lời ngắn và Điền khuyết

Trả lời ngắn: ô chỉ đọc chứa chữ đã nộp, cạnh ô là biểu tượng kết quả; bỏ trống thì "Chưa trả lời" (`notAnswered`); được xem đáp án thì dòng "Đáp án: <đáp án chuẩn> · <đáp án khác>…" (`correctAnswerPrefix`, đáp án chuẩn đậm) kèm "(e / m điểm)" khi có điểm. Điền khuyết: các ô trong đoạn chỉ đọc, viền và nền theo kết quả từng ô (đúng, đúng một phần, sai), góc ô có biểu tượng kết quả kèm tên cho trình đọc màn hình; dưới đoạn là bảng: "Mục" ("Câu n"), "Bạn chọn" (chữ đã điền hoặc "bỏ trống"), "Đáp án được chấp nhận" (nối bằng " · ", chỉ khi được xem đáp án), "Điểm" ("e / m điểm" hoặc "—", chỉ khi có ô có điểm).

- Nguồn: `short-answer.tsx:86-91,113-135`; `cloze.tsx:157-263`; `ui-strings.ts:272,280-285`; mẫu `short`, `cloze`, `clozeText`
- Áp dụng cho: màn xem lại (mục 6.6)
- Tiêu chí chấp nhận:
  - **AC-541** Khi câu mẫu `short` hiện ở chế độ xem lại thì ô chứa chữ đã nộp, không mở bàn phím khi bấm, và dòng đáp án là "Đáp án: 56 · 56 phút".
  - **AC-542** Khi câu mẫu `cloze` có ô 1 đúng, ô 2 sai thì ô 1 viền màu thành công, ô 2 viền màu lỗi, và bảng có 2 hàng "Câu 22", "Câu 23" với chữ đã điền, đáp án chấp nhận và điểm từng ô.
  - **AC-543** *(Giới hạn)* Khi không được xem đáp án thì bảng Điền khuyết không có cột "Đáp án được chấp nhận" và Trả lời ngắn không có dòng "Đáp án:".

### R-141 Chế độ xem lại: Đúng/Sai và Ghép đôi

Đúng/Sai: bảng mỗi ý một hàng — "Mục" (chữ cái và nội dung ý), "Bạn chọn" ("Đúng", "Sai" hoặc "Chưa trả lời", kèm biểu tượng kết quả), "Đáp án" (khi được xem và dữ liệu có khóa), "Điểm" ("e / m điểm" hoặc "—"); chấm theo bảng số ý đúng thì dưới bảng có "Đúng c/t ý → e điểm" (`countTableSummary`). Ghép đôi: bảng mỗi mục trái một hàng — "Mục" ("n." và nội dung), "Bạn chọn" (chữ cái và nội dung mục phải, hoặc "Chưa ghép" — `notMatched`), "Đáp án" (khi được xem), "Điểm". Trên Bề rộng hẹp mỗi hàng của bảng kết quả xếp chồng, mỗi ô có nhãn cột phía trước ("Bạn chọn: …", "Điểm: …"); bảng không cuộn ngang.

- Nguồn: `true-false.tsx:139-221`; `matching.tsx:298-376`; `review-table.tsx:4-92`; `ui-strings.ts:273,279-287`; mẫu `trueFalse`, `trueFalseNoKey`, `matching`
- Áp dụng cho: màn xem lại (mục 6.6)
- Tiêu chí chấp nhận:
  - **AC-544** Khi câu mẫu `trueFalse` hiện ở chế độ xem lại trên điện thoại thì mỗi ý là một khối xếp chồng có "Bạn chọn: Đúng" hoặc tương tự, "Đáp án: …", "Điểm: …", không cuộn ngang.
  - **AC-545** *(Giới hạn)* Khi câu mẫu `trueFalseNoKey` hiện ở chế độ xem lại thì không có cột hay nhãn "Đáp án".
  - **AC-546** Khi câu mẫu `matching` hiện ở chế độ xem lại trên tablet hai cột thì là bảng bốn cột, mục chưa ghép hiện "Chưa ghép", nội dung có công thức được vẽ.

### R-142 Chế độ xem lại: Tự luận và Bộ câu hỏi

Tự luận: khung câu có chip "Chấm tay" (`manualGrading`). Bỏ trống: hộp "Chưa trả lời". Chờ chấm: hộp màu thông tin "Câu này do người chấm chấm sau khi bạn nộp bài. Điểm tổng của lượt hiện khi người chấm chấm xong." (`pendingReviewNote`) rồi bài viết. Đã chấm: dòng "Đang chấm lại" (`regrading`) nếu đang chấm lại, dòng đếm chữ như R-130, "Bài viết của bạn" (`yourEssayLabel`) và bài viết vẽ như nội dung đề; bài dài hơn 600 ký tự thu gọn còn 6 dòng với nút "Xem toàn bộ bài viết" / "Thu gọn bài viết" (`showFullEssay`, `hideFullEssay`); thiếu chữ so với mức tối thiểu thì có dòng `wordsNeeded`; có tiêu chí thì bảng "Điểm theo tiêu chí" ("Tiêu chí", "Điểm"); có nhận xét thì khối "Nhận xét của người chấm". Bộ câu hỏi: ngữ liệu như R-127, mỗi câu con hiện chế độ xem lại theo kết quả riêng của nó; câu con không có dòng kết quả thì không có chip kết quả; lời giải của cả bộ ở cuối bộ.

- Nguồn: `essay.tsx:76,129-254`; `question-set.tsx:55,97,107`; `shared.ts:27-35`; `ui-strings.ts:208,211,255,272-279`; mẫu `essay`, `set`
- Áp dụng cho: màn xem lại (mục 6.6)
- Tiêu chí chấp nhận:
  - **AC-547** Khi câu mẫu `essay` ở trạng thái chờ chấm thì hiện hộp `pendingReviewNote` và bài viết, không có điểm số mà có "Chưa có điểm · tối đa m điểm".
  - **AC-548** Khi bài viết dài 1 200 ký tự thì ban đầu chỉ hiện 6 dòng và nút "Xem toàn bộ bài viết"; bấm thì hiện hết và nút thành "Thu gọn bài viết".
  - **AC-549** *(Giới hạn)* Khi câu mẫu `set` hiện ở chế độ xem lại mà một câu con không có kết quả trong dữ liệu xem lại thì câu con đó không có chip kết quả và không có đáp án nếu bộ không được xem đáp án.

### R-177 Tài nguyên của lượt thi không nằm lại trên máy

Mọi tệp app tải từ `/api/v1/me/attempts/{attemptId}/assets/…` và `/solution-assets/…` (ảnh, âm thanh, tệp đính kèm) chỉ được giữ trong bộ nhớ, hoặc khi buộc phải có tệp trên đĩa (trình xem tệp của hệ điều hành, đệm âm thanh của trình phát) thì là tệp tạm trong thư mục bộ đệm riêng của app, không nằm trong bản sao lưu của máy và không ghi vào nơi dùng chung (Tải xuống, Ảnh, Tệp). Tệp tạm của tệp đính kèm bị xóa ngay khi trình xem đóng; tệp tạm của âm thanh bị xóa khi lần nghe kết thúc hoặc khi trình phát rời khỏi màn hình; mọi tệp tạm còn lại bị xóa khi rời màn làm bài hay màn xem lại, khi đăng xuất, và lúc app khởi động (cho trường hợp app bị tắt giữa chừng). Trình xem của hệ điều hành chỉ nhận quyền đọc tệp đó, không có nút lưu hay chia sẻ do app cung cấp.

- Nguồn: `AttemptAssetController.java:73-78` (`Cache-Control: private, no-store` cho mọi tài nguyên của lượt); giả định của phần này (tệp tạm cho trình xem và đệm âm thanh)
- Áp dụng cho: mọi tài nguyên của lượt thi ở màn làm bài và màn xem lại
- Tiêu chí chấp nhận:
  - **AC-731** Khi học viên mở tệp "bang-so-lieu.pdf" trong trình xem của hệ điều hành rồi đóng trình xem, thì kiểm thư mục dữ liệu và bộ đệm của app (qua `adb` hay Xcode) không còn tệp nào có nội dung của tệp đó.
  - **AC-732** *(Giới hạn)* Khi học viên nghe hết một tệp audio, rời màn làm bài rồi tắt hẳn app, thì thư mục dữ liệu và bộ đệm của app không chứa tệp âm thanh nào đã tải từ `/assets/`.
  - **AC-733** *(Giới hạn)* Khi app bị hệ điều hành tắt trong lúc trình xem tệp đang mở, thì lần khởi động kế tiếp xóa tệp tạm đó trước khi hiện màn đầu tiên.
  - **AC-734** *(Từ chối)* Khi học viên đã mở ảnh, tệp đính kèm và audio trong một lượt, thì thư mục Tải xuống, thư viện Ảnh và ứng dụng Tệp của máy không có tệp nào do app ghi, và bản sao lưu của app (Android Auto Backup, bản sao lưu iCloud) không chứa các tệp đó.
  - **AC-735** *(Giới hạn)* Khi học viên đăng xuất ngay sau khi xem một tệp đính kèm, thì thư mục dữ liệu và bộ đệm của app không còn tài nguyên nào của lượt thi.

### 6.6 Kết quả và xem lại bài

### R-143 Mở màn Kết quả

App mở màn Kết quả từ ba chỗ như web. Mỗi lần màn hiện lên (mở mới hoặc quay lại từ màn khác), app gọi `GET /api/v1/me/attempts/{attemptId}/result`. Ở lần đầu trong phiên, app gọi thêm `GET /api/v1/catalog/options` với `group=EXAM` và `group=SUBJECT`.

- Nguồn: `attempt-player.tsx:1639-1672`, `my-exam-card.tsx:203-210`, `history-view.tsx:109-111`, `result-view.tsx:485-513`.
- Áp dụng cho: học viên; tài khoản khác thấy màn thông báo theo quyết định của đợt khung và xác thực.
- Tiêu chí chấp nhận:
  - **AC-550** Khi trình làm bài đang hiện màn lượt đã đóng, bấm "Xem kết quả" thì mở màn Kết quả của lượt đó; màn trình làm bài không còn trong ngăn quay lại.
  - **AC-551** Khi một thẻ ở Bài thi của tôi có nút "Xem kết quả", bấm nút thì mở màn Kết quả của `resultAttemptId` trong mục Bài thi của tôi.
  - **AC-552** Khi một dòng ở Lịch sử có `action` khác `CONTINUE`, bấm nút "Xem kết quả" hoặc tên bài của dòng thì mở màn Kết quả của lượt đó trong mục Lịch sử; nút "Tiếp tục" của dòng có `action = CONTINUE` mở trình làm bài (xem mục 6.4). Cách chạm trên dòng theo mục 6.3.
  - **AC-553** Khi danh mục `EXAM` hoặc `SUBJECT` tải lỗi, mở màn Kết quả thì điểm vẫn hiện; ở chỗ cần tên kỳ thi, tên môn, màn hiện mã thô (ví dụ `HSA`).
  - **AC-554** *(Giới hạn)* Khi đã tải hai danh mục chưa quá 5 phút, mở màn Kết quả của một lượt khác thì app không gọi lại `/api/v1/catalog/options`.

### R-144 Tải, lỗi và mất mạng ở màn Kết quả

- Nguồn: `result-view.tsx:608-715`; khóa `result.*` (`messages/vi.json:1398-1578`).
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-555** Khi đang chờ phản hồi đầu tiên của `…/result`, mở màn thì hiện khung chờ, đọc cho trình đọc màn hình là "Đang tải kết quả lượt thi" (`result.loading`).
  - **AC-556** *(Từ chối)* Khi máy chủ trả 404, mở màn thì hiện "MÃ LỖI 404", tiêu đề "Không tìm thấy kết quả lượt thi", dòng "Lượt thi không tồn tại hoặc không thuộc tài khoản đang đăng nhập." (`result.notFound.*`) và hai nút "Lịch sử luyện thi" (chuyển sang mục Lịch sử) và "Bài thi của tôi" (chuyển sang mục Bài thi của tôi).
  - **AC-557** *(Từ chối)* Khi máy chủ trả 409 `ATTEMPT_NOT_FINISHED`, mở màn thì hiện "Lượt thi chưa kết thúc", dòng "Lượt thi này vẫn đang mở hoặc đang được chuẩn bị nên chưa có kết quả." (`result.notFinished.*`), nút "Tiếp tục làm bài" mở trình làm bài của lượt (xem mục 6.4) và nút "Lịch sử luyện thi".
  - **AC-558** *(Từ chối)* Khi `…/result` lỗi khác (5xx, phản hồi hỏng) và chưa có dữ liệu, mở màn thì hiện "Không tải được kết quả lượt thi", "Kiểm tra kết nối mạng rồi thử lại." (`result.errorTitle`, `result.errorDescription`) và nút "Thử lại" (`common.retry`); bấm "Thử lại" thì gọi lại `…/result` một lần.
  - **AC-559** Khi máy không có mạng và chưa có dữ liệu, mở màn thì hiện "Không có kết nối", "Kiểm tra kết nối mạng rồi thử lại." (`common.offline.*`) và nút "Thử lại"; có mạng lại thì app tự gọi lại `…/result`.
  - **AC-560** Khi màn đang hiện kết quả, một lần tải lại (quay lại màn hoặc hỏi lại lúc đang chấm) bị lỗi thì kết quả cũ vẫn hiện, phía trên có dải cảnh báo "Không tải được kết quả lượt thi" kèm nút "Thử lại".

### R-145 Đầu màn và thông tin lượt thi

Ở mọi trạng thái có dữ liệu, màn hiện phần đầu và khối "Thông tin lượt thi".

- Nguồn: `result-view.tsx:755-822,1065-1136`, `format.ts:18-38,72-92,115-146`.
- Áp dụng cho: học viên, mọi `state` có phản hồi 200.
- Tiêu chí chấp nhận:
  - **AC-561** Khi có phản hồi 200, mở màn thì phần đầu có dòng "Kết quả lượt thi" (`result.eyebrow`), tên đề `examName`, các nhãn tên kỳ thi, phạm vi ("Bài thi FULL", "Bài thi theo phần", "Bài thi theo môn" theo `result.scopes.*`), "Lần {count}" (`result.attemptNo`) và nhãn trạng thái theo `result.states.*`: "Đang chấm", "Chờ chấm", "Đã chấm", "Đang xử lý kết quả". Khi `regrading = true`, nhãn trạng thái luôn là "Đang chấm lại".
  - **AC-562** Khi có phản hồi 200, mở màn thì khối "Thông tin lượt thi" có: Kỳ thi, Phạm vi thi, Phần thi (mã `partCode`, chỉ khi có), Môn thi (chỉ khi có `subjectCode`), Lần làm, "Lựa chọn đã chọn", Bắt đầu, Kết thúc, "Thời gian làm bài thực tế", "Cách kết thúc" (chỉ khi có `endReason`; hiện "Nộp bài", "Hết giờ", "Quá hạn chọn phần 3" hoặc "Đóng do lỗi hệ thống" theo `result.endReasons.*`).
  - **AC-563** Khi `startedAt = 2026-11-26T01:05:00Z` và `finishedAt = 2026-11-26T02:20:30Z`, mở màn thì Bắt đầu là "08:05, 26/11/2026", Kết thúc là "09:20, 26/11/2026", thời gian làm bài là "1 giờ 15 phút". Dưới 60 giây thì hiện "{n} giây"; dưới 60 phút thì hiện "{n} phút"; bằng 0 thì hiện "0 giây"; thiếu một trong hai mốc thì hiện "—".
  - **AC-564** Khi `choices` có lựa chọn `optionCode = KH` với `subjectCodes = [LY, HOA]`, mở màn thì "Lựa chọn đã chọn" hiện "Khoa học: <tên môn LY>, <tên môn HOA>". Nhiều lựa chọn thì nối bằng " · ". Không có lựa chọn thì hiện "Không có" (`result.info.noChoices`).

### R-146 Đang chấm và hỏi lại

- Nguồn: `attempt-result/api.ts:16-17`, `result-view.tsx:499-550,848-900`.
- Áp dụng cho: lượt ở `GRADING`.
- Tiêu chí chấp nhận:
  - **AC-565** Khi lượt ở `GRADING`, mở màn thì hiện "Đang chấm" (`result.grading.heading`) và "Bài làm đã được ghi nhận. Hệ thống đang tính điểm, màn hình sẽ tự cập nhật." (chuỗi app suy từ `result.grading.description`), và app gọi lại `…/result` mỗi 3 s.
  - **AC-566** Khi app đang hỏi lại và máy chủ chuyển sang `SCORED` hoặc `PENDING_REVIEW`, đến lần hỏi kế tiếp thì màn hiện khối điểm của R-148 và app ngừng hỏi.
  - **AC-567** *(Giới hạn)* Khi lượt vẫn ở `GRADING` sau 120 s kể từ lúc màn thấy `GRADING`, đến mốc 120 s thì app ngừng hỏi và hiện "Đang tính điểm, bạn có thể quay lại sau", "Quá trình chấm mất nhiều thời gian hơn dự kiến. Kết quả sẽ cập nhật trong Lịch sử luyện thi khi hoàn tất.", nút "Tải lại" (`result.grading.reload`) và nút "Lịch sử luyện thi".
  - **AC-568** Khi đang hiện khối hết giờ chờ, bấm "Tải lại" thì app gọi `…/result` ngay. Nếu vẫn `GRADING` thì màn trở lại "Đang chấm" và hỏi mỗi 3 s thêm tối đa 120 s. Trong lúc lời gọi chưa xong, nút ở trạng thái đang xử lý và bấm lần hai không gửi thêm lời gọi.
  - **AC-569** Khi app vào nền lúc đang hỏi lại, rồi quay về sau ít hơn 120 s tính từ lúc thấy `GRADING`, thì app không hỏi lúc ở nền. Khi quay về, app gọi `…/result` ngay rồi hỏi tiếp mỗi 3 s đến hết mốc 120 s ban đầu.

### R-147 Đang xử lý kết quả

- Nguồn: `result-view.tsx:902-925`; `AttemptResultService.java:185-230`.
- Áp dụng cho: lượt ở `GRADING_ERROR`.
- Tiêu chí chấp nhận:
  - **AC-570** Khi lượt ở `GRADING_ERROR`, mở màn thì hiện "Đang xử lý kết quả", "Bài làm đã được ghi nhận, kết quả đang được xử lý" (`result.gradingError.*`), nút "Lịch sử luyện thi" và "Bài thi của tôi"; không có điểm, không có nút xem lại và app không tự hỏi lại.

### R-148 Tổng điểm và thống kê câu

- Nguồn: `result-view.tsx:78-143,927-997`, `format.ts:40-70`.
- Áp dụng cho: lượt ở `SCORED` hoặc `PENDING_REVIEW`.
- Tiêu chí chấp nhận:
  - **AC-571** Khi lượt `SCORED` có `displayScore = 7.5` và `scaleMax = 10`, ngôn ngữ app là tiếng Việt, mở màn thì khối "Tổng điểm" hiện "7,5" và "/10", kèm dòng "Tổng điểm các câu trong phạm vi bài làm. Câu bỏ trống được 0 điểm." (`result.total.scaleDescription`). Ở tiếng Anh, điểm viết "7.5". Điểm có tối đa 2 chữ số thập phân, không thêm số 0 ở cuối.
  - **AC-572** Khi `displayScore` không có, màn dùng `rawScore`. Khi `scaleMax` không có, màn dùng `rawMaxScore`. Khi cả hai mức tối đa đều không có, màn chỉ hiện điểm, không có "/…".
  - **AC-573** Khi lượt `PENDING_REVIEW` và `regrading = false`, mở màn thì thay cho điểm là "Chờ chấm", "Bài có câu tự luận chấm tay. Điểm tổng hiện sau khi người chấm chấm xong mọi câu tự luận." và "Bạn có thể rời màn hình. Khi chấm xong, mở lại lượt này từ Lịch sử luyện thi để xem điểm tổng." (`result.total.pendingTitle`, `pendingDescription`; câu cuối là chuỗi app suy từ `result.total.pendingReturnNote`).
  - **AC-574** Khi lượt `PENDING_REVIEW` và `regrading = true`, mở màn thì hiện điểm cũ, nhãn "Đang chấm lại" và dòng "Người chấm đang chấm lại câu tự luận. Điểm bên trên là điểm trước khi chấm lại và có thể đổi khi chấm xong." (`result.total.regradingNote`).
  - **AC-575** Khi có `statistics`, mở màn thì hiện bốn ô "Đúng", "Đúng một phần", "Sai", "Bỏ trống" kèm số câu, một thanh tỉ lệ theo các số đó, và dòng "Trên {total} câu phải làm." (`result.stats.*`). Ô thứ năm "Chờ chấm" hiện khi `pendingReviewCount > 0` hoặc lượt ở `PENDING_REVIEW`, và được tô nổi khi số lớn hơn 0.
  - **AC-576** *(Giới hạn)* Khi `SCORED` với `displayScore = 0`, mở màn thì hiện "0" như một điểm thật, không hiện "Chờ chấm".
  - **AC-577** Khi đang ở màn và người chấm chấm xong ở nơi khác, nếu học viên không rời màn thì màn không đổi. Mở lại màn (hoặc quay lại từ màn Xem lại) thì app gọi lại `…/result` và hiện điểm mới.

### R-149 Lỗi hệ thống và cấp bù

- Nguồn: `result-view.tsx:787-846`; khóa `result.fault.*` (`messages/vi.json:1432-1436`).
- Áp dụng cho: lượt có `compensated = true`, `faultFlag = SYSTEM_FAULT` hoặc `endReason = FAULT_CLOSED`, ở mọi `state`.
- Tiêu chí chấp nhận:
  - **AC-578** Khi `compensated = true`, mở màn thì dưới phần đầu có dải thông tin "Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt".
  - **AC-579** Khi `compensated = false` và (`faultFlag = SYSTEM_FAULT` hoặc `endReason = FAULT_CLOSED`), mở màn thì có dải cảnh báo "Lượt kết thúc do lỗi hệ thống".
  - **AC-580** Khi dải hiện và có `faultReason`, mở màn thì dải có thêm dòng "Ghi chú: {faultReason}". Màn không hiện trường `faultMessage`.

### R-150 Điểm từng phần

- Nguồn: `result-view.tsx:145-279,1138-1168`; khóa `result.parts.*`.
- Áp dụng cho: phản hồi có `parts` không rỗng.
- Tiêu chí chấp nhận:
  - **AC-581** Khi có `parts`, mở màn thì có khối "Điểm từng phần". Dòng mô tả là "Điểm phần là tổng điểm các câu trong phần."; khi lượt ở Chờ chấm thì là "Điểm của phần đã chấm xong là điểm riêng của phần đó, chưa phải điểm tổng.". Các phần theo thứ tự máy chủ trả, mỗi phần mang tên `partName`, không có tên thì dùng `partCode`.
  - **AC-582** Khi một phần đã chấm, mở màn thì phần đó hiện số "Đúng", "Sai", "Bỏ trống", có thêm "Đúng một phần" khi số này lớn hơn 0, một thanh tỉ lệ, và "Điểm phần" `score/maxScore` khi có cả hai số.
  - **AC-583** Khi một phần có `skipped = true`, mở màn thì phần đó có nhãn "Bị bỏ qua" và điểm "0/{maxScore}". Lý do là "Không làm phần 3: quá thời gian chọn" khi `endReason = CHOICE_TIMEOUT`, "Phần bị bỏ qua do lượt kết thúc vì lỗi hệ thống." khi `FAULT_CLOSED`, còn lại là "Phần này đã bị bỏ qua và tính 0 điểm.".
  - **AC-584** Khi một phần có `pendingReview = true`, mở màn thì phần đó có nhãn "Chấm tay", dòng "Điểm tối đa của phần: {max}." và nhãn "Chờ chấm"; không hiện điểm phần.

### R-151 Điểm theo phân đoạn

- Nguồn: `segment-breakdown-section.tsx:31-225`; khóa `result.segments.*`.
- Áp dụng cho: phản hồi có `segments` không rỗng (đề có phân đoạn hiển thị).
- Tiêu chí chấp nhận:
  - **AC-585** Khi có `segments`, mở màn thì có khối "Điểm theo phân đoạn". Mỗi phân đoạn có tên (`name`, không có thì `code`), dòng "Câu {from}–{to}" khi có cả hai vị trí, số "Đúng", "Sai", "Bỏ trống", có thêm "Đúng một phần" và "Chờ chấm" khi số đó lớn hơn 0, và "Điểm phân đoạn: {score}/{maxPoints}". Khi không có `maxPoints` thì chỉ hiện `score`; không có `score` thì không hiện dòng điểm.
  - **AC-586** Khi một phân đoạn có `pendingReview = true`, mở màn thì thay cho điểm là nhãn "Chấm tay" và "Chờ chấm". Khi lượt ở Chờ chấm, dòng mô tả của khối là "Điểm của phân đoạn đã chấm xong là điểm riêng của phân đoạn đó, chưa phải điểm tổng.".
  - **AC-587** *(Giới hạn)* Khi `segments` vắng hoặc rỗng, mở màn thì không có khối phân đoạn.

### R-152 Điểm theo môn

- Nguồn: `result-view.tsx:281-452,1178-1185`; khóa `result.subjects.*`.
- Áp dụng cho: phản hồi có `subjects` không rỗng và `examKindCode ≠ VACT`.
- Tiêu chí chấp nhận:
  - **AC-588** Khi có `subjects`, mở màn thì có khối "Điểm theo môn", mô tả "Điểm từng môn thực tế đã làm trong lượt thi." (khi Chờ chấm thì là `result.subjects.pendingDescription`). Mỗi môn có: tên môn lấy từ danh mục `SUBJECT` theo `code`, không có trong danh mục thì lấy `name`, không có `name` thì lấy `code`; số "Đúng", "Sai", "Bỏ trống" (có thêm "Đúng một phần", "Chờ chấm" khi lớn hơn 0); "Điểm môn: {score}/{maxScore}" khi có cả hai số.
  - **AC-589** Khi một môn có `pendingReview = true`, mở màn thì môn đó có nhãn "Chấm tay" và "Chờ chấm", không có điểm môn.
  - **AC-590** *(Giới hạn)* Khi `examKindCode = VACT`, kể cả khi phản hồi có `subjects`, mở màn thì không có khối "Điểm theo môn".

### R-153 Kết quả V-ACT

- Nguồn: `result-view.tsx:940-971,1024-1063`, `thinking-subsections.tsx:10-76`, `format.ts:148-161`; khóa `result.vact.*` (`messages/vi.json:1559-1578`).
- Áp dụng cho: `examKindCode = VACT`.
- Tiêu chí chấp nhận:
  - **AC-591** Khi lượt V-ACT có điểm, mở màn thì trên điểm có nhãn "Điểm luyện tập", dưới điểm có "Không phải điểm V-ACT chính thức" và "Điểm luyện tập quy từ điểm các câu về thang {scaleMax}." (khi không chấm lại).
  - **AC-592** Khi có `vactComponents`, mở màn thì có khối "Kết quả theo thành phần" với chú thích "Số câu đúng và tỷ lệ đúng theo từng thành phần (không hiện điểm thành phần).". Mỗi thành phần mang tên theo `result.vact.components.*` cho `VIETNAMESE`, `ANH`, `TOAN`, `SCIENTIFIC_THINKING`; mã khác lấy tên từ danh mục `SUBJECT`, không có thì hiện mã. Mỗi thành phần có dòng "{correct}/{total} câu đúng · {rate}%"; không có điểm thành phần.
  - **AC-593** Khi một thành phần có `subsections`, mở màn thì có nút "Xem riêng 2 tiểu mục (12 và 18 câu)", mặc định đóng. Bấm nút thì mở danh sách tiểu mục và nút đổi thành "Ẩn chi tiết 2 tiểu mục". Mỗi tiểu mục có nhãn, "{n} câu" và "{correct}/{total} câu đúng · {rate}%". Nhãn là "Logic và phân tích số liệu" khi `questionCount = 12`, hoặc khi đó là tiểu mục đầu trong 2 tiểu mục và `questionCount ≠ 18`; còn lại là "Suy luận khoa học".

### R-154 Quy tắc cho xem lại bài

App chỉ đưa ra lối vào màn Xem lại khi `reviewAvailable = true`, `resultView` khác `SCORE` và `state` là `SCORED` hoặc `PENDING_REVIEW`.

- Nguồn: `result-view.tsx:790-794,999-1020`; `AttemptResultService.java:179-180,443-445`.
- Áp dụng cho: lượt ở `SCORED` hoặc `PENDING_REVIEW`.
- Tiêu chí chấp nhận:
  - **AC-594** Khi lượt `SCORED` có mức xem `SCORE_AND_ANSWERS` hoặc `FULL_SOLUTION`, mở màn thì dưới khối điểm có nút chính "Xem lại bài" (`result.actions.reviewFull`), rồi đến nút "Bài thi của tôi" và "Lịch sử luyện thi".
  - **AC-595** Khi lượt `PENDING_REVIEW` có mức xem khác `SCORE`, mở màn thì nút chính là "Xem lại câu đã chấm tự động" (`result.actions.reviewAutoGraded`), kèm dòng "Câu tự luận xem lại được sau khi chấm xong." (`result.actions.pendingReviewNote`).
  - **AC-596** *(Từ chối)* Khi mức xem là `SCORE`, mở màn thì không có nút xem lại; có dòng "Bài thi này chỉ cho xem điểm, không có phần xem lại bài làm." (`result.actions.scoreOnlyNote`).
  - **AC-597** *(Từ chối)* Khi lượt ở `GRADING` hoặc `GRADING_ERROR`, mở màn thì không có nút xem lại.
  - **AC-598** Khi đang hiện nút xem lại, bấm nút thì mở màn Xem lại của lượt trong cùng mục; bấm nhanh hai lần chỉ mở một màn.

### R-155 Rời màn Kết quả

- Nguồn: `result-view.tsx:557-566,880-882,917-922,1008-1013`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-599** Khi màn Kết quả được mở từ Bài thi của tôi hoặc Lịch sử, bấm quay lại (nút trên thanh đầu, cử chỉ hay phím quay lại của hệ thống) thì về đúng màn trước trong mục đó, giữ nguyên vị trí cuộn và bộ lọc của màn đó.
  - **AC-600** Khi màn Kết quả được mở từ trình làm bài (AC-550), bấm quay lại thì về màn gốc của mục đã mở trình làm bài, không về trình làm bài.
  - **AC-601** Khi bấm nút "Bài thi của tôi" hoặc "Lịch sử luyện thi" trong màn, thì app chuyển sang mục Bài thi của tôi hoặc mục Lịch sử, ở màn gốc của mục đó, và tải lại dữ liệu của màn đó.

### R-156 Mở màn Xem lại và các trạng thái lỗi

App gọi `GET /api/v1/me/attempts/{attemptId}/review` không kèm `part`, một lần khi mở màn. Khi lời gọi này thành công, app gọi `…/result` để lấy dữ liệu cho phần đầu màn; app được dùng lại kết quả đã có chưa quá 60 s.

- Nguồn: `review-view.tsx:182-207,281-385`; khóa `review.*` (`messages/vi.json:1580-1649`).
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-602** Khi đang chờ `…/review`, mở màn thì hiện khung chờ, đọc cho trình đọc màn hình là "Đang tải bài làm để xem lại" (`review.loading`).
  - **AC-603** *(Từ chối)* Khi máy chủ trả 403 `REVIEW_NOT_INCLUDED`, mở màn thì hiện "Bài thi này không có phần xem lại bài làm", "Bài thi bạn đã mua chỉ cho xem điểm. Bạn vẫn xem được điểm của lượt này ở màn hình kết quả." (`review.notIncluded.title`; câu sau là chuỗi app suy từ `review.notIncluded.description`) và nút "Về kết quả".
  - **AC-604** *(Từ chối)* Khi máy chủ trả 409 `REVIEW_NOT_AVAILABLE` (ví dụ lượt vừa chuyển sang đang chấm), mở màn thì hiện "Chưa thể xem lại bài làm", "Kết quả của lượt này chưa sẵn sàng. Bạn xem lại bài được sau khi chấm xong." (`review.notAvailable.*`) và nút "Về kết quả".
  - **AC-605** *(Từ chối)* Khi máy chủ trả 404, mở màn thì hiện "MÃ LỖI 404", "Không tìm thấy bài làm", dòng `review.notFound.description` và hai nút "Lịch sử luyện thi", "Bài thi của tôi" như AC-556.
  - **AC-606** *(Từ chối)* Khi `…/review` lỗi khác, mở màn thì hiện "Không tải được bài làm", "Kiểm tra kết nối mạng rồi thử lại." và nút "Thử lại". Khi không có mạng, màn hiện trạng thái của AC-559 và tự gọi lại khi có mạng.
  - **AC-607** Khi `…/review` thành công nhưng `…/result` lỗi, mở màn thì các câu vẫn hiện; phần đầu chỉ có tiêu đề "Xem lại bài làm".

### R-157 Phần đầu màn Xem lại và dải thông báo

- Nguồn: `review-view.tsx:494-531`.
- Áp dụng cho: phản hồi `…/review` 200.
- Tiêu chí chấp nhận:
  - **AC-608** Khi đã có cả hai phản hồi, mở màn thì có tiêu đề "Xem lại bài làm" và một dòng "{examName} · {phạm vi} · Lần {n}"; có điểm hiện theo luật AC-571 thì thêm " · Tổng điểm: {điểm}/{thang}".
  - **AC-609** Khi `resultView = SCORE_AND_ANSWERS`, mở màn thì có dải "Không hiển thị lời giải cho mức quyền lợi hiện tại." (`review.scoreOnlyNotice`).
  - **AC-610** Khi `state = PENDING_REVIEW` (kể cả đang chấm lại), mở màn thì có dải cảnh báo "Lượt thi còn câu tự luận đang chờ chấm. Bạn chỉ xem lại được các câu đã chấm tự động cho tới khi chấm xong." (`review.pendingBanner`).

### R-158 Chuyển phần

- Nguồn: `review-view.tsx:402-404,426-445,533-589`.
- Áp dụng cho: phản hồi có `parts`.
- Tiêu chí chấp nhận:
  - **AC-611** Khi phản hồi có nhiều phần, mở màn thì có dải thẻ phần, đọc là "Các phần của đề" (`review.nav.partsLabel`), xếp theo `ord` tăng dần, tên là `partName` (không có thì `partCode`). Thẻ của phần có `state = SKIPPED` có thêm nhãn "Bị bỏ qua". Phần đầu tiên được chọn sẵn.
  - **AC-612** Khi đang xem một phần, chọn thẻ phần khác thì danh sách câu và Bảng câu đổi sang phần đó, câu đầu của phần mới thành câu đang xem, và app không gọi lại `…/review`.
  - **AC-613** Khi chọn một phần bị bỏ qua, thì thay cho câu là "Phần này bị bỏ qua" và "Phần này đã bị bỏ qua nên không có câu để xem lại. Phần được {score}/{max} điểm." (khi không có điểm thì dùng `review.skippedPart.descriptionNoScore`).
  - **AC-614** *(Giới hạn)* Khi phần đang chọn không còn câu nào để xem (ví dụ mọi câu đều chờ chấm), thì hiện "Không có câu hỏi trong phần này", "Phần này hiện chưa có câu hỏi nào để xem lại." (`review.emptyPart.*`).
  - **AC-615** Khi rời màn Xem lại rồi mở lại, thì phần đầu tiên lại được chọn.

### R-159 Bảng câu và chuyển câu

Màn Xem lại có Bảng câu của phần đang chọn và danh sách các nhóm câu của phần đó.

- Nguồn: `review-view.tsx:447-484,591-726`, `attempt-review/mapper.ts:461-470,489-492,535-569`; khóa `review.nav.*`, `review.outcomes.*`.
- Áp dụng cho: phần có ít nhất một nhóm câu.
- Tiêu chí chấp nhận:
  - **AC-616** Khi mở một phần, thì Bảng câu có tiêu đề "Danh sách câu", dòng "Đúng {correct} · Đúng một phần {partial} · Sai {wrong} · Bỏ trống {blank}" và mỗi nhóm câu một ô. Trong dòng này, mỗi câu con của chùm câu hỏi được đếm riêng.
  - **AC-617** Mỗi ô ghi số câu `displayNo`; câu điền khuyết có nhiều ô trống và chùm câu hỏi ghi dải "{from}–{to}". Kiểu ô thể hiện kết quả: đúng, đúng một phần, sai, bỏ trống. Kiểu ô không dựa riêng vào màu. Nhãn đọc cho trình đọc màn hình là "Câu {no}, {kết quả}" hoặc "Câu {from}–{to}, {kết quả}". Dưới lưới có chú thích bốn kiểu: "đúng", "đúng một phần", "sai", "bỏ trống".
  - **AC-618** Khi bấm một ô, thì danh sách cuộn tới nhóm câu đó, nhóm đó được tô viền là câu đang xem và ô được đánh dấu đang chọn.
  - **AC-619** Khi câu đang xem là câu đầu của phần, thì nút "Câu trước" bị tắt. Khi là câu cuối, thì nút "Câu sau" bị tắt. Bấm nút còn bật thì câu đang xem chuyển sang nhóm liền trước hoặc liền sau và danh sách cuộn tới nhóm đó.
  - **AC-620** Kết quả của một chùm câu hỏi là kết quả máy chủ trả cho câu cha. Khi kết quả đó vắng hoặc là `PENDING_REVIEW`, kết quả được suy từ các câu con đang hiện: tất cả đúng là đúng, tất cả bỏ trống là bỏ trống, tất cả sai là sai, còn lại là đúng một phần.
  - **AC-621** Ở một cột, Bảng câu nằm trên danh sách câu. Ở hai ô (Android từ 840dp, iOS bề ngang rộng), Bảng câu là ô bên cạnh danh sách câu và vẫn thấy được khi cuộn danh sách.

### R-160 Nội dung một câu ở xem lại

Mỗi nhóm câu được vẽ bằng thành phần câu hỏi chỉ đọc ở chế độ xem lại (xem mục 6.5), với dữ liệu sau từ phản hồi `…/review`: nội dung `content` và `body`; câu trả lời của học viên `studentAnswer`; kết quả `outcome`; điểm `earnedPoints` trên `pointsMax` (không có `pointsMax` thì là 1); đáp án đúng `expectedAnswer`; chi tiết chấm `gradingDetail` theo từng ô trống, từng ý, từng cặp; thứ tự lựa chọn `optionOrder`.

- Nguồn: `attempt-review/mapper.ts:82-360,377-515`, `review-view.tsx:593-628`.
- Áp dụng cho: mọi mức xem cho xem lại.
- Tiêu chí chấp nhận:
  - **AC-622** Khi một câu trắc nghiệm một đáp án có `expectedAnswer.expectedOptionIds = [B]` và học viên chọn `A`, mở màn thì câu có kết quả "Sai", điểm `earnedPoints/pointsMax`, lựa chọn A được đánh dấu là câu đã chọn và B được đánh dấu là đáp án đúng. Các lựa chọn xếp theo `optionOrder` khi có.
  - **AC-623** Khi một câu có `earnedPoints` vắng, mở màn thì chỗ điểm của khung câu hiện "Chưa có điểm · tối đa {max} điểm" với `{max}` là `pointsMax` (chuỗi `noPointsYet` và `maxPoints` của `packages/ui/src/messages/ui-strings.ts:264-265`, hiện ở `packages/ui/src/question/question-frame.tsx:217-218`) như thành phần câu hỏi (xem mục 6.5).
  - **AC-624** Khi một câu ghép cặp có `gradingDetail.pairResults`, mở màn thì mỗi mục trái có kết quả riêng. Cặp khớp là đúng, cặp đã chọn nhưng sai là sai, chưa chọn là bỏ trống. Điểm tối đa của mỗi cặp là `pointsMax` chia đều cho số cặp.
  - **AC-625** Khi một câu Đúng/Sai có `gradingDetail.statementResults`, mở màn thì mỗi ý có kết quả: `matched = true` là đúng; `answered = true` mà không khớp là sai; chưa trả lời là bỏ trống.
  - **AC-626** Khi một chùm câu hỏi có câu con, mở màn thì đoạn dẫn và các câu con hiện trong cùng một nhóm, mỗi câu con có kết quả và điểm riêng.

### R-161 Ẩn và hiện theo mức xem

- Nguồn: `attempt-review/mapper.ts:321-349,378,430-437,505`, `review-view.tsx:80-146,616-627`; `AttemptResultService.java:509-519,734-759`.
- Áp dụng cho: mức xem `SCORE_AND_ANSWERS` và `FULL_SOLUTION`.
- Tiêu chí chấp nhận:
  - **AC-627** Khi `resultView = SCORE_AND_ANSWERS`, mở màn thì mỗi câu hiện câu trả lời, kết quả, điểm và đáp án đúng; không có khối "Lời giải", không có "Nhận xét của người chấm" và không có bảng "Điểm từng tiêu chí", dù phản hồi có những trường đó.
  - **AC-628** Khi `resultView = FULL_SOLUTION` và một câu có `solution`, mở màn thì dưới câu có khối "Lời giải" chứa nội dung `solution`, vẽ như nội dung câu hỏi (xem mục 6.5). Khi câu không có `solution`, thì không có khối này.
  - **AC-629** Khi `resultView = FULL_SOLUTION` và một câu tự luận đã chấm có `essayFeedback.comment`, mở màn thì có khối "Nhận xét của người chấm" chứa nhận xét đó.
  - **AC-630** Khi `resultView = FULL_SOLUTION` và `essayFeedback.criteria` không rỗng, mở màn thì dưới câu có bảng "Điểm từng tiêu chí" với ba cột "Tiêu chí", "Điểm", "Nhận xét tiêu chí" (`review.essayCriteria.*`). Tên tiêu chí là `id`, không có thì "Tiêu chí {index}". Điểm là "{points} điểm", không có thì "—". Nhận xét không có thì "—". Với câu con tự luận trong chùm, tiêu đề bảng là "Điểm từng tiêu chí · Câu {no}".
  - **AC-631** Khi một câu tự luận có `outcome = BLANK`, mở màn thì câu hiện "Chưa trả lời", không có bảng tiêu chí và không có nhận xét.

### R-162 Câu chờ chấm khi lượt còn chờ chấm

- Nguồn: `attempt-review/mapper.ts:404-414,482-484`; `AttemptResultService.java:375-379,447-469`.
- Áp dụng cho: `state = PENDING_REVIEW`.
- Tiêu chí chấp nhận:
  - **AC-632** Khi lượt có một câu tự luận `PENDING_REVIEW` và các câu khác đã chấm, mở màn thì câu tự luận đó không có trong danh sách câu và cũng không có trong Bảng câu; các câu đã chấm hiện bình thường.
  - **AC-633** *(Giới hạn)* Khi mọi câu con của một chùm câu hỏi đều `PENDING_REVIEW`, mở màn thì cả chùm không hiện. Khi chỉ một số câu con chờ chấm, chùm hiện cùng các câu con đã chấm.

### R-163 Ảnh trong đề và trong lời giải

App tải mọi ảnh có địa chỉ `/api/v1/me/attempts/{id}/assets/…` hoặc `…/solution-assets/…` trong nội dung xem lại từ máy chủ API, kèm `Authorization: Bearer`. App không lưu các ảnh này xuống bộ nhớ máy.

- Nguồn: quyết định của đợt khung và xác thực (gọi backend trực tiếp kèm `Authorization: Bearer`); `AttemptResultService.java:493,761-804`; `AttemptSolutionAssetController.java:37-66`; `AttemptSolutionAssetService.java:75-102`.
- Áp dụng cho: màn Xem lại.
- Tiêu chí chấp nhận:
  - **AC-634** Khi đề hoặc lời giải có ảnh với `src` tương đối như trên, mở màn thì app gọi đường dẫn đó trên máy chủ API kèm `Authorization: Bearer` và hiện ảnh tại đúng chỗ trong nội dung.
  - **AC-635** *(Từ chối)* Khi tải một ảnh bị 404, ví dụ ảnh lời giải lúc lượt ở `PENDING_REVIEW` mà không chấm lại, mở màn thì chỗ ảnh hiện ô giữ chỗ không vỡ khung (xem mục 6.5); phần còn lại của câu và của màn vẫn hiện.
  - **AC-636** Khi đóng màn Xem lại rồi tắt hẳn app, mở bộ nhớ của app để kiểm tra thì không còn tệp ảnh nào từ `…/solution-assets/…`.

### R-164 Rời màn Xem lại

- Nguồn: `review-view.tsx:223,230-239,310-340`.
- Áp dụng cho: học viên.
- Tiêu chí chấp nhận:
  - **AC-637** Khi đang ở màn Xem lại, bấm quay lại (thanh đầu, cử chỉ hay phím hệ thống) hoặc nút "Về kết quả" trên các màn của AC-603 và AC-604, thì về màn Kết quả của lượt. Màn Kết quả gọi lại `…/result` và vẫn hiện kết quả cũ trong lúc chờ.

### 6.7 Hồ sơ trong mục Tài khoản

### R-165 Lối vào Hồ sơ, Đơn hàng và Đổi mật khẩu trong mục Tài khoản

Mục Tài khoản thêm hai dòng "Hồ sơ" (`header.nav.profile`) và "Đơn của tôi" (`header.nav.orders`) ngay dưới thẻ tài khoản, theo thứ tự của menu tài khoản trên web; dòng "Đổi mật khẩu" giữ ở nhóm "Bảo mật". Bấm vào thẻ tài khoản cũng mở màn Hồ sơ. Mọi phần đang có (thẻ chỉ đọc, sinh trắc học, ngôn ngữ, sáng/tối, đăng xuất) giữ nguyên hành vi.

- Nguồn: giả định A-1; `apps/web-student/src/components/student-header.tsx:23-28`; `AccountScreen.kt:440-583`; `AccountView.swift:21-55`
- Áp dụng cho: học viên đã đăng nhập; mọi trạng thái tải của thẻ tài khoản.
- Tiêu chí chấp nhận:
  - **AC-638** Cho mục Tài khoản ở một cột, khi bấm "Hồ sơ" hoặc bấm vào thẻ tài khoản, thì màn Hồ sơ mở đè lên danh sách với nút quay lại; quay lại thì về danh sách ở đúng vị trí cuộn cũ.
  - **AC-639** Cho mục Tài khoản ở một cột, khi bấm "Đơn của tôi", thì mở danh sách đơn của học viên (xem mục 6.2); quay lại thì về mục Tài khoản.
  - **AC-640** Cho mục Tài khoản ở hai cột, khi mở mục lần đầu trong phiên, thì dòng "Hồ sơ" được chọn và ngăn chi tiết hiện màn Hồ sơ; bấm "Đơn của tôi" hoặc "Đổi mật khẩu" thì dòng đó được đánh dấu chọn và ngăn chi tiết đổi sang màn tương ứng. Trên iOS điều này thay cho ngăn chi tiết luôn là màn đổi mật khẩu; trên Android các ngăn Ngôn ngữ và Giao diện giữ như cũ.
  - **AC-641** Cho thẻ tài khoản đang ở trạng thái đang tải, lỗi hoặc mất mạng, khi bấm "Hồ sơ" hoặc "Đơn của tôi", thì màn tương ứng vẫn mở và tự tải dữ liệu của nó.
  - **AC-642** Cho mục Tài khoản sau thay đổi này, khi chạy lại các tiêu chí sau của đặc tả `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md`: R-9 (đổi mật khẩu), R-10 (mở khóa sinh trắc học), R-11 gồm AC-67 đến AC-70 (thẻ tài khoản, đăng xuất), AC-73 và AC-74 của R-12 (chọn sáng/tối), R-13 (ngôn ngữ), thì tất cả vẫn đạt; riêng AC-67 nay đọc thêm ảnh đại diện theo R-166.
  - **AC-643** *(Giới hạn)* Cho màn Hồ sơ đang có ô sửa dở, khi xoay máy tính bảng hoặc gập/mở máy gập làm app đổi giữa một cột và hai cột, thì màn Hồ sơ vẫn hiện (là màn đè hoặc ngăn chi tiết) và giá trị đang gõ còn nguyên.

### R-166 Ảnh đại diện có xác thực

Khi hồ sơ có `avatarUrl`, app hiện ảnh đại diện ở thẻ tài khoản và ở màn Hồ sơ. `avatarUrl` là đường dẫn tương đối, app ghép với địa chỉ API của môi trường và gửi kèm `Authorization: Bearer`.

- Nguồn: `ProfileService.java:46,167-178`; `AvatarController.java:80-92`; `profile-view.tsx:256-273`
- Áp dụng cho: học viên đã đăng nhập; hồ sơ có hoặc không có ảnh.
- Tiêu chí chấp nhận:
  - **AC-644** Cho hồ sơ có `avatarUrl = null`, khi hiện thẻ tài khoản hoặc màn Hồ sơ, thì hiện vòng tròn chữ cái đầu tên như hôm nay, trình đọc màn hình đọc "Chưa có ảnh đại diện, đang hiện chữ cái đầu tên" (`account.profile.avatar.currentEmpty`) ở màn Hồ sơ.
  - **AC-645** Cho hồ sơ có `avatarUrl = /api/v1/avatars/{assetId}`, khi hiện thẻ tài khoản hoặc màn Hồ sơ, thì app gọi `GET {địa chỉ API}/api/v1/avatars/{assetId}` có `Authorization: Bearer`; trong lúc chờ, vòng tròn hiện chữ cái đầu tên; nhận 200 thì thay bằng ảnh tròn, nhãn trình đọc màn hình "Ảnh đại diện hiện tại" (`account.profile.avatar.current`).
  - **AC-646** *(Từ chối)* Cho lời gọi ảnh trả 404, lỗi khác hoặc mất mạng, khi hiện thẻ hoặc màn Hồ sơ, thì vòng tròn giữ chữ cái đầu tên, không hiện câu lỗi, không gọi lại liên tục; lần mở mục Tài khoản hoặc màn Hồ sơ kế tiếp thì thử lại một lần. Nhận 401 thì đi theo AC-34 của đặc tả khung (xóa phiên, về Đăng nhập).
  - **AC-647** *(Giới hạn)* Cho ảnh đã hiện một lần trong phiên, khi mở lại thẻ hoặc màn Hồ sơ với cùng `assetId`, thì ảnh hiện ngay không chờ mạng; sau khi đăng xuất, ảnh của tài khoản cũ không còn trong bộ đệm của app (AC-70 của đặc tả khung).

### R-167 Màn Hồ sơ: tải, lỗi, mất mạng

- Nguồn: `profile-view.tsx:86-154,369-371` (truy vấn `PROFILE_QUERY_KEY` với `staleTime: 0`: lần mở sau hiện bản trong bộ đệm rồi tải lại; biểu mẫu đặt lại theo hồ sơ mới); `ProfileService.java:78-83`
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-648** Cho màn Hồ sơ mở lần đầu trong phiên (app chưa có bản hồ sơ đầy đủ nào do `GET /api/v1/me/profile` trả trong phiên này), khi app gọi `GET /api/v1/me/profile`, thì hiện khung chờ có nhãn trình đọc màn hình "Đang tải" (`common.loading`).
  - **AC-736** Cho app đã có bản hồ sơ đầy đủ trong phiên (từ lần mở màn Hồ sơ hoặc màn Xác minh email trước, hoặc phản hồi của một thao tác), khi mở lại màn Hồ sơ, thì màn hiện ngay bản đó, không có khung chờ, và app vẫn gọi `GET /api/v1/me/profile` một lần; có phản hồi thì mọi khối và các ô của "Thông tin cá nhân" lấy giá trị mới. Lời gọi lại lỗi thì bản đang hiện giữ nguyên và phía trên có khối lỗi của AC-650 kèm "Thử lại". Đăng xuất thì bản này bị xóa (AC-70 của đặc tả khung).
  - **AC-649** Cho lời gọi thành công, khi hiện màn, thì có tiêu đề "Hồ sơ", câu "Đổi họ tên, email hay mật khẩu không ảnh hưởng tới bài đã mua, số lượt còn lại và kết quả của bạn." (`account.profile.intro`), băng cảnh báo nếu R-171 yêu cầu, khối "Ảnh đại diện" (ảnh, họ tên, tên đăng nhập, nút, gợi ý), khối "Thông tin cá nhân" và khối "Email". Màn không có khối "Mật khẩu" vì dòng "Đổi mật khẩu" đã nằm cạnh trong danh sách Tài khoản.
  - **AC-650** *(Từ chối)* Cho lời gọi trả lỗi HTTP, khi hiện màn, thì hiện "Không tải được hồ sơ" (`account.profile.errorTitle`), "Đã có lỗi khi tải hồ sơ. Hãy thử lại." (`account.profile.errorDescription`) và nút "Thử lại" (`common.retry`); bấm "Thử lại" thì gọi lại và về trạng thái của AC-648.
  - **AC-651** *(Từ chối)* Cho máy mất mạng, khi mở màn Hồ sơ, thì hiện "Không có kết nối" (`common.offline.title`), "Kiểm tra kết nối mạng rồi thử lại." (`common.offline.description`) và nút "Thử lại".
  - **AC-652** Cho một thao tác trên màn Hồ sơ vừa thành công (lưu thông tin, đặt hoặc xóa ảnh, xác minh hoặc đổi email), khi quay lại mục Tài khoản hoặc khi app khóa, thì thẻ tài khoản hiện họ tên, ảnh, email và nhãn xác minh mới, và màn khóa hiện họ tên mới (màn khóa không hiện ảnh hay email), mà không cần tải lại.
  - **AC-653** *(Giới hạn)* Cho hồ sơ bị đổi ở máy khác hoặc trên web, khi mở lại màn Hồ sơ trên app, thì màn hiện giá trị mới của máy chủ.

### R-168 Sửa họ tên và số điện thoại

- Nguồn: `profile-view.tsx:324-486`; `ProfileService.java:85-147`
- Áp dụng cho: học viên đã đăng nhập; hồ sơ có hoặc không có số điện thoại.
- Tiêu chí chấp nhận:
  - **AC-654** Cho màn Hồ sơ đã tải, khi xem khối "Thông tin cá nhân", thì có "Tên đăng nhập" chỉ đọc kèm "Tên đăng nhập không đổi được.", ô "Họ và tên" bắt buộc, ô "Số điện thoại (không bắt buộc)" với chữ mờ "Ví dụ 0912345678", bàn phím số điện thoại; hồ sơ chưa có số thì có gợi ý "Bạn chưa nhập số điện thoại."; nút "Hủy thay đổi" và "Lưu thay đổi" chỉ bấm được khi một ô khác giá trị đã lưu.
  - **AC-655** Cho đã sửa họ tên, khi bấm "Lưu thay đổi", thì app gửi `PATCH /api/v1/me/profile` với `fullName` đã cắt khoảng trắng và không có `phone` nếu số không đổi; nhận 200 thì hiện thông báo nổi "Đã lưu thông tin cá nhân." (`account.profile.info.saved`), các ô lấy giá trị trả về làm mốc và hai nút lại bị khóa.
  - **AC-656** *(Từ chối)* Cho "Họ và tên" chỉ có khoảng trắng, khi bấm lưu, thì hiện "Vui lòng nhập họ và tên." dưới ô và không gọi API. Ô không nhận quá 200 ký tự; máy chủ trả lỗi `fullName` mã `SIZE` thì hiện "Họ và tên tối đa 200 ký tự."
  - **AC-657** *(Từ chối)* Cho số điện thoại khác rỗng, khác số đang lưu và không khớp `^0\d{9}$` (ví dụ `912345678`), khi bấm lưu, thì hiện "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0." và không gọi API; máy chủ trả lỗi trường `phone` thì hiện cùng câu đó.
  - **AC-658** *(Giới hạn)* Cho số đang lưu không khớp mẫu (ví dụ `+84 912 345 678` do quản trị nhập), khi chỉ sửa họ tên rồi lưu, thì lưu thành công và số cũ giữ nguyên. Cho xóa trống ô số rồi lưu, thì app gửi `phone: ""`, hồ sơ trả `phone = null` và gợi ý "Bạn chưa nhập số điện thoại." hiện ra.
  - **AC-659** *(Từ chối)* Cho máy chủ trả lỗi khác lỗi trường hoặc mất mạng, khi lưu, thì trong khối hiện "Không lưu được thay đổi. Vui lòng thử lại." (`account.profile.info.genericError`) và các ô giữ giá trị đang gõ.
  - **AC-660** Cho đang lưu, khi bấm "Lưu thay đổi" lần nữa, thì nút đang ở trạng thái xử lý và app chỉ gửi một yêu cầu. Bấm "Hủy thay đổi" thì các ô về giá trị đã lưu.
  - **AC-661** *(Giới hạn)* Cho họ tên vừa được đổi ở máy khác sau khi màn này đã tải, khi lưu ở máy này, thì giá trị của máy này được lưu (máy chủ không có kiểm phiên bản) và màn hiện giá trị trả về.

### R-169 Đặt ảnh đại diện từ thư viện hoặc máy ảnh

- Nguồn: `profile-view.tsx:42-43,206-229,279-319`; `AvatarController.java:52-71`; `AvatarImageProcessor.java:42-114`
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-662** Cho màn Hồ sơ đã tải, khi bấm "Tải ảnh lên" (`account.profile.avatar.upload`), thì app cho chọn "Chọn từ thư viện" hoặc "Chụp ảnh"; máy không có máy ảnh thì chỉ có lựa chọn thư viện. Dưới nút luôn có "JPG, PNG hoặc WEBP; tối đa 5 MB và 4.096 × 4.096 px. Ảnh được cắt vuông." (`account.profile.avatar.hint`).
  - **AC-663** Cho người dùng chọn một ảnh trong thư viện hoặc chụp một ảnh (kể cả ảnh HEIC/HEIF, ảnh dọc chụp xoay máy, ảnh 48 MP), khi xác nhận, thì app gửi `PUT /api/v1/me/avatar` multipart phần `file` chứa ảnh JPEG, PNG hoặc WebP đã xoay đúng chiều, cạnh dài nhất ≤ 4.096 px và ≤ 5 MB; trong lúc gửi, nút "Tải ảnh lên" và "Xóa ảnh" bị khóa và có dấu đang xử lý.
  - **AC-664** Cho máy chủ trả 200, khi nhận phản hồi, thì ảnh mới hiện theo `avatarUrl` trả về (phần giữa của ảnh, hình vuông), thông báo nổi "Đã cập nhật ảnh đại diện." (`account.profile.avatar.uploaded`), thẻ tài khoản cũng đổi ảnh.
  - **AC-665** *(Từ chối)* Cho máy chủ trả 413 (`AVATAR_TOO_LARGE` hoặc `UPLOAD_TOO_LARGE`), khi nhận phản hồi, thì khối ảnh hiện "Ảnh lớn hơn 5 MB. Hãy chọn ảnh nhỏ hơn." (`account.profile.avatar.errors.TOO_LARGE`); 422 `AVATAR_DIMENSIONS_EXCEEDED` thì hiện "Ảnh lớn hơn 4.096 × 4.096 px. Hãy chọn ảnh nhỏ hơn."; 422 `AVATAR_INVALID_IMAGE`, hoặc app không đọc được tệp đã chọn (khi đó không gọi API), thì hiện "Không đọc được ảnh. Hãy chọn tệp JPG, PNG hoặc WEBP hợp lệ."; lỗi khác hoặc mất mạng thì hiện "Không cập nhật được ảnh đại diện. Ảnh hiện tại được giữ nguyên." (`account.profile.avatar.errors.generic`). Ảnh đang hiện giữ nguyên trong mọi trường hợp.
  - **AC-666** *(Giới hạn)* Cho trình chọn ảnh hoặc máy ảnh đang mở, khi người dùng hủy, thì không gọi API và màn giữ nguyên như trước khi bấm. Câu `account.profile.avatar.errors.TOO_LARGE_FILE` (có tên tệp) không dùng trong app vì app không gửi tệp quá 5 MB.
  - **AC-667** *(Từ chối)* Cho người dùng đã từ chối quyền máy ảnh của app, khi chọn "Chụp ảnh", thì app không mở máy ảnh, hiện câu báo cần cấp quyền kèm nút mở phần cài đặt của app trong hệ điều hành; lựa chọn thư viện vẫn dùng được. Chọn ảnh từ thư viện không xin quyền đọc toàn bộ thư viện.

### R-170 Xóa ảnh đại diện

- Nguồn: `profile-view.tsx:231-244,296-300`; `AvatarService.java:105-122`
- Áp dụng cho: học viên đã đăng nhập, hồ sơ đang có ảnh.
- Tiêu chí chấp nhận:
  - **AC-668** Cho hồ sơ có `avatarUrl`, khi xem khối ảnh, thì có nút "Xóa ảnh" (`account.profile.avatar.remove`); hồ sơ không có ảnh thì không có nút này.
  - **AC-669** Cho bấm "Xóa ảnh" (không có hộp xác nhận, như web), khi app gọi `DELETE /api/v1/me/avatar` và nhận 204, thì vòng tròn về chữ cái đầu tên ở màn Hồ sơ và thẻ tài khoản, nút "Xóa ảnh" biến mất, thông báo nổi "Đã xóa ảnh đại diện." (`account.profile.avatar.removed`).
  - **AC-670** *(Từ chối)* Cho lời gọi xóa lỗi hoặc mất mạng, khi nhận kết quả, thì hiện "Không cập nhật được ảnh đại diện. Ảnh hiện tại được giữ nguyên." và ảnh giữ nguyên. Đang xóa thì hai nút bị khóa, bấm lần nữa không gửi thêm yêu cầu.
  - **AC-671** *(Giới hạn)* Cho ảnh đã bị xóa ở máy khác, khi bấm "Xóa ảnh" trên máy này, thì máy chủ vẫn trả 204 và app hiện kết quả như AC-669.

### R-171 Băng email chưa xác minh và trạng thái email

- Nguồn: `profile-view.tsx:156-169,619-643`
- Áp dụng cho: học viên đã đăng nhập; tài khoản có email đã xác minh, chưa xác minh, hoặc không có email.
- Tiêu chí chấp nhận:
  - **AC-672** Cho hồ sơ có email và `emailVerified = false`, khi mở màn Hồ sơ, thì trên cùng có băng cảnh báo "**Email chưa xác minh.** Xác minh để dùng email này khôi phục tài khoản và nhận thư về đơn hàng." kèm nút "Xác minh email" (`account.profile.verifyEmail`); hồ sơ không có email hoặc đã xác minh thì không có băng.
  - **AC-673** Cho khối "Email", khi hiện, thì có email hiện tại kèm nhãn "Đã xác minh" hoặc "Chưa xác minh" (`account.profile.email.verified`/`unverified`), hoặc "Tài khoản chưa có email." (`account.profile.email.none`), và nút "Đổi email".

### R-172 Xác minh email hiện tại bằng mã

Nút "Xác minh email" của băng cảnh báo (R-171) mở màn Xác minh email của app; đây là lối vào duy nhất của màn này (web cũng chỉ có một liên kết tới `/verify-email`, ở băng cảnh báo `profile-view.tsx:166`; thẻ tài khoản chỉ có nhãn "Chưa xác minh", không phải nút). Mỗi lần mở màn từ băng cảnh báo, sau khi tải hồ sơ và thấy email chưa xác minh, app tự gửi mã đúng một lần bằng `POST /api/v1/me/email/verification`, để câu "Chúng tôi đã gửi mã…" là đúng. Web không tự gửi khi mở trang; chỗ lệch này của web đã ghi ở Phụ lục A và web giữ nguyên hành vi. Thành công thì về màn Hồ sơ thay cho `callbackUrl` của web.

- Nguồn: `verify-email-view.tsx:49-346`; `profile-view.tsx:166`; `MeEmailController.java:35-55`; `EmailCodeVerificationService.java:58-108`; `EmailCodeSendPolicy.java:14-37`
- Áp dụng cho: học viên đã đăng nhập, có email.
- Tiêu chí chấp nhận:
  - **AC-674** Cho hồ sơ có email chưa xác minh, khi bấm "Xác minh email" trên băng cảnh báo, thì app gọi `GET /api/v1/me/profile` (khung chờ trong lúc tải), rồi gọi `POST /api/v1/me/email/verification` đúng một lần, và màn hiện "Xác minh email", câu "Chúng tôi đã gửi mã 6 số tới **{email}**. Mã có hiệu lực trong 10 phút." (`account.verifyEmail.sentTo`), ô "Mã xác minh" bàn phím số, nút "Xác minh", dòng "Chưa nhận được mã?" với nút "Gửi lại mã", nút "Để sau" và chú thích `account.verifyEmail.footnote` (chữ "Hồ sơ" trong chú thích đưa về màn Hồ sơ). Nhận 202 thì không hiện thêm câu nào và "Gửi lại mã" bắt đầu thời gian chờ 60 giây (R-175).
  - **AC-737** *(Giới hạn)* Cho lần gửi tự động của AC-674 nhận 429, khi hiện màn, thì `retryAfter` < 3.600 khóa "Gửi lại mã" và đếm ngược theo R-175, `retryAfter` ≥ 3.600 hiện câu giới hạn ngày của AC-678; lỗi khác 429 hoặc mất mạng thì hiện "Không gửi được mã mới. Vui lòng thử lại." (`account.verifyEmail.alerts.resendFailed`) và "Gửi lại mã" bấm được ngay. Trong mọi trường hợp app không tự gửi lần hai.
  - **AC-738** *(Giới hạn)* Cho màn Xác minh email đang mở sau lần gửi tự động, khi xoay hoặc gập/mở máy, đưa app xuống nền rồi mở lại, hoặc mở khóa app, thì app không gọi lại `POST /api/v1/me/email/verification`. Cho lần tải hồ sơ đầu bị lỗi, thì app chưa gửi mã; bấm "Thử lại" và tải được hồ sơ chưa xác minh thì app gửi đúng một lần. Cho hồ sơ tải về đã xác minh, thì app không gửi. Cho người dùng rời màn rồi mở lại từ băng cảnh báo, thì đó là một lần mở mới và app gửi một lần; nếu chưa quá 60 giây, máy chủ trả 429 và màn hiện thời gian chờ theo AC-737, mã đã gửi trước vẫn dùng được.
  - **AC-739** Cho ô "Mã xác minh" (ở màn Xác minh email và ở khối chờ của R-174), khi người dùng chạm vào ô, thì bàn phím là bàn phím số; trên iOS ô được đánh dấu là ô mã dùng một lần (`textContentType = .oneTimeCode`) để hệ điều hành gợi ý mã khi có. Trên Android không bắt buộc tự điền mã; app không dùng API đọc SMS hay đọc thư.
  - **AC-675** Cho mã đúng, khi bấm "Xác minh", thì app gửi `POST /api/v1/me/email/verify {code}`, nhận 204 thì về màn Hồ sơ đã tải lại: băng cảnh báo biến mất, nhãn "Đã xác minh"; thẻ tài khoản hiện "Email đã xác minh".
  - **AC-676** *(Từ chối)* Cho ô mã không đủ đúng 6 chữ số, khi bấm "Xác minh", thì hiện "Vui lòng nhập mã gồm 6 chữ số." và không gọi API. Cho 422 `EMAIL_CODE_INVALID`, thì hiện "Mã chưa đúng. Bạn còn {n} lần thử." với n là `remainingAttempts`. Cho 422 `EMAIL_CODE_EXPIRED` (quá 10 phút, đã dùng, đã có mã mới hơn, hoặc sai đủ 5 lần), thì hiện "Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới." Lỗi khác hoặc mất mạng thì hiện "Không xác minh được email. Vui lòng thử lại." Màn giữ nguyên, email vẫn chưa xác minh.
  - **AC-677** Cho nút "Gửi lại mã" bấm được, khi bấm, thì app gửi `POST /api/v1/me/email/verification`; nhận 202 thì hiện "Đã gửi mã mới. Mã cũ không còn dùng được.", xóa lỗi dưới ô mã và bắt đầu thời gian chờ 60 giây (R-175). Lỗi khác 429 hoặc mất mạng thì hiện "Không gửi được mã mới. Vui lòng thử lại."
  - **AC-678** *(Giới hạn)* Cho 429 có `retryAfter` < 3.600, khi nhận phản hồi, thì nút "Gửi lại mã" bị khóa và đếm ngược theo R-175. Cho `retryAfter` ≥ 3.600 (đã gửi 5 mã trong 24 giờ), thì hiện "Bạn đã gửi lại mã quá số lần cho phép. Thử lại sau {time}." với {time} là ngày/tháng và giờ:phút 24 giờ của mốc hết chờ (`now + retryAfter`) theo múi `Asia/Ho_Chi_Minh` như web (múi giờ chung của `next-intl`, `apps/web-student/src/i18n/request.ts:28`, `packages/web-kit/src/preferences.ts:17`), không theo múi giờ của máy; không hiện đếm ngược, nút khóa tới mốc đó.
  - **AC-679** *(Giới hạn)* Cho email đã được xác minh ở máy khác, khi mở màn Xác minh email, thì chỉ hiện "Email của bạn đã được xác minh." và nút "Tiếp tục" về màn Hồ sơ. Cho lời gọi hồ sơ lỗi, thì hiện "Không tải được màn hình" (chuỗi app suy từ `account.verifyEmail.errorLoadTitle`, tiếng Anh "Unable to load screen"), "Không tải được thông tin tài khoản. Hãy thử lại." (`account.verifyEmail.errorLoad`) và "Thử lại".
  - **AC-680** Cho màn Xác minh email, khi bấm "Để sau" hoặc nút quay lại, thì về màn Hồ sơ, hồ sơ không đổi.

### R-173 Yêu cầu đổi email

- Nguồn: `profile-view.tsx:493-584,645-708`; `EmailChangeService.java:89-120,191-226`
- Áp dụng cho: học viên đã đăng nhập; có hoặc không có email hiện tại.
- Tiêu chí chấp nhận:
  - **AC-681** Cho khối "Email", khi bấm "Đổi email", thì mở biểu mẫu "Đổi email" gồm ô "Email mới" (bàn phím email), ô "Mật khẩu hiện tại" có nút hiện/ẩn (nhãn trình đọc màn hình "Hiện mật khẩu hiện tại"/"Ẩn mật khẩu hiện tại"), nút "Hủy" và "Gửi mã xác minh"; bấm "Đổi email" lần nữa hoặc "Hủy" thì đóng biểu mẫu.
  - **AC-682** *(Từ chối)* Cho ô "Email mới" trống, sai dạng (không khớp `^[^\s@]+@[^\s@]+\.[^\s@]+$`) hoặc ô mật khẩu trống, khi bấm "Gửi mã xác minh", thì hiện lần lượt "Vui lòng nhập email mới.", "Email chưa đúng định dạng.", "Vui lòng nhập mật khẩu hiện tại." dưới ô và không gọi API.
  - **AC-683** Cho dữ liệu hợp lệ, khi bấm "Gửi mã xác minh", thì app gửi `POST /api/v1/me/email/change {newEmail (đã cắt khoảng trắng), currentPassword}`; nhận 202 thì biểu mẫu đóng, khối chờ của R-174 hiện với email mới và thời gian chờ 60 giây bắt đầu. Email hiện tại và nhãn xác minh không đổi.
  - **AC-684** *(Từ chối)* Cho 409 `EMAIL_TAKEN` (email thuộc tài khoản khác, hoặc trùng email hiện tại), thì dưới "Email mới" hiện "Email này đã thuộc tài khoản khác."; 400 có lỗi trường `currentPassword` (`WRONG_OLD_PASSWORD`) thì dưới ô mật khẩu hiện "Mật khẩu hiện tại không đúng."; 400 có lỗi trường `newEmail` thì hiện "Email chưa đúng định dạng."; 429 (`RATE_LIMITED`, hoặc `LOGIN_THROTTLED` khi sai mật khẩu quá nhiều) thì hiện cảnh báo "Bạn gửi mã quá nhanh hoặc quá số lần cho phép. Vui lòng thử lại sau." và khóa nút gửi trong `retryAfter` giây, thiếu `retryAfter` thì 60 giây; lỗi khác hoặc mất mạng thì hiện "Không gửi được mã xác minh. Vui lòng thử lại." Mọi trường hợp: biểu mẫu giữ giá trị đang gõ, không có email chờ mới.
  - **AC-685** Cho đang gửi, khi bấm "Gửi mã xác minh" lần nữa, thì nút ở trạng thái xử lý và chỉ một yêu cầu được gửi.

### R-174 Email chờ và xác nhận đổi email

- Nguồn: `profile-view.tsx:586-609,710-778`; `EmailChangeService.java:129-181`; `UserAccount.java:150-173`
- Áp dụng cho: học viên đã đăng nhập, hồ sơ có `pendingEmail`.
- Tiêu chí chấp nhận:
  - **AC-686** Cho hồ sơ có `pendingEmail` (kể cả khi tải lại từ máy chủ sau khi tắt app hoặc từ yêu cầu gửi trên web), khi mở màn Hồ sơ, thì khối "Email" có khối chờ "Đang chờ xác minh email mới: {email}", đoạn "Mã 6 số đã gửi tới địa chỉ mới, có hiệu lực trong 10 phút. Trong lúc chờ, email hiện tại vẫn dùng để khôi phục tài khoản. Mã hết hạn thì hồ sơ giữ email hiện tại.", ô "Mã xác minh", nút "Xác nhận" và "Gửi lại mã".
  - **AC-687** Cho mã đúng, khi bấm "Xác nhận", thì app gửi `POST /api/v1/me/email/change/confirm {code}`; nhận 204 thì dòng email hiện email mới kèm "Đã xác minh", khối chờ biến mất, ô mã trống, thông báo nổi "Đã đổi email." (`account.profile.email.changed`); thẻ tài khoản hiện email mới.
  - **AC-688** *(Từ chối)* Cho ô mã không đủ đúng 6 chữ số, thì hiện "Vui lòng nhập mã gồm 6 chữ số." và không gọi API. Cho 422 `EMAIL_CODE_INVALID`, thì hiện "Mã chưa đúng. Bạn còn {n} lần thử."; 422 `EMAIL_CODE_EXPIRED` thì hiện "Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới."; 409 `EMAIL_TAKEN` thì hiện "Email này vừa thuộc về tài khoản khác. Hãy chọn email khác."; 503 thì hiện "Chưa đổi được email, email hiện tại giữ nguyên. Vui lòng thử lại sau."; lỗi khác hoặc mất mạng thì hiện "Không xác nhận được email mới. Vui lòng thử lại." Email hiện tại giữ nguyên, khối chờ còn.
  - **AC-689** Cho khối chờ và thời gian chờ đã hết, khi bấm "Gửi lại mã", thì biểu mẫu "Đổi email" mở với "Email mới" điền sẵn email chờ và ô mật khẩu trống; gửi lại đi theo R-173. Trong thời gian chờ, "Gửi lại mã" bị khóa.
  - **AC-690** *(Giới hạn)* Cho mã đã quá 10 phút, khi mở màn Hồ sơ, thì khối chờ vẫn hiện (máy chủ chỉ xóa email chờ khi xác nhận xong hoặc có yêu cầu mới); bấm "Xác nhận" với mã cũ thì nhận lỗi `EMAIL_CODE_EXPIRED` như AC-688.
  - **AC-691** *(Giới hạn)* Cho email chờ đã được xác nhận hoặc đã bị thay bằng một yêu cầu khác ở máy khác, khi bấm "Xác nhận" trên máy này với mã cũ, thì hiện câu của `EMAIL_CODE_EXPIRED`; mở lại màn Hồ sơ thì thấy trạng thái mới của máy chủ.

### R-175 Thời gian chờ gửi mã

Áp dụng chung cho nút "Gửi mã xác minh", "Gửi lại mã" ở khối chờ và "Gửi lại mã" ở màn Xác minh email.

- Nguồn: `cooldown.ts:4-57`; `profile-view.tsx:488-491,703-706,773-785`; `verify-email-view.tsx:47,318-327`; `EmailCodeSendPolicy.java:14-37`
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-692** Cho một lần gửi mã vừa thành công, khi xem nút gửi, thì nút bị khóa 60 giây và dưới nút có dòng "Gửi mã xác minh sau 00:59" (biểu mẫu đổi email) hoặc "Gửi lại mã sau 00:59" (khối chờ, màn Xác minh email), số `mm:ss` giảm mỗi giây; về 0 thì dòng biến mất và nút bấm được.
  - **AC-693** Cho trình đọc màn hình đang bật, khi thời gian chờ bắt đầu và khi kết thúc, thì app đọc đúng một lần "Có thể gửi lại mã sau {time}." và một lần "Bạn có thể gửi lại mã bây giờ.", không đọc từng giây.
  - **AC-694** *(Giới hạn)* Cho thời gian chờ 60 giây đang chạy, khi đưa app xuống nền 30 giây rồi mở lại, thì dòng đếm ngược còn khoảng 30 giây (tính theo đồng hồ thật, không dừng khi app ở nền).
  - **AC-695** *(Giới hạn)* Cho thời gian chờ từ 3.600 giây trở lên ở biểu mẫu đổi email, khi hiện, thì không có dòng đếm ngược, chỉ có cảnh báo của AC-684 và nút khóa tới hết thời gian đó.
  - **AC-696** *(Giới hạn)* Cho người dùng rời màn Hồ sơ hoặc màn Xác minh email trong lúc chờ rồi quay lại, khi bấm gửi trước khi hết 60 giây, thì máy chủ trả 429 với `retryAfter` còn lại và app khóa nút theo số giây đó (AC-678, AC-684).

### R-176 Hợp đồng API mobile có các thao tác hồ sơ

- Nguồn: `MobileOpenApiExportIT.java:66,196-210`; `packages/api-client/openapi.json` (các dòng ở mục Hiện trạng)
- Áp dụng cho: hợp đồng `contracts/mobile/`.
- Tiêu chí chấp nhận:
  - **AC-697** Cho bản xuất `contracts/mobile/openapi-mobile.json` sau thay đổi, khi đọc các đường dẫn, thì có `PATCH /api/v1/me/profile`, `PUT` và `DELETE /api/v1/me/avatar`, `GET /api/v1/avatars/{assetId}`, `POST /api/v1/me/email/verification`, `/verify`, `/change`, `/change/confirm` cùng các schema chúng dùng (`ProfileUpdateRequest`, `EmailCodeChangeRequest`, `EmailCodeVerifyRequest`, `EmailCodeErrorResponse`); test xuất hợp đồng và bước so tệp của CI đạt; không thao tác nào có tham số cookie.
  - **AC-698** Cho thư mục `contracts/mobile/api/`, khi liệt kê, thì có tệp mẫu cho: hồ sơ có `avatarUrl` và `pendingEmail`; ảnh 413 `AVATAR_TOO_LARGE`, 422 `AVATAR_INVALID_IMAGE`, 422 `AVATAR_DIMENSIONS_EXCEEDED`; 422 `EMAIL_CODE_INVALID` có `remainingAttempts`; 422 `EMAIL_CODE_EXPIRED`; 429 `RATE_LIMITED` có `retryAfter`; 409 `EMAIL_TAKEN`; 400 `WRONG_OLD_PASSWORD` trường `currentPassword`; 400 lỗi trường `phone`. Test của backend khẳng định các tệp khớp phản hồi thật, như các tệp mẫu đang có.

## 7. Ảnh hưởng tới phần đang có

| Thứ bị ảnh hưởng | Ở đâu | Thay đổi | Yêu cầu |
| --- | --- | --- | --- |
| Ba màn giữ chỗ của app | Android `shell/PlaceholderScreen.kt`, `shell/AppNavHost.kt`; iOS tương ứng; test `PlaceholderScreensTest` / `PlaceholderScreensTests` | Thay bằng màn thật; test giữ chỗ bỏ hoặc đổi | R-1 |
| Mức iOS tối thiểu | `apps/ios/project.yml:6,19,40,82,106`; `apps/ios/Thi360Tests/BundleInfoTests.swift`; `.github/workflows/ios.yml` | Nâng lên 18.0; test khẳng định 18.0 | R-2 |
| Tài liệu nêu iOS 17 | `ARCHITECTURE.md:47`, `DESIGN.md:586`, `README.md:85,200`, `apps/ios/README.md:3,11` | Sửa thành iOS 18 | R-2 |
| Hợp đồng API mobile và client sinh tự động | `contracts/mobile/openapi-mobile.json`, `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java`, `apps/ios/Thi360/Core/Network/OpenAPI/openapi.json`, `apps/android/app/build.gradle.kts` | Thêm các endpoint của đợt này; sinh lại client | R-3 |
| Bảng màu của app | `DESIGN.md` mục 11.2; nguồn màu của app; có thể cả route `apps/web-student/src/app/mobile/appearance-tokens.json/route.ts` | Thêm sáu màu trạng thái | R-6 |
| Tệp chuỗi của hai app | Android `res/values*/strings.xml`; iOS `Localizable` | Thêm chuỗi cho mọi màn mới, hai ngôn ngữ | R-7 |
| Tài liệu dự án | `ARCHITECTURE.md` mục 5.9, `DESIGN.md` mục 11, `README.md`, `TASK.md`, `PROGRESS.md`, `docs/design/app-hoc-vien/README.md` | Ghi các màn mới, bộ vẽ công thức, lệnh chạy | R-1, R-2 |
| Thay đổi trên `develop` sau khi viết đặc tả (`6b8fc1e6` đến `04a9457b`) | UNI-2817 `commerce/pricing/PricingEngine.java` (khuyến mãi tự động giảm hết giá thì giá là 0, không kẹp giá sàn); UNI-2826 `attempt-player.tsx`, `part-navigation.tsx`, `question-nav.tsx` (đầu trang màn làm bài của web gọn lại, thanh danh sách câu luôn thấy trên màn hẹp, dải phần tự cuộn tới phần đang làm; không đổi chuỗi, không đổi API); UNI-2828 hai trang văn bản, chân trang mới, logo tạm thay chữ "360" (`common.logoMark`) ở mọi màn của web học viên có logo (đầu trang, đăng nhập, đăng ký, quên và đặt lại mật khẩu, xác minh email, đổi mật khẩu, màn chờ bắt đầu lượt, và khung thay ảnh bìa ở màn chi tiết `exam-detail-view.tsx`), cùng phần sửa luồng dựng đề, trình soạn câu hỏi và quy tắc MAPID cho QDA, BCA ở web admin và backend; UNI-2821 đến UNI-2825 chỉ đổi web admin và quy tắc dựng đề của backend | Đặc tả đổi ba chỗ: R-180 (hai trang đã có, thay nội dung, D-35), R-24 (khung thay ảnh bìa hiện hình dấu), giả định về số điện thoại ở chân trang. Không chuỗi nào của web bị đổi hay gỡ giữa hai mốc; mã vẽ câu hỏi, nội dung đề, mua hàng, kết quả, hồ sơ không đổi. Giá 0 do khuyến mãi tự động đã nằm trong các tiêu chí về đơn miễn phí của mục 6.2 | R-180, R-24 |
| Tên hiển thị "Thi 360" | hai app (tệp chuỗi, `apps/ios/project.yml:67`, `apps/ios/Config/Base.xcconfig:7`, các tệp Info của iOS; số chỗ đếm lại bằng `git grep -o` lúc làm task), `apps/web-student/src` (18 chỗ), `apps/web-admin/src` (84 chỗ), `packages/ui/src` (8, kể cả `components/brand-logo.tsx` mới của UNI-2828), `backend/src/main/resources` (40 chỗ, 20 tệp), `infra` (6), đếm trên `develop` tại `04a9457b`; các test khẳng định chuỗi có tên | Đổi thành "Thi360"; test đổi theo; định danh kỹ thuật giữ nguyên. Tên hiển thị của realm trong tệp `infra/keycloak/thi-360-realm.json` đổi trong repo; áp lên Keycloak dùng chung là việc sau merge | R-178 |
| Tên sản phẩm trong thư của backend | `backend/src/main/resources/mail/templates/` (38 chỗ ở 18 trong 26 tệp), `application.yml:210`, `shared/mail/MailProperties.java`, nơi dựng thư (`shared/mail/`), tệp biến môi trường mẫu và tài liệu triển khai | Mẫu thư dùng biến tên sản phẩm lấy từ cấu hình `thi360.mail.product-name` (`THI360_MAIL_PRODUCT_NAME`, mặc định "Thi360"); người gửi mặc định đổi thành "Thi360 <no-reply@thi360.local>"; môi trường đang chạy đã đặt `THI360_MAIL_FROM` với tên cũ thì đơn vị vận hành tự đổi giá trị đó | R-178 |
| Biểu tượng và logo của app | Android `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_thi360_logo.xml`, `mipmap-anydpi/*`; iOS `Assets.xcassets/AppIcon.appiconset`; `LoginScreen.kt:747`, `LockScreen.kt:246`, `PlaceholderScreen.kt:78` và chỗ tương ứng trên iOS; ảnh chụp màn của đợt trước | Thay bằng hình dấu Thi360; ảnh chụp ghi lại | R-179 |
| Hộp "Điều khoản sử dụng" ở Đăng ký | web `features/account/register-view.tsx:48,498-507`, `messages/vi.json:180-186`, `en.json`; Android `strings_recovery.xml:37-43`, `RegistrationScreen.kt`, `RegistrationState.kt:5`; iOS `RegisterView.swift`, `RegistrationModels.swift:28`, `Recovery.xcstrings`; test đăng ký của ba nơi | Thay câu giữ chỗ bằng hai văn bản thật; `termsVersion` lấy từ tệp văn bản | R-180, R-181 |
| Trang công khai của web học viên | `apps/web-student/src/app/dieu-khoan-giao-dich-chung/`, `chinh-sach-bao-mat-thong-tin/`, `apps/web-student/src/features/legal/**`, các khóa `legal.*` trong `messages/vi.json`, `en.json`, `apps/web-student/src/proxy.ts` (hai đường dẫn đã là công khai từ `04a9457b`) | Hai trang đã có từ UNI-2828 được đổi sang vẽ từ `contracts/mobile/legal/`; hai thành phần ghi cứng nội dung và các khóa `legal.terms.*`, `legal.privacy.*` bị gỡ; test của hai trang viết lại; chân trang và danh sách đường dẫn công khai không đổi | R-180 |
| Tệp nội dung văn bản | thư mục mới `contracts/mobile/legal/`; `docs/legal/` | Tệp có cấu trúc dựng từ bản đã duyệt; web và hai app cùng đọc | R-180, R-181 |
| Mục Tài khoản của app | `feature/account/**`, `Features/Account/**` | Thêm hai dòng mở màn văn bản | R-181 |
| Ghi nhận thanh toán và cấp bài | `commerce/payment/application/PaymentOutcomeApplier.java`, `commerce/payment/application/VnpayIpnService.java:91-101`, `commerce` `FulfillmentService`; cờ cần xem xét của đơn; test của luồng IPN và cấp bài | Thêm một điều kiện: đơn của tài khoản đã xóa thì ghi nhận tiền nhưng không cấp quyền thi, đánh dấu cần xem xét; tài khoản chưa xóa không đổi | R-182 |
| Bản ghi đăng ký và dòng nhập từ tệp | `identity.pending_registration`, `identity.import_row` (cột `raw` và các cột đã chuẩn hóa) | Lệnh tự xóa xóa bản ghi đăng ký của tài khoản và gỡ thông tin cá nhân trong dòng nhập của tài khoản | R-182 |
| Lệnh xóa người dùng và dữ liệu liên quan | `identity/application/UserAdminService.java` (xóa mềm, `performSoftDeleteLocal`), `identity/domain/UserAccount.java`, `identity/spi/AccountDeletionGuard.java` và hai guard của `commerce`, `identity/application/AvatarService.java`, `identity/infrastructure/KeycloakIdentityClient.java`, `shared/mail/MailKind.java` và thư mục mẫu thư, `classroom/application/UserDeletedClassCleanupListener.java`; test của các lớp đó | Thêm đường tự xóa dùng lại phần xóa mềm, thêm gỡ thông tin cá nhân, mẫu thư mới, mã lỗi mới; lệnh xóa của admin và các guard không đổi hành vi | R-182 |
| Hợp đồng API | `packages/api-client/openapi.json`, `contracts/mobile/openapi-mobile.json`, `contracts/mobile/api/` | Thêm hai thao tác xóa tài khoản và tệp mẫu của chúng (200 của `deletion-check` cho ba trường hợp, 204, 400, 409, 429, 403) | R-182 |
| Màn quản trị người dùng | `apps/web-admin/src/features/users/components/user-detail-view.tsx:292-299`, `apps/web-admin/src/messages/vi.json`, `en.json`, danh sách người dùng | Tài khoản tự xóa hiện ở trạng thái đã xóa với tên vô danh; dòng báo đã xóa đổi mã `SELF_DELETED` thành nhãn "Học viên tự xóa tài khoản" / "Deleted by the student" (D-33); lý do do admin nhập hiện nguyên văn như trước | R-182 |
| Mục Tài khoản của app và trang Hồ sơ của web | `feature/account/**`, `Features/Account/**`, `apps/web-student/src/features/account/**`, `apps/web-student/src/app/account/` | Thêm dòng, màn và trang Xóa tài khoản | R-183, R-184 |
| Hai văn bản | `docs/legal/` | Phát hành ở trạng thái `published`, phiên bản `2026-11-26` (D-32); các điểm do người viết đề xuất được ghi ở `docs/legal/README.md` để đơn vị vận hành tra khi cần sửa. Chính sách bảo mật nêu cách xóa trong app và trên web; Điều kiện giao dịch chung nêu lượt thi chưa dùng mất khi tự xóa tài khoản | R-184 |
| Màn giữ chỗ mục Trang chủ | `apps/android`, `apps/ios` | Thay bằng danh mục; AC-10 của bản khung không còn đúng cho mục Trang chủ | R-9 |
| Hợp đồng API của app | `contracts/mobile/openapi-mobile.json`, `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java:196` | Thêm `GET /api/v1/public/storefront/filters`, `GET /api/v1/public/storefront/products`, `GET /api/v1/public/storefront/products/{code}`, `POST /api/v1/me/exam-access/query` và các schema đi kèm; thêm mẫu phản hồi vào `contracts/mobile/api/` (200, 404, 410, 409 `PRODUCT_UNAVAILABLE`, 429) | R-9, R-21, R-24 tới R-30 |
| Chuỗi giao diện của app | String Catalog iOS, `strings.xml` Android | Thêm các khóa `storefront.catalog`, `storefront.format`, `storefront.access`, `storefront.detail`, `storefront.gone`, `storefront.notFound`, `bundle.detail`, `bundle.unavailablePage`, `bundle.buyLabel`, `bundle.buyNote`, `errorPage.title|description`, `common.offline`, cả vi và en, cùng câu chữ với web trừ hai câu ở Giả định | R-9 tới R-31 |
| Tải ảnh qua mạng | hai app | App cần tải ảnh từ đường dẫn công khai của API (chưa có ở bản khung) | R-19 |
| Web học viên | `apps/web-student` | Không đổi | — |
| Hợp đồng API mobile và client sinh ra | `contracts/mobile/openapi-mobile.json` (hiện chỉ có `auth/*`, `/me`, `/me/profile`) | Thêm `POST /me/price-quote`, `POST /me/exam-access/query`, `GET/POST /me/orders`, `GET /me/orders/{code}`, `POST /me/orders/{code}/pay`, `POST /me/orders/{code}/cancel`, `GET /public/payments/vnpay/return`, `GET /public/storefront/products/{code}` theo `packages/api-client/openapi.json:54445,54783,54940,55104,55268,56002` | R-32 đến R-47 |
| Tệp xác minh liên kết của web | `apps/web-student/src/features/mobile/deep-links.ts:16,75`; test `app/.well-known/well-known.route.test.ts` | Khai thêm `/checkout/result/app` cạnh `/reset-password`; nếu phiên trình duyệt của iOS đòi host gắn với app qua dịch vụ `webcredentials` thì khai thêm mục đó cùng `appIDs` | R-40 |
| Quyền tên miền của app iOS | `apps/ios/Thi360/Thi360.entitlements:7` (chỉ `applinks:`) | Thêm `webcredentials:` cho host web nếu phiên trình duyệt đòi (xem mục "Thay đổi backend hoặc web cần có") | R-39, R-40 |
| Trang web mới | `apps/web-student/src/app/checkout/result/` (hôm nay chỉ có `page.tsx`) | Thêm Trang dự phòng `/checkout/result/app` | R-41 |
| Chọn `vnp_ReturnUrl` ở backend | `OrderService.java:674-686`, dùng ở `:314,580,603` | Chọn đường dẫn theo kênh của phiên gọi lệnh tạo ra lần thanh toán | R-40, R-44 |
| Tiêu chí AC-50 của đặc tả khung và xác thực | `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md:204` ("chỉ đường dẫn `/reset-password`") | Sửa thành hai đường dẫn | R-40 |
| Intent-filter App Links của Android | `apps/android/app/src/main/AndroidManifest.xml:28-36`; test `ManifestAppLinksTest.kt` | Thêm `data` cho đường dẫn trả về | R-40 |
| Bộ định tuyến liên kết đến của hai app | Android `shell/AppNavHost.kt:202-230,350`; iOS `Shell/AppRouter.swift:62-85`, `App/Thi360App.swift:45-50` | Nhận thêm Liên kết trả về; khác liên kết đặt lại mật khẩu ở chỗ phải qua màn khóa và cần phiên | R-40 |
| Mục Tài khoản | khung app hiện có | Thêm lối vào Đơn hàng (giả định A-1) | R-45 |
| Màn giữ chỗ mục Bài thi của tôi, Lịch sử (Android) | `apps/android/app/src/main/java/vn/unicom/thi360/shell/AppNavHost.kt:831-842`, `shell/PlaceholderScreen.kt:86-168` | Hai route `my_exams`, `history` dựng màn thật; `PlaceholderTabScreen` chỉ còn cho mục nào chưa làm (Trang chủ do phần khác thay). | R-49, R-58 |
| Màn giữ chỗ (iOS) | `apps/ios/Thi360/Shell/AdaptiveShellView.swift:186-219,240-256`, `PlaceholderDestinationView.swift` | `.myExams`, `.history` dựng màn thật ở cả ba bố cục (tab bar, sidebar iPad, hai nửa iPhone Duo); câu `shell.placeholder.myExamsDescription`, `shell.placeholder.historyDescription` và nhánh `placeholderDescriptionKey` của hai mục bỏ đi. | R-49, R-58 |
| Chuỗi giữ chỗ | `apps/android/app/src/main/res/values*/strings_shell.xml:9-10`, `apps/ios/Thi360/Resources/Shell.xcstrings`, `apps/ios/Thi360/Shell/ShellLayout.swift:30-37` | Không còn dùng cho hai mục này. | R-49, R-58 |
| Test màn giữ chỗ | `apps/android/app/src/test/java/vn/unicom/thi360/PlaceholderScreensTest.kt`, `apps/ios/Thi360Tests/PlaceholderScreensTests.swift` | Bỏ khẳng định "Chưa có nội dung" và "không gọi mạng" cho hai mục; AC-10 của đặc tả khung không còn đúng với hai mục này. | R-49, R-58 |
| Hợp đồng API mobile | `contracts/mobile/openapi-mobile.json` (và fixture `contracts/mobile/api/`) | Thêm `GET /api/v1/me/exams`, `GET /api/v1/me/attempts`, `GET /api/v1/catalog/options` cùng schema `MyExamsResponse`, `MyExamCard`, `MyExamBundleRef`, `ExamKindRef`, `IntRange`, `LocalizedName`, `PageResponseAttemptHistoryItem`, `AttemptHistoryItem`, `AttemptChoiceSummary`, `CatalogOptionResponse`, chép từ `packages/api-client/openapi.json`; sinh lại client Kotlin và Swift. | R-49, R-58 |
| Chuỗi của app | tài nguyên chuỗi Android, iOS | Thêm các khóa `home.*`, `bundle.home.*`, `history.*`, `home.checking.*`, `storefront.format.*`, `storefront.review`, `storefront.units.questions`, `common.offline.*`, `common.retry`, `common.loading`, `landing.support.pending` với cùng câu chữ vi/en như web. | R-49 đến R-70 |
| Hợp đồng API mobile và client sinh ra | `contracts/mobile/openapi-mobile.json`; client Kotlin, Swift sinh từ đó | Thêm `GET /api/v1/me/entitlements/{entitlementId}/prepare`, `POST /api/v1/me/attempts`, `GET /api/v1/me/attempts/{attemptId}`, `POST …/session`, `POST …/heartbeat`, `PUT …/answers`, `POST …/parts/{partId}/finish`, `POST …/submit`, `POST …/parts/{partId}/choice`, `POST …/parts/{partId}/retry`, `GET /api/v1/me/exams` cùng các schema; khai `Idempotency-Key` của `POST /api/v1/me/attempts` là bắt buộc, phản hồi 202 của nó, 409 `AttemptSessionConflictResponse` và header `Retry-After` của 429 | R-71 – R-98 |
| Đăng xuất trong app | Mục Tài khoản, màn khóa (đặc tả khung app AC-61, AC-69) | Đăng xuất xóa thêm mọi bản nháp, vị trí, `sessionId`, ngữ cảnh bắt đầu của tài khoản đăng xuất (không đụng dữ liệu của tài khoản khác trên máy); không thêm cảnh báo về câu trả lời chưa lưu | R-95 |
| Màn khóa app | Khóa sinh trắc học của khung app (AC-58, AC-59) | Khi mở khóa trên màn làm bài, chạy làm mới của R-99 | R-100 |
| Khung điều hướng | Mục Bài thi của tôi | Màn chuẩn bị, màn chờ, màn làm bài mở từ mục này; màn làm bài che thanh điều hướng dưới và thanh bên | R-71, R-77 |
| Hai app | `apps/android`, `apps/ios` | Thêm bộ vẽ nội dung đề, bộ vẽ công thức (RaTeX, SwaTex), tám thành phần câu hỏi ở ba chế độ, trình soạn Tự luận, trình phát audio, hàng phím dưới ô số, quản lý tệp tạm của tài nguyên lượt thi. Trên Android, bộ vẽ RaTeX vẽ qua API View và chữ có định dạng trong khi app viết bằng Compose (phần Hiện trạng) | R-106 đến R-177 |
| Tệp chuỗi của hai app | tệp chuỗi `vi`/`en` của mỗi app | Thêm nhóm khóa `question.*` chép từ `packages/ui/src/messages/ui-strings.ts:190-329` và bản tiếng Anh cùng tệp; thêm `attempt.editor.*`, `attempt.player.attachmentsLabel`; thêm một khóa mới thay cho `question.audioPlayer.autoplayBlocked`; thêm tên cho trình đọc màn hình của 4 phím "-", "/", ",", "." dưới ô số | R-118 đến R-142 |
| Hợp đồng API mobile | `contracts/mobile/openapi-mobile.json` | Phải có `GET …/assets/{assetId}`, `GET …/solution-assets/{assetId}`, bốn endpoint `…/audio/{assetId}/plays…` và các schema câu hỏi, câu trả lời, `AttemptAudioPlayView`, `AudioPolicyView` (nằm trong R-3) | R-109, R-132 đến R-137 |
| Bộ mẫu đối chiếu | thư mục mới `contracts/mobile/rich-text/` | Tệp công thức hai nguồn (danh mục `docs/specs/fixtures/formula-catalogue.json` với 225 nhóm, mẫu có sẵn), danh sách miễn trừ, trạng thái lượt 50 câu, 10 bài Tự luận mẫu, 30 bài đếm chữ; tham chiếu tới hai tệp mẫu hiện có (không sửa hai tệp đó) | R-117, R-130, R-129 |
| Phụ thuộc và phép kiểm của hai app | tệp khai báo phụ thuộc của `apps/android`, `apps/ios`; cấu hình test Android | Ghim đúng một phiên bản RaTeX và SwaTex; thêm lượt chạy phép kiểm tệp công thức trên ảnh máy ảo hoặc máy thật ARM64 trang 16 KB | R-117 |
| Lưu trữ trên máy của app | thư mục bộ đệm của app; quy tắc sao lưu của app | Tệp tạm của tài nguyên lượt thi, xóa theo R-177, loại khỏi sao lưu | R-177 |
| Quy ước giao diện app | `DESIGN.md` mục 11 | Thêm mục nội dung đề và thành phần câu hỏi trên app: bảng và công thức rộng cuộn trong khung, phóng to ảnh, màu đầu bảng ở hai chế độ, trình phát audio | R-108, R-110, R-113, R-115, R-133 |
| Phiên phát âm thanh của app | cấu hình âm thanh của app (Android `AudioFocus`/media session, iOS `AVAudioSession`) | Không bật phát ở nền; điều khiển phát hệ thống không có tua, tốc độ | R-134, R-136 |
| Mục Bài thi của tôi và Lịch sử (đang là chỗ giữ chỗ) | `apps/android`, `apps/ios` | Có thêm màn Kết quả và màn Xem lại trong ngăn của từng mục | R-143, R-155, R-164 |
| Hợp đồng API mobile | `contracts/mobile/openapi-mobile.json` | Thêm `GET /api/v1/me/attempts/{attemptId}/result`, `…/review`, `…/solution-assets/{assetId}`, `GET /api/v1/catalog/options` cùng các schema `AttemptResultResponse`, `AttemptReviewResponse`, `CatalogOptionResponse`, mẫu phản hồi cho từng `state` và từng mức xem, và các mã lỗi `ATTEMPT_NOT_FINISHED`, `REVIEW_NOT_INCLUDED`, `REVIEW_NOT_AVAILABLE`, `NOT_FOUND` | R-143, R-156, R-163 |
| Chuỗi giao diện của app | tài nguyên chuỗi vi/en của hai app | Thêm các khóa `result.*`, `review.*` với câu chữ của web | Mọi yêu cầu |
| Màn lượt đã đóng của trình làm bài | mục 6.4 | Nút "Xem kết quả" thay màn trình làm bài bằng màn Kết quả | R-143 |
| Danh sách mục Tài khoản Android | `AccountScreen.kt:440-583` | Thêm dòng "Hồ sơ", "Đơn của tôi" dưới thẻ; thẻ bấm được | R-165 |
| Danh sách mục Tài khoản iOS | `AccountView.swift:21-55,231-289` | Như trên | R-165 |
| Ngăn chi tiết Android | `AccountTestTags.kt:3-7`, `AppNavHost.kt:323-325`, `AccountScreen.kt:255-279` | Thêm ngăn Hồ sơ và Đơn của tôi; ngăn mặc định đổi từ `CHANGE_PASSWORD` sang Hồ sơ | R-165 |
| Ngăn chi tiết iOS | `AccountModels.swift:81-83`, `AccountView.swift:532-563`, `AdaptiveShellView.swift:221-243` | Ngăn phải hiện theo dòng đang chọn thay cho luôn là màn đổi mật khẩu | R-165 |
| Thẻ tài khoản hai app | `AccountScreen.kt:639-745`, `AccountView.swift:132-190` | Hiện ảnh có xác thực thay cho chỉ chữ cái đầu tên; cập nhật theo kết quả từ màn Hồ sơ | R-166, R-167 |
| Mô hình hồ sơ trong app | `AccountProfileGateway.kt:7-46`, `AccountModels.swift:3-26` | Thêm `phone`, `avatarAssetId`, `avatarUrl`, `pendingEmail` | R-166 – R-174 |
| Tải hồ sơ một lần mỗi phiên | `AppNavHost.kt:320-339`, `AccountViewModel.swift:16,86-111` | Màn Hồ sơ tải lại mỗi lần mở; thẻ tài khoản nhận giá trị mới từ màn Hồ sơ | R-167 |
| Màn khóa | `AppNavHost.kt:586-603` | Họ tên mới hiện ở màn khóa sau khi lưu | R-167 |
| Chuỗi giao diện | `apps/android/app/src/main/res/values/strings_account.xml`, `values-en/strings_account.xml`, `apps/ios/Thi360/Resources/Account.xcstrings` | Thêm chuỗi theo khóa `account.profile.*`, `account.verifyEmail.*`, `common.offline.*`, `header.nav.profile`, `header.nav.orders` của `vi.json`/`en.json`, cộng các chuỗi riêng của app ở mục Giả định | R-165 – R-175 |
| Quyền máy ảnh | iOS `apps/ios/Thi360/Resources/Info-Dev.plist`, `Info-Demo.plist` (`apps/ios/project.yml:49-50`); Android `AndroidManifest.xml:4` | iOS cần câu giải thích quyền máy ảnh; Android cần cách mở máy ảnh và nhận ảnh | R-169 |
| Hợp đồng mobile | `MobileOpenApiExportIT.java:66,196-210`, `contracts/mobile/openapi-mobile.json`, `contracts/mobile/api/` | Thêm thao tác và tệp mẫu; bỏ khẳng định "không có `PATCH /me/profile`" | R-176 |

### 7.1 Thay đổi backend hoặc web cần có

**Danh mục và chi tiết bài thi (mục Trang chủ của app)**

- Không có thay đổi hành vi backend hay web. Bốn endpoint đã nhận `Authorization: Bearer` (`/api/v1/public/**` cho phép mọi người, `shared/security/SecurityConfig.java:113-114`; `/me/exam-access` qua `ExamLearnerGate.java:31-41`).
- Cần thêm bốn thao tác trên vào danh sách của bản lọc mobile (`MobileOpenApiExportIT.java:196`) và sinh lại `contracts/mobile/openapi-mobile.json`, vì app chỉ sinh client từ tệp này (`ARCHITECTURE.md:45`) và CI so tệp này với bản xuất (`.github/workflows/backend.yml:29`). Ảnh bìa (`/covers/{assetId}`) tải bằng đường dẫn, không cần trong client sinh.

**Mua bài thi, thanh toán VNPAY và đơn hàng**

- **Đường dẫn trả về riêng cho thanh toán bắt đầu từ app (đã chốt: cách B).** Hôm nay `vnp_ReturnUrl` luôn là `{web-url}/checkout/result` (`OrderService.java:674-686`, dùng ở `OrderService.java:314,580,603`), và tệp xác minh liên kết chỉ khai `/reset-password` (`deep-links.ts:16,75`, `AndroidManifest.xml:32-35`), nên không đường nào đưa kết quả VNPAY về app. Thay đổi:
  - *Backend:* `vnp_ReturnUrl` thuộc về từng lần thanh toán, không thuộc về đơn. Nó được chọn theo kênh của phiên gọi lệnh tạo ra lần thanh toán đó: lệnh tạo đơn (cả đơn mới lẫn đơn chờ được dùng lại) và lệnh thanh toán tiếp. Phiên kênh `MOBILE` nhận `{web-url}/checkout/result/app`, phiên web giữ `{web-url}/checkout/result`. Kênh của phiên tạo ra đơn không có vai trò gì: đơn tạo trên web mà thanh toán tiếp trong app thì lần mới về app, và ngược lại (AC-710). Máy chủ đã ghi kênh của phiên (`SessionStore.java:282,538-544`, `AuthService.java:358`), nên không phải đổi API hay hợp đồng. Sửa: `OrderService.buildPaymentUrl` nhận đường dẫn theo kênh (`OrderService.java:674-686`) ở cả `createOrderInTx` và `payOrderInTx`. Cổng giả lập nhận được ngay vì URL tuyệt đối chỉ bị so origin (`MockVnpayController.java:259-278`) và tiền tố `/checkout/result/` cũng được nhận (`MockVnpayController.java:251`); cổng thật chuyển nguyên giá trị (`RealVnpayGateway.java:131,155`). Không đổi cách xử lý IPN.
  - *Tệp xác minh và app:* tệp xác minh của web và intent-filter Android (`autoVerify`) khai thêm `/checkout/result/app`; web `/checkout/result` không đổi. Trên iOS, app mở Trang thanh toán bằng phiên đăng nhập web của hệ điều hành với liên kết trả về `https` khớp host web và đường dẫn `/checkout/result/app`; hệ điều hành tự đóng phiên khi trang đi tới liên kết đó và trao liên kết cho app. Cần làm khi lập kế hoạch: nâng mức tối thiểu của dự án iOS lên 18 (D-5; hôm nay `project.yml:19,40` còn 17.0), và kiểm theo tài liệu Apple xem host có phải được gắn với app qua dịch vụ `webcredentials` hay không; nếu có thì khai thêm ở tệp xác minh và `Thi360.entitlements`. Trên Android, app mở Trang thanh toán bằng Custom Tab; chuyển hướng tới `/checkout/result/app` được intent-filter App Links nhận. Đây là hành vi nền tảng, không kiểm được trong repo; AC-706 và AC-708 kiểm trên máy thật sau khi merge.
  - *Web, Trang dự phòng:* thêm trang `/checkout/result/app` (hôm nay chỉ có `app/checkout/result/page.tsx`; đường dẫn con trả 404). Trang này luôn dùng được khi liên kết mở ra trong trình duyệt, dù hệ điều hành có đưa về app hay không. Trang gọi cùng API xác minh như `checkout-result-view.tsx:103-109` và không cần đăng nhập. Trang hiện kết quả đã xác minh theo `paymentState`, dùng các tiêu đề khách sẵn có (`checkout.result.guestSuccessHeading`, `guestFailedHeading`, `guestCheckingHeading`), không dùng các câu thân bài nhắc đăng nhập. Trang có câu nhắc "Quay lại ứng dụng Thi360 để xem bài thi của bạn." và nút "Mở ứng dụng Thi360" trỏ tới chính URL đang mở, không có nút đăng nhập web. Hai chuỗi mới cần thêm vào `vi.json` và `en.json` của web (đề xuất `checkout.result.app.returnHint`, `checkout.result.app.openApp`). Đây là trang thêm, không đổi trang web đang có (AC-151).
  - *Rủi ro đã biết:* theo tài liệu Apple, chạm vào liên kết cùng tên miền với trang Safari đang xem thì mở trong Safari, nên nút "Mở ứng dụng Thi360" có thể không mở app trên iOS. Đường chính trên iOS vẫn là phiên trình duyệt trao liên kết lại cho app. Khi cả hai đường không tới, học viên tự quay lại app và AC-150 vẫn hiện kết quả.
  - *Các cách đã loại:* cách A (khai `/checkout/result` vào tệp xác minh) làm mọi lần mua bằng trình duyệt điện thoại trên web bị chuyển vào app, tức là đổi hành vi web, việc đợt này không làm (Phạm vi). Cách B' (app gửi thêm trường `returnTarget`) phải đổi hai API và hợp đồng. Cách C (URL scheme `thi360://`) bị cổng giả lập từ chối (`MockVnpayController.java:273-276`) và ngược với test `apps/ios/Thi360Tests/Recovery/ResetLinkRoutingTests.swift:91`.
- Không cần API mới để app biết kết quả: app luôn đọc `GET /api/v1/me/orders/{code}`, và việc cấp bài do IPN (`VnpayIpnController.java:42-48`).

**Bài thi của tôi và Lịch sử**

- Không bắt buộc. Cả hai mục làm được với `GET /api/v1/me/exams`, `GET /api/v1/me/attempts`, `GET /api/v1/catalog/options` hiện có.
- Không làm trong đợt này (xem A-82): có thể thêm trường `examId` (UUID) vào `MyExamCard` để nút Lịch sử lọc được theo bài. Hiện thẻ chỉ có `code` (`MyExamCard.java:16-75`) trong khi `GET /api/v1/me/attempts` chỉ nhận `examId` dạng UUID (`AttemptResultController.java:40`), nên cả web lẫn app đều không lọc được (`my-exam-card.tsx:214`, `history-query.ts:46-49`). `MyExamsService` đã có `examId` của từng quyền (`MyExamsService.java:166-169`, `CardMeta.examId()` ở `:216`). Nếu làm, web nên đổi `my-exam-card.tsx:214` dùng trường mới; đó là thay đổi hành vi web, nằm ngoài phạm vi của đợt (mục "Ngoài phạm vi" của đặc tả), nên cần người yêu cầu đồng ý.

**Chuẩn bị, bắt đầu lượt thi và khung làm bài**

- Không cần đổi hành vi backend: mọi yêu cầu dùng endpoint đang có, đã nhận `Authorization: Bearer` như mọi đường `/api/v1/me/**`.
- Hợp đồng mobile phải thêm các đường ở bảng trên (`contracts/mobile/openapi-mobile.json` hiện chỉ có xác thực và hồ sơ). Khi thêm, sửa khai báo `Idempotency-Key` của `POST /api/v1/me/attempts` thành bắt buộc cho khớp backend (`shared/idempotency/IdempotentCommand.java:136,149-158`; `packages/api-client/openapi.json` đang ghi `required: false`).
- Kênh hỗ trợ ở AC-290, AC-299 trên web lấy từ biến môi trường build `NEXT_PUBLIC_SUPPORT_*` (`features/storefront/support.ts:11-17`), không có API; app cần cùng nguồn cấu hình với các phần khác dùng thông tin hỗ trợ (xem mục 6.1).

**Nội dung câu hỏi, các dạng câu và cách trả lời**

- **Không có thay đổi hành vi** của backend hay web: mọi yêu cầu của phần này dùng được endpoint hiện có (`AttemptAssetController.java:40-79`, `AttemptSolutionAssetController.java:37`, `AttemptAudioController.java:41-95`, `PUT /api/v1/me/attempts/{id}/answers`).
- **Thêm một test ở web, không đổi mã chạy:** test Vitest trong `packages/ui` đọc tệp công thức của bộ mẫu đối chiếu, dựng từng công thức bằng KaTeX 0.19.1 kèm mhchem theo đúng cách của `math-render.ts:52-60`, và so với cột "KaTeX vẽ được" trong tệp. Cần vì kết quả mong đợi của AC-464 phải lấy từ web, không ghi tay, và AC-467 cần báo đỏ khi KaTeX đổi bản.
- **Thêm endpoint vào hợp đồng mobile** như bảng trên; việc này thuộc R-3, ghi ở đây để không sót các endpoint nhị phân và audio.

**Kết quả và xem lại bài**

- Không có. App dùng nguyên các đường dẫn hiện có với `Authorization: Bearer`.

**Hồ sơ trong mục Tài khoản**

- Hợp đồng mobile: thêm 8 thao tác của AC-697 vào danh sách `orderedMobileOperations()` và bỏ dòng khẳng định `PATCH /api/v1/me/profile` không có trong bản mobile (`backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java:66,196-210`), xuất lại `contracts/mobile/openapi-mobile.json`, thêm tệp mẫu của AC-698. Lý do: client của hai app sinh từ tệp này (`apps/android/app/build.gradle.kts:13`), hôm nay chỉ có `GET /me/profile` nên app không gọi được các thao tác còn lại. Không đổi hành vi của endpoint nào.
- Nên sửa mô tả `ProfileUpdateRequest.fullName` từ "tối đa 100 ký tự" thành 200 cho khớp mã kiểm (`ProfileUpdateRequest.java:11` so với `ProfileService.java:44`), vì mô tả này đi vào client sinh cho app. Không chặn yêu cầu nào.
- Endpoint: không cần thay đổi. Bearer đã được nhận và miễn kiểm XSRF (ghi ở intake, `SecurityConfig.java:91,129-131`, `CsrfFilter.java:51-54,82-90`).

## 8. Dữ liệu và chuyển đổi

- Không đổi lược đồ cơ sở dữ liệu, trừ khi mục 7.1 nêu rõ.
- Trên máy người dùng có thêm: bản nháp câu trả lời của lượt thi đang làm (mục 6.4), lưu trong vùng lưu riêng của app; và dữ liệu danh sách đã tải dùng cho trạng thái mất mạng, chỉ giữ trong bộ nhớ và mất khi app bị tắt (A-30). Cả hai gắn với tài khoản: tài khoản nào chỉ đọc được dữ liệu của tài khoản đó; khi một tài khoản đăng xuất thì dữ liệu của tài khoản đó bị xóa (chi tiết ở mục 6.4).
- Người đã cài bản app đợt trước cập nhật lên bản này vẫn giữ phiên đăng nhập, lựa chọn ngôn ngữ, sáng/tối và trạng thái sinh trắc học.
- Máy iOS 17 không cài được bản mới; chưa có bản phát hành nào cho người dùng thật nên không có người dùng bị ảnh hưởng.
- Hoàn tác: các thay đổi backend và web là thêm mới; gỡ bản app mới không ảnh hưởng dữ liệu máy chủ vì lượt thi, đơn hàng và quyền thi dùng chung với web.

## 9. Yêu cầu phi chức năng

| Loại | Yêu cầu | Cách kiểm |
| --- | --- | --- |
| Bảo mật | Token, mật khẩu, mã OTP, mã voucher, tham số `vnp_*` và nội dung câu trả lời không vào log của bản `demo` | Test rà log như AC-85 của đợt trước, mở rộng cho các lời gọi mới |
| Bảo mật | Trang thanh toán VNPAY mở trong bề mặt trình duyệt của hệ điều hành; app không đọc, không lưu thông tin thẻ hay tài khoản ngân hàng | Rà mã và test giao diện của luồng thanh toán (mục 6.2) |
| Bảo mật | Bản nháp câu trả lời và bộ đệm trên máy không đọc được bằng tài khoản khác đăng nhập trên cùng máy | Test: tài khoản A có bản nháp và bộ đệm; tài khoản B đăng nhập trên cùng máy không thấy dữ liệu nào của A ở bất kỳ màn nào |
| Hiệu năng | Mở một phần thi 50 câu: từ lúc bấm tới lúc câu đầu tiên hiển thị đủ nội dung không quá 2 giây trên máy ảo tham chiếu (Pixel 7 API 34; iPhone 17), trung vị 5 lần, dữ liệu là bộ mẫu của mục 6.5 | Công cụ đo của từng nền tảng |
| Hiệu năng | Chọn một phương án: giao diện phản hồi ngay, không chờ máy chủ | Test với mạng bị làm chậm 3 giây: phương án hiện đã chọn trước khi lời gọi lưu hoàn tất |
| Hiệu năng | Cuộn danh mục và tờ đề không khựng thấy được: không quá 5% khung hình chậm trên máy ảo tham chiếu | Công cụ đo khung hình của từng nền tảng trên bộ mẫu của mục 6.5 |
| Truy cập | Vùng bấm tối thiểu 48dp (Android), 44pt (iOS); mọi điều khiển có nhãn cho TalkBack và VoiceOver; công thức có nhãn đọc được, tối thiểu là nguồn LaTeX | Kiểm tự động của bộ test giao diện; rà tay trên màn làm bài và màn thanh toán |
| Truy cập | Cỡ chữ hệ thống tới 130% không cắt hay chồng chữ; tới 200% mọi màn vẫn cuộn được và dùng được, kể cả tờ đề | Ảnh chụp màn ở hai mức cỡ chữ |
| Ngôn ngữ | Hai bản dịch cùng bộ khóa | AC-26 |
| Tương thích | Các kích thước của đợt trước: Android 360×640dp, 412×915dp, 840×720dp, 1280×800dp; iOS iPhone 390×844pt, iPad 1180×820pt và 820×1180pt, màn ngoài và màn trong iPhone Duo. Mỗi kích thước ở sáng và tối | Ảnh chụp màn tự động cho Trang chủ, chi tiết bài thi, thanh toán, Bài thi của tôi, màn làm bài, kết quả, xem lại; không tràn ngang, không cắt nội dung |
| Môi trường build iOS | Như đợt trước: mỗi bước build và test chạy lại được từ đầu trên máy `devin-mac` vừa thức dậy | Chạy bước đó ngay sau khi máy thức dậy |

## 10. Quyết định

Các quyết định của đợt khung và xác thực (D-1 đến D-36 trong đặc tả đợt đó) vẫn áp dụng, trừ chỗ bảng này ghi thay.

| ID | Quyết định | Ai, khi nào | Nguyên văn | Thay cho |
| --- | --- | --- | --- | --- |
| D-1 | Đợt 2 của app là port cổng thi của web học viên; nguồn là web đang chạy, không phải các PBI mobile | M.Trường, 2026-11-26 | "ý t là port phần cồng thi ấy. k phải check các PBI tiếp theo" | — |
| D-2 | Làm toàn bộ cổng thi trong một đợt | M.Trường, 2026-11-26 | "Làm hết trong một đợt" | — |
| D-3 | Mua trong app bằng VNPAY như web | M.Trường, 2026-11-26 | "VNPAY như web" | — |
| D-4 | Tạo issue cha mới cho đợt này (UNI-2820) | M.Trường, 2026-11-26 | "Tạo issue cha mới" | — |
| D-5 | Nội dung câu hỏi vẽ native; chốt hai bộ vẽ công thức; iOS tối thiểu 18; không đo thử nghiệm trước | M.Trường, 2026-11-26 | "ok chốt 2 bộ này, và nâng mức ios tối thiểu lên 18. tiếp tục làm nhé. k cần đo thử nghiệm" | D-11 của đợt trước (phần iOS 17) |
| D-6 | Hai bộ vẽ công thức là SwaTex cho iOS và RaTeX cho Android | M.Trường, 2026-11-26 | "1- đúng." | A-2 |
| D-7 | Bản thiết kế các màn mới làm ngay sau vòng rà soát đầu của đặc tả, duyệt trước khi duyệt kế hoạch | M.Trường, 2026-11-26 | "2- ok" | Q-1 |
| D-8 | Người yêu cầu duyệt danh sách công thức được miễn trừ. Bộ vẽ của app phải vẽ được các công thức thuộc chương trình cấp 3 và đại học; công thức chuyên sâu hơn nếu thiếu thì chưa cần | M.Trường, 2026-11-26 | "3- ok, chủ yếu là các công thức thuộc chương trình học cấp 3/đại học là được nhé. các công thức chuyên sâu hơn nếu thiếu có thể chưa cần" | A-76 |
| D-9 | Duyệt đặc tả bản 3 và bản thiết kế 80 màn mới như đã trình. Các điểm đã nêu khi xin duyệt: màn gập Android từ 840dp vẽ ba cột (ngữ liệu, câu hỏi, danh sách câu), nội dung chạy qua nếp gập; iPhone Duo mở đặt danh sách câu và ngữ liệu ở nửa trái, thanh đầu và câu hỏi ở nửa phải; dòng bài trong bộ đề có chip "Còn # lượt" và "Đang kiểm tra" (R-26, AC-740); màn xem lại trên điện thoại vẽ lưới câu thành một dải cuộn ngang; các câu chữ mới của A-86, A-87, A-89, A-92 và câu "chưa có kết quả" ở màn Kết quả thanh toán; các trạng thái chưa có bản vẽ làm theo tiêu chí của đặc tả | M.Trường, 2026-11-26 | "duyệt" | — |
| D-10 | Trên dải phần của màn làm bài: phần đã hoàn thành chỉ cần dấu tích xanh, không hiện chữ "Đã hoàn thành"; phần đang làm hiện tiến độ làm bài của phần đó thay cho chữ "Đang làm" (R-84, AC-340, AC-341). Khác web, nơi mỗi phần hiện nhãn trạng thái bằng chữ. Cùng lúc người yêu cầu nhắc lại: màn làm bài không có nút câu trước, câu sau; mọi câu của phần nằm trên một trang cuộn (đặc tả đã ghi vậy ở R-85) | M.Trường, 2026-11-26 | "chỗ Đã hoàn thành thì tích xanh là đủ rồi, đang làm thì hiện tiến trình đang làm là được" · "k dùng câu trước câu sau mà toàn bộ câu hỏi sẽ nằm trong cùng 1 page ấy" | — |
| D-11 | Logo, Điều kiện giao dịch chung và Chính sách bảo mật thông tin của app lấy theo tệp logo người yêu cầu gửi và hai trang của hsavnu.edu.vn; địa chỉ web trong nội dung đổi sang thi360.vn, tên đổi sang Thi360 | M.Trường, 2026-11-26 | "đây là logo, term/privacy cho app nhé. url trong nội dung thì đổi sang thi360.vn , tên HSA Edu... đổi sang Thi360" | — |
| D-12 | Nội dung hai văn bản được viết lại cho đúng dịch vụ Thi360, giữ bố cục các mục của hai trang gốc; không chép nguyên văn (trang gốc viết cho khóa học online) | M.Trường, 2026-11-26 | "Viết lại theo đúng Thi360" | — |
| D-13 | Thông tin đơn vị vận hành trong hai văn bản ghi đúng theo trang gốc: Thi360 là sản phẩm làm cho Công ty Cổ phần Đầu tư và Phát triển Giáo dục HSA | M.Trường, 2026-11-26 | "điền theo đúng các thông tin trong web đó. này là sản phẩm làm cho đơn vị này" | — |
| D-14 | Phạm vi: biểu tượng và logo mới cho hai app; hai trang công khai trên web học viên làm nguồn nội dung; app hiện nội dung ở màn Đăng ký và mục Tài khoản; logo của web giữ nguyên | M.Trường, 2026-11-26 | "App và trang web công khai" | — |
| D-15 | Tên hiển thị viết liền "Thi360" ở mọi nơi | M.Trường, 2026-11-26 | "Đổi hết sang \"Thi360\"" | D-10 của đợt trước (tên tạm "Thi 360") |
| D-16 | Thêm chức năng học viên tự xóa tài khoản vào đợt này | M.Trường, 2026-11-26 | "thêm xóa tài khoản ở phiên này luôn" | — |
| D-17 | Xác nhận xóa thì tài khoản bị đóng ngay, không khôi phục được; thông tin cá nhân bị xóa ngay | M.Trường, 2026-11-26 | "Đóng ngay, không khôi phục" | — |
| D-18 | Bài làm (lượt thi, câu trả lời, bài tự luận, điểm) được giữ nhưng gỡ danh tính; đơn hàng và thanh toán giữ theo luật kế toán | M.Trường, 2026-11-26 | "Giữ nhưng gỡ danh tính" | — |
| D-19 | Còn lượt thi chưa dùng thì cảnh báo rồi vẫn cho xóa, không hoàn tiền; đang có lượt thi làm dở hoặc đơn đã trả tiền đang kiểm tra thì chưa cho xóa | M.Trường, 2026-11-26 | "Cảnh báo rồi cho xóa, không hoàn tiền" | — |
| D-20 | Chức năng có ở hai app và web học viên, dùng chung một API | M.Trường, 2026-11-26 | "Hai app và web học viên" | — |
| D-21 | Giờ làm việc ghi trong Điều kiện giao dịch chung là 08h00–17h30 | M.Trường, 2026-11-26 | "thời gian thì để 17h30 nhé." | — |
| D-22 | Tiền VNPAY về sau khi tài khoản đã xóa: không cấp bài, đánh dấu đơn cần xem xét để vận hành hoàn tiền | M.Trường, 2026-11-26 | "Không cấp bài, chuyển hoàn tiền" | — |
| D-23 | Lệnh xóa dọn thêm bản ghi đăng ký ban đầu và gỡ thông tin trong dòng nhập học viên từ tệp; nhật ký hệ thống và thư đã gửi được giữ, màn xóa và Chính sách bảo mật thông tin nói rõ điều đó | M.Trường, 2026-11-26 | "Dọn 2 nơi, nói rõ phần còn giữ" | — |
| D-24 | Có bài đang chờ chấm thì không chặn việc xóa, chỉ thêm một câu cảnh báo | M.Trường, 2026-11-26 | "Không chặn, thêm một câu cảnh báo" | — |
| D-25 | Đổi email trong app làm theo đặc tả: biểu mẫu mở ngay trong màn Hồ sơ như web; bản vẽ `ProfileEmail` (màn riêng) không dùng | M.Trường, 2026-11-26 | "1-ok" | — |
| D-26 | Tên hiển thị "Thi360" áp cho cả web admin và backend; riêng tên sản phẩm trong thư của backend lấy từ biến môi trường, không ghi cứng | M.Trường, 2026-11-26 | "2-cái email thì đặt env đi" | — |
| D-27 | Bộ mẫu công thức không lấy từ cơ sở dữ liệu demo (dữ liệu ở đó do agent tạo để thử); thay bằng danh mục công thức dựng từ nghiên cứu chương trình học và tài liệu của KaTeX, mhchem | M.Trường, 2026-11-26 | "3-csdl demo cungz toàn là agent thêm dữ liệu test mà, nên phần này cần research để ra danh sách dđây đủ hơn" | Nguồn 2 của R-117 ở bản 8 |
| D-28 | Màn xem lại bài giữ hai nút "Câu trước" và "Câu sau" | M.Trường, 2026-11-26 | "4-giữ cungz được" | — |
| D-29 | Ô của câu được đánh dấu trong bảng câu hỏi tô màu đỏ như web, không dùng cờ màu hổ phách của bản vẽ | M.Trường, 2026-11-26 | "5-như web" | Bản vẽ bảng câu hỏi ở D-9 (riêng màu ô đánh dấu) |
| D-30 | Tự xóa tài khoản: chỉ tài khoản học viên; đơn đang chờ thanh toán cũng chặn việc xóa; hai màn `A-DeleteAccount`, `I-DeleteAccount` được duyệt | M.Trường, 2026-11-26 | "6-ok" | — |
| D-31 | Các câu chữ mới của giả định A-118 được dùng; màn khóa của app chỉ hiện tên mới "Thi360" | M.Trường, 2026-11-26 | "7-ok" | — |
| D-32 | Hai văn bản phát hành như đang có, không ghi là bản nháp | M.Trường, 2026-11-26 | "2 văn bản k cần ghi bản nháp, cứ làm như thế đi" | — |
| D-33 | Web admin hiện lý do tự xóa tài khoản bằng nhãn tiếng Việt thay cho mã `SELF_DELETED` | M.Trường, 2026-11-26 | "Thêm nhãn tiếng việt khi self del" | — |
| D-34 | Duyệt kế hoạch bản 8 cùng đặc tả bản 9. Xác nhận: tên sản phẩm trong thư của backend lấy từ biến môi trường (cách hiểu của D-26); ngày có hiệu lực 26/11/2026 của hai văn bản; tối đa hai lane iOS trên `devin-mac` khi phép đo đạt | M.Trường, 2026-11-26 | "Duyệt. Cách hiểu 2 đúng. Ngày hiệu lực để như thế cungz được. Quy tắc ios cứ vậy đi" | — |
| D-35 | Nội dung hai văn bản lấy theo bản đã duyệt ở `docs/legal/`; hai trang văn bản mà UNI-2828 vừa thêm vào web được thay nội dung bằng bản này | M.Trường, 2026-11-26 | "Lấy theo bản plan duyệt lần này nhé" | Nội dung hai trang của UNI-2828 |

## 11. Giả định cần xác nhận

| ID | Giả định | Vì sao an toàn | Đã xác nhận |
| --- | --- | --- | --- |
| A-1 | Trang chủ của app là danh mục bài thi; Bài thi của tôi là `/home` của web; Lịch sử là `/history`; Hồ sơ và Đơn hàng nằm trong Tài khoản | Giữ bốn mục đã chốt ở đợt trước (D-7 đợt trước) và khớp cách web chia | Chưa |
| A-2 | "2 bộ này" ở D-5 là SwaTex cho iOS và RaTeX cho Android | Hai bộ cùng một họ (SwaTex là bản port của RaTeX) và việc nâng iOS lên 18 là điều kiện của SwaTex | Đã xác nhận 2026-11-26 (D-6) |
| A-3 | App không hiện các khối giới thiệu và thông tin liên hệ hỗ trợ của trang `/` | Chúng không phải chức năng của cổng thi; liên hệ hỗ trợ trên web lấy từ biến môi trường, không có API | Chưa |
| A-4 | Trình soạn của admin không đổi. Công thức KaTeX vẽ được mà bộ vẽ của app không vẽ được thì app hiện phương án dự phòng nêu ở mục 6.5 | D-5 chốt vẽ native và không đo trước; đổi trình soạn là việc của web admin, ngoài phạm vi | Chưa |
| A-5 | App không chặn chụp màn hình và không phát hiện rời app trong lúc làm bài | Web không có biện pháp nào tương tự | Chưa |
| A-6 | Màn khóa của app vẫn hiện sau 5 phút rời app kể cả khi đang làm bài; đồng hồ bài thi do máy chủ tính nên vẫn chạy | Giữ quy tắc D-16 của đợt trước; thời gian thi vốn do máy chủ quyết định | Chưa |
| A-7 | Kiểm thử iOS chạy trên các runtime đang có ở `devin-mac` (iOS 26.5, 27.1). Kiểm trên iOS 18.x chuyển vào danh sách việc sau merge nếu máy không đủ chỗ cài thêm runtime | `devin-mac` còn rất ít đĩa trống (ghi nhận 2026-11-25) | Chưa |
| A-8 | Danh mục của app là danh sách dọc tải dần 12 thẻ một lần (R-16), thay cho hàng cuộn ngang và số trang của web; dòng "Hiển thị {from}–{to} …" dùng `from = 1`. Chỉ dẫn "Vuốt hoặc kéo ngang để xem thêm" và các nút cuộn trái/phải không có trong app. | Cùng endpoint, cùng cỡ trang 12 (`catalog-query.ts:7`); chỉ đổi cách trình bày, không đổi dữ liệu hay luật. | Chưa |
| A-9 | Bộ lọc và vị trí cuộn chỉ giữ trong lúc app còn chạy; tắt hẳn app rồi mở lại thì về bộ lọc mặc định. | Web giữ bộ lọc trên URL (`catalog-query.ts:1-4`); app không có URL, mở lại từ đầu là hành vi thông thường của app và không mất dữ liệu nghiệp vụ. | Chưa |
| A-10 | Bấm phím tìm trên bàn phím áp dụng ô tìm ngay, không chờ 400 ms. | Kết quả giống hệt chờ 400 ms; chỉ sớm hơn. | Chưa |
| A-11 | Đường dẫn vị trí trang (Trang chủ / Chọn bài thi / tên) của web thay bằng nút quay lại của nền tảng; tiêu đề thanh trên của màn chi tiết là tên sản phẩm. | Đường dẫn vị trí là điều hướng của web (`exam-detail-view.tsx:158-176`); app có ngăn xếp màn riêng. | Chưa |
| A-12 | Câu `storefront.detail.policy.refundPending` nhắc "khối Bạn cần hỗ trợ trên trang chủ", khối này không có trong app. App dùng câu mới "theo chính sách do đơn vị vận hành công bố; cần hỗ trợ, hãy liên hệ qua các kênh ghi ngay bên dưới." (en: "per the policy published by the operating unit; if you need help, contact support through the channels listed below."). | Dòng kênh hỗ trợ luôn nằm ngay dưới dòng này trong cùng khối (`exam-detail-view.tsx:434-439`). | Chưa |
| A-13 | Ở hai cột, cột phải khi chưa chọn sản phẩm hiện câu mới "Chọn một bài thi để xem chi tiết." (en: "Select an exam to see its details."). | Web không có bố cục hai cột cho danh mục; câu chỉ là chỗ trống, không mang luật. | Chưa |
| A-14 | Ảnh bìa lỗi thì hiện bìa mặc định (AC-66). | Web khi đó hiện ảnh vỡ (`product-card.tsx:172`); bìa mặc định là thứ web hiện khi không có ảnh, nên không thêm nội dung mới. | Chưa |
| A-15 | Kéo xuống ở đầu danh mục và màn chi tiết để tải lại (R-23); kéo ở danh mục thành công thì thay cả danh sách bằng trang đầu, bỏ các trang đã tải thêm; quay về màn thì chỉ hỏi lại quyền. | Web tải lại khi điều hướng; trên app, quyền là phần đổi theo thao tác của học viên (mua, làm bài), còn danh mục có cache 60 giây ở API (`StorefrontPublicController.java:41`). | Chưa |
| A-16 | App gửi `Authorization: Bearer` cả với endpoint công khai như mọi lời gọi; 401 đi theo luồng làm mới phiên chung của app. | Quyết định của đợt khung và xác thực (app gọi backend trực tiếp bằng Bearer); một luồng cho mọi lời gọi tránh trường hợp riêng. | Chưa |
| A-17 | App không mở chi tiết từ liên kết ngoài (deep link `/exams/{code}`); chi tiết chỉ mở từ thẻ hoặc từ dòng bài trong bộ. | Deep link hiện chỉ có `/reset-password`; thêm đường dẫn mới không nằm trong phạm vi phần này. | Chưa |
| A-18 | Liên kết trả về phải qua màn khóa và cần phiên, khác liên kết đặt lại mật khẩu (bỏ qua khóa, `AppNavHost.kt:350`). | Màn Kết quả thanh toán hiện dữ liệu đơn của tài khoản; quy tắc khóa 5 phút của quyết định của đợt khung và xác thực vẫn giữ. | Chưa |
| A-19 | Đơn đang chờ kết quả được lưu trên máy (chỉ mã đơn và thời điểm, không có token) để sống qua lần app bị tắt hẳn; hết hiệu lực sau 15 phút. | 15 phút là hạn của một lần thanh toán VNPAY và hạn giữ giá (`OrderService.java:91,656-659`, `application.yml:225`); quá hạn đó đơn không thể thành công qua lần thanh toán ấy nữa. Mã đơn không phải bí mật: mọi API đơn đều kiểm chủ sở hữu (`OrderService.java:247-252,588-590`). | Chưa |
| A-20 | `paymentUrl` tương đối được ghép với địa chỉ backend của bản build. | Chỉ xảy ra ở chế độ giả lập khi chưa đặt `mockPayUrl` (`MockVnpayGateway.java:64`); trang `/mock-vnpay` do backend phục vụ (`MockVnpayController.java:65`). | Chưa |
| A-21 | Danh sách đơn dùng cuộn tải thêm 20 đơn mỗi lần thay cho thanh số trang của web. | Cùng API, cùng cỡ trang (`api.ts:12`); chỉ khác cách hiện trên điện thoại. | Chưa |
| A-22 | Hỏi lại ngừng khi app vào nền và chạy tiếp khi quay lại, mốc 60 giây tính từ lúc bắt đầu. | Web dùng hẹn giờ trình duyệt (`checkout-result-view.tsx:181-204`); app không được gọi mạng ngầm, và đọc ngay khi quay lại cho kết quả mới nhất. | Chưa |
| A-23 | Nút "Đăng nhập bằng tài khoản khác" (màn mua, màn kết quả, chi tiết đơn) trong app là đăng xuất rồi về màn Đăng nhập; sau khi đăng nhập app về mục Trang chủ. | App chỉ có một phiên; web làm việc này bằng `/login?callbackUrl` (`checkout-view.tsx:841-850`), app không có trang đăng nhập kèm đích quay về. | Chưa |
| A-24 | Các nhánh dành cho khách và quản trị của web (`checkout-result-view.tsx:363-484`, `orders-view.tsx:358-372`) không có trong app. | Quyết định của đợt khung và xác thực: app luôn cần đăng nhập và chỉ học viên dùng. | Chưa |
| A-25 | Khi Apple Team ID chưa có (việc còn mở của đợt trước), hệ điều hành không mở app từ Liên kết trả về trên iOS; phiên trình duyệt của R-39 và R-41 vẫn cho học viên thấy kết quả, còn AC-706, AC-708 chờ kiểm trên máy thật sau khi merge. | D-3 yêu cầu app tự xử lý khi liên kết không mở app. | Chưa |
| A-26 | Danh sách Bài thi của tôi và trang lịch sử được tải lại mỗi lần mục được mở hoặc quay lại (AC-194, R-58), không tự tải lại khi app trở lại từ nền. | Web gọi lại khi vào lại trang (`staleTime` mặc định 0) và tắt tải lại khi cửa sổ nhận tiêu điểm (`query-provider.tsx:72`). | Chưa |
| A-27 | Bài thi của tôi có trạng thái mất mạng riêng (AC-201) dùng `common.offline.*`. | Web không có trạng thái này ở `/home`; Lịch sử đã có và dùng cùng khóa (`history-view.tsx:529-548`), nên câu chữ không mới. | Chưa |
| A-28 | Lịch sử nạp thêm khi cuộn thay cho thanh số trang (R-69). | Cùng API, cùng `size = 20`; trên điện thoại một danh sách cuộn dễ dùng hơn số trang. Trạng thái "Trang này chưa có dữ liệu" (`history.empty.outOfRange*`) của web chỉ đến được qua tham số `page` trên URL, app không có nên không dùng. Xem A-83. | Chưa |
| A-29 | Bộ lọc Lịch sử sống trong bộ nhớ của app (R-65): giữ khi chuyển mục, mất khi tắt hẳn app. | Web giữ bộ lọc trên URL; app không có URL, đây là tương đương gần nhất. | Chưa |
| A-30 | Dữ liệu cũ không lưu xuống máy (AC-202). | Mục "Ngoài phạm vi" của đặc tả loại chế độ ngoại tuyến đầy đủ; web cũng chỉ giữ trong bộ nhớ trang. | Chưa |
| A-31 | 403 ở hai mục hiện khối lỗi và kích hoạt làm mới phiên (AC-273), không có màn "403" riêng. | Web chuyển sang `/forbidden` kèm thông báo `errors.forbiddenToast`; app đã có màn "chỉ dành cho học viên" và luồng phiên của đặc tả khung cho đúng các trường hợp này. | Chưa |
| A-32 | Lịch sử hiện mỗi dòng dạng thẻ ở mọi bề rộng, không dựng bảng. | Web đổi sang thẻ dưới 760 px (`history-view.tsx:73,285-288`); hai dạng có cùng thông tin. Mục không có cặp danh sách–chi tiết. | Chưa |
| A-33 | Nút "Khám phá bài thi" và nút "Bài thi của tôi" ở trạng thái rỗng chuyển mục trong app thay vì mở `/#exams`, `/home`. | Giả định A-1 gán `/` (danh mục) cho Trang chủ và `/home` cho Bài thi của tôi. | Chưa |
| A-34 | Bản nháp, vị trí, `sessionId`, ngữ cảnh bắt đầu lưu trong bộ nhớ riêng của app, theo khóa `userId:attemptId[:itemId]`, không vào bản sao lưu của hệ điều hành | Cùng khóa với web (`draft-store.ts:21-27`, `edit-session-storage.ts:8-10`); quy tắc xóa như web trừ hai điểm ghi ở A-107 (lượt `ABORTED`) và A-108 (đăng xuất); không chứa mật khẩu hay token | Chưa |
| A-35 | App chỉ chạy một cửa sổ (không bật nhiều cửa sổ trên iPad, không nhiều phiên bản trên Android), nên không có trạng thái "Chỉ xem" theo tab và không dùng các khóa `attempt.tabLock.*` | Khóa tab của web chỉ chống hai tab cùng trình duyệt (`edit-session.ts:47-221`); khác máy đã có phiên sửa của máy chủ lo | Chưa |
| A-36 | App không có phím tắt và không hiện `attempt.player.shortcutHint` | Phím tắt cần bàn phím cứng (`attempt-player.tsx:1430-1513`); mọi thao tác đều có nút chạm | Chưa |
| A-37 | Bảng câu hỏi là cột cố định ở màn rộng (Android từ 840dp, iOS regular) và tấm phủ ở màn hẹp | Web đổi ở 1024 px (`question-nav.tsx:322,354`); ngưỡng app theo quyết định của đợt khung và xác thực | Chưa |
| A-38 | "Có mạng" lấy theo tín hiệu kết nối của hệ điều hành, cộng với lỗi kết nối của lời gọi như web (`save-queue.ts:832-837`) | Tương đương sự kiện `online`/`offline` của trình duyệt | Chưa |
| A-39 | Đồng hồ đếm ngược dùng đồng hồ đơn điệu của máy giữa hai lần neo, và neo lại mỗi lần lên từ nền | Tương đương `performance.now()` của web (`server-clock.ts:6-11`); khi máy ngủ đồng hồ đơn điệu có thể dừng, lần neo lại sau khi lên sửa sai lệch | Chưa |
| A-40 | Gửi ngay khi xuống nền, làm mới khi lên lại (R-99), không gửi nhịp ở nền | Web không có bước này; thêm vào chỉ giảm rủi ro mất bài. Không nhịp ở nền không làm mất phiên vì phiên không tự hết hạn (`EditSessionService.java:139-141`) | Chưa |
| A-41 | Hệ điều hành tắt app ở nền thì mở lại vào màn làm bài; người dùng tự tắt thì mở ở màn đầu (R-101); app dựa hoàn toàn vào cơ chế khôi phục trạng thái mặc định của hệ điều hành, không tự xây cách phân biệt (Android API 26–29 không có `ApplicationExitInfo`, iOS không có API tương đương) | Khớp thói quen của từng hệ điều hành; dữ liệu cần để làm tiếp đều nằm trên máy và máy chủ, nên mở nhầm ở màn đầu chỉ tốn một lần bấm "Tiếp tục" | Chưa |
| A-42 | Cử chỉ quay lại rời màn làm bài không nộp bài; chỉ hỏi khi còn thay đổi chưa lưu (R-103) | Tương đương web: rời trang không nộp bài, trình duyệt chỉ hỏi khi còn chưa lưu (`attempt-player.tsx:747-751`) | Chưa |
| A-43 | Bấm "Hủy" ở hộp thoại tiếp quản đưa về mục Bài thi của tôi | Web đưa về `/home` (`attempt-player.tsx:2493-2495`), là mục này theo cách gán mục điều hướng của đặc tả (A-1) | Chưa |
| A-44 | Mã 429 cho bắt đầu lượt, chọn phần, thử lại sinh phần được xử lý như web dù backend hiện không trả | Web đã xử lý (`prepare-view.tsx:106-107`, `attempt-player.tsx:1822-1824`); giữ để không vỡ khi backend thêm giới hạn | Chưa |
| A-45 | Thanh trên thu gọn được như web | Có sẵn trên web (`attempt-player.tsx:2195-2207,2336-2353`) và có ích trên điện thoại | Chưa |
| A-46 | Bộ mẫu đối chiếu nằm ở `contracts/mobile/rich-text/`; tệp công thức có hai nguồn của R-117 (danh mục 225 nhóm, 1.915 công thức vẽ được, 93 công thức KaTeX từ chối; mọi công thức mẫu có sẵn) cộng 43 mẫu HTML và 11 câu có sẵn. Khối lượng so ảnh bằng mắt: 54 mẫu × 2 nền tảng × 2 chế độ = 216 cặp, cộng 226 tờ ảnh công thức cho mỗi nền tảng (mỗi tờ một nhóm) ở chế độ sáng; phần so ảnh công thức cần nhiều buổi và chia được cho nhiều người rà soát theo nhóm | Cùng chỗ với các tệp hợp đồng mobile khác (`contracts/mobile/api`, `appearance`); danh mục phủ chương trình học mà D-8 đặt làm mức phải đạt, dạng đề của các kì thi và mọi mục lệnh của KaTeX, mhchem, thay cho dữ liệu thử của môi trường demo (D-27); việc kiểm "vẽ được, không rơi về khung cảnh báo" của mọi công thức là tự động, so ảnh công thức chỉ bắt lỗi bố cục. Danh mục mới được kiểm bằng máy là KaTeX vẽ không lỗi; chưa ai so hình bằng mắt, và cách viết công thức theo sách giáo khoa dựa trên hiểu biết của người dựng danh mục (sách không tra được trực tuyến) | Chưa |
| A-47 | Bảng và công thức rộng cuộn ngang trong khung riêng; công thức dòng không bị ngắt ra nhiều dòng (web chỉ co bảng, không ngắt công thức dòng và để công thức tràn) | Theo quy tắc chung "Bảng rộng hơn khung" của `DESIGN.md:245`; không đổi nội dung, chỉ đổi cách xem trên màn hẹp | Chưa |
| A-48 | Cỡ chữ hệ thống được hỗ trợ tới 200 % không cắt chữ | Web dùng đơn vị `rem` và co giãn theo trình duyệt; 200 % là mức WCAG 1.4.4 yêu cầu | Chưa |
| A-49 | Ảnh, âm thanh và tệp đính kèm của lượt thi chỉ giữ trong bộ nhớ, hoặc là tệp tạm trong thư mục bộ đệm riêng của app khi trình xem tệp hay trình phát của hệ điều hành cần tệp trên đĩa, và bị xóa theo R-177 | Máy chủ trả `Cache-Control: private, no-store` cho mọi tài nguyên của lượt (`AttemptAssetController.java:76`); trình xem tệp của Android và iOS chỉ mở được tệp trên đĩa, nên tệp tạm có hạn xóa là cách gần nhất với `no-store` | Chưa |
| A-50 | Màu đầu bảng lấy `tableHead` của `packages/ui/src/theme/scale.ts` (sáng `#fafafb`, tối `#1a2126`) và được kiểm như R-6 | Màu cố định, không đổi theo bộ màu; R-6 chưa liệt kê màu này | Chưa |
| A-51 | Khi app vào nền hoặc bị cuộc gọi chen, âm thanh dừng và học viên bấm "Phát lại" để nghe tiếp | Máy chủ cho phát tiếp không mất lượt (`AttemptAudioService.java:135-156`); app không cần quyền phát ở nền; web không có hành vi tương ứng để chép | Chưa |
| A-52 | Ghép đôi trên app chỉ có cách chọn bằng danh sách (không có cách bấm hai lượt) | Màn làm bài web chỉ dùng `select` (`attempt-player.tsx:2129-2182`); cách `pick` chỉ có ở trang `/dev/question-kit`, ngoài phạm vi | Chưa |
| A-53 | Tệp đính kèm không phải audio mở bằng trình xem của hệ điều hành | Web mở ở tab mới (`attempt-player.tsx:2110-2122`); trình xem hệ điều hành là cách tương đương trên app và không rời lượt thi | Chưa |
| A-54 | Dán vào Tự luận giữ đậm, nghiêng, danh sách | Trình soạn web dựng chữ dán theo đúng bộ định dạng được bật (`tiptap-essay-editor.tsx:142-154`) | Chưa |
| A-55 | Với `INTEGER`, `DECIMAL`, `FRACTION`, app mở bàn phím số của hệ điều hành và thêm hàng 4 phím "-", "/", ",", "." dưới ô (R-122), vì không bàn phím số hệ thống nào của iOS hay Android có đủ bốn ký tự này trên một mặt phím | `FRACTION` cần "/", số âm cần "-"; web chỉ đặt `inputMode="decimal"` (`short-answer.tsx:71-75`) và gợi ý "0-9 · ." chỉ dẫn cách viết, không giới hạn ký tự (`short-answer.tsx:141-154`); hàng phím chỉ chèn ký tự, không đổi giá trị gửi hay cách chấm | Chưa |
| A-56 | Chữ đang ghép của bộ gõ không bao giờ được gửi; mức chờ tối đa 15 giây của nhịp lưu Tự luận chỉ tính trên chữ đã chốt (R-131) | Gửi chữ chưa chốt sẽ tạo chuỗi sai rồi ghi đè; chữ đã chốt vẫn được gửi đúng hạn, cụm đang ghép chỉ trễ tới lần gửi kế tiếp; câu trả lời cuối vẫn là chữ đã chốt | Chưa |
| A-57 | App vẽ các bảng phân đoạn, môn và tiêu chí ở dạng danh sách thẻ ở mọi bề ngang, giống dạng web dùng khi khung hẹp hơn 680px. | Web đã có sẵn dạng thẻ cho cùng dữ liệu (`result-view.tsx:325-370`, `segment-breakdown-section.tsx:81-128`). Màn Kết quả không có cặp danh sách và chi tiết nên không cần hai ô. | Chưa |
| A-58 | Nút quay lại của app về màn trước trong ngăn; không cố định về Lịch sử như liên kết của web. | Web có liên kết cố định vì trình duyệt không có ngăn màn theo mục. Nút "Lịch sử luyện thi" vẫn có trong màn (R-155). | Chưa |
| A-59 | Khi bấm "Xem kết quả", màn Kết quả thay màn trình làm bài trong ngăn. | Lượt đã đóng không sửa được nữa, nên quay lại trình làm bài không có ích (`attempt-player.tsx:1639-1655`). | Chưa |
| A-60 | Khi app ở nền thì ngừng hỏi lại lúc đang chấm, nhưng mốc 120 s vẫn tính theo thời gian thực. | Web cũng ngừng hỏi khi tab ẩn (mặc định của thư viện truy vấn) và giữ nguyên bộ đếm 120 s (`result-view.tsx:519-527`). | Chưa |
| A-61 | Phần đang chọn ở màn Xem lại chỉ nằm trong trạng thái của màn, không lưu lại khi rời màn. | Web giữ phần trong `?part=` của địa chỉ trang. App không có địa chỉ trang, và không có liên kết sâu nào tới màn Xem lại. | Chưa |
| A-62 | Dữ liệu danh mục `EXAM` và `SUBJECT` được giữ trong bộ nhớ 5 phút cho cả phiên app. | Web cũng giữ 5 phút (`result-view.tsx:489,496`). Khi lỗi thì màn hiện mã, không chặn màn. | Chưa |
| A-63 | Dòng "Hồ sơ" và "Đơn của tôi" nằm ngay dưới thẻ tài khoản, không có tiêu đề nhóm; bấm thẻ cũng mở Hồ sơ. | Dùng nhãn và thứ tự có sẵn của menu tài khoản web (`student-header.tsx:23-28`); không thêm chữ mới. | Chưa |
| A-64 | Màn Hồ sơ của app không lặp khối "Mật khẩu" của web. | Dòng "Đổi mật khẩu" đã có trong danh sách Tài khoản ngay cạnh; khối web chỉ là một liên kết (`profile-view.tsx:176-186`). | Chưa |
| A-65 | Ở hai cột, ngăn chi tiết mặc định là Hồ sơ. | Hồ sơ là phần chính mới của mục; ngăn khác vẫn chọn được bằng một lần bấm. | Chưa |
| A-66 | Màn Hồ sơ tách lỗi máy chủ và mất mạng, dùng câu `common.offline` cho mất mạng. | Thẻ tài khoản hôm nay đã tách hai trạng thái này (`AccountScreen.kt:621-637`); câu lấy từ `vi.json`, không viết mới. | Chưa |
| A-67 | App tự đổi ảnh đã chọn sang JPEG (hoặc giữ PNG/WebP), xoay đúng chiều, thu cạnh dài nhất về ≤ 4.096 px và nén tới ≤ 5 MB trước khi gửi; vì vậy câu có tên tệp `TOO_LARGE_FILE` không dùng. | Ảnh máy ảnh của điện thoại thường là HEIC và có thể lớn hơn 4.096 px, máy chủ sẽ từ chối (`AvatarImageProcessor.java:42-61,103-114`); máy chủ vẫn cắt vuông và thu về 256 px nên kết quả trên máy chủ như web. Các câu lỗi máy chủ vẫn giữ cho trường hợp còn sót. | Chưa |
| A-68 | Chuỗi riêng của app, chưa có trên web, cần cả tiếng Việt và tiếng Anh: "Chọn từ thư viện", "Chụp ảnh", câu báo thiếu quyền máy ảnh kèm nút mở Cài đặt, câu giải thích quyền máy ảnh của iOS. Câu đề xuất: "Chọn từ thư viện", "Chụp ảnh", "Thi360 chưa được dùng máy ảnh. Bật quyền trong Cài đặt để chụp ảnh đại diện.", "Mở Cài đặt", "Thi360 dùng máy ảnh để chụp ảnh đại diện của bạn." | Web dùng ô chọn tệp của trình duyệt nên không có các câu này; câu ngắn, không nhắc tới web. | Chưa |
| A-69 | Ảnh đại diện đang tải hoặc tải lỗi thì hiện chữ cái đầu tên, không có câu lỗi. | Web không xử lý ảnh lỗi (`profile-view.tsx:256-264`); 404 là trường hợp bình thường khi ảnh vừa bị thay ở máy khác (`AvatarService.java:128-133`). | Chưa |
| A-70 | Bộ đệm ảnh của app theo `assetId` và bị xóa khi đăng xuất. | Mỗi lần thay ảnh có `assetId` mới (`AvatarService.java:84`), nên ảnh cũ không bị hiện nhầm; xóa khi đăng xuất theo AC-70 của đặc tả khung. | Chưa |
| A-71 | Thời gian chờ gửi mã chỉ sống trong màn đang mở; rời màn thì mất, máy chủ vẫn chặn bằng 429. | Web làm đúng như vậy (`cooldown.ts:22-43` là trạng thái của trang); máy chủ là nơi giữ giới hạn thật (`EmailCodeSendPolicy.java:25-37`). | Chưa |
| A-72 | Xác minh thành công thì về màn Hồ sơ, thay cho `callbackUrl` của web. | Trong app, nút "Xác minh email" chỉ có ở màn Hồ sơ; web cũng đặt `callbackUrl=/account/profile` cho nút này (`profile-view.tsx:166`). | Chưa |
| A-73 | Thông báo nổi của web (`notify.success`) là Snackbar trên Android và thông báo nổi tự ẩn trên iOS, cùng câu chữ. | Thành phần gốc theo nền tảng (quyết định của đợt khung và xác thực, `DESIGN.md` mục 11.1). | Chưa |
| A-74 | Rời màn Hồ sơ khi biểu mẫu đang sửa dở thì không hỏi lại; giá trị đang gõ bị bỏ. | Web không có cảnh báo rời trang ở đây. | Chưa |
| A-75 | Chọn ảnh từ thư viện dùng trình chọn ảnh của hệ điều hành, không xin quyền đọc toàn bộ thư viện. | App chỉ cần một ảnh; trình chọn của hệ điều hành trả đúng ảnh được chọn. | Chưa |
| A-76 | Web lấy số điện thoại, email và giờ hỗ trợ từ biến môi trường lúc build (`support.ts:11-17`), không có API. App lấy ở đâu? **Chọn:** Đặt trong cấu hình build của app theo môi trường (`dev`, `demo`), cùng giá trị với web của môi trường đó; trống thì hiện "Thông tin liên hệ đang được cập nhật." như web. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-77 | `exam-access/query` trả 403 khi tài khoản không còn là học viên `ACTIVE` (`ExamLearnerGate.java:31-41`). Web hiện câu dành cho quản trị viên (`storefront.access.adminNote`), câu này sai với học viên. App làm gì? **Chọn:** App làm mới phiên: nếu bị từ chối `APP_STUDENT_ONLY` thì hiện màn "Ứng dụng này dành cho học viên" của bản khung; nếu 401 thì về Đăng nhập; nếu làm mới được mà vẫn 403 thì hiện lỗi quyền của AC-72. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-78 | Backend trả 404 cho bộ đề `UNAVAILABLE` (`StorefrontQueryService.java:219-220`), nên màn "Bộ đề tạm thời chưa bán được" của web không bao giờ hiện. Có muốn backend trả 409 `PRODUCT_UNAVAILABLE` kèm chi tiết như web đang chờ không? **Chọn:** Giữ backend như hiện nay trong đợt này; app vẫn xử lý 409 như R-30 để khớp web khi backend đổi. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-79 | Web không có trạng thái "quay lại mà chưa có kết quả" (học viên đóng Trang thanh toán khi chưa trả tiền). Câu `checkout.result.checkingTimeoutBody` của web nói "bạn không cần thanh toán lại", sai cho trường hợp này. Dùng câu chữ nào? **Chọn:** Thêm chuỗi riêng của app: tiêu đề "Chưa nhận được kết quả thanh toán"; nội dung "Nếu bạn đã thanh toán xong trên VNPAY, trạng thái đơn {code} sẽ cập nhật trong giây lát. Nếu bạn đã đóng trang thanh toán, đơn vẫn giữ giá tới {time}."; nút "Tiếp tục thanh toán" và "Xem chi tiết đơn". | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-80 | Đã bỏ (vòng 1): tác giả chốt cách B, đường dẫn `/checkout/result/app` chọn theo kênh phiên của từng lần thanh toán. **Chọn:** Cách B: không đổi API, không đổi hành vi web, chỉ thêm một trang web mới. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-81 | Đã bỏ (vòng 1): tác giả chốt nội dung Trang dự phòng (AC-151). **Chọn:** Kết quả đã xác minh như các nhánh khách của web nhưng bỏ nút đăng nhập, thêm câu "Quay lại ứng dụng Thi360 để xem bài thi của bạn." | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-82 | Nút Lịch sử trên thẻ có cần lọc theo bài không? Web hiện mở lịch sử không lọc vì thẻ không có id bài thi. **Chọn:** Trong đợt này app làm như web đang làm trên thực tế (AC-250): mở mục Lịch sử với bộ lọc rỗng, không lọc theo bài, không gửi `examId`. Muốn lọc được thì phải thêm `examId` vào `MyExamCard` và sửa web, là thay đổi hành vi web nên ghi ở Phụ lục A để làm ở việc khác. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-83 | Lịch sử trên app dùng nạp thêm khi cuộn hay giữ thanh số trang như web? **Chọn:** Nạp thêm khi cuộn (A-28). | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-84 | Ở bố cục hai nửa (iPhone Duo mở, Android từ 840dp) hai mục này không có chi tiết; nửa phải hiện gì? **Chọn:** Danh sách dùng cả bề rộng như iPad (thẻ xếp nhiều cột); nếu khung bắt buộc hai nửa thì nửa phải để trống thay vì màn giữ chỗ. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-85 | Nhận phiên sửa lỗi mạng hoặc 5xx: web không thử lại và không báo gì, nên học viên sửa bài mà không lưu được tới khi tải lại trang (`edit-session.ts:364-365`, `attempt-player.tsx:1116-1123`). App làm gì? **Chọn:** App thử nhận phiên lại theo nấc lùi của lưu bài (1, 2, 4, 8, 16, 30 giây rồi giữ 30 giây) và ngay khi có mạng lại, luôn với cùng `sessionId` do app tự sinh và lưu trước lần gọi đầu (AC-314), nên lần thử lại sau một lần nhận đã thành công mà mất phản hồi là nối lại, không gây 409; trong lúc đó ô trạng thái hiện "Chưa lưu · Lỗi kết nối", "Chưa lưu · Máy chủ bận" hoặc "Chưa lưu · Mất kết nối"; chưa có phiên thì app không gửi câu trả lời, không cho hoàn thành phần hay nộp bài | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-86 | Câu chữ khi rời màn làm bài lúc còn thay đổi chưa lưu (web dùng hộp thoại có sẵn của trình duyệt, không có chuỗi riêng) **Chọn:** Thêm khóa mới: tiêu đề "Rời màn làm bài?", nội dung "Một số câu trả lời chưa được lưu lên máy chủ. Câu trả lời vẫn được giữ trên thiết bị và gửi khi bạn quay lại; đồng hồ vẫn chạy.", nút "Ở lại" và "Rời màn" (và bản tiếng Anh) | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-87 | Ba chuỗi nhắc tới "trang": `attempt.waiting.reassureNote` ("Lỡ đóng trang…"), `attempt.waiting.busyDescription` và `attempt.partChoice.busyNotice` ("Trang tự kiểm tra lại") **Chọn:** App dùng: "Đề sẵn sàng thì màn làm bài tự mở. Lỡ thoát ra thì mở lại từ Bài thi của tôi và bấm Tiếp tục; lượt không bị bắt đầu lại." và "Ứng dụng tự kiểm tra lại…" cho hai chuỗi còn lại | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-88 | Ở `GENERATION_FAILED` web không tự hỏi lại, trong khi backend tự thử sinh lại sau 30 giây, 2 phút, 5 phút (`PartGenerationRetryWorker.java:41-43`); học viên chỉ thấy phần mở khi bấm Thử lại hoặc tải lại **Chọn:** App giữ như web (không hỏi lại), vì nút "Thử lại" đã có; nếu muốn tự cập nhật thì hỏi lại 5 giây một lần như màn chờ bận | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-89 | Web không phóng to ảnh được. App có cho bấm để xem ảnh toàn màn hình và phóng to không? **Chọn:** Có, chỉ với ảnh `figure.image` có `data-zoom="true"` (cờ người soạn đã đặt), tối đa 4 lần; ảnh khác không mở | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-90 | Ghi chú `question.essayNote` nói bài viết nhận "gạch chân", nhưng trình soạn web không có nút gạch chân (`tiptap-essay-editor.tsx:152`). App hiện câu nào? **Chọn:** App hiện cùng câu với web để hai nơi giống nhau, mở một issue riêng sửa câu ở web; hoặc nếu muốn sửa ngay, app bỏ chữ "gạch chân" | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-91 | Khi app vào nền trong lúc phát audio thì dừng hay tiếp tục phát? **Chọn:** Dừng, bấm "Phát lại" để nghe tiếp không mất lượt; không tự phát lại khi quay về | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-92 | Câu `question.audioPlayer.autoplayBlocked` nhắc "Trình duyệt". App dùng câu nào khi âm thanh bị dừng (nền, cuộc gọi, tai nghe)? **Chọn:** "Âm thanh đã tạm dừng. Bấm Phát lại để nghe tiếp mà không mất lượt nghe." (tiếng Anh: "Audio paused. Tap Resume to continue without using a play.") | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-93 | Công thức KaTeX vẽ được mà bộ vẽ của app không vẽ được: hiện mã nguồn trong khung cảnh báo như công thức lỗi của web là đủ, hay cần báo về cho quản trị? **Chọn:** Hiện khung cảnh báo như web; bản build không được phát hành khi bộ mẫu đối chiếu còn công thức như vậy ngoài danh sách miễn trừ đã duyệt | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-94 | Chuỗi web có chữ "trang" theo nghĩa màn đang xem: trong app chữ đó là "màn hình" (tiếng Anh "page" thành "screen"), phần còn lại của câu giữ như web; tiêu chí trích câu của app và ghi khóa web gốc. Danh sách khép kín của phần này: `result.grading.description` ("màn hình sẽ tự cập nhật"), `result.total.pendingReturnNote` ("Bạn có thể rời màn hình"), `review.notIncluded.description` ("ở màn hình kết quả"). Chuỗi có "trang" theo nghĩa khác giữ nguyên; các khóa `*.adminNotice.*` không dùng trong app. **Chọn:** Đã chốt: đổi "trang" thành "màn hình" chỉ trong chuỗi của app như trên (AC-565, AC-573, AC-603). Cùng quy tắc áp cho chuỗi của mục 6.3 và mục 6.7. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-95 | Khi lượt ở `PENDING_REVIEW` và không chấm lại, máy chủ vẫn trả chữ của lời giải nhưng trả 404 cho ảnh trong lời giải (`AttemptSolutionAssetService.java:86-90` so với `AttemptResultService.java:511`). Có cần sửa máy chủ để cho tải ảnh lời giải ở trạng thái này không? **Chọn:** Sửa máy chủ trong một việc riêng để điều kiện của ảnh khớp điều kiện xem lại. App vẫn theo AC-635 nên không phải chờ việc này. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-96 | Ở `GRADING_ERROR`, màn không tự hỏi lại và không có nút tải lại, giống web. Có cần thêm nút "Tải lại" trong app không? **Chọn:** Giữ như web để cùng hành vi trên mọi nền tảng. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-97 | Màn Xác minh email của app tự gửi mã một lần mỗi lần được mở từ băng cảnh báo của màn Hồ sơ (lối vào duy nhất), sau khi tải hồ sơ và thấy email chưa xác minh, bằng `POST /api/v1/me/email/verification`; không gửi lần hai trong cùng lần mở (xoay máy, về nền, mở khóa, "Thử lại"); 429 thì hiện thời gian chờ hoặc giới hạn ngày như AC-678. Web `/verify-email` ghi "Chúng tôi đã gửi mã 6 số tới {email}" nhưng mở trang không gửi (`verify-email-view.tsx:132-154,244-249`); web giữ nguyên hành vi và chỗ lệch nằm ở Phụ lục A. **Chọn:** Đã chốt như cột trước: câu "đã gửi mã" của app là đúng; mỗi lần mở chỉ tốn một trong 5 mã mỗi 24 giờ, mở lại trong 60 giây chỉ nhận 429 chứ không có mã mới (`EmailCodeSendPolicy.java:14-37`). | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-98 | Gửi yêu cầu đổi sang chính email hiện tại thì máy chủ trả `EMAIL_TAKEN` và web hiện "Email này đã thuộc tài khoản khác." (`EmailChangeService.java:93-95`), câu này sai với người dùng. Có cần câu riêng không? **Chọn:** Giữ câu của web trong đợt này để hai bên giống nhau; nếu muốn sửa thì sửa cả web và app ở một việc khác. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-99 | Có cần màn cắt ảnh để người dùng chọn vùng vuông trước khi gửi không? **Chọn:** Không. Máy chủ tự cắt phần giữa như web, câu gợi ý đã ghi "Ảnh được cắt vuông." | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-100 | Băng "Email chưa xác minh" có cần hiện cả ở danh sách mục Tài khoản không? **Chọn:** Không; thẻ tài khoản đã có nhãn "Chưa xác minh", băng chỉ ở màn Hồ sơ như web. | Lựa chọn mặc định của người viết theo đề xuất khi đọc mã; đổi được khi duyệt | Chưa |
| A-101 | Nút "Về danh mục bài thi" của màn 410 và màn 404 đưa về danh mục với bộ lọc và vị trí cuộn đang có, không xóa bộ lọc. Web dùng liên kết `/#exams` nên mở danh mục không có điều kiện (`exam-status-views.tsx:103,128`). | Cùng luật giữ bộ lọc khi mở chi tiết rồi quay về của R-15 (AC-47); học viên vẫn xóa bộ lọc được bằng "Xóa bộ lọc"; không đổi dữ liệu hay quyền. | Chưa |
| A-102 | Trạng thái "chưa nhận được kết quả" (AC-150, AC-707) dùng cho đơn `PENDING_PAYMENT` mà lần thanh toán mới nhất `CREATED` hoặc `UNKNOWN` khi học viên quay lại không kèm Liên kết trả về, và vẫn giữ sau 60 giây Hỏi lại thay vì đổi sang câu "không cần thanh toán lại" của web. | Web không có trường hợp này vì trình duyệt luôn về `/checkout/result` kèm tham số; giữ nút "Tiếp tục thanh toán" không làm mất tiền: mỗi lần bấm tạo lần thanh toán mới, và nếu lần cũ vẫn được trả thì máy chủ tính đúng một lần, lần thừa được ghi "Thu trùng" và vào báo cáo đối soát (`PaymentOutcomeApplier.java:272-304`, `ReconciliationService.java:78-93`). | Chưa |
| A-103 | Trong chuỗi của app, chữ "trang" theo nghĩa màn đang xem đổi thành "màn hình" (tiếng Anh "page" thành "screen"), phần còn lại của câu giữ như web. Ở phần này chỉ áp cho bốn khóa `home.checking.message`, `home.checking.messageNoContact`, `home.checking.messageNoOrder`, `home.checking.messageNoOrderNoContact` ("Tải lại trang sau ít phút" thành "Tải lại màn hình sau ít phút"). Chuỗi có "trang" theo nghĩa khác (tên mục "Trang chủ", số trang) giữ nguyên. | App không có trang web; câu chỉ đổi một từ nên nghĩa không đổi. Cùng quy tắc với các chuỗi "trang" ở mục 6.6 và mục 6.7. | Chưa |
| A-104 | Nút Lịch sử của thẻ đưa bộ lọc Lịch sử về rỗng (R-64, AC-250). | Web mở `/history?examId={code}` nên mọi điều kiện cũ mất và `examId` bị bỏ (`history-query.ts:46-49`); app làm đúng kết quả đó mà không cần thay đổi backend. | Chưa |
| A-105 | Ở danh sách một cột của Lịch sử, dòng `VIEW_RESULT` vẫn có nút "Xem kết quả" dù bố cục thẻ của web không có (R-68). | Bố cục bảng của web có nút này (`history-view.tsx:880-891`) và `history.subtitle` nhắc tới nó; chạm tên bài vẫn mở kết quả như bố cục thẻ. | Chưa |
| A-106 | App tự sinh `sessionId` (UUID) cho mỗi tài khoản và lượt trên máy, lưu trước lần nhận phiên đầu và gửi ngay từ lần đầu (AC-314); web để máy chủ sinh ở lần đầu (`edit-session.ts:318-326`) | Máy chủ dùng đúng khóa client gửi khi lượt chưa có phiên và nối lại khi cùng khóa (`EditSessionService.java:107-131`); nhờ vậy việc thử lại nhận phiên (AC-318) không tự gây 409 `ATTEMPT_OPEN_ELSEWHERE` khi lần đầu đã thành công mà mất phản hồi. Không đổi backend | Chưa |
| A-107 | Lượt `ABORTED` giữ ngữ cảnh bắt đầu tới khi "Thử lại" tạo được lượt mới hoặc tài khoản đăng xuất (AC-389); web xóa ngay (`attempt-player.tsx:546-551`, `draft-store.ts:128-133`) nên "Thử lại" sau khi tải lại trang về Bài thi của tôi | Ngữ cảnh chỉ chứa `entitlementId`, lựa chọn trước, tên bài và thời lượng; giữ lại làm cho AC-298, AC-305 đúng cả sau khi tắt app. Lượt `ABORTED` không có câu trả lời để lộ | Chưa |
| A-108 | Đăng xuất chỉ xóa bản nháp, vị trí, `sessionId`, ngữ cảnh bắt đầu của tài khoản đăng xuất; dữ liệu của tài khoản khác trên máy còn nguyên và chỉ chủ của nó thấy (AC-390, AC-414, AC-428). Web xóa của mọi tài khoản (`features/session/api.ts:67-78`, `draft-store.ts:185-193`). Như web, đăng xuất không có cảnh báo riêng về câu trả lời chưa lưu (`features/home/student-user-menu.tsx:42-50`) | Quyết định của người viết đặc tả cho đợt này; trên máy dùng chung, một người đăng xuất không làm mất bài chưa lưu của người khác. Dữ liệu còn lại nằm trong bộ nhớ riêng của app, theo khóa có `userId`, không vào bản sao lưu | Chưa |
| A-109 | Không lấy được danh sách phương án ở màn chọn phần thì hiện lỗi kèm "Thử lại" và "Về Bài thi của tôi" (AC-718), dùng lại chuỗi `prepare.errorLoad.*`; web hiện danh sách rỗng, không lối thoát (`features/attempt-player/api.ts:280-301`) | Không có chuỗi mới; học viên có cách thử lại trong 15 phút hạn chọn thay vì kẹt tới khi lượt đóng `CHOICE_TIMEOUT` | Chưa |
| A-110 | Bản nháp thắng theo `seq` (AC-386) như web, kể cả khi hai máy tiếp quản qua lại: nháp cũ của máy bị đẩy ra có `seq` lớn hơn có thể ghi đè bài máy kia đã lưu khi máy này tiếp quản lại (`save-queue.ts:178-219`, `AnswerService.java:339-417`) | Giống web; tránh được chỉ khi đổi giao thức máy chủ, ngoài phạm vi đợt này. Cần hai lần tiếp quản và câu chưa lưu ở máy bị đẩy ra, xác suất thấp | Chưa |
| A-111 | Mở màn làm bài khi máy chủ báo lần nghe đang `PLAYING` thì app hiện "Phát lại" và không tự phát (R-133, AC-519); web khi gắn lại trình phát thì cố tự phát tiếp | Thống nhất với hành vi khi vào nền (R-136) nên học viên chỉ gặp một quy tắc; chính web cũng không chắc tự phát được khi trình duyệt chặn tự phát và khi đó hiện nút chờ bấm (`audio-player.tsx:441-451`); bấm "Phát lại" không mất lượt (`AttemptAudioService.java:135-156`) | Chưa |
| A-112 | Trình phát rời khỏi màn hình (chuyển phần khi `FREE`, rời màn làm bài mà app vẫn ở nền trước) thì dừng phát, lần nghe vẫn dở | Web dừng phát khi trình phát bị gỡ khỏi màn (`audio-player.tsx:495-502`); tránh âm thanh phát mà học viên không thấy trình phát; không mất lượt | Chưa |
| A-113 | Hai tệp audio khác nhau có thể phát cùng lúc, như web | Web không chặn (mỗi câu một trình phát riêng) và máy chủ đếm lượt theo từng tệp (`AttemptAudioPlay.java:22`); giữ giống web để không thêm luật mới chưa ai quyết | Chưa |
| A-114 | Bộ gõ bắt buộc khi nghiệm thu là Telex trên Gboard (Android) và bàn phím tiếng Việt Telex của hệ thống (iOS); Laban Key kiểm tay khi có, ghi kết quả, không chặn nghiệm thu | Gboard và bàn phím iOS có sẵn trên máy kiểm và chạy được kiểm tự động ổn định; Laban Key là app bên thứ ba phải cài tay, cách ghép chữ nằm ngoài tầm kiểm soát của app | Chưa |
| A-115 | Với Bộ câu hỏi, tệp không phải audio của câu con không hiện trên tờ đề, như web (AC-727) | Đúng hành vi web hiện tại (`attempt-player.tsx:355-358`); hiện thêm tệp là đổi hành vi so với web, cần người yêu cầu quyết | Chưa |
| A-116 | Trong chuỗi của app, chữ "trang" theo nghĩa màn đang xem đổi thành "màn hình" (tiếng Anh "page" thành "screen"), phần còn lại giữ như web. Ở phần này chỉ áp cho `account.verifyEmail.errorLoadTitle` ("Không tải được màn hình", AC-679). `account.profile.password.description` ("Đổi mật khẩu ở trang riêng.") không dùng vì màn Hồ sơ của app không có khối "Mật khẩu". | Cùng quy tắc với mục 6.3 và mục 6.6; câu chỉ đổi một từ. | Chưa |
| A-117 | Ô mã xác minh trên iOS đánh dấu là ô mã dùng một lần để hệ điều hành gợi ý mã; Android chỉ có bàn phím số, không tự điền (AC-739). | Đó là khả năng có sẵn của từng nền tảng, không cần hạ tầng thêm; Android không có cơ chế hệ điều hành điền mã từ thư, và đọc SMS hay thư thì cần quyền mà app không xin. Web chỉ đặt `inputMode` và `autocomplete` cho trình duyệt. | Chưa |
| A-118 | Bốn câu chữ của app mà web không có và đặc tả chưa nêu: báo không mở được tệp đính kèm (AC-514) là "Không mở được tệp. Vui lòng thử lại."; tên cho trình đọc màn hình của ba phím "/", ",", "." dưới ô số (R-122) là "Dấu gạch chéo", "Dấu phẩy", "Dấu chấm", phím "-" dùng lại "Dấu trừ (-)" của thanh chèn ký hiệu; ảnh trong nội dung đề tải lỗi (R-109) hiện "Không tải được ảnh. Bấm để tải lại."; nút đóng ảnh đang phóng to (R-110) dùng lại "Đóng" (`common.close`). Bản tiếng Anh: "Could not open the file. Please try again.", "Slash", "Comma", "Full stop", "Could not load the image. Tap to reload." | Chỉ là câu chữ, phát hiện khi viết kế hoạch; không đổi hành vi nào của đặc tả. Chờ người yêu cầu xác nhận cùng kế hoạch. | Đã xác nhận 2026-11-26 (D-31) |
| A-119 | App mang sẵn nội dung hai văn bản lúc build thay vì tải từ web khi mở (R-181). | Màn Đăng ký phải xem được văn bản cả khi mạng kém; văn bản hiếm khi đổi, và bản trên web (nút "Mở trên web") luôn là bản mới nhất. Đổi văn bản thì phát hành bản app mới; `termsVersion` cho biết người dùng đã đồng ý bản nào. | Chưa |
| A-120 | Bản tiếng Anh của hai văn bản là bản dịch từ bản tiếng Việt đã duyệt, do task web dựng, có câu ghi bản tiếng Việt là bản có giá trị (R-180). | App và web có hai ngôn ngữ với cùng bộ khóa (R-7); đơn vị vận hành chỉ cung cấp nội dung tiếng Việt. | Chưa |
| A-121 | Trong app, logo là hình dấu kèm tên "Thi360" viết bằng chữ; hình logo có chữ không đặt trên nền tối (R-179). | Chữ "THI" của hình logo màu xanh đậm, gần như không đọc được trên nền tối của app; hình dấu thì rõ trên cả hai nền (đã dựng thử). | Chưa |
| A-122 | Biểu tượng app dùng nền trắng (R-179). | Hình dấu có cả xanh đậm lẫn cam, chỉ nền trắng giữ được đủ tương phản cho mọi phần; người yêu cầu chưa nêu màu nền. | Chưa |
| A-123 | Ô đồng ý ở Đăng ký nhắc cả hai văn bản và ghi tên "Điều kiện giao dịch chung" thay cho "Điều khoản sử dụng" (R-180, R-181). | Trang gốc đặt tên văn bản là "Điều kiện giao dịch chung"; kho ứng dụng đòi người dùng thấy Chính sách bảo mật trước khi tạo tài khoản. | Chưa |
| A-124 | Hai văn bản được đưa vào mã ở trạng thái `published`, không có dòng "Bản nháp"; ngày có hiệu lực ghi 2026-11-26, là ngày chốt nội dung (R-180). | Người yêu cầu chốt phát hành hai văn bản như đang có (D-32). Ngày có hiệu lực do người viết chọn vì văn bản `published` phải có ngày; đơn vị vận hành đổi ngày hay nội dung bằng một phiên bản mới. Các điểm do người viết đề xuất ghi ở `docs/legal/README.md`. | Đã xác nhận 2026-11-26 (D-32, D-34) |
| A-125 | Câu chữ của màn Xóa tài khoản là chữ mới của đặc tả (web chưa có chức năng này): tiêu đề "Xóa tài khoản"; đoạn giải thích "Khi xóa tài khoản, bạn sẽ bị đăng xuất khỏi mọi thiết bị và không đăng nhập lại được. Họ tên, email, số điện thoại và ảnh đại diện của bạn bị xóa khỏi hồ sơ. Bài làm của bạn được giữ ở dạng không còn gắn với bạn. Đơn hàng, thanh toán, nhật ký hệ thống và thư đã gửi được lưu theo quy định của pháp luật và thời hạn lưu trữ."; các câu cảnh báo, câu chặn, câu lỗi và câu "Tài khoản của bạn đã được xóa." như ở R-183. Trong các câu có số, app và web giữ cùng một chuỗi nguồn với chỗ đặt số viết là `{n}`. Bản tiếng Anh dịch sát nghĩa. | Chỉ là câu chữ; người yêu cầu duyệt cùng kế hoạch. Web và hai app dùng cùng một bộ câu. | Đã xác nhận 2026-11-26 (D-30, duyệt hai màn Xóa tài khoản) |
| A-126 | Chỉ tài khoản học viên tự xóa được; tài khoản có vai trò quản trị hoặc giáo viên bị từ chối (R-182). | Tài khoản vận hành do admin quản lý qua màn quản trị đang có; cho chúng tự xóa có thể làm mất người chịu trách nhiệm của lớp hay đề thi. | Đã xác nhận 2026-11-26 (D-30) |
| A-127 | Tự xóa không áp điều kiện "đã có đơn hàng thì không xóa được" của lệnh xóa của admin (R-182). | Điều kiện đó chặn mọi học viên từng mua bài, trong khi kho ứng dụng đòi người dùng tự xóa được; dữ liệu giao dịch vẫn được giữ nên mục đích của điều kiện (không mất sổ sách) vẫn đạt. | Chưa |
| A-128 | Thông tin cá nhân bị thay bằng giá trị vô danh ngay trong bản ghi tài khoản thay vì xóa hẳn bản ghi (R-182). | Đơn hàng, quyền thi, lượt thi và nhật ký tham chiếu mã tài khoản; giữ bản ghi vô danh thì các tham chiếu còn nguyên mà không còn danh tính. | Chưa |
| A-129 | Tên người mua đã chụp trong đơn hàng được giữ sau khi xóa tài khoản (R-182). | Chứng từ bán hàng phải giữ theo quy định kế toán; Chính sách bảo mật thông tin đã nêu điều này. Hai văn bản đã phát hành theo D-32. | Chưa |
| A-130 | Lượt thi đang làm dở và đơn đã trả tiền đang kiểm tra chặn việc xóa; quyền thi còn lượt chưa dùng thì không chặn (R-182). | Theo D-19. Chặn hai trường hợp đó để học viên không mất bài đang làm hay khoản tiền chưa được cấp bài vì một thao tác vội. | Chưa |
| A-131 | Đơn ở trạng thái chờ thanh toán cũng chặn việc xóa, tới khi học viên hủy đơn hoặc hệ thống chuyển đơn sang hết hạn (R-182). Người yêu cầu chưa nêu trường hợp này. | Trang VNPAY của đơn đó có thể vẫn đang mở; nếu học viên trả tiền sau khi tài khoản đã xóa thì tiền bị trừ mà không còn tài khoản để nhận bài. Hủy đơn là thao tác có sẵn ở Đơn của tôi. | Đã xác nhận 2026-11-26 (D-30) |
| A-132 | Ba trạng thái sau không có điều kiện chặn hay cảnh báo riêng, ngoài các điều kiện theo trạng thái của đơn nêu ở R-182: đơn đang được hoàn tiền (việc hoàn chạy ở cổng thanh toán, không cần tài khoản), đơn mà hệ thống chưa biết đã bị trừ tiền hay chưa (khi tiền được xác nhận thì theo D-22), lượt thi bị lỗi chấm (R-182). | Không trường hợp nào làm khách mất tiền mà không có đường hoàn; ghi ra để sau này không mở lại. | Chưa |
| A-133 | Số điện thoại và email liên hệ trong hai văn bản ghi theo bản đã duyệt ở `docs/legal/` (0974187313, 0968964334). Chân trang của web (`apps/web-student/src/lib/contact.ts`, thêm ở UNI-2828) ghi hai số khác (0988.371.194, 0961.167.606); đợt này không đổi chân trang (R-180). | Người yêu cầu chốt lấy văn bản theo bản đã duyệt (D-35); chân trang là phần của UNI-2828, ngoài phạm vi. Việc thống nhất các số liên hệ ghi vào danh sách việc sau merge cho đơn vị vận hành. | Chưa |

## 12. Câu hỏi mở

| ID | Câu hỏi | Đề xuất | Người trả lời | Chặn |
| --- | --- | --- | --- | --- |
| Q-1 | Đã trả lời 2026-11-26 (D-7): bản thiết kế các màn mới làm ngay sau vòng rà soát đầu của đặc tả, duyệt trước khi duyệt kế hoạch | — | M.Trường | Không còn chặn |
| Q-2 | Đã trả lời 2026-11-26 (D-8): người yêu cầu duyệt danh sách miễn trừ; mức phải đạt là công thức thuộc chương trình cấp 3 và đại học. | — | M.Trường | R-117 |

## Phụ lục A. Chỗ lệch của web và backend phát hiện khi đọc mã

Các điểm dưới đây nằm ngoài phạm vi; app làm theo cách ghi ở giả định tương ứng trong mục 11. Mỗi điểm nên có issue riêng nếu người yêu cầu muốn sửa.

- Nút Lịch sử trên thẻ ở Bài thi của tôi gửi mã sản phẩm làm `examId`; trang lịch sử chỉ nhận UUID nên bỏ qua, kết quả là lịch sử không được lọc theo bài (`apps/web-student/src/features/my-exams/my-exam-card.tsx:214`, `apps/web-student/src/features/attempt-history/history-query.ts:46-49`).
- Backend trả 404 cho bộ đề `UNAVAILABLE` nên màn "Bộ đề tạm thời chưa bán được" của web không bao giờ hiện (`backend/src/main/java/vn/unicom/thi360/commerce/storefront/application/StorefrontQueryService.java:219-220`).
- Ghi chú `question.essayNote` nói bài viết nhận gạch chân nhưng trình soạn không có nút gạch chân (`packages/ui/src/essay-editor/tiptap-essay-editor.tsx:152`).
- Ở trạng thái `PENDING_REVIEW`, máy chủ trả chữ của lời giải nhưng trả 404 cho ảnh trong lời giải (`AttemptSolutionAssetService.java:86-90` so với `AttemptResultService.java:511`).
- Trang `/verify-email` ghi "Chúng tôi đã gửi mã" nhưng mở trang không gửi mã (`apps/web-student/src/features/account/verify-email-view.tsx:132-154,244-249`).
- Đổi sang chính email hiện tại nhận `EMAIL_TAKEN` và câu "Email này đã thuộc tài khoản khác." (`EmailChangeService.java:93-95`).
- Mô tả `ProfileUpdateRequest.fullName` ghi tối đa 100 ký tự trong khi mã kiểm cho 200 (`ProfileUpdateRequest.java:11`, `ProfileService.java:44`).
- OpenAPI ghi `Idempotency-Key` của `POST /api/v1/me/attempts` là không bắt buộc trong khi backend bắt buộc (`shared/idempotency/IdempotentCommand.java:136,149-158`).
- Sáu chuỗi của luồng chọn phần ghi cứng phần tự chọn là "phần 3" thay vì lấy thứ tự của phần (`apps/web-student/src/messages/vi.json`, các khóa `attempt.partChoice.title`, `confirmCta`, `deadlineWarning`, `deadlineExpired`, `attempt.closed.headingChoiceTimeout`, `attempt.dialogs.finishPart.nextPartChoiceNote`; `apps/web-student/src/features/attempt-player/part-choice-view.tsx:106-264`). App dùng nguyên các chuỗi này.
- Bấm thanh toán tiếp tạo lần thanh toán mới mà không hủy lần đang mở ở VNPAY, nên học viên có thể trả hai lần cho một đơn. Máy chủ tính lần thành công đầu tiên, đánh dấu lần sau là "Thu trùng" và đưa vào báo cáo đối soát; học viên chỉ thấy nhãn "Thu trùng", không có thông tin hoàn tiền (chi tiết ở mục 6.2).
- Nhận phiên sửa bài gặp lỗi mạng hoặc 5xx thì web không thử lại và không báo; học viên sửa bài mà không được lưu tới khi tải lại trang (`apps/web-student/src/features/attempt-player/edit-session.ts:364-365`, `attempt-player.tsx:1116-1123`).

## 13. Nhật ký rà soát

| Vòng | Ngày | Người rà soát | Chặn | Nên sửa | Nhỏ | Bác (kèm lý do) | Báo cáo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 2026-11-26 | omp `devin/swe-2-1` mức max, bốn phiên chia theo phần (A: chung, 6.1, 6.2; B: 6.3, 6.6, 6.7; C: 6.4; D: 6.5) | 15 | 24 | 17 | Bác một phần: S1-A-8 (số dòng trích của `catalog-section.tsx` khớp tệp hiện tại), S1-B-9 và S1-C-15 (web ghim múi giờ `Asia/Ho_Chi_Minh`, không dùng giờ máy), S1-B-11 (AC-264 là nút Tiếp tục), S1-A-4 (một đơn có tối đa 99 lần thanh toán, không phải 5). Tám mục cần người quyết: người viết chọn mặc định và ghi thành giả định ở mục 11 | Thư mục phiên `reviews/spec-r1-report-{A,B,C,D}.md`; cách xử lý từng phát hiện ở `reviews/spec-r1-dispositions-{A1,A2,B,C,D}.md` |
| 2 | 2026-11-26 | omp `devin/swe-2-1` mức max, hai phiên rà phần thay đổi (AB: chung, 6.1, 6.2, 6.3, 6.6, 6.7; CD: 6.4, 6.5) | 1 | 1 | 1 | Không bác mục nào. Trong 66 mục vòng 1 được xét lại, 63 được xác nhận đã xử lý (kể cả các mục người viết bác một phần ở vòng 1), 2 mục người rà soát vòng 1 tự chấm là sai vẫn là sai, 1 mục còn mở là S1-A-7, thành S2-AB-1: mục 4.2 ghi sai rằng web gửi `seenPrice` khi áp mã, đã sửa theo `voucher-box.tsx:114-118,171-175`. Cũng đã sửa: hàng "Chuỗi của app" của mục 7 còn ghi nhãn của bản nháp rời (S2-AB-2); bảy tham chiếu tới bảng của bản nháp rời đổi thành số giả định (S2-CD-1); số dòng trích của `AttemptExceptionAdvice.java`; tên "Phụ lục A" | `reviews/spec-r2-report-{AB,CD}.md`; cách xử lý ở `reviews/spec-r2-dispositions.md` |
| 3 | 2026-11-26 | omp `devin/swe-2-1` mức max, một phiên chỉ kiểm mục chặn còn mở của vòng 2 và 16 chỗ vừa sửa | 0 | 0 | 0 | Không có. S2-AB-1 được xác nhận đã xử lý; không có phát hiện mới. Người rà soát chỉ ra một chỗ còn ghi "phụ lục chỗ lệch của đặc tả" ở mục 6.3, đã đổi thành "Phụ lục A" | `reviews/spec-r3-report.md` |
| 4 | 2026-11-26 | omp `devin/swe-2-1` mức max, một phiên rà phần đặc tả thêm theo yêu cầu thay đổi (R-178 đến R-181, D-11 đến D-15, A-119 đến A-124), cùng vòng 3 của kế hoạch | 0 | 1 | 3 | Không bác mục nào. Đã sửa: các chỗ còn trích tên cũ làm chuỗi hiển thị (AC-151, AC-708 và hai giả định), phạm vi rà của AC-742, hai trích dẫn mã không khớp repo, quy tắc `version` và `status` của tệp văn bản. Một mục cần đơn vị vận hành quyết: hai khung giờ làm việc khác nhau trong Điều kiện giao dịch chung | `reviews/plan-r3-report-A.md` (P3-A-2, 6, 9, 10), `reviews/plan-r3-report-B.md` (P3-B-6) |
| 5 | 2026-11-26 | omp `devin/swe-2-1` mức max, hai vòng riêng cho yêu cầu thay đổi tự xóa tài khoản (R-182 đến R-184), cùng phần kế hoạch tương ứng | 3 (vòng đầu), 0 (vòng hai) | 5 | 9 | Không bác mục nào; một mục nhỏ không làm (nhãn `SELF_DELETED` ở web admin). Ba mục chặn: tài khoản chưa có quyền thi nhận lỗi máy chủ; đơn quá hạn vẫn nhận tiền trễ; thanh toán xác nhận đúng lúc đang xóa. Ba mục người yêu cầu quyết thành D-22, D-23, D-24. Vòng hai xác nhận mọi mục vòng đầu đã xử lý | `reviews/plan-cr-delete-report.md`, `reviews/plan-cr-delete-r2-report.md`; cách xử lý ở `reviews/plan-cr-delete-dispositions.md` |
| 6 | 2026-11-26 | omp `devin/swe-2-1` mức max, hai vòng rà phần đặc tả và kế hoạch đổi theo các câu trả lời của người yêu cầu ở cổng G2 (D-25 đến D-33) | 1 (vòng đầu), 0 (vòng hai) | 2 | 3 | Bác một mục nhỏ về danh mục công thức (`align`, `\tag`): đã chạy KaTeX 0.19.1 của repo, danh mục ghi đúng. Mục chặn và ba mục còn lại nằm ở kế hoạch, đã sửa; đặc tả không có mục nào phải sửa; vòng hai xác nhận và chấp nhận việc bác | `reviews/plan-g2-answers-report.md`, `reviews/plan-g2-answers-r2-report.md`; cách xử lý ở `reviews/plan-g2-answers-dispositions.md` |
| 7 | 2026-11-26 | omp `devin/swe-2-1` mức max, một phiên rà phần đối chiếu lại với `develop` tại `04a9457b` (D-35, R-180 với AC-788, R-24, dòng ảnh hưởng mới, A-133) | 0 | 4 | 3 | Không bác mục nào. Ở đặc tả có hai mục: trường "Bản" của dòng trạng thái còn ghi 8, và dòng liệt kê thay đổi trên `develop` thiếu việc logo thay ở mọi màn học viên; đã sửa. Người rà soát xác nhận không thay đổi nào trên `develop` làm gãy yêu cầu hay tiêu chí mà đặc tả chưa xử lý | `reviews/plan-develop-reconcile-report.md`; cách xử lý ở `reviews/plan-develop-reconcile-dispositions.md` |
