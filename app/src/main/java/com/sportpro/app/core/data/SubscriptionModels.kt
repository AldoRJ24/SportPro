package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// ---------------- Suscripciones de academias (migración 023, pago SIMULADO) ----------------

/** Plan de una academia. [code] es el valor que guarda el servidor. */
enum class Plan(val code: String) {
    BASIC("basic"), BLACK("black"), GOLD("gold");

    companion object {
        fun of(code: String?): Plan? = entries.firstOrNull { it.code == code }
    }
}

/** Forma de pago: mensual o anual (20 % menos por mes). */
enum class Billing(val code: String) { MONTHLY("monthly"), ANNUAL("annual") }

/** Una academia que administro y su plan vigente (plan null = todavía no eligió). */
@Serializable
data class AcademySubscription(
    @SerialName("academy_id") val academyId: String,
    @SerialName("academy_name") val academyName: String,
    val plan: String? = null,
    val billing: String? = null,
    @SerialName("price_usd") val priceUsd: Double? = null,
    @SerialName("renews_at") val renewsAt: String? = null,
) {
    val planOrNull: Plan? get() = Plan.of(plan)
}

/** Respuesta de subscribe_academy. */
@Serializable
data class SubscribeResult(
    val plan: String,
    val billing: String,
    @SerialName("charged_usd") val chargedUsd: Double = 0.0,
    @SerialName("renews_at") val renewsAt: String? = null,
)

/** Mi cupo de IA para un partido (lo calcula el servidor con el plan de la academia). */
@Serializable
data class AiQuota(
    val plan: String = "basic",
    val limit: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0,
    @SerialName("next_free_at") val nextFreeAt: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

/** Fila de gold_names: una academia o un equipo con nombre dorado. */
@Serializable
data class GoldName(val kind: String, val id: String)

/**
 * Precios y límites de los planes. Son los MISMOS números que usa el servidor
 * (_plan_monthly_price, _plan_charge, _plan_ai_limit); aquí solo sirven para mostrarlos.
 * Lógica pura, sin Android: se prueba con JUnit.
 */
object Plans {
    /** Precio mensual de lista en dólares. */
    fun monthlyPrice(plan: Plan): Int = when (plan) {
        Plan.BASIC -> 0
        Plan.BLACK -> 50
        Plan.GOLD -> 100
    }

    /** Descuento del pago anual. */
    const val ANNUAL_DISCOUNT = 0.20

    /** Precio por mes según la forma de pago (anual = 20 % menos). */
    fun perMonth(plan: Plan, billing: Billing): Int =
        if (billing == Billing.ANNUAL) (monthlyPrice(plan) * (1 - ANNUAL_DISCOUNT)).roundToInt() else monthlyPrice(plan)

    /** Lo que se cobra al contratar: un mes, o 12 meses con descuento. */
    fun charge(plan: Plan, billing: Billing): Int =
        if (billing == Billing.ANNUAL) perMonth(plan, billing) * 12 else monthlyPrice(plan)

    /** Usos de IA cada 3 días por entrenador (Gold = 5 veces Black). */
    fun aiUses(plan: Plan): Int = when (plan) {
        Plan.BASIC -> 0
        Plan.BLACK -> 3
        Plan.GOLD -> 15
    }
}
