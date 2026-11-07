-- =====================================================================
-- SportPro · Migración 003: tipos de cuenta, código familiar, vínculo
-- apoderado↔jugador menor, notificaciones e invitaciones (ADM y DT).
-- Reglas (en el servidor):
--   · Apoderado, administrador y entrenador: 18 años o más.
--   · Jugador: 13 años o más; si es menor de 18 debe vincularse a un apoderado,
--     que acepta o rechaza la solicitud.
--   · Administrador: solo con invitación del superadmin y creando su academia.
--   · Entrenador: solo con invitación de un administrador; queda en esa academia.
--   · El tipo de cuenta lo fija el servidor al registrarse; el cliente no puede cambiarlo.
-- =====================================================================
begin;

-- 1) Perfil: tipo de cuenta y código familiar -------------------------
alter table public.profiles add column if not exists account_type text;
alter table public.profiles drop constraint if exists profiles_account_type_chk;
alter table public.profiles add constraint profiles_account_type_chk
  check (account_type is null or account_type in ('pad','jug','adm','dt'));
alter table public.profiles add column if not exists guardian_code text;
create unique index if not exists profiles_guardian_code_uq on public.profiles (guardian_code) where guardian_code is not null;
-- (el cliente solo puede actualizar full_name y avatar_path: account_type/guardian_code no son editables)

-- 2) Notificaciones ---------------------------------------------------
create table if not exists public.notifications (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade,
  kind       text not null,
  title      text not null,
  body       text,
  data       jsonb not null default '{}'::jsonb,
  read_at    timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications (user_id, created_at desc);
alter table public.notifications enable row level security;
drop policy if exists notifications_select on public.notifications;
create policy notifications_select on public.notifications for select to authenticated using (user_id = auth.uid());
drop policy if exists notifications_update on public.notifications;
create policy notifications_update on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;

-- 3) Invitaciones -----------------------------------------------------
create table if not exists public.invitations (
  id          uuid primary key default gen_random_uuid(),
  code        text not null unique,
  kind        text not null check (kind in ('adm','dt')),
  academy_id  uuid references public.academies(id) on delete cascade,
  created_by  uuid references public.profiles(id) default auth.uid(),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '7 days',
  used_by     uuid references public.profiles(id) on delete set null,
  used_at     timestamptz,
  revoked_at  timestamptz,
  check ((kind = 'adm' and academy_id is null) or (kind = 'dt' and academy_id is not null))
);
alter table public.invitations enable row level security;
drop policy if exists invitations_select on public.invitations;
create policy invitations_select on public.invitations for select to authenticated
  using (created_by = auth.uid() or public.is_platform_admin());
grant select on public.invitations to authenticated;   -- crear/usar solo vía funciones

-- 4) Utilidades -------------------------------------------------------
create or replace function public._random_code(p_len int)
returns text language plpgsql volatile set search_path = public as $fn$
declare
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';  -- sin 0/O/1/I/L
  out text := '';
begin
  for i in 1..p_len loop
    out := out || substr(alphabet, 1 + (get_byte(uuid_send(gen_random_uuid()), 0) % length(alphabet)), 1);
  end loop;
  return out;
end $fn$;

create or replace function public._new_code(p_prefix text)
returns text language plpgsql volatile set search_path = public as $fn$
declare c text;
begin
  loop
    c := p_prefix || '-' || public._random_code(4) || '-' || public._random_code(4);
    exit when not exists (select 1 from public.profiles where guardian_code = c)
          and not exists (select 1 from public.invitations where code = c);
  end loop;
  return c;
end $fn$;

create or replace function public._notify(p_user uuid, p_kind text, p_title text, p_body text, p_data jsonb default '{}')
returns void language sql security definer set search_path = public as $fn$
  insert into public.notifications (user_id, kind, title, body, data) values (p_user, p_kind, p_title, p_body, coalesce(p_data, '{}'::jsonb));
$fn$;

create or replace function public._invite_label(p_kind text)
returns text language sql immutable as $fn$
  select case p_kind when 'adm' then 'administrador de academia' else 'entrenador (DT)' end
$fn$;

-- Valida y aplica una invitación a un usuario (alta de academia o de DT)
create or replace function public._apply_invitation(p_user uuid, p_code text, p_academy_name text, p_academy_city text)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare inv public.invitations%rowtype; v_academy uuid; v_name text;
begin
  select * into inv from public.invitations where code = upper(trim(p_code)) for update;
  if not found then raise exception 'Código de invitación inválido'; end if;
  if inv.revoked_at is not null then raise exception 'Esta invitación fue anulada'; end if;
  if inv.used_at is not null then raise exception 'Esta invitación ya fue usada'; end if;
  if inv.expires_at < now() then raise exception 'Esta invitación venció; pide una nueva'; end if;

  select full_name into v_name from public.profiles where id = p_user;
  if inv.kind = 'adm' then
    if char_length(trim(coalesce(p_academy_name, ''))) < 2 then
      raise exception 'Para registrarte como administrador debes registrar tu academia (nombre)';
    end if;
    insert into public.academies (name, city, status, created_by)
    values (trim(p_academy_name), nullif(trim(coalesce(p_academy_city, '')), ''), 'verified', p_user)
    returning id into v_academy;
    insert into public.academy_memberships (academy_id, user_id, role, status, invited_by)
    values (v_academy, p_user, 'adm', 'active', inv.created_by)
    on conflict (academy_id, user_id, role) do update set status = 'active';
  else
    v_academy := inv.academy_id;
    insert into public.academy_memberships (academy_id, user_id, role, status, invited_by)
    values (v_academy, p_user, 'dt', 'active', inv.created_by)
    on conflict (academy_id, user_id, role) do update set status = 'active';
  end if;

  update public.invitations set used_by = p_user, used_at = now() where id = inv.id;
  if inv.created_by is not null then
    perform public._notify(inv.created_by, 'invite_used', 'Invitación aceptada',
      coalesce(nullif(v_name, ''), 'Un usuario') || ' se registró como ' || public._invite_label(inv.kind),
      jsonb_build_object('invitation_id', inv.id, 'academy_id', v_academy));
  end if;
  return v_academy;
end $fn$;

-- 5) Alta de usuario: tipo de cuenta, edades, código familiar, invitación
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_birth date; v_name text; v_first text; v_last text;
  v_type text := nullif(lower(trim(coalesce(meta->>'account_type', ''))), '');
  v_invite text := nullif(upper(trim(coalesce(meta->>'invite_code', ''))), '');
  v_kind text; v_age int;
begin
  v_first := nullif(trim(coalesce(meta->>'first_name', '')), '');
  v_last  := nullif(trim(coalesce(meta->>'last_name', '')), '');
  v_name := coalesce(nullif(trim(meta->>'full_name'), ''), nullif(trim(concat_ws(' ', v_first, v_last)), ''),
                     split_part(coalesce(new.email, ''), '@', 1), '');
  begin
    v_birth := nullif(meta->>'birth_date', '')::date;
  exception when others then
    v_birth := null;
  end;
  v_age := case when v_birth is null then null else public.age_years(v_birth) end;

  if v_age is not null and v_age < 13 then
    raise exception 'Debes tener al menos 13 años para crear una cuenta. Un apoderado puede crear tu perfil.';
  end if;

  -- El tipo de cuenta privilegiado SOLO lo da una invitación válida
  if v_invite is not null then
    select kind into v_kind from public.invitations
     where code = v_invite and used_at is null and revoked_at is null and expires_at > now();
    if v_kind is null then raise exception 'Código de invitación inválido o vencido'; end if;
    v_type := v_kind;
  elsif v_type in ('adm', 'dt') then
    raise exception 'Se necesita una invitación para registrarse como %', public._invite_label(v_type);
  end if;
  if v_type is not null and v_type not in ('pad', 'jug', 'adm', 'dt') then v_type := null; end if;

  if v_type is not null and v_birth is null then
    raise exception 'La fecha de nacimiento es obligatoria';
  end if;
  if v_type in ('pad', 'adm', 'dt') and v_age < 18 then
    raise exception 'Debes ser mayor de edad para registrarte como %',
      case v_type when 'pad' then 'apoderado' else public._invite_label(v_type) end;
  end if;

  insert into public.profiles (id, full_name, birth_date, account_type, guardian_code)
  values (new.id, v_name, v_birth, v_type, case when v_type = 'pad' then public._new_code('SP') end);

  if v_type = 'jug' then
    insert into public.players (user_id, first_name, last_name, birth_date, created_by, claimed_at)
    values (new.id,
            coalesce(v_first, split_part(v_name, ' ', 1), 'Jugador'),
            coalesce(v_last, nullif(trim(substr(v_name, length(split_part(v_name, ' ', 1)) + 1)), ''), '-'),
            v_birth, new.id, now());
  end if;

  if v_invite is not null then
    perform public._apply_invitation(new.id, v_invite, meta->>'academy_name', meta->>'academy_city');
  end if;
  return new;
end $fn$;

-- 6) Funciones para la app ---------------------------------------------
-- Valida un código antes de registrarse (también sin sesión): no expone datos personales.
create or replace function public.check_invitation(p_code text)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare inv public.invitations%rowtype; v_academy text;
begin
  select * into inv from public.invitations where code = upper(trim(coalesce(p_code, '')));
  if not found then return jsonb_build_object('valid', false, 'reason', 'Código inválido'); end if;
  if inv.revoked_at is not null then return jsonb_build_object('valid', false, 'reason', 'Invitación anulada'); end if;
  if inv.used_at is not null then return jsonb_build_object('valid', false, 'reason', 'Invitación ya usada'); end if;
  if inv.expires_at < now() then return jsonb_build_object('valid', false, 'reason', 'Invitación vencida'); end if;
  select name into v_academy from public.academies where id = inv.academy_id;
  return jsonb_build_object('valid', true, 'kind', inv.kind, 'academy_name', v_academy, 'expires_at', inv.expires_at);
end $fn$;

-- Estado de la cuenta (lo usa la app para decidir si deja pasar al menú)
create or replace function public.my_account()
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare p public.profiles%rowtype; v_age int; v_player uuid; l record; v_gname text;
begin
  select * into p from public.profiles where id = auth.uid();
  if not found then return '{}'::jsonb; end if;
  v_age := public.age_years(p.birth_date);
  select id into v_player from public.players where user_id = auth.uid();
  select g.status, g.guardian_user_id, g.created_at into l from public.guardian_player_links g
   where g.player_id = v_player order by (g.status = 'approved') desc, g.created_at desc limit 1;
  if l.guardian_user_id is not null then select full_name into v_gname from public.profiles where id = l.guardian_user_id; end if;
  return jsonb_build_object(
    'account_type', p.account_type,
    'age', v_age,
    'guardian_code', p.guardian_code,
    'player_id', v_player,
    'guardian_status', coalesce(l.status, 'none'),
    'guardian_name', v_gname,
    'needs_guardian', (p.account_type = 'jug' and coalesce(v_age, 0) < 18
                       and not exists (select 1 from public.guardian_player_links
                                        where player_id = v_player and status = 'approved')));
end $fn$;

-- Código familiar del apoderado (lo crea si aún no existe; solo adultos)
create or replace function public.my_guardian_code()
returns text language plpgsql security definer set search_path = public as $fn$
declare c text;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if coalesce(public.my_age(), 0) < 18 then raise exception 'Solo un adulto puede ser apoderado'; end if;
  select guardian_code into c from public.profiles where id = auth.uid();
  if c is null then
    c := public._new_code('SP');
    update public.profiles set guardian_code = c,
           account_type = coalesce(account_type, 'pad') where id = auth.uid();
  end if;
  return c;
end $fn$;

-- El jugador menor pide vincularse con el código de su apoderado
create or replace function public.request_guardian_link(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_guardian uuid; v_gname text; v_player uuid; v_pname text; v_link uuid; v_pending int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select id, first_name || ' ' || last_name into v_player, v_pname from public.players where user_id = auth.uid();
  if v_player is null then raise exception 'Tu cuenta no es de jugador'; end if;
  select id, full_name into v_guardian, v_gname from public.profiles
   where guardian_code = upper(trim(coalesce(p_code, '')));
  if v_guardian is null then raise exception 'Código de apoderado no encontrado. Revísalo con tu apoderado.'; end if;
  if v_guardian = auth.uid() then raise exception 'No puedes usar tu propio código'; end if;
  if public.age_years((select birth_date from public.profiles where id = v_guardian)) < 18 then
    raise exception 'El apoderado debe ser mayor de edad';
  end if;
  if exists (select 1 from public.guardian_player_links where player_id = v_player and status = 'approved') then
    raise exception 'Ya estás vinculado a un apoderado';
  end if;
  select count(*) into v_pending from public.guardian_player_links
   where player_id = v_player and status = 'pending' and guardian_user_id <> v_guardian;
  if v_pending >= 3 then raise exception 'Tienes demasiadas solicitudes pendientes'; end if;

  insert into public.guardian_player_links (guardian_user_id, player_id, status, is_primary, created_at, decided_at)
  values (v_guardian, v_player, 'pending', true, now(), null)
  on conflict (guardian_user_id, player_id)
  do update set status = 'pending', created_at = now(), decided_at = null
  returning id into v_link;

  perform public._notify(v_guardian, 'guardian_request', 'Solicitud de vinculación',
    v_pname || ' quiere vincularse contigo como su apoderado',
    jsonb_build_object('link_id', v_link, 'player_id', v_player, 'player_name', v_pname));
  return jsonb_build_object('link_id', v_link, 'guardian_name', v_gname);
end $fn$;

-- Solicitudes pendientes para el apoderado
create or replace function public.my_guardian_requests()
returns table (link_id uuid, player_id uuid, player_name text, age int, requested_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select g.id, p.id, p.first_name || ' ' || p.last_name, public.age_years(p.birth_date), g.created_at
  from public.guardian_player_links g
  join public.players p on p.id = g.player_id
  where g.guardian_user_id = auth.uid() and g.status = 'pending'
  order by g.created_at desc
$fn$;

-- El apoderado acepta o rechaza
create or replace function public.respond_guardian_link(p_link uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = public as $fn$
declare g public.guardian_player_links%rowtype; v_user uuid; v_gname text;
begin
  select * into g from public.guardian_player_links where id = p_link for update;
  if not found or g.guardian_user_id <> auth.uid() then raise exception 'Solicitud no encontrada'; end if;
  if g.status <> 'pending' then raise exception 'Esta solicitud ya fue respondida'; end if;
  update public.guardian_player_links
     set status = case when p_accept then 'approved' else 'rejected' end, decided_at = now()
   where id = p_link;
  update public.notifications set read_at = now()
   where user_id = auth.uid() and kind = 'guardian_request' and data->>'link_id' = p_link::text and read_at is null;
  select user_id into v_user from public.players where id = g.player_id;
  select full_name into v_gname from public.profiles where id = auth.uid();
  if v_user is not null then
    perform public._notify(v_user, 'guardian_response',
      case when p_accept then 'Vinculación aceptada' else 'Vinculación rechazada' end,
      coalesce(v_gname, 'Tu apoderado') || case when p_accept then ' aceptó ser tu apoderado' else ' rechazó la solicitud' end,
      jsonb_build_object('link_id', p_link));
  end if;
  perform public.log_audit(case when p_accept then 'guardian_link_approved' else 'guardian_link_rejected' end,
                           'guardian_link', p_link, '{}'::jsonb);
end $fn$;

-- Invitaciones: el superadmin invita administradores; el ADM invita entrenadores a su academia
create or replace function public.create_admin_invitation()
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare c text; v_exp timestamptz;
begin
  if not public.is_platform_admin() then raise exception 'Solo el superadmin puede invitar administradores'; end if;
  c := public._new_code('ADM');
  insert into public.invitations (code, kind, created_by) values (c, 'adm', auth.uid()) returning expires_at into v_exp;
  return jsonb_build_object('code', c, 'kind', 'adm', 'expires_at', v_exp);
end $fn$;

create or replace function public.create_coach_invitation(p_academy uuid)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare c text; v_exp timestamptz; v_name text;
begin
  if not (public.has_academy_role(p_academy, 'adm') or public.is_platform_admin()) then
    raise exception 'Solo el administrador de la academia puede invitar entrenadores';
  end if;
  select name into v_name from public.academies where id = p_academy;
  c := public._new_code('DT');
  insert into public.invitations (code, kind, academy_id, created_by) values (c, 'dt', p_academy, auth.uid())
  returning expires_at into v_exp;
  return jsonb_build_object('code', c, 'kind', 'dt', 'academy_name', v_name, 'expires_at', v_exp);
end $fn$;

create or replace function public.my_invitations()
returns table (id uuid, code text, kind text, academy_name text, created_at timestamptz,
               expires_at timestamptz, status text, used_by_name text)
language sql stable security definer set search_path = public as $fn$
  select i.id, i.code, i.kind, a.name, i.created_at, i.expires_at,
         case when i.revoked_at is not null then 'revoked'
              when i.used_at is not null then 'used'
              when i.expires_at < now() then 'expired' else 'active' end,
         u.full_name
  from public.invitations i
  left join public.academies a on a.id = i.academy_id
  left join public.profiles u on u.id = i.used_by
  where i.created_by = auth.uid()
  order by i.created_at desc
  limit 50
$fn$;

create or replace function public.revoke_invitation(p_id uuid)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  update public.invitations set revoked_at = now()
   where id = p_id and created_by = auth.uid() and used_at is null and revoked_at is null;
  if not found then raise exception 'No se pudo anular (ya usada, anulada o no es tuya)'; end if;
end $fn$;

-- Cuenta ya existente que recibe una invitación (sin volver a registrarse)
create or replace function public.redeem_invitation(p_code text, p_academy_name text default null, p_academy_city text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_academy uuid; v_kind text;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if coalesce(public.my_age(), 0) < 18 then raise exception 'Debes ser mayor de edad'; end if;
  select kind into v_kind from public.invitations where code = upper(trim(coalesce(p_code, '')));
  v_academy := public._apply_invitation(auth.uid(), p_code, p_academy_name, p_academy_city);
  update public.profiles set account_type = coalesce(account_type, v_kind) where id = auth.uid();
  return v_academy;
end $fn$;

-- 7) Permisos -----------------------------------------------------------
revoke execute on function public._random_code(int), public._new_code(text),
  public._notify(uuid, text, text, text, jsonb), public._apply_invitation(uuid, text, text, text)
  from public, anon, authenticated;
revoke execute on function public.check_invitation(text), public.my_account(), public.my_guardian_code(),
  public.request_guardian_link(text), public.my_guardian_requests(), public.respond_guardian_link(uuid, boolean),
  public.create_admin_invitation(), public.create_coach_invitation(uuid), public.my_invitations(),
  public.revoke_invitation(uuid), public.redeem_invitation(text, text, text)
  from public, anon;
grant execute on function public.check_invitation(text) to anon, authenticated;
grant execute on function public.my_account(), public.my_guardian_code(), public.request_guardian_link(text),
  public.my_guardian_requests(), public.respond_guardian_link(uuid, boolean), public.create_admin_invitation(),
  public.create_coach_invitation(uuid), public.my_invitations(), public.revoke_invitation(uuid),
  public.redeem_invitation(text, text, text)
  to authenticated;

-- 8) Realtime: el apoderado ve llegar la solicitud y el menor la respuesta
do $do$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                     and schemaname = 'public' and tablename = 'notifications') then
    alter publication supabase_realtime add table public.notifications;
  end if;
end $do$;

-- 9) Cuentas existentes: apoderados vinculados reciben su código familiar
update public.profiles p set account_type = coalesce(p.account_type, 'pad'),
       guardian_code = coalesce(p.guardian_code, public._new_code('SP'))
 where public.age_years(p.birth_date) >= 18
   and exists (select 1 from public.academy_memberships m where m.user_id = p.id and m.role = 'pad' and m.status = 'active');

commit;
