#!/usr/bin/env python3
"""
Keeps the app's built-in certificate pins (app/src/main/kotlin/.../util/
BuiltInPins.kt) in line with what each host serves. For every host listed
there it connects (openssl s_client, verifying against the runner's trust
store), takes the CA certificates of the chain (the intermediates the server
sends and the root they lead to; never the server's own certificate, which
changes at every renewal) and pins their public keys, in OkHttp's
"sha256/<base64 SPKI hash>" form.

    python3 .github/scripts/certificate-pins.py check  <BuiltInPins.kt>
    python3 .github/scripts/certificate-pins.py update <BuiltInPins.kt>

check fails when a host's chain shares no key with its pins (the app would
refuse that host), and only notes a chain that changed but still matches.
update rewrites the pins from the chains served now. A new host is added to
the file by hand with no pins (`"host" to setOf(),`) and filled by update.
Run on a network without TLS interception: pins taken behind a proxy that
re-signs traffic would be the proxy's.
"""
import base64
import hashlib
import pathlib
import re
import subprocess
import sys
import tempfile

PORT = 443
TIMEOUT_SECONDS = 30
MAX_LABEL = 100
TRUST_STORE = pathlib.Path("/etc/ssl/certs")
PEM = re.compile(r"-----BEGIN CERTIFICATE-----.+?-----END CERTIFICATE-----", re.DOTALL)
CONSTANT = re.compile(r'^private const val ([A-Z0-9_]+) = "(sha256/[A-Za-z0-9+/=]+)"', re.MULTILINE)
HOST_ENTRY = re.compile(r'^\s*"(?P<host>[a-z0-9.-]+)" to', re.MULTILINE)
PINS_START = "    mapOf(\n"
HEADER_COMMENT = """package com.eliormachlev.currencix.util

// The public keys of the certificate authorities each host's certificate
// chain runs through: its intermediates and their root, never the server's
// own certificate (that changes at every renewal). A connection is accepted
// when its verified chain holds one of them (OkHttp's CertificatePinner).
// Written by .github/scripts/certificate-pins.py ("Update certificate pins"
// workflow, which also checks them weekly): add a new host by hand with no
// pins, then run the workflow to fill it in.
"""
DECLARATION = "internal val BUILT_IN_PINS: Map<String, Set<String>> =\n"


def openssl(*args: str, data: bytes | None = None) -> bytes:
    return subprocess.run(["openssl", *args], input=data, capture_output=True, check=True, timeout=TIMEOUT_SECONDS).stdout


def served_chain(host: str) -> list[str]:
    # The certificates the server sends, in order (its own first). -verify_return_error
    # makes the handshake fail unless the chain verifies against the trust store.
    out = subprocess.run(
        ["openssl", "s_client", "-connect", f"{host}:{PORT}", "-servername", host, "-showcerts",
         "-verify_return_error", "-verify_hostname", host, "-CApath", str(TRUST_STORE)],
        input=b"", capture_output=True, timeout=TIMEOUT_SECONDS,
    )
    if out.returncode != 0:
        sys.exit(f"{host}: the TLS handshake failed or the chain didn't verify:\n{out.stderr.decode(errors='replace')}")
    return PEM.findall(out.stdout.decode())


def subject(pem: str) -> str:
    # The certificate's name and organization, "ISRG Root X1 (Internet Security Research Group)",
    # kept short enough for a comment line.
    fields = {}
    for line in openssl("x509", "-noout", "-subject", "-nameopt", "multiline", data=pem.encode()).decode().splitlines()[1:]:
        key, _, value = line.partition("=")
        fields.setdefault(key.strip(), value.strip())
    name = fields.get("commonName") or fields.get("organizationalUnitName", "?")
    organization = fields.get("organizationName")
    label = f"{name} ({organization})" if organization and organization not in name else name
    return label if len(label) <= MAX_LABEL else label[: MAX_LABEL - 1] + "…"


def is_self_signed(pem: str) -> bool:
    names = openssl("x509", "-noout", "-subject", "-issuer", "-nameopt", "RFC2253", data=pem.encode()).decode().splitlines()
    return names[0].split("=", 1)[1] == names[1].split("=", 1)[1]


def issuing_root(pem: str) -> str | None:
    # The trust store's certificate that signed [pem]: the root the chain ends at.
    issuer_hash = openssl("x509", "-noout", "-issuer_hash", data=pem.encode()).decode().strip()
    with tempfile.NamedTemporaryFile("w", suffix=".pem") as cert:
        cert.write(pem)
        cert.flush()
        for candidate in sorted(TRUST_STORE.glob(f"{issuer_hash}.*")):
            verified = subprocess.run(
                ["openssl", "verify", "-no-CApath", "-no-CAfile", "-partial_chain", "-trusted", str(candidate), cert.name],
                capture_output=True, timeout=TIMEOUT_SECONDS,
            )
            if verified.returncode == 0:
                return candidate.read_text()
    return None


def spki_pin(pem: str) -> str:
    public_key = openssl("x509", "-noout", "-pubkey", data=pem.encode())
    der = openssl("pkey", "-pubin", "-outform", "DER", data=public_key)
    return "sha256/" + base64.b64encode(hashlib.sha256(der).digest()).decode()


def ca_pins(host: str) -> dict[str, str]:
    # Pin -> the certificate's subject, for every CA certificate of the chain.
    chain = served_chain(host)
    authorities = chain[1:]
    last = chain[-1]
    if not is_self_signed(last):
        root = issuing_root(last)
        if root is None:
            print(f"::warning::{host}: its root isn't in the runner's trust store; pinning the intermediates only")
        else:
            authorities.append(root)
    if not authorities:
        sys.exit(f"{host}: the server sent no CA certificates and no root was found")
    return {spki_pin(pem): subject(pem) for pem in authorities}


def hosts_in(path: pathlib.Path) -> list[str]:
    hosts = HOST_ENTRY.findall(path.read_text())
    if not hosts:
        sys.exit(f"{path}: no hosts")
    return hosts


def pins_in(path: pathlib.Path, host: str) -> set[str]:
    # The pins named between this host's entry and the next one, through their constants.
    text = path.read_text()
    constants = dict(CONSTANT.findall(text))
    entries = list(HOST_ENTRY.finditer(text))
    index = next(i for i, entry in enumerate(entries) if entry["host"] == host)
    end = entries[index + 1].start() if index + 1 < len(entries) else len(text)
    block = text[entries[index].end():end]
    return {constants[name] for name in re.findall(r"\b([A-Z][A-Z0-9_]+),", block) if name in constants}


def constant_name(label: str) -> str:
    # "GTS Root R4 (Google Trust Services LLC)" -> GTS_ROOT_R4
    return re.sub(r"[^A-Z0-9]+", "_", label.split(" (")[0].upper()).strip("_")


def kotlin(pins: dict[str, dict[str, str]]) -> str:
    # One constant per CA key, named after the CA, then each host's set of them:
    # a CA several hosts use (GTS, Let's Encrypt) is written once.
    names: dict[str, str] = {}
    for pin, label in sorted({pin: label for host in pins.values() for pin, label in host.items()}.items(), key=lambda i: i[1]):
        name = constant_name(label)
        while name in names.values():
            name += "_2"
        names[pin] = name
    constants = "".join(f'// {label}\nprivate const val {names[pin]} = "{pin}"\n\n' for pin, label in
                        sorted({p: l for h in pins.values() for p, l in h.items()}.items(), key=lambda i: names[i[0]]))
    entries = []
    for host in sorted(pins):
        lines = "".join(f"                {names[pin]},\n" for pin in sorted(pins[host], key=lambda p: names[p]))
        entries.append(f'        "{host}" to\n            setOf(\n{lines}            ),\n')
    return HEADER_COMMENT + "\n" + constants + DECLARATION + PINS_START + "".join(entries) + "    )\n"


def main(mode: str, file: str) -> None:
    path = pathlib.Path(file)
    current = {host: ca_pins(host) for host in hosts_in(path)}
    if mode == "update":
        path.write_text(kotlin(current))
        print(f"Pinned {sum(map(len, current.values()))} keys for {len(current)} hosts")
        return
    broken = []
    for host, served in current.items():
        pinned = pins_in(path, host)
        if not pinned & served.keys():
            broken.append(host)
            print(f"::error file={file}::{host}: none of its pins is in the chain it serves now ({', '.join(served.values())})")
        elif pinned != served.keys():
            print(f"::notice file={file}::{host}: its chain changed but still holds a pinned key; update the pins to follow it")
    if broken:
        sys.exit("The app would refuse these hosts: run the Update certificate pins workflow with update, and release")
    print(f"All {len(current)} hosts serve a pinned key")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("check", "update"):
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
