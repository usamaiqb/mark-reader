// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 DigiGate
export const NO_CAPABILITIES = {
    revealInFolder: false,
    windowBackground: false,
    watchesFiles: false,
};
/** A host that declares nothing can do nothing optional. */
export function capabilitiesOf(host) {
    return host.capabilities ?? NO_CAPABILITIES;
}
//# sourceMappingURL=host.js.map