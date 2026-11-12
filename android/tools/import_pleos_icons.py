"""Pleos ConnectTheme SVG 아이콘 -> Android VectorDrawable (android/app/pleos-icons/res/drawable/pleos_<name>.xml).

- 현대차 소유 리소스라 출력 폴더(android/app/pleos-icons/)는 Git에 올리지 않는다(.gitignore).
- 이 폴더가 없으면 빌드는 app/fallback-icons/res 의 단순 도형 아이콘을 쓴다.
- VectorDrawable로 옮길 수 없는 요소(변환 행렬, 48 아닌 viewBox 등)가 있는 아이콘은 경고를 찍고 건너뛴다.

사용: python android/tools/import_pleos_icons.py [pleos-design-ref/icons-svg 경로]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]  # SignalSupervisor/
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "pleos-design-ref" / "icons-svg"
OUT = HERE.parent / "app" / "pleos-icons" / "res" / "drawable"

# mockup/tools/copy_pleos_icons.py 의 NAMES 와 같은 목록을 읽어 온다.
_src = (ROOT / "mockup" / "tools" / "copy_pleos_icons.py").read_text(encoding="utf-8")
NAMES = re.search(r'NAMES = """(.*?)"""', _src, re.S).group(1).split()

SVG = "{http://www.w3.org/2000/svg}"


def convert(svg_file):
    root = ET.parse(svg_file).getroot()
    if root.get("viewBox") != "0 0 48 48":
        raise ValueError(f"viewBox {root.get('viewBox')}")
    paths = []

    def walk(el):
        for ch in el:
            tag = ch.tag.replace(SVG, "")
            if tag == "g":
                if ch.get("transform"):
                    raise ValueError("g transform")
                walk(ch)
            elif tag == "path":
                paths.append(ch)
            else:
                raise ValueError(f"element <{tag}>")

    walk(root)
    out = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="48dp" android:height="48dp"',
        '    android:viewportWidth="48" android:viewportHeight="48">',
    ]
    for p in paths:
        attrs = ['android:fillColor="#FF000000"', f'android:pathData="{p.get("d")}"']
        if p.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
        if p.get("fill-opacity"):
            attrs.append(f'android:fillAlpha="{p.get("fill-opacity")}"')
        out.append("    <path " + " ".join(attrs) + "/>")
    out.append("</vector>")
    return "\n".join(out) + "\n"


OUT.mkdir(parents=True, exist_ok=True)
ok, skipped = 0, []
for n in NAMES:
    f = SRC / f"ic_conn_{n}_bk_default.svg"
    try:
        xml = convert(f)
    except (OSError, ValueError, ET.ParseError) as e:
        print(f"warning: skip {n}: {e}")
        skipped.append(n)
        continue
    (OUT / f"pleos_{n}.xml").write_text(xml, encoding="utf-8")
    ok += 1
print(f"wrote {ok}/{len(NAMES)} icons to {OUT}")
