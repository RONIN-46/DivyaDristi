cmake_minimum_required(VERSION 3.22.1)

project(divyadrishti_native)

# ---------- Configure llama.cpp ----------
set(LLAMA_BUILD_COMMON OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(LLAMA_BUILD_UI OFF)
set(BUILD_SHARED_LIBS OFF)

# Path to your cloned llama.cpp
add_subdirectory(
        "D:/Divya FinalEN/llama.cpp"
        "${CMAKE_BINARY_DIR}/llama_build"
)

# ---------- JNI Library ----------
add_library(
        llm_native
        SHARED
        llm_jni.cpp
)

find_library(log-lib log)

target_link_libraries(
        llm_native
        PRIVATE
        llama
        ${log-lib}
)