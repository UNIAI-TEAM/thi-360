# @thi360/api-client

Client gọi API, **sinh từ OpenAPI của backend** (`openapi.json` → `src/schema.d.ts`). Không sửa tay `openapi.json` hoặc `src/schema.d.ts`.

## Sinh lại mã khi đổi API

```sh
cd backend && ./mvnw -B -ntp verify
pnpm --filter @thi360/api-client generate
```
