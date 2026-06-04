/* Borderless-fullscreen toggle for the foreground guest window (arg "on"/"off").
 * Build: x86_64-w64-mingw32-gcc -O2 -s -mwindows refactorsize.c -o ../../assets/winnative/refactorsize.exe -luser32 */
#include <windows.h>
#include <stdint.h>

#define STRIP_STYLES (WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_THICKFRAME)

typedef struct {
    unsigned long long hwnd;
    long style;
    long left, top, right, bottom;
} SavedState;

static void state_path(char *buf, int n) {
    char tmp[MAX_PATH];
    DWORD len = GetTempPathA(MAX_PATH, tmp);
    if (len == 0 || len >= MAX_PATH) lstrcpyA(tmp, "C:\\winnative\\");
    wsprintfA(buf, "%srefactorsize.dat", tmp);
}

static int contains(const char *hay, const char *needle) {
    if (!hay) return 0;
    for (; *hay; hay++) {
        const char *a = hay, *b = needle;
        while (*b && *a == *b) { a++; b++; }
        if (!*b) return 1;
    }
    return 0;
}

int APIENTRY WinMain(HINSTANCE inst, HINSTANCE prev, LPSTR cmd, int show) {
    char path[MAX_PATH + 32];
    state_path(path, sizeof(path));

    /* Argument is "on" or "off". Tolerate the exe path being prepended:
       neither "on" nor "off" occurs in "C:\winnative\refactorsize.exe". */
    int enable = contains(cmd, "on") && !contains(cmd, "off");

    if (enable) {
        HWND hwnd = GetForegroundWindow();
        if (!hwnd) return 1;

        LONG style = GetWindowLong(hwnd, GWL_STYLE);
        RECT r;
        GetWindowRect(hwnd, &r);

        SavedState s;
        s.hwnd = (unsigned long long)(uintptr_t)hwnd;
        s.style = (long)style;
        s.left = r.left; s.top = r.top; s.right = r.right; s.bottom = r.bottom;

        HANDLE f = CreateFileA(path, GENERIC_WRITE, 0, NULL,
                               CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (f != INVALID_HANDLE_VALUE) {
            DWORD wrote;
            WriteFile(f, &s, sizeof(s), &wrote, NULL);
            CloseHandle(f);
        }

        SetWindowLong(hwnd, GWL_STYLE, style & ~STRIP_STYLES);
        int sw = GetSystemMetrics(SM_CXSCREEN);
        int sh = GetSystemMetrics(SM_CYSCREEN);
        SetWindowPos(hwnd, HWND_TOP, 0, 0, sw, sh,
                     SWP_FRAMECHANGED | SWP_NOOWNERZORDER | SWP_SHOWWINDOW);
    } else {
        HANDLE f = CreateFileA(path, GENERIC_READ, 0, NULL,
                               OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
        if (f != INVALID_HANDLE_VALUE) {
            SavedState s;
            DWORD read = 0;
            BOOL ok = ReadFile(f, &s, sizeof(s), &read, NULL);
            CloseHandle(f);
            if (ok && read == sizeof(s)) {
                HWND hwnd = (HWND)(uintptr_t)s.hwnd;
                if (IsWindow(hwnd)) {
                    SetWindowLong(hwnd, GWL_STYLE, (LONG)s.style);
                    SetWindowPos(hwnd, HWND_TOP, s.left, s.top,
                                 s.right - s.left, s.bottom - s.top,
                                 SWP_FRAMECHANGED | SWP_NOOWNERZORDER | SWP_SHOWWINDOW);
                }
            }
            DeleteFileA(path);
        }
    }
    return 0;
}
