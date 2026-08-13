package com.winlator.cmod.feature.retro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.GameSettingsStyle

private val RoomBg = Color(0xF0121824)
private val RoomBorder = Color(0xFF1A9FFF).copy(alpha = 0.45f)
private val OnlineDot = Color(0xFF3FB950)
private val RoomCardBg = Color(0xFF141C28)
private val RoomCardBorder = Color(0xFF2A3A4E)

@Composable
fun RetroNetplayRoomBanner(
    onLeave: () -> Unit,
    onJoinRoom: ((RetroNetplayDiscoveredRoom) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val phase = RetroNetplayLobby.phase
    val status = RetroNetplayLobby.statusText
    val discovered = RetroNetplayLobby.discovered
    val showResults =
        phase == RetroNetplayPhase.SCAN_RESULTS ||
            phase == RetroNetplayPhase.SCANNING ||
            (phase == RetroNetplayPhase.IDLE && discovered.isNotEmpty())
    if (!showResults) return

    Column(
        modifier =
            modifier
                .fillMaxWidth(0.78f)
                .background(RoomBg, RoundedCornerShape(12.dp))
                .border(1.dp, RoomBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector =
                    when (phase) {
                        RetroNetplayPhase.SCANNING, RetroNetplayPhase.SCAN_RESULTS -> Icons.Outlined.Search
                        else -> Icons.Outlined.Wifi
                    },
                contentDescription = null,
                tint = GameSettingsStyle.AccentBlue,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text =
                        when (phase) {
                            RetroNetplayPhase.SCANNING -> stringResource(R.string.retro_netplay_scanning)
                            RetroNetplayPhase.SCAN_RESULTS -> stringResource(R.string.retro_netplay_scan_results)
                            else -> stringResource(R.string.retro_netplay_title)
                        },
                    color = Color(0xFFF0F4FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = Color(0xFF93A6BC),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (phase == RetroNetplayPhase.SCANNING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    strokeWidth = 2.dp,
                    color = GameSettingsStyle.AccentBlue,
                )
            }
            TextButton(onClick = onLeave) {
                Text(
                    text =
                        when (phase) {
                            RetroNetplayPhase.SCANNING -> stringResource(R.string.retro_netplay_stop_scan)
                            else -> stringResource(R.string.retro_netplay_dismiss)
                        },
                    color = GameSettingsStyle.DangerRed,
                    fontSize = 12.sp,
                )
            }
        }

        if (discovered.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            discovered.forEach { room ->
                RoomResultRow(
                    room = room,
                    onClick =
                        if (onJoinRoom != null) {
                            { onJoinRoom(room) }
                        } else {
                            null
                        },
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun RoomResultRow(
    room: RetroNetplayDiscoveredRoom,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RoomCardBg)
                .border(1.dp, RoomCardBorder, RoundedCornerShape(8.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Link,
            contentDescription = null,
            tint = GameSettingsStyle.AccentBlue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${room.gameName} · ${room.hostPlayerName}",
                color = Color(0xFFF0F4FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${room.hostAddress}:${room.port}",
                color = Color(0xFF93A6BC),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Text(
                stringResource(R.string.retro_netplay_join_room),
                color = GameSettingsStyle.AccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun RetroNetplayEventToast(modifier: Modifier = Modifier) {
    val message = RetroNetplayLobby.eventToast
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Text(
            text = message.orEmpty(),
            color = Color(0xFFF0F4FF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE0121824))
                    .border(1.dp, RoomBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun RetroNetplaySettingsSection(
    systemId: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    version
    val enabled = RetroDefaults.netplayEnabled(context, systemId)
    val mode = RetroDefaults.netplayLaunchMode(context, systemId)
    val host = RetroDefaults.netplayHost(context, systemId)
    val port = RetroDefaults.netplayPort(context, systemId)
    val player = RetroDefaults.netplayPlayerName(context)
    val roomLabel =
        RetroSystems.fromId(systemId)?.displayName
            ?: stringResource(R.string.retro_netplay_title)

    val phase = RetroNetplayLobby.phase
    val status = RetroNetplayLobby.statusText
    val discovered = RetroNetplayLobby.discovered
    val members = RetroNetplayLobby.members
    val scanning = phase == RetroNetplayPhase.SCANNING
    val inSession =
        phase == RetroNetplayPhase.HOSTING ||
            phase == RetroNetplayPhase.IN_ROOM ||
            phase == RetroNetplayPhase.CONNECTING

    val isGameLink = RetroOnlineSupport.supportsGameLink(systemId)
    val effectivePort =
        if (isGameLink) RetroGameLink.clampPort(port) else port

    fun startHostNow() {
        RetroNetplayLobby.bindLocalName(context)
        RetroNetplayLobby.host(
            systemId = systemId,
            gameName = roomLabel,
            playerName = RetroNetplayLobby.defaultPlayerName(context),
            port = effectivePort,
            context = context,
            retroView = null,
        )
        onChanged()
    }

    fun joinNow(address: String, joinPort: Int = effectivePort, game: String = roomLabel) {
        val target = address.trim()
        if (target.isBlank()) return
        RetroNetplayLobby.bindLocalName(context)
        RetroNetplayLobby.join(
            host = target,
            port = joinPort,
            playerName = RetroNetplayLobby.defaultPlayerName(context),
            gameName = game,
            systemId = systemId,
            retroView = null,
        )
        onChanged()
    }

    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_section_online))
        Text(
            stringResource(
                if (isGameLink) {
                    R.string.retro_netplay_help_game_link
                } else {
                    R.string.retro_netplay_help
                },
            ),
            color = GameSettingsStyle.TextSecondary,
            fontSize = 12.sp,
            softWrap = true,
            maxLines = 8,
            lineHeight = 16.sp,
            overflow = TextOverflow.Clip,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
        )
        RetroSettingSwitch(
            stringResource(R.string.retro_gs_netplay_enable),
            enabled,
            subtitle = stringResource(R.string.retro_gs_netplay_enable_subtitle),
        ) {
            if (it) {
                RetroDefaults.setNetplayEnabled(context, systemId, true)
                if (RetroDefaults.netplayLaunchMode(context, systemId) !in setOf("host", "join")) {
                    RetroDefaults.setNetplayLaunchMode(context, systemId, "off")
                }
            } else {
                RetroDefaults.clearNetplayArm(context, systemId)
                if (inSession ||
                    phase == RetroNetplayPhase.SCANNING ||
                    phase == RetroNetplayPhase.SCAN_RESULTS
                ) {
                    RetroNetplayLobby.leave(silent = true)
                }
            }
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
            Text(
                stringResource(R.string.retro_netplay_launch_mode),
                color = GameSettingsStyle.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "host" to R.string.retro_netplay_mode_host,
                    "join" to R.string.retro_netplay_mode_join,
                ).forEach { (id, labelRes) ->
                    val selected = mode == id
                    OutlinedButton(
                        onClick = {
                            RetroDefaults.setNetplayLaunchMode(context, systemId, id)
                            if (id == "host") {
                                startHostNow()
                            } else if (RetroNetplayLobby.isHost && inSession) {
                                RetroNetplayLobby.leave(silent = true)
                            }
                            onChanged()
                        },
                        modifier = Modifier.weight(1f),
                        border =
                            BorderStroke(
                                1.dp,
                                if (selected) {
                                    GameSettingsStyle.AccentBlue
                                } else {
                                    GameSettingsStyle.CardBorder
                                },
                            ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor =
                                    if (selected) {
                                        GameSettingsStyle.AccentBlue
                                    } else {
                                        GameSettingsStyle.TextPrimary
                                    },
                            ),
                    ) {
                        Text(stringResource(labelRes), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            RetroNetplayEditField(
                label = stringResource(R.string.retro_gs_netplay_port),
                value = port.toString(),
                placeholder = RetroNetplayLobby.DEFAULT_TCP_PORT.toString(),
                numeric = true,
            ) { entered ->
                val p = entered.toIntOrNull() ?: RetroNetplayLobby.DEFAULT_TCP_PORT
                RetroDefaults.setNetplayPort(context, systemId, p)
                onChanged()
            }

            if (inSession) {
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        RetroNetplayLobby.isHost && phase == RetroNetplayPhase.HOSTING ->
                            stringResource(R.string.retro_netplay_hosting_room)
                        phase == RetroNetplayPhase.CONNECTING ->
                            stringResource(R.string.retro_netplay_connecting)
                        else -> stringResource(R.string.retro_netplay_in_room)
                    },
                    color = GameSettingsStyle.AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = GameSettingsStyle.TextSecondary,
                        fontSize = 11.sp,
                        softWrap = true,
                        maxLines = 3,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Clip,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 6.dp),
                    )
                }
                members.forEach { member ->
                    val line =
                        buildString {
                            append(member.name)
                            if (member.isHost) append(" · Host")
                            if (member.isLocal) append(" · You")
                            if (!member.isHost && !member.isLocal) append(" · Joined")
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Group,
                            contentDescription = null,
                            tint = GameSettingsStyle.AccentBlue,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line,
                            color = GameSettingsStyle.TextPrimary,
                            fontSize = 13.sp,
                            softWrap = true,
                            maxLines = 2,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        RetroNetplayLobby.leave(silent = false)
                        onChanged()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.retro_netplay_leave),
                        color = GameSettingsStyle.DangerRed,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    stringResource(R.string.retro_netplay_prelaunch_hint),
                    color = GameSettingsStyle.TextDim,
                    fontSize = 11.sp,
                    softWrap = true,
                    maxLines = 4,
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Clip,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                )
            } else if (mode == "host") {
                Text(
                    stringResource(R.string.retro_netplay_host_mode_hint),
                    color = GameSettingsStyle.TextDim,
                    fontSize = 11.sp,
                    softWrap = true,
                    maxLines = 5,
                    lineHeight = 15.sp,
                    overflow = TextOverflow.Clip,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = { startHostNow() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    border = BorderStroke(1.dp, GameSettingsStyle.AccentBlue),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = GameSettingsStyle.AccentBlue,
                        ),
                ) {
                    Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retro_netplay_host_action), fontSize = 12.sp)
                }
            }

            if (mode == "join" && !inSession) {
                RetroNetplayEditField(
                    label = stringResource(R.string.retro_gs_netplay_host),
                    value = host,
                    placeholder = stringResource(R.string.retro_gs_netplay_host_hint),
                ) {
                    RetroDefaults.setNetplayHost(context, systemId, it)
                    onChanged()
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.retro_netplay_find_rooms),
                    color = GameSettingsStyle.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (discovered.isNotEmpty()) {
                    discovered.forEach { room ->
                        RoomResultRow(
                            room = room,
                            onClick = {
                                joinNow(
                                    address = room.hostAddress,
                                    joinPort = room.port,
                                    game = room.gameName.ifBlank { roomLabel },
                                )
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        stringResource(R.string.retro_netplay_select_room_hint),
                        color = GameSettingsStyle.TextDim,
                        fontSize = 11.sp,
                        softWrap = true,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (phase == RetroNetplayPhase.SCAN_RESULTS) {
                    Text(
                        stringResource(R.string.retro_netplay_no_rooms),
                        color = GameSettingsStyle.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (scanning) {
                                RetroNetplayLobby.stopScan()
                            } else {
                                RetroNetplayLobby.scan(context, systemId)
                            }
                            onChanged()
                        },
                        border =
                            BorderStroke(
                                1.dp,
                                if (scanning) GameSettingsStyle.AccentBlue else GameSettingsStyle.CardBorder,
                            ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor =
                                    if (scanning) {
                                        GameSettingsStyle.AccentBlue
                                    } else {
                                        GameSettingsStyle.TextPrimary
                                    },
                            ),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = GameSettingsStyle.AccentBlue,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retro_netplay_stop_scan), fontSize = 12.sp)
                        } else {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.retro_netplay_scan_action), fontSize = 12.sp)
                        }
                    }
                    if (scanning || phase == RetroNetplayPhase.SCAN_RESULTS || discovered.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                RetroNetplayLobby.dismissScanResults()
                                onChanged()
                            },
                        ) {
                            Text(
                                stringResource(R.string.retro_netplay_dismiss),
                                color = GameSettingsStyle.TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                if (host.isNotBlank()) {
                    OutlinedButton(
                        onClick = { joinNow(host) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        border = BorderStroke(1.dp, GameSettingsStyle.AccentBlue),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = GameSettingsStyle.AccentBlue,
                            ),
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.retro_netplay_join_action), fontSize = 12.sp)
                    }
                }

                if (status.isNotBlank() &&
                    (scanning || phase == RetroNetplayPhase.SCAN_RESULTS || discovered.isNotEmpty())
                ) {
                    Text(
                        status,
                        color =
                            if (discovered.isEmpty() && phase == RetroNetplayPhase.SCAN_RESULTS) {
                                GameSettingsStyle.TextDim
                            } else {
                                GameSettingsStyle.AccentBlue
                            },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RetroNetplayEventToast(Modifier.padding(top = 10.dp))
            }
        }
    }
}
