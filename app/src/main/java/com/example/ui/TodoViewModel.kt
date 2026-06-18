package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Todo
import com.example.data.TodoDatabase
import com.example.data.TodoRepository
import com.example.data.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    private val sharedPrefs = application.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isSessionLoaded = MutableStateFlow(false)
    val isSessionLoaded: StateFlow<Boolean> = _isSessionLoaded.asStateFlow()

    val todos: StateFlow<List<Todo>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getAllTodos(user.email)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        val db = TodoDatabase.getDatabase(application)
        val todoDao = db.todoDao()
        val userDao = db.userDao()
        repository = TodoRepository(todoDao, userDao)

        // Load saved session
        viewModelScope.launch {
            val savedEmail = sharedPrefs.getString("logged_in_email", null)
            if (savedEmail != null) {
                val user = repository.getUserByEmail(savedEmail)
                _currentUser.value = user
            }
            _isSessionLoaded.value = true
        }
    }

    private fun hashPassword(password: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            password
        }
    }

    suspend fun signUp(name: String, email: String, password: CharSequence): String? {
        val cleanEmail = email.trim().lowercase()
        if (name.isBlank()) return "Please enter a valid name."
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return "Please enter a valid email address."
        }
        if (password.length < 6) {
            return "Password must be at least 6 characters."
        }

        val existing = repository.getUserByEmail(cleanEmail)
        if (existing != null) {
            return "An account with this email already exists."
        }

        val passwordHash = hashPassword(password.toString())
        val newUser = User(
            email = cleanEmail,
            name = name.trim(),
            passwordHash = passwordHash
        )
        repository.insertUser(newUser)
        loginUserSession(newUser)
        return null
    }

    suspend fun login(email: String, password: CharSequence): String? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || password.isEmpty()) {
            return "Email and password cannot be empty."
        }

        val user = repository.getUserByEmail(cleanEmail) ?: return "Invalid email or password."
        val hash = hashPassword(password.toString())
        if (user.passwordHash != hash) {
            return "Invalid email or password."
        }

        loginUserSession(user)
        return null
    }

    suspend fun resetPassword(email: String, name: String, newPassword: CharSequence): String? {
        val cleanEmail = email.trim().lowercase()
        val cleanName = name.trim()
        if (cleanEmail.isBlank() || cleanName.isBlank()) {
            return "Email and matching registered name are required."
        }
        if (newPassword.length < 6) {
            return "New password must be at least 6 characters."
        }
        val user = repository.getUserByEmail(cleanEmail) ?: return "No account found with this email."
        if (!user.name.equals(cleanName, ignoreCase = true)) {
            return "Registered name does not match our records for this email."
        }
        val newHash = hashPassword(newPassword.toString())
        val updatedUser = user.copy(passwordHash = newHash)
        repository.updateUser(updatedUser)
        return null
    }

    private fun loginUserSession(user: User) {
        _currentUser.value = user
        sharedPrefs.edit().putString("logged_in_email", user.email).apply()
    }

    fun logout() {
        _currentUser.value = null
        sharedPrefs.edit().remove("logged_in_email").apply()
    }

    fun addTodo(title: String, description: String = "", priority: Int = 1, dueDate: Long? = null, repeatWeekly: Boolean = false) {
        if (title.isBlank()) return
        val currentEmail = _currentUser.value?.email ?: ""
        viewModelScope.launch {
            if (repeatWeekly) {
                val baseTime = dueDate ?: System.currentTimeMillis()
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = baseTime
                }
                for (i in 0 until 7) {
                    repository.insert(
                        Todo(
                            title = title.trim(),
                            description = description.trim(),
                            priority = priority,
                            dueDate = calendar.timeInMillis,
                            userEmail = currentEmail
                        )
                    )
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                }
            } else {
                repository.insert(
                    Todo(
                        title = title.trim(),
                        description = description.trim(),
                        priority = priority,
                        dueDate = dueDate,
                        userEmail = currentEmail
                    )
                )
            }
        }
    }

    fun toggleTodo(todo: Todo) {
        viewModelScope.launch {
            repository.update(todo.copy(isCompleted = !todo.isCompleted))
        }
    }

    fun updateTodo(todo: Todo) {
        viewModelScope.launch {
            repository.update(todo)
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            repository.delete(todo)
        }
    }

    fun toggleArchiveTodo(todo: Todo) {
        viewModelScope.launch {
            repository.update(todo.copy(isArchived = !todo.isArchived))
        }
    }

    fun clearCompleted() {
        val currentEmail = _currentUser.value?.email ?: return
        viewModelScope.launch {
            repository.clearCompleted(currentEmail)
        }
    }

    fun clearArchived() {
        val currentEmail = _currentUser.value?.email ?: return
        viewModelScope.launch {
            repository.clearArchived(currentEmail)
        }
    }

    fun deleteAllTodos() {
        val currentEmail = _currentUser.value?.email ?: return
        viewModelScope.launch {
            repository.deleteAll(currentEmail)
        }
    }
}
