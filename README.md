# MySecondBrain-Worker

## 개요
텔레그램 등 외부에서 전달된 자연어 명령을 RabbitMQ를 통해 수신하여 LLM으로 의도를 파악하고, Obsidian 파일 생성/갱신, Google Calendar/Tasks 연동, Docker 기반 동기화를 수행하는 백엔드 워커입니다.

## 아키텍처
- 헥사고날 아키텍처
  - `domain`: 순수 자바, 포트/모델/유스케이스 (프레임워크 의존 금지)
  - `adapter/in`: RabbitMQ 소비자 등 진입 어댑터
  - `adapter/out`: LLM, FileSystem, Google, Docker, Reply 등 외부 연동
  - `config`: 포트-어댑터 바인딩, 프로필(dev/prod)별 빈 구성

## 요구사항 요약
- Java 21, Quarkus 최신, Maven
- LLM: gpt-5-mini (LangChain4j)
- 메시징: SmallRye Reactive Messaging (RabbitMQ)
- Google Calendar/Tasks 연동 (StoredCredential 기반)
- Docker 제어: prod 실소켓, dev는 mock 로그
- 주석/README/문서: 한국어, “왜” 설명

## 실행 프로필
- `dev`: in-memory 메시징, Docker mock, 로컬 vault 경로(`./data/vault`), 템플릿 `src/main/resources/templates`
- `prod`: RabbitMQ 실제 브로커, Docker socket(`/var/run/docker.sock`), vault `/app/data/vault`, 템플릿 `/app/resources/templates`

## 환경 변수 예시
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
SYNC_IMAGE=obsidian-livesync-client:latest
SYNC_WAIT_SECONDS=180
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
