package com.example.data

import kotlinx.coroutines.flow.Flow

class TodoRepository(private val todoDao: TodoDao, private val userDao: UserDao) {
    fun getAllTodos(userEmail: String): Flow<List<Todo>> = todoDao.getAllTodos(userEmail)

    suspend fun insert(todo: Todo): Long {
        return todoDao.insertTodo(todo)
    }

    suspend fun update(todo: Todo) {
        todoDao.updateTodo(todo)
    }

    suspend fun delete(todo: Todo) {
        todoDao.deleteTodo(todo)
    }

    suspend fun deleteById(id: Long) {
        todoDao.deleteTodoById(id)
    }

    suspend fun clearCompleted(userEmail: String) {
        todoDao.clearCompletedTodos(userEmail)
    }

    suspend fun clearArchived(userEmail: String) {
        todoDao.clearArchivedTodos(userEmail)
    }

    suspend fun deleteAll(userEmail: String) {
        todoDao.deleteAllTodos(userEmail)
    }

    // User Operations
    suspend fun getUserByEmail(email: String): User? {
        return userDao.getByEmail(email)
    }

    suspend fun insertUser(user: User): Long {
        return userDao.insertUser(user)
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
    }
}
