---
name: dayan-food-development
description: Develop and maintain the Dayan Food Java, Vue, MySQL application. Use when changing Spring Boot MVC APIs, entity DTO/PO/VO models, Flyway migrations, Vue pages, Leaflet maps, image uploads, internationalization, or when validating and running this repository.
---

# Dayan Food Development

## Overview

Apply this repository's conventions consistently across backend, frontend, database, and local verification. Read [references/architecture.md](references/architecture.md) before changing package boundaries, persistence models, API contracts, Flyway migrations, maps, uploads, or i18n.

## Workflow

1. Inspect relevant source and `git status`. Preserve unrelated work.
2. Trace the complete feature flow: Vue UI → API client → Controller → Service interface → ServiceImpl → Mapper/XML → MySQL.
3. Keep object types under `entity`: DTOs in `entity.dto`, MyBatis persistence objects in `entity.po`, response models in `entity.vo`, and enums in `entity.enums`.
4. Put business decisions and transactions in services. Keep controllers limited to HTTP binding and status codes.
5. Change database structure only through Flyway. Keep Mapper XML aligned with the migrated schema.
6. Add user-facing Vue text to both locale files. Do not hardcode translated UI text in components.
7. Comment intent, security, lifecycle, or non-obvious constraints. Do not narrate obvious syntax.
8. Run proportional validation and report the exact checks.

## Backend rules

- Use constructor injection and explicit imports.
- Define business contracts in `service` and implementations in `service.impl`.
- Keep SQL in `resources/mapper/*.xml`; Mapper interfaces live in `com.dayan.food.mapper`.
- Never return PO classes from controllers. Convert PO to VO at the service boundary.
- Validate DTO fields with Jakarta Validation.
- Restrict uploaded file types, generate server-side names, and keep resolved paths inside the configured upload directory.
- Preserve UTF-8 for Java, YAML, and Flyway SQL.

## Frontend rules

- Use Vue 3 Composition API with TypeScript.
- Centralize HTTP calls in `src/api.ts` and shared contracts in `src/types.ts`.
- Keep Leaflet setup and teardown inside `FoodMap.vue`.
- Add every i18n key to both `zh-CN.ts` and `en-US.ts` in the same change.
- Preserve the editorial Chinese visual language and responsive behavior unless redesign is requested.

## Validation

```powershell
cd dayanfood-backend
mvn test

cd ../web
npm.cmd run build
```

For database changes, start MySQL and the backend, then confirm the new `flyway_schema_history` row. For API changes, call the affected endpoint. For UI changes, confirm HTTP 200 and inspect desktop and mobile widths.

Never rewrite an applied Flyway migration to fix a live schema. Add the next version.
