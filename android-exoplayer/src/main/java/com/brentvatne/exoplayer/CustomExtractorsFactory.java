package com.brentvatne.exoplayer;


import android.util.Base64;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.amr.AmrExtractor;
import com.google.android.exoplayer2.extractor.flv.FlvExtractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.extractor.ogg.OggExtractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.Ac4Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.wav.WavExtractor;
import com.google.android.exoplayer2.util.TimestampAdjuster;

import java.util.ArrayList;

import androidx.annotation.Nullable;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 *   <li>MP4, including M4A ({@link Mp4Extractor})
 *   <li>fMP4 ({@link FragmentedMp4Extractor})
 *   <li>Matroska and WebM ({@link MatroskaExtractor})
 *   <li>Ogg Vorbis/FLAC ({@link OggExtractor}
 *   <li>MP3 ({@link Mp3Extractor})
 *   <li>AAC ({@link AdtsExtractor})
 *   <li>MPEG TS ({@link TsExtractor})
 *   <li>MPEG PS ({@link PsExtractor})
 *   <li>FLV ({@link FlvExtractor})
 *   <li>WAV ({@link WavExtractor})
 *   <li>AC3 ({@link Ac3Extractor})
 *   <li>AC4 ({@link Ac4Extractor})
 *   <li>AMR ({@link AmrExtractor})
 *   <li>FLAC (only available if the FLAC extension is built and included)
 * </ul>
 */
//class customFragmentedMp4Extractor extends FragmentedMp4Extractor implements SeekMap {
//    public customFragmentedMp4Extractor(
//            @Flags int flags,
//            @Nullable TimestampAdjuster timestampAdjuster,
//            @Nullable Track sideloadedTrack,
//            @Nullable DrmInitData sideloadedDrmInitData) {
//        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData);
//    }
//
//    @Override
//    public boolean isSeekable() {
//        return true;
//    }
//
//    @Override
//    public long getDurationUs() {
//        return durationUs;
//    }
//}

public final class CustomExtractorsFactory implements ExtractorsFactory {
    private String pssh = null;
    private String licenseServerUrl = null;
    private String mimeType = null;

    public CustomExtractorsFactory(String pssh, String licenseServerUrl, String mimeType) {
        this.pssh = pssh;
        this.licenseServerUrl = licenseServerUrl;
        this.mimeType = mimeType;
        return;
    }

    @Override
    public synchronized Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[1];

        ArrayList<DrmInitData.SchemeData> schemeDatas = new ArrayList<>();
        byte[] psshData = Base64.decode(pssh, Base64.DEFAULT);
        schemeDatas.add(new DrmInitData.SchemeData(C.WIDEVINE_UUID, licenseServerUrl, mimeType, psshData, false));
        DrmInitData drmData = new DrmInitData(schemeDatas);
        extractors[0] = new FragmentedMp4Extractor(0, null, null, drmData);
        return extractors;
    }

}