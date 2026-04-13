package com.winlator.cmod.feature.setup

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentContainerView
import com.winlator.cmod.R
import com.winlator.cmod.feature.settings.DriversFragment

class SetupWizardDriversDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "setup_wizard_drivers_dialog"
        const val RESULT_KEY = "setup_wizard_drivers_result"
    }

    private val containerViewId = View.generateViewId()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext()).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(false)
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = requireContext()
        val padding = (18 * resources.displayMetrics.density).toInt()
        val headerPadding = (12 * resources.displayMetrics.density).toInt()

        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding)
                setBackgroundColor(Color.parseColor("#161B22"))
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(headerPadding)
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
            }

        val title =
            TextView(context).apply {
                text = context.getString(R.string.settings_drivers_manager)
                setTextColor(Color.parseColor("#E6EDF3"))
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            }

        val closeButton =
            ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                background = null
                setColorFilter(Color.parseColor("#8B949E"))
                setOnClickListener { dismiss() }
            }

        val fragmentContainer =
            FragmentContainerView(context).apply {
                id = containerViewId
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
            }

        header.addView(title)
        header.addView(closeButton)
        root.addView(header)
        root.addView(fragmentContainer)
        return root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager
                .beginTransaction()
                .replace(containerViewId, DriversFragment())
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.88f).toInt(),
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
        super.onDismiss(dialog)
    }
}
