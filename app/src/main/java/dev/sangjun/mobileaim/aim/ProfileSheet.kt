package dev.sangjun.mobileaim.aim

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sangjun.mobileaim.app.settings.AimProfile

/** [androidx.compose.ui.Modifier.testTag] on the sheet's content — `ModalBottomSheet` renders in its own compose root (a separate window), so tests need this to disambiguate from the main screen's root rather than relying on `onRoot()`. */
const val ProfileSheetTestTag = "profile_sheet"

/**
 * The header's profile dropdown opens this bottom sheet ("편집", or "새로 만들기" straight on the new
 * profile's editor via [initialEditingProfileId]) — a flat list of saved [AimProfile]s (tap
 * to select) plus an "새 프로파일" row, with a pencil icon on each row that switches to an inline
 * editor for that profile's name (see [ProfileEditor]). Replaces the old
 * fixed 화기/수동 `AimMode` toggle with an arbitrary number of named zeros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    initialEditingProfileId: String? = null,
    profiles: List<AimProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> String,
    onRenameProfile: (profileId: String, name: String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingProfileId by remember { mutableStateOf(initialEditingProfileId) }
    val editingProfile = profiles.firstOrNull { it.id == editingProfileId }
    // This app is landscape-only, so the sheet's default "partially expanded" resting state would
    // show only a sliver of content — skip straight to fully expanded (the content inside still
    // scrolls if it's taller than that).
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(modifier = Modifier.testTag(ProfileSheetTestTag)) {
            if (editingProfile != null) {
                ProfileEditor(
                    profile = editingProfile,
                    canDelete = profiles.size > 1,
                    onSave = { name ->
                        onRenameProfile(editingProfile.id, name)
                        editingProfileId = null
                    },
                    onDelete = {
                        onDeleteProfile(editingProfile.id)
                        editingProfileId = null
                    },
                    onCancel = { editingProfileId = null },
                )
            } else {
                ProfileList(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    onSelect = {
                        onSelectProfile(it)
                        onDismiss()
                    },
                    onEdit = { editingProfileId = it },
                    onAdd = { editingProfileId = onAddProfile() },
                )
            }
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<AimProfile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // This app is landscape-only, so the visible height is short — a longer profile list needs to scroll.
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Text(
            text = "프로파일",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        profiles.forEach { profile ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(profile.id) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (profile.id == activeProfileId) {
                        Icon(Icons.Filled.Check, contentDescription = "선택됨", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { onEdit(profile.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "${profile.name} 편집")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("새 프로파일", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: AimProfile,
    canDelete: Boolean,
    onSave: (name: String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }

    // This app is landscape-only, so the visible height is short — keep it scrollable.
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("프로파일 편집", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("취소") }
            if (canDelete) {
                TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (name.isNotBlank()) onSave(name) },
            ) { Text("저장") }
        }
    }
}
