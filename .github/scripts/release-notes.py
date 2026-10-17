#!/usr/bin/env python3
"""
Writes a release's notes from the commits since the previous release, as
Markdown for the GitHub release.

    python3 .github/scripts/release-notes.py v1.24.0 > notes.md

master takes each pull request as one squashed commit titled in the
Conventional Commits style (contributing.md): "feat(fees): split the fee
amount (#169)". Those titles are grouped by type, user-facing ones only:
features, fixes, speed-ups, translations; chores, CI, refactors, tests and
docs are left out. Weblate's commits ("Translated using Weblate (Czech)")
become one line naming the languages. A title in no known style is listed
under "Other changes", so nothing user-facing goes missing.

The range starts at the previous v* tag reachable from this one; for the
first release, at the fork's rebrand to CurrenciX (FIRST_RELEASE_BASE),
since what came before was the upstream app's. Ends with the compare link
when $GITHUB_REPOSITORY is set.
"""
import os
import re
import subprocess
import sys

# "Fork rebrand to CurrenciX + wordmark, docs, prefs, and i18n polish (#89)":
# where CurrenciX's own history begins.
FIRST_RELEASE_BASE = "35965a8e"

# type(scope)(scope)!: description
CONVENTIONAL = re.compile(r"^(?P<type>[a-z0-9]+)(?:\([^)]*\))*(?P<breaking>!)?:\s*(?P<text>.+)$", re.IGNORECASE)
WEBLATE = re.compile(r"^Translated using Weblate \((?P<language>.+)\)$")

# Section titles, in the order they're shown.
SECTIONS = {
    "feat": "New",
    "fix": "Fixes",
    "perf": "Faster",
    "i18n": "Translations",
}
SKIPPED = {"chore", "ci", "build", "refactor", "test", "tests", "docs", "style", "revert", "deps"}
OTHER = "Other changes"


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def previous_tag(tag: str) -> str | None:
    """The newest v* tag before [tag] in its history, if any."""
    result = subprocess.run(
        ["git", "describe", "--tags", "--abbrev=0", "--match", "v*", f"{tag}^"],
        capture_output=True,
        text=True,
    )
    return result.stdout.strip() if result.returncode == 0 else None


def subjects(base: str, tag: str) -> list[str]:
    log = git("log", "--no-merges", "--format=%s", f"{base}..{tag}")
    return [line for line in log.splitlines() if line.strip()]


def sentence(text: str) -> str:
    text = text.strip().rstrip(".")
    return text[:1].upper() + text[1:]


def group(lines: list[str]) -> dict[str, list[str]]:
    """Commit titles by section title, oldest first within each."""
    sections: dict[str, list[str]] = {title: [] for title in [*SECTIONS.values(), OTHER]}
    languages: list[str] = []
    for subject in reversed(lines):
        if weblate := WEBLATE.match(subject):
            if weblate["language"] not in languages:
                languages.append(weblate["language"])
            continue
        if match := CONVENTIONAL.match(subject):
            kind = match["type"].lower()
            if kind in SKIPPED:
                continue
            title = SECTIONS.get(kind, OTHER)
            entry = sentence(match["text"]) + (" (breaking)" if match["breaking"] else "")
        else:
            title, entry = OTHER, sentence(subject)
        if entry not in sections[title]:
            sections[title].append(entry)
    if languages:
        sections[SECTIONS["i18n"]].append("Updated by Weblate's volunteers: " + ", ".join(sorted(languages)))
    return {title: entries for title, entries in sections.items() if entries}


def notes(tag: str) -> str:
    base = previous_tag(tag)
    sections = group(subjects(base or FIRST_RELEASE_BASE, tag))
    out: list[str] = []
    for title, entries in sections.items():
        out += [f"### {title}", "", *(f"- {entry}" for entry in entries), ""]
    if not sections:
        out += ["Maintenance only: no user-facing changes.", ""]
    repository = os.environ.get("GITHUB_REPOSITORY")
    if repository and base:
        out.append(f"**Full changelog:** https://github.com/{repository}/compare/{base}...{tag}")
    return "\n".join(out).rstrip() + "\n"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.stdout.write(notes(sys.argv[1]))
