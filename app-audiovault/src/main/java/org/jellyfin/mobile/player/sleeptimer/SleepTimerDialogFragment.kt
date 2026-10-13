package org.jellyfin.mobile.player.sleeptimer

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import org.jellyfin.mobile.R

/**
 * Dialog fragment for choosing a sleep timer duration, or turning off a running timer. Create it with [newInstance].
 *
 * Communicates the result via the Fragment Result API (key: [REQUEST_KEY]) so that
 * the selection survives process death and configuration changes without storing
 * non-serialisable lambda callbacks as instance fields.
 *
 * Callers should register a listener with [REQUEST_KEY] before showing this fragment:
 *   parentFragmentManager.setFragmentResultListener(REQUEST_KEY, viewLifecycleOwner) { _, bundle -> … }
 */
class SleepTimerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val timerActive = requireArguments().getBoolean(ARG_TIMER_ACTIVE)
        val items = listOfNotNull(
            getString(R.string.sleep_timer_15_min),
            getString(R.string.sleep_timer_30_min),
            getString(R.string.sleep_timer_45_min),
            getString(R.string.sleep_timer_60_min),
            getString(R.string.sleep_timer_end_of_chapter),
            if (timerActive) getString(R.string.sleep_timer_turn_off) else null,
        ).toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.sleep_timer)
            .setItems(items) { _, which ->
                val result = when (which) {
                    0 -> bundleOf(KEY_DURATION to SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES.name)
                    1 -> bundleOf(KEY_DURATION to SleepTimerManager.SleepTimerDuration.THIRTY_MINUTES.name)
                    2 -> bundleOf(KEY_DURATION to SleepTimerManager.SleepTimerDuration.FORTY_FIVE_MINUTES.name)
                    ITEM_INDEX_SIXTY_MINUTES -> bundleOf(
                        KEY_DURATION to SleepTimerManager.SleepTimerDuration.SIXTY_MINUTES.name,
                    )
                    ITEM_INDEX_END_OF_CHAPTER -> bundleOf(KEY_END_OF_CHAPTER to true)
                    ITEM_INDEX_TURN_OFF -> bundleOf(KEY_TURN_OFF to true)
                    else -> return@setItems
                }
                parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
            .create()
    }

    companion object {
        const val REQUEST_KEY = "sleep_timer_result"
        const val KEY_DURATION = "duration"
        const val KEY_END_OF_CHAPTER = "end_of_chapter"
        const val KEY_TURN_OFF = "turn_off"
        private const val ARG_TIMER_ACTIVE = "timer_active"

        // Item positions in the `items` array built in onCreateDialog, named since detekt flags
        // the two not in its default-ignored 0/1/2 magic-number allowlist.
        private const val ITEM_INDEX_SIXTY_MINUTES = 3
        private const val ITEM_INDEX_END_OF_CHAPTER = 4
        private const val ITEM_INDEX_TURN_OFF = 5

        /** [timerActive] adds a "Turn off timer" option, shown only while a timer is running. */
        fun newInstance(timerActive: Boolean) = SleepTimerDialogFragment().apply {
            arguments = bundleOf(ARG_TIMER_ACTIVE to timerActive)
        }
    }
}
