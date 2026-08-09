import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return json({ error: 'No autorizado' }, 401)
    }

    // Cliente con JWT del ADMIN para verificar identidad y rol
    const supabaseUser = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: authHeader } } }
    )

    const { data: { user }, error: userError } = await supabaseUser.auth.getUser()
    if (userError || !user) {
      return json({ error: 'Token inválido' }, 401)
    }

    // Verificar que el llamante sea ADMIN
    const { data: adminData, error: adminError } = await supabaseUser
      .from('usuarios')
      .select('rol, empresa_id')
      .eq('id', user.id)
      .single()

    if (adminError || !adminData) {
      return json({ error: 'Usuario sin perfil en la empresa' }, 403)
    }
    if (adminData.rol !== 'ADMIN') {
      return json({ error: 'Se requiere rol ADMIN para esta operación' }, 403)
    }

    const empresaId: string = adminData.empresa_id

    const { user_id, password } = await req.json()
    if (!user_id || !password) {
      return json({ error: 'user_id y password son requeridos' }, 400)
    }
    if (password.length < 8) {
      return json({ error: 'La contraseña debe tener mínimo 8 caracteres' }, 400)
    }

    // Cliente con service role para Admin API
    const supabaseAdmin = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
      { auth: { autoRefreshToken: false, persistSession: false } }
    )

    // Verificar que el usuario objetivo pertenece a la misma empresa del ADMIN
    const { data: targetData, error: targetError } = await supabaseAdmin
      .from('usuarios')
      .select('empresa_id')
      .eq('id', user_id)
      .single()

    if (targetError || !targetData) {
      return json({ error: 'Usuario objetivo no encontrado' }, 404)
    }
    if (targetData.empresa_id !== empresaId) {
      return json({ error: 'Usuario no pertenece a tu empresa' }, 403)
    }

    const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(
      user_id,
      { password }
    )

    if (updateError) {
      return json({ error: `Error al restablecer contraseña: ${updateError.message}` }, 500)
    }

    return json({ success: true }, 200)

  } catch (error) {
    return json({ error: `Error interno: ${error.message}` }, 500)
  }
})

function json(body: object, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' }
  })
}
