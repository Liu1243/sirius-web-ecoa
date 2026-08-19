# ARM64/x86_64 cross-compilation override for log4cplus.
#
# Activated by: set(log4cplus_DIR /app/cmake ...) in toolchain files.
#
# The Ubuntu cmake config (in /usr/lib/<arch>-linux-gnu/cmake/log4cplus/)
# incorrectly computes _IMPORT_PREFIX as "" when cmake loads it via the
# FIND_ROOT_PATH mechanism, producing INTERFACE_INCLUDE_DIRECTORIES "/include"
# (a non-existent path).
#
# This override uses absolute multiarch paths that are stable on Ubuntu 22.04
# with the respective liblog4cplus-dev:<arch> package installed.
#   headers  /usr/include              (arch-independent)
#   library  /usr/lib/<arch>-linux-gnu/liblog4cplus.so

if(TARGET log4cplus::log4cplus)
  return()
endif()

# Select multiarch library directory based on the cmake target processor.
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)")
  set(_l4cplus_dir "/usr/lib/aarch64-linux-gnu")
else()
  set(_l4cplus_dir "/usr/lib/x86_64-linux-gnu")
endif()

set(_l4cplus_so "${_l4cplus_dir}/liblog4cplus.so")
if(NOT EXISTS "${_l4cplus_so}")
  file(GLOB _l4cplus_so_list
    "${_l4cplus_dir}/liblog4cplus-*.so"
    "${_l4cplus_dir}/liblog4cplus.so.*"
  )
  list(GET _l4cplus_so_list 0 _l4cplus_so)
endif()

if(NOT _l4cplus_so OR NOT EXISTS "${_l4cplus_so}")
  message(FATAL_ERROR
    "log4cplus not found at ${_l4cplus_dir}/. "
    "Install liblog4cplus-dev:${CMAKE_SYSTEM_PROCESSOR} via apt-get.")
endif()

add_library(log4cplus::log4cplus SHARED IMPORTED)
set_target_properties(log4cplus::log4cplus PROPERTIES
  IMPORTED_LOCATION             "${_l4cplus_so}"
  INTERFACE_INCLUDE_DIRECTORIES "/usr/include"
  INTERFACE_LINK_LIBRARIES      "-lpthread"
)
unset(_l4cplus_so)
unset(_l4cplus_so_list)
unset(_l4cplus_dir)

set(log4cplus_FOUND   TRUE)
set(log4cplus_VERSION "2.0")
