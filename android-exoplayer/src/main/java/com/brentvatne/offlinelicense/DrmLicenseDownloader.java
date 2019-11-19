package com.brentvatne.offlinelicense;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.util.Pair;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.source.dash.DashUtil;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;


public class DrmLicenseDownloader extends ReactContextBaseJavaModule
{
    private ReactApplicationContext reactContext = null;

    private int LIC_DOWNLOAD_SUCCESS = 0;
    private int LIC_DOWNLOAD_FAILED = 1;

    private int FETCH_TYPE_CACHED_LIC = 0;
    private int FETCH_TYPE_NEW_LIC = 1;
    private int FETCH_TYPE_INVALID = 100;

    private int ERR_INVALID_INPUT_PARAMETERS = 101;
    private int ERR_NO_SCHEMEDATA_IN_MANIFEST = 102;
    private int ERR_NO_WIDEVINE_SCHEME_IN_MANIFEST = 103;
    private int ERR_INVALID_OFFLINE_LIC_KEYSET_ID = 104;
    private int ERR_SAVING_LICENSE_FAILED = 105;
    private int ERR_DOWNLOAD_LICENSE_EXCEPTION = 106;

    public DrmLicenseDownloader(ReactApplicationContext reactContext)
    {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName()
    {
        return "DrmLicenseDownloader";
    }

    @ReactMethod void isOfflineLicenseAvailable(final ReadableMap inputs, Promise promise)
    {
        try {
            JSONObject params = convertMapToJson(inputs);
            String contentId = params.getString("contentId");
            String offlineKeySetIdStr = getOfflineKeySetIdStr(contentId);
            if (offlineKeySetIdStr == null) {
                promise.resolve(null);
                return;
            }
            promise.resolve(offlineKeySetIdStr);
        } catch(Exception e) {
            e.printStackTrace();
            promise.resolve(null);
        }
    }

    @ReactMethod
    public void downloadLicense(final ReadableMap inputs, Promise promise)
    {
        Thread thread = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    downloadOfflineLicense(convertMapToJson(inputs), promise);
                }
                catch (JSONException e)
                {
                    e.printStackTrace();
                    promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                            FETCH_TYPE_INVALID,
                            ERR_INVALID_INPUT_PARAMETERS,
                            "Exception when parsing input params",
                            getStackTrace(e),
                            null,
                            null));
                }
            }
        });
        thread.start();
    }

    private void downloadOfflineLicense(JSONObject params, Promise promise)
    {
        String manifestUrl = null;
        String licenseServerUrl = null;
        String mimeType = null;
        String contentId = null;
        byte[] psshData = null;

        try
        {
            licenseServerUrl = params.getString("licenseServerUrl");

            if (!params.isNull("mimeType"))
            {
                mimeType = params.getString("mimeType");
            }

            if (!params.isNull("pssh"))
            {
                String pssh = params.getString("pssh");
                psshData = Base64.decode(pssh, Base64.DEFAULT);
            }

            if (!params.isNull("contentId"))
            {
                contentId = params.getString("contentId");
            }

            if (!params.isNull("manifestUrl"))
            {
                manifestUrl = params.getString("manifestUrl");
            }

            if (((psshData == null) && (manifestUrl == null))
                    || ((psshData != null) && (manifestUrl != null))
                    || ((psshData != null) && ((mimeType == null) || (contentId == null))))
            {
                promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                        FETCH_TYPE_INVALID,
                        ERR_INVALID_INPUT_PARAMETERS,
                        "Either pssh+mimetype+contentId or manifestUrl should be present",
                        null,
                        contentId,
                        null));
                return;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                    FETCH_TYPE_INVALID,
                    ERR_INVALID_INPUT_PARAMETERS,
                    "Exception when parsing input params",
                    getStackTrace(e),
                    contentId,
                    null));
        }

        if (contentId != null)
        {
            String offlineKeySetIdStr = getOfflineKeySetIdStr(contentId);
            if (null != offlineKeySetIdStr)
            {
                promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_SUCCESS,
                        FETCH_TYPE_CACHED_LIC,
                        0,
                        null,
                        null,
                        contentId,
                        offlineKeySetIdStr));
                return;
            }
        }

        try
        {
            DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory("ExoPlayer");
            DataSource dataSource = httpDataSourceFactory.createDataSource();

            if (manifestUrl != null)
            {
                DashManifest dashManifest = DashUtil.loadManifest(dataSource, Uri.parse(manifestUrl));
                DrmInitData drmInitData = DashUtil.loadDrmInitData(dataSource, dashManifest.getPeriod(0));

                if (drmInitData.schemeDataCount <= 0)
                {
                    promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                            FETCH_TYPE_INVALID,
                            ERR_NO_SCHEMEDATA_IN_MANIFEST,
                            "Scheme data not present in manifest",
                            null,
                            contentId,
                            null));
                    return;
                }

                int matchedSchemeDataIdx = -1;
                for (int i = 0; i < drmInitData.schemeDataCount; i++)
                {
                    DrmInitData.SchemeData sData = drmInitData.get(i);
                    if (sData.matches(C.WIDEVINE_UUID))
                    {
                        matchedSchemeDataIdx = i;
                        break;
                    }
                }

                if (matchedSchemeDataIdx == -1)
                {
                    promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                            FETCH_TYPE_INVALID,
                            ERR_NO_WIDEVINE_SCHEME_IN_MANIFEST,
                            "Widevine scheme not present in manifest",
                            null,
                            contentId,
                            null));
                    return;
                }

                DrmInitData.SchemeData widevineSchemeData = drmInitData.get(matchedSchemeDataIdx);

                //TODO: If contentId is null then get contentID from PSSH and then check whether license is already available
                // Also validate input content ID with extracted content ID and if mismatch return failure
                // Always set contentid here when it is null
                //contentID =

                psshData = widevineSchemeData.data;
                mimeType = widevineSchemeData.mimeType;
            }

            DrmInitData.SchemeData newSchemeData = new DrmInitData.SchemeData(C.WIDEVINE_UUID,
                    licenseServerUrl,
                    mimeType,
                    psshData,
                    false);
            ArrayList<DrmInitData.SchemeData> newSchemeDatas = new ArrayList<>();
            newSchemeDatas.add(newSchemeData);
            DrmInitData newDrmInitData = new DrmInitData(newSchemeDatas);

            OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper;
            offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(licenseServerUrl,
                    httpDataSourceFactory);

            byte[] offlineLicenseKeySetId = offlineLicenseHelper.downloadLicense(newDrmInitData);
            if ((offlineLicenseKeySetId == null) || (offlineLicenseKeySetId.length <= 0))
            {
                promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                        FETCH_TYPE_INVALID,
                        ERR_INVALID_OFFLINE_LIC_KEYSET_ID,
                        "offlineLicenseKeySetId is invalid",
                        null,
                        contentId,
                        null));
                return;
            }

            String offlineKeySetIdStr = saveLicense(offlineLicenseKeySetId, contentId);
            if (null != offlineKeySetIdStr)
            {
                promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_SUCCESS,
                        FETCH_TYPE_NEW_LIC,
                        0,
                        null,
                        null,
                        contentId,
                        offlineKeySetIdStr));
            }
            else
            {
                promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                        FETCH_TYPE_INVALID,
                        ERR_SAVING_LICENSE_FAILED,
                        "Saving license failed",
                        null,
                        contentId,
                        null));
            }
        }
        catch(Exception e)
        {
            promise.resolve(constructDownloadLicenseResult(LIC_DOWNLOAD_FAILED,
                    FETCH_TYPE_INVALID,
                    ERR_DOWNLOAD_LICENSE_EXCEPTION,
                    "Exception when downloading license",
                    getStackTrace(e),
                    contentId,
                    null));
        }

        return;
    }

    private String saveLicense(byte[] offlineLicenseKeySetId, String contentId)
    {
        try
        {
            String offlineLicenseKeySetIdStr = Base64.encodeToString(offlineLicenseKeySetId,
                    Base64.DEFAULT);
            SharedPreferences sharedPreferences = this.reactContext.getSharedPreferences("lstOfflineLicenses",
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(contentId);
            editor.putString(contentId, offlineLicenseKeySetIdStr);
            editor.commit();
            return offlineLicenseKeySetIdStr;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private String getOfflineKeySetIdStr(String contentId)
    {
        try
        {
            SharedPreferences sharedPreferences = this.reactContext.getSharedPreferences("lstOfflineLicenses",
                    Context.MODE_PRIVATE);
            String offlineLicenseKeySetIdStr = sharedPreferences.getString(contentId, null);
            if ((null == offlineLicenseKeySetIdStr)
                || (offlineLicenseKeySetIdStr.length() <= 0))
            {
                return null;
            }

            OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper;
            offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(null, null);
            byte[] offlineAssetKeyId = Base64.decode(offlineLicenseKeySetIdStr, Base64.DEFAULT);
            Pair<Long, Long> remainingSecPair = offlineLicenseHelper.getLicenseDurationRemainingSec(offlineAssetKeyId);

            if ((remainingSecPair.first <= 0) || (remainingSecPair.second <= 0)) {
                return null;
            }

            return offlineLicenseKeySetIdStr;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private WritableMap constructDownloadLicenseResult(int result,
                                                       int fetchType,
                                                       int errCode,
                                                       String failureReason,
                                                       String exceptionTrace,
                                                       String contentId,
                                                       String offlineKeySetIdStr)
    {
        WritableMap map = Arguments.createMap();

        map.putInt("result", result);
        map.putInt("fetchType", fetchType);
        map.putInt("errCode", errCode);
        if (failureReason != null)
        {
            map.putString("failureReason", failureReason);
        }

        if (exceptionTrace != null)
        {
            map.putString("exceptionTrace", exceptionTrace);
        }

        if (contentId != null)
        {
            map.putString("contentId", contentId);
        }

        if (offlineKeySetIdStr != null)
        {
            map.putString("offlineKeySetIdStr", offlineKeySetIdStr);
        }

        return map;
    }

    private JSONObject convertMapToJson(ReadableMap readableMap) throws JSONException
    {
        JSONObject object = new JSONObject();
        ReadableMapKeySetIterator iterator = readableMap.keySetIterator();

        while (iterator.hasNextKey())
        {
            String key = iterator.nextKey();
            switch (readableMap.getType(key))
            {
                case Null:
                    object.put(key, JSONObject.NULL);
                    break;
                case Boolean:
                    object.put(key, readableMap.getBoolean(key));
                    break;
                case Number:
                    object.put(key, readableMap.getDouble(key));
                    break;
                case String:
                    object.put(key, readableMap.getString(key));
                    break;
                case Map:
                    object.put(key, convertMapToJson(readableMap.getMap(key)));
                    break;
                case Array:
                    object.put(key, convertArrayToJson(readableMap.getArray(key)));
                    break;
            }
        }
        return object;
    }

    private JSONArray convertArrayToJson(ReadableArray readableArray) throws JSONException
    {
        JSONArray array = new JSONArray();
        for (int i = 0; i < readableArray.size(); i++)
        {
            switch (readableArray.getType(i))
            {
                case Null:
                    break;
                case Boolean:
                    array.put(readableArray.getBoolean(i));
                    break;
                case Number:
                    array.put(readableArray.getDouble(i));
                    break;
                case String:
                    array.put(readableArray.getString(i));
                    break;
                case Map:
                    array.put(convertMapToJson(readableArray.getMap(i)));
                    break;
                case Array:
                    array.put(convertArrayToJson(readableArray.getArray(i)));
                    break;
            }
        }
        return array;
    }

    private String getStackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
