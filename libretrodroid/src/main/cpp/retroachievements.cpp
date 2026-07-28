#include <jni.h>
#include <string>
#include <vector>
#include <deque>
#include <map>
#include <mutex>
#include <cstring>
#include <cstdio>
#include <cstring>
#include <strings.h>

#include "rc_client.h"
#include "rc_libretro.h"
#include "rc_hash.h"
#include <formats/cdfs.h>

// Custom rc_hash CD reader. For .chd images we drive libretro's cdfs layer
// (chd_stream + libchdr), which cooks 2048-byte user sectors and handles the
// pregap/sector-size detection the default reader cannot do on a raw chd. Every
// other format (.iso/.cue/.bin/.gdi) is delegated to rcheevos' default reader.
namespace {
rc_hash_cdreader_t g_defaultCdReader;

bool ra_is_chd(const char *path) {
    if (!path) return false;
    const size_t n = std::strlen(path);
    return n >= 4 && strcasecmp(path + n - 4, ".chd") == 0;
}

struct RaTrack {
    bool isChd;
    cdfs_track_t *track;  // chd only
    cdfs_file_t file;     // chd only
    void *inner;          // default-reader handle otherwise
};

void *ra_cd_open_track(const char *path, uint32_t track) {
    auto *h = new RaTrack{};
    if (ra_is_chd(path)) {
        h->isChd = true;
        if (track == RC_HASH_CDTRACK_FIRST_DATA || track == RC_HASH_CDTRACK_LARGEST) {
            h->track = cdfs_open_data_track(path);
        } else {
            h->track = cdfs_open_track(path, track);
        }
        if (!h->track || !cdfs_open_file(&h->file, h->track, nullptr)) {
            if (h->track) cdfs_close_track(h->track);
            delete h;
            return nullptr;
        }
        return h;
    }
    h->isChd = false;
    h->inner = g_defaultCdReader.open_track ? g_defaultCdReader.open_track(path, track) : nullptr;
    if (!h->inner) { delete h; return nullptr; }
    return h;
}

size_t ra_cd_read_sector(void *handle, uint32_t sector, void *buffer, size_t bytes) {
    auto *h = static_cast<RaTrack *>(handle);
    if (h->isChd) {
        cdfs_seek_sector(&h->file, sector);
        int64_t r = cdfs_read_file(&h->file, buffer, bytes);
        return r < 0 ? 0 : static_cast<size_t>(r);
    }
    return g_defaultCdReader.read_sector(h->inner, sector, buffer, bytes);
}

uint32_t ra_cd_first_track_sector(void *handle) {
    auto *h = static_cast<RaTrack *>(handle);
    if (h->isChd) return cdfs_get_first_sector(&h->file);
    return g_defaultCdReader.first_track_sector(h->inner);
}

void ra_cd_close_track(void *handle) {
    auto *h = static_cast<RaTrack *>(handle);
    if (!h) return;
    if (h->isChd) {
        cdfs_close_file(&h->file);
        if (h->track) cdfs_close_track(h->track);
    } else if (h->inner) {
        g_defaultCdReader.close_track(h->inner);
    }
    delete h;
}
}
#include "rc_hash.h"
#include "libretrodroid.h"
#include "environment.h"
#include "log.h"

using namespace libretrodroid;

namespace {

struct HttpRequest {
    int id;
    std::string url;
    std::string postData;
    std::string contentType;
};

std::recursive_mutex g_clientMutex;
std::mutex g_httpMutex;
std::mutex g_eventMutex;

rc_client_t *g_client = nullptr;
rc_libretro_memory_regions_t g_regions {};
bool g_memoryInitialized = false;
bool g_gameLoaded = false;

std::deque<HttpRequest> g_httpQueue;
std::map<int, std::pair<rc_client_server_callback_t, void *>> g_pending;
int g_requestCounter = 0;

std::deque<std::string> g_eventQueue;

void pushEvent(const std::string &event) {
    std::lock_guard<std::mutex> lock(g_eventMutex);
    g_eventQueue.push_back(event);
}

std::string sanitize(const char *value) {
    if (value == nullptr) return std::string();
    std::string result(value);
    for (char &c : result) {
        if (c == '\t' || c == '\n' || c == '\r') c = ' ';
    }
    return result;
}

uint32_t read_memory_callback(uint32_t address, uint8_t *buffer, uint32_t num_bytes, rc_client_t *client) {
    if (!g_memoryInitialized) return 0;
    return rc_libretro_memory_read(&g_regions, address, buffer, num_bytes);
}

void get_core_memory_info(uint32_t id, rc_libretro_core_memory_info_t *info) {
    info->data = static_cast<uint8_t *>(LibretroDroid::getInstance().coreMemoryData(id));
    info->size = LibretroDroid::getInstance().coreMemorySize(id);
}

void server_call_callback(const rc_api_request_t *request, rc_client_server_callback_t callback, void *callback_data, rc_client_t *client) {
    int id;
    {
        std::lock_guard<std::mutex> lock(g_httpMutex);
        id = ++g_requestCounter;
        g_pending[id] = std::make_pair(callback, callback_data);
        HttpRequest req;
        req.id = id;
        req.url = request->url ? request->url : "";
        req.postData = request->post_data ? request->post_data : "";
        req.contentType = request->content_type ? request->content_type : "";
        g_httpQueue.push_back(req);
    }
}

void event_handler(const rc_client_event_t *event, rc_client_t *client) {
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            const rc_client_achievement_t *ach = event->achievement;
            char url[256];
            url[0] = '\0';
            if (ach->badge_url && ach->badge_url[0]) {
                snprintf(url, sizeof(url), "%s", ach->badge_url);
            } else {
                rc_client_achievement_get_image_url(ach, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED, url, sizeof(url));
            }
            pushEvent("unlock\t" + std::to_string(ach->id) + "\t" + sanitize(ach->title) + "\t" +
                      sanitize(ach->description) + "\t" + std::to_string(ach->points) + "\t" + sanitize(url));
            break;
        }
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
            pushEvent("challenge_show\t" + std::to_string(event->achievement->id));
            break;
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_HIDE:
            pushEvent("challenge_hide\t" + std::to_string(event->achievement->id));
            break;
        case RC_CLIENT_EVENT_GAME_COMPLETED: {
            rc_client_user_game_summary_t summary;
            rc_client_get_user_game_summary(client, &summary);
            pushEvent("game_completed\t" + std::to_string(summary.points_unlocked));
            break;
        }
        case RC_CLIENT_EVENT_RESET:
            pushEvent("reset\t");
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR:
            pushEvent("server_error\t" + sanitize(event->server_error ? event->server_error->error_message : ""));
            break;
        case RC_CLIENT_EVENT_DISCONNECTED:
            pushEvent("disconnected\t");
            break;
        case RC_CLIENT_EVENT_RECONNECTED:
            pushEvent("reconnected\t");
            break;
        default:
            break;
    }
}

void login_callback(int result, const char *error_message, rc_client_t *client, void *userdata) {
    if (result == RC_OK) {
        const rc_client_user_t *user = rc_client_get_user_info(client);
        std::string token = user && user->token ? user->token : "";
        std::string display = user && user->display_name ? user->display_name : "";
        pushEvent("login_ok\t" + sanitize(display.c_str()) + "\t" + sanitize(token.c_str()));
    } else {
        pushEvent("login_failed\t" + std::to_string(result) + "\t" + sanitize(error_message));
    }
}

void load_game_callback(int result, const char *error_message, rc_client_t *client, void *userdata) {
    if (result == RC_OK) {
        const rc_client_game_t *game = rc_client_get_game_info(client);
        if (game != nullptr && game->id != 0) {
            g_gameLoaded = true;
            pushEvent("game_loaded\t" + std::to_string(game->id) + "\t" + sanitize(game->title));
        } else {
            pushEvent("game_unidentified\t");
        }
    } else if (result == RC_NO_GAME_LOADED) {
        pushEvent("game_unidentified\t");
    } else {
        pushEvent("game_load_failed\t" + std::to_string(result) + "\t" + sanitize(error_message));
    }
}

jstring toJString(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeInit(JNIEnv *env, jobject, jboolean hardcore) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr) {
        rc_client_destroy(g_client);
        g_client = nullptr;
    }
    g_client = rc_client_create(read_memory_callback, server_call_callback);
    rc_client_set_event_handler(g_client, event_handler);
    rc_client_set_hardcore_enabled(g_client, hardcore ? 1 : 0);
    // Register the default CD reader so pre-launch identify/hash can read disc
    // images (PS2/PSX/GC .iso/.cue/.bin/.gdi) and resolve the same game the
    // running core does — without it, disc-based systems fail to hash by path.
    rc_hash_init_default_cdreader();
    rc_hash_get_default_cdreader(&g_defaultCdReader);
    static rc_hash_cdreader_t ra_cdreader = {
        ra_cd_open_track, ra_cd_read_sector, ra_cd_close_track, ra_cd_first_track_sector, nullptr,
    };
    rc_hash_init_custom_cdreader(&ra_cdreader);
    g_gameLoaded = false;
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeLoginPassword(JNIEnv *env, jobject, jstring username, jstring password) {
    const char *u = env->GetStringUTFChars(username, nullptr);
    const char *p = env->GetStringUTFChars(password, nullptr);
    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        if (g_client != nullptr) {
            rc_client_begin_login_with_password(g_client, u, p, login_callback, nullptr);
        }
    }
    env->ReleaseStringUTFChars(username, u);
    env->ReleaseStringUTFChars(password, p);
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeLoginToken(JNIEnv *env, jobject, jstring username, jstring token) {
    const char *u = env->GetStringUTFChars(username, nullptr);
    const char *t = env->GetStringUTFChars(token, nullptr);
    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        if (g_client != nullptr) {
            rc_client_begin_login_with_token(g_client, u, t, login_callback, nullptr);
        }
    }
    env->ReleaseStringUTFChars(username, u);
    env->ReleaseStringUTFChars(token, t);
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeLoadGame(JNIEnv *env, jobject, jint consoleId, jstring filePath) {
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        if (g_client != nullptr) {
            g_gameLoaded = false;
            if (g_memoryInitialized) {
                rc_libretro_memory_destroy(&g_regions);
                g_memoryInitialized = false;
            }
            const struct retro_memory_map *mmap = Environment::getInstance().getMemoryMap();
            if (rc_libretro_memory_init(&g_regions, mmap, get_core_memory_info, static_cast<uint32_t>(consoleId))) {
                g_memoryInitialized = true;
            }
            rc_client_begin_identify_and_load_game(g_client, static_cast<uint32_t>(consoleId), path, nullptr, 0, load_game_callback, nullptr);
        }
    }
    env->ReleaseStringUTFChars(filePath, path);
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeUnloadGame(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr) {
        rc_client_unload_game(g_client);
    }
    if (g_memoryInitialized) {
        rc_libretro_memory_destroy(&g_regions);
        g_memoryInitialized = false;
    }
    g_gameLoaded = false;
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeDoFrame(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr && g_gameLoaded) {
        rc_client_do_frame(g_client);
    }
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeIdle(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr) {
        rc_client_idle(g_client);
    }
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeReset(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr) {
        rc_client_reset(g_client);
    }
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeSetHardcore(JNIEnv *env, jobject, jboolean enabled) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client != nullptr) {
        rc_client_set_hardcore_enabled(g_client, enabled ? 1 : 0);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeGetHardcore(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_client == nullptr) return JNI_FALSE;
    return rc_client_get_hardcore_enabled(g_client) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeHardcoreCompatible(JNIEnv *env, jobject) {
    std::string libraryName = LibretroDroid::getInstance().coreLibraryName();
    if (libraryName.empty()) return JNI_TRUE;
    const rc_disallowed_setting_t *disallowed = rc_libretro_get_disallowed_settings(libraryName.c_str());
    if (disallowed == nullptr) return JNI_TRUE;
    for (const auto &variable : Environment::getInstance().getVariables()) {
        if (!rc_libretro_is_setting_allowed(disallowed, variable.key.c_str(), variable.value.c_str())) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativePollHttpRequest(JNIEnv *env, jobject) {
    HttpRequest req;
    bool has = false;
    {
        std::lock_guard<std::mutex> lock(g_httpMutex);
        if (!g_httpQueue.empty()) {
            req = g_httpQueue.front();
            g_httpQueue.pop_front();
            has = true;
        }
    }
    if (!has) return nullptr;
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(4, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, toJString(env, std::to_string(req.id)));
    env->SetObjectArrayElement(result, 1, toJString(env, req.url));
    env->SetObjectArrayElement(result, 2, toJString(env, req.postData));
    env->SetObjectArrayElement(result, 3, toJString(env, req.contentType));
    return result;
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeProvideResponse(JNIEnv *env, jobject, jint requestId, jint httpStatus, jbyteArray body) {
    rc_client_server_callback_t callback = nullptr;
    void *callback_data = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_httpMutex);
        auto it = g_pending.find(requestId);
        if (it != g_pending.end()) {
            callback = it->second.first;
            callback_data = it->second.second;
            g_pending.erase(it);
        }
    }
    if (callback == nullptr) return;

    std::string bodyData;
    if (body != nullptr) {
        jsize len = env->GetArrayLength(body);
        bodyData.resize(len);
        env->GetByteArrayRegion(body, 0, len, reinterpret_cast<jbyte *>(&bodyData[0]));
    }

    rc_api_server_response_t response;
    memset(&response, 0, sizeof(response));
    response.body = bodyData.c_str();
    response.body_length = bodyData.size();
    response.http_status_code = httpStatus;

    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        callback(&response, callback_data);
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativePollEvents(JNIEnv *env, jobject) {
    std::vector<std::string> events;
    {
        std::lock_guard<std::mutex> lock(g_eventMutex);
        while (!g_eventQueue.empty()) {
            events.push_back(g_eventQueue.front());
            g_eventQueue.pop_front();
        }
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(events.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(events.size()); i++) {
        env->SetObjectArrayElement(result, i, toJString(env, events[i]));
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeGetAchievements(JNIEnv *env, jobject) {
    std::vector<std::string> rows;
    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        if (g_client != nullptr) {
            rc_client_achievement_list_t *list = rc_client_create_achievement_list(
                g_client,
                RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
                RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
            if (list != nullptr) {
                for (uint32_t b = 0; b < list->num_buckets; b++) {
                    const rc_client_achievement_bucket_t *bucket = &list->buckets[b];
                    for (uint32_t a = 0; a < bucket->num_achievements; a++) {
                        const rc_client_achievement_t *ach = bucket->achievements[a];
                        char lockedUrl[256];
                        lockedUrl[0] = '\0';
                        if (ach->badge_locked_url && ach->badge_locked_url[0]) {
                            snprintf(lockedUrl, sizeof(lockedUrl), "%s", ach->badge_locked_url);
                        } else {
                            rc_client_achievement_get_image_url(ach, RC_CLIENT_ACHIEVEMENT_STATE_ACTIVE, lockedUrl, sizeof(lockedUrl));
                        }
                        char unlockedUrl[256];
                        unlockedUrl[0] = '\0';
                        if (ach->badge_url && ach->badge_url[0]) {
                            snprintf(unlockedUrl, sizeof(unlockedUrl), "%s", ach->badge_url);
                        } else {
                            rc_client_achievement_get_image_url(ach, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED, unlockedUrl, sizeof(unlockedUrl));
                        }
                        std::string row = std::to_string(ach->id) + "\t" + sanitize(ach->title) + "\t" +
                                          sanitize(ach->description) + "\t" + (ach->unlocked ? "1" : "0") + "\t" +
                                          std::to_string(ach->points) + "\t" + sanitize(ach->measured_progress) + "\t" +
                                          sanitize(unlockedUrl) + "\t" + sanitize(lockedUrl) + "\t" +
                                          std::to_string(ach->bucket);
                        rows.push_back(row);
                    }
                }
                rc_client_destroy_achievement_list(list);
            }
        }
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(rows.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(rows.size()); i++) {
        env->SetObjectArrayElement(result, i, toJString(env, rows[i]));
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeGetGameSummary(JNIEnv *env, jobject) {
    std::string summaryText;
    {
        std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
        if (g_client != nullptr && g_gameLoaded) {
            const rc_client_game_t *game = rc_client_get_game_info(g_client);
            rc_client_user_game_summary_t summary;
            rc_client_get_user_game_summary(g_client, &summary);
            char badge[256];
            badge[0] = '\0';
            if (game != nullptr) {
                if (game->badge_url && game->badge_url[0]) {
                    snprintf(badge, sizeof(badge), "%s", game->badge_url);
                } else {
                    rc_client_game_get_image_url(game, badge, sizeof(badge));
                }
                summaryText = std::to_string(game->id) + "\t" + sanitize(game->title) + "\t" + sanitize(badge) + "\t" +
                              std::to_string(summary.num_core_achievements) + "\t" +
                              std::to_string(summary.num_unlocked_achievements) + "\t" +
                              std::to_string(summary.points_core) + "\t" +
                              std::to_string(summary.points_unlocked);
            }
        }
    }
    return toJString(env, summaryText);
}

JNIEXPORT void JNICALL
Java_com_swordfish_libretrodroid_RetroAchievements_nativeDestroy(JNIEnv *env, jobject) {
    std::lock_guard<std::recursive_mutex> lock(g_clientMutex);
    if (g_memoryInitialized) {
        rc_libretro_memory_destroy(&g_regions);
        g_memoryInitialized = false;
    }
    if (g_client != nullptr) {
        rc_client_destroy(g_client);
        g_client = nullptr;
    }
    g_gameLoaded = false;
    {
        std::lock_guard<std::mutex> hlock(g_httpMutex);
        g_httpQueue.clear();
        g_pending.clear();
    }
    {
        std::lock_guard<std::mutex> elock(g_eventMutex);
        g_eventQueue.clear();
    }
}

}
