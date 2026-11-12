package com.markreader.ui.screens

/**
 * How long a `stateIn` flow stays warm after its last collector goes away.
 *
 * It exists to survive a configuration change: the old screen's collector is gone and the new
 * one's has not arrived, and a shorter window makes the recreated screen re-read the store and
 * flash its empty state. Named once because every view model that shares state wants the same
 * answer, and the number means nothing on its own.
 */
const val SHARING_STOP_TIMEOUT_MS = 5_000L
