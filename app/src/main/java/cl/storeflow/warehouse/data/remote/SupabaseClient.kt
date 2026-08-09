package cl.storeflow.warehouse.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

internal const val SUPABASE_URL = "https://quvkxpjstzssivsaqimu.supabase.co"
internal const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InF1dmt4cGpzdHpzc2l2c2FxaW11Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYxODY4MDcsImV4cCI6MjEwMTc2MjgwN30.ZyagUxQQbix3imROEiLBY7cd2Pr-EArT9cKhzlMYsX0"

val supabaseClient = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    install(GoTrue)
    install(Postgrest)
    install(Realtime)
}
