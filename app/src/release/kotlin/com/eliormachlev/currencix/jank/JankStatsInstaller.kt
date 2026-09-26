package com.eliormachlev.currencix.jank

import android.app.Application

// Release variant: no-op. The debug variant imports androidx.metrics and
// registers per-Activity JankStats collectors; keeping the entry point
// identically-named here means Application.onCreate calls the same
// function regardless of build type — the source-set split decides
// whether anything actually happens.
fun Application.installJankStats() = Unit
