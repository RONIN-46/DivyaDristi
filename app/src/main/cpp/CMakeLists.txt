cmake_minimum_required(VERSION 3.22.1)

project(divyadrishti_native)

# ---------- Configure llama.cpp ----------
set(LLAMA_BUILD_COMMON ON)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_TOOLS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_APP OFF)
set(LLAMA_BUILD_UI OFF)
set(BUILD_SHARED_LIBS OFF)
# Android / GGML configuration
set(GGML_NATIVE OFF)
set(GGML_LLAMAFILE OFF)

# Path to your cloned llama.cpp
add_subdirectory(
        llama.cpp
)

# ---------- JNI Library ----------
add_library(
        llm_native
        SHARED
        llm_jni.cpp
        llm/InferenceEngine.cpp
)
target_include_directories(
        llm_native
        PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp"
        "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common"
        "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/src"
)

find_library(log-lib log)

target_link_libraries(
        llm_native
        PRIVATE
        llama
        llama-common
        ${log-lib}
)