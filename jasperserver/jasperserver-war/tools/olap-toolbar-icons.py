# -*- coding: utf-8 -*-
"""Bangun ikon toolbar OLAP (JPivot) bergaya garis modern.

    python3 tools/olap-toolbar-icons.py

Hasilnya ditulis ke src/main/webapp/jpivot/toolbar/, yang menimpa ikon lama dari
overlay biner ji-jpivot-war (maven-war-plugin mengutamakan berkas proyek sendiri).

Setiap ikon didefinisikan sebagai isi <svg viewBox="0 0 24 24"> memakai stroke saja,
lalu dirender Chrome headless pada 3x (72 px) supaya tajam di layar HiDPI — markup
vtoolbar.xsl memasang width/height 24 secara keras. Nama berkas tidak boleh berubah:
wcf toolbar menyusunnya dari atribut img= di viewOlap.jsp ditambah akhiran -up/-down
(-down = keadaan aktif/tertekan).

Butuh Google Chrome dan Pillow; jalankan di host, bukan di VM build.
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "build")
WEBAPP = os.path.join(HERE, os.pardir, "src", "main", "webapp", "jpivot", "toolbar")
os.makedirs(OUT, exist_ok=True)
UP_STROKE   = "#37475C"   # slate, seirama dengan #333/#666 pada tema default
DOWN_BG     = "#062E79"   # aksen tema default JasperReports Server
DOWN_STROKE = "#FFFFFF"
SCALE = 3
CELL = 24 * SCALE

# --- glyph: hanya elemen bergaya stroke, koordinat pada grid 24x24 ---------
G = {}

G["zoom"] = """
<rect x="2.6" y="2.6" width="10.4" height="10.4" rx="1.6"/>
<path d="M2.6 7.8h10.4M7.8 2.6v10.4"/>
<circle cx="16.6" cy="16.6" r="4.1"/>
<path d="M19.6 19.6l2.1 2.1"/>
"""

G["sortacross"] = """
<path d="M3.5 6h11M3.5 12h8M3.5 18h5"/>
<path d="M18.6 5.2v13.9"/>
<path d="M15.7 16.2l2.9 2.9 2.9-2.9"/>
"""

G["showempty"] = """
<rect x="3" y="4" width="18" height="16" rx="2"/>
<path d="M3 9.4h18M3 14.6h18M9.6 4v16"/>
<path d="M4.6 20.4L19.4 3.6"/>
"""

G["swapaxes"] = """
<path d="M3.6 8.4h13.2"/>
<path d="M13.8 5.4l3 3-3 3"/>
<path d="M20.4 15.6H7.2"/>
<path d="M10.2 12.6l-3 3 3 3"/>
"""

G["displayoptions"] = """
<path d="M3 8h18M3 16h18"/>
<circle cx="9" cy="8" r="2.4"/>
<circle cx="15" cy="16" r="2.4"/>
"""

G["chart-new"] = """
<path d="M3.6 20.4h16.8"/>
<path d="M7.4 20.4v-6.6M12 20.4V8.6M16.6 20.4v-9.4"/>
"""

G["chartoptions"] = """
<path d="M3 20.2h11.4"/>
<path d="M6 20.2v-5.4M9.6 20.2v-9M13.2 20.2v-6.6"/>
<circle cx="18.2" cy="7.4" r="2.4"/>
<path d="M18.2 3.4v1.2M18.2 10.2v1.2M14.9 5.5l1 .6M20.5 8.7l1 .6M14.9 9.3l1-.6M20.5 6.1l1-.6"/>
"""

G["cube-new"] = """
<path d="M12 2.6l8.6 4.6v9.6L12 21.4 3.4 16.8V7.2z"/>
<path d="M3.4 7.2L12 11.8l8.6-4.6"/>
<path d="M12 11.8v9.6"/>
"""

G["mdxquery"] = """
<path d="M8.6 7.2L3.8 12l4.8 4.8"/>
<path d="M15.4 7.2L20.2 12l-4.8 4.8"/>
<path d="M13.4 4.4l-2.8 15.2"/>
"""

G["excel-new"] = """
<rect x="3" y="3.4" width="12.8" height="12.8" rx="1.8"/>
<path d="M3 9.8h12.8M9.4 3.4v12.8"/>
<path d="M19.2 12.4v8.8"/>
<path d="M16.4 18.4l2.8 2.8 2.8-2.8"/>
"""

G["print-new"] = """
<path d="M6.8 9V3.2h10.4V9"/>
<path d="M6.8 18H4.6a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h14.8a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2.2"/>
<rect x="6.8" y="14" width="10.4" height="6.8" rx="1.2"/>
"""

G["outputopts"] = """
<path d="M13 2.8H5.8a1.8 1.8 0 0 0-1.8 1.8v13.2a1.8 1.8 0 0 0 1.8 1.8h5.4"/>
<path d="M13 2.8l4.6 4.6v3.2"/>
<path d="M13 2.8v4.6h4.6"/>
<circle cx="17.8" cy="17.4" r="2.4"/>
<path d="M17.8 13.4v1.2M17.8 20.2v1.2M14.5 15.5l1 .6M20.1 18.7l1 .6M14.5 19.3l1-.6M20.1 16.1l1-.6"/>
"""

G["save"] = """
<path d="M18.8 20.8H5.2a2 2 0 0 1-2-2V5.2a2 2 0 0 1 2-2h10.4l5.2 5.2v10.4a2 2 0 0 1-2 2z"/>
<path d="M16.6 20.8v-7.4H7.4v7.4"/>
<path d="M7.4 3.2v4.6h6.8"/>
"""

G["save-as"] = """
<path d="M15.6 18.6H4.6a1.8 1.8 0 0 1-1.8-1.8V4.4a1.8 1.8 0 0 1 1.8-1.8h9.2l4.6 4.6v3"/>
<path d="M14 18.6v-6.4H6.2v6.4"/>
<path d="M6.2 2.6v4h6"/>
<path d="M20.4 13.6l1.8 1.8-6 6-2.4.6.6-2.4z"/>
"""

ORDER = ["zoom", "sortacross", "showempty", "swapaxes", "displayoptions", "chart-new",
         "chartoptions", "cube-new", "mdxquery", "excel-new", "print-new", "outputopts",
         "save", "save-as"]
assert set(ORDER) == set(G), set(ORDER) ^ set(G)


def svg(name, down):
    body = G[name].strip()
    if down:
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
            f'<rect x="0.5" y="0.5" width="23" height="23" rx="5" fill="{DOWN_BG}"/>'
            f'<g transform="translate(1.68 1.68) scale(0.86)" fill="none" stroke="{DOWN_STROKE}"'
            ' stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'
            f'{body}</g></svg>')
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">'
        f'<g fill="none" stroke="{UP_STROKE}" stroke-width="1.8" stroke-linecap="round"'
        f' stroke-linejoin="round">{body}</g></svg>')


CELLS = [(n, s) for n in ORDER for s in ("up", "down")]
COLS = 7
ROWS = (len(CELLS) + COLS - 1) // COLS

cells_html = "".join(
    f'<i>{svg(n, s == "down")}</i>' for n, s in CELLS)
html = f"""<!doctype html><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;background:transparent}}
#g{{display:grid;grid-template-columns:repeat({COLS},{CELL}px);width:{COLS*CELL}px}}
i{{display:block;width:{CELL}px;height:{CELL}px;line-height:0}}
i>svg{{width:{CELL}px;height:{CELL}px;display:block}}
</style><div id="g">{cells_html}</div>"""

sheet = os.path.join(OUT, "sheet.html")
open(sheet, "w").write(html)

png = os.path.join(OUT, "sheet.png")
chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                "--default-background-color=00000000",
                f"--window-size={COLS*CELL},{ROWS*CELL}",
                f"--screenshot={png}", f"--user-data-dir={OUT}/chrome-profile",
                "file://" + sheet], check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

from PIL import Image
sheet_img = Image.open(png).convert("RGBA")
print("sheet:", sheet_img.size, "alpha pojok:", sheet_img.getpixel((2, 2)))
dest = os.path.abspath(WEBAPP)
os.makedirs(dest, exist_ok=True)
for idx, (name, state) in enumerate(CELLS):
    c, r = idx % COLS, idx // COLS
    tile = sheet_img.crop((c * CELL, r * CELL, (c + 1) * CELL, (r + 1) * CELL))
    tile.save(os.path.join(dest, f"{name}-{state}.png"))
print("ditulis:", len(CELLS), "berkas ->", dest)
