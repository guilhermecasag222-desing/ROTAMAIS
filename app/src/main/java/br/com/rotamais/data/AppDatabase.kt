package br.com.rotamais.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Entrega::class, RotaHistorico::class], version = 1, exportSchema = false)
@TypeConverters(Conversores::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entregaDao(): EntregaDao
    abstract fun rotaDao(): RotaDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun obter(ctx: Context): AppDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "rotamais.db"
            ).fallbackToDestructiveMigration().build().also { instancia = it }
        }
    }
}
