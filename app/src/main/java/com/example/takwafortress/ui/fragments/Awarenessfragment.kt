package com.example.takwafortress.ui.fragments

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

// ══════════════════════════════════════════════════════════════════════════════
// PALETTE — Soft Dark  (matches DashboardFragment — lifted, breathable)
// ══════════════════════════════════════════════════════════════════════════════
private val BG_DARK      = Color.parseColor("#161B27")   // background — warm navy, not black
private val CARD_BG      = Color.parseColor("#1E2535")   // card surface — clear lift above bg
private val BORDER_DIM   = Color.parseColor("#2A3347")   // border — visible but soft
private val BORDER_TOP   = Color.parseColor("#354059")   // top-highlight line — blue-grey
private val GREEN        = Color.parseColor("#5DB88A")   // active — muted teal-green
private val GREEN_BRIGHT = Color.parseColor("#5DB88A")   // same — one consistent green
private val TEXT_WHITE   = Color.parseColor("#EFF3F8")   // headings — soft white, not harsh
private val TEXT_SOFT    = Color.parseColor("#D4DCE8")   // body — warm, easy to read
private val TEXT_GREY    = Color.parseColor("#7A8BA0")   // captions — bluer, matches palette
private val DIVIDER_CLR  = Color.parseColor("#252E3F")   // subtle divider

data class Article(
    val category: String,
    val categoryColor: Int,
    val title: String,
    val summary: String,
    val readMinutes: Int,
    val content: String
)

val ARTICLES = listOf(
    Article(
        category = "UNDERSTANDING",
        categoryColor = Color.parseColor("#E57373"),
        title = "Why Your Brain Craves It: The Neuroscience",
        summary = "Dopamine, reward circuits, and why willpower alone is never enough.",
        readMinutes = 5,
        content = """The brain doesn't distinguish between real intimacy and artificial stimulation. When you consume explicit content, your brain releases dopamine — the same chemical released by food, social connection, and achievement.

The problem is escalation. Over time, the brain adapts. What once produced a strong dopamine response requires more, more intense, more frequent stimulation to produce the same effect. This is called tolerance.

This is not a moral failing. It is neuroscience.

The good news: the brain is plastic. Neuroplasticity means the circuits that were strengthened through repetition can be weakened through abstinence and replaced with healthier reward pathways.

Recovery is not about willpower fighting an urge. Recovery is about literally rewiring your brain. Every day of abstinence is a day your dopamine system recalibrates toward baseline.

The first 30 days are the hardest. After 90 days, most people report cravings are manageable. After 6 months, the rewiring is significant.

You are not broken. Your brain adapted to what it was given. Give it something better."""
    ),
    Article(
        category = "RECOVERY",
        categoryColor = Color.parseColor("#5DB88A"),
        title = "The 90-Day Reset: What Actually Happens",
        summary = "A week-by-week breakdown of what your brain and body go through.",
        readMinutes = 6,
        content = """Week 1-2: Withdrawal
Your brain is used to spikes of artificial dopamine. Without them, you may feel flat, irritable, anxious, or have trouble sleeping. This is normal. This is withdrawal. It passes.

Week 3-4: Flatline
Many people experience a "flatline" — a period of low energy and low motivation. This is your brain adjusting to normal dopamine levels. It is temporary.

Week 5-8: Fog lifting
Most people report that mental fog begins to clear. Concentration improves. Social anxiety decreases. You start noticing real beauty in the world again.

Week 9-12: New normal
The dopamine system is recalibrating. You may find yourself more present in conversations, more creative, more emotionally available. Real relationships feel more rewarding.

Beyond 90 days: Long-term rewiring
The neural pathways associated with the addiction weaken from disuse. The pathways associated with real connection, achievement, and meaning strengthen. This is the science of recovery.

Consistency matters more than perfection. A slip doesn't erase your progress. Get up and continue."""
    ),
    Article(
        category = "IDENTITY",
        categoryColor = Color.parseColor("#D4924A"),
        title = "You Are Not Your Urges",
        summary = "The difference between having a craving and being controlled by one.",
        readMinutes = 4,
        content = """An urge is a wave. It rises, peaks, and falls — usually within 20 minutes. You do not have to surf it. You can let it crash on the shore without being swept away.

The mistake most people make is identifying with their urges. "I am someone who needs this." That is not true. That is the addiction speaking.

You are the one watching the urge. You are the observer, not the craving.

Urge surfing: When a craving comes, don't fight it and don't follow it. Observe it. Notice where you feel it in your body. Name it. "There is an urge. It is in my chest. It feels like tension." Watch it rise. Watch it peak. Watch it fall.

Every time you watch an urge rise and fall without acting on it, you weaken the neural pathway that says "urge → action." You strengthen the pathway that says "urge → I am in control."

This is how character is built. Not in the absence of difficulty, but in the response to it."""
    ),
    Article(
        category = "FAITH",
        categoryColor = Color.parseColor("#9B72CF"),
        title = "Tawbah: The Door That Never Closes",
        summary = "On guilt, self-compassion, and the Islamic understanding of return.",
        readMinutes = 5,
        content = """One of the most destructive patterns in recovery is shame spiraling. A slip happens. Shame floods in. The shame feels unbearable. To escape the shame, the behavior is repeated. More shame. The cycle deepens.

Islam provides a different framework. Tawbah — repentance — is not about self-punishment. It is about turning back. The word itself means "to return."

Allah does not want you to drown in shame. He wants you to return.

"Say: O My servants who have transgressed against their own souls, do not despair of the mercy of Allah. Indeed, Allah forgives all sins." — Quran 39:53

The conditions of tawbah are: sincere regret, stopping the action, resolving not to return. Notice what is not on the list: perfect execution, never slipping again, feeling worthy.

Your worth is not conditional on your performance. You were created with worth. The struggle is not evidence that you are broken. It is evidence that you are human.

Return. Again and again if needed. The door does not close."""
    ),
    Article(
        category = "PRACTICAL",
        categoryColor = Color.parseColor("#4A90D9"),
        title = "Building Your Environment for Success",
        summary = "Removing triggers before they become temptations.",
        readMinutes = 4,
        content = """Willpower is a limited resource. Every time you rely on willpower to resist a trigger, you deplete it. The solution is not more willpower. The solution is fewer triggers.

Environment design is more powerful than resolve.

1. Device hygiene
Content filters (like this app) handle the digital environment. Don't just rely on willpower when boredom hits — make the content inaccessible by default.

2. Time awareness
Most relapses happen in specific windows: late at night, alone, when stressed. Identify your high-risk times and fill them with alternative activities before the urge arrives.

3. The 5-second rule
When a trigger appears, you have approximately 5 seconds before the urge gains momentum. In that window, move. Physically stand up. Change rooms. Splash cold water on your face. Interrupt the pattern before it completes.

4. Accountability
Humans behave differently when they know someone is aware of their struggle. An accountability partner — even without sharing details — dramatically increases success rates.

5. Replace, don't just remove
The brain craves stimulation. If you only remove the source, the craving will search for another outlet. Replace the habit with something that provides genuine reward: exercise, learning, creative work, service."""
    )
)

class AwarenessFragment : Fragment() {

    private lateinit var frameRoot: FrameLayout
    private lateinit var listScrollView: ScrollView
    private lateinit var articleContainer: LinearLayout

    private var articleTitleView: TextView? = null
    private var articleCategoryView: TextView? = null
    private var articleReadTimeView: TextView? = null
    private var articleContentView: TextView? = null

    companion object {
        fun newInstance() = AwarenessFragment()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = buildUi()

    private fun buildUi(): View {
        frameRoot = FrameLayout(requireContext()).apply {
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        listScrollView = buildListView()
        articleContainer = buildArticleView()
        articleContainer.visibility = View.GONE
        frameRoot.addView(listScrollView)
        frameRoot.addView(articleContainer)
        return frameRoot
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIST VIEW
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildListView(): ScrollView {
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
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
            text = "Awareness"
            textSize = 20f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; letterSpacing = 0.03f; setPadding(0, dp(4), 0, dp(6))
        })
        content.addView(TextView(requireContext()).apply {
            text = "Knowledge that strengthens your journey"
            textSize = 13f; setTextColor(TEXT_GREY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        ARTICLES.forEach { article ->
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                background = cardDrawable(CARD_BG, BORDER_DIM)
            }

            card.addView(makeCategoryPill(article.category, article.categoryColor))

            card.addView(TextView(requireContext()).apply {
                text = article.title
                textSize = 16f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(8))
            })

            card.addView(TextView(requireContext()).apply {
                text = article.summary
                textSize = 13f; setTextColor(TEXT_SOFT); setLineSpacing(0f, 1.4f)
                setPadding(0, 0, 0, dp(14))
            })

            card.addView(View(requireContext()).apply {
                setBackgroundColor(DIVIDER_CLR)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { bottomMargin = dp(12) }
            })

            val footer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            footer.addView(TextView(requireContext()).apply {
                text = "📖  ${article.readMinutes} min read"
                textSize = 11f; setTextColor(TEXT_GREY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            // Ghost pill CTA — uses soft blue border to match Dashboard ghost buttons
            footer.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(12), dp(5))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.parseColor("#2A3347"))
                    cornerRadius = dp(20).toFloat()
                }
                addView(TextView(requireContext()).apply {
                    text = "Read  →"; textSize = 12f; setTextColor(GREEN_BRIGHT); typeface = Typeface.DEFAULT_BOLD
                })
            })
            card.addView(footer)

            card.setOnClickListener { showArticle(article) }
            content.addView(card)
        }

        scroll.addView(content)
        return scroll
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ARTICLE VIEW
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildArticleView(): LinearLayout {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BG_DARK)
            setPadding(dp(16), getStatusBarHeight() + dp(10), dp(16), dp(10))
        }
        val backBtn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(14), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#2A3347"))
                cornerRadius = dp(20).toFloat()
            }
        }
        backBtn.addView(TextView(requireContext()).apply {
            text = "←  Back"; textSize = 13f; setTextColor(GREEN_BRIGHT); typeface = Typeface.DEFAULT_BOLD
        })
        backBtn.setOnClickListener {
            articleContainer.visibility = View.GONE
            listScrollView.visibility = View.VISIBLE
        }
        topBar.addView(backBtn)
        container.addView(topBar)

        container.addView(View(requireContext()).apply {
            setBackgroundColor(DIVIDER_CLR)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        })

        val articleScroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = false
        }
        val articleContent = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(56))
        }

        articleCategoryView = TextView(requireContext()).apply {
            textSize = 10f; letterSpacing = 0.15f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(10))
        }
        articleContent.addView(articleCategoryView)

        articleTitleView = TextView(requireContext()).apply {
            textSize = 22f; setTextColor(TEXT_WHITE); typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.2f); setPadding(0, 0, 0, dp(10))
        }
        articleContent.addView(articleTitleView)

        articleReadTimeView = TextView(requireContext()).apply {
            textSize = 11f; setTextColor(TEXT_GREY); setPadding(0, 0, 0, dp(4))
        }
        articleContent.addView(articleReadTimeView)

        articleContent.addView(View(requireContext()).apply {
            setBackgroundColor(DIVIDER_CLR)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(4); bottomMargin = dp(24) }
        })

        articleContentView = TextView(requireContext()).apply {
            textSize = 15f; setTextColor(TEXT_SOFT); setLineSpacing(0f, 1.75f)
        }
        articleContent.addView(articleContentView)

        articleScroll.addView(articleContent)
        container.addView(articleScroll)
        return container
    }

    private fun showArticle(article: Article) {
        articleCategoryView?.text = article.category
        articleCategoryView?.setTextColor(article.categoryColor)
        articleReadTimeView?.text = "📖  ${article.readMinutes} min read"
        articleTitleView?.text = article.title
        articleContentView?.text = article.content
        (articleContainer.getChildAt(2) as? ScrollView)?.scrollTo(0, 0)
        listScrollView.visibility = View.GONE
        articleContainer.visibility = View.VISIBLE
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI FACTORIES
    // ══════════════════════════════════════════════════════════════════════════

    private fun makeCategoryPill(label: String, color: Int) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(3), dp(10), dp(3))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color.withAlpha(28)); cornerRadius = dp(20).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        addView(View(requireContext()).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { rightMargin = dp(6); gravity = Gravity.CENTER_VERTICAL }
        })
        addView(TextView(requireContext()).apply {
            text = label; textSize = 10f; setTextColor(color); letterSpacing = 0.15f; typeface = Typeface.DEFAULT_BOLD
        })
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