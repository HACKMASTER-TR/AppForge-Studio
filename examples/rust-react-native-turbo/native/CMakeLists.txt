cmake_minimum_required(VERSION 3.22.1)
project(appmodules)
add_library(appforge_jsi SHARED IMPORTED)
set_target_properties(appforge_jsi PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libappforge_jsi.so")
include(${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake)
target_link_libraries(${CMAKE_PROJECT_NAME} appforge_jsi)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
