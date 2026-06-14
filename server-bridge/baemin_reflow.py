"""
배민 PDF(주문전표) → 42칸 ESC/POS 텍스트 재배치.

배경: 배민 PC 클라이언트는 주문/로그인 API가 인증서 고정(cert pinning)이라 쿠팡처럼
API를 따라할 수 없음. 대신 클라이언트가 "Microsoft Print to PDF"로 A4 PDF를 뽑을 수 있음(B안).
A4를 이미지로 래스터하면 글자가 뭉개져서, 텍스트를 추출(pdftotext -layout)해 프린터 폭(42칸)으로
재배치하면 쿠팡처럼 선명하게 나온다.

사용(단독):
  pdftotext -layout -enc UTF-8 input.pdf out.txt
  python3 baemin_reflow.py out.txt

server.py 에서는 reflow_text()/reflow_pdf() 를 import 해서 쓴다.

ESC/POS(init/EUC-KR/LF피드/ESC m 절단)는 앱(PreformattedReceiptFormatter)이 붙인다.
여기선 순수 42칸 텍스트 레이아웃만 만든다.
"""
import re
import subprocess
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


def wrap(s, w):
    """표시폭 기준 w칸을 넘지 않게 줄바꿈(글자 경계). 빈 문자열은 ['']."""
    lines = []
    cur = ''
    cw = 0
    for c in s:
        c_w = 2 if is_wide(c) else 1
        if cw + c_w > w and cur:
            lines.append(cur)
            cur = ''
            cw = 0
        cur += c
        cw += c_w
    lines.append(cur)
    return lines


def between(left, right):
    """좌/우 양끝 정렬. 한 줄에 안 들어가면 left를 줄바꿈하고 right는 첫 줄 끝에 둔다.

    (금액 right 는 절대 잘리거나 다음 줄로 안 넘김 — 영수증 가독성.)
    """
    gap = W - dw(left) - dw(right)
    if gap >= 1:
        return [left + ' ' * gap + right]
    # left가 너무 길다 → 첫 줄엔 right가 들어갈 자리만큼만 left를 넣고, 나머지 left는 아래 줄로.
    first_w = W - dw(right) - 1
    head = trunc(left, first_w)
    out = [head + ' ' * (W - dw(head) - dw(right)) + right]
    tail = left[len(head):]
    if tail:
        out.extend(wrap(tail, W))
    return out


def reflow_to_lines(text):
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
        # 옵션 줄(└): 마지막 칸이 금액. 금액이 0이면 떼고 이름만 좌측 정렬·줄바꿈
        # (긴 옵션이 우측 정렬되며 수량/금액 칸을 침범하는 것 방지). 금액이 있으면(배달팁 등) 표시.
        if raw.lstrip().startswith('└') and len(segs) >= 2:
            name = '  '.join(segs[:-1])
            price = segs[-1].strip()
            if price in ('0', '-0'):
                out.extend(wrap(name, W))
            else:
                out.extend(between(name, price))
        elif len(segs) <= 1:
            out.extend(wrap(segs[0], W) if segs else [''])
        elif len(segs) == 2:
            out.extend(between(segs[0], segs[1]))
        else:
            out.extend(between(segs[0], '  '.join(segs[1:])))
    return out


def reflow_text(text):
    """재배치된 42칸 영수증 텍스트(문자열)."""
    return '\n'.join(reflow_to_lines(text))


def reflow_pdf(pdf_path):
    """PDF 경로 → 42칸 영수증 텍스트. pdftotext -layout 사용."""
    raw = subprocess.run(
        ['pdftotext', '-layout', '-enc', 'UTF-8', pdf_path, '-'],
        check=True, capture_output=True, text=True,
    ).stdout
    return reflow_text(raw)


if __name__ == '__main__':
    arg = sys.argv[1]
    txt = reflow_pdf(arg) if arg.lower().endswith('.pdf') else reflow_text(open(arg, encoding='utf-8').read())
    print('=' * W)
    print(txt)
    print('=' * W)
