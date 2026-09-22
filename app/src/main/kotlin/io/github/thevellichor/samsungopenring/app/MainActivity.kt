package io.github.thevellichor.samsungopenring.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.thevellichor.samsungopenring.app.triggers.TriggerManager
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "openring_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var webhookInput: TextInputEditText
    private lateinit var triggersList: LinearLayout
    private lateinit var triggersEmpty: TextView
    private lateinit var eventLog: TextView
    private lateinit var triggerManager: TriggerManager
    private lateinit var accessibilityStatusText: TextView

    private lateinit var scrollDistanceSeekBar: SeekBar
    private lateinit var swipeDurationSeekBar: SeekBar
    private lateinit var inputCooldownSeekBar: SeekBar

    private lateinit var scrollDistanceValue: TextView
    private lateinit var swipeDurationValue: TextView
    private lateinit var inputCooldownValue: TextView

    private var logExpanded = false

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread {
            val current = eventLog.text.toString()

            eventLog.text =
                if (current == "No events yet.") {
                    line
                } else {
                    "$current\n$line"
                }

            if (logExpanded) {
                val sv = findViewById<ScrollView>(R.id.mainScroll)
                sv.post {
                    sv.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

        triggerManager = TriggerManager(this)

        statusText =
            findViewById(R.id.statusText)

        webhookInput =
            findViewById(R.id.webhookUrlInput)

        triggersList =
            findViewById(R.id.triggersList)

        triggersEmpty =
            findViewById(R.id.triggersEmpty)

        eventLog =
            findViewById(R.id.eventLog)

        accessibilityStatusText =
            findViewById(R.id.accessibilityStatusText)

        scrollDistanceSeekBar =
            findViewById(R.id.scrollDistanceSeekBar)

        swipeDurationSeekBar =
            findViewById(R.id.swipeDurationSeekBar)

        inputCooldownSeekBar =
            findViewById(R.id.inputCooldownSeekBar)

        scrollDistanceValue =
            findViewById(R.id.scrollDistanceValue)

        swipeDurationValue =
            findViewById(R.id.swipeDurationValue)

        inputCooldownValue =
            findViewById(R.id.inputCooldownValue)

        webhookInput.setText(
            prefs.getString(
                KEY_WEBHOOK_URL,
                ""
            )
        )

        eventLog.text =
            EventLog.getRecentLines(this)

        setupAccessibility()
        setupScrollControls()

        // Manual Control
        findViewById<MaterialButton>(
            R.id.startButton
        ).setOnClickListener {

            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(
                    this,
                    "Enable accessibility first",
                    Toast.LENGTH_SHORT
                ).show()

                openAccessibilitySettings()
                return@setOnClickListener
            }

            ensureBluetooth {
                saveWebhookUrl()
                GestureService.start(this)
                statusText.text = "Starting..."
            }
        }

        findViewById<MaterialButton>(
            R.id.stopButton
        ).setOnClickListener {

            GestureService.stop(this)
            statusText.text = "Stopped"
        }

        // Triggers
        findViewById<MaterialButton>(
            R.id.addTriggerButton
        ).setOnClickListener {
            showTriggerTypePicker()
        }

        findViewById<MaterialButton>(
            R.id.armTriggersButton
        ).setOnClickListener {

            saveWebhookUrl()
            triggerManager.armAll()

            EventLog.log(
                this,
                "Triggers armed"
            )

            statusText.text =
                "Triggers armed"
        }

        findViewById<MaterialButton>(
            R.id.disarmTriggersButton
        ).setOnClickListener {

            triggerManager.disarmAll()
            GestureService.stop(this)

            EventLog.log(
                this,
                "Triggers disarmed"
            )

            statusText.text =
                "Disarmed"
        }

        // Advanced
        findViewById<TextView>(
            R.id.readLogsStatus
        ).text =
            ShizukuHelper.getStatusText(this)

        findViewById<MaterialButton>(
            R.id.shizukuGrantButton
        ).setOnClickListener {
            doShizukuGrant()
        }

        findViewById<MaterialButton>(
            R.id.copyAdbButton
        ).setOnClickListener {
            ShizukuHelper
                .copyAdbCommandToClipboard(this)
        }

        // Log
        findViewById<ImageButton>(
            R.id.toggleLogButton
        ).setOnClickListener {

            logExpanded = !logExpanded

            eventLog.visibility =
                if (logExpanded) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            (it as ImageButton)
                .setImageResource(
                    if (logExpanded) {
                        android.R.drawable.arrow_up_float
                    } else {
                        android.R.drawable.arrow_down_float
                    }
                )
        }

        findViewById<MaterialButton>(
            R.id.exportLogButton
        ).setOnClickListener {
            exportLog()
        }

        findViewById<MaterialButton>(
            R.id.clearLogButton
        ).setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Clear log?")
                .setPositiveButton(
                    "Clear"
                ) { _, _ ->

                    EventLog.clear(this)
                    eventLog.text =
                        "No events yet."
                }
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .show()
        }

        setupHelp()
        refreshTriggerList()
    }

    override fun onResume() {
        super.onResume()

        EventLog.addListener(
            logListener
        )

        eventLog.text =
            EventLog.getRecentLines(this)

        findViewById<TextView>(
            R.id.readLogsStatus
        ).text =
            ShizukuHelper.getStatusText(this)

        updateAccessibilityStatus()
    }

    override fun onPause() {
        super.onPause()

        EventLog.removeListener(
            logListener
        )
    }

    // =========================================================
    // Accessibility
    // =========================================================

    private fun setupAccessibility() {

        findViewById<MaterialButton>(
            R.id.openAccessibilityButton
        ).setOnClickListener {

            openAccessibilitySettings()
        }

        updateAccessibilityStatus()
    }

    private fun openAccessibilitySettings() {

        try {
            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Unable to open Accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {

        val expectedComponent =
            ComponentName(
                this,
                RingAccessibilityService::class.java
            )

        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull {
                ComponentName.unflattenFromString(it)
            }
            .any {
                it.packageName ==
                    expectedComponent.packageName &&
                    it.className ==
                    expectedComponent.className
            }
    }

    private fun updateAccessibilityStatus() {

        if (!::accessibilityStatusText.isInitialized) {
            return
        }

        val enabled =
            isAccessibilityServiceEnabled()

        accessibilityStatusText.text =
            if (enabled) {
                "Accessibility: ON ✓"
            } else {
                "Accessibility: OFF"
            }
    }

    // =========================================================
    // Scroll controls
    // =========================================================

    private fun setupScrollControls() {

        val distance =
            prefs.getInt(
                RingAccessibilityService.KEY_SCROLL_DISTANCE,
                RingAccessibilityService.DEFAULT_SCROLL_DISTANCE
            ).coerceIn(
                10,
                60
            )

        val duration =
            prefs.getInt(
                RingAccessibilityService.KEY_SWIPE_DURATION,
                RingAccessibilityService.DEFAULT_SWIPE_DURATION
            ).coerceIn(
                80,
                400
            )

        val cooldown =
            prefs.getInt(
                RingAccessibilityService.KEY_INPUT_COOLDOWN,
                RingAccessibilityService.DEFAULT_INPUT_COOLDOWN
            ).coerceIn(
                100,
                1000
            )

        scrollDistanceSeekBar.progress =
            distance - 10

        swipeDurationSeekBar.progress =
            duration - 80

        inputCooldownSeekBar.progress =
            cooldown - 100

        updateScrollLabels(
            distance,
            duration,
            cooldown
        )

        scrollDistanceSeekBar
            .setOnSeekBarChangeListener(
                simpleSeekListener { progress ->

                    val value =
                        progress + 10

                    prefs.edit()
                        .putInt(
                            RingAccessibilityService.KEY_SCROLL_DISTANCE,
                            value
                        )
                        .apply()

                    updateScrollLabels()
                }
            )

        swipeDurationSeekBar
            .setOnSeekBarChangeListener(
                simpleSeekListener { progress ->

                    val value =
                        progress + 80

                    prefs.edit()
                        .putInt(
                            RingAccessibilityService.KEY_SWIPE_DURATION,
                            value
                        )
                        .apply()

                    updateScrollLabels()
                }
            )

        inputCooldownSeekBar
            .setOnSeekBarChangeListener(
                simpleSeekListener { progress ->

                    val value =
                        progress + 100

                    prefs.edit()
                        .putInt(
                            RingAccessibilityService.KEY_INPUT_COOLDOWN,
                            value
                        )
                        .apply()

                    updateScrollLabels()
                }
            )

        findViewById<MaterialButton>(
            R.id.resetScrollSettingsButton
        ).setOnClickListener {

            val distanceDefault =
                RingAccessibilityService
                    .DEFAULT_SCROLL_DISTANCE

            val durationDefault =
                RingAccessibilityService
                    .DEFAULT_SWIPE_DURATION

            val cooldownDefault =
                RingAccessibilityService
                    .DEFAULT_INPUT_COOLDOWN

            prefs.edit()
                .putInt(
                    RingAccessibilityService.KEY_SCROLL_DISTANCE,
                    distanceDefault
                )
                .putInt(
                    RingAccessibilityService.KEY_SWIPE_DURATION,
                    durationDefault
                )
                .putInt(
                    RingAccessibilityService.KEY_INPUT_COOLDOWN,
                    cooldownDefault
                )
                .apply()

            scrollDistanceSeekBar.progress =
                distanceDefault - 10

            swipeDurationSeekBar.progress =
                durationDefault - 80

            inputCooldownSeekBar.progress =
                cooldownDefault - 100

            updateScrollLabels(
                distanceDefault,
                durationDefault,
                cooldownDefault
            )

            Toast.makeText(
                this,
                "Scroll settings restored",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun simpleSeekListener(
        onChange: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {

        return object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {

                if (fromUser) {
                    onChange(progress)
                }
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?
            ) = Unit

            override fun onStopTrackingTouch(
                seekBar: SeekBar?
            ) = Unit
        }
    }

    private fun updateScrollLabels(
        distance: Int =
            scrollDistanceSeekBar.progress + 10,
        duration: Int =
            swipeDurationSeekBar.progress + 80,
        cooldown: Int =
            inputCooldownSeekBar.progress + 100
    ) {

        scrollDistanceValue.text =
            "Scroll distance: $distance%"

        swipeDurationValue.text =
            "Swipe speed: $duration ms"

        inputCooldownValue.text =
            "Re-input interval: $cooldown ms"
    }

    // =========================================================
    // Help
    // =========================================================

    private fun setupHelp() {

        findViewById<ImageButton>(
            R.id.helpManual
        ).setOnClickListener {

            showHelp(
                "Manual Control",
                "Start/Stop gesture monitoring immediately.\n\n" +
                    "When started, the app connects to your Galaxy Ring over BLE and enables gesture detection."
            )
        }

        findViewById<ImageButton>(
            R.id.helpWebhook
        ).setOnClickListener {

            showHelp(
                "Webhook",
                "Optional URL that receives an HTTP POST when a gesture is detected."
            )
        }

        findViewById<ImageButton>(
            R.id.helpTriggers
        ).setOnClickListener {

            showHelp(
                "Triggers",
                "Triggers can automatically enable gesture monitoring when configured conditions are met."
            )
        }

        findViewById<ImageButton>(
            R.id.helpAdvanced
        ).setOnClickListener {

            showHelp(
                "Advanced",
                "READ_LOGS permission is optional and is not required for normal BLE gesture detection."
            )
        }
    }

    private fun showHelp(
        title: String,
        message: String
    ) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "Got it",
                null
            )
            .show()
    }

    // =========================================================
    // Trigger picker
    // =========================================================

    private fun showTriggerTypePicker() {

        val types =
            arrayOf(
                "Bluetooth device",
                "WiFi network",
                "Android Auto",
                "Time schedule",
                "Location (geofence)",
                "Charging",
                "App in foreground"
            )

        AlertDialog.Builder(this)
            .setTitle("Add trigger")
            .setItems(types) { _, which ->

                when (which) {

                    0 ->
                        ensureBluetooth {
                            showBluetoothDevicePicker()
                        }

                    1 ->
                        showWifiNameDialog()

                    2 -> {
                        triggerManager
                            .addAndroidAutoTrigger()

                        refreshTriggerList()

                        EventLog.log(
                            this,
                            "Added Android Auto trigger"
                        )
                    }

                    3 ->
                        showScheduleDialog()

                    4 ->
                        ensureLocation {
                            showGeofenceDialog()
                        }

                    5 -> {
                        triggerManager
                            .addChargingTrigger()

                        refreshTriggerList()

                        EventLog.log(
                            this,
                            "Added charging trigger"
                        )
                    }

                    6 ->
                        showAppPicker()
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // Permission helpers
    // =========================================================

    private fun ensureBluetooth(
        action: () -> Unit
    ) {

        if (
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            action()

        } else {

            pendingAction = action

            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                1
            )
        }
    }

    private fun ensureLocation(
        action: () -> Unit
    ) {

        if (
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            action()

        } else {

            pendingAction = action

            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                2
            )
        }
    }

    private var pendingAction:
        (() -> Unit)? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            results
        )

        if (
            results.isNotEmpty() &&
            results[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            pendingAction?.invoke()

        } else {

            statusText.text =
                "Permission denied"
        }

        pendingAction = null
    }

    // =========================================================
    // Trigger dialogs
    // =========================================================

    private fun showBluetoothDevicePicker() {

        val adapter =
            (
                getSystemService(
                    BLUETOOTH_SERVICE
                ) as? BluetoothManager
            )?.adapter
                ?: return

        val devices =
            adapter.bondedDevices
                ?.toList()
                ?: return

        if (devices.isEmpty()) {

            statusText.text =
                "No paired devices"

            return
        }

        val names =
            devices.map {
                "${it.name ?: "?"} (${it.address})"
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select device")
            .setItems(names) { _, i ->

                val device =
                    devices[i]

                triggerManager
                    .addBluetoothTrigger(
                        device.address,
                        device.name
                            ?: device.address
                    )

                refreshTriggerList()

                EventLog.log(
                    this,
                    "Added BT trigger: ${device.name}"
                )
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun showWifiNameDialog() {

        val input =
            EditText(this).apply {

                hint =
                    "Network name (SSID)"

                setPadding(
                    48,
                    32,
                    48,
                    16
                )
            }

        AlertDialog.Builder(this)
            .setTitle("WiFi trigger")
            .setView(input)
            .setPositiveButton(
                "Add"
            ) { _, _ ->

                val ssid =
                    input.text
                        .toString()
                        .trim()

                if (ssid.isNotEmpty()) {

                    triggerManager
                        .addWifiTrigger(
                            ssid
                        )

                    refreshTriggerList()

                    EventLog.log(
                        this,
                        "Added WiFi trigger: $ssid"
                    )
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun showScheduleDialog() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    48,
                    32,
                    48,
                    16
                )
            }

        val startInput =
            EditText(this).apply {
                hint =
                    "Start (HH:MM)"
            }

        val endInput =
            EditText(this).apply {
                hint =
                    "End (HH:MM)"
            }

        val daysInput =
            EditText(this).apply {
                hint =
                    "Days (Mon,Tue,... or empty=all)"
            }

        layout.addView(startInput)
        layout.addView(endInput)
        layout.addView(daysInput)

        AlertDialog.Builder(this)
            .setTitle("Schedule trigger")
            .setView(layout)
            .setPositiveButton(
                "Add"
            ) { _, _ ->

                val start =
                    parseTime(
                        startInput.text.toString()
                    )

                val end =
                    parseTime(
                        endInput.text.toString()
                    )

                if (
                    start != null &&
                    end != null
                ) {

                    triggerManager
                        .addScheduleTrigger(
                            start.first,
                            start.second,
                            end.first,
                            end.second,
                            parseDays(
                                daysInput.text
                                    .toString()
                            )
                        )

                    refreshTriggerList()

                    EventLog.log(
                        this,
                        "Added schedule trigger"
                    )

                } else {

                    statusText.text =
                        "Invalid time (use HH:MM)"
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun showGeofenceDialog() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    48,
                    32,
                    48,
                    16
                )
            }

        val labelInput =
            EditText(this).apply {
                hint =
                    "Label (e.g. Home)"
            }

        val latInput =
            EditText(this).apply {

                hint =
                    "Latitude"

                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }

        val lngInput =
            EditText(this).apply {

                hint =
                    "Longitude"

                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }

        val radiusInput =
            EditText(this).apply {

                hint =
                    "Radius meters (default 500)"

                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER
            }

        layout.addView(labelInput)
        layout.addView(latInput)
        layout.addView(lngInput)
        layout.addView(radiusInput)

        AlertDialog.Builder(this)
            .setTitle("Location trigger")
            .setView(layout)
            .setPositiveButton(
                "Add"
            ) { _, _ ->

                val lat =
                    latInput.text
                        .toString()
                        .toDoubleOrNull()

                val lng =
                    lngInput.text
                        .toString()
                        .toDoubleOrNull()

                if (
                    lat != null &&
                    lng != null
                ) {

                    val radius =
                        radiusInput.text
                            .toString()
                            .toFloatOrNull()
                            ?: 500f

                    val label =
                        labelInput.text
                            .toString()
                            .trim()
                            .ifEmpty {
                                "Location"
                            }

                    triggerManager
                        .addGeofenceTrigger(
                            lat,
                            lng,
                            radius,
                            label
                        )

                    refreshTriggerList()

                    EventLog.log(
                        this,
                        "Added location trigger: $label"
                    )

                } else {

                    statusText.text =
                        "Invalid coordinates"
                }
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    private fun showAppPicker() {

        val pm =
            packageManager

        val apps =
            pm.getInstalledApplications(
                PackageManager.GET_META_DATA
            )
                .filter {
                    it.flags and
                        ApplicationInfo.FLAG_SYSTEM ==
                        0
                }
                .sortedBy {
                    pm.getApplicationLabel(it)
                        .toString()
                        .lowercase()
                }

        val labels =
            apps.map {
                pm.getApplicationLabel(it)
                    .toString()
            }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select app")
            .setItems(labels) { _, i ->

                val label =
                    pm.getApplicationLabel(
                        apps[i]
                    ).toString()

                triggerManager
                    .addAppTrigger(
                        apps[i].packageName,
                        label
                    )

                refreshTriggerList()

                EventLog.log(
                    this,
                    "Added app trigger: $label"
                )
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // Shizuku
    // =========================================================

    private fun doShizukuGrant() {

        val status =
            findViewById<TextView>(
                R.id.readLogsStatus
            )

        if (
            ShizukuHelper
                .hasReadLogs(this)
        ) {

            status.text =
                "READ_LOGS already granted!"

            return
        }

        if (
            !ShizukuHelper
                .isShizukuInstalled(this)
        ) {

            status.text =
                "Install Shizuku from Play Store"

            return
        }

        if (
            !ShizukuHelper
                .isShizukuRunning()
        ) {

            status.text =
                "Open Shizuku app and start it"

            return
        }

        if (
            !ShizukuHelper
                .hasShizukuPermission()
        ) {

            ShizukuHelper
                .requestPermission { granted ->

                    runOnUiThread {

                        if (granted) {

                            doShizukuGrantInner(
                                status
                            )

                        } else {

                            status.text =
                                "Permission denied"
                        }
                    }
                }

            return
        }

        doShizukuGrantInner(
            status
        )
    }

    private fun doShizukuGrantInner(
        statusView: TextView
    ) {

        statusView.text =
            "Granting..."

        ShizukuHelper
            .bindAndGrant(
                packageName
            ) { success, message ->

                runOnUiThread {

                    statusView.text =
                        if (success) {
                            "READ_LOGS granted!"
                        } else {
                            "Failed: $message"
                        }

                    EventLog.log(
                        this,
                        "Shizuku: $message"
                    )
                }
            }
    }

    // =========================================================
    // Trigger list
    // =========================================================

    private fun refreshTriggerList() {

        val configs =
            triggerManager
                .getConfiguredTriggers()

        triggersList.removeAllViews()

        if (configs.isEmpty()) {

            triggersEmpty.visibility =
                View.VISIBLE

            return
        }

        triggersEmpty.visibility =
            View.GONE

        for (config in configs) {

            val row =
                LayoutInflater
                    .from(this)
                    .inflate(
                        R.layout.item_trigger,
                        triggersList,
                        false
                    )

            val label =
                row.findViewById<TextView>(
                    R.id.triggerLabel
                )

            label.text =
                when (config.type) {

                    "bluetooth" ->
                        "BT: ${config.name}"

                    "android_auto" ->
                        "Android Auto"

                    "wifi" ->
                        "WiFi: ${config.name}"

                    "schedule" ->
                        "Schedule: ${config.name}"

                    "geofence" ->
                        "Location: ${config.name} " +
                            "(${config.radiusMeters?.toInt() ?: 500}m)"

                    "charging" ->
                        "Charging"

                    "app" ->
                        "App: ${config.name}"

                    else ->
                        config.type
                }

            val triggerId =
                when (config.type) {

                    "bluetooth" ->
                        "bluetooth_${config.address}"

                    "android_auto" ->
                        "android_auto"

                    "wifi" ->
                        "wifi_${config.name}"

                    "schedule" ->
                        "schedule_${config.startHour}" +
                            "${config.startMinute}_" +
                            "${config.endHour}" +
                            "${config.endMinute}"

                    "geofence" ->
                        "geofence_${config.latitude}_" +
                            "${config.longitude}"

                    "charging" ->
                        "charging"

                    "app" ->
                        "app_${config.address}"

                    else ->
                        ""
                }

            row.findViewById<ImageButton>(
                R.id.triggerEditButton
            ).setOnClickListener {

                showEditTriggerDialog(
                    config,
                    triggerId
                )
            }

            row.findViewById<ImageButton>(
                R.id.triggerDeleteButton
            ).setOnClickListener {

                AlertDialog.Builder(this)
                    .setTitle(
                        "Remove trigger?"
                    )
                    .setMessage(
                        label.text
                    )
                    .setPositiveButton(
                        "Remove"
                    ) { _, _ ->

                        triggerManager
                            .removeTrigger(
                                triggerId
                            )

                        refreshTriggerList()

                        EventLog.log(
                            this,
                            "Removed: ${label.text}"
                        )
                    }
                    .setNegativeButton(
                        "Cancel",
                        null
                    )
                    .show()
            }

            triggersList.addView(
                row
            )
        }
    }

    private fun showEditTriggerDialog(
        config:
            io.github.thevellichor.samsungopenring.app.triggers.TriggerConfig,
        triggerId: String
    ) {

        when (config.type) {

            "bluetooth" -> {

                triggerManager
                    .removeTrigger(
                        triggerId
                    )

                ensureBluetooth {
                    showBluetoothDevicePicker()
                }
            }

            "wifi" -> {

                triggerManager
                    .removeTrigger(
                        triggerId
                    )

                showWifiNameDialog()
            }

            "schedule" -> {

                triggerManager
                    .removeTrigger(
                        triggerId
                    )

                showScheduleDialog()
            }

            "geofence" -> {

                triggerManager
                    .removeTrigger(
                        triggerId
                    )

                ensureLocation {
                    showGeofenceDialog()
                }
            }

            "app" -> {

                triggerManager
                    .removeTrigger(
                        triggerId
                    )

                showAppPicker()
            }

            else -> {

                AlertDialog.Builder(this)
                    .setTitle("Edit")
                    .setMessage(
                        "This trigger has no configurable options."
                    )
                    .setPositiveButton(
                        "OK",
                        null
                    )
                    .show()
            }
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    private fun saveWebhookUrl() {

        prefs.edit()
            .putString(
                KEY_WEBHOOK_URL,
                webhookInput.text
                    .toString()
                    .trim()
            )
            .apply()
    }

    private fun exportLog() {

        val log =
            EventLog.getFullLog(this)

        if (log.isBlank()) {

            statusText.text =
                "No log to export"

            return
        }

        startActivity(
            Intent.createChooser(
                Intent(
                    Intent.ACTION_SEND
                ).apply {

                    type =
                        "text/plain"

                    putExtra(
                        Intent.EXTRA_TEXT,
                        log
                    )

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "SamsungOpenRing Event Log"
                    )
                },
                "Export log"
            )
        )
    }

    private fun parseTime(
        text: String
    ): Pair<Int, Int>? {

        val parts =
            text.trim()
                .split(":")

        if (parts.size != 2) {
            return null
        }

        val hour =
            parts[0]
                .toIntOrNull()
                ?: return null

        val minute =
            parts[1]
                .toIntOrNull()
                ?: return null

        return if (
            hour in 0..23 &&
            minute in 0..59
        ) {
            hour to minute
        } else {
            null
        }
    }

    private fun parseDays(
        text: String
    ): Set<Int> {

        if (text.isBlank()) {
            return emptySet()
        }

        val map =
            mapOf(
                "mon" to Calendar.MONDAY,
                "tue" to Calendar.TUESDAY,
                "wed" to Calendar.WEDNESDAY,
                "thu" to Calendar.THURSDAY,
                "fri" to Calendar.FRIDAY,
                "sat" to Calendar.SATURDAY,
                "sun" to Calendar.SUNDAY
            )

        return text
            .lowercase()
            .split(
                ",",
                " "
            )
            .mapNotNull {
                map[it.trim()]
            }
            .toSet()
    }
}
