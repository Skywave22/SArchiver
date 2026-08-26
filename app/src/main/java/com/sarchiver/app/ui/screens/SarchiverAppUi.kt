@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.sarchiver.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sarchiver.app.data.archive.ArchiveEngine
import com.sarchiver.app.data.archive.ArchiveFormat
import com.sarchiver.app.data.transfer.TransferEngine
import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.SortMode
import com.sarchiver.app.domain.ViewMode
import com.sarchiver.app.ui.BrowserViewModel
import com.sarchiver.app.util.PathSecurity
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SarchiverAppUi(vm: BrowserViewModel) {
    val st by vm.state.collectAsState()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("New folder") }
    var archiveName by remember { mutableStateOf("archive.zip") }

    LaunchedEffect(st.error) {
        st.error?.let {
            snack.showSnackbar(it)
            vm.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sarchiver", style = MaterialTheme.typography.titleLarge)
                        Text(
                            st.path.ifBlank { "Storage" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.forward() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { vm.toggleView() }) {
                        Icon(
                            if (st.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "View mode"
                        )
                    }
                    IconButton(onClick = { vm.cycleTheme() }) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme")
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Sort by name") }, onClick = { vm.setSort(SortMode.NAME); menu = false })
                        DropdownMenuItem(text = { Text("Sort by size") }, onClick = { vm.setSort(SortMode.SIZE); menu = false })
                        DropdownMenuItem(text = { Text("Sort by date") }, onClick = { vm.setSort(SortMode.DATE); menu = false })
                        DropdownMenuItem(text = { Text("Sort by type") }, onClick = { vm.setSort(SortMode.TYPE); menu = false })
                        DropdownMenuItem(text = { Text("Select all") }, onClick = { vm.selectAll(); menu = false }, leadingIcon = { Icon(Icons.Default.SelectAll, null) })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0; vm.refreshRoots() }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Files") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Usb, null) }, label = { Text("Devices") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Bookmark, null) }, label = { Text("Bookmarks") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Archive, null) }, label = { Text("Jobs") })
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { vm.setShowCreateFolder(true) }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Create folder")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { vm.copySelected(false) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    IconButton(onClick = { vm.copySelected(true) }) { Icon(Icons.Default.ContentCut, "Cut") }
                    IconButton(onClick = { vm.paste() }) { Icon(Icons.Default.ContentPaste, "Paste") }
                    IconButton(onClick = { vm.setShowCompress(true) }) { Icon(Icons.Default.Archive, "Compress") }
                    IconButton(onClick = {
                        st.selected.firstOrNull()?.let {
                            ctx.startActivity(android.content.Intent.createChooser(vm.share(it), "Share"))
                        }
                    }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = { vm.deleteSelected() }) { Icon(Icons.Default.Delete, "Delete") }
                    IconButton(onClick = { vm.clearSelect() }) { Icon(Icons.Default.Close, "Clear") }
                }
            }
            OutlinedTextField(
                value = st.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search this folder") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            when (tab) {
                0 -> FilePane(st.items, st.viewMode, st.selected, vm)
                1 -> DevicePane(st.storageRoots, st.mtpDevices, vm)
                2 -> BookmarkPane(st.bookmarks, vm)
                3 -> JobsPane(st.transfers)
            }
        }
    }

    if (st.showCreateFolder) {
        AlertDialog(
            onDismissRequest = { vm.setShowCreateFolder(false) },
            title = { Text("Create folder") },
            text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Name") }) },
            confirmButton = { Button(onClick = { vm.createFolder(folderName) }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { vm.setShowCreateFolder(false) }) { Text("Cancel") } }
        )
    }
    if (st.showCompress) {
        AlertDialog(
            onDismissRequest = { vm.setShowCompress(false) },
            title = { Text("Create archive") },
            text = {
                Column {
                    OutlinedTextField(archiveName, { archiveName = it }, label = { Text("File name") })
                    Text("ZIP, 7z, tar, tar.gz, tar.xz based on extension. RAR creation is not offered (licensing).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fmt = ArchiveEngine.detect(File(archiveName)) ?: ArchiveFormat.ZIP
                    vm.compressSelected(fmt, archiveName, 6, null)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { vm.setShowCompress(false) }) { Text("Cancel") } }
        )
    }
    st.properties?.let { node ->
        AlertDialog(
            onDismissRequest = { vm.dismissProperties() },
            title = { Text("Properties") },
            text = {
                Column {
                    Text("Name: ${node.name}")
                    Text("Path: ${node.path}")
                    Text("Type: ${if (node.isDirectory) "Folder" else (node.mime ?: "File")}")
                    Text("Writable: ${node.canWrite}")
                    val size = st.propertiesSize ?: node.size
                    Text("Size: ${PathSecurity.formatBytes(size)}")
                    Text("Modified: ${DateFormat.getDateTimeInstance().format(Date(node.lastModified))}")
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissProperties() }) { Text("Close") } }
        )
    }
    st.archiveEntries?.let { entries ->
        ModalBottomSheet(onDismissRequest = { vm.closeArchive() }) {
            Text("Archive: ${st.archiveFile}", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(entries) { e ->
                    Text("${e.path}  ${PathSecurity.formatBytes(e.size)}", modifier = Modifier.padding(12.dp, 6.dp))
                }
            }
            Button(
                onClick = {
                    val src = st.archiveFile ?: return@Button
                    vm.extract(src, File(src).parent ?: st.path)
                    vm.closeArchive()
                },
                modifier = Modifier.padding(16.dp)
            ) { Text("Extract here") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePane(
    items: List<FsNode>,
    mode: ViewMode,
    selected: Set<String>,
    vm: BrowserViewModel,
) {
    if (items.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("This folder is empty")
        }
        return
    }
    if (mode == ViewMode.GRID) {
        LazyVerticalGrid(GridCells.Adaptive(140.dp), modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { n -> FileCard(n, selected.contains(n.id), vm) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { n -> FileRow(n, selected.contains(n.id), vm) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(n: FsNode, sel: Boolean, vm: BrowserViewModel) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (sel || vm.state.value.selected.isNotEmpty()) vm.toggleSelect(n.id)
                    else if (n.isDirectory) vm.open(n.path)
                    else {
                        val arch = ArchiveEngine.detect(File(n.path))
                        if (arch != null) vm.inspectArchive(n.path)
                        else ctx.startActivity(android.content.Intent.createChooser(vm.openWith(n.path), "Open with"))
                    }
                },
                onLongClick = { vm.toggleSelect(n.id) }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (n.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (n.isDirectory) "Folder" else PathSecurity.formatBytes(n.size),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (sel) Text("✓", color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = { vm.showProperties(n) }) { Icon(Icons.Default.Info, "Properties") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(n: FsNode, sel: Boolean, vm: BrowserViewModel) {
    Card(
        modifier = Modifier.padding(8.dp).combinedClickable(
            onClick = { if (n.isDirectory) vm.open(n.path) else vm.inspectArchive(n.path) },
            onLongClick = { vm.toggleSelect(n.id) }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (n.isDirectory) Icons.Default.Folder else Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.primary)
            Text(n.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DevicePane(roots: List<FsNode>, mtp: List<String>, vm: BrowserViewModel) {
    LazyColumn(Modifier.padding(12.dp)) {
        item { Text("Storage volumes", style = MaterialTheme.typography.titleMedium) }
        items(roots) { r ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).combinedClickable(onClick = { vm.open(r.path) }, onLongClick = {}),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(r.path, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("MTP devices", style = MaterialTheme.typography.titleMedium)
            if (mtp.isEmpty()) Text("No MTP device connected. Plug in a phone or camera over USB and choose File transfer / MTP.")
            mtp.forEach { Text("• $it") }
            Button(onClick = { vm.connectMtp() }, modifier = Modifier.padding(top = 8.dp)) { Text("Browse MTP") }
        }
    }
}

@Composable
private fun BookmarkPane(bookmarks: List<String>, vm: BrowserViewModel) {
    Column(Modifier.padding(16.dp)) {
        Button(onClick = { vm.bookmark(vm.state.value.path) }) { Text("Bookmark current folder") }
        LazyColumn {
            items(bookmarks) { p ->
                Text(p, modifier = Modifier.padding(12.dp).combinedClickable(onClick = { vm.open(p) }, onLongClick = { vm.bookmark(p) }))
            }
        }
    }
}

@Composable
private fun JobsPane(jobs: List<com.sarchiver.app.domain.TransferProgress>) {
    if (jobs.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("No transfers")
        }
        return
    }
    LazyColumn(Modifier.padding(12.dp)) {
        items(jobs) { j ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(j.title, style = MaterialTheme.typography.titleMedium)
                    Text(j.currentName)
                    val frac = if (j.bytesTotal <= 0) 0f else j.bytesDone.toFloat() / j.bytesTotal
                    LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text("${PathSecurity.formatBytes(j.bytesDone)} / ${PathSecurity.formatBytes(j.bytesTotal)}")
                    Text(TransferEngine.speedLabel(j.bytesPerSecond))
                    Text("ETA ${j.etaSeconds}s · files ${j.filesDone}/${j.filesTotal}")
                    j.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
