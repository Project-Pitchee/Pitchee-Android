package io.rovly.pitchee.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.update.UpdateUiState

@Composable
internal fun SettingsScreen(
    updateState: UpdateUiState,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val languageCode = LocalConfiguration.current.locales[0].language
    val preferences = remember(context) { PassagePreferences(context) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
    }
    var source by remember { mutableStateOf(preferences.source()) }
    var officialIndex by remember { mutableIntStateOf(preferences.officialIndex()) }
    var customText by remember { mutableStateOf(preferences.customText()) }
    var customSaved by remember { mutableStateOf(false) }
    val language = if (languageCode.startsWith("en")) "en" else "zh"
    val uriHandler = LocalUriHandler.current
    val repositoryUrl = stringResource(R.string.settings_repository_url)
    val pitcheeCoreUrl = stringResource(R.string.settings_pitchee_core_url)
    val lvyZhanUrl = stringResource(R.string.settings_author_lvy_url)
    val ryoFengUrl = stringResource(R.string.settings_author_ryo_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = language == "zh",
                onClick = {
                    AppLanguage.set(context, AppLanguage.LANGUAGE_ZH)
                },
                label = { Text(stringResource(R.string.language_chinese)) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = language == "en",
                onClick = {
                    AppLanguage.set(context, AppLanguage.LANGUAGE_EN)
                },
                label = { Text(stringResource(R.string.language_english)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_passages),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = source == PassageSource.OFFICIAL,
                onClick = {
                    source = PassageSource.OFFICIAL
                    preferences.setSource(source)
                },
                label = { Text(stringResource(R.string.passage_source_official)) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = source == PassageSource.CUSTOM,
                onClick = {
                    source = PassageSource.CUSTOM
                    preferences.setSource(source)
                    customSaved = false
                },
                label = { Text(stringResource(R.string.passage_source_custom)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (source == PassageSource.OFFICIAL) {
            readingPassages.forEachIndexed { index, passage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = officialIndex == index,
                        onClick = {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            text = passage.title(context),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = passage.text(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    customSaved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.passage_source_custom)) },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    preferences.setCustomText(customText)
                    customText = preferences.customText()
                    customSaved = true
                },
                enabled = customText.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.save_passage))
            }
            val savedCustomText = preferences.customText()
            if (customSaved || (savedCustomText.isNotBlank() && customText == savedCustomText)) {
                Text(
                    text = stringResource(R.string.custom_passage_active),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_app),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_current_version),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onCheckUpdates,
                enabled = updateState !is UpdateUiState.Checking &&
                    updateState !is UpdateUiState.Downloading,
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.settings_check_updates))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_updates),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_auto_updates_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoCheckUpdates,
                onCheckedChange = onAutoCheckChange,
            )
        }
        when (val state = updateState) {
            is UpdateUiState.UpToDate -> UpdateStatusText(
                stringResource(R.string.update_up_to_date),
            )
            is UpdateUiState.Available -> UpdateStatusText(
                stringResource(R.string.update_available, state.release.displayName),
            )
            is UpdateUiState.Downloading -> UpdateStatusText(
                stringResource(R.string.update_downloading),
            )
            is UpdateUiState.ReadyToInstall -> UpdateStatusText(
                stringResource(R.string.update_ready),
            )
            is UpdateUiState.Error -> UpdateStatusText(
                text = state.message,
                isError = true,
            )
            UpdateUiState.Idle,
            UpdateUiState.Checking,
            -> Unit
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.settings_authors),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        AuthorRow(
            name = "Lvy Zhan",
            profileUrl = lvyZhanUrl,
            avatarRes = R.drawable.author_lvy_zhan,
            onClick = { uriHandler.openUri(lvyZhanUrl) },
        )
        Spacer(Modifier.height(10.dp))
        AuthorRow(
            name = "Ryo Feng",
            profileUrl = ryoFengUrl,
            avatarRes = R.drawable.author_ryo_feng,
            onClick = { uriHandler.openUri(ryoFengUrl) },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_repository),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_repository_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = repositoryUrl,
            modifier = Modifier.clickable { uriHandler.openUri(repositoryUrl) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.settings_open_source),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_open_source_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        listOf(
            R.string.settings_license_pitchee_core,
            R.string.settings_license_swift_f0,
            R.string.settings_license_silero_vad,
            R.string.settings_license_speechbrain,
            R.string.settings_license_onnxruntime,
            R.string.settings_license_androidx,
            R.string.settings_license_markwon,
            R.string.settings_license_material_icons,
        ).forEach { license ->
            Text(
                text = stringResource(license),
                modifier = Modifier.padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = pitcheeCoreUrl,
            modifier = Modifier.clickable { uriHandler.openUri(pitcheeCoreUrl) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AuthorRow(
    name: String,
    profileUrl: String,
    avatarRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(avatarRes),
            contentDescription = name,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = profileUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun UpdateStatusText(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
