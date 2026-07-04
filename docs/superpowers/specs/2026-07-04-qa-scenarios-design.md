# Q&A Scenarios — Ecom Microservices Interview Prep — Design Spec

> **Date**: 2026-07-04
> **Status**: Draft (awaiting user review)
> **Author**: brainstorming session
> **Related**: `.claude/knowledge-map.md` (full system map), `docs/redis-setup-documentation.html`, `docs/kafka-setup-documentation.html`

---

## 1. Context

User yêu cầu tạo bộ câu hỏi & câu trả lời về **các tình huống thực tế** có thể gặp trong project **Ecom** (food-delivery microservices) và trong **e-commerce nói chung**, đóng gói thành **một file HTML tĩnh** dùng để ôn tập / chuẩn bị phỏng vấn / kiểm tra kiến thức nội bộ.

Qua 3 câu hỏi clarifying đã chốt với user:

- **Mục đích**: Chuẩn bị phỏng vấn / kiểm tra kiến thức (senior/staff level).
- **Phạm vi kiến thức**: **70% project Ecom** (file:line cụ thể) + **30% e-commerce tổng quát** (kiến thức nền mở rộng).
- **Tương tác HTML**: **Accordion** — click câu hỏi để hiện/ẩn đáp án. Có filter theo category, search box, progress tracking.
- **Tổng số câu**: **33 câu** (saga-kafka: 6, outbox-idem: 5, jwt-sec: 4, redis-cart: 5, tech-debt: 6, ecom-general: 7).

Mục tiêu: Bộ Q&A phải **sâu, scenario-based, có tham chiếu file:line thật** trong repo, người đọc phải hiểu được flow end-to-end của Ecom và vận dụng được kiến thức e-commerce rộng hơn.

---

## 2. Approach

**Approach A: Single-file static HTML** (chốt với user).

Một file `qa-scenarios.html` duy nhất, HTML + CSS + JS inline, không phụ thuộc CDN/framework. Có thể mở trực tiếp trong browser offline. Lý do: đơn giản, portable, dễ commit vào git, không cần build step.

**Từ chối**:
- Approach B (multi-page + framework React): Overkill cho một tài liệu ôn tập.
- Approach C (Markdown thuần): Không có tương tác accordion, search, filter.

---

## 3. File output

**Vị trí**: `D:\project\Ecom\docs\qa-scenarios.html`

Cùng cấp với `docs/redis-setup-documentation.html` và `docs/kafka-setup-documentation.html` đã có sẵn.

**Cấu trúc file**:
```
<!doctype html>
<html lang="vi" data-theme="dark">
  <head>
    <meta charset="utf-8">
    <title>Ecom Q&A — Phỏng vấn Microservices</title>
    <style> ... CSS (dark + light theme) ... </style>
  </head>
  <body>
    <header>
      <h1>📚 Ecom Q&A — Phỏng vấn Microservices</h1>
      <p class="subtitle">70% project Ecom · 30% e-commerce · 33 câu scenario-based</p>
      <div class="toolbar">
        <input id="search" placeholder="🔍 Tìm trong câu hỏi / đáp án...">
        <button id="theme-toggle">🌗 Theme</button>
      </div>
      <div class="filters">
        <button class="chip active" data-filter="all">Tất cả (33)</button>
        <button class="chip" data-filter="kafka-saga">Saga & Kafka (6)</button>
        <button class="chip" data-filter="outbox-idempotency">Outbox & Idempotency (5)</button>
        <button class="chip" data-filter="jwt-security">JWT & Security (4)</button>
        <button class="chip" data-filter="redis-cart">Redis & Cart (5)</button>
        <button class="chip" data-filter="tech-debt">Tech-debt & Debug (6)</button>
        <button class="chip" data-filter="ecom-general">E-commerce tổng quát (7)</button>
      </div>
      <div class="progress">Đã mở: <span id="opened-count">0</span> / 33</div>
    </header>
    <main>
      <!-- 28 <article class="qa-card"> -->
    </main>
    <footer>
      <p>Tham chiếu: <code>BE/common</code> · <code>BE/config-repo</code> · <code>.claude/knowledge-map.md</code></p>
    </footer>
    <script>... JS (accordion, filter, search, theme, localStorage) ...</script>
  </body>
</html>
```

---

## 4. Nội dung — 28 câu hỏi, 6 category

### 4.1 Saga & Kafka (6 câu)

| # | Câu hỏi (tóm tắt) | Đáp án highlight | Tham chiếu |
|---|---------------------|------------------|------------|
| Q1 | Order publish `inventory.reservation-requested` xuống Kafka. Nếu inventory consume xong nhưng CHƯA kịp publish `inventory.reserved` mà order timeout thì sao? Compensation chạy thế nào? | Kafka retry 3 lần/1s (`KafkaErrorHandler`); nếu fail cuối → throw; order vẫn PENDING, sau 30s timeout client retry. Compensation: `cancelAfterPaymentFailure` gọi `InventoryClient.release` qua Feign. | `BE/common/.../config/KafkaErrorHandler.java:19-55`, `BE/order-service/.../service/OrderService.java:91-101` |
| Q2 | Tại sao partition key của `inventory.reservation-requested` là `order.id()` chứ không phải `product.id()`? | Đảm bảo tất cả event của 1 order vào cùng partition → xử lý in-order, tránh race giữa 2 items cùng order. Nếu key=productId, 2 items khác partition có thể được consume concurrent → double-reserve. | `BE/order-service/.../messaging/OrderEventProducer.java:19-29` |
| Q3 | `ack-mode: RECORD` nghĩa là gì? Nếu handler throw exception ở giữa thì offset có commit không? | RECORD = commit ngay sau khi handler return. Nếu throw → `DefaultErrorHandler` retry 3 lần/1s; fail cuối cùng → log error, **KHÔNG commit offset** (theo docs Spring Kafka), message sẽ được re-deliver khi consumer restart. Trade-off: có thể mất event nếu retry hết mà vẫn fail. | `BE/common/.../config/KafkaErrorHandler.java:19-55` |
| Q4 | Order state machine: PENDING → RESERVED → CONFIRMED. Nếu payment.failed nhận được khi order đang ở RESERVED thì xử lý release inventory thế nào? | `PaymentEventConsumer.handleFailed` → `OrderService.cancelAfterPaymentFailure(orderId)` → gọi `InventoryClient.release(reservationId)` qua Feign → publish `order.cancelled` Kafka. Inventory consumer sẽ set status RELEASED. | `BE/order-service/.../messaging/PaymentEventConsumer.java:39-54`, `BE/order-service/.../service/OrderService.java:91-101` |
| Q5 | Nếu Kafka broker down giữa lúc order-service đang publish `order.confirmed`, event có bị mất không? | Có nguy cơ. Default `acks=1` (leader only). Để đảm bảo at-least-once: set `acks=all` + `enable.idempotence=true` trên producer. Hiện tại project chưa config → có thể mất event khi leader fail ngay sau khi write. | Tech debt: thiếu `acks=all` config (chưa list trong knowledge-map #13, đề xuất bổ sung) |
| Q6 | Saga pattern trong Ecom có phải là Orchestration hay Choreography? Tại sao? | **Choreography**: order-service, inventory-service, payment-service, notification-service tự react với event Kafka, không có central orchestrator. Ưu: loose coupling, dễ thêm service mới. Nhược: khó trace toàn bộ flow, cần distributed tracing (Zipkin). | `BE/.../messaging/*EventConsumer.java` (mỗi service tự listen) |

### 4.2 Outbox & Idempotency (5 câu)

| # | Câu hỏi | Đáp án | Tham chiếu |
|---|---------|--------|------------|
| Q7 | Giải thích Transactional Outbox pattern. Tại sao payment-service dùng mà order-service thì không? | Outbox = lưu event vào bảng `outbox_events` **trong cùng transaction** với business write → đảm bảo atomic. Scheduler publish lên Kafka sau. Order-service publish trực tiếp trong handler → có thể mất event nếu Kafka down sau khi DB commit. | `BE/payment-service/.../outbox/OutboxPublisher.java:22-36`, `BE/payment-service/.../service/PaymentService.java:43-53, 107-115` |
| Q8 | OutboxPublisher chạy `fixedDelay 5s`. Nếu scheduler restart giữa lúc gửi Kafka thì event có bị trùng không? | **Có thể**. `repository.save(event)` được gọi bên ngoài transaction (sau khi send return future); nếu crash giữa → status vẫn PENDING → scheduler kế tiếp gửi lại. Đây là tech-debt #3. Fix: dùng `SELECT ... FOR UPDATE SKIP LOCKED` hoặc version-based optimistic lock. | Tech debt #3 trong `.claude/knowledge-map.md:286-305` |
| Q9 | Idempotency-Key `pay-{orderId}` ở POST /api/payments. Nếu client gọi 2 lần với cùng key thì sao? | Lần 1: tạo Payment mới, status PENDING. Lần 2: `findByIdempotencyKey` trả về payment cũ → trả lại response cũ (idempotent). UNIQUE constraint ở DB đảm bảo chỉ 1 record. Trade-off: client phải tự generate key ổn định. | `BE/payment-service/.../service/PaymentService.java:36-41`, `BE/database/payment-service/V1__create_payment_tables.sql:10` |
| Q10 | Mock webhook `POST /api/payments/webhooks/mock` xử lý duplicate `providerEventId` thế nào? | `PaymentWebhookService.handle` gọi `existsById(providerEventId)` → nếu có, return ngay (no-op). Nếu chưa → xử lý + save `payment_webhook_events` với `provider_event_id` PK. Idempotent ở DB layer. | `BE/payment-service/.../service/PaymentWebhookService.java:24-38` |
| Q11 | Nếu 2 client cùng `POST /api/payments` đồng thời với 2 `idempotency_key` khác nhau cho cùng 1 order? | Cả 2 đều tạo Payment. UNIQUE constraint ở `payments.order_id` chỉ cho phép 1 payment/order → request thứ 2 fail với `DataIntegrityViolationException` → trả 409 Conflict. Phòng chống double-charge. | `BE/database/payment-service/V1__create_payment_tables.sql:7` (`order_id UNIQUE`) |

### 4.3 JWT & Security (4 câu)

| # | Câu hỏi | Đáp án | Tham chiếu |
|---|---------|--------|------------|
| Q12 | JWT HS256 dùng secret chung giữa auth-service và api-gateway. Nếu secret leak thì sao? Cách mitigate? | Attacker có thể tự forge token với role ADMIN → truy cập `/api/admin/**`. Mitigate: rotate secret thường xuyên (auth-service enforce ≥ 32 bytes, gateway KHÔNG enforce — tech-debt #11), dùng RS256 với public key, lưu secret ở Vault/KMS. | `BE/auth-service/.../security/JwtTokenService.java:85-91`, tech-debt #11 |
| Q13 | Gateway set 3 header `X-User-Id/Email/Roles` xuống downstream. Nếu attacker gửi thẳng `X-User-Roles: ADMIN` đến payment-service (bypass gateway) thì sao? | **Lỗ hổng nghiêm trọng**. Downstream `GatewayUserContextFilter` chỉ đọc header, không verify chữ ký. Mỗi service phải enforce ở `application.yml` rằng CHỈ chấp nhận request từ gateway (qua network policy / firewall) HOẶC verify lại JWT. Hiện tại chỉ tin tưởng gateway — chấp nhận risk. | `BE/cart-service/.../security/GatewayUserContextFilter.java:13-34` |
| Q14 | Refresh token lưu DB dạng SHA-256 hash, không lưu plaintext. Tại sao? | Nếu DB leak (SQL injection, backup lộ), attacker không dùng được refresh token để lấy access token mới. Hash 1 chiều + UNIQUE constraint → chỉ owner mới có thể dùng kèm session hợp lệ. Logout set `revoked_at` để invalidate. | `BE/auth-service/.../service/AuthService.java:95-109` |
| Q15 | Tại sao `Authorization` header phải `Bearer ` mà không parse trực tiếp token? | Theo RFC 6750 OAuth 2.0. Cho phép mở rộng scheme sau này (MAC, Digest). Đồng thời phân biệt rõ authentication scheme. Một số proxy/cdn có thể strip header nếu format lạ. | `BE/api-gateway/.../security/JwtAuthenticationFilter.java:34-41` |

### 4.4 Redis & Cart (5 câu)

| # | Câu hỏi | Đáp án | Tham chiếu |
|---|---------|--------|------------|
| Q16 | Cart lưu Redis JSON với TTL 30 ngày. Tại sao không lưu DB? | Cart là data ephemeral, user có thể bỏ, không cần durability tuyệt đối. Redis read/write nhanh hơn DB 10-100x. TTL tự động cleanup → không cần GC job. Trade-off: nếu Redis down → cart unavailable (cache-aside fallback chưa implement). | `BE/cart-service/.../repository/CartRepository.java:21, 116-129` |
| Q17 | `findByUserIdOrCreate` dùng `Thread.sleep(50)` + retry đệ quy khi lock contention. Rủi ro? | **Stack overflow** nếu lock bị giữ quá 5s (TTL). Tech-debt #2. Fix tốt hơn: bounded retry với exponential backoff, hoặc dùng Redisson distributed lock. Hiện tại chỉ phù hợp với traffic thấp. | `BE/cart-service/.../repository/CartRepository.java:107-114`, tech-debt #2 |
| Q18 | Product cache dùng `@Cacheable` KHÔNG cấu hình TTL. Nếu admin update product nhưng quên `changeStatus` thì sao? | Stale data **vĩnh viễn** vì không có TTL → chỉ evict khi `createProduct`/`updateProduct`/`changeStatus` gọi `@CacheEvict(allEntries=true)`. Tech-debt #9. Fix: set TTL 5-10 phút cho `product-detail`, hoặc dùng Redis `@TimeToLive`. | Tech-debt #9, `BE/product-service/.../service/ProductCatalogService.java:52-88` |
| Q19 | `CartClient` trong order-service gọi `GET /api/cart` qua OpenFeign. Nếu cart-service chậm 3s thì checkout mất bao lâu? | Cộng dồn: `getCart` 3s + `clearCart` ~50ms + reserve publish ~10ms + Kafka roundtrip ~100ms = **~3.2s**. Đây là lý do cần Circuit Breaker + Timeout (đã có Resilience4j trong commits gần đây). Bulkhead fail-fast. | `BE/order-service/.../service/OrderService.java:30-40` |
| Q20 | Lua script `SET_IF_SAME_SCRIPT` trong cart chỉ `GET`, fallback tạo mới bằng Java. Có race condition không? | **Có**. Nếu 2 request cùng lúc cho user mới: cả 2 đều GET miss → cả 2 đều tạo cart mới → cả 2 setIfAbsent lock → 1 được, 1 sleep retry. Khi retry, lock đã release, GET lại vẫn miss → loop. Tech-debt #10. Fix: viết Lua atomic `GET or CREATE`. | `BE/cart-service/.../repository/CartRepository.java:29-36`, tech-debt #10 |

### 4.5 Tech-debt & Troubleshooting (6 câu)

| # | Câu hỏi | Đáp án | Tham chiếu |
|---|---------|--------|------------|
| Q21 | Order PENDING quá 10 phút không chuyển RESERVED. Debug thế nào? | 1) Check Zipkin UI `localhost:9411` filter `orderId`. 2) Xem `inventory-service` log xem có nhận `inventory.reservation-requested` không. 3) Xem Kafka lag bằng `kafka-consumer-groups.sh --describe --group inventory-service`. 4) Check `outbox_events` ở payment xem có payment.succeeded. 5) Có thể do race: product out-of-stock. | `.claude/knowledge-map.md:269-305` (tech debt 1-17) |
| Q22 | Notification email không gửi (log chỉ thấy "Mock email to=..."). Production phải làm gì? | Replace `MockEmailSender` (chỉ log INFO) bằng `JavaMailSender` + SMTP credentials hoặc SendGrid SDK. Update DI bean. Cũng cần lookup email thật từ `user-service` qua Feign (tech-debt #1: hiện hardcode `user-{uuid}@example.test`). | Tech debt #1, #8, `BE/notification-service/.../service/MockEmailSender.java:11-13` |
| Q23 | Prometheus scrape config chỉ list 3/11 service. 8 business service không có metric trong Grafana. Fix? | Thêm 8 job vào `BE/infra/prometheus/prometheus.yml` cho port 8081-8088, mỗi job scrape `/actuator/prometheus` mỗi 15s. Reload prometheus. Tech-debt #6. | `BE/infra/prometheus/prometheus.yml:5-17`, tech-debt #6 |
| Q24 | Memory leak cart-service sau 24h. Nghi ngờ gì? | 1) `Thread.sleep(50)` recursion không terminate → thread bị block, không release connection. 2) Kafka consumer không close → listener container leak. 3) Feign client không release HTTP connection → cần check keep-alive. Cách debug: enable heap dump (`-XX:+HeapDumpOnOutOfMemoryError`) + VisualVM. | Tech-debt #2, `BE/cart-service/.../repository/CartRepository.java:107-114` |
| Q25 | Lỗi `GlobalExceptionHandler not found` khi start service. Tại sao? | Tech-debt #5: `common.web.GlobalExceptionHandler` tồn tại nhưng KHÔNG được scan. Mỗi service tự viết `*ExceptionHandler` riêng. Format lỗi không đồng nhất. Fix: thêm `@Import(GlobalExceptionHandler.class)` vào main class, hoặc `scanBasePackages = "com.ecom"`. | Tech-debt #5, `BE/common/.../web/GlobalExceptionHandler.java:21-96` |
| Q26 | Sau khi deploy, lỗi 500 random. Correlation ID giúp debug thế nào? | Log format: `%5p [appName,correlationId]`. Client gửi `X-Correlation-Id` (gateway tự generate nếu thiếu). MDC propagate xuống mọi service qua filter. Khi lỗi, response có header `X-Correlation-Id` → grep trong Kibana/Grafana Loki theo ID đó → xem full trace 12 service. | `.claude/knowledge-map.md:237-259` |

### 4.6 E-commerce tổng quát (7 câu)

| # | Câu hỏi | Đáp án | Áp dụng cho Ecom |
|---|---------|--------|------------------|
| Q27 | Flash sale 1 triệu user cùng click "Mua" trong 1 giây. Thiết kế inventory reservation thế nào để không oversell? | 1) Optimistic check tại DB (version field). 2) Pessimistic lock `SELECT ... FOR UPDATE` (chậm). 3) Redis atomic decrement `DECRBY` (nhanh nhất, Ecom chưa dùng). 4) Queue-based: đẩy vào Kafka, consumer xử lý tuần tự. 5) Pre-shard stock theo segment. | Ecom hiện dùng option 2 (reservation qua DB transaction) → bottleneck khi flash sale. |
| Q28 | Payment idempotency: client retry 3 lần do timeout. Server đảm bảo chỉ charge 1 lần thế nào? | 1) Client cung cấp `Idempotency-Key` (UUID). 2) Server check key trong DB trước khi charge. 3) Nếu có → trả response cũ. 4) Nếu chưa → charge + lưu key + response. 5) UNIQUE constraint ở DB. Stripe API dùng pattern này. | Ecom đã làm đúng ở Q9. |
| Q29 | Cart abandonment 70% ở bước checkout. Cách giảm? | 1) Guest checkout (không bắt login). 2) Save cart cho user chưa login (qua cookie). 3) One-click checkout. 4) Auto-fill address. 5) Progress indicator. 6) Email reminder 24h sau. 7) Real-time shipping cost. | Ecom hiện bắt login (`POST /api/orders` cần Bearer) → friction. Đề xuất bổ sung guest flow. |
| Q30 | Distributed transaction giữa Order + Inventory + Payment. Tại sao không dùng 2PC? | 2PC chặt (synchronous, blocking) → giảm availability, không scale. Thay thế: **Saga** (Ecom dùng) — eventual consistency, mỗi step có compensation. Alternative: **TCC** (Try-Confirm-Cancel), **Event Sourcing**. Trade-off Saga: khó debug, cần distributed tracing. | Ecom dùng Saga qua Kafka, đã có Zipkin. |
| Q31 | SQL injection: tìm product theo keyword. Ecom có an toàn không? | An toàn: dùng JPA `Specification` với `criteriaBuilder.like(... , "%" + keyword + "%")` → bind parameter (PreparedStatement), không concatenate string. KHÔNG dùng `LIKE '%" + userInput + "%'`. Verify: `BE/product-service/.../service/ProductCatalogService.java:94-113` thấy dùng `cb.like(cb.lower(root.get("name")), "%" + criteria.keyword().toLowerCase() + "%")` — OK. | Best practice. |
| Q32 | Rate limiting cho `POST /api/auth/login` chống brute force. Ecom đã làm chưa? | **Có** (commit `0340f03` tháng 6): RequestRateLimiter STRICT ở gateway, 5 req/min/IP cho login/register, NORMAL cho refresh/products. Dùng Redis token bucket qua `spring-cloud-gateway`. Tech-debt #12 đã giải quyết một phần. | Đã implement, có thể hỏi follow-up: per-user thay vì per-IP? |
| Q33 | Khi 1 service down (vd payment), đơn hàng đang xử lý thì sao? User trải nghiệm thế nào? | Circuit Breaker mở (đã có Resilience4j ở commits gần đây) → fail-fast → user thấy 503 "Service temporarily unavailable". Cart vẫn giữ, order vẫn PENDING. Khi service recover → user retry. Tốt hơn: queue order, xử lý async qua Kafka, email khi hoàn tất. | Ecom đã có CB nhưng sync, có thể cải thiện UX bằng async. |

---

## 5. UI/UX Design

### 5.1 Color theme

**Dark mode (default)**:
- Background: `#0d1117` (GitHub dark)
- Card: `#161b22`
- Text: `#e6edf3`
- Accent: `#58a6ff` (link/code)
- Diff red (tech-debt): `#f85149`
- Diff green (correct): `#3fb950`

**Light mode**:
- Background: `#ffffff`
- Card: `#f6f8fa`
- Text: `#1f2328`
- Accent: `#0969da`

Toggle lưu vào `localStorage`.

### 5.2 Card layout

```
┌──────────────────────────────────────────────────┐
│ [Q1] [Saga & Kafka]                      [★★★]  │  ← header
│ Order publish inventory.reservation-requested   │
│ xuống Kafka. Nếu inventory consume xong nhưng... │  ← question
│                                       [▼ Mở]    │  ← toggle button
├──────────────────────────────────────────────────┤
│ 💡 Compensation: OrderService.cancelAfter-      │  ← answer (hidden)
│    PaymentFailure gọi InventoryClient.release.  │
│    Kafka retry 3 lần/1s; fail cuối → throw.     │
│                                                  │
│ 📎 KafkaErrorHandler.java:19-55                 │
│    OrderService.java:91-101                     │
│                                                  │
│ 🏷️ #kafka #saga #compensation                   │
└──────────────────────────────────────────────────┘
```

### 5.3 Difficulty indicator

- ★ (junior) — 6 câu
- ★★ (mid) — 14 câu
- ★★★ (senior) — 8 câu

---

## 6. JS features

```js
// 1. Accordion
document.querySelectorAll('.qa-question').forEach(btn => {
  btn.addEventListener('click', () => {
    const answer = btn.nextElementSibling;
    const isOpen = !answer.hidden;
    answer.hidden = isOpen;
    btn.setAttribute('aria-expanded', !isOpen);
    updateProgress();
  });
});

// 2. Filter by category
document.querySelectorAll('.chip').forEach(chip => {
  chip.addEventListener('click', () => {
    const filter = chip.dataset.filter;
    document.querySelectorAll('.qa-card').forEach(card => {
      card.hidden = !(filter === 'all' || card.dataset.category === filter);
    });
    document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
  });
});

// 3. Search
document.getElementById('search').addEventListener('input', e => {
  const q = e.target.value.toLowerCase();
  document.querySelectorAll('.qa-card').forEach(card => {
    const text = card.textContent.toLowerCase();
    card.hidden = !text.includes(q);
  });
});

// 4. Theme toggle
document.getElementById('theme-toggle').addEventListener('click', () => {
  const html = document.documentElement;
  const newTheme = html.dataset.theme === 'dark' ? 'light' : 'dark';
  html.dataset.theme = newTheme;
  localStorage.setItem('theme', newTheme);
});

// 5. Progress tracking
function updateProgress() {
  const opened = document.querySelectorAll('.qa-question[aria-expanded="true"]').length;
  document.getElementById('opened-count').textContent = opened;
  localStorage.setItem('openedCount', opened);
}

// 6. Init
const savedTheme = localStorage.getItem('theme') || 'dark';
document.documentElement.dataset.theme = savedTheme;
```

---

## 7. Testing

Manual test:
- Mở file trong Chrome, Firefox, Safari.
- Click từng câu hỏi → đáp án hiện/ẩn.
- Filter 6 chip → đúng số card hiện.
- Search "kafka" → chỉ hiện card có chứa "kafka".
- Toggle theme → đổi màu, refresh vẫn giữ.
- Progress đếm đúng.

---

## 8. Out of scope

- Không tích hợp linter/test framework (file tĩnh).
- Không làm dynamic load câu hỏi từ JSON (giữ inline cho đơn giản).
- Không làm multi-language (chỉ tiếng Việt).
- Không tích hợp analytics.

---

## 9. Success criteria

- File `docs/qa-scenarios.html` mở được offline trên mọi browser hiện đại.
- 33 câu hỏi, 6 category, đầy đủ file:line tham chiếu code thật.
- Accordion, filter, search, theme toggle hoạt động đúng.
- Progress tracking lưu localStorage.
- Commit vào git với message rõ ràng.

---

## 10. Implementation steps

1. Tạo file `docs/qa-scenarios.html` với skeleton + CSS + JS.
2. Thêm 33 card câu hỏi theo 4.1-4.6.
3. Manual test tất cả feature.
4. Commit `docs(qa): add interview prep Q&A HTML with 33 scenarios`.
