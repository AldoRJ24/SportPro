# Diseño Técnico - SportPro (Semana 7)
## Fecha: 03 de Octubre de 2026

Este documento detalla la arquitectura para la sincronización de eventos en tiempo real y la integración con la Inteligencia Artificial (LLM), cumpliendo estrictamente con los requerimientos del 40% del proyecto.

---

## 1. Actualización en Tiempo Real (Supabase Realtime)

Para que el marcador y los eventos del partido se actualicen de manera síncrona sin necesidad de recargar la vista, utilizaremos **Supabase Realtime** mediante WebSockets (módulo `realtime-kt`).

### Flujo de Trabajo:
1. **Suscripción a la tabla (`Postgres Changes`)**:
   El `ViewModel` de la pantalla del partido inicializará un `Flow` (o canal) escuchando exclusivamente los eventos de tipo `INSERT` en la tabla `eventos_partido`.
2. **Filtrado por Partido**:
   La suscripción se filtrará a nivel de base de datos usando el `partido_id`, garantizando que la app solo reciba los eventos del partido actual.
3. **Reacción de la UI (StateFlow)**:
   Al recibir un evento (ej. GOL, TARJETA_AMARILLA), el `ViewModel` actualizará el `StateFlow` que contiene la lista de eventos o el marcador. Jetpack Compose, al estar observando este estado (`collectAsState`), recompondrá la vista instantáneamente.

**Ejemplo conceptual de la tabla `eventos_partido`**:
* `id` (UUID)
* `partido_id` (UUID)
* `minuto` (Int)
* `jugador_id` (UUID)
* `tipo_evento` (String: "GOL", "AMARILLA", "ROJA", "CAMBIO")

---

## 2. Integración con LLM (Prevención de Alucinaciones)

Para generar resúmenes o crónicas deportivas del partido usando IA (Gemini / OpenAI), es crucial evitar que el modelo invente datos (alucinaciones). 

### Estrategia de Inyección de Contexto Estricta:

1. **Extracción de Datos Crudos (JSON)**:
   Al finalizar el partido (o al solicitar la crónica), el cliente Android obtendrá todos los registros de la tabla `eventos_partido` y los serializará en un arreglo JSON estructurado.

2. **Diseño del System Prompt**:
   Se configurará el cliente del LLM con un *System Instruction* extremadamente restrictivo, inyectando el JSON como única fuente de verdad.
   
   **Prompt Base**:
   > "Eres un analista deportivo profesional de la app SportPro. Tu objetivo es generar una crónica narrativa del partido. 
   > **REGLA ESTRICTA**: SOLO puedes basarte en el siguiente arreglo de eventos en formato JSON. NO inventes nombres, NO inventes goles, y NO asumas resultados que no se deriven de estos datos exactos. Si el JSON está vacío, indica que el partido transcurrió sin eventos destacables."

3. **Ejemplo del Payload (Data Transfer)**:
   ```json
   [
     {"minuto": 15, "jugador": "Carlos Pérez", "tipo": "GOL"},
     {"minuto": 42, "jugador": "Luis Gómez", "tipo": "TARJETA_AMARILLA"}
   ]
   ```

4. **Procesamiento de Respuesta**:
   La respuesta del LLM se devolverá al ViewModel y se mostrará en un modal o pantalla de resumen dentro de la app mediante Compose.
