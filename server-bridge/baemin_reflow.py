"""
배민 PDF(주문전표) → 42칸 ESC/POS 텍스트 재배치 프로토타입.

배경: 배민 PC 클라이언트는 주문/로그인 API가 인증서 고정(cert pinning)이라 쿠팡처럼
API를 따라할 수 없음. 대신 클라이언트가 "Microsoft Print to PDF"로 A4 PDF를 뽑을 수 있음(B안).
A4를 이미지로 래스터하면 글자가 뭉개져서, 텍스트를 추출(pdftotext -layout)해 프린터 폭(42칸)으로
재배치하면 쿠팡처럼 선명하게 나온다.

사용:
  pdftotext -layout -enc UTF-8 input.pdf out.txt
  python3 baemin_reflow.py out.txt

TODO(다음 세션):
  - 긴 옵션 줄(예: "└ [산미약간] Inner Peace (카라멜, 월넛, 코코넛)")이 truncate됨 → wrap 처리
  - 서버(server.py)에 /baemin 엔드포인트로 통합(PDF 업로드 → 이 변환 → ESC/POS)
  - ESC/POS 헤더(init)/하단 LF피드/ESC m 절단 붙이기, EUC-KR 인코딩
  - 앱: 변환된 바이트를 내장 프린터(KiccUdsClient, 포트 INTERNAL)로 전송
  - 자동화: 배민 자동 PDF저장(무프롬프트) + 폴더 감시 → 서버 업로드
"""
import re
import sys

W = 42


def is_wide(c):
    o = ord(c)
    return (0x1100 <= o <= 0x115F or 0x2E80 <= o <= 0xA4CF or 0xAC00 <= o <= 0xD7A3 or
            0xF900 <= o <= 0xFAFF or 0x3000 <= o <= 0x303F or 0xFF00 <= o <= 0xFF60)


def dw(s):
    return sum(2 if is_wide(c) else 1 for c in s)


def trunc(s, m):
    o = ''
    w = 0
    for c in s:
        cw = 2 if is_wide(c) else 1
        if w + cw > m:
            break
        o += c
        w += cw
    return o


def between(left, right):
    gap = W - dw(left) - dw(right)
    return left + ' ' * gap + right if gap >= 1 else trunc(left, W - dw(right) - 1) + ' ' + right


def reflow(text):
    out = []
    for raw in text.split('\n'):
        s = raw.strip()
        if not s:
            out.append('')
            continue
        if set(s) <= set('-─┌┐└┘'):  # dashes / box chars
            out.append('-' * W)
            continue
        if '주문전표' in s:
            out.append(' ' * max(0, (W - dw(s)) // 2) + s)
            continue
        if s == '[고객용]':
            out.append(' ' * max(1, W - dw(s)) + s)
            continue
        segs = [x for x in re.split(r'\s{2,}', raw.strip()) if x]
        if len(segs) <= 1:
            out.append(segs[0] if segs else '')
        elif len(segs) == 2:
            out.append(between(segs[0], segs[1]))
        else:
            out.append(between(segs[0], '  '.join(segs[1:])))
    return out


if __name__ == '__main__':
    txt = open(sys.argv[1], encoding='utf-8').read()
    print('=' * W)
    for line in reflow(txt):
        print(line)
    print('=' * W)
