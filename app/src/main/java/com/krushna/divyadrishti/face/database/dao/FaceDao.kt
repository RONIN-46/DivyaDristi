package com.krushna.divyadrishti.face.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.krushna.divyadrishti.face.database.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    suspend fun getAllPersonsOnce(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :personId LIMIT 1")
    suspend fun getPersonById(personId: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getPersonByName(name: String): PersonEntity?

    @Query("DELETE FROM persons WHERE id = :personId")
    suspend fun deletePersonById(personId: Long)

    @Query("DELETE FROM persons")
    suspend fun deleteAllPersons()
}

