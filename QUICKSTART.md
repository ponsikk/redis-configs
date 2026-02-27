# Quick Start Guide

## 🚀 Option 1: Docker Compose (Recommended)

### Prerequisites
- Docker Desktop installed
- Docker Compose installed

### Start All Redis Instances

```bash
# Start all 6 Redis instances in Docker
docker-compose up -d

# Verify all instances are running
docker-compose ps

# Check health
docker exec redis-cache redis-cli PING
docker exec redis-session redis-cli PING
docker exec redis-ratelimit redis-cli PING
docker exec redis-analytics redis-cli PING
docker exec redis-pubsub redis-cli PING
docker exec redis-locks redis-cli PING
```

### Build and Test

```bash
# Build shared libraries
cd shared-libs/redis-config && mvn clean install -DskipTests
cd ../cache-client && mvn clean install -DskipTests
cd ../event-bus && mvn clean install -DskipTests

# Build microservices
cd ../../api-gateway && mvn clean install -DskipTests
cd ../flight-service && mvn clean install -DskipTests
cd ../hotel-service && mvn clean install -DskipTests
cd ../booking-service && mvn clean install -DskipTests
cd ../analytics-service && mvn clean install -DskipTests

# Run integration tests
cd ../integration-tests
mvn test
```

### Run Specific Tests

```bash
cd integration-tests

# Test Cache Redis (6379)
mvn test -Dtest=CacheRedisTest

# Test Session Redis (6380)
mvn test -Dtest=SessionRedisTest

# Test Rate Limiting Redis (6381)
mvn test -Dtest=RateLimitRedisTest

# Test Analytics Redis (6382)
mvn test -Dtest=AnalyticsRedisTest

# Test Pub/Sub Redis (6383)
mvn test -Dtest=PubSubRedisTest

# Test Distributed Locks Redis (6384)
mvn test -Dtest=DistributedLockRedisTest
```

### Stop All Redis Instances

```bash
# Stop and remove containers
docker-compose down

# Stop and remove containers + volumes (clean slate)
docker-compose down -v
```

---

## 🔧 Option 2: Manual Redis Installation

### Start Redis Instances

Open 6 separate terminals:

```bash
# Terminal 1 - Cache Redis
redis-server redis-configs/caching/redis.conf

# Terminal 2 - Session Redis
redis-server redis-configs/session-store/redis.conf

# Terminal 3 - Rate Limiting Redis
redis-server redis-configs/rate-limiting/redis.conf

# Terminal 4 - Analytics Redis
redis-server redis-configs/realtime-analytics/redis.conf

# Terminal 5 - Pub/Sub Redis
redis-server redis-configs/pubsub/redis.conf

# Terminal 6 - Distributed Locks Redis
redis-server redis-configs/distributed-lock/redis.conf
```

Then follow the build and test steps from Option 1.

---

## 🎯 GitHub Actions CI/CD

### Automated Testing

Every push to `main` or `develop` branch automatically:

1. ✅ Starts all 6 Redis instances in Docker
2. ✅ Builds all Maven modules
3. ✅ Runs all integration tests
4. ✅ Generates test reports

### View Test Results

1. Go to your repository on GitHub
2. Click "Actions" tab
3. View the latest workflow run
4. Download test reports from "Artifacts"

---

## 📊 Monitoring Redis Instances

### Check Memory Usage

```bash
docker exec redis-cache redis-cli INFO memory | grep used_memory_human
docker exec redis-session redis-cli INFO memory | grep used_memory_human
```

### Monitor Cache Hit Ratio

```bash
docker exec redis-cache redis-cli INFO stats | grep keyspace_hits
docker exec redis-cache redis-cli INFO stats | grep keyspace_misses
```

### View All Keys

```bash
docker exec redis-cache redis-cli KEYS "*"
docker exec redis-session redis-cli KEYS "session:*"
docker exec redis-ratelimit redis-cli KEYS "ratelimit:*"
docker exec redis-analytics redis-cli KEYS "analytics:*"
docker exec redis-locks redis-cli KEYS "lock:*"
```

### Monitor Pub/Sub Channels

```bash
# Terminal 1 - Subscribe
docker exec -it redis-pubsub redis-cli
> SUBSCRIBE events:booking:*

# Terminal 2 - Publish
docker exec -it redis-pubsub redis-cli
> PUBLISH events:booking:created '{"bookingId": 123}'
```

---

## 🐛 Troubleshooting

### Port Already in Use

```bash
# Check what's using port 6379
netstat -ano | findstr :6379

# Kill the process (Windows)
taskkill /PID <PID> /F

# Or change ports in docker-compose.yml
```

### Maven Build Fails

```bash
# Clean all Maven projects
mvn clean

# Rebuild shared-libs first (dependency order matters!)
cd shared-libs/redis-config && mvn clean install -DskipTests
cd ../cache-client && mvn clean install -DskipTests
cd ../event-bus && mvn clean install -DskipTests
```

### Redis Connection Refused

```bash
# Check if Redis is running
docker-compose ps

# Restart specific instance
docker-compose restart redis-cache

# View logs
docker-compose logs redis-cache
```

### Tests Failing

```bash
# Check Redis health
docker exec redis-cache redis-cli PING
docker exec redis-session redis-cli PING
docker exec redis-ratelimit redis-cli PING
docker exec redis-analytics redis-cli PING
docker exec redis-pubsub redis-cli PING
docker exec redis-locks redis-cli PING

# All should return "PONG"
```

---

## 📦 One-Line Commands

### Quick Start Everything

```bash
# Start Redis + Build + Test
docker-compose up -d && \
cd shared-libs/redis-config && mvn clean install -DskipTests && \
cd ../cache-client && mvn clean install -DskipTests && \
cd ../event-bus && mvn clean install -DskipTests && \
cd ../../integration-tests && mvn test
```

### Quick Stop Everything

```bash
docker-compose down -v
```

---

## 🎓 Next Steps

1. ✅ Start Redis with Docker Compose
2. ✅ Run integration tests
3. ✅ Review test reports
4. ✅ Push to GitHub to trigger CI/CD
5. ✅ Start microservices for end-to-end testing
