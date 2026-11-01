



include_directories(include)

set(SOURCES
        main.cpp
        elf_loader.cpp
        elf_reader.cpp
        memory_manager.cpp
        relocator.cpp
        soinfo_manager.cpp
        utils.cpp
)

find_library(log-lib log)

# This target is loaded from Java via System.loadLibrary("elf_loader_module").
add_library(elf_loader_module SHARED ${SOURCES})

target_link_libraries(elf_loader_module ${log-lib})
