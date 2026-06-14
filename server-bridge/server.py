import http.server, socketserver, sys, os, json, threading, time, urllib.request, urllib.error

# baemin_reflow.py 는 이 파일과 같은 디렉터리에 있어야 한다(배포 시 함께 복사).
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from baemin_reflow import reflow_pdf

APK = "/tmp/apk-serve/print.apk"
UPDIR = "/tmp/apk-serve/uploads"

# --- 배민 변환 큐 (메모리) — PC클라가 PDF 올리면 42칸 텍스트로 변환해 쌓아두고, 앱이 가져가 출력 ---
_baemin = []          # [{"id": str, "text": str, "at": int}]
_baemin_lock = threading.Lock()
_baemin_seq = [0]

# --- 쿠팡 POS API (HAR 캡처로 파악) — 서버가 직접 로그인해 세션 유지 ---
CREDS = json.load(open("/tmp/apk-serve/coupang_creds.json"))
COUPANG_STORE_ID = str(CREDS["storeId"])
# 클라이언트가 스스로 붙이는 정적 쿠키(서버 Set-Cookie 아님)
STATIC_COOKIES = ("device-id=%s; version=1.10.41; coupang-pos-version=1.10.41; app-type=COUPANG_POS"
                  % CREDS.get("deviceId", ""))
_LOGIN_HEADERS = {
    "content-type": "application/json", "accept": "application/json",
    "x-eats-locale": "ko", "coupang-pos-version": "1.10.41",
    "client-sign": CREDS.get("clientSign", ""), "user-agent": "Mozilla/5.0",
    "app-type": "COUPANG_POS", "device-id": CREDS.get("deviceId", ""),
}
_lock = threading.Lock()
_cookie = [None]   # 현재 유효 쿠키 문자열


def _post(url, payload, cookie):
    headers = dict(_LOGIN_HEADERS)
    if cookie:
        headers["cookie"] = cookie
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"),
                                 headers=headers, method="POST")
    resp = urllib.request.urlopen(req, timeout=15)
    setc = {}
    for v in resp.headers.get_all("Set-Cookie") or []:
        nv = v.split(";", 1)[0]
        if "=" in nv:
            k, val = nv.split("=", 1)
            setc[k.strip()] = val.strip()
    return json.loads(resp.read().decode("utf-8")), setc


def _login():
    """2단계 로그인 → 전체 쿠키 문자열 생성/저장."""
    jar = {}
    _, c1 = _post("https://pos-api.coupang.com/api/v2/auth/sign-in/user",
                  {"username": CREDS["username"], "password": CREDS["password"], "encrypt": False},
                  STATIC_COOKIES)
    jar.update(c1)
    cookie_after_user = STATIC_COOKIES + "; " + "; ".join("%s=%s" % kv for kv in jar.items())
    _, c2 = _post("https://pos-api.coupang.com/api/v2/auth/sign-in/store",
                  {"storeId": CREDS["storeId"]}, cookie_after_user)
    jar.update(c2)
    _cookie[0] = STATIC_COOKIES + "; " + "; ".join("%s=%s" % kv for kv in jar.items())
    sys.stderr.write("COUPANG LOGIN ok (cookies: %s)\n" % ",".join(jar.keys()))
    return _cookie[0]


def coupang_get(url):
    with _lock:
        if not _cookie[0]:
            _login()
        cookie = _cookie[0]
    try:
        req = urllib.request.Request(url, headers={
            "accept": "application/json", "x-eats-locale": "ko",
            "user-agent": "Mozilla/5.0", "cookie": cookie})
        return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):   # 세션 만료 → 재로그인 후 1회 재시도
            with _lock:
                _login()
                cookie = _cookie[0]
            req = urllib.request.Request(url, headers={
                "accept": "application/json", "x-eats-locale": "ko",
                "user-agent": "Mozilla/5.0", "cookie": cookie})
            return json.loads(urllib.request.urlopen(req, timeout=15).read().decode("utf-8"))
        raise


def map_order(o):
    items = []
    for it in o.get("items", []):
        opts = [op.get("optionName", "") for op in it.get("itemOptions", [])]
        price = (it.get("subTotalPriceMoney") or {}).get("units")
        items.append({
            "name": it.get("name", ""),
            "quantity": it.get("quantity", 1),
            "price": price,
            "options": [x for x in opts if x],
        })
    return {
        "orderId": o.get("orderId"),
        "abbrOrderId": o.get("abbrOrderId"),
        "platform": "COUPANG_EATS",
        "status": o.get("status"),
        "serviceType": o.get("orderServiceType"),
        "orderedAt": (o.get("orderedAt") or {}).get("dateTime", 0),
        "storeName": (o.get("store") or {}).get("name"),
        "subtotal": o.get("salePrice"),
        "deliveryFee": int((o.get("deliveryFeeMoney") or {}).get("units", 0)),
        "totalAmount": o.get("totalAmount"),
        "paymentMethod": o.get("paymentMethodType"),
        "note": o.get("note", "") or "",
        "items": items,
    }


def fetch_orders(status):
    """status 는 콤마로 여러 개 지정 가능. 예: "PENDING,PROCESSING".

    쿠팡은 조회 버킷이 주문의 실제 status 필드와 다르다:
      - PENDING    = 신규(미수락)
      - PROCESSING = 수락·조리·배달 등 진행중(실제 status 는 ACCEPTED 등)
      - COMPLETED  = 완료
    "진행중"을 보려면 PENDING + PROCESSING 을 합쳐야 한다.
    """
    statuses = [s.strip() for s in status.split(",") if s.strip()] or ["PENDING"]
    merged = {}  # orderId → order (중복 제거)
    for st in statuses:
        url = ("https://pos-api.coupang.com/api/v2/stores/orders"
               "?version=v3&storeIds=%s&sort=OLD&status=%s" % (COUPANG_STORE_ID, st))
        data = coupang_get(url)
        content = (data.get("content") or {}).get("content") or []
        for o in content:
            mo = map_order(o)
            merged[mo["orderId"]] = mo
    orders = list(merged.values())
    orders.sort(key=lambda o: o.get("orderedAt") or 0)
    return orders


def parse_multipart(body, ctype):
    """multipart/form-data → [(filename, bytes), ...]. 파일 파트만."""
    if "boundary=" not in ctype:
        return []
    boundary = ctype.split("boundary=", 1)[1].strip().strip('"')
    files = []
    for part in body.split(("--" + boundary).encode()):
        if b"\r\n\r\n" not in part:
            continue
        head, data = part.split(b"\r\n\r\n", 1)
        hs = head.decode("utf-8", "replace")
        if "filename=" not in hs:
            continue
        fn = hs.split("filename=", 1)[1].split("\r\n", 1)[0].strip().strip('"')
        if not fn:
            continue
        if data.endswith(b"\r\n"):
            data = data[:-2]
        files.append((fn, data))
    return files


def baemin_enqueue(pdf_bytes):
    """배민 PDF 바이트 → 임시저장 → 42칸 변환 → 큐 적재. 새 항목 id 반환."""
    os.makedirs(UPDIR, exist_ok=True)
    with _baemin_lock:
        _baemin_seq[0] += 1
        oid = "bm%d" % _baemin_seq[0]
    path = os.path.join(UPDIR, oid + ".pdf")
    with open(path, "wb") as f:
        f.write(pdf_bytes)
    text = reflow_pdf(path)
    item = {"id": oid, "text": text, "at": int(time.time() * 1000)}
    with _baemin_lock:
        _baemin.append(item)
    sys.stderr.write("BAEMIN enqueue %s (%d chars)\n" % (oid, len(text)))
    return oid


DL_PAGE = ("""<!DOCTYPE html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>배달프린터 설치</title></head>
<body style="margin:0;font-family:sans-serif;background:#f5f5f5;text-align:center;padding:36px 16px;">
<h2 style="color:#222;">배달프린터 앱 설치</h2>
<a href="/print.apk" download="print.apk" style="display:inline-block;margin-top:22px;padding:24px 44px;
background:#2e7d32;color:#fff;font-size:24px;font-weight:bold;text-decoration:none;border-radius:14px;">⬇ print.apk 다운로드</a>
<p style="margin-top:6px;font-size:15px;"><a href="/print.apk">print.apk 직접 링크</a></p></body></html>""").encode("utf-8")

UP_PAGE = ("""<!DOCTYPE html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>업로드</title></head>
<body style="margin:0;font-family:sans-serif;background:#f5f5f5;text-align:center;padding:36px 16px;">
<h2>파일 업로드</h2>
<form method="post" action="/upload" enctype="multipart/form-data" style="margin-top:24px;">
<input type="file" name="file" style="font-size:16px;"><br><br>
<button type="submit" style="padding:20px 40px;background:#1565c0;color:#fff;font-size:22px;
font-weight:bold;border:none;border-radius:12px;">⬆ 업로드</button></form></body></html>""").encode("utf-8")


class H(http.server.BaseHTTPRequestHandler):
    def _send(self, code, ctype, body, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        if self.path.startswith("/print.apk"):
            with open(APK, "rb") as f:
                data = f.read()
            self._send(200, "application/octet-stream", data,
                       {"Content-Disposition": 'attachment; filename="print.apk"'})
        elif self.path.startswith("/orders"):
            status = "PENDING"
            if "status=" in self.path:
                status = self.path.split("status=", 1)[1].split("&")[0]
            try:
                orders = fetch_orders(status)
                body = json.dumps({"ok": True, "orders": orders}, ensure_ascii=False).encode("utf-8")
                self._send(200, "application/json; charset=utf-8", body)
            except urllib.error.HTTPError as e:
                msg = {"ok": False, "error": "coupang HTTP %d (세션 만료 가능)" % e.code}
                self._send(200, "application/json; charset=utf-8",
                           json.dumps(msg, ensure_ascii=False).encode("utf-8"))
            except Exception as e:
                self._send(200, "application/json; charset=utf-8",
                           json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False).encode("utf-8"))
        elif self.path.startswith("/baemin"):
            with _baemin_lock:
                receipts = list(_baemin)
            body = json.dumps({"ok": True, "receipts": receipts}, ensure_ascii=False).encode("utf-8")
            self._send(200, "application/json; charset=utf-8", body)
        elif self.path.startswith("/up"):
            self._send(200, "text/html; charset=utf-8", UP_PAGE)
        else:
            self._send(200, "text/html; charset=utf-8", DL_PAGE)

    def _read_body(self):
        clen = int(self.headers.get("Content-Length", "0") or "0")
        return self.rfile.read(clen)

    def do_POST(self):
        if self.path.startswith("/uplog"):
            body = self._read_body()
            os.makedirs(UPDIR, exist_ok=True)
            with open(os.path.join(UPDIR, "notiflog.json"), "wb") as f:
                f.write(body)
            self._send(200, "application/json; charset=utf-8", b'{"ok":true}')
            return

        # 배민 PDF 업로드 → 변환 큐 적재. PC클라 폴더감시기 또는 /baeminup 페이지에서 사용.
        if self.path.startswith("/baemin/ack"):
            body = self._read_body()
            try:
                oid = json.loads(body.decode("utf-8")).get("id")
            except Exception:
                oid = None
            with _baemin_lock:
                _baemin[:] = [r for r in _baemin if r["id"] != oid]
            self._send(200, "application/json; charset=utf-8", b'{"ok":true}')
            return
        if self.path.startswith("/baemin"):
            ctype = self.headers.get("Content-Type", "")
            files = parse_multipart(self._read_body(), ctype)
            ids = []
            try:
                for _, data in files:
                    if data:
                        ids.append(baemin_enqueue(data))
            except Exception as e:
                self._send(200, "application/json; charset=utf-8",
                           json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False).encode("utf-8"))
                return
            self._send(200, "application/json; charset=utf-8",
                       json.dumps({"ok": True, "ids": ids}, ensure_ascii=False).encode("utf-8"))
            return

        if not self.path.startswith("/upload"):
            self._send(404, "text/plain; charset=utf-8", b"not found"); return
        os.makedirs(UPDIR, exist_ok=True)
        saved = []
        for fn, data in parse_multipart(self._read_body(), self.headers.get("Content-Type", "")):
            safe = os.path.basename(fn).replace("/", "_") or "upload.bin"
            with open(os.path.join(UPDIR, safe), "wb") as f:
                f.write(data)
            saved.append(safe)
        msg = ("업로드 완료: " + ", ".join(saved)) if saved else "파일 없음"
        self._send(200, "text/html; charset=utf-8",
                   ("<html><meta charset=utf-8><body style='font-family:sans-serif;text-align:center;padding:40px'>"
                    "<h2>" + msg + "</h2></body></html>").encode("utf-8"))

    def log_message(self, *a):
        sys.stderr.write("%s %s %s\n" % (self.client_address[0], self.command, self.path))


socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("0.0.0.0", 8765), H) as httpd:
    print("serving on 8765", flush=True)
    httpd.serve_forever()
