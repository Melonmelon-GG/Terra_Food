# Repository architecture

## Projects

- `dayanfood-backend`: Java 21, Spring Boot 3, Spring MVC, MyBatis, Flyway, MySQL.
- `web`: Vue 3, TypeScript, Vite, Vue Router, Axios, Leaflet, vue-i18n.

## Backend request flow

```text
controller -> service interface -> service.impl -> mapper interface -> mapper XML -> MySQL
                                      +-> PO mapped to VO
```

- Controllers live in `com.dayan.food.controller`.
- Service interfaces define business contracts; implementations under `service.impl` own transactions and mapping decisions.
- Mapper interfaces under `com.dayan.food.mapper` match XML files under `src/main/resources/mapper`.
- Mapper interfaces and XML statements accept and return only `entity.po` types.
- Spreadsheet imports use Apache POI, tolerate irregular headers and columns, and return skipped-row diagnostics. City-only locations use the offline `city-centers.csv` fallback.
- DTO, PO, VO, and enum types live under their matching `entity` subpackage.
- `ImageStorageService` stores generated filenames under `app.upload-directory`; `/uploads/**` exposes them read-only.

## Database

Flyway is the sole schema owner. MyBatis only reads and writes the schema created by migrations.

- Add migrations under `src/main/resources/db/migration`.
- Before the first release, `V1__init_schema.sql` is the consolidated baseline containing the complete schema and initial catalog data.
- Use the next `V<number>__description.sql` version.
- Do not edit a migration after it has been applied to a shared database.
- Keep PO fields and Mapper XML result maps aligned with MySQL column types.

## Frontend

- `src/api.ts`: all backend requests.
- `src/types.ts`: API-facing TypeScript models.
- `src/components/FoodMap.vue`: Leaflet instance, marker lifecycle, and map selection.
- `src/components/FoodUploadModal.vue`: two-step image upload and food creation.
- Map clicks use browser-side Nominatim reverse geocoding; the authenticated region resolve endpoint reuses or creates the returned province/city before opening the food form.
- `src/locales`: matching Chinese and English message trees.
- `src/i18n.ts`: locale detection and persistence.

## Authentication and authorization

- Spring Security stores authentication in the HTTP Session; the frontend sends credentials through the same-origin Vite proxy.
- `AuthController` owns registration, login, current-session lookup, and logout HTTP behavior.
- `AuthService` registers public accounts as `USER` only, authenticates credentials, and verifies that the selected login role matches the stored account role. Administrator accounts must never be created through public self-registration. Successful registration returns to the login page and requires an explicit sign-in instead of creating a session automatically.
- `AppUserDetailsServiceImpl` adapts `AppUserMapper` PO records for Spring Security.
- Public users can read foods and regions. Authenticated users can create foods and upload images. Only `ADMIN` can import spreadsheets, delete foods, or access `/api/admin/**`.
- Frontend route guards restore `/api/auth/me` before entering protected pages. The catalog and food details require a signed-in user, while `/admin` additionally requires the `ADMIN` role. Login keeps the public self-registration entry inside the authentication flow instead of the global header.
- Configure initial passwords with environment variables; never commit real credentials.

Uploaded image URLs are relative (`/uploads/...`). Vite proxies `/api` and `/uploads` to port 8080 during development.

## Local services

- MySQL: `localhost:3306`, database `dayan_food`.
- Backend: `http://localhost:8080`.
- Frontend: `http://localhost:5173`.

Configure credentials through `DB_USERNAME` and `DB_PASSWORD`; do not commit passwords.
