package com.krushna.divyadrishti.face.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.krushna.divyadrishti.face.database.converter.EmbeddingConverter
import com.krushna.divyadrishti.face.database.dao.FaceDao
import com.krushna.divyadrishti.face.database.entity.PersonEntity

@Database(
    entities = [PersonEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(EmbeddingConverter::class)
abstract class FaceDatabase : RoomDatabase() {

    abstract fun faceDao(): FaceDao

    companion object {

        @Volatile
        private var INSTANCE: FaceDatabase? = null

        fun getInstance(context: Context): FaceDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_database"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}