//! JNI wrapper around `pocketdeck_remote::Client` for `RemoteTunnelNative` in the app.
//!
//! Errors are thrown as `java.io.IOException` whose message is `"<kind>\n<text>"`, where kind is
//! one of `config`, `bind`, `connect`, `hello`, `no-direct-path`, `datagrams`. The Kotlin side
//! splits it into a `TunnelException`.

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jint, jintArray, jlong, jstring};
use pocketdeck_remote::{Client, Config, Error, Link};

fn throw(env: &mut JNIEnv, kind: &str, message: &str) {
    let _ = env.throw_new("java/io/IOException", format!("{kind}\n{message}"));
}

fn kind(e: &Error) -> &'static str {
    match e {
        Error::Config(_) => "config",
        Error::Bind(_) => "bind",
        Error::Connect(_) => "connect",
        Error::Hello(_) => "hello",
        Error::NoDirectPath => "no-direct-path",
        Error::DatagramsTooSmall(_) => "datagrams",
    }
}

fn secret_from(env: &mut JNIEnv, bytes: &JByteArray) -> Option<[u8; 32]> {
    let v = env.convert_byte_array(bytes).ok()?;
    <[u8; 32]>::try_from(v.as_slice()).ok()
}

fn int_array(env: &mut JNIEnv, values: &[i32]) -> jintArray {
    match env.new_int_array(values.len() as i32) {
        Ok(arr) => {
            let _ = env.set_int_array_region(&arr, 0, values);
            arr.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// # Safety
/// `handle` must come from `start` and not have been passed to `stop` yet.
unsafe fn client<'a>(handle: jlong) -> &'a Client {
    unsafe { &*(handle as *const Client) }
}

/// Hands the JVM and the application context to `ndk-context`, which iroh's DNS resolver and
/// network monitor need on Android (without it they abort). Call once, before anything else.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_init<'a>(
    env: JNIEnv<'a>,
    _class: JClass<'a>,
    context: JObject<'a>,
) {
    static INIT: std::sync::Once = std::sync::Once::new();
    INIT.call_once(|| {
        let (Ok(vm), Ok(global)) = (env.get_java_vm(), env.new_global_ref(context)) else { return };
        let ctx = global.as_obj().as_raw();
        // The context must live for the rest of the process.
        std::mem::forget(global);
        // SAFETY: a valid JavaVM pointer and a global reference that is never released.
        unsafe { ndk_context::initialize_android_context(vm.get_java_vm_pointer().cast(), ctx.cast()) };
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_generateSecret<'a>(
    env: JNIEnv<'a>,
    _class: JClass<'a>,
) -> JByteArray<'a> {
    let key = pocketdeck_remote::generate_secret();
    env.byte_array_from_slice(&key).unwrap_or_default()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_endpointId<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    secret: JByteArray<'a>,
) -> jstring {
    let Some(key) = secret_from(&mut env, &secret) else {
        throw(&mut env, "config", "the tunnel key must be 32 bytes");
        return std::ptr::null_mut();
    };
    let id = pocketdeck_remote::endpoint_id(&key).to_string();
    env.new_string(id).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

/// Dials the PC and opens the local ports. Blocks for up to ~20 s. Returns a handle for the
/// other calls, or throws.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_start<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    secret: JByteArray<'a>,
    relay: JString<'a>,
    pc_id: JString<'a>,
) -> jlong {
    let Some(key) = secret_from(&mut env, &secret) else {
        throw(&mut env, "config", "the tunnel key must be 32 bytes");
        return 0;
    };
    let relay: Option<String> =
        if relay.is_null() { None } else { env.get_string(&relay).ok().map(Into::into).filter(|s: &String| !s.is_empty()) };
    let pc: String = match env.get_string(&pc_id) {
        Ok(s) => s.into(),
        Err(_) => {
            throw(&mut env, "config", "no PC id");
            return 0;
        }
    };
    let pc = match pocketdeck_remote::parse_id(&pc) {
        Ok(id) => id,
        Err(e) => {
            throw(&mut env, kind(&e), &e.to_string());
            return 0;
        }
    };
    match Client::start(Config::new(key, relay), pc) {
        Ok(client) => Box::into_raw(Box::new(client)) as jlong,
        Err(e) => {
            throw(&mut env, kind(&e), &e.to_string());
            0
        }
    }
}

/// `[https, http, rtsp, display, video, control, audio]`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_ports<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    // SAFETY: the Kotlin wrapper only passes live handles.
    let p = unsafe { client(handle) }.ports();
    let values = [p.https, p.http, p.rtsp, p.display, p.video, p.control, p.audio].map(i32::from);
    int_array(&mut env, &values)
}

/// `[link, rttMs or -1, closed 0/1]`. Link: 0 off, 1 connecting, 2 lan, 3 internet, 4 relayed,
/// 5 no direct path.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_status<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    handle: jlong,
) -> jintArray {
    if handle == 0 {
        return int_array(&mut env, &[0, -1, 1]);
    }
    // SAFETY: the Kotlin wrapper only passes live handles.
    let c = unsafe { client(handle) };
    let s = c.status();
    let link: jint = match s.link {
        Link::Off => 0,
        Link::Connecting => 1,
        Link::Lan => 2,
        Link::Internet => 3,
        Link::Relayed => 4,
        Link::NoDirectPath => 5,
    };
    let rtt = s.rtt.map(|d| d.as_millis().min(i32::MAX as u128) as i32).unwrap_or(-1);
    int_array(&mut env, &[link, rtt, i32::from(c.is_closed())])
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_abidnafi_pocketdeck_data_stream_RemoteTunnelNative_stop<'a>(
    _env: JNIEnv<'a>,
    _class: JClass<'a>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY: the handle came from Box::into_raw in `start`, and the wrapper stops it once.
        let c = unsafe { Box::from_raw(handle as *mut Client) };
        c.stop();
    }
}
