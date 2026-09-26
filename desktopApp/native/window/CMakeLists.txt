cmake_minimum_required(VERSION 3.16)

project(bitchord_window LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

foreach(suffix "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${suffix} ${CMAKE_BINARY_DIR})
endforeach()

add_library(bitchord_window SHARED window_jni.cpp)
set_target_properties(bitchord_window PROPERTIES PREFIX "")

if(DEFINED ENV{JAVA_HOME})
    target_include_directories(bitchord_window PRIVATE
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/win32
    )
else()
    find_package(JNI REQUIRED)
    target_include_directories(bitchord_window PRIVATE ${JNI_INCLUDE_DIRS})
endif()

target_link_libraries(bitchord_window PRIVATE user32 dwmapi)

if(MINGW)
    target_link_options(bitchord_window PRIVATE -static-libgcc -static-libstdc++)
endif()
