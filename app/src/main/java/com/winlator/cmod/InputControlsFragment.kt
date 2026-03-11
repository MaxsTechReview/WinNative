package com.winlator.cmod

import android.util.Log
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.HttpUtils
import com.winlator.cmod.databinding.InputControlsFragmentV2Binding
import com.winlator.cmod.databinding.ControlsProfileCardBinding
import com.winlator.cmod.databinding.ControlsSettingSliderCardBinding
import com.winlator.cmod.databinding.ControlsSettingToggleCardBinding
import com.winlator.cmod.databinding.ControlsSettingChipsCardBinding
import com.winlator.cmod.databinding.ControlsActionCardBinding
import com.winlator.cmod.databinding.ControlsGyroscopeCardBinding
import com.winlator.cmod.databinding.ControlsAnalogSticksCardBinding
import com.winlator.cmod.databinding.ContentSectionHeaderItemBinding
import com.winlator.cmod.databinding.ControlsExternalControllerCardBinding
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.inputcontrols.Binding
import com.winlator.cmod.inputcontrols.ControlElement
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.ExternalController
import com.winlator.cmod.inputcontrols.ExternalControllerBinding
import com.winlator.cmod.inputcontrols.InputControlsManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.winlator.cmod.inputcontrols.PreferenceKeys
import com.winlator.cmod.math.Mathf
import com.winlator.cmod.widget.InputControlsView
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

class InputControlsFragment(private val selectedProfileId: Int) : Fragment() {

    private var _binding: InputControlsFragmentV2Binding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var manager: InputControlsManager
    private lateinit var preferences: SharedPreferences
    private lateinit var controlAdapter: ControlRowAdapter

    private var currentProfile: ControlsProfile? = null
    private var triggerTypeExpanded = false
    private var analogSticksExpanded = false
    private var gyroscopeExpanded = false
    private var gyroSensorManager: SensorManager? = null
    private var gyroListener: SensorEventListener? = null
    private val expandedControllerIds = mutableSetOf<String>()
    private var activeBindingController: ExternalController? = null
    private var activeBindingViewHolder: ControllerViewHolder? = null
    private var activeBindingL2WasPressed = false
    private var activeBindingR2WasPressed = false

    private var importProfileCallback: ((ControlsProfile) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = InputControlsManager(requireContext())
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val profileId = if (selectedProfileId > 0) selectedProfileId
            else preferences.getInt(PREF_SELECTED_PROFILE_ID, 0)
        currentProfile = if (profileId > 0) manager.getProfile(profileId) else null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InputControlsFragmentV2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.input_controls)

        controlAdapter = ControlRowAdapter()
        binding.RecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = controlAdapter
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        submitRows()
    }

    override fun onStart() {
        super.onStart()
        Log.d("ICFrag", "onStart: currentProfile=${currentProfile?.name}, id=${currentProfile?.id}, elementsLoaded=${currentProfile?.isElementsLoaded}, elementCount=${currentProfile?.elements?.size ?: -1}")
        if (_binding != null) submitRows()
    }

    override fun onDestroyView() {
        stopGyroSensor()
        stopControllerInputCapture()
        binding.RecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        savePreferences()
        super.onDestroy()
    }

    @Suppress("deprecation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE.toInt() && resultCode == Activity.RESULT_OK) {
            try {
                val imported = manager.importProfile(JSONObject(FileUtils.readString(requireContext(), data?.data)))
                importProfileCallback?.invoke(imported)
            } catch (e: Exception) {
                AppUtils.showToast(requireContext(), R.string.unable_to_import_profile)
            }
            importProfileCallback = null
        }
    }

    // Preference persistence

    private fun persistSelectedProfileId() {
        preferences.edit().putInt(PREF_SELECTED_PROFILE_ID, currentProfile?.id ?: 0).apply()
    }

    private fun savePreferences() {
        // Gyro and trigger preferences are saved inline when changed
    }

    private fun stopGyroSensor() {
        gyroListener?.let { listener ->
            gyroSensorManager?.unregisterListener(listener)
        }
        gyroSensorManager = null
        gyroListener = null
    }

    // Controller input capture

    private fun startControllerInputCapture(controller: ExternalController, viewHolder: ControllerViewHolder? = null) {
        activeBindingController = controller
        if (viewHolder != null) activeBindingViewHolder = viewHolder
        activeBindingL2WasPressed = false
        activeBindingR2WasPressed = false
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ctrl = activeBindingController ?: return false
        if (event.deviceId != ctrl.deviceId) return false
        if (event.repeatCount != 0) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            onControllerButtonPressed(ctrl, event.keyCode)
        }
        return true
    }

    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val ctrl = activeBindingController ?: return false
        if (event.deviceId != ctrl.deviceId) return false
        if (!ctrl.updateStateFromMotionEvent(event)) return false

        // L2 / R2 triggers
        val l2Pressed = ctrl.state.isPressed(ExternalController.IDX_BUTTON_L2.toInt()) || ctrl.state.triggerL > 0.5f
        val r2Pressed = ctrl.state.isPressed(ExternalController.IDX_BUTTON_R2.toInt()) || ctrl.state.triggerR > 0.5f
        if (l2Pressed && !activeBindingL2WasPressed) onControllerButtonPressed(ctrl, KeyEvent.KEYCODE_BUTTON_L2)
        if (r2Pressed && !activeBindingR2WasPressed) onControllerButtonPressed(ctrl, KeyEvent.KEYCODE_BUTTON_R2)
        activeBindingL2WasPressed = l2Pressed
        activeBindingR2WasPressed = r2Pressed

        // Joystick axes
        val axes = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
        )
        val values = floatArrayOf(
            ctrl.state.thumbLX, ctrl.state.thumbLY,
            ctrl.state.thumbRX, ctrl.state.thumbRY,
            ctrl.state.getDPadX().toFloat(), ctrl.state.getDPadY().toFloat()
        )
        for (i in axes.indices) {
            val sign = Mathf.sign(values[i])
            if (sign.toInt() != 0) {
                val axisKeyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign)
                onControllerButtonPressed(ctrl, axisKeyCode)
            }
        }
        return true
    }

    private fun onControllerButtonPressed(controller: ExternalController, keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val profile = currentProfile ?: return

        var controllerBinding = controller.getControllerBinding(keyCode)
        if (controllerBinding == null) {
            controllerBinding = ExternalControllerBinding()
            controllerBinding.setKeyCode(keyCode)
            controllerBinding.setBinding(Binding.NONE)
            controller.addControllerBinding(controllerBinding)
            // Ensure the controller is in profile.controllers so save() serializes it
            profile.putController(controller)
            profile.save()
            // Directly update the ViewHolder UI instead of submitRows(),
            // which would call loadControllers() and invalidate activeBindingController
            activeBindingViewHolder?.refreshAfterBindingChange(controller)
        }
    }

    private fun stopControllerInputCapture() {
        activeBindingController = null
        activeBindingViewHolder = null
    }

    // Row building

    private fun submitRows() {
        val rows = buildRows()
        binding.TVEmptyText.isVisible = rows.isEmpty()
        binding.RecyclerView.isVisible = rows.isNotEmpty()
        controlAdapter.submitList(rows)
    }

    private fun buildRows(): List<ControlRow> {
        val rows = mutableListOf<ControlRow>()

        // Profiles
        rows += ControlRow.SectionHeader(R.string.profile)
        rows += ControlRow.ProfileSelector(currentProfile?.id, currentProfile?.name, currentProfile?.elementCountFromFile ?: 0)

        // Overlay
        rows += ControlRow.SectionHeader(R.string.overlay_opacity)
        rows += ControlRow.OverlayOpacity

        // Gyroscope
        rows += ControlRow.SectionHeader(R.string.gyroscope)
        rows += ControlRow.GyroscopeCard(gyroscopeExpanded)

        // Trigger
        rows += ControlRow.SectionHeader(R.string.trigger_type)
        rows += ControlRow.TriggerType(triggerTypeExpanded)

        // Analog Sticks
        rows += ControlRow.SectionHeader(R.string.configure_analog_sticks)
        rows += ControlRow.AnalogSticksCard(analogSticksExpanded)

        // Import / Export
        rows += ControlRow.SectionHeader(R.string.profile)
        rows += ControlRow.ActionCard(
            iconRes = R.drawable.ic_controls_import,
            labelResId = R.string.import_profile,
            action = ACTION_IMPORT
        )
        rows += ControlRow.ActionCard(
            iconRes = R.drawable.ic_controls_export,
            labelResId = R.string.export_profile,
            action = ACTION_EXPORT
        )

        // External Controllers
        rows += ControlRow.SectionHeader(R.string.external_controllers)
        val connectedControllers = ExternalController.getControllers()
        val controllers = if (currentProfile != null) {
            val loaded = currentProfile!!.loadControllers()
            val combined = ArrayList(loaded)
            for (c in connectedControllers) {
                if (!combined.contains(c)) combined.add(c)
            }
            combined
        } else {
            connectedControllers
        }

        if (controllers.isNotEmpty()) {
            controllers.forEach { controller ->
                rows += ControlRow.ExternalControllerRow(controller, controller.id in expandedControllerIds, controller.controllerBindingCount)
            }
        } else {
            rows += ControlRow.EmptyState(R.string.no_items_to_display)
        }

        return rows
    }

    // Actions

    private fun onProfileSelected(profile: ControlsProfile?) {
        currentProfile = profile
        persistSelectedProfileId()
        submitRows()
    }

    private fun openControlsEditor() {
        val profile = currentProfile
        if (profile != null) {
            Log.d("ICFrag", "openControlsEditor: BEFORE launch, id=${profile.id}, elementsLoaded=${profile.isElementsLoaded}, elementCount=${profile.elements?.size ?: 0}")
            val intent = Intent(requireContext(), ControlsEditorActivity::class.java)
            intent.putExtra("profile_id", profile.id)
            startActivity(intent)
        } else {
            AppUtils.showToast(requireContext(), R.string.no_profile_selected)
        }
    }

    private fun addProfile() {
        ContentDialog.prompt(requireContext(), R.string.profile_name, null) { name ->
            currentProfile = manager.createProfile(name)
            persistSelectedProfileId()
            submitRows()
        }
    }

    private fun editProfile() {
        val profile = currentProfile
        if (profile != null) {
            ContentDialog.prompt(requireContext(), R.string.profile_name, profile.name) { name ->
                profile.name = name
                profile.save()
                submitRows()
            }
        } else {
            AppUtils.showToast(requireContext(), R.string.no_profile_selected)
        }
    }

    private fun duplicateProfile() {
        val profile = currentProfile
        if (profile != null) {
            ContentDialog.confirm(requireContext(), R.string.do_you_want_to_duplicate_this_profile) {
                currentProfile = manager.duplicateProfile(profile)
                persistSelectedProfileId()
                submitRows()
            }
        } else {
            AppUtils.showToast(requireContext(), R.string.no_profile_selected)
        }
    }

    private fun removeProfile() {
        val profile = currentProfile
        if (profile != null) {
            ContentDialog.confirm(requireContext(), R.string.do_you_want_to_remove_this_profile) {
                manager.removeProfile(profile)
                currentProfile = null
                persistSelectedProfileId()
                submitRows()
            }
        } else {
            AppUtils.showToast(requireContext(), R.string.no_profile_selected)
        }
    }

    private fun showProfilePicker() {
        val profiles = manager.profiles
        if (profiles.isEmpty()) {
            AppUtils.showToast(requireContext(), R.string.no_profile_found)
            return
        }
        val names = profiles.map { it.name }.toTypedArray()
        var selectedIndex = profiles.indexOfFirst { it == currentProfile }
        if (selectedIndex < 0) selectedIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_profile)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                onProfileSelected(profiles[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Suppress("deprecation")
    private fun importProfile() {
        openProfileFile()
    }

    private fun openProfileFile() {
        importProfileCallback = { imported ->
            currentProfile = imported
            persistSelectedProfileId()
            submitRows()
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        @Suppress("deprecation")
        activity?.startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE.toInt())
    }

    private fun downloadProfileList() {
        val activity = activity as? MainActivity ?: return
        activity.preloaderDialog.show(R.string.loading)
        HttpUtils.download(String.format(INPUT_CONTROLS_URL, "index.txt")) { content ->
            activity.runOnUiThread {
                activity.preloaderDialog.close()
                if (content != null) {
                    val items = content.split("\n").toTypedArray()
                    ContentDialog.showMultipleChoiceList(activity, R.string.import_profile, items) { positions ->
                        if (positions.isNotEmpty()) {
                            ContentDialog.confirm(activity, R.string.do_you_want_to_download_the_selected_profiles) {
                                downloadSelectedProfiles(items, positions)
                            }
                        }
                    }
                } else {
                    AppUtils.showToast(activity, R.string.unable_to_load_profile_list)
                }
            }
        }
    }

    private fun downloadSelectedProfiles(items: Array<String>, positions: ArrayList<Int>) {
        val activity = activity as? MainActivity ?: return
        activity.preloaderDialog.show(R.string.downloading_file)
        currentProfile = null
        persistSelectedProfileId()
        val processedCount = AtomicInteger()

        for (position in positions) {
            HttpUtils.download(String.format(INPUT_CONTROLS_URL, items[position])) { content ->
                try {
                    if (content != null) manager.importProfile(JSONObject(content))
                } catch (_: JSONException) {
                }
                if (processedCount.incrementAndGet() == positions.size) {
                    activity.runOnUiThread {
                        activity.preloaderDialog.close()
                        submitRows()
                    }
                }
            }
        }
    }

    private fun exportProfile() {
        val profile = currentProfile
        if (profile != null) {
            val exportedFile = manager.exportProfile(profile)
            if (exportedFile != null) {
                AppUtils.showToast(
                    requireContext(),
                    getString(R.string.profile_exported_to) + " " + exportedFile.path
                )
            }
        } else {
            AppUtils.showToast(requireContext(), R.string.no_profile_selected)
        }
    }

    // Gyroscope config dialog

    private fun showGyroConfigDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.gyro_config_dialog, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setTitle("Gyroscope Configuration")

        val inputControlsView = InputControlsView(requireContext(), true)
        inputControlsView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        inputControlsView.setEditMode(false)
        inputControlsView.initializeStickElement(600f, 250f, 2.0f)
        inputControlsView.stickElement.setType(ControlElement.Type.STICK)

        val placeholder: FrameLayout = dialogView.findViewById(R.id.stick_placeholder)
        placeholder.addView(inputControlsView)
        inputControlsView.invalidate()

        val btnResetCenter: Button = dialogView.findViewById(R.id.btnResetCenter)
        btnResetCenter.setOnClickListener {
            inputControlsView.resetStickPosition()
            inputControlsView.invalidate()
        }

        val sbGyroXSensitivity: SeekBar = dialogView.findViewById(R.id.SBGyroXSensitivity)
        val sbGyroYSensitivity: SeekBar = dialogView.findViewById(R.id.SBGyroYSensitivity)
        val sbGyroSmoothing: SeekBar = dialogView.findViewById(R.id.SBGyroSmoothing)
        val sbGyroDeadzone: SeekBar = dialogView.findViewById(R.id.SBGyroDeadzone)
        val cbInvertGyroX: CheckBox = dialogView.findViewById(R.id.CBInvertGyroX)
        val cbInvertGyroY: CheckBox = dialogView.findViewById(R.id.CBInvertGyroY)
        val tvGyroXSensitivity: TextView = dialogView.findViewById(R.id.TVGyroXSensitivity)
        val tvGyroYSensitivity: TextView = dialogView.findViewById(R.id.TVGyroYSensitivity)
        val tvGyroSmoothing: TextView = dialogView.findViewById(R.id.TVGyroSmoothing)
        val tvGyroDeadzone: TextView = dialogView.findViewById(R.id.TVGyroDeadzone)

        sbGyroXSensitivity.progress = (preferences.getFloat("gyro_x_sensitivity", 1.0f) * 100).toInt()
        sbGyroYSensitivity.progress = (preferences.getFloat("gyro_y_sensitivity", 1.0f) * 100).toInt()
        sbGyroSmoothing.progress = (preferences.getFloat("gyro_smoothing", 0.9f) * 100).toInt()
        sbGyroDeadzone.progress = (preferences.getFloat("gyro_deadzone", 0.05f) * 100).toInt()
        cbInvertGyroX.isChecked = preferences.getBoolean("invert_gyro_x", false)
        cbInvertGyroY.isChecked = preferences.getBoolean("invert_gyro_y", false)

        tvGyroXSensitivity.text = "X Sensitivity: ${sbGyroXSensitivity.progress}%"
        tvGyroYSensitivity.text = "Y Sensitivity: ${sbGyroYSensitivity.progress}%"
        tvGyroSmoothing.text = "Smoothing: ${sbGyroSmoothing.progress}%"
        tvGyroDeadzone.text = "Deadzone: ${sbGyroDeadzone.progress}%"

        val seekBarListener = { seekBar: SeekBar, textView: TextView, prefix: String ->
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    textView.text = "$prefix: $progress%"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        seekBarListener(sbGyroXSensitivity, tvGyroXSensitivity, "X Sensitivity")
        seekBarListener(sbGyroYSensitivity, tvGyroYSensitivity, "Y Sensitivity")
        seekBarListener(sbGyroSmoothing, tvGyroSmoothing, "Smoothing")
        seekBarListener(sbGyroDeadzone, tvGyroDeadzone, "Deadzone")

        val smoothingFactor = preferences.getFloat("gyro_smoothing", 0.9f)
        val gyroDeadzone = preferences.getFloat("gyro_deadzone", 0.05f)
        val invertGyroX = preferences.getBoolean("invert_gyro_x", false)
        val invertGyroY = preferences.getBoolean("invert_gyro_y", false)
        val gyroSensitivityX = preferences.getFloat("gyro_x_sensitivity", 1.0f)
        val gyroSensitivityY = preferences.getFloat("gyro_y_sensitivity", 1.0f)

        val sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val smoothGyroX = floatArrayOf(0f)
        val smoothGyroY = floatArrayOf(0f)

        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var rawGyroX = event.values[0]
                var rawGyroY = event.values[1]

                if (Math.abs(rawGyroX) < gyroDeadzone) rawGyroX = 0f
                if (Math.abs(rawGyroY) < gyroDeadzone) rawGyroY = 0f

                if (invertGyroX) rawGyroX = -rawGyroX
                if (invertGyroY) rawGyroY = -rawGyroY

                rawGyroX *= gyroSensitivityX
                rawGyroY *= gyroSensitivityY

                smoothGyroX[0] = smoothGyroX[0] * smoothingFactor + rawGyroX * (1 - smoothingFactor)
                smoothGyroY[0] = smoothGyroY[0] * smoothingFactor + rawGyroY * (1 - smoothingFactor)

                val stickElement = inputControlsView.stickElement ?: run {
                    Log.w(TAG, "Gyro calibration event ignored because stickElement is null")
                    return
                }
                val stickCenterX = stickElement.x
                val stickCenterY = stickElement.y
                val stickRadius = 100

                var newX = stickElement.currentPosition.x + smoothGyroX[0]
                var newY = stickElement.currentPosition.y + smoothGyroY[0]

                val deltaX = newX - stickCenterX
                val deltaY = newY - stickCenterY
                val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()

                if (distance > stickRadius) {
                    val scaleFactor = stickRadius / distance
                    newX = stickCenterX + deltaX * scaleFactor
                    newY = stickCenterY + deltaY * scaleFactor
                }

                inputControlsView.updateStickPosition(newX, newY)
                inputControlsView.invalidate()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(gyroListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME)

        builder.setPositiveButton("OK") { _, _ ->
            preferences.edit().apply {
                putFloat("gyro_x_sensitivity", sbGyroXSensitivity.progress / 100.0f)
                putFloat("gyro_y_sensitivity", sbGyroYSensitivity.progress / 100.0f)
                putFloat("gyro_smoothing", sbGyroSmoothing.progress / 100.0f)
                putFloat("gyro_deadzone", sbGyroDeadzone.progress / 100.0f)
                putBoolean("invert_gyro_x", cbInvertGyroX.isChecked)
                putBoolean("invert_gyro_y", cbInvertGyroY.isChecked)
                apply()
            }
            sensorManager.unregisterListener(gyroListener)
        }

        builder.setNegativeButton("Cancel") { _, _ ->
            sensorManager.unregisterListener(gyroListener)
        }

        builder.setOnDismissListener {
            sensorManager.unregisterListener(gyroListener)
        }

        builder.create().show()
    }

    // Adapter

    private inner class ControlRowAdapter :
        ListAdapter<ControlRow, RecyclerView.ViewHolder>(DiffCallback) {

        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is ControlRow.SectionHeader -> VIEW_TYPE_HEADER
            is ControlRow.ProfileSelector -> VIEW_TYPE_PROFILE
            is ControlRow.OverlayOpacity -> VIEW_TYPE_SLIDER
            is ControlRow.GyroscopeCard -> VIEW_TYPE_GYRO
            is ControlRow.AnalogSticksCard -> VIEW_TYPE_ANALOG
            is ControlRow.TriggerType -> VIEW_TYPE_CHIPS
            is ControlRow.ActionCard -> VIEW_TYPE_ACTION
            is ControlRow.ExternalControllerRow -> VIEW_TYPE_CONTROLLER
            is ControlRow.EmptyState -> VIEW_TYPE_EMPTY
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderViewHolder(
                    ContentSectionHeaderItemBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_PROFILE -> ProfileViewHolder(
                    ControlsProfileCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_SLIDER -> SliderViewHolder(
                    ControlsSettingSliderCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_GYRO -> GyroViewHolder(
                    ControlsGyroscopeCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_ANALOG -> AnalogSticksViewHolder(
                    ControlsAnalogSticksCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_CHIPS -> ChipsViewHolder(
                    ControlsSettingChipsCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_ACTION -> ActionViewHolder(
                    ControlsActionCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_CONTROLLER -> ControllerViewHolder(
                    ControlsExternalControllerCardBinding.inflate(inflater, parent, false)
                )
                VIEW_TYPE_EMPTY -> EmptyViewHolder(
                    inflater.inflate(R.layout.content_section_header_item, parent, false)
                )
                else -> throw IllegalArgumentException("Unknown viewType: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is ControlRow.SectionHeader -> (holder as HeaderViewHolder).bind(row)
                is ControlRow.ProfileSelector -> (holder as ProfileViewHolder).bind()
                is ControlRow.OverlayOpacity -> (holder as SliderViewHolder).bind()
                is ControlRow.GyroscopeCard -> (holder as GyroViewHolder).bind(row.expanded)
            is ControlRow.AnalogSticksCard -> (holder as AnalogSticksViewHolder).bind(row.expanded)
            is ControlRow.TriggerType -> (holder as ChipsViewHolder).bindTriggerType(row.expanded)
                is ControlRow.ActionCard -> (holder as ActionViewHolder).bind(row)
                is ControlRow.ExternalControllerRow -> (holder as ControllerViewHolder).bind(row)
                is ControlRow.EmptyState -> (holder as EmptyViewHolder).bind(row)
            }
        }
    }

    // ViewHolders

    private inner class HeaderViewHolder(
        private val itemBinding: ContentSectionHeaderItemBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(header: ControlRow.SectionHeader) {
            itemBinding.TVSectionTitle.setText(header.titleResId)
        }
    }

    private inner class ProfileViewHolder(
        private val itemBinding: ControlsProfileCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind() {
            val profile = currentProfile
            if (profile != null) {
                itemBinding.TVProfileName.text = profile.name
                val elementCount = profile.elementCountFromFile
                Log.d("ICFrag", "ProfileViewHolder.bind: name=${profile.name}, id=${profile.id}, elementCountFromFile=$elementCount")
                itemBinding.TVProfileSubtitle.text = "$elementCount elements"
            } else {
                itemBinding.TVProfileName.setText(R.string.select_profile)
                itemBinding.TVProfileSubtitle.text = getString(R.string.no_profile_selected)
            }

            itemBinding.root.setOnClickListener { showProfilePicker() }
            itemBinding.BTOpenEditor.setOnClickListener { openControlsEditor() }
            itemBinding.BTAddProfile.setOnClickListener { addProfile() }
            itemBinding.BTEditProfile.setOnClickListener { editProfile() }
            itemBinding.BTDuplicateProfile.setOnClickListener { duplicateProfile() }
            itemBinding.BTRemoveProfile.setOnClickListener { removeProfile() }
        }
    }

    private inner class SliderViewHolder(
        private val itemBinding: ControlsSettingSliderCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind() {
            itemBinding.IVIcon.setImageResource(R.drawable.ic_controls_opacity)
            itemBinding.TVLabel.setText(R.string.overlay_opacity)

            val currentOpacity = (preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY) * 100).toInt()
            itemBinding.TVValue.text = "$currentOpacity%"
            itemBinding.SBSlider.min = 10
            itemBinding.SBSlider.max = 100
            itemBinding.SBSlider.progress = currentOpacity

            itemBinding.SBSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVValue.text = "$progress%"
                    if (fromUser) {
                        val rounded = Mathf.roundTo(progress.toFloat(), 5f).toInt()
                        seekBar.progress = rounded
                        itemBinding.TVValue.text = "$rounded%"
                        preferences.edit().putFloat("overlay_opacity", rounded / 100.0f).apply()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
    }

    private inner class GyroViewHolder(
        private val itemBinding: ControlsGyroscopeCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        private var inputControlsView: InputControlsView? = null

        fun bind(expanded: Boolean) {
            itemBinding.IVIcon.setImageResource(R.drawable.icon_motion_controls)
            itemBinding.SWToggle.isChecked = preferences.getBoolean("gyro_enabled", false)
            itemBinding.SWToggle.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("gyro_enabled", isChecked).apply()
            }

            // Mode chips
            val options = listOf("Hold", "Toggle")
            val selectedIndex = preferences.getInt("gyro_mode", 0)
            itemBinding.LLChips.removeAllViews()
            val inflater = LayoutInflater.from(itemBinding.root.context)
            options.forEachIndexed { index, label ->
                val chip = inflater.inflate(
                    R.layout.content_type_tab_item,
                    itemBinding.LLChips,
                    false
                ) as TextView
                chip.text = label
                chip.isSelected = index == selectedIndex
                chip.setOnClickListener {
                    preferences.edit().putInt("gyro_mode", index).apply()
                    for (i in 0 until itemBinding.LLChips.childCount) {
                        itemBinding.LLChips.getChildAt(i).isSelected = i == index
                    }
                }
                itemBinding.LLChips.addView(chip)
            }

            // Activator button selector
            loadActivatorValue()
            itemBinding.TVActivatorValue.setOnClickListener {
                showActivatorPicker()
            }

            // Right Stick Gyro toggle
            itemBinding.SWRightStickGyro.setOnCheckedChangeListener(null)
            itemBinding.SWRightStickGyro.isChecked = preferences.getBoolean("process_gyro_with_left_trigger", false)
            itemBinding.SWRightStickGyro.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("process_gyro_with_left_trigger", isChecked).apply()
            }

            // Calibrate expandable subcard
            ExpandableCardHelper.applyTransition(
                itemRoot = itemBinding.root,
                chevron = itemBinding.IVExpandChevron,
                contentView = itemBinding.LLCalibrateContent,
                expanded = expanded
            )

            ExpandableCardHelper.setupClickListeners(
                itemBinding.LLCalibrateHeader,
                itemBinding.FLChevronContainer,
                itemBinding.IVExpandChevron
            ) {
                gyroscopeExpanded = !gyroscopeExpanded
                if (!gyroscopeExpanded) {
                    stopGyroSensor()
                }
                submitRows()
            }

            if (expanded) {
                setupStickVisualization()
                startGyroSensor()
            } else {
                stopGyroSensor()
                itemBinding.FLStickPlaceholder.removeAllViews()
                inputControlsView = null
            }

            // Reset button
            itemBinding.TVResetStick.setOnClickListener {
                inputControlsView?.let {
                    it.resetStickPosition()
                    it.invalidate()
                }
            }

            loadGyroValues()
        }

        private fun loadActivatorValue() {
            val ctx = itemBinding.root.context
            val buttonNames = ctx.resources.getStringArray(R.array.button_options)
            val buttonKeycodes = ctx.resources.getIntArray(R.array.button_keycodes)
            val currentKeycode = preferences.getInt("gyro_trigger_button", KeyEvent.KEYCODE_BUTTON_L1)
            val index = buttonKeycodes.indexOf(currentKeycode)
            itemBinding.TVActivatorValue.text = if (index >= 0) buttonNames[index] else buttonNames[6]
        }

        private fun showActivatorPicker() {
            val ctx = itemBinding.root.context
            val buttonNames = ctx.resources.getStringArray(R.array.button_options)
            val buttonKeycodes = ctx.resources.getIntArray(R.array.button_keycodes)

            val dialog = ContentDialog(ctx)
            dialog.contentView.findViewById<View>(R.id.BTConfirm).visibility = View.GONE

            // Compact title
            val tvTitle = dialog.findViewById<TextView>(R.id.TVTitle)
            tvTitle.textSize = 16f
            val titleRow = tvTitle.parent as View
            titleRow.minimumHeight = (24 * ctx.resources.displayMetrics.density).toInt()
            val titleBar = dialog.findViewById<View>(R.id.LLTitleBar) as android.widget.LinearLayout
            val divider = titleBar.getChildAt(1)
            val dp = ctx.resources.displayMetrics.density
            (divider.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                topMargin = (6 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            }

            // Compact cancel button
            val btnCancel = dialog.findViewById<Button>(R.id.BTCancel)
            btnCancel.textSize = 12f
            btnCancel.minimumHeight = 0
            btnCancel.minHeight = 0
            val cancelPadV = (4 * dp).toInt()
            val cancelPadH = (12 * dp).toInt()
            btnCancel.setPadding(cancelPadH, cancelPadV, cancelPadH, cancelPadV)

            // Compact bottom bar margins
            val bottomBar = dialog.findViewById<View>(R.id.LLBottomBar) as android.widget.LinearLayout
            (bottomBar.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin = (4 * dp).toInt()
            val bottomDivider = bottomBar.getChildAt(0)
            (bottomDivider.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                topMargin = (2 * dp).toInt()
                bottomMargin = (6 * dp).toInt()
            }

            // Compact dialog padding
            val root = dialog.contentView as View
            val padH = (14 * dp).toInt()
            val padTop = (12 * dp).toInt()
            val padBot = (10 * dp).toInt()
            root.setPadding(padH, padTop, padH, padBot)

            val listView = dialog.findViewById<android.widget.ListView>(R.id.ListView)
            listView.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            listView.layoutParams.height = (140 * ctx.resources.displayMetrics.density).toInt()
            listView.choiceMode = android.widget.ListView.CHOICE_MODE_NONE

            val currentKeycode = preferences.getInt("gyro_trigger_button", KeyEvent.KEYCODE_BUTTON_L1)
            val checkedIndex = buttonKeycodes.indexOf(currentKeycode)

            val adapter = object : android.widget.BaseAdapter() {
                private var selectedPos = if (checkedIndex >= 0) checkedIndex else 6
                override fun getCount() = buttonNames.size
                override fun getItem(pos: Int) = buttonNames[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                    val row = convertView ?: LayoutInflater.from(ctx).inflate(R.layout.compact_single_choice_item, parent, false)
                    row.findViewById<TextView>(android.R.id.text1).text = buttonNames[pos]
                    row.findViewById<android.widget.RadioButton>(R.id.RBIndicator).isChecked = pos == selectedPos
                    return row
                }
                fun select(pos: Int) { selectedPos = pos; notifyDataSetChanged() }
            }
            listView.adapter = adapter
            listView.visibility = View.VISIBLE
            listView.setOnItemClickListener { _, _, position, _ ->
                adapter.select(position)
                preferences.edit().putInt("gyro_trigger_button", buttonKeycodes[position]).apply()
                itemBinding.TVActivatorValue.text = buttonNames[position]
                dialog.dismiss()
            }
            dialog.setTitle(R.string.gyro_activator_button)
            dialog.show()
        }

        private fun setupStickVisualization() {
            itemBinding.FLStickPlaceholder.removeAllViews()
            val ctx = itemBinding.root.context
            val icv = InputControlsView(ctx, true)
            icv.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            icv.setEditMode(false)
            itemBinding.FLStickPlaceholder.addView(icv)
            icv.post {
                val centerX = icv.width / 2f
                val centerY = icv.height / 2f
                icv.initializeStickElement(centerX, centerY, 1.5f)
                icv.stickElement.setType(ControlElement.Type.STICK)
                icv.invalidate()
            }
            inputControlsView = icv
        }

        private fun loadGyroValues() {
            val xSens = (preferences.getFloat("gyro_x_sensitivity", 1.0f) * 100).toInt()
            val ySens = (preferences.getFloat("gyro_y_sensitivity", 1.0f) * 100).toInt()
            val smoothing = (preferences.getFloat("gyro_smoothing", 0.9f) * 100).toInt()
            val deadzone = (preferences.getFloat("gyro_deadzone", 0.05f) * 100).toInt()

            itemBinding.TVGyroXSensitivity.text = "X Sensitivity: $xSens%"
            itemBinding.SBGyroXSensitivity.progress = xSens
            itemBinding.TVGyroYSensitivity.text = "Y Sensitivity: $ySens%"
            itemBinding.SBGyroYSensitivity.progress = ySens
            itemBinding.TVGyroSmoothing.text = "Smoothing: $smoothing%"
            itemBinding.SBGyroSmoothing.progress = smoothing
            itemBinding.TVGyroDeadzone.text = "Deadzone: $deadzone%"
            itemBinding.SBGyroDeadzone.progress = deadzone

            // Remove old listeners before setting checked state
            itemBinding.SWInvertGyroX.setOnCheckedChangeListener(null)
            itemBinding.SWInvertGyroY.setOnCheckedChangeListener(null)

            itemBinding.SWInvertGyroX.isChecked = preferences.getBoolean("invert_gyro_x", false)
            itemBinding.SWInvertGyroY.isChecked = preferences.getBoolean("invert_gyro_y", false)

            // Seekbar listeners
            itemBinding.SBGyroXSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVGyroXSensitivity.text = "X Sensitivity: $progress%"
                    if (fromUser) preferences.edit().putFloat("gyro_x_sensitivity", progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            itemBinding.SBGyroYSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVGyroYSensitivity.text = "Y Sensitivity: $progress%"
                    if (fromUser) preferences.edit().putFloat("gyro_y_sensitivity", progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            itemBinding.SBGyroSmoothing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVGyroSmoothing.text = "Smoothing: $progress%"
                    if (fromUser) preferences.edit().putFloat("gyro_smoothing", progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            itemBinding.SBGyroDeadzone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVGyroDeadzone.text = "Deadzone: $progress%"
                    if (fromUser) preferences.edit().putFloat("gyro_deadzone", progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            // Toggle listeners
            itemBinding.SWInvertGyroX.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("invert_gyro_x", isChecked).apply()
            }
            itemBinding.SWInvertGyroY.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean("invert_gyro_y", isChecked).apply()
            }
        }

        private fun startGyroSensor() {
            stopGyroSensor()
            val ctx = itemBinding.root.context
            val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return

            val smoothingFactor = preferences.getFloat("gyro_smoothing", 0.9f)
            val gyroDeadzone = preferences.getFloat("gyro_deadzone", 0.05f)
            val invertGyroX = preferences.getBoolean("invert_gyro_x", false)
            val invertGyroY = preferences.getBoolean("invert_gyro_y", false)
            val gyroSensitivityX = preferences.getFloat("gyro_x_sensitivity", 1.0f)
            val gyroSensitivityY = preferences.getFloat("gyro_y_sensitivity", 1.0f)

            val smoothGyroX = floatArrayOf(0f)
            val smoothGyroY = floatArrayOf(0f)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val icv = inputControlsView ?: return
                    var rawGyroX = event.values[0]
                    var rawGyroY = event.values[1]

                    if (Math.abs(rawGyroX) < gyroDeadzone) rawGyroX = 0f
                    if (Math.abs(rawGyroY) < gyroDeadzone) rawGyroY = 0f

                    if (invertGyroX) rawGyroX = -rawGyroX
                    if (invertGyroY) rawGyroY = -rawGyroY

                    rawGyroX *= gyroSensitivityX
                    rawGyroY *= gyroSensitivityY

                    smoothGyroX[0] = smoothGyroX[0] * smoothingFactor + rawGyroX * (1 - smoothingFactor)
                    smoothGyroY[0] = smoothGyroY[0] * smoothingFactor + rawGyroY * (1 - smoothingFactor)

                    val stickElement = icv.stickElement ?: run {
                        Log.w(TAG, "Gyro preview event ignored because stickElement is null")
                        return
                    }
                    val stickCenterX = stickElement.x
                    val stickCenterY = stickElement.y
                    val stickRadius = 100

                    var newX = stickElement.currentPosition.x + smoothGyroX[0]
                    var newY = stickElement.currentPosition.y + smoothGyroY[0]

                    val deltaX = newX - stickCenterX
                    val deltaY = newY - stickCenterY
                    val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()

                    if (distance > stickRadius) {
                        val scaleFactor = stickRadius / distance
                        newX = stickCenterX + deltaX * scaleFactor
                        newY = stickCenterY + deltaY * scaleFactor
                    }

                    icv.updateStickPosition(newX, newY)
                    icv.invalidate()
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME)
            gyroSensorManager = sensorManager
            gyroListener = listener
        }
    }

    private inner class AnalogSticksViewHolder(
        private val itemBinding: ControlsAnalogSticksCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        private var currentStick = 0 // 0 = left, 1 = right

        fun bind(expanded: Boolean) {
            itemBinding.IVIcon.setImageResource(R.drawable.ic_controls_analog)

            // Stick tabs
            val tabs = listOf("Left Stick", "Right Stick")
            itemBinding.LLStickTabs.removeAllViews()
            val inflater = LayoutInflater.from(itemBinding.root.context)
            tabs.forEachIndexed { index, label ->
                val tab = inflater.inflate(
                    R.layout.content_type_tab_item,
                    itemBinding.LLStickTabs,
                    false
                ) as TextView
                tab.text = label
                tab.isSelected = index == currentStick
                tab.setOnClickListener {
                    currentStick = index
                    for (i in 0 until itemBinding.LLStickTabs.childCount) {
                        itemBinding.LLStickTabs.getChildAt(i).isSelected = i == index
                    }
                    loadStickValues()
                }
                itemBinding.LLStickTabs.addView(tab)
            }

            // Expand/collapse logic
            ExpandableCardHelper.applyTransition(
                itemRoot = itemBinding.root,
                chevron = itemBinding.IVExpandChevron,
                contentView = itemBinding.LLSettingsContent,
                expanded = expanded
            )

            ExpandableCardHelper.setupClickListeners(
                itemBinding.FLChevronContainer,
                itemBinding.IVExpandChevron
            ) {
                analogSticksExpanded = !analogSticksExpanded
                submitRows()
            }

            loadStickValues()
        }

        private fun loadStickValues() {
            val isLeft = currentStick == 0
            val deadzoneKey = if (isLeft) PreferenceKeys.DEADZONE_LEFT else PreferenceKeys.DEADZONE_RIGHT
            val sensitivityKey = if (isLeft) PreferenceKeys.SENSITIVITY_LEFT else PreferenceKeys.SENSITIVITY_RIGHT
            val invertXKey = if (isLeft) PreferenceKeys.INVERT_LEFT_X else PreferenceKeys.INVERT_RIGHT_X
            val invertYKey = if (isLeft) PreferenceKeys.INVERT_LEFT_Y else PreferenceKeys.INVERT_RIGHT_Y

            val deadzone = (preferences.getFloat(deadzoneKey, 0.1f) * 100).toInt()
            val sensitivity = (preferences.getFloat(sensitivityKey, 1.0f) * 100).toInt()

            itemBinding.TVDeadzone.text = "Deadzone: $deadzone%"
            itemBinding.SBDeadzone.progress = deadzone
            itemBinding.TVSensitivity.text = "Sensitivity: $sensitivity%"
            itemBinding.SBSensitivity.progress = sensitivity

            // Remove old listeners before setting checked state
            itemBinding.SWInvertX.setOnCheckedChangeListener(null)
            itemBinding.SWInvertY.setOnCheckedChangeListener(null)
            itemBinding.SWSquareDeadzone.setOnCheckedChangeListener(null)

            itemBinding.SWInvertX.isChecked = preferences.getBoolean(invertXKey, false)
            itemBinding.SWInvertY.isChecked = preferences.getBoolean(invertYKey, false)

            // Square deadzone only for left stick
            itemBinding.LLSquareDeadzone.visibility = if (isLeft) View.VISIBLE else View.GONE
            if (isLeft) {
                itemBinding.SWSquareDeadzone.isChecked = preferences.getBoolean(PreferenceKeys.SQUARE_DEADZONE_LEFT, false)
            }

            // Seekbar listeners
            itemBinding.SBDeadzone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVDeadzone.text = "Deadzone: $progress%"
                    if (fromUser) preferences.edit().putFloat(deadzoneKey, progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            itemBinding.SBSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    itemBinding.TVSensitivity.text = "Sensitivity: $progress%"
                    if (fromUser) preferences.edit().putFloat(sensitivityKey, progress / 100.0f).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            // Toggle listeners
            itemBinding.SWInvertX.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean(invertXKey, isChecked).apply()
            }
            itemBinding.SWInvertY.setOnCheckedChangeListener { _, isChecked ->
                preferences.edit().putBoolean(invertYKey, isChecked).apply()
            }
            if (isLeft) {
                itemBinding.SWSquareDeadzone.setOnCheckedChangeListener { _, isChecked ->
                    preferences.edit().putBoolean(PreferenceKeys.SQUARE_DEADZONE_LEFT, isChecked).apply()
                }
            }
        }
    }

    private inner class ChipsViewHolder(
        private val itemBinding: ControlsSettingChipsCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        fun bindTriggerType(expanded: Boolean) {
            itemBinding.IVIcon.setImageResource(R.drawable.ic_controls_trigger)
            itemBinding.TVLabel.setText(R.string.trigger_type)

            val descRaw = itemBinding.root.context.getString(R.string.help_trigger_mode).trim()
            itemBinding.TVDescription.text = android.text.Html.fromHtml(descRaw, android.text.Html.FROM_HTML_MODE_LEGACY)

            ExpandableCardHelper.applyTransition(
                itemRoot = itemBinding.root,
                chevron = itemBinding.IVExpandChevron,
                contentView = itemBinding.TVDescription,
                expanded = expanded
            )

            ExpandableCardHelper.setupClickListeners(
                itemBinding.LLDescriptionContainer,
                itemBinding.FLChevronContainer,
                itemBinding.IVExpandChevron
            ) {
                triggerTypeExpanded = !triggerTypeExpanded
                submitRows()
            }

            val options = listOf(
                getString(R.string.as_button),
                getString(R.string.as_axis)
            )
            val selectedIndex = preferences.getInt("trigger_type", ExternalController.TRIGGER_IS_AXIS.toInt())

            setupChips(options, selectedIndex) { index ->
                preferences.edit().putInt("trigger_type", index).apply()
            }
        }

        private fun setupChips(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
            itemBinding.LLChips.removeAllViews()
            val inflater = LayoutInflater.from(itemBinding.root.context)

            options.forEachIndexed { index, label ->
                val chip = inflater.inflate(
                    R.layout.content_type_tab_item,
                    itemBinding.LLChips,
                    false
                ) as TextView
                chip.text = label
                chip.isSelected = index == selectedIndex

                chip.setOnClickListener {
                    onSelected(index)
                    // Update selection state on all chips
                    for (i in 0 until itemBinding.LLChips.childCount) {
                        itemBinding.LLChips.getChildAt(i).isSelected = i == index
                    }
                }

                itemBinding.LLChips.addView(chip)
            }
        }
    }

    private inner class ActionViewHolder(
        private val itemBinding: ControlsActionCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(row: ControlRow.ActionCard) {
            itemBinding.IVIcon.setImageResource(row.iconRes)
            itemBinding.TVLabel.setText(row.labelResId)
            itemBinding.TVSubtitle.isVisible = false

            itemBinding.root.setOnClickListener {
                when (row.action) {
                    ACTION_CALIBRATE_GYRO -> showGyroConfigDialog()
                    ACTION_IMPORT -> importProfile()
                    ACTION_EXPORT -> exportProfile()
                }
            }
        }
    }

    private inner class ControllerViewHolder(
        private val itemBinding: ControlsExternalControllerCardBinding
    ) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(row: ControlRow.ExternalControllerRow) {
            val controller = row.controller
            val expanded = row.expanded
            // Header
            itemBinding.IVIcon.setImageResource(R.drawable.icon_gamepad)
            itemBinding.TVControllerName.text = controller.name
            val bindingCount = controller.controllerBindingCount
            itemBinding.TVBindingCount.text = "$bindingCount ${getString(R.string.bindings)}"

            val tintColor = if (controller.isConnected)
                ContextCompat.getColor(requireContext(), R.color.colorAccent)
            else 0xffe57373.toInt()
            ImageViewCompat.setImageTintList(itemBinding.IVIcon, ColorStateList.valueOf(tintColor))

            // Remove button
            itemBinding.BTRemove.isVisible = bindingCount > 0 && currentProfile != null
            if (bindingCount > 0 && currentProfile != null) {
                itemBinding.BTRemove.setOnClickListener {
                    ContentDialog.confirm(requireContext(), R.string.do_you_want_to_remove_this_controller) {
                        expandedControllerIds.remove(controller.id)
                        stopControllerInputCapture()
                        currentProfile?.removeController(controller)
                        currentProfile?.save()
                        submitRows()
                    }
                }
            }

            // Show bindings submenu only when a profile is selected
            itemBinding.LLBindingsContainer.isVisible = currentProfile != null

            // Expandable bindings subcard
            ExpandableCardHelper.applyTransition(
                itemRoot = itemBinding.root,
                chevron = itemBinding.IVExpandChevron,
                contentView = itemBinding.LLBindingsContent,
                expanded = expanded
            )

            ExpandableCardHelper.setupClickListeners(
                itemBinding.LLBindingsHeader,
                itemBinding.FLChevronContainer,
                itemBinding.IVExpandChevron
            ) {
                if (currentProfile == null) {
                    AppUtils.showToast(requireContext(), R.string.no_profile_selected)
                    return@setupClickListeners
                }
                if (controller.id in expandedControllerIds) {
                    expandedControllerIds.remove(controller.id)
                    stopControllerInputCapture()
                } else {
                    // Close any other expanded controller first
                    expandedControllerIds.clear()
                    stopControllerInputCapture()
                    expandedControllerIds.add(controller.id)
                    // Ensure controller is added to profile (matches old activity behavior)
                    val profile = currentProfile!!
                    if (profile.getController(controller.id) == null) {
                        profile.addController(controller.id)
                        profile.save()
                    }
                    startControllerInputCapture(controller, this@ControllerViewHolder)
                }
                submitRows()
            }

            if (expanded && currentProfile != null) {
                populateBindings(controller)
                startControllerInputCapture(controller, this)
            } else {
                if (activeBindingController?.id == controller.id) {
                    stopControllerInputCapture()
                }
            }
        }

        private fun populateBindings(controller: ExternalController) {
            val profile = currentProfile ?: return
            val inflater = LayoutInflater.from(itemBinding.root.context)
            val ctx = requireContext()

            itemBinding.LLBindingsList.removeAllViews()

            val count = controller.controllerBindingCount
            itemBinding.TVEmptyBindings.isVisible = count == 0
            itemBinding.LLBindingsList.isVisible = count > 0

            for (i in 0 until count) {
                val binding = controller.getControllerBindingAt(i)
                val rowView = inflater.inflate(R.layout.controls_binding_row_item, itemBinding.LLBindingsList, false)

                val tvName = rowView.findViewById<TextView>(R.id.TVButtonName)
                val spinnerType = rowView.findViewById<Spinner>(R.id.SBindingType)
                val spinnerBinding = rowView.findViewById<Spinner>(R.id.SBinding)
                val btnRemove = rowView.findViewById<View>(R.id.BTRemoveBinding)

                tvName.text = binding.toString()

                // Setup binding spinners
                setupBindingSpinners(ctx, spinnerType, spinnerBinding, binding, controller, profile)

                btnRemove.setOnClickListener {
                    controller.removeControllerBinding(binding)
                    profile.save()
                    populateBindings(controller)
                    // Update binding count in header
                    itemBinding.TVBindingCount.text = "${controller.controllerBindingCount} ${getString(R.string.bindings)}"
                    itemBinding.BTRemove.isVisible = controller.controllerBindingCount > 0
                }

                itemBinding.LLBindingsList.addView(rowView)
            }
        }

        fun refreshAfterBindingChange(controller: ExternalController) {
            populateBindings(controller)
            val count = controller.controllerBindingCount
            itemBinding.TVBindingCount.text = "$count ${getString(R.string.bindings)}"
            itemBinding.BTRemove.isVisible = count > 0 && currentProfile != null
        }

        private fun setupBindingSpinners(
            ctx: Context,
            spinnerType: Spinner,
            spinnerBinding: Spinner,
            item: ExternalControllerBinding,
            controller: ExternalController,
            profile: ControlsProfile
        ) {
            // Set themed adapter for binding type spinner (replaces XML entries)
            val typeEntries = ctx.resources.getStringArray(R.array.binding_type_entries)
            val typeAdapter = ArrayAdapter(ctx, R.layout.spinner_item_themed, typeEntries)
            typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed)
            spinnerType.adapter = typeAdapter

            val updateBindingEntries = Runnable {
                val bindingEntries: Array<String> = when (spinnerType.selectedItemPosition) {
                    0 -> Binding.keyboardBindingLabels()
                    1 -> Binding.mouseBindingLabels()
                    2 -> Binding.gamepadBindingLabels()
                    else -> Binding.keyboardBindingLabels()
                }
                val bindingAdapter = ArrayAdapter(ctx, R.layout.spinner_item_themed, bindingEntries)
                bindingAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_themed)
                spinnerBinding.adapter = bindingAdapter
                AppUtils.setSpinnerSelectionFromValue(spinnerBinding, item.binding.toString())
            }

            spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateBindingEntries.run()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            val selectedBinding = item.binding
            when {
                selectedBinding.isKeyboard -> spinnerType.setSelection(0, false)
                selectedBinding.isMouse -> spinnerType.setSelection(1, false)
                selectedBinding.isGamepad -> spinnerType.setSelection(2, false)
            }

            spinnerBinding.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val newBinding = when (spinnerType.selectedItemPosition) {
                        0 -> Binding.keyboardBindingValues()[position]
                        1 -> Binding.mouseBindingValues()[position]
                        2 -> Binding.gamepadBindingValues()[position]
                        else -> Binding.NONE
                    }
                    if (newBinding != item.binding) {
                        item.binding = newBinding
                        profile.save()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            updateBindingEntries.run()
        }
    }

    private inner class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(row: ControlRow.EmptyState) {
            (itemView as? TextView)?.apply {
                setText(row.messageResId)
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    // Types

    companion object {
        private const val TAG = "ICFrag"
        private const val INPUT_CONTROLS_URL =
            "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/%s"

        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_PROFILE = 1
        private const val VIEW_TYPE_SLIDER = 2
        private const val VIEW_TYPE_GYRO = 3
        private const val VIEW_TYPE_ANALOG = 8
        private const val VIEW_TYPE_CHIPS = 4
        private const val VIEW_TYPE_ACTION = 5
        private const val VIEW_TYPE_CONTROLLER = 6
        private const val VIEW_TYPE_EMPTY = 7

        private const val ACTION_CALIBRATE_GYRO = "calibrate_gyro"
        private const val ACTION_IMPORT = "import"
        private const val ACTION_EXPORT = "export"
        private const val PREF_SELECTED_PROFILE_ID = "input_controls_selected_profile_id"

        private val DiffCallback = object : DiffUtil.ItemCallback<ControlRow>() {
            override fun areItemsTheSame(oldItem: ControlRow, newItem: ControlRow): Boolean {
                return when {
                    oldItem is ControlRow.SectionHeader && newItem is ControlRow.SectionHeader ->
                        oldItem.titleResId == newItem.titleResId
                    oldItem is ControlRow.ProfileSelector && newItem is ControlRow.ProfileSelector -> true
                    oldItem is ControlRow.OverlayOpacity && newItem is ControlRow.OverlayOpacity -> true
                    oldItem is ControlRow.GyroscopeCard && newItem is ControlRow.GyroscopeCard -> true
                    oldItem is ControlRow.AnalogSticksCard && newItem is ControlRow.AnalogSticksCard -> true
                    oldItem is ControlRow.TriggerType && newItem is ControlRow.TriggerType -> true
                    oldItem is ControlRow.ActionCard && newItem is ControlRow.ActionCard ->
                        oldItem.action == newItem.action
                    oldItem is ControlRow.ExternalControllerRow && newItem is ControlRow.ExternalControllerRow ->
                        oldItem.controller.id == newItem.controller.id
                    oldItem is ControlRow.EmptyState && newItem is ControlRow.EmptyState -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ControlRow, newItem: ControlRow): Boolean {
                return oldItem == newItem
            }
        }
    }

    private sealed interface ControlRow {
        data class SectionHeader(val titleResId: Int) : ControlRow
        data class ProfileSelector(val profileId: Int?, val profileName: String?, val elementCount: Int) : ControlRow
        data object OverlayOpacity : ControlRow
        data class GyroscopeCard(val expanded: Boolean) : ControlRow
        data class AnalogSticksCard(val expanded: Boolean) : ControlRow
        data class TriggerType(val expanded: Boolean) : ControlRow
        data class ActionCard(
            val iconRes: Int,
            val labelResId: Int,
            val action: String
        ) : ControlRow
        data class ExternalControllerRow(val controller: ExternalController, val expanded: Boolean, val bindingCount: Int) : ControlRow
        data class EmptyState(val messageResId: Int) : ControlRow
    }
}
