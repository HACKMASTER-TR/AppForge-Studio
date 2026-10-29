#!/usr/bin/env python3
"""Opt-in AArch64 seccomp network boundary; no license/production approval."""
import ctypes
import errno
import os
import platform
import socket
import stat
import sys


def deny_network():
    if os.uname().sysname != 'Linux' or platform.machine() != 'aarch64':
        raise RuntimeError('only audited Linux AArch64 syscall ABI is supported')

    class Filter(ctypes.Structure):
        _fields_ = [('code', ctypes.c_ushort), ('jt', ctypes.c_ubyte),
                    ('jf', ctypes.c_ubyte), ('k', ctypes.c_uint)]

    class Program(ctypes.Structure):
        _fields_ = [('len', ctypes.c_ushort), ('filter', ctypes.POINTER(Filter))]

    # Kill alternate ABIs. Deny socket creation/use and io_uring networking.
    instructions = [(0x20, 0, 0, 4), (0x15, 1, 0, 0xc00000b7),
                    (0x06, 0, 0, 0x80000000), (0x20, 0, 0, 0)]
    for number in (198, 199, 203, 206, 211, 269, 425, 426, 427):
        instructions += [(0x15, 0, 1, number), (0x06, 0, 0, 0x50000 | errno.EPERM)]
    instructions.append((0x06, 0, 0, 0x7fff0000))
    filters = (Filter * len(instructions))(*(Filter(*i) for i in instructions))
    program = Program(len(filters), filters)
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(38, 1, 0, 0, 0) or libc.prctl(22, 2, ctypes.byref(program), 0, 0):
        raise OSError(ctypes.get_errno(), 'seccomp installation failed')
    for family in (socket.AF_INET, socket.AF_INET6):
        try:
            connection = socket.socket(family, socket.SOCK_STREAM)
        except OSError as error:
            if error.errno != errno.EPERM:
                raise
        else:
            connection.close()
            raise RuntimeError('network boundary probe failed')


def main():
    if len(sys.argv) < 2:
        raise SystemExit('verified command required')
    if any(stat.S_ISSOCK(os.fstat(fd).st_mode) for fd in (0, 1, 2)):
        raise RuntimeError('socket stdio is forbidden')
    for fd in os.listdir('/proc/self/fd'):
        if int(fd) > 2:
            try:
                os.close(int(fd))
            except OSError:
                pass
    deny_network()
    os.execvp(sys.argv[1], sys.argv[1:])


if __name__ == '__main__':
    main()
