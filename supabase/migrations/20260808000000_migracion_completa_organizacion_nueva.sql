    -- ============================================================================
    -- Migración completa de esquema StockFlow
    -- Origen: proyecto "StockFlow" (eygbgykglovbivthyqfb) — organización antigua
    -- Destino: proyecto nuevo (quvkxpjstzssivsaqimu) — organización nueva
    -- Generado: 2026-08-08
    --
    -- Contiene: tablas, índices, RLS + políticas, funciones RPC.
    -- NO incluye datos (ver proceso de migración de datos documentado en
    -- .harness/MIGRACION_SUPABASE.md) ni el event trigger `ensure_rls`
    -- (ya presente por defecto en la organización nueva).
    -- ============================================================================

    -- ----------------------------------------------------------------------------
    -- 1. TABLAS (orden respetando dependencias de FK)
    -- ----------------------------------------------------------------------------

    create table public.empresas (
        id uuid primary key default gen_random_uuid(),
        nombre text not null,
        rut text,
        rubro text,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    create table public.usuarios (
        id uuid primary key,
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        nombre text not null,
        email text not null,
        rol text not null default 'ADMIN',
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    create table public.bodegas (
        id uuid primary key default gen_random_uuid(),
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        nombre text not null,
        ubicacion text,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    create table public.proveedores (
        id uuid primary key default gen_random_uuid(),
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        nombre text not null,
        contacto text,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    create table public.atributo_templates (
        id uuid primary key default gen_random_uuid(),
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        clave text not null,
        etiqueta text not null,
        tipo text not null default 'TEXT',
        obligatorio boolean not null default false,
        orden integer not null default 0,
        created_at timestamptz default now(),
        updated_at timestamptz default now()
    );

    create table public.productos (
        id uuid primary key default gen_random_uuid(),
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        bodega_id uuid not null references public.bodegas(id) on delete cascade,
        nombre text not null,
        descripcion text,
        sku text,
        precio integer not null default 0,
        stock_minimo integer not null default 0,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        es_perecedero boolean not null default false
    );

    create table public.lotes (
        id uuid primary key default gen_random_uuid(),
        producto_id uuid not null references public.productos(id) on delete cascade,
        empresa_id uuid not null references public.empresas(id) on delete cascade,
        numero_lote text,
        fecha_caducidad timestamptz not null,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    create table public.movimientos (
        id uuid primary key default gen_random_uuid(),
        producto_id uuid not null references public.productos(id) on delete cascade,
        tipo text not null check (tipo = any (array['ENTRADA','SALIDA','AJUSTE'])),
        cantidad integer not null,
        nota text,
        synced boolean not null default false,
        synced_at timestamptz,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        lote_id uuid references public.lotes(id) on delete set null,
        usuario_id uuid references public.usuarios(id) on delete set null
    );

    create table public.producto_atributos (
        producto_id uuid not null references public.productos(id) on delete cascade,
        template_id uuid not null references public.atributo_templates(id) on delete cascade,
        valor text not null,
        primary key (producto_id, template_id)
    );

    create table public.sync_queue (
        id uuid primary key default gen_random_uuid(),
        entidad_tipo text not null,
        entidad_id uuid not null,
        operacion text not null check (operacion = any (array['INSERT','UPDATE','DELETE'])),
        payload text not null,
        reintentos integer not null default 0,
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now()
    );

    -- ----------------------------------------------------------------------------
    -- 2. ÍNDICES (los de primary key se crean automáticamente arriba)
    -- ----------------------------------------------------------------------------

    create index idx_bodegas_empresa on public.bodegas using btree (empresa_id);
    create index idx_lotes_empresa on public.lotes using btree (empresa_id);
    create index idx_lotes_producto on public.lotes using btree (producto_id);
    create index idx_movimientos_created on public.movimientos using btree (created_at desc);
    create index idx_movimientos_lote on public.movimientos using btree (lote_id);
    create index idx_movimientos_producto on public.movimientos using btree (producto_id);
    create index idx_movimientos_usuario on public.movimientos using btree (usuario_id);
    create index idx_productos_bodega on public.productos using btree (bodega_id);
    create index idx_productos_empresa on public.productos using btree (empresa_id);
    create index idx_proveedores_empresa on public.proveedores using btree (empresa_id);
    create index idx_usuarios_empresa on public.usuarios using btree (empresa_id);

    -- ----------------------------------------------------------------------------
    -- 3. FUNCIONES RPC
    -- ----------------------------------------------------------------------------

    create or replace function public.get_empresa_id()
    returns uuid
    language sql
    stable security definer
    as $$
        select empresa_id from usuarios where id = auth.uid()
    $$;

    create or replace function public.registrar_empresa(p_nombre text, p_rubro text, p_correo text)
    returns jsonb
    language plpgsql
    security definer
    set search_path to 'public'
    as $$
    DECLARE
      v_empresa_id uuid;
      v_user_id    uuid;
    BEGIN
      v_user_id := auth.uid();
      IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autenticado';
      END IF;

      INSERT INTO empresas (nombre, rubro)
      VALUES (p_nombre, p_rubro)
      RETURNING id INTO v_empresa_id;

      INSERT INTO usuarios (id, empresa_id, nombre, email, rol)
      VALUES (v_user_id, v_empresa_id, split_part(p_correo, '@', 1), p_correo, 'ADMIN');

      INSERT INTO bodegas (empresa_id, nombre)
      VALUES (v_empresa_id, 'Bodega Principal');

      RETURN jsonb_build_object('empresa_id', v_empresa_id::text);
    END;
    $$;

    create or replace function public.unirse_a_empresa(p_empresa_id uuid, p_nombre text, p_email text)
    returns void
    language plpgsql
    security definer
    as $$
    DECLARE
        v_user_id UUID;
    BEGIN
        v_user_id := auth.uid();
        IF v_user_id IS NULL THEN
            RAISE EXCEPTION 'No autenticado';
        END IF;

        IF NOT EXISTS (SELECT 1 FROM public.empresas WHERE id = p_empresa_id) THEN
            RAISE EXCEPTION 'Empresa no encontrada';
        END IF;

        IF EXISTS (SELECT 1 FROM public.usuarios WHERE id = v_user_id) THEN
            RAISE EXCEPTION 'Este usuario ya tiene una empresa asignada';
        END IF;

        INSERT INTO public.usuarios (id, empresa_id, nombre, email, rol)
        VALUES (v_user_id, p_empresa_id, p_nombre, p_email, 'OPERADOR');
    END;
    $$;

    -- ----------------------------------------------------------------------------
    -- 4. RLS + POLÍTICAS
    -- ----------------------------------------------------------------------------

    alter table public.empresas enable row level security;
    alter table public.usuarios enable row level security;
    alter table public.bodegas enable row level security;
    alter table public.proveedores enable row level security;
    alter table public.atributo_templates enable row level security;
    alter table public.productos enable row level security;
    alter table public.lotes enable row level security;
    alter table public.movimientos enable row level security;
    alter table public.producto_atributos enable row level security;
    alter table public.sync_queue enable row level security;

    create policy empresa_propia on public.empresas
        for all using (id = get_empresa_id()) with check (id = get_empresa_id());

    create policy usuarios_empresa on public.usuarios
        for all using (empresa_id = get_empresa_id()) with check (empresa_id = get_empresa_id());

    create policy usuario_insert_propio on public.usuarios
        for insert with check (id = auth.uid());

    create policy bodegas_empresa on public.bodegas
        for all using (empresa_id = get_empresa_id()) with check (empresa_id = get_empresa_id());

    create policy proveedores_empresa on public.proveedores
        for all using (empresa_id = get_empresa_id()) with check (empresa_id = get_empresa_id());

    create policy rls_atributo_templates on public.atributo_templates
        for all using (empresa_id = get_empresa_id());

    create policy productos_empresa on public.productos
        for all using (empresa_id = get_empresa_id()) with check (empresa_id = get_empresa_id());

    create policy lotes_empresa on public.lotes
        for all using (empresa_id = get_empresa_id()) with check (empresa_id = get_empresa_id());

    create policy movimientos_empresa on public.movimientos
        for all using (
            producto_id in (select productos.id from productos where productos.empresa_id = get_empresa_id())
        );

    create policy rls_producto_atributos on public.producto_atributos
        for all using (
            producto_id in (select productos.id from productos where productos.empresa_id = get_empresa_id())
        );

    create policy sync_queue_auth on public.sync_queue
        for all using (auth.role() = 'authenticated');
