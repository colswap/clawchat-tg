# CLAUDE.md — ClawChat (Telegram Fork)

## 프로젝트 개요
텔레그램 Android 공식 소스(DrKLO/Telegram) 포크.
OpenClaw 봇 대화에 향상된 렌더링(마크다운, Artifact, 구문강조) + 서브에이전트 상태 패널 추가.

## 핵심 원칙
1. **텔레그램 원본 코드 최소 수정** — 훅 포인트만 삽입 (총 ~30줄 미만)
2. **모든 커스텀 코드는 `com/clawchat/` 패키지에** — `org/telegram/` 직접 수정 최소화
3. **업스트림 머지 용이성이 최우선** — diff 최소화
4. 원본 로직 삭제/변경 금지. 분기(if)로 우리 코드 호출만 추가.

## 코드 구조
```
TMessagesProj/src/main/java/
├── org/telegram/          # 텔레그램 원본 — 필수 훅 외 수정 금지
└── com/clawchat/          # ClawChat 전용 코드 — 전부 여기에
    ├── ClawChatConfig.java          # SharedPreferences 설정
    ├── ClawChatUtils.java           # 봇 감지, 유틸
    ├── Extra.java                    # 빌드 변수
    ├── gateway/                      # OpenClaw GW 연결
    │   ├── GatewayClient.java       # WebSocket + HTTP
    │   ├── GatewayAuth.java         # Bearer token
    │   └── SubagentManager.java     # 서브에이전트 상태
    ├── render/                       # 커스텀 렌더링
    │   ├── MarkdownRenderer.java    # 향상된 마크다운
    │   ├── ArtifactView.java        # WebView Artifact
    │   ├── CodeBlockView.java       # 구문 강조
    │   └── ClawMessageOverlay.java  # 봇 메시지 오버레이
    └── ui/                           # 커스텀 UI
        ├── SubagentPanelView.java   # 서브에이전트 패널
        ├── GatewayStatusView.java   # GW 상태
        └── ClawSettingsActivity.java # 설정 화면
```

## 원본 훅 위치 (수정하는 파일들)
- `ChatActivity.java` — 봇 메시지 감지 → 커스텀 렌더링 트리거 (~10줄)
- `ChatMessageCell.java` — 렌더링 확장 훅 (~5줄)
- `LaunchActivity.java` — GW 연결 초기화 (~3줄)
- 설정 화면 진입점 — ClawChat 메뉴 항목 1개 추가

## 빌드
```bash
# 로컬 빌드 (Android Studio)
./gradlew TMessagesProj:assembleAfatDebug

# CI (GitHub Actions)
# push to master → 자동 빌드 → APK 아티팩트
```

- NDK 필수 (jni/ 폴더에 네이티브 코드)
- BuildVars.java에 api_id/hash — .gitignore에 추가하지 않음 (재현 빌드 지원)
- 실제 배포 시 자체 api_id로 교체 필요

## 커밋 규칙
- Conventional Commits (영어)
- 텔레그램 원본 훅 수정: `hook(telegram): description`
- ClawChat 기능: `feat(claw): description`
- 업스트림 머지: `sync: upstream vX.Y.Z`
- Codex 정리: `fix(claw): description`

## 주의사항
- **ChatMessageCell.java**: ~20,000줄. Canvas 직접 드로잉 클래스.
  수정 시 기존 렌더링 로직 절대 건드리지 않기. View 오버레이 방식 사용.
- **ChatActivity.java**: ~20,000줄. 기존 메서드 변경 금지. 훅 분기만 추가.
- **TLRPC.java**: 절대 수정 금지 (자동 생성 프로토콜 코드)
- 새 의존성 추가 시 build.gradle에 주석으로 "// ClawChat" 표시

## OpenClaw Gateway 연결
- WebSocket: 서브에이전트 상태, 실시간 이벤트
- HTTP: /tools/invoke (세션 목록, 상태 조회)
- 인증: Bearer token (설정 화면에서 입력)
- 네트워크: Tailscale VPN or LAN (100.98.150.110:18789)

## 봇 메시지 감지
```java
// user_id 기반 — ClawChatConfig에서 봇 ID 목록 관리
ClawChatUtils.isClawBotMessage(messageObject)
```
봇 메시지만 커스텀 렌더링 적용, 나머지는 텔레그램 기본 동작.
