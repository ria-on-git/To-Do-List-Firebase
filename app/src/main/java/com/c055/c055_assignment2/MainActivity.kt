
package com.c055.c055_assignment2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextDecoration

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()

        val databaseUrl = "https://c055-assignment2-default-rtdb.asia-southeast1.firebasedatabase.app/"
        val database = FirebaseDatabase.getInstance(databaseUrl)

        setContent {
            MaterialTheme {
                var description by remember { mutableStateOf("") }
                var priority by remember { mutableStateOf("Medium") }
                var expanded by remember { mutableStateOf(false) }
                var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
                var editingTask by remember { mutableStateOf<Task?>(null) }
                var editedDescription by remember { mutableStateOf("") }
                var editedPriority by remember { mutableStateOf("Medium") }
                var status by remember { mutableStateOf("Connecting...") }
                var userId by remember { mutableStateOf(auth.currentUser?.uid) }

                LaunchedEffect(Unit) {
                    if (auth.currentUser != null) {
                        userId = auth.currentUser?.uid
                        status = "Connected"
                    } else {
                        auth.signInAnonymously()
                            .addOnSuccessListener {
                                userId = it.user?.uid
                                status = "Connected"
                            }
                            .addOnFailureListener {
                                status = "Authentication failed: ${it.message}"
                            }
                    }
                }

                DisposableEffect(userId) {
                    val uid = userId

                    if (uid == null) {
                        onDispose { }
                    } else {
                        val tasksRef = database
                            .getReference("tasks")
                            .child(uid)

                        val listener = object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val taskList = mutableListOf<Task>()

                                for (child in snapshot.children) {
                                    val task = child.getValue(Task::class.java)
                                    if (task != null) {
                                        taskList.add(task)
                                    }
                                }

                                val priorityOrder = mapOf(
                                    "High" to 0,
                                    "Medium" to 1,
                                    "Low" to 2
                                )

                                tasks = taskList.sortedWith(
                                    compareBy<Task> { it.completed }
                                        .thenBy { priorityOrder[it.priority] ?: 3 }
                                )

                            }

                            override fun onCancelled(error: DatabaseError) {
                                status = "Read failed: ${error.message}"
                            }
                        }

                        tasksRef.addValueEventListener(listener)

                        onDispose {
                            tasksRef.removeEventListener(listener)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "My To-Do List",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = status)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Task Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))


                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Priority: $priority")
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            listOf("High", "Medium", "Low").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        priority = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val uid = userId
                            if (uid != null && description.isNotBlank()) {
                                val ref = database.getReference("tasks")
                                    .child(uid)

                                val taskId = ref.push().key

                                if (taskId != null) {
                                    val task = Task(
                                        id = taskId,
                                        description = description.trim(),
                                        priority = priority
                                    )

                                    ref.child(taskId).setValue(task)
                                        .addOnSuccessListener {
                                            description = ""
                                            status = "Task added successfully"
                                        }
                                        .addOnFailureListener {
                                            status = "Save failed: ${it.message}"
                                        }
                                }
                            }
                        },
                        enabled = userId != null && description.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Task")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Your Tasks",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Tap a task to edit • Swipe left to delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { newValue ->
                                    if (newValue == SwipeToDismissBoxValue.EndToStart) {
                                        val uid = userId

                                        if (uid != null) {
                                            database.getReference("tasks")
                                                .child(uid)
                                                .child(task.id)
                                                .removeValue()
                                                .addOnSuccessListener {
                                                    status = "Task deleted successfully"
                                                }
                                                .addOnFailureListener {
                                                    status = "Delete failed: ${it.message}"
                                                }
                                        }
                                    }
                                    false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFD32F2F))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Task",
                                            tint = Color.White
                                        )
                                    }
                                }
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingTask = task
                                            editedDescription = task.description
                                            editedPriority = task.priority
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.completed,
                                            onCheckedChange = { isChecked ->
                                                val uid = userId

                                                if (uid != null) {
                                                    database.getReference("tasks")
                                                        .child(uid)
                                                        .child(task.id)
                                                        .child("completed")
                                                        .setValue(isChecked)
                                                        .addOnFailureListener {
                                                            status = "Update failed: ${it.message}"
                                                        }
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = task.description,
                                                style = MaterialTheme.typography.titleMedium,
                                                textDecoration = if (task.completed) {
                                                    TextDecoration.LineThrough
                                                } else {
                                                    TextDecoration.None
                                                }
                                            )

                                            Text(
                                                text = if (task.completed) {
                                                    "Completed"
                                                } else {
                                                    "Priority: ${task.priority}"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                if (editingTask != null) {
                    AlertDialog(
                        onDismissRequest = {
                            editingTask = null
                        },
                        title = {
                            Text("Edit Task")
                        },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editedDescription,
                                    onValueChange = {
                                        editedDescription = it
                                    },
                                    label = {
                                        Text("Task Description")
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Priority")

                                listOf("High", "Medium", "Low").forEach { option ->
                                    Row {
                                        RadioButton(
                                            selected = editedPriority == option,
                                            onClick = {
                                                editedPriority = option
                                            }
                                        )
                                        Text(
                                            text = option,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val task = editingTask
                                    val uid = userId

                                    if (task != null &&
                                        uid != null &&
                                        editedDescription.isNotBlank()
                                    ) {
                                        val updatedTask = task.copy(
                                            description = editedDescription.trim(),
                                            priority = editedPriority
                                        )

                                        database.getReference("tasks")
                                            .child(uid)
                                            .child(task.id)
                                            .setValue(updatedTask)
                                            .addOnSuccessListener {
                                                status = "Task updated successfully"
                                                editingTask = null
                                            }
                                            .addOnFailureListener {
                                                status = "Update failed: ${it.message}"
                                            }
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    editingTask = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

            }
        }
    }
}
