package com.app.castall;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to a {@link DlnaDevice}'s AVTransport service over UPnP SOAP-over-HTTP.
 * All network calls run on a background thread; results/errors are delivered
 * on the main thread.
 */
final class DlnaController {

    private static final String TAG = "DlnaController";
    private static final String AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1";
    // A single-threaded queue, not a thread pool: TVs generally only handle
    // one UPnP control request at a time correctly. With a cached thread
    // pool, a user-triggered cast could run concurrently with the
    // background position-polling request and race against it on the TV's
    // side, occasionally causing the cast to silently get lost or ignored
    // — this is what made it feel like buttons needed pressing more than
    // once. Serializing everything through one thread fixes that.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String lastError;

    /** A short, human-readable description of the most recent failure, if any. */
    String getLastError() {
        return lastError;
    }

    interface PositionCallback {
        void onPosition(long positionMs, long durationMs);
    }

    interface VolumeCallback {
        void onVolume(int volumePercent);
    }

    interface ResultCallback {
        void onResult(boolean success);
    }

    void setVolume(DlnaDevice device, int volumePercent, ResultCallback callback) {
        if (device.renderingControlUrl == null) {
            postResult(callback, false);
            return;
        }
        EXECUTOR.execute(() -> {
            int clamped = Math.max(0, Math.min(100, volumePercent));
            boolean ok = sendAction(device.renderingControlUrl, RENDERING_CONTROL_SERVICE, "SetVolume",
                    "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>" + clamped + "</DesiredVolume>");
            postResult(callback, ok);
        });
    }

    void getVolume(DlnaDevice device, VolumeCallback callback) {
        if (device.renderingControlUrl == null) {
            mainHandler.post(() -> callback.onVolume(-1));
            return;
        }
        EXECUTOR.execute(() -> {
            String response = sendActionForResponse(device.renderingControlUrl, RENDERING_CONTROL_SERVICE,
                    "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>");
            int volume = -1;
            if (response != null) {
                Pattern pattern = Pattern.compile("<CurrentVolume>([^<]*)</CurrentVolume>");
                Matcher matcher = pattern.matcher(response);
                if (matcher.find()) {
                    try {
                        volume = Integer.parseInt(matcher.group(1).trim());
                    } catch (NumberFormatException ignored) {
                        // leave as -1
                    }
                }
            }
            int finalVolume = volume;
            mainHandler.post(() -> callback.onVolume(finalVolume));
        });
    }

    void setAvTransportUriAndPlay(DlnaDevice device, String mediaUrl, String title, String mimeType,
                                   String upnpClass, ResultCallback callback) {
        EXECUTOR.execute(() -> {
            boolean uriSet = setAvTransportUri(device, mediaUrl, title, mimeType, upnpClass);
            if (uriSet) {
                // Give the TV a brief moment to actually apply the new
                // transport URI before asking it to Play — issuing both back
                // to back is what produces "Transition not available" on
                // several Samsung models, especially right after a previous
                // item was playing (e.g. a fast-advancing playlist).
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            boolean ok = uriSet && play(device);
            postResult(callback, ok);
        });
    }

    void play(DlnaDevice device, ResultCallback callback) {
        EXECUTOR.execute(() -> postResult(callback, play(device)));
    }

    void pause(DlnaDevice device, ResultCallback callback) {
        EXECUTOR.execute(() -> postResult(callback, sendAction(device.avTransportControlUrl, AV_TRANSPORT_SERVICE,
                "Pause", "<InstanceID>0</InstanceID>")));
    }

    void stop(DlnaDevice device, ResultCallback callback) {
        EXECUTOR.execute(() -> postResult(callback, sendAction(device.avTransportControlUrl, AV_TRANSPORT_SERVICE,
                "Stop", "<InstanceID>0</InstanceID>")));
    }

    void seek(DlnaDevice device, long positionMs, ResultCallback callback) {
        EXECUTOR.execute(() -> {
            String target = formatTime(positionMs);
            boolean ok = sendAction(device.avTransportControlUrl, AV_TRANSPORT_SERVICE, "Seek",
                    "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>" + target + "</Target>");
            postResult(callback, ok);
        });
    }

    void getPositionInfo(DlnaDevice device, PositionCallback callback) {
        EXECUTOR.execute(() -> {
            // Deliberately the non-retrying variant: this runs every 1.5s in
            // the background, so if one poll fails it's fine to just wait
            // for the next cycle rather than retrying and holding up the
            // single-threaded queue behind it (which would delay an actual
            // cast command the user is waiting on).
            String responseBody = sendActionForResponseOnce(device.avTransportControlUrl, AV_TRANSPORT_SERVICE,
                    "GetPositionInfo", "<InstanceID>0</InstanceID>");
            long positionMs = -1;
            long durationMs = -1;
            if (responseBody != null) {
                positionMs = extractTimeMs(responseBody, "RelTime");
                durationMs = extractTimeMs(responseBody, "TrackDuration");
            }
            long finalPosition = positionMs;
            long finalDuration = durationMs;
            mainHandler.post(() -> callback.onPosition(finalPosition, finalDuration));
        });
    }

    private void postResult(ResultCallback callback, boolean success) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(success));
        }
    }

    private boolean play(DlnaDevice device) {
        return sendAction(device.avTransportControlUrl, AV_TRANSPORT_SERVICE, "Play",
                "<InstanceID>0</InstanceID><Speed>1</Speed>");
    }

    private boolean setAvTransportUri(DlnaDevice device, String mediaUrl, String title, String mimeType, String upnpClass) {
        String escapedUrl = escapeXml(mediaUrl);
        String metadata = buildDidlLiteMetadata(mediaUrl, title, mimeType, upnpClass);
        String body = "<InstanceID>0</InstanceID>"
                + "<CurrentURI>" + escapedUrl + "</CurrentURI>"
                + "<CurrentURIMetaData>" + escapeXml(metadata) + "</CurrentURIMetaData>";
        return sendAction(device.avTransportControlUrl, AV_TRANSPORT_SERVICE, "SetAVTransportURI", body);
    }

    private String buildDidlLiteMetadata(String mediaUrl, String title, String mimeType, String upnpClass) {
        String safeTitle = title == null || title.isEmpty() ? "Media" : escapeXml(title);
        String safeMimeType = mimeType == null || mimeType.isEmpty() ? "video/mp4" : mimeType;
        String safeUpnpClass = upnpClass == null || upnpClass.isEmpty() ? "object.item.videoItem" : upnpClass;
        // Samsung TVs (and several other DLNA renderers) are notably strict
        // about audio: a plain "http-get:*:audio/mpeg:*" protocolInfo is
        // often silently rejected, whereas the same wildcard is tolerated
        // for video. Adding the standard DLNA.ORG_PN profile token for the
        // formats we actually serve fixes that without touching the video
        // path, which already works.
        String additionalInfo = dlnaAdditionalInfoFor(safeMimeType);
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"0\" parentID=\"0\" restricted=\"1\">"
                + "<dc:title>" + safeTitle + "</dc:title>"
                + "<upnp:class>" + safeUpnpClass + "</upnp:class>"
                + "<res protocolInfo=\"http-get:*:" + safeMimeType + ":" + additionalInfo + "\">" + escapeXml(mediaUrl) + "</res>"
                + "</item></DIDL-Lite>";
    }

    private String dlnaAdditionalInfoFor(String mimeType) {
        switch (mimeType) {
            case "audio/mpeg":
                return "DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
            case "image/jpeg":
                return "DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=00900000000000000000000000000000";
            default:
                // Leave every other type (video included, which already
                // works) exactly as it was: a permissive wildcard.
                return "*";
        }
    }

    private static final int MAX_TRANSIENT_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500L;

    private boolean sendAction(String controlUrl, String serviceType, String actionName, String argumentsXml) {
        return sendActionForResponse(controlUrl, serviceType, actionName, argumentsXml) != null;
    }

    /**
     * Same as sendActionForResponseOnce, but retries a couple of times on the
     * transient failures Samsung TVs commonly return when a previous
     * transport command (e.g. from a fast-advancing playlist) hasn't
     * finished processing yet: an UPnP "Transition not available" fault, or
     * a connect/read timeout because the TV was too busy to answer in time.
     * A short pause before retrying is usually enough for the TV to catch up.
     */
    private String sendActionForResponse(String controlUrl, String serviceType, String actionName, String argumentsXml) {
        String response = null;
        for (int attempt = 0; attempt <= MAX_TRANSIENT_RETRIES; attempt++) {
            response = sendActionForResponseOnce(controlUrl, serviceType, actionName, argumentsXml);
            if (response != null) {
                return response;
            }
            boolean isTransient = lastError != null
                    && (lastError.contains("Transition not available")
                        || lastError.contains("SocketTimeoutException")
                        || lastError.contains("HTTP 500"));
            if (!isTransient || attempt == MAX_TRANSIENT_RETRIES) {
                break;
            }
            Log.w(TAG, actionName + ": transient failure (" + lastError + "), retrying in "
                    + RETRY_DELAY_MS + "ms (attempt " + (attempt + 2) + "/" + (MAX_TRANSIENT_RETRIES + 1) + ")");
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return response;
    }

    private String sendActionForResponseOnce(String controlUrl, String serviceType, String actionName, String argumentsXml) {
        HttpURLConnection connection = null;
        try {
            String soapBody =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body>"
                    + "<u:" + actionName + " xmlns:u=\"" + serviceType + "\">"
                    + argumentsXml
                    + "</u:" + actionName + ">"
                    + "</s:Body></s:Envelope>";
            byte[] bodyBytes = soapBody.getBytes(StandardCharsets.UTF_8);

            URL url = new URL(controlUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            connection.setRequestProperty("SOAPACTION", "\"" + serviceType + "#" + actionName + "\"");
            connection.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            connection.setRequestProperty("Connection", "close");
            // IMPORTANT: without this, HttpURLConnection may silently switch to
            // chunked transfer-encoding for the POST body. Many DLNA renderers
            // (especially older Samsung TVs) reject or mishandle chunked SOAP
            // requests, which otherwise looks like a generic connection failure.
            connection.setFixedLengthStreamingMode(bodyBytes.length);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(bodyBytes);
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);

            if (responseCode < 200 || responseCode >= 300) {
                String faultDescription = extractSoapFaultDescription(response);
                lastError = "HTTP " + responseCode + (faultDescription != null ? ": " + faultDescription : "");
                Log.w(TAG, actionName + " failed with " + lastError + " — body: " + response);
                return null;
            }
            lastError = null;
            return response;
        } catch (IOException e) {
            lastError = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            Log.w(TAG, actionName + " failed: " + lastError);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Pulls the UPnPError description out of a SOAP fault response body, if present. */
    private String extractSoapFaultDescription(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("<errorDescription>([^<]*)</errorDescription>");
        Matcher matcher = pattern.matcher(responseBody);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[2048];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private long extractTimeMs(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<" + tagName + ">([^<]*)</" + tagName + ">");
        Matcher matcher = pattern.matcher(xml);
        if (!matcher.find()) {
            return -1;
        }
        return parseTimeToMs(matcher.group(1));
    }

    /** Parses "H:MM:SS" (or "HH:MM:SS(.mmm)") into milliseconds. */
    private long parseTimeToMs(String time) {
        if (time == null || time.isEmpty() || "NOT_IMPLEMENTED".equalsIgnoreCase(time)) {
            return -1;
        }
        try {
            String[] parts = time.trim().split(":");
            if (parts.length != 3) {
                return -1;
            }
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return (long) ((hours * 3600 + minutes * 60 + seconds) * 1000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatTime(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
