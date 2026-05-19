# Shim that maps the Prefab `com.android.ndk.thirdparty:openssl` AAR onto
# CMake's upstream `OpenSSL::*` target convention.
#
# FetchContent-included third-party libraries (IXWebSocket, etc.) call
# `find_package(OpenSSL REQUIRED)` (capitalized) and expect the standard
# imported targets `OpenSSL::SSL` and `OpenSSL::Crypto`. The Prefab AAR
# exposes lowercase `openssl::ssl` / `openssl::crypto` instead. This shim
# bridges the two by aliasing the targets and populating the legacy module
# variables that FindOpenSSL.cmake would normally set.
#
# Loaded automatically because the parent CMakeLists prepends our `cmake/`
# directory to CMAKE_MODULE_PATH before any FetchContent_MakeAvailable.

if (NOT OpenSSL_FOUND)
    find_package(openssl CONFIG REQUIRED)

    set(OPENSSL_FOUND TRUE)
    set(OpenSSL_FOUND TRUE)

    # IXWebSocket (and most modern CMake projects that expect upstream
    # OpenSSL) call:
    #     target_include_directories(<tgt> PUBLIC $<BUILD_INTERFACE:${OPENSSL_INCLUDE_DIR}>)
    #     target_link_libraries(<tgt> PRIVATE ${OPENSSL_LIBRARIES})
    # We therefore need OPENSSL_INCLUDE_DIR to be a plain path (no
    # generator expressions) and OPENSSL_LIBRARIES to be names CMake can
    # resolve. We use the underlying Prefab `openssl::ssl`/`openssl::crypto`
    # imported targets directly here — wrapping them in ALIAS targets has
    # been shown to cause linker confusion in some CMake versions.
    if (TARGET openssl::ssl)
        get_target_property(_wn_ssl_incdir openssl::ssl INTERFACE_INCLUDE_DIRECTORIES)
        if (_wn_ssl_incdir)
            # Strip any generator expression wrappers Prefab may emit.
            string(REGEX REPLACE "\\$<[^>]+:([^>]+)>" "\\1" _wn_ssl_incdir "${_wn_ssl_incdir}")
            set(OPENSSL_INCLUDE_DIR "${_wn_ssl_incdir}")
        endif()
    endif()
    set(OPENSSL_LIBRARIES      openssl::ssl openssl::crypto)
    set(OPENSSL_SSL_LIBRARY    openssl::ssl)
    set(OPENSSL_CRYPTO_LIBRARY openssl::crypto)

    # Provide capitalized OpenSSL::SSL / OpenSSL::Crypto aliases too, for
    # downstream code that bypasses the legacy variables and links the
    # imported targets by name.
    if (TARGET openssl::ssl AND NOT TARGET OpenSSL::SSL)
        add_library(OpenSSL::SSL ALIAS openssl::ssl)
    endif()
    if (TARGET openssl::crypto AND NOT TARGET OpenSSL::Crypto)
        add_library(OpenSSL::Crypto ALIAS openssl::crypto)
    endif()

    # Version is unknown from Prefab metadata; downstream libs that gate
    # on version checks see this. Prefab AAR is 1.1.1q.
    set(OPENSSL_VERSION "1.1.1q")
endif()
