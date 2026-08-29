package com.winlator.cmod.feature.stores.steam.data
import com.winlator.cmod.feature.stores.steam.db.serializers.OsEnumSetSerializer
import com.winlator.cmod.feature.stores.steam.enums.OS
import com.winlator.cmod.feature.stores.steam.enums.OSArch
import kotlinx.serialization.Serializable
import java.util.EnumSet

@Serializable
data class LaunchInfo(
    val executable: String,
    val workingDir: String,
    val description: String,
    val type: String,
    // Default keeps already-cached appinfo JSON (without this key) decodable.
    val arguments: String = "",
    // config.launch VDF key ("0","1",…) — Steam's launch-option id, which can differ from the
    // list position when keys are non-contiguous. -1 (pre-launchId cache) = fall back to position.
    val launchId: Int = -1,
    @Serializable(with = OsEnumSetSerializer::class)
    val configOS: java.util.EnumSet<OS>,
    val configArch: OSArch,
)
