#!/usr/bin/env python3
"""
Compares a published release with a rebuild of its tag, as Markdown.

    python3 .github/scripts/compare-release.py \
        --release-apk released.apk --rebuilt-apk rebuilt.apk \
        --release-sbom released.cdx.json --rebuilt-sbom rebuilt.cdx.json

The released APK is signed and the rebuild is not, so the two are compared
entry by entry (name, size and CRC-32 of the content) and the signature is
left out: the APK Signing Block sits outside the entries, and a v1 signature's
files (META-INF/MANIFEST.MF, *.SF, *.RSA, *.DSA, *.EC) are skipped. The two
CycloneDX SBOMs are compared by their components (coordinates and hashes);
their serial number and timestamp differ on every build and are ignored.

Exits 0 when both match, 1 when either differs, 2 on a file it can't read.
The Markdown goes to stdout, for the run summary and the issue.
"""
import argparse
import json
import re
import sys
import zipfile

# A v1 signature's files; a v2+ signature isn't an entry at all.
SIGNATURE_ENTRY = re.compile(r"^META-INF/(?:[^/]+\.(?:SF|RSA|DSA|EC)|MANIFEST\.MF)$")

# How many names a section lists before saying how many more there are.
LISTED = 25


def apk_entries(path):
    """Every entry of the APK that isn't a signature file: name -> (size, crc)."""
    with zipfile.ZipFile(path) as apk:
        return {
            info.filename: (info.file_size, info.CRC)
            for info in apk.infolist()
            if not info.is_dir() and not SIGNATURE_ENTRY.match(info.filename)
        }


def components(bom):
    """A CycloneDX document's components, nested ones too: coordinates -> (version, hashes)."""
    found = {}

    def walk(items):
        for item in items:
            key = item.get("purl") or "{}/{}@{}".format(item.get("group", ""), item.get("name", ""), item.get("version", ""))
            found[key] = (item.get("version"), frozenset((h.get("alg"), h.get("content")) for h in item.get("hashes", [])))
            walk(item.get("components", []))

    walk(bom.get("components", []))
    return found


def sbom_components(path):
    with open(path, encoding="utf-8") as file:
        return components(json.load(file))


def listed(names):
    names = sorted(names)
    lines = ["- `{}`".format(name) for name in names[:LISTED]]
    if len(names) > LISTED:
        lines.append("- … and {} more".format(len(names) - LISTED))
    return lines


def compare(released, rebuilt, what):
    """(markdown lines, whether they match) for two name -> value maps."""
    only_released = released.keys() - rebuilt.keys()
    only_rebuilt = rebuilt.keys() - released.keys()
    changed = {name for name in released.keys() & rebuilt.keys() if released[name] != rebuilt[name]}
    lines = []
    for title, names in (
        ("only in the release", only_released),
        ("only in the rebuild", only_rebuilt),
        ("different content", changed),
    ):
        if names:
            lines += ["", "**{} {}** ({}):".format(what, title, len(names))] + listed(names)
    return lines, not (only_released or only_rebuilt or changed)


def report(args):
    lines = ["## Release reproducibility", ""]
    matches = True

    released, rebuilt = apk_entries(args.release_apk), apk_entries(args.rebuilt_apk)
    found, same = compare(released, rebuilt, "APK entries")
    matches &= same
    lines.append("- APK: {} ({} entries compared, signature left out)".format("identical" if same else "**differs**", len(released)))
    detail = found

    released_sbom, rebuilt_sbom = sbom_components(args.release_sbom), sbom_components(args.rebuilt_sbom)
    found, same = compare(released_sbom, rebuilt_sbom, "SBOM components")
    matches &= same
    lines.append("- SBOM: {} ({} components compared)".format("identical" if same else "**differs**", len(released_sbom)))
    return lines + detail + found, matches


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    for name in ("release-apk", "rebuilt-apk", "release-sbom", "rebuilt-sbom"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args(argv)
    try:
        lines, matches = report(args)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print("Can't read the files to compare: {}".format(error), file=sys.stderr)
        return 2
    print("\n".join(lines))
    return 0 if matches else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
