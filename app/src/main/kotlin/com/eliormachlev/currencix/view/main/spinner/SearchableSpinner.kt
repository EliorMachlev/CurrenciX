package com.eliormachlev.currencix.view.main.spinner

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SpinnerAdapter
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.FragmentActivity
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.util.hapticTap
import java.math.BigDecimal

// Matches AdapterView.INVALID_POSITION; used when there's no rate for the
// requested currency so the spinner clears its selection.
private const val NO_SELECTION = -1

class SearchableSpinner : AppCompatSpinner {
    private val mContext = context
    private lateinit var spinnerDialog: SearchableSpinnerDialog

    private val adapter = SearchableSpinnerAdapter(context, android.R.layout.simple_spinner_item)

    constructor(
        context: Context,
    ) : this(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, R.attr.spinnerStyle)

    constructor (
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr) {
        init()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        super.setAdapter(adapter)

        spinnerDialog = SearchableSpinnerDialog(context)
        // click listeners
        spinnerDialog.onRateClicked = { rate: Rate, _: Int ->
            setSelection(adapter.getPosition(rate.currency))
        }
        // prevent "drag-to-open" (interferes with pull-to-refresh): https://stackoverflow.com/questions/27923266/
        setOnTouchListener { v, event ->
            if (event.action != MotionEvent.ACTION_MOVE) {
                v.onTouchEvent(event)
            } else {
                true
            }
        }
    }

    fun setSelection(currency: Currency?) {
        setSelection(currency?.let { adapter.getPosition(it) } ?: NO_SELECTION)
    }

    override fun setAdapter(adapter: SpinnerAdapter?): Unit = throw NoSuchMethodException("This Spinner sets its own adapter.")

    // click on spinner -> open the dialog
    override fun performClick(): Boolean =
        when {
            // dialog is already active
            spinnerDialog.isAdded -> true
            // else show dialog, if this spinner is backed by an adapter
            !spinnerDialog.isVisible -> {
                val fm = findActivity(mContext)?.supportFragmentManager
                if (fm != null) {
                    hapticTap()
                    spinnerDialog.show(fm, null)
                }
                true
            }
            // else do nothing
            else -> super.performClick()
        }

    fun setRates(rates: List<Rate>?) {
        // set in own adapter...
        adapter.setRates(rates)
    }

    // Repopulate the adapter and immediately restore [selection]. setRates
    // alone drops the previous selection; without re-applying, AbsSpinner
    // auto-picks position 0 on the next layout pass and its onItemSelected
    // callback clobbers any persisted currency. If [selection] isn't in the
    // new rate set (provider dropped it), the fallback to position 0 stands.
    fun setRates(
        rates: List<Rate>?,
        selection: Currency?,
    ) {
        setRates(rates)
        setSelection(selection)
    }

    //  conversion preview
    fun setCurrentRate(currentRate: Rate) {
        // set in dialog
        spinnerDialog.setCurrentRate(currentRate)
    }

    fun setCurrentSum(currentSum: BigDecimal) {
        // set in dialog
        spinnerDialog.setCurrentSum(currentSum)
    }

    // Currency that should be greyed out and unselectable in this spinner's
    // picker — used to keep the two sides of a pair distinct. Callers push
    // the opposite side's current selection whenever it changes.
    fun setDisabledCurrency(currency: Currency?) {
        spinnerDialog.setDisabledCurrency(currency)
    }

    private fun findActivity(context: Context?): FragmentActivity? =
        when (context) {
            is FragmentActivity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }
}
