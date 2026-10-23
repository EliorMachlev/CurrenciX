package com.eliormachlev.currencix.strict

import android.app.Application

// Release variant: no-op. The debug variant turns on StrictMode's checks;
// the same entry point here keeps Application.onCreate the same in every
// build type.
fun Application.installStrictMode() = Unit
