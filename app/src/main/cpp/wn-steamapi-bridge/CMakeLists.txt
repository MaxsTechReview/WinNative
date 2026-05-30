## wn-steamapi-bridge — PE32+ x86_64 replacement steam_api64.dll.
##
## Built with the MinGW cross toolchain — NOT the Android NDK. The
## main app CMakeLists builds Linux .so artifacts (libwnsteam.so,
## libwn-libsteamclient.so) via the NDK. This DLL is consumed by Wine
## inside box64 at runtime, so it must be a Windows PE binary. Driving
## it from gradle would require dragging the MinGW toolchain into the
## NDK build, which we explicitly don't want.
##
## Build manually with build.sh in this directory; the script outputs
## the PE under build/ and copies it into
## app/src/main/assets/wnsteam/steampipe/steam_api64.dll for APK
## packaging.
##
## Keep this source list aligned with build.sh. The generated .def and
## override C sources are checked in so standalone CMake users can build
## the bridge without the legacy steam_api_bridge.c shim.

cmake_minimum_required(VERSION 3.18)
project(wn_steamapi_bridge C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_SHARED_LIBRARY_PREFIX "")

if(NOT CMAKE_SYSTEM_NAME STREQUAL "Windows")
    message(FATAL_ERROR
        "wn-steamapi-bridge must be cross-compiled with a MinGW toolchain.\n"
        "Run cmake with -DCMAKE_TOOLCHAIN_FILE=mingw64.cmake or use build.sh.")
endif()

add_library(steam_api64 SHARED
    steam_api_bridge_callbacks.c
    steam_api_bridge_overrides.c
    steam_api_bridge_steamclient.c
    steam_api_bridge_lifecycle.c
    steam_api_bridge.def
)
target_link_libraries(steam_api64 PRIVATE kernel32 user32)
target_link_options(steam_api64 PRIVATE -static-libgcc -Wl,--kill-at)
set_target_properties(steam_api64 PROPERTIES
    OUTPUT_NAME "steam_api64"
    SUFFIX ".dll")
