package cl.stockflow.warehouse.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

internal const val SUPABASE_URL = "https://eygbgykglovbivthyqfb.supabase.co"
internal const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV5Z2JneWtnbG92Yml2dGh5cWZiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY3Mzc3MTQsImV4cCI6MjA5MjMxMzcxNH0.gXf4q-i71IsPWuNddCumFcAIV2Lm0O1keVTE6bSoTJY"

val supabaseClient = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    install(GoTrue)
    install(Postgrest)
    install(Realtime)
}
