#include "wn_steam/depot_writer.h"

#include <android/log.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "wn_steam/depot_chunk.h"

namespace wn_steam {

namespace {
constexpr const char* kLogTag = "WnSteamDepotWriter";
#define WN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define WN_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  kLogTag, __VA_ARGS__)

// EDepotFileFlag bits we care about.
constexpr uint32_t kFlagExecutable = 32;
constexpr uint32_t kFlagDirectory  = 64;

// A failing chunk fetch is retried this many times (with backoff, rotating
// CDN servers) before the depot is declared failed. A transient network
// blip or a flaky CDN edge therefore no longer aborts a whole download.
constexpr unsigned kMaxChunkAttempts = 5;

// Chunks are <2MB and finish in 1–2s on a healthy edge; longer than this
// flags the worker to rotate edges on its next chunk so it doesn't stay
// stuck on a throttled one for the whole run (visible as post-resume
// throughput drop when workers re-pick their pre-pause edges).
constexpr std::chrono::seconds kSlowChunkRotateThreshold{8};
constexpr unsigned kSlowChunkRotateConsecutiveLimit = 3;

// Backoff before chunk retry `attempt` (attempt is >= 1): 300ms, 600ms,
// 1200ms, 2400ms — capped so a long stall doesn't wedge a worker forever.
std::chrono::milliseconds retry_backoff(unsigned attempt) {
    unsigned ms = 300u << (attempt - 1);
    if (ms > 4000u) ms = 4000u;
    return std::chrono::milliseconds(ms);
}

// Steam's chunk checksum (ContentManifest ChunkData.crc) — an Adler-32
// variant seeded with a=0 (NOT zlib's a=1). Matches steam_adler_hash in
// depot_chunk.cpp / SteamKit2 Util.AdlerHash. Used by the resume path to
// check whether a chunk's bytes are already correct on disk.
//
// Uses the standard deferred-modulo form (one modulo per 5552-byte block
// instead of two per byte) — mathematically identical to the naive loop
// because modulo distributes over the additions, but several times faster.
// This is the verify hot path, so it matters on multi-GB games.
uint32_t depot_adler_hash(std::span<const uint8_t> data) {
    constexpr size_t kBlock = 5552;   // zlib NMAX — largest overflow-safe run
    uint32_t a = 0, b = 0;
    size_t i = 0;
    const size_t n = data.size();
    while (i < n) {
        const size_t block = std::min(kBlock, n - i);
        for (size_t j = 0; j < block; ++j) {
            a += data[i + j];
            b += a;
        }
        a %= 65521;
        b %= 65521;
        i += block;
    }
    return a | (b << 16);
}

DepotWriteResult fail(std::string msg, bool resume_trust_safe = false) {
    DepotWriteResult r;
    r.resume_trust_safe = resume_trust_safe;
    r.error = std::move(msg);
    WN_LOGE("%s", r.error.c_str());
    return r;
}

// A manifest filename is server-controlled. Reject any path that could
// escape target_dir (absolute, or a ".." component) before touching disk.
bool path_is_safe(std::string_view rel) {
    if (rel.empty()) return false;
    if (rel.front() == '/') return false;
    size_t start = 0;
    while (start <= rel.size()) {
        size_t slash = rel.find('/', start);
        std::string_view comp = rel.substr(
            start, slash == std::string_view::npos ? std::string_view::npos
                                                   : slash - start);
        if (comp == "..") return false;
        if (slash == std::string_view::npos) break;
        start = slash + 1;
    }
    return true;
}

// mkdir -p for the directory portion of `path` (everything before the last
// '/'). Existing directories are fine.
bool make_parent_dirs(const std::string& path) {
    size_t slash = path.rfind('/');
    if (slash == std::string::npos || slash == 0) return true;
    std::string dir = path.substr(0, slash);
    std::string acc;
    acc.reserve(dir.size());
    for (size_t i = 0; i <= dir.size(); ++i) {
        if (i == dir.size() || dir[i] == '/') {
            if (!acc.empty() && acc != "/") {
                if (::mkdir(acc.c_str(), 0755) != 0 && errno != EEXIST) {
                    WN_LOGE("mkdir(%s): %s", acc.c_str(), std::strerror(errno));
                    return false;
                }
            }
        }
        if (i < dir.size()) acc.push_back(dir[i]);
    }
    return true;
}

// Return false when the filesystem can prove this byte range is an unallocated
// sparse hole. Older WinNative builds pre-sized every manifest file before any
// chunks landed, so resume validation could waste minutes checksumming zeroed
// holes for chunks that were never downloaded.
bool range_may_have_data(int fd, uint64_t offset, uint32_t size,
                         off_t file_size) {
    if (size == 0) return false;
    if (offset >= static_cast<uint64_t>(file_size)) return false;
    const uint64_t end = offset + static_cast<uint64_t>(size);
    if (end > static_cast<uint64_t>(file_size)) return false;

#ifdef SEEK_DATA
    errno = 0;
    off_t data = ::lseek(fd, static_cast<off_t>(offset), SEEK_DATA);
    if (data < 0) {
        if (errno == ENXIO) return false;
        // Filesystem does not support SEEK_DATA or returned an unexpected
        // error. Fall back to reading and checksumming the range.
        return true;
    }
    return static_cast<uint64_t>(data) < end;
#else
    (void)fd;
    return true;
#endif
}

bool range_is_fully_allocated(int fd, uint64_t offset, uint32_t size,
                              off_t file_size) {
    if (size == 0) return false;
    if (offset >= static_cast<uint64_t>(file_size)) return false;
    const uint64_t end = offset + static_cast<uint64_t>(size);
    if (end > static_cast<uint64_t>(file_size)) return false;

#if defined(SEEK_DATA) && defined(SEEK_HOLE)
    errno = 0;
    off_t data = ::lseek(fd, static_cast<off_t>(offset), SEEK_DATA);
    if (data < 0 || static_cast<uint64_t>(data) != offset) return false;

    errno = 0;
    off_t hole = ::lseek(fd, static_cast<off_t>(offset), SEEK_HOLE);
    if (hole < 0) return false;
    return static_cast<uint64_t>(hole) >= end;
#else
    (void)fd;
    return false;
#endif
}

}  // namespace

DepotWriteResult write_depot(const ContentManifest& manifest,
                             std::span<const uint8_t> depot_key,
                             CdnClient& cdn,
                             const std::vector<pb::CContentServerDirectory_ServerInfo>& servers,
                             const std::string& target_dir,
                             std::string_view cdn_auth_token,
                             const DepotWriteProgress& progress,
                             const std::atomic<bool>* cancel,
                             unsigned max_workers,
                             bool trust_existing_chunks,
                             const std::function<void()>& before_download,
                             DepotProgressStore* progress_store) {
    if (manifest.metadata.filenames_encrypted) {
        return fail("write_depot: manifest filenames are still encrypted");
    }
    if (depot_key.size() != 32) return fail("write_depot: bad depot key length");
    if (servers.empty())        return fail("write_depot: no CDN servers");
    const unsigned server_count = static_cast<unsigned>(servers.size());

    const auto cancelled = [cancel]() { return cancel && cancel->load(); };

    uint64_t total_bytes = 0;
    for (const auto& f : manifest.files) total_bytes += f.size;

    DepotWriteResult result;

    // One outstanding chunk to download: indices into manifest.files and
    // that file's chunk list (zero-copy — the manifest outlives this call).
    struct ChunkJob {
        uint32_t file_idx;
        uint32_t chunk_idx;
    };
    std::vector<std::string> file_paths(manifest.files.size());
    std::vector<ChunkJob>    jobs;            // chunks that need a CDN download
    std::vector<uint32_t>    validate_files;  // regular files with on-disk
                                              // content whose chunks must be
                                              // Adler-checked (Phase A2)
    // Decompressed bytes confirmed present: advanced by the parallel
    // validation in Phase A2 and the download workers in Phase B.
    std::atomic<uint64_t> bytes_done{0};
    std::mutex            jobs_mtx;           // guards `jobs` appends from A2

    // ── Phase A — single-threaded prep ──────────────────────────────────
    // Create directories / symlinks and create regular files without
    // pre-sizing them. Pre-sizing every file makes a partially downloaded
    // depot look fully present on resume, so validation has to read sparse
    // placeholders for chunks that were never downloaded.
    for (uint32_t fi = 0; fi < manifest.files.size(); ++fi) {
        const auto& f = manifest.files[fi];
        if (cancelled()) return fail("write_depot: cancelled");
        if (!path_is_safe(f.filename)) {
            return fail("write_depot: unsafe path '" + f.filename + "'");
        }
        const std::string path = target_dir + "/" + f.filename;
        file_paths[fi] = path;

        // Symlink — identified by a non-empty linktarget.
        if (!f.linktarget.empty()) {
            if (!make_parent_dirs(path)) return fail("write_depot: mkdir failed");
            ::unlink(path.c_str());
            if (::symlink(f.linktarget.c_str(), path.c_str()) != 0) {
                return fail("write_depot: symlink '" + f.filename + "': "
                            + std::strerror(errno));
            }
            ++result.files_written;
            continue;
        }

        // Directory. The manifest file list is not guaranteed parent-first,
        // so create any missing ancestors before the directory itself.
        if (f.flags & kFlagDirectory) {
            if (!make_parent_dirs(path)) return fail("write_depot: mkdir failed");
            if (::mkdir(path.c_str(), 0755) != 0 && errno != EEXIST) {
                return fail("write_depot: mkdir '" + f.filename + "': "
                            + std::strerror(errno));
            }
            continue;
        }

        // Resume fast-path: this file was recorded fully written + fdatasync'd
        // (and ftruncate'd to f.size) on an earlier run. Skip it outright —
        // no re-open, no chunk re-hash. A size check guards against a file
        // the user deleted or that is the wrong size: those fall through and
        // rebuild. (Only regular files are ever recorded, so this never
        // matches a directory or symlink.)
        if (progress_store && progress_store->is_file_done(fi)) {
            struct stat done_st {};
            if (::stat(path.c_str(), &done_st) == 0 &&
                static_cast<uint64_t>(done_st.st_size) == f.size) {
                bytes_done.fetch_add(f.size, std::memory_order_relaxed);
                ++result.files_written;
                continue;
            }
        }

        // Regular file: create if missing, but don't pre-size. A pre-existing
        // file with content may already hold this depot's chunks (resume, or a
        // verify pass), and successful completion truncates to the exact
        // manifest size below.
        if (!make_parent_dirs(path)) return fail("write_depot: mkdir failed");
        const mode_t mode = (f.flags & kFlagExecutable) ? 0755 : 0644;
        struct stat prev_st {};
        const bool had_content =
            (::stat(path.c_str(), &prev_st) == 0 && prev_st.st_size > 0);
        int fd = ::open(path.c_str(), O_RDWR | O_CREAT, mode);
        if (fd < 0) {
            return fail("write_depot: open '" + f.filename + "': "
                        + std::strerror(errno));
        }
        ::close(fd);
        ++result.files_written;

        if (f.chunks.empty()) {
            // Nothing to download — the file is complete the moment it exists.
            if (progress_store) progress_store->mark_file_done(fi);
            continue;
        }
        if (had_content) {
            validate_files.push_back(fi);
        } else {
            for (uint32_t ci = 0; ci < f.chunks.size(); ++ci) {
                jobs.push_back({fi, ci});
            }
        }
    }

    if (progress) {
        progress(bytes_done.load(std::memory_order_relaxed), total_bytes, true);
    }

    // ── Phase A2 — parallel on-disk validation ──────────────────────────
    // The verify hot path. Each worker takes whole files (open once, O_RDONLY)
    // and Adler-checks every chunk: chunks already correct on disk are counted
    // done, the rest become download jobs. Single-threaded this ran at
    // ~20 MB/s; parallel it is bounded by storage read bandwidth.
    if (!validate_files.empty() && !cancelled()) {
        unsigned vn = max_workers == 0 ? 1u : max_workers;
        vn = std::min<unsigned>(vn, 64u);
        vn = std::min<unsigned>(vn, static_cast<unsigned>(validate_files.size()));

        std::atomic<size_t> next_vf{0};
        std::atomic<int>    v_active{static_cast<int>(vn)};

        auto validator = [&]() {
            std::vector<ChunkJob> local;
            while (true) {
                if (cancelled()) break;
                const size_t vi =
                    next_vf.fetch_add(1, std::memory_order_relaxed);
                if (vi >= validate_files.size()) break;

                const uint32_t fi = validate_files[vi];
                const auto&    f  = manifest.files[fi];
                local.clear();

                int fd = ::open(file_paths[fi].c_str(), O_RDONLY);
                if (fd < 0) {
                    // Cannot read it back — every chunk needs a download.
                    for (uint32_t ci = 0; ci < f.chunks.size(); ++ci)
                        local.push_back({fi, ci});
                } else {
                    struct stat st {};
                    const off_t file_size =
                        (::fstat(fd, &st) == 0 && st.st_size > 0)
                            ? st.st_size
                            : 0;
                    for (uint32_t ci = 0; ci < f.chunks.size(); ++ci) {
                        const auto& chunk = f.chunks[ci];
                        bool on_disk = false;
                        if (trust_existing_chunks &&
                            range_is_fully_allocated(fd, chunk.offset,
                                                     chunk.cb_original,
                                                     file_size)) {
                            on_disk = true;
                        } else if (range_may_have_data(fd, chunk.offset,
                                                       chunk.cb_original,
                                                       file_size)) {
                            std::vector<uint8_t> buf(chunk.cb_original);
                            ssize_t rd = ::pread(fd, buf.data(), buf.size(),
                                                 static_cast<off_t>(chunk.offset));
                            if (rd == static_cast<ssize_t>(buf.size()) &&
                                depot_adler_hash(buf) == chunk.crc) {
                                on_disk = true;
                            }
                        }
                        if (on_disk) {
                            bytes_done.fetch_add(chunk.cb_original,
                                                 std::memory_order_relaxed);
                        } else {
                            local.push_back({fi, ci});
                        }
                    }
                    // Every chunk verified present → the file is about to be
                    // recorded done. The sidecar promises durability, but
                    // these bytes may still be un-synced page cache from an
                    // earlier interrupted run — flush before we trust them.
                    // (fsync on an O_RDONLY fd is valid and flushes the file.)
                    if (local.empty()) ::fdatasync(fd);
                    ::close(fd);
                }
                if (!local.empty()) {
                    std::lock_guard<std::mutex> lk(jobs_mtx);
                    jobs.insert(jobs.end(), local.begin(), local.end());
                } else if (progress_store) {
                    // Every chunk verified present on disk — the file is
                    // complete and durable. Record it so the next resume
                    // skips it entirely instead of re-hashing it again.
                    progress_store->mark_file_done(fi);
                }
            }
            v_active.fetch_sub(1, std::memory_order_acq_rel);
        };

        std::vector<std::thread> vpool;
        vpool.reserve(vn);
        for (unsigned w = 0; w < vn; ++w) vpool.emplace_back(validator);
        unsigned vtick = 0;
        while (v_active.load(std::memory_order_acquire) > 0) {
            if (progress) {
                progress(bytes_done.load(std::memory_order_relaxed),
                         total_bytes, true);
            }
            // Persist newly-validated files every ~3s so an interruption
            // mid-verify doesn't throw away the validation work.
            if (progress_store && ++vtick % 20 == 0) progress_store->flush();
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
        }
        for (auto& t : vpool) t.join();
        if (progress_store) progress_store->flush();
        if (cancelled()) return fail("write_depot: cancelled");
    }
    result.resume_trust_safe = true;

    const bool any_download = !jobs.empty();
    // The validation→download boundary should flip the UI out of verifying as
    // soon as missing chunks are known. Waiting for the worker progress loop
    // makes resume look stuck if connection setup or the first CDN chunk takes
    // a while after a long validation pass.
    if (progress) {
        progress(bytes_done.load(std::memory_order_relaxed), total_bytes,
                 !any_download);
    }

    // ── Phase B — parallel chunk download ───────────────────────────────
    // N workers, each with its own keep-alive CdnConnection, pull jobs off a
    // shared atomic cursor. pwrite() is positional, so concurrent writes to
    // distinct offsets — even within one file — need no file locking.
    //
    // file_remaining[fi] counts how many of file `fi`'s chunks still need a
    // download. The worker that lands the last one fdatasync's the file and
    // records it done in the progress store — so a later resume skips it.
    std::vector<std::atomic<uint32_t>> file_remaining(manifest.files.size());
    for (const auto& j : jobs) {
        file_remaining[j.file_idx].fetch_add(1, std::memory_order_relaxed);
    }

    unsigned workers_used = 0;
    if (cancelled()) return fail("write_depot: cancelled", result.resume_trust_safe);

    if (!jobs.empty()) {
        if (before_download) before_download();
        unsigned n = max_workers == 0 ? 1u : max_workers;
        n = std::min<unsigned>(n, 64u);
        n = std::min<unsigned>(n, static_cast<unsigned>(jobs.size()));
        workers_used = n;

        std::atomic<size_t> next_job{0};
        std::atomic<int>    active{static_cast<int>(n)};
        std::atomic<bool>   failed{false};
        std::mutex          err_mtx;
        std::string         err;

        auto record_error = [&](std::string msg) {
            std::lock_guard<std::mutex> lk(err_mtx);
            if (err.empty()) err = std::move(msg);
            failed.store(true, std::memory_order_release);
        };

        auto worker = [&](unsigned worker_index) {
            CdnConnection conn;   // one reused TCP+TLS connection per worker
            // Spread workers across the CDN server list; rotation is
            // triggered on fetch failure or after multiple consecutive
            // slow-but-successful chunks (see kSlowChunkRotateThreshold).
            unsigned srv_idx = worker_index % server_count;
            unsigned consecutive_slow_chunks = 0;
            while (true) {
                if (failed.load(std::memory_order_acquire) || cancelled()) break;
                const size_t i =
                    next_job.fetch_add(1, std::memory_order_relaxed);
                if (i >= jobs.size()) break;

                const ChunkJob     job   = jobs[i];
                const auto&        f     = manifest.files[job.file_idx];
                const auto&        chunk = f.chunks[job.chunk_idx];
                const std::string& path  = file_paths[job.file_idx];

                if (consecutive_slow_chunks >= kSlowChunkRotateConsecutiveLimit &&
                    server_count > 1) {
                    srv_idx = (srv_idx + 1) % server_count;
                    conn = CdnConnection{};
                    consecutive_slow_chunks = 0;
                }

                // Fetch + decode the chunk, retrying transient failures with
                // backoff and rotating CDN servers. Only after every attempt
                // is exhausted does the depot fail.
                CdnChunkResult   fetched;
                DepotChunkResult processed;
                std::string      last_err;
                bool             got = false;
                const auto       fetch_start = std::chrono::steady_clock::now();
                for (unsigned attempt = 0;
                     attempt < kMaxChunkAttempts; ++attempt) {
                    if (failed.load(std::memory_order_acquire) || cancelled()) {
                        break;
                    }
                    if (attempt > 0) {
                        std::this_thread::sleep_for(retry_backoff(attempt));
                        if (failed.load(std::memory_order_acquire) ||
                            cancelled()) {
                            break;
                        }
                        // Move to the next server; a keep-alive handle is
                        // bound to its host, so start a fresh connection.
                        if (server_count > 1) {
                            srv_idx = (srv_idx + 1) % server_count;
                        }
                        conn = CdnConnection{};
                    }
                    const auto& srv = servers[srv_idx];
                    fetched =
                        conn.valid()
                            ? cdn.fetch_chunk(conn, srv,
                                              manifest.metadata.depot_id,
                                              chunk.sha, cdn_auth_token)
                            : cdn.fetch_chunk(srv,
                                              manifest.metadata.depot_id,
                                              chunk.sha, cdn_auth_token);
                    if (!fetched.ok()) { last_err = fetched.error; continue; }
                    processed = process_depot_chunk(
                        fetched.data, depot_key, chunk.crc, chunk.cb_original);
                    if (!processed.ok()) {
                        last_err = "decode: " + processed.error;
                        continue;
                    }
                    got = true;
                    break;
                }
                if (!got) {
                    if (failed.load(std::memory_order_acquire) || cancelled()) {
                        break;
                    }
                    record_error("write_depot: chunk for '" + f.filename
                                 + "' failed after "
                                 + std::to_string(kMaxChunkAttempts)
                                 + " attempts: " + last_err);
                    break;
                }
                if (server_count > 1 &&
                    std::chrono::steady_clock::now() - fetch_start
                        > kSlowChunkRotateThreshold) {
                    ++consecutive_slow_chunks;
                } else {
                    consecutive_slow_chunks = 0;
                }
                int fd = ::open(path.c_str(), O_WRONLY);
                if (fd < 0) {
                    record_error("write_depot: open '" + f.filename + "': "
                                 + std::strerror(errno));
                    break;
                }
                ssize_t w = ::pwrite(fd, processed.data.data(),
                                     processed.data.size(),
                                     static_cast<off_t>(chunk.offset));
                ::close(fd);
                if (w < 0 ||
                    static_cast<size_t>(w) != processed.data.size()) {
                    record_error("write_depot: pwrite '" + f.filename + "': "
                                 + std::strerror(errno));
                    break;
                }
                bytes_done.fetch_add(processed.data.size(),
                                     std::memory_order_relaxed);
                // Last outstanding chunk of this file just landed — bring the
                // file to its exact manifest size now (the depot-end ftruncate
                // loop only runs on a fully-successful depot, so a pause/kill
                // before that point would leave this file at max-chunk-offset
                // size and the resume fast-path's stat guard would reject it),
                // flush it to stable storage, then record it done so a kill
                // or a pause/resume can't lose it or force a re-verify.
                if (file_remaining[job.file_idx].fetch_sub(
                        1, std::memory_order_acq_rel) == 1) {
                    int sfd = ::open(path.c_str(), O_WRONLY);
                    if (sfd >= 0) {
                        ::ftruncate(sfd, static_cast<off_t>(f.size));
                        ::fdatasync(sfd);
                        ::close(sfd);
                    }
                    if (progress_store) {
                        progress_store->mark_file_done(job.file_idx);
                    }
                }
            }
            active.fetch_sub(1, std::memory_order_acq_rel);
        };

        std::vector<std::thread> pool;
        pool.reserve(n);
        for (unsigned w = 0; w < n; ++w) pool.emplace_back(worker, w);

        // The calling thread owns progress reporting: write_depot's progress
        // callback ends up calling into the JVM, which is only legal on the
        // thread that owns its JNIEnv (this one). Workers never touch it.
        unsigned btick = 0;
        while (active.load(std::memory_order_acquire) > 0) {
            if (progress) {
                progress(bytes_done.load(std::memory_order_relaxed),
                         total_bytes, /*verifying=*/false);
            }
            // Persist completed-file records every ~3s so an interruption
            // loses at most a few seconds of "file done" markers (those
            // files are merely re-verified — never re-downloaded — on resume).
            if (progress_store && ++btick % 20 == 0) progress_store->flush();
            std::this_thread::sleep_for(std::chrono::milliseconds(150));
        }
        for (auto& t : pool) t.join();
        if (progress_store) progress_store->flush();

        if (failed.load(std::memory_order_acquire)) {
            return fail(err.empty() ? "write_depot: download failed" : err);
        }
        if (cancelled()) {
            // fdatasync partial files (file_remaining > 0) so the next
            // resume's Phase A2 SEEK_DATA fast-path sees the chunks; without
            // this, those bytes sit only in the page cache and resume falls
            // back to Adler-hashing every chunk — slow on big pauses.
            // Files with file_remaining == 0 were already synced at
            // mark_file_done. Parallelized; per-file fds are independent.
            std::vector<uint32_t> to_sync;
            to_sync.reserve(manifest.files.size());
            for (uint32_t fi = 0; fi < manifest.files.size(); ++fi) {
                const auto& f = manifest.files[fi];
                if (!f.linktarget.empty() || (f.flags & kFlagDirectory)) continue;
                if (f.chunks.empty()) continue;
                if (file_remaining[fi].load(std::memory_order_acquire) == 0) continue;
                to_sync.push_back(fi);
            }
            if (!to_sync.empty()) {
                unsigned sn = max_workers == 0 ? 1u : max_workers;
                sn = std::min<unsigned>(sn, 64u);
                sn = std::min<unsigned>(sn, static_cast<unsigned>(to_sync.size()));
                std::atomic<size_t> next_sync{0};
                auto syncer = [&]() {
                    while (true) {
                        const size_t si =
                            next_sync.fetch_add(1, std::memory_order_relaxed);
                        if (si >= to_sync.size()) break;
                        const uint32_t fi = to_sync[si];
                        int sfd = ::open(file_paths[fi].c_str(), O_WRONLY);
                        if (sfd >= 0) {
                            ::fdatasync(sfd);
                            ::close(sfd);
                        }
                    }
                };
                std::vector<std::thread> spool;
                spool.reserve(sn);
                for (unsigned w = 0; w < sn; ++w) spool.emplace_back(syncer);
                for (auto& t : spool) t.join();
                WN_LOGI("cancel: fdatasync'd %zu partial file(s) so resume "
                        "can fast-skip already-written chunks",
                        to_sync.size());
            }
            return fail("write_depot: cancelled", result.resume_trust_safe);
        }
    }

    for (uint32_t fi = 0; fi < manifest.files.size(); ++fi) {
        const auto& f = manifest.files[fi];
        if (!f.linktarget.empty() || (f.flags & kFlagDirectory)) continue;
        int fd = ::open(file_paths[fi].c_str(), O_WRONLY);
        if (fd < 0) {
            return fail("write_depot: final open '" + f.filename + "': "
                        + std::strerror(errno));
        }
        if (::ftruncate(fd, static_cast<off_t>(f.size)) != 0) {
            ::close(fd);
            return fail("write_depot: final ftruncate '" + f.filename + "': "
                        + std::strerror(errno));
        }
        ::close(fd);
    }

    if (progress_store) progress_store->flush();

    result.bytes_written = bytes_done.load(std::memory_order_relaxed);
    if (progress) progress(result.bytes_written, total_bytes, !any_download);

    WN_LOGI("write_depot: depot %u — %llu files, %llu bytes "
            "(%u dl workers, %zu validated files)",
            manifest.metadata.depot_id,
            static_cast<unsigned long long>(result.files_written),
            static_cast<unsigned long long>(result.bytes_written),
            workers_used, validate_files.size());
    return result;
}

}  // namespace wn_steam
