package cl.storeflow.warehouse.data.remote

import cl.storeflow.warehouse.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

// URL y clave según el tipo de build: producción (debug/release) o Sandbox (build "sandbox").
internal const val SUPABASE_URL = BuildConfig.SUPABASE_URL
internal const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

val supabaseClient = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    // GestorSesion es la única vía de refresh, con Room como fuente (R17).
    install(GoTrue) {
        alwaysAutoRefresh = false
        autoLoadFromStorage = false
    }
    install(Postgrest)
    install(Realtime)
}
