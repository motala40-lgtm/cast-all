package com.app.castall;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "CastAllApp";
    private Uri castLogFileUri;
    private final SimpleDateFormat castLogTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /**
     * Writes a timestamped line to a plain-text log file in the public
     * Downloads folder (via MediaStore, no storage permission needed), so a
     * failed cast attempt can be diagnosed from real data afterwards instead
     * of guessing. Mirrors the approach used for the mirror-cast debug log.
     */
    private void logCastEvent(String message) {
        Log.d(TAG, message);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            if (castLogFileUri == null) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "EasyCast-cast-log.txt");
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
                castLogFileUri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            if (castLogFileUri != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(castLogFileUri, "wa")) {
                    if (out != null) {
                        String line = "[" + castLogTimeFormat.format(new Date()) + "] " + message + "\n";
                        out.write(line.getBytes());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write cast log file", e);
        }
    }
    private static final int REQUEST_DISCOVERY_PERMISSION = 410;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 411;
    private static final int LOCAL_SERVER_PORT = 8080;
    private static final String THEME_PREFS_NAME = "easycast_prefs";
    private static final String THEME_PREF_KEY = "theme_mode";
    private static final String ACCENT_PREF_KEY = "accent_color";
    private static final String CUSTOM_BG_ENABLED_KEY = "custom_bg_enabled";
    private static final String CUSTOM_BG_URI_KEY = "custom_bg_uri";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaRouter.RouteInfo> discoveredRoutes = new ArrayList<>();

    private MediaRouter mediaRouter;
    private MediaRouteSelector routeSelector;
    private CastContext castContext;
    private SessionManager sessionManager;

    private TextView statusView;
    private View statusDot;
    private View statusSpinner;
    private Button selectDeviceButton;
    private EditText videoUrlInput;
    private Button castVideoButton;
    private View urlCastHeaderRow;
    private View urlCastContent;
    private ImageView urlCastChevron;
    private View sleepTimerBanner;
    private TextView sleepTimerBannerText;
    private TextView sleepTimerBannerCancel;
    private Button localVideoButton;
    private Button castPhotoButton;
    private Button castMusicButton;
    private Button playlistButton;
    private ImageButton languageButton;
    private ImageButton settingsButton;
    private View rootLayout;
    private ActivityResultLauncher<PickVisualMediaRequest> customBackgroundPickerLauncher;
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;
    private long sleepTimerEndAtMillis = 0;
    private MediaRouteButton castButton;

    // The following are only valid while the device-selection bottom sheet is
    // showing; they are (re)bound in showDeviceSelectionSheet() and cleared
    // back to null when it's dismissed, so every method that touches them
    // must null-check first.
    private Dialog deviceSheetDialog;
    private LinearLayout deviceList;
    private Button scanButton;
    private Button disconnectButton;
    private View sheetStatusDot;
    private View sheetStatusSpinner;
    private TextView sheetStatusText;

    private LocalWebServer localWebServer;
    private Uri currentLocalVideoUri;
    private String currentLocalVideoMimeType;
    private String currentLocalVideoFileName;
    private long currentLocalVideoFileSize = -1;

    // Which kind of local media is currently selected/being cast — the same
    // "local file → local web server → load on receiver" pipeline is shared
    // by video, photo, and music, but the receiver needs to know which.
    private static final int MEDIA_KIND_VIDEO = 0;
    private static final int MEDIA_KIND_PHOTO = 1;
    private static final int MEDIA_KIND_MUSIC = 2;
    private int currentMediaKind = MEDIA_KIND_VIDEO;

    private ActivityResultLauncher<PickVisualMediaRequest> videoPickerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher;
    private ActivityResultLauncher<String> musicPickerLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> playlistPickerLauncher;
    private ActivityResultLauncher<String> playlistMusicAddLauncher;
    private List<Uri> pendingPlaylistMediaUris;

    private List<Uri> playlistQueue = new ArrayList<>();
    private int playlistIndex = -1;
    private boolean playlistActive = false;
    private boolean playlistAdvancedForCurrentItem = false;
    private static final long SLIDESHOW_INTERVAL_MS = 6000L;
    private final Runnable playlistAdvanceRunnable = this::advancePlaylist;

    // Now Playing / transport controls
    private View nowPlayingCard;
    private TextView playingOnText;
    private TextView nowPlayingTitle;
    private SeekBar mediaSeekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private ImageButton playPauseButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private SeekBar volumeSeekBar;
    private boolean userIsScrubbing = false;
    private boolean userIsAdjustingVolume = false;
    private static final long SEEK_STEP_MS = 10_000L;

    // Reliable mDNS-based Cast discovery requires holding a multicast lock;
    // without it Android's Wi-Fi radio silently drops most discovery packets
    // and only a previously-connected device may ever show up in the list.
    private WifiManager.MulticastLock multicastLock;

    private RemoteMediaClient.Callback mediaClientCallback;
    private RemoteMediaClient.ProgressListener progressListener;
    private RemoteMediaClient registeredMediaClient;

    // ---- DLNA/UPnP support (for TVs like many Samsung models that don't
    // support Google Cast but do support DLNA "MediaRenderer") ----
    private final DlnaDiscovery dlnaDiscovery = new DlnaDiscovery();
    private final DlnaController dlnaController = new DlnaController();
    private final List<DlnaDevice> discoveredDlnaDevices = new ArrayList<>();
    private DlnaDevice connectedDlnaDevice;
    private boolean dlnaIsPlaying = false;
    private long dlnaLastKnownDurationMs = -1;
    private final Runnable dlnaProgressPoller = new Runnable() {
        @Override
        public void run() {
            if (connectedDlnaDevice == null) {
                return;
            }
            dlnaController.getPositionInfo(connectedDlnaDevice, (positionMs, durationMs) -> {
                if (connectedDlnaDevice == null) {
                    return;
                }
                if (durationMs > 0) {
                    dlnaLastKnownDurationMs = durationMs;
                }
                if (!userIsScrubbing && positionMs >= 0 && dlnaLastKnownDurationMs > 0) {
                    updateSeekProgress(positionMs, dlnaLastKnownDurationMs);
                }
                if (playlistActive && dlnaLastKnownDurationMs > 0
                        && positionMs >= dlnaLastKnownDurationMs - 1200) {
                    advancePlaylist();
                }
            });
            handler.postDelayed(this, 1500L);
        }
    };

    private final MediaRouter.Callback routeCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteAdded(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route) {
            refreshRoutes();
        }

        @Override
        public void onRouteRemoved(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route) {
            refreshRoutes();
        }

        @Override
        public void onRouteChanged(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route) {
            refreshRoutes();
        }

        @Override
        public void onRouteSelected(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
            statusView.setText(getString(R.string.connecting_to, route.getName()));
            refreshRoutes();
        }

        @Override
        public void onRouteUnselected(@NonNull MediaRouter router, @NonNull MediaRouter.RouteInfo route, int reason) {
            updateConnectionStatus();
            refreshRoutes();
            stopLocalWebServer();
        }
    };

    private final SessionManagerListener<CastSession> sessionListener = new SessionManagerListener<CastSession>() {
        @Override
        public void onSessionStarting(@NonNull CastSession session) {
            updateConnectionStatus();
        }

        @Override
        public void onSessionStarted(@NonNull CastSession session, @NonNull String sessionId) {
            updateConnectionStatus();
            // Keep the CPU/Wi-Fi radio awake for the whole session so casting
            // (local video OR a remote URL) doesn't drop when the phone screen locks.
            startKeepAliveService();
            attachMediaClient(session);
        }

        @Override
        public void onSessionStartFailed(@NonNull CastSession session, int error) {
            statusView.setText(R.string.not_connected);
            refreshRoutes();
        }

        @Override
        public void onSessionEnding(@NonNull CastSession session) {
            updateConnectionStatus();
        }

        @Override
        public void onSessionEnded(@NonNull CastSession session, int error) {
            updateConnectionStatus();
            refreshRoutes();
            detachMediaClient();
            stopLocalWebServer();
            stopKeepAliveService();
        }

        @Override
        public void onSessionResuming(@NonNull CastSession session, @NonNull String sessionId) {
            updateConnectionStatus();
        }

        @Override
        public void onSessionResumed(@NonNull CastSession session, boolean wasSuspended) {
            updateConnectionStatus();
            startKeepAliveService();
            attachMediaClient(session);
        }

        @Override
        public void onSessionResumeFailed(@NonNull CastSession session, int error) {
            updateConnectionStatus();
        }

        @Override
        public void onSessionSuspended(@NonNull CastSession session, int reason) {
            updateConnectionStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);

        castContext = CastContext.getSharedInstance(getApplicationContext());
        sessionManager = castContext.getSessionManager();
        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        routeSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(
                        getString(R.string.cast_receiver_app_id)))
                .build();

        setContentView(R.layout.activity_cast);
        bindViews();
        applySavedBackground();
        wireListeners();
        updateConnectionStatus();
        applySavedAccentColor();

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        stopPlaylist();
                        currentMediaKind = MEDIA_KIND_VIDEO;
                        currentLocalVideoUri = uri;
                        currentLocalVideoMimeType = getContentResolver().getType(uri);
                        currentLocalVideoFileName = getFileName(uri);
                        currentLocalVideoFileSize = getFileSize(uri);
                        castLocalVideo();
                    }
                });

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        stopPlaylist();
                        currentMediaKind = MEDIA_KIND_PHOTO;
                        currentLocalVideoUri = uri;
                        currentLocalVideoMimeType = getContentResolver().getType(uri);
                        currentLocalVideoFileName = getFileName(uri);
                        currentLocalVideoFileSize = getFileSize(uri);
                        castLocalVideo();
                    }
                });

        musicPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    stopPlaylist();
                    if (uris.size() == 1) {
                        Uri uri = uris.get(0);
                        currentMediaKind = MEDIA_KIND_MUSIC;
                        currentLocalVideoUri = uri;
                        currentLocalVideoMimeType = getContentResolver().getType(uri);
                        currentLocalVideoFileName = getFileName(uri);
                        currentLocalVideoFileSize = getFileSize(uri);
                        castLocalVideo();
                    } else {
                        // Several songs picked at once: play them one after
                        // another using the same auto-advance-on-finish
                        // engine as the Playlist feature.
                        startPlaylist(uris);
                    }
                });

        // Step 1 of the playlist picker: the nice system Photos/Videos grid.
        // Music has no equivalent polished picker on Android (a real OS
        // limitation, not something this app can change), so after this
        // step we separately ask whether to add music tracks too, using the
        // plain file browser only for that part.
        playlistPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(20),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        return;
                    }
                    pendingPlaylistMediaUris = new ArrayList<>(uris);
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.playlist_add_music_title)
                            .setMessage(R.string.playlist_add_music_message)
                            .setPositiveButton(R.string.playlist_add_music_yes, (dialog, which) ->
                                    playlistMusicAddLauncher.launch("audio/*"))
                            .setNegativeButton(R.string.playlist_add_music_no, (dialog, which) -> {
                                startPlaylist(pendingPlaylistMediaUris);
                                pendingPlaylistMediaUris = null;
                            })
                            .setCancelable(false)
                            .show();
                });

        playlistMusicAddLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                musicUris -> {
                    List<Uri> combined = pendingPlaylistMediaUris != null
                            ? new ArrayList<>(pendingPlaylistMediaUris) : new ArrayList<>();
                    if (musicUris != null) {
                        combined.addAll(musicUris);
                    }
                    pendingPlaylistMediaUris = null;
                    startPlaylist(combined);
                });

        customBackgroundPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        setCustomBackground(uri);
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        acquireMulticastLock();
        mediaRouter.addCallback(routeSelector, routeCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN | MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        sessionManager.addSessionManagerListener(sessionListener, CastSession.class);
        CastSession existingSession = sessionManager.getCurrentCastSession();
        if (existingSession != null && existingSession.isConnected()) {
            attachMediaClient(existingSession);
        }
        beginDiscovery();
    }

    @Override
    protected void onStop() {
        sessionManager.removeSessionManagerListener(sessionListener, CastSession.class);
        mediaRouter.removeCallback(routeCallback);
        releaseMulticastLock();
        handler.removeCallbacks(dlnaProgressPoller);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachMediaClient();
        stopLocalWebServer();
    }

    private void acquireMulticastLock() {
        if (multicastLock == null) {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock("CastAll:DiscoveryMulticastLock");
                multicastLock.setReferenceCounted(false);
            }
        }
        if (multicastLock != null && !multicastLock.isHeld()) {
            multicastLock.acquire();
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_DISCOVERY_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                beginDiscovery();
            } else {
                statusView.setText(R.string.permission_required);
            }
        }
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.rootLayout);
        statusView = findViewById(R.id.statusText);
        statusDot = findViewById(R.id.statusDot);
        statusSpinner = findViewById(R.id.statusSpinner);
        selectDeviceButton = findViewById(R.id.selectDeviceButton);
        videoUrlInput = findViewById(R.id.videoUrlInput);
        castVideoButton = findViewById(R.id.castVideoButton);
        urlCastHeaderRow = findViewById(R.id.urlCastHeaderRow);
        urlCastContent = findViewById(R.id.urlCastContent);
        urlCastChevron = findViewById(R.id.urlCastChevron);
        sleepTimerBanner = findViewById(R.id.sleepTimerBanner);
        sleepTimerBannerText = findViewById(R.id.sleepTimerBannerText);
        sleepTimerBannerCancel = findViewById(R.id.sleepTimerBannerCancel);
        localVideoButton = findViewById(R.id.localVideoButton);
        castPhotoButton = findViewById(R.id.castPhotoButton);
        castMusicButton = findViewById(R.id.castMusicButton);
        playlistButton = findViewById(R.id.playlistButton);
        languageButton = findViewById(R.id.languageButton);
        settingsButton = findViewById(R.id.settingsButton);
        castButton = findViewById(R.id.castButton);
        castButton.setRouteSelector(routeSelector);

        nowPlayingCard = findViewById(R.id.nowPlayingCard);
        playingOnText = findViewById(R.id.playingOnText);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        mediaSeekBar = findViewById(R.id.mediaSeekBar);
        currentTimeText = findViewById(R.id.currentTimeText);
        totalTimeText = findViewById(R.id.totalTimeText);
        playPauseButton = findViewById(R.id.playPauseButton);
        rewindButton = findViewById(R.id.rewindButton);
        forwardButton = findViewById(R.id.forwardButton);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
    }

    private void wireListeners() {
        localVideoButton.setOnClickListener(v -> openLocalVideoPicker());
        castPhotoButton.setOnClickListener(v -> openLocalPhotoPicker());
        castMusicButton.setOnClickListener(v -> openLocalMusicPicker());
        playlistButton.setOnClickListener(v -> onPlaylistButtonClicked());
        castVideoButton.setOnClickListener(v -> castVideo(videoUrlInput.getText().toString().trim()));
        urlCastHeaderRow.setOnClickListener(v -> {
            boolean willOpen = urlCastContent.getVisibility() != View.VISIBLE;
            urlCastContent.setVisibility(willOpen ? View.VISIBLE : View.GONE);
            urlCastChevron.setRotation(willOpen ? 180f : 0f);
        });
        sleepTimerBannerCancel.setOnClickListener(v -> {
            cancelSleepTimer();
            updateSleepTimerBanner();
        });
        selectDeviceButton.setOnClickListener(v -> showDeviceSelectionSheet());
        languageButton.setOnClickListener(this::showLanguageMenu);
        settingsButton.setOnClickListener(v -> showSettingsSheet());

        playPauseButton.setOnClickListener(v -> togglePlayPause());
        rewindButton.setOnClickListener(v -> seekBy(-SEEK_STEP_MS));
        forwardButton.setOnClickListener(v -> seekBy(SEEK_STEP_MS));

        mediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTimeText.setText(formatMillis(progressToMillis(progress)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsScrubbing = false;
                long targetMs = progressToMillis(seekBar.getProgress());
                if (connectedDlnaDevice != null) {
                    dlnaController.seek(connectedDlnaDevice, targetMs, null);
                    return;
                }
                RemoteMediaClient client = getActiveMediaClient();
                if (client != null) {
                    client.seek(targetMs);
                }
            }
        });

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                if (connectedDlnaDevice != null) {
                    dlnaController.setVolume(connectedDlnaDevice, progress, null);
                    return;
                }
                CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
                if (currentSession != null && currentSession.isConnected()) {
                    try {
                        currentSession.setVolume(progress / 100.0);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to set Cast volume: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userIsAdjustingVolume = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsAdjustingVolume = false;
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void beginDiscovery() {
        refreshDlnaDevices();
        if (!isWifiConnected()) {
            statusView.setText(R.string.wifi_required);
            if (scanButton != null) {
                scanButton.setEnabled(false);
            }
            refreshRoutes();
            return;
        }
        if (scanButton != null) {
            scanButton.setEnabled(true);
        }
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }
        statusView.setText(R.string.searching);
        refreshRoutes();
        handler.postDelayed(this::updateConnectionStatus, 1500L);
    }

    private void refreshDlnaDevices() {
        dlnaDiscovery.discover(devices -> {
            discoveredDlnaDevices.clear();
            discoveredDlnaDevices.addAll(devices);
            Collections.sort(discoveredDlnaDevices, (left, right) ->
                    left.friendlyName.compareToIgnoreCase(right.friendlyName));
            renderDeviceList();
        });
    }

    private boolean hasDiscoveryPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestDiscoveryPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST_DISCOVERY_PERMISSION);
    }

    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private String getLocalIpAddress() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                try {
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException ex) {
                    Log.e(TAG, "Error getting IP address", ex);
                }
            }
        }
        return null;
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private long getFileSize(Uri uri) {
        long fileSize = -1;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex);
                    }
                }
            }
        }
        return fileSize;
    }

    private void refreshRoutes() {
        if (deviceList == null) {
            return;
        }
        discoveredRoutes.clear();
        for (MediaRouter.RouteInfo route : mediaRouter.getRoutes()) {
            if (route.matchesSelector(routeSelector) && !route.isDefaultOrBluetooth()) {
                discoveredRoutes.add(route);
            }
        }
        Collections.sort(discoveredRoutes, new Comparator<MediaRouter.RouteInfo>() {
            @Override
            public int compare(MediaRouter.RouteInfo left, MediaRouter.RouteInfo right) {
                return left.getName().toString().compareToIgnoreCase(right.getName().toString());
            }
        });
        renderDeviceList();
    }

    private void showDeviceSelectionSheet() {
        Dialog dialog = new Dialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_devices, null);
        dialog.setContentView(sheetView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        deviceList = sheetView.findViewById(R.id.deviceList);
        scanButton = sheetView.findViewById(R.id.scanButton);
        disconnectButton = sheetView.findViewById(R.id.disconnectButton);
        sheetStatusDot = sheetView.findViewById(R.id.sheetStatusDot);
        sheetStatusSpinner = sheetView.findViewById(R.id.sheetStatusSpinner);
        sheetStatusText = sheetView.findViewById(R.id.sheetStatusText);

        scanButton.setOnClickListener(v -> beginDiscovery());
        disconnectButton.setOnClickListener(v -> {
            stopPlaylist();
            if (connectedDlnaDevice != null) {
                disconnectDlna();
            } else {
                sessionManager.endCurrentSession(true);
            }
            updateConnectionStatus();
        });

        dialog.setOnDismissListener(d -> {
            deviceList = null;
            scanButton = null;
            disconnectButton = null;
            sheetStatusDot = null;
            sheetStatusSpinner = null;
            sheetStatusText = null;
            deviceSheetDialog = null;
        });

        deviceSheetDialog = dialog;
        renderDeviceList();
        updateConnectionStatus();
        dialog.show();
    }

    private void renderDeviceList() {
        if (deviceList == null) {
            return;
        }
        deviceList.removeAllViews();
        int visibleCount = 0;

        for (MediaRouter.RouteInfo route : discoveredRoutes) {
            String routeName = route.getName().toString();
            String routeDescription = route.getDescription() == null
                    ? "" : route.getDescription().toString();
            visibleCount++;
            addDeviceRow(routeName, routeDescription, v -> selectRoute(route));
        }

        for (DlnaDevice device : discoveredDlnaDevices) {
            String dlnaLabel = getString(R.string.dlna_device_label);
            visibleCount++;
            addDeviceRow(device.friendlyName, dlnaLabel, v -> selectDlnaDevice(device));
        }

        if (visibleCount == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_devices);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            deviceList.addView(empty, matchWidthWrapHeight());
        }
    }

    private void addDeviceRow(String name, String description, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_device_item));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setClickable(true);
        card.setFocusable(true);

        TextView iconView = new TextView(this);
        iconView.setText("\u25B6"); // simple play-style glyph inside the icon circle
        iconView.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_device_icon));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        boolean isRtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (isRtl) {
            iconParams.leftMargin = dp(12);
        } else {
            iconParams.rightMargin = dp(12);
        }
        card.addView(iconView, iconParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        nameView.setTypeface(nameView.getTypeface(), android.graphics.Typeface.BOLD);
        textColumn.addView(nameView, matchWidthWrapHeight());

        TextView descView = new TextView(this);
        descView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (description != null && !description.isEmpty()) {
            descView.setText(description);
            LinearLayout.LayoutParams descParams = matchWidthWrapHeight();
            descParams.topMargin = dp(2);
            textColumn.addView(descView, descParams);
        }

        LinearLayout.LayoutParams textColumnParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(textColumn, textColumnParams);

        // Small "connecting…" spinner, hidden until this row is tapped, so the
        // user gets immediate feedback that a connection attempt is actually
        // in progress rather than wondering whether their tap registered.
        ProgressBar connectingSpinner = new ProgressBar(this);
        connectingSpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        if (isRtl) {
            spinnerParams.rightMargin = dp(8);
        } else {
            spinnerParams.leftMargin = dp(8);
        }
        card.addView(connectingSpinner, spinnerParams);

        card.setOnClickListener(v -> {
            connectingSpinner.setVisibility(View.VISIBLE);
            String originalDescription = descView.getText().toString();
            descView.setText(R.string.connecting_device);
            descView.setVisibility(View.VISIBLE);
            card.setClickable(false);
            onClick.onClick(v);
            // If this row is still around later (e.g. connection failed and
            // the list wasn't rebuilt), restore its normal look after a
            // reasonable timeout instead of leaving it stuck mid-connect.
            handler.postDelayed(() -> {
                connectingSpinner.setVisibility(View.GONE);
                card.setClickable(true);
                descView.setText(originalDescription);
                if (originalDescription.isEmpty()) {
                    descView.setVisibility(View.GONE);
                }
            }, 8000L);
        });

        LinearLayout.LayoutParams cardParams = matchWidthWrapHeight();
        cardParams.bottomMargin = dp(10);
        deviceList.addView(card, cardParams);
    }

    private void selectRoute(@NonNull MediaRouter.RouteInfo route) {
        if (!isWifiConnected()) {
            statusView.setText(R.string.wifi_required);
            return;
        }
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }
        if (connectedDlnaDevice != null) {
            disconnectDlna();
        }
        statusView.setText(getString(R.string.connecting_to, route.getName()));
        mediaRouter.selectRoute(route);
    }

    private void selectDlnaDevice(@NonNull DlnaDevice device) {
        if (!isWifiConnected()) {
            statusView.setText(R.string.wifi_required);
            return;
        }
        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        if (currentSession != null && currentSession.isConnected()) {
            sessionManager.endCurrentSession(true);
        }
        connectedDlnaDevice = device;
        dlnaIsPlaying = false;
        dlnaLastKnownDurationMs = -1;
        startKeepAliveService();
        updateConnectionStatus();
    }

    private void disconnectDlna() {
        if (connectedDlnaDevice == null) {
            return;
        }
        DlnaDevice device = connectedDlnaDevice;
        dlnaController.stop(device, null);
        connectedDlnaDevice = null;
        dlnaIsPlaying = false;
        handler.removeCallbacks(dlnaProgressPoller);
        stopLocalWebServer();
        stopKeepAliveService();
        if (nowPlayingCard != null) {
            nowPlayingCard.setVisibility(View.GONE);
        }
    }

    private void updateConnectionStatus() {
        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        boolean isCastConnected = currentSession != null && currentSession.isConnected();
        boolean isDlnaConnected = connectedDlnaDevice != null;
        boolean isConnected = isCastConnected || isDlnaConnected;

        videoUrlInput.setEnabled(isConnected);
        castVideoButton.setEnabled(isConnected);
        localVideoButton.setEnabled(isConnected);
        castPhotoButton.setEnabled(isConnected);
        castMusicButton.setEnabled(isConnected);
        playlistButton.setEnabled(isConnected);

        boolean isSearching = !isConnected && isWifiConnected();
        statusSpinner.setVisibility(isSearching ? View.VISIBLE : View.GONE);
        statusDot.setVisibility(isSearching ? View.GONE : View.VISIBLE);
        statusDot.setBackground(ContextCompat.getDrawable(this,
                isConnected ? R.drawable.dot_success : R.drawable.dot_neutral));

        String statusMessage;
        if (isDlnaConnected) {
            statusMessage = getString(R.string.connected_to, connectedDlnaDevice.friendlyName);
        } else if (isCastConnected) {
            CastDevice device = currentSession.getCastDevice();
            String name = device == null ? getString(R.string.app_name) : device.getFriendlyName();
            statusMessage = getString(R.string.connected_to, name);
        } else if (isWifiConnected()) {
            statusMessage = getString(R.string.searching);
        } else {
            statusMessage = getString(R.string.wifi_required);
        }
        statusView.setText(statusMessage);

        if (selectDeviceButton != null) {
            selectDeviceButton.setText(isConnected ? statusMessage : getString(R.string.select_device_button));
        }
        if (!isConnected && nowPlayingCard != null) {
            nowPlayingCard.setVisibility(View.GONE);
        }

        // Mirror the same status into the bottom sheet, if it's currently open.
        if (sheetStatusDot != null) {
            sheetStatusSpinner.setVisibility(isSearching ? View.VISIBLE : View.GONE);
            sheetStatusDot.setVisibility(isSearching ? View.GONE : View.VISIBLE);
            sheetStatusDot.setBackground(ContextCompat.getDrawable(this,
                    isConnected ? R.drawable.dot_success : R.drawable.dot_neutral));
            sheetStatusText.setText(statusMessage);
        }
        if (disconnectButton != null) {
            disconnectButton.setVisibility(isConnected ? View.VISIBLE : View.GONE);
        }
    }

    // ---- Playback controls (Now Playing card) ----

    private RemoteMediaClient getActiveMediaClient() {
        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        return currentSession == null ? null : currentSession.getRemoteMediaClient();
    }

    private void attachMediaClient(@NonNull CastSession session) {
        RemoteMediaClient client = session.getRemoteMediaClient();
        if (client == null) {
            return;
        }
        detachMediaClient();
        registeredMediaClient = client;

        mediaClientCallback = new RemoteMediaClient.Callback() {
            @Override
            public void onStatusUpdated() {
                updateNowPlayingUi();
                if (playlistActive) {
                    MediaStatus mediaStatus = client.getMediaStatus();
                    if (mediaStatus != null && mediaStatus.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED) {
                        advancePlaylist();
                    }
                }
            }

            @Override
            public void onMetadataUpdated() {
                updateNowPlayingUi();
            }
        };
        client.registerCallback(mediaClientCallback);

        progressListener = (progressMs, durationMs) -> {
            if (!userIsScrubbing) {
                updateSeekProgress(progressMs, durationMs);
            }
        };
        client.addProgressListener(progressListener, 500L);

        updateNowPlayingUi();
    }

    private void detachMediaClient() {
        if (registeredMediaClient != null) {
            if (mediaClientCallback != null) {
                registeredMediaClient.unregisterCallback(mediaClientCallback);
            }
            if (progressListener != null) {
                registeredMediaClient.removeProgressListener(progressListener);
            }
        }
        registeredMediaClient = null;
        mediaClientCallback = null;
        progressListener = null;
        if (nowPlayingCard != null) {
            nowPlayingCard.setVisibility(View.GONE);
        }
    }

    private void updateNowPlayingUi() {
        RemoteMediaClient client = getActiveMediaClient();
        if (client == null || client.getMediaStatus() == null) {
            nowPlayingCard.setVisibility(View.GONE);
            return;
        }

        MediaStatus mediaStatus = client.getMediaStatus();
        nowPlayingCard.setVisibility(View.VISIBLE);

        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        CastDevice castDevice = currentSession == null ? null : currentSession.getCastDevice();
        String deviceName = castDevice == null ? getString(R.string.app_name) : castDevice.getFriendlyName();
        playingOnText.setText(getString(R.string.playing_on, deviceName));

        String title = null;
        if (mediaStatus.getMediaInfo() != null && mediaStatus.getMediaInfo().getMetadata() != null) {
            title = mediaStatus.getMediaInfo().getMetadata().getString(MediaMetadata.KEY_TITLE);
        }
        nowPlayingTitle.setText(title == null || title.isEmpty() ? getString(R.string.app_name) : title);

        int playerState = mediaStatus.getPlayerState();
        boolean isPlaying = playerState == MediaStatus.PLAYER_STATE_PLAYING;
        playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        playPauseButton.setContentDescription(getString(
                isPlaying ? R.string.content_desc_pause : R.string.content_desc_play));

        if (!userIsScrubbing) {
            updateSeekProgress(client.getApproximateStreamPosition(), client.getStreamDuration());
        }
        if (!userIsAdjustingVolume && currentSession != null) {
            volumeSeekBar.setProgress((int) Math.round(currentSession.getVolume() * 100));
        }
    }

    private void showDlnaNowPlaying(String title) {
        nowPlayingCard.setVisibility(View.VISIBLE);
        playingOnText.setText(getString(R.string.playing_on,
                connectedDlnaDevice != null ? connectedDlnaDevice.friendlyName : getString(R.string.app_name)));
        nowPlayingTitle.setText(title == null || title.isEmpty() ? getString(R.string.app_name) : title);
        dlnaIsPlaying = true;
        updateDlnaPlayPauseIcon();
        mediaSeekBar.setProgress(0);
        currentTimeText.setText(formatMillis(0));
        totalTimeText.setText(formatMillis(0));
        handler.removeCallbacks(dlnaProgressPoller);
        handler.postDelayed(dlnaProgressPoller, 500L);

        if (connectedDlnaDevice != null) {
            dlnaController.getVolume(connectedDlnaDevice, volume -> {
                if (volume >= 0 && !userIsAdjustingVolume) {
                    volumeSeekBar.setProgress(volume);
                }
            });
        }
    }

    private void updateDlnaPlayPauseIcon() {
        playPauseButton.setImageResource(dlnaIsPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        playPauseButton.setContentDescription(getString(
                dlnaIsPlaying ? R.string.content_desc_pause : R.string.content_desc_play));
    }

    private void updateSeekProgress(long positionMs, long durationMs) {
        if (durationMs <= 0) {
            return;
        }
        int progress = (int) Math.round((positionMs * 1000.0) / durationMs);
        mediaSeekBar.setMax(1000);
        mediaSeekBar.setProgress(Math.max(0, Math.min(1000, progress)));
        currentTimeText.setText(formatMillis(positionMs));
        totalTimeText.setText(formatMillis(durationMs));
    }

    private long progressToMillis(int progress) {
        long duration;
        if (connectedDlnaDevice != null) {
            duration = dlnaLastKnownDurationMs;
        } else {
            RemoteMediaClient client = getActiveMediaClient();
            duration = client == null ? 0 : client.getStreamDuration();
        }
        return Math.round((progress / 1000.0) * duration);
    }

    private String formatMillis(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private void togglePlayPause() {
        if (connectedDlnaDevice != null) {
            DlnaDevice device = connectedDlnaDevice;
            if (dlnaIsPlaying) {
                dlnaController.pause(device, success -> {
                    if (success) {
                        dlnaIsPlaying = false;
                        updateDlnaPlayPauseIcon();
                    }
                });
            } else {
                dlnaController.play(device, success -> {
                    if (success) {
                        dlnaIsPlaying = true;
                        updateDlnaPlayPauseIcon();
                    }
                });
            }
            return;
        }
        RemoteMediaClient client = getActiveMediaClient();
        if (client == null || client.getMediaStatus() == null) {
            return;
        }
        if (client.isPlaying()) {
            client.pause();
        } else {
            client.play();
        }
    }

    private void seekBy(long deltaMs) {
        if (connectedDlnaDevice != null) {
            long duration = dlnaLastKnownDurationMs;
            long target = mediaSeekBar.getProgress() == 0 && duration <= 0
                    ? deltaMs
                    : progressToMillis(mediaSeekBar.getProgress()) + deltaMs;
            target = Math.max(0, target);
            if (duration > 0) {
                target = Math.min(target, duration);
            }
            dlnaController.seek(connectedDlnaDevice, target, null);
            return;
        }
        RemoteMediaClient client = getActiveMediaClient();
        if (client == null || client.getMediaStatus() == null) {
            return;
        }
        long duration = client.getStreamDuration();
        long target = client.getApproximateStreamPosition() + deltaMs;
        target = Math.max(0, target);
        if (duration > 0) {
            target = Math.min(target, duration);
        }
        client.seek(target);
    }

    private String getDlnaUpnpClass() {
        switch (currentMediaKind) {
            case MEDIA_KIND_PHOTO:
                return "object.item.imageItem";
            case MEDIA_KIND_MUSIC:
                return "object.item.audioItem.musicTrack";
            default:
                return "object.item.videoItem";
        }
    }

    private String guessMimeTypeFromUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".mkv")) {
            return "video/x-matroska";
        } else if (lower.contains(".webm")) {
            return "video/webm";
        } else if (lower.contains(".mov")) {
            return "video/quicktime";
        } else if (lower.contains(".avi")) {
            return "video/x-msvideo";
        } else if (lower.contains(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        return "video/mp4";
    }

    private void castVideo(String videoUrl) {
        currentMediaKind = MEDIA_KIND_VIDEO;
        if (videoUrl.isEmpty() || !android.util.Patterns.WEB_URL.matcher(videoUrl).matches()) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        if (connectedDlnaDevice != null) {
            DlnaDevice device = connectedDlnaDevice;
            statusView.setText(getString(R.string.cast_loading, device.friendlyName));
            dlnaController.setAvTransportUriAndPlay(device, videoUrl, videoUrl, guessMimeTypeFromUrl(videoUrl),
                    getDlnaUpnpClass(), success -> {
                if (device != connectedDlnaDevice) {
                    return; // user disconnected/switched devices while this was in flight
                }
                if (success) {
                    statusView.setText(getString(R.string.casting_video, device.friendlyName));
                    showDlnaNowPlaying(videoUrl);
                } else {
                    String detail = dlnaController.getLastError();
                    String errorMessage = detail != null ? detail : getString(R.string.cast_unknown_error);
                    Toast.makeText(this, getString(R.string.cast_failed, errorMessage), Toast.LENGTH_LONG).show();
                    statusView.setText(getString(R.string.cast_failed, errorMessage));
                }
            });
            return;
        }

        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        if (currentSession == null || !currentSession.isConnected()) {
            Toast.makeText(this, R.string.no_device_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        RemoteMediaClient remoteMediaClient = currentSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            Toast.makeText(this, R.string.cast_unknown_error, Toast.LENGTH_SHORT).show();
            return;
        }

        loadMedia(remoteMediaClient, videoUrl, null);
    }

    private String extensionForMimeType(String mimeType, int mediaKind) {
        if (mimeType != null) {
            switch (mimeType) {
                case "video/mp4": return "mp4";
                case "video/webm": return "webm";
                case "video/x-matroska": return "mkv";
                case "video/quicktime": return "mov";
                case "video/avi": case "video/x-msvideo": return "avi";
                case "audio/mpeg": return "mp3";
                case "audio/mp4": case "audio/aac": return "m4a";
                case "audio/ogg": return "ogg";
                case "audio/wav": case "audio/x-wav": return "wav";
                case "image/jpeg": return "jpg";
                case "image/png": return "png";
                case "image/gif": return "gif";
                case "image/webp": return "webp";
            }
        }
        // Fall back to a sensible default per media kind if the MIME type
        // wasn't recognized above.
        switch (mediaKind) {
            case MEDIA_KIND_PHOTO: return "jpg";
            case MEDIA_KIND_MUSIC: return "mp3";
            default: return "mp4";
        }
    }

    private void castLocalVideo() {
        boolean toDlna = connectedDlnaDevice != null;
        if (!toDlna) {
            CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
            if (currentSession == null || !currentSession.isConnected()) {
                Toast.makeText(this, R.string.no_device_selected, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (currentLocalVideoUri == null) {
            int message = currentMediaKind == MEDIA_KIND_PHOTO ? R.string.select_photo_file
                    : currentMediaKind == MEDIA_KIND_MUSIC ? R.string.select_music_file
                    : R.string.select_video_file;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        RemoteMediaClient remoteMediaClient = null;
        if (!toDlna) {
            CastSession currentSession = sessionManager.getCurrentCastSession();
            remoteMediaClient = currentSession.getRemoteMediaClient();
            if (remoteMediaClient == null) {
                Toast.makeText(this, R.string.cast_unknown_error, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Update the running server's source in place instead of stopping and
        // restarting it. Restarting rebinds the same TCP port immediately,
        // which can fail (silently leaving no server running) if the OS
        // hasn't released the previous socket yet — this was the bug where
        // picking a new file while something was already playing required
        // force-closing the app first.
        if (localWebServer != null) {
            localWebServer.updateSource(currentLocalVideoUri, currentLocalVideoMimeType, currentLocalVideoFileSize);
        } else {
            startLocalWebServer();
        }

        String localIp = getLocalIpAddress();
        if (localIp == null) {
            Toast.makeText(this, "Could not get local IP address.", Toast.LENGTH_LONG).show();
            stopLocalWebServer();
            return;
        }

        // Use a fixed, safe path derived from the MIME type instead of the
        // real file name. Real names (especially from the system Photo
        // Picker) can contain spaces or unicode characters that break the
        // URL when not percent-encoded — some DLNA renderers and even the
        // Chromecast default receiver then reject the fetch outright and
        // report it as "unsupported format", even for a perfectly normal
        // mp4/mp3 file. A fixed path sidesteps that whole class of bug.
        String extension = extensionForMimeType(currentLocalVideoMimeType, currentMediaKind);
        String streamPath = "media." + extension;
        String videoUrl = String.format(Locale.ROOT, "http://%s:%d/%s?t=%d", localIp, LOCAL_SERVER_PORT, streamPath, System.currentTimeMillis());
        Log.d(TAG, "Casting local video from URL: " + videoUrl);
        Toast.makeText(this, "Casting local video from: " + videoUrl, Toast.LENGTH_LONG).show();

        if (toDlna) {
            DlnaDevice device = connectedDlnaDevice;
            String title = currentLocalVideoFileName != null ? currentLocalVideoFileName : "Local Video";
            logCastEvent("DLNA setAvTransportUriAndPlay: device=" + device.friendlyName
                    + " url=" + videoUrl + " mimeType=" + currentLocalVideoMimeType
                    + " upnpClass=" + getDlnaUpnpClass());
            dlnaController.setAvTransportUriAndPlay(device, videoUrl, title, currentLocalVideoMimeType,
                    getDlnaUpnpClass(), success -> {
                if (device != connectedDlnaDevice) {
                    return;
                }
                if (success) {
                    statusView.setText(getString(R.string.casting_video, device.friendlyName));
                    showDlnaNowPlaying(title);
                    logCastEvent("DLNA setAvTransportUriAndPlay SUCCEEDED for " + device.friendlyName
                            + " — if only audio plays despite this, the TV accepted the stream but "
                            + "couldn't decode the video track (codec issue in the file itself).");
                } else {
                    String detail = dlnaController.getLastError();
                    String errorMessage = detail != null ? detail : getString(R.string.cast_unknown_error);
                    Toast.makeText(this, getString(R.string.cast_failed, errorMessage), Toast.LENGTH_LONG).show();
                    statusView.setText(getString(R.string.cast_failed, errorMessage));
                    logCastEvent("DLNA setAvTransportUriAndPlay FAILED: " + errorMessage);
                    stopLocalWebServer();
                }
            });
            return;
        }

        loadMedia(remoteMediaClient, videoUrl, currentLocalVideoUri);
    }

    private void loadMedia(RemoteMediaClient remoteMediaClient, String mediaUrl, Uri localUri) {
        String defaultContentType = currentMediaKind == MEDIA_KIND_PHOTO ? "image/jpeg"
                : currentMediaKind == MEDIA_KIND_MUSIC ? "audio/mpeg"
                : "video/mp4";
        String contentType = currentLocalVideoMimeType != null ? currentLocalVideoMimeType : defaultContentType;

        if (currentMediaKind == MEDIA_KIND_VIDEO) {
            if (mediaUrl.contains(".m3u8")) {
                contentType = "application/x-mpegURL"; // HLS
            } else if (mediaUrl.contains(".mpd")) {
                contentType = "application/dash+xml"; // DASH
            } else if (mediaUrl.contains(".webm")) {
                contentType = "video/webm";
            } else if (mediaUrl.contains(".ogg")) {
                contentType = "video/ogg";
            } else if (mediaUrl.contains(".mp4")) {
                contentType = "video/mp4";
            } else if (mediaUrl.contains(".avi")) {
                contentType = "video/avi";
            } else if (mediaUrl.contains(".mov")) {
                contentType = "video/quicktime";
            } else if (mediaUrl.contains(".mkv")) {
                contentType = "video/x-matroska";
            }
        }

        int metadataType;
        int streamType;
        boolean isLiveHls = "application/x-mpegURL".equals(contentType);
        switch (currentMediaKind) {
            case MEDIA_KIND_PHOTO:
                metadataType = MediaMetadata.MEDIA_TYPE_PHOTO;
                streamType = MediaInfo.STREAM_TYPE_NONE;
                break;
            case MEDIA_KIND_MUSIC:
                metadataType = MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
                streamType = MediaInfo.STREAM_TYPE_BUFFERED;
                break;
            default:
                metadataType = MediaMetadata.MEDIA_TYPE_MOVIE;
                // A live, ever-growing HLS playlist (our screen mirror) is not
                // a fixed-duration file — telling Chromecast's receiver it's
                // STREAM_TYPE_BUFFERED (its default assumption for video)
                // makes it expect a known duration and seek behavior that a
                // live stream doesn't have, which can leave the receiver
                // sitting on the idle Cast screen instead of playing.
                streamType = isLiveHls ? MediaInfo.STREAM_TYPE_LIVE : MediaInfo.STREAM_TYPE_BUFFERED;
        }

        MediaMetadata mediaMetadata = new MediaMetadata(metadataType);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, currentLocalVideoFileName != null ? currentLocalVideoFileName : "Casting Video");
        mediaMetadata.addImage(new WebImage(Uri.parse("https://developers.google.com/static/cast/images/cast_icon_light.png")));

        MediaInfo mediaInfo = new MediaInfo.Builder(mediaUrl)
                .setStreamType(streamType)
                .setContentType(contentType)
                .setMetadata(mediaMetadata)
                .build();

        MediaLoadRequestData mediaLoadRequestData = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build();

        CastSession currentSession = sessionManager.getCurrentCastSession();
        String deviceName = (currentSession != null && currentSession.getCastDevice() != null) 
                ? currentSession.getCastDevice().getFriendlyName() : "Device";

        statusView.setText(getString(R.string.cast_loading, deviceName));
        logCastEvent("Chromecast load: device=" + deviceName + " url=" + mediaUrl
                + " contentType=" + contentType + " streamType=" + streamType
                + " mediaKind=" + currentMediaKind);

        remoteMediaClient.load(mediaLoadRequestData)
                .addStatusListener(status -> {
                    if (status.isSuccess()) {
                        statusView.setText(getString(R.string.casting_video, deviceName));
                        logCastEvent("Chromecast load SUCCEEDED for " + deviceName
                                + " — if no picture appears despite this, the receiver accepted "
                                + "the stream but couldn't decode the video track (codec issue in "
                                + "the file itself, not something this app controls).");
                    } else {
                        String errorMessage = status.getStatus().getStatusMessage();
                        if (errorMessage == null || errorMessage.isEmpty()) {
                            errorMessage = getString(R.string.cast_unknown_error);
                        }
                        Toast.makeText(this, getString(R.string.cast_failed, errorMessage), Toast.LENGTH_LONG).show();
                        statusView.setText(getString(R.string.cast_failed, errorMessage));
                        logCastEvent("Chromecast load FAILED: " + errorMessage
                                + " (statusCode=" + status.getStatus().getStatusCode() + ")");
                        stopLocalWebServer(); // Stop server on cast failure
                    }
                });
    }

    private void startLocalWebServer() {
        if (localWebServer == null) {
            try {
                localWebServer = new LocalWebServer(this, LOCAL_SERVER_PORT, currentLocalVideoUri, currentLocalVideoMimeType, currentLocalVideoFileSize);
                localWebServer.start();
                Log.d(TAG, "Local web server started on port " + LOCAL_SERVER_PORT);
                // Wake lock / Wi-Fi lock are managed for the whole Cast session
                // (see sessionListener), so no extra call is needed here.
            } catch (IOException e) {
                Log.e(TAG, "Could not start local web server", e);
                Toast.makeText(this, "Error starting local server: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void stopLocalWebServer() {
        if (localWebServer != null) {
            localWebServer.stop();
            localWebServer = null;
            Log.d(TAG, "Local web server stopped.");
        }
    }

    private void startKeepAliveService() {
        Intent serviceIntent = new Intent(this, CastKeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopKeepAliveService() {
        stopService(new Intent(this, CastKeepAliveService.class));
    }

    private void openLocalVideoPicker() {
        if (!isWifiConnected()) {
            Toast.makeText(this, R.string.wifi_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }
        videoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                .build());
    }

    private void openLocalPhotoPicker() {
        if (!isWifiConnected()) {
            Toast.makeText(this, R.string.wifi_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }
        photoPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void openLocalMusicPicker() {
        if (!isWifiConnected()) {
            Toast.makeText(this, R.string.wifi_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDiscoveryPermission()) {
            requestDiscoveryPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }
        musicPickerLauncher.launch("audio/*");
    }

    // ---- Playlist (auto-advance queue + photo slideshow) ----

    private void onPlaylistButtonClicked() {
        boolean isDlnaConnected = connectedDlnaDevice != null;
        CastSession currentSession = sessionManager == null ? null : sessionManager.getCurrentCastSession();
        boolean isCastConnected = currentSession != null && currentSession.isConnected();
        if (!isDlnaConnected && !isCastConnected) {
            Toast.makeText(this, R.string.no_device_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        playlistPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                .build());
    }

    private void startPlaylist(List<Uri> uris) {
        stopPlaylist();
        playlistQueue = new ArrayList<>(uris);
        playlistIndex = 0;
        playlistActive = true;
        Toast.makeText(this, getString(R.string.playlist_started, playlistQueue.size()), Toast.LENGTH_SHORT).show();
        playCurrentPlaylistItem();
    }

    /** Cancels any in-progress playlist without stopping whatever is currently casting. */
    private void stopPlaylist() {
        playlistActive = false;
        playlistIndex = -1;
        playlistQueue = new ArrayList<>();
        handler.removeCallbacks(playlistAdvanceRunnable);
    }

    private void playCurrentPlaylistItem() {
        if (!playlistActive || playlistIndex < 0 || playlistIndex >= playlistQueue.size()) {
            playlistActive = false;
            return;
        }
        Uri uri = playlistQueue.get(playlistIndex);
        String mimeType = getContentResolver().getType(uri);
        int kind;
        if (mimeType != null && mimeType.startsWith("video/")) {
            kind = MEDIA_KIND_VIDEO;
        } else if (mimeType != null && mimeType.startsWith("audio/")) {
            kind = MEDIA_KIND_MUSIC;
        } else {
            kind = MEDIA_KIND_PHOTO;
        }

        currentMediaKind = kind;
        currentLocalVideoUri = uri;
        currentLocalVideoMimeType = mimeType;
        currentLocalVideoFileName = getFileName(uri);
        currentLocalVideoFileSize = getFileSize(uri);
        playlistAdvancedForCurrentItem = false;
        castLocalVideo();

        handler.removeCallbacks(playlistAdvanceRunnable);
        if (kind == MEDIA_KIND_PHOTO) {
            // Photos have no natural "finished" event, so advance on a timer.
            handler.postDelayed(playlistAdvanceRunnable, SLIDESHOW_INTERVAL_MS);
        }
        // Videos advance when playback actually finishes (see the
        // RemoteMediaClient callback for Chromecast and dlnaProgressPoller
        // for DLNA), not on a fixed timer.
    }

    private void advancePlaylist() {
        if (!playlistActive || playlistAdvancedForCurrentItem) {
            return;
        }
        playlistAdvancedForCurrentItem = true;
        playlistIndex++;
        if (playlistIndex >= playlistQueue.size()) {
            playlistActive = false;
            Toast.makeText(this, R.string.playlist_finished, Toast.LENGTH_SHORT).show();
            return;
        }
        playCurrentPlaylistItem();
    }

    private LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void showLanguageMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(Menu.NONE, 0, Menu.NONE, "English");
        popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "فارسی");
        popup.getMenu().add(Menu.NONE, 2, Menu.NONE, "العربية");
        popup.getMenu().add(Menu.NONE, 3, Menu.NONE, "Svenska");
        popup.getMenu().add(Menu.NONE, 4, Menu.NONE, "Deutsch");
        popup.getMenu().add(Menu.NONE, 5, Menu.NONE, "Français");
        popup.getMenu().add(Menu.NONE, 6, Menu.NONE, "Español");
        popup.getMenu().add(Menu.NONE, 7, Menu.NONE, "Türkçe");
        popup.getMenu().add(Menu.NONE, 8, Menu.NONE, "Русский");
        popup.getMenu().add(Menu.NONE, 9, Menu.NONE, "中文");
        popup.setOnMenuItemClickListener(item -> {
            String lang = "en";
            switch (item.getItemId()) {
                case 0: lang = "en"; break;
                case 1: lang = "fa"; break;
                case 2: lang = "ar"; break;
                case 3: lang = "sv"; break;
                case 4: lang = "de"; break;
                case 5: lang = "fr"; break;
                case 6: lang = "es"; break;
                case 7: lang = "tr"; break;
                case 8: lang = "ru"; break;
                case 9: lang = "zh"; break;
            }
            setLocale(lang);
            return true;
        });
        popup.show();
    }

    private void setThemeMode(int mode) {
        getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(THEME_PREF_KEY, mode)
                .putBoolean(CUSTOM_BG_ENABLED_KEY, false)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
        restoreGradientBackground();
    }

    private void showSettingsSheet() {
        Dialog dialog = new Dialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_settings, null);
        dialog.setContentView(sheetView);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        View themeHeaderRow = sheetView.findViewById(R.id.themeHeaderRow);
        View themeContent = sheetView.findViewById(R.id.themeContent);
        ImageView themeChevron = sheetView.findViewById(R.id.themeChevron);

        View accentHeaderRow = sheetView.findViewById(R.id.accentHeaderRow);
        View accentContent = sheetView.findViewById(R.id.accentColorGrid);
        ImageView accentChevron = sheetView.findViewById(R.id.accentChevron);

        View sleepHeaderRow = sheetView.findViewById(R.id.sleepTimerHeaderRow);
        View sleepContent = sheetView.findViewById(R.id.sleepTimerContent);
        ImageView sleepChevron = sheetView.findViewById(R.id.sleepTimerChevron);

        View[] allContents = {themeContent, accentContent, sleepContent};
        ImageView[] allChevrons = {themeChevron, accentChevron, sleepChevron};

        themeHeaderRow.setOnClickListener(v ->
                toggleAccordionSection(themeContent, themeChevron, allContents, allChevrons));
        accentHeaderRow.setOnClickListener(v ->
                toggleAccordionSection(accentContent, accentChevron, allContents, allChevrons));
        sleepHeaderRow.setOnClickListener(v ->
                toggleAccordionSection(sleepContent, sleepChevron, allContents, allChevrons));

        sheetView.findViewById(R.id.themeLightButton).setOnClickListener(v -> {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_NO);
            dialog.dismiss();
        });
        sheetView.findViewById(R.id.themeDarkButton).setOnClickListener(v -> {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_YES);
            dialog.dismiss();
        });
        sheetView.findViewById(R.id.themeSystemButton).setOnClickListener(v -> {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            dialog.dismiss();
        });
        sheetView.findViewById(R.id.themeCustomButton).setOnClickListener(v -> {
            launchCustomBackgroundPicker();
            dialog.dismiss();
        });

        TextView customThemeStatusText = sheetView.findViewById(R.id.customThemeStatusText);
        if (isCustomBackgroundActive()) {
            customThemeStatusText.setVisibility(View.VISIBLE);
        }

        setupAccentColorGrid(sheetView);

        TextView sleepTimerStatusText = sheetView.findViewById(R.id.sleepTimerStatusText);
        TextView sleepTimerHeaderStatus = sheetView.findViewById(R.id.sleepTimerHeaderStatus);
        updateSleepTimerStatusText(sleepTimerStatusText);
        updateSleepTimerStatusText(sleepTimerHeaderStatus);

        sheetView.findViewById(R.id.sleepTimer15Button).setOnClickListener(v -> {
            startSleepTimer(15);
            updateSleepTimerStatusText(sleepTimerStatusText);
            updateSleepTimerStatusText(sleepTimerHeaderStatus);
        });
        sheetView.findViewById(R.id.sleepTimer30Button).setOnClickListener(v -> {
            startSleepTimer(30);
            updateSleepTimerStatusText(sleepTimerStatusText);
            updateSleepTimerStatusText(sleepTimerHeaderStatus);
        });
        sheetView.findViewById(R.id.sleepTimer60Button).setOnClickListener(v -> {
            startSleepTimer(60);
            updateSleepTimerStatusText(sleepTimerStatusText);
            updateSleepTimerStatusText(sleepTimerHeaderStatus);
        });
        sheetView.findViewById(R.id.sleepTimerOffButton).setOnClickListener(v -> {
            cancelSleepTimer();
            updateSleepTimerStatusText(sleepTimerStatusText);
            updateSleepTimerStatusText(sleepTimerHeaderStatus);
        });

        sheetView.findViewById(R.id.supportHeaderRow).setOnClickListener(v -> {
            dialog.dismiss();
            showSupportInfo();
        });

        dialog.show();
    }

    /** Opens the given section and closes every other one (simple single-open accordion). */
    private void toggleAccordionSection(View content, ImageView chevron, View[] allContents, ImageView[] allChevrons) {
        boolean willOpen = content.getVisibility() != View.VISIBLE;
        for (int i = 0; i < allContents.length; i++) {
            boolean isTarget = allContents[i] == content;
            allContents[i].setVisibility(isTarget && willOpen ? View.VISIBLE : View.GONE);
            allChevrons[i].setRotation(isTarget && willOpen ? 180f : 0f);
        }
    }

    private void updateSleepTimerStatusText(TextView view) {
        if (sleepTimerRunnable == null) {
            view.setText(R.string.sleep_timer_off);
            return;
        }
        long remainingMs = sleepTimerEndAtMillis - System.currentTimeMillis();
        int remainingMin = (int) Math.max(1, Math.ceil(remainingMs / 60_000.0));
        view.setText(getString(R.string.sleep_timer_active, remainingMin));
    }

    private final Runnable sleepTimerBannerTick = this::tickSleepTimerBanner;

    /** Shows/hides the main-screen sleep timer banner and keeps its countdown live while a timer is running. */
    private void updateSleepTimerBanner() {
        if (sleepTimerBanner == null) {
            return;
        }
        if (sleepTimerRunnable == null) {
            sleepTimerBanner.setVisibility(View.GONE);
            sleepTimerHandler.removeCallbacks(sleepTimerBannerTick);
            return;
        }
        sleepTimerBanner.setVisibility(View.VISIBLE);
        long remainingMs = sleepTimerEndAtMillis - System.currentTimeMillis();
        int remainingMin = (int) Math.max(1, Math.ceil(remainingMs / 60_000.0));
        sleepTimerBannerText.setText(getString(R.string.sleep_timer_active, remainingMin));
        sleepTimerHandler.removeCallbacks(sleepTimerBannerTick);
        sleepTimerHandler.postDelayed(sleepTimerBannerTick, 30_000L);
    }

    private void tickSleepTimerBanner() {
        updateSleepTimerBanner();
    }

    private void startSleepTimer(int minutes) {
        cancelSleepTimer();
        long delayMs = minutes * 60_000L;
        sleepTimerEndAtMillis = System.currentTimeMillis() + delayMs;
        sleepTimerRunnable = () -> {
            pausePlaybackForSleepTimer();
            Toast.makeText(MainActivity.this, R.string.sleep_timer_finished, Toast.LENGTH_SHORT).show();
            sleepTimerRunnable = null;
            updateSleepTimerBanner();
        };
        sleepTimerHandler.postDelayed(sleepTimerRunnable, delayMs);
        updateSleepTimerBanner();
    }

    private void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        sleepTimerEndAtMillis = 0;
        updateSleepTimerBanner();
    }

    private void pausePlaybackForSleepTimer() {
        if (connectedDlnaDevice != null) {
            DlnaDevice device = connectedDlnaDevice;
            dlnaController.pause(device, success -> {
                if (success) {
                    dlnaIsPlaying = false;
                    updateDlnaPlayPauseIcon();
                }
            });
            return;
        }
        RemoteMediaClient client = getActiveMediaClient();
        if (client != null && client.getMediaStatus() != null && client.isPlaying()) {
            client.pause();
        }
    }

    private void applySavedTheme() {
        int savedMode = getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .getInt(THEME_PREF_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }

    // ---------------- Custom background (user-picked photo) ----------------

    private boolean isCustomBackgroundActive() {
        return getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .getBoolean(CUSTOM_BG_ENABLED_KEY, false);
    }

    private void launchCustomBackgroundPicker() {
        customBackgroundPickerLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void setCustomBackground(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some picker URIs (e.g. from the system Photo Picker) don't
            // support persistable permission grants; the image will still
            // work for this session, it just won't survive an app restart.
        }
        getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(CUSTOM_BG_ENABLED_KEY, true)
                .putString(CUSTOM_BG_URI_KEY, uri.toString())
                .apply();
        applySavedBackground();
    }

    /** Applies whatever background the user has configured: a custom photo if set, otherwise the default gradient. */
    private void applySavedBackground() {
        if (rootLayout == null) {
            return;
        }
        boolean customEnabled = isCustomBackgroundActive();
        String uriString = getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .getString(CUSTOM_BG_URI_KEY, null);
        if (customEnabled && uriString != null) {
            try {
                Uri uri = Uri.parse(uriString);
                try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        rootLayout.setBackground(new BitmapDrawable(getResources(), bitmap));
                        return;
                    }
                }
            } catch (Exception e) {
                // Fall through to the gradient below — e.g. the picked photo
                // was moved/deleted, or its access grant didn't persist.
            }
        }
        restoreGradientBackground();
    }

    private void restoreGradientBackground() {
        if (rootLayout != null) {
            rootLayout.setBackgroundResource(R.drawable.bg_gradient_sky);
        }
    }

    // ---------------- Accent color theme (10 pastel colors + white) ----------------

    // Index -1 = no saved preference yet -> keep the app's default blue.
    // Indices 0-9 are the pastel palette; index 10 is plain white.
    private static final int[] ACCENT_COLORS = new int[]{
            0xFFFFD1DC, // Pastel pink
            0xFFFFDAC1, // Pastel peach
            0xFFFFF5BA, // Pastel yellow
            0xFFB5EAD7, // Pastel mint
            0xFFC7F0BD, // Pastel green
            0xFFC1E7FF, // Pastel sky blue
            0xFFD4C1FF, // Pastel lavender
            0xFFFFC1E3, // Pastel rose
            0xFFB8F2E6, // Pastel aqua
            0xFFFFB4A2, // Pastel coral
            0xFFFFFFFF, // White
    };

    private void setupAccentColorGrid(View sheetView) {
        GridLayout grid = sheetView.findViewById(R.id.accentColorGrid);
        if (grid == null) return;
        grid.removeAllViews();

        int savedColor = getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .getInt(ACCENT_PREF_KEY, -1);

        int swatchSize = dpToPx(40);
        int innerSize = dpToPx(32);
        int margin = dpToPx(4);

        List<FrameLayout> ringViews = new ArrayList<>();

        for (int i = 0; i < ACCENT_COLORS.length; i++) {
            int color = ACCENT_COLORS[i];
            boolean isSelected = (i == savedColor);

            FrameLayout ring = new FrameLayout(this);
            ring.setBackgroundResource(R.drawable.swatch_ring_bg);
            GridLayout.LayoutParams ringParams = new GridLayout.LayoutParams();
            ringParams.width = swatchSize;
            ringParams.height = swatchSize;
            ringParams.setMargins(margin, margin, margin, margin);
            ring.setLayoutParams(ringParams);
            if (isSelected) {
                ring.getBackground().mutate().setTint(
                        ContextCompat.getColor(this, R.color.swatch_ring_selected));
            }

            View fill = new View(this);
            FrameLayout.LayoutParams fillParams =
                    new FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER);
            fill.setLayoutParams(fillParams);
            fill.setBackgroundResource(R.drawable.swatch_fill_bg);
            fill.getBackground().mutate().setTintList(ColorStateList.valueOf(color));
            ring.addView(fill);

            final int index = i;
            ring.setClickable(true);
            ring.setFocusable(true);
            ring.setOnClickListener(v -> {
                setAccentColor(index);
                for (FrameLayout r : ringViews) {
                    r.getBackground().mutate().setTint(
                            ContextCompat.getColor(this, R.color.swatch_ring_default));
                }
                ring.getBackground().mutate().setTint(
                        ContextCompat.getColor(this, R.color.swatch_ring_selected));
            });

            ringViews.add(ring);
            grid.addView(ring);
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void setAccentColor(int index) {
        getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(ACCENT_PREF_KEY, index)
                .apply();
        applyAccentColor(index);
    }

    private void applySavedAccentColor() {
        int savedIndex = getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE)
                .getInt(ACCENT_PREF_KEY, -1);
        applyAccentColor(savedIndex);
    }

    // Tints the app's primary action buttons and progress indicators with the
    // chosen pastel/white color; index -1 (nothing picked yet) keeps the
    // built-in blue accent.
    private void applyAccentColor(int index) {
        boolean isDefault = (index < 0 || index >= ACCENT_COLORS.length);
        int accent = isDefault
                ? ContextCompat.getColor(this, R.color.primary_blue)
                : ACCENT_COLORS[index];

        ColorStateList accentTint = ColorStateList.valueOf(accent);

        if (mediaSeekBar != null) {
            mediaSeekBar.setProgressTintList(accentTint);
            mediaSeekBar.setThumbTintList(accentTint);
        }
        if (volumeSeekBar != null) {
            volumeSeekBar.setProgressTintList(accentTint);
            volumeSeekBar.setThumbTintList(accentTint);
        }

        // Cast Video, Select Device, and the four colored source buttons
        // (video/photo/music/playlist) always keep their fixed original
        // colors — only the background gradient and the seek bars follow
        // the chosen accent.

        applyAccentBackground(isDefault ? -1 : accent);
    }

    /** Mixes a color toward white by the given ratio (0 = original color, 1 = pure white). */
    private int blendWithWhite(int color, float ratio) {
        int r = (int) (Color.red(color) + (255 - Color.red(color)) * ratio);
        int g = (int) (Color.green(color) + (255 - Color.green(color)) * ratio);
        int b = (int) (Color.blue(color) + (255 - Color.blue(color)) * ratio);
        return Color.rgb(r, g, b);
    }

    /**
     * Recolors the page's background gradient to match the chosen accent —
     * light theme only, and only while no custom background photo is active
     * (per the user's explicit choices). Dark theme and custom-photo mode
     * both leave the background exactly as applySavedBackground() set it.
     */
    private void applyAccentBackground(int accentOrMinusOne) {
        if (rootLayout == null || isCustomBackgroundActive()) {
            return;
        }
        boolean isLightTheme = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (!isLightTheme) {
            return;
        }
        if (accentOrMinusOne == -1) {
            restoreGradientBackground();
            return;
        }
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{accentOrMinusOne, blendWithWhite(accentOrMinusOne, 0.85f)});
        rootLayout.setBackground(gradient);
    }

    // Picks black or white text depending on the pastel swatch's brightness,
    // so button labels stay readable on every accent color including white.
    private int contrastingTextColor(int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.6 ? Color.parseColor("#1A1D29") : Color.WHITE;
    }

    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        recreate(); // Restart activity to apply language changes
    }

    private void showSupportInfo() {
        String[] options = {
                getString(R.string.support_email_option),
                getString(R.string.support_privacy_option)
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.support_button)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setData(Uri.parse("mailto:Newlifetech25@hotmail.com"));
                        try {
                            startActivity(emailIntent);
                        } catch (Exception e) {
                            Toast.makeText(this, getString(R.string.support_message, "Newlifetech25@hotmail.com"), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://motala40-lgtm.github.io/cast-all/privacy-policy.html"));
                        try {
                            startActivity(browserIntent);
                        } catch (Exception e) {
                            Toast.makeText(this, R.string.cast_unknown_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    // Inner class for local web server with Range Request support
    private class LocalWebServer extends NanoHTTPD {
        private final Context context;
        private volatile Uri videoUri;
        private volatile String mimeType;
        private volatile long fileSize;

        public LocalWebServer(Context context, int port, Uri videoUri, String mimeType, long fileSize) {
            super(port);
            this.context = context;
            this.videoUri = videoUri;
            this.mimeType = mimeType;
            this.fileSize = fileSize;
        }

        /** Points this already-running server at a new file without touching the socket. */
        public void updateSource(Uri videoUri, String mimeType, long fileSize) {
            this.videoUri = videoUri;
            this.mimeType = mimeType;
            this.fileSize = fileSize;
        }

        @Override
        public Response serve(IHTTPSession session) {
            // This server only ever serves one active source at a time (the
            // currently selected local file), so it doesn't need to match
            // the request path against a specific file name — doing that
            // used to break whenever the real file name (especially from
            // the system Photo Picker) contained spaces or unicode
            // characters that weren't percent-encoded in the URL.
            if (videoUri != null) {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(videoUri);
                    if (inputStream == null) {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found");
                    }

                    long startFrom = 0;
                    long endAt = fileSize - 1;
                    String rangeHeader = session.getHeaders().get("range");
                    Log.d(TAG, "Range header: " + rangeHeader);

                    if (rangeHeader != null) {
                        Pattern pattern = Pattern.compile("bytes=(\\d*)-(\\d*)");
                        Matcher matcher = pattern.matcher(rangeHeader);
                        if (matcher.find()) {
                            String rangeStart = matcher.group(1);
                            String rangeEnd = matcher.group(2);

                            if (rangeStart != null && !rangeStart.isEmpty()) {
                                startFrom = Long.parseLong(rangeStart);
                            }
                            if (rangeEnd != null && !rangeEnd.isEmpty()) {
                                endAt = Long.parseLong(rangeEnd);
                            }
                        }
                    }

                    long contentLength = endAt - startFrom + 1;
                    boolean isRangeRequest = rangeHeader != null;

                    // Seek to the requested position. InputStream.skip() is not
                    // guaranteed to skip the full requested amount in a single
                    // call (this varies by content provider), so loop until
                    // we've actually skipped everything or hit end-of-stream.
                    long remainingToSkip = startFrom;
                    while (remainingToSkip > 0) {
                        long skipped = inputStream.skip(remainingToSkip);
                        if (skipped <= 0) {
                            Log.e(TAG, "Failed to seek: no more bytes to skip with "
                                    + remainingToSkip + " remaining (requested startFrom=" + startFrom + ")");
                            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error seeking file");
                        }
                        remainingToSkip -= skipped;
                    }

                    Response.IStatus status = isRangeRequest ? Response.Status.PARTIAL_CONTENT : Response.Status.OK;
                    Response response = newFixedLengthResponse(status, mimeType, inputStream, contentLength);
                    response.addHeader("Content-Length", String.valueOf(contentLength));
                    if (isRangeRequest) {
                        response.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileSize);
                    }
                    response.addHeader("Accept-Ranges", "bytes");
                    return response;

                } catch (FileNotFoundException e) {
                    Log.e(TAG, "Local video file not found", e);
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "File not found");
                } catch (Exception e) {
                    Log.e(TAG, "Error serving local video file", e);
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal server error: " + e.getMessage());
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
        }
    }
}
