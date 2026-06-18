package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE userEmail = :userEmail ORDER BY isCompleted ASC, priority DESC, createdAt DESC")
    fun getAllTodos(userEmail: String): Flow<List<Todo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: Todo)

    @Update
    suspend fun updateTodo(todo: Todo)

    @Delete
    suspend fun deleteTodo(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodoById(id: Long)

    @Query("DELETE FROM todos WHERE isCompleted = 1 AND userEmail = :userEmail")
    suspend fun clearCompletedTodos(userEmail: String)

    @Query("DELETE FROM todos WHERE isArchived = 1 AND userEmail = :userEmail")
    suspend fun clearArchivedTodos(userEmail: String)

    @Query("DELETE FROM todos WHERE userEmail = :userEmail")
    suspend fun deleteAllTodos(userEmail: String)
}
