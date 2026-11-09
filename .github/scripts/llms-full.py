#!/usr/bin/env python3
"""
Builds docs/llms-full.txt, the whole site and the main reference documents as
one plain-text file, for AI assistants that read a single file instead of
following links (the llms.txt convention; docs/llms.txt is the short index).

    python3 .github/scripts/llms-full.py build docs   # write docs/llms-full.txt
    python3 .github/scripts/llms-full.py check docs   # fail when it is stale

The Site workflow runs check, so a page or document changed without rebuilding
the file fails the pull request.
"""
import pathlib
import sys
from html.parser import HTMLParser

OUTPUT = "llms-full.txt"
INDEX = "llms.txt"
PAGES = ("index.html", "features.html", "providers.html", "privacy.html")
DOCUMENTS = ("docs/markDown/product/providers.md", "SECURITY.md")
SKIPPED_TAGS = {"script", "style", "head", "nav", "footer", "svg", "noscript"}
BLOCK_TAGS = {"p", "li", "h1", "h2", "h3", "h4", "tr", "section", "div", "br", "dt", "dd", "details", "summary"}
HEADING_TAGS = {"h1": "# ", "h2": "## ", "h3": "### ", "h4": "#### "}


class PageText(HTMLParser):
    """The visible text of a page, one block per line, headings marked."""

    def __init__(self) -> None:
        super().__init__()
        self.lines: list[str] = [""]
        self.skip_depth = 0
        self.heading = ""

    def handle_starttag(self, tag, attrs):
        if tag in SKIPPED_TAGS:
            self.skip_depth += 1
        elif self.skip_depth == 0:
            if tag in BLOCK_TAGS:
                self.lines.append("")
            self.heading = HEADING_TAGS.get(tag, self.heading)
            if tag in HEADING_TAGS:
                self.lines[-1] = HEADING_TAGS[tag]
            elif tag == "li":
                self.lines[-1] = "- "

    def handle_endtag(self, tag):
        if tag in SKIPPED_TAGS:
            self.skip_depth = max(0, self.skip_depth - 1)
        elif self.skip_depth == 0 and tag in BLOCK_TAGS:
            self.lines.append("")

    def handle_data(self, data):
        if self.skip_depth == 0:
            self.lines[-1] += " ".join(data.split()) + " "

    def text(self) -> str:
        return "\n".join(line.strip() for line in self.lines if line.strip())


def page_text(path: pathlib.Path) -> str:
    parser = PageText()
    parser.feed(path.read_text(encoding="utf-8"))
    return parser.text()


def build(docs: pathlib.Path) -> str:
    root = docs.parent
    sections = [(docs / INDEX).read_text(encoding="utf-8").strip()]
    for page in PAGES:
        sections.append(f"<!-- {page} -->\n{page_text(docs / page)}")
    for document in DOCUMENTS:
        sections.append(f"<!-- {document} -->\n{(root / document).read_text(encoding='utf-8').strip()}")
    return "\n\n---\n\n".join(sections) + "\n"


def main(argv: list[str]) -> int:
    if len(argv) != 3 or argv[1] not in ("build", "check"):
        print(__doc__)
        return 2
    docs = pathlib.Path(argv[2])
    target = docs / OUTPUT
    content = build(docs)
    if argv[1] == "build":
        target.write_text(content, encoding="utf-8")
        print(f"Wrote {target} ({len(content)} bytes).")
        return 0
    if not target.exists() or target.read_text(encoding="utf-8") != content:
        print(f"{target} is stale. Run: python3 .github/scripts/llms-full.py build {docs}")
        return 1
    print(f"{target} is current.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
