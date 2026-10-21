// Authored JNI binding. AppForge never injects Java/Rust bridges into user sources.
#[no_mangle]
pub extern "system" fn Java_com_example_rustjni_MainActivity_rustAnswer(
    _env: *mut core::ffi::c_void, _class: *mut core::ffi::c_void,
) -> i32 { 42 }
