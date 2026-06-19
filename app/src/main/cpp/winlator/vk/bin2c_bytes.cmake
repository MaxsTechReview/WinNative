if(NOT INPUT_FILE OR NOT OUTPUT_FILE OR NOT VAR_NAME)
    message(FATAL_ERROR "bin2c_bytes.cmake requires INPUT_FILE, OUTPUT_FILE, VAR_NAME")
endif()

file(READ "${INPUT_FILE}" hex_data HEX)
string(LENGTH "${hex_data}" hex_len)

set(bytes "")
set(line_bytes "")
set(bytes_per_line 0)

set(i 0)
while(i LESS hex_len)
    string(SUBSTRING "${hex_data}" ${i} 2 b)
    math(EXPR i "${i} + 2")
    string(APPEND line_bytes "0x${b}, ")
    math(EXPR bytes_per_line "${bytes_per_line} + 1")
    if(bytes_per_line EQUAL 16)
        string(APPEND bytes "    ${line_bytes}\n")
        set(line_bytes "")
        set(bytes_per_line 0)
    endif()
endwhile()
if(bytes_per_line GREATER 0)
    string(APPEND bytes "    ${line_bytes}\n")
endif()

file(WRITE "${OUTPUT_FILE}"
"#pragma once
#include <stdint.h>
#include <stddef.h>

static const uint8_t ${VAR_NAME}[] = {
${bytes}};
static const size_t ${VAR_NAME}_size = sizeof(${VAR_NAME});
")
