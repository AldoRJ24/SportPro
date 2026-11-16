package com.sportpro.app.ui.components
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Muestra los dígitos que escribe la persona con los separadores de [mask] ('#' = dígito);
 * por ejemplo "##/##/####" convierte 07112026 en 07/11/2026 mientras se escribe.
 */
class DigitMaskTransformation(private val mask: String) : VisualTransformation {
    private val slots = mask.count { it == '#' }

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(slots)
        val out = StringBuilder()
        var d = 0
        for (ch in mask) {
            if (d >= digits.length) break
            if (ch == '#') {
                out.append(digits[d]); d++
            } else {
                out.append(ch)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var seen = 0
                for (i in mask.indices) {
                    if (mask[i] == '#') {
                        seen++
                        if (seen == offset) return (i + 1).coerceAtMost(out.length)
                    }
                }
                return out.length
            }

            override fun transformedToOriginal(offset: Int): Int =
                mask.take(offset.coerceAtMost(mask.length)).count { it == '#' }.coerceAtMost(digits.length)
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** Campo que solo acepta dígitos, hasta el largo de la máscara. [value] guarda solo los dígitos. */
@Composable
fun MaskedDigitsField(
    value: String,
    onValueChange: (String) -> Unit,
    mask: String,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val slots = mask.count { it == '#' }
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(slots)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
        visualTransformation = DigitMaskTransformation(mask),
        modifier = modifier,
    )
}

const val DATE_MASK = "##/##/####"
const val TIME_MASK = "##:##"

private val strictDate: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
private val strictTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmm").withResolverStyle(ResolverStyle.STRICT)

/** "07112026" → 2026-11-07, o null si está incompleta o no es una fecha real (por ejemplo 31/02). */
fun parseDateDigits(digits: String): LocalDate? =
    if (digits.length != 8) null else runCatching { LocalDate.parse(digits, strictDate) }.getOrNull()

fun parseTimeDigits(digits: String): LocalTime? =
    if (digits.length != 4) null else runCatching { LocalTime.parse(digits, strictTime) }.getOrNull()

fun LocalDate.toDateDigits(): String = format(DateTimeFormatter.ofPattern("ddMMyyyy"))
fun LocalTime.toTimeDigits(): String = format(DateTimeFormatter.ofPattern("HHmm"))

/** Texto de ayuda de un campo de fecha mientras se escribe. */
fun dateHint(digits: String): String? = when {
    digits.isEmpty() -> AppText.get(R.string.masked_input_dia_mes_y_ano_8)
    digits.length < 8 -> AppText.get(R.string.masked_input_faltan_x_digitos, 8 - digits.length)
    parseDateDigits(digits) == null -> AppText.get(R.string.masked_input_esa_fecha_no_existe)
    else -> null
}
