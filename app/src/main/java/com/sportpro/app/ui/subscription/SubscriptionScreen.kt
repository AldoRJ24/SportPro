package com.sportpro.app.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sportpro.app.R
import com.sportpro.app.core.AppContainer
import com.sportpro.app.core.data.Billing
import com.sportpro.app.core.data.Plan
import com.sportpro.app.core.data.Plans
import com.sportpro.app.core.data.formatDay
import com.sportpro.app.core.data.friendlyMessage
import com.sportpro.app.ui.components.ErrorBox
import com.sportpro.app.ui.components.GoldDeep
import com.sportpro.app.ui.components.GoldLight
import com.sportpro.app.ui.components.GoldMid
import com.sportpro.app.ui.components.GoldNames
import com.sportpro.app.ui.components.GoldText
import com.sportpro.app.ui.components.LoadingBox
import com.sportpro.app.ui.components.SegmentedControl
import com.sportpro.app.ui.components.SpScaffold
import com.sportpro.app.ui.navigation.LocalAppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionUi(
    val loading: Boolean = true,
    val error: String? = null,
    /** Plan vigente de la academia (null = todavía no eligió). */
    val current: Plan? = null,
    val currentBilling: Billing? = null,
    val renewsAt: String? = null,
    val billing: Billing = Billing.ANNUAL,
    /** Plan elegido para pagar (abre la hoja de pago). */
    val checkout: Plan? = null,
    val paying: Boolean = false,
    /** Plan recién contratado (muestra el «¡Listo!»). */
    val paidPlan: Plan? = null,
    val message: String? = null,
)

/** Planes de una academia. El servidor decide quién puede contratar y registra el pago simulado. */
class SubscriptionViewModel(private val container: AppContainer, private val academyId: String) : ViewModel() {
    private val _ui = MutableStateFlow(SubscriptionUi())
    val ui: StateFlow<SubscriptionUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val mine = container.subscriptions.mine().firstOrNull { it.academyId == academyId }
                _ui.update {
                    it.copy(
                        loading = false,
                        current = mine?.planOrNull,
                        currentBilling = Billing.entries.firstOrNull { b -> b.code == mine?.billing },
                        renewsAt = mine?.renewsAt,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun setBilling(b: Billing) = _ui.update { it.copy(billing = b) }
    fun openCheckout(p: Plan) = _ui.update { it.copy(checkout = p, paidPlan = null, message = null) }
    fun closeCheckout() = _ui.update { it.copy(checkout = null, paidPlan = null) }

    /** Contrata el plan (el Básico es gratis: no pasa por la barra de pago). */
    fun subscribe(plan: Plan) {
        if (_ui.value.paying) return
        viewModelScope.launch {
            _ui.update { it.copy(paying = true, message = null) }
            try {
                val billing = if (plan == Plan.BASIC) Billing.MONTHLY else _ui.value.billing
                container.subscriptions.subscribe(academyId, plan, billing)
                // El dorado de los nombres cambia al instante en toda la app
                runCatching { container.subscriptions.goldNames() }.onSuccess { GoldNames.set(it.map { g -> g.id }.toSet()) }
                _ui.update { it.copy(paying = false, paidPlan = plan, current = plan, currentBilling = billing) }
                load()
            } catch (e: Exception) {
                _ui.update { it.copy(paying = false, message = e.friendlyMessage()) }
            }
        }
    }
}

/**
 * Planes de la academia: tarjetas comparativas (Básico, Black y Gold) con el selector
 * Mensual / Anual («ahorra 20 %»). [required] = la academia aún no tiene plan: no hay flecha
 * de volver hasta elegir uno (el Básico es gratis y se elige con un toque).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(academyId: String, academyName: String, required: Boolean, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SubscriptionViewModel = viewModel(key = "subs-$academyId") { SubscriptionViewModel(container, academyId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mustChoose = required && ui.current == null

    SpScaffold(
        title = stringResource(R.string.subs_title),
        onBack = if (mustChoose) null else onBack,
    ) { pad ->
        when {
            ui.loading && ui.current == null -> LoadingBox(Modifier.padding(pad))
            ui.error != null -> ErrorBox(ui.error!!, onRetry = vm::load, modifier = Modifier.padding(pad))
            else -> LazyColumn(
                contentPadding = PaddingValues(top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(academyName, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(if (mustChoose) R.string.subs_choose_first else R.string.subs_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    // Mensual / Anual: el anual muestra el ahorro
                    SegmentedControl(
                        options = listOf(stringResource(R.string.subs_monthly), stringResource(R.string.subs_annual_save)),
                        selected = if (ui.billing == Billing.MONTHLY) 0 else 1,
                        onSelect = { vm.setBilling(if (it == 0) Billing.MONTHLY else Billing.ANNUAL) },
                    )
                }
                Plan.entries.forEach { plan ->
                    item(key = plan.code) {
                        PlanCard(
                            plan = plan,
                            billing = ui.billing,
                            isCurrent = ui.current == plan,
                            currentBilling = ui.currentBilling,
                            renewsAt = ui.renewsAt,
                            onChoose = { if (plan == Plan.BASIC) vm.subscribe(Plan.BASIC) else vm.openCheckout(plan) },
                            busy = ui.paying,
                        )
                    }
                }
                ui.message?.takeIf { ui.checkout == null }?.let { msg ->
                    item { Text(msg, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Text(
                        stringResource(R.string.subs_simulated_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    ui.checkout?.let { plan ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!ui.paying) vm.closeCheckout() }, sheetState = sheet) {
            Checkout(
                plan = plan,
                billing = ui.billing,
                academyName = academyName,
                paying = ui.paying,
                paid = ui.paidPlan == plan,
                error = ui.message,
                onPaid = { vm.subscribe(plan) },
                onClose = {
                    vm.closeCheckout()
                    if (required) onBack()
                },
            )
        }
    }
}

// ---------------------------------------------------------------------
// Tarjeta de un plan
// ---------------------------------------------------------------------

@Composable
private fun PlanCard(
    plan: Plan,
    billing: Billing,
    isCurrent: Boolean,
    currentBilling: Billing?,
    renewsAt: String?,
    onChoose: () -> Unit,
    busy: Boolean,
) {
    // Cada plan tiene su «material»: Básico claro, Black negro mate, Gold negro con borde dorado
    val dark = plan != Plan.BASIC
    val container = when (plan) {
        Plan.BASIC -> MaterialTheme.colorScheme.surfaceVariant
        Plan.BLACK -> Color(0xFF0B0B0D)
        Plan.GOLD -> Color(0xFF14110A)
    }
    val onCard = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    val soft = if (dark) Color.White.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant
    val border: BorderStroke? = when {
        plan == Plan.GOLD -> BorderStroke(1.5.dp, Brush.linearGradient(listOf(GoldDeep, GoldLight, GoldMid, GoldDeep)))
        isCurrent -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Etiqueta superior
        val tag = when {
            isCurrent -> stringResource(R.string.subs_current)
            plan == Plan.BLACK -> stringResource(R.string.subs_popular)
            plan == Plan.GOLD -> stringResource(R.string.subs_premium)
            else -> null
        }
        if (tag != null) {
            Text(
                tag.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (plan == Plan.GOLD) GoldLight else if (dark) Color(0xFF8FB2FF) else MaterialTheme.colorScheme.primary,
            )
        }
        // Nombre del plan (el Gold, en oro animado)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.subs_plan_word) + " ", style = MaterialTheme.typography.headlineSmall, color = onCard)
            when (plan) {
                Plan.GOLD -> GoldText(stringResource(R.string.subs_gold), style = MaterialTheme.typography.headlineSmall)
                Plan.BLACK -> Text(stringResource(R.string.subs_black), style = MaterialTheme.typography.headlineSmall, color = onCard, fontWeight = FontWeight.Black)
                Plan.BASIC -> Text(stringResource(R.string.subs_basic), style = MaterialTheme.typography.headlineSmall, color = onCard)
            }
        }
        // Precio
        val perMonth = Plans.perMonth(plan, billing)
        val list = Plans.monthlyPrice(plan)
        if (billing == Billing.ANNUAL && list > 0) {
            Text(stringResource(R.string.subs_usd, list), style = MaterialTheme.typography.bodyMedium, color = soft, textDecoration = TextDecoration.LineThrough)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.subs_usd, perMonth),
                style = MaterialTheme.typography.displaySmall,
                color = if (billing == Billing.ANNUAL && list > 0) (if (plan == Plan.GOLD) GoldLight else Color(0xFF4ADE80)) else onCard,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.subs_per_month), style = MaterialTheme.typography.bodyLarge, color = soft, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            when {
                list == 0 -> stringResource(R.string.subs_free_forever)
                billing == Billing.ANNUAL -> stringResource(R.string.subs_billed_annual, Plans.charge(plan, billing))
                else -> stringResource(R.string.subs_billed_monthly)
            },
            style = MaterialTheme.typography.bodySmall,
            color = soft,
        )
        if (isCurrent && renewsAt != null && plan != Plan.BASIC) {
            Text(
                stringResource(
                    R.string.subs_renews,
                    stringResource(if (currentBilling == Billing.ANNUAL) R.string.subs_annual_word else R.string.subs_monthly_word),
                    formatDay(renewsAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = soft,
            )
        }
        // Botón
        val label = when {
            isCurrent -> stringResource(R.string.subs_current_button)
            else -> stringResource(R.string.subs_choose, planName(plan))
        }
        val colors = when (plan) {
            Plan.GOLD -> ButtonDefaults.buttonColors(containerColor = GoldMid, contentColor = Color(0xFF2A1E03))
            Plan.BLACK -> ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            Plan.BASIC -> ButtonDefaults.buttonColors()
        }
        Button(
            onClick = onChoose,
            enabled = !isCurrent && !busy,
            colors = colors,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(label, fontWeight = FontWeight.SemiBold) }

        // Qué incluye
        Spacer(Modifier.height(2.dp))
        features(plan).forEach { (title, detail) ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (plan == Plan.GOLD) GoldLight else if (dark) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = onCard)
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = soft)
                }
            }
        }
    }
}

@Composable
private fun planName(plan: Plan): String = stringResource(
    when (plan) {
        Plan.BASIC -> R.string.subs_basic
        Plan.BLACK -> R.string.subs_black
        Plan.GOLD -> R.string.subs_gold
    },
)

/** Lista de lo que incluye cada plan (título y detalle). */
@Composable
private fun features(plan: Plan): List<Pair<String, String?>> = when (plan) {
    Plan.BASIC -> listOf(
        stringResource(R.string.subs_f_all) to stringResource(R.string.subs_f_all_d),
        stringResource(R.string.subs_f_no_ai) to null,
    )
    Plan.BLACK -> listOf(
        stringResource(R.string.subs_f_all) to stringResource(R.string.subs_f_all_d),
        stringResource(R.string.subs_f_ai) to stringResource(R.string.subs_f_ai_black, Plans.aiUses(Plan.BLACK)),
        stringResource(R.string.subs_f_future_ai) to null,
    )
    Plan.GOLD -> listOf(
        stringResource(R.string.subs_f_everything_black) to null,
        stringResource(R.string.subs_f_ai_gold) to stringResource(R.string.subs_f_ai_gold_d, Plans.aiUses(Plan.GOLD)),
        stringResource(R.string.subs_f_gold_name) to stringResource(R.string.subs_f_gold_name_d),
        stringResource(R.string.subs_f_exclusive) to stringResource(R.string.subs_f_exclusive_d),
    )
}

// ---------------------------------------------------------------------
// Hoja de pago (simulado)
// ---------------------------------------------------------------------

@Composable
private fun Checkout(
    plan: Plan,
    billing: Billing,
    academyName: String,
    paying: Boolean,
    paid: Boolean,
    error: String?,
    onPaid: () -> Unit,
    onClose: () -> Unit,
) {
    val total = Plans.charge(plan, billing)
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (paid) {
            // ¡Listo!
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(if (plan == Plan.GOLD) GoldMid else Color.Black),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp)) }
                Text(stringResource(R.string.subs_paid_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.subs_paid_body, academyName, planName(plan)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.subs_done)) }
            }
            return@Column
        }
        Text(stringResource(R.string.subs_checkout_title), style = MaterialTheme.typography.titleLarge)
        SummaryLine(stringResource(R.string.subs_line_academy), academyName)
        SummaryLine(stringResource(R.string.subs_line_plan), planName(plan))
        SummaryLine(
            stringResource(R.string.subs_line_billing),
            stringResource(if (billing == Billing.ANNUAL) R.string.subs_annual_word else R.string.subs_monthly_word),
        )
        SummaryLine(stringResource(R.string.subs_line_total), stringResource(R.string.subs_usd, total), strong = true)
        Text(
            stringResource(R.string.subs_simulated_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HoldToPayBar(
            plan = plan,
            idleLabel = stringResource(R.string.subs_hold_idle, total),
            holdingLabel = { s -> context.getString(R.string.subs_hold_progress, s) },
            doneLabel = stringResource(R.string.subs_hold_done),
            enabled = !paying,
            onPaid = onPaid,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onClose, enabled = !paying, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.common_cancel))
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium)
    }
}
