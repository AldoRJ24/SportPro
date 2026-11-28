-- =====================================================================
-- SportPro · Migración 024: modo manos libres (asistente de voz para crear partidos).
--
--   El asistente de voz es una herramienta de IA: está en los planes Black y Gold y gasta del
--   mismo cupo que el resumen con IA (Black 3, Gold 15 usos cada 3 días por entrenador).
--   Cuenta UN uso por partido creado por voz, no por cada frase: conversar, corregir o
--   cancelar no gasta nada.
--
--   - voice_access(): ¿puedo usar el asistente? Mira el mejor plan entre las academias de los
--     equipos que dirijo o administro, y cuántos usos me quedan en ellas.
--   - voice_usage_record(p_match): registra el uso cuando el asistente creó el partido (o la
--     propuesta/desafío). Solo puede descontarse cupo a sí mismo.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- Academia cuyo cupo usa el asistente: la de mejor plan entre las que dirijo o administro
-- (Gold > Black > Básico); a igual plan, la que tenga más usos libres.
create or replace function public._voice_academy(p_user uuid)
returns uuid language sql stable security definer set search_path = public as $fn$
  with mine as (
    select t.academy_id from public.team_staff s join public.teams t on t.id = s.team_id where s.user_id = p_user
    union
    select m.academy_id from public.academy_memberships m
     where m.user_id = p_user and m.role = 'adm' and m.status = 'active'
  )
  select m.academy_id from mine m
  order by public._plan_ai_limit(coalesce(public.academy_plan(m.academy_id), 'basic'))
           - (select count(*) from public.ai_usage u
               where u.academy_id = m.academy_id and u.user_id = p_user and u.created_at > now() - interval '3 days') desc
  limit 1
$fn$;

create or replace function public.voice_access()
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare v_academy uuid; v_plan text; v_limit int; v_used int; v_oldest timestamptz;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  v_academy := public._voice_academy(auth.uid());
  if v_academy is null then
    return jsonb_build_object('allowed', false, 'reason', 'Solo los entrenadores y administradores pueden crear partidos por voz.');
  end if;
  v_plan := coalesce(public.academy_plan(v_academy), 'basic');
  v_limit := public._plan_ai_limit(v_plan);
  select count(*), min(created_at) into v_used, v_oldest from public.ai_usage
   where academy_id = v_academy and user_id = auth.uid() and created_at > now() - interval '3 days';
  if v_limit = 0 then
    return jsonb_build_object('allowed', false, 'plan', v_plan,
      'reason', 'El modo manos libres está en los planes Black y Gold. Tu academia tiene el plan Básico.');
  end if;
  if v_used >= v_limit then
    return jsonb_build_object('allowed', false, 'plan', v_plan,
      'reason', 'Usaste tus ' || v_limit || ' usos de IA de estos 3 días. Podrás usar otro el '
        || to_char((v_oldest + interval '3 days') at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI') || '.');
  end if;
  return jsonb_build_object('allowed', true, 'plan', v_plan, 'remaining', v_limit - v_used, 'limit', v_limit);
end $fn$;

-- Descuenta un uso al crear el partido por voz. Siempre a quien llama (auth.uid()): nadie puede
-- gastar el cupo de otra persona. [p_match] puede ser el id de un partido o, si se envió un
-- desafío/propuesta, null o un id que todavía no es partido (entonces no se enlaza).
create or replace function public.voice_usage_record(p_match uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare v_academy uuid;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  v_academy := public._voice_academy(auth.uid());
  if v_academy is null then raise exception 'Sin academia para registrar el uso'; end if;
  insert into public.ai_usage (academy_id, user_id, feature, match_id)
  values (v_academy, auth.uid(), 'voice_match',
          case when exists (select 1 from public.matches where id = p_match) then p_match end);
end $fn$;

revoke execute on function public._voice_academy(uuid) from public, anon, authenticated;
revoke execute on function public.voice_access(), public.voice_usage_record(uuid) from public, anon;
grant execute on function public.voice_access(), public.voice_usage_record(uuid) to authenticated;
-- La Edge Function también consulta el acceso con la sesión del usuario (no necesita más permisos)

commit;
