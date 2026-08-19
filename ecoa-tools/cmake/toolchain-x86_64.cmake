# CMake toolchain file for cross-compiling ECOA LDP output to x86_64 (x86_64-linux-gnu).
# Used when the build host is ARM64 (e.g. Apple Silicon) and the target is x86_64.
# Usage: cmake -DCMAKE_TOOLCHAIN_FILE=/app/cmake/toolchain-x86_64.cmake ...

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

set(CMAKE_C_COMPILER   x86_64-linux-gnu-gcc)
set(CMAKE_CXX_COMPILER x86_64-linux-gnu-g++)
set(CMAKE_AR           x86_64-linux-gnu-ar   CACHE FILEPATH "archiver")
set(CMAKE_STRIP        x86_64-linux-gnu-strip CACHE FILEPATH "strip")

# Prevent cmake from running test executables (can't execute x86_64 on arm64 host).
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)

# Tell FindThreads to skip the header check and use -lpthread directly.
set(CMAKE_THREAD_LIBS_INIT     "-lpthread")
set(CMAKE_HAVE_THREADS_LIBRARY 1)
set(CMAKE_USE_WIN32_THREADS_INIT 0)
set(CMAKE_USE_PTHREADS_INIT    1)
set(THREADS_PREFER_PTHREAD_FLAG ON)

# Restrict library/header searches to the x86_64 multiarch prefix.
# Do NOT set CMAKE_SYSROOT — it causes cmake to strip path components from
# config-file paths, corrupting the _IMPORT_PREFIX calculation in cmake
# targets files (e.g. log4cplusTargets.cmake ends up with INTERFACE_INCLUDE
# "/include" instead of "/usr/include").
set(CMAKE_FIND_ROOT_PATH /usr/x86_64-linux-gnu)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

# Use our corrected cmake configs for cross-compilation.
# The Ubuntu arm64/x86_64 cmake config files can compute incorrect _IMPORT_PREFIX
# or conflict on headers, so we override with configs in /app/cmake/.
set(log4cplus_DIR /app/cmake CACHE PATH "" FORCE)
set(CycloneDDS_DIR /app/cmake CACHE PATH "" FORCE)
