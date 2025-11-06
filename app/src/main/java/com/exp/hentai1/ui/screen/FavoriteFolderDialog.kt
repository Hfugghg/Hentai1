package com.exp.hentai1.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exp.hentai1.data.FavoriteFolder
import com.exp.hentai1.ui.detail.FavoriteFolderWithCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFolderDialog(
    foldersWithCount: List<FavoriteFolderWithCount>,
    onDismiss: () -> Unit,
    onFolderSelected: (FavoriteFolder) -> Unit,
    onCreateNewFolder: (String) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择收藏夹") },
        text = {
            Column {
                LazyColumn {
                    items(foldersWithCount) {
                        folderWithCount ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { onFolderSelected(folderWithCount.folder) }.padding(8.dp)) {
                            Text(text = "${folderWithCount.folder.name} (${folderWithCount.count})")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("新建收藏夹") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newFolderName.isNotBlank()) {
                        onCreateNewFolder(newFolderName)
                    }
                }
            ) {
                Text("新建")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
