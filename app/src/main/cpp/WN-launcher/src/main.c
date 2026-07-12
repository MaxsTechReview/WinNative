#include <windows.h>
#include <shellapi.h>
#include <tlhelp32.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// Counts real (visible, non-trivial) top-level windows on our desktop.
static BOOL CALLBACK countVisibleProc(HWND hwnd, LPARAM lparam) {
  if (IsWindowVisible(hwnd)) {
    RECT r;
    if (GetWindowRect(hwnd, &r) && (r.right - r.left) > 1 && (r.bottom - r.top) > 1) {
      (*(int *)lparam)++;
    }
  }
  return TRUE;
}

static int visibleWindowCount(void) {
  int count = 0;
  EnumWindows(countVisibleProc, (LPARAM)&count);
  return count;
}

// Waits for the game to exit, force-killing a leftover process once every window is gone.
static void waitForGame(HANDLE hProc) {
  int sawWindow = 0;
  int emptyTicks = 0;
  for (;;) {
    if (WaitForSingleObject(hProc, 1000) == WAIT_OBJECT_0) return;
    if (visibleWindowCount() > 0) {
      sawWindow = 1;
      emptyTicks = 0;
    } else if (sawWindow && ++emptyTicks >= 4) {
      TerminateProcess(hProc, 0);
      WaitForSingleObject(hProc, 5000);
      return;
    }
  }
}

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

static HANDLE g_serviceJob = NULL;
static DWORD g_servicePid = 0;

// Starts winhandler in a kill-on-close job so its whole tree dies the instant we exit.
static void startInputService(void) {
  char cmd[] = "winhandler.exe WN-launcher.exe /idle";
  STARTUPINFOA si;
  ZeroMemory(&si, sizeof(si));
  si.cb = sizeof(si);
  PROCESS_INFORMATION pi;
  ZeroMemory(&pi, sizeof(pi));

  HANDLE job = CreateJobObjectA(NULL, NULL);
  if (job) {
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION jeli;
    ZeroMemory(&jeli, sizeof(jeli));
    jeli.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    SetInformationJobObject(job, JobObjectExtendedLimitInformation, &jeli, sizeof(jeli));
  }

  if (CreateProcessA(NULL, cmd, NULL, NULL, FALSE, CREATE_SUSPENDED, NULL, NULL, &si, &pi)) {
    if (job) AssignProcessToJobObject(job, pi.hProcess);
    ResumeThread(pi.hThread);
    g_serviceJob = job;
    g_servicePid = pi.dwProcessId;
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
  } else if (job) {
    CloseHandle(job);
  }
}

// Tears down the input service: closing the job kills winhandler; Toolhelp is the fallback.
static void stopInputService(void) {
  if (g_serviceJob) {
    CloseHandle(g_serviceJob);
    g_serviceJob = NULL;
  }
  if (!g_servicePid) return;
  DWORD pid = g_servicePid;
  g_servicePid = 0;
  HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (snap != INVALID_HANDLE_VALUE) {
    PROCESSENTRY32 pe;
    pe.dwSize = sizeof(pe);
    if (Process32First(snap, &pe)) {
      do {
        if (pe.th32ParentProcessID == pid) {
          HANDLE c = OpenProcess(PROCESS_TERMINATE, FALSE, pe.th32ProcessID);
          if (c) {
            TerminateProcess(c, 0);
            CloseHandle(c);
          }
        }
      } while (Process32Next(snap, &pe));
    }
    CloseHandle(snap);
  }
  HANDLE h = OpenProcess(PROCESS_TERMINATE, FALSE, pid);
  if (h) {
    TerminateProcess(h, 0);
    CloseHandle(h);
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

  HANDLE gameJob = CreateJobObjectA(NULL, NULL);
  if (gameJob) {
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION jeli;
    ZeroMemory(&jeli, sizeof(jeli));
    jeli.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    SetInformationJobObject(gameJob, JobObjectExtendedLimitInformation, &jeli, sizeof(jeli));
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

  // Put the game (and everything it spawns) in the job so closing it kills the whole tree.
  if (gameJob && hProc) AssignProcessToJobObject(gameJob, hProc);

  if (affinity > 0) {
    HANDLE t = CreateThread(NULL, 0, affinityWatcherThread,
                            (LPVOID)(LONG_PTR)affinity, 0, NULL);
    if (t) CloseHandle(t);
  }

  if (hProc) {
    waitForGame(hProc);
    CloseHandle(hProc);
  }
  if (gameJob) CloseHandle(gameJob);
  stopInputService();
  return 0;
}
