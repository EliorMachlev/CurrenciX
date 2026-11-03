package com.eliormachlev.currencix.util

// The public keys of the certificate authorities each host's certificate
// chain runs through: its intermediates and their root, never the server's
// own certificate (that changes at every renewal). A connection is accepted
// when its verified chain holds one of them (OkHttp's CertificatePinner).
// Written by .github/scripts/certificate-pins.py ("Update certificate pins"
// workflow, which also checks them weekly): add a new host by hand with no
// pins, then run the workflow to fill it in.
internal val BUILT_IN_PINS: Map<String, Set<String>> =
    mapOf(
        "allratestoday.com" to
            setOf(
                // GTS Root R4 (Google Trust Services LLC)
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
                // WE1 (Google Trust Services)
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            ),
        "api.frankfurter.dev" to
            setOf(
                // GTS Root R4 (Google Trust Services LLC)
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
                // WE1 (Google Trust Services)
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            ),
        "boi.org.il" to
            setOf(
                // DigiCert Global G2 TLS RSA SHA256 2020 CA1 (DigiCert Inc)
                "sha256/Wec45nQiFwKvHtuHxSAMGkt19k+uPSw9JlEkxhvYPHk=",
                // DigiCert Global Root G2 (DigiCert Inc)
                "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY=",
            ),
        "data.norges-bank.no" to
            setOf(
                // GTS Root R4 (Google Trust Services LLC)
                "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
                // WE1 (Google Trust Services)
                "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            ),
        "ec.europa.eu" to
            setOf(
                // GlobalSign Atlas R46 OV TLS CA 2026 Q3 (GlobalSign nv-sa)
                "sha256/lNoiizlhI3Ewli3DibA3cBj6ca20tHD06z+V8+VjZU8=",
                // GlobalSign Root R46 (GlobalSign nv-sa)
                "sha256/rn+WLLnmp9v3uDP7GPqbcaiRdd+UnCMrap73yz3yu/w=",
            ),
        "edge.boi.gov.il" to
            setOf(
                // Sectigo Public Server Authentication CA OV R36 (Sectigo Limited)
                "sha256/KqkYYX5LYAYP7XGemqzbtPPIA8x7BS/BbOIcAXf3j2k=",
                // Sectigo Public Server Authentication Root R46 (Sectigo Limited)
                "sha256/Douxi77vs4G+Ib/BogbTFymEYq0QSFXwSgVCaZcI09Q=",
            ),
        "openexchangerates.org" to
            setOf(
                // Amazon RSA 2048 S25
                "sha256/bpALxrV+gOA/h4UBkxB+p10bX3e1Zs9TRoC0NykxRoQ=",
                // Amazon Root CA 1
                "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=",
                // Starfield Services Root Certificate Authority - G2 (Starfield Technologies, Inc.)
                "sha256/KwccWaCgrnaw6tsrrSO61FgLacNgG2MMLq8GE6+oP5I=",
            ),
        "raw.githubusercontent.com" to
            setOf(
                // ISRG Root X1 (Internet Security Research Group)
                "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
                // Root YR (ISRG)
                "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=",
                // YR1 (Let's Encrypt)
                "sha256/LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=",
            ),
        "www.bankofcanada.ca" to
            setOf(
                // Sectigo Public Server Authentication CA OV E36 (Sectigo Limited)
                "sha256/C0ruG6pqJqe+UDfJO02OWmh2qaGi8SKyJkfeZdqg0Nc=",
                // Sectigo Public Server Authentication Root E46 (Sectigo Limited)
                "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=",
                // USERTrust ECC Certification Authority (The USERTRUST Network)
                "sha256/ICGRfpgmOUXIWcQ/HXPLQTkFPEFPoDyjvH7ohhQpjzs=",
            ),
        "www.cbr.ru" to
            setOf(
                // HARICA DV TLS RSA (Hellenic Academic and Research Institutions CA)
                "sha256/rk2I2etvSahZka3FPKyvABNS0NJaDCduKDeQ5Vvg/Xs=",
                // HARICA TLS RSA Root CA 2021 (Hellenic Academic and Research Institutions CA)
                "sha256/aTyaprJFs7AmFjd1CGPq22wkihblLW9LyQyGu/MtcEI=",
            ),
    )
