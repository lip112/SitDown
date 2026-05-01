# UNIV SITDOWN Backend Project

## Purpose
Backend server for a university seat reservation system. Manages real-time seat booking and occupancy for lecture halls, reading rooms, study rooms, and PC labs.

## Tech Stack
- **Language**: Java 17 (Record, Sealed Class supported)
- **Framework**: Spring Boot 3.5.x (Web, Data JPA, Security, Validation)
- **Build**: Gradle (Kotlin DSL)
- **Database**: PostgreSQL 16 (with btree_gist extension for EXCLUDE constraints)
- **Cache/Lock**: Redis 7.2 (Lettuce driver, Redisson for distributed locks)
- **Migration**: Flyway (V{version}__{description}.sql)
- **Auth**: JWT (jjwt 0.12.x) - Access + Refresh tokens
- **Documentation**: springdoc-openapi (/swagger-ui.html)
- **Testing**: JUnit 5 + Testcontainers
- **Deployment**: AWS ECS Fargate + GitHub Actions CI/CD

## Core Domain
**Seat reservation concurrency control** - the technical heart of the project.

## Key Documents
- @docs/01-spec.md - Feature specification (screens, features, business rules, data model)
- @docs/02-api.md - API specification (endpoints, error codes, concurrency policies)
- @docs/03-backend-roadmap.md - Phase-based learning roadmap
