# MySecondBrain-Worker Project Rules

## 1. Project Context
- **Name:** MySecondBrain-Worker
- **Goal:** A backend worker processing Telegram commands via RabbitMQ to manage Google Calendar/Tasks and Obsidian Notes.
- **Key Feature:** Event-driven, Self-hosted Sync (Docker), Local File System manipulation.

## 2. Tech Stack & Version
- **Java:** 21 (LTS) - Use `var`, `Record`, `Virtual Threads`, `Pattern Matching`.
- **Framework:** Quarkus (Latest)
- **Build Tool:** Maven
- **Libraries:**
  - LangChain4j (LLM)
  - SmallRye Reactive Messaging (RabbitMQ)
  - Google API Client
  - Docker Java Client
  - Lombok (Only for DTOs/Adapters, NOT in Domain Logic if possible)

## 3. Architecture Rules (Hexagonal Architecture) - STRICT!
- **Domain Layer (`com.my.brain.domain`)**:
  - PURE JAVA only. NO Framework annotations (No `@ApplicationScoped`, `@Inject`, `@Json...`).
  - Business logic resides here.
  - Define Interfaces in `port/in` (UseCases) and `port/out` (SPIs).
- **Adapter Layer (`com.my.brain.adapter`)**:
  - Implement ports here.
  - Framework dependencies (Quarkus, RabbitMQ, Google) are ALLOWED here.
  - `in`: Driving Adapters (e.g., RabbitMQ Consumer).
  - `out`: Driven Adapters (e.g., Google API, FileSystem, OpenAI).
- **Config Layer (`com.my.brain.config`)**:
  - All Bean creations and configurations go here.

## 4. Environment Isolation
- **Profile `dev` (MacOS):**
  - Use Mock/Stub for Docker control (Do NOT interact with real Docker daemon).
  - Use local file paths for Obsidian vault.
- **Profile `prod` (NixOS):**
  - Use Real Docker Socket for Sync Worker.
  - Use RabbitMQ for message consumption.

## 5. Coding Style & Conventions
- **Language:**
  - **Code:** English naming, standard Java conventions.
  - **Comments/Javadoc:** **MUST BE KOREAN (한국어)**. Explain 'Why' not just 'What'.
  - **Commit Messages:** English (Conventional Commits).
- **Error Handling:** Use Custom Exceptions in Domain, catch and log/retry in Adapters.
- **Records:** Use `record` for all DTOs and Immutable Domain Objects.

## 6. Implementation Priorities
1. Define Domain Models (`Note`, `Task`, `CalendarEvent`) & Ports first.
2. Implement Core Logic (`UseCase`).
3. Implement Adapters (RabbitMQ -> Logic -> Google/File).
4. Write Tests (JUnit 5, Mockito).

## 7. Documentation
- Update `README.md` in **Korean** whenever a new feature is added.
- Include usage examples for `application.properties`.
