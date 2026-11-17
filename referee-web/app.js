/*
 * SportPro Árbitro — página web para registrar un partido en tiempo real SIN cuenta.
 *
 * Cómo funciona:
 *  - El QR del DT abre esta página con un código secreto después del «#»
 *    (https://tu-dominio.vercel.app/#CODIGO). Lo que va después de «#» nunca se envía al
 *    servidor de la página: solo lo usa este archivo.
 *  - La página no lee ni escribe tablas. Solo llama a 4 funciones de Supabase que reciben el
 *    código, lo validan y hacen una acción: ver el partido, registrar un evento, cambiar el
 *    estado y anular un evento. Todas las reglas las valida el servidor.
 *  - Cada 4 segundos vuelve a pedir el partido: así ve también lo que registra el DT en la app.
 *    Quien mira en la app ve lo del árbitro al instante (Realtime).
 *  - Sin señal, los eventos se guardan en el navegador y se reenvían solos. Cada evento lleva un
 *    identificador propio, así un reenvío nunca lo duplica.
 */
(() => {
  'use strict';

  // ---------------------------------------------------------------------------------------
  // Estado
  // ---------------------------------------------------------------------------------------
  const POLL_MS = 4000;
  let cfg = null;            // { url, key } que entrega /api/config
  let token = '';            // código secreto del QR
  let data = null;           // última respuesta de referee_match
  let clockOffset = 0;       // diferencia entre la hora del servidor y la del celular (ms)
  let online = true;
  let busy = false;
  let draft = null;          // evento que se está armando en la hoja inferior
  let toastTimer = null;
  const sentIds = new Map();   // identificador del celular → id del evento en el servidor (para «Deshacer»)

  const $ = (id) => document.getElementById(id);

  // ---------------------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------------------

  /** Crea un elemento con texto SEGURO (textContent: un nombre nunca se interpreta como HTML). */
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    // Respaldo para navegadores antiguos (UUID v4 con números aleatorios seguros)
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }

  /** Lee el código del enlace: admite «#CODIGO» y «#t=CODIGO». */
  function readToken() {
    const raw = decodeURIComponent((location.hash || '').replace(/^#/, '')).trim();
    const m = raw.match(/(?:^|t=)([0-9a-f]{64})/i);
    return m ? m[1].toLowerCase() : '';
  }

  // Icono simple por tipo de evento (sin imágenes externas)
  const ICONS = {
    goal: '⚽', penalty: '🎯', yellow_card: '🟨', red_card: '🟥', substitution: '🔁', foul: '⚠️',
    corner: '🚩', free_kick: '🦶', goal_kick: '🥅', throw_in: '🙌', offside: '⛳', save: '🧤',
    clearance: '🛡️', match_start: '▶️', first_half_end: '⏸️', second_half_start: '▶️', match_end: '⏹️',
  };
  const icon = (code) => ICONS[code] || '•';

  // ---------------------------------------------------------------------------------------
  // Llamadas al servidor
  // ---------------------------------------------------------------------------------------

  class NetworkError extends Error {}

  /**
   * Llama a una función de Supabase (PostgREST). Solo envía la clave PUBLICABLE, que es
   * pública por diseño: lo que protege los datos es el código del QR y las reglas del servidor.
   */
  async function rpc(name, body) {
    const headers = { apikey: cfg.key, 'Content-Type': 'application/json' };
    // Las claves antiguas (JWT) también van como Authorization; las nuevas sb_publishable_ no
    if (cfg.key.startsWith('eyJ')) headers.Authorization = `Bearer ${cfg.key}`;
    let res;
    try {
      res = await fetch(`${cfg.url}/rest/v1/rpc/${name}`, { method: 'POST', headers, body: JSON.stringify(body) });
    } catch (e) {
      throw new NetworkError('Sin conexión');
    }
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    if (!res.ok) {
      // El servidor responde con el mensaje en español de la regla que no se cumplió
      throw new Error((json && (json.message || json.hint)) || `Error ${res.status}`);
    }
    return json;
  }

  // ---------------------------------------------------------------------------------------
  // Cola sin conexión (se guarda en el navegador)
  // ---------------------------------------------------------------------------------------
  const queueKey = () => `sportpro-referee-queue-${token.slice(0, 16)}`;

  function readQueue() {
    try { return JSON.parse(localStorage.getItem(queueKey()) || '[]'); } catch { return []; }
  }
  function writeQueue(q) {
    try { localStorage.setItem(queueKey(), JSON.stringify(q)); } catch { /* sin almacenamiento: se intenta igual */ }
  }

  /** Envía los pendientes en orden. Un error de red detiene el envío; un rechazo del servidor lo descarta y avisa. */
  async function flushQueue() {
    let q = readQueue();
    while (q.length) {
      const ev = q[0];
      try {
        const id = await rpc('referee_record_event', ev.body);
        sentIds.set(ev.body.p_client_event_id, id);
        setOnline(true);
      } catch (e) {
        if (e instanceof NetworkError) { setOnline(false); return; }
        showToast(`No se guardó «${ev.label}»: ${e.message}`);
      }
      q = readQueue().filter((x) => x.body.p_client_event_id !== ev.body.p_client_event_id);
      writeQueue(q);
    }
  }

  function setOnline(v) {
    online = v;
    renderNet();
  }

  // ---------------------------------------------------------------------------------------
  // Reloj del partido (igual que en la app: se calcula con datos del servidor)
  // ---------------------------------------------------------------------------------------
  function clock(m, nowMs) {
    let running = 0;
    if (m.clock_running && m.period_started_at) {
      running = Math.max(0, Math.floor((nowMs - Date.parse(m.period_started_at)) / 1000));
    }
    const elapsed = (m.elapsed_before_seconds || 0) + running;
    const p = Math.max(1, m.period || 0);
    const half = m.half_minutes;
    const base = (p - 1) * half;
    const inPeriod = Math.floor(elapsed / 60) + 1; // el primer minuto es el 1'
    if (inPeriod > half) return { minute: base + half, extra: Math.min(inPeriod - half, 30), period: p };
    return { minute: base + inPeriod, extra: 0, period: p };
  }
  const minuteLabel = (min, extra) => (extra > 0 ? `${min}+${extra}'` : `${min}'`);
  const serverNow = () => Date.now() + clockOffset;

  // ---------------------------------------------------------------------------------------
  // Jugadores en cancha (igual que onFieldPlayers de la app)
  // ---------------------------------------------------------------------------------------
  function lineupOf(side) { return (data.lineup || []).filter((p) => p.side === side); }

  function sortedActiveEvents(side) {
    return (data.events || [])
      .filter((e) => e.status !== 'voided' && e.side === side)
      .sort((a, b) => a.period - b.period || a.minute - b.minute || a.minute_extra - b.minute_extra || a.seq - b.seq);
  }

  /** Mapa clave → jugador con su posición actual: titulares ± cambios − rojas. */
  function onField(side) {
    const field = new Map();
    lineupOf(side).filter((p) => p.is_starter).forEach((p) => field.set(p.key, p.position));
    for (const e of sortedActiveEvents(side)) {
      if (e.code === 'substitution') {
        const out = e.payload && e.payload.player_out_id;
        const inn = e.payload && e.payload.player_in_id;
        let pos = null;
        if (out && field.has(out)) { pos = field.get(out); field.delete(out); }
        else { const p = lineupOf(side).find((x) => x.key === inn); pos = p ? p.position : null; }
        if (inn) field.set(inn, pos);
      } else if (e.code === 'red_card' && e.player_key) {
        field.delete(e.player_key);
      }
    }
    return field;
  }

  /** Jugadores que ya salieron (cambiados o expulsados): no vuelven a entrar. */
  function leftGame(side) {
    const out = new Set();
    for (const e of sortedActiveEvents(side)) {
      if (e.code === 'substitution' && e.payload && e.payload.player_out_id) out.add(e.payload.player_out_id);
      if (e.code === 'red_card' && e.player_key) out.add(e.player_key);
    }
    return out;
  }

  const playerByKey = (key) => (data.lineup || []).find((p) => p.key === key);
  const sideName = (side) => (side === 'home' ? data.match.home_name : data.match.away_name);

  // ---------------------------------------------------------------------------------------
  // Pintar la pantalla
  // ---------------------------------------------------------------------------------------
  function showNotice(title, body) {
    $('scoreboard').hidden = true;
    ['controls', 'record', 'timeline-section'].forEach((id) => { $(id).hidden = true; });
    const n = $('notice');
    n.hidden = false;
    n.replaceChildren(el('p', 'notice-title', title), el('p', 'notice-body', body || ''));
  }

  function renderNet() {
    const pending = token ? readQueue().length : 0;
    const n = $('net');
    n.className = 'net' + (online ? '' : ' off');
    n.textContent = !online
      ? (pending ? `Sin conexión · ${pending} por enviar` : 'Sin conexión')
      : (pending ? `Enviando ${pending}…` : 'En línea');
  }

  function renderClock() {
    if (!data) return;
    const m = data.match;
    const pill = $('status-pill');
    const c = clock(m, serverNow());
    if (m.status === 'live') { pill.textContent = 'EN VIVO'; pill.className = 'pill live'; $('clock').textContent = minuteLabel(c.minute, c.extra); }
    else if (m.status === 'halftime') { pill.textContent = 'ENTRETIEMPO'; pill.className = 'pill half'; $('clock').textContent = ''; }
    else if (m.status === 'finished') { pill.textContent = 'FINALIZADO'; pill.className = 'pill done'; $('clock').textContent = ''; }
    else {
      pill.textContent = 'POR INICIAR'; pill.className = 'pill';
      $('clock').textContent = m.scheduled_at
        ? new Date(m.scheduled_at).toLocaleString('es-PE', { weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
        : '';
    }
  }

  function render() {
    const m = data.match;
    $('notice').hidden = true;
    $('scoreboard').hidden = false;
    $('home-name').textContent = m.home_name;
    $('away-name').textContent = m.away_name;
    $('home-score').textContent = m.home_score;
    $('away-score').textContent = m.away_score;
    $('meta').textContent = [m.category, `${m.format_players} vs ${m.format_players}`, `${m.half_minutes}' por tiempo`, m.venue]
      .filter(Boolean).join(' · ');
    renderClock();
    renderNet();
    renderControls();
    renderEvents();
    renderTimeline();
  }

  function button(label, cls, onClick) {
    const b = el('button', `btn ${cls || ''}`, label);
    b.disabled = busy;
    b.addEventListener('click', onClick);
    return b;
  }

  function renderControls() {
    const m = data.match;
    const box = $('controls');
    box.replaceChildren();
    if (m.status === 'scheduled') box.append(button('Iniciar partido', 'primary', () => changeState('start')));
    if (m.status === 'live' && m.period === 1) box.append(button('Fin del 1.er tiempo', '', () => changeState('halftime')));
    if (m.status === 'halftime') box.append(button('Iniciar 2.º tiempo', 'primary', () => changeState('second_half')));
    if (m.status === 'live' || m.status === 'halftime') {
      box.append(button('Finalizar', 'danger', () => {
        if (confirm('¿Finalizar el partido? Después ya no se podrán registrar eventos.')) changeState('finish');
      }));
    }
    box.hidden = box.children.length === 0;
  }

  function renderEvents() {
    const m = data.match;
    const canRecord = m.status === 'live' || m.status === 'halftime';
    $('record').hidden = !canRecord;
    if (!canRecord) return;
    const grid = $('event-grid');
    grid.replaceChildren();
    for (const t of data.event_types) {
      const b = el('button', 'event-btn');
      b.append(el('span', 'event-ico', icon(t.code)), el('span', null, t.name));
      b.addEventListener('click', () => startDraft(t));
      grid.append(b);
    }
  }

  /** Texto de detalle de un evento: «en contra», resultado del penal o «sale X, entra Y». */
  function detailOf(e) {
    const p = e.payload || {};
    const parts = [];
    if (p.own_goal === true) parts.push('en contra');
    if (p.result) parts.push({ converted: 'convertido', missed: 'fallado', saved: 'atajado' }[p.result] || p.result);
    if (e.code === 'substitution') {
      const out = playerByKey(p.player_out_id);
      const inn = playerByKey(p.player_in_id);
      parts.push(`sale ${out ? out.name : '?'}, entra ${inn ? inn.name : '?'}`);
    }
    return parts.join(' · ');
  }

  function renderTimeline() {
    const list = $('timeline');
    list.replaceChildren();
    $('tl-home').textContent = data.match.home_name;
    $('tl-away').textContent = data.match.away_name;
    const live = data.match.status === 'live' || data.match.status === 'halftime';

    // Primero los que aún no llegan al servidor (marcados «pendiente»)
    for (const q of readQueue()) {
      const li = el('li', 'tl-row control');
      li.append(el('span', 'tag pending', 'PENDIENTE'), document.createTextNode(` ${q.label}`));
      list.append(li);
    }
    const events = [...(data.events || [])].sort((a, b) =>
      b.period - a.period || b.minute - a.minute || b.minute_extra - a.minute_extra || b.seq - a.seq);
    $('timeline-section').hidden = events.length === 0 && readQueue().length === 0;

    for (const e of events) {
      const voided = e.status === 'voided';
      if (e.group_kind === 'control') {
        list.append(el('li', 'tl-row control', `${icon(e.code)} ${e.name} · ${minuteLabel(e.minute, e.minute_extra)}`));
        continue;
      }
      const li = el('li', 'tl-row' + (voided ? ' voided' : ''));
      const side = el('div', `tl-side ${e.side}`);
      const text = el('div', 'tl-text');
      const who = e.code === 'substitution' ? null : (e.player_name || 'Sin jugador');
      text.append(el('div', 'tl-main', who || e.name));
      const sub = [who ? e.name : null, detailOf(e)].filter(Boolean).join(' · ');
      if (sub) text.append(el('div', 'tl-sub', sub));
      if (voided) text.append(el('span', 'tag', 'ANULADO'));
      const ico = el('span', 'event-ico', icon(e.code));
      if (e.side === 'home') side.append(text, ico); else side.append(ico, text);
      const left = el('div'); const right = el('div');
      (e.side === 'home' ? left : right).append(side);
      li.append(left, el('div', 'tl-min', minuteLabel(e.minute, e.minute_extra)), right);
      if (live && !voided) {
        li.classList.add('tappable');
        li.addEventListener('click', () => voidEvent(e));
      }
      list.append(li);
    }
  }

  // ---------------------------------------------------------------------------------------
  // Armar un evento en 3 toques: tipo → equipo → jugador (+ dato extra si hace falta)
  // ---------------------------------------------------------------------------------------
  function startDraft(type) {
    const c = clock(data.match, serverNow());
    draft = { type, minute: c.minute, extra: c.extra, period: c.period, side: null, payload: {} };
    $('sheet-title').textContent = `${icon(type.code)} ${type.name}`;
    $('sheet-backdrop').hidden = false;
    renderMinute();
    stepSide();
  }

  function closeSheet() { draft = null; $('sheet-backdrop').hidden = true; }

  function renderMinute() { $('minute-label').textContent = minuteLabel(draft.minute, draft.extra); }

  function setOptions(stepText, nodes, twoCols) {
    $('sheet-step').textContent = stepText;
    const box = $('sheet-options');
    box.className = 'options' + (twoCols ? ' two' : '');
    box.replaceChildren(...nodes);
  }

  function opt(label, onClick, extraCls) {
    const b = el('button', `opt ${extraCls || ''}`, label);
    b.addEventListener('click', onClick);
    return b;
  }

  function playerOpt(p, onClick) {
    const b = el('button', 'opt');
    b.append(el('span', 'num', p.jersey != null ? String(p.jersey) : '–'), el('span', null, p.name));
    if (p.position) b.append(el('span', 'pos', p.position));
    b.addEventListener('click', onClick);
    return b;
  }

  function stepSide() {
    setOptions('¿De qué equipo?', [
      opt(data.match.home_name, () => { draft.side = 'home'; stepPlayer(); }, 'center'),
      opt(data.match.away_name, () => { draft.side = 'away'; stepPlayer(); }, 'center'),
    ], true);
  }

  function fieldPlayers(side) {
    const field = onField(side);
    return [...field.keys()].map((k) => ({ ...(playerByKey(k) || { key: k, name: 'Jugador' }), position: field.get(k) }))
      .sort((a, b) => (a.jersey ?? 99) - (b.jersey ?? 99));
  }

  function stepPlayer() {
    const code = draft.type.code;
    const players = fieldPlayers(draft.side);

    // Atajada: se asigna sola al arquero en cancha
    if (code === 'save') {
      const gk = players.find((p) => p.position === 'POR');
      if (gk) { draft.player = gk; return submit(); }
    }
    if (code === 'substitution') return stepSubOut(players);

    const nodes = players.map((p) => playerOpt(p, () => { draft.player = p; afterPlayer(); }));
    nodes.push(opt('Sin identificar', () => { draft.player = null; afterPlayer(); }, 'muted'));
    // Gol en contra: el gol suma al otro equipo (lo calcula el servidor)
    if (code === 'goal') {
      const og = opt(draft.payload.own_goal ? '✓ Gol en contra' : 'Gol en contra', () => {
        draft.payload.own_goal = !draft.payload.own_goal; stepPlayer();
      }, 'center' + (draft.payload.own_goal ? ' on' : ''));
      nodes.unshift(og);
    }
    setOptions(players.length ? `¿Quién? (${sideName(draft.side)})` : `No hay alineación de ${sideName(draft.side)}: registra sin jugador`, nodes);
  }

  function afterPlayer() {
    if (draft.type.code === 'penalty') {
      return setOptions('¿Cómo terminó el penal?', [
        opt('Convertido', () => { draft.payload.result = 'converted'; submit(); }, 'center'),
        opt('Atajado', () => { draft.payload.result = 'saved'; submit(); }, 'center'),
        opt('Fallado', () => { draft.payload.result = 'missed'; submit(); }, 'center'),
      ]);
    }
    submit();
  }

  function stepSubOut(players) {
    setOptions(`¿Quién sale? (${sideName(draft.side)})`,
      players.map((p) => playerOpt(p, () => { draft.payload.player_out_id = p.key; stepSubIn(); })));
  }

  function stepSubIn() {
    const field = onField(draft.side);
    const left = leftGame(draft.side);
    const bench = lineupOf(draft.side).filter((p) => !field.has(p.key) && !left.has(p.key));
    const nodes = bench.map((p) => playerOpt(p, () => { draft.payload.player_in_id = p.key; draft.player = null; submit(); }));
    setOptions(bench.length ? '¿Quién entra?' : 'No quedan suplentes disponibles', nodes);
  }

  /** Guarda el evento: primero en la cola del navegador y luego lo envía. */
  async function submit() {
    const d = draft;
    closeSheet();
    const p = d.player;
    const isExternal = p && p.external;
    const label = [d.type.name, sideName(d.side), p ? p.name : null, minuteLabel(d.minute, d.extra)].filter(Boolean).join(' · ');
    const body = {
      p_token: token,
      p_client_event_id: uuid(),
      p_event_type: d.type.id,
      p_period: d.period,
      p_minute: d.minute,
      p_minute_extra: d.extra,
      p_team_side: d.side,
      p_player: p && !isExternal ? p.key : null,
      p_external_player: p && isExternal ? p.key : null,
      p_payload: d.payload,
    };
    writeQueue([...readQueue(), { body, label }]);
    renderTimeline();
    renderNet();
    showToast(`Registrado: ${label}`, () => undo(body.p_client_event_id));
    await flushQueue();
    await load();
  }

  /** «Deshacer»: si aún no salió, se quita de la cola; si ya llegó, se anula con motivo. */
  async function undo(clientEventId) {
    const q = readQueue();
    if (q.some((x) => x.body.p_client_event_id === clientEventId)) {
      writeQueue(q.filter((x) => x.body.p_client_event_id !== clientEventId));
      renderTimeline(); renderNet();
      showToast('Se quitó el evento');
      return;
    }
    // Ya está en el servidor: se busca por su identificador y se anula
    try {
      // Si se envió en esta sesión ya se conoce su id. Si no, el servidor lo devuelve: con un
      // client_event_id que ya existe, referee_record_event no inserta nada y entrega el id guardado.
      const id = sentIds.get(clientEventId) || await rpc('referee_record_event', {
        p_token: token, p_client_event_id: clientEventId, p_event_type: null, p_period: null, p_minute: null,
      });
      await rpc('referee_void_event', { p_token: token, p_event: id, p_reason: 'Deshecho por el árbitro' });
      showToast('Evento anulado');
    } catch (e) {
      showToast(e.message);
    }
    await load();
  }

  async function voidEvent(e) {
    const reason = prompt(`Anular «${e.name}${e.player_name ? ' · ' + e.player_name : ''} · ${minuteLabel(e.minute, e.minute_extra)}».\nEscribe el motivo:`);
    if (reason == null) return;
    if (reason.trim().length < 3) { showToast('El motivo debe tener al menos 3 letras'); return; }
    try {
      await rpc('referee_void_event', { p_token: token, p_event: e.id, p_reason: reason.trim() });
      showToast('Evento anulado');
    } catch (err) {
      showToast(err.message);
    }
    await load();
  }

  async function changeState(action) {
    busy = true; renderControls();
    try {
      await rpc('referee_change_state', { p_token: token, p_action: action });
    } catch (e) {
      showToast(e.message);
    }
    busy = false;
    await load();
  }

  // ---------------------------------------------------------------------------------------
  // Aviso inferior
  // ---------------------------------------------------------------------------------------
  function showToast(text, onAction) {
    $('toast-text').textContent = text;
    const a = $('toast-action');
    a.hidden = !onAction;
    a.onclick = onAction ? () => { $('toast').hidden = true; onAction(); } : null;
    $('toast').hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { $('toast').hidden = true; }, onAction ? 7000 : 4000);
  }

  // ---------------------------------------------------------------------------------------
  // Carga y ciclo
  // ---------------------------------------------------------------------------------------
  async function load() {
    try {
      const fresh = await rpc('referee_match', { p_token: token });
      clockOffset = Date.parse(fresh.server_now) - Date.now();
      data = fresh;
      setOnline(true);
      if (!draft) render(); else { renderClock(); renderNet(); }
    } catch (e) {
      if (e instanceof NetworkError) { setOnline(false); if (!data) showNotice('Sin conexión', 'Se reintentará solo.'); return; }
      data = null;
      showNotice('No se puede abrir el partido', e.message);
    }
  }

  async function main() {
    token = readToken();
    if (!token) {
      showNotice('Escanea el QR del partido', 'Pídele al DT que te muestre el QR del árbitro desde la app SportPro.');
      return;
    }
    try {
      cfg = await (await fetch('/api/config', { cache: 'no-store' })).json();
    } catch {
      cfg = null;
    }
    if (!cfg || !cfg.url || !cfg.key) {
      showNotice('Falta configurar la página', 'En Vercel agrega SUPABASE_URL y SUPABASE_PUBLISHABLE_KEY (ver README).');
      return;
    }
    cfg.url = cfg.url.replace(/\/+$/, '');

    $('sheet-close').addEventListener('click', closeSheet);
    $('sheet-backdrop').addEventListener('click', (e) => { if (e.target === $('sheet-backdrop')) closeSheet(); });
    $('min-down').addEventListener('click', () => {
      if (!draft) return;
      if (draft.extra > 0) draft.extra -= 1; else draft.minute = Math.max(0, draft.minute - 1);
      renderMinute();
    });
    $('min-up').addEventListener('click', () => {
      if (!draft) return;
      // En el tiempo añadido se suma al añadido (25+2 → 25+3); si no, al minuto
      if (draft.extra > 0) draft.extra = Math.min(30, draft.extra + 1); else draft.minute = Math.min(200, draft.minute + 1);
      renderMinute();
    });
    window.addEventListener('online', async () => { await flushQueue(); await load(); });
    window.addEventListener('offline', () => setOnline(false));
    window.addEventListener('hashchange', () => location.reload());

    await flushQueue();
    await load();
    setInterval(renderClock, 1000);
    setInterval(async () => { await flushQueue(); await load(); }, POLL_MS);
  }

  main();
})();
