# CMake toolchain file for cross-compiling ECOA LDP output to ARM64 (aarch64-linux-gnu).
# Usage: cmake -DCMAKE_TOOLCHAIN_FILE=/app/cmake/toolchain-aarch64.cmake ...

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)

set(CMAKE_C_COMPILER   aarch64-linux-gnu-gcc)
set(CMAKE_CXX_COMPILER aarch64-linux-gnu-g++)
set(CMAKE_AR           aarch64-linux-gnu-ar   CACHE FILEPATH "archiver")
set(CMAKE_STRIP        aarch64-linux-gnu-strip CACHE FILEPATH "strip")

# ---------------------------------------------------------------------------
# Critical: prevent CMake from linking and running test executables.
# During cross-compilation the build machine (x86) cannot execute arm64
# binaries, so any try_compile / try_run that produces a linked binary would
# fail or hang.  STATIC_LIBRARY mode compiles only — no linking, no running.
# ---------------------------------------------------------------------------
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# ---------------------------------------------------------------------------
# FindThreads: bypass header detection and use -lpthread directly.
# Without this, FindThreads tries to compile a pthreads test program,
# which requires pthread.h from the arm64 sysroot — but the host pkg-config
# paths point to x86 headers and the check fails.
# ---------------------------------------------------------------------------
set(CMAKE_THREAD_LIBS_INIT     "-lpthread")
set(CMAKE_HAVE_THREADS_LIBRARY 1)
set(CMAKE_USE_WIN32_THREADS_INIT 0)
set(CMAKE_USE_PTHREADS_INIT    1)
set(THREADS_PREFER_PTHREAD_FLAG ON)

# ---------------------------------------------------------------------------
# Search paths: restrict library/header searches to the arm64 multiarch
# prefix installed by dpkg --add-architecture arm64.
#
# Note: CMAKE_SYSROOT is intentionally NOT set here.  When CMAKE_SYSROOT is
# set, cmake strips the sysroot prefix from cmake config-file paths during
# loading, making the arm64 cmake config (physically at
# /usr/lib/aarch64-linux-gnu/cmake/log4cplus/) appear to be only 4 directories
# deep from root instead of 5.  The log4cplusTargets.cmake file then computes
# _IMPORT_PREFIX as "" (4 × PATH-up lands on /) and the include path becomes
# "/include" — a path that does not exist.  CMAKE_FIND_ROOT_PATH alone is
# sufficient to restrict all find_* calls without the path-stripping side-effect.
# ---------------------------------------------------------------------------
set(CMAKE_FIND_ROOT_PATH /usr/aarch64-linux-gnu)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
# BOTH: cmake config files often contain absolute paths already correct for the
# target, and ONLY mode remaps <Pkg>_DIR so our override at /app/cmake is missed.
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

# ---------------------------------------------------------------------------
# log4cplus / CycloneDDS: bypass the Ubuntu arm64 cmake configs which either
# compute incorrect _IMPORT_PREFIX or conflict with amd64 headers.
# Point cmake to our corrected configs in /app/cmake/.
# ---------------------------------------------------------------------------
set(log4cplus_DIR /app/cmake CACHE PATH "" FORCE)
set(CycloneDDS_DIR /app/cmake CACHE PATH "" FORCE)
