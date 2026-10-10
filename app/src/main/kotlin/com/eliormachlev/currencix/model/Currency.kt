package com.eliormachlev.currencix.model

import android.content.Context
import androidx.annotation.DrawableRes
import com.eliormachlev.currencix.R
import com.squareup.moshi.JsonClass

// Unicode bidi controls used to force LTR display of an RTL currency symbol
// (embedding form: LRE + PDF). The isolate form (FSI + PDI) is theoretically
// nicer but not universally supported, so we stick with embedding.
private const val LTR_EMBEDDING = "\u202A"
private const val POP_DIRECTIONAL_FORMATTING = "\u202C"

// Constructor arg roles, in order:
//   iso4217Alpha    e.g. "USD"
//   symbol          e.g. "$"
//   fullName        string res, e.g. "US dollar"
//   flag            drawable res, e.g. star-spangled banner
@JsonClass(generateAdapter = false) // see https://stackoverflow.com/a/64085370/421140
enum class Currency(
    private val iso4217Alpha: String,
    private val symbol: String?,
    private val fullName: Int,
    private val flag: Int?,
) {
    AED("AED", "د.إ", R.string.name_aed, R.drawable.flag_ae),
    AFN("AFN", "؋", R.string.name_afn, R.drawable.flag_af),
    ALL("ALL", "L", R.string.name_all, R.drawable.flag_al),
    AMD("AMD", "֏", R.string.name_amd, R.drawable.flag_am),

    // Dutch flag: on 10 October 2010, the Netherlands Antilles was dissolved into Curaçao,
    // Sint Maarten and the three public bodies of the Caribbean Netherlands.
    ANG("ANG", "ƒ", R.string.name_ang, R.drawable.flag_nl),
    AOA("AOA", "Kz", R.string.name_aoa, R.drawable.flag_ao),
    ARS("ARS", "$", R.string.name_ars, R.drawable.flag_ar),
    AUD("AUD", "$", R.string.name_aud, R.drawable.flag_au),
    AWG("AWG", "Afl.", R.string.name_awg, R.drawable.flag_aw),
    AZN("AZN", "₼", R.string.name_azn, R.drawable.flag_az),
    BAM("BAM", "KM", R.string.name_bam, R.drawable.flag_ba),
    BBD("BBD", "$", R.string.name_bbd, R.drawable.flag_bb),
    BDT("BDT", "৳", R.string.name_bdt, R.drawable.flag_bd),
    BGN("BGN", "лв", R.string.name_bgn, R.drawable.flag_bg),
    BHD("BHD", ".د.ب", R.string.name_bhd, R.drawable.flag_bh),
    BIF("BIF", "Fr", R.string.name_bif, R.drawable.flag_bi),
    BMD("BMD", "$", R.string.name_bmd, R.drawable.flag_bm),
    BND("BND", "$", R.string.name_bnd, R.drawable.flag_bn),
    BOB("BOB", "Bs.", R.string.name_bob, R.drawable.flag_bo),
    BRL("BRL", "R$", R.string.name_brl, R.drawable.flag_br),
    BSD("BSD", "$", R.string.name_bsd, R.drawable.flag_bs),
    BTC("BTC", "₿", R.string.name_btc, null),
    BTN("BTN", "Nu.", R.string.name_btn, R.drawable.flag_bt),
    BWP("BWP", "P", R.string.name_bwp, R.drawable.flag_bw),
    BYN("BYN", "Br", R.string.name_byn, R.drawable.flag_by),
    BZD("BZD", "$", R.string.name_bzd, R.drawable.flag_bz),
    CAD("CAD", "$", R.string.name_cad, R.drawable.flag_ca),
    CDF("CDF", "Fr", R.string.name_cdf, R.drawable.flag_cd),
    CHF("CHF", "Fr.", R.string.name_chf, R.drawable.flag_ch),
    CLF("CLF", null, R.string.name_clf, R.drawable.flag_cl),
    CLP("CLP", "$", R.string.name_clp, R.drawable.flag_cl),
    CNH("CNH", "¥", R.string.name_cnh, R.drawable.flag_cn),
    CNY("CNY", "¥", R.string.name_cny, R.drawable.flag_cn),
    COP("COP", "$", R.string.name_cop, R.drawable.flag_co),
    CRC("CRC", "₡", R.string.name_crc, R.drawable.flag_cr),
    CUC("CUC", "$", R.string.name_cuc, R.drawable.flag_cu),
    CUP("CUP", "$", R.string.name_cup, R.drawable.flag_cu),
    CVE("CVE", "$", R.string.name_cve, R.drawable.flag_cv),
    CZK("CZK", "Kč", R.string.name_czk, R.drawable.flag_cz),
    DJF("DJF", "Fr", R.string.name_djf, R.drawable.flag_dj),
    DKK("DKK", "kr", R.string.name_dkk, R.drawable.flag_dk),
    DOP("DOP", "RD$", R.string.name_dop, R.drawable.flag_do),
    DZD("DZD", "د.ج", R.string.name_dzd, R.drawable.flag_dz),
    EGP("EGP", "ج.م", R.string.name_egp, R.drawable.flag_eg),
    ERN("ERN", "Nfk", R.string.name_ern, R.drawable.flag_er),
    ETB("ETB", "Br", R.string.name_etb, R.drawable.flag_et),
    EUR("EUR", "€", R.string.name_eur, R.drawable.flag_eu),
    FJD("FJD", "$", R.string.name_fjd, R.drawable.flag_fj),
    FKP("FKP", "£", R.string.name_fkp, R.drawable.flag_fk),
    FOK("FOK", "kr", R.string.name_fok, R.drawable.flag_fo),
    GBP("GBP", "£", R.string.name_gbp, R.drawable.flag_gb),
    GEL("GEL", "₾", R.string.name_gel, R.drawable.flag_ge),
    GGP("GGP", "£", R.string.name_ggp, R.drawable.flag_gg),
    GHS("GHS", "₵", R.string.name_ghs, R.drawable.flag_gh),
    GIP("GIP", "£", R.string.name_gip, R.drawable.flag_gi),
    GMD("GMD", "D", R.string.name_gmd, R.drawable.flag_gm),
    GNF("GNF", "Fr", R.string.name_gnf, R.drawable.flag_gn),
    GTQ("GTQ", "Q", R.string.name_gtq, R.drawable.flag_gt),
    GYD("GYD", "$", R.string.name_gyd, R.drawable.flag_gy),
    HKD("HKD", "$", R.string.name_hkd, R.drawable.flag_hk),
    HNL("HNL", "L", R.string.name_hnl, R.drawable.flag_hn),
    HRK("HRK", "kn", R.string.name_hrk, R.drawable.flag_hr),
    HTG("HTG", "G", R.string.name_htg, R.drawable.flag_ht),
    HUF("HUF", "Ft", R.string.name_huf, R.drawable.flag_hu),
    IDR("IDR", "Rp", R.string.name_idr, R.drawable.flag_id),
    ILS("ILS", "₪", R.string.name_ils, R.drawable.flag_il),
    IMP("IMP", "£", R.string.name_imp, R.drawable.flag_im),
    INR("INR", "₹", R.string.name_inr, R.drawable.flag_in),
    IQD("IQD", "ع.د", R.string.name_iqd, R.drawable.flag_iq),
    IRR("IRR", "﷼", R.string.name_irr, R.drawable.flag_ir),
    ISK("ISK", "kr", R.string.name_isk, R.drawable.flag_is),
    JEP("JEP", "£", R.string.name_jep, R.drawable.flag_je),
    JMD("JMD", "$", R.string.name_jmd, R.drawable.flag_jm),
    JOD("JOD", "د.أ", R.string.name_jod, R.drawable.flag_jo),
    JPY("JPY", "¥", R.string.name_jpy, R.drawable.flag_jp),
    KES("KES", "Sh", R.string.name_kes, R.drawable.flag_ke),
    KGS("KGS", "С̲", R.string.name_kgs, R.drawable.flag_kg),
    KHR("KHR", "៛", R.string.name_khr, R.drawable.flag_kh),
    KMF("KMF", "Fr", R.string.name_kmf, R.drawable.flag_km),
    KPW("KPW", "₩", R.string.name_kpw, R.drawable.flag_kp),
    KRW("KRW", "₩", R.string.name_krw, R.drawable.flag_kr),
    KWD("KWD", "د.ك", R.string.name_kwd, R.drawable.flag_kw),
    KYD("KYD", "$", R.string.name_kyd, R.drawable.flag_ky),
    KZT("KZT", "₸", R.string.name_kzt, R.drawable.flag_kz),
    LAK("LAK", "₭", R.string.name_lak, R.drawable.flag_la),
    LBP("LBP", "ل.ل.", R.string.name_lbp, R.drawable.flag_lb),
    LKR("LKR", "Rs", R.string.name_lkr, R.drawable.flag_lk),
    LRD("LRD", "$", R.string.name_lrd, R.drawable.flag_lr),
    LSL("LSL", "L", R.string.name_lsl, R.drawable.flag_ls),
    LYD("LYD", "ل.د", R.string.name_lyd, R.drawable.flag_ly),
    MAD("MAD", "د.م.", R.string.name_mad, R.drawable.flag_ma),
    MDL("MDL", "L", R.string.name_mdl, R.drawable.flag_md),
    MGA("MGA", "Ar", R.string.name_mga, R.drawable.flag_mg),
    MKD("MKD", "ден", R.string.name_mkd, R.drawable.flag_mk),
    MMK("MMK", "Ks", R.string.name_mmk, R.drawable.flag_mm),
    MNT("MNT", "₮", R.string.name_mnt, R.drawable.flag_mn),
    MOP("MOP", "MOP$", R.string.name_mop, R.drawable.flag_mo),
    MRO("MRO", "UM", R.string.name_mro, R.drawable.flag_mr),
    MRU("MRU", "UM", R.string.name_mru, R.drawable.flag_mr),
    MUR("MUR", "₨", R.string.name_mur, R.drawable.flag_mu),
    MVR("MVR", ".ރ", R.string.name_mvr, R.drawable.flag_mv),
    MWK("MWK", "MK", R.string.name_mwk, R.drawable.flag_mw),
    MXN("MXN", "$", R.string.name_mxn, R.drawable.flag_mx),
    MYR("MYR", "RM", R.string.name_myr, R.drawable.flag_my),
    MZN("MZN", "MT", R.string.name_mzn, R.drawable.flag_mz),
    NAD("NAD", "$", R.string.name_nad, R.drawable.flag_na),
    NGN("NGN", "₦", R.string.name_ngn, R.drawable.flag_ng),
    NIO("NIO", "C$", R.string.name_nio, R.drawable.flag_ni),
    NOK("NOK", "kr", R.string.name_nok, R.drawable.flag_no),
    NPR("NPR", "रु", R.string.name_npr, R.drawable.flag_np),
    NZD("NZD", "$", R.string.name_nzd, R.drawable.flag_nz),
    OMR("OMR", "ر.ع.", R.string.name_omr, R.drawable.flag_om),
    PAB("PAB", "B/.", R.string.name_pab, R.drawable.flag_pa),
    PEN("PEN", "S/", R.string.name_pen, R.drawable.flag_pe),
    PGK("PGK", "K", R.string.name_pgk, R.drawable.flag_pg),
    PHP("PHP", "₱", R.string.name_php, R.drawable.flag_ph),
    PKR("PKR", "₨", R.string.name_pkr, R.drawable.flag_pk),
    PLN("PLN", "zł", R.string.name_pln, R.drawable.flag_pl),
    PYG("PYG", "₲", R.string.name_pyg, R.drawable.flag_py),
    QAR("QAR", "ر.ق", R.string.name_qar, R.drawable.flag_qa),
    RON("RON", "lei", R.string.name_ron, R.drawable.flag_ro),
    RSD("RSD", "дин.", R.string.name_rsd, R.drawable.flag_rs),
    RUB("RUB", "₽", R.string.name_rub, R.drawable.flag_ru),
    RWF("RWF", "Fr", R.string.name_rwf, R.drawable.flag_rw),
    SAR("SAR", "ر.س", R.string.name_sar, R.drawable.flag_sa),
    SBD("SBD", " $", R.string.name_sbd, R.drawable.flag_sb),
    SCR("SCR", "₨", R.string.name_scr, R.drawable.flag_sc),
    SDG("SDG", "ج.س.", R.string.name_sdg, R.drawable.flag_sd),
    SEK("SEK", "kr", R.string.name_sek, R.drawable.flag_se),
    SGD("SGD", "$", R.string.name_sgd, R.drawable.flag_sg),
    SHP("SHP", "£", R.string.name_shp, R.drawable.flag_sh),
    SLE("SLE", "Le", R.string.name_sle, R.drawable.flag_sl),
    SLL("SLL", "Le", R.string.name_sll, R.drawable.flag_sl),
    SOS("SOS", "Sh", R.string.name_sos, R.drawable.flag_so),
    SRD("SRD", "$", R.string.name_srd, R.drawable.flag_sr),
    SSP("SSP", "£", R.string.name_ssp, R.drawable.flag_ss),
    STD("STD", "Db", R.string.name_std, R.drawable.flag_st),
    STN("STN", "Db", R.string.name_stn, R.drawable.flag_st),
    SVC("SVC", "₡", R.string.name_svc, R.drawable.flag_sv),
    SYP("SYP", "ل.س", R.string.name_syp, R.drawable.flag_sy),
    SZL("SZL", "L", R.string.name_szl, R.drawable.flag_sz),
    THB("THB", "฿", R.string.name_thb, R.drawable.flag_th),
    TJS("TJS", "SM", R.string.name_tjs, R.drawable.flag_tj),
    TMT("TMT", "m", R.string.name_tmt, R.drawable.flag_tm),
    TND("TND", "د.ت", R.string.name_tnd, R.drawable.flag_tn),
    TOP("TOP", "T$", R.string.name_top, R.drawable.flag_to),
    TRY("TRY", "₺", R.string.name_try, R.drawable.flag_tr),
    TTD("TTD", "$", R.string.name_ttd, R.drawable.flag_tt),
    TWD("TWD", "$", R.string.name_twd, R.drawable.flag_tw),
    TZS("TZS", "Sh", R.string.name_tzs, R.drawable.flag_tz),
    UAH("UAH", "₴", R.string.name_uah, R.drawable.flag_ua),
    UGX("UGX", "Sh", R.string.name_ugx, R.drawable.flag_ug),
    USD("USD", "$", R.string.name_usd, R.drawable.flag_us),
    UYU("UYU", "$", R.string.name_uyu, R.drawable.flag_uy),
    UZS("UZS", "сўм", R.string.name_uzs, R.drawable.flag_uz),
    VEF("VEF", "Bs.", R.string.name_vef, R.drawable.flag_ve),
    VES("VES", "Bs.", R.string.name_ves, R.drawable.flag_ve),
    VND("VND", "₫", R.string.name_vnd, R.drawable.flag_vn),
    VUV("VUV", "Vt", R.string.name_vuv, R.drawable.flag_vu),
    WST("WST", "T", R.string.name_wst, R.drawable.flag_ws),
    XAF("XAF", "Fr", R.string.name_xaf, null),
    XAG("XAG", null, R.string.name_xag, null),
    XAU("XAU", null, R.string.name_xau, null),
    XCD("XCD", "$", R.string.name_xcd, null),
    XDR("XDR", null, R.string.name_xdr, null),
    XOF("XOF", "Fr", R.string.name_xof, null),
    XPD("XPD", null, R.string.name_xpd, null),
    XPF("XPF", "₣", R.string.name_xpf, null),
    XPT("XPT", null, R.string.name_xpt, null),
    YER("YER", "ر.ي", R.string.name_yer, R.drawable.flag_ye),
    ZAR("ZAR", "R", R.string.name_zar, R.drawable.flag_za),
    ZMW("ZMW", "ZK", R.string.name_zmw, R.drawable.flag_zm),
    ZWL("ZWL", "$", R.string.name_zwl, R.drawable.flag_zw),
    ;

    companion object {
        // non-tradeable / superseded / special currencies excluded from conversion
        private val excluded =
            setOf(
                "BTC", // Bitcoin
                "XAG",
                "XAU",
                "XPD",
                "XPT", // metals
                "MRO",
                "STD",
                "VEF",
                "CUC", // superseded
                "XDR",
                "CLF",
                "CNH", // special / offshore
            )

        fun fromString(value: String): Currency? =
            if (value !in excluded) {
                entries.firstOrNull { it.iso4217Alpha == value }
            } else {
                null
            }
    }

    /**
     * https://en.wikipedia.org/wiki/ISO_4217#Alpha_codes
     * e.g. USD
     */
    fun iso4217Alpha(): String = this.iso4217Alpha

    /**
     * e.g. US dollar (localized) for USD
     */
    fun fullName(context: Context): String = context.getString(this.fullName)

    /**
     * Flag drawable resource — e.g. the star-spangled banner for USD — or the
     * generic placeholder for currencies without one. Compose code should
     * render it via `painterResource`, which parses each vector once and
     * caches it app-wide.
     */
    @get:DrawableRes
    val flagRes: Int get() = this.flag ?: R.drawable.flag_unknown

    /**
     * https://en.wikipedia.org/wiki/Currency_symbol
     * e.g. $ for USD
     */
    fun symbol(): String? =
        this.symbol
            ?.let { if (it.hasRtlChar()) it.wrapLtr() else it }

    /** The symbol as written, without the LTR wrapping [symbol] adds — for matching text. */
    internal val plainSymbol: String? get() = this.symbol

    /**
     * Preferred display marker for UI: the currency symbol when known,
     * otherwise the ISO alpha code as a fallback (e.g. "$" for USD, "CHF" for CHF).
     */
    fun symbolOrIso(): String = symbol() ?: iso4217Alpha

    /**
     * https://en.wikipedia.org/wiki/Bidirectional_text#Table_of_possible_BiDi_character_types
     */
    private fun String.wrapLtr(): String {
        // isolate (recommended, but too new - FSI + PDI)
        // return "\u2067" + this + "\u2069"
        // embedding (discouraged - LRE + PDF)
        return LTR_EMBEDDING + this + POP_DIRECTIONAL_FORMATTING
    }

    private fun String.hasRtlChar(): Boolean = this.any { it.directionality == CharDirectionality.RIGHT_TO_LEFT_ARABIC }
}
