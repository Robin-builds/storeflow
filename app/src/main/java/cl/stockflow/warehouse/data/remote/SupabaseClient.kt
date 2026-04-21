package cl.stockflow.warehouse.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

private const val SUPABASE_URL = "https://eygbgykglovbivthyqfb.supabase.co"
private const val SUPABASE_ANON_KEY = "sb_publishable_Ugc72hl-1VJgPy03nnlD_Q_irJstp_0"

val supabaseClient = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    install(GoTrue)
    install(Postgrest)
    install(Realtime)
}
