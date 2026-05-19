#define _GNU_SOURCE

#include <sys/fsuid.h>
#include <sys/types.h>
#include <unistd.h>

/*
 * Xorg's xkbcomp Popen child drops privileges with setgid(getgid()) and
 * setuid(getuid()) before execve. In Android's app sandbox those no-op drops
 * fail with EPERM because the process has no CAP_SETGID/CAP_SETUID. This shim
 * is LD_PRELOADed only for bundled Xvfb so the child can reach execve.
 */

int setgid(gid_t gid) {
  (void)gid;
  return 0;
}

int setuid(uid_t uid) {
  (void)uid;
  return 0;
}

int setregid(gid_t rgid, gid_t egid) {
  (void)rgid;
  (void)egid;
  return 0;
}

int setreuid(uid_t ruid, uid_t euid) {
  (void)ruid;
  (void)euid;
  return 0;
}

int setresgid(gid_t rgid, gid_t egid, gid_t sgid) {
  (void)rgid;
  (void)egid;
  (void)sgid;
  return 0;
}

int setresuid(uid_t ruid, uid_t euid, uid_t suid) {
  (void)ruid;
  (void)euid;
  (void)suid;
  return 0;
}

int setfsgid(gid_t fsgid) {
  (void)fsgid;
  return 0;
}

int setfsuid(uid_t fsuid) {
  (void)fsuid;
  return 0;
}
