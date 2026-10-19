package nl.planernl.unraider.data

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Tekst die pas in de UI wordt omgezet naar de actieve taal.
 * Zo kunnen de data-laag en de ViewModel meldingen maken zonder Context,
 * en wisselt alles mee als je in de app van taal verandert.
 */
sealed interface UiText {
    /** Letterlijke tekst, bijv. een foutmelding van de server. */
    class Raw(val value: String) : UiText

    class Res(@StringRes val id: Int, vararg val args: Any) : UiText

    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText

    fun resolve(res: Resources): String = when (this) {
        is Raw -> value
        is Res -> if (args.isEmpty()) res.getString(id) else res.getString(id, *resolveArgs(res, args))
        is Plural -> res.getQuantityString(id, count, *resolveArgs(res, args))
    }

    private fun resolveArgs(res: Resources, args: Array<out Any>): Array<Any> =
        args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray()
}

@Composable
fun UiText.asString(): String {
    LocalConfiguration.current // opnieuw tekenen bij taalwissel
    return resolve(LocalContext.current.resources)
}

fun Throwable.toUiText(): UiText =
    (this as? UnraidException)?.text ?: UiText.Raw(message ?: javaClass.simpleName)
