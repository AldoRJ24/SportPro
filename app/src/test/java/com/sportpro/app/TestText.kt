package com.sportpro.app

import com.sportpro.app.core.AppText

/**
 * Textos falsos para los tests. En la PC no existe Android ni strings.xml, así que en vez del
 * texto real se devuelve una «huella» con el id del recurso y sus argumentos, por ejemplo
 * `res2131689|q=3|5,7`. Los tests comparan contra [key] con el mismo id: así comprueban QUÉ
 * texto se eligió y con qué datos, sin depender de la redacción exacta en español.
 */
object TestText {
    fun install() {
        AppText.fake = { id, quantity, args -> key(id, quantity, *args.toTypedArray()) }
    }

    fun key(id: Int, quantity: Int? = null, vararg args: Any?): String =
        "res$id|q=${quantity ?: "-"}|${args.joinToString(",")}"
}
