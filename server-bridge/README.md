# server-bridge — 클라우드 주문 중계

POS 앱(`android-printer`)과 배달 플랫폼을 잇는 맥/리눅스용 중계 서버 + 배민 자동화 도구.

## 구성

| 파일 | 역할 |
|------|------|
| `server.py` | HTTP 서버(:8765). 쿠팡 POS API 자동로그인·주문조회, 배민 PDF 변환 큐, APK 다운로드/업로드 |
| `baemin_reflow.py` | 배민 주문전표 PDF → 42칸 영수증 텍스트 재배치 (`server.py`가 import) |
| `baemin_watch.py` | 배민 PDF 저장 폴더 감시 → 새 PDF 자동 업로드(`/baemin`) |
| `coupang_creds.json` | 쿠팡 로그인 정보. **gitignore됨, 커밋 금지** |

> `server.py`는 `baemin_reflow.py`를 import하므로 **둘은 항상 같은 디렉터리**에 둘 것.

## 엔드포인트

- `GET  /orders?status=PENDING|COMPLETED` — 쿠팡 주문 목록(JSON)
- `POST /baemin` (multipart PDF) — 배민 PDF 업로드 → 변환 → 큐 적재
- `GET  /baemin` — 대기 중 배민 변환 영수증 목록(JSON)
- `POST /baemin/ack` (`{"id":"bm1"}`) — 출력 완료분 큐에서 제거(중복 방지)
- `GET  /print.apk` / `GET /up`(업로드 페이지) — APK 사이드로딩

## 실행

```bash
# 서버 (8765). coupang_creds.json 이 같은 폴더에 있어야 쿠팡 기능 동작.
python3 server.py

# 임시 공개 URL (오라클 이전 전까지)
cloudflared tunnel --url http://localhost:8765
```

배포 시 `server.py`, `baemin_reflow.py`, `coupang_creds.json` 세 개를 함께 복사할 것.

## 배민 자동화

```bash
# 배민 PC클라가 PDF를 저장하는 폴더를 감시 → 자동 업로드
python3 baemin_watch.py [WATCH_DIR] [SERVER_URL]
BAEMIN_WATCH_DIR=~/배민PDF BAEMIN_SERVER=https://<터널>.trycloudflare.com python3 baemin_watch.py
```

- 새 `*.pdf` 감지 → 쓰기 완료 대기 → 업로드 → 성공분은 `WATCH_DIR/_uploaded/`, 실패분은 `_failed/`로 이동.
- **선행조건**: 배민 PC클라 인쇄가 경로를 매번 묻지 않고 고정 폴더에 **무프롬프트 자동저장**되도록 PDF 프린터를 설정해야 함(이건 OS/프린터 설정이라 코드 밖).

## 흐름 요약

```
쿠팡:  앱 ──GET /orders──▶ 서버 ──쿠팡 POS API──▶ (자동로그인·세션유지)
배민:  PC PDF ──watch──▶ POST /baemin ──pdftotext+42칸──▶ 큐 ──GET /baemin──▶ 앱 출력 ──ack──▶ 큐 제거
```
