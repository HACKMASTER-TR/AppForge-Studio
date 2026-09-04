cmake_minimum_required(VERSION 3.22.1)

project(appforge_pty C)

add_library(
    appforge_pty
    SHARED
    appforge_pty.c
)

target_compile_features(
    appforge_pty
    PRIVATE
    c_std_17
)

target_compile_options(
    appforge_pty
    PRIVATE
    -Wall
    -Wextra
    -Werror
    -fstack-protector-strong
)
