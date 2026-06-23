package com.fnva.winkeydisable;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;

import java.util.Arrays;
import java.util.List;

public class WinKeyHook {

    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_KEYDOWN     = 0x0100;
    private static final int WM_KEYUP       = 0x0101;
    private static final int WM_SYSKEYDOWN  = 0x0104;
    private static final int WM_SYSKEYUP    = 0x0105;
    private static final int VK_LWIN        = 0x5B;
    private static final int VK_RWIN        = 0x5C;

    private HHOOK hHook;
    private LowLevelKeyboardProc proc;
    private boolean active = false;
    private volatile boolean installing = false;
    private Thread hookThread;

    private volatile int hookThreadId = 0;

    public void install() {
        if (installing || active) return;
        installing = true;

        hookThread = new Thread(() -> {
            proc = (nCode, wParam, lParam) -> {
                if (nCode >= 0) {
                    int msg = wParam.intValue();
                    if (msg == WM_KEYDOWN    || msg == WM_KEYUP ||
                            msg == WM_SYSKEYDOWN || msg == WM_SYSKEYUP) {
                        KBDLLHOOKSTRUCT hookStruct = new KBDLLHOOKSTRUCT(lParam.getPointer());
                        int vk = hookStruct.vkCode;
                        if (vk == VK_LWIN || vk == VK_RWIN) {
                            return new LRESULT(1);
                        }
                    }
                }
                return User32.INSTANCE.CallNextHookEx(hHook, nCode, wParam, new LPARAM(Pointer.nativeValue(lParam.getPointer())));
            };

            hHook = User32.INSTANCE.SetWindowsHookEx(
                    WH_KEYBOARD_LL,
                    proc,
                    Kernel32.INSTANCE.GetModuleHandle(null),
                    0
            );

            active = (hHook != null);
            installing = false;

            if (active) {
                System.out.println("[WinkeyDisable] Hook installed successfully");
            } else {
                System.out.println("[WinkeyDisable] Hook FAILED: " + Kernel32.INSTANCE.GetLastError());
                return;
            }
            hookThreadId = Kernel32.INSTANCE.GetCurrentThreadId();


            MSG msg = new MSG();
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }
        });

        hookThread.setDaemon(true);
        hookThread.setName("winkey-hook-thread");
        hookThread.start();
    }

    private static final int WM_QUIT = 0x0012;


    public void uninstall() {
        if (hHook != null) {
            User32.INSTANCE.UnhookWindowsHookEx(hHook);
            hHook = null;
            active = false;
        }
        if (hookThreadId != 0) {
            User32.INSTANCE.PostThreadMessage(hookThreadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
            hookThreadId = 0;
        }
    }

    public boolean isActive() {
        return active;
    }

    public static class KBDLLHOOKSTRUCT extends Structure {
        public int vkCode;
        public int scanCode;
        public int flags;
        public int time;
        public BaseTSD.ULONG_PTR dwExtraInfo;

        public KBDLLHOOKSTRUCT(Pointer p) {
            super(p);
            read();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("vkCode", "scanCode", "flags", "time", "dwExtraInfo");
        }
    }
}
