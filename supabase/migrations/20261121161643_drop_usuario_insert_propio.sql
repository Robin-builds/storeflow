-- Cierra cross-tenant leak: el bootstrap va por registrar_empresa() (SECURITY DEFINER),
-- esta policy sólo permitía insertarse en una empresa ajena.
drop policy if exists "usuario_insert_propio" on public.usuarios;
