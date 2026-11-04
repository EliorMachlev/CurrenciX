package com.eliormachlev.currencix.util

// The public keys of the certificate authorities each host's certificate
// chain runs through: its intermediates and their root, never the server's
// own certificate (that changes at every renewal). A connection is accepted
// when its verified chain holds one of them (OkHttp's CertificatePinner).
// Written by .github/scripts/certificate-pins.py ("Update certificate pins"
// workflow, which also checks them weekly): add a new host by hand with no
// pins, then run the workflow to fill it in.

// Amazon Root CA 1
private const val AMAZON_ROOT_CA_1 = "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="

// Amazon RSA 2048 S25
private const val AMAZON_RSA_2048_S25 = "sha256/bpALxrV+gOA/h4UBkxB+p10bX3e1Zs9TRoC0NykxRoQ="

// DigiCert Global G2 TLS RSA SHA256 2020 CA1 (DigiCert Inc)
private const val DIGICERT_GLOBAL_G2_TLS_RSA_SHA256_2020_CA1 = "sha256/Wec45nQiFwKvHtuHxSAMGkt19k+uPSw9JlEkxhvYPHk="

// DigiCert Global Root G2 (DigiCert Inc)
private const val DIGICERT_GLOBAL_ROOT_G2 = "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY="

// GlobalSign Atlas R46 OV TLS CA 2026 Q3 (GlobalSign nv-sa)
private const val GLOBALSIGN_ATLAS_R46_OV_TLS_CA_2026_Q3 = "sha256/lNoiizlhI3Ewli3DibA3cBj6ca20tHD06z+V8+VjZU8="

// GlobalSign Root R46 (GlobalSign nv-sa)
private const val GLOBALSIGN_ROOT_R46 = "sha256/rn+WLLnmp9v3uDP7GPqbcaiRdd+UnCMrap73yz3yu/w="

// GTS Root R4 (Google Trust Services LLC)
private const val GTS_ROOT_R4 = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

// HARICA DV TLS RSA (Hellenic Academic and Research Institutions CA)
private const val HARICA_DV_TLS_RSA = "sha256/rk2I2etvSahZka3FPKyvABNS0NJaDCduKDeQ5Vvg/Xs="

// HARICA TLS RSA Root CA 2021 (Hellenic Academic and Research Institutions CA)
private const val HARICA_TLS_RSA_ROOT_CA_2021 = "sha256/aTyaprJFs7AmFjd1CGPq22wkihblLW9LyQyGu/MtcEI="

// ISRG Root X1 (Internet Security Research Group)
private const val ISRG_ROOT_X1 = "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="

// Root YR (ISRG)
private const val ROOT_YR = "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="

// Sectigo Public Server Authentication CA OV E36 (Sectigo Limited)
private const val SECTIGO_PUBLIC_SERVER_AUTHENTICATION_CA_OV_E36 = "sha256/C0ruG6pqJqe+UDfJO02OWmh2qaGi8SKyJkfeZdqg0Nc="

// Sectigo Public Server Authentication CA OV R36 (Sectigo Limited)
private const val SECTIGO_PUBLIC_SERVER_AUTHENTICATION_CA_OV_R36 = "sha256/KqkYYX5LYAYP7XGemqzbtPPIA8x7BS/BbOIcAXf3j2k="

// Sectigo Public Server Authentication Root E46 (Sectigo Limited)
private const val SECTIGO_PUBLIC_SERVER_AUTHENTICATION_ROOT_E46 = "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE="

// Sectigo Public Server Authentication Root R46 (Sectigo Limited)
private const val SECTIGO_PUBLIC_SERVER_AUTHENTICATION_ROOT_R46 = "sha256/Douxi77vs4G+Ib/BogbTFymEYq0QSFXwSgVCaZcI09Q="

// Starfield Services Root Certificate Authority - G2 (Starfield Technologies, Inc.)
private const val STARFIELD_SERVICES_ROOT_CERTIFICATE_AUTHORITY_G2 = "sha256/KwccWaCgrnaw6tsrrSO61FgLacNgG2MMLq8GE6+oP5I="

// USERTrust ECC Certification Authority (The USERTRUST Network)
private const val USERTRUST_ECC_CERTIFICATION_AUTHORITY = "sha256/ICGRfpgmOUXIWcQ/HXPLQTkFPEFPoDyjvH7ohhQpjzs="

// WE1 (Google Trust Services)
private const val WE1 = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="

// YR1 (Let's Encrypt)
private const val YR1 = "sha256/LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w="

internal val BUILT_IN_PINS: Map<String, Set<String>> =
    mapOf(
        "allratestoday.com" to
            setOf(
                GTS_ROOT_R4,
                WE1,
            ),
        "api.frankfurter.dev" to
            setOf(
                GTS_ROOT_R4,
                WE1,
            ),
        "boi.org.il" to
            setOf(
                DIGICERT_GLOBAL_G2_TLS_RSA_SHA256_2020_CA1,
                DIGICERT_GLOBAL_ROOT_G2,
            ),
        "data.norges-bank.no" to
            setOf(
                GTS_ROOT_R4,
                WE1,
            ),
        "ec.europa.eu" to
            setOf(
                GLOBALSIGN_ATLAS_R46_OV_TLS_CA_2026_Q3,
                GLOBALSIGN_ROOT_R46,
            ),
        "edge.boi.gov.il" to
            setOf(
                SECTIGO_PUBLIC_SERVER_AUTHENTICATION_CA_OV_R36,
                SECTIGO_PUBLIC_SERVER_AUTHENTICATION_ROOT_R46,
            ),
        "openexchangerates.org" to
            setOf(
                AMAZON_ROOT_CA_1,
                AMAZON_RSA_2048_S25,
                STARFIELD_SERVICES_ROOT_CERTIFICATE_AUTHORITY_G2,
            ),
        "raw.githubusercontent.com" to
            setOf(
                ISRG_ROOT_X1,
                ROOT_YR,
                YR1,
            ),
        "www.bankofcanada.ca" to
            setOf(
                SECTIGO_PUBLIC_SERVER_AUTHENTICATION_CA_OV_E36,
                SECTIGO_PUBLIC_SERVER_AUTHENTICATION_ROOT_E46,
                USERTRUST_ECC_CERTIFICATION_AUTHORITY,
            ),
        "www.cbr.ru" to
            setOf(
                HARICA_DV_TLS_RSA,
                HARICA_TLS_RSA_ROOT_CA_2021,
            ),
    )
