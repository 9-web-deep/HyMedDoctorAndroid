package com.hymed.doctorapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var currentLocale: String = LocaleHelper.LANG_ZH

    // ── Screen helpers ──
    private val screenW: Int get() = resources.displayMetrics.widthPixels
    private val screenH: Int get() = resources.displayMetrics.heightPixels
    private val isTablet: Boolean get() = resources.configuration.smallestScreenWidthDp >= 600
    private val isWideTablet: Boolean get() = resources.configuration.smallestScreenWidthDp >= 720
    private val maxContentDp: Int get() = if (isWideTablet) 800 else if (isTablet) 640 else 0
    private val cardSpanCount: Int get() = if (isWideTablet) 3 else if (isTablet) 2 else 1

    // ── Icon glyphs (Unicode) ──
    private fun icon(key: String): String = when (key) {
        "home" -> "⌂"       // ⌂
        "grid" -> "▦"       // ▦
        "gear" -> "⚙"       // ⚙
        "pill" -> "⦿"       // ⦿
        "drip" -> "⧖"       // ⧖
        "case" -> "✎"       // ✎
        "ct" -> "⌘"         // ⌘
        "patient" -> "☺"    // ☺
        "chart" -> "↑"      // ↑
        "theme" -> "◑"      // ◑
        "lang" -> "⟘"       // ⟘
        "refresh" -> "↻"    // ↻
        "logout" -> "←"     // ←
        "add" -> "+"        // +
        "edit" -> "✎"       // ✎
        "delete" -> "✗"     // ✗
        "save" -> "✔"       // ✔
        "back" -> "←"       // ←
        "mic" -> "●"        // ●
        "send" -> "➤"       // ➤
        "check" -> "✔"      // ✔
        "clock" -> "⏰"      // ⏰
        "person" -> "☺"     // ☺
        else -> "●"         // ●
    }

    private fun s(id: Int, vararg args: Any): String = if (args.isEmpty()) getString(id) else getString(id, *args)

    @Suppress("DEPRECATION")
    private fun applyLocale(lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private val prefs: SharedPreferences by lazy { getSharedPreferences("hymed_doctor", MODE_PRIVATE) }

    private var baseUrl = "http://119.29.163.179:8082"
    private var token = ""
    private var username = ""
    private var displayName = ""
    private var darkMode = false

    private var currentPatientId = "customer_0001"
    private var currentPatientName = "Patient 0001"
    private var opdId = "OPD-20260507-001"
    private var mainTab = MainTab.HOME
    private var feature = Feature.MENU

    private var patients: List<Patient> = emptyList()
    private var dispensingTasks: List<Task> = emptyList()
    private var infusionTasks: List<Task> = emptyList()
    private var timelineEvents: List<TimelineEvent> = emptyList()

    private val dispensingItems = mutableListOf(OrderItem("Amoxicillin Capsule", "0.25g*24", 1))
    private val infusionItems = mutableListOf(OrderItem("Glucose Injection", "5% 250ml", 1))

    private var casePatientName = "Patient 0001"
    private var caseOpdId = "OPD-20260507-001"
    private var caseDraft = ""
    private var caseAnswer = ""
    private var caseTextView: EditText? = null
    private var caseResultView: TextView? = null

    private var patientMgmtMode = "list"
    private var editingPatient: Patient? = null

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private var ctWebView: android.webkit.WebView? = null

    private val blue = 0xFF1769C2.toInt()
    private val blueDark = 0xFF0E4F94.toInt()
    private val red = 0xFFB42318.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLocale = LocaleHelper.getLocale(getSharedPreferences("hymed_doctor", MODE_PRIVATE))
        applyLocale(currentLocale)
        loadPrefs()
        if (token.isBlank()) showLogin() else showShell(refresh = true)
    }

    override fun onDestroy() {
        stopRecordingIfNeeded()
        ctWebView?.apply {
            stopLoading()
            destroy()
        }
        ctWebView = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (ctWebView?.canGoBack() == true) {
            ctWebView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadPrefs() {
        baseUrl = prefs.getString("baseUrl", baseUrl) ?: baseUrl
        token = prefs.getString("token", "") ?: ""
        username = prefs.getString("username", "") ?: ""
        displayName = prefs.getString("displayName", "") ?: ""
        darkMode = prefs.getBoolean("darkMode", false)
    }

    private fun showLogin() {
        val loginWidth = if (maxContentDp > 0) {
            LinearLayout.LayoutParams(dp(maxContentDp), -1)
        } else {
            LinearLayout.LayoutParams(-1, -1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(bg())
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1, 1f)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(42), dp(24), dp(24))
            layoutParams = loginWidth
        }

        content.addView(TextView(this).apply {
            text = "${icon("pill")}  ${s(R.string.title_brand_name)}"
            textSize = if (isTablet) 16f else 14f
            setTextColor(blue)
            setTypeface(null, Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = s(R.string.title_login)
            textSize = if (isTablet) 36f else 30f
            setTextColor(fg())
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(18))
        })

        val serverInput = input(s(R.string.label_server_url), baseUrl)
        val userInput = input(s(R.string.label_doctor_account), username.ifBlank { s(R.string.hint_doctor_account) })
        val passInput = input(s(R.string.label_password), "").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val error = TextView(this).apply {
            setTextColor(red)
            setPadding(0, dp(10), 0, dp(8))
        }

        content.addView(card().apply {
            addView(label(s(R.string.label_server_url)))
            addView(serverInput)
            addView(label(s(R.string.label_doctor_account)))
            addView(userInput)
            addView(label(s(R.string.label_password)))
            addView(passInput)
        }.withMargin())
        content.addView(error)
        content.addView(primaryButton(s(R.string.btn_login), icon("person")) {
            baseUrl = serverInput.text.toString().trim().trimEnd('/')
            prefs.edit().putString("baseUrl", baseUrl).apply()
            login(userInput.text.toString().trim(), passInput.text.toString(), error)
        }.withMargin())

        scrollView.addView(content)
        root.addView(scrollView)
        setContentView(root)
    }

    private fun login(account: String, password: String, error: TextView) {
        if (account.isBlank() || password.isBlank()) {
            error.text = s(R.string.msg_enter_account_password)
            return
        }
        error.text = s(R.string.msg_logging_in)
        requestAsync(
            block = {
                val body = JSONObject()
                    .put("username", account)
                    .put("password", password)
                    .put("role", "doctor")
                postJson("/api/administrator/auth/login", body, auth = false)
            },
            onSuccess = { json ->
                val user = json.optJSONObject("user") ?: JSONObject()
                token = json.optString("token")
                username = user.optString("username", account)
                displayName = user.optString("display_name", username)
                prefs.edit()
                    .putString("token", token)
                    .putString("username", username)
                    .putString("displayName", displayName)
                    .apply()
                showShell(refresh = true)
            },
            onError = { error.text = s(R.string.msg_login_failed, it) }
        )
    }

    private fun showShell(refresh: Boolean = false) {
        if (refresh) refreshData(redraw = false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val contentWidth = if (maxContentDp > 0) {
            LinearLayout.LayoutParams(dp(maxContentDp), -1, 1f)
        } else {
            LinearLayout.LayoutParams(-1, 0, 1f)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                if (isTablet) dp(32) else dp(14),
                if (isTablet) dp(24) else dp(14),
                if (isTablet) dp(32) else dp(14),
                dp(8)
            )
        }
        content.addView(header())
        content.addView(patientSelector())
        content.addView(screenView())

        root.addView(ScrollView(this).apply {
            layoutParams = contentWidth
            addView(content)
        })
        root.addView(bottomNav())
        setContentView(root)
    }

    private fun header(): View {
        val avatarSize = if (isTablet) dp(60) else dp(48)
        val avatarRadius = if (isTablet) dp(20) else dp(14)
        val titleSize = if (isTablet) 34f else 27f
        val brandSize = if (isTablet) 14f else 12f

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = "${icon("pill")}  ${s(R.string.title_brand_name)}"
                    textSize = brandSize
                    setTextColor(blue)
                    setTypeface(null, Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = title()
                    textSize = titleSize
                    setTextColor(fg())
                    setTypeface(null, Typeface.BOLD)
                })
            })
            addView(Button(this@MainActivity).apply {
                text = (displayName.ifBlank { username }.ifBlank { s(R.string.default_avatar) }).take(1).uppercase()
                setTextColor(0xFFFFFFFF.toInt())
                textSize = if (isTablet) 20f else 16f
                background = rounded(blue, avatarRadius)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    elevation = dp(2).toFloat()
                    stateListAnimator = null
                }
                setOnClickListener {
                    mainTab = MainTab.SETTINGS
                    feature = Feature.MENU
                    showShell()
                }
            }, LinearLayout.LayoutParams(avatarSize, avatarSize))
        }
    }

    private fun patientSelector(): View {
        return card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "${icon("person")}  ${s(R.string.label_current_patient)}"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(muted())
                setPadding(0, 0, 0, dp(5))
            })
            val spinner = Spinner(this@MainActivity)
            val labels = if (patients.isEmpty()) {
                listOf("$currentPatientName - $currentPatientId")
            } else {
                patients.map { "${it.name} - ${it.id}" }
            }
            spinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            val index = patients.indexOfFirst { it.id == currentPatientId }
            if (index >= 0) spinner.setSelection(index)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (patients.isNotEmpty()) {
                        val selected = patients[position]
                        if (selected.id != currentPatientId) {
                            currentPatientId = selected.id
                            currentPatientName = selected.name
                            casePatientName = selected.name
                            refreshData(redraw = true)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            addView(spinner)
            addView(TextView(this@MainActivity).apply {
                text = "${icon("person")}  $currentPatientName  /  $currentPatientId"
                textSize = if (isTablet) 18f else 16f
                setTextColor(fg())
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
        }.withMargin()
    }

    private fun screenView(): View = when (mainTab) {
        MainTab.HOME -> homeView()
        MainTab.FEATURES -> if (feature == Feature.MENU) featureMenu() else featureView()
        MainTab.SETTINGS -> settingsView()
    }

    private fun homeView(): View {
        val pendingDispensing = dispensingTasks.count { !it.done }
        val pendingInfusion = infusionTasks.count { !it.done }
        val completed = dispensingTasks.count { it.done } + infusionTasks.count { it.done }
        val totalPending = pendingDispensing + pendingInfusion

        return vertical().apply {
            // Banner card
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                background = rounded(blue)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    elevation = dp(3).toFloat()
                }
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = s(R.string.label_pending_tasks)
                        setTextColor(0xFFEAF4FF.toInt())
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "$totalPending"
                        textSize = 42f
                        setTextColor(0xFFFFFFFF.toInt())
                        setTypeface(null, Typeface.BOLD)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = s(R.string.label_patient_overview, currentPatientName)
                        setTextColor(0xFFEAF4FF.toInt())
                    })
                })
                addView(Button(this@MainActivity).apply {
                    text = "${icon("chart")}  ${s(R.string.btn_data)}"
                    setOnClickListener { openFeature(Feature.TIMELINE) }
                })
            }.withMargin())

            // Stat cards row
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(statCard(s(R.string.label_dispense), pendingDispensing, "pill"), LinearLayout.LayoutParams(0, -2, 1f))
                addView(statCard(s(R.string.label_infusion), pendingInfusion, "drip"), LinearLayout.LayoutParams(0, -2, 1f))
                addView(statCard(s(R.string.label_done), completed, "check"), LinearLayout.LayoutParams(0, -2, 1f))
            })

            // Quick actions — grid on tablet, list on phone
            addView(section(s(R.string.label_quick_actions)).apply {
                if (isTablet) {
                    // 2-column grid for tablets
                    val rows = listOf(
                        listOf(
                            Triple(s(R.string.title_dispensing_tasks), s(R.string.desc_dispensing_tasks), Feature.DISPENSING) to "pill",
                            Triple(s(R.string.title_infusion_tasks), s(R.string.desc_infusion_tasks), Feature.INFUSION) to "drip",
                        ),
                        listOf(
                            Triple(s(R.string.title_case_generator), s(R.string.desc_case_generator), Feature.CASE) to "case",
                            Triple(s(R.string.title_ct_analysis), s(R.string.desc_ct_analysis), Feature.CT) to "ct",
                        ),
                    )
                    rows.forEach { row ->
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            row.forEach { (item, iconKey) ->
                                addView(actionButton(item.first, item.second, icon(iconKey)) {
                                    openFeature(item.third)
                                }, LinearLayout.LayoutParams(0, -2, 1f))
                            }
                        })
                    }
                } else {
                    addView(actionButton(s(R.string.title_dispensing_tasks), s(R.string.desc_dispensing_tasks), icon("pill")) { openFeature(Feature.DISPENSING) })
                    addView(actionButton(s(R.string.title_infusion_tasks), s(R.string.desc_infusion_tasks), icon("drip")) { openFeature(Feature.INFUSION) })
                    addView(actionButton(s(R.string.title_case_generator), s(R.string.desc_case_generator), icon("case")) { openFeature(Feature.CASE) })
                    addView(actionButton(s(R.string.title_ct_analysis), s(R.string.desc_ct_analysis), icon("ct")) { openFeature(Feature.CT) })
                }
            })

            // Recent events
            addView(section(s(R.string.label_recent_events)).apply {
                if (timelineEvents.isEmpty()) addView(empty(s(R.string.msg_no_recent_events)))
                timelineEvents.take(4).forEach { addView(eventRow(it)) }
            })
        }
    }

    private fun statCard(name: String, value: Int, iconKey: String = "●"): View {
        return card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "${icon(iconKey)}  $name"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(muted())
                setPadding(0, 0, 0, dp(5))
            })
            addView(TextView(this@MainActivity).apply {
                text = value.toString()
                textSize = if (isTablet) 32f else 26f
                setTextColor(blue)
                setTypeface(null, Typeface.BOLD)
            })
        }.withMargin()
    }

    private fun featureMenu(): View {
        val items = listOf(
            listOf(
                Triple(s(R.string.title_dispensing_tasks), s(R.string.desc_dispensing_menu), Feature.DISPENSING) to "pill",
                Triple(s(R.string.title_infusion_tasks), s(R.string.desc_infusion_menu), Feature.INFUSION) to "drip",
            ),
            listOf(
                Triple(s(R.string.title_case_generator_ai), s(R.string.desc_case_menu), Feature.CASE) to "case",
                Triple(s(R.string.title_ct_analysis), s(R.string.desc_ct_menu), Feature.CT) to "ct",
            ),
            listOf(
                Triple(s(R.string.title_patient_management), s(R.string.desc_patient_management), Feature.PATIENT_MANAGEMENT) to "patient",
                Triple(s(R.string.title_treatment_data), s(R.string.desc_timeline_menu), Feature.TIMELINE) to "chart",
            ),
        )

        return vertical().apply {
            if (isTablet) {
                items.forEach { row ->
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        row.forEach { (item, iconKey) ->
                            addView(actionButton(item.first, item.second, icon(iconKey)) {
                                openFeature(item.third)
                            }, LinearLayout.LayoutParams(0, -2, 1f))
                        }
                    })
                }
            } else {
                items.flatten().forEach { (item, iconKey) ->
                    addView(actionButton(item.first, item.second, icon(iconKey)) { openFeature(item.third) })
                }
            }
        }
    }

    private fun featureView(): View {
        return vertical().apply {
            addView(Button(this@MainActivity).apply {
                text = "${icon("back")}  ${s(R.string.btn_back_features)}"
                setOnClickListener {
                    feature = Feature.MENU
                    showShell()
                }
            }.withMargin())
            addView(
                when (feature) {
                    Feature.DISPENSING -> dispensingView()
                    Feature.INFUSION -> infusionView()
                    Feature.CASE -> caseView()
                    Feature.CT -> ctView()
                    Feature.TIMELINE -> timelineView()
                    Feature.PATIENT_MANAGEMENT -> patientManagementView()
                    Feature.MENU -> TextView(this@MainActivity)
                }
            )
        }
    }

    private fun dispensingView(): View = vertical().apply {
        addView(
            orderForm(
                title = s(R.string.title_create_dispensing),
                items = dispensingItems,
                addItem = { dispensingItems.add(OrderItem("", "", 1)) },
                removeItem = { if (dispensingItems.size > 1) dispensingItems.removeAt(it) },
                submit = { submitDispensing() },
            )
        )
        addView(taskList(s(R.string.title_dispensing_status), dispensingTasks) { if (it.done) s(R.string.label_done) else s(R.string.label_pending) })
    }

    private fun infusionView(): View = vertical().apply {
        addView(
            orderForm(
                title = s(R.string.title_create_infusion),
                items = infusionItems,
                addItem = { infusionItems.add(OrderItem("", "", 1)) },
                removeItem = { if (infusionItems.size > 1) infusionItems.removeAt(it) },
                submit = { submitInfusion() },
            )
        )
        addView(taskList(s(R.string.title_infusion_status), infusionTasks) { infusionStatusText(it) })
    }

    private fun orderForm(
        title: String,
        items: MutableList<OrderItem>,
        addItem: () -> Unit,
        removeItem: (Int) -> Unit,
        submit: () -> Unit,
    ): View {
        return section(title).apply {
            val opdInput = input(s(R.string.hint_opd_id), opdId)
            val inputRows = mutableListOf<Triple<EditText, EditText, EditText>>()
            addView(label(s(R.string.label_opd_id)))
            addView(opdInput)

            items.forEachIndexed { index, item ->
                val nameInput = input(s(R.string.hint_medicine_name), item.medicine)
                val specInput = input(s(R.string.hint_specification), item.specification)
                val amountInput = input(s(R.string.hint_amount), item.amount.toString())
                inputRows.add(Triple(nameInput, specInput, amountInput))

                addView(label(s(R.string.label_item, index + 1)))
                addView(nameInput)
                addView(specInput)
                addView(amountInput)
                addView(Button(this@MainActivity).apply {
                    text = "${icon("delete")}  ${s(R.string.btn_remove_item)}"
                    setOnClickListener {
                        saveOrderInputs(items, index, nameInput, specInput, amountInput)
                        removeItem(index)
                        showShell()
                    }
                })
            }

            addView(Button(this@MainActivity).apply {
                text = "${icon("add")}  ${s(R.string.btn_add_item)}"
                setOnClickListener {
                    addItem()
                    showShell()
                }
            })
            addView(primaryButton(s(R.string.btn_submit), icon("send")) {
                opdId = opdInput.text.toString().trim()
                inputRows.forEachIndexed { index, row ->
                    saveOrderInputs(items, index, row.first, row.second, row.third)
                }
                submit()
            }.withMargin())
        }
    }

    private fun saveOrderInputs(items: MutableList<OrderItem>, index: Int, nameInput: EditText, specInput: EditText, amountInput: EditText) {
        items.getOrNull(index)?.let {
            it.medicine = nameInput.text.toString()
            it.specification = specInput.text.toString()
            it.amount = amountInput.text.toString().toIntOrNull() ?: 1
        }
    }

    private fun taskList(title: String, tasks: List<Task>, statusText: (Task) -> String): View {
        return section(title).apply {
            if (tasks.isEmpty()) addView(empty(s(R.string.msg_no_tasks)))
            tasks.forEach {
                addView(TextView(this@MainActivity).apply {
                    text = "${statusText(it)}\n${formatOrderItems(it.items)}\n${s(R.string.label_task_id, it.id)}\n${s(R.string.label_updated, it.updatedAt)}"
                    setTextColor(fg())
                    setPadding(0, dp(8), 0, dp(8))
                })
            }
        }
    }

    private fun caseView(): View {
        return vertical().apply {
            val nameInput = input(s(R.string.label_patient_name), casePatientName.ifBlank { currentPatientName })
            val opdInput = input(s(R.string.label_opd_id), caseOpdId)
            caseTextView = EditText(this@MainActivity).apply {
                setText(caseDraft)
                minLines = 7
                hint = s(R.string.hint_conversation)
                setTextColor(fg())
                setHintTextColor(muted())
                background = rounded(inputBg())
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            caseResultView = TextView(this@MainActivity).apply {
                text = caseAnswer
                setTextColor(fg())
                setPadding(0, dp(10), 0, 0)
            }

            addView(section(s(R.string.title_case_generator_ai)).apply {
                addView(label(s(R.string.label_patient_name)))
                addView(nameInput)
                addView(label(s(R.string.label_opd_id)))
                addView(opdInput)
                addView(label(s(R.string.label_conversation_text)))
                addView(caseTextView)
                addView(Button(this@MainActivity).apply {
                    text = if (isRecording) "${icon("mic")}  ${s(R.string.btn_stop_transcribe)}" else "${icon("mic")}  ${s(R.string.btn_mic_input)}"
                    setOnClickListener {
                        casePatientName = nameInput.text.toString()
                        caseOpdId = opdInput.text.toString()
                        caseDraft = caseTextView?.text?.toString().orEmpty()
                        if (isRecording) stopRecordingAndTranscribe() else startRecording()
                    }
                }.withMargin())
                addView(primaryButton(s(R.string.btn_generate_case), icon("send")) {
                    casePatientName = nameInput.text.toString()
                    caseOpdId = opdInput.text.toString()
                    caseDraft = caseTextView?.text?.toString().orEmpty()
                    generateCase()
                }.withMargin())
                addView(Button(this@MainActivity).apply {
                    text = "${icon("delete")}  ${s(R.string.btn_clear_case)}"
                    setOnClickListener {
                        casePatientName = nameInput.text.toString()
                        caseOpdId = opdInput.text.toString()
                        caseDraft = ""
                        caseAnswer = ""
                        caseTextView?.setText("")
                        caseResultView?.text = ""
                        caseResultView?.let { v ->
                            val p = v.parent
                            if (p is LinearLayout) {
                                p.removeAllViews()
                                p.addView(empty(s(R.string.msg_result_placeholder)))
                                p.addView(v)
                            }
                        }
                        toast(s(R.string.msg_case_cleared))
                    }
                }.withMargin())
            })

            addView(section(s(R.string.label_result)).apply {
                if (caseAnswer.isBlank()) addView(empty(s(R.string.msg_result_placeholder)))
                addView(caseResultView)
            })
        }
    }

    private fun ctView(): View {
        return vertical().apply {
            addView(TextView(this@MainActivity).apply {
                text = s(R.string.msg_ct_info)
                setTextColor(muted())
                setPadding(0, 0, 0, dp(8))
            })

            val ctUrl = "http://119.29.163.179/chat/ws9Y2p6rtKK4oJdO"

            val wv = android.webkit.WebView(this@MainActivity).apply {
                val heightRatio = if (isTablet) 0.78 else 0.72
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (screenH * heightRatio).toInt()
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        if (request?.url?.toString()?.startsWith(baseUrl) != true || token.isBlank()) {
                            return super.shouldInterceptRequest(view, request)
                        }
                        try {
                            val conn = URL(request.url.toString()).openConnection() as HttpURLConnection
                            conn.requestMethod = request.method
                            conn.connectTimeout = 15000
                            conn.readTimeout = 60000
                            conn.setRequestProperty("Authorization", "Bearer $token")
                            request.requestHeaders.forEach { (key, value) ->
                                if (!key.equals("Authorization", ignoreCase = true)) {
                                    conn.setRequestProperty(key, value)
                                }
                            }
                            conn.connect()
                            val code = conn.responseCode
                            val stream = if (code >= 400) conn.errorStream else conn.inputStream
                            val mime = conn.contentType ?: "text/html"
                            val encoding = conn.contentEncoding ?: "utf-8"
                            return android.webkit.WebResourceResponse(mime, encoding, stream)
                        } catch (_: Exception) {
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                }
                webChromeClient = android.webkit.WebChromeClient()

                // Set cookie with auth token as fallback for JavaScript-initiated requests
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                @Suppress("DEPRECATION")
                cm.setAcceptThirdPartyCookies(this@apply, true)
                if (token.isNotBlank()) {
                    cm.setCookie(baseUrl, "hy_token=$token; Path=/")
                    cm.flush()
                }

                loadUrl(ctUrl, mapOf("Authorization" to "Bearer $token"))
            }
            ctWebView = wv
            addView(wv)
        }
    }

    private fun timelineView(): View {
        return section(s(R.string.title_treatment_data)).apply {
            addView(Button(this@MainActivity).apply {
                text = "${icon("refresh")}  ${s(R.string.btn_refresh)}"
                setOnClickListener { refreshData(redraw = true) }
            })
            if (timelineEvents.isEmpty()) addView(empty(s(R.string.msg_no_timeline)))
            timelineEvents.forEach { addView(eventRow(it)) }
        }
    }

    private fun patientManagementView(): View {
        if (patientMgmtMode == "form") return patientFormView()

        return vertical().apply {
            addView(primaryButton(s(R.string.btn_add_patient), icon("add")) {
                editingPatient = null
                patientMgmtMode = "form"
                showShell()
            }.withMargin())

            if (patients.isEmpty()) {
                addView(empty(s(R.string.msg_no_patients)))
            } else {
                patients.forEach { patient ->
                    addView(card().apply {
                        addView(TextView(this@MainActivity).apply {
                            text = "${patient.name} (${patient.id})"
                            textSize = 16f
                            setTextColor(fg())
                            setTypeface(null, Typeface.BOLD)
                        })
                        val subtitle = listOfNotNull(
                            patient.gender.ifBlank { null },
                            patient.age.ifBlank { null }
                        ).joinToString(" / ")
                        if (subtitle.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = subtitle
                                setTextColor(muted())
                            })
                        }
                        if (patient.department.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_department)}: ${patient.department}"
                                setTextColor(muted())
                            })
                        }
                        if (patient.bedNo.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_bed_no)}: ${patient.bedNo}"
                                setTextColor(muted())
                            })
                        }
                        if (patient.diagnosis.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_diagnosis)}: ${patient.diagnosis}"
                                setTextColor(muted())
                            })
                        }
                        if (patient.attendingDoctor.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_attending_doctor)}: ${patient.attendingDoctor}"
                                setTextColor(muted())
                            })
                        }
                        if (patient.primaryNurse.isNotBlank()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_primary_nurse)}: ${patient.primaryNurse}"
                                setTextColor(muted())
                            })
                        }
                        if (patient.allergies.isNotEmpty()) {
                            addView(TextView(this@MainActivity).apply {
                                text = "${s(R.string.label_allergies)}: ${patient.allergies.joinToString(", ")}"
                                setTextColor(muted())
                            })
                        }
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, dp(8), 0, 0)
                            addView(Button(this@MainActivity).apply {
                                text = "${icon("edit")}  ${s(R.string.btn_edit_patient)}"
                                setOnClickListener {
                                    editingPatient = patient
                                    patientMgmtMode = "form"
                                    showShell()
                                }
                            })
                            addView(Button(this@MainActivity).apply {
                                text = "${icon("delete")}  ${s(R.string.btn_delete_patient)}"
                                setOnClickListener {
                                    android.app.AlertDialog.Builder(this@MainActivity)
                                        .setTitle(s(R.string.btn_delete_patient))
                                        .setMessage(s(R.string.msg_confirm_delete_patient, patient.name))
                                        .setPositiveButton(s(R.string.btn_delete_patient)) { _, _ ->
                                            deletePatient(patient.id)
                                        }
                                        .setNegativeButton(s(R.string.btn_cancel), null)
                                        .show()
                                }
                            })
                        })
                    }.withMargin())
                }
            }
        }
    }

    private fun patientFormView(): View {
        val editing = editingPatient
        val isEdit = editing != null

        val idInput = input(s(R.string.hint_patient_id), editing?.id ?: "").apply {
            if (isEdit) isEnabled = false
        }
        val nameInput = input(s(R.string.hint_name), editing?.name ?: "")
        val genderSpinner = Spinner(this).apply {
            val genders = listOf(s(R.string.label_male), s(R.string.label_female), s(R.string.label_other))
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, genders)
            val idx = when (editing?.gender) {
                s(R.string.label_male) -> 0
                s(R.string.label_female) -> 1
                s(R.string.label_other) -> 2
                else -> -1
            }
            if (idx >= 0) setSelection(idx)
        }
        val ageInput = input(s(R.string.hint_age), editing?.age ?: "")
        val departmentInput = input(s(R.string.hint_department), editing?.department ?: "")
        val bedNoInput = input(s(R.string.hint_bed_no), editing?.bedNo ?: "")
        val diagnosisInput = input(s(R.string.hint_diagnosis), editing?.diagnosis ?: "")
        val doctorInput = input(s(R.string.hint_attending_doctor), editing?.attendingDoctor ?: "")
        val nurseInput = input(s(R.string.hint_primary_nurse), editing?.primaryNurse ?: "")
        val allergiesInput = EditText(this).apply {
            setText(editing?.allergies?.joinToString(", ") ?: "")
            minLines = 2
            hint = s(R.string.hint_allergies)
            setTextColor(fg())
            setHintTextColor(muted())
            background = rounded(inputBg())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        return vertical().apply {
            addView(section(if (isEdit) s(R.string.title_edit_patient) else s(R.string.title_create_patient)).apply {
                addView(label(s(R.string.label_patient_id)))
                addView(idInput)
                addView(label(s(R.string.label_name)))
                addView(nameInput)
                addView(label(s(R.string.label_gender)))
                addView(genderSpinner)
                addView(label(s(R.string.label_age)))
                addView(ageInput)
                addView(label(s(R.string.label_department)))
                addView(departmentInput)
                addView(label(s(R.string.label_bed_no)))
                addView(bedNoInput)
                addView(label(s(R.string.label_diagnosis)))
                addView(diagnosisInput)
                addView(label(s(R.string.label_attending_doctor)))
                addView(doctorInput)
                addView(label(s(R.string.label_primary_nurse)))
                addView(nurseInput)
                addView(label(s(R.string.label_allergies)))
                addView(allergiesInput)

                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(primaryButton(s(R.string.btn_save_patient), icon("save")) {
                        val genderValue = (genderSpinner.selectedItem as? String) ?: ""
                        val pid = idInput.text.toString().trim()
                        val pname = nameInput.text.toString().trim()
                        if (pid.isBlank() || pname.isBlank()) {
                            toast(s(R.string.msg_enter_account_password))
                        } else if (isEdit) {
                            updatePatient(
                                editing!!.id,
                                Patient(
                                    id = pid, name = pname,
                                    gender = genderValue,
                                    age = ageInput.text.toString().trim(),
                                    department = departmentInput.text.toString().trim(),
                                    bedNo = bedNoInput.text.toString().trim(),
                                    diagnosis = diagnosisInput.text.toString().trim(),
                                    attendingDoctor = doctorInput.text.toString().trim(),
                                    primaryNurse = nurseInput.text.toString().trim(),
                                    allergies = splitAllergies(allergiesInput.text.toString()),
                                )
                            )
                        } else {
                            createPatient(
                                Patient(
                                    id = pid, name = pname,
                                    gender = genderValue,
                                    age = ageInput.text.toString().trim(),
                                    department = departmentInput.text.toString().trim(),
                                    bedNo = bedNoInput.text.toString().trim(),
                                    diagnosis = diagnosisInput.text.toString().trim(),
                                    attendingDoctor = doctorInput.text.toString().trim(),
                                    primaryNurse = nurseInput.text.toString().trim(),
                                    allergies = splitAllergies(allergiesInput.text.toString()),
                                )
                            )
                        }
                    }.withMargin())
                    addView(Button(this@MainActivity).apply {
                        text = "${icon("back")}  ${s(R.string.btn_cancel)}"
                        setOnClickListener {
                            patientMgmtMode = "list"
                            editingPatient = null
                            showShell()
                        }
                    }.withMargin())
                })
            })
        }
    }

    private fun createPatient(data: Patient) {
        val body = JSONObject()
            .put("patient_id", data.id)
            .put("name", data.name)
            .put("gender", data.gender)
            .put("age", data.age.toIntOrNull() ?: 0)
            .put("department", data.department)
            .put("bed_no", data.bedNo)
            .put("diagnosis", data.diagnosis)
            .put("attending_doctor", data.attendingDoctor)
            .put("primary_nurse", data.primaryNurse)
            .put("allergies", JSONArray(data.allergies))
        requestAsync(
            block = { postJson("/api/customer/patients", body) },
            onSuccess = {
                toast(s(R.string.msg_patient_created))
                patientMgmtMode = "list"
                editingPatient = null
                refreshData(redraw = true)
            },
            onError = { toast(s(R.string.msg_patient_create_failed, it)) }
        )
    }

    private fun updatePatient(originalId: String, data: Patient) {
        val body = JSONObject()
            .put("name", data.name)
            .put("gender", data.gender)
            .put("age", data.age.toIntOrNull() ?: 0)
            .put("department", data.department)
            .put("bed_no", data.bedNo)
            .put("diagnosis", data.diagnosis)
            .put("attending_doctor", data.attendingDoctor)
            .put("primary_nurse", data.primaryNurse)
            .put("allergies", JSONArray(data.allergies))
        requestAsync(
            block = { patchJson("/api/customer/patients/${java.net.URLEncoder.encode(originalId, "UTF-8")}", body) },
            onSuccess = {
                toast(s(R.string.msg_patient_updated))
                patientMgmtMode = "list"
                editingPatient = null
                refreshData(redraw = true)
            },
            onError = { toast(s(R.string.msg_patient_update_failed, it)) }
        )
    }

    private fun deletePatient(patientId: String) {
        requestAsync(
            block = { deleteJson("/api/customer/patients/${java.net.URLEncoder.encode(patientId, "UTF-8")}") },
            onSuccess = {
                toast(s(R.string.msg_patient_deleted))
                if (currentPatientId == patientId && patients.isNotEmpty()) {
                    val next = patients.firstOrNull { it.id != patientId } ?: patients.first()
                    currentPatientId = next.id
                    currentPatientName = next.name
                    casePatientName = next.name
                }
                refreshData(redraw = true)
            },
            onError = { toast(s(R.string.msg_patient_delete_failed, it)) }
        )
    }

    private fun settingsView(): View {
        return vertical().apply {
            addView(actionButton(s(R.string.label_theme), if (darkMode) s(R.string.label_dark_mode) else s(R.string.label_light_mode), icon("theme")) {
                darkMode = !darkMode
                prefs.edit().putBoolean("darkMode", darkMode).apply()
                showShell()
            })
            addView(actionButton(s(R.string.label_language), if (currentLocale == LocaleHelper.LANG_ZH) s(R.string.label_chinese) else s(R.string.label_english), icon("lang")) {
                val newLang = if (currentLocale == LocaleHelper.LANG_ZH) LocaleHelper.LANG_EN else LocaleHelper.LANG_ZH
                LocaleHelper.setLocale(prefs, newLang)
                applyLocale(newLang)
                currentLocale = newLang
                showShell()
            })
            addView(actionButton(s(R.string.title_patient_management), s(R.string.desc_patient_management), icon("patient")) { openFeature(Feature.PATIENT_MANAGEMENT) })
            addView(actionButton(s(R.string.title_treatment_data), s(R.string.label_timeline_count, timelineEvents.size), icon("chart")) { openFeature(Feature.TIMELINE) })
            addView(actionButton(s(R.string.title_refresh_data), s(R.string.desc_refresh_data), icon("refresh")) { refreshData(redraw = true) })
            addView(actionButton(s(R.string.label_server_url), baseUrl, icon("gear")) { showLogin() })
            addView(actionButton(s(R.string.btn_logout), displayName.ifBlank { username }, icon("logout")) {
                token = ""
                prefs.edit().remove("token").remove("username").remove("displayName").apply()
                showLogin()
            })
        }
    }

    private fun bottomNav(): View {
        val navHeight = if (isTablet) dp(68) else dp(58)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                if (isTablet) dp(24) else dp(10),
                if (isTablet) dp(12) else dp(8),
                if (isTablet) dp(24) else dp(10),
                if (isTablet) dp(14) else dp(10)
            )
            setBackgroundColor(cardColor())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(4).toFloat()
            }
            addView(navButton("${icon("home")}  ${s(R.string.tab_home)}", MainTab.HOME), LinearLayout.LayoutParams(0, navHeight, 1f))
            addView(navButton("${icon("grid")}  ${s(R.string.tab_features)}", MainTab.FEATURES), LinearLayout.LayoutParams(0, navHeight, 1f))
            addView(navButton("${icon("gear")}  ${s(R.string.tab_settings)}", MainTab.SETTINGS), LinearLayout.LayoutParams(0, navHeight, 1f))
        }
    }

    private fun navButton(textValue: String, tab: MainTab): View {
        val isActive = mainTab == tab
        return Button(this).apply {
            text = textValue
            textSize = if (isTablet) 15f else 13f
            setTextColor(if (isActive) blueDark else muted())
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            background = rounded(if (isActive) softBlue() else cardColor(), if (isTablet) dp(22) else dp(18))
            setPadding(dp(4), dp(8), dp(4), dp(8))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stateListAnimator = null
            }
            setOnClickListener {
                mainTab = tab
                if (tab != MainTab.FEATURES) feature = Feature.MENU
                showShell()
            }
        }
    }

    private fun refreshData(redraw: Boolean) {
        if (token.isBlank()) return
        requestAsync(
            block = {
                val patientItems = getJson("/api/customer/patients").optJSONArray("items") ?: JSONArray()
                val loadedPatients = (0 until patientItems.length()).mapNotNull {
                    val obj = patientItems.optJSONObject(it) ?: return@mapNotNull null
                    Patient(
                        id = obj.optString("patient_id"),
                        name = obj.optString("name", obj.optString("patient_id")),
                        gender = obj.optString("gender"),
                        age = obj.optString("age"),
                        department = obj.optString("department"),
                        bedNo = obj.optString("bed_no"),
                        diagnosis = obj.optString("diagnosis"),
                        attendingDoctor = obj.optString("attending_doctor"),
                        primaryNurse = obj.optString("primary_nurse"),
                        allergies = jsonArrayToList(obj.optJSONArray("allergies")),
                    )
                }
                val dispensing = tasksFrom(
                    getJson("/api/nurse/dispensing_tasks?target_id=$currentPatientId&limit=100&offset=0").optJSONArray("items"),
                    infusion = false,
                )
                val infusion = tasksFrom(
                    getJson("/api/nurse/infusion_tasks?target_id=$currentPatientId&limit=100&offset=0").optJSONArray("items"),
                    infusion = true,
                )
                val events = eventsFrom(getJson("/api/customer/patients/$currentPatientId/events?limit=100&offset=0").optJSONArray("items"))
                DataBundle(loadedPatients, dispensing, infusion, events)
            },
            onSuccess = {
                patients = it.patients
                if (patients.isNotEmpty() && patients.none { patient -> patient.id == currentPatientId }) {
                    currentPatientId = patients.first().id
                    currentPatientName = patients.first().name
                    casePatientName = currentPatientName
                }
                patients.find { patient -> patient.id == currentPatientId }?.let { patient ->
                    currentPatientName = patient.name
                    casePatientName = patient.name
                }
                dispensingTasks = it.dispensing
                infusionTasks = it.infusion
                timelineEvents = it.events
                if (redraw) showShell()
            },
            onError = {
                if (redraw) toast(s(R.string.msg_refresh_failed, it))
            }
        )
    }

    private fun submitDispensing() {
        val items = dispensingItems.map {
            it.copy(medicine = it.medicine.trim(), specification = it.specification.trim())
        }.filter { it.medicine.isNotBlank() }
        if (items.isEmpty()) {
            toast(s(R.string.msg_enter_medicine))
            return
        }
        val payload = JSONObject()
            .put("target_id", currentPatientId)
            .put("opd_id", opdId)
            .put("medicines", orderArray(items))
        requestAsync(
            block = { postJson("/api/doctor/dispensing_tasks", payload) },
            onSuccess = {
                toast(s(R.string.msg_dispensing_submitted))
                refreshData(redraw = true)
            },
            onError = { toast(s(R.string.msg_submit_failed, it)) }
        )
    }

    private fun submitInfusion() {
        val items = infusionItems.map {
            it.copy(medicine = it.medicine.trim(), specification = it.specification.trim())
        }.filter { it.medicine.isNotBlank() }
        if (items.isEmpty()) {
            toast(s(R.string.msg_enter_fluid))
            return
        }
        val payload = JSONObject()
            .put("target_id", currentPatientId)
            .put("opd_id", opdId)
            .put("fluids", orderArray(items))
        requestAsync(
            block = { postJson("/api/doctor/infusion_tasks", payload) },
            onSuccess = {
                toast(s(R.string.msg_infusion_submitted))
                refreshData(redraw = true)
            },
            onError = { toast(s(R.string.msg_submit_failed, it)) }
        )
    }

    private fun generateCase() {
        if (caseDraft.isBlank()) {
            toast(s(R.string.msg_enter_conversation))
            return
        }
        val fields = mapOf(
            "patient_id" to currentPatientId,
            "patient_name" to casePatientName.ifBlank { currentPatientName },
            "opd_id" to caseOpdId.ifBlank { opdId },
            "message" to caseDraft,
        )
        caseAnswer = s(R.string.msg_generating)
        caseResultView?.text = caseAnswer
        requestAsync(
            block = { multipart("/api/doctor/case-generator", fields, null) },
            onSuccess = {
                caseAnswer = it.optString("answer", it.toString())
                caseResultView?.text = caseAnswer
            },
            onError = {
                caseAnswer = s(R.string.msg_case_failed, it)
                caseResultView?.text = caseAnswer
            }
        )
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            toast(s(R.string.msg_mic_permission))
            return
        }
        try {
            audioFile = File(cacheDir, "doctor_case_${System.currentTimeMillis()}.m4a")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            toast(s(R.string.msg_recording_started))
            showShell()
        } catch (e: Exception) {
            stopRecordingIfNeeded()
            toast(s(R.string.msg_recording_failed, e.message ?: ""))
        }
    }

    private fun stopRecordingAndTranscribe() {
        stopRecordingIfNeeded()
        val file = audioFile
        if (file == null || !file.exists()) {
            toast(s(R.string.msg_no_audio))
            return
        }
        toast(s(R.string.msg_transcribing))
        requestAsync(
            block = { multipart("/api/doctor/audio/transcriptions", emptyMap(), file) },
            onSuccess = {
                val text = it.optString("text")
                if (text.isBlank()) {
                    toast(s(R.string.msg_no_text))
                } else {
                    caseDraft = if (caseDraft.isBlank()) text else "$caseDraft\n$text"
                    caseTextView?.setText(caseDraft)
                    toast(s(R.string.msg_voice_converted))
                    showShell()
                }
            },
            onError = { toast(s(R.string.msg_transcription_failed, it)) }
        )
    }

    private fun stopRecordingIfNeeded() {
        if (isRecording) {
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
    }

    private fun getJson(path: String): JSONObject {
        return readJson(connection(path, "GET"))
    }

    private fun postJson(path: String, body: JSONObject, auth: Boolean = true): JSONObject {
        val conn = connection(path, "POST", auth)
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        return readJson(conn)
    }

    private fun putJson(path: String, body: JSONObject): JSONObject {
        val conn = connection(path, "PUT")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        return readJson(conn)
    }

    private fun patchJson(path: String, body: JSONObject): JSONObject {
        val conn = connection(path, "PATCH")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        return readJson(conn)
    }

    private fun deleteJson(path: String): JSONObject {
        return readJson(connection(path, "DELETE"))
    }

    private fun multipart(path: String, fields: Map<String, String>, file: File?): JSONObject {
        val boundary = "----HyMed${System.currentTimeMillis()}"
        val conn = connection(path, "POST")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.doOutput = true
        DataOutputStream(conn.outputStream).use { out ->
            fields.forEach { (key, value) ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                out.write(value.toByteArray(StandardCharsets.UTF_8))
                out.writeBytes("\r\n")
            }
            if (file != null) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n")
            }
            out.writeBytes("--$boundary--\r\n")
        }
        return readJson(conn)
    }

    private fun connection(path: String, method: String, auth: Boolean = true): HttpURLConnection {
        return (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 180000
            setRequestProperty("Accept", "application/json")
            if (auth && token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
        if (code >= 400) throw IOException(text.ifBlank { "HTTP $code" })
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun tasksFrom(array: JSONArray?, infusion: Boolean): List<Task> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            val obj = array.optJSONObject(it) ?: return@mapNotNull null
            val rawItems = if (infusion) obj.optJSONArray("fluids") else obj.optJSONArray("medicines")
            Task(
                id = obj.optString("task_id"),
                done = obj.optBoolean("is_completed") || obj.optString("status").contains("complete", ignoreCase = true),
                status = obj.optString("status"),
                updatedAt = obj.optString("status_updated_at", obj.optString("updated_at")),
                items = orderItems(rawItems),
            )
        }
    }

    private fun orderItems(array: JSONArray?): List<OrderItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            val obj = array.optJSONObject(it) ?: return@mapNotNull null
            OrderItem(
                medicine = obj.optString("medicine", obj.optString("drug_name")),
                specification = obj.optString("specification", obj.optString("dosage")),
                amount = obj.optInt("amount", 1),
            )
        }
    }

    private fun eventsFrom(array: JSONArray?): List<TimelineEvent> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull {
            val obj = array.optJSONObject(it) ?: return@mapNotNull null
            TimelineEvent(
                title = obj.optString("title", obj.optString("action_code")),
                summary = obj.optString("summary", obj.optString("description")),
                time = obj.optString("time"),
                user = "${obj.optString("user_role")} ${obj.optString("user_id")}".trim(),
            )
        }
    }

    private fun orderArray(items: List<OrderItem>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("medicine", it.medicine)
                    .put("specification", it.specification)
                    .put("amount", it.amount)
            )
        }
        return array
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, null) }
    }

    private fun splitAllergies(text: String): List<String> {
        return text.split(",", "，", "、", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun <T> requestAsync(block: () -> T, onSuccess: (T) -> Unit, onError: (String) -> Unit) {
        thread {
            try {
                val result = block()
                runOnUiThread { onSuccess(result) }
            } catch (e: Exception) {
                runOnUiThread { onError(e.message ?: "Request failed") }
            }
        }
    }

    private fun actionButton(title: String, subtitle: String, iconGlyph: String = "●", click: () -> Unit): View {
        return Button(this).apply {
            text = "$iconGlyph  $title\n     $subtitle"
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setTextColor(fg())
            background = rounded(cardColor(), dp(18), strokeColor = borderColor())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stateListAnimator = null
                elevation = dp(1).toFloat()
            }
            setOnClickListener { click() }
        }.withMargin()
    }

    private fun eventRow(event: TimelineEvent): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(10))
            addView(TextView(this@MainActivity).apply {
                text = icon("clock")
                setTextColor(blue)
                setPadding(0, dp(2), dp(10), 0)
                textSize = 14f
            })
            addView(TextView(this@MainActivity).apply {
                text = "${event.title}\n${event.summary}\n${event.time}  /  ${event.user}"
                setTextColor(fg())
                textSize = if (isTablet) 15f else 13f
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun section(title: String, iconKey: String? = null): LinearLayout {
        return card().apply {
            addView(TextView(this@MainActivity).apply {
                text = if (iconKey != null) "${icon(iconKey)}  $title" else title
                textSize = if (isTablet) 22f else 18f
                setTextColor(fg())
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(10))
            })
        }.withMargin() as LinearLayout
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(cardColor())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                elevation = cardElevation()
            }
        }
    }

    private fun vertical(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(muted())
            setPadding(0, dp(8), 0, dp(5))
        }
    }

    private fun empty(textValue: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(16), dp(12), dp(16))
            addView(TextView(this@MainActivity).apply {
                text = "●"
                setTextColor(muted())
                setPadding(0, 0, dp(6), 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = textValue
                textSize = if (isTablet) 16f else 14f
                setTextColor(muted())
            })
        }
    }

    private fun input(hintValue: String, value: String): EditText {
        return EditText(this).apply {
            hint = hintValue
            setText(value)
            setTextColor(fg())
            setHintTextColor(muted())
            background = rounded(inputBg(), dp(12), strokeColor = borderColor())
            minHeight = dp(46)
            setPadding(dp(12), 0, dp(12), 0)
        }
    }

    private fun primaryButton(textValue: String, iconGlyph: String? = null, click: () -> Unit): Button {
        return Button(this).apply {
            text = if (iconGlyph != null) "$iconGlyph  $textValue" else textValue
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(blue, btnCornerRadius())
            setPadding(dp(4), dp(12), dp(4), dp(12))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stateListAnimator = null
                elevation = dp(1).toFloat()
            }
            setOnClickListener { click() }
        }
    }

    private fun View.withMargin(): View {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
        return this
    }

    private fun rounded(color: Int, radius: Int = dp(20), strokeColor: Int? = null, strokeWidth: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null) {
                setStroke(dp(strokeWidth), strokeColor)
            }
        }
    }

    private fun openFeature(next: Feature) {
        mainTab = MainTab.FEATURES
        feature = next
        showShell()
    }

    private fun title(): String = when (mainTab) {
        MainTab.HOME -> s(R.string.title_doctor_home)
        MainTab.FEATURES -> if (feature == Feature.MENU) s(R.string.title_features) else when (feature) {
            Feature.DISPENSING -> s(R.string.title_dispensing)
            Feature.INFUSION -> s(R.string.title_infusion)
            Feature.CASE -> s(R.string.title_case_ai)
            Feature.CT -> s(R.string.title_ct)
            Feature.TIMELINE -> s(R.string.title_data)
            Feature.PATIENT_MANAGEMENT -> s(R.string.title_patient_management)
            Feature.MENU -> s(R.string.title_features)
        }
        MainTab.SETTINGS -> s(R.string.title_settings)
    }

    private fun infusionStatusText(task: Task): String {
        return when {
            task.done -> s(R.string.label_done)
            task.status.isNotBlank() -> task.status
            else -> s(R.string.label_pending)
        }
    }

    private fun formatOrderItems(items: List<OrderItem>): String {
        if (items.isEmpty()) return "-"
        return items.joinToString("\n") {
            val spec = it.specification.ifBlank { "-" }
            "${it.medicine} $spec x${it.amount}"
        }
    }

    private fun bg() = if (darkMode) 0xFF0F172A.toInt() else 0xFFF0F4F8.toInt()
    private fun fg() = if (darkMode) 0xFFEEF4FF.toInt() else 0xFF132033.toInt()
    private fun muted() = if (darkMode) 0xFFA7B4C6.toInt() else 0xFF6A7688.toInt()
    private fun cardColor() = if (darkMode) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
    private fun inputBg() = if (darkMode) 0xFF263449.toInt() else 0xFFF8FBFE.toInt()
    private fun softBlue() = if (darkMode) 0xFF193655.toInt() else 0xFFE7F2FF.toInt()
    private fun borderColor() = if (darkMode) 0xFF334155.toInt() else 0xFFE2E8F0.toInt()
    private fun successColor() = if (darkMode) 0xFF22C55E.toInt() else 0xFF16A34A.toInt()
    private fun warningColor() = if (darkMode) 0xFFFBBF24.toInt() else 0xFFF59E0B.toInt()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    // ── Elevation helper ──
    private fun cardElevation(): Float = if (isTablet) dp(3).toFloat() else dp(2).toFloat()
    private fun btnCornerRadius(): Int = if (isTablet) dp(16) else dp(14)

    enum class MainTab { HOME, FEATURES, SETTINGS }
    enum class Feature { MENU, DISPENSING, INFUSION, CASE, CT, TIMELINE, PATIENT_MANAGEMENT }
    data class Patient(
        val id: String,
        val name: String,
        val gender: String = "",
        val age: String = "",
        val department: String = "",
        val bedNo: String = "",
        val diagnosis: String = "",
        val attendingDoctor: String = "",
        val primaryNurse: String = "",
        val allergies: List<String> = emptyList()
    )
    data class OrderItem(var medicine: String, var specification: String, var amount: Int)
    data class Task(val id: String, val done: Boolean, val status: String, val updatedAt: String, val items: List<OrderItem>)
    data class TimelineEvent(val title: String, val summary: String, val time: String, val user: String)
    data class DataBundle(
        val patients: List<Patient>,
        val dispensing: List<Task>,
        val infusion: List<Task>,
        val events: List<TimelineEvent>,
    )
}
