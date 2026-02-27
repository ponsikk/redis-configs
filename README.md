# Travel Analytics System - Complete Redis Use Cases

Comprehensive microservices system demonstrating **all 6 Redis configurations** for travel booking analytics.

## 🎯 Project Goal

Demonstrate real-world Redis usage patterns through a travel booking system with proper microservices architecture.

---

## 📊 Redis Instances & Use Cases

| Redis Instance | Port | Config | Service | Use Case |
|----------------|------|--------|---------|----------|
| **Cache** | 6379 | allkeys-lru | flight, booking | Search results caching |
| **Session Store** | 6380 | volatile-lru, AOF | api-gateway | User sessions |
| **Rate Limiting** | 6381 | volatile-ttl, hz=20 | api-gateway | API throttling |
| **Analytics** | 6382 | allkeys-lfu, RDB+AOF | analytics | Real-time metrics |
| **Pub/Sub** | 6383 | noeviction | flight, booking, analytics | Event messaging |
| **Distributed Locks** | 6384 | appendfsync always | booking | Prevent double booking |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      API Gateway (8080)                  │
│  - Rate Limiting (Redis 6381)                           │
│  - Session Management (Redis 6380)                      │
└───────────┬─────────────────────────────────────────────┘
            │
    ┌───────┼────────────┬──────────────┐
    │       │            │              │
┌───▼───┐ ┌─▼────┐ ┌─────▼──────┐ ┌────▼────────┐
│Flight │ │Booking│ │ Analytics  │ │  Shared     │
│Service│ │Service│ │  Service   │ │  Libraries  │
│ 8081  │ │ 8082  │ │   8083     │ │             │
└───┬───┘ └──┬────┘ └─────┬──────┘ └─────────────┘
    │        │            │
    └────────┴────────────┘
             │
    ┌────────┼─────────────────────────────────┐
    │        │                                  │
┌───▼────┐ ┌▼────┐ ┌───────▼──┐ ┌────▼───┐ ┌──▼───────┐ ┌──────▼─────┐
│ Cache  │ │Locks│ │Analytics │ │ PubSub │ │RateLimit │ │  Session   │
│ Redis  │ │Redis│ │  Redis   │ │ Redis  │ │  Redis   │ │   Redis    │
│  6379  │ │6384 │ │   6382   │ │  6383  │ │   6381   │ │   6380     │
└────────┘ └─────┘ └──────────┘ └────────┘ └──────────┘ └────────────┘
```

---

## 🚀 Microservices

### 1. **API Gateway** (Port 8080)

**Responsibilities:**
- Entry point for all client requests
- Rate limiting (100 req/min per user)
- Session management (30-minute TTL)

**Redis Usage:**
- **Rate Limit (6381)**: Lua scripts for atomic counters
- **Session (6380)**: AOF-persisted user sessions

**Endpoints:**
```bash
POST /api/auth/login          # Create session
POST /api/auth/logout         # Invalidate session
GET  /api/auth/session        # Get session info
```

**Rate Limiting Example:**
```java
// Fixed window with Lua script
rateLimitService.allowRequest("user:123", 100, 60)
// Returns: true/false + X-RateLimit headers
```

---

### 2. **Flight Service** (Port 8081)

**Responsibilities:**
- CRUD operations for flights
- Search and filtering with caching
- Publish flight update events

**Redis Usage:**
- **Cache (6379)**: LRU-cached search results (15 min TTL)
- **Pub/Sub (6383)**: Publish "flight updated" events

**Endpoints:**
```bash
GET  /api/flights/search      # Cached search
GET  /api/flights/{id}        # Get flight (cached)
POST /api/flights             # Create flight
PUT  /api/flights/{id}        # Update flight (invalidates cache)
```

**Caching Flow:**
```java
1. Client searches: NYC → LAX
2. Check cache: flights:route:NYC-LAX
3. Cache MISS → Query database
4. Cache result for 15 minutes
5. Return to client
```

---

### 3. **Booking Service** (Port 8082)

**Responsibilities:**
- Create/confirm/cancel bookings
- Distributed locking for seat reservation
- Publish booking events

**Redis Usage:**
- **Locks (6384)**: Redlock for preventing double booking
- **Cache (6379)**: User bookings cache
- **Pub/Sub (6383)**: Publish booking events

**Endpoints:**
```bash
POST /api/bookings                    # Create booking (with lock)
POST /api/bookings/{ref}/confirm      # Confirm after payment
POST /api/bookings/{ref}/cancel       # Cancel booking
GET  /api/bookings/user/{userId}      # Get user bookings (cached)
```

**Distributed Lock Flow:**
```java
1. User A tries to book seat 12A
2. Acquire lock: lock:seat:flight123:12A
3. Check if seat available in DB
4. Create booking in DB
5. Release lock
6. User B tries same seat → waits or fails
```

---

### 4. **Analytics Service** (Port 8083)

**Responsibilities:**
- Real-time metrics aggregation
- Subscribe to booking/flight events via Pub/Sub
- Provide dashboard data

**Redis Usage:**
- **Analytics (6382)**: Sorted Sets, HyperLogLog, Counters, Hashes
- **Pub/Sub (6383)**: Subscribe to events from other services

**Endpoints:**
```bash
GET /api/analytics/dashboard          # All metrics
GET /api/analytics/bookings/total     # Total bookings counter
GET /api/analytics/routes/top         # Top 10 routes (Sorted Set)
GET /api/analytics/revenue/daily      # Daily revenue
GET /api/analytics/users/unique       # Unique users (HyperLogLog)
```

**Event Processing:**
```java
1. Booking service publishes: events:booking:created
2. Analytics subscribes to events:booking:*
3. On event received:
   - INCR analytics:bookings:total
   - ZINCRBY analytics:popular:routes NYC-LAX 1
   - PFADD analytics:unique:users:2024-03-15 user123
   - INCRBY analytics:revenue:daily:2024-03-15 250
```

**Redis Data Structures Used:**
- **Counters**: `INCR analytics:bookings:total`
- **Sorted Sets**: `ZADD analytics:popular:routes NYC-LAX 1542`
- **HyperLogLog**: `PFADD analytics:unique:users:2024-03-15 user123`
- **Hashes**: `HSET analytics:stats:flight:123 totalBookings 1542`

---

## 📚 Shared Libraries

### shared-libs/cache-client
Generic caching service used by all microservices.

```java
@Autowired
private CacheService cacheService;

// Cache with TTL
cacheService.cache("flights:route:NYC-LAX", flights, 900);

// Get from cache
List<Flight> cached = cacheService.getList("flights:route:NYC-LAX");

// Invalidate
cacheService.invalidate("flights:route:NYC-LAX");
```

### shared-libs/event-bus
Pub/Sub event publisher used by all microservices.

```java
@Autowired
private EventPublisher eventPublisher;

// Publish event
eventPublisher.publish("events:booking:created", bookingEvent);
```

### shared-libs/redis-config
Redis connection configurations for Cache and Pub/Sub.

---

## 🔧 Running the System

### 1. Start Redis Instances

```bash
# Cache Redis
redis-server redis-configs/caching/redis.conf

# Session Store Redis
redis-server redis-configs/session-store/redis.conf

# Rate Limiting Redis
redis-server redis-configs/rate-limiting/redis.conf

# Analytics Redis
redis-server redis-configs/realtime-analytics/redis.conf

# Pub/Sub Redis
redis-server redis-configs/pubsub/redis.conf

# Distributed Locks Redis
redis-server redis-configs/distributed-lock/redis.conf
```

### 2. Start Microservices

```bash
# API Gateway (must start first for rate limiting)
cd api-gateway && mvn spring-boot:run

# Flight Service
cd flight-service && mvn spring-boot:run

# Booking Service
cd booking-service && mvn spring-boot:run

# Analytics Service (starts Pub/Sub listener)
cd analytics-service && mvn spring-boot:run
```

---

## 📝 Usage Examples

### Example 1: Search Flights (with caching and rate limiting)

```bash
# Request through API Gateway (rate limited)
curl -X GET "http://localhost:8080/api/flights/search?origin=NYC&destination=LAX&startDate=2024-03-15T00:00:00&endDate=2024-03-16T00:00:00" \
  -H "X-User-Id: user123"

# Response headers:
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99

# First request: Cache MISS → DB query
# Second request: Cache HIT → instant response
```

### Example 2: Create Booking (with distributed lock)

```bash
# Login first to get session
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user123","password":"pass"}'

# Response:
{"sessionId":"550e8400-e29b-41d4-a716-446655440000","userId":"user123"}

# Create booking (seat 12A locked)
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user123" \
  -H "X-Session-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "flightId": 1,
    "passengerName": "John Doe",
    "passengerEmail": "john@example.com",
    "seatNumber": "12A",
    "price": 250.00
  }'

# What happens:
# 1. Rate limit check (Redis 6381)
# 2. Session validation (Redis 6380)
# 3. Distributed lock acquired (Redis 6384): lock:seat:1:12A
# 4. Booking created in DB
# 5. Lock released
# 6. Event published (Redis 6383): events:booking:created
# 7. Analytics listens and updates metrics (Redis 6382)
```

### Example 3: View Analytics Dashboard

```bash
curl http://localhost:8083/api/analytics/dashboard

# Response:
{
  "totalBookings": 1542,
  "todayRevenue": 38500.00,
  "todayUniqueUsers": 287,
  "topRoutes": [
    {"NYC-LAX": 543},
    {"LAX-SFO": 421},
    {"JFK-MIA": 389}
  ],
  "topFlights": [
    {"123": 234},
    {"456": 198}
  ]
}
```

---

## 📖 Redis Configuration Details

### 1. Cache (6379) - LRU Eviction

```conf
maxmemory 2gb
maxmemory-policy allkeys-lru
save ""  # No persistence
appendonly no
```

**Why?**
- Data can be regenerated from DB
- Speed over durability
- Automatic eviction of old data

---

### 2. Session Store (6380) - AOF Persistence

```conf
maxmemory 1gb
maxmemory-policy volatile-lru
appendonly yes
appendfsync everysec
```

**Why?**
- Sessions must survive restarts
- 1 second data loss acceptable
- Evicts only keys with TTL

---

### 3. Rate Limiting (6381) - High Frequency Expiration

```conf
maxmemory 512mb
maxmemory-policy volatile-ttl
hz 20  # Check expired keys 20x/sec
appendonly no
```

**Why?**
- Counters expire quickly (60s windows)
- No persistence needed (reset on restart OK)
- Fast cleanup critical

---

### 4. Analytics (6382) - LFU + Hybrid Persistence

```conf
maxmemory 4gb
maxmemory-policy allkeys-lfu
save 900 1
appendonly yes
activedefrag yes
```

**Why?**
- LFU keeps frequently queried metrics
- Both RDB + AOF for durability
- Active defrag for long-running instance

---

### 5. Pub/Sub (6383) - No Eviction

```conf
maxmemory 256mb
maxmemory-policy noeviction
save ""
appendonly no
client-output-buffer-limit pubsub 32mb 8mb 60
```

**Why?**
- Pub/Sub doesn't store data
- Fire-and-forget messaging
- Buffer limits protect against slow subscribers

---

### 6. Distributed Locks (6384) - Maximum Durability

```conf
maxmemory 512mb
maxmemory-policy volatile-ttl
appendonly yes
appendfsync always  # Sync every write!
hz 20
```

**Why?**
- Locks must survive crashes
- appendfsync always for consistency
- Performance trade-off acceptable for correctness

---

## 🧪 Testing Each Redis Use Case

### Test Cache (6379)
```bash
redis-cli -p 6379
> KEYS flights:*
> GET flights:route:NYC-LAX
> TTL flights:route:NYC-LAX
```

### Test Session (6380)
```bash
redis-cli -p 6380
> KEYS session:*
> GET session:550e8400-e29b-41d4-a716-446655440000
> TTL session:550e8400-e29b-41d4-a716-446655440000
```

### Test Rate Limit (6381)
```bash
redis-cli -p 6381
> KEYS ratelimit:*
> GET ratelimit:user:123:28506720
```

### Test Analytics (6382)
```bash
redis-cli -p 6382
> GET analytics:bookings:total
> ZREVRANGE analytics:popular:routes 0 9 WITHSCORES
> PFCOUNT analytics:unique:users:2024-03-15
```

### Test Pub/Sub (6383)
```bash
# Terminal 1 (subscriber)
redis-cli -p 6383
> SUBSCRIBE events:booking:*

# Terminal 2 (publisher)
redis-cli -p 6383
> PUBLISH events:booking:created '{"bookingId":123}'
```

### Test Locks (6384)
```bash
redis-cli -p 6384
> KEYS lock:*
> GET lock:seat:1:12A
> TTL lock:seat:1:12A
```

---

## 📚 Documentation

Detailed configuration docs for each Redis instance:
- [01-caching-configuration.md](docs/redis-configurations/01-caching-configuration.md)
- [02-session-store-configuration.md](docs/redis-configurations/02-session-store-configuration.md)
- [03-rate-limiting-configuration.md](docs/redis-configurations/03-rate-limiting-configuration.md)
- [04-realtime-analytics-configuration.md](docs/redis-configurations/04-realtime-analytics-configuration.md)
- [05-pubsub-configuration.md](docs/redis-configurations/05-pubsub-configuration.md)
- [06-distributed-lock-configuration.md](docs/redis-configurations/06-distributed-lock-configuration.md)

---

## ✅ All 6 Redis Use Cases Covered

| # | Redis Instance | Port | Status | Implementation |
|---|----------------|------|--------|----------------|
| 1 | Cache | 6379 | ✅ | flight-service, booking-service |
| 2 | Session Store | 6380 | ✅ | api-gateway (SessionService) |
| 3 | Rate Limiting | 6381 | ✅ | api-gateway (RateLimitService) |
| 4 | Analytics | 6382 | ✅ | analytics-service (AnalyticsService) |
| 5 | Pub/Sub | 6383 | ✅ | All services (EventPublisher/Subscriber) |
| 6 | Distributed Locks | 6384 | ✅ | booking-service (BookingLockService) |

---

## 🎓 Key Learnings

### When to Use Each Redis Config

✅ **Cache (LRU)**: Read-heavy, data can be regenerated
✅ **Session (AOF)**: Critical data, must survive restarts
✅ **Rate Limit (volatile-ttl)**: High throughput, transient data
✅ **Analytics (LFU)**: Frequently accessed metrics, long-running
✅ **Pub/Sub (noeviction)**: Fire-and-forget messaging
✅ **Locks (appendfsync always)**: Consistency over performance

---

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3.2.2**
- **Redis 7.0+**
- **PostgreSQL 14+**
- **Maven 3.8+**
- **Redisson 3.24.3** (for distributed locks)

---

## 📄 License

MIT License - Educational project demonstrating Redis configurations
