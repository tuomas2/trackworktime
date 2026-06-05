package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import io.rebble.pebblekit2.client.java.DefaultJavaPebbleSender;
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem;

import org.pmw.tinylog.Logger;

import java.util.Map;
import java.util.UUID;

/**
 * Helper around PebbleKit Android 2's {@link DefaultJavaPebbleSender}. The sender binds to the
 * Pebble/Core app's service; we create one per send and close it in the result callback (the
 * send is asynchronous, so closing earlier would cancel the in-flight transmission).
 */
final class PebbleSenders {

    private PebbleSenders() {}

    static void sendAndClose(Context context, UUID uuid,
                             Map<Integer, PebbleDictionaryItem> dict, String label) {
        final DefaultJavaPebbleSender sender =
                new DefaultJavaPebbleSender(context.getApplicationContext());
        try {
            sender.sendDataToPebble(uuid, dict, results -> {
                Logger.debug("{} sent to {}: {}", label, uuid, results);
                sender.close();
            });
        } catch (Exception e) {
            Logger.warn(e, "failed to send {} to Pebble", label);
            sender.close();
        }
    }
}
