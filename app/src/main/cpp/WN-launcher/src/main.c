#include <windows.h>
#include <shellapi.h>
#include <tlhelp32.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// Continuously pins every later-spawned process to the given affinity mask.
static DWORD WINAPI affinityWatcherThread(LPVOID param) {
  DWORD_PTR mask = (DWORD_PTR)param;
  for (;;) {
    DWORD self = GetCurrentProcessId();
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
      PROCESSENTRY32 pe;
      pe.dwSize = sizeof(pe);
      if (Process32First(snap, &pe)) {
        do {
          if (pe.th32ProcessID > self) {
            HANDLE h = OpenProcess(PROCESS_SET_INFORMATION, FALSE, pe.th32ProcessID);
            if (h) {
              SetProcessAffinityMask(h, mask);
              CloseHandle(h);
            }
          }
        } while (Process32Next(snap, &pe));
      }
      CloseHandle(snap);
    }
    Sleep(1000);
  }
  return 0;
}

// Start winhandler as a child so it inherits the game's desktop.
static void startInputService(void) {
  char cmd[] = "winhandler.exe WN-launcher.exe /idle";
  STARTUPINFOA si;
  ZeroMemory(&si, sizeof(si));
  si.cb = sizeof(si);
  PROCESS_INFORMATION pi;
  ZeroMemory(&pi, sizeof(pi));
  if (CreateProcessA(NULL, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
  }
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                   LPSTR lpCmdLine, int nShowCmd) {
  (void)hInstance;
  (void)hPrevInstance;
  (void)lpCmdLine;
  (void)nShowCmd;

  int argc = __argc;
  char **argv = __argv;

  // Idle mode: stay resident as the input service's child; launch nothing.
  if (argc <= 1 || _stricmp(argv[1], "/idle") == 0) {
    Sleep(INFINITE);
    return 0;
  }

  // One-shot: pin every process named argv[2] to the mask in argv[3].
  if (argc == 4 && strcmp(argv[1], "/setaffinity") == 0) {
    const char *name = argv[2];
    DWORD_PTR mask = (DWORD_PTR)strtoul(argv[3], NULL, 16);
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap != INVALID_HANDLE_VALUE) {
      PROCESSENTRY32 pe;
      pe.dwSize = sizeof(pe);
      if (Process32First(snap, &pe)) {
        do {
          if (_stricmp(pe.szExeFile, name) == 0) {
            HANDLE h = OpenProcess(PROCESS_SET_INFORMATION, FALSE, pe.th32ProcessID);
            if (h) {
              SetProcessAffinityMask(h, mask);
              CloseHandle(h);
            }
          }
        } while (Process32Next(snap, &pe));
      }
      CloseHandle(snap);
    }
    return 0;
  }

  // Fire-and-forget: open argv[2] with the remaining args, do not wait.
  if (argc >= 3 && strcmp(argv[1], "/exec") == 0) {
    const char *file = argv[2];
    char params[2048];
    params[0] = '\0';
    for (int i = 3; i < argc; i++) {
      if (i > 3) strncat(params, " ", sizeof(params) - 1 - strlen(params));
      strncat(params, argv[i], sizeof(params) - 1 - strlen(params));
    }
    ShellExecuteA(NULL, "open", file, params[0] ? params : NULL, NULL, SW_SHOW);
    return 0;
  }

  // Default (shell) mode: [/affinity <hex>] [/dir <path>] <target> [args...].
  const char *dir = NULL;
  const char *target = NULL;
  long affinity = 0;
  int i = 1;
  for (; i < argc; i++) {
    if (strcmp(argv[i], "/affinity") == 0) {
      if (i + 1 < argc) {
        affinity = strtol(argv[i + 1], NULL, 16);
        i += 1;
      }
    } else if (strcmp(argv[i], "/dir") == 0) {
      if (i + 1 < argc) {
        dir = argv[i + 1];
        i += 1;
      }
    } else {
      target = argv[i];
      i += 1;
      break;
    }
  }
  if (!target) return 1;

  startInputService();

  char params[2048];
  params[0] = '\0';
  for (int j = i; j < argc; j++) {
    strcat(params, "\"");
    strcat(params, argv[j]);
    strcat(params, "\" ");
  }

  HANDLE hProc = NULL;
  if (dir && (strchr(dir, '[') || strchr(dir, ']'))) {
    char appPath[268];
    char cmdLine[4096];
    snprintf(appPath, sizeof(appPath), "%s\\%s", dir, target);
    snprintf(cmdLine, sizeof(cmdLine), "\"%s\"", target);
    if (params[0]) {
      strncat(cmdLine, " ", sizeof(cmdLine) - 1 - strlen(cmdLine));
      strncat(cmdLine, params, sizeof(cmdLine) - 1 - strlen(cmdLine));
    }
    STARTUPINFOA si;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_SHOW;
    PROCESS_INFORMATION pi;
    ZeroMemory(&pi, sizeof(pi));
    if (CreateProcessA(appPath, cmdLine, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
      CloseHandle(pi.hThread);
      hProc = pi.hProcess;
    }
  }

  if (!hProc) {
    SHELLEXECUTEINFOA sei;
    ZeroMemory(&sei, sizeof(sei));
    sei.cbSize = sizeof(sei);
    sei.fMask = SEE_MASK_NOCLOSEPROCESS;
    sei.lpVerb = "open";
    sei.lpFile = target;
    sei.lpParameters = params[0] ? params : NULL;
    sei.lpDirectory = dir;
    sei.nShow = SW_SHOW;
    ShellExecuteExA(&sei);
    hProc = sei.hProcess;
  }

  if (affinity > 0) {
    HANDLE t = CreateThread(NULL, 0, affinityWatcherThread,
                            (LPVOID)(LONG_PTR)affinity, 0, NULL);
    if (t) CloseHandle(t);
  }

  if (hProc) {
    WaitForSingleObject(hProc, INFINITE);
    CloseHandle(hProc);
  }
  return 0;
}
