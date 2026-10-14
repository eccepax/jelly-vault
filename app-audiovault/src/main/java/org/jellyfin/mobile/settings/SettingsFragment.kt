package org.jellyfin.mobile.settings

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.categoryHeader
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.defaultOnClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.preferences.CheckBoxPreference
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.databinding.FragmentSettingsBinding
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.extensions.requireMainActivity
import org.jellyfin.mobile.utils.getDownloadsPaths
import org.jellyfin.mobile.utils.withThemedContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.jellyfin.mobile.common.R as CommonR

class SettingsFragment : Fragment(), BackPressInterceptor {

    private val mainViewModel: MainViewModel by activityViewModel()
    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }

    private lateinit var pressSpeedUpPreference: CheckBoxPreference
    private lateinit var backgroundAudioPreference: Preference

    init {
        Preference.Config.titleMaxLines = 2
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localInflater = inflater.withThemedContext(requireContext(), CommonR.style.AppTheme_Settings)
        val binding = FragmentSettingsBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()
        binding.toolbar.setTitle(R.string.activity_name_settings)
        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        binding.recyclerView.adapter = settingsAdapter
        return binding.root
    }

    override fun onInterceptBackPressed(): Boolean {
        return settingsAdapter.goBack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireMainActivity().setSupportActionBar(null)
    }

    @Suppress("LongMethod")
    private fun buildSettingsScreen() = screen(requireContext()) {
        collapseIcon = true
        categoryHeader(PREF_CATEGORY_MUSIC_PLAYER) {
            titleRes = R.string.pref_category_music_player
        }
        checkBox(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE) {
            titleRes = R.string.pref_music_notification_always_dismissible_title
            summaryRes = R.string.pref_music_notification_always_dismissible_summary_off
            summaryOnRes = R.string.pref_music_notification_always_dismissible_summary_on
        }

        // Audio playback settings (useful for audiobooks)
        pressSpeedUpPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP) {
            titleRes = R.string.pref_exoplayer_allow_press_speed_up
            summaryRes = R.string.pref_exoplayer_allow_press_speed_up_summary
            defaultValue = true
        }
        backgroundAudioPreference = checkBox(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO) {
            titleRes = R.string.pref_exoplayer_allow_background_audio
            summaryRes = R.string.pref_exoplayer_allow_background_audio_summary
        }
        checkBox(Constants.PREF_SKIP_SILENCE) {
            titleRes = R.string.pref_skip_silence_title
            summaryRes = R.string.pref_skip_silence_summary
        }
        categoryHeader(PREF_CATEGORY_LIBRARY) {
            titleRes = R.string.pref_category_library
        }
        checkBox(Constants.PREF_LIBRARY_GLOBAL_FLATTEN) {
            titleRes = R.string.pref_library_global_flatten_title
            summaryRes = R.string.pref_library_global_flatten_summary_off
            summaryOnRes = R.string.pref_library_global_flatten_summary_on
        }
        categoryHeader(PREF_CATEGORY_DOWNLOADS) {
            titleRes = R.string.pref_category_downloads
        }

        val downloadMethods = listOf(
            SelectionItem(
                DownloadMethod.WIFI_ONLY,
                R.string.wifi_only,
                R.string.wifi_only_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_DATA,
                R.string.mobile_data,
                R.string.mobile_data_summary,
            ),
            SelectionItem(
                DownloadMethod.MOBILE_AND_ROAMING,
                R.string.mobile_data_and_roaming,
                R.string.mobile_data_and_roaming_summary,
            ),
        )
        singleChoice(Constants.PREF_DOWNLOAD_METHOD, downloadMethods) {
            titleRes = R.string.network_title
        }

        val downloadsDirs = requireContext().getDownloadsPaths().map { path ->
            SelectionItem(path, path, null)
        }
        singleChoice(Constants.PREF_DOWNLOAD_LOCATION, downloadsDirs) {
            titleRes = R.string.pref_download_location
            initialSelection = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        }

        checkBox(Constants.PREF_DOWNLOAD_INTERNAL) {
            titleRes = R.string.store_videos_in_internal_storage
            summaryRes = R.string.stored_videos_in_internal_storage_desc
            defaultValue = true
            // Note: String says "videos" but this applies to audiobooks too
        }
        categoryHeader(PREF_CATEGORY_ACCOUNT) {
            titleRes = R.string.pref_category_account
        }
        pref(PREF_SIGN_OUT) {
            titleRes = R.string.pref_sign_out
            defaultOnClick {
                lifecycleScope.launch { mainViewModel.logout() }
                true
            }
        }
    }

    companion object {
        const val PREF_CATEGORY_MUSIC_PLAYER = "pref_category_music"

        const val PREF_CATEGORY_LIBRARY = "pref_category_library"
        const val PREF_CATEGORY_DOWNLOADS = "pref_category_downloads"
        const val PREF_CATEGORY_ACCOUNT = "pref_category_account"
        const val PREF_SIGN_OUT = "pref_sign_out"
    }
}
