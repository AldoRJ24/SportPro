// Edge Function: genera el BORRADOR del resumen del partido con Gemini.
// ⚠️ SIN PROBAR (la base de datos sí está probada; esta función no se pudo ejecutar fuera de Supabase).
// Claude Code debe desplegarla, ejecutarla con un partido finalizado y corregir lo que haga falta.
//
// Despliegue (con el CLI de Supabase):  supabase functions deploy generate-match-summary --no-verify-jwt
//   (se valida al usuario dentro de la función; así funciona con claves publishable)
// Secretos (NUNCA en el APK ni en el repo):
//   supabase secrets set GEMINI_API_KEY=xxxx GEMINI_MODEL=<modelo Flash vigente en AI Studio>
// Variables que Supabase inyecta solas: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY
//   (verifica en el dashboard → Edge Functions → Secrets los nombres disponibles en tu proyecto).
import { createClient } from "jsr:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
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
    const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

    // 1) Autenticación del usuario que llama
    const authHeader = req.headers.get("Authorization") ?? "";
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const { data: u, error: ue } = await admin.auth.getUser(token);
    if (ue || !u?.user) return json({ error: "No autenticado" }, 401);

    const { match_id } = await req.json();
    if (!match_id) return json({ error: "Falta match_id" }, 400);

    // 2) Autorización: solo el DT del equipo y solo si el partido está finalizado
    const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: ok } = await userClient.rpc("can_generate_summary", { p_match: match_id });
    if (ok !== true) return json({ error: "Solo el DT puede generar el resumen de un partido finalizado" }, 403);

    // 3) Datos para la IA: SIN nombres (seudónimos J1.. / R1..)
    const { data: payload, error: pe } = await admin.rpc("build_ai_payload", { p_match: match_id });
    if (pe || !payload) return json({ error: "No se pudieron leer los eventos" }, 500);
    if (!payload.events?.length) return json({ error: "El partido no tiene eventos registrados" }, 422);

    // 4) Llamada a Gemini (REST)
    const model = Deno.env.get("GEMINI_MODEL")!;
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": Deno.env.get("GEMINI_API_KEY")! },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [{ role: "user", parts: [{ text: JSON.stringify(payload) }] }],
        generationConfig: { temperature: 0.3, maxOutputTokens: 600 },
      }),
    });
    if (r.status === 429) return json({ error: "La IA está saturada. Intenta de nuevo en un minuto." }, 429);
    if (!r.ok) return json({ error: "Error del proveedor de IA", detail: await r.text() }, 502);
    const g = await r.json();
    const text: string = g?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? "").join("").trim() ?? "";
    if (!text) return json({ error: "La IA no devolvió texto" }, 502);

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
