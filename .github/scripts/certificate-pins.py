#!/usr/bin/env python3
"""
Keeps the app's certificate pins, the <pin-set>s in app/src/main/res/xml/
network_security_config.xml, in line with what each host serves. For every
host listed there it connects (openssl s_client, verifying against the
runner's trust store), takes the CA certificates of the chain (the
intermediates the server sends and the root they lead to; never the server's
own certificate, which changes at every renewal) and pins their public keys
(the base64 SHA-256 of each SubjectPublicKeyInfo, as <pin digest="SHA-256">
takes it).

    python3 .github/scripts/certificate-pins.py check  <network_security_config.xml>
    python3 .github/scripts/certificate-pins.py update <network_security_config.xml>
    python3 .github/scripts/certificate-pins.py expiry <network_security_config.xml>

check fails when a host's chain shares no key with its pins (the app would
refuse that host) or when the pins expire within EXPIRY_WARNING_DAYS (after
expiry Android stops checking them), and only notes a chain that changed but
still matches. expiry checks only the date, without connecting (the release
workflow runs it). update rewrites the pins from the chains served now and sets
their expiry EXPIRY_DAYS ahead. Only the part between the two PINS markers
is written; the rest of the file is kept as it is. A new host is added by
hand between the markers with no pins, as
`<domain-config><domain includeSubdomains="true">host</domain></domain-config>`,
and filled by update. Run on a network without TLS interception: pins taken
behind a proxy that re-signs traffic would be the proxy's.
"""
import base64
import datetime
import hashlib
import pathlib
import re
import subprocess
import sys
import tempfile

PORT = 443
TIMEOUT_SECONDS = 30
MAX_LABEL = 100
# A year of pins per update, and the weekly check asks for the next update
# with three months to go, so a release carrying it reaches users in time.
EXPIRY_DAYS = 365
EXPIRY_WARNING_DAYS = 90
TRUST_STORE = pathlib.Path("/etc/ssl/certs")
PEM = re.compile(r"-----BEGIN CERTIFICATE-----.+?-----END CERTIFICATE-----", re.DOTALL)
START = "<!-- PINS: written by .github/scripts/certificate-pins.py; edit by hand only to add a host. -->"
END = "<!-- END OF PINS -->"
HOST = re.compile(r'<domain includeSubdomains="true">([a-z0-9.-]+)</domain>')
PIN = re.compile(r'<pin digest="SHA-256">([A-Za-z0-9+/=]+)</pin>')
EXPIRATION = re.compile(r'<pin-set expiration="(\d{4}-\d{2}-\d{2})">')
INDENT = "    "


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
    # "--" may not appear inside an XML comment.
    label = label.replace("--", "-")
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
    return base64.b64encode(hashlib.sha256(der).digest()).decode()


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


def pins_section(text: str, path: pathlib.Path) -> tuple[int, int]:
    # Where the written part starts and ends: just after START, at the line holding END.
    start, end = text.find(START), text.find(END)
    if start < 0 or end < start:
        sys.exit(f"{path}: no {START!r} … {END!r} section")
    return start + len(START), text.rfind("\n", 0, end) + 1


def domain_configs(section: str) -> list[str]:
    return re.findall(r"<domain-config>.*?</domain-config>", section, re.DOTALL)


def hosts_in(section: str, path: pathlib.Path) -> list[str]:
    hosts = [host for config in domain_configs(section) for host in HOST.findall(config)]
    if not hosts:
        sys.exit(f"{path}: no hosts between the PINS markers")
    return hosts


def pins_of(section: str, host: str) -> set[str]:
    config = next(c for c in domain_configs(section) if host in HOST.findall(c))
    return set(PIN.findall(config))


def xml(pins: dict[str, dict[str, str]], expiration: datetime.date) -> str:
    # One <domain-config> per host, its subdomains included, as the allowed hosts are
    # (AllowedHostsInterceptor), so a redirect to one doesn't go unpinned.
    blocks = []
    for host in sorted(pins):
        lines = [
            f"{INDENT}<domain-config>",
            f'{INDENT * 2}<domain includeSubdomains="true">{host}</domain>',
            f'{INDENT * 2}<pin-set expiration="{expiration.isoformat()}">',
        ]
        for pin, label in sorted(pins[host].items(), key=lambda item: item[1]):
            lines.append(f"{INDENT * 3}<!-- {label} -->")
            lines.append(f'{INDENT * 3}<pin digest="SHA-256">{pin}</pin>')
        lines += [f"{INDENT * 2}</pin-set>", f"{INDENT}</domain-config>"]
        blocks.append("\n".join(lines) + "\n")
    return "\n" + "".join(blocks)


def expires_soon(section: str, file: str, today: datetime.date) -> bool:
    # Every pin set carries the same date; the earliest decides.
    expirations = {datetime.date.fromisoformat(date) for date in EXPIRATION.findall(section)}
    soon = not expirations or min(expirations) - today < datetime.timedelta(days=EXPIRY_WARNING_DAYS)
    if soon:
        print(f"::error file={file}::the pins expire on {min(expirations, default='(no date)')}; after that Android doesn't check them")
    else:
        print(f"The pins hold until {min(expirations)}")
    return soon


def main(mode: str, file: str) -> None:
    path = pathlib.Path(file)
    text = path.read_text()
    start, end = pins_section(text, path)
    section = text[start:end]
    today = datetime.date.today()
    if mode == "expiry":
        if expires_soon(section, file, today):
            sys.exit("Run the Certificate pins workflow with update before releasing")
        return
    current = {host: ca_pins(host) for host in hosts_in(section, path)}
    if mode == "update":
        expiration = today + datetime.timedelta(days=EXPIRY_DAYS)
        path.write_text(text[:start] + xml(current, expiration) + text[end:])
        print(f"Pinned {sum(map(len, current.values()))} keys for {len(current)} hosts, until {expiration}")
        return
    broken = []
    for host, served in current.items():
        pinned = pins_of(section, host)
        if not pinned & served.keys():
            broken.append(host)
            print(f"::error file={file}::{host}: none of its pins is in the chain it serves now ({', '.join(served.values())})")
        elif pinned != served.keys():
            print(f"::notice file={file}::{host}: its chain changed but still holds a pinned key; update the pins to follow it")
    expiring = expires_soon(section, file, today)
    if broken or expiring:
        sys.exit("Run the Certificate pins workflow with update, and release the app with the new pins")
    print(f"All {len(current)} hosts serve a pinned key")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("check", "update", "expiry"):
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
