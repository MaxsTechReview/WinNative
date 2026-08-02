#include <jni.h>
#include <stdatomic.h>

// Store-store barrier (dmb ish on arm64) so ring event/snapshot payload stores
// are visible to the guest-side reader before the sequence word that publishes
// them. Pairs with the __ATOMIC_ACQUIRE loads in fakeinput.cpp.
JNIEXPORT void JNICALL
Java_com_winlator_cmod_runtime_input_controls_FakeInputWriter_nativeStoreFence(
    JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  atomic_thread_fence(memory_order_release);
}
