#!/usr/bin/env bash
# 오라클(또는 임의 리눅스 VM)에 print bridge 서버를 상시가동 서비스로 설치한다.
#
# 사용:
#   sudo bash install.sh
# 선행:
#   - 이 스크립트를 server-bridge/deploy/ 안에서 실행(상위에 server.py, baemin_reflow.py 있어야 함).
#   - 쿠팡 자격증명 coupang_creds.json 을 server-bridge/ 에 두면 함께 설치된다(없으면 배민만 동작).
#
# 설치 후:
#   - 서비스 상태:  systemctl status printbridge
#   - 로그:        journalctl -u printbridge -f
#   - 앱 설정의 "서버 주소"에  http://<이 VM 공인IP>:8765  입력(고정 URL).
#   - 방화벽/보안그룹에서 8765 포트(TCP) 인바운드 허용 필요(오라클은 VCN 보안목록 + OS 방화벽 둘 다).
set -euo pipefail

DEST=/opt/printbridge
SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"   # server-bridge/

echo "==> 소스: $SRC_DIR  →  설치: $DEST"

# 1) pdftotext(poppler) 설치 — 배민 PDF 변환에 필수
if command -v pdftotext >/dev/null 2>&1; then
  echo "==> pdftotext 이미 설치됨"
elif command -v apt-get >/dev/null 2>&1; then
  echo "==> poppler-utils 설치(apt)"; apt-get update -y && apt-get install -y poppler-utils
elif command -v dnf >/dev/null 2>&1; then
  echo "==> poppler-utils 설치(dnf)"; dnf install -y poppler-utils
elif command -v yum >/dev/null 2>&1; then
  echo "==> poppler-utils 설치(yum)"; yum install -y poppler-utils
else
  echo "!! 패키지 매니저를 못 찾음 — pdftotext 를 수동 설치하세요"; fi

# 2) 파일 배치
mkdir -p "$DEST/uploads"
install -m 644 "$SRC_DIR/server.py" "$DEST/server.py"
install -m 644 "$SRC_DIR/baemin_reflow.py" "$DEST/baemin_reflow.py"
if [ -f "$SRC_DIR/coupang_creds.json" ]; then
  install -m 600 "$SRC_DIR/coupang_creds.json" "$DEST/coupang_creds.json"
  echo "==> coupang_creds.json 설치됨(쿠팡 활성)"
else
  echo "==> coupang_creds.json 없음 → 쿠팡 비활성(배민만). 나중에 $DEST 에 두고 재시작하면 활성."
fi

# 3) systemd 등록
install -m 644 "$SRC_DIR/deploy/printbridge.service" /etc/systemd/system/printbridge.service
systemctl daemon-reload
systemctl enable printbridge
systemctl restart printbridge
sleep 1
systemctl --no-pager status printbridge || true

echo ""
echo "==> 완료. 점검:"
echo "    curl -s http://localhost:8765/baemin"
echo "    앱 설정 '서버 주소' →  http://<이 VM 공인IP>:8765"
echo "    8765 포트 인바운드 허용 잊지 마세요(오라클 VCN 보안목록 + firewalld/iptables)."
