"""Cooperative progress and cancellation for scripts run through Rider's ue_execute_python.

Unreal's Python API is game-thread only, so a script executed by the tool blocks the whole
editor for as long as it runs. Rider gives most calls a wall-clock budget and stops the script
when it elapses. Calling tick() inside a long loop buys two things:

  * the editor's UI keeps repainting, and once the script has run for a moment, a progress
    dialog with a Cancel button appears. tick() refreshes Slate, but does not tick the engine,
    so no frame is rendered until the script returns;
  * pressing Cancel in that dialog raises ScriptAborted at your next tick() call.

Usage:
    import rider_progress
    for i, asset in enumerate(assets):
        rider_progress.tick('scanning %d/%d' % (i + 1, len(assets)))
        ...

tick() is cheap enough for an inner loop - it reads a clock, and talks to the editor at most
once every 100 ms.

Everything prefixed with an underscore is called by Rider around your script; you only need
tick().
"""

import ctypes
import threading
import time

import unreal

__all__ = ['tick', 'ScriptAborted']

# PyThreadState_SetAsyncExc(unsigned long thread_id, PyObject *exc) schedules an exception in
# another thread; CPython delivers it at that thread's next bytecode boundary.
ctypes.pythonapi.PyThreadState_SetAsyncExc.argtypes = [ctypes.c_ulong, ctypes.py_object]
ctypes.pythonapi.PyThreadState_SetAsyncExc.restype = ctypes.c_int


class ScriptAborted(BaseException):
    """Raised in the script's thread when its time budget elapses or someone cancels.

    Derives from BaseException rather than Exception so that a broad ``except Exception``
    inside a script does not swallow the abort by accident.
    """


_PUMP_INTERVAL = 0.1     # seconds between editor progress pumps
_POLL_INTERVAL = 0.05    # seconds between watchdog checks
_REFIRE_INTERVAL = 0.5   # keep re-raising past the deadline, to outlast a bare `except:`

_lock = threading.Lock()
_deadline = None         # time.monotonic() value, or None when nothing is armed
_cancel_addr = 0         # address of the int32 the editor sets when the client cancels
_running_addr = 0        # address of the int32 the editor holds at 1 while the script runs
_script_thread = None    # thread running the script, i.e. the game thread
_generation = 0          # bumped on every arm/disarm so a stale watchdog exits
_last_pump = 0.0


def tick(message=None):
    """Yield to the editor: refresh progress, and raise ScriptAborted if time is up.

    Safe to call from a script running outside the tool - it does nothing when no budget
    is armed. `message` is shown in the progress dialog.
    """
    global _last_pump

    with _lock:
        deadline = _deadline
        since_pump = time.monotonic() - _last_pump

    if deadline is None:
        return

    if time.monotonic() >= deadline:
        raise ScriptAborted('script exceeded its time budget')
    if _cancel_requested():
        raise ScriptAborted('cancelled by the client')

    if since_pump < _PUMP_INTERVAL:
        return
    with _lock:
        _last_pump = time.monotonic()

    if not unreal.RiderAgentBridgeLibrary.python_progress_tick(message or ''):
        raise ScriptAborted('cancelled from the editor')


def _cancel_requested():
    if not _cancel_addr:
        return False
    return ctypes.c_int32.from_address(_cancel_addr).value != 0


def _script_running():
    if not _running_addr:
        return True  # no flag to read: keep the deadline rather than drop it
    return ctypes.c_int32.from_address(_running_addr).value != 0


def _raise_in_script_thread(thread_id):
    if thread_id is not None:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(
            ctypes.c_ulong(thread_id), ctypes.py_object(ScriptAborted))


def _watch(generation):
    """Watchdog body: abort the script once its deadline passes or the client cancels.

    Runs on its own daemon thread. It can only act while the script releases the GIL, which
    a pure-Python loop does every few milliseconds - but a script sitting inside one long
    native call (a large get_referencers, LoadPackage, a Blueprint compile) is not
    interrupted until that call returns.
    """
    last_fired = 0.0
    while True:
        time.sleep(_POLL_INTERVAL)

        # The decision and the raise happen under the lock so that _disarm() cannot slip in
        # between them. The lock alone is not enough: _disarm() needs the game thread to run
        # Python, and that is the thread this raise targets, so an abort can kill the command
        # that would have stopped it. The editor's running flag closes that hole, because the
        # editor lowers it without the interpreter.
        with _lock:
            if generation != _generation:
                return  # disarmed, or superseded by a newer run
            if _deadline is None:
                return
            if not _script_running():
                return  # the script already returned; an abort now would hit the editor's cleanup
            if time.monotonic() < _deadline and not _cancel_requested():
                continue
            now = time.monotonic()
            if now - last_fired < _REFIRE_INTERVAL:
                continue
            last_fired = now
            _raise_in_script_thread(_script_thread)


def _arm(timeout_ms, cancel_addr, running_addr):
    global _deadline, _cancel_addr, _running_addr, _script_thread, _generation, _last_pump

    with _lock:
        _generation += 1
        generation = _generation
        _deadline = time.monotonic() + timeout_ms / 1000.0
        _cancel_addr = cancel_addr
        _running_addr = running_addr
        _script_thread = threading.get_ident()
        _last_pump = time.monotonic()

    threading.Thread(target=_watch, args=(generation,), daemon=True,
                     name='rider-python-watchdog').start()


def _disarm():
    global _deadline, _cancel_addr, _running_addr, _script_thread, _generation

    with _lock:
        _generation += 1  # tells the running watchdog to exit
        _deadline = None
        _cancel_addr = 0
        _running_addr = 0
        _script_thread = None


def _snapshot(scope):
    """Record which names a scope held before the script ran, so _restore can undo it."""
    scope['__rider_scope_keys__'] = frozenset(scope)


def _restore(scope):
    """Drop the names an aborted script left behind, keeping everything that predates it.

    Batch scripts share __main__ so that steps can pass state to each other. Without this,
    a script aborted halfway would leave its half-built objects bound there for the next
    call to trip over.
    """
    keys = scope.pop('__rider_scope_keys__', None)
    if keys is None:
        return
    for name in [n for n in list(scope) if n not in keys and not n.startswith('__')]:
        scope.pop(name, None)
