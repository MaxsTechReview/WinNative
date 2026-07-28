# Frame-Gen-Tester

A 30 FPS Win64 scene for exercising Frame Generation: a character walks for 30 s,
turns, walks 30 s back, turns, and repeats — a 64 s loop — with wind moving through the
trees behind. The camera follows, so the walk phases give sustained global motion and
the spin phases give a static background with independent local motion: the two cases
that break interpolation in different ways. The forest repeats spatially, so the walk
never runs out of scenery.

Every element is a pure function of time, so rendering at `t = n + 0.5` produces the
exact frame a perfect interpolator would generate between frames `n` and `n+1`. That
makes generated frames measurable against ground truth rather than eyeballed.

A ticker along the top advances exactly one cell per frame and a yellow bar sweeps
linearly, so a duplicated, dropped, or out-of-order frame is visible directly.

## Build

    ./build.sh

`Frame-Gen-Tester.exe` is the tester. `fgtester <dir> <start-frame> <count>` is the
native build; it writes `real_NNN.ppm`, `mid_NNN.ppm` (the true midpoint) and
`q1/q2/q3_NNN.ppm` (the true 0.25/0.5/0.75 phases).

## Use

Add `Frame-Gen-Tester.exe` as a custom game in WinNative and run it with Frame
Generation on. It targets 30 FPS, so on a 120 Hz panel there are four vblanks per
content frame and 2x/3x/4x all have room.

Holding 30 FPS is the whole point — the compositor is being tested against that content
rate — so at startup it benchmarks itself and picks the largest of 1280x720, 1024x576,
854x480, 640x360 or 480x270 that renders inside the frame budget. The title bar shows
the chosen size, the measured rate and the late-frame count, and a block in the top
right corner is green while 30 FPS is being held and red when it is not. Pass a size on
the command line (`Frame-Gen-Tester.exe 854 480`) to override the choice.

Check the ticker first: it must advance one cell per real frame with no repeats or
reversals. Then watch the tree canopies (independent motion), the trunks (thin
structures) and the ground (large motion) for artifacts.

To capture what the compositor actually produced:

    adb shell setprop debug.winnative.fgdump 0
    adb shell setprop debug.winnative.fgdump 1
    adb pull /sdcard/Android/data/com.winnative.cmod/files/ fgdump/
    python3 ../fgdump_view.py fgdump/
