#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#ifndef ERROR_EMPTY
#define ERROR_EMPTY 4306L
#endif

#ifndef XUSER_MAX_COUNT
#define XUSER_MAX_COUNT 4
#endif

#ifndef BATTERY_DEVTYPE_GAMEPAD
#define BATTERY_DEVTYPE_GAMEPAD 0x00
#endif

#ifndef BATTERY_TYPE_DISCONNECTED
#define BATTERY_TYPE_DISCONNECTED 0x00
#endif

#ifndef BATTERY_TYPE_WIRED
#define BATTERY_TYPE_WIRED 0x01
#endif

#ifndef BATTERY_LEVEL_EMPTY
#define BATTERY_LEVEL_EMPTY 0x00
#endif

#ifndef BATTERY_LEVEL_FULL
#define BATTERY_LEVEL_FULL 0x03
#endif

#ifndef XINPUT_FLAG_GAMEPAD
#define XINPUT_FLAG_GAMEPAD 0x00000001
#endif

#ifndef XINPUT_DEVTYPE_GAMEPAD
#define XINPUT_DEVTYPE_GAMEPAD 0x01
#endif

#ifndef XINPUT_DEVSUBTYPE_GAMEPAD
#define XINPUT_DEVSUBTYPE_GAMEPAD 0x01
#endif

#ifndef XINPUT_GAMEPAD_DPAD_UP
#define XINPUT_GAMEPAD_DPAD_UP 0x0001
#define XINPUT_GAMEPAD_DPAD_DOWN 0x0002
#define XINPUT_GAMEPAD_DPAD_LEFT 0x0004
#define XINPUT_GAMEPAD_DPAD_RIGHT 0x0008
#define XINPUT_GAMEPAD_START 0x0010
#define XINPUT_GAMEPAD_BACK 0x0020
#define XINPUT_GAMEPAD_LEFT_THUMB 0x0040
#define XINPUT_GAMEPAD_RIGHT_THUMB 0x0080
#define XINPUT_GAMEPAD_LEFT_SHOULDER 0x0100
#define XINPUT_GAMEPAD_RIGHT_SHOULDER 0x0200
#define XINPUT_GAMEPAD_A 0x1000
#define XINPUT_GAMEPAD_B 0x2000
#define XINPUT_GAMEPAD_X 0x4000
#define XINPUT_GAMEPAD_Y 0x8000
#endif

typedef struct _XINPUT_GAMEPAD {
    WORD wButtons;
    BYTE bLeftTrigger;
    BYTE bRightTrigger;
    SHORT sThumbLX;
    SHORT sThumbLY;
    SHORT sThumbRX;
    SHORT sThumbRY;
} XINPUT_GAMEPAD;

typedef struct _XINPUT_STATE {
    DWORD dwPacketNumber;
    XINPUT_GAMEPAD Gamepad;
} XINPUT_STATE;

typedef struct _XINPUT_VIBRATION {
    WORD wLeftMotorSpeed;
    WORD wRightMotorSpeed;
} XINPUT_VIBRATION;

typedef struct _XINPUT_CAPABILITIES {
    BYTE Type;
    BYTE SubType;
    WORD Flags;
    XINPUT_GAMEPAD Gamepad;
    XINPUT_VIBRATION Vibration;
} XINPUT_CAPABILITIES;

typedef struct _XINPUT_BATTERY_INFORMATION {
    BYTE BatteryType;
    BYTE BatteryLevel;
} XINPUT_BATTERY_INFORMATION;

typedef struct _XINPUT_KEYSTROKE {
    WORD VirtualKey;
    WCHAR Unicode;
    WORD Flags;
    BYTE UserIndex;
    BYTE HidCode;
} XINPUT_KEYSTROKE;

typedef struct gamepad_io {
    int16_t lx;
    int16_t ly;
    int16_t rx;
    int16_t ry;
    int16_t lt;
    int16_t rt;
    uint8_t btn[15];
    uint8_t hat;
    uint8_t padding[4];
    uint16_t low_freq_rumble;
    uint16_t high_freq_rumble;
} gamepad_io;

typedef struct slot_state {
    HANDLE file;
    HANDLE mapping;
    volatile gamepad_io *view;
    DWORD packet_number;
    BYTE last_packet[28];
    DWORD last_retry_tick;
    char path[MAX_PATH * 4];
} slot_state;

static slot_state g_slots[XUSER_MAX_COUNT];

static void release_slot(slot_state *slot) {
    if (slot->view) {
        UnmapViewOfFile((LPCVOID)slot->view);
        slot->view = NULL;
    }
    if (slot->mapping) {
        CloseHandle(slot->mapping);
        slot->mapping = NULL;
    }
    if (slot->file && slot->file != INVALID_HANDLE_VALUE) {
        CloseHandle(slot->file);
        slot->file = INVALID_HANDLE_VALUE;
    }
    slot->path[0] = '\0';
}

static void normalize_path(char *dst, size_t dst_len, const char *src) {
    size_t i = 0;
    size_t j = 0;

    if (!src || !dst || dst_len < 4) {
        if (dst && dst_len > 0) {
            dst[0] = '\0';
        }
        return;
    }

    if (((src[0] >= 'A' && src[0] <= 'Z') || (src[0] >= 'a' && src[0] <= 'z')) && src[1] == ':') {
        for (; src[i] != '\0' && j + 1 < dst_len; ++i, ++j) {
            dst[j] = src[i] == '/' ? '\\' : src[i];
        }
    } else {
        dst[j++] = 'Z';
        dst[j++] = ':';
        for (; src[i] != '\0' && j + 1 < dst_len; ++i, ++j) {
            dst[j] = src[i] == '/' ? '\\' : src[i];
        }
    }

    while (j > 0 && (dst[j - 1] == '\\' || dst[j - 1] == '/')) {
        --j;
    }
    dst[j] = '\0';
}

static BOOL build_slot_path(DWORD user_index, char *path, size_t path_len) {
    char base[MAX_PATH * 4];
    DWORD rc = GetEnvironmentVariableA("EVSHIM_DATA_PATH", base, (DWORD)sizeof(base));
    const char *suffix = user_index == 0 ? "\\gamepad.mem" : NULL;
    char normalized[MAX_PATH * 4];

    if (path_len == 0 || rc == 0 || rc >= sizeof(base) || user_index >= XUSER_MAX_COUNT) {
        return FALSE;
    }

    normalize_path(normalized, sizeof(normalized), base);
    if (normalized[0] == '\0') {
        return FALSE;
    }

    if (suffix != NULL) {
        _snprintf(path, path_len, "%s%s", normalized, suffix);
    } else {
        _snprintf(path, path_len, "%s\\gamepad%lu.mem", normalized, (unsigned long)user_index);
    }
    path[path_len - 1] = '\0';
    return TRUE;
}

static BOOL ensure_slot(DWORD user_index) {
    slot_state *slot;
    char path[MAX_PATH * 4];
    DWORD now;

    if (user_index >= XUSER_MAX_COUNT) {
        return FALSE;
    }

    slot = &g_slots[user_index];
    if (slot->view != NULL) {
        return TRUE;
    }

    now = GetTickCount();
    if (now - slot->last_retry_tick < 250) {
        return FALSE;
    }
    slot->last_retry_tick = now;

    if (!build_slot_path(user_index, path, sizeof(path))) {
        return FALSE;
    }

    slot->file = CreateFileA(path, GENERIC_READ | GENERIC_WRITE,
        FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING,
        FILE_ATTRIBUTE_NORMAL, NULL);
    if (slot->file == INVALID_HANDLE_VALUE) {
        slot->file = NULL;
        return FALSE;
    }

    slot->mapping = CreateFileMappingA(slot->file, NULL, PAGE_READWRITE, 0, sizeof(gamepad_io), NULL);
    if (!slot->mapping) {
        release_slot(slot);
        return FALSE;
    }

    slot->view = (volatile gamepad_io *)MapViewOfFile(slot->mapping, FILE_MAP_READ | FILE_MAP_WRITE, 0, 0, sizeof(gamepad_io));
    if (!slot->view) {
        release_slot(slot);
        return FALSE;
    }

    memcpy(slot->path, path, sizeof(slot->path));
    ZeroMemory(slot->last_packet, sizeof(slot->last_packet));
    slot->packet_number = 0;
    return TRUE;
}

static BYTE normalize_trigger(int16_t value) {
    if (value <= 0) {
        return 0;
    }
    if (value >= 32767) {
        return 255;
    }
    return (BYTE)((value * 255 + 16383) / 32767);
}

static WORD build_buttons(const volatile gamepad_io *raw) {
    WORD buttons = 0;

    if (raw->btn[0]) buttons |= XINPUT_GAMEPAD_A;
    if (raw->btn[1]) buttons |= XINPUT_GAMEPAD_B;
    if (raw->btn[2]) buttons |= XINPUT_GAMEPAD_X;
    if (raw->btn[3]) buttons |= XINPUT_GAMEPAD_Y;
    if (raw->btn[4]) buttons |= XINPUT_GAMEPAD_BACK;
    if (raw->btn[6]) buttons |= XINPUT_GAMEPAD_START;
    if (raw->btn[7]) buttons |= XINPUT_GAMEPAD_LEFT_THUMB;
    if (raw->btn[8]) buttons |= XINPUT_GAMEPAD_RIGHT_THUMB;
    if (raw->btn[9]) buttons |= XINPUT_GAMEPAD_LEFT_SHOULDER;
    if (raw->btn[10]) buttons |= XINPUT_GAMEPAD_RIGHT_SHOULDER;
    if (raw->btn[11]) buttons |= XINPUT_GAMEPAD_DPAD_UP;
    if (raw->btn[12]) buttons |= XINPUT_GAMEPAD_DPAD_DOWN;
    if (raw->btn[13]) buttons |= XINPUT_GAMEPAD_DPAD_LEFT;
    if (raw->btn[14]) buttons |= XINPUT_GAMEPAD_DPAD_RIGHT;
    return buttons;
}

static DWORD fill_state(DWORD user_index, XINPUT_STATE *state) {
    slot_state *slot;
    BYTE snapshot[28];
    volatile const gamepad_io *raw;

    if (!state || user_index >= XUSER_MAX_COUNT) {
        return ERROR_BAD_ARGUMENTS;
    }
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }

    slot = &g_slots[user_index];
    raw = slot->view;

    memcpy(snapshot, (const void *)raw, sizeof(snapshot));
    if (memcmp(snapshot, slot->last_packet, sizeof(snapshot)) != 0) {
        memcpy(slot->last_packet, snapshot, sizeof(snapshot));
        ++slot->packet_number;
    }

    ZeroMemory(state, sizeof(*state));
    state->dwPacketNumber = slot->packet_number;
    state->Gamepad.wButtons = build_buttons(raw);
    state->Gamepad.bLeftTrigger = normalize_trigger(raw->lt);
    state->Gamepad.bRightTrigger = normalize_trigger(raw->rt);
    state->Gamepad.sThumbLX = raw->lx;
    state->Gamepad.sThumbLY = raw->ly;
    state->Gamepad.sThumbRX = raw->rx;
    state->Gamepad.sThumbRY = raw->ry;
    return ERROR_SUCCESS;
}

__declspec(dllexport) DWORD WINAPI XInputGetState(DWORD user_index, XINPUT_STATE *state) {
    return fill_state(user_index, state);
}

__declspec(dllexport) DWORD WINAPI XInputGetStateEx(DWORD user_index, XINPUT_STATE *state) {
    return fill_state(user_index, state);
}

__declspec(dllexport) DWORD WINAPI XInputSetState(DWORD user_index, XINPUT_VIBRATION *vibration) {
    slot_state *slot;
    if (!vibration || user_index >= XUSER_MAX_COUNT) {
        return ERROR_BAD_ARGUMENTS;
    }
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }

    slot = &g_slots[user_index];
    slot->view->low_freq_rumble = vibration->wLeftMotorSpeed;
    slot->view->high_freq_rumble = vibration->wRightMotorSpeed;
    return ERROR_SUCCESS;
}

__declspec(dllexport) DWORD WINAPI XInputGetCapabilities(DWORD user_index, DWORD flags, XINPUT_CAPABILITIES *caps) {
    if (!caps) {
        return ERROR_BAD_ARGUMENTS;
    }
    if ((flags & XINPUT_FLAG_GAMEPAD) != 0 && !ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }

    ZeroMemory(caps, sizeof(*caps));
    caps->Type = XINPUT_DEVTYPE_GAMEPAD;
    caps->SubType = XINPUT_DEVSUBTYPE_GAMEPAD;
    caps->Flags = 0;
    caps->Gamepad.wButtons = XINPUT_GAMEPAD_DPAD_UP |
        XINPUT_GAMEPAD_DPAD_DOWN |
        XINPUT_GAMEPAD_DPAD_LEFT |
        XINPUT_GAMEPAD_DPAD_RIGHT |
        XINPUT_GAMEPAD_START |
        XINPUT_GAMEPAD_BACK |
        XINPUT_GAMEPAD_LEFT_THUMB |
        XINPUT_GAMEPAD_RIGHT_THUMB |
        XINPUT_GAMEPAD_LEFT_SHOULDER |
        XINPUT_GAMEPAD_RIGHT_SHOULDER |
        XINPUT_GAMEPAD_A |
        XINPUT_GAMEPAD_B |
        XINPUT_GAMEPAD_X |
        XINPUT_GAMEPAD_Y;
    caps->Gamepad.bLeftTrigger = 0xFF;
    caps->Gamepad.bRightTrigger = 0xFF;
    caps->Gamepad.sThumbLX = 0x7FFF;
    caps->Gamepad.sThumbLY = 0x7FFF;
    caps->Gamepad.sThumbRX = 0x7FFF;
    caps->Gamepad.sThumbRY = 0x7FFF;
    caps->Vibration.wLeftMotorSpeed = 0xFFFF;
    caps->Vibration.wRightMotorSpeed = 0xFFFF;
    return ERROR_SUCCESS;
}

__declspec(dllexport) void WINAPI XInputEnable(BOOL enable) {
    (void)enable;
}

__declspec(dllexport) DWORD WINAPI XInputGetBatteryInformation(DWORD user_index, BYTE dev_type, XINPUT_BATTERY_INFORMATION *battery) {
    if (!battery || dev_type != BATTERY_DEVTYPE_GAMEPAD) {
        return ERROR_BAD_ARGUMENTS;
    }
    if (!ensure_slot(user_index)) {
        battery->BatteryType = BATTERY_TYPE_DISCONNECTED;
        battery->BatteryLevel = BATTERY_LEVEL_EMPTY;
        return ERROR_DEVICE_NOT_CONNECTED;
    }

    battery->BatteryType = BATTERY_TYPE_WIRED;
    battery->BatteryLevel = BATTERY_LEVEL_FULL;
    return ERROR_SUCCESS;
}

__declspec(dllexport) DWORD WINAPI XInputGetKeystroke(DWORD user_index, DWORD reserved, XINPUT_KEYSTROKE *keystroke) {
    (void)reserved;
    if (keystroke) {
        ZeroMemory(keystroke, sizeof(*keystroke));
    }
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }
    return ERROR_EMPTY;
}

__declspec(dllexport) DWORD WINAPI XInputGetDSoundAudioDeviceGuids(DWORD user_index, GUID *render_guid, GUID *capture_guid) {
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }
    if (render_guid) {
        ZeroMemory(render_guid, sizeof(*render_guid));
    }
    if (capture_guid) {
        ZeroMemory(capture_guid, sizeof(*capture_guid));
    }
    return ERROR_SUCCESS;
}

__declspec(dllexport) DWORD WINAPI XInputGetAudioDeviceIds(DWORD user_index,
    LPWSTR render_device_id, UINT *render_count,
    LPWSTR capture_device_id, UINT *capture_count) {
    if (!ensure_slot(user_index)) {
        return ERROR_DEVICE_NOT_CONNECTED;
    }
    if (render_count) {
        if (render_device_id && *render_count > 0) {
            render_device_id[0] = L'\0';
        }
        *render_count = 0;
    }
    if (capture_count) {
        if (capture_device_id && *capture_count > 0) {
            capture_device_id[0] = L'\0';
        }
        *capture_count = 0;
    }
    return ERROR_SUCCESS;
}

__declspec(dllexport) DWORD WINAPI XInputWaitForGuideButton(DWORD user_index, DWORD flags, void *unknown) {
    (void)user_index;
    (void)flags;
    (void)unknown;
    return ERROR_CALL_NOT_IMPLEMENTED;
}

__declspec(dllexport) DWORD WINAPI XInputCancelGuideButtonWait(DWORD user_index) {
    (void)user_index;
    return ERROR_CALL_NOT_IMPLEMENTED;
}

__declspec(dllexport) DWORD WINAPI XInputPowerOffController(DWORD user_index) {
    (void)user_index;
    return ERROR_CALL_NOT_IMPLEMENTED;
}

__declspec(dllexport) DWORD WINAPI XInputGetBaseBusInformation(DWORD user_index, void *info) {
    (void)user_index;
    (void)info;
    return ERROR_CALL_NOT_IMPLEMENTED;
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    DWORD i;
    (void)instance;
    (void)reserved;

    if (reason == DLL_PROCESS_DETACH) {
        for (i = 0; i < XUSER_MAX_COUNT; ++i) {
            release_slot(&g_slots[i]);
        }
    }
    return TRUE;
}
