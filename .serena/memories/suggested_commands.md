# UNIV SITDOWN - Suggested Commands

## Build & Test
- `./gradlew compileJava` - Compile Java code
- `./gradlew build` - Full build
- `./gradlew test` - Run all tests
- `./gradlew clean` - Clean build artifacts

## Development
- `./gradlew bootRun` - Start Spring Boot app (local)
- `git status` - Check git status
- `git add <files>` - Stage files
- `git commit -m "<message>"` - Commit changes
- `git log --oneline -10` - View recent commits

## Documentation
- Project CLAUDE.md contains project-specific rules
- API spec: @docs/02-api.md
- Feature spec: @docs/01-spec.md
- Backend roadmap: @docs/03-backend-roadmap.md

## Database & Migration
- Flyway migrations in: `src/main/resources/db/migration/`
- Format: `V{number}__{description}.sql`
- Never use JPA ddl-auto=update in any environment

## Package Structure
```
com.univsitdown
├── global/           (config, exception, response, security)
├── auth/             (authentication & email verification)
├── user/             (user profile management)
├── space/            (space & seat management)
├── reservation/      (core domain - booking & concurrency)
├── stat/             (usage statistics)
├── notice/           (announcements)
└── admin/            (admin operations)
```

Each domain package has: controller/, service/, repository/, domain/, dto/
