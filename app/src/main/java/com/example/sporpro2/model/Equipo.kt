package com.example.sporpro2.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Equipo(
    @SerialName("id")
    val id: String,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("categoria")
    val categoria: String? = null
)
