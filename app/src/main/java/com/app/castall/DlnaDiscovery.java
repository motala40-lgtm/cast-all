package com.app.castall;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discovers DLNA/UPnP "MediaRenderer" devices (e.g. many Samsung/LG smart TVs
 * that don't support Google Cast) using SSDP multicast search, then fetches
 * each device's description XML to find its AVTransport control URL.
 *
 * All work runs on a background thread; results are delivered on the main
 * thread via {@link Callback}.
 */
final class DlnaDiscovery {

    private static final String TAG = "DlnaDiscovery";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int SEARCH_TIMEOUT_MS = 4000;
    private static final String SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1";

    interface Callback {
        void onDevicesFound(List<DlnaDevice> devices);
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    void discover(@androidx.annotation.NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            List<DlnaDevice> devices = performDiscovery();
            mainHandler.post(() -> callback.onDevicesFound(devices));
        });
    }

    private List<DlnaDevice> performDiscovery() {
        Map<String, DlnaDevice> devicesByUsn = new HashMap<>();
        Set<String> locationsSeen = new LinkedHashSet<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SEARCH_TIMEOUT_MS);
            socket.setReuseAddress(true);

            String searchRequest =
                    "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: " + SEARCH_TARGET + "\r\n" +
                    "\r\n";
            byte[] requestBytes = searchRequest.getBytes(StandardCharsets.UTF_8);
            InetAddress ssdpAddress = InetAddress.getByName(SSDP_ADDRESS);
            DatagramPacket requestPacket =
                    new DatagramPacket(requestBytes, requestBytes.length, ssdpAddress, SSDP_PORT);
            socket.send(requestPacket);

            long deadline = System.currentTimeMillis() + SEARCH_TIMEOUT_MS;
            byte[] buffer = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(responsePacket);
                } catch (SocketTimeoutException timeout) {
                    break;
                }
                String response = new String(
                        responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8);
                String location = extractHeader(response, "LOCATION");
                if (location == null || locationsSeen.contains(location)) {
                    continue;
                }
                locationsSeen.add(location);

                DlnaDevice device = fetchDeviceDescription(location);
                if (device != null) {
                    devicesByUsn.put(device.usn, device);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "SSDP discovery failed: " + e.getMessage());
        }

        return new ArrayList<>(devicesByUsn.values());
    }

    private String extractHeader(String httpMessage, String headerName) {
        for (String line : httpMessage.split("\r\n")) {
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            String name = line.substring(0, colonIndex).trim();
            if (name.equalsIgnoreCase(headerName)) {
                return line.substring(colonIndex + 1).trim();
            }
        }
        return null;
    }

    /**
     * Fetches the UPnP device description XML at {@code location} and, if it
     * describes a device with an AVTransport service, returns a
     * {@link DlnaDevice} with the friendly name and resolved control URL.
     */
    private DlnaDevice fetchDeviceDescription(String location) {
        HttpURLConnection connection = null;
        try {
            URL locationUrl = new URL(location);
            connection = (HttpURLConnection) locationUrl.openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestMethod("GET");

            try (InputStream inputStream = connection.getInputStream()) {
                return parseDeviceDescription(inputStream, locationUrl);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch device description from " + location + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private DlnaDevice parseDeviceDescription(InputStream inputStream, URL baseUrl) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(inputStream, null);

        String friendlyName = null;
        String udn = null;
        String avTransportControlUrl = null;
        String renderingControlUrl = null;

        String currentTag = null;
        boolean isAvTransportService = false;
        boolean isRenderingControlService = false;
        String pendingControlUrl = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if ("service".equalsIgnoreCase(currentTag)) {
                    isAvTransportService = false;
                    isRenderingControlService = false;
                    pendingControlUrl = null;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                String text = parser.getText() == null ? "" : parser.getText().trim();
                if (text.isEmpty() || currentTag == null) {
                    // no-op
                } else if ("friendlyName".equalsIgnoreCase(currentTag) && friendlyName == null) {
                    friendlyName = text;
                } else if ("UDN".equalsIgnoreCase(currentTag) && udn == null) {
                    udn = text;
                } else if ("serviceType".equalsIgnoreCase(currentTag)) {
                    if (text.contains("service:AVTransport:")) {
                        isAvTransportService = true;
                    } else if (text.contains("service:RenderingControl:")) {
                        isRenderingControlService = true;
                    }
                } else if ("controlURL".equalsIgnoreCase(currentTag)) {
                    pendingControlUrl = text;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("service".equalsIgnoreCase(parser.getName()) && pendingControlUrl != null) {
                    if (isAvTransportService && avTransportControlUrl == null) {
                        avTransportControlUrl = resolveUrl(baseUrl, pendingControlUrl);
                    } else if (isRenderingControlService && renderingControlUrl == null) {
                        renderingControlUrl = resolveUrl(baseUrl, pendingControlUrl);
                    }
                }
                currentTag = null;
            }
            eventType = parser.next();
        }

        if (avTransportControlUrl == null || friendlyName == null) {
            return null;
        }
        String usn = udn != null ? udn : avTransportControlUrl;
        return new DlnaDevice(usn, friendlyName, avTransportControlUrl, renderingControlUrl);
    }

    private String resolveUrl(URL baseUrl, String maybeRelative) {
        try {
            return new URL(baseUrl, maybeRelative).toString();
        } catch (Exception e) {
            return maybeRelative;
        }
    }
}
