package com.kidlearn.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM lesson_progress WHERE profileId = :profileId")
    fun observeLessons(profileId: Long): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE profileId = :profileId ORDER BY takenAt")
    fun observeQuizAttempts(profileId: Long): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(progress: LessonProgressEntity)

    @Insert
    suspend fun insertQuizAttempt(attempt: QuizAttemptEntity)
}

@Database(
    entities = [ProfileEntity::class, LessonProgressEntity::class, QuizAttemptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KidLearnDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun progress(): ProgressDao

    companion object {
        fun create(context: Context): KidLearnDatabase =
            Room.databaseBuilder(context.applicationContext, KidLearnDatabase::class.java, "kidlearn.db").build()
    }
}
