# 세션 핸드오프 노트 (로컬에서 이어서 작업하기)

> 이 파일은 **로컬에서 세션을 열었을 때 빠르게 맥락을 잡기 위한 요약**입니다.
> 작업 브랜치: **`android-printer`**

---

## 1. 무엇을 만드는가 (목표)

안드로이드 POS 단말기 **이지체크 TS-194N**(제조사: 한국정보통신/KICC, 안드로이드 기반)에서,
배달앱 **사장님 앱(배달의민족 · 쿠팡이츠)** 으로 들어오는 **주문 푸시 알림을 가로채
내장/블루투스 프린터로 자동 출력**하는 앱.

- 배경: 기존 배달앱이 이 단말기에서 출력을 제대로 지원하지 않음. (윈도우 설치까지 고민했으나 불필요)
- 접근법 = **방법 A (알림 가로채기)**: `NotificationListenerService` 로 주문 알림을 받아 파싱 → 출력.
  배달사 공식 API 제휴가 필요 없어 가장 간단하고 현실적.

### 확정된 요구사항
- 대상 배달앱: **배달의민족, 쿠팡이츠**
- 단말기: **이지체크 TS-194N** (내장 프린터 + 블루투스 있음)
- 사장님 앱이 깔려서 **주문 알림(푸시)은 뜨지만 프린터로 출력이 안 됨** → 방법 A 가능
- 핵심 기능: **영수증/주문서 출력**
- 데이터 저장: 미정 → 1차에는 설정만 DataStore, 출력 이력 DB는 보류

---

## 2. 아키텍처 / 모듈

```
core (순수 Kotlin, 안드로이드 비의존, JVM 테스트 가능)
 ├ model/Order.kt              주문/메뉴/플랫폼 모델
 ├ parser/NotificationData.kt  알림의 플랫폼 비의존 표현
 ├ parser/OrderParser.kt       파서 인터페이스 + ParseUtils(정규식 유틸)
 ├ parser/BaeminParser.kt      배민 파서   ← 실제 샘플로 보정 필요
 ├ parser/CoupangEatsParser.kt 쿠팡이츠 파서 ← 실제 샘플로 보정 필요
 ├ parser/OrderParsers.kt      파서 레지스트리(진입점)
 ├ printer/EscPos.kt           ESC/POS 명령 빌더(EUC-KR)
 └ printer/ReceiptFormatter.kt Order → ESC/POS 바이트열

app (안드로이드)
 ├ DeliveryPrintApp.kt                  Application(채널 생성, DI init)
 ├ di/ServiceLocator.kt                 경량 수동 DI(lazy 싱글톤)
 ├ data/SettingsStore.kt                DataStore 설정(프린터/용지/자동출력)
 ├ data/NotificationRepository.kt       알림 허브: 로그 기록 + 주문이면 출력 큐로
 ├ notification/OrderNotificationListener.kt  ★ 알림 가로채기 핵심
 ├ notification/NotificationMapper.kt   StatusBarNotification → NotificationData
 ├ printer/Printer.kt                   출력 추상화 인터페이스
 ├ printer/BluetoothEscPosPrinter.kt    표준 ESC/POS SPP 출력 (1순위, 권장)
 ├ printer/KiccInnerPrinter.kt          내장 프린터 stub (KICC SDK 확보 시 구현)
 ├ printer/PrinterFactory.kt            설정에 맞는 Printer 생성
 ├ printer/PrintController.kt           출력 큐(Channel) + 재시도
 ├ service/PrintForegroundService.kt    포그라운드 서비스(큐 소비)
 └ ui/MainActivity.kt                   Compose UI(홈/프린터설정/알림로그)
```

흐름: `알림 → OrderNotificationListener → NotificationRepository → OrderParsers(파싱)
→ (자동출력 ON이면) PrintController.submit → PrintForegroundService → ReceiptFormatter → Printer`

---

## 3. 현재 상태

### ✅ 구현 완료
- 위 모든 파일 작성 완료, 브랜치 `android-printer` 에 커밋·푸시됨
- `core` 단위 테스트 작성: `OrderParsersTest`, `EscPosTest`, `ReceiptFormatterTest`
- 프린터 출력 추상화로 블루투스(즉시 동작) / 내장(SDK 확보 시) 둘 다 대비

### ⚠️ 아직 검증 안 됨 (중요)
- **컴파일/테스트를 한 번도 실행하지 못함.**
  (클라우드 환경에 Android SDK 없음 + Gradle 플러그인 다운로드용 네트워크 차단)
- 따라서 **로컬에서 처음 빌드 시 사소한 오류(임포트/버전 호환 등)가 날 수 있음.**
  → 에러 로그를 그대로 주면 즉시 수정.
- 컴파일 안전성 위주로 검토는 했음(특히 ESC/POS 바이트, 파서 정규식, Compose API).

---

## 4. 빌드 / 테스트

```bash
# 핵심 로직 테스트 (Android SDK 불필요, JDK 17+ & 인터넷만 있으면 됨)
./gradlew :core:test

# 디버그 APK (Android SDK 필요 — Android Studio 설치 시 자동)
./gradlew :app:assembleDebug
#   → app/build/outputs/apk/debug/app-debug.apk
```

### 버전 (호환 조합으로 고정)
- AGP **8.7.3** / Kotlin **2.0.21** / Gradle wrapper **8.11.1**
- compileSdk **35** / minSdk **24** / targetSdk **35**
- Android Studio **Ladybug(2024.2)+** 권장
- `local.properties` 는 커밋 안 됨 → Android Studio로 열면 SDK 경로 자동 생성

---

## 5. 다음 할 일 (우선순위)

1. **빌드 통과시키기** — 로컬에서 `./gradlew :core:test`, `:app:assembleDebug` 돌려 에러 잡기.
2. **실제 알림 샘플로 파서 보정** ← 가장 중요/현실적
   - APK 설치 → 앱의 **"알림 로그"** 화면에서 실제 배민/쿠팡 주문 알림의 **패키지명·본문** 확인
   - 패키지명을 `BaeminParser.KNOWN_PACKAGES` / `CoupangEatsParser.KNOWN_PACKAGES` 에 추가
   - 본문 패턴에 맞춰 `ParseUtils`/파서 정규식 보정 + `core` 테스트에 실제 샘플 추가
   - (현재 파서는 합리적 추정값 기반 — 실제 포맷과 다를 수 있음)
3. **블루투스 프린터로 실출력 확인** — 프린터 설정 → 페어링 기기 선택 → "테스트 출력"
4. (선택) **내장 프린터** — KICC에서 TS-194N 내장 프린터 SDK 확보 시 `KiccInnerPrinter` 구현

---

## 6. 미해결 / 사용자 확인 필요

- **사이드로딩**: TS-194N 에 외부 APK 설치가 허용되는지? (잠긴 VAN 단말기일 수 있음 → KICC/공급처 문의)
- **내장 프린터 SDK**: 공개 문서 없음. 확보 전까지는 **블루투스 ESC/POS 프린터** 사용.
- **알림 권한**: 앱 첫 실행 시 "알림 접근 권한" 허용 필수(설정 화면에서 유도).
- 알림 가로채기 방식의 한계: 알림에 담긴 정보까지만 출력 가능(부족하면 원문 그대로 출력).

---

## 7. 한 줄 요약 (다음 세션 첫 액션)

> "로컬에서 `./gradlew :core:test` 와 `:app:assembleDebug` 를 돌려 빌드 오류부터 잡고,
> APK를 TS-194N에 설치해 '알림 로그'로 실제 배민/쿠팡 주문 알림 샘플을 수집한 뒤 파서를 보정한다."
