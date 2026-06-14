"""
배민 PDF 폴더 감시기 — 새 주문전표 PDF가 폴더에 생기면 자동으로 서버 /baemin 에 업로드.

배경: 배민 PC 클라이언트는 주문/로그인 API가 인증서 고정이라 API 미러링 불가(B안).
PC클라가 새 주문을 PDF로 저장하면, 이 감시기가 폴더를 지켜보다 서버로 올린다.
서버(server.py)가 pdftotext로 42칸 변환해 큐에 쌓고, POS 앱이 가져가 내장 프린터로 출력한다.

흐름:
  PC클라 → (PDF 자동저장) WATCH_DIR → [이 감시기] → POST SERVER/baemin → 서버 변환·큐 → 앱 출력

특징:
  - 외부 패키지 없음(표준 라이브러리만). 맥/윈도우 공통.
  - 폴링 방식. 새 *.pdf 발견 시 파일 크기가 멈출 때까지 기다렸다가(쓰기 완료) 업로드.
  - 성공한 PDF는 WATCH_DIR/_uploaded/ 로, 실패는 _failed/ 로 이동 → 같은 파일 중복 업로드 방지.

사용:
  python3 baemin_watch.py [WATCH_DIR] [SERVER_URL]
  # 또는 환경변수
  BAEMIN_WATCH_DIR=... BAEMIN_SERVER=https://... python3 baemin_watch.py

기본값:
  WATCH_DIR  = ~/Downloads
  SERVER_URL = http://localhost:8765   (오라클 이전 시 고정 URL, 임시로는 cloudflared 터널 URL)

선행조건(환경설정 — 코드 밖):
  배민 PC클라의 인쇄가 "경로를 매번 묻는" 프린터(Microsoft Print to PDF)면 자동저장 불가.
  무프롬프트로 고정 폴더에 저장하는 PDF 프린터(예: 자동저장 옵션이 있는 PDF 프린터)를 WATCH_DIR 로 지정할 것.
"""
import os
import sys
import time
import uuid
import urllib.request
import urllib.error

WATCH_DIR = os.environ.get("BAEMIN_WATCH_DIR") or (sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/Downloads"))
SERVER = (os.environ.get("BAEMIN_SERVER") or (sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8765")).rstrip("/")

POLL_SEC = 2.0          # 폴더 스캔 주기
STABLE_SEC = 1.5        # 파일 크기가 이 시간 동안 안 변하면 "쓰기 완료"로 간주
UPLOADED_DIR = os.path.join(WATCH_DIR, "_uploaded")
FAILED_DIR = os.path.join(WATCH_DIR, "_failed")


def log(msg):
    sys.stderr.write("[baemin_watch] %s\n" % msg)
    sys.stderr.flush()


def is_stable(path):
    """파일이 더 이상 쓰이지 않는지(크기 안정) 확인."""
    try:
        s1 = os.path.getsize(path)
    except OSError:
        return False
    time.sleep(STABLE_SEC)
    try:
        return os.path.getsize(path) == s1 and s1 > 0
    except OSError:
        return False


def build_multipart(filename, data):
    boundary = "----baeminwatch" + uuid.uuid4().hex
    crlf = b"\r\n"
    body = bytearray()
    body += b"--" + boundary.encode() + crlf
    body += ('Content-Disposition: form-data; name="file"; filename="%s"' % filename).encode() + crlf
    body += b"Content-Type: application/pdf" + crlf + crlf
    body += data + crlf
    body += b"--" + boundary.encode() + b"--" + crlf
    return bytes(body), "multipart/form-data; boundary=" + boundary


def upload(path):
    with open(path, "rb") as f:
        data = f.read()
    body, ctype = build_multipart(os.path.basename(path), data)
    req = urllib.request.Request(SERVER + "/baemin", data=body,
                                 headers={"Content-Type": ctype}, method="POST")
    resp = urllib.request.urlopen(req, timeout=30)
    return resp.read().decode("utf-8", "replace")


def move_to(path, dest_dir):
    os.makedirs(dest_dir, exist_ok=True)
    base = os.path.basename(path)
    dest = os.path.join(dest_dir, base)
    if os.path.exists(dest):  # 같은 이름 충돌 시 접미사
        root, ext = os.path.splitext(base)
        dest = os.path.join(dest_dir, "%s_%s%s" % (root, uuid.uuid4().hex[:6], ext))
    os.replace(path, dest)
    return dest


def main():
    os.makedirs(WATCH_DIR, exist_ok=True)
    log("watching %s  →  %s/baemin" % (WATCH_DIR, SERVER))
    seen = set()  # 이번 실행에서 처리 시도한 경로(이동 전 중복 처리 방지)
    while True:
        try:
            for name in sorted(os.listdir(WATCH_DIR)):
                if not name.lower().endswith(".pdf"):
                    continue
                path = os.path.join(WATCH_DIR, name)
                if path in seen or not os.path.isfile(path):
                    continue
                if not is_stable(path):
                    continue  # 아직 쓰는 중 → 다음 스캔에서 재시도
                seen.add(path)
                try:
                    result = upload(path)
                    dest = move_to(path, UPLOADED_DIR)
                    log("uploaded %s → %s  (%s)" % (name, os.path.basename(dest), result.strip()))
                except (urllib.error.URLError, urllib.error.HTTPError, OSError) as e:
                    log("FAILED %s: %s" % (name, e))
                    try:
                        move_to(path, FAILED_DIR)
                    except OSError:
                        pass
                finally:
                    seen.discard(path)  # 이동됐으므로 set에서 제거(같은 이름 재유입 대비)
        except OSError as e:
            log("scan error: %s" % e)
        time.sleep(POLL_SEC)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("stopped")
