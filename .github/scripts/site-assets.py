#!/usr/bin/env python3
"""
Keeps the website's pages (docs/*.html) and the files they load in step.

GitHub Pages, and the CDN in front of the site, let browsers keep a
stylesheet, script or image for hours but a page for minutes, so after a
deploy a visitor can get the new page with the old stylesheet. Every local
file a page or the stylesheet loads is therefore asked for as
"name?v=<its content hash>": a changed file is a new address, fetched
fresh, and an unchanged one stays cached.

    python3 .github/scripts/site-assets.py stamp docs   # rewrite the ?v= values
    python3 .github/scripts/site-assets.py check docs   # fail when one is stale

check also fails when a page's inline script isn't allowed by the hash in
its Content-Security-Policy (the browser would refuse to run it).
"""
import base64
import hashlib
import pathlib
import re
import sys

VERSION_LENGTH = 10
STYLESHEET = "style.css"
# A local file reference: href="…" / src="…" in a page, url("…") in the stylesheet.
# Not a URL with a scheme, an in-page #anchor or a mailto:, and not a link to
# another page (a page is fetched fresh anyway, and pages linking each other
# would never settle on a version).
REFERENCE = re.compile(
    r'(?P<before>(?:href|src)="|url\(")(?P<path>(?![a-z][a-z0-9+.-]*:|#|/)[^"?#]+\.(?!html\b)[A-Za-z0-9]+)(?:\?v=[0-9a-f]*)?(?P<after>[#"])'
)
INLINE_SCRIPT = re.compile(r"<script>(?P<body>.*?)</script>", re.DOTALL)
CSP = re.compile(r'<meta http-equiv="Content-Security-Policy" content="(?P<policy>[^"]*)"')


def version(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:VERSION_LENGTH]


def stamped(text: str, base: pathlib.Path, missing: list[str]) -> str:
    def replace(match: re.Match) -> str:
        target = base / match["path"]
        if not target.is_file():
            missing.append(f"{match['path']} (from {base.name})")
            return match[0]
        return f"{match['before']}{match['path']}?v={version(target)}{match['after']}"

    return REFERENCE.sub(replace, text)


def files_in_order(site: pathlib.Path) -> list[pathlib.Path]:
    # The stylesheet first: its own version depends on the fonts it names.
    return [site / STYLESHEET] + sorted(site.glob("*.html"))


def csp_problems(page: pathlib.Path, text: str) -> list[str]:
    policy = CSP.search(text)
    scripts = [m["body"] for m in INLINE_SCRIPT.finditer(text)]
    if not scripts:
        return []
    if not policy:
        return [f"{page.name}: inline script but no Content-Security-Policy"]
    allowed = set(re.findall(r"'sha256-([A-Za-z0-9+/=]+)'", policy["policy"]))
    problems = []
    for body in scripts:
        digest = base64.b64encode(hashlib.sha256(body.encode()).digest()).decode()
        if digest not in allowed:
            problems.append(f"{page.name}: inline script needs 'sha256-{digest}' in script-src")
    return problems


def main(command: str, site_dir: str) -> None:
    site = pathlib.Path(site_dir)
    stale, missing, csp = [], [], []
    for path in files_in_order(site):
        text = path.read_text()
        updated = stamped(text, site, missing)
        if updated != text:
            if command == "stamp":
                path.write_text(updated)
            else:
                stale.append(path.name)
        if path.suffix == ".html":
            csp += csp_problems(path, updated)
    for name in missing:
        print(f"::error::{name}: no such file")
    for name in stale:
        print(f"::error file=docs/{name}::asset versions out of date; run: python3 .github/scripts/site-assets.py stamp docs")
    for problem in csp:
        print(f"::error::{problem}")
    if missing or stale or csp:
        sys.exit(1)
    print("Site assets versioned" + (" (updated)" if command == "stamp" else "") + "; inline scripts allowed by the CSP.")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("stamp", "check"):
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
