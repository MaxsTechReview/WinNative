#![allow(non_snake_case)]

#[cfg(unix)]
mod unix_exports {
    #[no_mangle]
    pub extern "C" fn setgid(_gid: libc::gid_t) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setuid(_uid: libc::uid_t) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setregid(_rgid: libc::gid_t, _egid: libc::gid_t) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setreuid(_ruid: libc::uid_t, _euid: libc::uid_t) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setresgid(
        _rgid: libc::gid_t,
        _egid: libc::gid_t,
        _sgid: libc::gid_t,
    ) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setresuid(
        _ruid: libc::uid_t,
        _euid: libc::uid_t,
        _suid: libc::uid_t,
    ) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setfsgid(_fsgid: libc::gid_t) -> libc::c_int {
        0
    }

    #[no_mangle]
    pub extern "C" fn setfsuid(_fsuid: libc::uid_t) -> libc::c_int {
        0
    }
}
