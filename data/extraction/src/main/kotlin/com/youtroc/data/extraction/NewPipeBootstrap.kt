package com.youtroc.data.extraction

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [NewPipe.init] wires a single global, static [Downloader]. It must run exactly once,
 * before any extraction. This guards that invariant idempotently so the adapter can call
 * it lazily without worrying about double-initialization.
 */
object NewPipeBootstrap {

    private val initialized = AtomicBoolean(false)

    fun ensureInitialized(downloader: Downloader = OkHttpDownloader()) {
        if (initialized.compareAndSet(false, true)) {
            // Localization stays at DEFAULT (en-GB) for now; the hl=es decision is wired later.
            NewPipe.init(downloader, Localization.DEFAULT)
        }
    }
}
