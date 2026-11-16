-- =====================================================================
-- SportPro · Migración 010: ajustes de notificaciones + avisos de
-- convocatoria y de partido en vivo.
--
--  1. notification_prefs: cada usuario apaga o enciende 5 categorías de avisos:
--        callups  = convocatorias          live     = partido en vivo
--        comments = comentarios            requests = solicitudes y documentos
--        payments = mensualidades
--     Si no hay fila, la categoría está ENCENDIDA (por defecto se avisa todo).
--  2. _notify (la única puerta por donde se crean notificaciones) ahora respeta esos
--     ajustes: si el usuario apagó la categoría, la notificación no se crea.
--     Así el filtro vive en el servidor y vale para todas las funciones, viejas y nuevas.
--  3. Dos avisos que faltaban, hechos con triggers (no se toca ninguna función anterior):
--        · «Convocatoria»: al convocar a un jugador se avisa a su apoderado y al jugador.
--        · «Partido en vivo»: al iniciar un partido se avisa a las familias de los
--          convocados y a quienes siguen al equipo o a la academia.
--     Ambos llevan match_id en «data» para que la app abra el partido al tocarlos.
--  4. mark_all_notifications_read(): marcar todo como leído en una sola llamada.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. TABLA DE PREFERENCIAS
-- ---------------------------------------------------------------------
create table if not exists public.notification_prefs (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  category   text not null check (category in ('callups','live','comments','requests','payments')),
  enabled    boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (user_id, category)
);

alter table public.notification_prefs enable row level security;
drop policy if exists notification_prefs_select on public.notification_prefs;
create policy notification_prefs_select on public.notification_prefs for select to authenticated
  using (user_id = auth.uid());
revoke all on public.notification_prefs from anon, authenticated;
grant select on public.notification_prefs to authenticated;   -- se escribe solo con set_notification_pref

-- ---------------------------------------------------------------------
-- 2. A QUÉ CATEGORÍA PERTENECE CADA TIPO DE NOTIFICACIÓN
--    Un tipo sin categoría (null) se entrega siempre.
-- ---------------------------------------------------------------------
create or replace function public._notification_category(p_kind text)
returns text language sql immutable set search_path = public as $fn$
  select case
    when p_kind = 'callup'                                   then 'callups'
    when p_kind = 'match_live'                               then 'live'
    when p_kind like 'post\_comment%'                        then 'comments'
    when p_kind like 'fee\_%'                                then 'payments'
    when p_kind like 'guardian\_%' or p_kind like 'team\_join\_%'
      or p_kind like 'document\_%' or p_kind = 'invite_used' then 'requests'
    else null end
$fn$;

-- ---------------------------------------------------------------------
-- 3. _notify RESPETA LAS PREFERENCIAS (misma firma: no cambia nada para quien la llama)
-- ---------------------------------------------------------------------
create or replace function public._notify(p_user uuid, p_kind text, p_title text, p_body text, p_data jsonb default '{}')
returns void language sql security definer set search_path = public as $fn$
  insert into public.notifications (user_id, kind, title, body, data)
  select p_user, p_kind, p_title, p_body, coalesce(p_data, '{}'::jsonb)
  where p_user is not null
    and not exists (select 1 from public.notification_prefs np
                    where np.user_id = p_user
                      and np.category = public._notification_category(p_kind)
                      and not np.enabled);
$fn$;

-- ---------------------------------------------------------------------
-- 4. FUNCIONES PARA LA APP
-- ---------------------------------------------------------------------

-- Mis 5 categorías con su estado (las que nunca toqué salen encendidas)
create or replace function public.my_notification_prefs()
returns table (category text, enabled boolean)
language sql stable security definer set search_path = public as $fn$
  select c.category, coalesce(np.enabled, true)
  from unnest(array['callups','live','comments','requests','payments']) with ordinality as c(category, pos)
  left join public.notification_prefs np on np.user_id = auth.uid() and np.category = c.category
  order by c.pos
$fn$;

create or replace function public.set_notification_pref(p_category text, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if p_category is null or p_category not in ('callups','live','comments','requests','payments') then
    raise exception 'Categoría de notificación no válida';
  end if;
  insert into public.notification_prefs (user_id, category, enabled)
  values (auth.uid(), p_category, coalesce(p_enabled, true))
  on conflict (user_id, category) do update set enabled = excluded.enabled, updated_at = now();
end $fn$;

-- Marca como leídas TODAS mis notificaciones pendientes. Devuelve cuántas marcó.
create or replace function public.mark_all_notifications_read()
returns int language plpgsql security definer set search_path = public as $fn$
declare v_n int;
begin
  update public.notifications set read_at = now() where user_id = auth.uid() and read_at is null;
  get diagnostics v_n = row_count;
  return v_n;
end $fn$;

-- ---------------------------------------------------------------------
-- 5. AVISO DE CONVOCATORIA (trigger al insertar en match_callups)
-- ---------------------------------------------------------------------
create or replace function public.trg_callup_notify()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare v_name text; v_body text;
begin
  -- Solo si queda pendiente de respuesta: si el DT la crea ya confirmada no hay nada que responder
  if new.status <> 'pending' then return new; end if;
  select trim(first_name || ' ' || last_name) into v_name from public.players where id = new.player_id;
  select v_name || ' fue convocado al partido de ' || t.name
         || coalesce(' del ' || to_char(m.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI'), '')
         || '. Confirma su asistencia.'
    into v_body
    from public.matches m join public.teams t on t.id = new.team_id
   where m.id = new.match_id;

  -- A los apoderados aprobados y a la cuenta del propio jugador (si tiene)
  perform public._notify(s.u, 'callup', 'Convocatoria', v_body,
            jsonb_build_object('match_id', new.match_id, 'callup_id', new.id,
                               'team_id', new.team_id, 'player_id', new.player_id))
  from (select g.guardian_user_id as u from public.guardian_player_links g
         where g.player_id = new.player_id and g.status = 'approved'
        union
        select pl.user_id from public.players pl where pl.id = new.player_id and pl.user_id is not null) s;
  return new;
end $fn$;

drop trigger if exists trg_callups_notify on public.match_callups;
create trigger trg_callups_notify after insert on public.match_callups
  for each row execute function public.trg_callup_notify();

-- ---------------------------------------------------------------------
-- 6. AVISO DE PARTIDO EN VIVO (trigger cuando el partido pasa de programado a en vivo)
-- ---------------------------------------------------------------------
create or replace function public.trg_match_live_notify()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare v_title text;
begin
  select th.name || ' vs ' || coalesce(ta.name, new.away_external_name, 'Rival')
    into v_title
    from public.teams th left join public.teams ta on ta.id = new.away_team_id
   where th.id = new.home_team_id;

  perform public._notify(s.u, 'match_live', 'Partido en vivo',
            'Empezó ' || v_title || '. Síguelo minuto a minuto.',
            jsonb_build_object('match_id', new.id, 'team_id', new.home_team_id))
  from (
    -- familias y jugadores convocados (de cualquiera de los dos equipos)
    select g.guardian_user_id as u
      from public.match_callups c
      join public.guardian_player_links g on g.player_id = c.player_id and g.status = 'approved'
     where c.match_id = new.id and c.status <> 'declined'
    union
    select pl.user_id
      from public.match_callups c join public.players pl on pl.id = c.player_id
     where c.match_id = new.id and c.status <> 'declined' and pl.user_id is not null
    union
    -- seguidores del equipo o de la academia, solo si pueden ver el partido (no los privados)
    select f.user_id
      from public.follows f
     where new.visibility <> 'private'
       and (f.team_id in (new.home_team_id, new.away_team_id) or f.academy_id = new.academy_id)
  ) s
  -- quien inició el partido ya lo sabe
  where s.u is distinct from auth.uid();
  return new;
end $fn$;

drop trigger if exists trg_matches_live_notify on public.matches;
create trigger trg_matches_live_notify after update of status on public.matches
  for each row when (old.status = 'scheduled' and new.status = 'live')
  execute function public.trg_match_live_notify();

-- ---------------------------------------------------------------------
-- 7. PERMISOS DE EJECUCIÓN
-- ---------------------------------------------------------------------
revoke execute on function public._notification_category(text), public.trg_callup_notify(),
  public.trg_match_live_notify() from public, anon, authenticated;
-- (_notify conserva los permisos que ya tenía: «create or replace» no los cambia)

revoke execute on function public.my_notification_prefs(), public.set_notification_pref(text, boolean),
  public.mark_all_notifications_read() from public, anon;
grant execute on function public.my_notification_prefs(), public.set_notification_pref(text, boolean),
  public.mark_all_notifications_read() to authenticated;

commit;
