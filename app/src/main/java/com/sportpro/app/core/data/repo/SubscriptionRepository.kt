package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.AcademySubscription
import com.sportpro.app.core.data.AiQuota
import com.sportpro.app.core.data.Billing
import com.sportpro.app.core.data.GoldName
import com.sportpro.app.core.data.Plan
import com.sportpro.app.core.data.SubscribeResult
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Suscripciones (migración 023). El pago es una SIMULACIÓN: el servidor registra el cambio de plan
 * y un movimiento «simulado»; no hay tarjetas ni cobros reales. Quién puede contratar (solo el
 * administrador de la academia) y los límites de IA los decide el servidor.
 */
class SubscriptionRepository(private val client: SupabaseClient) {

    /** Academias que administro con su plan vigente (null = falta elegir). */
    suspend fun mine(): List<AcademySubscription> =
        client.postgrest.rpc("my_academy_subscriptions").decodeRows()

    /** Contrata o cambia el plan. El Básico es gratis y siempre mensual. */
    suspend fun subscribe(academyId: String, plan: Plan, billing: Billing): SubscribeResult =
        client.postgrest.rpc("subscribe_academy", buildJsonObject {
            put("p_academy", academyId)
            put("p_plan", plan.code)
            put("p_billing", billing.code)
        }).decodeScalar()

    /** Academias y equipos con nombre dorado (plan Gold). */
    suspend fun goldNames(): List<GoldName> =
        client.postgrest.rpc("gold_names").decodeRows()

    /** Mi cupo de IA para un partido. */
    suspend fun aiQuota(matchId: String): AiQuota =
        client.postgrest.rpc("ai_quota", buildJsonObject { put("p_match", matchId) }).decodeScalar()
}
