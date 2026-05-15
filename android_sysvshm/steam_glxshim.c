/*
 * Minimal GLX compatibility shim for Valve's native Linux ARM64 Steam client.
 *
 * WinNative's Java X server deliberately does not advertise GLX to Chromium/
 * CEF, because Mesa's GLX loader crashes under the Android/proot environment.
 * The main steam binary still uses an older VGUI path that hard-fails when
 * glXChooseVisual returns NULL. This preload answers only the tiny set of GLX
 * probes that the main steam process needs, while leaving steamwebhelper and
 * other helper binaries on the real libGLX path.
 */

#define _GNU_SOURCE

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef struct _XDisplay Display;
typedef struct _XVisual Visual;
typedef void *GLXContext;
typedef void *GLXFBConfig;
typedef unsigned long XID;
typedef XID GLXDrawable;
typedef unsigned int GLenum;
typedef unsigned char GLubyte;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef float GLfloat;
typedef double GLdouble;
typedef unsigned int GLbitfield;
typedef unsigned char GLboolean;
typedef void (*__GLXextFuncPtr)(void);

typedef struct {
    Visual *visual;
    unsigned long visualid;
    int screen;
    int depth;
    int class;
    unsigned long red_mask;
    unsigned long green_mask;
    unsigned long blue_mask;
    int colormap_size;
    int bits_per_rgb;
} XVisualInfo;

extern XVisualInfo *XGetVisualInfo(Display *display, long mask, XVisualInfo *template, int *count)
    __attribute__((weak));

enum {
    TRUE_COLOR = 4,
    VISUAL_SCREEN_MASK = 0x2,
    VISUAL_DEPTH_MASK = 0x4,
    VISUAL_CLASS_MASK = 0x8,
    GLX_USE_GL = 1,
    GLX_BUFFER_SIZE = 2,
    GLX_LEVEL = 3,
    GLX_RGBA = 4,
    GLX_DOUBLEBUFFER = 5,
    GLX_STEREO = 6,
    GLX_AUX_BUFFERS = 7,
    GLX_RED_SIZE = 8,
    GLX_GREEN_SIZE = 9,
    GLX_BLUE_SIZE = 10,
    GLX_ALPHA_SIZE = 11,
    GLX_DEPTH_SIZE = 12,
    GLX_STENCIL_SIZE = 13,
};

static int is_main_steam_process(void) {
    char exe[512];
    ssize_t len = readlink("/proc/self/exe", exe, sizeof(exe) - 1);
    if (len <= 0) return 0;
    exe[len] = '\0';

    const char *base = strrchr(exe, '/');
    base = base ? base + 1 : exe;
    return strcmp(base, "steam") == 0;
}

static void log_once(const char *message) {
    static int count = 0;
    if (count < 32) {
        fprintf(stderr, "WinNative GLX shim: %s\n", message);
        count++;
    }
}

static XVisualInfo *find_visual(Display *display, int screen, int depth) {
    if (!XGetVisualInfo) return NULL;

    XVisualInfo templ;
    memset(&templ, 0, sizeof(templ));
    templ.screen = screen;
    templ.depth = depth;
    templ.class = TRUE_COLOR;

    int count = 0;
    return XGetVisualInfo(
        display,
        VISUAL_SCREEN_MASK | VISUAL_DEPTH_MASK | VISUAL_CLASS_MASK,
        &templ,
        &count);
}

static Display *current_display = NULL;
static GLXDrawable current_drawable = 0;
static GLXContext current_context = NULL;
static uintptr_t next_texture_id = 1;

XVisualInfo *glXChooseVisual(Display *display, int screen, int *attrib_list) {
    if (!is_main_steam_process()) {
        return NULL;
    }

    XVisualInfo *visual = find_visual(display, screen, 32);
    if (!visual) visual = find_visual(display, screen, 24);
    if (visual) {
        fprintf(stderr, "WinNative GLX shim: returning X visual %#lx depth %d for main steam\n",
                visual->visualid, visual->depth);
    } else {
        fprintf(stderr, "WinNative GLX shim: no matching X visual for main steam\n");
    }
    return visual;
}

int glXQueryExtension(Display *display, int *error_base, int *event_base) {
    if (!is_main_steam_process()) {
        return 0;
    }
    if (error_base) *error_base = 0;
    if (event_base) *event_base = 0;
    return 1;
}

int glXQueryVersion(Display *display, int *major, int *minor) {
    if (!is_main_steam_process()) {
        return 0;
    }
    if (major) *major = 1;
    if (minor) *minor = 4;
    return 1;
}

int glXGetConfig(Display *display, XVisualInfo *visual, int attribute, int *value) {
    if (!is_main_steam_process()) {
        return 1;
    }
    if (!value) return 1;

    switch (attribute) {
        case GLX_USE_GL:
        case GLX_RGBA:
            *value = 1;
            break;
        case GLX_BUFFER_SIZE:
            *value = visual ? visual->depth : 32;
            break;
        case GLX_LEVEL:
        case GLX_STEREO:
        case GLX_AUX_BUFFERS:
            *value = 0;
            break;
        case GLX_DOUBLEBUFFER:
            *value = 1;
            break;
        case GLX_RED_SIZE:
        case GLX_GREEN_SIZE:
        case GLX_BLUE_SIZE:
        case GLX_ALPHA_SIZE:
        case GLX_STENCIL_SIZE:
            *value = 8;
            break;
        case GLX_DEPTH_SIZE:
            *value = 24;
            break;
        default:
            *value = 0;
            break;
    }
    return 0;
}

GLXContext glXCreateContext(Display *display, XVisualInfo *visual, GLXContext share_list, int direct) {
    if (!is_main_steam_process()) {
        return NULL;
    }
    (void)display;
    (void)visual;
    (void)share_list;
    (void)direct;
    log_once("created synthetic legacy GL context for main steam");
    return (GLXContext)(uintptr_t)0x1;
}

int glXMakeCurrent(Display *display, GLXDrawable drawable, GLXContext context) {
    if (!is_main_steam_process()) {
        return 0;
    }
    current_display = display;
    current_drawable = drawable;
    current_context = context;
    log_once("made synthetic GL context current for main steam");
    return 1;
}

int glXMakeContextCurrent(
        Display *display,
        GLXDrawable draw,
        GLXDrawable read,
        GLXContext context) {
    (void)read;
    return glXMakeCurrent(display, draw, context);
}

void glXDestroyContext(Display *display, GLXContext context) {
    (void)display;
    (void)context;
    if (current_context == context) {
        current_context = NULL;
        current_drawable = 0;
        current_display = NULL;
    }
}

void glXSwapBuffers(Display *display, GLXDrawable drawable) {
    (void)display;
    (void)drawable;
}

int glXIsDirect(Display *display, GLXContext context) {
    (void)display;
    (void)context;
    return 0;
}

GLXContext glXGetCurrentContext(void) {
    return current_context;
}

GLXDrawable glXGetCurrentDrawable(void) {
    return current_drawable;
}

Display *glXGetCurrentDisplay(void) {
    return current_display;
}

GLXFBConfig *glXChooseFBConfig(Display *display, int screen, const int *attrib_list, int *nelements) {
    if (!is_main_steam_process()) {
        if (nelements) *nelements = 0;
        return NULL;
    }
    (void)display;
    (void)screen;
    (void)attrib_list;
    GLXFBConfig *configs = (GLXFBConfig *)malloc(sizeof(GLXFBConfig));
    if (!configs) {
        if (nelements) *nelements = 0;
        return NULL;
    }
    configs[0] = (GLXFBConfig)(uintptr_t)0x1;
    if (nelements) *nelements = 1;
    log_once("returned synthetic FBConfig for main steam");
    return configs;
}

XVisualInfo *glXGetVisualFromFBConfig(Display *display, GLXFBConfig config) {
    if (!is_main_steam_process()) {
        return NULL;
    }
    (void)config;
    XVisualInfo *visual = find_visual(display, 0, 32);
    if (!visual) visual = find_visual(display, 0, 24);
    return visual;
}

const GLubyte *glGetString(GLenum name) {
    switch (name) {
        case 0x1F00: return (const GLubyte *)"WinNative";
        case 0x1F01: return (const GLubyte *)"Steam legacy GL shim";
        case 0x1F02: return (const GLubyte *)"1.4";
        case 0x1F03: return (const GLubyte *)"";
        default: return (const GLubyte *)"";
    }
}

void glGetIntegerv(GLenum pname, GLint *data) {
    if (!data) return;
    switch (pname) {
        case 0x0BA2: /* GL_VIEWPORT */
            data[0] = 0;
            data[1] = 0;
            data[2] = 1280;
            data[3] = 720;
            break;
        case 0x0D33: /* GL_MAX_TEXTURE_SIZE */
            *data = 4096;
            break;
        default:
            *data = 0;
            break;
    }
}

GLenum glGetError(void) { return 0; }
void glClear(GLbitfield mask) { (void)mask; }
void glViewport(GLint x, GLint y, GLsizei width, GLsizei height) { (void)x; (void)y; (void)width; (void)height; }
void glMatrixMode(GLenum mode) { (void)mode; }
void glLoadIdentity(void) {}
void glOrtho(GLdouble left, GLdouble right, GLdouble bottom, GLdouble top, GLdouble near_val, GLdouble far_val) { (void)left; (void)right; (void)bottom; (void)top; (void)near_val; (void)far_val; }
void glTranslatef(GLfloat x, GLfloat y, GLfloat z) { (void)x; (void)y; (void)z; }
void glEnable(GLenum cap) { (void)cap; }
void glDisable(GLenum cap) { (void)cap; }
void glBlendFunc(GLenum sfactor, GLenum dfactor) { (void)sfactor; (void)dfactor; }
void glTexEnvf(GLenum target, GLenum pname, GLfloat param) { (void)target; (void)pname; (void)param; }
void glShadeModel(GLenum mode) { (void)mode; }
void glPixelStorei(GLenum pname, GLint param) { (void)pname; (void)param; }
void glDisableClientState(GLenum array) { (void)array; }
GLint glRenderMode(GLenum mode) { (void)mode; return 0; }
void glLineWidth(GLfloat width) { (void)width; }
void glBegin(GLenum mode) { (void)mode; }
void glEnd(void) {}
void glVertex2f(GLfloat x, GLfloat y) { (void)x; (void)y; }
void glTexCoord2f(GLfloat s, GLfloat t) { (void)s; (void)t; }
void glColor4ub(GLubyte red, GLubyte green, GLubyte blue, GLubyte alpha) { (void)red; (void)green; (void)blue; (void)alpha; }
void glColor4f(GLfloat red, GLfloat green, GLfloat blue, GLfloat alpha) { (void)red; (void)green; (void)blue; (void)alpha; }
void glColor4fv(const GLfloat *v) { (void)v; }
void glGenTextures(GLsizei n, GLuint *textures) {
    if (!textures) return;
    for (GLsizei i = 0; i < n; i++) textures[i] = (GLuint)(next_texture_id++);
}
void glDeleteTextures(GLsizei n, const GLuint *textures) { (void)n; (void)textures; }
void glBindTexture(GLenum target, GLuint texture) { (void)target; (void)texture; }
GLboolean glIsTexture(GLuint texture) { return texture != 0; }
void glTexParameterf(GLenum target, GLenum pname, GLfloat param) { (void)target; (void)pname; (void)param; }
void glTexParameteri(GLenum target, GLenum pname, GLint param) { (void)target; (void)pname; (void)param; }
void glTexImage2D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void *pixels) { (void)target; (void)level; (void)internalformat; (void)width; (void)height; (void)border; (void)format; (void)type; (void)pixels; }
void glTexSubImage2D(GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void *pixels) { (void)target; (void)level; (void)xoffset; (void)yoffset; (void)width; (void)height; (void)format; (void)type; (void)pixels; }
void glReadBuffer(GLenum mode) { (void)mode; }
void glReadPixels(GLint x, GLint y, GLsizei width, GLsizei height, GLenum format, GLenum type, void *pixels) { (void)x; (void)y; (void)width; (void)height; (void)format; (void)type; (void)pixels; }

__GLXextFuncPtr glXGetProcAddress(const GLubyte *procname) {
    if (!procname) return NULL;
    const char *name = (const char *)procname;
#define MAP_PROC(symbol) if (strcmp(name, #symbol) == 0) return (__GLXextFuncPtr)symbol
    MAP_PROC(glGetString);
    MAP_PROC(glGetIntegerv);
    MAP_PROC(glGetError);
    MAP_PROC(glClear);
    MAP_PROC(glViewport);
    MAP_PROC(glMatrixMode);
    MAP_PROC(glLoadIdentity);
    MAP_PROC(glOrtho);
    MAP_PROC(glTranslatef);
    MAP_PROC(glEnable);
    MAP_PROC(glDisable);
    MAP_PROC(glBlendFunc);
    MAP_PROC(glTexEnvf);
    MAP_PROC(glShadeModel);
    MAP_PROC(glPixelStorei);
    MAP_PROC(glDisableClientState);
    MAP_PROC(glRenderMode);
    MAP_PROC(glLineWidth);
    MAP_PROC(glBegin);
    MAP_PROC(glEnd);
    MAP_PROC(glVertex2f);
    MAP_PROC(glTexCoord2f);
    MAP_PROC(glColor4ub);
    MAP_PROC(glColor4f);
    MAP_PROC(glColor4fv);
    MAP_PROC(glGenTextures);
    MAP_PROC(glDeleteTextures);
    MAP_PROC(glBindTexture);
    MAP_PROC(glIsTexture);
    MAP_PROC(glTexParameterf);
    MAP_PROC(glTexParameteri);
    MAP_PROC(glTexImage2D);
    MAP_PROC(glTexSubImage2D);
    MAP_PROC(glReadBuffer);
    MAP_PROC(glReadPixels);
    MAP_PROC(glXSwapBuffers);
    MAP_PROC(glXMakeContextCurrent);
#undef MAP_PROC
    return NULL;
}

__GLXextFuncPtr glXGetProcAddressARB(const GLubyte *procname) {
    return glXGetProcAddress(procname);
}
