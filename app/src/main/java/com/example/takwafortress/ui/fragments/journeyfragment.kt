package com.example.takwafortress.ui.fragments

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════════════════
// PALETTE — Soft Dark  (matches DashboardFragment — lifted, breathable)
// ══════════════════════════════════════════════════════════════════════════════
private val BG_DARK          = Color.parseColor("#161B27")   // background — warm navy, not black
private val CARD_BG          = Color.parseColor("#1E2535")   // card surface — clear lift above bg
private val CARD_HERO_BG     = Color.parseColor("#152238")   // hero card — deep blue-navy
private val CARD_HERO_BORDER = Color.parseColor("#1F3554")   // hero card border
private val BORDER_DIM       = Color.parseColor("#2A3347")   // border — visible but soft
private val BORDER_TOP       = Color.parseColor("#354059")   // top-highlight line — blue-grey
private val GREEN            = Color.parseColor("#5DB88A")   // active — muted teal-green
private val GREEN_BRIGHT     = Color.parseColor("#5DB88A")   // same — one consistent green
private val GREEN_DIM        = Color.parseColor("#1A3040")   // green pill bg — navy-tinted
private val YELLOW           = Color.parseColor("#D4A847")   // okay mood — warmer, less neon
private val RED_SOFT         = Color.parseColor("#E05C5C")   // hard day — matches palette red
private val EMPTY_DAY        = Color.parseColor("#1A2030")   // heatmap empty cell
private val TEXT_WHITE       = Color.parseColor("#EFF3F8")   // headings — soft white, not harsh
private val TEXT_SOFT        = Color.parseColor("#D4DCE8")   // body — warm, easy to read
private val TEXT_GREY        = Color.parseColor("#7A8BA0")   // captions — bluer, matches palette
private val DIVIDER_CLR      = Color.parseColor("#252E3F")   // subtle divider

data class EmojiOption(val emoji: String, val label: String, val mood: Int)

val EMOJI_OPTIONS = listOf(
    EmojiOption("💪", "Strong",   2),
    EmojiOption("🙏", "Grateful", 2),
    EmojiOption("😐", "Okay",     1),
    EmojiOption("😴", "Tired",    1),
    EmojiOption("😤", "Tempted",  0),
    EmojiOption("💔", "Hard Day", 0)
)

class JourneyFragment : Fragment() {

    private val PREFS_NAME = "journey_data"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    companion object {
        fun newInstance() = JourneyFragment()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = buildUi()

    private fun buildUi(): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), getStatusBarHeight() + dp(12), dp(16), dp(32))
        }

        // ── Header ────────────────────────────────────────────────────────────
        content.addView(TextView(requireContext()).apply {
            text = "TAKWA FORTRESS"
            textSize = 10f; setTextColor(TEXT_GREY); letterSpacing = 0.25f; gravity = Gravity.CENTER
        })
        content.addView(TextView(requireContext()).apply {
            text = "Your Journey"
            textSize = 20f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; letterSpacing = 0.03f; setPadding(0, dp(4), 0, dp(6))
        })
        content.addView(TextView(requireContext()).apply {
            text = "Track your daily state through the commitment"
            textSize = 13f; setTextColor(TEXT_GREY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        // ── HERO: Daily check-in card ─────────────────────────────────────────
        val today = dateFormat.format(Date())
        val todayData = getDayData(today)
        val alreadyCheckedIn = todayData != null

        val checkInCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            background = cardDrawable(CARD_HERO_BG, CARD_HERO_BORDER)
        }

        // Section label row with pulse dot
        val labelRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }
        labelRow.addView(View(requireContext()).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GREEN_BRIGHT) }
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { rightMargin = dp(8); gravity = Gravity.CENTER_VERTICAL }
        })
        labelRow.addView(TextView(requireContext()).apply {
            text = "TODAY'S CHECK-IN"
            textSize = 10f; setTextColor(Color.parseColor("#7FA8CC")); letterSpacing = 0.2f
        })
        checkInCard.addView(labelRow)

        if (alreadyCheckedIn) {
            val checkedRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            checkedRow.addView(TextView(requireContext()).apply {
                text = todayData!!.optString("emoji", "")
                textSize = 40f; setPadding(0, 0, dp(16), 0)
            })
            val checkedInfo = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            checkedInfo.addView(TextView(requireContext()).apply {
                text = todayData!!.optString("label", "")
                textSize = 18f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            })
            val note = todayData!!.optString("note", "")
            if (note.isNotEmpty()) {
                checkedInfo.addView(TextView(requireContext()).apply {
                    text = "\"$note\""
                    textSize = 13f; setTextColor(TEXT_SOFT); setLineSpacing(0f, 1.4f)
                    setPadding(0, dp(4), 0, 0)
                })
            }
            checkedRow.addView(checkedInfo)
            checkInCard.addView(checkedRow)

            checkInCard.addView(View(requireContext()).apply {
                setBackgroundColor(Color.parseColor("#1F3554"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(14); bottomMargin = dp(10) }
            })
            checkInCard.addView(TextView(requireContext()).apply {
                text = "✅  Checked in today"
                textSize = 12f; setTextColor(GREEN_BRIGHT)
            })
        } else {
            checkInCard.addView(TextView(requireContext()).apply {
                text = "How are you feeling today?"
                textSize = 15f; setTextColor(TEXT_SOFT); setPadding(0, 0, 0, dp(16))
            })

            val emojiGrid = GridLayout(requireContext()).apply {
                columnCount = 3
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            var selectedOption: EmojiOption? = null
            val emojiButtons = mutableListOf<LinearLayout>()

            EMOJI_OPTIONS.forEach { option ->
                val btn = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(8), dp(12), dp(8), dp(12))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(EMPTY_DAY); setStroke(dp(1), BORDER_DIM); cornerRadius = dp(10).toFloat()
                    }
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                }
                btn.addView(TextView(requireContext()).apply {
                    text = option.emoji; textSize = 28f; gravity = Gravity.CENTER
                })
                btn.addView(TextView(requireContext()).apply {
                    text = option.label; textSize = 11f; setTextColor(TEXT_GREY)
                    gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
                })
                emojiButtons.add(btn)
                btn.setOnClickListener {
                    selectedOption = option
                    emojiButtons.forEach { b ->
                        (b.background as? GradientDrawable)?.setColor(EMPTY_DAY)
                    }
                    (btn.background as? GradientDrawable)?.setColor(when (option.mood) {
                        2 -> Color.parseColor("#1A3040")   // green-navy tint
                        1 -> Color.parseColor("#2A2210")   // warm amber tint
                        else -> Color.parseColor("#2E1A1A") // muted red tint
                    })
                }
                emojiGrid.addView(btn)
            }
            checkInCard.addView(emojiGrid)

            val noteInput = EditText(requireContext()).apply {
                hint = "Add a note (optional)"
                setTextColor(TEXT_WHITE); setHintTextColor(TEXT_GREY)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#111825"))
                    setStroke(dp(1), BORDER_DIM); cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                textSize = 14f; maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
            }
            checkInCard.addView(noteInput)

            // Primary save button — solid blue, matches Dashboard
            val saveBtn = Button(requireContext()).apply {
                text = "Save Today's Check-in"
                textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#4A90D9"))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
                ).apply { topMargin = dp(12) }
            }
            saveBtn.setOnClickListener {
                val opt = selectedOption ?: run {
                    Toast.makeText(requireContext(), "Please select how you're feeling", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveDayData(today, opt, noteInput.text.toString().trim())
                val parent = view?.parent as? ViewGroup
                if (parent != null) {
                    val newView = buildUi()
                    val index = parent.indexOfChild(view)
                    parent.removeViewAt(index)
                    parent.addView(newView, index)
                }
            }
            checkInCard.addView(saveBtn)
        }
        content.addView(checkInCard)

        // ── Heatmap card ──────────────────────────────────────────────────────
        val heatmapCard = makeCard()

        val hmHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        hmHeader.addView(TextView(requireContext()).apply {
            text = "MOOD HISTORY"
            textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        hmHeader.addView(TextView(requireContext()).apply {
            text = "Last 90 days"; textSize = 10f; setTextColor(TEXT_GREY)
        })
        heatmapCard.addView(hmHeader)

        // Legend
        val legendRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        listOf(
            Pair(GREEN_BRIGHT, "Strong"),
            Pair(YELLOW,       "Okay"),
            Pair(RED_SOFT,     "Hard"),
            Pair(TEXT_GREY,    "No entry")
        ).forEach { (dotColor, label) ->
            legendRow.addView(View(requireContext()).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(5); gravity = Gravity.CENTER_VERTICAL }
            })
            legendRow.addView(TextView(requireContext()).apply {
                text = label; textSize = 10f; setTextColor(TEXT_GREY); setPadding(0, 0, dp(14), 0)
            })
        }
        heatmapCard.addView(legendRow)

        // Day-of-week labels
        val dowRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
            dowRow.addView(TextView(requireContext()).apply {
                text = day; textSize = 9f; setTextColor(TEXT_GREY); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        heatmapCard.addView(dowRow)

        // Grid
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -89)
        val daysToMonday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysToMonday)

        val weeksContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        repeat(13) {
            val weekCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            repeat(7) {
                val dayStr = dateFormat.format(cal.time)
                val dayData = getDayData(dayStr)
                val isToday = dayStr == dateFormat.format(Date())
                val isFuture = cal.time.after(Date())

                val fillColor = when {
                    isFuture        -> Color.parseColor("#111825")
                    dayData == null -> EMPTY_DAY
                    dayData.optInt("mood", -1) == 2 -> Color.parseColor("#1A3040")
                    dayData.optInt("mood", -1) == 1 -> Color.parseColor("#2A2210")
                    dayData.optInt("mood", -1) == 0 -> Color.parseColor("#2E1A1A")
                    else            -> EMPTY_DAY
                }
                val dotColor = when {
                    isFuture        -> Color.TRANSPARENT
                    dayData == null -> EMPTY_DAY
                    dayData.optInt("mood", -1) == 2 -> GREEN_BRIGHT
                    dayData.optInt("mood", -1) == 1 -> YELLOW
                    dayData.optInt("mood", -1) == 0 -> RED_SOFT
                    else            -> EMPTY_DAY
                }

                val cell = TextView(requireContext()).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(fillColor)
                        if (!isFuture && dayData != null) setStroke(dp(1), dotColor.withAlpha(80))
                        cornerRadius = dp(3).toFloat()
                    }
                    if (isToday) {
                        text = "·"; setTextColor(TEXT_WHITE); gravity = Gravity.CENTER; textSize = 8f
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(14)
                    ).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }
                }
                if (dayData != null) {
                    val tapDayStr = dayStr
                    cell.setOnClickListener {
                        val emoji = dayData.optString("emoji", "")
                        val label = dayData.optString("label", "")
                        val note  = dayData.optString("note", "")
                        Toast.makeText(requireContext(),
                            if (note.isEmpty()) "$emoji  $label  ·  $tapDayStr"
                            else "$emoji  $label\n\"$note\"\n$tapDayStr",
                            Toast.LENGTH_LONG).show()
                    }
                }
                weekCol.addView(cell)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            weeksContainer.addView(weekCol)
        }
        heatmapCard.addView(weeksContainer)
        content.addView(heatmapCard)

        // ── Stats card ────────────────────────────────────────────────────────
        val allData = getAllData()
        val statsCard = makeCard()

        statsCard.addView(TextView(requireContext()).apply {
            text = "SUMMARY"
            textSize = 11f; setTextColor(TEXT_GREY); letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        })

        val statsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        listOf(
            Triple("${allData.size}",  "Check-ins",  TEXT_SOFT),
            Triple("${allData.values.count { it.optInt("mood", -1) == 2 }}", "Strong Days", GREEN_BRIGHT),
            Triple("${allData.values.count { it.optInt("mood", -1) == 0 }}", "Hard Days",   RED_SOFT)
        ).forEach { (value, label, color) ->
            val block = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                background = cardDrawable(Color.parseColor("#171F2E"), BORDER_DIM)
            }
            block.addView(TextView(requireContext()).apply {
                text = value; textSize = 28f; setTextColor(color); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            })
            block.addView(TextView(requireContext()).apply {
                text = label; textSize = 10f; setTextColor(TEXT_GREY); gravity = Gravity.CENTER; letterSpacing = 0.05f
                setPadding(0, dp(3), 0, 0)
            })
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(dp(4), 0, dp(4), 0)
            block.layoutParams = params
            statsRow.addView(block)
        }
        statsCard.addView(statsRow)
        content.addView(statsCard)

        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun getPrefs() = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveDayData(date: String, option: EmojiOption, note: String) {
        val json = JSONObject().apply {
            put("emoji", option.emoji); put("label", option.label)
            put("mood", option.mood);   put("note", note)
        }
        getPrefs().edit().putString("day_$date", json.toString()).apply()
    }

    private fun getDayData(date: String): JSONObject? {
        val str = getPrefs().getString("day_$date", null) ?: return null
        return try { JSONObject(str) } catch (e: Exception) { null }
    }

    private fun getAllData(): Map<String, JSONObject> {
        val result = mutableMapOf<String, JSONObject>()
        getPrefs().all.forEach { (key, value) ->
            if (key.startsWith("day_") && value is String) {
                try { result[key.removePrefix("day_")] = JSONObject(value) } catch (e: Exception) {}
            }
        }
        return result
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI FACTORIES
    // ══════════════════════════════════════════════════════════════════════════

    private fun makeCard() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        background = cardDrawable(CARD_BG, BORDER_DIM)
    }

    private fun cardDrawable(fill: Int, border: Int): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill); setStroke(dp(1), border); cornerRadius = dp(12).toFloat()
        }
        val topHighlight = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(BORDER_TOP); cornerRadius = dp(12).toFloat()
        }
        return LayerDrawable(arrayOf(base, topHighlight)).apply {
            setLayerInset(1, dp(1), dp(1), dp(1), 0)
            setLayerHeight(1, dp(1))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    private fun Int.withAlpha(alpha: Int) =
        Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}