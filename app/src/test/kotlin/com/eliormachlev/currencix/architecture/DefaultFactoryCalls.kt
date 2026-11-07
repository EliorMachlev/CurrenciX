package com.eliormachlev.currencix.architecture

// A call that gets a ViewModel: Compose's viewModel, Activity's viewModels,
// ViewModelProvider or ViewModelProvider.create, with or without type arguments.
private val VIEW_MODEL_CALL =
    Regex("""\b(viewModels?|ViewModelProvider(?:\.create)?)\b\s*(?:<(?:[^<>()]|<[^<>()]*>)*>)?\s*\(""")

// Named arguments that hand over the construction instead of the default factory.
private val FACTORY_ARGUMENT = Regex("""^\s*(factory|factoryProducer|initializer)\s*=""")

private val OPENERS = setOf('(', '[', '{')
private val CLOSERS = setOf(')', ']', '}')

/**
 * The calls in [source] that leave a ViewModel to the default factory, which
 * finds its constructor by reflection. A call is fine when it passes a factory
 * (named `factory`, `factoryProducer` or `initializer`, or ViewModelProvider's
 * second argument) or an initializer as its trailing lambda. Each call's
 * arguments are read to their closing parenthesis, so a type argument or any
 * other named argument (`key`, `viewModelStoreOwner`) doesn't hide it; one
 * whose parentheses don't close is reported too.
 */
internal fun defaultFactoryCalls(source: String): List<String> =
    VIEW_MODEL_CALL
        .findAll(source)
        .mapNotNull { call ->
            val open = call.range.last
            val close = closingParenthesis(source, open) ?: return@mapNotNull source.substring(call.range.first)
            val arguments = topLevelArguments(source.substring(open + 1, close))
            val passesFactory =
                arguments.any(FACTORY_ARGUMENT::containsMatchIn) ||
                    when (call.groupValues[1]) {
                        "viewModel", "viewModels" -> source.substring(close + 1).trimStart().startsWith("{")
                        else -> arguments.size >= 2
                    }
            if (passesFactory) null else source.substring(call.range.first, close + 1)
        }.toList()

// Where the parenthesis opened at [open] closes, or null when it doesn't.
private fun closingParenthesis(
    source: String,
    open: Int,
): Int? {
    var depth = 0
    for (index in open until source.length) {
        when (source[index]) {
            in OPENERS -> depth++
            in CLOSERS -> if (--depth == 0) return index
        }
    }
    return null
}

// [arguments] split at its own commas, not those inside nested brackets; blank for none.
private fun topLevelArguments(arguments: String): List<String> {
    if (arguments.isBlank()) return emptyList()
    val split = mutableListOf<String>()
    var depth = 0
    var start = 0
    arguments.forEachIndexed { index, char ->
        when {
            char in OPENERS -> {
                depth++
            }

            char in CLOSERS -> {
                depth--
            }

            char == ',' && depth == 0 -> {
                split += arguments.substring(start, index)
                start = index + 1
            }
        }
    }
    split += arguments.substring(start)
    return split.filter { it.isNotBlank() }
}
