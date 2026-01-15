# 프로젝트 간 메시징 계약 (Telegram Bot ↔ MySecondBrain-Worker)

## 범위
- 텔레그램 봇 서버(또는 기타 프로듀서)와 MySecondBrain-Worker 사이의 RabbitMQ 기반 메시징 계약을 정의합니다.
- 워커는 특정 메신저 SDK에 종속되지 않으며, 아래 계약만 지키면 교체/확장이 가능합니다.

## 토폴로지 개요
- 공통 교환기/큐
  - 요청: exchange `bot.exchange` (topic), queue `brain.inbox.q`, routing key `brain.req.#`
  - 응답: exchange `brain.reply.exchange` (topic) — 필요 시 default routing key 설정 가능
  - DLQ: exchange `brain.dlx` (direct), queue `brain.inbox.dlq`, routing key `brain.req.dlq`
- 텔레그램 전용 교환기/큐
  - 송신(타 서비스 → 텔레그램): exchange `telegram.exchange`, queue `telegram.outgoing.q`, routing key `telegram.outgoing`
  - 수신(텔레그램 → 워커/다른 서비스): exchange `telegram.exchange`, routing key `telegram.incoming` (소비 큐는 환경별로 바인딩)

## Payload 계약
- 요청 → 워커 (`brain.req.#`)
  ```json
  {
    "eventId": "uuid-v4",
    "timestamp": "2026-01-13T10:00:00Z",
    "userId": "user-123",
    "type": "CHAT" | "SYNC",
    "content": "내일 오후 2시 강남역 미팅 잡아줘"
  }
  ```
  - `eventId`는 idempotency 키로 재사용(중복 처리 방지).
  - `type=CHAT` 시 LLM으로 intent 파싱, `SYNC` 시 동기화 트리거.
- 응답 ← 워커 (`brain.reply.exchange`)
  ```json
  {
    "replyToUserId": "user-123",
    "content": "✅ 일정이 등록되었습니다. (관련 노트: [[2026-01-14-미팅]])"
  }
  ```
- 텔레그램 수신 → 워커 (`telegram.incoming`)
  ```json
  {
    "updateId": 123,
    "chatId": 123456789,
    "from": "username",
    "text": "/todo",
    "epochSeconds": 1700000000
  }
  ```
  - `text` 필수, 공백 불가. `updateId` 단조 증가 권장.
- 텔레그램 송신 ← 워커/타 서비스 (`telegram.outgoing`)
  ```json
  { "chatId": 123456789, "text": "hello", "parseMode": "Markdown" }
  ```

## 예시 명령어 (rabbitmqadmin)
- 텔레그램 수신 발행
  ```bash
  rabbitmqadmin publish \
    exchange=telegram.exchange \
    routing_key=telegram.incoming \
    payload='{"updateId":123,"chatId":123456789,"from":"tester","text":"/todo","epochSeconds":1700000000}'
  ```
- 텔레그램 송신 발행
  ```bash
  rabbitmqadmin publish \
    exchange=telegram.exchange \
    routing_key=telegram.outgoing \
    payload='{"chatId":123456789,"text":"hello","parseMode":"Markdown"}'
  ```
- 워커 요청 발행
  ```bash
  rabbitmqadmin publish \
    exchange=bot.exchange \
    routing_key=brain.req.chat \
    payload='{"eventId":"uuid","timestamp":"2026-01-13T10:00:00Z","userId":"user-123","type":"CHAT","content":"note"}'
  ```

## 에러/재시도/DLQ
- `brain.req.#` 소비 실패 시 `failure-strategy=reject`, 자동 DLQ 바인딩(`brain.inbox.dlq`) 적용.
- 텔레그램 발행 시 직렬화 실패는 워커 로그 경고 후 drop; 필요 시 브로커 레벨 재시도/개별 DLQ를 운영 정책으로 추가 가능.

## 프로필별 차이
- dev: `smallrye-in-memory` 커넥터로 로컬 테스트 (브로커 불필요).
- prod: RabbitMQ 사용, 교환기/큐/바인딩을 사전 선언해야 함.

## 상관관계/중복 처리
- 요청 메시지의 `eventId`는 idempotency 키로 사용되며, 워커에서 재처리 방지.
- RabbitMQ `correlationId` 메타데이터가 있으면 로그 MDC에 기록해 추적성을 높입니다.

## 환경 변수 요약
- `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASS`
- 텔레그램: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_POLL_INTERVAL_SECONDS`, `TELEGRAM_POLL_TIMEOUT_SECONDS`
- OpenAI/Google/Docker/Vault 경로 등은 `README.md` 참조
