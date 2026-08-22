package com.app.castall;

/**
 * A DLNA/UPnP "MediaRenderer" device discovered on the local network via SSDP
 * (e.g. a Samsung Smart TV that doesn't support Google Cast but does support
 * DLNA). Holds just enough information to send it SOAP/AVTransport commands.
 */
public final class DlnaDevice {

    public final String usn;
    public final String friendlyName;
    public final String avTransportControlUrl;
    public final String renderingControlUrl;

    public DlnaDevice(String usn, String friendlyName, String avTransportControlUrl, String renderingControlUrl) {
        this.usn = usn;
        this.friendlyName = friendlyName;
        this.avTransportControlUrl = avTransportControlUrl;
        this.renderingControlUrl = renderingControlUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DlnaDevice)) {
            return false;
        }
        DlnaDevice other = (DlnaDevice) obj;
        return usn != null && usn.equals(other.usn);
    }

    @Override
    public int hashCode() {
        return usn == null ? 0 : usn.hashCode();
    }
}
