package com.example

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import android.view.WindowManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Todo
import com.example.data.User
import com.example.ui.TodoViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Extend layout into window cutout/notch areas to cover the full screen completely
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = 
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        TodoApp()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(viewModel: TodoViewModel = viewModel()) {
  val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
  val isSessionLoaded by viewModel.isSessionLoaded.collectAsStateWithLifecycle()

  if (!isSessionLoaded) {
    Box(
      modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F16)),
      contentAlignment = Alignment.Center
    ) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
    return
  }

  if (currentUser == null) {
    AuthScreen(viewModel)
    return
  }

  val todos by viewModel.todos.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("todo_settings", Context.MODE_PRIVATE) }

  var cleanFocusMode by remember {
    mutableStateOf(prefs.getBoolean("clean_focus_mode", true))
  }
  var showLockedUpcoming by remember {
    mutableStateOf(prefs.getBoolean("show_locked_upcoming", true))
  }
  var playHapticFeedbacks by remember {
    mutableStateOf(prefs.getBoolean("play_haptics", true))
  }

  // State for Navigation Tab
  // 0: Tasks, 1: Calendar, 2: Archive, 3: Settings
  var currentTab by remember { mutableStateOf(0) }

  // State for Search
  var searchQuery by remember { mutableStateOf("") }

  // State for Priority Filter
  // -1: All, 0: Low, 1: Medium, 2: High, 3: Completed, 4: Active
  var selectedFilter by remember { mutableStateOf(-1) }

  // State for Add Task Sheet
  var showAddSheet by remember { mutableStateOf(false) }
  var prefilledDate by remember { mutableStateOf<Long?>(null) }

  // Filter out archived tasks for main task and calendar tabs
  val activeTodos = remember(todos) { todos.filter { !it.isArchived } }

  // Only show tasks scheduled/due today (or created today if no due date)
  val todayTodos = remember(activeTodos, cleanFocusMode, showLockedUpcoming) {
    if (cleanFocusMode) {
      activeTodos.filter { todo ->
        val isFuture = isFutureDay(todo.dueDate)
        if (isFuture) {
          showLockedUpcoming
        } else {
          if (todo.dueDate != null) {
            isToday(todo.dueDate)
          } else {
            isToday(todo.createdAt)
          }
        }
      }
    } else {
      activeTodos
    }
  }

  // Filtered Todos
  val filteredTodos = remember(todayTodos, searchQuery, selectedFilter) {
    todayTodos.filter { todo ->
      val matchesSearch = todo.title.contains(searchQuery, ignoreCase = true) ||
          todo.description.contains(searchQuery, ignoreCase = true)

      val matchesFilter = when (selectedFilter) {
        -1 -> true
        0 -> todo.priority == 0 && !todo.isCompleted
        1 -> todo.priority == 1 && !todo.isCompleted
        2 -> todo.priority == 2 && !todo.isCompleted
        3 -> todo.isCompleted
        4 -> !todo.isCompleted
        else -> true
      }

      matchesSearch && matchesFilter
    }
  }

  // Stats on today's active tasks (which disappear next day)
  val totalCount = todayTodos.size
  val completedCount = todayTodos.count { it.isCompleted }
  val completionProgress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f

  Scaffold(
    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { 
          prefilledDate = null
          showAddSheet = true 
        },
        containerColor = Color(0xFFD0BCFF), // Light purple FAB container in Bento theme
        contentColor = Color(0xFF21005D),   // Dark purple icon color
        shape = RoundedCornerShape(16.dp),  // Bento round rectangle FAB
        modifier = Modifier.testTag("add_task_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(28.dp))
      }
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        modifier = Modifier.height(80.dp)
      ) {
        NavigationBarItem(
          selected = currentTab == 0,
          onClick = { currentTab = 0 },
          icon = {
            if (currentTab == 0) {
              Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Tasks",
                  tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            } else {
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Tasks",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          label = { Text("Tasks", fontWeight = if (currentTab == 0) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp, color = if (currentTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        NavigationBarItem(
          selected = currentTab == 1,
          onClick = { currentTab = 1 },
          icon = {
            if (currentTab == 1) {
              Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CalendarMonth,
                  contentDescription = "Calendar",
                  tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            } else {
              Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Calendar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          label = { Text("Calendar", fontWeight = if (currentTab == 1) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp, color = if (currentTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        NavigationBarItem(
          selected = currentTab == 2,
          onClick = { currentTab = 2 },
          icon = {
            if (currentTab == 2) {
              Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.BarChart,
                  contentDescription = "Progress",
                  tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            } else {
              Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "Progress",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          label = { Text("Progress", fontWeight = if (currentTab == 2) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp, color = if (currentTab == 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        NavigationBarItem(
          selected = currentTab == 3,
          onClick = { currentTab = 3 },
          icon = {
            if (currentTab == 3) {
              Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = "Settings",
                  tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            } else {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          label = { Text("Settings", fontWeight = if (currentTab == 3) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp, color = if (currentTab == 3) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) }
        )
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(innerPadding)
    ) {
      if (currentTab == 0) {
        // Screen Header with Date and Progress Bento
        HeaderSection(
          completedCount = completedCount,
          totalCount = totalCount,
          progress = completionProgress,
          onClearCompleted = { viewModel.clearCompleted() }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Main Bento Task List representation (super rounded white board layout with thin border)
        Card(
          modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .padding(horizontal = 16.dp, vertical = 8.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          shape = RoundedCornerShape(28.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
          Column(modifier = Modifier.fillMaxSize()) {
            // Inside-Bento Title row of list
            Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 18.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              val titleLabel = when (selectedFilter) {
                -1 -> "All Tasks"
                4 -> "Active Tasks"
                3 -> "Completed Tasks"
                2 -> "High Priority"
                1 -> "Medium Priority"
                0 -> "Low Priority"
                else -> "Tasks"
              }
              Text(
                text = titleLabel.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                letterSpacing = 1.sp
              )
              Icon(
                imageVector = Icons.Default.ListAlt,
                contentDescription = "List Option",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
              )
            }

            // Search & Filter Panel contained neatly inside Bento Card
            SearchAndFilterSection(
              searchQuery = searchQuery,
              onSearchQueryChanged = { searchQuery = it },
              selectedFilter = selectedFilter,
              onFilterSelected = { selectedFilter = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // List of Tasks or Empty State
            if (filteredTodos.isEmpty()) {
              EmptyStateSection(
                searchActive = searchQuery.isNotEmpty(),
                filterActive = selectedFilter != -1
              )
            } else {
              LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(filteredTodos, key = { it.id }) { todo ->
                  TodoItemCard(
                    todo = todo,
                    onToggle = { viewModel.toggleTodo(todo) },
                    onDelete = { viewModel.deleteTodo(todo) },
                    onArchive = { viewModel.toggleArchiveTodo(todo) }
                  )
                }
              }
            }
          }
        }
      } else if (currentTab == 1) {
        // Render beautiful Bento Calendar Page!
        BentoCalendarScreen(
          todos = activeTodos,
          viewModel = viewModel,
          onAddTaskWithDate = { date ->
            prefilledDate = date
            showAddSheet = true
          }
        )
      } else if (currentTab == 2) {
        BentoProgressTrackerScreen(
          todos = todos,
          viewModel = viewModel
        )
      } else {
        BentoSettingsScreen(
          user = currentUser,
          onLogout = { viewModel.logout() },
          cleanFocusMode = cleanFocusMode,
          onCleanFocusModeChanged = { enabled ->
            cleanFocusMode = enabled
            prefs.edit().putBoolean("clean_focus_mode", enabled).apply()
          },
          showLockedUpcoming = showLockedUpcoming,
          onShowLockedUpcomingChanged = { enabled ->
            showLockedUpcoming = enabled
            prefs.edit().putBoolean("show_locked_upcoming", enabled).apply()
          },
          playHapticFeedbacks = playHapticFeedbacks,
          onPlayHapticFeedbacksChanged = { enabled ->
            playHapticFeedbacks = enabled
            prefs.edit().putBoolean("play_haptics", enabled).apply()
          },
          todos = todos,
          viewModel = viewModel
        )
      }
    }

    // Modal Bottom Sheet for creation
    if (showAddSheet) {
      AddTaskBottomSheet(
        initialDueDate = prefilledDate,
        onDismiss = { showAddSheet = false },
        onAdd = { title, desc, priority, date, repeatWeekly ->
          viewModel.addTodo(title, desc, priority, date, repeatWeekly)
          showAddSheet = false
        }
      )
    }
  }
}

@Composable
fun HeaderSection(
    completedCount: Int,
    totalCount: Int,
    progress: Float,
    onClearCompleted: () -> Unit
) {
  Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Elegant Bento Title & Subtitle with Date
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Daily Focus",
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
          ),
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = getTodayDateString(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
      }

      if (completedCount > 0) {
        TextButton(
          onClick = onClearCompleted,
          colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
          ),
          modifier = Modifier.testTag("clear_completed_button")
        ) {
          Icon(
            imageVector = Icons.Default.DeleteSweep,
            contentDescription = "Clear Completed",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Clear Done",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Progress Bento Card (Blue Container)
    Card(
      modifier = Modifier
          .fillMaxWidth()
          .height(130.dp),
      colors = CardDefaults.cardColors(
        containerColor = BentoCardBlue
      ),
      shape = RoundedCornerShape(28.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
                .background(Color(0xFF001D36), shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Text(
              text = "ACTIVE",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              letterSpacing = 1.sp
            )
          }
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Spark Icon",
            tint = Color(0xFF001D36),
            modifier = Modifier.size(20.dp)
          )
        }

        Column {
          val percent = (progress * 100).toInt()
          Text(
            text = "$percent%",
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            color = Color(0xFF001D36),
            lineHeight = 40.sp
          )
          Text(
            text = "$completedCount of $totalCount Tasks Completed",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF001D36).copy(alpha = 0.8f)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Stats Grid Row (Pink Card & Purple Card)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Pink Bento card
      Card(
        modifier = Modifier
            .weight(1f)
            .height(98.dp),
        colors = CardDefaults.cardColors(
          containerColor = BentoCardPink
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "Streak Icon",
            tint = BentoTextPink,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.height(4.dp))
          // Dynamic streak based on completed count!
          val streakValue = if (completedCount > 0) completedCount + 2 else completedCount
          Text(
            text = "$streakValue",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BentoTextPink
          )
          Text(
            text = "STREAK",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = BentoTextPink.copy(alpha = 0.7f),
            letterSpacing = 0.5.sp
          )
        }
      }

      // Purple Bento card
      Card(
        modifier = Modifier
            .weight(1f)
            .height(98.dp),
        colors = CardDefaults.cardColors(
          containerColor = BentoCardPurple
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Calendar Icon",
            tint = BentoTextPurple,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = getShortMonthString().uppercase(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BentoTextPurple
          )
          Text(
            text = "MONTHLY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = BentoTextPurple.copy(alpha = 0.7f),
            letterSpacing = 0.5.sp
          )
        }
      }
    }
  }
}

data class FilterItem(
  val filterId: Int,
  val label: String
)

@Composable
fun SearchAndFilterSection(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit
) {
  Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
  ) {
    // Elegant Borderless Search Input with soft grey/violet background tint
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChanged,
      placeholder = { 
        Text(
          text = "Search tasks...", 
          fontSize = 13.sp, 
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ) 
      },
      leadingIcon = { 
        Icon(
          imageVector = Icons.Default.Search, 
          contentDescription = "Search icon", 
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.size(18.dp)
        ) 
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(
            onClick = { onSearchQueryChanged("") },
            modifier = Modifier.size(28.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close, 
              contentDescription = "Clear search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(16.dp)
            )
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
          .fillMaxWidth()
          .testTag("search_input"),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        unfocusedBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent
      )
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Modern row of clean minimalist filter pills (completely flat, no borders, no icons)
    Row(
      modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val filters = listOf(
        FilterItem(-1, "All"),
        FilterItem(4, "Active"),
        FilterItem(3, "Completed"),
        FilterItem(2, "High"),
        FilterItem(1, "Medium"),
        FilterItem(0, "Low")
      )

      filters.forEach { filterItem ->
        val isSelected = selectedFilter == filterItem.filterId
        
        // Define beautiful modern bento pill colors
        val chipBackground = if (isSelected) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        }

        val chipTextColor = if (isSelected) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        }

        Box(
          modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(chipBackground)
              .clickable { onFilterSelected(filterItem.filterId) }
              .padding(horizontal = 14.dp, vertical = 7.dp)
              .testTag("filter_chip_${filterItem.filterId}"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = filterItem.label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = chipTextColor
          )
        }
      }
    }
  }
}

@Composable
fun EmptyStateSection(searchActive: Boolean, filterActive: Boolean) {
  Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp)
        .testTag("empty_state_section"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(vertical = 16.dp)
    ) {
      Card(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_empty_state_1781771225769),
          contentDescription = "No tasks illustration",
          modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(4f / 3f)
              .clip(RoundedCornerShape(20.dp)),
          contentScale = ContentScale.Crop
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      val titleText = if (searchActive || filterActive) "No items match query" else "All items completed"
      val bodyText = if (searchActive || filterActive) {
        "Try checking your keywords or filter choices to find this task."
      } else {
        "Enjoy the calm! Tap the '+' button below to start organizing your day."
      }

      Text(
        text = titleText,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = bodyText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 20.dp)
      )
    }
  }
}

@Composable
fun TodoItemCard(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
  var isExpanded by remember { mutableStateOf(false) }
  val isLocked = isFutureDay(todo.dueDate)

  val priorityColor = when (todo.priority) {
    2 -> PriorityHigh
    1 -> PriorityMedium
    else -> PriorityLow
  }

  val priorityLabel = when (todo.priority) {
    2 -> "High"
    1 -> "Medium"
    else -> "Low"
  }

  Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { isExpanded = !isExpanded }
        .testTag("task_card_${todo.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = when {
        isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        todo.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
      }
    ),
    elevation = CardDefaults.cardElevation(
      defaultElevation = if (todo.isCompleted || isLocked) 0.dp else 2.dp
    ),
    border = if (isExpanded) {
      androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else null
  ) {
    Row(
      modifier = Modifier
          .fillMaxWidth()
          .height(IntrinsicSize.Min)
          .then(if (isLocked) Modifier.alpha(0.65f) else Modifier)
    ) {
      // Left vertical line representing priority
      Box(
        modifier = Modifier
            .width(6.dp)
            .fillMaxHeight()
            .background(priorityColor)
      )

      Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Circle radio-checkbox toggle
        IconButton(
          onClick = { if (!isLocked) onToggle() },
          enabled = !isLocked,
          modifier = Modifier
              .size(44.dp)
              .testTag("task_check_${todo.id}")
        ) {
          Icon(
            imageVector = when {
              isLocked -> Icons.Default.Lock
              todo.isCompleted -> Icons.Default.CheckCircle
              else -> Icons.Outlined.Circle
            },
            contentDescription = if (isLocked) "Task is locked until its scheduled day" else "Toggle completion",
            tint = when {
              isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
              todo.isCompleted -> MaterialTheme.colorScheme.primary
              else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Info Column
        Column(
          modifier = Modifier
              .weight(1f)
              .padding(vertical = 2.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = todo.title,
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null
              ),
              color = if (todo.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
              } else {
                MaterialTheme.colorScheme.onSurface
              },
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Priority badge
            Box(
              modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(priorityColor.copy(alpha = 0.15f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = priorityLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = priorityColor
              )
            }

            if (isLocked) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "Upcoming",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer
                )
              }
            }
          }

          if (todo.description.isNotBlank() && (!todo.isCompleted || isExpanded)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = todo.description,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
              maxLines = if (isExpanded) Int.MAX_VALUE else 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          if (todo.dueDate != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Due Date",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Due: " + formatLongDateTime(todo.dueDate),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Archive / Unarchive Button
        IconButton(
          onClick = onArchive,
          modifier = Modifier
              .size(44.dp)
              .testTag("task_archive_${todo.id}")
        ) {
          Icon(
            imageVector = if (todo.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
            contentDescription = if (todo.isArchived) "Unarchive task" else "Archive task",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Deletion Button
        IconButton(
          onClick = onDelete,
          modifier = Modifier
              .size(44.dp)
              .testTag("task_delete_${todo.id}")
        ) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "Delete task",
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskBottomSheet(
    initialDueDate: Long? = null,
    onDismiss: () -> Unit,
    onAdd: (title: String, description: String, priority: Int, dueDate: Long?, repeatWeekly: Boolean) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var priority by remember { mutableIntStateOf(1) } // 0: Low, 1: Medium, 2: High
  var dueDate by remember { mutableStateOf<Long?>(initialDueDate) }
  var repeatWeekly by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    onResult = { _ -> }
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface,
    contentWindowInsets = { WindowInsets.navigationBars }
  ) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(bottom = 36.dp)
    ) {
      Text(
        text = "Create Task",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Title
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Title *") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_title_input"),
        shape = RoundedCornerShape(12.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Description
      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Details (optional)") },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_description_input"),
        shape = RoundedCornerShape(12.dp),
        minLines = 2,
        maxLines = 3
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Priority
      Text(
        text = "Priority",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val priorities = listOf(
          Triple(0, "Low" to Icons.Default.SignalCellularAlt, PriorityLow),
          Triple(1, "Medium" to Icons.Default.AccessTime, PriorityMedium),
          Triple(2, "High" to Icons.Default.Whatshot, PriorityHigh)
        )

        priorities.forEach { (level, nameAndIcon, color) ->
          val (name, icon) = nameAndIcon
          val isSelected = priority == level
          val outlineColor = if (isSelected) color else color.copy(alpha = 0.9f)
          val bgColor = if (isSelected) color else color.copy(alpha = 0.08f)

          Card(
            modifier = Modifier
                .weight(1f)
                .clickable { priority = level }
                .testTag("priority_selection_$level"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, outlineColor),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
          ) {
            Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 11.dp, horizontal = 4.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = icon,
                contentDescription = "$name Priority Icon",
                tint = if (isSelected) Color.White else color,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else color
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Date Selection
      Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
              val calendar = Calendar.getInstance()
              if (dueDate != null) {
                calendar.timeInMillis = dueDate!!
              }
              android.app.DatePickerDialog(
                context,
                { _, year, month, day ->
                  val selectedCal = Calendar.getInstance()
                  selectedCal.set(Calendar.YEAR, year)
                  selectedCal.set(Calendar.MONTH, month)
                  selectedCal.set(Calendar.DAY_OF_MONTH, day)
                  
                  val currentHour = selectedCal.get(Calendar.HOUR)
                  val currentMinute = selectedCal.get(Calendar.MINUTE)
                  
                  android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                      selectedCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                      selectedCal.set(Calendar.MINUTE, minute)
                      selectedCal.set(Calendar.SECOND, 0)
                      selectedCal.set(Calendar.MILLISECOND, 0)
                      dueDate = selectedCal.timeInMillis
                      
                      // Request notification permission on Android 13+ contextually
                      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                          val permission = android.Manifest.permission.POST_NOTIFICATIONS
                          if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                              permissionLauncher.launch(permission)
                          }
                      }
                    },
                    currentHour,
                    currentMinute,
                    false
                  ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
              ).show()
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Select Date",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Set Due Date & Time",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = if (dueDate != null) formatLongDateTime(dueDate!!) else "No due date set",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (dueDate != null) {
          IconButton(
            onClick = { dueDate = null },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Clear date selection",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp)
            )
          }
        } else {
          Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Pick date option",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Repeat Option
      Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = Icons.Default.Repeat,
            contentDescription = "Repeat Task",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Repeat Daily for 1 Week",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Duplicates this task daily for 7 days",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Switch(
          checked = repeatWeekly,
          onCheckedChange = { repeatWeekly = it },
          modifier = Modifier.testTag("repeat_weekly_switch")
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Action Rows
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Text("Cancel", fontWeight = FontWeight.Bold)
        }

        Button(
          onClick = { if (title.isNotBlank()) onAdd(title, description, priority, dueDate, repeatWeekly) },
          enabled = title.isNotBlank(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
              .weight(1.5f)
              .testTag("save_task_button")
        ) {
          Text("Add Task", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

// Simple local helper to prevent any type conversion bugs or unresolved issues on float sizes!
private fun Int.getM3IconSize(): androidx.compose.ui.unit.Dp = this.dp

fun formatLongDate(timestamp: Long): String {
  val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

fun formatLongDateTime(timestamp: Long): String {
  val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

fun getTodayDateString(): String {
  val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
  return sdf.format(Date())
}

fun getShortMonthString(): String {
  val sdf = SimpleDateFormat("MMM", Locale.getDefault())
  return sdf.format(Date())
}

// Calendar Calculation Helpers
fun getDaysInMonthGrid(calendar: Calendar): List<Calendar> {
    val gridList = mutableListOf<Calendar>()
    val tempCal = calendar.clone() as Calendar
    tempCal.set(Calendar.DAY_OF_MONTH, 1)
    
    // Day of week for the 1st of the month: 1 = Sunday, 2 = Monday, etc.
    val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
    val daysBefore = firstDayOfWeek - 1
    tempCal.add(Calendar.DAY_OF_MONTH, -daysBefore)
    
    // Draw exactly 42 cells (6 rows * 7 columns) for a complete balanced grid representation
    for (i in 0 until 42) {
        gridList.add(tempCal.clone() as Calendar)
        tempCal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return gridList
}

fun isSameDay(timeMillis: Long, calendar: Calendar): Boolean {
    if (timeMillis <= 0) return false
    val t = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return t.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
           t.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
}

fun getMonthYearString(calendar: Calendar): String {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return sdf.format(calendar.time)
}

fun getReadableDateOf(calendar: Calendar): String {
    val sdf = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
    return sdf.format(calendar.time)
}

@Composable
fun BentoCalendarScreen(
    todos: List<Todo>,
    viewModel: TodoViewModel,
    onAddTaskWithDate: (Long) -> Unit
) {
    var currentMonthOfCal by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateOfCal by remember { mutableStateOf(Calendar.getInstance()) }
    
    // Calculate calendar days
    val daysGrid = remember(currentMonthOfCal) {
        getDaysInMonthGrid(currentMonthOfCal)
    }
    
    // Find todos scheduled on the selected date
    val selectedDateTodos = remember(todos, selectedDateOfCal) {
        todos.filter { isSameDay(it.dueDate ?: 0, selectedDateOfCal) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Grid Row (Pink Card & Purple Card) to maintain cohesive Bento spacing
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BentoCardPink
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .background(BentoTextPink.copy(alpha = 0.15f), shape = CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CALENDAR FEED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPink,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Time Box Tasks",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPink,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "Tap any day to allocate and schedule focus items.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = BentoTextPink.copy(alpha = 0.8f)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Calendar Illustration Icon",
                        tint = BentoTextPink.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        // Month Grid Controller Card (Bento Card - Surface White)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Header controls of monthly grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getMonthYearString(currentMonthOfCal).uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Left Arrow Bento button
                            IconButton(
                                onClick = {
                                    val newCal = currentMonthOfCal.clone() as Calendar
                                    newCal.add(Calendar.MONTH, -1)
                                    currentMonthOfCal = newCal
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Right Arrow Bento button
                            IconButton(
                                onClick = {
                                    val newCal = currentMonthOfCal.clone() as Calendar
                                    newCal.add(Calendar.MONTH, 1)
                                    currentMonthOfCal = newCal
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Month",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Week headers S M T W T F S
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val weeks = listOf("S", "M", "T", "W", "T", "F", "S")
                        weeks.forEach { week ->
                            Text(
                                text = week,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 6 x 7 calendar grid of bento cells
                    val chunkedWeeks = daysGrid.chunked(7)
                    chunkedWeeks.forEach { weekDays ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            weekDays.forEach { dayCalendar ->
                                val isSelected = isSameDay(dayCalendar.timeInMillis, selectedDateOfCal)
                                val isCurrentMonth = dayCalendar.get(Calendar.MONTH) == currentMonthOfCal.get(Calendar.MONTH)
                                val isToday = isSameDay(System.currentTimeMillis(), dayCalendar)
                                
                                val dayTodos = remember(todos, dayCalendar) {
                                    todos.filter { isSameDay(it.dueDate ?: 0, dayCalendar) }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    isToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                                color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedDateOfCal = dayCalendar.clone() as Calendar
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "${dayCalendar.get(Calendar.DAY_OF_MONTH)}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    isToday -> MaterialTheme.colorScheme.primary
                                                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            
                                            // Tiny dots representing priorities of tasks for that day
                                            if (dayTodos.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Max 3 dots to prevent overflow
                                                    val displayDots = dayTodos.take(3)
                                                    displayDots.forEach { todo ->
                                                        val dotColor = when (todo.priority) {
                                                            2 -> PriorityHigh
                                                            1 -> PriorityMedium
                                                            else -> PriorityLow
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .background(dotColor, shape = CircleShape)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Selected Date Tasks Bento Card (Surface White container)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SCHEDULE FOR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = getReadableDateOf(selectedDateOfCal),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // Icon button to add task pre-selected on this date
                        IconButton(
                            onClick = { onAddTaskWithDate(selectedDateOfCal.timeInMillis) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Task for Date",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    if (selectedDateTodos.isEmpty()) {
                        // Minimalist Bento Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = "No tasks",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No tasks scheduled for this day.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onAddTaskWithDate(selectedDateOfCal.timeInMillis) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text("Add Task", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Render date's tasks
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            selectedDateTodos.forEach { todo ->
                                TodoItemCard(
                                    todo = todo,
                                    onToggle = { viewModel.toggleTodo(todo) },
                                    onDelete = { viewModel.deleteTodo(todo) },
                                    onArchive = { viewModel.toggleArchiveTodo(todo) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DayProgress(
    val dayOfWeek: String, // e.g. "Mon"
    val dateString: String, // e.g. "18 Jun"
    val timestampStart: Long,
    val completedCount: Int,
    val totalCount: Int,
    val completionRate: Float,
    val dayTodos: List<Todo>
)

fun calculateWeeklyProgress(todos: List<Todo>): List<DayProgress> {
    val list = mutableListOf<DayProgress>()
    val dateFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
    val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()) // "Mon", "Tue" etc.
    
    val cal = java.util.Calendar.getInstance()
    // Normalize to start of today
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    
    // Go 6 days back (so today is index 6, making 7 days total)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
    
    for (i in 0 until 7) {
        val dayStart = cal.timeInMillis
        val tempCal = java.util.Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val dayEnd = tempCal.timeInMillis
        
        // Find todos for this day
        val dayTodos = todos.filter { todo ->
            val refTime = todo.dueDate ?: todo.createdAt
            refTime >= dayStart && refTime < dayEnd
        }
        
        val completed = dayTodos.count { it.isCompleted }
        val total = dayTodos.size
        val rate = if (total > 0) completed.toFloat() / total else 0f
        
        list.add(
            DayProgress(
                dayOfWeek = dayFormat.format(cal.time),
                dateString = dateFormat.format(cal.time),
                timestampStart = dayStart,
                completedCount = completed,
                totalCount = total,
                completionRate = rate,
                dayTodos = dayTodos
            )
        )
        
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1) // Move to next day
    }
    return list
}

fun calculateWeeklyStreak(weeklyProgress: List<DayProgress>): Int {
    var streak = 0
    // Traverse backwards from today (last day in weeklyProgress is index 6)
    for (day in weeklyProgress.reversed()) {
        if (day.totalCount > 0) {
            if (day.completedCount == day.totalCount) {
                streak++
            } else {
                break
            }
        } else {
            // An empty day doesn't break the streak if we are looking at today,
            // or if we already have a running streak.
        }
    }
    return streak
}

@Composable
fun BentoProgressTrackerScreen(
    todos: List<Todo>,
    viewModel: TodoViewModel
) {
    val weeklyProgress = remember(todos) {
        calculateWeeklyProgress(todos)
    }

    // Default selected day is today (the last index in our 7-day progress list)
    var selectedDayIndex by remember(weeklyProgress) {
        mutableStateOf(6)
    }

    val selectedDay = weeklyProgress.getOrNull(selectedDayIndex) ?: weeklyProgress.lastOrNull()

    val totalWeeklyTasks = remember(weeklyProgress) {
        weeklyProgress.sumOf { it.totalCount }
    }
    val totalWeeklyCompleted = remember(weeklyProgress) {
        weeklyProgress.sumOf { it.completedCount }
    }
    val overallRate = if (totalWeeklyTasks > 0) {
        (totalWeeklyCompleted.toFloat() / totalWeeklyTasks * 100).toInt()
    } else {
        0
    }
    val streak = remember(weeklyProgress) {
        calculateWeeklyStreak(weeklyProgress)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Stats Bento Card (Purple Theme)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BentoCardPurple
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .background(BentoTextPurple.copy(alpha = 0.15f), shape = CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "WEEKLY ANALYTICS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPurple,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Streak: $streak Day${if (streak != 1) "s" else ""}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPurple,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "$overallRate% weekly completion ($totalWeeklyCompleted of $totalWeeklyTasks tasks fully achieved)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = BentoTextPurple.copy(alpha = 0.8f)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Trending Up icon",
                        tint = BentoTextPurple.copy(alpha = 0.25f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        // Beautiful Interactive Interactive 7-Day Capsule Bar Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-DAY PROGRESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Tap a bar to inspect that day's feed",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Chart Option",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weeklyProgress.forEachIndexed { index, progress ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedDayIndex = index }
                                    .padding(vertical = 8.dp)
                                    .background(if (selectedDayIndex == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                            ) {
                                Text(
                                    text = "${progress.completedCount}/${progress.totalCount}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (progress.totalCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    if (progress.totalCount > 0 && progress.completionRate > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(progress.completionRate)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (progress.completionRate == 1.0f) {
                                                        Color(0xFF4CAF50)
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = progress.dayOfWeek,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedDayIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = progress.dateString,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detailed Task Feed Card for selected day
        if (selectedDay != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "TASKS FOR ${selectedDay.dayOfWeek.uppercase()} (${selectedDay.dateString.uppercase()})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selectedDay.dayTodos.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "No tasks",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No tasks listed for this day.",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (todo in selectedDay.dayTodos) {
                                    TodoItemCard(
                                        todo = todo,
                                        onToggle = { viewModel.toggleTodo(todo) },
                                        onDelete = { viewModel.deleteTodo(todo) },
                                        onArchive = { viewModel.toggleArchiveTodo(todo) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to check if a timestamp is on today's calendar date
fun isToday(timestamp: Long): Boolean {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
           target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

// Helper function to check if a target date is in the future relative to today's date
fun isFutureDay(dueDate: Long?): Boolean {
    if (dueDate == null) return false
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val due = Calendar.getInstance().apply {
        timeInMillis = dueDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return due.after(today)
}

@Composable
fun BentoSettingsScreen(
    user: User?,
    onLogout: () -> Unit,
    cleanFocusMode: Boolean,
    onCleanFocusModeChanged: (Boolean) -> Unit,
    showLockedUpcoming: Boolean,
    onShowLockedUpcomingChanged: (Boolean) -> Unit,
    playHapticFeedbacks: Boolean,
    onPlayHapticFeedbacksChanged: (Boolean) -> Unit,
    todos: List<Todo>,
    viewModel: TodoViewModel
) {
    var showResetDialog by remember { mutableStateOf(false) }

    val totalCreated = todos.size
    val totalCompleted = todos.count { it.isCompleted }
    val completionRate = if (totalCreated > 0) (totalCompleted.toFloat() / totalCreated * 100).toInt() else 0

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                     text = "Nuclear Reset All Data?",
                     fontWeight = FontWeight.Bold,
                     fontSize = 18.sp
                )
            },
            text = {
                Text(
                     text = "This action is permanent and will completely purge your local database, wiping all active, recurring, and archived focus tasks. Are you absolutely sure?",
                     fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllTodos()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All Data", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Bento Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BentoCardPink),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .background(BentoTextPink.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SYSTEM PREFERENCES",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPink,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Daily Focus Console",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPink,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Tailor layout rules, cleanup old lists, and configure deep work focus triggers.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = BentoTextPink.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Preference Gears Icon",
                        tint = BentoTextPink.copy(alpha = 0.25f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }
        }

        // Section: Active User Session
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (user?.name?.firstOrNull() ?: 'U').uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = user?.name ?: "Unknown User",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = user?.email ?: "No email stored",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log Out Icon",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: UI Rules & Layout
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "BEHAVIOR & RULES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Clean Focus Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Midnight Clean Wipeout",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-hide old tasks. Main page only shows actions scheduled/due today to start every day fresh.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = cleanFocusMode,
                            onCheckedChange = onCleanFocusModeChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = BentoBorderColor.copy(alpha = 0.4f))

                    // 2. Upcoming Locked Tasks Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preview Future Locked Goals",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Provide anticipation by showing future recurring actions on the main list with a Lock indicator.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = showLockedUpcoming,
                            onCheckedChange = onShowLockedUpcomingChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = BentoBorderColor.copy(alpha = 0.4f))

                    // 3. Completions Sound & Haptic Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Haptic Tick Feedback",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Engage tactile physical pulses on achieving completion to celebrate progress.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = playHapticFeedbacks,
                            onCheckedChange = onPlayHapticFeedbacksChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // Section: Data Cleanups
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorderColor.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "DATA PORTABILITY & PURGING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Clear Completed Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                     ) {
                         Column(modifier = Modifier.weight(1f)) {
                             Text(
                                 text = "Clear Completed",
                                 fontSize = 14.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = MaterialTheme.colorScheme.onSurface
                             )
                             Text(
                                 text = "Permanently purge already accomplished actions from databases.",
                                 fontSize = 11.sp,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
                         Button(
                             onClick = { viewModel.clearCompleted() },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                 contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                             ),
                             shape = RoundedCornerShape(12.dp)
                         ) {
                             Text("Purge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                         }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BentoBorderColor.copy(alpha = 0.4f))

                    // Clear Archived
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Empty Archived Tasks",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Obliterate all files tucked away in the archives safely.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.clearArchived() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Empty", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BentoBorderColor.copy(alpha = 0.4f))

                    // Nuclear Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nuclear Wipe Database",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Clear absolutely everything including statistics to start anew.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Diagnostics & Metrics Info Bento
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BentoCardPurple),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "METRICS & SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPurple,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$totalCreated",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPurple,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "Purged & Active Tasks",
                                fontSize = 11.sp,
                                color = BentoTextPurple.copy(alpha = 0.7f)
                            )
                        }

                        Column {
                            Text(
                                text = "$totalCompleted",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPurple,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "Total Completed",
                                fontSize = 11.sp,
                                color = BentoTextPurple.copy(alpha = 0.7f)
                            )
                        }

                        Column {
                            Text(
                                text = "$completionRate%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPurple,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "Completion Score",
                                fontSize = 11.sp,
                                color = BentoTextPurple.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { completionRate.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BentoTextPurple,
                        trackColor = BentoTextPurple.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Section: Credits Developer info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BentoCardBlue),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Icon",
                        tint = BentoTextBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Focus Bento Todo App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BentoTextBlue
                    )
                    Text(
                        text = "v1.2.0-Bento Premium Edition",
                        fontSize = 11.sp,
                        color = BentoTextBlue.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "\"Focus is a muscle, and day-by-day clarity is the workout. Clear the noise, achieve the day.\"",
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BentoTextBlue.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Bottom space in column
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

enum class AuthMode { LOGIN, REGISTER, FORGOT_PASSWORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: TodoViewModel) {
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Vibrant background gradient representing modern/sleek style
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFDDEBFF), // light sky blue at very top
            Color(0xFFF1F6FE), // clean ambient blue
            Color(0xFFFFFFFF)  // pure white at bottom
        )
    )

    val activeColor = Color(0xFF1D5AFA)
    val inactiveColor = Color(0xFF94A3B8)
    val fieldBackground = Color(0xFFF8FAFC)
    val inactiveBorderColor = Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 410.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main card container matches screenshot perfectly
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_card"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with gradient representing high-quality technology aesthetics
                    val headerGradient = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0072FF), // Electric deep blue
                            Color(0xFF00C6FF)  // Brilliant cyan
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                headerGradient,
                                shape = RoundedCornerShape(
                                    topStart = 31.dp,
                                    topEnd = 31.dp,
                                    bottomStart = 32.dp,
                                    bottomEnd = 32.dp
                                )
                            )
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (authMode == AuthMode.LOGIN) Icons.Default.Login else if (authMode == AuthMode.REGISTER) Icons.Default.Dashboard else Icons.Default.Lock,
                                contentDescription = "Bento Logo",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = when (authMode) {
                                    AuthMode.LOGIN -> "WELCOME BACK"
                                    AuthMode.REGISTER -> "CREATE ACCOUNT"
                                    AuthMode.FORGOT_PASSWORD -> "RESET PASSWORD"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Focus Bento",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = when (authMode) {
                                    AuthMode.LOGIN -> "Enter credentials to unlock deep focus daily"
                                    AuthMode.REGISTER -> "Setup a profile to secure your tasks offline"
                                    AuthMode.FORGOT_PASSWORD -> "Recover your profile securely without the internet"
                                },
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }

                    // Form container block with nice layout margin
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 28.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Error message
                        if (errorMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = Color(0xFF991B1B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        // Success message
                        if (successMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
                            ) {
                                Text(
                                    text = successMessage ?: "",
                                    color = Color(0xFF065F46),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        // Input fields
                        if (authMode == AuthMode.REGISTER || authMode == AuthMode.FORGOT_PASSWORD) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = { Text("Your Name", color = inactiveColor) },
                                label = { Text(if (authMode == AuthMode.FORGOT_PASSWORD) "Registered Name" else "Your Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name Icon", tint = activeColor) },
                                modifier = Modifier.fillMaxWidth().testTag("name_input"),
                                shape = RoundedCornerShape(22.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = activeColor,
                                    unfocusedLabelColor = inactiveColor,
                                    focusedContainerColor = fieldBackground,
                                    unfocusedContainerColor = fieldBackground,
                                    focusedBorderColor = activeColor,
                                    unfocusedBorderColor = inactiveBorderColor,
                                    cursorColor = activeColor
                                )
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = { Text("Email Address", color = inactiveColor) },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email Icon", tint = activeColor) },
                            modifier = Modifier.fillMaxWidth().testTag("email_input"),
                            shape = RoundedCornerShape(22.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1E293B),
                                unfocusedTextColor = Color(0xFF1E293B),
                                focusedLabelColor = activeColor,
                                unfocusedLabelColor = inactiveColor,
                                focusedContainerColor = fieldBackground,
                                unfocusedContainerColor = fieldBackground,
                                focusedBorderColor = activeColor,
                                unfocusedBorderColor = inactiveBorderColor,
                                cursorColor = activeColor
                            )
                        )

                        var passwordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text(if (authMode == AuthMode.FORGOT_PASSWORD) "New Password" else "Password", color = inactiveColor) },
                            label = { Text(if (authMode == AuthMode.FORGOT_PASSWORD) "New Password" else "Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock Icon", tint = activeColor) },
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(image, contentDescription = "Toggle password visibility", tint = inactiveColor)
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("password_input"),
                            shape = RoundedCornerShape(22.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = activeColor,
                                    unfocusedLabelColor = inactiveColor,
                                    focusedContainerColor = fieldBackground,
                                    unfocusedContainerColor = fieldBackground,
                                    focusedBorderColor = activeColor,
                                    unfocusedBorderColor = inactiveBorderColor,
                                    cursorColor = activeColor
                            )
                        )

                        if (authMode == AuthMode.REGISTER || authMode == AuthMode.FORGOT_PASSWORD) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                placeholder = { Text(if (authMode == AuthMode.FORGOT_PASSWORD) "Confirm New Password" else "Confirm Password", color = inactiveColor) },
                                label = { Text(if (authMode == AuthMode.FORGOT_PASSWORD) "Confirm New Password" else "Confirm Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock Confirm Icon", tint = activeColor) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("confirm_password_input"),
                                shape = RoundedCornerShape(22.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1E293B),
                                    unfocusedTextColor = Color(0xFF1E293B),
                                    focusedLabelColor = activeColor,
                                    unfocusedLabelColor = inactiveColor,
                                    focusedContainerColor = fieldBackground,
                                    unfocusedContainerColor = fieldBackground,
                                    focusedBorderColor = activeColor,
                                    unfocusedBorderColor = inactiveBorderColor,
                                    cursorColor = activeColor
                                )
                            )
                        }

                        if (authMode == AuthMode.LOGIN) {
                            Text(
                                text = "Forgot Password?",
                                color = activeColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable {
                                        errorMessage = null
                                        successMessage = null
                                        authMode = AuthMode.FORGOT_PASSWORD
                                    }
                                    .testTag("forgot_password_link")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val buttonGradient = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF005AFF),
                                Color(0xFF00AAFF)
                            )
                        )

                        Button(
                            onClick = {
                                if (isLoading) return@Button
                                errorMessage = null
                                successMessage = null
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        when (authMode) {
                                            AuthMode.LOGIN -> {
                                                val err = viewModel.login(email, password)
                                                if (err != null) {
                                                    errorMessage = err
                                                } else {
                                                    successMessage = "Welcome inside!"
                                                }
                                            }
                                            AuthMode.REGISTER -> {
                                                if (password != confirmPassword) {
                                                    errorMessage = "Passwords do not match."
                                                } else {
                                                    val err = viewModel.signUp(name, email, password)
                                                    if (err != null) {
                                                        errorMessage = err
                                                    } else {
                                                        successMessage = "Account registered successfully!"
                                                    }
                                                }
                                            }
                                            AuthMode.FORGOT_PASSWORD -> {
                                                if (password != confirmPassword) {
                                                    errorMessage = "Passwords do not match."
                                                } else {
                                                    val err = viewModel.resetPassword(email, name, password)
                                                    if (err != null) {
                                                        errorMessage = err
                                                    } else {
                                                        successMessage = "Password reset successfully! You can now log in."
                                                        authMode = AuthMode.LOGIN
                                                        password = ""
                                                        confirmPassword = ""
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage ?: "An unexpected error occurred."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .background(buttonGradient, shape = RoundedCornerShape(22.dp))
                                .testTag("submit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues() // clear default button padding to allow full gradient coverage
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = when (authMode) {
                                        AuthMode.LOGIN -> "Log In"
                                        AuthMode.REGISTER -> "Register Account"
                                        AuthMode.FORGOT_PASSWORD -> "Reset Password"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Bottom link matches registration/login swap perfectly
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (authMode) {
                                    AuthMode.LOGIN -> "New to focus? "
                                    AuthMode.REGISTER -> "Already signed up? "
                                    AuthMode.FORGOT_PASSWORD -> "Remembered password? "
                                },
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                            Text(
                                text = when (authMode) {
                                    AuthMode.LOGIN -> "Create Account"
                                    AuthMode.REGISTER -> "Log in"
                                    AuthMode.FORGOT_PASSWORD -> "Log in"
                                },
                                color = activeColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        authMode = if (authMode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                                        errorMessage = null
                                        successMessage = null
                                    }
                                    .testTag("toggle_auth_mode_button")
                            )
                        }
                    }
                }
            }
        }
    }
}
