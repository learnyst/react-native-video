package com.brentvatne.offlinelicense;

import com.google.android.exoplayer2.C;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import android.util.Base64;

public class PsshUtils {
    /*code taken from https://github.com/HbbTV-Association/ReferenceApplication/blob/master/tools/java/src/org/hbbtv/refapp/DashDRM.java*/
    public static final String SYSID_WIDEVINE = "EDEF8BA979D64ACEA3C827DCD51D21ED";

    public static String createWidevinePsshBox(String kid) {
        WidevineCencHeaderProto.WidevineCencHeader.Builder psshBuilder=WidevineCencHeaderProto.WidevineCencHeader.newBuilder();
        psshBuilder.setAlgorithm( WidevineCencHeaderProto.WidevineCencHeader.Algorithm.valueOf("AESCTR") );
        psshBuilder.addKeyId( ByteString.copyFrom(hexToBytes(kid)) );
        //if (!provider.isEmpty())  psshBuilder.setProvider(provider); // intertrust, usp-cenc, widevine_test, whatever, ..
        //if (!contentId.isEmpty()) psshBuilder.setContentId( ByteString.copyFrom(contentId,"ISO-8859-1") );
        WidevineCencHeaderProto.WidevineCencHeader psshObj = psshBuilder.build();

        byte[] pssh=psshObj.toByteArray(); // pssh payload
        byte[] psshBox = PsshAtomUtil.buildPsshAtom(C.WIDEVINE_UUID, pssh);

        return Base64.encodeToString(psshBox, Base64.DEFAULT);
    }

    public static String getContentIdFromWidevinePsshBox(byte[] psshBox) {
        try {
            byte[] wvPsshData = PsshAtomUtil.parseSchemeSpecificData(psshBox, C.WIDEVINE_UUID);
            WidevineCencHeaderProto.WidevineCencHeader psshObj = WidevineCencHeaderProto.WidevineCencHeader.parseFrom(wvPsshData);
            System.out.println("contentid " + psshObj.getKeyId(0));
            System.out.println("contentid " + psshObj.getKeyId(0).toString());
            System.out.println("contentid " + psshObj.getKeyId(0).toStringUtf8());
            byte[] bKeyId = psshObj.getKeyId(0).toByteArray();
            return bytesToHex(bKeyId);
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert hexstring to byte array
     * @param s hex string "656667" is decoded to ABC bytes
     * @return byte array or NULL if string is null
     */
    public static byte[] hexToBytes(String s) {
        if (s==null || s.isEmpty())
            return null;
        if (s.startsWith("0x")) s=s.substring(2);
        byte[] bytes = new byte[s.length()/2];
        int count = 0;
        for(int i=0; i < s.length(); i+=2) {
            byte b = (byte)Integer.parseInt(s.substring(i, i+2), 16);
            bytes[count++] = b;
        }
        return bytes;
    }

    /**
     * Convert bytes to hexstring.
     * @param bytes
     * @return
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder buffer = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            String s = Integer.toHexString(bytes[i] & 0xff).toUpperCase(Locale.US);
            if (s.length() < 2) buffer.append("0");
            buffer.append(s);
        }
        return buffer.toString();
    }
}

