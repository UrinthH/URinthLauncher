
cmake_minimum_required(VERSION 3.22.1)

# ------ MojoExec ------

add_subdirectory(mojoexec)

# ------   GLFW   ------

set(GLFW_BUILD_WAYLAND  OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_X11      OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_ANDROID  ON  CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(GLFW_INSTALL        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS   ON  CACHE BOOL "" FORCE)

add_subdirectory(glfw)

target_compile_options(glfw PRIVATE -Wno-nullability-completeness)

# ------   SDL   ------

set(SDL_SHARED       ON CACHE BOOL "" FORCE)
set(SDL_VIDEO        ON CACHE BOOL "" FORCE)
set(SDL_AUDIO		 ON CACHE BOOL "" FORCE)
set(SDL_STATIC       OFF CACHE BOOL "" FORCE)
set(SDL_CAMERA		 OFF CACHE BOOL "" FORCE)
set(SDL_TESTS		 OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES	 OFF CACHE BOOL "" FORCE)
set(SDL3_MAINPROJECT OFF CACHE BOOL "" FORCE)
add_subdirectory(sdl)
target_link_libraries(SDL3-shared PRIVATE mojoexec)

# ----------------------

project("MojoLauncher")

add_library(pojavexec SHARED
        affinity.c
        opensl_preset.c
        opensl_preset_hook.c
        glibc_shim.c
        glibc_shim_names.c
        jvm_hooks/emui_iterator_fix_hook.c
        jvm_hooks/java_exec_hooks.c
        jvm_hooks/lwjgl_dlopen_hook.c
        jre_launcher/jre_launcher.c
        jre_launcher/native_library_hook.c
        jre_launcher/elf_hinter.c
        jre_launcher/abort_wait.c
        utils.c
        stdio_is.c
        minibridge.c
)

target_include_directories(pojavexec PRIVATE .)
target_link_libraries(pojavexec PRIVATE log mojoexec)

add_library(pojavexec_awt SHARED
		awt/awt.c
		awt/awt_window.c
		awt/awt_util.c
		awt/awt_input.c
		awt/awt_clipboard.c
        awt/awt_mapper.c
)

target_link_libraries(pojavexec_awt PRIVATE android log)

add_library(awt_headless SHARED 
	awt_xawt/dummy.c
)

add_library(awt_xawt SHARED
        awt_xawt/xawt_fake.c
)
target_link_libraries(awt_xawt PRIVATE awt_headless)

# ------ Names for Linux (glibc) libraries, see glibc_shim.h ------
# Each is an empty library that announces a glibc file name and depends on the Android library
# that has the same functions. --no-as-needed keeps that dependency, which is what puts the
# Android functions in reach of the library that asks for the glibc name.
foreach(shim IN ITEMS "m;libm.so.6;m;cos" "c;libc.so.6;c;getpid" "dl;libdl.so.2;dl;dlopen" "pthread;libpthread.so.0;c;pthread_self")
    list(GET shim 0 shim_id)
    list(GET shim 1 shim_soname)
    list(GET shim 2 shim_depends)
    list(GET shim 3 shim_symbol)
    add_library(glibc_shim_${shim_id} SHARED glibc_shim_stub.c)
    target_compile_definitions(glibc_shim_${shim_id} PRIVATE SHIM_SYMBOL=${shim_symbol})
    target_link_options(glibc_shim_${shim_id} PRIVATE "-Wl,-soname,${shim_soname}" "-Wl,--no-as-needed")
    target_link_libraries(glibc_shim_${shim_id} PRIVATE ${shim_depends})
endforeach()
