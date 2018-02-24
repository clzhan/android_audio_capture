package com.example.clzhan.jnidemo;

/**
 * Created by cl.zhan on 2018/2/11.
 */

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;

import java.lang.System;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.os.SystemClock;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;


import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

public class MyAudioRecorder {

    private static final boolean DEBUG = false;

    private static final String TAG = "WebRtcAudioRecord";

    // Default audio data format is PCM 16 bit per sample.
    // Guaranteed to be supported by all devices.
    private static final int BITS_PER_SAMPLE = 16;

    // Requested size of each recorded buffer provided to the client.
    private static final int CALLBACK_BUFFER_SIZE_MS = 10;

    // Average number of callbacks per second.
    private static final int BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS;

    // We ask for a native buffer size of BUFFER_SIZE_FACTOR * (minimum required
    // buffer size). The extra space is allocated to guard against glitches under
    // high load.
    private static final int BUFFER_SIZE_FACTOR = 2;

    // The AudioRecordJavaThread is allowed to wait for successful call to join()
    // but the wait times out afther this amount of time.
    private static final long AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS = 2000;

    private static final int DEFAULT_AUDIO_SOURCE = getDefaultAudioSource();
    private static int audioSource = DEFAULT_AUDIO_SOURCE;

    // private final long nativeAudioRecord;

    private File mAudioFile = null;

    private ByteBuffer byteBuffer;

    private AudioRecord audioRecord = null;
    private AudioRecordThread audioThread = null;

    private static volatile boolean microphoneMute = false;
    private byte[] emptyBytes;

    // Audio recording error handler functions.
    public enum AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION,
        AUDIO_RECORD_START_STATE_MISMATCH,
    }


    private class AudioRecordThread extends Thread {
        private volatile boolean keepAlive = true;

        public AudioRecordThread(String name) {
            super(name);
        }

        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            Log.d(TAG, "AdioRecordThread" + getThreadInfo());
            assertTrue(audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);

            // 开通输出流到指定的文件
            try {
                DataOutputStream dos = new DataOutputStream(
                        new BufferedOutputStream(new FileOutputStream(mAudioFile)));


                long lastTime = System.nanoTime();

                while (keepAlive) {
                    int bytesRead = audioRecord.read(byteBuffer, byteBuffer.capacity());
                    if (bytesRead == byteBuffer.capacity()) {
                        if (microphoneMute) {
                            byteBuffer.clear();
                            byteBuffer.put(emptyBytes);
                        }
                        // It's possible we've been shut down during the read, and stopRecording() tried and
                        // failed to join this thread. To be a bit safer, try to avoid calling any native methods
                        // in case they've been unregistered after stopRecording() returned.
                        if (keepAlive) {
                            //nativeDataIsRecorded(bytesRead, nativeAudioRecord);
                            Log.e(TAG, "-------------------data-------");
                            // 循环将buffer中的音频数据写入到OutputStream中
                            for (int i = 0; i < bytesRead; i++) {
                                //dos.writeShort(byteBuffer.getShort(i));
                                dos.writeByte(byteBuffer.get(i));
                            }
                        }
                    } else {
                        String errorMessage = "AudioRecord.read failed: " + bytesRead;
                        Log.e(TAG, errorMessage);
                        if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            keepAlive = false;
                            reportWebRtcAudioRecordError(errorMessage);
                        }
                    }
                    if (DEBUG) {
                        long nowTime = System.nanoTime();
                        long durationInMs = TimeUnit.NANOSECONDS.toMillis((nowTime - lastTime));
                        lastTime = nowTime;
                        Log.d(TAG, "bytesRead[" + durationInMs + "] " + bytesRead);
                    }
                }

                Log.i("slack", "::" + mAudioFile.length());
                dos.close();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            try {
                if (audioRecord != null) {
                    audioRecord.stop();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioRecord.stop failed: " + e.getMessage());
            }
        }

        // Stops the inner thread loop and also calls AudioRecord.stop().
        // Does not block the calling thread.
        public void stopThread() {
            Log.d(TAG, "stopThread");
            keepAlive = false;
        }
    }

//    MyAudioRecorder(long nativeAudioRecord) {
//        Log.d(TAG, "ctor" + getThreadInfo());
//        this.nativeAudioRecord = nativeAudioRecord;
//
//    }

    //@SuppressWarnings("unused")
    public int InitRecording() {
        int sampleRate = 16000;
        int channels = 1;
        //AudioFormat.CHANNEL_IN_STEREO;


        Log.d(TAG, "initRecording(sampleRate=" + sampleRate + ", channels=" + channels + ")");
        if (audioRecord != null) {
            reportWebRtcAudioRecordInitError("InitRecording called twice without StopRecording.");
            return -1;
        }
        final int bytesPerFrame = channels * (BITS_PER_SAMPLE / 8);
        final int framesPerBuffer = sampleRate / BUFFERS_PER_SECOND;
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer);
        Log.d(TAG, "byteBuffer.capacity: " + byteBuffer.capacity());
        emptyBytes = new byte[byteBuffer.capacity()];
        // Rather than passing the ByteBuffer with every callback (requiring
        // the potentially expensive GetDirectBufferAddress) we simply have the
        // the native class cache the address to the memory once.
        //nativeCacheDirectBufferAddress(byteBuffer, nativeAudioRecord);

        // Get the minimum buffer size required for the successful creation of
        // an AudioRecord object, in byte units.
        // Note that this size doesn't guarantee a smooth recording under load.
        final int channelConfig = channelCountToConfiguration(channels);
        int minBufferSize =
                AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            reportWebRtcAudioRecordInitError("AudioRecord.getMinBufferSize failed: " + minBufferSize);
            return -1;
        }
        Log.d(TAG, "AudioRecord.getMinBufferSize: " + minBufferSize);

        // Use a larger buffer size than the minimum required when creating the
        // AudioRecord instance to ensure smooth recording under load. It has been
        // verified that it does not increase the actual recording latency.
        int bufferSizeInBytes = Math.max(BUFFER_SIZE_FACTOR * minBufferSize, byteBuffer.capacity());
        Log.d(TAG, "bufferSizeInBytes: " + bufferSizeInBytes);
        try {
            audioSource = AudioSource.MIC;
            audioRecord = new AudioRecord(audioSource, sampleRate, channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            reportWebRtcAudioRecordInitError("AudioRecord ctor error: " + e.getMessage());
            releaseAudioResources();
            return -1;
        }
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            reportWebRtcAudioRecordInitError("Failed to create a new AudioRecord instance");
            releaseAudioResources();
            return -1;
        }

        File fpath = new File(Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/clzhan");
        fpath.mkdirs();// 创建文件夹
        try {
            // 创建临时文件,注意这里的格式为.pcm
            mAudioFile = File.createTempFile("recording", ".pcm", fpath);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        logMainParameters();
        //logMainParametersExtended();
        return framesPerBuffer;
    }

    public boolean startRecording() {
        Log.d(TAG, "startRecording");
        //assertTrue(audioRecord != null);
        assertTrue(audioThread == null);
        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            reportWebRtcAudioRecordStartError(AudioRecordStartErrorCode.AUDIO_RECORD_START_EXCEPTION,
                    "AudioRecord.startRecording failed: " + e.getMessage());
            return false;
        }
        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            reportWebRtcAudioRecordStartError(
                    AudioRecordStartErrorCode.AUDIO_RECORD_START_STATE_MISMATCH,
                    "AudioRecord.startRecording failed - incorrect state :"
                            + audioRecord.getRecordingState());
            return false;
        }
        audioThread = new AudioRecordThread("AudioRecordJavaThread");
        audioThread.start();
        return true;
    }

    public boolean stopRecording() {
        Log.d(TAG, "stopRecording");
        assertTrue(audioThread != null);
        audioThread.stopThread();
        if (!joinUninterruptibly(audioThread, AUDIO_RECORD_THREAD_JOIN_TIMEOUT_MS)) {
            Log.e(TAG, "Join of AudioRecordJavaThread timed out");
        }
        audioThread = null;

        releaseAudioResources();
        return true;
    }

    private void logMainParameters() {
        Log.e(TAG, "AudioRecord: "
                + "session ID: " + audioRecord.getAudioSessionId() + ", "
                + "channels: " + audioRecord.getChannelCount() + ", "
                + "sample rate: " + audioRecord.getSampleRate());
    }

    @TargetApi(23)


    // Helper method which throws an exception  when an assertion has failed.
    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }

    private int channelCountToConfiguration(int channels) {
        return (channels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
    }

    //private native void nativeCacheDirectBufferAddress(ByteBuffer byteBuffer, long nativeAudioRecord);

    // private native void nativeDataIsRecorded(int bytes, long nativeAudioRecord);

    @SuppressWarnings("NoSynchronizedMethodCheck")
    public static synchronized void setAudioSource(int source) {
        Log.w(TAG, "Audio source is changed from: " + audioSource
                + " to " + source);
        audioSource = source;
    }

    private static int getDefaultAudioSource() {
        return AudioSource.VOICE_COMMUNICATION;
    }

    // Sets all recorded samples to zero if |mute| is true, i.e., ensures that
    // the microphone is muted.
    public static void setMicrophoneMute(boolean mute) {
        //Logging.w(TAG, "setMicrophoneMute(" + mute + ")");
        microphoneMute = mute;
    }

    // Releases the native AudioRecord resources.
    private void releaseAudioResources() {
        DoLog("releaseAudioResources");
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void reportWebRtcAudioRecordInitError(String errorMessage) {
        DoLog("Init recording error: " + errorMessage);

    }

    private void reportWebRtcAudioRecordStartError(
            AudioRecordStartErrorCode errorCode, String errorMessage) {
        DoLog("Start recording error: " + errorCode + ". " + errorMessage);

    }

    private void reportWebRtcAudioRecordError(String errorMessage) {
        DoLog("Run-time recording error: " + errorMessage);

    }


    // Helper method for building a string of thread information.
    public static String getThreadInfo() {
        return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
                + "]";
    }

    private void DoLog(String msg) {
        Log.d(TAG, msg);
    }

    private void DoLogErr(String msg) {
        Log.e(TAG, msg);
    }

    public static boolean joinUninterruptibly(Thread thread, long timeoutMs) {
        long startTimeMs = SystemClock.elapsedRealtime();
        long timeRemainingMs = timeoutMs;
        boolean wasInterrupted = false;
        while (timeRemainingMs > 0) {
            try {
                thread.join(timeRemainingMs);
                break;
            } catch (InterruptedException e) {
                wasInterrupted = true;
                timeRemainingMs = timeoutMs - (SystemClock.elapsedRealtime() - startTimeMs);
            }
        }
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            return false;
        }
        return true;
    }


}
