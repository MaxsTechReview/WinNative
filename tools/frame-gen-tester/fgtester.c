// Frame Gen test scene. Everything is a pure function of time, so rendering at
// t = n + 0.5 gives the exact frame a perfect interpolator would produce.
//
//   x86_64-w64-mingw32-gcc -O2 -municode -mwindows fgtester.c -o Frame-Gen-Tester.exe -lgdi32
//   gcc -O2 -DFGT_HEADLESS fgtester.c -o fgtester -lm      (writes PPM frames + midpoints)

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAXW 1600
#define MAXH 900
#define FPS 30.0

static int RW = 1280, RH = 720;
static float HORIZ, FOCAL = 620.0f;
#define EYE_H 1.65f

static unsigned char fb[MAXH * MAXW * 4];   /* BGRA, matches a Win32 DIB */
#define PIX(x, y) (fb + (((y) * RW + (x)) << 2))

static float clampf(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }
static float smoothstepf(float a, float b, float x) {
    float t = clampf((x - a) / (b - a), 0.f, 1.f);
    return t * t * (3.f - 2.f * t);
}
static float fract(float x) { return x - floorf(x); }
static float hash1(float n) { return fract(sinf(n * 12.9898f) * 43758.5453f); }
/* fast floor for the ranges this scene uses; avoids a libm call per pixel */
static int ffloor(float v) { return (int)(v + 1048576.0f) - 1048576; }

static void px(int x, int y, float r, float g, float b, float a) {
    if (x < 0 || y < 0 || x >= RW || y >= RH || a <= 0.f) return;
    unsigned char *p = PIX(x, y);
    if (a >= 0.996f) {
        p[2] = (unsigned char)(clampf(r, 0, 1) * 255.f);
        p[1] = (unsigned char)(clampf(g, 0, 1) * 255.f);
        p[0] = (unsigned char)(clampf(b, 0, 1) * 255.f);
        p[3] = 255;
        return;
    }
    float ia = 1.f - a;
    p[2] = (unsigned char)(clampf(p[2] * (1.f / 255.f) * ia + r * a, 0, 1) * 255.f);
    p[1] = (unsigned char)(clampf(p[1] * (1.f / 255.f) * ia + g * a, 0, 1) * 255.f);
    p[0] = (unsigned char)(clampf(p[0] * (1.f / 255.f) * ia + b * a, 0, 1) * 255.f);
    p[3] = 255;
}

/* ---- precomputed tables ---------------------------------------------------
   The scene needs noise, but evaluating it per pixel costs a sin per sample.
   Both users of it are periodic, so they are baked once at startup. */
#define CLW 256
#define CLH 96
#define CLMASK (CLW - 1)
static float cloud[CLH][CLW];
#define NTILE 64
static float tilecol[NTILE][3];

static float vnoise(const float *g, int gw, int gh, float x, float y) {
    int x0 = ffloor(x), y0 = ffloor(y);
    float fx = x - x0, fy = y - y0;
    fx = fx * fx * (3.f - 2.f * fx); fy = fy * fy * (3.f - 2.f * fy);
    int xa = ((x0 % gw) + gw) % gw, xb = (xa + 1) % gw;
    int ya = ((y0 % gh) + gh) % gh, yb = (ya + 1) % gh;
    float a = g[ya * gw + xa], b = g[ya * gw + xb];
    float c = g[yb * gw + xa], d = g[yb * gw + xb];
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
}

static void build_tables(void) {
    /* fBm from a few small periodic lattices: smooth, wraps, and costs nothing
       at run time because the result is baked into a table */
    static float g1[16 * 6], g2[32 * 12], g3[64 * 24];
    for (int i = 0; i < 16 * 6; i++)  g1[i] = hash1(i * 1.31f + 0.7f);
    for (int i = 0; i < 32 * 12; i++) g2[i] = hash1(i * 2.17f + 3.1f);
    for (int i = 0; i < 64 * 24; i++) g3[i] = hash1(i * 4.53f + 9.4f);
    float lo = 1e9f, hi = -1e9f;
    for (int y = 0; y < CLH; y++)
        for (int x = 0; x < CLW; x++) {
            float u = (float)x / CLW, v = (float)y / CLH;
            float n = vnoise(g1, 16, 6, u * 16.f, v * 6.f) * 0.55f
                    + vnoise(g2, 32, 12, u * 32.f, v * 12.f) * 0.30f
                    + vnoise(g3, 64, 24, u * 64.f, v * 24.f) * 0.15f;
            cloud[y][x] = n;
            if (n < lo) lo = n; if (n > hi) hi = n;
        }
    for (int y = 0; y < CLH; y++)
        for (int x = 0; x < CLW; x++)
            cloud[y][x] = smoothstepf(0.45f, 0.88f, (cloud[y][x] - lo) / (hi - lo + 1e-6f));
    for (int i = 0; i < NTILE; i++) {
        float h = hash1(i * 3.17f + 0.5f), h2 = hash1(i * 8.11f), h3 = hash1(i * 5.03f);
        tilecol[i][0] = 0.16f + h * 0.10f;
        tilecol[i][1] = 0.26f + h2 * 0.16f;
        tilecol[i][2] = 0.12f + h3 * 0.08f;
    }
}

/* ---- motion script -------------------------------------------------------
   30 s out, turn, 30 s back, turn, repeat. */
#define WALK_T 30.0f
#define SPIN_T 2.0f
#define LOOP_T (2 * WALK_T + 2 * SPIN_T)
#define WALK_SPEED 3.6f

static void character_state(float t, float *wx, float *phase, float *spin) {
    float u = fmodf(t, LOOP_T);
    if (u < 0) u += LOOP_T;
    float x, sp = 0.f;
    if (u < WALK_T) {
        x = u * WALK_SPEED;
    } else if (u < WALK_T + SPIN_T) {
        x = WALK_T * WALK_SPEED; sp = (u - WALK_T) / SPIN_T * 6.2831853f;
    } else if (u < 2 * WALK_T + SPIN_T) {
        x = WALK_T * WALK_SPEED - (u - WALK_T - SPIN_T) * WALK_SPEED;
    } else {
        x = 0.f; sp = (u - 2 * WALK_T - SPIN_T) / SPIN_T * 6.2831853f;
    }
    *wx = x;
    *spin = sp;
    *phase = x * 1.9f;              /* stride tied to distance so feet never slide */
}

/* ---- world ---------------------------------------------------------------- */
#define CELL 1.5f
#define INVCELL (1.0f / CELL)

static void draw_world(float t, float camx) {
    int hy = (int)HORIZ;
    float cscroll = t * 3.5f;
    for (int y = 0; y < hy && y < RH; y++) {
        float sky = (float)y / HORIZ;
        float r0 = 0.35f + 0.42f * sky, g0 = 0.55f + 0.32f * sky, b0 = 0.86f - 0.06f * sky;
        float amt = smoothstepf(0.0f, 0.55f, sky) * 0.34f;
        int row = (int)(sky * (CLH - 1));
        const float *cr = cloud[row < 0 ? 0 : (row >= CLH ? CLH - 1 : row)];
        float u = cscroll;
        float du = (float)CLW / (float)RW * 1.6f;
        unsigned char *p = PIX(0, y);
        for (int x = 0; x < RW; x++, p += 4) {
            int ui = (int)u; float uf = u - (float)ui;
            float c = cr[ui & CLMASK] * (1.f - uf) + cr[(ui + 1) & CLMASK] * uf;
            c *= amt;
            p[2] = (unsigned char)(clampf(r0 + c, 0, 1) * 255.f);
            p[1] = (unsigned char)(clampf(g0 + c * 0.9f, 0, 1) * 255.f);
            p[0] = (unsigned char)(clampf(b0 + c * 0.5f, 0, 1) * 255.f);
            p[3] = 255;
            u += du;
        }
    }
    for (int y = hy < 0 ? 0 : hy; y < RH; y++) {
        float dy = (float)y - HORIZ;
        float z = (EYE_H * FOCAL) / (dy + 0.5f);
        float fade = smoothstepf(90.f, 12.f, z);
        float haze = 1.f - fade;
        float hr = 0.62f * haze, hg = 0.72f * haze, hb = 0.80f * haze;
        float dwx = z / FOCAL;
        float wx = (0.5f - RW * 0.5f) * dwx + camx;
        int iz = ffloor(z * INVCELL);
        unsigned char *p = PIX(0, y);
        for (int x = 0; x < RW; x++, p += 4) {
            int ix = ffloor(wx * INVCELL);
            unsigned h = (unsigned)(ix * 73856093) ^ (unsigned)(iz * 19349663);
            const float *tc = tilecol[(h >> 7) & (NTILE - 1)];
            float k = ((ix + iz) & 1) ? 1.22f : 0.86f;
            p[2] = (unsigned char)(clampf(tc[0] * k * fade + hr, 0, 1) * 255.f);
            p[1] = (unsigned char)(clampf(tc[1] * k * fade + hg, 0, 1) * 255.f);
            p[0] = (unsigned char)(clampf(tc[2] * k * fade + hb, 0, 1) * 255.f);
            p[3] = 255;
            wx += dwx;
        }
    }
}

static void project(float wx, float wz, float camx, float *sx, float *sy, float *scale) {
    float z = wz < 0.35f ? 0.35f : wz;
    *scale = FOCAL / z;
    *sx = RW * 0.5f + (wx - camx) * (*scale);
    *sy = HORIZ + EYE_H * (*scale);
}

static void disc(float cx, float cy, float rx, float ry, float r, float g, float b) {
    if (rx < 0.4f) rx = 0.4f;
    if (ry < 0.4f) ry = 0.4f;
    int x0 = (int)(cx - rx) - 1, x1 = (int)(cx + rx) + 1;
    int y0 = (int)(cy - ry) - 1, y1 = (int)(cy + ry) + 1;
    if (x1 < 0 || y1 < 0 || x0 >= RW || y0 >= RH) return;
    if (x0 < 0) x0 = 0; if (y0 < 0) y0 = 0;
    if (x1 >= RW) x1 = RW - 1; if (y1 >= RH) y1 = RH - 1;
    float irx = 1.f / rx, iry = 1.f / ry;
    for (int y = y0; y <= y1; y++) {
        float v = (y + 0.5f - cy) * iry, v2 = v * v;
        for (int x = x0; x <= x1; x++) {
            float u = (x + 0.5f - cx) * irx;
            float d = u * u + v2;
            if (d >= 1.15f) continue;
            px(x, y, r, g, b, d <= 0.80f ? 1.f : smoothstepf(1.15f, 0.80f, d));
        }
    }
}

/* rotated bar, used for limbs and trunks; sqrt only in the antialiased band */
static void bar(float x0, float y0, float x1, float y1, float w, float r, float g, float b) {
    float dx = x1 - x0, dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    if (len < 0.001f) return;
    dx /= len; dy /= len;
    int bx0 = (int)fminf(x0, x1) - (int)w - 2, bx1 = (int)fmaxf(x0, x1) + (int)w + 2;
    int by0 = (int)fminf(y0, y1) - (int)w - 2, by1 = (int)fmaxf(y0, y1) + (int)w + 2;
    if (bx1 < 0 || by1 < 0 || bx0 >= RW || by0 >= RH) return;
    if (bx0 < 0) bx0 = 0; if (by0 < 0) by0 = 0;
    if (bx1 >= RW) bx1 = RW - 1; if (by1 >= RH) by1 = RH - 1;
    float wi = w - 1.2f; if (wi < 0.f) wi = 0.f;
    float wo2 = w * w, wi2 = wi * wi;
    for (int y = by0; y <= by1; y++)
        for (int x = bx0; x <= bx1; x++) {
            float pxx = x + 0.5f - x0, pyy = y + 0.5f - y0;
            float tp = pxx * dx + pyy * dy;
            if (tp < 0.f) tp = 0.f; else if (tp > len) tp = len;
            float ox = pxx - dx * tp, oy = pyy - dy * tp;
            float d2 = ox * ox + oy * oy;
            if (d2 >= wo2) continue;
            px(x, y, r, g, b, d2 <= wi2 ? 1.f : smoothstepf(w, wi, sqrtf(d2)));
        }
}

typedef struct { float wx, wz, h, phase, lean; int kind; } Tree;
#define NTREES 64
#define TREE_PERIOD 140.0f      /* the forest repeats, so the walk never runs out of it */
static Tree trees[NTREES];

static void init_trees(void) {
    build_tables();
    for (int i = 0; i < NTREES; i++) {
        float a = hash1(i * 1.7f + 0.3f), b = hash1(i * 4.1f + 1.9f), c = hash1(i * 9.3f);
        trees[i].wx = a * TREE_PERIOD;
        trees[i].wz = 9.f + b * 46.f;
        trees[i].h = 3.4f + c * 3.4f;
        trees[i].phase = hash1(i * 2.3f) * 6.2831853f;
        trees[i].lean = (hash1(i * 5.5f) - 0.5f) * 0.25f;
        trees[i].kind = (int)(hash1(i * 8.8f) * 3.f);
    }
}

/* Gusty wind: two slow envelopes so sway is not a single clean sinusoid. */
static float wind(float t, float phase) {
    float gust = 0.55f + 0.45f * sinf(t * 0.37f + phase * 0.2f) * sinf(t * 0.11f + 1.3f);
    return gust * (sinf(t * 1.9f + phase) + 0.45f * sinf(t * 3.7f + phase * 1.7f));
}

static void draw_trees(float t, float camx) {
    for (int i = 0; i < NTREES; i++) {
        Tree *tr = &trees[i];
        float wx = tr->wx + TREE_PERIOD * floorf((camx - tr->wx) / TREE_PERIOD + 0.5f);
        float sx, sy, sc;
        project(wx, tr->wz, camx, &sx, &sy, &sc);
        float trunkH = tr->h * sc;
        if (sx < -trunkH || sx > RW + trunkH) continue;
        float sway = wind(t, tr->phase) * (0.055f + 0.02f * tr->kind);
        float topx = sx + (sway + tr->lean) * trunkH * 0.55f;
        float topy = sy - trunkH;
        float fade = smoothstepf(90.f, 14.f, tr->wz);
        float tw = clampf(trunkH * 0.045f, 1.2f, 22.f);
        bar(sx, sy, topx, topy, tw, 0.26f * fade + 0.55f * (1 - fade),
            0.19f * fade + 0.66f * (1 - fade), 0.13f * fade + 0.76f * (1 - fade));
        int nb = 5 + tr->kind;
        for (int k = 0; k < nb; k++) {
            float f = (float)k / nb;
            float lag = wind(t - 0.10f - f * 0.06f, tr->phase + k * 0.9f);
            float bx = topx + lag * trunkH * 0.10f + (hash1(i * 3.f + k) - 0.5f) * trunkH * 0.42f;
            float by = topy + (hash1(i * 6.f + k * 2.f) - 0.35f) * trunkH * 0.34f;
            float rr = trunkH * (0.20f + 0.10f * hash1(i + k * 5.f));
            float g0 = 0.34f + 0.20f * hash1(i * 2.f + k);
            disc(bx, by, rr * 1.25f, rr, 0.10f * fade + 0.6f * (1 - fade),
                 g0 * fade + 0.70f * (1 - fade), 0.12f * fade + 0.78f * (1 - fade));
        }
    }
}

static void draw_character(float t, float camx) {
    float wx, phase, spin;
    character_state(t, &wx, &phase, &spin);
    float sx, sy, sc;
    project(wx, 5.0f, camx, &sx, &sy, &sc);
    float hgt = 1.75f * sc;
    float cs = cosf(spin);
    float xs = fabsf(cs) * 0.72f + 0.28f;
    float step = sinf(phase), step2 = sinf(phase + 3.14159f);
    float bob = fabsf(sinf(phase)) * hgt * 0.018f;
    float hipY = sy - hgt * 0.48f - bob;
    float shoY = sy - hgt * 0.82f - bob;
    float headY = sy - hgt * 0.93f - bob;
    float lw = hgt * 0.055f;
    #define SXP(o) (sx + (o) * xs)
    bar(SXP(0), hipY, SXP(step * hgt * 0.17f), sy - fabsf(step) * hgt * 0.06f, lw, .18f, .20f, .30f);
    bar(SXP(0), hipY, SXP(step2 * hgt * 0.17f), sy - fabsf(step2) * hgt * 0.06f, lw, .14f, .16f, .26f);
    bar(SXP(0), hipY, SXP(0), shoY, hgt * 0.075f, .72f, .26f, .22f);
    bar(SXP(0), shoY, SXP(step2 * hgt * 0.15f), shoY + hgt * 0.24f, lw * 0.85f, .80f, .62f, .48f);
    bar(SXP(0), shoY, SXP(step * hgt * 0.15f), shoY + hgt * 0.24f, lw * 0.85f, .70f, .54f, .42f);
    disc(SXP(0), headY, hgt * 0.062f * xs + hgt * 0.02f, hgt * 0.072f, .90f, .74f, .60f);
    if (cs > 0.f)
        disc(SXP(hgt * 0.05f * (spin > 3.14159f ? -1.f : 1.f)), headY + hgt * 0.008f,
             hgt * 0.018f, hgt * 0.016f, .80f, .58f, .46f);
    #undef SXP
}

/* A marker that advances exactly one cell per frame: any duplicated, dropped or
   out-of-order frame is immediately visible, and it is trivial to read off a dump. */
static void draw_tick(float t, int health) {
    int n = (int)floorf(t * FPS + 0.5f);
    int cells = 30, cw = RW / cells;
    for (int i = 0; i < cells; i++) {
        float on = (i == (n % cells)) ? 1.f : 0.12f;
        for (int y = 8; y < 26; y++)
            for (int x = i * cw + 3; x < (i + 1) * cw - 3; x++) px(x, y, on, on, on * 0.6f, 1.f);
    }
    /* smooth sub-frame sweep: position is linear in t, so a correctly paced
       generated frame lands exactly between its neighbours */
    float u = fmodf(t * 0.25f, 1.f);
    int bx = (int)(u * (RW - 60));
    for (int y = 32; y < 46; y++)
        for (int x = bx; x < bx + 60; x++) px(x, y, 1.f, 0.85f, 0.1f, 1.f);
    /* green while the tester is holding 30 FPS, red when it is not */
    for (int y = 8; y < 46; y++)
        for (int x = RW - 30; x < RW - 8; x++)
            px(x, y, health ? 0.1f : 0.95f, health ? 0.9f : 0.15f, 0.1f, 1.f);
}

static void render(float t, int health) {
    float wx, phase, spin;
    character_state(t, &wx, &phase, &spin);
    float camx = wx;
    draw_world(t, camx);
    draw_trees(t, camx);
    draw_character(t, camx);
    draw_tick(t, health);
}

#ifdef FGT_HEADLESS
static void write_ppm(const char *path) {
    FILE *f = fopen(path, "wb");
    if (!f) { fprintf(stderr, "cannot write %s\n", path); return; }
    fprintf(f, "P6\n%d %d\n255\n", RW, RH);
    for (int y = 0; y < RH; y++)
        for (int x = 0; x < RW; x++) {
            unsigned char *p = PIX(x, y);
            fputc(p[2], f); fputc(p[1], f); fputc(p[0], f);
        }
    fclose(f);
}
int main(int argc, char **argv) {
    const char *dir = argc > 1 ? argv[1] : ".";
    int n0 = argc > 2 ? atoi(argv[2]) : 0;
    int nframes = argc > 3 ? atoi(argv[3]) : 8;
    int step = argc > 4 ? atoi(argv[4]) : 1;      /* 1 = 30 FPS, 3 = 10 FPS, 15 = 2 FPS */
    if (argc > 6) { RW = atoi(argv[5]); RH = atoi(argv[6]); }
    if (step < 1) step = 1;
    HORIZ = RH * 0.52f; FOCAL = RW * 0.484f;
    init_trees();
    char path[512];
    for (int i = 0; i < nframes; i++) {
        float t = (n0 + (float)i * step) / FPS;
        float d = step / FPS;                      /* interval between presented frames */
        render(t, 1);
        snprintf(path, sizeof(path), "%s/real_%03d.ppm", dir, i); write_ppm(path);
        render(t + 0.5f * d, 1);
        snprintf(path, sizeof(path), "%s/mid_%03d.ppm", dir, i); write_ppm(path);
        for (int q = 1; q <= 3; q++) {
            render(t + q * 0.25f * d, 1);
            snprintf(path, sizeof(path), "%s/q%d_%03d.ppm", dir, q, i); write_ppm(path);
        }
    }
    printf("wrote %d frames at step %d (%.0f FPS) to %s (%dx%d)\n",
           nframes, step, FPS / step, dir, RW, RH);
    return 0;
}
#else
#include <windows.h>

static double now_s(LARGE_INTEGER freq) {
    LARGE_INTEGER c; QueryPerformanceCounter(&c);
    return (double)c.QuadPart / (double)freq.QuadPart;
}

/* Presentation rate. The animation clock always runs in real time, so dropping the
   rate skips the frames in between rather than slowing the scene down: the character
   jumps to where it would have been. That is the point of the lower rates - it gives
   frame generation a much larger gap to bridge. */
static const int RATES[3] = {30, 10, 2};
static int g_rate_idx = 0;
#define RATE_STEP (30 / RATES[g_rate_idx])

#define BTN_W 76
#define BTN_H 32
#define BTN_Y 52
#define BTN_X0 12
#define BTN_GAP 8
static void btn_rect(int i, RECT *r) {
    r->left = BTN_X0 + i * (BTN_W + BTN_GAP);
    r->top = BTN_Y; r->right = r->left + BTN_W; r->bottom = BTN_Y + BTN_H;
}

static LRESULT CALLBACK wndproc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
    case WM_DESTROY: PostQuitMessage(0); return 0;
    case WM_LBUTTONDOWN: {
        int mx = (short)LOWORD(l), my = (short)HIWORD(l);
        for (int i = 0; i < 3; i++) {
            RECT r; btn_rect(i, &r);
            if (mx >= r.left && mx < r.right && my >= r.top && my < r.bottom) {
                g_rate_idx = i; break;
            }
        }
        return 0;
    }
    case WM_KEYDOWN:
        if (w == VK_ESCAPE) PostQuitMessage(0);
        else if (w == '1') g_rate_idx = 0;
        else if (w == '2') g_rate_idx = 1;
        else if (w == '3') g_rate_idx = 2;
        return 0;
    case WM_ERASEBKGND: return 1;
    }
    return DefWindowProcW(h, m, w, l);
}

static void draw_buttons(HDC dc, HFONT font) {
    HFONT old = (HFONT)SelectObject(dc, font);
    SetBkMode(dc, TRANSPARENT);
    for (int i = 0; i < 3; i++) {
        RECT r; btn_rect(i, &r);
        int on = (i == g_rate_idx);
        HBRUSH bg = CreateSolidBrush(on ? RGB(240, 200, 40) : RGB(30, 34, 44));
        FillRect(dc, &r, bg);
        DeleteObject(bg);
        HBRUSH fr = CreateSolidBrush(on ? RGB(255, 255, 255) : RGB(120, 130, 150));
        FrameRect(dc, &r, fr);
        DeleteObject(fr);
        wchar_t lab[16];
        swprintf(lab, 16, L"%d FPS", RATES[i]);
        SetTextColor(dc, on ? RGB(20, 20, 20) : RGB(215, 220, 230));
        DrawTextW(dc, lab, -1, &r, DT_CENTER | DT_VCENTER | DT_SINGLELINE);
    }
    SelectObject(dc, old);
}

/* Pick the largest of a few sizes that renders inside the frame budget. The
   content rate is what the compositor is being tested against, so holding 30 FPS
   matters more than resolution. */
static void pick_resolution(void) {
    static const int cand[][2] = {{1280,720},{1024,576},{854,480},{640,360},{480,270}};
    LARGE_INTEGER freq; QueryPerformanceFrequency(&freq);
    for (int i = 0; i < 5; i++) {
        RW = cand[i][0]; RH = cand[i][1];
        HORIZ = RH * 0.52f; FOCAL = RW * 0.484f;
        render(0.f, 1);                       /* warm caches */
        double a = now_s(freq);
        for (int k = 0; k < 3; k++) render(k * 0.033f, 1);
        double ms = (now_s(freq) - a) * 1000.0 / 3.0;
        if (ms <= 16.0 || i == 4) return;
    }
}

int WINAPI wWinMain(HINSTANCE hi, HINSTANCE hp, PWSTR cmd, int show) {
    (void)hp;
    init_trees();
    int forced = 0;
    if (cmd && *cmd) {
        int w = 0, h = 0;
        if (swscanf(cmd, L"%d %d", &w, &h) == 2 && w >= 320 && h >= 180 && w <= MAXW && h <= MAXH) {
            RW = w; RH = h; forced = 1;
            HORIZ = RH * 0.52f; FOCAL = RW * 0.484f;
        }
    }
    if (!forced) pick_resolution();

    WNDCLASSW wc = {0};
    wc.lpfnWndProc = wndproc; wc.hInstance = hi; wc.lpszClassName = L"FGTester";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);
    RECT r = {0, 0, RW, RH};
    AdjustWindowRect(&r, WS_OVERLAPPEDWINDOW, FALSE);
    HWND hwnd = CreateWindowW(L"FGTester", L"Frame-Gen-Tester", WS_OVERLAPPEDWINDOW,
                              CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left, r.bottom - r.top,
                              NULL, NULL, hi, NULL);
    ShowWindow(hwnd, show);
    HDC dc = GetDC(hwnd);
    BITMAPINFO bi = {0};
    bi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth = RW; bi.bmiHeader.biHeight = -RH;
    bi.bmiHeader.biPlanes = 1; bi.bmiHeader.biBitCount = 32; bi.bmiHeader.biCompression = BI_RGB;
    void *bits = NULL;
    HBITMAP dib = CreateDIBSection(dc, &bi, DIB_RGB_COLORS, &bits, NULL, 0);
    HDC memdc = CreateCompatibleDC(dc);
    SelectObject(memdc, dib);

    HFONT font = CreateFontW(-16, 0, 0, 0, FW_BOLD, 0, 0, 0, DEFAULT_CHARSET,
                             OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
                             DEFAULT_PITCH | FF_SWISS, L"Segoe UI");

    LARGE_INTEGER freq; QueryPerformanceFrequency(&freq);
    /* n counts in 30 FPS units regardless of the presentation rate, so the animation
       clock stays real-time and a lower rate simply skips the frames between presents */
    double t0 = now_s(freq), tick = 1.0 / FPS;
    long long n = 0, win_late = 0, win_n = 0;
    double win_t0 = t0;
    int health = 1;
    MSG msg;
    for (;;) {
        while (PeekMessageW(&msg, NULL, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) goto done;
            TranslateMessage(&msg); DispatchMessageW(&msg);
        }
        int step = RATE_STEP;
        double target = n * tick;
        render((float)target, health);
        memcpy(bits, fb, (size_t)RW * RH * 4);
        draw_buttons(memdc, font);
        BitBlt(dc, 0, 0, RW, RH, memdc, 0, 0, SRCCOPY);
        n += step; win_n++;
        double after = now_s(freq) - t0;
        if (after > target + step * tick) win_late++;
        if (now_s(freq) - win_t0 >= 1.0) {
            double secs = now_s(freq) - win_t0;
            double fps = win_n / secs;
            int want = RATES[g_rate_idx];
            health = (win_late * 20 <= win_n);            /* under 5% late frames */
            wchar_t title[224];
            swprintf(title, 224,
                     L"Frame-Gen-Tester  %dx%d  %.1f FPS (target %d, skipping %d of every %d)"
                     L"  late %lld/%lld  %s",
                     RW, RH, fps, want, step - 1, step, win_late, win_n,
                     health ? L"OK" : L"TOO SLOW");
            SetWindowTextW(hwnd, title);
            win_t0 = now_s(freq); win_n = 0; win_late = 0;
        }
        for (;;) {
            double e = now_s(freq) - t0, tg = n * tick;
            if (e >= tg) break;
            double rem = tg - e;
            if (rem > 0.002) Sleep((DWORD)((rem - 0.001) * 1000));
        }
        /* never try to catch up by racing: a stalled frame just shifts the phase */
        if (now_s(freq) - t0 > (n + 2 * step) * tick) t0 = now_s(freq) - n * tick;
    }
done:
    DeleteObject(font);
    DeleteDC(memdc); DeleteObject(dib); ReleaseDC(hwnd, dc);
    return 0;
}
#endif
