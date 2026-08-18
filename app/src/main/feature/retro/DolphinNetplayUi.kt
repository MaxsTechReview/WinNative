package com.winlator.cmod.feature.retro

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.GameSettingsStyle
import org.dolphinemu.dolphinemu.wn.DolphinNetplay

@Composable
fun DolphinNetplaySettingsSection(
    systemId: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    version

    val enabled = RetroDefaults.netplayEnabled(context, systemId)
    val isHost = RetroDefaults.netplayHostMode(context, systemId)
    val traversal = RetroDefaults.netplayTraversal(context, systemId)
    val player = RetroDefaults.netplayPlayerName(context)
    val host = RetroDefaults.netplayHost(context, systemId)
    val hostCode = RetroDefaults.netplayHostCode(context, systemId)
    val port = RetroDefaults.netplayPort(context, systemId)

    RetroSettingGroup {
        RetroSettingSwitch(
            stringResource(R.string.retro_gs_netplay_enable),
            enabled,
            subtitle = stringResource(R.string.retro_gs_netplay_enable_subtitle),
        ) {
            RetroDefaults.setNetplayEnabled(context, systemId, it)
            onChanged()
        }

        if (enabled) {
            RetroSettingTextField(
                stringResource(R.string.retro_netplay_player_name),
                player,
                stringResource(R.string.retro_netplay_player_name_hint),
            ) {
                RetroDefaults.setNetplayPlayerName(context, it)
                onChanged()
            }

            RetroSettingDropdown(
                stringResource(R.string.retro_netplay_launch_mode),
                listOf(
                    stringResource(R.string.retro_netplay_mode_host),
                    stringResource(R.string.retro_netplay_mode_join),
                ),
                if (isHost) 0 else 1,
            ) { index ->
                RetroDefaults.setNetplayHostMode(context, systemId, index == 0)
                onChanged()
            }

            RetroSettingDropdown(
                stringResource(R.string.retro_netplay_connection),
                listOf(
                    stringResource(R.string.retro_netplay_conn_direct),
                    stringResource(R.string.retro_netplay_conn_traversal),
                ),
                if (traversal) 1 else 0,
            ) { index ->
                RetroDefaults.setNetplayTraversal(context, systemId, index == 1)
                onChanged()
            }

            if (!isHost && traversal) {
                RetroNetplayEditField(
                    label = stringResource(R.string.retro_netplay_host_code),
                    value = hostCode,
                    placeholder = stringResource(R.string.retro_netplay_host_code_hint),
                ) {
                    RetroDefaults.setNetplayHostCode(context, systemId, it)
                    onChanged()
                }
            } else if (!isHost) {
                RetroNetplayEditField(
                    label = stringResource(R.string.retro_gs_netplay_host),
                    value = host,
                    placeholder = stringResource(R.string.retro_gs_netplay_host_hint),
                ) {
                    RetroDefaults.setNetplayHost(context, systemId, it)
                    onChanged()
                }
            }

            if (!traversal) {
                RetroNetplayEditField(
                    label = stringResource(R.string.retro_gs_netplay_port),
                    value = port.toString(),
                    placeholder = DolphinNetplay.DEFAULT_PORT.toString(),
                    numeric = true,
                ) { entered ->
                    val p = entered.toIntOrNull() ?: DolphinNetplay.DEFAULT_PORT
                    RetroDefaults.setNetplayPort(context, systemId, p)
                    onChanged()
                }
            }

            val hint =
                when {
                    isHost && traversal -> R.string.retro_netplay_dolphin_host_online_hint
                    isHost -> R.string.retro_netplay_dolphin_host_lan_hint
                    traversal -> R.string.retro_netplay_dolphin_join_online_hint
                    else -> R.string.retro_netplay_dolphin_join_lan_hint
                }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(hint),
                color = GameSettingsStyle.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Text(
                stringResource(R.string.retro_netplay_dolphin_help),
                color = GameSettingsStyle.TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
