/*
 * Steam runs gldriverquery/vulkandriverquery during app launch preflight.
 * On Android/proot we provide rendering through WinNative's own path, and
 * the stock Steam ARM64 gldriverquery currently requires a newer glibc than
 * the sniper rootfs. Report success and let the Proton wrapper own the real
 * game launch environment.
 */
int main(void) {
    return 0;
}
