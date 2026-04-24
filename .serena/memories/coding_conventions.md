# UNIV SITDOWN Coding Conventions

## Naming
- **No abbreviations**: `Rsv` ❌ → `Reservation` ✅, `Usr` ❌ → `User` ✅
- **DTO Suffixes**: `XxxRequest` / `XxxResponse` (e.g., `CreateReservationRequest`)
- **Exception Suffix**: `XxxException` (e.g., `SeatAlreadyReservedException`)
- **Enum Values**: `SCREAMING_SNAKE_CASE` (e.g., `READING_ROOM`, `IN_USE`)

## Class Design
- Request/Response DTOs: Use **Java Record**
- Entities: **Lombok @Getter only** - no setters. Changes via methods.
- Static factory methods (`of`, `from`, `create`) preferred over @Builder
- Controllers: Keep thin, logic in Service layer

## Exception Handling
- All business exceptions extend `BusinessException`
- Never throw `RuntimeException` directly
- Error codes must be in `ErrorCode` enum and @docs/02-api.md section 8

## Transactions
- `@Transactional` explicit on Service methods
- Read-only: `@Transactional(readOnly = true)`
- Reservation create/extend/cancel: **@Transactional required**
- No external API calls inside transactions

## Logging
- **NO `System.out.println`** - forbidden
- Use SLF4J: `private static final Logger log = LoggerFactory.getLogger(Xxx.class);`
- Or Lombok `@Slf4j`
- Never log passwords, tokens, PII
- traceId auto-included in MDC for error logs

## Concurrency (Core to this project)
- Application level: `SELECT ... FOR UPDATE` (pessimistic lock) + @Transactional
- DB level: EXCLUDE constraint on (seat_id, tsrange)
- Optional: Redisson distributed lock

## Testing
- Service layer: unit tests required, min 1 happy + 2 error cases
- Concurrency tests: ExecutorService + CountDownLatch
- Controller: @WebMvcTest + MockMvc
- Repository: @DataJpaTest + Testcontainers
