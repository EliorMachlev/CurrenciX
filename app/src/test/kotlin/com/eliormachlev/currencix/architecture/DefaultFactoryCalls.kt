package com.eliormachlev.currencix.architecture

// A call that gets a ViewModel: Compose's viewModel, Activity's viewModels,
// ViewModelProvider or ViewModelProvider.create, with or without type arguments.
private val VIEW_MODEL_CALL =
    Regex("""\b(viewModels?|ViewModelProvider(?:\.create)?)\b\s*(?:<(?:[^<>()]|<[^<>()]*>)*>)?\s*\(""")

// `name = value`, as a named argument (not `a == b`).
private val NAMED_ARGUMENT = Regex("""^\s*(\w+)\s*=(?!=)\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

// The names under which a call takes its factory or initializer.
private val FACTORY_NAMES = setOf("factory", "factoryProducer", "initializer")

// Where each call takes its factory when it's passed without a name:
// viewModel(owner, key, factory), viewModels(extrasProducer, factoryProducer),
// ViewModelProvider(owner, factory), ViewModelProvider.create(owner, factory).
private val FACTORY_POSITION = mapOf("viewModel" to 2, "viewModels" to 1, "ViewModelProvider" to 1, "ViewModelProvider.create" to 1)

// The calls whose trailing lambda is their initializer or factoryProducer.
private val TRAILING_FACTORY = setOf("viewModel", "viewModels")

private val OPENERS = setOf('(', '[', '{')
private val CLOSERS = setOf(')', ']', '}')

/**
 * The calls in [source] that leave a ViewModel to the default factory, which
 * finds its constructor by reflection. A call is fine when it passes a factory
 * by name (`factory`, `factoryProducer`, `initializer`) or at its position, or
 * an initializer as its trailing lambda; a factory written as `null` is the
 * default one and doesn't count. Each call's arguments are read to their
 * closing parenthesis, so a type argument or another argument (`key`,
 * `viewModelStoreOwner`, `extras`) doesn't hide it; one whose parentheses
 * don't close is reported too. Text, not types: a nullable factory variable
 * that turns out null at runtime isn't seen.
 */
internal fun defaultFactoryCalls(source: String): List<String> =
    VIEW_MODEL_CALL
        .findAll(source)
        .mapNotNull { call ->
            val open = call.range.last
            val close = closingParenthesis(source, open) ?: return@mapNotNull source.substring(call.range.first)
            val callee = call.groupValues[1]
            val trailingLambda = callee in TRAILING_FACTORY && source.substring(close + 1).trimStart().startsWith("{")
            val factories = factoryArguments(callee, topLevelArguments(source.substring(open + 1, close)))
            if (trailingLambda || factories.any { it != "null" }) null else source.substring(call.range.first, close + 1)
        }.toList()

// What [callee] is given as its factory: the argument at its factory position
// (among those before the first named one) and any named factory, trimmed.
private fun factoryArguments(
    callee: String,
    arguments: List<String>,
): List<String> {
    val positional = arguments.takeWhile { !NAMED_ARGUMENT.matches(it) }
    val named =
        arguments
            .mapNotNull(NAMED_ARGUMENT::matchEntire)
            .filter { it.groupValues[1] in FACTORY_NAMES }
            .map { it.groupValues[2] }
    return (listOfNotNull(positional.getOrNull(FACTORY_POSITION.getValue(callee))) + named).map(String::trim)
}

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
