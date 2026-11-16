-- =====================================================================
-- SportPro · Migración 008: mensualidades (SIMULADAS, sin pasarela de pago)
--
-- Reglas de negocio (todas se validan aquí, en el servidor):
--  1. El ADMIN de la academia define el plan del equipo: monto y día de vencimiento.
--  2. El DT (o el admin) ELIGE qué jugadores del equipo pagan mensualidad. No es para todos.
--  3. El DT (o el admin) genera los cobros del mes. Es idempotente: repetirlo no duplica.
--  4. QUIÉN PAGA: si el jugador es menor de edad, SOLO su apoderado aprobado.
--     Si ya tiene 18 años o más, también puede pagar el propio jugador.
--  5. El pago es simulado: el apoderado «informa» que pagó (Yape, Plin, transferencia o
--     efectivo, con número de operación). El dinero va al ADMIN, que confirma o rechaza.
--     El admin también puede registrar directamente un pago recibido fuera de la app.
--  6. El DT solo MIRA: ve si cada jugador de su equipo está al día y todos los movimientos.
--  7. Nada se borra: un pago se rechaza o se anula con motivo, un cobro se exonera con
--     motivo, y todo queda en audit_log.
--  8. «Vencido» no se guarda: se calcula (pendiente + fecha de vencimiento pasada). Así no
--     hace falta un proceso nocturno que cambie estados.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. TABLAS
-- ---------------------------------------------------------------------

-- Plan de mensualidad de un equipo (uno por equipo)
create table if not exists public.fee_plans (
  id         uuid primary key default gen_random_uuid(),
  team_id    uuid not null unique references public.teams(id) on delete cascade,
  amount     numeric(10,2) not null check (amount > 0 and amount <= 10000),
  due_day    smallint not null check (due_day between 1 and 28),   -- hasta 28 para que exista en febrero
  currency   text not null default 'PEN' check (currency = 'PEN'),
  updated_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Jugadores del equipo seleccionados para pagar mensualidad
create table if not exists public.fee_players (
  team_id    uuid not null references public.teams(id) on delete cascade,
  player_id  uuid not null references public.players(id) on delete cascade,
  active     boolean not null default true,
  updated_by uuid references public.profiles(id),
  updated_at timestamptz not null default now(),
  primary key (team_id, player_id)
);
create index if not exists fee_players_player_idx on public.fee_players (player_id);

-- Cobro mensual por jugador. La clave única (equipo, jugador, mes) da la idempotencia.
create table if not exists public.fee_charges (
  id               uuid primary key default gen_random_uuid(),
  team_id          uuid not null references public.teams(id) on delete cascade,
  player_id        uuid not null references public.players(id) on delete cascade,
  period           date not null check (extract(day from period) = 1),  -- siempre el día 1 del mes
  amount           numeric(10,2) not null check (amount > 0),
  currency         text not null default 'PEN' check (currency = 'PEN'),
  due_date         date not null,
  status           text not null default 'pending' check (status in ('pending','paid','waived')),
  waived_reason    text,
  paid_at          timestamptz,
  last_reminder_on date,                    -- para no enviar dos recordatorios el mismo día
  created_by       uuid references public.profiles(id),
  created_at       timestamptz not null default now(),
  unique (team_id, player_id, period)
);
create index if not exists fee_charges_player_idx on public.fee_charges (player_id);

-- Pagos (movimientos). reported = informado por la familia; confirmed = recibido por el admin.
create table if not exists public.fee_payments (
  id              uuid primary key default gen_random_uuid(),
  charge_id       uuid not null references public.fee_charges(id) on delete cascade,
  amount          numeric(10,2) not null check (amount > 0),
  method          text not null check (method in ('cash','yape','plin','transfer')),
  reference       text,                     -- número de operación (Yape/Plin/transferencia)
  note            text,
  paid_on         date not null,
  status          text not null default 'reported' check (status in ('reported','confirmed','rejected','voided')),
  reported_by     uuid references public.profiles(id),
  created_at      timestamptz not null default now(),
  decided_by      uuid references public.profiles(id),
  decided_at      timestamptz,
  decision_reason text
);
create index if not exists fee_payments_charge_idx on public.fee_payments (charge_id);
-- Un cobro solo puede tener UN pago «vivo» (informado o confirmado) a la vez
create unique index if not exists fee_payments_live_uq
  on public.fee_payments (charge_id) where status in ('reported','confirmed');

-- ---------------------------------------------------------------------
-- 2. FUNCIONES AUXILIARES
-- ---------------------------------------------------------------------

-- «Hoy» en hora de Perú (el servidor trabaja en UTC y a las 7 p. m. ya sería mañana)
create or replace function public._today_pe()
returns date language sql stable set search_path = public as $fn$
  select (now() at time zone 'America/Lima')::date
$fn$;

-- «noviembre 2026»
create or replace function public._fee_period_label(p_period date)
returns text language sql immutable set search_path = public as $fn$
  select (array['enero','febrero','marzo','abril','mayo','junio','julio','agosto',
                'septiembre','octubre','noviembre','diciembre'])[extract(month from p_period)::int]
         || ' ' || extract(year from p_period)::int
$fn$;

-- «S/ 120.00»
create or replace function public._fee_money(p_amount numeric)
returns text language sql immutable set search_path = public as $fn$
  select 'S/ ' || to_char(p_amount, 'FM999990.00')
$fn$;

-- ¿Puedo pagar la mensualidad de este jugador? Menor: solo su apoderado. Adulto: él o su apoderado.
create or replace function public.fee_can_pay(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.players pl
    where pl.id = p_player
      and (public.is_guardian_of(pl.id)
           or (pl.user_id = auth.uid() and public.age_years(pl.birth_date) >= 18)))
$fn$;

-- El dinero lo recibe el admin de la academia: solo él confirma, rechaza, anula o exonera
create or replace function public.fee_is_admin(p_team uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_platform_admin() or public.has_academy_role(public.team_academy(p_team), 'adm')
$fn$;

-- Avisa a quienes pagan por un jugador (apoderados aprobados y, si es adulto, él mismo)
create or replace function public._fee_notify_payers(p_player uuid, p_kind text, p_title text, p_body text, p_data jsonb)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  perform public._notify(s.u, p_kind, p_title, p_body, p_data)
  from (select g.guardian_user_id as u from public.guardian_player_links g
         where g.player_id = p_player and g.status = 'approved'
        union
        select pl.user_id from public.players pl
         where pl.id = p_player and pl.user_id is not null and public.age_years(pl.birth_date) >= 18) s;
end $fn$;

-- Avisa a los administradores de la academia del equipo
create or replace function public._fee_notify_admins(p_team uuid, p_kind text, p_title text, p_body text, p_data jsonb)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  perform public._notify(m.user_id, p_kind, p_title, p_body, p_data)
  from public.academy_memberships m
  where m.academy_id = public.team_academy(p_team) and m.role = 'adm' and m.status = 'active';
end $fn$;

-- ---------------------------------------------------------------------
-- 3. RLS: lectura para staff del equipo y para quien paga; escritura SOLO por funciones
-- ---------------------------------------------------------------------
alter table public.fee_plans    enable row level security;
alter table public.fee_players  enable row level security;
alter table public.fee_charges  enable row level security;
alter table public.fee_payments enable row level security;

drop policy if exists fee_plans_select on public.fee_plans;
create policy fee_plans_select on public.fee_plans for select to authenticated
  using (public.is_team_manager(team_id)
         or exists (select 1 from public.fee_players fp
                    where fp.team_id = fee_plans.team_id and public.fee_can_pay(fp.player_id)));

drop policy if exists fee_players_select on public.fee_players;
create policy fee_players_select on public.fee_players for select to authenticated
  using (public.is_team_manager(team_id) or public.fee_can_pay(player_id));

drop policy if exists fee_charges_select on public.fee_charges;
create policy fee_charges_select on public.fee_charges for select to authenticated
  using (public.is_team_manager(team_id) or public.fee_can_pay(player_id));

drop policy if exists fee_payments_select on public.fee_payments;
create policy fee_payments_select on public.fee_payments for select to authenticated
  using (exists (select 1 from public.fee_charges c
                 where c.id = fee_payments.charge_id
                   and (public.is_team_manager(c.team_id) or public.fee_can_pay(c.player_id))));

revoke all on public.fee_plans, public.fee_players, public.fee_charges, public.fee_payments from anon, authenticated;
grant select on public.fee_plans, public.fee_players, public.fee_charges, public.fee_payments to authenticated;

-- ---------------------------------------------------------------------
-- 4. FUNCIONES DE ESCRITURA
-- ---------------------------------------------------------------------

-- 4.1 El admin define (o cambia) el plan del equipo. Los cobros ya generados no cambian.
create or replace function public.fee_set_plan(p_team uuid, p_amount numeric, p_due_day int)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid;
begin
  if not public.fee_is_admin(p_team) then
    raise exception 'Solo el administrador de la academia puede definir la mensualidad';
  end if;
  if p_amount is null or p_amount <= 0 or p_amount > 10000 then
    raise exception 'El monto debe ser mayor que 0 y como máximo S/ 10000';
  end if;
  if p_due_day is null or p_due_day < 1 or p_due_day > 28 then
    raise exception 'El día de vencimiento debe estar entre 1 y 28';
  end if;
  insert into public.fee_plans (team_id, amount, due_day, updated_by)
  values (p_team, round(p_amount, 2), p_due_day, auth.uid())
  on conflict (team_id) do update
    set amount = excluded.amount, due_day = excluded.due_day, updated_by = auth.uid(), updated_at = now()
  returning id into v_id;
  perform public.log_audit('fee_plan_set', 'team', p_team,
    jsonb_build_object('amount', round(p_amount, 2), 'due_day', p_due_day));
  return v_id;
end $fn$;

-- 4.2 El DT (o el admin) marca o desmarca a un jugador como «paga mensualidad»
create or replace function public.fee_set_player(p_team uuid, p_player uuid, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not public.is_team_manager(p_team) then
    raise exception 'Solo el DT o el administrador del equipo elige quién paga mensualidad';
  end if;
  if not exists (select 1 from public.team_players
                 where team_id = p_team and player_id = p_player and status = 'active') then
    raise exception 'El jugador no pertenece al plantel activo de este equipo';
  end if;
  insert into public.fee_players (team_id, player_id, active, updated_by)
  values (p_team, p_player, coalesce(p_enabled, false), auth.uid())
  on conflict (team_id, player_id) do update
    set active = excluded.active, updated_by = auth.uid(), updated_at = now();
  perform public.log_audit('fee_player_' || case when coalesce(p_enabled, false) then 'enabled' else 'disabled' end,
    'team', p_team, jsonb_build_object('player_id', p_player));
end $fn$;

-- 4.3 Generar los cobros de un mes para los jugadores seleccionados. IDEMPOTENTE:
--     la clave única (equipo, jugador, mes) + «on conflict do nothing» evita duplicados.
--     Devuelve cuántos cobros NUEVOS creó (0 si ya estaban todos).
create or replace function public.fee_generate_charges(p_team uuid, p_period date default null)
returns int language plpgsql security definer set search_path = public as $fn$
declare
  v_plan public.fee_plans%rowtype;
  v_today date := public._today_pe();
  v_period date; v_due date; v_n int := 0; r record;
begin
  if not public.is_team_manager(p_team) then
    raise exception 'Solo el DT o el administrador del equipo puede generar cobros';
  end if;
  select * into v_plan from public.fee_plans where team_id = p_team;
  if not found then
    raise exception 'Este equipo aún no tiene mensualidad definida. Pídele al administrador que fije el monto.';
  end if;
  v_period := date_trunc('month', coalesce(p_period, v_today))::date;
  -- Solo el mes actual, el siguiente o hasta 2 meses atrás (evita cobros por error de fecha)
  if v_period > (date_trunc('month', v_today) + interval '1 month')::date
     or v_period < (date_trunc('month', v_today) - interval '2 months')::date then
    raise exception 'Solo se pueden generar cobros del mes actual, el siguiente o los dos anteriores';
  end if;
  -- Vence el día del plan; si ese día ya pasó, vence hoy (no nace vencido)
  v_due := greatest(v_period + (v_plan.due_day - 1), v_today);

  for r in
    insert into public.fee_charges (team_id, player_id, period, amount, due_date, created_by)
    select p_team, fp.player_id, v_period, v_plan.amount, v_due, auth.uid()
    from public.fee_players fp
    join public.team_players tp on tp.team_id = fp.team_id and tp.player_id = fp.player_id and tp.status = 'active'
    where fp.team_id = p_team and fp.active
    on conflict (team_id, player_id, period) do nothing
    returning id, player_id
  loop
    v_n := v_n + 1;
    perform public._fee_notify_payers(r.player_id, 'fee_charge', 'Nueva mensualidad',
      'Mensualidad de ' || public._fee_period_label(v_period) || ' de '
        || (select trim(first_name || ' ' || last_name) from public.players where id = r.player_id)
        || ': ' || public._fee_money(v_plan.amount) || '. Vence el ' || to_char(v_due, 'DD/MM/YYYY') || '.',
      jsonb_build_object('charge_id', r.id, 'team_id', p_team, 'player_id', r.player_id));
  end loop;

  perform public.log_audit('fee_charges_generated', 'team', p_team,
    jsonb_build_object('period', v_period, 'created', v_n));
  return v_n;
end $fn$;

-- 4.4 La familia (o el jugador adulto) INFORMA que pagó. Queda «por confirmar» por el admin.
create or replace function public.fee_report_payment(p_charge uuid, p_method text, p_reference text default null, p_note text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare c public.fee_charges%rowtype; v_id uuid; v_name text;
begin
  select * into c from public.fee_charges where id = p_charge for update;
  if not found then raise exception 'Cobro no encontrado'; end if;
  if not public.fee_can_pay(c.player_id) then
    raise exception 'Solo el apoderado (o el jugador si es mayor de edad) puede pagar esta mensualidad';
  end if;
  if c.status <> 'pending' then raise exception 'Este cobro ya está pagado o exonerado'; end if;
  if p_method is null or p_method not in ('cash','yape','plin','transfer') then
    raise exception 'Método de pago no válido';
  end if;
  -- Con Yape, Plin o transferencia hace falta el número de operación para que el admin lo verifique
  if p_method <> 'cash' and char_length(trim(coalesce(p_reference, ''))) < 4 then
    raise exception 'Escribe el número de operación (mínimo 4 caracteres)';
  end if;
  if exists (select 1 from public.fee_payments where charge_id = c.id and status = 'reported') then
    raise exception 'Ya informaste un pago para este cobro. Espera la confirmación del administrador.';
  end if;

  insert into public.fee_payments (charge_id, amount, method, reference, note, paid_on, status, reported_by)
  values (c.id, c.amount, p_method, nullif(trim(coalesce(p_reference, '')), ''),
          nullif(trim(coalesce(p_note, '')), ''), public._today_pe(), 'reported', auth.uid())
  returning id into v_id;

  select trim(first_name || ' ' || last_name) into v_name from public.players where id = c.player_id;
  perform public._fee_notify_admins(c.team_id, 'fee_payment_reported', 'Pago por confirmar',
    'Informaron el pago de ' || public._fee_period_label(c.period) || ' de ' || v_name
      || ' (' || public._fee_money(c.amount) || '). Revísalo y confírmalo.',
    jsonb_build_object('charge_id', c.id, 'payment_id', v_id, 'team_id', c.team_id, 'player_id', c.player_id));
  perform public.log_audit('fee_payment_reported', 'fee_charge', c.id,
    jsonb_build_object('payment_id', v_id, 'method', p_method));
  return v_id;
end $fn$;

-- 4.5 El admin registra directamente un pago que recibió fuera de la app (queda confirmado)
create or replace function public.fee_register_payment(p_charge uuid, p_method text, p_paid_on date default null,
                                                       p_reference text default null, p_note text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare c public.fee_charges%rowtype; v_id uuid; v_on date;
begin
  select * into c from public.fee_charges where id = p_charge for update;
  if not found then raise exception 'Cobro no encontrado'; end if;
  if not public.fee_is_admin(c.team_id) then
    raise exception 'Solo el administrador de la academia registra pagos';
  end if;
  if c.status <> 'pending' then raise exception 'Este cobro ya está pagado o exonerado'; end if;
  if p_method is null or p_method not in ('cash','yape','plin','transfer') then
    raise exception 'Método de pago no válido';
  end if;
  if exists (select 1 from public.fee_payments where charge_id = c.id and status = 'reported') then
    raise exception 'Hay un pago informado por la familia: confírmalo o recházalo primero';
  end if;
  v_on := coalesce(p_paid_on, public._today_pe());
  if v_on > public._today_pe() then raise exception 'La fecha de pago no puede ser futura'; end if;

  insert into public.fee_payments (charge_id, amount, method, reference, note, paid_on, status,
                                   reported_by, decided_by, decided_at)
  values (c.id, c.amount, p_method, nullif(trim(coalesce(p_reference, '')), ''),
          nullif(trim(coalesce(p_note, '')), ''), v_on, 'confirmed', auth.uid(), auth.uid(), now())
  returning id into v_id;
  update public.fee_charges set status = 'paid', paid_at = now() where id = c.id;

  perform public._fee_notify_payers(c.player_id, 'fee_payment_confirmed', 'Pago registrado',
    'Se registró el pago de la mensualidad de ' || public._fee_period_label(c.period)
      || ' (' || public._fee_money(c.amount) || ').',
    jsonb_build_object('charge_id', c.id, 'payment_id', v_id, 'team_id', c.team_id, 'player_id', c.player_id));
  perform public.log_audit('fee_payment_registered', 'fee_charge', c.id,
    jsonb_build_object('payment_id', v_id, 'method', p_method, 'paid_on', v_on));
  return v_id;
end $fn$;

-- 4.6 El admin confirma o rechaza (con motivo) un pago informado por la familia
create or replace function public.fee_decide_payment(p_payment uuid, p_accept boolean, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $fn$
declare p public.fee_payments%rowtype; c public.fee_charges%rowtype; v_ok boolean := coalesce(p_accept, false);
begin
  select * into p from public.fee_payments where id = p_payment for update;
  if not found then raise exception 'Pago no encontrado'; end if;
  select * into c from public.fee_charges where id = p.charge_id for update;
  if not public.fee_is_admin(c.team_id) then
    raise exception 'Solo el administrador de la academia confirma o rechaza pagos';
  end if;
  if p.status <> 'reported' then raise exception 'Este pago ya fue revisado'; end if;
  if not v_ok and char_length(trim(coalesce(p_reason, ''))) < 3 then
    raise exception 'Escribe el motivo del rechazo';
  end if;

  update public.fee_payments
     set status = case when v_ok then 'confirmed' else 'rejected' end,
         decided_by = auth.uid(), decided_at = now(),
         decision_reason = nullif(trim(coalesce(p_reason, '')), '')
   where id = p.id;
  if v_ok then update public.fee_charges set status = 'paid', paid_at = now() where id = c.id; end if;

  perform public._fee_notify_payers(c.player_id,
    case when v_ok then 'fee_payment_confirmed' else 'fee_payment_rejected' end,
    case when v_ok then 'Pago confirmado' else 'Pago rechazado' end,
    'Mensualidad de ' || public._fee_period_label(c.period) || ' (' || public._fee_money(c.amount) || ')'
      || case when v_ok then ': el administrador confirmó tu pago.'
              else ': el administrador rechazó el pago. Motivo: ' || trim(p_reason) end,
    jsonb_build_object('charge_id', c.id, 'payment_id', p.id, 'team_id', c.team_id, 'player_id', c.player_id));
  perform public.log_audit('fee_payment_' || case when v_ok then 'confirmed' else 'rejected' end,
    'fee_charge', c.id, jsonb_build_object('payment_id', p.id, 'reason', p_reason));
end $fn$;

-- 4.7 El admin ANULA un pago ya confirmado (se equivocó). No se borra: queda «anulado»
--     con motivo y el cobro vuelve a pendiente.
create or replace function public.fee_void_payment(p_payment uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare p public.fee_payments%rowtype; c public.fee_charges%rowtype;
begin
  select * into p from public.fee_payments where id = p_payment for update;
  if not found then raise exception 'Pago no encontrado'; end if;
  select * into c from public.fee_charges where id = p.charge_id for update;
  if not public.fee_is_admin(c.team_id) then
    raise exception 'Solo el administrador de la academia anula pagos';
  end if;
  if p.status <> 'confirmed' then raise exception 'Solo se puede anular un pago confirmado'; end if;
  if char_length(trim(coalesce(p_reason, ''))) < 3 then raise exception 'Escribe el motivo de la anulación'; end if;

  update public.fee_payments
     set status = 'voided', decided_by = auth.uid(), decided_at = now(), decision_reason = trim(p_reason)
   where id = p.id;
  update public.fee_charges set status = 'pending', paid_at = null where id = c.id;

  perform public._fee_notify_payers(c.player_id, 'fee_payment_voided', 'Pago anulado',
    'Se anuló el pago de la mensualidad de ' || public._fee_period_label(c.period) || '. Motivo: ' || trim(p_reason),
    jsonb_build_object('charge_id', c.id, 'payment_id', p.id, 'team_id', c.team_id, 'player_id', c.player_id));
  perform public.log_audit('fee_payment_voided', 'fee_charge', c.id,
    jsonb_build_object('payment_id', p.id, 'reason', trim(p_reason)));
end $fn$;

-- 4.8 El admin EXONERA un cobro pendiente (beca, lesión…) con motivo
create or replace function public.fee_waive_charge(p_charge uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare c public.fee_charges%rowtype;
begin
  select * into c from public.fee_charges where id = p_charge for update;
  if not found then raise exception 'Cobro no encontrado'; end if;
  if not public.fee_is_admin(c.team_id) then
    raise exception 'Solo el administrador de la academia exonera cobros';
  end if;
  if c.status <> 'pending' then raise exception 'Solo se puede exonerar un cobro pendiente'; end if;
  if char_length(trim(coalesce(p_reason, ''))) < 3 then raise exception 'Escribe el motivo de la exoneración'; end if;
  if exists (select 1 from public.fee_payments where charge_id = c.id and status = 'reported') then
    raise exception 'Hay un pago informado por la familia: confírmalo o recházalo primero';
  end if;

  update public.fee_charges set status = 'waived', waived_reason = trim(p_reason) where id = c.id;
  perform public._fee_notify_payers(c.player_id, 'fee_charge_waived', 'Mensualidad exonerada',
    'La mensualidad de ' || public._fee_period_label(c.period) || ' fue exonerada. Motivo: ' || trim(p_reason),
    jsonb_build_object('charge_id', c.id, 'team_id', c.team_id, 'player_id', c.player_id));
  perform public.log_audit('fee_charge_waived', 'fee_charge', c.id, jsonb_build_object('reason', trim(p_reason)));
end $fn$;

-- 4.9 Recordatorio de vencimiento: avisa a quien paga cada cobro VENCIDO del equipo.
--     Como mucho un aviso por cobro y por día (last_reminder_on). Devuelve cuántos envió.
create or replace function public.fee_send_reminders(p_team uuid)
returns int language plpgsql security definer set search_path = public as $fn$
declare v_today date := public._today_pe(); v_n int := 0; r record;
begin
  if not public.is_team_manager(p_team) then
    raise exception 'Solo el DT o el administrador del equipo envía recordatorios';
  end if;
  for r in
    update public.fee_charges c set last_reminder_on = v_today
     where c.team_id = p_team and c.status = 'pending' and c.due_date < v_today
       and c.last_reminder_on is distinct from v_today
       and not exists (select 1 from public.fee_payments p where p.charge_id = c.id and p.status = 'reported')
    returning c.id, c.player_id, c.period, c.amount, c.due_date
  loop
    v_n := v_n + 1;
    perform public._fee_notify_payers(r.player_id, 'fee_overdue', 'Mensualidad vencida',
      'La mensualidad de ' || public._fee_period_label(r.period) || ' (' || public._fee_money(r.amount)
        || ') venció el ' || to_char(r.due_date, 'DD/MM/YYYY') || '.',
      jsonb_build_object('charge_id', r.id, 'team_id', p_team, 'player_id', r.player_id));
  end loop;
  perform public.log_audit('fee_reminders_sent', 'team', p_team, jsonb_build_object('sent', v_n));
  return v_n;
end $fn$;

-- ---------------------------------------------------------------------
-- 5. FUNCIONES DE LECTURA
-- ---------------------------------------------------------------------

-- 5.1 Resumen por equipo de los equipos que gestiono (admin: toda su academia; DT: sus equipos)
create or replace function public.fee_summary_for_me()
returns table (team_id uuid, team_name text, category text, academy_name text, can_admin boolean,
               plan_amount numeric, due_day int, enrolled int,
               debt numeric, pending_count int, overdue_count int, reported_count int)
language sql stable security definer set search_path = public as $fn$
  select t.id, t.name, t.category, a.name, public.fee_is_admin(t.id),
         pl.amount, pl.due_day::int,
         (select count(*)::int from public.fee_players fp where fp.team_id = t.id and fp.active),
         coalesce((select sum(c.amount) from public.fee_charges c where c.team_id = t.id and c.status = 'pending'), 0),
         (select count(*)::int from public.fee_charges c where c.team_id = t.id and c.status = 'pending'),
         (select count(*)::int from public.fee_charges c
           where c.team_id = t.id and c.status = 'pending' and c.due_date < public._today_pe()),
         (select count(*)::int from public.fee_payments p join public.fee_charges c on c.id = p.charge_id
           where c.team_id = t.id and p.status = 'reported')
  from public.teams t
  join public.academies a on a.id = t.academy_id
  left join public.fee_plans pl on pl.team_id = t.id
  where public.is_team_manager(t.id)
  order by a.name, t.name
$fn$;

-- 5.2 Plantel activo del equipo con su situación: ¿paga mensualidad?, deuda, vencidos…
create or replace function public.fee_team_players(p_team uuid)
returns table (player_id uuid, player_name text, jersey_number int, enabled boolean,
               debt numeric, pending_count int, overdue_count int, reported_count int, last_paid_on date)
language plpgsql stable security definer set search_path = public as $fn$
begin
  if not public.is_team_manager(p_team) then
    raise exception 'Solo el DT o el administrador del equipo ve las mensualidades del equipo';
  end if;
  return query
  select p.id, trim(p.first_name || ' ' || p.last_name), tp.jersey_number::int,
         coalesce(fp.active, false),
         coalesce((select sum(c.amount) from public.fee_charges c
                    where c.team_id = p_team and c.player_id = p.id and c.status = 'pending'), 0),
         (select count(*)::int from public.fee_charges c
           where c.team_id = p_team and c.player_id = p.id and c.status = 'pending'),
         (select count(*)::int from public.fee_charges c
           where c.team_id = p_team and c.player_id = p.id and c.status = 'pending'
             and c.due_date < public._today_pe()),
         (select count(*)::int from public.fee_payments y join public.fee_charges c on c.id = y.charge_id
           where c.team_id = p_team and c.player_id = p.id and y.status = 'reported'),
         (select max(y.paid_on) from public.fee_payments y join public.fee_charges c on c.id = y.charge_id
           where c.team_id = p_team and c.player_id = p.id and y.status = 'confirmed')
  from public.team_players tp
  join public.players p on p.id = tp.player_id
  left join public.fee_players fp on fp.team_id = tp.team_id and fp.player_id = tp.player_id
  where tp.team_id = p_team and tp.status = 'active'
  order by coalesce(fp.active, false) desc, 2;
end $fn$;

-- 5.3 Estado de cuenta: cada cobro con su último movimiento y TODOS los detalles.
--     · Sin parámetros: lo que YO pago (mis hijos; o yo mismo si soy jugador adulto).
--     · Con p_team / p_player: vista del DT o admin de ese equipo.
--     El filtro de permisos está en el WHERE: nunca devuelve cobros ajenos.
create or replace function public.fee_statement(p_team uuid default null, p_player uuid default null)
returns table (charge_id uuid, team_id uuid, team_name text, player_id uuid, player_name text,
               period date, amount numeric, currency text, due_date date,
               status text,                 -- pending | overdue | paid | waived
               waived_reason text, can_pay boolean, can_admin boolean,
               payment_id uuid, payment_status text, method text, reference text, note text,
               paid_on date, reported_by_name text, reported_at timestamptz,
               decided_by_name text, decided_at timestamptz, decision_reason text)
language sql stable security definer set search_path = public as $fn$
  select c.id, c.team_id, t.name, c.player_id, trim(p.first_name || ' ' || p.last_name),
         c.period, c.amount, c.currency, c.due_date,
         case when c.status = 'pending' and c.due_date < public._today_pe() then 'overdue' else c.status end,
         c.waived_reason, public.fee_can_pay(c.player_id), public.fee_is_admin(c.team_id),
         y.id, y.status, y.method, y.reference, y.note, y.paid_on,
         nullif(trim(rb.full_name), ''), y.created_at,
         nullif(trim(db.full_name), ''), y.decided_at, y.decision_reason
  from public.fee_charges c
  join public.teams t on t.id = c.team_id
  join public.players p on p.id = c.player_id
  -- último movimiento del cobro (los anteriores rechazados/anulados quedan en fee_payments)
  left join lateral (select * from public.fee_payments fp where fp.charge_id = c.id
                     order by fp.created_at desc limit 1) y on true
  left join public.profiles rb on rb.id = y.reported_by
  left join public.profiles db on db.id = y.decided_by
  where (p_team is null or c.team_id = p_team)
    and (p_player is null or c.player_id = p_player)
    and case when p_team is null and p_player is null
             then public.fee_can_pay(c.player_id)                                -- «mi» estado de cuenta
             else public.fee_can_pay(c.player_id) or public.is_team_manager(c.team_id) end
  order by c.period desc, 5
$fn$;

-- ---------------------------------------------------------------------
-- 6. PERMISOS DE EJECUCIÓN
-- ---------------------------------------------------------------------
-- Internas: nadie las llama desde la app
revoke execute on function public._today_pe(), public._fee_period_label(date), public._fee_money(numeric),
  public._fee_notify_payers(uuid, text, text, text, jsonb),
  public._fee_notify_admins(uuid, text, text, text, jsonb) from public, anon, authenticated;

revoke execute on function public.fee_can_pay(uuid), public.fee_is_admin(uuid),
  public.fee_set_plan(uuid, numeric, int), public.fee_set_player(uuid, uuid, boolean),
  public.fee_generate_charges(uuid, date), public.fee_report_payment(uuid, text, text, text),
  public.fee_register_payment(uuid, text, date, text, text), public.fee_decide_payment(uuid, boolean, text),
  public.fee_void_payment(uuid, text), public.fee_waive_charge(uuid, text), public.fee_send_reminders(uuid),
  public.fee_summary_for_me(), public.fee_team_players(uuid), public.fee_statement(uuid, uuid)
  from public, anon;
grant execute on function public.fee_can_pay(uuid), public.fee_is_admin(uuid),
  public.fee_set_plan(uuid, numeric, int), public.fee_set_player(uuid, uuid, boolean),
  public.fee_generate_charges(uuid, date), public.fee_report_payment(uuid, text, text, text),
  public.fee_register_payment(uuid, text, date, text, text), public.fee_decide_payment(uuid, boolean, text),
  public.fee_void_payment(uuid, text), public.fee_waive_charge(uuid, text), public.fee_send_reminders(uuid),
  public.fee_summary_for_me(), public.fee_team_players(uuid), public.fee_statement(uuid, uuid)
  to authenticated;

commit;
