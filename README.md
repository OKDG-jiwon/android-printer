# 배달주문 출력 (Delivery Order Printer)

안드로이드 POS 단말기(예: **이지체크 TS-194N / KICC**)에서, 배달앱(**배달의민족 · 쿠팡이츠**) 사장님 앱으로
들어오는 **주문 푸시 알림을 가로채 자동으로 영수증/주문서를 출력**하는 앱입니다.

기존 배달앱이 단말기에서 출력 기능을 제대로 지원하지 않을 때, 배달사 공식 API 제휴 없이
가장 간단하게 출력을 가능하게 하는 것이 목표입니다.

## 동작 방식

```
배달앱 주문 알림  ──▶  NotificationListenerService  ──▶  OrderParser(배민/쿠팡)
                                                              │
                                                              ▼
                          블루투스 ESC/POS 프린터  ◀──  PrintController(큐·재시도)  ◀──  ReceiptFormatter
```

1. `NotificationListenerService` 로 배민/쿠팡이츠 주문 알림을 수신
2. 알림 본문을 `OrderParser` 가 주문 정보로 파싱
3. `ReceiptFormatter` 가 ESC/POS 바이트열로 변환
4. `PrintController`(포그라운드 서비스) 가 프린터로 출력(실패 시 재시도)

## 모듈 구조

| 모듈 | 내용 | 비고 |
|------|------|------|
| `core` | 순수 Kotlin: 주문 모델, 알림 파서, ESC/POS 영수증 포맷터 | 안드로이드 비의존, JVM 단위테스트 가능 |
| `app`  | 안드로이드: 알림 리스너, 블루투스 프린터, 포그라운드 서비스, Compose UI | |

## 프린터 옵션

- **블루투스 ESC/POS (권장, 기본):** 제조사 SDK 불필요. 표준 ESC/POS 영수증 프린터에서 바로 동작.
- **내장 프린터 (KICC):** `KiccInnerPrinter` 자리만 마련됨. KICC에서 단말기 내장 프린터 SDK를 확보하면 연동.
  SDK 확보 전까지는 블루투스 프린터를 사용하세요.

## 빌드 / 테스트

```bash
# 핵심 로직 단위 테스트 (Android SDK 불필요)
./gradlew :core:test

# 디버그 APK 빌드 (Android SDK 필요)
./gradlew :app:assembleDebug
# 산출물: app/build/outputs/apk/debug/app-debug.apk
```

> 이 저장소에는 `local.properties` 가 포함되지 않습니다. Android Studio로 열면 SDK 경로가 자동 생성됩니다.

## 단말기 설정 (TS-194N 등)

1. 앱 설치 후 실행 → **"권한 설정 열기"** 에서 알림 접근 권한 허용
2. **프린터 설정** → 블루투스 프린터 페어링 후 선택, 용지 폭(58/80mm) 지정
3. **테스트 출력** 으로 정상 출력 확인
4. **자동 출력 ON** → 실제 배달 주문이 오면 자동 출력

## 파서 보정 (중요)

배달앱 알림의 실제 포맷은 앱 버전에 따라 다릅니다. 앱의 **알림 로그** 화면에서
실제 주문 알림의 패키지명/본문을 확인한 뒤:

- 패키지명을 `BaeminParser.KNOWN_PACKAGES` / `CoupangEatsParser.KNOWN_PACKAGES` 에 추가
- 본문 패턴에 맞춰 `OrderParser`/`ParseUtils` 의 정규식을 보정
- `core` 단위 테스트에 실제 샘플을 추가해 회귀 방지

## 알려진 제약 / 확인 필요

- **사이드로딩:** TS-194N 에 외부 APK 설치가 허용되어야 합니다(잠긴 단말기일 수 있으므로 KICC/공급처 확인).
- **내장 프린터 SDK:** 공개되어 있지 않아 KICC 문의 필요. 그 전까지는 블루투스 프린터 사용.
- 알림 가로채기 방식은 알림에 담긴 정보까지만 출력할 수 있습니다(정보가 부족하면 원문을 그대로 출력).
