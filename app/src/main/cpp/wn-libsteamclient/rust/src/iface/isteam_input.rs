//! ISteamInput — 48 slots (isteam_stubs.cpp:3451-3505).

#![allow(non_snake_case)]

use crate::vtable::{noop_p, noop_v, LazyInstance};
use core::ffi::c_void;

const N: usize = 48;

pub fn instance() -> *mut c_void {
    static INSTANCE: LazyInstance = LazyInstance::new();
    INSTANCE.instance(|| {
        let mut s = vec![noop_p as usize; N];
        s[3]  = noop_v as usize; // <slot 3>  RunFrame
        s[7]  = noop_v as usize; // <slot 7>  EnableDeviceCallbacks
        s[8]  = noop_v as usize; // <slot 8>  EnableActionEventCallbacks
        s[10] = noop_v as usize; // <slot 10> ActivateActionSet
        s[12] = noop_v as usize; // <slot 12> ActivateActionSetLayer
        s[13] = noop_v as usize; // <slot 13> DeactivateActionSetLayer
        s[14] = noop_v as usize; // <slot 14> DeactivateAllActionSetLayers
        s[17] = noop_v as usize; // <slot 17> GetDigitalActionData
        s[21] = noop_v as usize; // <slot 21> GetAnalogActionData
        s[28] = noop_v as usize; // <slot 28> StopAnalogActionMomentum
        s[29] = noop_v as usize; // <slot 29> GetMotionData
        s[30] = noop_v as usize; // <slot 30> TriggerVibration
        s[31] = noop_v as usize; // <slot 31> TriggerVibrationExtended
        s[32] = noop_v as usize; // <slot 32> TriggerSimpleHapticEvent
        s[33] = noop_v as usize; // <slot 33> SetLEDColor
        s[34] = noop_v as usize; // <slot 34> Legacy_TriggerHapticPulse
        s[35] = noop_v as usize; // <slot 35> Legacy_TriggerRepeatedHapticPulse
        s[47] = noop_v as usize; // <slot 47> SetDualSenseTriggerEffect
        assert_eq!(s.len(), N);
        s
    })
}
