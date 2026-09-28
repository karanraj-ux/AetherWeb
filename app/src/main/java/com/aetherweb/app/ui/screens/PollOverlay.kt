package com.aetherweb.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aetherweb.app.PollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.aetherweb.app.MeshNetworkManager
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip

@Composable
fun PollOverlay(
    poll: PollState,
    onVote: (Int) -> Unit,
    onClose: () -> Unit
) {
    val totalVotes = poll.votes.size
    val myVote = poll.votes[MeshNetworkManager.localNodeId]
    val isHost = poll.hostId == MeshNetworkManager.localNodeId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Live Poll",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = poll.question,
                style = MaterialTheme.typography.titleLarge
            )
            
            if (poll.attachmentUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = poll.attachmentUrl,
                    contentDescription = "Poll Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            poll.options.forEachIndexed { index, option ->
                val votesForOption = poll.votes.values.count { it == index }
                val percentage = if (totalVotes > 0) votesForOption.toFloat() / totalVotes else 0f
                
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = myVote == index,
                                onClick = { if (myVote == null) onVote(index) }
                            )
                            Text(text = option)
                        }
                        Text(text = "$votesForOption (${(percentage * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                    }
                    
                    // Animated Bar Chart representation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentage)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            
            if (isHost) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Close Poll")
                }
            }
        }
    }
}
