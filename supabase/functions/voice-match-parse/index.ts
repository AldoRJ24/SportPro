// Edge Function: entiende lo que el DT o el administrador DIJO en el modo manos libres.
// Recibe el texto ya transcrito por el celular y devuelve los datos del partido en JSON
// (fecha, hora, equipos, entrenadores, formato…). NO crea nada: la app conversa, pregunta lo que
// falte y crea el partido solo después de que la persona confirme.
//
// Despliegue: Dashboard → Edge Functions → nombre «voice-match-parse» → pegar → Deploy.
// «Verify JWT» desactivado: la función valida al usuario por su cuenta (igual que el resumen IA).
// Secretos: GEMINI_API_KEY (y opcional GEMINI_MODEL), los mismos del resumen con IA.
import { createClient } from "jsr:@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
function projectKey(newVar: string, legacyVar: string): string {
  const raw = Deno.env.get(newVar);
  if (raw) {
    try {
      const dict = JSON.parse(raw) as Record<string, string>;
      const k = dict["default"] ?? Object.values(dict)[0];
      if (k) return k;
    } catch { /* no es JSON */ }
  }
  const legacy = Deno.env.get(legacyVar);
  if (legacy) return legacy;
  throw new Error(`Falta la clave del proyecto (${newVar} / ${legacyVar})`);
}
// Los errores «de negocio» (sin plan, no entendí, IA saturada) se devuelven con 200 y un campo
// «error»: así la app siempre puede leerlos y decirlos en voz alta.
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

// Lo que la IA debe devolver. Todo es opcional: solo lo que la persona dijo de verdad.
const SCHEMA = {
  type: "OBJECT",
  properties: {
    intent: {
      type: "STRING",
      enum: ["create_match", "answer", "confirm_yes", "confirm_no", "cancel", "other"],
      description: "create_match: pide crear/programar un partido. answer: responde la pregunta pendiente o corrige un dato. confirm_yes / confirm_no: acepta o rechaza el resumen. cancel: quiere dejar de crear el partido.",
    },
    date: { type: "STRING", nullable: true, description: "Fecha del partido en formato AAAA-MM-DD" },
    time: { type: "STRING", nullable: true, description: "Hora en formato 24 h HH:MM" },
    home_team: { type: "STRING", nullable: true, description: "Nombre del equipo propio (local), sin la categoría" },
    home_coach: { type: "STRING", nullable: true, description: "Entrenador del equipo propio, tal como lo dijo" },
    home_category: { type: "INTEGER", nullable: true, description: "Categoría/división del equipo propio (Sub-15 o división 15 = 15)" },
    away_team: { type: "STRING", nullable: true, description: "Nombre del equipo rival, sin la categoría" },
    away_coach: { type: "STRING", nullable: true, description: "Entrenador del rival, tal como lo dijo" },
    away_academy: { type: "STRING", nullable: true, description: "Academia o club del rival" },
    away_category: { type: "INTEGER", nullable: true, description: "Categoría/división del rival" },
    format_players: { type: "INTEGER", nullable: true, description: "Jugadores por lado (5 a 11). «Fútbol 7» o «7 contra 7» = 7" },
    half_minutes: { type: "INTEGER", nullable: true, description: "Minutos de CADA tiempo. Si dice la duración total, divide entre 2" },
    venue: { type: "STRING", nullable: true, description: "Lugar o cancha. Si dice que no hay sede o que da igual, escribe «-»" },
    option_index: { type: "INTEGER", nullable: true, description: "Si eligió una de las opciones ofrecidas, su número empezando en 1" },
  },
  required: ["intent"],
};

function systemPrompt(today: string, weekday: string): string {
  return `Eres el asistente de voz de SportPro, una app de academias de fútbol formativo en Perú.
Un entrenador (DT) o administrador te habla para CREAR UN PARTIDO. Tu única tarea es EXTRAER datos
de lo que dijo y devolverlos en el JSON pedido. No inventes nada: si un dato no se dijo, déjalo en null.
Hoy es ${weekday} ${today} (hora de Lima, Perú). Resuelve fechas relativas con esa referencia:
«mañana», «pasado mañana», «el sábado» (el próximo sábado), «el 15» (del mes actual, o del siguiente si ya pasó).
Horas: «11 de la mañana» = 11:00, «4 de la tarde» = 16:00, «8 de la noche» = 20:00, «mediodía» = 12:00.
Si solo dice un número de hora sin indicar mañana/tarde y es de 1 a 6, asume la tarde (fútbol formativo).
«División 15», «sub 15», «categoría 2011» → usa el número de la división (15). No confundas el año con la división.
Los diminutivos («Fernandito», «Rodolfito») se copian tal cual: la app los busca.
«Equipo propio» = el que el usuario dirige o administra (suele decir «mi equipo», «con el entrenador X y su grupo Y»).
El rival es el otro («contra», «vs», «frente a»). Si menciona la academia del rival, va en away_academy.
Si hay una PREGUNTA PENDIENTE, la respuesta corta del usuario responde esa pregunta (intent «answer»).
Si se ofrecieron OPCIONES numeradas y el usuario elige una («la primera», «la de Sub-15», «Lagartos»), pon option_index.
«Sí», «dale», «confírmalo», «envíalo» ante un resumen = confirm_yes. «No», «espera», «todavía no» = confirm_no.
«Cancela», «olvídalo», «ya no» = cancel.`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const admin = createClient(url, projectKey("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    // 1) ¿Quién habla?
    const authHeader = req.headers.get("Authorization") ?? "";
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const { data: u, error: ue } = await admin.auth.getUser(token);
    if (ue || !u?.user) return json({ error: "No autenticado" }, 401);

    const body = await req.json();
    const text: string = String(body?.text ?? "").slice(0, 600).trim();
    if (!text) return json({ error: "No se escuchó nada" });

    // 2) ¿Tiene el asistente en su plan? (Black / Gold y con cupo)
    const userClient = createClient(url, projectKey("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY"), {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: access, error: ae } = await userClient.rpc("voice_access");
    if (ae) return json({ error: `No se pudo verificar tu plan: ${ae.message}` }, 500);
    if (!access?.allowed) return json({ error: access?.reason ?? "El modo manos libres no está disponible" });

    // 3) Contexto de la conversación: fecha de hoy en Lima y la pregunta pendiente
    const now = new Date();
    const lima = new Date(now.toLocaleString("en-US", { timeZone: "America/Lima" }));
    const today = `${lima.getFullYear()}-${String(lima.getMonth() + 1).padStart(2, "0")}-${String(lima.getDate()).padStart(2, "0")}`;
    const weekday = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"][lima.getDay()];
    const question: string | null = body?.question ? String(body.question).slice(0, 300) : null;
    const options: string[] = Array.isArray(body?.options) ? body.options.slice(0, 8).map((o: unknown) => String(o).slice(0, 80)) : [];
    const userText = [
      question ? `PREGUNTA PENDIENTE: ${question}` : null,
      options.length ? `OPCIONES: ${options.map((o, i) => `${i + 1}) ${o}`).join(" | ")}` : null,
      `LO QUE DIJO EL USUARIO: ${text}`,
    ].filter(Boolean).join("\n");

    // 4) Gemini con salida JSON estructurada
    const model = Deno.env.get("GEMINI_MODEL") || "gemini-3.6-flash";
    const apiKey = Deno.env.get("GEMINI_API_KEY");
    if (!apiKey) return json({ error: "Falta el secreto GEMINI_API_KEY" }, 500);
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt(today, weekday) }] },
        contents: [{ role: "user", parts: [{ text: userText }] }],
        generationConfig: { responseMimeType: "application/json", responseSchema: SCHEMA, maxOutputTokens: 2048 },
      }),
    });
    if (r.status === 429) return json({ error: "La IA está saturada. Intenta de nuevo en un momento." });
    if (!r.ok) return json({ error: `Error del proveedor de IA (${r.status})`, detail: await r.text() });
    const g = await r.json();
    const parts: { text?: string; thought?: boolean }[] = g?.candidates?.[0]?.content?.parts ?? [];
    const raw = parts.filter((p) => !p.thought).map((p) => p.text ?? "").join("").trim();
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(raw);
    } catch {
      return json({ error: "No entendí bien. ¿Puedes repetirlo?" });
    }
    // Normalización mínima: formatos fuera de rango se descartan (la app los volverá a preguntar)
    const fp = Number(parsed.format_players);
    if (!(fp >= 5 && fp <= 11)) parsed.format_players = null;
    const hm = Number(parsed.half_minutes);
    if (!(hm >= 5 && hm <= 45)) parsed.half_minutes = null;
    if (typeof parsed.date === "string" && !/^\d{4}-\d{2}-\d{2}$/.test(parsed.date)) parsed.date = null;
    if (typeof parsed.time === "string" && !/^\d{2}:\d{2}$/.test(parsed.time)) parsed.time = null;
    return json({ ...parsed, today, remaining: access.remaining ?? null });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});
