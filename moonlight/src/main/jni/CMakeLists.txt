# PocketDeck: CMake port of upstream's moonlight-core/Android.mk (ndk-build cannot build from
# paths containing spaces). Same sources, flags and prebuilt libraries as upstream.
cmake_minimum_required(VERSION 3.22.1)
project(moonlight-core C)

set(CORE ${CMAKE_CURRENT_SOURCE_DIR}/moonlight-core)
set(COMMON ${CORE}/moonlight-common-c)

add_library(opus STATIC IMPORTED)
set_target_properties(opus PROPERTIES IMPORTED_LOCATION ${CORE}/opus/${ANDROID_ABI}/libopus.a)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CORE}/openssl/${ANDROID_ABI}/libcrypto.a)

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()

add_library(moonlight-core SHARED
    ${COMMON}/src/AudioStream.c
    ${COMMON}/src/ByteBuffer.c
    ${COMMON}/src/Connection.c
    ${COMMON}/src/ConnectionTester.c
    ${COMMON}/src/ControlStream.c
    ${COMMON}/src/FakeCallbacks.c
    ${COMMON}/src/InputStream.c
    ${COMMON}/src/LinkedBlockingQueue.c
    ${COMMON}/src/Misc.c
    ${COMMON}/src/Platform.c
    ${COMMON}/src/PlatformCrypto.c
    ${COMMON}/src/PlatformSockets.c
    ${COMMON}/src/RtpAudioQueue.c
    ${COMMON}/src/RtpVideoQueue.c
    ${COMMON}/src/RtspConnection.c
    ${COMMON}/src/RtspParser.c
    ${COMMON}/src/SdpGenerator.c
    ${COMMON}/src/SimpleStun.c
    ${COMMON}/src/VideoDepacketizer.c
    ${COMMON}/src/VideoStream.c
    ${COMMON}/nanors/deps/obl/oblas_common.c
    ${COMMON}/nanors/deps/obl/oblas_lite.c
    ${COMMON}/nanors/rs.c
    ${COMMON}/enet/callbacks.c
    ${COMMON}/enet/compress.c
    ${COMMON}/enet/host.c
    ${COMMON}/enet/list.c
    ${COMMON}/enet/packet.c
    ${COMMON}/enet/peer.c
    ${COMMON}/enet/protocol.c
    ${COMMON}/enet/unix.c
    ${COMMON}/enet/win32.c
    ${CORE}/simplejni.c
    ${CORE}/callbacks.c
    ${CORE}/minisdl.c
)

target_include_directories(moonlight-core PRIVATE
    ${COMMON}/enet/include
    ${COMMON}/nanors/deps/obl
    ${COMMON}/nanors
    ${COMMON}/src
    ${CORE}/opus/include
    ${CORE}/openssl/include
)

target_compile_definitions(moonlight-core PRIVATE HAS_SOCKLEN_T=1 LC_ANDROID HAVE_CLOCK_GETTIME=1)
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(moonlight-core PRIVATE LC_DEBUG)
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # Upstream: LOCAL_BRANCH_PROTECTION := standard
    target_compile_options(moonlight-core PRIVATE -mbranch-protection=standard)
endif()

target_link_libraries(moonlight-core PRIVATE opus crypto cpufeatures log)
target_link_options(moonlight-core PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
