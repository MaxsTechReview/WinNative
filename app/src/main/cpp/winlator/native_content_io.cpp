#include <jni.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <curl/curl.h>
#include <lzma.h>
#include <zstd.h>

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_set>
#include <vector>

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {

constexpr const char* kLogTag = "NativeContentIO";
constexpr size_t kBufferSize = 256 * 1024;
constexpr int64_t kProgressBatchBytes = 8 * 1024 * 1024;
constexpr int64_t kProgressBatchIntervalMs = 100;

#define NATIVE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define NATIVE_LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)

std::once_flag g_curl_global_once;

static void ensure_curl_global_init() {
    std::call_once(g_curl_global_once, [] { curl_global_init(CURL_GLOBAL_DEFAULT); });
}

std::string jstr(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

bool mkdirs(std::string_view path) {
    if (path.empty()) return true;
    std::string current;
    current.reserve(path.size());

    size_t pos = 0;
    if (path[0] == '/') {
        current.push_back('/');
        pos = 1;
    }

    while (pos <= path.size()) {
        size_t next = path.find('/', pos);
        std::string_view part =
            path.substr(pos, next == std::string_view::npos ? path.size() - pos : next - pos);
        if (!part.empty()) {
            if (!current.empty() && current.back() != '/') current.push_back('/');
            current.append(part);
            if (::mkdir(current.c_str(), 0771) != 0 && errno != EEXIST) {
                return false;
            }
        }
        if (next == std::string_view::npos) break;
        pos = next + 1;
    }
    return true;
}

bool ensure_parent_dir(const std::string& path) {
    const size_t slash = path.find_last_of('/');
    if (slash == std::string::npos || slash == 0) return true;
    return mkdirs(std::string_view(path).substr(0, slash));
}

std::string join_path(const std::string& base, const std::string& rel) {
    if (base.empty()) return rel;
    if (base.back() == '/') return base + rel;
    return base + "/" + rel;
}

bool is_safe_relative_path(std::string_view path) {
    if (path.empty() || path[0] == '/') return false;
    size_t pos = 0;
    while (pos <= path.size()) {
        size_t next = path.find('/', pos);
        std::string_view part =
            path.substr(pos, next == std::string_view::npos ? path.size() - pos : next - pos);
        if (part == "..") return false;
        if (next == std::string_view::npos) break;
        pos = next + 1;
    }
    return true;
}

std::string clean_entry_name(std::string name) {
    while (name.rfind("./", 0) == 0) {
        name.erase(0, 2);
    }
    return name;
}

std::string trim_trailing_slashes(std::string path) {
    while (path.size() > 1 && path.back() == '/') {
        path.pop_back();
    }
    return path;
}

bool has_symlink_ancestor(
    const std::string& entry_name,
    const std::unordered_set<std::string>& symlink_entries) {
    if (symlink_entries.empty()) return false;
    std::string path = trim_trailing_slashes(entry_name);
    while (true) {
        if (symlink_entries.count(path)) return true;
        const size_t slash = path.find_last_of('/');
        if (slash == std::string::npos || slash == 0) break;
        path.resize(slash);
    }
    return false;
}

std::string parent_entry_name(const std::string& entry_name) {
    std::string normalized = trim_trailing_slashes(entry_name);
    const size_t slash = normalized.find_last_of('/');
    if (slash == std::string::npos) return {};
    return normalized.substr(0, slash);
}

uint64_t parse_tar_number(const char* field, size_t length) {
    if (length == 0) return 0;
    const unsigned char first = static_cast<unsigned char>(field[0]);
    if ((first & 0x80) != 0) {
        uint64_t value = first & 0x7f;
        for (size_t i = 1; i < length; ++i) {
            value = (value << 8) | static_cast<unsigned char>(field[i]);
        }
        return value;
    }

    size_t i = 0;
    while (i < length && (field[i] == ' ' || field[i] == '\0')) ++i;
    uint64_t value = 0;
    for (; i < length; ++i) {
        if (field[i] < '0' || field[i] > '7') break;
        value = (value << 3) + static_cast<uint64_t>(field[i] - '0');
    }
    return value;
}

std::string tar_string(const char* field, size_t length) {
    size_t n = 0;
    while (n < length && field[n] != '\0') ++n;
    return std::string(field, n);
}

std::string read_octal_record_string(std::string data) {
    while (!data.empty() && data.back() == '\0') data.pop_back();
    return data;
}

struct PaxValues {
    std::optional<std::string> path;
    std::optional<std::string> link_path;
};

PaxValues parse_pax(std::string_view data) {
    PaxValues values;
    size_t pos = 0;
    while (pos < data.size()) {
        size_t space = data.find(' ', pos);
        if (space == std::string_view::npos) break;
        size_t len = 0;
        for (size_t i = pos; i < space; ++i) {
            if (data[i] < '0' || data[i] > '9') {
                len = 0;
                break;
            }
            len = len * 10 + static_cast<size_t>(data[i] - '0');
        }
        if (len == 0 || pos + len > data.size()) break;
        std::string_view record = data.substr(space + 1, pos + len - space - 1);
        if (!record.empty() && record.back() == '\n') record.remove_suffix(1);
        size_t eq = record.find('=');
        if (eq != std::string_view::npos) {
            std::string_view key = record.substr(0, eq);
            std::string value(record.substr(eq + 1));
            if (key == "path") values.path = std::move(value);
            if (key == "linkpath") values.link_path = std::move(value);
        }
        pos += len;
    }
    return values;
}

class Reader {
public:
    virtual ~Reader() = default;
    virtual ssize_t read(uint8_t* out, size_t length) = 0;

    bool read_exact(uint8_t* out, size_t length) {
        size_t total = 0;
        while (total < length) {
            ssize_t n = read(out + total, length - total);
            if (n <= 0) return false;
            total += static_cast<size_t>(n);
        }
        return true;
    }

    bool skip(uint64_t amount) {
        uint8_t buffer[8192];
        while (amount > 0) {
            const size_t chunk = static_cast<size_t>(std::min<uint64_t>(amount, sizeof(buffer)));
            if (!read_exact(buffer, chunk)) return false;
            amount -= chunk;
        }
        return true;
    }
};

class FileReader final : public Reader {
public:
    explicit FileReader(std::string path) : file_(std::fopen(path.c_str(), "rb")) {
        if (file_) std::setvbuf(file_, nullptr, _IOFBF, kBufferSize);
    }
    ~FileReader() override {
        if (file_) std::fclose(file_);
    }
    bool ok() const { return file_ != nullptr; }
    ssize_t read(uint8_t* out, size_t length) override {
        if (!file_) return -1;
        size_t n = std::fread(out, 1, length, file_);
        if (n == 0 && std::ferror(file_)) return -1;
        return static_cast<ssize_t>(n);
    }

private:
    FILE* file_ = nullptr;
};

class AssetReader final : public Reader {
public:
    AssetReader(AAssetManager* manager, std::string path)
        : asset_(manager ? AAssetManager_open(manager, path.c_str(), AASSET_MODE_STREAMING) : nullptr) {}

    ~AssetReader() override {
        if (asset_) AAsset_close(asset_);
    }

    bool ok() const { return asset_ != nullptr; }

    ssize_t read(uint8_t* out, size_t length) override {
        if (!asset_) return -1;
        return AAsset_read(asset_, out, length);
    }

private:
    AAsset* asset_ = nullptr;
};

class FileWriter final {
public:
    FileWriter(const std::string& path, bool nofollow) {
        int flags = O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC;
        if (nofollow) flags |= O_NOFOLLOW;
        int fd = ::open(
            path.c_str(),
            flags,
            0660);
        if (fd >= 0) file_ = ::fdopen(fd, "wb");
        if (file_) {
            std::setvbuf(file_, nullptr, _IONBF, 0);
        } else if (fd >= 0) {
            ::close(fd);
        }
    }

    ~FileWriter() {
        close();
    }

    bool ok() const {
        return file_ != nullptr;
    }

    bool write(const uint8_t* data, size_t length) {
        return file_ && std::fwrite(data, 1, length, file_) == length;
    }

    bool close() {
        if (!file_) return true;
        FILE* file = file_;
        file_ = nullptr;
        int fd = ::fileno(file);
        if (fd >= 0) {
            std::fflush(file);
            ::sync_file_range(fd, 0, 0, SYNC_FILE_RANGE_WRITE);
        }
        return std::fclose(file) == 0;
    }

private:
    FILE* file_ = nullptr;
};

class XzReader final : public Reader {
public:
    explicit XzReader(std::unique_ptr<Reader> source) : source_(std::move(source)) {
        ok_ = lzma_stream_decoder(&strm_, UINT64_MAX, LZMA_CONCATENATED) == LZMA_OK;
    }
    ~XzReader() override {
        if (ok_) lzma_end(&strm_);
    }
    bool ok() const { return source_ != nullptr && ok_; }
    ssize_t read(uint8_t* out, size_t length) override {
        if (finished_) return 0;
        strm_.next_out = out;
        strm_.avail_out = length;

        while (strm_.avail_out > 0) {
            if (strm_.avail_in == 0 && !input_finished_) {
                ssize_t n = source_->read(input_.data(), input_.size());
                if (n < 0) return -1;
                if (n == 0) input_finished_ = true;
                strm_.next_in = input_.data();
                strm_.avail_in = static_cast<size_t>(std::max<ssize_t>(n, 0));
            }

            const size_t in_before = strm_.avail_in;
            const size_t out_before = length - strm_.avail_out;
            lzma_ret ret = lzma_code(&strm_, input_finished_ ? LZMA_FINISH : LZMA_RUN);
            if (ret == LZMA_STREAM_END) {
                finished_ = true;
                break;
            }
            if (ret != LZMA_OK) {
                NATIVE_LOGW("XZ decode failed: %d", static_cast<int>(ret));
                return -1;
            }
            if (strm_.avail_in == in_before && (length - strm_.avail_out) == out_before) {
                NATIVE_LOGW("XZ decoder stalled");
                return -1;
            }
        }
        return static_cast<ssize_t>(length - strm_.avail_out);
    }

private:
    std::unique_ptr<Reader> source_;
    lzma_stream strm_ = LZMA_STREAM_INIT;
    bool ok_ = false;
    std::vector<uint8_t> input_ = std::vector<uint8_t>(1 << 20);
    bool input_finished_ = false;
    bool finished_ = false;
};

class ZstdReader final : public Reader {
public:
    explicit ZstdReader(std::unique_ptr<Reader> source)
        : source_(std::move(source)), stream_(ZSTD_createDStream()) {
        if (stream_) {
            size_t rc = ZSTD_initDStream(stream_);
            if (ZSTD_isError(rc)) {
                ZSTD_freeDStream(stream_);
                stream_ = nullptr;
            }
        }
    }
    ~ZstdReader() override {
        if (stream_) ZSTD_freeDStream(stream_);
    }
    bool ok() const { return source_ != nullptr && stream_ != nullptr; }
    ssize_t read(uint8_t* out, size_t length) override {
        if (finished_) return 0;
        ZSTD_outBuffer output{out, length, 0};
        while (output.pos < output.size) {
            if (input_.pos == input_.size && !input_finished_) {
                ssize_t n = source_->read(input_storage_.data(), input_storage_.size());
                if (n < 0) return -1;
                if (n == 0) input_finished_ = true;
                input_ = ZSTD_inBuffer{input_storage_.data(), static_cast<size_t>(std::max<ssize_t>(n, 0)), 0};
            }
            size_t in_before = input_.pos;
            size_t out_before = output.pos;
            size_t rc = ZSTD_decompressStream(stream_, &output, &input_);
            if (ZSTD_isError(rc)) {
                NATIVE_LOGW("Zstd decode failed: %s", ZSTD_getErrorName(rc));
                return -1;
            }
            frame_finished_ = rc == 0;
            if (frame_finished_ && input_finished_ && input_.pos == input_.size) {
                finished_ = true;
                break;
            }
            if (input_finished_ && input_.pos == input_.size && output.pos == out_before && !frame_finished_) {
                NATIVE_LOGW("Zstd stream ended before frame completion");
                return -1;
            }
            if (input_.pos == in_before && output.pos == out_before) {
                NATIVE_LOGW("Zstd decoder stalled");
                return -1;
            }
        }
        return static_cast<ssize_t>(output.pos);
    }

private:
    std::unique_ptr<Reader> source_;
    ZSTD_DStream* stream_ = nullptr;
    std::vector<uint8_t> input_storage_ = std::vector<uint8_t>(ZSTD_DStreamInSize());
    ZSTD_inBuffer input_{nullptr, 0, 0};
    bool input_finished_ = false;
    bool finished_ = false;
    bool frame_finished_ = false;
};

class JavaExtractListener {
public:
    JavaExtractListener(JNIEnv* env, jobject listener) : env_(env), listener_(listener) {
        if (!listener_) return;
        jclass listener_cls = env_->FindClass("com/winlator/cmod/shared/util/OnExtractFileListener");
        on_extract_ =
            env_->GetMethodID(listener_cls, "onExtractFile", "(Ljava/io/File;J)Ljava/io/File;");
        on_progress_ =
            env_->GetMethodID(listener_cls, "onExtractFileProgress", "(Ljava/io/File;J)V");
        maps_files_method_ = env_->GetMethodID(listener_cls, "mapsExtractedFiles", "()Z");
        byte_progress_method_ = env_->GetMethodID(listener_cls, "reportsExtractedBytesOnly", "()Z");
        on_bytes_progress_ = env_->GetMethodID(listener_cls, "onExtractedBytes", "(J)V");

        if (maps_files_method_) {
            maps_files_ = env_->CallBooleanMethod(listener_, maps_files_method_) == JNI_TRUE;
            if (env_->ExceptionCheck()) return;
        }
        if (byte_progress_method_) {
            byte_progress_only_ =
                env_->CallBooleanMethod(listener_, byte_progress_method_) == JNI_TRUE;
            if (env_->ExceptionCheck()) return;
        }

        if (maps_files_ || !byte_progress_only_) {
            jclass file_cls = env_->FindClass("java/io/File");
            file_class_ = static_cast<jclass>(env_->NewLocalRef(file_cls));
            file_ctor_ = env_->GetMethodID(file_class_, "<init>", "(Ljava/lang/String;)V");
            get_path_ = env_->GetMethodID(file_class_, "getPath", "()Ljava/lang/String;");
        }

        enabled_ =
            (!maps_files_ || (file_class_ && file_ctor_ && get_path_ && on_extract_)) &&
            (byte_progress_only_ || !listener_ || (file_class_ && file_ctor_ && on_progress_));
    }

    std::optional<std::string> map(const std::string& destination, int64_t size) {
        if (!listener_) return destination;
        if (!enabled_) return std::nullopt;
        if (!maps_files_) return destination;

        jstring path = env_->NewStringUTF(destination.c_str());
        jobject file = env_->NewObject(file_class_, file_ctor_, path);
        env_->DeleteLocalRef(path);
        jobject mapped =
            env_->CallObjectMethod(listener_, on_extract_, file, static_cast<jlong>(size));
        env_->DeleteLocalRef(file);
        if (env_->ExceptionCheck()) return std::nullopt;
        if (!mapped) return std::nullopt;

        auto mapped_path = static_cast<jstring>(env_->CallObjectMethod(mapped, get_path_));
        env_->DeleteLocalRef(mapped);
        if (env_->ExceptionCheck() || !mapped_path) return std::nullopt;
        std::string result = jstr(env_, mapped_path);
        env_->DeleteLocalRef(mapped_path);
        return result;
    }

    bool progress(const std::string& destination, int64_t size) {
        if (!listener_ || !enabled_) return true;
        if (byte_progress_only_) {
            pending_progress_bytes_ += std::max<int64_t>(size, 0);
            auto now = std::chrono::steady_clock::now();
            int64_t elapsed_ms =
                std::chrono::duration_cast<std::chrono::milliseconds>(now - last_progress_flush_).count();
            if (pending_progress_bytes_ >= kProgressBatchBytes || elapsed_ms >= kProgressBatchIntervalMs) {
                flush_progress();
            }
            return !env_->ExceptionCheck();
        }
        if (!on_progress_) return true;

        jstring path = env_->NewStringUTF(destination.c_str());
        jobject file = env_->NewObject(file_class_, file_ctor_, path);
        env_->DeleteLocalRef(path);
        env_->CallVoidMethod(listener_, on_progress_, file, static_cast<jlong>(size));
        env_->DeleteLocalRef(file);
        return !env_->ExceptionCheck();
    }

    bool flush_progress() {
        if (!listener_ || !enabled_ || !byte_progress_only_ || !on_bytes_progress_) return true;
        if (pending_progress_bytes_ <= 0) return true;
        int64_t bytes = pending_progress_bytes_;
        pending_progress_bytes_ = 0;
        last_progress_flush_ = std::chrono::steady_clock::now();
        env_->CallVoidMethod(listener_, on_bytes_progress_, static_cast<jlong>(bytes));
        return !env_->ExceptionCheck();
    }

private:
    JNIEnv* env_;
    jobject listener_;
    jclass file_class_ = nullptr;
    jmethodID file_ctor_ = nullptr;
    jmethodID get_path_ = nullptr;
    jmethodID on_extract_ = nullptr;
    jmethodID on_progress_ = nullptr;
    jmethodID maps_files_method_ = nullptr;
    jmethodID byte_progress_method_ = nullptr;
    jmethodID on_bytes_progress_ = nullptr;
    bool enabled_ = false;
    bool maps_files_ = true;
    bool byte_progress_only_ = false;
    int64_t pending_progress_bytes_ = 0;
    std::chrono::steady_clock::time_point last_progress_flush_ = std::chrono::steady_clock::now();
};

bool read_payload(Reader& reader, uint64_t size, std::string* out) {
    if (size > 64 * 1024 * 1024) return false;
    out->assign(static_cast<size_t>(size), '\0');
    if (size > 0 && !reader.read_exact(reinterpret_cast<uint8_t*>(out->data()), static_cast<size_t>(size))) {
        return false;
    }
    const uint64_t padding = (512 - (size % 512)) % 512;
    return reader.skip(padding);
}

bool extract_tar(
    Reader& reader,
    const std::string& destination,
    JNIEnv* env,
    jobject listener,
    bool enforce_safe_symlinks) {
    JavaExtractListener java_listener(env, listener);
    std::vector<uint8_t> header(512);
    std::optional<std::string> next_name;
    std::optional<std::string> next_link;
    std::unordered_set<std::string> symlink_entries;

    std::vector<uint8_t> file_buffer(kBufferSize);
    std::unordered_set<std::string> created_dirs;
    const auto mkdirs_cached = [&](std::string_view dir) -> bool {
        if (dir.empty()) return true;
        if (created_dirs.find(std::string(dir)) != created_dirs.end()) return true;
        std::string current;
        current.reserve(dir.size());
        size_t pos = 0;
        if (dir[0] == '/') {
            current.push_back('/');
            pos = 1;
        }
        while (pos <= dir.size()) {
            size_t next = dir.find('/', pos);
            std::string_view part =
                dir.substr(pos, next == std::string_view::npos ? dir.size() - pos : next - pos);
            if (!part.empty()) {
                if (!current.empty() && current.back() != '/') current.push_back('/');
                current.append(part);
                if (created_dirs.insert(current).second) {
                    if (::mkdir(current.c_str(), 0771) != 0 && errno != EEXIST) return false;
                }
            }
            if (next == std::string_view::npos) break;
            pos = next + 1;
        }
        return true;
    };
    const auto ensure_parent_cached = [&](const std::string& path) -> bool {
        const size_t slash = path.find_last_of('/');
        if (slash == std::string::npos || slash == 0) return true;
        return mkdirs_cached(std::string_view(path).substr(0, slash));
    };

    while (true) {
        if (!reader.read_exact(header.data(), header.size())) return false;
        bool all_zero = true;
        for (uint8_t b : header) {
            if (b != 0) {
                all_zero = false;
                break;
            }
        }
        if (all_zero) return java_listener.flush_progress();

        auto* h = reinterpret_cast<const char*>(header.data());
        std::string name = tar_string(h, 100);
        std::string prefix = tar_string(h + 345, 155);
        if (!prefix.empty()) name = prefix + "/" + name;
        std::string link_name = tar_string(h + 157, 100);
        const uint64_t size = parse_tar_number(h + 124, 12);
        const uint32_t mode = static_cast<uint32_t>(parse_tar_number(h + 100, 8));
        const char type = h[156] == '\0' ? '0' : h[156];

        if (type == 'L' || type == 'K') {
            std::string payload;
            if (!read_payload(reader, size, &payload)) return false;
            if (type == 'L') next_name = clean_entry_name(read_octal_record_string(std::move(payload)));
            if (type == 'K') next_link = read_octal_record_string(std::move(payload));
            continue;
        }

        if (type == 'x' || type == 'g') {
            std::string payload;
            if (!read_payload(reader, size, &payload)) return false;
            if (type == 'x') {
                PaxValues pax = parse_pax(payload);
                if (pax.path) next_name = clean_entry_name(*pax.path);
                if (pax.link_path) next_link = *pax.link_path;
            }
            continue;
        }

        if (next_name) {
            name = *next_name;
            next_name.reset();
        } else {
            name = clean_entry_name(std::move(name));
        }
        if (next_link) {
            link_name = *next_link;
            next_link.reset();
        }

        const uint64_t padding = (512 - (size % 512)) % 512;
        if (!is_safe_relative_path(name)) {
            if (!reader.skip(size + padding)) return false;
            continue;
        }

        const bool is_symlink = type == '2';
        if (!is_symlink && has_symlink_ancestor(name, symlink_entries)) {
            NATIVE_LOGW("skipping archive entry under symlink: %s", name.c_str());
            if (!reader.skip(size + padding)) return false;
            continue;
        }

        std::string out_path = join_path(destination, name);
        auto mapped = java_listener.map(out_path, static_cast<int64_t>(size));
        if (!mapped) {
            if (!reader.skip(size + padding)) return false;
            continue;
        }
        out_path = std::move(*mapped);

        if (type == '5') {
            if (!mkdirs_cached(out_path)) return false;
        } else if (type == '2') {
            // Wine prefixes legitimately use links like c: -> ../drive_c and z: -> /.
            // Allow the link itself, but never extract later archive entries through it.
            std::string parent_name = parent_entry_name(name);
            if (!parent_name.empty() && has_symlink_ancestor(parent_name, symlink_entries)) {
                NATIVE_LOGW("skipping symlink under symlinked parent: %s", name.c_str());
                if (!reader.skip(size + padding)) return false;
                continue;
            }
            if (!ensure_parent_cached(out_path)) return false;
            ::unlink(out_path.c_str());
            if (::symlink(link_name.c_str(), out_path.c_str()) != 0 && errno != EEXIST) {
                NATIVE_LOGW("symlink failed for %s: %s", out_path.c_str(), std::strerror(errno));
            }
            symlink_entries.insert(trim_trailing_slashes(name));
        } else if (type == '0' || type == '\0') {
            if (!ensure_parent_cached(out_path)) return false;
            FileWriter out(out_path, enforce_safe_symlinks);
            if (!out.ok()) return false;

            uint64_t remaining = size;
            bool ok = true;
            while (remaining > 0) {
                const size_t chunk = static_cast<size_t>(std::min<uint64_t>(remaining, file_buffer.size()));
                if (!reader.read_exact(file_buffer.data(), chunk)) {
                    ok = false;
                    break;
                }
                if (!out.write(file_buffer.data(), chunk)) {
                    ok = false;
                    break;
                }
                remaining -= chunk;
            }
            if (!out.close()) ok = false;
            if (!ok) return false;
            if ((mode & 0111) != 0) ::chmod(out_path.c_str(), 0771);
            if (!java_listener.progress(out_path, static_cast<int64_t>(size))) return false;
            if (!reader.skip(padding)) return false;
            continue;
        } else if (type == '1') {
            if (!ensure_parent_cached(out_path)) return false;
            ::unlink(out_path.c_str());
            std::string clean_link_name = clean_entry_name(link_name);
            if (!is_safe_relative_path(clean_link_name)) {
                if (!reader.skip(size + padding)) return false;
                continue;
            }
            if (has_symlink_ancestor(clean_link_name, symlink_entries)) {
                NATIVE_LOGW("skipping hard link through symlink ancestor: %s", clean_link_name.c_str());
                if (!reader.skip(size + padding)) return false;
                continue;
            }
            std::string link_path = join_path(destination, clean_link_name);
            if (::link(link_path.c_str(), out_path.c_str()) != 0) {
                NATIVE_LOGW("hard link failed for %s: %s", out_path.c_str(), std::strerror(errno));
            }
            if (!java_listener.progress(out_path, static_cast<int64_t>(size))) return false;
        }

        if (!reader.skip(size + padding)) return false;
        if (type == '5') ::chmod(out_path.c_str(), 0771);
    }
}

struct DownloadContext {
    FILE* file = nullptr;
    JNIEnv* env = nullptr;
    jobject listener = nullptr;
    jmethodID on_progress = nullptr;
    std::chrono::steady_clock::time_point last_update = std::chrono::steady_clock::now();
};

size_t curl_write_file(char* ptr, size_t size, size_t nmemb, void* userdata) {
    auto* ctx = static_cast<DownloadContext*>(userdata);
    const size_t bytes = size * nmemb;
    return std::fwrite(ptr, 1, bytes, ctx->file);
}

int curl_progress(void* userdata, curl_off_t total, curl_off_t now, curl_off_t, curl_off_t) {
    auto* ctx = static_cast<DownloadContext*>(userdata);
    if (!ctx->listener || !ctx->on_progress) return 0;

    auto current = std::chrono::steady_clock::now();
    if (now == 0 || total == now ||
        std::chrono::duration_cast<std::chrono::milliseconds>(current - ctx->last_update).count() >= 80) {
        ctx->env->CallVoidMethod(
            ctx->listener,
            ctx->on_progress,
            static_cast<jlong>(now),
            static_cast<jlong>(total > 0 ? total : -1));
        ctx->last_update = current;
        if (ctx->env->ExceptionCheck()) return 1;
    }
    return 0;
}

void configure_curl_common(CURL* curl, const std::string& url, const std::string& ca_bundle) {
    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 15L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 0L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, 1L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME, 30L);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "WinNative/1.0");
    curl_easy_setopt(curl, CURLOPT_ACCEPT_ENCODING, "identity");
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L);
    if (!ca_bundle.empty()) curl_easy_setopt(curl, CURLOPT_CAINFO, ca_bundle.c_str());
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_shared_io_NativeContentIO_nativeExtractArchive(
    JNIEnv* env, jclass, jint type, jstring jsource, jstring jdestination, jobject listener) {
    std::string source = jstr(env, jsource);
    std::string destination = jstr(env, jdestination);
    if (source.empty() || destination.empty()) return JNI_FALSE;
    if (!mkdirs(destination)) return JNI_FALSE;

    bool ok = false;
    if (type == 0) {
        auto raw = std::make_unique<FileReader>(source);
        if (!raw->ok()) return JNI_FALSE;
        XzReader reader(std::move(raw));
        ok = reader.ok() && extract_tar(reader, destination, env, listener, true);
    } else if (type == 1) {
        auto raw = std::make_unique<FileReader>(source);
        if (!raw->ok()) return JNI_FALSE;
        ZstdReader reader(std::move(raw));
        ok = reader.ok() && extract_tar(reader, destination, env, listener, true);
    }
    if (!ok && env->ExceptionCheck()) return JNI_FALSE;
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_shared_io_NativeContentIO_nativeExtractAsset(
    JNIEnv* env,
    jclass,
    jint type,
    jobject jasset_manager,
    jstring jasset_file,
    jstring jdestination,
    jobject listener) {
    AAssetManager* manager = AAssetManager_fromJava(env, jasset_manager);
    std::string asset_file = jstr(env, jasset_file);
    std::string destination = jstr(env, jdestination);
    if (!manager || asset_file.empty() || destination.empty()) return JNI_FALSE;
    if (!mkdirs(destination)) return JNI_FALSE;

    bool ok = false;
    if (type == 0) {
        auto raw = std::make_unique<AssetReader>(manager, asset_file);
        if (!raw->ok()) return JNI_FALSE;
        XzReader reader(std::move(raw));
        ok = reader.ok() && extract_tar(reader, destination, env, listener, false);
    } else if (type == 1) {
        auto raw = std::make_unique<AssetReader>(manager, asset_file);
        if (!raw->ok()) return JNI_FALSE;
        ZstdReader reader(std::move(raw));
        ok = reader.ok() && extract_tar(reader, destination, env, listener, false);
    }
    if (!ok && env->ExceptionCheck()) return JNI_FALSE;
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_shared_io_NativeContentIO_nativeDownloadFile(
    JNIEnv* env, jclass, jstring jaddress, jstring jdestination, jstring jca_bundle, jobject listener) {
    std::string address = jstr(env, jaddress);
    std::string destination = jstr(env, jdestination);
    std::string ca_bundle = jstr(env, jca_bundle);
    if (address.empty() || destination.empty() || !ensure_parent_dir(destination)) return JNI_FALSE;

    ensure_curl_global_init();
    std::string partial = destination + ".part";
    FILE* file = std::fopen(partial.c_str(), "wb");
    if (!file) return JNI_FALSE;
    std::setvbuf(file, nullptr, _IOFBF, kBufferSize);

    DownloadContext ctx;
    ctx.file = file;
    ctx.env = env;
    ctx.listener = listener;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        ctx.on_progress = env->GetMethodID(cls, "onProgress", "(JJ)V");
        if (ctx.on_progress) {
            env->CallVoidMethod(listener, ctx.on_progress, static_cast<jlong>(0), static_cast<jlong>(-1));
        }
    }

    CURL* curl = curl_easy_init();
    if (!curl) {
        std::fclose(file);
        ::unlink(partial.c_str());
        return JNI_FALSE;
    }
    configure_curl_common(curl, address, ca_bundle);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, curl_write_file);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &ctx);
    curl_easy_setopt(curl, CURLOPT_NOPROGRESS, 0L);
    curl_easy_setopt(curl, CURLOPT_XFERINFOFUNCTION, curl_progress);
    curl_easy_setopt(curl, CURLOPT_XFERINFODATA, &ctx);

    CURLcode rc = curl_easy_perform(curl);
    long status = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    curl_off_t downloaded = 0;
    curl_off_t expected = -1;
    curl_easy_getinfo(curl, CURLINFO_SIZE_DOWNLOAD_T, &downloaded);
    curl_easy_getinfo(curl, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &expected);
    curl_easy_cleanup(curl);

    bool ok = (rc == CURLE_OK && status >= 200 && status < 300);
    if (expected >= 0 && downloaded != expected) ok = false;
    if (std::fclose(file) != 0) ok = false;

    if (ok && listener && ctx.on_progress && !env->ExceptionCheck()) {
        env->CallVoidMethod(listener, ctx.on_progress, static_cast<jlong>(downloaded), static_cast<jlong>(downloaded));
    }

    if (!ok || env->ExceptionCheck()) {
        ::unlink(partial.c_str());
        NATIVE_LOGW("download failed for %s: curl=%d http=%ld", address.c_str(), static_cast<int>(rc), status);
        return JNI_FALSE;
    }

    ::unlink(destination.c_str());
    if (::rename(partial.c_str(), destination.c_str()) != 0) {
        ::unlink(partial.c_str());
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_shared_io_NativeContentIO_nativeFetchContentLength(
    JNIEnv* env, jclass, jstring jaddress, jstring jca_bundle) {
    std::string address = jstr(env, jaddress);
    std::string ca_bundle = jstr(env, jca_bundle);
    if (address.empty()) return -1;

    ensure_curl_global_init();
    CURL* curl = curl_easy_init();
    if (!curl) return -1;
    configure_curl_common(curl, address, ca_bundle);
    curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
    curl_easy_setopt(curl, CURLOPT_HEADER, 0L);

    CURLcode rc = curl_easy_perform(curl);
    long status = 0;
    curl_off_t length = -1;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    curl_easy_getinfo(curl, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &length);
    curl_easy_cleanup(curl);
    if (rc != CURLE_OK || status < 200 || status >= 300 || length < 0) return -1;
    return static_cast<jlong>(length);
}
