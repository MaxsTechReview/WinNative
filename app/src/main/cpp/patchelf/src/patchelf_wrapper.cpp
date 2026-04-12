#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <fstream>
#include <set>
#include <map>
#include <unistd.h>
#include "patchelf.h"

// Explicitly define the template arguments for 32-bit and 64-bit ELF files
typedef ElfFile<Elf32_Ehdr, Elf32_Phdr, Elf32_Shdr, Elf32_Addr, Elf32_Off, Elf32_Dyn, Elf32_Sym, Elf32_Versym, Elf32_Verdef, Elf32_Verdaux, Elf32_Verneed, Elf32_Vernaux, Elf32_Rel, Elf32_Rela, 1> ElfFile32;
typedef ElfFile<Elf64_Ehdr, Elf64_Phdr, Elf64_Shdr, Elf64_Addr, Elf64_Off, Elf64_Dyn, Elf64_Sym, Elf64_Versym, Elf64_Verdef, Elf64_Verdaux, Elf64_Verneed, Elf64_Vernaux, Elf64_Rel, Elf64_Rela, 2> ElfFile64;

class ElfFileInterface {
public:
    virtual ~ElfFileInterface() = default;
    virtual bool isChanged() const = 0;
    virtual std::string getInterpreter() const = 0;
    virtual void setInterpreter(const std::string &newInterpreter) = 0;
    virtual void modifyRPath(bool add, const std::string &rpath) = 0;
    virtual void modifySoname(const std::string &newSoname) = 0;
    virtual void modifyOsAbi(const std::string &newOsAbi) = 0;
    virtual void addNeeded(const std::string &needed) = 0;
    virtual void removeNeeded(const std::string &needed) = 0;
    virtual void rewriteSections() = 0;
};

class ElfFileImpl32 : public ElfFileInterface {
    ElfFile32 elf;
public:
    ElfFileImpl32(FileContents contents) : elf(contents) {}
    bool isChanged() const override { return elf.isChanged(); }
    std::string getInterpreter() const override { return elf.getInterpreter(); }
    void setInterpreter(const std::string &newInterpreter) override { elf.setInterpreter(newInterpreter); }
    void modifyRPath(bool add, const std::string &rpath) override {
        elf.modifyRPath(add ? ElfFile32::rpAdd : ElfFile32::rpRemove, {}, rpath);
    }
    void modifySoname(const std::string &newSoname) override {
        elf.modifySoname(ElfFile32::replaceSoname, newSoname);
    }
    void modifyOsAbi(const std::string &newOsAbi) override {
        elf.modifyOsAbi(ElfFile32::replaceOsAbi, newOsAbi);
    }
    void addNeeded(const std::string &needed) override {
        std::set<std::string> libs;
        libs.insert(needed);
        elf.addNeeded(libs);
    }
    void removeNeeded(const std::string &needed) override {
        std::set<std::string> libs;
        libs.insert(needed);
        elf.removeNeeded(libs);
    }
    void rewriteSections() override { elf.rewriteSections(); }
};

class ElfFileImpl64 : public ElfFileInterface {
    ElfFile64 elf;
public:
    ElfFileImpl64(FileContents contents) : elf(contents) {}
    bool isChanged() const override { return elf.isChanged(); }
    std::string getInterpreter() const override { return elf.getInterpreter(); }
    void setInterpreter(const std::string &newInterpreter) override { elf.setInterpreter(newInterpreter); }
    void modifyRPath(bool add, const std::string &rpath) override {
        elf.modifyRPath(add ? ElfFile64::rpAdd : ElfFile64::rpRemove, {}, rpath);
    }
    void modifySoname(const std::string &newSoname) override {
        elf.modifySoname(ElfFile64::replaceSoname, newSoname);
    }
    void modifyOsAbi(const std::string &newOsAbi) override {
        elf.modifyOsAbi(ElfFile64::replaceOsAbi, newOsAbi);
    }
    void addNeeded(const std::string &needed) override {
        std::set<std::string> libs;
        libs.insert(needed);
        elf.addNeeded(libs);
    }
    void removeNeeded(const std::string &needed) override {
        std::set<std::string> libs;
        libs.insert(needed);
        elf.removeNeeded(libs);
    }
    void rewriteSections() override { elf.rewriteSections(); }
};

struct ElfObject {
    std::string path;
    FileContents fileContents;
    std::unique_ptr<ElfFileInterface> elfFile;
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_winlator_cmod_core_PatchElf_createElfObject(JNIEnv *env, jobject thiz, jstring path) {
    const char *nativePath = env->GetStringUTFChars(path, nullptr);
    std::string sPath(nativePath);
    env->ReleaseStringUTFChars(path, nativePath);

    std::ifstream strm(sPath, std::ios::binary | std::ios::ate);
    if (!strm) return 0;

    std::streamsize size = strm.tellg();
    strm.seekg(0, std::ios::beg);

    auto contents = std::make_shared<std::vector<unsigned char>>(size);
    if (!strm.read((char*)contents->data(), size)) return 0;

    auto obj = new ElfObject();
    obj->path = sPath;
    obj->fileContents = contents;

    try {
        unsigned char *data = contents->data();
        if (data[0] != 0x7f || data[1] != 'E' || data[2] != 'L' || data[3] != 'F') {
            delete obj;
            return 0;
        }

        if (data[4] == 1) { // ELFCLASS32
            obj->elfFile = std::make_unique<ElfFileImpl32>(contents);
        } else if (data[4] == 2) { // ELFCLASS64
            obj->elfFile = std::make_unique<ElfFileImpl64>(contents);
        } else {
            delete obj;
            return 0;
        }
    } catch (...) {
        delete obj;
        return 0;
    }

    return reinterpret_cast<jlong>(obj);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_winlator_cmod_core_PatchElf_destroyElfObject(JNIEnv *env, jobject thiz, jlong object_ptr) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    delete obj;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_isChanged(JNIEnv *env, jobject thiz, jlong object_ptr) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    return obj && obj->elfFile ? obj->elfFile->isChanged() : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getInterpreter(JNIEnv *env, jobject thiz, jlong object_ptr) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return nullptr;
    try {
        std::string interpreter = obj->elfFile->getInterpreter();
        return env->NewStringUTF(interpreter.c_str());
    } catch (...) {
        return nullptr;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_setInterpreter(JNIEnv *env, jobject thiz, jlong object_ptr, jstring interpreter) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeInterpreter = env->GetStringUTFChars(interpreter, nullptr);
    try {
        obj->elfFile->setInterpreter(nativeInterpreter);
        env->ReleaseStringUTFChars(interpreter, nativeInterpreter);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(interpreter, nativeInterpreter);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_addNeeded(JNIEnv *env, jobject thiz, jlong object_ptr, jstring needed) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeNeeded = env->GetStringUTFChars(needed, nullptr);
    try {
        obj->elfFile->addNeeded(nativeNeeded);
        env->ReleaseStringUTFChars(needed, nativeNeeded);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(needed, nativeNeeded);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_removeNeeded(JNIEnv *env, jobject thiz, jlong object_ptr, jstring needed) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeNeeded = env->GetStringUTFChars(needed, nullptr);
    try {
        obj->elfFile->removeNeeded(nativeNeeded);
        env->ReleaseStringUTFChars(needed, nativeNeeded);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(needed, nativeNeeded);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_addRPath(JNIEnv *env, jobject thiz, jlong object_ptr, jstring rpath) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeRPath = env->GetStringUTFChars(rpath, nullptr);
    try {
        obj->elfFile->modifyRPath(true, nativeRPath);
        env->ReleaseStringUTFChars(rpath, nativeRPath);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(rpath, nativeRPath);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_removeRPath(JNIEnv *env, jobject thiz, jlong object_ptr, jstring rpath) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeRPath = env->GetStringUTFChars(rpath, nullptr);
    try {
        obj->elfFile->modifyRPath(false, nativeRPath);
        env->ReleaseStringUTFChars(rpath, nativeRPath);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(rpath, nativeRPath);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getSoName(JNIEnv *env, jobject thiz, jlong object_ptr) {
    return nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_replaceSoName(JNIEnv *env, jobject thiz, jlong object_ptr, jstring soName) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeSoName = env->GetStringUTFChars(soName, nullptr);
    try {
        obj->elfFile->modifySoname(nativeSoName);
        env->ReleaseStringUTFChars(soName, nativeSoName);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(soName, nativeSoName);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_PatchElf_getOsAbi(JNIEnv *env, jobject thiz, jlong object_ptr) {
    return nullptr;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_core_PatchElf_replaceOsAbi(JNIEnv *env, jobject thiz, jlong object_ptr, jstring osAbi) {
    auto obj = reinterpret_cast<ElfObject*>(object_ptr);
    if (!obj || !obj->elfFile) return JNI_FALSE;
    const char *nativeOsAbi = env->GetStringUTFChars(osAbi, nullptr);
    try {
        obj->elfFile->modifyOsAbi(nativeOsAbi);
        env->ReleaseStringUTFChars(osAbi, nativeOsAbi);
        return JNI_TRUE;
    } catch (...) {
        env->ReleaseStringUTFChars(osAbi, nativeOsAbi);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_PatchElf_getNeeded(JNIEnv *env, jobject thiz, jlong object_ptr) {
    return nullptr;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_PatchElf_getRPath(JNIEnv *env, jobject thiz, jlong object_ptr) {
    return nullptr;
}
