# ARM64 cross-compilation override for CycloneDDS (DDS protocol library).
#
# Activated by: set(CycloneDDS_DIR /app/cmake ...) in toolchain files.
#
# On cross-compilation Docker images only cyclonedds-dev:amd64 is installed
# (cyclonedds-dev:arm64 conflicts on /usr/include/ headers).  This override
# creates the CycloneDDS::ddsc IMPORTED target pointing at the arm64 .so,
# which is installed manually as libddsc0:arm64 in the Dockerfile.
#
#   headers  /usr/include              (arch-independent, from amd64 -dev pkg)
#   library  /usr/lib/aarch64-linux-gnu/libddsc.so

if(TARGET CycloneDDS::ddsc)
  return()
endif()

# Select multiarch directory based on the cmake target processor.
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)")
  set(_ddsc_dir "/usr/lib/aarch64-linux-gnu")
else()
  set(_ddsc_dir "/usr/lib/x86_64-linux-gnu")
endif()

set(_ddsc_so "${_ddsc_dir}/libddsc.so")
if(NOT EXISTS "${_ddsc_so}")
  # Fall back to the system-installed cmake config
  find_package(CycloneDDS QUIET)
  if(CycloneDDS_FOUND)
    return()
  endif()
  message(FATAL_ERROR
    "CycloneDDS not found at ${_ddsc_so}. "
    "Install libddsc-dev:${CMAKE_SYSTEM_PROCESSOR} via apt-get.")
endif()

# CycloneDDS::ddsc — the C DDS library
add_library(CycloneDDS::ddsc SHARED IMPORTED)
# CycloneDDS headers: dds/dds.h is arch-independent (lives in /usr/include, from
# the host arch's cyclonedds-dev).  dds/features.h is multiarch — it lives in
# /usr/include/<arch>-linux-gnu/dds/features.h and is pure compile-time feature
# flags (#define DDS_HAS_*), NOT architecture-specific.
#
# Only ONE arch's cyclonedds-dev can be installed (arm64 + amd64 conflict on
# /usr/include/dds/dds.h), so when cross-compiling the TARGET arch's features.h
# is usually absent (e.g. x86 host → arm64 target: only x86_64 has it).
#
# CRITICAL: we must NOT add another arch's multiarch include dir
# (/usr/include/<other-arch>-linux-gnu) to the include path.  Those dirs contain
# that arch's FULL glibc headers (bits/, gnu/, sys/, stdlib.h ...).  Mixing them
# into a cross-compile breaks glibc: the target's features.h ends up #including
# the wrong arch's gnu/stubs.h → "gnu/stubs-32.h: No such file or directory".
#
# Fix: locate features.h in any arch's multiarch dir and COPY just that one file
# into a private build dir.  Only the private dir (containing dds/features.h
# alone, no glibc) is added to the include path — never a multiarch dir.
set(_ddsc_include_dirs "/usr/include")

set(_ddsc_features_src "")
foreach(_try_arch "${CMAKE_SYSTEM_PROCESSOR}-linux-gnu" "x86_64-linux-gnu" "aarch64-linux-gnu" "arm-linux-gnueabihf")
  set(_try_file "/usr/include/${_try_arch}/dds/features.h")
  if(EXISTS "${_try_file}")
    set(_ddsc_features_src "${_try_file}")
    break()
  endif()
endforeach()

if(_ddsc_features_src)
  set(_ddsc_features_dir "${CMAKE_BINARY_DIR}/_cyclonedds_features")
  file(MAKE_DIRECTORY "${_ddsc_features_dir}/dds")
  file(COPY "${_ddsc_features_src}" DESTINATION "${_ddsc_features_dir}/dds")
  list(APPEND _ddsc_include_dirs "${_ddsc_features_dir}")
else()
  message(WARNING
    "CycloneDDS dds/features.h not found in any multiarch include dir; "
    "DDS sources may fail to compile. Install cyclonedds-dev.")
endif()

set_target_properties(CycloneDDS::ddsc PROPERTIES
  IMPORTED_LOCATION             "${_ddsc_so}"
  INTERFACE_INCLUDE_DIRECTORIES "${_ddsc_include_dirs}"
)

# CycloneDDS::idlc — IDL compiler (required by idlc_generate cmake macro)
# For cross-compilation, use the native (host) idlc — it only generates code.
find_program(_idlc_bin idlc PATHS /usr/bin NO_DEFAULT_PATH)
if(_idlc_bin)
  add_executable(CycloneDDS::idlc IMPORTED)
  set_target_properties(CycloneDDS::idlc PROPERTIES
    IMPORTED_LOCATION "${_idlc_bin}"
  )
endif()

# idlc_generate macro — load from any installed CycloneDDS cmake config.
# The idlc binary is architecture-independent (just generates .c/.h from .idl).
# Try both arch dirs — the native cyclonedds-dev package always provides this file.
set(_idlc_generate "")
foreach(_try_arch "x86_64-linux-gnu" "aarch64-linux-gnu")
  if(EXISTS "/usr/lib/${_try_arch}/cmake/CycloneDDS/idlc/Generate.cmake")
    set(_idlc_generate "/usr/lib/${_try_arch}/cmake/CycloneDDS/idlc/Generate.cmake")
    break()
  endif()
endforeach()
if(_idlc_generate)
  include("${_idlc_generate}")
endif()

set(CycloneDDS_FOUND TRUE)
unset(_ddsc_so)
unset(_ddsc_dir)
unset(_ddsc_include_dirs)
unset(_ddsc_features_src)
unset(_ddsc_features_dir)
unset(_try_arch)
unset(_try_file)
unset(_idlc_bin)
