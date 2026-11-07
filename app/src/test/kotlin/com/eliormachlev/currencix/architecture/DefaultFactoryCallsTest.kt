package com.eliormachlev.currencix.architecture

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultFactoryCallsTest {
    @Test
    fun `a call without a factory is found, whatever else it passes`() {
        listOf(
            "val vm: PreferenceViewModel = viewModel()",
            "val vm = viewModel<PreferenceViewModel>()",
            "val vm = viewModel<Holder<PreferenceViewModel>>()",
            """val vm: PreferenceViewModel = viewModel(key = "picker")""",
            "val vm: PreferenceViewModel = viewModel(viewModelStoreOwner = owner)",
            "val vm: PreferenceViewModel = viewModel(\n    viewModelStoreOwner = owner,\n    key = KEY,\n)",
            "val vm = ViewModelProvider(this)[PreferenceViewModel::class.java]",
            "val vm = ViewModelProvider.create(owner)[PreferenceViewModel::class]",
            "private val vm: PreferenceViewModel by viewModels()",
            "val vm: PreferenceViewModel = viewModel(",
        ).forEach { source -> assertEquals(source, 1, defaultFactoryCalls(source).size) }
    }

    @Test
    fun `a call that passes a factory or an initializer is left alone`() {
        listOf(
            "val vm = viewModel(factory = MainViewModel.factory(app))",
            """val vm: MainViewModel = viewModel(key = "picker", factory = MainViewModel.factory(app, onlyCache = true))""",
            "val vm = viewModel(key = key) { create(application) }",
            "val vm = viewModel<PreferenceViewModel> { PreferenceViewModel(app) }",
            "val vm = viewModel { PreferenceViewModel(app) }",
            "val vm = ViewModelProvider(this, MainViewModel.factory(application))[MainViewModel::class.java]",
            "val vm = ViewModelProvider.create(owner, factory = preferenceFactory)[PreferenceViewModel::class]",
            "private val vm: PreferenceViewModel by viewModels { preferenceFactory }",
            "private val vm: PreferenceViewModel by viewModels(factoryProducer = { preferenceFactory })",
            "val vm = applicationViewModel { PreferenceViewModel(it) }",
            "viewModel.runExport(uri)",
            "fun Screen(viewModel: PreferenceViewModel) = viewModelScope.launch {}",
        ).forEach { source -> assertEquals(source, emptyList<String>(), defaultFactoryCalls(source)) }
    }
}
