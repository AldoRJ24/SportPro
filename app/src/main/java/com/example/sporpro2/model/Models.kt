package com.example.sporpro2.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Usuario(
    @SerialName("id") val id: String,
    @SerialName("nombre_completo") val nombreCompleto: String? = null,
    @SerialName("correo") val correo: String? = null,
    @SerialName("rol") val rol: String? = null
)

@Serializable
data class Publicacion(
    @SerialName("id") val id: String,
    @SerialName("autor_id") val autorId: String? = null,
    @SerialName("contenido") val contenido: String? = null,
    @SerialName("fecha_publicacion") val fechaPublicacion: String? = null,
    @SerialName("tipo") val tipo: String? = null
)

@Serializable
data class PublicacionInsert(
    @SerialName("contenido") val contenido: String,
    @SerialName("tipo") val tipo: String? = null
)

@Serializable
data class Partido(
    @SerialName("id") val id: String,
    @SerialName("rival") val rival: String? = null,
    @SerialName("fecha_hora") val fechaHora: String? = null,
    @SerialName("marcador_favor") val marcadorFavor: Int? = null,
    @SerialName("marcador_contra") val marcadorContra: Int? = null,
    @SerialName("estado") val estado: String? = null
)

@Serializable
data class Jugador(
    @SerialName("id") val id: String? = null,
    @SerialName("usuario_id") val usuarioId: String? = null,
    @SerialName("nombre") val nombre: String? = null,
    @SerialName("posicion_principal") val posicionPrincipal: String? = null,
    @SerialName("peso_kg") val pesoKg: Double? = null,
    @SerialName("estatura_cm") val estaturaCm: Double? = null,
    @SerialName("pie_dominante") val pieDominante: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("codigo_apoderado") val codigoApoderado: String? = null,
    @SerialName("estado") val estado: String? = null
)

@Serializable
data class VinculoFamiliar(
    @SerialName("id") val id: String? = null,
    @SerialName("jugador_id") val jugadorId: String,
    @SerialName("padre_id") val padreId: String,
    @SerialName("estado") val estado: String
)

@Serializable
data class Padre(
    @SerialName("id") val id: String? = null,
    @SerialName("usuario_id") val usuarioId: String,
    @SerialName("codigo_apoderado") val codigoApoderado: String? = null
)

@Serializable
data class Entrenamiento(
    @SerialName("id") val id: String,
    @SerialName("fecha_hora") val fechaHora: String? = null,
    @SerialName("objetivo") val objetivo: String? = null,
    @SerialName("duracion_minutos") val duracionMinutos: Int? = null
)

@Serializable
data class Pago(
    @SerialName("id") val id: String,
    @SerialName("mes_correspondiente") val mesCorrespondiente: String? = null,
    @SerialName("monto") val monto: Double? = null,
    @SerialName("estado") val estado: String? = null
)

@Serializable
data class Documento(
    @SerialName("id") val id: String? = null,
    @SerialName("tipo_documento") val tipoDocumento: String? = null,
    @SerialName("fecha_vencimiento") val fechaVencimiento: String? = null,
    @SerialName("estado") val estado: String? = null
)

// UI Model para Calendario
data class EventoCalendario(
    val id: String,
    val titulo: String,
    val fechaHora: String,
    val tipo: String
)

@Serializable
data class EventoPartido(
    @SerialName("id") val id: String? = null,
    @SerialName("partido_id") val partidoId: String? = null,
    @SerialName("minuto") val minuto: Int? = null,
    @SerialName("tipo_evento") val tipoEvento: String? = null,
    @SerialName("jugador_id") val jugadorId: String? = null,
    @SerialName("equipo_involucrado") val equipoInvolucrado: String? = null,
    @SerialName("observaciones") val observaciones: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
