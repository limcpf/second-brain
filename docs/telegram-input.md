# 텔레그램 입력 메시지 발행 가이드

## 목적
- 텔레그램 봇 서버 없이도 RabbitMQ로 직접 메시지를 발행해 워커 파이프라인을 구동하기 위한 스펙을 정의합니다.
- 명시적 명령어 문자열(`/auth` 외) 대신 **메시지 계약**만 지키면 됩니다.

## 토폴로지 (prod 기준)
- **Exchange:** `telegram.exchange` (topic)
- **Routing Key:** `telegram.incoming`
- **Queue 예시:** `telegram.incoming.q` (exchange `telegram.exchange`에 `routing_key=telegram.incoming`으로 바인딩)
- 워커는 이 라우팅키로 발행만 하며, 소비자는 운영 환경에 맞춰 선언해야 합니다.

## 메시지 스키마 (JSON)
```json
{
  "updateId": 123,          // long, 텔레그램 update_id와 동일한 고유 값
  "chatId": 123456789,      // long, 채팅 ID
  "from": "username",      // string, 발신자 표시
  "text": "/todo" ,        // string, 실제 입력 텍스트
  "epochSeconds": 1700000000 // long, 메시지 발생 시각(초)
}
```
- `text`가 비어 있으면 거부됩니다.
- `updateId`는 중복 방지를 위해 단조 증가 값을 권장합니다.

## 발행 예시 (rabbitmqadmin)
```bash
rabbitmqadmin publish \
  exchange=telegram.exchange \
  routing_key=telegram.incoming \
  payload='{"updateId":123,"chatId":123456789,"from":"tester","text":"/todo","epochSeconds":1700000000}'
```

## 운영 시나리오
- 텔레그램 폴러(TelegramUpdatePoller)가 실제 봇으로부터 받은 업데이트를 동일 스키마로 `telegram.incoming`에 발행합니다.
- 외부 시스템에서 테스트/연동 시 위 스키마로 발행하면 워커가 동일하게 처리합니다.

## 참고: 텔레그램으로 보내기(응답/푸시)
- 워커/다른 서비스가 텔레그램으로 전송하려면 `telegram.exchange`에 `routing_key=telegram.outgoing`으로 발행합니다.
- 스키마: `{ "chatId": 123456789, "text": "hello", "parseMode": "Markdown" }`
- 이는 `TelegramRelayConsumer`가 소비하여 봇 API로 전송합니다.
