cmake_minimum_required(VERSION 3.13.2)

# now build app's shared lib
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall")

add_definitions("-DVFS_FRONTEND -DHAVE_STRL -DHAVE_CHD")

# Let's include oboe
set (OBOE_DIR oboe)
add_subdirectory (${OBOE_DIR} oboe)
include_directories (${OBOE_DIR}/include)
include_directories (${OBOE_DIR}/src)

include_directories("libretro/libretro-common/include")

set (LIBRETRO_COMMON
        libretro/libretro-common/vfs/vfs_implementation.c
        libretro/libretro-common/string/stdstring.c
        libretro/libretro-common/encodings/encoding_utf.c
        libretro/libretro-common/file/file_path.c
        libretro/libretro-common/time/rtime.c
        libretro/libretro-common/streams/chd_stream.c
        libretro/libretro-common/streams/file_stream.c
        libretro/libretro-common/streams/interface_stream.c
        libretro/libretro-common/streams/memory_stream.c
        libretro/libretro-common/encodings/encoding_crc32.c
        libretro/libretro-common/formats/cdfs/cdfs.c
)

# Vendored libchdr (from beetle-psx) so RetroAchievements can hash .chd discs.
include_directories("libchdr/include")
set (LIBCHDR
        libchdr/deps/lzma-25.01/src/LzmaDec.c
        libchdr/deps/zstd-1.5.7/zstddeclib.c
        libchdr/src/libchdr_bitstream.c
        libchdr/src/libchdr_cdrom.c
        libchdr/src/libchdr_chd.c
        libchdr/src/libchdr_codec_cdfl.c
        libchdr/src/libchdr_codec_cdlz.c
        libchdr/src/libchdr_codec_cdzl.c
        libchdr/src/libchdr_codec_cdzs.c
        libchdr/src/libchdr_codec_flac.c
        libchdr/src/libchdr_codec_huff.c
        libchdr/src/libchdr_codec_lzma.c
        libchdr/src/libchdr_codec_zlib.c
        libchdr/src/libchdr_codec_zstd.c
        libchdr/src/libchdr_flac.c
        libchdr/src/libchdr_huffman.c
)

add_definitions("-DRC_CLIENT_SUPPORTS_HASH")
include_directories("rcheevos/include")
include_directories("rcheevos/src")

set (RCHEEVOS
        rcheevos/src/rc_client.c
        rcheevos/src/rc_client_external.c
        rcheevos/src/rc_compat.c
        rcheevos/src/rc_libretro.c
        rcheevos/src/rc_util.c
        rcheevos/src/rc_version.c
        rcheevos/src/rapi/rc_api_common.c
        rcheevos/src/rapi/rc_api_editor.c
        rcheevos/src/rapi/rc_api_info.c
        rcheevos/src/rapi/rc_api_runtime.c
        rcheevos/src/rapi/rc_api_user.c
        rcheevos/src/rcheevos/alloc.c
        rcheevos/src/rcheevos/condition.c
        rcheevos/src/rcheevos/condset.c
        rcheevos/src/rcheevos/consoleinfo.c
        rcheevos/src/rcheevos/format.c
        rcheevos/src/rcheevos/lboard.c
        rcheevos/src/rcheevos/memref.c
        rcheevos/src/rcheevos/operand.c
        rcheevos/src/rcheevos/rc_validate.c
        rcheevos/src/rcheevos/richpresence.c
        rcheevos/src/rcheevos/runtime.c
        rcheevos/src/rcheevos/runtime_progress.c
        rcheevos/src/rcheevos/trigger.c
        rcheevos/src/rcheevos/value.c
        rcheevos/src/rhash/aes.c
        rcheevos/src/rhash/cdreader.c
        rcheevos/src/rhash/hash.c
        rcheevos/src/rhash/hash_disc.c
        rcheevos/src/rhash/hash_encrypted.c
        rcheevos/src/rhash/hash_rom.c
        rcheevos/src/rhash/hash_zip.c
        rcheevos/src/rhash/md5.c
)

add_library(libretrodroid SHARED
        libretrodroidjni.h
        libretrodroidjni.cpp
        libretrodroid.h
        libretrodroid.cpp
        log.h
        core.h
        core.cpp
        video.h
        video.cpp
        immersivemode.h
        immersivemode.cpp
        videolayout.h
        videolayout.cpp
        renderers/renderer.h
        renderers/renderer.cpp
        renderers/es3/es3utils.h
        renderers/es3/es3utils.cpp
        renderers/es3/framebufferrenderer.h
        renderers/es3/framebufferrenderer.cpp
        renderers/es2/imagerendereres2.h
        renderers/es2/imagerendereres2.cpp
        renderers/es3/imagerendereres3.h
        renderers/es3/imagerendereres3.cpp
        audio.h
        audio.cpp
        resamplers/resampler.h
        resamplers/linearresampler.h
        resamplers/linearresampler.cpp
        resamplers/sincresampler.h
        resamplers/sincresampler.cpp
        fpssync.h
        fpssync.cpp
        environment.h
        environment.cpp
        netpacket.h
        netpacket.cpp
        input.h
        input.cpp
        shadermanager.h
        shadermanager.cpp
        rumble.h
        rumble.cpp
        rumblestate.h
        rumblestate.cpp
        utils/javautils.h
        utils/javautils.cpp
        utils/utils.cpp
        utils/utils.h
        utils/jnistring.h
        utils/jnistring.cpp
        utils/libretrodroidexception.h
        utils/libretrodroidexception.cpp
        utils/rect.h
        utils/rect.cpp
        errorcodes.h
        errorcodes.cpp
        vfs/vfs.h
        vfs/vfs.cpp
        vfs/vfsfile.h
        vfs/vfsfile.cpp
        vfs/fdwrapper.h
        vfs/fdwrapper.cpp
        microphone/microphone.h
        microphone/microphone.cpp
        microphone/microphoneinterface.h
        microphone/microphoneinterface.cpp
        retroachievements.cpp
        ${LIBRETRO_COMMON}
        ${LIBCHDR}
        ${RCHEEVOS}
)

# Support 16k pages for Android 15
target_link_options(libretrodroid PRIVATE "-Wl,-z,max-page-size=16384")

# add lib dependencies
target_link_libraries(libretrodroid
                      android
                      log
                      EGL
                      oboe
                      GLESv3
                      z
)
