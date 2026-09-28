package com.aetherweb.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollBuilderDialog(
    onDismiss: () -> Unit,
    onStartPoll: (String, List<String>, android.net.Uri?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var attachmentUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachmentUri = uri
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a Poll") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (attachmentUri != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 16.dp)) {
                        AsyncImage(
                            model = attachmentUri,
                            contentDescription = "Poll Attachment",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        IconButton(
                            onClick = { attachmentUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { imageLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Add Image")
                        Spacer(Modifier.width(8.dp))
                        Text("Attach Image (Optional)")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text("Options", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    itemsIndexed(options) { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = option,
                                onValueChange = { 
                                    val newOptions = options.toMutableList()
                                    newOptions[index] = it
                                    options = newOptions
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Option ${index + 1}") }
                            )
                            if (options.size > 2) {
                                IconButton(onClick = {
                                    val newOptions = options.toMutableList()
                                    newOptions.removeAt(index)
                                    options = newOptions
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
                if (options.size < 6) {
                    TextButton(onClick = {
                        options = options + ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Option")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (question.isNotBlank() && options.all { it.isNotBlank() }) {
                        onStartPoll(question, options, attachmentUri)
                    }
                },
                enabled = question.isNotBlank() && options.all { it.isNotBlank() }
            ) {
                Text("Start Poll")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
