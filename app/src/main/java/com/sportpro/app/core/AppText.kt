package com.sportpro.app.core

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Acceso a los textos de strings.xml desde código que NO es Compose (ViewModels, utilidades
 * de formato, mensajes de error). En las pantallas Compose se usa stringResource(); aquí no se
 * puede porque estas funciones no son @Composable ni reciben un Context.
 *
 * SportProApp llama a [install] al arrancar. Se guarda solo `Resources` del contexto de la
 * aplicación (no una Activity), así no hay fugas de memoria.
 *
 * En los tests unitarios no existe Android: ahí se asigna [fake] para decidir qué devuelve
 * cada id y poder comprobar el resultado sin emulador.
 */
object AppText {
    @Volatile private var resources: Resources? = null

    /** Solo para tests: reemplaza la lectura de recursos. Recibe el id, la cantidad (plurales) y los argumentos. */
    @Volatile var fake: ((id: Int, quantity: Int?, args: List<Any?>) -> String)? = null

    fun install(context: Context) {
        resources = context.applicationContext.resources
    }

    /** Texto de strings.xml; con argumentos rellena %1$s, %2$s… */
    fun get(@StringRes id: Int, vararg args: Any?): String {
        fake?.let { return it(id, null, args.toList()) }
        val res = resources ?: return ""
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    /** Texto con plural («1 partido» / «3 partidos»). La cantidad es también el primer argumento. */
    fun plural(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String {
        fake?.let { return it(id, quantity, args.toList()) }
        val res = resources ?: return ""
        return if (args.isEmpty()) res.getQuantityString(id, quantity, quantity) else res.getQuantityString(id, quantity, *args)
    }
}
