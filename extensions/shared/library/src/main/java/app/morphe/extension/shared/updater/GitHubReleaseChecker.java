package app.morphe.extension.shared.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;

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
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubReleaseChecker {

    private static final String TAG = "MorpheUpdater";
    private static final String DEFAULT_RELEASES_URL = "https://api.github.com/repos/codeiva11/Morphe-AutoBuilds/releases/tags/latest";
    private static boolean hasCheckedThisSession = false;
    private static final AtomicBoolean isSilentDownloading = new AtomicBoolean(false);

    public static void checkUpdateOnStartup(final Context context) {
        checkUpdateOnStartup(context, DEFAULT_RELEASES_URL);
    }

    public static void checkUpdateOnStartup(final Context context, final String customUrl) {
        if (hasCheckedThisSession || context == null) {
            return;
        }
        hasCheckedThisSession = true;

        final String targetUrl = (customUrl != null && !customUrl.isEmpty()) ? customUrl : DEFAULT_RELEASES_URL;

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
                        final String finalLatestVersion = extractedLatestVersion;
                        final boolean finalIsRebuild = isNewerBuild && !isNewerVer;
                        final String finalAssetName = matchedAssetName;

                        File targetDir = getUpdateDirectory(context);
                        final File finalFile = new File(targetDir, matchedAssetName);

                        cleanupOldDownloads(context, matchedAssetName);

                        if (isValidApk(context, finalFile)) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    showReadyToInstallDialog(context, finalLatestVersion, finalFile,
                                            finalCurrentVersion, finalIsRebuild, finalAssetName);
                                }
                            });
                        } else {
                            downloadApkSilently(context, finalLatestVersion, finalDownloadUrl,
                                    finalCurrentVersion, finalIsRebuild, finalAssetName, finalFile);
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

    private static void downloadApkSilently(final Context context, final String latestVersion,
                                            final String downloadUrl, final String currentVersion,
                                            final boolean isRebuild, final String assetName,
                                            final File finalFile) {
        if (!isSilentDownloading.compareAndSet(false, true)) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                File tempFile = new File(finalFile.getParentFile(), finalFile.getName() + ".tmp");
                if (tempFile.exists()) tempFile.delete();
                if (finalFile.exists()) finalFile.delete();

                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;

                try {
                    String currentUrl = downloadUrl;
                    int redirectCount = 0;
                    while (redirectCount < 7) {
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

                    int code = conn.getResponseCode();
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw new IOException("Server returned HTTP " + code);
                    }

                    in = new BufferedInputStream(conn.getInputStream(), 131072);
                    out = new FileOutputStream(tempFile);

                    byte[] buffer = new byte[131072];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();

                    File targetApk = finalFile;
                    if (tempFile.renameTo(finalFile) || copyFile(tempFile, finalFile)) {
                        tempFile.delete();
                    } else {
                        targetApk = tempFile;
                    }

                    if (isValidApk(context, targetApk)) {
                        final File readyFile = targetApk;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                showReadyToInstallDialog(context, latestVersion, readyFile, currentVersion,
                                        isRebuild, assetName);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading update silently", e);
                    if (tempFile.exists()) tempFile.delete();
                } finally {
                    isSilentDownloading.set(false);
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
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

            Uri apkUri = null;
            try {
                Class<?> fpClass = Class.forName("androidx.core.content.FileProvider");
                Method getUriMethod = null;
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

                if (getUriMethod != null) {
                    try {
                        apkUri = (Uri) getUriMethod.invoke(null, context, context.getPackageName() + ".fileprovider", apkFile);
                    } catch (Exception ex) {
                        apkUri = (Uri) getUriMethod.invoke(null, context, "com.google.android.youtube.fileprovider", apkFile);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error obtaining FileProvider URI via reflection", e);
            }

            if (apkUri == null) {
                apkUri = Uri.fromFile(apkFile);
            }

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                List<ResolveInfo> resolveInfoList =
                        context.getPackageManager().queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    String targetPackage = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(targetPackage, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (Exception ignored) {}

            context.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error triggering package installer", e);
            try {
                new AlertDialog.Builder(context, getDialogTheme(context))
                        .setTitle("Installation Failed")
                        .setMessage("Could not start package installer: " + e.getMessage())
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
}
