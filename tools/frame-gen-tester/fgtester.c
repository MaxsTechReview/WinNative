// Frame Gen test scene. Everything is a pure function of time, so rendering at
// t = n + 0.5 gives the exact frame a perfect interpolator would produce.
//
//   x86_64-w64-mingw32-gcc -O2 -municode -mwindows fgtester.c -o Frame-Gen-Tester.exe -lgdi32
//   gcc -O2 -DFGT_HEADLESS fgtester.c -o fgtester -lm      (writes PPM frames + midpoints)

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define FGW 1280
#define FGH 720
#define FPS 30.0
#define HORIZON (FGH * 0.52f)
#define FOCAL 620.0f
#define EYE_H 1.65f

static unsigned char fb[FGH][FGW][4];   /* BGRA, matches a Win32 DIB */

static float clampf(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
static float smoothstepf(float a, float b, float x) {
    float t = clampf((x - a) / (b - a), 0.f, 1.f);
    return t * t * (3.f - 2.f * t);
}
static float fract(float x) { return x - floorf(x); }
static float hash1(float n) { return fract(sinf(n * 12.9898f) * 43758.5453f); }

static void px(int x, int y, float r, float g, float b, float a) {
    if (x < 0 || y < 0 || x >= FGW || y >= FGH || a <= 0.f) return;
    unsigned char *p = fb[y][x];
    p[2] = (unsigned char)(clampf(p[2] / 255.f * (1 - a) + r * a, 0, 1) * 255.f);
    p[1] = (unsigned char)(clampf(p[1] / 255.f * (1 - a) + g * a, 0, 1) * 255.f);
    p[0] = (unsigned char)(clampf(p[0] / 255.f * (1 - a) + b * a, 0, 1) * 255.f);
    p[3] = 255;
}

/* ---- motion script -------------------------------------------------------
   walk right, spin, walk back, spin, repeat. One loop is LOOP_T seconds. */
#define WALK_T 4.0f
#define SPIN_T 1.6f
#define LOOP_T (2 * WALK_T + 2 * SPIN_T)
#define WALK_SPEED 3.6f

static void character_state(float t, float *wx, float *facing, float *phase, float *spin) {
    float u = fmodf(t, LOOP_T);
    if (u < 0) u += LOOP_T;
    float x = 0, sp = 0, walking = 0;
    if (u < WALK_T) {
        x = u * WALK_SPEED; sp = 0; walking = 1;
    } else if (u < WALK_T + SPIN_T) {
        x = WALK_T * WALK_SPEED; sp = (u - WALK_T) / SPIN_T * 6.2831853f;
    } else if (u < 2 * WALK_T + SPIN_T) {
        float v = u - WALK_T - SPIN_T;
        x = WALK_T * WALK_SPEED - v * WALK_SPEED; sp = 0; walking = -1;
    } else {
        x = 0; sp = (u - 2 * WALK_T - SPIN_T) / SPIN_T * 6.2831853f;
    }
    *wx = x;
    *facing = walking;
    *spin = sp;
    /* stride phase advances with distance so the feet never slide */
    *phase = (walking != 0.f ? x : (walking == 0.f ? WALK_T * WALK_SPEED * 0.f + x : x)) * 1.9f;
}

/* ---- ground --------------------------------------------------------------- */
static void draw_world(float t, float camx) {
    for (int y = 0; y < FGH; y++) {
        float sky = (float)y / HORIZON;
        for (int x = 0; x < FGW; x++) {
            if (y < HORIZON) {
                float g = clampf(sky, 0, 1);
                float r0 = 0.35f + 0.42f * g, g0 = 0.55f + 0.32f * g, b0 = 0.86f - 0.06f * g;
                /* slow clouds so the sky is not perfectly static */
                float cx = x * 0.0035f + t * 0.06f, cy = y * 0.006f;
                float c = smoothstepf(0.55f, 0.95f,
                          0.5f + 0.5f * sinf(cx * 1.7f + sinf(cy * 2.3f + t * 0.1f) * 1.4f)
                                     * cosf(cy * 3.1f - t * 0.05f));
                c *= smoothstepf(0.0f, 0.55f, sky);
                fb[y][x][2] = (unsigned char)(clampf(r0 + c * 0.30f, 0, 1) * 255);
                fb[y][x][1] = (unsigned char)(clampf(g0 + c * 0.26f, 0, 1) * 255);
                fb[y][x][0] = (unsigned char)(clampf(b0 + c * 0.14f, 0, 1) * 255);
                fb[y][x][3] = 255;
            } else {
                float dy = (float)y - HORIZON;
                float z = (EYE_H * FOCAL) / (dy + 0.5f);          /* depth in metres */
                float wx = ((float)x - FGW * 0.5f) * z / FOCAL + camx;
                float fade = smoothstepf(90.f, 12.f, z);
                float cell = 1.5f;
                float ck = fmodf(floorf(wx / cell) + floorf(z / cell), 2.f);
                if (ck < 0) ck += 2.f;
                /* per-tile colour variation gives the search real structure */
                float h = hash1(floorf(wx / cell) * 7.13f + floorf(z / cell) * 3.77f);
                float base = (ck < 1.f) ? 0.30f : 0.24f;
                base += h * 0.10f;
                float r0 = base * (0.62f + 0.25f * h);
                float g0 = base * (0.95f + 0.10f * hash1(h * 31.f));
                float b0 = base * (0.48f + 0.20f * hash1(h * 17.f));
                float haze = 1.f - fade;
                fb[y][x][2] = (unsigned char)(clampf(r0 * fade + 0.62f * haze, 0, 1) * 255);
                fb[y][x][1] = (unsigned char)(clampf(g0 * fade + 0.72f * haze, 0, 1) * 255);
                fb[y][x][0] = (unsigned char)(clampf(b0 * fade + 0.80f * haze, 0, 1) * 255);
                fb[y][x][3] = 255;
            }
        }
    }
}

static void project(float wx, float wz, float camx, float wy, float *sx, float *sy, float *scale) {
    float z = wz < 0.35f ? 0.35f : wz;
    *scale = FOCAL / z;
    *sx = FGW * 0.5f + (wx - camx) * (*scale);
    *sy = HORIZON + (EYE_H - wy) * (*scale);
}

static void disc(float cx, float cy, float rx, float ry, float r, float g, float b, float a) {
    int x0 = (int)(cx - rx) - 1, x1 = (int)(cx + rx) + 1;
    int y0 = (int)(cy - ry) - 1, y1 = (int)(cy + ry) + 1;
    for (int y = y0; y <= y1; y++)
        for (int x = x0; x <= x1; x++) {
            float u = (x + 0.5f - cx) / (rx < 0.5f ? 0.5f : rx);
            float v = (y + 0.5f - cy) / (ry < 0.5f ? 0.5f : ry);
            float d = u * u + v * v;
            if (d < 1.4f) px(x, y, r, g, b, a * smoothstepf(1.15f, 0.80f, d));
        }
}

/* rotated bar, used for limbs and trunks */
static void bar(float x0, float y0, float x1, float y1, float w,
                float r, float g, float b) {
    float dx = x1 - x0, dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    if (len < 0.001f) return;
    dx /= len; dy /= len;
    int bx0 = (int)fminf(x0, x1) - (int)w - 2, bx1 = (int)fmaxf(x0, x1) + (int)w + 2;
    int by0 = (int)fminf(y0, y1) - (int)w - 2, by1 = (int)fmaxf(y0, y1) + (int)w + 2;
    for (int y = by0; y <= by1; y++)
        for (int x = bx0; x <= bx1; x++) {
            float px_ = x + 0.5f - x0, py_ = y + 0.5f - y0;
            float tproj = clampf(px_ * dx + py_ * dy, 0.f, len);
            float ox = px_ - dx * tproj, oy = py_ - dy * tproj;
            float d = sqrtf(ox * ox + oy * oy);
            px(x, y, r, g, b, smoothstepf(w, w - 1.2f, d));
        }
}

typedef struct { float wx, wz, h, phase, lean; int kind; } Tree;
#define NTREES 26
static Tree trees[NTREES];

static void init_trees(void) {
    for (int i = 0; i < NTREES; i++) {
        float a = hash1(i * 1.7f + 0.3f), b = hash1(i * 4.1f + 1.9f), c = hash1(i * 9.3f);
        trees[i].wx = (a - 0.5f) * 40.f + (i % 2 ? 5.5f : -5.5f);
        trees[i].wz = 9.f + b * 46.f;
        trees[i].h = 3.4f + c * 3.4f;
        trees[i].phase = hash1(i * 2.3f) * 6.2831853f;
        trees[i].lean = (hash1(i * 5.5f) - 0.5f) * 0.25f;
        trees[i].kind = (int)(hash1(i * 8.8f) * 3.f);
    }
}

static int tree_cmp(const void *a, const void *b) {
    float za = ((const Tree *)a)->wz, zb = ((const Tree *)b)->wz;
    return za < zb ? 1 : (za > zb ? -1 : 0);
}

/* Gusty wind: two slow envelopes so sway is not a single clean sinusoid. */
static float wind(float t, float phase) {
    float gust = 0.55f + 0.45f * sinf(t * 0.37f + phase * 0.2f)
                              * sinf(t * 0.11f + 1.3f);
    return gust * (sinf(t * 1.9f + phase) + 0.45f * sinf(t * 3.7f + phase * 1.7f));
}

static void draw_trees(float t, float camx) {
    for (int i = 0; i < NTREES; i++) {
        Tree *tr = &trees[i];
        float sx, sy, sc;
        project(tr->wx, tr->wz, camx, 0.f, &sx, &sy, &sc);
        if (sx < -300 || sx > FGW + 300) continue;
        float trunkH = tr->h * sc;
        float sway = wind(t, tr->phase) * (0.055f + 0.02f * tr->kind);
        float topx = sx + (sway + tr->lean) * trunkH * 0.55f;
        float topy = sy - trunkH;
        float fade = smoothstepf(90.f, 14.f, tr->wz);
        float tw = clampf(trunkH * 0.045f, 1.2f, 22.f);
        bar(sx, sy, topx, topy, tw, 0.26f * fade + 0.55f * (1 - fade),
            0.19f * fade + 0.66f * (1 - fade), 0.13f * fade + 0.76f * (1 - fade));
        /* canopy as a few overlapping blobs, each with its own sway lag */
        int nb = 5 + tr->kind;
        for (int k = 0; k < nb; k++) {
            float f = (float)k / nb;
            float lag = wind(t - 0.10f - f * 0.06f, tr->phase + k * 0.9f);
            float bx = topx + lag * trunkH * 0.10f + (hash1(i * 3.f + k) - 0.5f) * trunkH * 0.42f;
            float by = topy + (hash1(i * 6.f + k * 2.f) - 0.35f) * trunkH * 0.34f;
            float rr = trunkH * (0.20f + 0.10f * hash1(i + k * 5.f));
            float g0 = 0.34f + 0.20f * hash1(i * 2.f + k);
            disc(bx, by, rr * 1.25f, rr, 0.10f * fade + 0.6f * (1 - fade),
                 g0 * fade + 0.70f * (1 - fade), 0.12f * fade + 0.78f * (1 - fade), 1.f);
        }
    }
}

static void draw_character(float t, float camx) {
    float wx, facing, phase, spin;
    character_state(t, &wx, &facing, &phase, &spin);
    float wz = 5.0f;
    float sx, sy, sc;
    project(wx, wz, camx, 0.f, &sx, &sy, &sc);
    float hgt = 1.75f * sc;
    /* spin squashes the figure horizontally as it turns */
    float cs = cosf(spin);
    float xs = fabsf(cs) * 0.72f + 0.28f;
    float step = sinf(phase), step2 = sinf(phase + 3.14159f);
    float bob = fabsf(sinf(phase)) * hgt * 0.018f;
    float hipY = sy - hgt * 0.48f - bob;
    float shoY = sy - hgt * 0.82f - bob;
    float headY = sy - hgt * 0.93f - bob;
    float lw = hgt * 0.055f;
    #define SXP(o) (sx + (o) * xs)
    /* legs */
    bar(SXP(0), hipY, SXP(step * hgt * 0.17f), sy - fabsf(step) * hgt * 0.06f, lw, .18f, .20f, .30f);
    bar(SXP(0), hipY, SXP(step2 * hgt * 0.17f), sy - fabsf(step2) * hgt * 0.06f, lw, .14f, .16f, .26f);
    /* torso */
    bar(SXP(0), hipY, SXP(0), shoY, hgt * 0.075f, .72f, .26f, .22f);
    /* arms counter-swing */
    bar(SXP(0), shoY, SXP(step2 * hgt * 0.15f), shoY + hgt * 0.24f, lw * 0.85f, .80f, .62f, .48f);
    bar(SXP(0), shoY, SXP(step * hgt * 0.15f), shoY + hgt * 0.24f, lw * 0.85f, .70f, .54f, .42f);
    /* head, with a nose so the spin direction is readable */
    disc(SXP(0), headY, hgt * 0.062f * xs + hgt * 0.02f, hgt * 0.072f, .90f, .74f, .60f, 1.f);
    if (cs > 0.f)
        disc(SXP(hgt * 0.05f * (spin > 3.14159f ? -1.f : 1.f)), headY + hgt * 0.008f,
             hgt * 0.018f, hgt * 0.016f, .80f, .58f, .46f, 1.f);
    #undef SXP
}

/* A marker that advances exactly one cell per frame: any duplicated, dropped or
   out-of-order frame is immediately visible, and it is trivial to read off a dump. */
static void draw_tick(float t) {
    int n = (int)floorf(t * FPS + 0.5f);
    int cells = 30, cw = FGW / cells;
    for (int i = 0; i < cells; i++) {
        float on = (i == (n % cells)) ? 1.f : 0.12f;
        for (int y = 8; y < 26; y++)
            for (int x = i * cw + 3; x < (i + 1) * cw - 3; x++)
                px(x, y, on, on, on * 0.6f, 1.f);
    }
    /* smooth sub-frame sweep: position is linear in t, so a correctly paced
       generated frame lands exactly between its neighbours */
    float u = fmodf(t * 0.25f, 1.f);
    int bx = (int)(u * (FGW - 60));
    for (int y = 32; y < 46; y++)
        for (int x = bx; x < bx + 60; x++) px(x, y, 1.f, 0.85f, 0.1f, 1.f);
}

static void render(float t) {
    float wx, facing, phase, spin;
    character_state(t, &wx, &facing, &phase, &spin);
    float camx = wx;                      /* camera follows: global pan while walking, static while spinning */
    draw_world(t, camx);
    draw_trees(t, camx);
    draw_character(t, camx);
    draw_tick(t);
}

#ifdef FGT_HEADLESS
static void write_ppm(const char *path, unsigned char buf[FGH][FGW][4]) {
    FILE *f = fopen(path, "wb");
    if (!f) { fprintf(stderr, "cannot write %s\n", path); return; }
    fprintf(f, "P6\n%d %d\n255\n", FGW, FGH);
    for (int y = 0; y < FGH; y++)
        for (int x = 0; x < FGW; x++) {
            fputc(buf[y][x][2], f); fputc(buf[y][x][1], f); fputc(buf[y][x][0], f);
        }
    fclose(f);
}
int main(int argc, char **argv) {
    const char *dir = argc > 1 ? argv[1] : ".";
    int n0 = argc > 2 ? atoi(argv[2]) : 0;
    int nframes = argc > 3 ? atoi(argv[3]) : 8;
    init_trees();
    char path[512];
    for (int i = 0; i < nframes; i++) {
        float t = (n0 + i) / FPS;
        render(t);
        snprintf(path, sizeof(path), "%s/real_%03d.ppm", dir, i);
        write_ppm(path, fb);
        /* the frame a perfect interpolator would produce between i and i+1 */
        render(t + 0.5f / FPS);
        snprintf(path, sizeof(path), "%s/mid_%03d.ppm", dir, i);
        write_ppm(path, fb);
        for (int q = 1; q <= 3; q++) {
            render(t + (q * 0.25f) / FPS);
            snprintf(path, sizeof(path), "%s/q%d_%03d.ppm", dir, q, i);
            write_ppm(path, fb);
        }
    }
    printf("wrote %d real frames + midpoints + quarter-phases to %s\n", nframes, dir);
    return 0;
}
#else
#include <windows.h>
static HBITMAP g_dib; static void *g_bits; static HDC g_memdc;
static LRESULT CALLBACK wndproc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_DESTROY: PostQuitMessage(0); return 0;
    case WM_KEYDOWN: if (w == VK_ESCAPE) PostQuitMessage(0); return 0;
    case WM_ERASEBKGND: return 1;
    }
    return DefWindowProcW(h, m, w, l);
}
int WINAPI wWinMain(HINSTANCE hi, HINSTANCE hp, PWSTR cmd, int show) {
    (void)hp; (void)cmd;
    WNDCLASSW wc = {0};
    wc.lpfnWndProc = wndproc; wc.hInstance = hi; wc.lpszClassName = L"FGTester";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);
    RECT r = {0, 0, FGW, FGH};
    AdjustWindowRect(&r, WS_OVERLAPPEDWINDOW, FALSE);
    HWND hwnd = CreateWindowW(L"FGTester", L"Frame-Gen-Tester  30 FPS", WS_OVERLAPPEDWINDOW,
                              CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left, r.bottom - r.top,
                              NULL, NULL, hi, NULL);
    ShowWindow(hwnd, show);
    HDC dc = GetDC(hwnd);
    BITMAPINFO bi = {0};
    bi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth = FGW; bi.bmiHeader.biHeight = -FGH;
    bi.bmiHeader.biPlanes = 1; bi.bmiHeader.biBitCount = 32; bi.bmiHeader.biCompression = BI_RGB;
    g_dib = CreateDIBSection(dc, &bi, DIB_RGB_COLORS, &g_bits, NULL, 0);
    g_memdc = CreateCompatibleDC(dc);
    SelectObject(g_memdc, g_dib);
    init_trees();
    LARGE_INTEGER freq, t0, now; QueryPerformanceFrequency(&freq); QueryPerformanceCounter(&t0);
    double frame_period = 1.0 / FPS;
    long long n = 0; double fps_acc = 0; long long fps_n = 0; LARGE_INTEGER last = t0;
    MSG msg;
    for (;;) {
        while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) goto done;
            TranslateMessage(&msg); DispatchMessageW(&msg);
        }
        render((float)(n * frame_period));
        memcpy(g_bits, fb, sizeof(fb));
        BitBlt(dc, 0, 0, FGW, FGH, g_memdc, 0, 0, SRCCOPY);
        n++;
        QueryPerformanceCounter(&now);
        double dt = (double)(now.QuadPart - last.QuadPart) / freq.QuadPart;
        last = now; fps_acc += dt; fps_n++;
        if (fps_n >= 30) {
            wchar_t title[128];
            swprintf(title, 128, L"Frame-Gen-Tester  target 30 FPS  actual %.1f  frame %lld",
                     fps_n / fps_acc, n);
            SetWindowTextW(hwnd, title);
            fps_acc = 0; fps_n = 0;
        }
        /* pace to the next 1/30 boundary from a fixed origin so drift cannot accumulate */
        for (;;) {
            QueryPerformanceCounter(&now);
            double elapsed = (double)(now.QuadPart - t0.QuadPart) / freq.QuadPart;
            double target = n * frame_period;
            if (elapsed >= target) break;
            double remain = target - elapsed;
            if (remain > 0.002) Sleep((DWORD)((remain - 0.001) * 1000));
        }
    }
done:
    DeleteDC(g_memdc); DeleteObject(g_dib); ReleaseDC(hwnd, dc);
    return 0;
}
#endif
