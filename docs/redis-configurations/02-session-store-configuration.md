# Redis Session Store Configuration

## Use Case Overview

This Redis instance is configured as a **session store** for user authentication and session management across multiple microservices. It ensures user sessions persist across service restarts and provides fast session lookup for every API request.

## Key Configuration Details

### Memory Management

```conf
maxmemory 1gb
maxmemory-policy volatile-lru
```

**Why these settings?**

- **maxmemory 1gb**: Sessions typically small (1-10KB each), 1GB handles 100k-1M sessions
- **volatile-lru**: Only evicts keys WITH TTL set (sessions always have expiration). Protects keys without TTL from eviction

### Persistence - AOF Enabled

```conf
appendonly yes
appendfilename "sessions.aof"
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
```

**Why AOF for sessions?**

- **appendonly yes**: Every write is logged to disk
- **appendfsync everysec**: Sync to disk every second (balance between durability and performance)
- Sessions are **critical data** - losing them logs out all users
- AOF provides better durability than RDB snapshots
- In case of crash, worst case is 1 second of data loss

### Persistence Trade-off

| Strategy | Data Loss Risk | Performance |
|----------|----------------|-------------|
| appendfsync always | ~0ms (safest) | Slowest |
| appendfsync everysec | ~1 second | Balanced ✓ |
| appendfsync no | Up to 30s | Fastest |

**We chose `everysec`** - good balance for session data where 1 second loss is acceptable.

### Key Expiration Notifications

```conf
notify-keyspace-events Ex
hz 10
```

**Why notifications?**

- **Ex**: Notifies when keys expire
- Useful for triggering logout events, cleanup tasks, or analytics
- Application can subscribe to `__keyevent@0__:expired` channel

### Network Configuration

```conf
timeout 0
tcp-keepalive 60
```

- **timeout 0**: Never close idle connections (important for persistent session lookups)
- **tcp-keepalive 60**: Check connection health every 60 seconds

## Typical Use Cases in the System

### 1. User Session Storage

```java
// Store session after login
@Autowired
private RedisTemplate<String, Object> redisTemplate;

public void createSession(String sessionId, UserSession session) {
    String key = "session:" + sessionId;
    redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);
}

// Retrieve session on each request
public UserSession getSession(String sessionId) {
    String key = "session:" + sessionId;
    return (UserSession) redisTemplate.opsForValue().get(key);
}
```

### 2. Session Extension

```java
// Extend session TTL on user activity
public void extendSession(String sessionId) {
    String key = "session:" + sessionId;
    redisTemplate.expire(key, 30, TimeUnit.MINUTES);
}
```

### 3. Active Users Tracking

```java
// Get all active sessions
public Set<String> getActiveSessions() {
    return redisTemplate.keys("session:*");
}
```

### 4. Multi-device Session Management

```java
// Store multiple sessions per user
String key = "user:sessions:" + userId;
redisTemplate.opsForSet().add(key, sessionId);
redisTemplate.expire(key, 30, TimeUnit.MINUTES);
```

## Session Data Structure Examples

### Basic Session Object

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user123",
  "email": "user@example.com",
  "roles": ["USER", "PREMIUM"],
  "createdAt": 1709000000,
  "lastAccessedAt": 1709003600,
  "ipAddress": "192.168.1.1"
}
```

### Shopping Cart in Session

```java
// Store cart as part of session or separate key
String cartKey = "session:cart:" + sessionId;
redisTemplate.opsForHash().put(cartKey, "flight:123", bookingDetails);
```

## Performance Characteristics

| Metric | Expected Value |
|--------|----------------|
| Read latency | < 2ms |
| Write latency | < 5ms (with AOF) |
| Throughput | 50k+ ops/sec |
| Session lookup | Every API request |
| Typical session size | 1-10 KB |

## Monitoring Recommendations

```bash
# Check number of sessions
redis-cli DBSIZE

# Monitor memory per session
redis-cli --bigkeys

# Check AOF file size
ls -lh /var/lib/redis/sessions.aof

# Monitor expired keys (logged out sessions)
redis-cli INFO stats | grep expired_keys

# Check persistence status
redis-cli INFO persistence
```

## Session Cleanup Strategy

### Automatic Cleanup (Redis built-in)

1. **Passive expiration**: Key accessed → Redis checks TTL → Deletes if expired
2. **Active expiration**: Background task (hz=10) randomly tests keys and deletes expired ones

### Manual Cleanup

```bash
# Find sessions older than X
redis-cli --scan --pattern "session:*" | while read key; do
  ttl=$(redis-cli TTL "$key")
  if [ $ttl -eq -1 ]; then
    echo "Session without TTL: $key"
  fi
done
```

## Best Practices

### 1. Always Set TTL

```java
// BAD: Session never expires
redisTemplate.opsForValue().set(key, session);

// GOOD: Session expires after 30 minutes
redisTemplate.opsForValue().set(key, session, 30, TimeUnit.MINUTES);
```

### 2. Sliding Expiration

```java
@Component
public class SessionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null) {
            sessionService.extendSession(sessionId); // Refresh TTL
        }
        return true;
    }
}
```

### 3. Session Key Patterns

```
session:{sessionId}                  // Main session object
session:cart:{sessionId}             // Shopping cart
user:sessions:{userId}               // All sessions for a user
session:temp:{verificationToken}     // Temporary verification sessions
```

### 4. Handle AOF Rewrites

```bash
# AOF can grow large, Redis automatically rewrites it
# Monitor and manually trigger if needed:
redis-cli BGREWRITEAOF
```

## Configuration Trade-offs

| Setting | Pro | Con |
|---------|-----|-----|
| AOF everysec | Good durability | Slight performance overhead |
| volatile-lru | Protects critical keys | Must set TTL on all sessions |
| notify-keyspace-events | React to expirations | Small performance cost |

## Security Considerations

1. **Session ID generation**
   - Use cryptographically secure random IDs (UUID v4, JWT)
   - Never use sequential IDs

2. **Session hijacking prevention**
   - Store IP address and User-Agent in session
   - Validate on each request

3. **Secure session data**
   - Don't store passwords or credit card numbers
   - Encrypt sensitive session data

## When to Use This Configuration

✅ **Good for:**
- User authentication systems
- Multi-service architectures (shared session store)
- Session data that must survive restarts
- Compliance requirements (audit trails via AOF)

❌ **Not suitable for:**
- Very high write throughput (AOF overhead)
- Applications where 1 second data loss is unacceptable
- Stateless JWT-only authentication (no server-side sessions)

## Scaling Considerations

### Single Instance (Current Config)

- **Capacity**: ~100k-1M concurrent sessions (depending on session size)
- **Limitation**: Single point of failure

### High Availability Options

1. **Redis Sentinel**: Automatic failover, master-slave replication
2. **Redis Cluster**: Horizontal scaling, data sharding
3. **Redis Enterprise**: Multi-datacenter replication

```conf
# For Sentinel setup, add to configuration:
# replica-read-only yes
# min-replicas-to-write 1
# min-replicas-max-lag 10
```
