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

## 4. Nội dung — 33 câu hỏi, 6 category

**Định dạng đáp án "depth"**:
- **Độ dài**: 8-15 dòng (junior/mid), **15-22 dòng** (senior ★★★). Đủ để thể hiện chiều sâu.
- **Cấu trúc 5 phần** với emoji marker:
  1. 🎯 **TL;DR** (1-2 dòng) — Trả lời trực tiếp.
  2. 🔁 **Flow** (3-6 dòng) — Từng bước + sơ đồ ASCII khi cần.
  3. ⚖️ **Trade-off** (2-4 dòng) — So sánh với cách khác, lý do chọn.
  4. ⚠️ **Pitfall** (2-4 dòng) — Edge case, điều dễ sai.
  5. 🔍 **Verify** (1-3 dòng) — Cách debug, file:line, log key.
- Mỗi câu kèm **tham chiếu file:line** code thật + **tags** cho search.

**Nguyên tắc viết**: Trả lời câu hỏi ngầm "Tại sao làm thế này? Có cách khác? Khi nào sai?" — thể hiện tư duy, không chỉ mô tả code.

### 4.1 Saga & Kafka (6 câu)

#### Q1 — Order timeout khi Kafka chưa commit (★★★)

**Câu hỏi**: Order publish `inventory.reservation-requested` xuống Kafka. Nếu inventory consume xong nhưng CHƯA kịp publish `inventory.reserved` mà client order timeout thì sao? Compensation chạy thế nào?

**Đáp án**:
- 🎯 **TL;DR**: Order vẫn ở PENDING. Client retry an toàn (idempotent nhờ `order_id`). Compensation tự động qua Kafka retry; chỉ chạy tường minh khi `payment.failed`.
- 🔁 **Flow**:
  1. Order POST `/api/orders` → tạo order PENDING + publish `inventory.reservation-requested` (key=orderId).
  2. Inventory consume → reserve stock trong DB transaction (giảm `available_quantity`, tăng `reserved_quantity`, tạo `stock_reservations` row status=RESERVED).
  3. Inventory publish `inventory.reserved` (key=orderId) — **điểm dễ fail** vì commit DB xong nhưng Kafka send fail.
  4. Order consumer nhận → set order RESERVED.
  5. Nếu bước 3 fail 3 lần (`FixedBackOff(1000ms, 2 retries)`) → `DefaultErrorHandler` log error, **KHÔNG commit offset** → message re-deliver khi consumer restart.
- ⚖️ **Trade-off**: Spring Kafka `ack-mode: RECORD` + retry là **at-least-once**, không phải exactly-once. Nếu publish `inventory.reserved` thành công nhưng consumer order-service xử lý fail → nhận 2 lần → handler phải idempotent (check status trước khi update). Hiện tại `InventoryEventConsumer.handleReserved` có check status → OK.
- ⚠️ **Pitfall**: Client retry trong lúc inventory vẫn đang xử lý → tạo order thứ 2 với cùng cart (cart đã clear nhưng order vẫn còn). Ecom **chưa check duplicate request từ client** (không có request signature / nonce). Đề xuất: idempotency-key ở `POST /api/orders` tương tự payment.
- 🔍 **Verify**: Mở Zipkin `localhost:9411` filter `orderId=<uuid>` → thấy span `publish-reservation-requested` → `inventory-service.handleReservationRequested` → `publishReserved`. Nếu span cuối missing → bug. Hoặc `kafka-console-consumer.sh --topic inventory.reserved --from-beginning --property print.timestamp=true`.

📎 `BE/common/.../config/KafkaErrorHandler.java:19-55`, `BE/order-service/.../messaging/OrderEventProducer.java:19-29`, `BE/inventory-service/.../messaging/InventoryEventProducer.java:17-24`. 🏷️ #kafka #saga #compensation #timeout

---

#### Q2 — Tại sao partition key là `orderId` (★★★)

**Câu hỏi**: Tại sao partition key của `inventory.reservation-requested` là `order.id()` chứ không phải `product.id()`? Nếu chọn khác đi được không?

**Đáp án**:
- 🎯 **TL;DR**: Partition key = `orderId` đảm bảo tất cả event của 1 order vào cùng partition → xử lý tuần tự (in-order) bởi 1 consumer instance → tránh race condition giữa các item cùng order.
- 🔁 **Flow**:
  ```
  Order #123 (2 items: A x3, B x2)
       │
       ▼ publish "inventory.reservation-requested" với key=orderId="123"
  ┌──────────┐
  │ Kafka    │  Partition 0: order 123 (A, B tuần tự)
  │ Partition│  Partition 1: order 124, 125, ...
  │ Strategy │
  └──────────┘
       │
       ▼
  Inventory-Consumer-Instance-0 (chỉ đọc Partition 0)
       → reserve(A x3) xong → reserve(B x2) xong
       → publish "inventory.reserved" key=orderId="123"
  ```
- ⚖️ **Trade-off**:
  - **Chọn `orderId`** (hiện tại): Mỗi order xử lý tuần tự. Nhưng 1 product hot (iPhone) có N order khác nhau → phân tán đều các partition → parallelism cao. **Tốt cho throughput**.
  - **Chọn `productId`**: Tất cả event cho cùng product vào 1 partition → serialize. Flash sale iPhone, 10K order cùng product → tất cả dồn vào 1 partition → 1 consumer xử lý → **bottleneck**.
  - **Chọn `userId`**: Cũng OK nhưng ít intuitive hơn `orderId` vì 1 user có nhiều order.
- ⚠️ **Pitfall**: Nếu sau này cần scale, **KHÔNG được đổi partition key** của topic đang có data (Kafka không cho phép change key cho partitioner mặc định nếu đã có partition assignment). Phải tạo topic mới.
- 🔍 **Verify**: `BE/order-service/.../messaging/OrderEventProducer.java:19-29` thấy `RecordMetadata(key=order.id().toString(), ...)`. Test bằng `kafka-console-producer.sh --property "parse.key=true" --property "key.separator=:"` produce 2 event cùng key → check cùng partition.

📎 `BE/order-service/.../messaging/OrderEventProducer.java:19-29`, `.claude/knowledge-map.md:128-142`. 🏷️ #kafka #partitioning #performance

---

#### Q3 — `ack-mode: RECORD` semantics (★★)

**Câu hỏi**: `ack-mode: RECORD` nghĩa là gì? Nếu handler throw exception ở giữa thì offset có commit không? Event có bị mất không?

**Đáp án**:
- 🎯 **TL;DR**: `RECORD` = commit offset ngay sau khi handler return thành công. Throw exception → retry 3 lần (1s fixed back-off) → fail cuối → log error, **KHÔNG commit**. Event re-deliver khi consumer restart. Có thể mất event trong edge case.
- 🔁 **Flow**:
  ```
  Consumer poll batch
       │
       ▼ for each record
  ┌────────────┐
  │ handler()  │ ← business logic
  └─────┬──────┘
        │ success → commit offset
        │
        │ exception → DefaultErrorHandler
        │             → retry attempt 1 (after 1s)
        │             → retry attempt 2 (after 1s)
        │             → retry attempt 3 (after 1s)
        │             → fail final: log.error() + KHÔNG commit
        │             → message sẽ re-deliver ở lần poll tiếp
  ```
- ⚖️ **Trade-off**:
  - **RECORD** (hiện tại): Latency thấp (commit ngay). Nhưng nếu commit xong rồi handler mới throw exception ở bước downstream (vd publish Kafka phản hồi) → offset đã commit → **mất event**.
  - **BATCH**: Commit cuối batch → an toàn hơn nhưng throughput giảm.
  - **MANUAL**: Tự quản lý `Acknowledgment.acknowledge()` → max control, code phức tạp hơn.
  - **COUNT, TIME**: Commit theo số lượng hoặc thời gian.
- ⚠️ **Pitfall**: Khi handler gọi `kafkaTemplate.send().get()` (sync wait) để đảm bảo message đã gửi trước khi commit → tăng latency. Code hiện tại dùng fire-and-forget → **có thể tối ưu**.
- 🔍 **Verify**: `BE/config-repo/*-service.yml` thấy `spring.kafka.listener.ack-mode: record`. Trigger lỗi: stop DB giữa consume → check log có 3 retry + 1 error final.

📎 `BE/common/.../config/KafkaErrorHandler.java:19-55`, `BE/config-repo/order-service.yml:29-42`. 🏷️ #kafka #reliability #at-least-once

---

#### Q4 — Order state machine khi payment.failed (★★★)

**Câu hỏi**: Order state machine: PENDING → RESERVED → CONFIRMED. Nếu `payment.failed` nhận được khi order đang ở RESERVED thì xử lý release inventory thế nào? Có thể rơi vào inconsistent state không?

**Đáp án**:
- 🎯 **TL;DR**: `PaymentEventConsumer.handleFailed` → `OrderService.cancelAfterPaymentFailure(orderId)` → gọi `InventoryClient.release(reservationId)` qua Feign → DB update order=CANCELLED → publish `order.cancelled`.
- 🔁 **Flow**:
  ```
  payment-service → Kafka "payment.failed" key=paymentId
       │
       ▼
  order-service.PaymentEventConsumer.handleFailed
       │
       ├─→ OrderService.cancelAfterPaymentFailure(orderId)
       │     │
       │     ├─→ OrderRepo.findById(orderId) → order.status=RESERVED
       │     ├─→ InventoryClient.release(reservationId) ← Feign HTTP
       │     ├─→ OrderRepo.save(status=CANCELLED) ← DB
       │     └─→ OrderEventProducer.publishOrderCancelled()
       │              │
       │              ▼
       │         Kafka "order.cancelled" key=orderId
       │              │
       │              ▼
       │         notification-service.OrderEventConsumer.handleCancelled
       │              → send email + SMS
       │
       └─→ (parallel) notification-service.PaymentEventConsumer.handleFailed
            → send email "thanh toán thất bại"
  ```
- ⚖️ **Trade-off**: Compensation qua Feign (sync HTTP) — nhanh, có thể trả lỗi rõ ràng. Alternative: gửi Kafka event `inventory.release-requested` (async) — loose coupling hơn nhưng phải chờ. Hiện tại sync vì cancel là critical path user đang chờ.
- ⚠️ **Pitfall**:
  1. Nếu `InventoryClient.release` fail (inventory-service down) → `OrderService` vẫn set CANCELLED → **inconsistent state** (order CANCELLED nhưng stock vẫn RESERVED). Hiện tại chưa có retry cho compensation step.
  2. Nếu `publishOrderCancelled` fail sau khi DB commit → notification không gửi. Tech-debt chung: không có transactional outbox ở order-service.
  3. Nếu nhận `payment.failed` 2 lần (duplicate Kafka) → `cancelAfterPaymentFailure` check order.status; nếu đã CANCELLED → return no-op. OK.
- 🔍 **Verify**: Test bằng integration test: tạo order, mock payment.failed, kiểm tra stock trở về available. Hoặc manual: trigger webhook `POST /api/payments/webhooks/mock` với eventType=FAILED → grep log `cancelAfterPaymentFailure`.

📎 `BE/order-service/.../messaging/PaymentEventConsumer.java:39-54`, `BE/order-service/.../service/OrderService.java:91-101`. 🏷️ #saga #compensation #state-machine

---

#### Q5 — Kafka producer reliability (★★)

**Câu hỏi**: Nếu Kafka broker down giữa lúc order-service đang publish `order.confirmed`, event có bị mất không? Config nào cần thêm?

**Đáp án**:
- 🎯 **TL;DR**: Có nguy cơ mất event. Default `acks=1` (leader ghi xong là báo success) → nếu leader fail trước khi replicate → event mất. Fix: set `acks=all` + `enable.idempotence=true` + retry config.
- 🔁 **Flow**:
  ```
  OrderEventProducer.publishOrderConfirmed(order)
       │
       ▼
  KafkaTemplate.send(topic, key, value)
       │
       ├─→ Producer batch record → gửi đến broker leader
       │
       ▼ (acks=1, current config)
  Leader ghi vào log → return success ngay
       │
       ▼
  Nếu leader crash ngay sau → record chưa replicate → mất
  ```
- ⚖️ **Trade-off**:
  - **acks=1** (hiện tại): Latency thấp nhất (1 RTT). Nhưng chỉ leader, không replicate. Phù hợp non-critical event.
  - **acks=all** + `min.insync.replicas=2`: Đợi tất cả in-sync replica ghi xong → an toàn. Latency cao hơn. Phù hợp critical event (payment, order).
  - **enable.idempotence=true**: Producer đảm bảo không gửi trùng sequence number. Ngăn duplicate khi retry.
  - **transactional.id**: Exactly-once across producer/consumer (đắt nhất).
- ⚠️ **Pitfall**:
  1. Hiện tại project **CHƯA config** `acks=all` ở order-service / payment-service. Gap cần bổ sung.
  2. `acks=all` chỉ an toàn nếu `min.insync.replicas ≥ 2` ở broker. Nếu chỉ 1 broker (KRaft single-broker ở dev) → `min.insync.replicas=1` để tránh "Not enough replicas".
  3. Cần test: stop 1 broker, publish event, verify không mất.
- 🔍 **Verify**: Check `BE/config-repo/order-service.yml` tìm `spring.kafka.producer.acks` — hiện không có → default `acks=1`. Đề xuất: thêm `acks: all`, `enable.idempotence: true`, `retries: 3`.

📎 `BE/config-repo/order-service.yml:29-42`, `BE/config-repo/payment-service.yml:29-33`. 🏷️ #kafka #reliability #durability

---

#### Q6 — Saga: Orchestration vs Choreography (★★★)

**Câu hỏi**: Saga pattern trong Ecom có phải là Orchestration hay Choreography? Tại sao? Khi nào nên dùng loại nào?

**Đáp án**:
- 🎯 **TL;DR**: **Choreography**. Mỗi service tự react với Kafka event, không có central orchestrator. Pros: loose coupling, dễ scale. Cons: khó trace, phải có distributed tracing tool (Zipkin).
- 🔁 **Flow**:
  ```
  Orchestration (KHÔNG dùng trong Ecom):
  ┌─────────────────┐
  │ Saga Orchestrator│ ← biết toàn bộ flow
  └────────┬────────┘
           ▼
  Order → Inventory (RPC) → Payment (RPC) → Notification (RPC)
  
  Choreography (Ecom dùng):
  OrderService publish "order.created" → Kafka
       │
       ├→ InventoryService consume → reserve → publish "inventory.reserved"
       │     │
       │     └→ OrderService consume → mark RESERVED → publish "order.confirmed"
       │           │
       │           └→ NotificationService consume → send email
       │
       └→ (parallel) PaymentService có thể listen "order.confirmed" để charge
  ```
- ⚖️ **Trade-off**:
  - **Choreography** (Ecom): Mỗi service độc lập, thêm service mới chỉ cần listen event. Nhưng flow phân tán → debug khó, phải correlate qua `X-Correlation-Id` + Zipkin. Risk: cycle (A trigger B, B trigger A).
  - **Orchestration** (vd Camunda, Temporal): 1 service điều khiển toàn bộ, biết rõ step nào đang chạy. Debug dễ. Nhưng coupling cao, orchestrator thành single point of failure.
  - **Hybrid**: Core flow orchestration (order saga), side-effect choreography (notification, analytics).
- ⚠️ **Pitfall**:
  1. Event versioning: Khi thêm field mới vào `OrderConfirmedEvent`, consumer cũ chưa update có thể crash. Ecom dùng `JsonDeserializer` trusted packages `"*"` → nguy hiểm. Cần schema registry cho production.
  2. Cycle: `order.confirmed` → notification.email → user click → `payment.created` → `order.updated` → ... → loop.
  3. Lost event: Nếu 1 service down khi event được publish → retry hết → event mất. Cần DLQ + monitoring.
- 🔍 **Verify**: `grep -r "@KafkaListener" BE/*/src/`. Hiện tại Ecom có ~7 listener, OK.

📎 `BE/.../messaging/*EventConsumer.java`, `.claude/knowledge-map.md:126-142`. 🏷️ #saga #architecture #choreography

---

### 4.2 Outbox & Idempotency (5 câu)

#### Q7 — Transactional Outbox pattern (★★★)

**Câu hỏi**: Giải thích Transactional Outbox pattern. Tại sao payment-service dùng mà order-service thì không? Có nhược điểm gì?

**Đáp án**:
- 🎯 **TL;DR**: Outbox lưu event vào bảng `outbox_events` **trong cùng transaction** với business write → đảm bảo atomic "DB + event". Scheduler đọc PENDING rows → publish Kafka. Order-service publish trực tiếp trong handler → có thể mất event nếu Kafka down sau DB commit.
- 🔁 **Flow**:
  ```
  PaymentService.markSucceeded(paymentId)  [trong 1 @Transactional]
       │
       ├─→ paymentsRepo.save(status=SUCCEEDED)
       │
       └─→ outboxRepo.save(
              OutboxEvent(
                aggregateType="Payment",
                aggregateId=paymentId,
                eventType="payment.succeeded",
                payload=Map<...>,
                status=PENDING,
                createdAt=now()
              )
            )  ← CÙNG transaction → commit cùng lúc
  
  --- sau vài ms/giây ---
  
  OutboxPublisher @Scheduled(fixedDelay=5000ms)
       │
       ├─→ SELECT * FROM outbox_events WHERE status='PENDING' ORDER BY created_at LIMIT 100
       │
       ├─→ for each event: kafkaTemplate.send(eventType, key, payload)
       │
       └─→ on success → status=PUBLISHED, published_at=now() [NGOÀI transaction → tech-debt #3]
  ```
- ⚖️ **Trade-off**:
  - **Outbox (Ecom payment)**: Đảm bảo at-least-once delivery. Latency publish = 0-5s. Cần thêm bảng + scheduler.
  - **Direct publish (Ecom order)**: Latency thấp (ms). Nhưng nếu Kafka down → mất event. Cần retry queue hoặc message buffer.
  - **CDC-based (Debezium)**: Đọc WAL Postgres → publish Kafka. Zero code change. Nhưng cần Debezium connector + overhead.
- ⚠️ **Pitfall**:
  1. **Dual-write problem**: Nếu publish Kafka xong rồi mới update DB (hoặc ngược lại) → 1 bên fail → inconsistent. Outbox giải quyết bằng cách đảm bảo cả 2 cùng commit.
  2. **Outbox bloat**: Nếu scheduler fail liên tục → bảng `outbox_events` phình → query chậm. Cần partition theo tháng hoặc TTL.
  3. **Outbox không có DLQ**: Event fail publish 3 lần → status=FAILED → log only. Cần alert + manual replay tool.
- 🔍 **Verify**: Query `SELECT count(*), status FROM outbox_events GROUP BY status;` — nếu FAILED > 0 → cần check.

📎 `BE/payment-service/.../outbox/OutboxPublisher.java:22-36`, `BE/payment-service/.../service/PaymentService.java:43-53, 107-115`. 🏷️ #outbox #reliability #eventual-consistency

---

#### Q8 — OutboxPublisher race condition (★★★)

**Câu hỏi**: OutboxPublisher chạy `fixedDelay 5s`. Nếu scheduler restart giữa lúc gửi Kafka thì event có bị trùng không? Làm sao fix?

**Đáp án**:
- 🎯 **TL;DR**: **Có thể bị trùng**. `repository.save(event)` được gọi bên ngoài transaction (sau khi Kafka send return). Nếu app crash giữa `send()` và `save()` → status vẫn PENDING → scheduler kế tiếp gửi lại. Tech-debt #3.
- 🔁 **Flow race**:
  ```
  t=0ms:    Scheduler poll → lấy event #1 (status=PENDING)
  t=10ms:   kafkaTemplate.send(...) → fire-and-forget, return Future
  t=20ms:   ── APP CRASH ──
            (send có thể đã reach broker hoặc chưa)
  t=5s:     Scheduler restart → poll lại event #1 (status=PENDING)
            → gửi lại
            → DUPLICATE nếu send trước đó thực sự đã tới broker
  ```
- ⚖️ **Trade-off cách fix**:
  - **Optimistic update với version field**: Thêm `version` column, update `SET status=PUBLISHED, version=version+1 WHERE id=? AND version=?`. An toàn, lock-free.
  - **Pessimistic lock `SELECT ... FOR UPDATE SKIP LOCKED`**: Mỗi scheduler instance chỉ lấy rows chưa lock → scale được.
  - **Kafka idempotent producer + consumer**: Consumer check `eventId` đã xử lý chưa (qua Redis set hoặc DB).
  - **WAL-based outbox (Debezium)**: Loại bỏ scheduler, dùng CDC.
- ⚠️ **Pitfall**:
  1. Hiện tại `OutboxPublisher.java:33-36` gọi `kafkaTemplate.send(...).whenComplete((result, ex) -> { if (ex == null) repository.save(event); })` — save ở callback, không atomic với send.
  2. `fixedDelay=5000ms` không có jitter → nhiều instance cùng poll → DB spike. Thêm `initialDelay` random.
  3. Không có max retry → event poison (luôn fail) → scheduler loop vô tận.
- 🔍 **Verify**: Reproduce bằng `kill -9` Java process trong khi scheduler đang publish, restart, check duplicate event trong Kafka topic.

📎 `BE/payment-service/.../outbox/OutboxPublisher.java:22-36`, tech-debt #3 trong `.claude/knowledge-map.md:286-305`. 🏷️ #outbox #race-condition #at-least-once

---

#### Q9 — Idempotency-Key usage (★★)

**Câu hỏi**: Idempotency-Key `pay-{orderId}` ở `POST /api/payments`. Nếu client gọi 2 lần với cùng key thì sao? Trade-off với cách generate key thế nào?

**Đáp án**:
- 🎯 **TL;DR**: Lần 1 tạo Payment mới (status=PENDING). Lần 2 `findByIdempotencyKey` trả về record cũ → trả lại response cũ. UNIQUE constraint ở DB chống race. Client phải tự generate key ổn định qua các retry.
- 🔁 **Flow**:
  ```
  POST /api/payments {orderId, amount, currency}
  Header: Idempotency-Key: pay-uuid-123
  
  PaymentService.createPayment(req, key):
       │
       ├─→ paymentsRepo.findByIdempotencyKey(key)
       │     │
       │     ├─ found → return existing PaymentResponse  ← idempotent
       │     └─ null  ↓
       │
       ├─→ mockProvider.createPayment() → providerPaymentId
       │
       ├─→ paymentsRepo.save(Payment(id, ..., idempotencyKey=key, status=PENDING))
       │     [UNIQUE constraint idempotency_key]
       │
       └─→ return new PaymentResponse
  
  --- retry với CÙNG key ---
  
  PaymentService.createPayment(req, key):
       │
       ├─→ findByIdempotencyKey(key) → found → return same response
       │
       └─→ KHÔNG tạo record mới, KHÔNG charge lại
  ```
- ⚖️ **Trade-off**:
  - **Key = `pay-{orderId}`** (Ecom dùng): Stable, dễ debug. Nhưng nếu user muốn retry với amount khác (vd đổi tip) → phải đổi key → fail.
  - **Key = client UUID**: Mỗi retry sinh key mới → không idempotent. **SAI**.
  - **Key = UUID cho mỗi "logical attempt"**: User click "Pay" 1 lần → sinh UUID → tất cả retry cùng UUID. Đúng pattern của Stripe.
- ⚠️ **Pitfall**:
  1. **Idempotency ở create nhưng KHÔNG ở webhook**: Nếu webhook gửi 2 lần với cùng `providerEventId` → xử lý 2 lần → 2 lần mark SUCCEEDED + 2 outbox events. Ecom có xử lý ở Q10 — OK.
  2. **Idempotency chỉ check exact match body**: Nếu client retry với cùng key nhưng body khác (amount thay đổi) → vẫn trả response cũ. Có thể silent bug. Stripe trả 422 "key reused with different params".
  3. **Idempotency window chưa set**: Ecom lưu key vĩnh viễn ở DB. Nên expire sau 24h.
- 🔍 **Verify**: `BE/payment-service/.../service/PaymentService.java:36-41` thấy check `findByIdempotencyKey`. Test: gọi 2 lần `curl -X POST -H "Idempotency-Key: test-123"` → check `payments` table chỉ có 1 row.

📎 `BE/payment-service/.../service/PaymentService.java:36-41`, `BE/database/payment-service/V1__create_payment_tables.sql:10`. 🏷️ #idempotency #payment #reliability

---

#### Q10 — Webhook idempotency (★★)

**Câu hỏi**: Mock webhook `POST /api/payments/webhooks/mock` xử lý duplicate `providerEventId` thế nào? So sánh với cách xử lý ở Stripe.

**Đáp án**:
- 🎯 **TL;DR**: `PaymentWebhookService.handle` gọi `existsById(providerEventId)` → nếu có, return ngay (no-op). Nếu chưa → xử lý + save `payment_webhook_events` với `provider_event_id` PK. Idempotent ở DB layer.
- 🔁 **Flow**:
  ```
  POST /api/payments/webhooks/mock
  Body: {providerEventId, providerPaymentId, eventType}
  
  PaymentWebhookService.handle(event):
       │
       ├─→ paymentWebhookEventRepo.existsById(event.providerEventId())
       │     │
       │     ├─ true → log.info("already processed") + return  ← DEDUP
       │     └─ false ↓
       │
       ├─→ paymentService.markSucceeded(paymentId) 
       │     hoặc markFailed(paymentId) [dựa trên eventType]
       │     │
       │     └─→ @Transactional:
       │           ├─ paymentsRepo.save(status=...)
       │           └─ outboxRepo.save(...)
       │
       └─→ paymentWebhookEventRepo.save(
              PaymentWebhookEvent(providerEventId=PK, processedAt=now())
            ) ← trong cùng transaction với markSucceeded
  ```
- ⚖️ **Trade-off so với Stripe**:
  - **Ecom**: Idempotency ở application layer (check exists trước). DB unique constraint ở `payment_webhook_events.provider_event_id` PK → safety net cuối cùng.
  - **Stripe**: Dùng signed webhook + idempotency key riêng. Request có header `Stripe-Signature` verify bằng secret. Mỗi event có UUID. Stripe lưu 30 ngày.
  - **Ecom hiện tại KHÔNG verify signature** (mock provider) → production phải bổ sung. Tech-debt #7.
- ⚠️ **Pitfall**:
  1. **Race condition existsById + save**: 2 webhook cùng `providerEventId` đến cùng lúc → cả 2 existsById=false → cả 2 save → 1 fail vì PK constraint → exception. Hiện tại chưa catch. Cần `try/catch DataIntegrityViolationException` → return 200.
  2. **Outbox event bị duplicate**: Nếu webhook dedup fail → 2 outbox event → 2 Kafka `payment.succeeded` → idempotent check ở consumer.
  3. **Provider retry storm**: Provider (Stripe, Adyen) retry webhook nếu không nhận 2xx trong vài giây. Cần respond nhanh + xử lý async.
- 🔍 **Verify**: `BE/payment-service/.../service/PaymentWebhookService.java:24-38` đọc flow. Test: gửi 2 webhook cùng `providerEventId` → check log chỉ "processed" 1 lần.

📎 `BE/payment-service/.../service/PaymentWebhookService.java:24-38`, `BE/payment-service/.../web/MockPaymentWebhookController.java:19-23`. 🏷️ #webhook #idempotency #payment

---

#### Q11 — Double-charge prevention (★★)

**Câu hỏi**: Nếu 2 client cùng `POST /api/payments` đồng thời với 2 `idempotency_key` khác nhau cho cùng 1 order? Điều gì xảy ra?

**Đáp án**:
- 🎯 **TL;DR**: Cả 2 đều tạo Payment, nhưng `payments.order_id` UNIQUE → request thứ 2 fail với `DataIntegrityViolationException` → trả 409 Conflict. Phòng chống double-charge ở DB layer.
- 🔁 **Flow**:
  ```
  Client A: POST /api/payments {orderId: 100, amount: 50}
            Header: Idempotency-Key: key-A
  Client B: POST /api/payments {orderId: 100, amount: 50}  (cùng order)
            Header: Idempotency-Key: key-B
  ──────────────── concurrent ────────────────
  
  Thread A: findByIdempotencyKey(key-A) → null
  Thread B: findByIdempotencyKey(key-B) → null  (key khác nhau)
  
  Thread A: save(Payment(orderId=100, key=key-A, status=PENDING)) → SUCCESS
  Thread B: save(Payment(orderId=100, key=key-B, status=PENDING))
            → DataIntegrityViolationException (order_id UNIQUE)
            → catch → return 409 Conflict
  ```
- ⚖️ **Trade-off**:
  - **UNIQUE trên `order_id`** (Ecom dùng): Đơn giản, chắc chắn, không có race. Trade-off: 1 order chỉ có 1 payment attempt. Nếu fail → user phải tạo order mới hoặc cancel order cũ.
  - **UNIQUE trên `(order_id, status)`**: Cho phép nhiều payment attempt với status khác (vd PENDING + FAILED), chỉ chặn 2 SUCCEEDED. Phức tạp hơn nhưng linh hoạt.
  - **Không có constraint**: Có thể double-charge. **Tuyệt đối không dùng**.
- ⚠️ **Pitfall**:
  1. **Exception handling chưa rõ ràng**: Cần catch `DataIntegrityViolationException` ở controller hoặc `GlobalExceptionHandler` → trả 409. Nếu để bubble up → 500 Internal Server Error → user retry → confusion.
  2. **Cùng 1 user mở 2 tab browser**: Cùng 1 cart → cùng 1 order → 1 trong 2 tab bị 409. Frontend cần handle.
  3. **Refund flow**: Sau khi refund, nếu user mua lại cùng order? Phải tạo order mới (order_id khác). OK.
- 🔍 **Verify**: `BE/database/payment-service/V1__create_payment_tables.sql:7` thấy `order_id UUID UNIQUE NOT NULL`. Test: tạo 2 Payment cùng order_id → 1 thành công, 1 fail.

📎 `BE/database/payment-service/V1__create_payment_tables.sql:7`, `BE/payment-service/.../service/PaymentService.java`. 🏷️ #payment #double-charge #constraint

---

### 4.3 JWT & Security (4 câu)

#### Q12 — JWT secret leak (★★★)

**Câu hỏi**: JWT HS256 dùng secret chung giữa auth-service và api-gateway. Nếu secret leak thì sao? Cách mitigate? So sánh HS256 vs RS256.

**Đáp án**:
- 🎯 **TL;DR**: Attacker forge được token với role bất kỳ (CUSTOMER/ADMIN) → truy cập mọi API. Mitigate: rotate secret thường xuyên, dùng RS256 với private key chỉ auth-service giữ, public key gateway verify, lưu secret ở Vault/KMS. Tech-debt #11: gateway không enforce độ dài secret ≥ 32 bytes.
- 🔁 **Flow khi leak**:
  ```
  Attacker lấy được JWT_SECRET (qua env var leak, log dump, git history)
       │
       ▼
  Tạo JWT tùy ý:
  eyJhbGciOiJIUzI1NiJ9
  .eyJzdWIiOiJmYWtlLXVzZXItaWQiLCJyb2xlcyI6WyJBRE1JTiJdLCJleHAiOjQ4MDAwMDAwMDB9
  .<signature với secret đã biết>
       │
       ▼
  GET /api/admin/products -H "Authorization: Bearer <fake-token>"
       │
       ▼
  Gateway verify → valid → pass
  Downstream tin header X-User-Roles=ADMIN → trả data
       │
       ▼
  Attacker: full admin access
  ```
- ⚖️ **HS256 vs RS256**:
  - **HS256 (Ecom dùng)**: Symmetric, 1 secret cho cả sign + verify. Pros: nhanh, đơn giản. Cons: tất cả service phải biết secret → rủi ro leak.
  - **RS256**: Asymmetric. auth-service giữ private key (sign), gateway chỉ có public key (verify). Pros: gateway không thể tạo token, chỉ verify. Cons: chậm hơn (~10ms), key management phức tạp hơn.
  - **ES256**: Elliptic curve, nhanh hơn RS256, key ngắn hơn.
- ⚠️ **Pitfall**:
  1. **Tech-debt #11**: `GatewayJwtTokenService.java:73-75` lấy `secret.getBytes(UTF_8)` không check ≥ 32 bytes. Auth-service có check ở `JwtTokenService.java:85-91`. Nếu set secret ngắn → auth fail nhưng gateway verify vẫn pass → inconsistent.
  2. **Secret in env file**: `BE/.env` có `JWT_SECRET=xxx`. Nếu commit nhầm hoặc log in container → leak. Dùng Docker secret hoặc Vault.
  3. **Token revocation**: Ecom chỉ có refresh token revocation, access token (15 phút) **không thể revoke**. Nếu leak → attacker dùng đến hết 15 phút. Mitigate: blacklist Redis, giảm TTL xuống 5 phút, hoặc dùng short-lived + refresh.
- 🔍 **Verify**: Set `JWT_SECRET=test` (4 chars) trong `.env` → start gateway → có warning/error không? Hiện tại **KHÔNG có** (tech-debt). Đề xuất: thêm `if (secret.length < 32) throw`.

📎 `BE/auth-service/.../security/JwtTokenService.java:40-57, 85-91`, `BE/api-gateway/.../security/GatewayJwtTokenService.java:54-75`, tech-debt #11. 🏷️ #jwt #security #secret-management

---

#### Q13 — Header spoofing (★★★)

**Câu hỏi**: Gateway set 3 header `X-User-Id/Email/Roles` xuống downstream. Nếu attacker gửi thẳng `X-User-Roles: ADMIN` đến payment-service (bypass gateway) thì sao? Mức độ nghiêm trọng?

**Đáp án**:
- 🎯 **TL;DR**: **Lỗ hổng nghiêm trọng (P0)**. Downstream `GatewayUserContextFilter` chỉ đọc header, KHÔNG verify chữ ký. Mitigate: (1) network policy chỉ cho phép request từ gateway IP, (2) mỗi service verify lại JWT qua public key, (3) bind header ở TLS terminator.
- 🔁 **Attack flow**:
  ```
  Attacker discover port payment-service :8087 (qua nmap scan nội bộ)
       │
       ▼
  curl -X POST http://payment-service:8087/api/payments/123/refund \
       -H "X-User-Id: any-uuid" \
       -H "X-User-Email: hacker@evil.com" \
       -H "X-User-Roles: ADMIN"
       │
       ▼
  GatewayUserContextFilter: parse headers → set attribute
  GatewayRoleFilter: check X-User-Roles contains ADMIN → pass
  PaymentController.refund: execute refund logic
       │
       ▼
  Refund thành công mà KHÔNG CẦN login
  ```
- ⚖️ **Trade-off các cách fix**:
  - **Network policy (Kubernetes NetworkPolicy)**: Chỉ gateway pod được gọi payment-service. Đơn giản, hiệu quả. Nhưng dev/test local khó enforce.
  - **Verify lại JWT**: Mỗi service có public key → verify `Authorization` header. Tốn thêm ~5-10ms.
  - **mTLS**: Service-to-service TLS + client cert. An toàn nhất. Cần Istio/Linkerd.
  - **Bind header tại TLS terminator**: Envoy/Istio strip client header, chỉ chấp nhận từ gateway. Phức tạp.
- ⚠️ **Pitfall**:
  1. **Service exposed trong Docker network**: docker-compose expose port `:8087` ra `localhost:8087` → dev có thể curl trực tiếp từ máy host. Trong production, dùng internal network + ingress chỉ cho gateway.
  2. **Kubernetes Service**: Mỗi service có ClusterIP → pod khác cluster có thể gọi. Cần NetworkPolicy chặn.
  3. **Audit log chỉ ghi sau khi đã pass filter**: Nếu attack thành công, log không có cảnh báo. Cần ghi cả "denied" request.
- 🔍 **Verify**: Test: tắt gateway, chạy payment-service độc lập, gửi request với `X-User-Roles: ADMIN` → check có pass không. Hiện tại Ecom **SẼ PASS** (lỗ hổng). Đề xuất P0: thêm NetworkPolicy + verify JWT.

📎 `BE/cart-service/.../security/GatewayUserContextFilter.java:13-34`, `BE/payment-service/.../security/GatewayRoleFilter.java:14-32`. 🏷️ #security #authorization #spoofing

---

#### Q14 — Refresh token storage (★★)

**Câu hỏi**: Refresh token lưu DB dạng SHA-256 hash, không lưu plaintext. Tại sao? So sánh với việc lưu trực tiếp.

**Đáp án**:
- 🎯 **TL;DR**: Nếu DB leak (SQL injection, backup lộ, insider), attacker không dùng được refresh token để lấy access token mới. Hash 1 chiều → chỉ verify được, không reverse. Kèm UNIQUE constraint + `revoked_at` cho invalidation.
- 🔁 **Flow**:
  ```
  AuthService.register/login:
       │
       ├─→ generateRefreshToken():
       │     raw = base64url(16 random bytes)
       │     hash = SHA-256(raw)
       │     save RefreshToken(id, userId, tokenHash=hash, expiresAt=now+30d)
       │     return raw  ← chỉ trả raw 1 lần cho client
       │
  AuthService.refresh(rawToken):
       │
       ├─→ hash = SHA-256(rawToken)
       │
       ├─→ refreshTokenRepo.findByTokenHash(hash)
       │     │
       │     ├─ not found → 401 invalid
       │     ├─ revoked_at != null → 401 revoked
       │     ├─ expires_at < now → 401 expired
       │     └─ valid ↓
       │
       ├─→ revoke old (set revoked_at)
       │
       └─→ issue new access + refresh token
  ```
- ⚖️ **Trade-off**:
  - **SHA-256 hash (Ecom)**: Nhanh, đủ cho mục đích (không phải password). Trade-off: SHA-256 nhanh → nếu attacker lấy DB có thể brute force nếu token yếu. Nhưng token 16 random bytes = 128 bit entropy → không brute force được.
  - **BCrypt**: Chậm hơn (~100ms), designed cho password. Overkill cho random token.
  - **HMAC-SHA256 với server secret**: Hash với secret, cùng secret để verify. An toàn hơn SHA-256 thuần vì attacker cần cả DB + secret.
  - **Argon2**: State-of-art, chống GPU attack. Chưa cần cho refresh token.
- ⚠️ **Pitfall**:
  1. **Token rotation**: Mỗi lần refresh → revoke old, issue new. Ecom làm đúng. Nếu KHÔNG rotate → attacker có thể dùng song song.
  2. **Detection stolen token**: Nếu user A dùng refresh token cũ (đã revoke) → có thể là dấu hiệu token bị đánh cắp → force logout tất cả session của user A. Ecom chưa implement.
  3. **Long expiry**: 30 ngày khá dài. Best practice: 7-14 ngày, kèm absolute timeout.
- 🔍 **Verify**: `BE/auth-service/.../service/AuthService.java:95-109` thấy `sha256(token)` → save hash. Test: login → lấy refresh token → query DB: chỉ thấy hash, không có raw.

📎 `BE/auth-service/.../service/AuthService.java:95-109`. 🏷️ #security #token #storage

---

#### Q15 — Bearer scheme (★)

**Câu hỏi**: Tại sao `Authorization` header phải `Bearer ` mà không parse trực tiếp token?

**Đáp án**:
- 🎯 **TL;DR**: Theo RFC 6750 OAuth 2.0. Cho phép mở rộng scheme (Basic, Digest, MAC, NTLM). Phân biệt rõ authentication scheme. Một số proxy/CDN có thể strip header nếu format lạ.
- 🔁 **RFC 6750 format**:
  ```
  Authorization: Bearer <token>
                  ^^^^^^ scheme name (case-insensitive per RFC)
                         ^^^^^^ token (access_token, refresh_token, etc.)
  ```
- ⚖️ **Các scheme khác**:
  - **Basic**: `Basic base64(user:pass)` — HTTP Basic Auth.
  - **Digest**: Challenge-response, cũ.
  - **Bearer**: Phổ biến nhất cho OAuth 2.0 / JWT.
  - **MAC**: HTTP Signatures, phức tạp hơn.
  - **NTLM, Negotiate**: Windows auth.
- ⚠️ **Pitfall**:
  1. **Strip prefix**: Một số library yêu cầu đúng `Bearer ` (space) — nếu thiếu space → 401. Ecom dùng `startsWith("Bearer ")` → OK.
  2. **Case sensitivity**: Scheme name theo RFC là case-insensitive nhưng token sau scheme là case-sensitive. Ecom lowercase Bearer → OK.
  3. **Token trong URL**: KHÔNG bao giờ đặt token trong query string (log, history). Luôn header.
- 🔍 **Verify**: `BE/api-gateway/.../security/JwtAuthenticationFilter.java:34-41` thấy extract `header.substring(7)` sau khi check `startsWith("Bearer ")`. Test: gửi `Bearer` (không có space) → 401.

📎 `BE/api-gateway/.../security/JwtAuthenticationFilter.java:34-41`, RFC 6750. 🏷️ #jwt #rfc #http

---

### 4.4 Redis & Cart (5 câu)

#### Q16 — Cart trong Redis thay vì DB (★★)

**Câu hỏi**: Cart lưu Redis JSON với TTL 30 ngày. Tại sao không lưu DB? Nhược điểm là gì?

**Đáp án**:
- 🎯 **TL;DR**: Cart là data ephemeral, user có thể abandon. Redis read/write nhanh hơn DB 10-100x (in-memory, single-thread, no disk). TTL tự động cleanup → không cần GC job. Nhược: Redis down → cart unavailable (cache-aside fallback chưa có).
- 🔁 **Flow**:
  ```
  CartService.addItem(userId, productId, qty):
       │
       ├─→ ProductClient.getProduct(productId) ← Feign
       │
       ├─→ Cart cart = cartRepo.findByUserIdOrCreate(userId)
       │     │
       │     ├─→ Redis: GET cart:{userId} → JSON deserialize
       │     │
       │     └─→ if null → lock lock:cart:{userId} (5s) → Lua GET or CREATE → unlock
       │
       ├─→ cart.addItem(productSnapshot, qty)
       │
       └─→ cartRepo.save(cart)  ← Redis: SETEX cart:{userId} 30days <JSON>
  ```
- ⚖️ **Trade-off Redis vs DB**:
  - **Redis (Ecom)**: 0.1-1ms read/write. Tự expire. Schema-less JSON → dễ evolve. Nhưng cần backup nếu user complaint "mất cart".
  - **Postgres JSONB**: Persistent, query được (vd tìm cart có product X). Nhưng chậm hơn 10x, cần GC job, cần migration.
  - **Hybrid**: Cart metadata ở DB, items cache ở Redis. Phức tạp.
- ⚠️ **Pitfall**:
  1. **Redis down**: Toàn bộ cart-service ngừng. Cần fallback: (a) cho phép checkout với cart rỗng (user phải add lại), (b) lưu DB backup async.
  2. **JSON serialization version**: Nếu thêm field vào `Cart` record → Redis có data cũ thiếu field → deserialize OK (Jackson default) nhưng business logic có thể fail. Cần `@JsonIgnoreProperties(ignoreUnknown=true)`.
  3. **Memory pressure**: Mỗi cart ~1-5KB. 1M user = 1-5GB. Tính vào Redis capacity.
  4. **GDPR**: User xóa account → phải xóa cart. Hiện tại Ecom có cơ chế? Cần check.
- 🔍 **Verify**: `BE/cart-service/.../repository/CartRepository.java:21, 116-129` thấy `Duration.ofDays(30)`. Test: thêm cart → `redis-cli TTL cart:{userId}` → ~2.59M seconds.

📎 `BE/cart-service/.../repository/CartRepository.java:21, 116-129`, `.claude/knowledge-map.md:148-160`. 🏷️ #redis #cart #performance

---

#### Q17 — Cart lock contention (★★★)

**Câu hỏi**: `findByUserIdOrCreate` dùng `Thread.sleep(50)` + retry đệ quy khi lock contention. Rủi ro là gì? Fix thế nào?

**Đáp án**:
- 🎯 **TL;DR**: **Stack overflow** nếu lock bị giữ quá 5s (TTL). Tech-debt #2. Fix: bounded retry với exponential backoff (50ms → 100ms → 200ms → max 5 lần → fail), hoặc dùng Redisson distributed lock với watchdog tự renew.
- 🔁 **Problem flow**:
  ```
  Thread A: GET cart:user1 → miss
  Thread A: SET lock:cart:user1 NX EX 5 → success (lock acquired)
  Thread A: ... (đang tạo cart, lâu 6s do GC pause, network blip)
  
  Thread B: GET cart:user1 → miss
  Thread B: SET lock:cart:user1 NX EX 5 → fail (already locked)
  Thread B: Thread.sleep(50)
  Thread B: recursive call findByUserIdOrCreate(user1)
  
  ... repeat 100 lần trong 5s ...
  
  Thread A: finally { DEL lock:cart:user1 } → release
  Thread B: GET cart:user1 → success → return
  ```
- ⚖️ **Trade-off cách fix**:
  - **Bounded retry với exponential backoff**: 5 lần, 50ms → 100ms → 200ms → 400ms → 800ms (tổng ~1.5s). Sau đó fail với 503. Đơn giản, đủ cho 95% case.
  - **Redisson distributed lock**: Tự renew TTL (watchdog) → không sợ GC pause. Cần thêm dependency.
  - **Lock-free với Lua atomic**: `EVAL "if not exists then set; return end"` → atomic. Nhưng phải viết Lua cẩn thận.
  - **Optimistic check qua version**: Mỗi cart có `version` field. Update với `SET cart:user1 <new> XX GT <version>`. Retry khi fail.
- ⚠️ **Pitfall**:
  1. **Recursive call không bounded**: `BE/cart-service/.../repository/CartRepository.java:107-114` gọi đệ quy không giới hạn → stack overflow.
  2. **Sleep trong request thread**: Tốn Tomcat thread, không phục vụ request khác. Nên dùng async với `CompletableFuture`.
  3. **Lock không tự renew**: Nếu business logic lâu hơn TTL → lock expire → thread khác cùng lúc vào critical section → race.
- 🔍 **Verify**: Reproduce: trong `findByUserIdOrCreate` thêm `Thread.sleep(6000)` (giả lập GC) → fire 10 concurrent request cho cùng userId mới → check có StackOverflowError không.

📎 `BE/cart-service/.../repository/CartRepository.java:107-114`, tech-debt #2. 🏷️ #redis #lock #distributed-systems

---

#### Q18 — Product cache không TTL (★★★)

**Câu hỏi**: Product cache dùng `@Cacheable` KHÔNG cấu hình TTL. Nếu admin update product nhưng quên gọi `changeStatus` thì sao? Hậu quả?

**Đáp án**:
- 🎯 **TL;DR**: Stale data **vĩnh viễn** vì không có TTL → chỉ evict khi `createProduct`/`updateProduct`/`changeStatus` gọi `@CacheEvict(allEntries=true)`. Tech-debt #9. Fix: set TTL 5-10 phút cho `product-detail`, dùng `@TimeToLive` annotation, hoặc dùng Redis `EXPIRE` trên key.
- 🔁 **Problem**:
  ```
  T=0:    Admin sửa product #5 (price 100 → 80) trực tiếp ở DB (qua SQL client, fix bug, etc.)
          KHÔNG qua API → @CacheEvict KHÔNG chạy
          Redis vẫn còn: product-detail::5 = {price: 100}
  
  T=10:   User xem product #5 → cache hit → thấy 100 (SAI, phải là 80)
  
  T=∞:    Stale vĩnh viễn (no TTL, không ai evict)
  ```
- ⚖️ **Trade-off cách fix**:
  - **TTL 5-10 phút**: Đơn giản. Cache miss nhiều hơn → tăng tải DB. Nhưng OK cho catalog (read-heavy).
  - **Cache invalidation qua Kafka event**: Khi admin update → publish `product.updated` → consumer ở product-service evict cache. Realtime, nhưng thêm infra.
  - **Write-through**: Update DB + Redis atomic. Phức tạp, cần transaction.
  - **Versioned cache**: Key = `product-detail::{id}:v{version}`. Update version khi DB update. Cache miss tự nhiên, không cần evict explicit.
- ⚠️ **Pitfall**:
  1. **DB direct update**: DBA chạy `UPDATE products SET price=80 WHERE id=5` → bypass ORM → cache stale.
  2. **Bulk import**: Script ETL import 1000 products qua SQL → cache stale cho tất cả.
  3. **Cache stampede**: TTL hết hạn cùng lúc cho 100 popular products → 100 request đồng thời vào DB → spike. Cần lock hoặc pre-warm.
  4. **Memory leak**: Cache không expire → Redis đầy → eviction tự động (LRU) → cũng stale.
- 🔍 **Verify**: `BE/product-service/.../service/ProductCatalogService.java:52-88` không thấy `redisTemplate.expire(key, ...)`. Update product → check Redis: `redis-cli TTL product-detail::5` → `-1` (vĩnh viễn). Đề xuất: thêm `@Cacheable(value="product-detail", key="#id", sync=true)` + custom TTL config.

📎 Tech-debt #9, `BE/product-service/.../service/ProductCatalogService.java:52-88`. 🏷️ #cache #redis #consistency

---

#### Q19 — Feign client latency (★★)

**Câu hỏi**: `CartClient` trong order-service gọi `GET /api/cart` qua OpenFeign. Nếu cart-service chậm 3s thì checkout mất bao lâu? Cách cải thiện?

**Đáp án**:
- 🎯 **TL;DR**: Cộng dồn: `getCart` 3s + `clearCart` ~50ms + reserve publish ~10ms + Kafka roundtrip ~100ms + DB write ~20ms ≈ **~3.2s** (chỉ phần này, chưa tính payment). Cải thiện: Circuit Breaker + Timeout fail-fast (đã có Resilience4j), hoặc cache cart ở order-service.
- 🔁 **Latency budget breakdown**:
  ```
  POST /api/orders:
    │ OrderController.createOrder()                  ~5ms
    │ OrderService.createOrder()
    │   ├─→ CartClient.getCart() [Feign HTTP]        ~3000ms  ← BOTTLENECK
    │   ├─→ parse Cart → snapshot items              ~10ms
    │   ├─→ orderRepo.save() [DB]                    ~20ms
    │   ├─→ CartClient.clearCart() [Feign HTTP]      ~50ms
    │   └─→ OrderEventProducer.publish... [Kafka]    ~10ms
    │ Kafka consumer (async)                         ~100ms (background)
    │                                                ─────────
    │ TOTAL (user-perceived)                         ~3.2s
  ```
- ⚖️ **Trade-off**:
  - **Resilience4j Circuit Breaker (đã có ở commit `caa52b6`)**: Timeout 5s, fail-fast khi cart-service down. User thấy 503 nhanh thay vì đợi 30s (default Tomcat timeout).
  - **Cache cart ở order-service**: 1-2s checkout. Nhưng cart có thể đã cũ → inconsistency khi user add item mới. Cần TTL ngắn + invalidation khi cart update.
  - **Async qua Kafka**: Order-service publish `checkout.requested` → cart-service + product-service + inventory-service react → cuối cùng aggregate → response. Loose coupling nhưng user phải poll.
  - **GraphQL/BFF**: 1 endpoint gom data từ nhiều service, parallel fetch → 1 round trip.
- ⚠️ **Pitfall**:
  1. **Default Feign timeout 10s**: Nếu không config → user đợi 10s. Đã fix bằng CB timeout 5s (commit `caa52b6`).
  2. **Synchronous chain**: Order → Cart → Product. Nếu Product cũng chậm → tổng latency cộng dồn. Cần async hoặc cache.
  3. **No retry**: Nếu network blip 1 lần → fail. Cần `@Retry` (Resilience4j hỗ trợ).
- 🔍 **Verify**: Measure bằng `k6` (đã có trong `BE/benchmark/`): `http_req_duration` cho `POST /api/orders`. So sánh trước/sau khi bật CB.

📎 `BE/order-service/.../service/OrderService.java:30-40`, commit `caa52b6`. 🏷️ #feign #latency #performance

---

#### Q20 — Lua script race condition (★★★)

**Câu hỏi**: Lua script `SET_IF_SAME_SCRIPT` trong cart chỉ `GET`, fallback tạo mới bằng Java. Có race condition không? Fix thế nào?

**Đáp án**:
- 🎯 **TL;DR**: **Có race condition**. 2 request cùng lúc cho user mới: cả 2 GET miss → cả 2 tạo cart mới → setIfAbsent lock chỉ 1 thắng → 1 sleep retry, loop vô tận. Tech-debt #10. Fix: viết Lua atomic `GET or CREATE` trong 1 script.
- 🔁 **Problem flow**:
  ```
  Thread A: EVAL SET_IF_SAME_SCRIPT("cart:user1") → returns nil (miss)
  Thread B: EVAL SET_IF_SAME_SCRIPT("cart:user1") → returns nil (miss)
  
  Thread A: setIfAbsent("lock:cart:user1", "A", 5s) → success
  Thread B: setIfAbsent("lock:cart:user1", "B", 5s) → fail
  Thread B: Thread.sleep(50)
  Thread B: recursive findByUserIdOrCreate()  ← lặp
  
  Thread A: create new Cart() in Java
  Thread A: SETEX "cart:user1" 30d <new_cart_json>
  Thread A: DEL "lock:cart:user1"
  
  Thread B (sau sleep): EVAL GET → STILL NIL  (vì lock đã release TRƯỚC khi Thread A commit)
  Thread B: setIfAbsent lock → success (Thread A đã DEL)
  Thread B: create another new Cart()  ← DUPLICATE OBJECT
  Thread B: SETEX "cart:user1" 30d <another_cart>  ← GHI ĐÈ
  ```
- ⚖️ **Trade-off cách fix**:
  - **Lua atomic GET-or-CREATE**:
    ```lua
    local existing = redis.call('GET', KEYS[1])
    if existing then return existing end
    redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
    return ARGV[1]
    ```
    1 round trip, atomic. Java chỉ nhận JSON. Pros: clean. Cons: phải serialize trước khi eval.
  - **SETNX with value**: `SET key value NX EX 30d` → atomic create-or-fail. Nếu fail → GET lại. 2 round trip, có race nhỏ giữa 2 SET.
  - **Redis HASH + version**: `HSETNX key field value` → atomic per field. Phức tạp hơn.
- ⚠️ **Pitfall**:
  1. **Hiện tại dùng 2 bước**: EVAL GET + Java logic + SET. 3 round trip, 2 race window.
  2. **Lock TTL 5s nhưng business logic có thể lâu hơn**: Lock expire → thread khác vào critical section.
  3. **Không có deadlock detection**: Nếu lock leak (process crash không release) → đợi 5s.
- 🔍 **Verify**: `BE/cart-service/.../repository/CartRepository.java:29-36` đọc `GET_OR_CREATE_SCRIPT`. Reproduce: 2 thread đồng thời `findByUserIdOrCreate("new-user")` → check Redis `cart:new-user` chỉ có 1 entry (OK) hay nhiều entry liên tiếp (BUG).

📎 `BE/cart-service/.../repository/CartRepository.java:29-36, 107-114`, tech-debt #10. 🏷️ #redis #lua #race-condition

---

### 4.5 Tech-debt & Troubleshooting (6 câu)

#### Q21 — Debug order PENDING quá lâu (★★★)

**Câu hỏi**: Order PENDING quá 10 phút không chuyển RESERVED. Debug từng bước thế nào?

**Đáp án**:
- 🎯 **TL;DR**: Dùng correlation ID + Zipkin để trace flow 12 service. Check log ở từng bước: publish Kafka, consume, DB transaction, downstream response. Có thể do (1) Kafka lag, (2) inventory out-of-stock, (3) bug trong handler.
- 🔁 **Debug checklist**:
  ```
  1. Lấy correlation ID: response header "X-Correlation-Id: <uuid>"
  
  2. Zipkin UI (localhost:9411):
     - Filter by serviceName: order-service, inventory-service
     - Tìm trace theo orderId hoặc correlationId
     - Check span tree: createOrder → publishReservationRequested → consumeReservationRequested → reserveStock → publishReserved → consumeReserved → markReserved
     - Span nào có duration > 5s hoặc error? → root cause
  
  3. Nếu Zipkin không có span: check log từng service:
     grep "orderId=<uuid>" /var/log/{order,inventory}-service/
     # hoặc trong container:
     docker logs order-service | grep <uuid>
     # log format: %5p [order-service,abc-123]
  
  4. Check Kafka lag:
     docker exec -it kafka kafka-consumer-groups.sh \
       --bootstrap-server localhost:9092 \
       --describe --group inventory-service
     # LAG > 0 → consumer chậm
  
  5. Check outbox:
     docker exec -it payment-db psql -U postgres -d payment_service \
       -c "SELECT id, event_type, status, created_at FROM outbox_events 
           WHERE aggregate_id='<paymentId>' ORDER BY created_at;"
     # status=FAILED → bug publish
  
  6. Check DB transaction log:
     SELECT * FROM orders WHERE id='<orderId>';
     SELECT * FROM stock_reservations WHERE order_id='<orderId>';
     -- Nếu stock_reservations chưa có → inventory chưa xử lý
  ```
- ⚖️ **Trade-off các tool debug**:
  - **Zipkin (Ecom có)**: Trace end-to-end tự động qua `X-Correlation-Id`. Tốt cho flow liên service.
  - **Loki + Promtail (chưa có)**: Aggregate log theo correlationId. Tốt cho debug lỗi cụ thể.
  - **Kafka UI / AKHQ (chưa có)**: Browse topic, check lag, replay message. Tốt cho Kafka issue.
  - **psql + manual query**: Chậm nhưng chính xác cho DB issue.
- ⚠️ **Pitfall**:
  1. **Correlation ID chưa propagate đến Kafka payload**: Hiện tại MDC có correlationId, nhưng Kafka event payload chỉ có orderId, không có correlationId. Consumer log sẽ sinh correlationId mới → khó trace.
  2. **Sampling probability = 1.0**: Zipkin ghi tất cả span → nhiều data. Production nên sample 0.1.
  3. **Log không có stack trace đầy đủ**: Handler catch exception rồi log message ngắn → mất root cause.
- 🔍 **Verify**: Reproduce: tạo order → manually stop inventory-service → order PENDING → debug bằng checklist trên. Mỗi bước nên ra câu trả lời rõ ràng.

📎 `.claude/knowledge-map.md:237-259, 286-305`, `BE/common/.../web/CorrelationIdFilter.java:13-33`. 🏷️ #debug #observability #tracing

---

#### Q22 — Notification mock → production (★★)

**Câu hỏi**: Notification email không gửi thật (log chỉ thấy "Mock email to=..."). Production cần làm gì? Các bước theo thứ tự?

**Đáp án**:
- 🎯 **TL;DR**: Replace `MockEmailSender` (chỉ log INFO) bằng `JavaMailSender` + SMTP credentials hoặc SendGrid SDK. Cũng cần lookup email thật từ `user-service` qua Feign (tech-debt #1: hiện hardcode `user-{uuid}@example.test`). Thêm retry, dead letter queue, monitoring.
- 🔁 **Implementation steps**:
  ```
  1. Thêm dependency:
     <dependency>
       <groupId>com.sendgrid</groupId>
       <artifactId>sendgrid-java</artifactId>
     </dependency>
     # hoặc spring-boot-starter-mail cho SMTP
  
  2. Tạo bean mới:
     @Service
     @Profile("prod")
     public class SendGridEmailSender implements EmailSender {
       @Override
       public void send(String to, String subject, String body) {
         SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));
         Request request = new Request();
         request.setMethod(Method.POST);
         request.setEndpoint("mail/send");
         request.setBody(...);
         Response response = sg.api(request);
         if (response.getStatusCode() >= 400) throw new EmailSendException(...);
       }
     }
  
  3. Lookup email thật từ user-service:
     @FeignClient("user-service")
     public interface UserClient {
       @GetMapping("/api/users/{id}/profile")
       UserProfileResponse getProfile(@PathVariable UUID id);
     }
     # Replace hardcode: recipient(userId) → userClient.getProfile(userId).email()
  
  4. Retry với Resilience4j:
     @Retry(name = "email", fallbackMethod = "sendFailed")
     public void send(...) { ... }
  
  5. DLQ: Nếu fail 3 lần → save vào notification_failed table → cron job retry sau 1h
  
  6. Monitoring: alert nếu notification_logs.status=FAILED > threshold
  ```
- ⚖️ **Trade-off các provider**:
  - **SMTP (Gmail, AWS SES)**: Đơn giản, dùng `JavaMailSender`. Nhưng SES yêu cầu verify domain, Gmail cần app password.
  - **SendGrid**: API REST, tốt cho high volume, có template. Free tier 100 email/ngày.
  - **Twilio SendGrid / Mailgun / Postmark**: Tương tự, chọn theo giá & region.
  - **AWS SNS**: Push notification (mobile), không phải email.
- ⚠️ **Pitfall**:
  1. **Bounce / spam**: Cần handle bounce email → unsubscribe. Nếu gửi nhiều bounce → IP bị blacklist.
  2. **GDPR / CCPA**: Cần consent trước khi gửi marketing email. Transactional email (order confirmation) OK.
  3. **Email deliverability**: SPF, DKIM, DMARC record cần config DNS. Không có → vào spam.
  4. **Rate limit**: SendGrid free tier 100/ngày. Flash sale 10K order = fail. Cần paid plan.
- 🔍 **Verify**: `BE/notification-service/.../service/MockEmailSender.java:11-13` chỉ thấy `log.info`. Test: thay bean → trigger order confirmed → check email thật đến inbox. Check `notification_logs.status=SENT`.

📎 Tech-debt #1, #8, `BE/notification-service/.../service/MockEmailSender.java:11-13`. 🏷️ #notification #production-readiness

---

#### Q23 — Prometheus chỉ scrape 3/11 service (★★)

**Câu hỏi**: Prometheus scrape config chỉ list 3/11 service. 8 business service không có metric trong Grafana. Fix thế nào?

**Đáp án**:
- 🎯 **TL;DR**: Thêm 8 job vào `BE/infra/prometheus/prometheus.yml` cho port 8081-8088, mỗi job scrape `/actuator/prometheus` mỗi 15s. Reload prometheus (không cần restart). Tech-debt #6. Verify bằng `curl http://localhost:8083/actuator/prometheus | grep jvm_memory_used`.
- 🔁 **Fix**:
  ```yaml
  # BE/infra/prometheus/prometheus.yml
  scrape_configs:
    # existing 3 jobs (api-gateway, discovery-server, config-server)
    - job_name: 'auth-service'
      metrics_path: '/actuator/prometheus'
      scrape_interval: 15s
      static_configs:
        - targets: ['host.docker.internal:8081']
    - job_name: 'user-service'
      # ... port 8082
    - job_name: 'product-service'
      # ... port 8083
    # ... 8084 cart, 8085 inventory, 8086 order, 8087 payment, 8088 notification
  ```
  Sau đó:
  ```bash
  docker exec prometheus kill -HUP 1   # reload config
  # hoặc: curl -X POST http://localhost:9090/-/reload
  ```
- ⚖️ **Trade-off scrape config**:
  - **Static config (Ecom dùng)**: Đơn giản, list IP cứng. Phù hợp khi số service cố định.
  - **Service discovery (Consul, Eureka, K8s)**: Auto-detect khi service thêm/xoá. Tốt cho dynamic infra.
  - **File-based SD**: Watch file → update. Linh hoạt hơn static.
- ⚠️ **Pitfall**:
  1. **Sai host**: `host.docker.internal` chỉ work trên Docker Desktop (Mac/Windows). Trên Linux cần `172.17.0.1` hoặc `network_mode: host`.
  2. **Authentication**: Nếu actuator bật security → cần basic auth. Hiện tại Ecom permit `/actuator/prometheus` cho mọi user.
  3. **Metric cardinality explosion**: Nếu label = userId → 1M user = 1M series → Prometheus OOM. Chỉ dùng bounded labels (status, endpoint).
  4. **Network**: Prometheus container phải reach được business service qua Docker network. Trong docker-compose, dùng `network_mode: host` hoặc share network.
- 🔍 **Verify**: Sau khi thêm, `curl http://localhost:9090/api/v1/targets` → check 8 target mới state=UP. Query: `up{job="auth-service"}` → 1.

📎 `BE/infra/prometheus/prometheus.yml:5-17`, tech-debt #6. 🏷️ #prometheus #monitoring #observability

---

#### Q24 — Memory leak cart-service (★★★)

**Câu hỏi**: Memory leak cart-service sau 24h. Nghi ngờ gì? Debug thế nào?

**Đáp án**:
- 🎯 **TL;DR**: Nghi ngờ: (1) `Thread.sleep(50)` recursion không terminate → thread bị block giữ connection; (2) Kafka listener container leak; (3) Feign client không release HTTP connection. Cách debug: enable heap dump (`-XX:+HeapDumpOnOutOfMemoryError`), VisualVM, jmap, jstack, pprof.
- 🔁 **Debug checklist**:
  ```
  1. Heap dump khi OOM:
     Add JVM option: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap.hprof
     # Trigger bằng load test hoặc đợi 24h
  
  2. Analyze heap dump:
     # Eclipse MAT hoặc VisualVM
     # Tìm class chiếm nhiều memory nhất
     # Nếu thấy nhiều "java.lang.Thread" + "CartRepository$..." → thread leak
     # Nếu thấy nhiều "org.apache.http.Connection" → Feign connection leak
  
  3. Thread dump:
     jstack <pid> > thread-dump.txt
     # Tìm thread ở state BLOCKED hoặc WAITING với stack trace cart-related
     # 100 thread "CartRepository.findByUserIdOrCreate" → busy-wait leak
  
  4. Profile trong runtime:
     # VisualVM →Sampler → CPU/Memory
     # Chạy load test, watch memory tăng tuyến tính → leak
  
  5. Check metrics:
     curl http://cart-service:8084/actuator/metrics/jvm.threads.live
     # Tăng đều qua 24h → thread leak
     curl http://cart-service:8084/actuator/metrics/jvm.memory.used
     # Heap tăng đều → memory leak
  ```
- ⚖️ **Trade-off các tool**:
  - **Eclipse MAT**: Powerful, parse heap dump offline. Free.
  - **VisualVM**: Live profiling + thread dump. Free.
  - **YourKit / JProfiler**: Commercial, đẹp hơn, sampling tốt hơn.
  - **jcmd + jfr**: Built-in, low overhead, good cho production.
- ⚠️ **Pitfall**:
  1. **Heap dump lớn**: 4GB heap = 4GB dump file. Cần disk space. Tắt auto dump trong prod, chỉ bật khi nghi ngờ.
  2. **GC root unreachable**: Leak do static field thì GC không collect được. MAT sẽ chỉ.
  3. **Native memory leak**: DirectByteBuffer, JNI. Không thấy trong heap dump. Dùng `jcmd <pid> VM.native_memory`.
  4. **Connection pool leak**: HikariCP, Feign HTTP client. Symptom: thread "await" pool connection.
- 🔍 **Verify**: Reproduce: tạo script load test `wrk -t10 -c100 -d24h ...` (không thực tế, dùng `k6` thay). Monitor memory mỗi 5 phút.

📎 Tech-debt #2, `BE/cart-service/.../repository/CartRepository.java:107-114`. 🏷️ #performance #memory-leak #debug

---

#### Q25 — GlobalExceptionHandler không hoạt động (★★)

**Câu hỏi**: Lỗi `GlobalExceptionHandler not found` khi start service. Tại sao? Hậu quả? Fix?

**Đáp án**:
- 🎯 **TL;DR**: Tech-debt #5: `common.web.GlobalExceptionHandler` tồn tại nhưng KHÔNG được scan. Mỗi service tự viết `*ExceptionHandler` riêng → format lỗi không đồng nhất. Fix: thêm `@Import(GlobalExceptionHandler.class)` vào main class HOẶC `@SpringBootApplication(scanBasePackages = "com.ecom")`.
- 🔁 **Problem**:
  ```
  BE/common/src/main/java/com/ecom/common/web/GlobalExceptionHandler.java:
    @RestControllerAdvice
    public class GlobalExceptionHandler {
      @ExceptionHandler(MethodArgumentNotValidException.class)
      public ResponseEntity<ErrorResponse> handle(...) { ... }
    }
  
  # Comment trong file: "Must be imported by each service's component scan"
  # NHƯNG: không có service nào thực sự import → bean không được tạo
  
  # Kết quả: mỗi service có handler riêng
  BE/cart-service/.../exception/CartExceptionHandler.java  → format A
  BE/payment-service/.../exception/PaymentExceptionHandler.java → format B
  BE/order-service/.../exception/OrderExceptionHandler.java → format C
  
  # Client phải handle 3 format error khác nhau
  ```
- ⚖️ **Trade-off cách fix**:
  - **`@Import(GlobalExceptionHandler.class)` ở main class**: Đơn giản, explicit. Phải thêm vào 9 service.
  - **`@ComponentScan(basePackages = "com.ecom")`**: Scan toàn bộ. Có thể pick up bean không mong muốn.
  - **`@SpringBootApplication(scanBasePackages = {...})`**: Tương tự.
  - **Tạo module con `common-web-starter` với auto-config**: Dùng `spring.factories` → tự động. Phức tạp nhất, tốt nhất.
- ⚠️ **Pitfall**:
  1. **Duplicate handler**: Nếu `@Import` GlobalExceptionHandler + service có `*ExceptionHandler` riêng → 2 handler match → Spring chọn specific hơn (cùng class). Nên xóa handler cũ.
  2. **Order**: `@Order(Ordered.HIGHEST_PRECEDENCE)` cho GlobalExceptionHandler để nó chạy trước.
  3. **Logging**: GlobalExceptionHandler nên log error đầy đủ (stack trace) trước khi trả response.
- 🔍 **Verify**: `BE/common/.../web/GlobalExceptionHandler.java:21-96` đọc comment. `grep -r "@Import" BE/*/src/main/java/com/ecom/*/EcomServiceApplication.java` → hiện tại 0 kết quả. Sau fix: 9 kết quả. Test: gửi invalid request → check response format đồng nhất.

📎 Tech-debt #5, `BE/common/.../web/GlobalExceptionHandler.java:21-96`. 🏷️ #architecture #code-quality #consistency

---

#### Q26 — Correlation ID debug flow (★★)

**Câu hỏi**: Lỗi 500 random. Correlation ID giúp debug thế nào? Từng bước?

**Đáp án**:
- 🎯 **TL;DR**: Log format `%5p [appName,correlationId]`. Client gửi `X-Correlation-Id` (gateway tự generate nếu thiếu). MDC propagate xuống mọi service qua filter. Khi lỗi, response có header `X-Correlation-Id` → grep Kibana/Grafana Loki theo ID → xem full trace 12 service.
- 🔁 **Flow**:
  ```
  Client request:
    GET /api/products
    X-Correlation-Id: abc-123-uuid  (hoặc gateway tự sinh)
       │
       ▼
  api-gateway.GatewayCorrelationIdFilter:
    - Read header "X-Correlation-Id"
    - Nếu null → UUID.randomUUID()
    - Set MDC: MDC.put("correlationId", "abc-123")
    - Mutate request: header X-Correlation-Id = abc-123
    - Echo response: header X-Correlation-Id = abc-123
       │
       ▼
  product-service.CorrelationIdFilter (servlet):
    - Read header "X-Correlation-Id"
    - Set MDC: MDC.put("correlationId", "abc-123")
    - Echo response
       │
       ▼
  All log statements:
    log.info("Searching products keyword={}", keyword)
    # → "INFO [product-service,abc-123] Searching products keyword=phone"
       │
       ▼
  Client nhận response:
    HTTP 500
    X-Correlation-Id: abc-123
    Body: { "code": "...", "message": "...", "correlationId": "abc-123" }
       │
       ▼
  Dev debug:
    1. Copy "abc-123"
    2. Kibana query: correlationId:"abc-123" → tất cả log 12 service
    3. Zipkin query: traceId="abc-123" → span tree
    4. Tìm log ERROR → root cause
  ```
- ⚖️ **Trade-off các approach**:
  - **MDC + log pattern (Ecom)**: Đơn giản, không cần infra. Log structured qua MDC.
  - **Trace ID riêng (W3C tracecontext)**: `traceparent` header, OpenTelemetry standard. Mạnh hơn, cross-service trace tốt hơn.
  - **Centralized log (ELK, Loki)**: Cần ELK stack. Tốt cho search.
  - **APM tool (Datadog, New Relic)**: Commercial, đẹp nhưng $$.
- ⚠️ **Pitfall**:
  1. **MDC bị mất khi async**: `@Async` thread mới → MDC empty. Phải wrap `Runnable` để copy MDC.
  2. **Kafka payload chưa có correlationId**: Producer tạo MDC, nhưng khi gọi `kafkaTemplate.send()` ở thread khác → MDC empty → log không có correlationId. Cần set `kafkaTemplate.setObservationEnabled(true)` (Spring 3+) hoặc tự inject.
  3. **Sampling**: Zipkin sampling = 1.0 hiện tại → nhiều trace. Production nên 0.1.
  4. **Log level**: Mặc định INFO. Bật DEBUG chỉ khi cần debug cụ thể → log nhiều → tốn disk.
- 🔍 **Verify**: Trigger lỗi 500 (vd invalid payload). Lấy `X-Correlation-Id` từ response header. Grep log: `grep "abc-123" /var/log/*/app.log`. Phải thấy log ở cả gateway + downstream service.

📎 `.claude/knowledge-map.md:237-259`, `BE/common/.../web/CorrelationIdFilter.java:13-33`, `BE/api-gateway/.../filter/GatewayCorrelationIdFilter.java:17-58`. 🏷️ #observability #debug #logging

---

### 4.6 E-commerce tổng quát (7 câu)

#### Q27 — Flash sale design (★★★)

**Câu hỏi**: Flash sale 1 triệu user cùng click "Mua" trong 1 giây. Thiết kế inventory reservation thế nào để không oversell? Ecom hiện tại có handle được không?

**Đáp án**:
- 🎯 **TL;DR**: Không oversell = atomic check + atomic decrement. 5 cách: (1) optimistic version, (2) pessimistic DB lock, (3) Redis atomic DECRBY, (4) queue-based serial, (5) pre-shard stock. Ecom hiện dùng (2) qua DB transaction → **bottleneck** khi flash sale.
- 🔁 **5 cách so sánh**:
  ```
  ┌─────────────────────┬──────────────┬────────────┬──────────────┐
  │ Cách                │ Throughput   │ Complexity │ Ecom dùng?   │
  ├─────────────────────┼──────────────┼────────────┼──────────────┤
  │ 1. Optimistic check │ High         │ Low        │ Không        │
  │    (version field)  │ (retry on    │            │              │
  │                     │  conflict)   │            │              │
  │ 2. Pessimistic DB   │ Low          │ Low        │ CÓ           │
  │    (SELECT FOR      │ (serialize   │            │ (transaction │
  │     UPDATE)         │  per row)    │            │  trong       │
  │                     │              │            │  reserve)    │
  │ 3. Redis atomic     │ Very High    │ Medium     │ Không        │
  │    DECRBY           │ (in-memory)  │            │ (chỉ dùng    │
  │                     │              │            │  cho cart)   │
  │ 4. Queue-based      │ Medium       │ High       │ Không        │
  │    (Kafka serial)   │ (single      │            │              │
  │                     │  consumer)   │            │              │
  │ 5. Pre-shard stock  │ High         │ High       │ Không        │
  │    (10 shards       │ (parallel)   │            │              │
  │     per product)    │              │            │              │
  └─────────────────────┴──────────────┴────────────┴──────────────┘
  
  Ecom hiện tại: cách 2 → với 1M concurrent:
    - 1M request → 1M transaction Postgres
    - 1M SELECT FOR UPDATE trên cùng product_id
    - Tất cả serialize → 1 thread xử lý tại 1 thời điểm
    - Avg latency 5s × 1M = throughput ~200 req/s
    - → SỤP
  ```
- ⚖️ **Trade-off**:
  - **Cách 2 (Ecom)**: 1M request × 1 transaction × 5ms = ~80 phút. Quá tệ.
  - **Cách 3 (Redis DECRBY)**: 1M atomic DECRBY trên key `stock:product:5` = 1M ops trong ~5s. OK. Nhưng cần sync Redis → DB (eventual).
  - **Cách 5 (Pre-shard)**: 10 shard `stock:product:5:shard1..10` → mỗi shard 100K stock. 10 consumer parallel → 10x throughput. Phức tạp nhất nhưng scale tốt nhất.
  - **Cách 4 (Queue)**: 1M request → 1M Kafka message → 1 consumer process tuần tự → ~5 phút cho 1M. User phải poll trạng thái.
- ⚠️ **Pitfall**:
  1. **Oversell**: 2 user cùng read stock=1 → cả 2 reserve → cả 2 success → bán 2 dù chỉ có 1.
  2. **Undersell**: Lock sai, fail quá nhiều → bán 0 dù stock còn.
  3. **Hot key**: 1 product hot → 1M ops trên 1 key → Redis single-threaded → bottleneck. Cần sharding hoặc pre-warm nhiều instance.
  4. **Cart hold vs inventory reserve**: Ecom reserve khi checkout, không phải khi add to cart. Flash sale cần reserve khi "Add to cart" hoặc "Notify me" để chống flood.
- 🔍 **Verify**: Estimate: 1M user, 1s peak, 100K stock. Ecom = 100K × 5s transaction = bottleneck. Đề xuất: pre-shard Redis cho hot products.

📎 `BE/inventory-service/.../service/InventoryService.java:56-127`. 🏷️ #flash-sale #scalability #inventory

---

#### Q28 — Idempotent payment (standard pattern) (★)

**Câu hỏi**: Payment idempotency: client retry 3 lần do timeout. Server đảm bảo chỉ charge 1 lần thế nào? So sánh Ecom với Stripe.

**Đáp án**:
- 🎯 **TL;DR**: 5 bước: (1) Client cung cấp `Idempotency-Key` (UUID), (2) Server check key trong DB trước khi charge, (3) Nếu có → trả response cũ, (4) Nếu chưa → charge + lưu key + response, (5) UNIQUE constraint ở DB. Stripe làm đúng pattern này. Ecom cũng làm đúng ở Q9.
- 🔁 **Standard flow**:
  ```
  1. Client sinh UUID ngay khi user click "Pay":
     const idempotencyKey = crypto.randomUUID();
  
  2. Send request với header:
     POST /api/payments
     Idempotency-Key: <uuid>
     Body: {orderId, amount, currency}
  
  3. Server check DB:
     SELECT * FROM payments WHERE idempotency_key = '<uuid>';
     │
     ├─ found → return stored response (status, paymentId, etc.)
     └─ null  ↓
  
  4. Process payment (charge provider):
     INSERT INTO payments (..., idempotency_key='<uuid>', status='PENDING')
     -- UNIQUE constraint → safety net
  
  5. Trả response cho client, client lưu kèm idempotencyKey
  
  --- Retry (network timeout, 5xx) ---
  
  Client gửi LẠI với CÙNG idempotencyKey
  Server check → found → return SAME response
  → Không charge lần 2
  ```
- ⚖️ **Ecom vs Stripe**:
  - **Ecom**: `pay-{orderId}` key (deterministic), TTL vĩnh viễn, không verify body match.
  - **Stripe**: UUID per attempt, TTL 24h, verify body match (nếu khác → 422). Production-grade.
- ⚠️ **Pitfall**:
  1. **Lost key**: Client crash trước khi nhận response → key mất → user retry với key mới → double charge. Mitigate: lưu key ở client ngay khi generate.
  2. **Different body same key**: Client retry nhưng đổi amount (vd tip) → server vẫn trả response cũ. Ecom không check. Stripe trả 422.
  3. **TTL quá ngắn**: Retry sau 25h với key cũ → không idempotent. Stripe TTL 24h, sau đó key expire.
- 🔍 **Verify**: `BE/payment-service/.../service/PaymentService.java:36-41` đọc flow. Test: gửi 3 request cùng key → check 1 charge duy nhất.

📎 `BE/payment-service/.../service/PaymentService.java:36-41`, Stripe API docs. 🏷️ #payment #idempotency #best-practice

---

#### Q29 — Cart abandonment (★★)

**Câu hỏi**: Cart abandonment 70% ở bước checkout. Cách giảm? Ecom có thể cải thiện gì?

**Đáp án**:
- 🎯 **TL;DR**: 7 chiến lược: guest checkout, save cart for guest, one-click, auto-fill, progress indicator, email reminder, real-time shipping. Ecom hiện bắt login ngay từ đầu → friction cao. Đề xuất: guest flow + cart persistence qua cookie.
- 🔁 **7 chiến lược + impact estimate**:
  ```
  ┌────────────────────────┬────────────────────────┬──────────────┐
  │ Strategy               │ Implementation        │ Impact est.  │
  ├────────────────────────┼────────────────────────┼──────────────┤
  │ 1. Guest checkout      │ Không bắt login        │ -20% abandon │
  │    (Ecom chưa có)      │ → trừ 2 field (email)  │              │
  │ 2. Save cart for guest │ Cart trong cookie/local │ -10% abandon │
  │    (Ecom Redis có,     │ → sau login merge với  │              │
  │     nhưng phải login)  │   user cart            │              │
  │ 3. One-click checkout  │ Lưu payment + address  │ -15% abandon │
  │    (Amazon patent)     │ 1 click = order        │              │
  │ 4. Auto-fill address   │ Google Places API      │ -5% abandon  │
  │ 5. Progress indicator  │ "Step 2/3: Payment"    │ -5% abandon  │
  │ 6. Email reminder      │ Sau 24h, 72h           │ -10% abandon │
  │ 7. Real-time shipping  │ Tính ngay ở cart page  │ -8% abandon  │
  └────────────────────────┴────────────────────────┴──────────────┘
  ```
- ⚖️ **Trade-off**:
  - **Guest checkout**: Tăng conversion nhưng mất user data (email optional). Cần cân nhắc GDPR consent.
  - **One-click**: Phải lưu payment info → tăng security risk (PCI DSS). Cần tokenization (Stripe Elements).
  - **Email reminder**: Phải có ESP (SendGrid). Có thể bị spam complaint.
  - **Real-time shipping**: Cần API GHN/GHTK → tốn cost per request.
- ⚠️ **Pitfall**:
  1. **Friction cao ở auth**: Ecom `POST /api/orders` yêu cầu Bearer token ngay. User phải register trước → tăng abandon 30-40%.
  2. **Cart expire sớm**: TTL 30 ngày OK, nhưng Redis có thể bị evict sớm hơn nếu memory full.
  3. **Email reminder spam**: Gửi 3 lần/ngày → user unsubscribe → tăng bounce rate.
  4. **Mobile UX**: Ecom FE chưa optimize mobile. 60% user trên mobile.
- 🔍 **Verify**: Đo funnel: `Add to cart` → `Checkout start` → `Payment` → `Success`. Tỷ lệ drop-off ở đâu → ưu tiên fix. Tool: Google Analytics funnel, Hotjar session recording.

📎 `BE/cart-service/.../repository/CartRepository.java:21, 116-129`, `FE/`. 🏷️ #ux #conversion #best-practice

---

#### Q30 — Distributed transaction (★★★)

**Câu hỏi**: Distributed transaction giữa Order + Inventory + Payment. Tại sao không dùng 2PC? Ecom dùng gì?

**Đáp án**:
- 🎯 **TL;DR**: 2PC chặt (sync, blocking) → giảm availability, không scale, coordinator SPOF. Ecom dùng **Saga** (eventual consistency, mỗi step có compensation). Alternative: TCC, Event Sourcing. Trade-off Saga: khó debug, cần distributed tracing.
- 🔁 **2PC vs Saga**:
  ```
  2PC (XA transaction):
  ┌──────────┐     ┌──────────┐     ┌──────────┐
  │  Order   │     │Inventory │     │ Payment  │
  │  Service │     │ Service  │     │ Service  │
  └────┬─────┘     └────┬─────┘     └────┬─────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
                  ┌─────▼──────┐
                  │ Coordinator │  ← SPOF, sync block
                  └────────────┘
  
  Flow:
    1. Coordinator → All: PREPARE
    2. All: lock resource, write to log, vote YES/NO
    3. If all YES → Coordinator: COMMIT → All: commit
       If any NO → Coordinator: ROLLBACK → All: rollback
    4. Lock held until step 3 → blocking, slow
  
  Saga (Ecom dùng):
  ┌──────────┐  event   ┌──────────┐  event   ┌──────────┐
  │  Order   │ ───────▶ │Inventory │ ───────▶ │ Payment  │
  │  Service │          │ Service  │          │ Service  │
  └──────────┘          └──────────┘          └──────────┘
       │                     │                     │
       │  if fail            │  if fail            │
       ▼                     ▼                     ▼
  compensate           compensate            compensate
  (cancel order)       (release stock)       (refund)
  
  Flow:
    1. Order create → publish event
    2. Inventory reserve → publish event
    3. Payment charge → publish event
    4. If step 2 fail → Order cancel (compensation)
    5. Eventual consistency, no lock
  ```
- ⚖️ **Trade-off**:
  - **2PC**: Strong consistency, đơn giản tư duy. Nhưng: (a) blocking, (b) coordinator SPOF, (c) không scale horizontally, (d) lock trong DB rất lâu → throughput thấp.
  - **Saga (Ecom)**: Eventual consistency (cuối cùng cũng đúng). Loose coupling, scale tốt. Nhưng: (a) phải viết compensation cho mỗi step, (b) khó debug, (c) business phải chấp nhận "tạm thời inconsistent".
  - **TCC (Try-Confirm-Cancel)**: Chia 3 phase. Rõ ràng hơn Saga nhưng phức tạp hơn. Dùng cho finance (Alibaba).
  - **Event Sourcing**: Lưu event, derive state. Audit tốt nhưng phức tạp nhất.
- ⚠️ **Pitfall**:
  1. **Compensation fail**: Nếu compensation cũng fail (vd inventory-service down khi release) → data inconsistent. Cần retry + manual intervention.
  2. **Long-running saga**: User đợi 30s cho payment → bad UX. Cần async + webhook.
  3. **Idempotency**: Mỗi step phải idempotent vì event có thể deliver nhiều lần. Ecom đã làm.
  4. **Trace**: Cần correlation ID + Zipkin. Ecom có.
- 🔍 **Verify**: Ecom = Saga qua Kafka. `BE/order-service/.../messaging/*EventConsumer.java` chứng minh. Test: tạo order → stop payment-service → check inventory được release (compensation).

📎 `.claude/knowledge-map.md:126-142`, `BE/.../messaging/*EventConsumer.java`. 🏷️ #distributed-systems #saga #consistency

---

#### Q31 — SQL injection prevention (★★)

**Câu hỏi**: SQL injection: tìm product theo keyword. Ecom có an toàn không? Cách verify?

**Đáp án**:
- 🎯 **TL;DR**: An toàn. Dùng JPA `Specification` với `criteriaBuilder.like(...)` → bind parameter (PreparedStatement) → không concatenate string. KHÔNG dùng `LIKE '%" + userInput + "%'`. Verify bằng cách thử payload injection: `' OR '1'='1`.
- 🔁 **Cách Ecom làm**:
  ```java
  // BE/product-service/.../service/ProductCatalogService.java
  public Page<ProductSummaryResponse> search(ProductSearchCriteria criteria, ...) {
    Specification<Product> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("active"), true));
      
      if (criteria.keyword() != null) {
        String pattern = "%" + criteria.keyword().toLowerCase() + "%";
        predicates.add(cb.like(cb.lower(root.get("name")), pattern));
        //                      JPA bind parameter → PreparedStatement
      }
      // ...
      return cb.and(predicates.toArray(new Predicate[0]));
    };
    return productRepo.findAll(spec, pageable);
  }
  ```
  Khi chạy, Hibernate generate SQL:
  ```sql
  SELECT * FROM products WHERE active = true AND LOWER(name) LIKE ? 
  --                          bind: %phone%
  -- KHÔNG phải: LOWER(name) LIKE '%phone%' (concatenate)
  ```
- ⚖️ **Các cách phòng chống**:
  - **PreparedStatement / Parameterized query (Ecom dùng)**: Best practice. User input treated as data, not code.
  - **ORM (Hibernate, JPA)**: Tự động escape. An toàn nếu dùng API chuẩn.
  - **Whitelist input**: Chỉ cho phép alphanumeric, loại bỏ ký tự đặc biệt.
  - **WAF (Web Application Firewall)**: Cloudflare, ModSecurity. Defense in depth.
  - **Escape function**: MySQL `mysql_real_escape_string`. Dễ quên, không khuyến nghị.
- ⚠️ **Pitfall**:
  1. **Native query với string concat**: `em.createNativeQuery("SELECT * FROM products WHERE name LIKE '%" + input + "%'")` → VULNERABLE. Ecom không dùng.
  2. **Order BY injection**: `ORDER BY ${userInput}` → không thể bind. Phải whitelist: `if (sortBy in ["name", "price"])`.
  3. **JPA `@Query` với concat**: `@Query("SELECT p FROM Product p WHERE p.name LIKE %:keyword%")` → OK nếu dùng `:keyword`. Nhưng `LIKE '%" + kw + "%'"` (Java concat trong JPQL) → VULNERABLE.
  4. **JSON field injection**: `preferences->>'key' = 'value'` (Postgres) → vẫn OK nếu bind.
- 🔍 **Verify**: Test payload: `keyword = "' OR '1'='1"`. Nếu trả về tất cả product → vulnerable. Nếu trả về 0 (no match cho ký tự đặc biệt) → OK. Test thêm `keyword = "phone'; DROP TABLE products;--"` → nếu table bị drop → VULNERABLE.

📎 `BE/product-service/.../service/ProductCatalogService.java:94-113`. 🏷️ #security #sql-injection #best-practice

---

#### Q32 — Rate limiting (★★)

**Câu hỏi**: Rate limiting cho `POST /api/auth/login` chống brute force. Ecom đã làm chưa? So sánh các cách?

**Đáp án**:
- 🎯 **TL;DR**: **Có** (commit `0340f03` tháng 6): RequestRateLimiter STRICT ở gateway, 5 req/min/IP cho login/register, NORMAL cho refresh/products. Dùng Redis token bucket qua `spring-cloud-gateway`. Đã giải quyết một phần tech-debt #12.
- 🔁 **Cấu hình Ecom**:
  ```yaml
  # BE/config-repo/api-gateway.yml (commit 0340f03)
  routes:
    - id: auth-login
      uri: lb://auth-service
      predicates:
        - Path=/api/auth/login
      filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 5    # 5 token / giây? phút?
            redis-rate-limiter.burstCapacity: 10   # burst tối đa
            redis-rate-limiter.requestedTokens: 1
            key-resolver: "#{@ipKeyResolver}"     # rate theo IP
  ```
- ⚖️ **Các cách rate limit**:
  - **Per-IP (Ecom dùng)**: Đơn giản, chống brute force 1 IP. Nhưng attacker dùng botnet (1M IP) → bypass.
  - **Per-user**: Sau login, rate theo userId. Tốt cho authenticated API.
  - **Per-API-key**: Cho B2B. Mỗi customer có quota riêng.
  - **Global rate**: Bảo vệ backend tổng quát. Production nên có cả global + per-endpoint.
  - **Token bucket vs Sliding window**: Token bucket cho phép burst, sliding window chính xác hơn.
- ⚠️ **Pitfall**:
  1. **Behind proxy**: `X-Forwarded-For` header → IP thật nằm trong header, không phải remote IP. Cần config `setTrustedProxies`.
  2. **Shared IP (NAT)**: Công ty, trường học → 100 user cùng IP → 1 user spam → 99 user bị block. Cần kết hợp per-IP + per-user.
  3. **Redis down**: Nếu Redis down, rate limiter fail-open (cho qua) hay fail-closed (chặn)? Mặc định Ecom fail-open → rủi ro brute force khi Redis down.
  4. **Distributed attack**: 1M IP cùng brute force → per-IP không đủ. Cần CAPTCHA hoặc device fingerprinting.
- 🔍 **Verify**: `BE/config-repo/api-gateway.yml` đọc config. Test: `for i in {1..20}; do curl -X POST /api/auth/login; done` → request 6+ trả 429 Too Many Requests.

📎 `BE/config-repo/api-gateway.yml`, commit `0340f03`, tech-debt #12. 🏷️ #security #rate-limiting #brute-force

---

#### Q33 — Circuit Breaker UX (★★)

**Câu hỏi**: Khi 1 service down (vd payment), đơn hàng đang xử lý thì sao? User trải nghiệm thế nào? Ecom có vấn đề gì?

**Đáp án**:
- 🎯 **TL;DR**: Circuit Breaker mở (đã có Resilience4j) → fail-fast → user thấy 503 "Service temporarily unavailable". Cart vẫn giữ, order vẫn PENDING. Khi service recover → user retry. UX tốt hơn: queue order async, xử lý qua Kafka, email khi hoàn tất. Ecom hiện sync → user đợi hoặc phải retry.
- 🔁 **Flow**:
  ```
  User click "Place order"
       │
       ▼
  OrderService.createOrder()
       │
       ├─→ CartClient.getCart()  ── 200ms ✓
       │
       ├─→ InventoryClient.reserve()  ── 5000ms ✗ (payment-service down)
       │     │
       │     └─→ Resilience4j Circuit Breaker
       │           ├─ count fail: 1, 2, 3, 4, 5 → 5 fail trong 10s
       │           ├─ CB open → fail-fast (1ms)
       │           ├─ Wait 30s (slidingWindow)
       │           ├─ Half-open: cho 1 request thử
      │           │   ├─ success → close CB
       │           │   └─ fail → open lại 30s
       │
       └─→ throw ServiceUnavailableException
            │
            ▼
  OrderController → 503 Service Unavailable
  Body: { "code": "SVC_DOWN", "message": "Payment service tạm thời không khả dụng" }
       │
       ▼
  User thấy toast "Đặt hàng thất bại, vui lòng thử lại sau 1 phút"
  ```
- ⚖️ **Trade-off UX**:
  - **Sync fail-fast (Ecom hiện tại)**: User biết ngay, retry được. Nhưng cart vẫn giữ → user tự retry. Không tự động.
  - **Async queue + email**: Order POST → trả 202 Accepted ngay, queue xử lý, email khi xong. UX tốt nhưng phức tạp.
  - **Degraded mode**: Cho phép checkout không cần payment (trả sau) → business risk.
  - **Cached fallback**: Trả response cũ (nếu có) → consistency risk.
- ⚠️ **Pitfall**:
  1. **Cart stale**: User giữ cart 30 phút, retry → cart có thể hết hàng (inventory thay đổi). Cần re-check khi retry.
  2. **Payment retry**: Nếu user retry với key mới (Ecom `pay-{orderId}`) → fail vì order đã có payment PENDING.
  3. **CB config quá nhạy**: 5 fail trong 10s → mở CB → 1 false positive (network blip) đóng cả service 30s. Cần tune threshold.
  4. **No graceful degradation**: User thấy 503 → không biết khi nào retry. Cần `Retry-After` header.
- 🔍 **Verify**: Stop payment-service → tạo order → check log có `CircuitBreaker 'payment' is OPEN` không. Response time < 100ms (fail-fast). Add `Retry-After: 30` header.

📎 Commit `f047a92`, `caa52b6`, `4c94a1d` (Resilience4j). 🏷️ #resilience #ux #circuit-breaker

---

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
