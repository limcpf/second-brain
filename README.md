# MySecondBrain-Worker

> 외부 메신저(텔레그램 등)로부터 RabbitMQ 메시지를 받아 LLM으로 의도를 해석하고, Obsidian 노트/Google Calendar/Tasks를 동기화하는 백엔드 워커입니다. 모든 응답은 RabbitMQ로만 반환되며 특정 메신저 SDK에 종속되지 않습니다.

## 목차
- [아키텍처 개요](#아키텍처-개요)
- [실행 프로필](#실행-프로필)
- [필수 환경변수](#필수-환경변수)
- [RabbitMQ 연동 스펙](#rabbitmq-연동-스펙)
- [텔레그램 입력 메시지](#텔레그램-입력-메시지)
- [Docker 동기화 스펙](#docker-동기화-스펙)
- [애플리케이션 설정(app.*)](#애플리케이션-설정app)
- [배포/운영 가이드](#배포운영-가이드)
- [오프라인 빌드/문제해결](#오프라인-빌드문제해결)
- [추가 문서](#추가-문서)

## 아키텍처 개요
- 헥사고날 아키텍처
  - `domain`: 순수 자바 포트/모델/유스케이스 (프레임워크 의존 금지)
  - `adapter/in`: RabbitMQ 소비자 등 진입 어댑터
  - `adapter/out`: LLM, FileSystem, Google, Docker, Reply 등 외부 연동
  - `config`: 포트-어댑터 바인딩, 프로필(dev/prod)별 빈 구성
- 언어/도구: Java 21, Quarkus 최신, Maven, LangChain4j, SmallRye Reactive Messaging

## 실행 프로필
- `dev`: in-memory 메시징, Docker mock, vault `./data/vault`, 템플릿 `src/main/resources/templates`
- `prod`: RabbitMQ 실 브로커, Docker socket `/var/run/docker.sock`, vault `/app/data/vault`, 템플릿 `/app/resources/templates`

## 필수 환경변수
```
# 공통
OPENAI_API_KEY=sk-...
VAULT_PATH=./data/vault
TEMPLATE_PATH=src/main/resources/templates
GOOGLE_CREDENTIAL_PATH=./config/tokens

# RabbitMQ (prod)
RABBITMQ_HOST=rabbit
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# Docker
DOCKER_HOST=unix:///var/run/docker.sock
SYNC_IMAGE=lscr.io/linuxserver/obsidian:latest
SYNC_WAIT_SECONDS=30
OBSIDIAN_CONFIG_PATH=/app/data/obsidian-config
OBSIDIAN_PUID=1000
OBSIDIAN_PGID=1000
OBSIDIAN_TZ=Asia/Seoul

# Telegram
TELEGRAM_BOT_TOKEN=xxxx
TELEGRAM_POLL_INTERVAL_SECONDS=5
TELEGRAM_POLL_TIMEOUT_SECONDS=30
```
- `VAULT_PATH`, `OBSIDIAN_CONFIG_PATH`: 호스트 경로가 실제 존재해야 하며 퍼미션은 `PUID/PGID`에 맞춰야 합니다.
- `SYNC_WAIT_SECONDS`: 현재는 기동 후 대기(30초) 뒤 종료. 향후 헬스체크 폴링으로 대체 예정이면 타임아웃값으로 활용 가능합니다.

## RabbitMQ 연동 스펙
- 교환기/큐
  - 요청: `bot.exchange` (topic), 큐 `brain.inbox.q`, 라우팅키 `brain.req.#`
  - DLQ: `brain.dlx` (direct), 큐 `brain.inbox.dlq`, 라우팅키 `brain.req.dlq`
  - 응답: `brain.reply.exchange` (topic)
- 채널 설정 (`application-prod.properties` 예)
```
# Incoming
mp.messaging.incoming.brain-requests.connector=smallrye-rabbitmq
mp.messaging.incoming.brain-requests.exchange.name=bot.exchange
mp.messaging.incoming.brain-requests.queue.name=brain.inbox.q
mp.messaging.incoming.brain-requests.routing-key=brain.req.#
mp.messaging.incoming.brain-requests.failure-strategy=reject
mp.messaging.incoming.brain-requests.auto-bind-dlq=true
mp.messaging.incoming.brain-requests.dead-letter-queue-name=brain.inbox.dlq
mp.messaging.incoming.brain-requests.dead-letter-exchange=brain.dlx
mp.messaging.incoming.brain-requests.dead-letter-exchange-type=direct
mp.messaging.incoming.brain-requests.dead-letter-routing-key=brain.req.dlq

# Outgoing
mp.messaging.outgoing.brain-replies.connector=smallrye-rabbitmq
mp.messaging.outgoing.brain-replies.exchange.name=brain.reply.exchange
# 필요 시 라우팅키 지정
# mp.messaging.outgoing.brain-replies.default-routing-key=brain.reply
```
- 프로듀서: `adapter/out/reply/RabbitReplyProducer`에서 `@Channel("brain-replies")` 사용, 메시지 바디는 ReplyMessage JSON.
- 라우팅키 설계: reply 측이 topic 교환기를 사용하므로 구독자는 `brain.reply.#` 등 와일드카드로 바인딩하거나, 위 `default-routing-key`를 명시해 교환기-큐 매핑을 고정하십시오. per-message 키가 필요하면 `OutgoingRabbitMQMetadata.withRoutingKey(...)`를 사용할 수 있습니다.

### 텔레그램 중계 큐 (운영 브로커 기준)
- 송신(타 서비스 → 텔레그램): exchange `telegram.exchange`, 큐 `telegram.outgoing.q`, 라우팅키 `telegram.outgoing`
  - JSON 페이로드 스키마: `{ "chatId": 123456789, "text": "hello", "parseMode": "Markdown" }`
  - 예시 발행(`rabbitmqadmin`): `rabbitmqadmin publish exchange=telegram.exchange routing_key=telegram.outgoing payload='{"chatId":123456789,"text":"hello","parseMode":"Markdown"}'`
- 수신(텔레그램 → RabbitMQ): 워커가 `getUpdates` 폴링 후 exchange `telegram.exchange`에 라우팅키 `telegram.incoming`으로 게시
  - 페이로드 예: `{ "updateId":123, "chatId":123456789, "from":"username", "text":"/todo", "epochSeconds":1700000000 }`
- 폴링 설정(선택): `app.telegram.poll-interval-seconds`(기본 5초, env `TELEGRAM_POLL_INTERVAL_SECONDS`), `app.telegram.poll-timeout-seconds`(기본 30초, env `TELEGRAM_POLL_TIMEOUT_SECONDS`), 봇 토큰 `TELEGRAM_BOT_TOKEN`

## 텔레그램 입력 메시지
- 텔레그램 봇 없이도 `telegram.exchange`에 직접 발행하여 워커 파이프라인을 구동할 수 있습니다.
- 토폴로지: exchange `telegram.exchange`, 라우팅키 `telegram.incoming`, 바인딩 큐 예시 `telegram.incoming.q`
- 페이로드 계약: `{ "updateId":long, "chatId":long, "from":string, "text":string, "epochSeconds":long }` (빈 `text` 거부, `updateId` 단조 증가 권장)
- 발행 예시와 상세 가이드는 `docs/telegram-input.md`를 참고하세요.

## Docker 동기화 스펙
- 이미지: `lscr.io/linuxserver/obsidian:latest` (KasmVNC 기반)
- 볼륨: `/vault` ← `${VAULT_PATH}`, `/config` ← `${OBSIDIAN_CONFIG_PATH}`
- 포트: 컨테이너 3000/3001 → 호스트 3000/3001 (현재 고정)
- 환경변수: `PUID`, `PGID`, `TZ`
- 대기: 컨테이너 기동 후 `SYNC_WAIT_SECONDS` 동안 유지 뒤 stop+remove (가상 스레드 비동기)
- 보안/리소스: shm 2GB, seccomp unconfined 적용

## 애플리케이션 설정(app.*)
`application.properties` 또는 `application-prod.properties`에 주입:
```
app.openai.api-key=${OPENAI_API_KEY}
app.openai.model=${openai.model}
app.openai.temperature=${openai.temperature}

app.paths.vault-path=${VAULT_PATH}
app.paths.template-path=${TEMPLATE_PATH}
app.google.credential-path=${GOOGLE_CREDENTIAL_PATH}

app.docker.host=${DOCKER_HOST}
app.docker.image=${SYNC_IMAGE}
app.docker.sync-wait-seconds=${SYNC_WAIT_SECONDS}
app.docker.config-path=${OBSIDIAN_CONFIG_PATH}
app.docker.puid=${OBSIDIAN_PUID}
app.docker.pgid=${OBSIDIAN_PGID}
app.docker.timezone=${OBSIDIAN_TZ}

app.telegram.bot-token=${TELEGRAM_BOT_TOKEN}
app.telegram.poll-interval-seconds=${TELEGRAM_POLL_INTERVAL_SECONDS:5}
app.telegram.poll-timeout-seconds=${TELEGRAM_POLL_TIMEOUT_SECONDS:30}

app.idempotency.backend=sqlite
app.idempotency.path=/app/data/idempotency.log
app.idempotency.sqlite-path=/app/data/idempotency.db
app.idempotency.ttl-hours=24

quarkus.datasource.db-kind=sqlite
quarkus.datasource.jdbc.url=jdbc:sqlite:${app.idempotency.sqlite-path}?journal_mode=WAL&synchronous=NORMAL
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=2
```

## 배포/운영 가이드
- 프로필/환경: `QUARKUS_PROFILE=prod` 설정 후 `RABBITMQ_HOST/USER/PASS`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_POLL_INTERVAL_SECONDS`, `TELEGRAM_POLL_TIMEOUT_SECONDS`, `VAULT_PATH`, `OBSIDIAN_CONFIG_PATH`, `GOOGLE_CREDENTIAL_PATH` 등을 주입합니다.
- RabbitMQ 사전 준비(운영 브로커):
  - exchange/queue 선언: `telegram.exchange`(topic), `telegram.outgoing.q`(bind `telegram.outgoing`), `telegram.incoming` 라우팅키용 구독자 큐. 요청/응답/ DLQ 설정도 운영 정책에 맞게 선행 선언.
  - 예시(`rabbitmqadmin`):
    - `rabbitmqadmin declare exchange name=telegram.exchange type=topic durable=true`
    - `rabbitmqadmin declare queue name=telegram.outgoing.q durable=true`
    - `rabbitmqadmin declare binding source=telegram.exchange destination_type=queue destination=telegram.outgoing.q routing_key=telegram.outgoing`
- 마운트/권한: `/var/run/docker.sock`, `${VAULT_PATH}`, `${OBSIDIAN_CONFIG_PATH}`, 템플릿 경로를 읽기/쓰기 가능하게 마운트합니다.
- 실행: Docker 런타임 기준 `docker run --env-file <env> -e QUARKUS_PROFILE=prod -v /var/run/docker.sock:/var/run/docker.sock -v ${VAULT_PATH}:${VAULT_PATH} -v ${OBSIDIAN_CONFIG_PATH}:${OBSIDIAN_CONFIG_PATH} <image>` 형태로 기동합니다.
- 검증: 기동 후 `telegram.exchange`에 테스트 메시지 발행 → 텔레그램 수신 확인, `health`/로그에서 vault·template 경로 확인, MDC 상관관계 ID로 요청-응답을 추적합니다.
- 헬스체크: `WorkerReadinessCheck`가 vault/template 경로 존재 여부를 보고 (prod에서 미존재 시 실패)

## 오프라인 빌드/문제해결
- 네트워크 차단 시 의존성 확보 후 `./mvnw -o` 가능, 또는 `javac` 단독 컴파일 대안
- Maven 로컬 미러 예시: `~/.m2/settings.xml`에 file:// 미러 지정 후 `./mvnw clean compile -o`
- 네트워크 가능 시 표준 빌드: `./mvnw -Pdev test`, `./mvnw verify`

## 추가 문서
- `RFP.md`: RabbitMQ 토폴로지, 전체 메시징 플로우, 의도 처리 설계 요약
- `src/main/resources/application-prod.properties`: 프로덕션 메시징/Docker 기본값과 라우팅/DLQ 설정 실예
- `src/main/java/com/my/brain/adapter/out/reply/RabbitReplyProducer.java`: Reply 프로듀서 구현, 라우팅키 커스터마이즈 시 참고
- `src/main/java/com/my/brain/adapter/out/docker/ProdDockerAdapter.java`: Docker 컨테이너 기동/정리 플로우, 볼륨/포트/환경변수 적용부
- `src/main/java/com/my/brain/config/ConfigValidator.java`: 프로필별 필수 경로/자격 검증 로직
- `docs/telegram-input.md`: 텔레그램 입력 메시지 발행 가이드
- `docs/contracts.md`: 텔레그램 봇 ↔ 워커 메시징 계약 상세

## Google OAuth 최초 인증 절차 (메신저 무관, RabbitMQ 응답)
1. `/app/config/tokens`에 `client_secret.json`을 마운트합니다.
2. `credential.json`이 없을 때 캘린더/할 일 생성 요청 또는 `/auth` 명령을 보내면 연결된 메신저로 인증 링크가 RabbitMQ를 통해 전달됩니다.
3. 브라우저 승인 후 표시되는 코드를 `/auth <코드>`로 전송하면 토큰이 `/app/config/tokens`에 저장됩니다.
4. 이후 캘린더/할 일 기능이 정상 동작하며 토큰은 동일 경로에 영구 저장됩니다.

# 공통
OPENAI_API_KEY=sk-...
VAULT_PATH=./data/vault
TEMPLATE_PATH=src/main/resources/templates
GOOGLE_CREDENTIAL_PATH=./config/tokens

# RabbitMQ (prod)
RABBITMQ_HOST=rabbit
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# Docker
DOCKER_HOST=unix:///var/run/docker.sock
SYNC_IMAGE=lscr.io/linuxserver/obsidian:latest
SYNC_WAIT_SECONDS=30
OBSIDIAN_CONFIG_PATH=/app/data/obsidian-config
OBSIDIAN_PUID=1000
OBSIDIAN_PGID=1000
OBSIDIAN_TZ=Asia/Seoul

# Telegram
TELEGRAM_BOT_TOKEN=xxxx
TELEGRAM_POLL_INTERVAL_SECONDS=5
TELEGRAM_POLL_TIMEOUT_SECONDS=30
```

## 추가 설정(app.*)
`application.properties` 또는 `application-prod.properties`에 다음과 같이 설정합니다.
```
app.openai.api-key=${OPENAI_API_KEY}
app.openai.model=${openai.model}
app.openai.temperature=${openai.temperature}

app.paths.vault-path=${VAULT_PATH}
app.paths.template-path=${TEMPLATE_PATH}
app.google.credential-path=${GOOGLE_CREDENTIAL_PATH}

app.docker.host=${DOCKER_HOST}
app.docker.image=${SYNC_IMAGE}
app.docker.sync-wait-seconds=${SYNC_WAIT_SECONDS}
app.docker.config-path=${OBSIDIAN_CONFIG_PATH}
app.docker.puid=${OBSIDIAN_PUID}
app.docker.pgid=${OBSIDIAN_PGID}
app.docker.timezone=${OBSIDIAN_TZ}

app.telegram.bot-token=${TELEGRAM_BOT_TOKEN}
app.telegram.poll-interval-seconds=${TELEGRAM_POLL_INTERVAL_SECONDS:5}
app.telegram.poll-timeout-seconds=${TELEGRAM_POLL_TIMEOUT_SECONDS:30}

app.idempotency.backend=sqlite
app.idempotency.path=/app/data/idempotency.log
app.idempotency.sqlite-path=/app/data/idempotency.db
app.idempotency.ttl-hours=24

# SQLite datasource (예시)
quarkus.datasource.db-kind=sqlite
quarkus.datasource.jdbc.url=jdbc:sqlite:${app.idempotency.sqlite-path}?journal_mode=WAL&synchronous=NORMAL
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=2
```

## 신뢰성/관측성 강화
- 입력 스키마 검증 및 잘못된 요청 차단
- 중복 처리 방지를 위한 idempotency 저장 (memory/file/sqlite 선택, 기본 sqlite)
- LLM/Google API 재시도(백오프) 적용
- RabbitMQ DLQ 설정 + DLQ 소비자로 실패 메시지 격리/가시화
- MDC 기반 상관관계 ID 로깅
- Readiness 헬스체크 제공

## Docker 빌드/배포 자동화 (GitHub Actions)
- 브랜치: `main` push 시 자동 트리거
- 이미지: `daeseong0226/second-brain:latest` (linux/amd64)
- 워크플로우: `.github/workflows/docker-publish.yml`
- Secrets 필요: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`
- Dockerfile: 루트 `Dockerfile` (멀티스테이지, Maven 빌드 → slim JRE 런타임)

## 주요 경로
- `src/main/java/com/my/brain/domain/...`
- `src/main/java/com/my/brain/adapter/in/rabbitmq/RabbitMessageConsumer`
- `src/main/java/com/my/brain/adapter/out/...` (llm/filesystem/google/docker/reply/clock)
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `src/main/resources/templates/daily.md`

## 빌드/테스트
네트워크 차단 시 의존성 다운로드가 필요합니다. 접속 가능 시:
```
./mvnw -Pdev test
./mvnw verify
```

## 배포 힌트 (NixOS)
- `QUARKUS_PROFILE=prod`
- Docker socket 마운트 `/var/run/docker.sock`
- Vault, templates, token 디렉터리 볼륨 마운트

## Google OAuth 최초 인증 절차 (메신저 무관, RabbitMQ 응답)
1. `/app/config/tokens`에 `client_secret.json`을 마운트합니다.
2. `credential.json`이 없을 때 캘린더/할 일 생성 요청 또는 `/auth` 명령을 보내면 연결된 메신저(예: 텔레그램)로 인증 링크가 RabbitMQ를 통해 전달됩니다.
3. 브라우저에서 로그인/승인을 완료한 뒤, 화면에 표시되는 **코드**를 동일 메신저로 `/auth <코드>` 형태로 보내면 토큰이 `/app/config/tokens`에 저장됩니다.
4. 이후 캘린더/할 일 기능이 정상 동작하며, 토큰은 동일 경로에 영구 저장됩니다.

## 주석 규칙
- 코드 주석/Javadoc은 한국어로 “왜”를 설명합니다.

## 문제 해결 및 오프라인 빌드 방법

### 현재 상황
- Maven Central 접근이 차단되어 `quarkus-bom` 및 기타 의존성 다운로드 불가
- 오프라인 빌드가 필요한 경우, 네트워크 접속 상태에 따라 다음 방법 중 하나를 선택하여 수행할 수 있습니다.

### 방법 1: javac로 직접 컴파일 (오프라인 대안)
- **목적**: Maven 의존성 해상 없이 순수 자바 코드 컴파일
- **사용법**:
  \`\`\`bash
  cd src/main/java
  javac -d target/classes <패키지>.java
  # 예: javac -d target/classes com.my.brain.domain.model.*
  \`\`\`
- **장점**: Maven이 제공하는 라이프러리 관리 기능 없음, 전체 빌드 관리는 수동으로 필요
- **검증**: 모든 도메인 모델/포트/서비스 파일이 javac로 정상 컴파일됨을 확인 완료

### 방법 2: Maven 로컬 미러 사용 (오프라인 대안)
- **목적**: Maven Central 접근 제약을 우회하여 빌드 수행
- **사용법**:
  \`\`\`bash
  # 1. 로컬 미러 설정 파일 생성 (~/.m2/settings.xml)
  cat > ~/.m2/settings.xml << 'EOF'
  <settings>
    <mirrors>
      <mirror>
        <id>local-mirror</id>
        <url>file://$HOME/.m2/repository</url>
        <mirrorOf>central</mirrorOf>
      </mirror>
    </mirrors>
  </settings>
  EOF
  
  # 2. 필요한 의존성 수동으로 다운로드 및 설치
  # (이미 작성된 코드에는 Quarkus 확장 기능 외 사용)
  
  # 3. 빌드 실행
  ./mvnw clean compile -o
  \`\`\`
- **장점**: 로컬 스토리지 사용 시 네트워크 불필요, 의존성 관리 명확
- **단점**: 외부 라이브러리의 업데이트 관리가 필요함

### 방법 3: 네트워크 접속 가능 환경에서 Maven 실행
- 네트워크 접속이 가능한 환경에서는 정상적으로 `mvnw`를 사용하여 빌드하면 됩니다.
- 사무실, 카페 등에서는 일반적으로 인터넷 접속이 가능하므로 해당 환경에서 `./mvnw verify` 실행을 권장합니다.

### 오프라인 빌드 시나리오
- 현재 macOS 환경에서는 javac로 컴파일이 가능함을 확인했습니다.
- 향후 네트워크 접속이 가능해지면, 다음과 같이 실행하세요:
  \`\`\`bash
  ./mvnw clean compile
  ./mvnw test
  ./mvnw verify
  \`\`\`
