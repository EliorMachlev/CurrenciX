package com.eliormachlev.currencix.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// Every step is rounded to this many digits: far more than an amount of money
// has, so the rounding only ever shows in a fraction that doesn't end (1 ÷ 3).
private val PRECISION = MathContext(68, RoundingMode.HALF_EVEN)

// How deep brackets may nest. Nobody types this many; an imported cart file
// could carry thousands, and each level is a stack frame.
private const val MAX_DEPTH = 64

/** An expression that isn't arithmetic: a stray character, a missing operand, an unclosed bracket. */
internal class ArithmeticSyntaxException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Works out [expression]: decimal numbers, `+ - * /`, brackets and a leading
 * sign, with the usual precedence. A value directly followed by a bracket
 * multiplies: `2(3+4)` is 14. Expects no whitespace.
 *
 * @throws ArithmeticSyntaxException when it can't be read
 * @throws ArithmeticException on a division by zero
 */
internal fun evaluateArithmetic(expression: String): BigDecimal = ArithmeticParser(expression).parse()

// Recursive descent, one function per level of precedence:
//
//   sum     := product (('+' | '-') product)*
//   product := signed (('*' | '/') signed | group)*
//   signed  := ('+' | '-')* (group | number)
//   group   := '(' sum ')'
private class ArithmeticParser(
    private val text: String,
) {
    private var pos = 0
    private var depth = 0

    fun parse(): BigDecimal {
        val value = sum()
        if (pos < text.length) fail("unexpected '${text[pos]}'")
        return value
    }

    private fun sum(): BigDecimal {
        var value = product()
        while (true) {
            value =
                when (peek()) {
                    '+' -> value.add(next(::product), PRECISION)
                    '-' -> value.subtract(next(::product), PRECISION)
                    else -> return value
                }
        }
    }

    private fun product(): BigDecimal {
        var value = signed()
        while (true) {
            value =
                when (peek()) {
                    '*' -> value.multiply(next(::signed), PRECISION)
                    '/' -> value.divide(next(::signed), PRECISION)
                    '(' -> value.multiply(group(), PRECISION)
                    else -> return value
                }
        }
    }

    private fun signed(): BigDecimal =
        when (peek()) {
            '-' -> next(::signed).negate()
            '+' -> next(::signed)
            '(' -> group()
            else -> number()
        }

    private fun group(): BigDecimal {
        if (++depth > MAX_DEPTH) fail("brackets nested deeper than $MAX_DEPTH")
        val value = next(::sum)
        if (peek() != ')') fail("missing ')'")
        pos++
        depth--
        return value
    }

    private fun number(): BigDecimal {
        val start = pos
        while (peek().isNumberPart()) pos++
        return text.substring(start, pos).toBigDecimalOrNull() ?: fail("expected a number")
    }

    // Steps over the character just looked at, then reads what follows it.
    private fun next(read: () -> BigDecimal): BigDecimal {
        pos++
        return read()
    }

    private fun peek(): Char? = text.getOrNull(pos)

    private fun Char?.isNumberPart(): Boolean = this != null && (this in '0'..'9' || this == '.')

    private fun fail(what: String): Nothing = throw ArithmeticSyntaxException("$what at $pos in \"$text\"")
}
