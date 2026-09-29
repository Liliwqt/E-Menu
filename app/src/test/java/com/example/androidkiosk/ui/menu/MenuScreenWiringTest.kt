package com.example.androidkiosk.ui.menu

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.readText

/**
 * Source-level guard on the MenuScreen wiring.
 *
 * `MenuNavigation` is exhaustively unit tested, but this repo has been bitten twice by a correct
 * helper that nothing called — the bug passes every unit test and only shows up on a device.
 * `MenuScreen` takes a Hilt-injected `MenuViewModel`, so it cannot be composed in a plain
 * instrumentation test without standing up the whole graph. Asserting on the source is the
 * cheapest guard that actually catches the real regression: someone reintroducing the boolean
 * flags, or dropping the Back handler, and silently restoring the "Back kills the app and the
 * cart" behaviour.
 */
class MenuScreenWiringTest {

    private val source: String = run {
        val candidates = listOf(
            "src/main/java/com/example/androidkiosk/ui/menu/MenuScreen.kt",
            "app/src/main/java/com/example/androidkiosk/ui/menu/MenuScreen.kt"
        )
        val file = candidates.map(::File).firstOrNull(File::exists)
        if (file == null) {
            throw AssertionError("MenuScreen.kt not found from ${File(".").absolutePath}")
        }
        file.readText()
    }

    /**
     * [source] with comments removed.
     *
     * This file's comments discuss the Back handler at length, so any check that searches the
     * raw text can be satisfied by a paragraph of prose. Stripping comments first is what makes
     * the assertions mean something.
     */
    private val strippedSource: String = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        .replace(source, "")
        .let { Regex("""//[^\n]*""").replace(it, "") }
    @Test
    fun `the boolean overlay flags are gone`() {
        // Each of these was a stage with no back behaviour. If any reappears as a plain
        // mutableStateOf flag, the stack is no longer the single source of truth.
        val flags = listOf(
            "selectedItem", "showCart", "showCheckout",
            "showPaymentMethod", "showQRPayment", "showCounterPayment"
        )
        flags.forEach { flag ->
            val declaration = Regex("""var\s+$flag\s+by\s+remember""")
            assertTrue(
                "'$flag' is declared as independent state again; the overlay stack must be the " +
                    "only source of truth so Back can pop it",
                !declaration.containsMatchIn(strippedSource)
            )
        }
    }

    @Test
    fun `a Back handler unwinds the stack`() {
        // Comments are stripped first, and the pattern requires a real call with a lambda
        // argument. A bare token search is not enough: during the revert-check an `if (false)`
        // wrapper left the word "BackHandler" in the file and the test still passed, which is
        // precisely the "correct code, never executed" failure this file exists to catch.
        val live = Regex("""BackHandler\s*(\([^)]*\))?\s*\{""")
        assertTrue(
            "MenuScreen must register a live BackHandler; without one Back finishes the " +
                "Activity from every overlay and destroys the cart",
            live.containsMatchIn(strippedSource)
        )
        assertTrue(
            "The BackHandler must consult MenuNavigation.backAction rather than deciding " +
                "inline, so the exit guard stays provable by MenuNavigationTest",
            strippedSource.contains("MenuNavigation.backAction(navState, cartItems)")
        )
        assertTrue(
            "Back at an empty cart must still finish the Activity, or staff are locked out of " +
                "the app on their own phones",
            strippedSource.contains("BackAction.EXIT -> activity?.finish()")
        )
    }

    @Test
    fun `every stage transition pushes or pops instead of setting a flag`() {
        assertTrue(
            "Opening the cart must push onto the stack",
            strippedSource.contains("MenuNavigation.push(navState, MenuNavigation.Stage.Cart)")
        )
        assertTrue(
            "Closing an overlay must pop the stack",
            strippedSource.contains("MenuNavigation.pop(navState)")
        )
    }

    @Test
    fun `a full cart is never discarded without asking`() {
        // Asserting only that BackAction.CONFIRM_EXIT is *mentioned* is not enough: the
        // revert-check caught a version that routed it straight to finish() and the test still
        // passed. This pins the actual behaviour — the branch must open the dialog, and the
        // dialog must be the only thing that finishes the Activity on that path.
        val opensDialog = Regex(
            """BackAction\.CONFIRM_EXIT\s*->\s*showExitConfirmation\s*=\s*true"""
        )
        assertTrue(
            "Back with a non-empty cart must open the confirmation dialog, not finish the " +
                "Activity — that is the whole point of the guard",
            opensDialog.containsMatchIn(strippedSource)
        )
        assertTrue(
            "The dialog's 'Leave anyway' must be what actually finishes the Activity, so the " +
                "customer can always leave",
            Regex("""onLeave\s*=\s*\{[^}]*activity\?\.finish\(\)""")
                .containsMatchIn(strippedSource)
        )
        assertTrue(
            "The dialog must offer a way to stay and keep the cart",
            strippedSource.contains("onStay = { showExitConfirmation = false }")
        )
    }

    @Test
    fun `the exit guard is registered with the blur and overlay checks`() {
        // Missing either one is a real bug: the content would not blur behind the dialog, and
        // the admin button would stay visible and tappable through the scrim.
        val blurTarget = Regex("""targetValue\s*=\s*if\s*\([^)]*showExitConfirmation""")
        assertTrue(
            "showExitConfirmation must be part of the blur condition, or the menu stays sharp " +
                "behind the dialog",
            blurTarget.containsMatchIn(strippedSource)
        )
        val anyOverlay = Regex("""anyOverlayOpen\s*=\s*[^;]*showExitConfirmation""")
        assertTrue(
            "showExitConfirmation must be part of anyOverlayOpen, or the admin button shows " +
                "through the dialog",
            anyOverlay.containsMatchIn(strippedSource)
        )
    }

    @Test
    fun `the customer name is hoisted so checkout survives a detour`() {
        assertTrue(
            "CheckoutOverlay must take customerName as a parameter; a private remember here " +
                "discards a half-typed name whenever the customer closes and reopens checkout",
            Regex("""customerName:\s*String,""").containsMatchIn(strippedSource)
        )
    }
}
