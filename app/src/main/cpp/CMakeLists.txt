cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration -Wno-unused-parameter")

add_subdirectory(patchelf)
add_subdirectory(adrenotools)

# Add Winlator shared library
add_library(winlator SHARED
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
        xz/native_xz_stream.c
        xz/xz_embedded/xz_crc32.c
        xz/xz_embedded/xz_crc64.c
        xz/xz_embedded/xz_dec_bcj.c
        xz/xz_embedded/xz_dec_lzma2.c
        xz/xz_embedded/xz_dec_stream.c
)

target_compile_options(winlator PRIVATE -Wall -Wextra)
target_compile_definitions(winlator PRIVATE
        XZ_USE_CRC64
        XZ_DEC_CONCATENATED
        XZ_DEC_X86
        XZ_DEC_ARM
        XZ_DEC_ARMTHUMB
        XZ_DEC_ARM64
        XZ_DEC_RISCV
        XZ_DEC_POWERPC
        XZ_DEC_IA64
        XZ_DEC_SPARC
)
target_include_directories(winlator PRIVATE
        xz/xz_embedded
)

target_link_libraries(winlator
        log
        android
        jnigraphics
        aaudio
        EGL
        GLESv2
        GLESv3
        adrenotools
)

# Fake evdev input shim used by the pb_controller_fix controller path.
add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)
target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_libraries(fakeinput log dl)

# Evshim - controller support shim (loaded via LD_PRELOAD, uses dlopen for SDL2)
add_library(evshim SHARED
        winlator/evshim.c
)
target_compile_options(evshim PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_libraries(evshim log dl)
