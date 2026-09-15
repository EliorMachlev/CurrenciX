package com.eliormachlev.currencix.showkase

import com.airbnb.android.showkase.annotation.ShowkaseRoot
import com.airbnb.android.showkase.annotation.ShowkaseRootModule

// Marker class picked up by Showkase's KSP processor to generate the
// browser registry. One per module — this one covers all @Preview
// composables in the app module (Showkase auto-discovers them; no
// per-composable annotation needed). Lives in src/debug/ so neither
// the class nor the generated registry ships in release APKs.
@ShowkaseRoot
class CurrenciXShowkaseRoot : ShowkaseRootModule
