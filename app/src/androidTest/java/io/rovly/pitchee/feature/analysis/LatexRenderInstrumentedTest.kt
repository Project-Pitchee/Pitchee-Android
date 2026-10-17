package io.rovly.pitchee.feature.analysis

import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatexRenderInstrumentedTest {
    @Test
    fun rendersCompositeScoreFormula() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markwon = Markwon.builder(context)
            .usePlugin(JLatexMathPlugin.create(18f))
            .build()
        val textView = TextView(context)
        val formulas = listOf(
            """
            $$
            N_r = \max\left(0, \min\left(1, \frac{N - 40}{50}\right)\right)
            $$
            """.trimIndent(),
            """
            $$
            strength = \min\left(\frac{F_0 - 165}{25}, \frac{N - 80}{20}, 1\right)
            $$
            """.trimIndent(),
            """
            $$
            Base = 100 \times \left(\begin{gathered}
            0.50Standard_r + 0.20Naturalness_r\\
            + 0.15F0_r + 0.15Standard_rNaturalness_rF0_r
            \end{gathered}\right)
            $$
            """.trimIndent(),
        )

        formulas.forEach { formula ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                markwon.setMarkdown(textView, formula)
            }
            assertTrue(textView.text.isNotEmpty())
        }
    }
}
