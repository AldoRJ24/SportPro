-- =====================================================================
-- SportPro · Migración 023: suscripciones de academias (pago SIMULADO) y límites de IA.
--
--   Planes (los contrata el administrador de la academia):
--     - BÁSICO: gratis. Todo menos las funciones de IA (resumen con IA y las que vengan).
--     - BLACK : US$ 50 al mes. IA con límite: 3 usos cada 3 días por entrenador.
--     - GOLD  : US$ 100 al mes. IA con 5 veces más (15 usos cada 3 días por entrenador),
--               funciones exclusivas (próximamente) y el nombre de la academia y sus equipos
--               en dorado animado.
--     - Pago anual: 20 % de descuento por mes (Black US$ 40/mes = 480 al año; Gold US$ 80/mes = 960).
--
--   El pago es una SIMULACIÓN (proyecto académico): no se cobra nada ni se guardan tarjetas.
--   Queda registrado como movimiento con método «simulado».
--
--   Todas las academias que ya existían quedan en Black anual. Las nuevas no tienen plan hasta
--   que su administrador elige uno (el Básico es gratis y se elige con un toque).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------
create table if not exists public.academy_subscriptions (
  id           uuid primary key default gen_random_uuid(),
  academy_id   uuid not null references public.academies(id) on delete cascade,
  plan         text not null check (plan in ('basic','black','gold')),
  billing      text not null check (billing in ('monthly','annual')),
  price_usd    numeric(8,2) not null check (price_usd >= 0),   -- lo cobrado en ese período
  started_at   timestamptz not null default now(),
  renews_at    timestamptz not null,
  -- Al cambiar de plan, el anterior no se borra: se marca como reemplazado (trazabilidad)
  replaced_at  timestamptz,
  created_by   uuid references public.profiles(id),
  created_at   timestamptz not null default now()
);
create index if not exists academy_subscriptions_academy_idx on public.academy_subscriptions (academy_id, created_at desc);
-- Una sola suscripción vigente por academia
create unique index if not exists academy_subscriptions_current_uq
  on public.academy_subscriptions (academy_id) where replaced_at is null;

create table if not exists public.subscription_payments (
  id               uuid primary key default gen_random_uuid(),
  subscription_id  uuid not null references public.academy_subscriptions(id) on delete cascade,
  amount_usd       numeric(8,2) not null check (amount_usd > 0),
  method           text not null default 'simulado' check (method = 'simulado'),
  paid_by          uuid references public.profiles(id),
  paid_at          timestamptz not null default now()
);

-- Cada uso de una función de IA (para contar el límite por entrenador)
create table if not exists public.ai_usage (
  id          uuid primary key default gen_random_uuid(),
  academy_id  uuid not null references public.academies(id) on delete cascade,
  user_id     uuid not null references public.profiles(id) on delete cascade,
  feature     text not null default 'match_summary',
  match_id    uuid references public.matches(id) on delete set null,
  created_at  timestamptz not null default now()
);
create index if not exists ai_usage_user_idx on public.ai_usage (academy_id, user_id, created_at desc);

alter table public.academy_subscriptions enable row level security;
alter table public.subscription_payments enable row level security;
alter table public.ai_usage              enable row level security;
revoke all on public.academy_subscriptions, public.subscription_payments, public.ai_usage from anon, authenticated;

-- ---------------------------------------------------------------------
-- 2. Datos fijos de los planes
-- ---------------------------------------------------------------------
-- Precio mensual de lista (US$)
create or replace function public._plan_monthly_price(p_plan text)
returns numeric language sql immutable set search_path = public as $fn$
  select case p_plan when 'black' then 50 when 'gold' then 100 else 0 end::numeric
$fn$;

-- Lo que se cobra al contratar: mensual = precio de lista; anual = 12 meses con 20 % menos
create or replace function public._plan_charge(p_plan text, p_billing text)
returns numeric language sql immutable set search_path = public as $fn$
  select case when p_billing = 'annual' then round(public._plan_monthly_price(p_plan) * 0.8 * 12, 2)
              else public._plan_monthly_price(p_plan) end
$fn$;

-- Usos de IA permitidos cada 3 días por entrenador
create or replace function public._plan_ai_limit(p_plan text)
returns int language sql immutable set search_path = public as $fn$
  select case p_plan when 'black' then 3 when 'gold' then 15 else 0 end
$fn$;

-- Plan vigente de una academia (null = todavía no eligió)
create or replace function public.academy_plan(p_academy uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select plan from public.academy_subscriptions
   where academy_id = p_academy and replaced_at is null and renews_at > now()
   order by created_at desc limit 1
$fn$;

-- ---------------------------------------------------------------------
-- 3. Academias existentes: Black anual (pedido del cliente)
-- ---------------------------------------------------------------------
insert into public.academy_subscriptions (academy_id, plan, billing, price_usd, started_at, renews_at)
select a.id, 'black', 'annual', public._plan_charge('black', 'annual'), now(), now() + interval '1 year'
from public.academies a
where not exists (select 1 from public.academy_subscriptions s where s.academy_id = a.id and s.replaced_at is null);

-- ---------------------------------------------------------------------
-- 4. Contratar o cambiar de plan (solo el administrador de la academia). Pago simulado.
-- ---------------------------------------------------------------------
create or replace function public.subscribe_academy(p_academy uuid, p_plan text, p_billing text)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_charge numeric; v_name text; v_renews timestamptz;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not (public.has_academy_role(p_academy, 'adm') or public.is_platform_admin()) then
    raise exception 'Solo el administrador de la academia puede elegir el plan';
  end if;
  if p_plan is null or p_plan not in ('basic','black','gold') then raise exception 'Plan no válido'; end if;
  if p_billing is null or p_billing not in ('monthly','annual') then raise exception 'Elige mensual o anual'; end if;
  if p_plan = 'basic' then p_billing := 'monthly'; end if;   -- el Básico no tiene período que pagar

  v_charge := public._plan_charge(p_plan, p_billing);
  v_renews := now() + case when p_billing = 'annual' then interval '1 year' else interval '1 month' end;

  update public.academy_subscriptions set replaced_at = now()
   where academy_id = p_academy and replaced_at is null;
  insert into public.academy_subscriptions (academy_id, plan, billing, price_usd, renews_at, created_by)
  values (p_academy, p_plan, p_billing, v_charge, v_renews, auth.uid())
  returning id into v_id;
  if v_charge > 0 then
    insert into public.subscription_payments (subscription_id, amount_usd, paid_by) values (v_id, v_charge, auth.uid());
  end if;

  select name into v_name from public.academies where id = p_academy;
  perform public._notify(m.user_id, 'subscription_changed', 'Plan de la academia',
    v_name || ' ahora tiene el plan ' || initcap(p_plan)
      || case when v_charge > 0 then ' (' || case when p_billing = 'annual' then 'anual' else 'mensual' end
                                   || ', US$ ' || v_charge || ', pago simulado)' else ' (gratis)' end || '.',
    jsonb_build_object('academy_id', p_academy, 'plan', p_plan))
  from public.academy_memberships m
  where m.academy_id = p_academy and m.role = 'adm' and m.status = 'active';
  perform public.log_audit('subscription_changed', 'academy', p_academy,
    jsonb_build_object('plan', p_plan, 'billing', p_billing, 'charged_usd', v_charge, 'simulated', true));
  return jsonb_build_object('subscription_id', v_id, 'plan', p_plan, 'billing', p_billing,
                            'charged_usd', v_charge, 'renews_at', v_renews);
end $fn$;

-- Academias que administro y su plan (null = falta elegir)
create or replace function public.my_academy_subscriptions()
returns table (academy_id uuid, academy_name text, plan text, billing text, price_usd numeric, renews_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select a.id, a.name, s.plan, s.billing, s.price_usd, s.renews_at
  from public.academies a
  left join public.academy_subscriptions s
         on s.academy_id = a.id and s.replaced_at is null and s.renews_at > now()
  where public.has_academy_role(a.id, 'adm')
  order by a.name
$fn$;

-- ---------------------------------------------------------------------
-- 5. Nombres dorados: academias Gold y sus equipos (información pública de la vitrina)
-- ---------------------------------------------------------------------
create or replace function public.gold_names()
returns table (kind text, id uuid)
language sql stable security definer set search_path = public as $fn$
  select 'academy', a.id from public.academies a where public.academy_plan(a.id) = 'gold'
  union all
  select 'team', t.id from public.teams t where public.academy_plan(t.academy_id) = 'gold'
$fn$;

-- ---------------------------------------------------------------------
-- 6. Límite de IA por entrenador
-- ---------------------------------------------------------------------
-- Academia cuyo plan se usa para un partido: la del equipo que dirijo en él (primero el local)
create or replace function public._ai_academy_for(p_match uuid, p_user uuid)
returns uuid language sql stable security definer set search_path = public as $fn$
  select t.academy_id
  from public.matches m
  join public.teams t on t.id in (m.home_team_id, m.away_team_id)
  where m.id = p_match
  order by (exists (select 1 from public.team_staff s where s.team_id = t.id and s.user_id = p_user)) desc,
           (t.id = m.home_team_id) desc
  limit 1
$fn$;

-- Mi cupo de IA para un partido: plan, límite, usados en los últimos 3 días y cuándo se libera uno
create or replace function public.ai_quota(p_match uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare v_academy uuid; v_plan text; v_limit int; v_used int; v_oldest timestamptz;
begin
  v_academy := public._ai_academy_for(p_match, auth.uid());
  v_plan := coalesce(public.academy_plan(v_academy), 'basic');
  v_limit := public._plan_ai_limit(v_plan);
  select count(*), min(created_at) into v_used, v_oldest from public.ai_usage
   where academy_id = v_academy and user_id = auth.uid() and created_at > now() - interval '3 days';
  return jsonb_build_object('plan', v_plan, 'limit', v_limit, 'used', v_used,
    'remaining', greatest(v_limit - v_used, 0),
    'next_free_at', case when v_used >= v_limit and v_limit > 0 then v_oldest + interval '3 days' end,
    'is_admin', public.has_academy_role(v_academy, 'adm'));
end $fn$;

-- La llama la Edge Function ANTES de usar la IA: falla con un mensaje claro si no hay cupo
create or replace function public.ai_quota_check(p_match uuid)
returns void language plpgsql stable security definer set search_path = public as $fn$
declare q jsonb := public.ai_quota(p_match);
begin
  if (q->>'limit')::int = 0 then
    raise exception 'El resumen con IA está disponible en los planes Black y Gold. Tu academia tiene el plan Básico.';
  end if;
  if (q->>'remaining')::int <= 0 then
    raise exception 'Usaste tus % resúmenes con IA de estos 3 días. Podrás usar otro el %.',
      q->>'limit', to_char((q->>'next_free_at')::timestamptz at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI');
  end if;
end $fn$;

-- La llama la Edge Function (con la clave de servicio) DESPUÉS de que la IA respondió bien:
-- así un error de la IA no le gasta un uso al entrenador
create or replace function public.ai_usage_record(p_user uuid, p_match uuid, p_feature text default 'match_summary')
returns void language plpgsql security definer set search_path = public as $fn$
begin
  insert into public.ai_usage (academy_id, user_id, feature, match_id)
  values (public._ai_academy_for(p_match, p_user), p_user, coalesce(p_feature, 'match_summary'), p_match);
end $fn$;

-- ---------------------------------------------------------------------
-- 7. Permisos
-- ---------------------------------------------------------------------
revoke execute on function public._plan_monthly_price(text), public._plan_charge(text, text), public._plan_ai_limit(text),
  public._ai_academy_for(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.academy_plan(uuid), public.subscribe_academy(uuid, text, text),
  public.my_academy_subscriptions(), public.gold_names(), public.ai_quota(uuid), public.ai_quota_check(uuid)
  from public, anon;
grant execute on function public.academy_plan(uuid), public.subscribe_academy(uuid, text, text),
  public.my_academy_subscriptions(), public.gold_names(), public.ai_quota(uuid), public.ai_quota_check(uuid)
  to authenticated;
-- Registrar un uso: solo la Edge Function (clave de servicio), nunca la app
revoke execute on function public.ai_usage_record(uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.ai_usage_record(uuid, uuid, text) to service_role;

commit;
