// Edge Function: genera el BORRADOR del resumen del partido con Gemini.
// Despliegue sin CLI: Dashboard → Edge Functions → Deploy a new function → Via Editor,
//   nombre «generate-match-summary», pegar este archivo y Deploy. Desactivar «Verify JWT»:
//   la función valida al usuario por su cuenta.
// Secretos (NUNCA en el APK ni en el repo), en Edge Functions → Secrets:
//   GEMINI_API_KEY = clave de Google AI Studio
//   GEMINI_MODEL   = gemini-3.6-flash   (opcional; es el valor por defecto)
// Variables que Supabase inyecta solas: SUPABASE_URL y las claves nuevas en JSON
//   (SUPABASE_SECRET_KEYS / SUPABASE_PUBLISHABLE_KEYS). Se aceptan también las heredadas
//   SUPABASE_SERVICE_ROLE_KEY / SUPABASE_ANON_KEY si el proyecto aún las tiene.
import { createClient } from "jsr:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
// Lee una clave nueva (diccionario JSON, entrada "default") o la heredada.
function projectKey(newVar: string, legacyVar: string): string {
  const raw = Deno.env.get(newVar);
  if (raw) {
    try {
      const dict = JSON.parse(raw) as Record<string, string>;
      const k = dict["default"] ?? Object.values(dict)[0];
      if (k) return k;
    } catch { /* no es JSON: se ignora */ }
  }
  const legacy = Deno.env.get(legacyVar);
  if (legacy) return legacy;
  throw new Error(`Falta la clave del proyecto (${newVar} / ${legacyVar})`);
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const SYSTEM_PROMPT = `Eres un redactor deportivo para una academia de fútbol formativo.
Recibirás ÚNICAMENTE una lista cronológica de eventos de un partido en formato JSON.
REGLAS ESTRICTAS:
- Usa solo la información del JSON. No inventes jugadas, jugadores, minutos, resultados, clima, público ni emociones.
- Los jugadores aparecen con seudónimos (J1, J2, R1...). Mantén esos seudónimos tal cual, sin cambiarlos ni deducir nombres.
- Si un evento no tiene jugador ("actor"), redáctalo sin mencionar a nadie.
- Si algún dato no está en el JSON, no lo menciones.
- Los eventos "own_goal": true son goles en contra.
- Escribe en español, tono positivo y respetuoso, 1 o 2 párrafos breves, empezando por el resultado final.
- No des consejos, no juzgues a los jugadores ni a los árbitros.`;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const admin = createClient(url, projectKey("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    // 1) Autenticación del usuario que llama
    const authHeader = req.headers.get("Authorization") ?? "";
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const { data: u, error: ue } = await admin.auth.getUser(token);
    if (ue || !u?.user) return json({ error: "No autenticado" }, 401);

    const { match_id } = await req.json();
    if (!match_id) return json({ error: "Falta match_id" }, 400);

    // 2) Autorización: solo el DT del equipo y solo si el partido está finalizado
    const userClient = createClient(url, projectKey("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY"), {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: ok, error: oe } = await userClient.rpc("can_generate_summary", { p_match: match_id });
    if (oe) return json({ error: `No se pudo verificar el permiso: ${oe.message}` }, 500);
    if (ok !== true) return json({ error: "Solo el DT puede generar el resumen de un partido finalizado" }, 403);

    // 3) Datos para la IA: SIN nombres (seudónimos J1.. / R1..)
    const { data: payload, error: pe } = await admin.rpc("build_ai_payload", { p_match: match_id });
    if (pe || !payload) return json({ error: "No se pudieron leer los eventos" }, 500);
    if (!payload.events?.length) return json({ error: "El partido no tiene eventos registrados" }, 422);

    // 4) Llamada a Gemini (REST)
    const model = Deno.env.get("GEMINI_MODEL") || "gemini-3.6-flash";
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) return json({ error: "Falta el secreto GEMINI_API_KEY en Edge Functions → Secrets" }, 500);
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [{ role: "user", parts: [{ text: JSON.stringify(payload) }] }],
        // Gemini 3.x: los parámetros de muestreo se ignoran y el razonamiento interno consume
        // del límite de salida; por eso el límite es amplio (el prompt pide 1–2 párrafos).
        generationConfig: { maxOutputTokens: 4096 },
      }),
    });
    if (r.status === 429) return json({ error: "La IA está saturada. Intenta de nuevo en un minuto." }, 429);
    if (!r.ok) {
      const detail = await r.text();
      return json({ error: `Error del proveedor de IA (${r.status}). Revisa GEMINI_API_KEY y GEMINI_MODEL.`, detail }, 502);
    }
    const g = await r.json();
    const parts: { text?: string; thought?: boolean }[] = g?.candidates?.[0]?.content?.parts ?? [];
    const text = parts.filter((p) => !p.thought).map((p) => p.text ?? "").join("").trim();
    if (!text) {
      const reason = g?.candidates?.[0]?.finishReason ?? "desconocido";
      return json({ error: `La IA no devolvió texto (motivo: ${reason}). Intenta de nuevo.` }, 502);
    }

    // 5) Verificación anti-alucinación (mínima): los seudónimos citados deben existir en los eventos
    const known = new Set<string>();
    for (const e of payload.events) {
      if (e.actor) known.add(e.actor);
      for (const k of ["assist", "player_in", "player_out"]) if (e.details?.[k]) known.add(e.details[k]);
    }
    const cited = text.match(/\b[JR]\d+\b/g) ?? [];
    const invented = cited.filter((a) => !known.has(a));
    if (invented.length) return json({ error: "La IA citó jugadores inexistentes; reintenta", invented }, 422);
    // TODO (Claude Code): validar también minutos y marcador citados contra payload.events / payload.match.

    // 6) Restituir nombres (menores = "Nombre + inicial") y guardar el borrador (máx. 3 regeneraciones)
    const { data: rendered, error: re } = await admin.rpc("render_ai_text", { p_match: match_id, p_text: text });
    if (re) return json({ error: "No se pudo preparar el texto" }, 500);
    const { data: summaryId, error: se } = await admin.rpc("upsert_ai_summary", {
      p_match: match_id, p_text: rendered, p_model: model,
    });
    if (se) return json({ error: se.message }, 409);
    return json({ summary_id: summaryId, text: rendered });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
