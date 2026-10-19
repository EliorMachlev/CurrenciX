package com.eliormachlev.currencix.viewmodel.main

import com.eliormachlev.currencix.util.OPERATOR_DIVIDE
import com.eliormachlev.currencix.util.OPERATOR_MINUS
import com.eliormachlev.currencix.util.OPERATOR_MULTIPLY
import com.eliormachlev.currencix.util.OPERATOR_PLUS

/**
 * The four arithmetic operators the calculator keypad exposes. Each entry
 * carries the glyph an expression is written with ([display] — what
 * [CalculatorInputState.addOperator] takes) and the ASCII character a hardware
 * keyboard produces for it ([hardware]), so both input paths end in the same
 * call.
 */
enum class Operator(
    val display: String,
    val hardware: Char,
) {
    PLUS(OPERATOR_PLUS, '+'),
    MINUS(OPERATOR_MINUS, '-'),
    TIMES(OPERATOR_MULTIPLY, '*'),
    DIVIDE(OPERATOR_DIVIDE, '/'),
    ;

    companion object {
        fun fromHardware(char: Char): Operator? = entries.firstOrNull { it.hardware == char }
    }
}
