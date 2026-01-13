
# 📘 MySecondBrain-Worker : 통합 설계서

## 1. 프로젝트 정의 (Project Definition)

- **프로젝트명:** `MySecondBrain-Worker`
    
- **개요:** 외부(텔레그램 봇 등)에서 수신된 자연어 명령을 RabbitMQ를 통해 전달받아, **일정 관리(Google)**와 **지식 관리(Obsidian)**를 자동화하는 이벤트 기반의 백엔드 워커.
    
- **핵심 목표:**
    
    1. **RabbitMQ 기반의 비동기 처리**로 확장성 확보.
        
    2. **LLM(OpenAI)을 활용한 의도 분석** 및 데이터 구조화.
        
    3. **NixOS + Docker 환경**에서의 로컬 파일 제어 및 **Self-hosted Sync(CouchDB)** 자동화.
        
    4. **Hexagonal Architecture** 적용으로 외부 의존성(RabbitMQ, Google, Docker)과 핵심 로직의 분리.
        

---

## 2. 시스템 아키텍처 (System Architecture)

이 시스템은 외부 API 서버(텔레그램 봇)에 종속되지 않고, **메시지 브로커(RabbitMQ)**를 통해 트리거되는 **순수 처리기(Processor)**입니다.

코드 스니펫

```
graph LR
    subgraph External System
        TelegramBot[기존 텔레그램 봇 Server]
    end

    subgraph Infrastructure
        RabbitMQ[(RabbitMQ)]
        CouchDB[(CouchDB - LiveSync)]
    end

    subgraph MySecondBrain-Worker [Quarkus Application]
        direction TB
        MQ_Adapter[In: RabbitMQ Consumer]
        DomainLogic[Domain: Brain Service]
        
        MQ_Adapter --> DomainLogic
        
        DomainLogic -->|REST| Port_OpenAI[Out: OpenAI Adapter]
        DomainLogic -->|REST| Port_Google[Out: Google Adapter]
        DomainLogic -->|File I/O| Port_File[Out: File System Adapter]
        DomainLogic -->|Docker Socket| Port_Docker[Out: Docker Control Adapter]
    end
    
    TelegramBot -->|Publish: brain.req.#| RabbitMQ
    RabbitMQ -->|Consume| MQ_Adapter
    
    Port_OpenAI -->|Intent Parsing| OpenAI[GPT-5 Mini]
    Port_Google -->|Manage| GoogleAPI[Calendar/Tasks]
    Port_File -->|Write MD| Vault[Obsidian Vault (Volume)]
    
    Port_Docker -->|Run & Wait 3min| DockerDaemon[Docker Host Daemon]
    DockerDaemon -.->|Spawn| SyncWorker[Obsidian-Client Container]
    SyncWorker -.->|Sync Data| CouchDB
```

---

## 3. 운영 구조도 (Operational Flow on NixOS)

**운영 환경(NixOS)**에서의 구동 방식은 **개발 환경(MacOS)**과 다르게 동작합니다.

1. **메시지 수신:** RabbitMQ로부터 `{"content": "메모 저장..."}` 수신.
    
2. **파일 처리:** Worker 컨테이너가 마운트된 `/obsidian-vault` 볼륨에 `.md` 파일 작성.
    
3. **동기화 트리거:**
    
    - Worker가 호스트의 `/var/run/docker.sock`을 통해 `obsidian-livesync` 클라이언트 컨테이너 실행 명령 하달.
        
    - **"3분 대기 전략":** 컨테이너를 실행(`docker run`)하고 3분(180초) 동안 대기 후 종료(`docker stop/rm`)하여 CouchDB와 동기화 수행.
        

---

## 4. RabbitMQ 메시지 인터페이스 정의 (Contract)

기존 텔레그램 봇 서버(Producer)와 Brain Worker(Consumer) 간의 약속입니다.

### 4.1 토폴로지 (Topology)

- **Exchange:** `bot.exchange` (Topic)
    
- **Queue:** `brain.inbox.q`
    
- **Routing Key:** `brain.req.#`
    
    - `brain.req.chat`: 일반 대화/명령
        
    - `brain.req.sync`: 강제 동기화 요청
        

### 4.2 요청 Payload (Producer -> Consumer)

JSON

```
{
  "eventId": "uuid-v4",
  "timestamp": "2026-01-13T10:00:00",
  "userId": "user-123",
  "type": "CHAT", 
  "content": "내일 오후 2시 강남역 미팅 잡아줘"
}
```

### 4.3 응답 Payload (Consumer -> Producer)

처리가 완료된 후 사용자에게 알림이 필요한 경우 사용합니다.

- **Routing Key:** `telegram.res.reply`
    

JSON

```
{
  "replyToUserId": "user-123",
  "content": "✅ 일정이 등록되었습니다. (관련 노트: [[2026-01-14-미팅]])"
}
```

---

## 5. 상세 기능 요구사항 (Functional Requirements)

### A. 인공지능 처리 (LLM Processing)

- **모델:** `gpt-5-mini` (OpenAI)
    
- **역활:** "단순 텍스트"를 "구조화된 명령(JSON)"으로 변환.
    
- **프롬프트 전략:**
    
    - System Prompt에 현재 시간(`LocalDateTime`)을 주입하여 "내일", "다음 주" 등의 상대적 시간 계산.
        
    - **Intent 분류:** `CALENDAR`, `TASK`, `NOTE`, `SYNC`, `UNKNOWN`.
        

### B. Obsidian 관리 (File System)

- **데일리 노트 (Daily Note):**
    
    - **자동 생성:** 메시지 처리 시, 해당 날짜의 파일(`YYYY-MM-DD.md`)이 없으면 템플릿 기반 생성.
        
    - **템플릿 구조:** YAML Frontmatter + `## Tasks`, `## Logs` 섹션 포함. 외부 파일(`templates/daily.md`)로 관리하여 수정 용이성 확보.
        
- **퀵 로그 (Quick Log):**
    
    - 데일리 노트의 `## Logs` 섹션 하단에 `HH:mm - 내용` 형식으로 Append.
        
- **일정 연동 노트:**
    
    - 일정 등록 시 `Meeting-Notes/YYYY-MM-DD-{Summary}.md` 파일 생성.
        
    - 데일리 노트에 해당 파일로의 링크(`[[...]]`) 자동 삽입.
        

### C. 동기화 자동화 (Docker Control)

- **트리거 조건:**
    
    - 명시적 요청 (`/sync` 메시지 수신 시).
        
    - (옵션) 파일 변경이 많이 일어난 후(배치성).
        
- **Docker 제어 로직:**
    
    - 이미지: `obsidian-livesync-client:latest` (Headless 설정됨).
        
    - 동작: 컨테이너 실행 -> **180초 Sleep** -> 컨테이너 강제 종료 및 삭제.
        
- **환경 분리:**
    
    - **Prod (NixOS):** 실제 Docker Socket 통신.
        
    - **Dev (MacOS):** "동기화 시뮬레이션 중..." 로그 출력으로 대체 (Mocking).
        
### D. Google Workspace 연동 (Google API)

이 모듈은 `GooglePort` (Outbound)를 통해 구현되며, 사용자의 개인 구글 계정과 상호작용합니다.

1. **인증 및 세션 관리 (Authentication)**
    
    - **OAuth 2.0 Offline Access:** 서버 사이드 데몬이므로 브라우저를 통한 로그인이 불가능합니다.
        
    - **운영 방식:**
        
        - 최초 1회: 로컬 개발 환경(Mac)에서 인증을 수행하여 `Refresh Token`이 포함된 `StoredCredential` 파일을 생성.
            
        - 운영 배포: 해당 파일을 NixOS 서버의 보안 경로(`/app/config/tokens`)에 마운트하여, 앱 기동 시 자동으로 `GoogleCredential`을 갱신(Refresh)하며 사용.
            
    - **Scope:** `Calendar/Events` (Read/Write), `Tasks` (Read/Write).
        
2. **캘린더 일정 등록 (Calendar Integration)**
    
    - **입력 데이터:** LLM이 구조화한 `CalendarEvent` 객체 (제목, 시작시간, 종료시간, 장소, 참석자).
        
    - **비즈니스 로직:**
        
        1. **Timezone 처리:** 모든 시간은 `Asia/Seoul`을 기준으로 변환하여 API에 전송.
            
        2. **양방향 링크 생성 (핵심):** 일정을 등록하기 전(혹은 병렬로), Obsidian에 관련 노트(`Meeting-Notes/YYYY-MM-DD-제목.md`)를 먼저 생성합니다.
            
        3. **Description 주입:** 캘린더 API 호출 시, '설명(Description)' 필드에 **생성된 노트의 링크**를 삽입합니다.
            
            - _Format:_ `🔗 관련 노트: [[Meeting-Notes/2026-01-13-Kickoff]]` (Obsidian URI 스키마 사용 고려 가능)
                
    - **예외 처리:** 시간 포맷 파싱 실패 시, 기본 1시간 단위 일정으로 등록 후 "시간 확인 필요" 태그 추가.
        
3. **할일 등록 (Tasks Integration)**
    
    - **입력 데이터:** LLM이 구조화한 `TodoItem` 객체 (내용, 마감일).
        
    - **비즈니스 로직:**
        
        1. **Target List:** 별도 설정이 없으면 '기본 목록(My Tasks)' 혹은 사전에 정의된 'Inbox' 리스트 ID로 등록.
            
        2. **Due Date:** LLM이 날짜를 인식했다면 해당 날짜(RFC3339)로 설정, "오늘", "내일" 등의 상대적 시간도 `ProcessMessageUseCase` 단계에서 절대 시간으로 변환되어 들어옴.
            
        3. **Notes:** 원본 메시지(Raw Message)를 Tasks의 '세부 정보(Notes)' 란에 백업용으로 기재.
    

---

## 6. 비기능 요구사항 (Non-Functional Requirements)

1. **문서화 언어:**
    
    - 소스 코드 주석(Javadoc), 커밋 메시지 본문, README 등 모든 문서는 **한국어**로 작성한다.
        
2. **환경 격리 (Isolation):**
    
    - `application.properties` (공통/Dev)와 `application-prod.properties` (Prod)를 분리.
        
    - NixOS 배포 시 `QUARKUS_PROFILE=prod` 환경변수 주입 필수.
        
3. **내결함성:**
    
    - RabbitMQ 연결 끊김 시 자동 재접속(Reconnection) 지원.
        
    - Docker 제어 실패 시에도 메인 어플리케이션은 죽지 않고 에러 로그만 남겨야 함.
        

---

## 7. 프로젝트 구조 제안 (Clean Architecture)

외부 의존성(RabbitMQ, Docker 등)이 도메인 로직을 침범하지 않는 **헥사고날 아키텍처**입니다.

Plaintext

```
my-second-brain/
├── src/
│   ├── main/
│   │   ├── java/com/my/brain/
│   │   │   ├── domain/                  # [핵심] 비즈니스 로직 (POJO)
│   │   │   │   ├── model/               # 데이터 모델 (Record)
│   │   │   │   ├── port/in/             # UseCase 인터페이스 (ex: HandleCommand)
│   │   │   │   ├── port/out/            # 외부 포트 (ex: DockerPort, FilePort)
│   │   │   │   └── service/             # UseCase 구현체
│   │   │   ├── adapter/                 # [어댑터] 기술 구현체
│   │   │   │   ├── in/rabbitmq/         # RabbitMQ Consumer
│   │   │   │   └── out/
│   │   │   │       ├── llm/             # LangChain4j 구현체
│   │   │   │       ├── docker/          # Docker Java Client (Dev/Prod 분리)
│   │   │   │       ├── filesystem/      # Java NIO 파일 처리
│   │   │   │       └── google/          # Google API Client
│   │   │   └── config/                  # Quarkus Configuration
│   │   └── resources/
│   │       ├── templates/               # Qute 템플릿 파일 (daily-note.txt)
│   │       ├── application.properties
│   │       └── application-prod.properties
│   └── test/                            # 단위/통합 테스트
├── deploy/
│   ├── nixos/module.nix                 # NixOS OCI Container 설정
│   └── docker/                          # Docker Compose (Dev용)
├── README.md                            # 상세 가이드 (한국어)
└── pom.xml
```

---

## 8. README.md 미리보기 (구조 관련 섹션)

Markdown

```
## 🏗 아키텍처 및 폴더 구조

이 프로젝트는 **헥사고날 아키텍처(Hexagonal Architecture)** 를 채택하여, 기술적인 요소(Docker, RabbitMQ)가 변경되더라도 비즈니스 로직(일정 관리, 노트 작성)은 보호되도록 설계되었습니다.

### 📁 주요 패키지 설명
* **`domain`**: 비즈니스 로직의 심장부입니다. 프레임워크에 의존하지 않습니다.
    * `port/in`: 외부에서 어떤 일을 시킬 수 있는지 정의합니다. (예: `ProcessMessageUseCase`)
    * `port/out`: 도메인이 외부 시스템을 어떻게 사용하는지 정의합니다. (예: `SyncContainerPort`)
* **`adapter`**: 도메인 포트(`port`)를 실제로 구현하는 기술 계층입니다.
    * `in/rabbitmq`: 래빗MQ 큐(`brain.inbox.q`)를 구독하여 도메인을 호출합니다.
    * **`out/docker`**: 
        * `ProdDockerAdapter` (`@Profile("prod")`): NixOS의 Docker Socket을 제어합니다.
        * `DevDockerAdapter` (`@Profile("dev")`): 로컬 개발 시 Docker를 실행하지 않고 로그만 출력합니다.

## 🚀 개발 및 운영 환경

* **개발 (MacOS):** `./mvnw quarkus:dev` 실행 시 `dev` 프로필이 활성화되며, Docker 제어는 Mocking 됩니다.
* **운영 (NixOS):** `deploy/nixos/module.nix`를 참고하여 컨테이너로 배포하며, 실제 Docker Socket 마운트가 필요합니다.
```

---

## 9. NixOS 설정 예시 (oci-containers)

NixOS 서버의 `/etc/nixos/configuration.nix`에 추가할 내용입니다.

Nix

```
{ config, pkgs, ... }:

{
  # Docker 활성화 (필수)
  virtualisation.docker.enable = true;

  # OCI Containers 설정
  virtualisation.oci-containers.containers = {
    
    # 1. Brain Worker (Quarkus App)
    my-brain-worker = {
      image = "ghcr.io/my-user/my-second-brain-worker:latest";
      environment = {
        QUARKUS_PROFILE = "prod";
        OPENAI_API_KEY = "sk-proj-...."; # 실제 키 혹은 sops-nix 활용 권장
        RABBITMQ_HOST = "rabbitmq-host"; # 내부 IP 또는 Docker Network Alias
      };
      volumes = [
        # Obsidian Vault 마운트 (쓰기 권한 필수)
        "/home/server/obsidian-vault:/app/data/vault"
        # 템플릿 폴더 마운트 (운영 중 수정 가능하도록)
        "/home/server/brain-config/templates:/app/resources/templates"
        # ★ Docker Socket 마운트 (Sync Worker 제어용)
        "/var/run/docker.sock:/var/run/docker.sock"
      ];
      extraOptions = [ "--network=my-network" ];
    };

    # 2. Sync Worker (평소엔 꺼져있음, Brain이 필요할 때 실행시키는 이미지용 선언이 아님)
    # 주의: Sync Worker는 Brain Worker가 'docker run' 명령어로 동적으로 띄웁니다.
    # 따라서 NixOS 설정보다는 Brain Worker가 사용할 이미지를 `docker pull` 해두는 것이 중요합니다.
  };
  
  # 시스템 부팅 시 Sync Worker 이미지를 미리 Pull 해두는 스크립트 예시
  system.activationScripts.pullSyncImage = ''
    ${pkgs.docker}/bin/docker pull my-repo/obsidian-livesync-client:latest
  '';
}
```
