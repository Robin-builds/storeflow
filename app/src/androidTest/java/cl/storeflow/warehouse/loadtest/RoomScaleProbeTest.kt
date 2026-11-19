package cl.storeflow.warehouse.loadtest

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cl.storeflow.warehouse.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import org.junit.Test
import java.io.File

/**
 * S3: mide E1-E4 (capa DAO, ver LoadTestSchema.SQL_PARA_EXPLAIN) en cold/warm
 * contra cada escalón del barrido L0-L4 (Escalon.BARRIDO excluye L5 a propósito).
 *
 * Reemplaza a SmokeSeederTest.kt (S2, confirmado en Moto G60 y Samsung S25 FE).
 *
 * Por escenario se abre una conexión NUEVA de Room justo antes de medir "cold"
 * — es la única forma de vaciar el cache de la conexión sin root (ver la
 * limitación documentada en Medidor.medirCold). La medición "warm" reutiliza
 * esa misma conexión ya abierta, así que no mide un cache completamente
 * limpio, sino el estado normal de una app que ya viene navegando.
 */
class RoomScaleProbeTest {

    @Test
    fun barrerEscalones() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val resultados = mutableListOf<Medidor.Resultado>()

        for (escalon in Escalon.BARRIDO) {
            val dbFile = File(ctx.cacheDir, "loadtest-probe-${escalon.nombre}.db")
            borrarDb(dbFile)

            abrirDb(ctx, dbFile).also { db ->
                LoadTestSeeder(db.openHelper.writableDatabase).sembrar(escalon)
                db.close()
            }

            resultados += medirEscenario(ctx, dbFile, escalon, "E1_stock_producto") { db ->
                // calcularStock siempre devuelve un valor (COALESCE): no hay
                // "0 filas" posible para este escenario, así que filas=1 es correcto.
                db.productoDao().calcularStock(PRODUCTO_ID)
                1
            }
            resultados += medirEscenario(ctx, dbFile, escalon, "E2_lista_productos_stock") { db ->
                db.productoDao().observarConStock(BODEGA_ID).first().size
            }
            resultados += medirEscenario(ctx, dbFile, escalon, "E3_stock_bajo") { db ->
                db.productoDao().observarBajoMinimo(BODEGA_ID).first().size
            }
            resultados += medirEscenario(ctx, dbFile, escalon, "E4_historial_completo_sin_paginar") { db ->
                db.movimientoDao().observarPorProducto(PRODUCTO_ID).first().size
            }

            borrarDb(dbFile)
        }

        println(Medidor.Resultado.CABECERA)
        resultados.forEach { println(it.aCsv()) }
    }

    private fun medirEscenario(
        ctx: Context,
        dbFile: File,
        escalon: Escalon,
        nombre: String,
        consulta: suspend (AppDatabase) -> Int
    ): List<Medidor.Resultado> {
        val db = abrirDb(ctx, dbFile)
        val cold = Medidor.medirCold(nombre, escalon.nombre, "dao") { consulta(db) }
        val warm = Medidor.medirWarm(nombre, escalon.nombre, "dao") { consulta(db) }
        db.close()
        return listOf(cold, warm)
    }

    private fun abrirDb(ctx: Context, dbFile: File): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, dbFile.absolutePath)
            .fallbackToDestructiveMigration()
            .build()

    private fun borrarDb(dbFile: File) {
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    companion object {
        // El seeder crea bodegas "bod-0".."bod-(n-1)" en todos los escalones: bod-0 siempre existe.
        private const val BODEGA_ID = "bod-0"
        // El sesgo Pareto del seeder concentra movimientos en los primeros índices de
        // producto: prod-0 es siempre el "producto caliente", el caso que importa medir.
        private const val PRODUCTO_ID = "prod-0"
    }
}
