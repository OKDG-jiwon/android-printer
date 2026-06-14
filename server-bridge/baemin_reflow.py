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

# 줄 스타일 접두어(앱이 ESC/POS로 렌더). BIG=세로2배+굵게, BOLD=굵게, NORMAL=보통.
BIG = '\x01'
BOLD = '\x02'
NORMAL = ''


def push(out, style, lines):
    """스타일 접두어를 붙여 여러 줄을 out에 추가."""
    for l in lines:
        out.append(style + l)


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
    """좌/우 양끝 정렬. 한 줄에 안 들어가면 left(이름)를 통째로 줄바꿈하고
    right(금액)는 마지막 줄 끝에 우측 정렬한다(들어갈 자리가 없으면 새 줄에).

    이름이 길어도 금액 칸을 침범하지 않고, 금액은 항상 우측 정렬로 표시된다.
    """
    gap = W - dw(left) - dw(right)
    if gap >= 1:
        return [left + ' ' * gap + right]
    lines = wrap(left, W)
    last = lines[-1]
    if dw(last) + dw(right) + 1 <= W:
        lines[-1] = last + ' ' * (W - dw(last) - dw(right)) + right
    else:
        lines.append(' ' * (W - dw(right)) + right)
    return lines


def reflow_to_lines(text):
    """배민 PC는 '고객용 주문전표'만 PDF로 뽑으므로, 매장용 주문서 형태로 표시 변환한다.
    (데이터는 동일 — 라벨/제목 치환 + 매장용엔 없는 배달주소·결제방식 줄 제거.)
    """
    out = []
    in_addr = False
    for raw in text.split('\n'):
        raw = raw.replace('[고객용]', '[매장용]').replace('주문전표', '주문서')
        s = raw.strip()
        is_div = bool(s) and set(s) <= set('-─┌┐└┘')  # dashes / box chars

        # 배달주소 블록(매장용엔 없음): '배달주소'부터 다음 구분선까지 통째로 제거.
        if in_addr:
            if is_div:
                in_addr = False
            continue
        if s.startswith('배달주소'):
            in_addr = True
            continue
        if s.startswith('결제방식'):   # 매장용엔 없음
            continue

        if not s:
            out.append('')
            continue
        if is_div:
            out.append('-' * W)
            continue
        if '주문서' in s or '주문전표' in s:   # 제목: 가운데 + 크게/굵게
            push(out, BIG, [' ' * max(0, (W - dw(s)) // 2) + s])
            continue
        if re.fullmatch(r'\[\S{1,6}용\]', s):   # [매장용] 우측 정렬(보통)
            push(out, NORMAL, [' ' * max(1, W - dw(s)) + s])
            continue
        # 상단 주문번호(콜론 없는 짧은 코드) → 크게. 하단 '주문번호: T2...' 전체코드는 보통.
        if s.startswith('주문번호') and not s.startswith('주문번호:'):
            push(out, BIG, wrap(s, W))
            continue
        # 요청사항 값(가게 :/배달 : 등 ' : ' 포함, 수저포크) → 크게. '요청사항:'/'친환경:' 라벨은 보통.
        if ' : ' in s or s.startswith('수저포크'):
            push(out, BIG, wrap(s, W))
            continue
        segs = [x for x in re.split(r'\s{2,}', raw.strip()) if x]
        # 옵션 줄(└): 마지막 칸이 금액 → 금액(0 포함)을 우측 정렬. 이름이 길면 between 이 줄바꿈.
        if raw.lstrip().startswith('└') and len(segs) >= 2:
            push(out, NORMAL, between('  '.join(segs[:-1]), segs[-1].strip()))
        elif len(segs) <= 1:
            push(out, NORMAL, wrap(segs[0], W) if segs else [''])
        elif len(segs) == 2:
            left = segs[0]
            style = BIG if left.replace(' ', '') == '총결제금액' else (BOLD if left == '배달팁' else NORMAL)
            push(out, style, between(segs[0], segs[1]))
        else:
            left = segs[0]
            style = NORMAL if left == '메뉴' else (BOLD if left == '주문금액' else BIG)  # BIG=메뉴 항목
            push(out, style, between(segs[0], '  '.join(segs[1:])))

    # 줄 제거로 생긴 연속 구분선은 1개로 축약(구분선은 NORMAL=접두어 없음).
    collapsed = []
    for line in out:
        if line == '-' * W and collapsed and collapsed[-1] == '-' * W:
            continue
        collapsed.append(line)
    while collapsed and not collapsed[-1].strip():   # 끝 빈 줄 제거(하단 여백 축소)
        collapsed.pop()
    return collapsed


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
    # 미리보기: 스타일 접두어를 [B]/[b] 로 표시.
    print('=' * W)
    for line in txt.split('\n'):
        if line.startswith(BIG):
            print('[B] ' + line[1:])
        elif line.startswith(BOLD):
            print('[b] ' + line[1:])
        else:
            print('    ' + line)
    print('=' * W)
