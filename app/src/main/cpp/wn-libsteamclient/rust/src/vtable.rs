//! Shared no-op vtable slot helpers for `_slotNN` placeholders.
//!
//! Each Steam SDK interface has a fixed number of vtable slots in a specific
//! order. Many slots are no-ops in our implementation; rather than declaring
//! a unique function per no-op slot, we share a small set of return-shape
//! helpers (`noop_v`, `noop_b`, `noop_p`, `noop_i`, `noop_u32`, `noop_u64`).
//!
//! All helpers ignore `_this` because our produced objects are singletons —
//! they are one machine word holding `&VTBL as usize`.

use core::ffi::c_void;
use std::sync::OnceLock;

pub type This = c_void;

pub unsafe extern "C" fn noop_v(_this: *mut This) {}

pub unsafe extern "C" fn noop_b(_this: *mut This) -> bool {
    false
}

pub unsafe extern "C" fn noop_p(_this: *mut This) -> *mut c_void {
    core::ptr::null_mut()
}

pub unsafe extern "C" fn noop_i(_this: *mut This) -> i32 {
    0
}

pub unsafe extern "C" fn noop_u32(_this: *mut This) -> u32 {
    0
}

pub unsafe extern "C" fn noop_u64(_this: *mut This) -> u64 {
    0
}

pub unsafe extern "C" fn noop_f(_this: *mut This) -> f32 {
    0.0
}

/// `#[repr(transparent)]` wrapper for the produced object — a single
/// machine word holding `&VTBL as *const _ as usize`. Wine consumers
/// read `*(void***)obj` to fetch the vtable pointer; with this layout
/// that read returns `&VTBL`.
#[repr(transparent)]
pub struct InstanceWord(pub usize);

unsafe impl Sync for InstanceWord {}
unsafe impl Send for InstanceWord {}

/// One-shot holder for a built-once vtable + its "object word".
///
/// Casting a function pointer to `usize` is not permitted in Rust
/// const-eval (E0080). So we build each vtable's slot array at runtime
/// on the first `instance()` call, leak it to obtain a stable `'static`
/// address, then memoise that address in a `OnceLock<usize>`.
///
/// The address handed back to consumers is `&memoised_usize as *mut c_void`
/// — one machine word whose dereference yields the vtable pointer, which
/// matches the C++ object layout that Wine's `lsteamclient.dll` expects.
pub struct LazyInstance {
    cell: OnceLock<usize>,
}

impl LazyInstance {
    pub const fn new() -> Self {
        Self { cell: OnceLock::new() }
    }

    pub fn instance<F: FnOnce() -> Vec<usize>>(&'static self, build: F) -> *mut c_void {
        let word_ref: &'static usize = self.cell.get_or_init(|| {
            let slots = build();
            let leaked: &'static [usize] = Box::leak(slots.into_boxed_slice());
            leaked.as_ptr() as usize
        });
        word_ref as *const usize as *mut c_void
    }
}

unsafe impl Sync for LazyInstance {}
