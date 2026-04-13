#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "xz.h"

#define XZ_INPUT_BUFFER_SIZE (64 * 1024)
#define XZ_DICT_SIZE_MAX (128U << 20)

typedef struct native_xz_stream {
  FILE *file;
  struct xz_dec *decoder;
  struct xz_buf buffer;
  uint8_t input_buffer[XZ_INPUT_BUFFER_SIZE];
  bool input_finished;
  bool stream_finished;
} native_xz_stream_t;

static pthread_once_t xz_crc_once = PTHREAD_ONCE_INIT;

static void native_xz_init_crc(void) {
  xz_crc32_init();
  xz_crc64_init();
}

static void throw_io_exception(JNIEnv *env, const char *message) {
  jclass io_exception_class = (*env)->FindClass(env, "java/io/IOException");
  if (io_exception_class != NULL) {
    (*env)->ThrowNew(env, io_exception_class, message);
  }
}

static const char *to_xz_error(enum xz_ret ret) {
  switch (ret) {
  case XZ_STREAM_END:
    return "Unexpected XZ end-of-stream state";
  case XZ_MEM_ERROR:
    return "Native XZ decoder ran out of memory";
  case XZ_MEMLIMIT_ERROR:
    return "XZ dictionary exceeds native decoder limit";
  case XZ_FORMAT_ERROR:
    return "Not an XZ stream";
  case XZ_OPTIONS_ERROR:
    return "Unsupported XZ options";
  case XZ_DATA_ERROR:
    return "Corrupt XZ stream";
  case XZ_BUF_ERROR:
    return "Truncated or stalled XZ stream";
  case XZ_UNSUPPORTED_CHECK:
    return "Unsupported XZ integrity check";
  case XZ_OK:
  default:
    return "Unknown native XZ error";
  }
}

static void close_native_xz_stream(native_xz_stream_t *stream) {
  if (stream == NULL)
    return;

  if (stream->decoder != NULL) {
    xz_dec_end(stream->decoder);
    stream->decoder = NULL;
  }

  if (stream->file != NULL) {
    fclose(stream->file);
    stream->file = NULL;
  }

  free(stream);
}

JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_shared_io_NativeXzInputStream_nativeOpen(JNIEnv *env,
                                                                jclass clazz,
                                                                jstring path) {
  (void)clazz;

  if (path == NULL) {
    throw_io_exception(env, "Missing XZ source path");
    return 0;
  }

  if (pthread_once(&xz_crc_once, native_xz_init_crc) != 0) {
    throw_io_exception(env, "Failed to initialize native XZ CRC tables");
    return 0;
  }

  const char *path_chars = (*env)->GetStringUTFChars(env, path, NULL);
  if (path_chars == NULL) {
    return 0;
  }

  native_xz_stream_t *stream = calloc(1, sizeof(native_xz_stream_t));
  if (stream == NULL) {
    (*env)->ReleaseStringUTFChars(env, path, path_chars);
    throw_io_exception(env, "Failed to allocate native XZ stream");
    return 0;
  }

  stream->file = fopen(path_chars, "rb");
  (*env)->ReleaseStringUTFChars(env, path, path_chars);
  if (stream->file == NULL) {
    char error_message[160];
    snprintf(error_message, sizeof(error_message),
             "Failed to open XZ source: %s", strerror(errno));
    close_native_xz_stream(stream);
    throw_io_exception(env, error_message);
    return 0;
  }

  stream->decoder = xz_dec_init(XZ_DYNALLOC, XZ_DICT_SIZE_MAX);
  if (stream->decoder == NULL) {
    close_native_xz_stream(stream);
    throw_io_exception(env, "Failed to initialize native XZ decoder");
    return 0;
  }

  stream->buffer.in = stream->input_buffer;
  stream->buffer.in_pos = 0;
  stream->buffer.in_size = 0;
  stream->buffer.out = NULL;
  stream->buffer.out_pos = 0;
  stream->buffer.out_size = 0;
  return (jlong)(intptr_t)stream;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_shared_io_NativeXzInputStream_nativeRead(
    JNIEnv *env, jclass clazz, jlong handle, jbyteArray output, jint offset,
    jint length) {
  (void)clazz;

  if (length == 0) {
    return 0;
  }

  native_xz_stream_t *stream = (native_xz_stream_t *)(intptr_t)handle;
  if (stream == NULL || stream->decoder == NULL) {
    throw_io_exception(env, "Native XZ stream is closed");
    return -1;
  }

  jbyte *output_bytes = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
  if (output_bytes == NULL) {
    return -1;
  }

  stream->buffer.out = (uint8_t *)(output_bytes + offset);
  stream->buffer.out_pos = 0;
  stream->buffer.out_size = (size_t)length;

  const char *error_msg = NULL;

  while (stream->buffer.out_pos < stream->buffer.out_size) {
    if (stream->stream_finished) {
      break;
    }

    if (stream->buffer.in_pos == stream->buffer.in_size &&
        !stream->input_finished) {
      size_t amount_read = fread(stream->input_buffer, 1,
                                 sizeof(stream->input_buffer), stream->file);
      if (amount_read == 0) {
        if (ferror(stream->file)) {
          error_msg = "Failed reading XZ source";
          break;
        }
        stream->input_finished = true;
      }

      stream->buffer.in = stream->input_buffer;
      stream->buffer.in_pos = 0;
      stream->buffer.in_size = amount_read;
    }

    size_t input_before = stream->buffer.in_pos;
    size_t output_before = stream->buffer.out_pos;
    enum xz_ret ret = xz_dec_catrun(stream->decoder, &stream->buffer,
                                    stream->input_finished ? 1 : 0);

    if (ret == XZ_OK) {
      if (stream->buffer.out_pos == stream->buffer.out_size) {
        break;
      }

      if (stream->buffer.in_pos == input_before &&
          stream->buffer.out_pos == output_before) {
        error_msg = "Native XZ decoder stalled";
        break;
      }
      continue;
    }

    if (ret == XZ_STREAM_END) {
      stream->stream_finished = true;
      break;
    }

    error_msg = to_xz_error(ret);
    break;
  }

  int amount_decoded = (int)stream->buffer.out_pos;
  (*env)->ReleasePrimitiveArrayCritical(env, output, output_bytes, 0);

  if (error_msg != NULL) {
    throw_io_exception(env, error_msg);
    return -1;
  }
  if (amount_decoded == 0 && stream->stream_finished) {
    return -1;
  }
  return amount_decoded;
}

JNIEXPORT void JNICALL
Java_com_winlator_cmod_shared_io_NativeXzInputStream_nativeClose(JNIEnv *env,
                                                                 jclass clazz,
                                                                 jlong handle) {
  (void)env;
  (void)clazz;
  close_native_xz_stream((native_xz_stream_t *)(intptr_t)handle);
}
