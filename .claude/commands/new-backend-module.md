---
description: Add a new backend feature module with all layers
---

Create a complete backend feature module for: $ARGUMENTS

Steps:
1. Read `docs/ARCHITECTURE.md` to understand the feature-based package structure
2. Read `docs/API.md` to check if endpoints are already specified for this feature
3. Read `docs/DATABASE.md` to check if tables exist or a new migration is needed
4. Create the package: `backend/src/main/java/com/fintrack/{feature}/`
5. Create these files in order:
   - DTOs (Java records) — request and response objects
   - JPA Entity (if new entity needed) in `common/entity/`
   - Repository interface (extends JpaRepository)
   - Service class (business logic, @Transactional on writes)
   - Controller class (thin, @Valid on inputs, explicit HTTP status codes)
   - New Flyway migration if schema changes needed
6. Register any new beans in config if needed
7. Add unit tests for the service layer
8. Update `tasks/TODO.md` — mark relevant items as done
