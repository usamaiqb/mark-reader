// SPDX-License-Identifier: GPL-3.0-only
// Native clipboard writes are asynchronous and acknowledged; no optimistic success labels.
export function createClipboardBridge(send, { timeoutMs = 5000, maxLength = 2_000_000 } = {}) {
    let next = 0;
    const pending = new Map();
    return {
        copyText(text) {
            if (typeof text !== 'string' || text.length > maxLength) return Promise.reject(new Error('Clipboard payload too large'));
            if (pending.size >= 8) return Promise.reject(new Error('Clipboard busy'));
            return new Promise((resolve, reject) => {
                const id = String(++next);
                const timer = setTimeout(() => { pending.delete(id); reject(new Error('Clipboard timeout')); }, timeoutMs);
                pending.set(id, { resolve, reject, timer });
                try { send(id, text); } catch (error) {
                    clearTimeout(timer); pending.delete(id); reject(error);
                }
            });
        },
        complete(json) {
            let payload;
            try { payload = typeof json === 'string' ? JSON.parse(json) : json; } catch { return; }
            const entry = pending.get(payload?.id);
            if (!entry) return;
            clearTimeout(entry.timer); pending.delete(payload.id);
            if (payload.ok === true) entry.resolve();
            else entry.reject(new Error('Native clipboard write failed'));
        },
        dispose() {
            for (const entry of pending.values()) { clearTimeout(entry.timer); entry.reject(new Error('Page closed')); }
            pending.clear();
        },
    };
}
