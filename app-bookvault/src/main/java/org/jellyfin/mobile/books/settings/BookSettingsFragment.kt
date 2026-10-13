package org.jellyfin.mobile.books.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.categoryHeader
import de.Maxr1998.modernpreferences.helpers.defaultOnClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.screen
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.databinding.FragmentSettingsBinding
import org.jellyfin.mobile.books.reader.EPUB_FONT_FAMILY_PRESETS
import org.jellyfin.mobile.books.reader.EPUB_FONT_SCALE_PRESETS
import org.jellyfin.mobile.books.reader.EPUB_LINE_SPACING_PRESETS
import org.jellyfin.mobile.books.reader.formatFontFamilyLabel
import org.jellyfin.mobile.books.reader.formatFontScaleLabel
import org.jellyfin.mobile.books.reader.formatLineSpacingLabel
import org.jellyfin.mobile.common.MainViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class BookSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModel()
    private val appPreferences: BookAppPreferences by inject()
    private val settingsAdapter: PreferencesAdapter by lazy { PreferencesAdapter(buildSettingsScreen()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setTitle(R.string.settings)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = settingsAdapter
    }

    private fun buildSettingsScreen() = screen(requireContext()) {
        categoryHeader("pref_category_reading") {
            titleRes = R.string.pref_category_reading
        }
        pref("pref_epub_font_size") {
            titleRes = R.string.text_settings_font_size
            defaultOnClick {
                showFontSizeDialog()
                true
            }
        }
        pref("pref_epub_font_family") {
            titleRes = R.string.text_settings_font_family
            defaultOnClick {
                showFontFamilyDialog()
                true
            }
        }
        pref("pref_epub_line_spacing") {
            titleRes = R.string.text_settings_line_spacing
            defaultOnClick {
                showLineSpacingDialog()
                true
            }
        }
        categoryHeader("pref_category_account") {
            titleRes = R.string.pref_category_account
        }
        pref("pref_sign_out") {
            titleRes = R.string.pref_sign_out
            defaultOnClick {
                lifecycleScope.launch { mainViewModel.logout() }
                true
            }
        }
    }

    private fun showFontSizeDialog() {
        val labels = EPUB_FONT_SCALE_PRESETS.map { formatFontScaleLabel(it) }.toTypedArray()
        val currentIndex = EPUB_FONT_SCALE_PRESETS.indexOf(appPreferences.epubFontScale).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_settings_font_size)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubFontScale = EPUB_FONT_SCALE_PRESETS[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun showFontFamilyDialog() {
        val labels = EPUB_FONT_FAMILY_PRESETS.map { formatFontFamilyLabel(it) }.toTypedArray()
        val currentIndex = EPUB_FONT_FAMILY_PRESETS.indexOf(appPreferences.epubFontFamily).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_settings_font_family)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubFontFamily = EPUB_FONT_FAMILY_PRESETS[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun showLineSpacingDialog() {
        val labels = EPUB_LINE_SPACING_PRESETS.map { formatLineSpacingLabel(it) }.toTypedArray()
        val currentIndex = EPUB_LINE_SPACING_PRESETS.indexOf(appPreferences.epubLineSpacing).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.text_settings_line_spacing)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                appPreferences.epubLineSpacing = EPUB_LINE_SPACING_PRESETS[which]
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
