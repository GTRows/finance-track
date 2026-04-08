---
name: spring-patterns
description: Spring Boot 3 patterns and conventions used in this project. Read this before writing any backend code.
---

# Spring Boot Patterns — FinTrack

## Package Structure (Feature-Based)

Each feature is a self-contained vertical slice:
```
com.fintrack.{feature}/
├── {Feature}Controller.java   — REST endpoints only
├── {Feature}Service.java      — business logic + @Transactional
├── {Feature}Repository.java   — extends JpaRepository
└── dto/
    ├── {Feature}Response.java     — Java record
    └── Create{Feature}Request.java — Java record with @Valid
```

Entities live in `com.fintrack.common.entity` (shared across features).

## Controller Pattern

```java
@RestController
@RequestMapping("/api/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getAll(
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.ok(portfolioService.getAllForUser(user.getId()));
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(
            @Valid @RequestBody CreatePortfolioRequest request,
            @AuthenticationPrincipal FinTrackUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portfolioService.create(request, user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal FinTrackUserDetails user) {
        portfolioService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
```

Rules:
- Controllers are THIN — no business logic
- Always use `@AuthenticationPrincipal FinTrackUserDetails user` to get current user
- Always return `ResponseEntity<T>` with explicit status
- Always `@Valid` on request bodies

## Service Pattern

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getAllForUser(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PortfolioResponse create(CreatePortfolioRequest request, UUID userId) {
        var portfolio = Portfolio.builder()
                .userId(userId)
                .name(request.name())
                .type(request.type())
                .build();
        return toResponse(portfolioRepository.save(portfolio));
    }

    // Always check ownership before modifying
    @Transactional
    public void delete(UUID portfolioId, UUID userId) {
        var portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
        if (!portfolio.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your portfolio");
        }
        portfolioRepository.delete(portfolio);
    }

    private PortfolioResponse toResponse(Portfolio p) {
        return new PortfolioResponse(p.getId(), p.getName(), p.getType().name(), ...);
    }
}
```

Rules:
- `@Transactional(readOnly = true)` on reads (better performance)
- `@Transactional` on writes
- Always verify ownership (`userId` check) before modifying resources
- Map entities to DTOs in the service — never expose entities directly to controller

## DTO Pattern (Java Records)

```java
// Request — always validate
public record CreatePortfolioRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull PortfolioType type,
        String description
) {}

// Response — immutable, safe to expose
public record PortfolioResponse(
        UUID id,
        String name,
        String type,
        BigDecimal totalValueTry,
        BigDecimal pnlPercent
) {}
```

## Entity Pattern

```java
@Entity
@Table(name = "portfolios")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "portfolio_type", nullable = false)
    private PortfolioType type;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
```

Rules:
- Use UUID for all primary keys
- Use `Instant` for timestamps (timezone-safe)
- Use `@Enumerated(EnumType.STRING)` — never store enum ordinals
- `@CreationTimestamp` / `@UpdateTimestamp` from Hibernate for auto timestamps
- Use Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`

## Repository Pattern

```java
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByUserId(UUID userId);

    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT p FROM Portfolio p LEFT JOIN FETCH p.holdings WHERE p.id = :id")
    Optional<Portfolio> findWithHoldings(@Param("id") UUID id);
}
```

Rules:
- Use Spring Data method naming for simple queries
- Use `@Query` with JPQL for joins (avoid N+1)
- Add `userId` to all ownership-sensitive queries

## Exception Handling

```java
// GlobalExceptionHandler.java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(message, "VALIDATION_ERROR"));
    }
}

// Custom exceptions:
// ResourceNotFoundException extends RuntimeException → 404
// AccessDeniedException → 403
// BusinessRuleException → 400 (rule violation, e.g. "cannot delete active portfolio")
```

## Scheduled Jobs Pattern

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceSyncScheduler {

    private final CoinGeckoClient coinGeckoClient;
    private final AssetRepository assetRepository;
    private final PriceBroadcaster broadcaster;

    @Scheduled(fixedDelayString = "${price-api.sync-interval-minutes}000")
    public void syncCryptoPrices() {
        log.debug("Syncing crypto prices...");
        try {
            var prices = coinGeckoClient.fetchPrices(List.of("bitcoin", "ethereum"));
            prices.forEach((symbol, price) -> {
                assetRepository.findBySymbol(symbol).ifPresent(asset -> {
                    asset.setPrice(price.tryPrice());
                    asset.setPriceUpdatedAt(Instant.now());
                    assetRepository.save(asset);
                });
            });
            broadcaster.broadcastPrices();
        } catch (Exception e) {
            log.warn("Price sync failed: {}", e.getMessage());
            // Don't rethrow — scheduler must keep running
        }
    }
}
```

Rules:
- Always wrap scheduler body in try-catch — never let exceptions stop the scheduler
- Log at DEBUG level for normal operation, WARN for failures
- Use `fixedDelay` (not `fixedRate`) to avoid overlap if job takes long

## Redis Cache Pattern

```java
@Service
public class PriceService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void cachePrice(String symbol, PriceData price) {
        String key = "price:" + symbol;
        String json = objectMapper.writeValueAsString(price);
        redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(5));
    }

    public Optional<PriceData> getCachedPrice(String symbol) {
        String json = redisTemplate.opsForValue().get("price:" + symbol);
        if (json == null) return Optional.empty();
        return Optional.of(objectMapper.readValue(json, PriceData.class));
    }
}
```

## WebSocket Pattern

```java
// Config
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}

// Broadcaster
@Component
@RequiredArgsConstructor
public class PriceBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastPrices(Map<String, PriceData> prices) {
        messagingTemplate.convertAndSend("/topic/prices", prices);
    }
}
```
