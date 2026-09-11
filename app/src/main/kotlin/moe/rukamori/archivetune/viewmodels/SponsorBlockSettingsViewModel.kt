/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sponsorblock.ObserveSponsorBlockSettingsUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockApiUrlUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockCategoriesUseCase
import moe.rukamori.archivetune.sponsorblock.SetSponsorBlockEnabledUseCase
import moe.rukamori.archivetune.sponsorblock.SponsorBlockCategory
import moe.rukamori.archivetune.sponsorblock.SponsorBlockSettings
import moe.rukamori.archivetune.sponsorblock.ValidateSponsorBlockApiUrlUseCase
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface SponsorBlockSettingsScreenState {
    data object Loading : SponsorBlockSettingsScreenState

    data class Success(
        val data: SponsorBlockSettingsUiData,
    ) : SponsorBlockSettingsScreenState

    data object Empty : SponsorBlockSettingsScreenState

    data class Error(
        @StringRes val messageRes: Int,
    ) : SponsorBlockSettingsScreenState
}

@Immutable
data class SponsorBlockCategoryUiModel(
    val category: SponsorBlockCategory,
    @StringRes val labelRes: Int,
)

@Immutable
data class SponsorBlockSettingsUiData(
    val enabled: Boolean,
    val categoryOptions: ImmutableList<SponsorBlockCategoryUiModel>,
    val selectedCategoryOptions: ImmutableList<SponsorBlockCategoryUiModel>,
    val draftCategoryOptions: ImmutableList<SponsorBlockCategoryUiModel>,
    val isCategorySheetVisible: Boolean,
    val apiUrl: String,
    val apiUrlDraft: String,
    val isApiUrlDraftValid: Boolean,
    val isApiUrlEditorVisible: Boolean,
)

@HiltViewModel
class SponsorBlockSettingsViewModel
    @Inject
    constructor(
        private val observeSettings: ObserveSponsorBlockSettingsUseCase,
        private val setEnabled: SetSponsorBlockEnabledUseCase,
        private val setCategories: SetSponsorBlockCategoriesUseCase,
        private val setApiUrl: SetSponsorBlockApiUrlUseCase,
        private val validateApiUrl: ValidateSponsorBlockApiUrlUseCase,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow<SponsorBlockSettingsScreenState>(SponsorBlockSettingsScreenState.Loading)
        val uiState: StateFlow<SponsorBlockSettingsScreenState> = mutableUiState.asStateFlow()

        private var observeJob: Job? = null
        private var enabledUpdateJob: Job? = null
        private var categoriesUpdateJob: Job? = null
        private var apiUrlUpdateJob: Job? = null

        init {
            observe()
        }

        fun retry() {
            observeJob?.cancel()
            mutableUiState.value = SponsorBlockSettingsScreenState.Loading
            observe()
        }

        fun onEnabledChange(enabled: Boolean) {
            updateSuccess { data -> data.copy(enabled = enabled) }
            enabledUpdateJob?.cancel()
            enabledUpdateJob =
                launchUpdate {
                    setEnabled(enabled)
                }
        }

        fun onCategorySheetOpen() {
            updateSuccess { data ->
                data.copy(
                    draftCategoryOptions = data.selectedCategoryOptions,
                    isCategorySheetVisible = true,
                )
            }
        }

        fun onCategorySheetDismiss() {
            updateSuccess { data ->
                data.copy(
                    draftCategoryOptions = data.selectedCategoryOptions,
                    isCategorySheetVisible = false,
                )
            }
        }

        fun onCategoryCheckedChange(
            category: SponsorBlockCategory,
            checked: Boolean,
        ) {
            updateSuccess { data ->
                val draftCategories = data.draftCategoryOptions.mapTo(mutableSetOf()) { it.category }
                if (checked) {
                    draftCategories += category
                } else {
                    draftCategories -= category
                }
                data.copy(
                    draftCategoryOptions =
                        ImmutableList.copyOf(
                            data.categoryOptions.filter { it.category in draftCategories },
                        ),
                )
            }
        }

        fun onCategoryOptionCheckedChange(
            option: SponsorBlockCategoryUiModel,
            checked: Boolean,
        ) {
            onCategoryCheckedChange(option.category, checked)
        }

        fun onCategorySelectionConfirm() {
            val data = currentData() ?: return
            val categories = data.draftCategoryOptions.mapTo(linkedSetOf()) { it.category }
            updateSuccess { current ->
                current.copy(
                    selectedCategoryOptions = current.draftCategoryOptions,
                    isCategorySheetVisible = false,
                )
            }
            categoriesUpdateJob?.cancel()
            categoriesUpdateJob =
                launchUpdate {
                    setCategories(categories)
                }
        }

        fun onApiUrlEditorOpen() {
            updateSuccess { data ->
                data.copy(
                    apiUrlDraft = data.apiUrl,
                    isApiUrlDraftValid = true,
                    isApiUrlEditorVisible = true,
                )
            }
        }

        fun onApiUrlEditorDismiss() {
            updateSuccess { data ->
                data.copy(
                    apiUrlDraft = data.apiUrl,
                    isApiUrlDraftValid = true,
                    isApiUrlEditorVisible = false,
                )
            }
        }

        fun onApiUrlDraftChange(value: String) {
            updateSuccess { data ->
                data.copy(
                    apiUrlDraft = value,
                    isApiUrlDraftValid = validateApiUrl(value),
                )
            }
        }

        fun onApiUrlConfirm() {
            val data = currentData() ?: return
            if (!data.isApiUrlDraftValid) return
            val apiUrlDraft = data.apiUrlDraft
            updateSuccess { current -> current.copy(isApiUrlEditorVisible = false) }
            apiUrlUpdateJob?.cancel()
            apiUrlUpdateJob =
                launchUpdate {
                    setApiUrl(apiUrlDraft)
                }
        }

        private fun observe() {
            if (observeJob?.isActive == true) return
            lateinit var nextJob: Job
            nextJob =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        observeSettings().collect { settings ->
                            val existingData = currentData()
                            mutableUiState.value =
                                SponsorBlockSettingsScreenState.Success(
                                    settings.toUiData(existingData),
                                )
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        mutableUiState.value =
                            SponsorBlockSettingsScreenState.Error(R.string.error_unknown)
                    } finally {
                        if (observeJob === nextJob) {
                            observeJob = null
                        }
                    }
                }
            observeJob = nextJob
            nextJob.start()
        }

        private fun launchUpdate(update: suspend () -> Unit): Job =
            viewModelScope.launch {
                try {
                    update()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    reportException(throwable)
                    mutableUiState.value =
                        SponsorBlockSettingsScreenState.Error(R.string.error_unknown)
                }
            }

        private fun updateSuccess(transform: (SponsorBlockSettingsUiData) -> SponsorBlockSettingsUiData) {
            mutableUiState.update { state ->
                val success = state as? SponsorBlockSettingsScreenState.Success ?: return@update state
                success.copy(data = transform(success.data))
            }
        }

        private fun currentData(): SponsorBlockSettingsUiData? =
            (mutableUiState.value as? SponsorBlockSettingsScreenState.Success)?.data

        private fun SponsorBlockSettings.toUiData(
            existingData: SponsorBlockSettingsUiData?,
        ): SponsorBlockSettingsUiData {
            val selectedOptions = ImmutableList.copyOf(CATEGORY_OPTIONS.filter { it.category in categories })
            val keepCategoryDraft = existingData?.isCategorySheetVisible == true
            val keepApiUrlDraft = existingData?.isApiUrlEditorVisible == true
            return SponsorBlockSettingsUiData(
                enabled = enabled,
                categoryOptions = CATEGORY_OPTIONS,
                selectedCategoryOptions = selectedOptions,
                draftCategoryOptions =
                    if (keepCategoryDraft) {
                        existingData?.draftCategoryOptions ?: selectedOptions
                    } else {
                        selectedOptions
                    },
                isCategorySheetVisible = keepCategoryDraft,
                apiUrl = apiUrl,
                apiUrlDraft = if (keepApiUrlDraft) existingData?.apiUrlDraft ?: apiUrl else apiUrl,
                isApiUrlDraftValid =
                    if (keepApiUrlDraft) existingData?.isApiUrlDraftValid ?: true else true,
                isApiUrlEditorVisible = keepApiUrlDraft,
            )
        }

        private companion object {
            val CATEGORY_OPTIONS: ImmutableList<SponsorBlockCategoryUiModel> =
                ImmutableList.of(
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.MUSIC_OFF_TOPIC,
                        R.string.sponsor_block_music_off_topic,
                    ),
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.SPONSOR,
                        R.string.sponsor_block_sponsors,
                    ),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.INTRO, R.string.sponsor_block_intro),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.OUTRO, R.string.sponsor_block_outro),
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.SELF_PROMOTION,
                        R.string.sponsor_block_self_promotions,
                    ),
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.PREVIEW,
                        R.string.sponsor_block_previews,
                    ),
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.FILLER,
                        R.string.sponsor_block_fillers,
                    ),
                    SponsorBlockCategoryUiModel(
                        SponsorBlockCategory.INTERACTION,
                        R.string.sponsor_block_subscription_reminders,
                    ),
                    SponsorBlockCategoryUiModel(SponsorBlockCategory.HOOK, R.string.sponsor_block_hooks),
                )
        }
    }
