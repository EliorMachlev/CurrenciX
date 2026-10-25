#!/usr/bin/env bash
# Creates the keys for Android 17's post-quantum APK signature (APK Signature
# Scheme v3.2) and the lineages that tie them to the app's release key.
# Run once, on your own machine, then keep every file it writes (and the
# passwords) as safe as the release key itself: an update signed without them
# won't install over an app signed with them.
#
#   scripts/create-hybrid-signing-keys.sh <release.jks> <release-key-alias> [out-dir]
#
# <release.jks> is the app's existing release keystore (KEYSTORE_FILE). If it
# doesn't exist yet it is created first, as an EC P-521 key. out-dir defaults
# to ./signing. Needs JDK 24+ (keytool with ML-DSA) and Android build-tools
# 37.0.0+ (apksigner with --hybrid-signer-role), found through ANDROID_HOME.
#
# Afterwards add the lines it prints to secrets.properties; see
# docs/markDown/engineering/release.md, "Post-quantum signing".
set -euo pipefail

if [[ $# -lt 2 ]]; then
    sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
fi

release_ks=$1
release_alias=$2
out=${3:-signing}
validity_days=10000
hybrid_ks="$out/hybrid.p12"

java_major=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')
if (( java_major < 24 )); then
    echo "Needs JDK 24 or later for ML-DSA keys; java is $java_major." >&2
    exit 1
fi
build_tools=$(ls -d "${ANDROID_HOME:?set ANDROID_HOME}"/build-tools/37.* 2>/dev/null | sort -V | tail -1)
apksigner="${build_tools:?build-tools 37.0.0 or later is needed}/apksigner"

mkdir -p "$out"
if [[ -e "$hybrid_ks" ]]; then
    echo "$hybrid_ks already exists; not overwriting keys." >&2
    exit 1
fi

read -r -p "Name for the certificates (e.g. your name): " cn
dname="CN=$cn"

if [[ ! -e "$release_ks" ]]; then
    echo "== Creating the release key ($release_ks, alias $release_alias)"
    keytool -genkeypair -keyalg EC -groupname secp521r1 -alias "$release_alias" \
        -validity "$validity_days" -dname "$dname" -keystore "$release_ks"
fi

# The hybrid pair: a new classical key (the release key may not be reused)
# and an ML-DSA-65 key (ML-DSA-44 isn't accepted for APKs). Same keystore,
# same password.
echo "== Creating the hybrid keys ($hybrid_ks); choose its password"
keytool -genkeypair -keyalg EC -groupname secp521r1 -alias hybrid-classical \
    -validity "$validity_days" -dname "$dname" -keystore "$hybrid_ks" -storetype PKCS12
keytool -genkeypair -keyalg ML-DSA -groupname ML-DSA-65 -alias hybrid-pqc \
    -validity "$validity_days" -dname "$dname" -keystore "$hybrid_ks" -storetype PKCS12

# Each hybrid key rotates from the release key, without rollback: an update
# signed only with the release key won't install over a hybrid-signed app on
# Android 17+, which is what makes the new signature count.
for role in classical pqc; do
    echo "== Lineage: release key -> hybrid-$role"
    "$apksigner" rotate --out "$out/$role-lineage" \
        --old-signer --ks "$release_ks" --ks-key-alias "$release_alias" --set-rollback false \
        --new-signer --ks "$hybrid_ks" --ks-key-alias "hybrid-$role"
done

cat <<EOF

Done. Add to secrets.properties (next to the KEYSTORE_* lines):

HYBRID_KEYSTORE_FILE=$(realpath "$hybrid_ks")
HYBRID_KEYSTORE_PASSWORD=<the password you chose for $hybrid_ks>
HYBRID_CLASSICAL_LINEAGE=$(realpath "$out/classical-lineage")
HYBRID_PQC_LINEAGE=$(realpath "$out/pqc-lineage")

Back up $out/ and the release keystore together.
EOF
