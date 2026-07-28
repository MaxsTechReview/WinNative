#!/bin/sh
# Windows build (the tester itself) and a native build that writes frames plus the
# exact midpoints a perfect interpolator would produce.
set -e
cd "$(dirname "$0")"
x86_64-w64-mingw32-gcc -O2 -municode -mwindows fgtester.c -o Frame-Gen-Tester.exe -lgdi32
gcc -O2 -DFGT_HEADLESS fgtester.c -o fgtester -lm
echo "built Frame-Gen-Tester.exe and fgtester"
