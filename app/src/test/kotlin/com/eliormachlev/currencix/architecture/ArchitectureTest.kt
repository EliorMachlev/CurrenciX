package com.eliormachlev.currencix.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Encodes CurrenciX's MVVM layer boundaries as executable invariants so
 * Phase 1+ rewrites can't silently blur them (see docs/markDown/architecture.md).
 *
 * Layer packages under `com.eliormachlev.currencix`:
 *   view       — Activities, Custom Views, Dialogs, Widget, Compose surfaces
 *   viewmodel  — MainViewModel, TimelineViewModel, PreferenceViewModel, ...
 *   repository — ExchangeRatesRepository, Database, BackupManager, ...
 *   model      — Currency, ExchangeRates, Fee, adapter/, provider/, ...
 *
 * Rules encoded here (kept intentionally narrow — better 4 rock-solid rules
 * than 10 flaky ones):
 *   1. ViewModel layer does not depend on View layer.
 *   2. Repository layer does not depend on View or ViewModel layers.
 *   3. Model layer does not depend on View, ViewModel, or Repository layers.
 *   4. Every `*ViewModel` class extends androidx.lifecycle.ViewModel (or a subclass).
 */
class ArchitectureTest {
    private val scope get() = Konsist.scopeFromProject(sourceSetName = MAIN_SOURCE_SET)

    @Test
    fun `viewmodel layer does not depend on view layer`() {
        scope
            .files
            .withPackage("$ROOT_PACKAGE.viewmodel..")
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("$ROOT_PACKAGE.view.") }
            }
    }

    @Test
    fun `repository layer does not depend on view or viewmodel layers`() {
        scope
            .files
            .withPackage("$ROOT_PACKAGE.repository..")
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("$ROOT_PACKAGE.view.") ||
                        import.name.startsWith("$ROOT_PACKAGE.viewmodel.")
                }
            }
    }

    @Test
    fun `model layer does not depend on view, viewmodel, or repository layers`() {
        scope
            .files
            .withPackage("$ROOT_PACKAGE.model..")
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("$ROOT_PACKAGE.view.") ||
                        import.name.startsWith("$ROOT_PACKAGE.viewmodel.") ||
                        import.name.startsWith("$ROOT_PACKAGE.repository.")
                }
            }
    }

    @Test
    fun `classes named ViewModel extend androidx lifecycle ViewModel`() {
        scope
            .classes()
            .withPackage("$ROOT_PACKAGE.viewmodel..")
            .withNameEndingWith("ViewModel")
            .assertTrue { klass ->
                // indirectParents = true so any subclass of ViewModel (e.g.
                // AndroidViewModel) also satisfies the invariant.
                klass.hasParentWithName(
                    names = VIEWMODEL_PARENT_NAMES,
                    indirectParents = true,
                )
            }
    }

    private companion object {
        const val ROOT_PACKAGE = "com.eliormachlev.currencix"
        const val MAIN_SOURCE_SET = "main"

        // Konsist matches parents by simple name; both count as satisfying the
        // invariant because AndroidViewModel itself extends ViewModel.
        val VIEWMODEL_PARENT_NAMES = listOf("ViewModel", "AndroidViewModel")
    }
}
