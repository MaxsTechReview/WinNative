# ----------------------------------------------------------------------------
# wn-libsteamclient
# Rust-driven libsteamclient.so. Hosts Valve's Steam SDK ABI surface for the
# Wine `lsteamclient.dll` bridge inside the Proton prefix. Forwards CM-side
# work to libwnsteam.so over the wn_cm_* Rust-to-Rust C-ABI bridge.
#
# SONAME stays `libsteamclient.so` exactly so the staged file at
# <filesDir>/imagefs/usr/lib/libsteamclient.so behaves as a drop-in for Valve's
# binary.
# ----------------------------------------------------------------------------

cmake_minimum_required(VERSION 3.22.1)

find_program(CARGO_EXECUTABLE cargo REQUIRED)

if(NOT CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Rust wn_libsteamclient currently supports arm64-v8a; got ${CMAKE_ANDROID_ARCH_ABI}")
endif()
if(NOT DEFINED ANDROID_NDK)
    message(FATAL_ERROR "ANDROID_NDK not defined; Rust wn_libsteamclient must be built via Android Gradle/CMake")
endif()

set(WNLSC_RUST_TARGET "aarch64-linux-android")
if(DEFINED ANDROID_PLATFORM)
    string(REGEX REPLACE "^android-" "" WNLSC_ANDROID_API "${ANDROID_PLATFORM}")
elseif(DEFINED CMAKE_SYSTEM_VERSION)
    set(WNLSC_ANDROID_API "${CMAKE_SYSTEM_VERSION}")
else()
    set(WNLSC_ANDROID_API "26")
endif()

if(CMAKE_HOST_WIN32)
    set(WNLSC_NDK_HOST_TAG "windows-x86_64")
    set(WNLSC_LINKER_SUFFIX ".cmd")
    set(WNLSC_AR_SUFFIX ".exe")
elseif(CMAKE_HOST_APPLE)
    set(WNLSC_NDK_HOST_TAG "darwin-x86_64")
    set(WNLSC_LINKER_SUFFIX "")
    set(WNLSC_AR_SUFFIX "")
else()
    set(WNLSC_NDK_HOST_TAG "linux-x86_64")
    set(WNLSC_LINKER_SUFFIX "")
    set(WNLSC_AR_SUFFIX "")
endif()

set(WNLSC_RUST_DIR "${CMAKE_CURRENT_SOURCE_DIR}/rust")
file(GLOB_RECURSE WNLSC_RUST_SOURCES CONFIGURE_DEPENDS
    "${WNLSC_RUST_DIR}/src/*.rs")
set(WNLSC_RUST_BUILD_INPUTS
    "${WNLSC_RUST_DIR}/Cargo.toml"
    "${WNLSC_RUST_DIR}/exports.lds"
    ${WNLSC_RUST_SOURCES})
set(WNLSC_RUST_LIB
    "${WNLSC_RUST_DIR}/target/${WNLSC_RUST_TARGET}/release/libwn_libsteamclient.so")
set(WNLSC_RUST_OUT "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libsteamclient.so")
set(WNLSC_RUST_LINKER
    "${ANDROID_NDK}/toolchains/llvm/prebuilt/${WNLSC_NDK_HOST_TAG}/bin/${WNLSC_RUST_TARGET}${WNLSC_ANDROID_API}-clang${WNLSC_LINKER_SUFFIX}")

add_custom_command(
    OUTPUT "${WNLSC_RUST_OUT}"
    COMMAND "${CMAKE_COMMAND}" -E env
        "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=${WNLSC_RUST_LINKER}"
        "CC_aarch64_linux_android=${WNLSC_RUST_LINKER}"
        "AR_aarch64_linux_android=${ANDROID_NDK}/toolchains/llvm/prebuilt/${WNLSC_NDK_HOST_TAG}/bin/llvm-ar${WNLSC_AR_SUFFIX}"
        "${CARGO_EXECUTABLE}" rustc --release --target "${WNLSC_RUST_TARGET}" --lib --
        -Clink-arg=-Wl,-soname,libsteamclient.so
        -Clink-arg=-Wl,--no-undefined
        -Clink-arg=-Wl,--version-script=${WNLSC_RUST_DIR}/exports.lds
        -Clink-arg=-L${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        -Clink-arg=-lwnsteam
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different
        "${WNLSC_RUST_LIB}"
        "${WNLSC_RUST_OUT}"
    WORKING_DIRECTORY "${WNLSC_RUST_DIR}"
    DEPENDS ${WNLSC_RUST_BUILD_INPUTS} wnsteam_rust_build
    COMMENT "Building Rust wn_libsteamclient for ${WNLSC_RUST_TARGET}"
    VERBATIM
)

add_custom_target(wn_libsteamclient_rust_build DEPENDS "${WNLSC_RUST_OUT}")
add_library(wn_libsteamclient SHARED IMPORTED GLOBAL)
set_target_properties(wn_libsteamclient PROPERTIES
    IMPORTED_LOCATION "${WNLSC_RUST_OUT}"
    IMPORTED_SONAME "libsteamclient.so")
add_dependencies(wn_libsteamclient wn_libsteamclient_rust_build)
