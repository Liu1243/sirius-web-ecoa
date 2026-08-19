# Create imported target log4cplus::log4cplus
add_library(log4cplus::log4cplus SHARED IMPORTED)

# Guard: if log4cplus_ROOT_PATH was never set (e.g. cmake_config.cmake not loaded),
# default to /usr so the search stays scoped instead of searching from root.
if(NOT log4cplus_ROOT_PATH OR log4cplus_ROOT_PATH STREQUAL "")
  set(log4cplus_ROOT_PATH "/usr")
endif()

# Select the correct multiarch library directory based on the target processor.
# On cross-compilation Docker images both aarch64 and x86_64 -dev packages are
# installed; a naive glob picks the wrong one (alphabetically aarch64 < x86_64).
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)")
  set(_l4cplus_arch "aarch64-linux-gnu")
else()
  set(_l4cplus_arch "x86_64-linux-gnu")
endif()

# Try architecture-specific path first, then plain lib/ (non-multiarch systems).
# Direct path construction avoids find_library sysroot remapping issues when
# cross-compiling with CMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY.
set(_LOG4CPLUS_LIB "${log4cplus_ROOT_PATH}/lib/${_l4cplus_arch}/liblog4cplus.so")
if(NOT EXISTS "${_LOG4CPLUS_LIB}")
  set(_LOG4CPLUS_LIB "${log4cplus_ROOT_PATH}/lib/liblog4cplus.so")
endif()

if(NOT EXISTS "${_LOG4CPLUS_LIB}")
  message(FATAL_ERROR
    "log4cplus not found at ${log4cplus_ROOT_PATH}/lib/${_l4cplus_arch}/ or ${log4cplus_ROOT_PATH}/lib/. "
    "Install liblog4cplus-dev for ${CMAKE_SYSTEM_PROCESSOR} via apt-get.")
endif()

set(LOG4CPLUS_LIB "${_LOG4CPLUS_LIB}")

set_target_properties(log4cplus::log4cplus PROPERTIES
  INTERFACE_INCLUDE_DIRECTORIES "${log4cplus_ROOT_PATH}/include"
  IMPORTED_LOCATION "${LOG4CPLUS_LIB}"
  INTERFACE_LINK_LIBRARIES "Threads::Threads"
)

