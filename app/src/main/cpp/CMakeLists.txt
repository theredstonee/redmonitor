cmake_minimum_required(VERSION 3.22.1)
project(redmonitor_adb LANGUAGES CXX)

# BoringSSL kommt als Prefab-AAR (io.github.vvb2060.ndk:boringssl) — dieselbe Krypto-
# Bibliothek, die auch adbd fuer das Wireless-Pairing nutzt. Statisch gelinkt, damit
# die .so ohne Abhaengigkeit zu den (privaten) System-Libs auskommt.
find_package(boringssl REQUIRED CONFIG)

add_library(adbpair SHARED adb_spake2.cpp)

target_compile_options(adbpair PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_options(adbpair PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
target_link_libraries(adbpair boringssl::crypto_static log)
