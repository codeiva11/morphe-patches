package app.morphe.extension.shared.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubReleaseChecker {

    private static final String TAG = "MorpheUpdater";
    private static final String DEFAULT_RELEASES_URL = "https://api.github.com/repos/codeiva11/Morphe-AutoBuilds/releases/tags/latest";
    private static boolean hasCheckedThisSession = false;

    public static void checkUpdateOnStartup(final Context context) {
        checkUpdateOnStartup(context, DEFAULT_RELEASES_URL);
    }

    public static void checkUpdateOnStartup(final Context context, final String customUrl) {
        if (hasCheckedThisSession || context == null) {
            return;
        }
        hasCheckedThisSession = true;

        final String targetUrl = (customUrl != null && !customUrl.isEmpty() && !"null".equalsIgnoreCase(customUrl))
                ? customUrl : DEFAULT_RELEASES_URL;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(targetUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Morphe-Patched-App");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 404 && targetUrl.contains("/releases/tags/latest")) {
                        // Fallback to /releases/latest if release tag endpoint is different
                        URL fallbackUrl = new URL(targetUrl.replace("/releases/tags/latest", "/releases/latest"));
                        conn.disconnect();
                        conn = (HttpURLConnection) fallbackUrl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "Morphe-Patched-App");
                        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        responseCode = conn.getResponseCode();
                    }

                    if (responseCode != 200) {
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject releaseJson = new JSONObject(sb.toString());
                    JSONArray assets = releaseJson.optJSONArray("assets");
                    if (assets == null || assets.length() == 0) {
                        return;
                    }

                    String currentPackageName = context.getPackageName();
                    String appPrefix = resolveAppPrefix(currentPackageName);

                    String primaryAbi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                            ? Build.SUPPORTED_ABIS[0] : "universal";

                    String downloadUrl = null;
                    String assetUpdatedAtStr = null;
                    String matchedAssetName = null;
                    String extractedLatestVersion = null;

                    JSONObject bestAsset = findBestMatchingAsset(assets, appPrefix, primaryAbi);
                    if (bestAsset != null) {
                        matchedAssetName = bestAsset.optString("name", "");
                        downloadUrl = bestAsset.optString("browser_download_url", null);
                        assetUpdatedAtStr = bestAsset.optString("updated_at", bestAsset.optString("created_at", ""));
                        extractedLatestVersion = extractVersionFromAssetName(matchedAssetName);
                    }

                    if (downloadUrl == null || matchedAssetName == null) {
                        return;
                    }

                    PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    String currentVersion = pInfo.versionName != null
                            ? pInfo.versionName.replaceAll("[^0-9.]", "").replaceAll("^\\.|\\.$", "")
                            : "0.0.0";

                    if (extractedLatestVersion == null || extractedLatestVersion.isEmpty()) {
                        String tagName = releaseJson.optString("tag_name", "");
                        extractedLatestVersion = tagName.replaceAll("[^0-9.]", "").replaceAll("^\\.|\\.$", "");
                    }

                    long assetUpdatedAtMillis = parseIso8601(assetUpdatedAtStr);
                    if (assetUpdatedAtMillis <= 0) {
                        assetUpdatedAtMillis = parseIso8601(releaseJson.optString("published_at", ""));
                    }

                    boolean isNewerVer = isNewerVersion(extractedLatestVersion, currentVersion);
                    boolean isSameVer = !extractedLatestVersion.isEmpty() && extractedLatestVersion.equals(currentVersion);

                    boolean isNewerBuild = false;
                    if (isSameVer && assetUpdatedAtMillis > 0) {
                        if (assetUpdatedAtMillis > (pInfo.lastUpdateTime + 60000L)) {
                            isNewerBuild = true;
                        }
                    }

                    if (isNewerVer || isNewerBuild) {
                        final String finalDownloadUrl = downloadUrl;
                        final String finalCurrentVersion = currentVersion;
                        final boolean finalIsRebuild = isNewerBuild && !isNewerVer;
                        final String finalAssetName = matchedAssetName;
                        final String finalLatestVersion = extractedLatestVersion;
                        final long finalAssetTime = assetUpdatedAtMillis;

                        final String apkFileName = (matchedAssetName != null && !matchedAssetName.isEmpty())
                                ? matchedAssetName
                                : (appPrefix + "-v" + finalLatestVersion + ".apk");

                        File targetDir = getUpdateDirectory(context);
                        final File finalFile = new File(targetDir, apkFileName);

                        cleanupOldDownloads(context, apkFileName);

                        if (isValidApk(context, finalFile)) {
                            // Already completely downloaded and valid! Show instant install dialog directly
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    showReadyToInstallDialog(context, finalLatestVersion, finalFile,
                                            finalCurrentVersion, finalIsRebuild, finalAssetName);
                                }
                            });
                        } else {
                            // Show update prompt immediately like Google Photos
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    showUpdateDialog(context, finalLatestVersion, finalDownloadUrl,
                                            finalCurrentVersion, finalIsRebuild, finalAssetTime, finalAssetName, appPrefix);
                                }
                            });
                        }
                    } else {
                        cleanupOldDownloads(context, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking for updates", e);
                }
            }
        }).start();
    }

    private static String resolveAppPrefix(String packageName) {
        if (packageName == null) return "app";
        if (packageName.contains("youtube.music") || packageName.equals("com.google.android.apps.youtube.music")) {
            return "youtube-music";
        }
        if (packageName.contains("youtube") || packageName.equals("com.google.android.youtube")) {
            return "youtube";
        }
        if (packageName.contains("photos")) {
            return "google-photos";
        }
        if (packageName.contains("instagram")) {
            return "instagram";
        }
        if (packageName.contains("prime") || packageName.contains("avod")) {
            return "prime-video";
        }
        if (packageName.contains("protonmail") || packageName.contains("proton.mail")) {
            return "proton-mail";
        }
        if (packageName.contains("protonvpn") || packageName.contains("proton.vpn")) {
            return "proton-vpn";
        }
        if (packageName.contains("facebook") || packageName.contains("katana")) {
            return "facebook";
        }
        if (packageName.contains("wps") || packageName.contains("moffice")) {
            return "wps-office";
        }
        if (packageName.contains("twitter") || packageName.contains("x.android")) {
            return "x-new";
        }
        return packageName;
    }

    private static JSONObject findBestMatchingAsset(JSONArray assets, String appPrefix, String primaryAbi) {
        JSONObject abiMatch = null;
        JSONObject universalMatch = null;
        JSONObject generalMatch = null;

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            if (!name.endsWith(".apk")) continue;

            boolean matchesApp = false;
            if ("youtube".equals(appPrefix)) {
                if (name.startsWith("youtube-") && !name.startsWith("youtube-music-")) {
                    matchesApp = true;
                }
            } else {
                if (name.startsWith(appPrefix + "-") || name.contains(appPrefix)) {
                    matchesApp = true;
                }
            }

            if (!matchesApp) continue;

            if (name.contains(primaryAbi)) {
                abiMatch = asset;
                break;
            } else if (name.contains("universal")) {
                if (universalMatch == null) universalMatch = asset;
            } else {
                if (generalMatch == null) generalMatch = asset;
            }
        }

        if (abiMatch != null) return abiMatch;
        if (universalMatch != null) return universalMatch;
        return generalMatch;
    }

    private static String extractVersionFromAssetName(String assetName) {
        if (assetName == null) return null;
        Pattern p = Pattern.compile("-v([0-9]+(?:\\.[0-9]+)+)");
        Matcher m = p.matcher(assetName);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static boolean isValidApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() < 1024 * 1024) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            return info != null && info.packageName != null && !info.packageName.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void showUpdateDialog(final Context context, final String newVersion, final String downloadUrl,
                                         final String currentVersion, final boolean isRebuild, final long assetTime,
                                         final String assetName, final String appPrefix) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && ((Activity) context).isDestroyed()) {
            return;
        }

        String appName = "App";
        try {
            CharSequence label = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
            if (label != null && label.length() > 0) {
                appName = label.toString();
            }
        } catch (Exception ignored) {}

        String displayName = (assetName != null && !assetName.isEmpty())
                ? assetName
                : (appPrefix + "-v" + newVersion + ".apk");

        String message;
        if (isRebuild) {
            message = "An updated build of " + appName + " (v" + newVersion + ") is available.\n\n" +
                      "Package: " + displayName + "\n\n" +
                      "Would you like to download and install this latest build?";
        } else {
            message = "A new patched version of " + appName + " is available.\n\n" +
                      "Installed version: " + currentVersion + "\n" +
                      "Latest version: " + newVersion + "\n" +
                      "Package: " + displayName + "\n\n" +
                      "Would you like to download and install it?";
        }

        new AlertDialog.Builder(context, getDialogTheme(context))
                .setTitle(isRebuild ? "Build Update Available" : "Update Available")
                .setMessage(message)
                .setPositiveButton("Update", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        boolean canInstall = false;
                        try {
                            canInstall = context.getPackageManager().canRequestPackageInstalls();
                        } catch (SecurityException se) {
                            Log.e(TAG, "Missing REQUEST_INSTALL_PACKAGES check", se);
                        }
                        if (!canInstall) {
                            new AlertDialog.Builder(context, getDialogTheme(context))
                                    .setTitle("Permission Required")
                                    .setMessage(appName + " requires permission to install updates.\n\nPlease allow 'Install unknown apps' in the next screen, then tap Update again.")
                                    .setPositiveButton("Settings", (d, w) -> {
                                        try {
                                            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                            settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                                            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(settingsIntent);
                                        } catch (Exception ex) {
                                            Intent genericIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                            genericIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            context.startActivity(genericIntent);
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return;
                        }
                    }
                    downloadAndInstallApk(context, newVersion, downloadUrl, assetName);
                })
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private static void downloadAndInstallApk(final Context context, final String version, final String downloadUrl,
                                              final String assetName) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            return;
        }

        final String apkFileName = (assetName != null && !assetName.isEmpty())
                ? assetName
                : ("app-v" + version + "-patched.apk");

        final float density = context.getResources().getDisplayMetrics().density;
        final int pad20 = (int) (20 * density);
        final int pad10 = (int) (10 * density);
        final int pad6 = (int) (6 * density);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context, getDialogTheme(context));
        final Context dialogContext = dialogBuilder.getContext();

        final boolean isDark = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        final int primaryTextColor = resolveThemeColor(dialogContext, android.R.attr.textColorPrimary, isDark ? 0xFFFFFFFF : 0xDE000000);
        final int secondaryTextColor = resolveThemeColor(dialogContext, android.R.attr.textColorSecondary, isDark ? 0xB3FFFFFF : 0x8A000000);

        LinearLayout layout = new LinearLayout(dialogContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad20, (int) (14 * density), pad20, pad10);

        TextView fileNameView = new TextView(dialogContext);
        fileNameView.setText(apkFileName);
        fileNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fileNameView.setTypeface(null, android.graphics.Typeface.BOLD);
        fileNameView.setSingleLine(true);
        fileNameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        fileNameView.setTextColor(primaryTextColor);
        LinearLayout.LayoutParams fnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fileNameView.setLayoutParams(fnLp);
        layout.addView(fileNameView);

        final ProgressBar progressBar = new ProgressBar(dialogContext, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.setMargins(0, pad10, 0, pad10);
        progressBar.setLayoutParams(pbLp);
        layout.addView(progressBar);

        final TextView progressInfoView = new TextView(dialogContext);
        progressInfoView.setText("Connecting to server...");
        progressInfoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        progressInfoView.setTextColor(primaryTextColor);
        LinearLayout.LayoutParams piLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressInfoView.setLayoutParams(piLp);
        layout.addView(progressInfoView);

        final TextView speedInfoView = new TextView(dialogContext);
        speedInfoView.setText("Speed: calculating...  •  ETA: --");
        speedInfoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        speedInfoView.setTextColor(secondaryTextColor);
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        speedLp.setMargins(0, pad6, 0, 0);
        speedInfoView.setLayoutParams(speedLp);
        layout.addView(speedInfoView);

        final AtomicBoolean isCancelled = new AtomicBoolean(false);
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        final AlertDialog downloadDialog = dialogBuilder
                .setTitle("Downloading Update")
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("Cancel", (dialog, which) -> {
                    isCancelled.set(true);
                })
                .create();

        downloadDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File targetDir = getUpdateDirectory(context);
                File tempFile = new File(targetDir, apkFileName + ".tmp");
                File finalFile = new File(targetDir, apkFileName);

                if (tempFile.exists()) tempFile.delete();
                if (finalFile.exists()) finalFile.delete();

                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;

                try {
                    String currentUrl = downloadUrl;
                    int redirectCount = 0;
                    while (redirectCount < 7) {
                        if (isCancelled.get()) return;
                        URL url = new URL(currentUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setInstanceFollowRedirects(false);
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Morphe-Updater");
                        conn.setRequestProperty("Accept-Encoding", "identity");
                        conn.setRequestProperty("Connection", "keep-alive");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(20000);

                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                                || responseCode == 307
                                || responseCode == 308) {
                            String location = conn.getHeaderField("Location");
                            conn.disconnect();
                            if (location != null && !location.isEmpty()) {
                                if (location.startsWith("/")) {
                                    URL base = new URL(currentUrl);
                                    currentUrl = new URL(base.getProtocol(), base.getHost(), base.getPort(), location).toString();
                                } else {
                                    currentUrl = location;
                                }
                                redirectCount++;
                                continue;
                            }
                        }
                        break;
                    }

                    if (isCancelled.get()) return;

                    int code = conn.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw new IOException("Server returned HTTP " + code);
                    }

                    final long totalBytes = conn.getContentLength();
                    in = new BufferedInputStream(conn.getInputStream(), 131072);
                    out = new FileOutputStream(tempFile);

                    byte[] buffer = new byte[131072];
                    int bytesRead;
                    long downloadedBytes = 0;

                    long startTime = System.currentTimeMillis();
                    long lastSpeedCalcTime = startTime;
                    long bytesSinceLastSpeed = 0;
                    double currentSpeedBps = 0.0;
                    long lastUiUpdate = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (isCancelled.get()) {
                            tempFile.delete();
                            return;
                        }

                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                        bytesSinceLastSpeed += bytesRead;

                        long now = System.currentTimeMillis();
                        if (now - lastSpeedCalcTime >= 500) {
                            long elapsed = now - lastSpeedCalcTime;
                            currentSpeedBps = (bytesSinceLastSpeed * 1000.0) / elapsed;
                            lastSpeedCalcTime = now;
                            bytesSinceLastSpeed = 0;
                        }

                        if (now - lastUiUpdate >= 80) {
                            lastUiUpdate = now;
                            final long curBytes = downloadedBytes;
                            final double speed = currentSpeedBps;

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isCancelled.get() || !downloadDialog.isShowing()) return;

                                    if (totalBytes > 0) {
                                        progressBar.setIndeterminate(false);
                                        int permille = (int) ((curBytes * 1000L) / totalBytes);
                                        progressBar.setProgress(permille);

                                        double pct = (curBytes * 100.0) / totalBytes;
                                        double curMb = curBytes / (1024.0 * 1024.0);
                                        double totMb = totalBytes / (1024.0 * 1024.0);
                                        progressInfoView.setText(String.format(Locale.US, "%.1f%%  (%.1f / %.1f MB)", pct, curMb, totMb));

                                        if (speed > 1024) {
                                            long remainingBytes = totalBytes - curBytes;
                                            long etaSeconds = (long) (remainingBytes / speed);
                                            String speedStr = speed > (1024 * 1024)
                                                    ? String.format(Locale.US, "%.1f MB/s", speed / (1024.0 * 1024.0))
                                                    : String.format(Locale.US, "%.0f KB/s", speed / 1024.0);
                                            String etaStr = etaSeconds >= 60
                                                    ? String.format(Locale.US, "%dm %ds", etaSeconds / 60, etaSeconds % 60)
                                                    : String.format(Locale.US, "%ds", etaSeconds);
                                            speedInfoView.setText(String.format(Locale.US, "Speed: %s  •  ETA: %s", speedStr, etaStr));
                                        }
                                    } else {
                                        progressBar.setIndeterminate(true);
                                        double curMb = curBytes / (1024.0 * 1024.0);
                                        progressInfoView.setText(String.format(Locale.US, "Downloaded %.1f MB", curMb));
                                    }
                                }
                            });
                        }
                    }

                    out.flush();
                    out.close();
                    out = null;
                    in.close();
                    in = null;

                    if (isCancelled.get()) {
                        tempFile.delete();
                        return;
                    }

                    if (!tempFile.renameTo(finalFile)) {
                        boolean copied = copyFile(tempFile, finalFile);
                        tempFile.delete();
                        if (!copied) {
                            throw new IOException("Failed to save downloaded APK to destination file");
                        }
                    }

                    // Download complete! Dismiss progress and trigger install
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (downloadDialog.isShowing()) {
                                    downloadDialog.dismiss();
                                }
                            } catch (Exception ignored) {}

                            installApk(context, finalFile);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Download error", e);
                    final String errorMsg = (e.getMessage() != null) ? e.getMessage() : "Network error";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (downloadDialog.isShowing()) {
                                    downloadDialog.dismiss();
                                }
                            } catch (Exception ignored) {}

                            if (!isCancelled.get()) {
                                showDownloadErrorDialog(context, errorMsg);
                            }
                        }
                    });
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private static void showReadyToInstallDialog(final Context context, final String newVersion, final File apkFile,
                                                 final String currentVersion, final boolean isRebuild, final String assetName) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && ((Activity) context).isDestroyed()) {
            return;
        }

        String appName = "App";
        try {
            CharSequence label = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
            if (label != null && label.length() > 0) {
                appName = label.toString();
            }
        } catch (Exception ignored) {}

        String title = isRebuild ? "Build Update Ready to Install" : "Update Ready to Install";
        String message;
        if (isRebuild) {
            message = "An updated build of " + appName + " (v" + newVersion + ") has been downloaded in the background and is ready to install.\n\n" +
                      "Package: " + assetName + "\n\n" +
                      "Tap 'Install Now' to update instantly with zero wait time.";
        } else {
            message = "A new patched version of " + appName + " (v" + newVersion + ") has been downloaded in the background and is ready to install.\n\n" +
                      "Current version: " + currentVersion + "\n" +
                      "New version: " + newVersion + "\n" +
                      "Package: " + assetName + "\n\n" +
                      "Tap 'Install Now' to update instantly with zero wait time.";
        }

        new AlertDialog.Builder(context, getDialogTheme(context))
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Install Now", (dialog, which) -> {
                    installApk(context, apkFile);
                })
                .setNegativeButton("Later", null)
                .setCancelable(true)
                .show();
    }

    private static void installApk(Context context, File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean canInstall = false;
                try {
                    canInstall = context.getPackageManager().canRequestPackageInstalls();
                } catch (SecurityException se) {
                    canInstall = false;
                }
                if (!canInstall) {
                    new AlertDialog.Builder(context, getDialogTheme(context))
                            .setTitle("Permission Required")
                            .setMessage("Please allow 'Install unknown apps' permission to install updates instantly.")
                            .setPositiveButton("Settings", (dialog, which) -> {
                                try {
                                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                    settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                                    settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(settingsIntent);
                                } catch (Exception ex) {
                                    Intent genericIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                    genericIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(genericIntent);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    Class<?> fpClass = Class.forName("androidx.core.content.FileProvider");
                    Method getUriMethod = null;
                    try {
                        getUriMethod = fpClass.getMethod("getUriForFile", Context.class, String.class, File.class);
                    } catch (NoSuchMethodException e) {
                        for (Method m : fpClass.getDeclaredMethods()) {
                            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                                    && m.getReturnType() == Uri.class
                                    && m.getParameterTypes().length == 3
                                    && m.getParameterTypes()[0] == Context.class
                                    && m.getParameterTypes()[1] == String.class
                                    && m.getParameterTypes()[2] == File.class) {
                                getUriMethod = m;
                                getUriMethod.setAccessible(true);
                                break;
                            }
                        }
                    }
                    if (getUriMethod != null) {
                        String[] authorities = new String[] {
                            context.getPackageName() + ".morphe.updater.provider",
                            context.getPackageName() + ".fileprovider",
                            context.getPackageName() + ".provider"
                        };
                        for (String auth : authorities) {
                            try {
                                uri = (Uri) getUriMethod.invoke(null, context, auth, apkFile);
                                if (uri != null) break;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "FileProvider reflection failed", ex);
                }
            }

            if (uri == null) {
                uri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Install failed", e);
            try {
                new AlertDialog.Builder(context, getDialogTheme(context))
                        .setTitle("Installation Failed")
                        .setMessage("Failed to launch package installer: " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception ignored) {}
        }
    }

    private static void showDownloadErrorDialog(Context context, String error) {
        if (context instanceof Activity && !((Activity) context).isFinishing()) {
            try {
                new AlertDialog.Builder(context, getDialogTheme(context))
                        .setTitle("Download Failed")
                        .setMessage("Could not download the update:\n" + error + "\n\nPlease check your internet connection and try again.")
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception ignored) {}
        }
    }

    private static File getUpdateDirectory(Context context) {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static void cleanupOldDownloads(Context context, String activeFileName) {
        try {
            File dir = new File(context.getCacheDir(), "updates");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".apk")) {
                            if (activeFileName != null && file.getName().equals(activeFileName)) {
                                continue;
                            }
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory()) {
                File[] files = downloadDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().startsWith("youtube") && file.getName().endsWith(".apk")) {
                            if (activeFileName != null && file.getName().equals(activeFileName)) {
                                continue;
                            }
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean copyFile(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long parseIso8601(String isoString) {
        if (isoString == null || isoString.isEmpty()) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoString);
            return date != null ? date.getTime() : 0;
        } catch (Exception ignored) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(isoString);
                return date != null ? date.getTime() : 0;
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null || latest.isEmpty() || current.isEmpty()) return false;
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int maxLen = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < maxLen; i++) {
            long l = i < latestParts.length ? parseSafeLong(latestParts[i]) : 0L;
            long c = i < currentParts.length ? parseSafeLong(currentParts[i]) : 0L;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static long parseSafeLong(String str) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int getDialogTheme(Context context) {
        try {
            int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                return android.R.style.Theme_DeviceDefault_Dialog_Alert;
            } else {
                return android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static int resolveThemeColor(Context context, int attrResId, int fallbackColor) {
        try {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(attrResId, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                int resId = tv.resourceId != 0 ? tv.resourceId : tv.data;
                if (resId != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        return context.getColor(resId);
                    } else {
                        return context.getResources().getColor(resId);
                    }
                }
            }
        } catch (Exception ignored) {}
        return fallbackColor;
    }
}
