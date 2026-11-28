package com.sportpro.app

import com.sportpro.app.core.data.Billing
import com.sportpro.app.core.data.Plan
import com.sportpro.app.core.data.Plans
import com.sportpro.app.ui.creatematch.CreateMatchForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Precios de los planes (migración 023) y propuestas del administrador (migración 021). */
class PlansAndProposalsTest {

    @Before fun texts() = TestText.install()

    @Test fun preciosMensuales() {
        assertEquals(0, Plans.perMonth(Plan.BASIC, Billing.MONTHLY))
        assertEquals(50, Plans.perMonth(Plan.BLACK, Billing.MONTHLY))
        assertEquals(100, Plans.perMonth(Plan.GOLD, Billing.MONTHLY))
    }

    @Test fun anualTiene20PorCientoMenosPorMes() {
        assertEquals(40, Plans.perMonth(Plan.BLACK, Billing.ANNUAL))
        assertEquals(80, Plans.perMonth(Plan.GOLD, Billing.ANNUAL))
        // Se cobran los 12 meses juntos (igual que _plan_charge en el servidor)
        assertEquals(480, Plans.charge(Plan.BLACK, Billing.ANNUAL))
        assertEquals(960, Plans.charge(Plan.GOLD, Billing.ANNUAL))
        assertEquals(0, Plans.charge(Plan.BASIC, Billing.ANNUAL))
    }

    @Test fun goldTieneCincoVecesLaIaDeBlack() {
        assertEquals(0, Plans.aiUses(Plan.BASIC))
        assertEquals(Plans.aiUses(Plan.BLACK) * 5, Plans.aiUses(Plan.GOLD))
    }

    @Test fun dtConDosEquiposPropiosCreaDirecto() {
        val f = CreateMatchForm(homeTeamId = "a", externalRival = false, awayTeamId = "b")
        assertFalse(f.needsApproval(setOf("a", "b")))
    }

    @Test fun dtContraOtroEntrenadorEsDesafio() {
        val f = CreateMatchForm(homeTeamId = "a", externalRival = false, awayTeamId = "x")
        assertTrue(f.needsApproval(setOf("a")))
        assertFalse(f.isAdminProposal(setOf("a")))
    }

    @Test fun adminQueNoDirigeElEquipoEnviaPropuesta() {
        // El admin gestiona «a» pero no es su DT: deben aceptar los dos entrenadores
        val f = CreateMatchForm(homeTeamId = "a", externalRival = false, awayTeamId = "x")
        assertTrue(f.needsApproval(emptySet()))
        assertTrue(f.isAdminProposal(emptySet()))
    }
}
