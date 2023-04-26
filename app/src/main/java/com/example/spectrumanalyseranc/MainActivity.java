package com.example.spectrumanalyseranc;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import com.androidplot.xy.XYPlot;

public class MainActivity extends AppCompatActivity {
    private XYPlot spectrumPlot,ancPlot;
    private static final String TAG = "MainActivity";

    int audioSource,sampleRateInHz,channelConfig,audioFormat,bufferSizeInBytes;
    int streamType,sampleRateOutHz,channelConfigOut,audioFormatOut,bufferSizeOutBytes;

    boolean parameterUpdatedFlag = false,continueStreamMode = true,outputSoundEnable = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        spectrumPlot = findViewById(R.id.spectrum_plot);
        ancPlot = findViewById(R.id.ANC_plot);
        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!parameterUpdatedFlag) {
                    //set up audio source parameter
                    audioSource = MediaRecorder.AudioSource.DEFAULT;
                    sampleRateInHz = 44100;
                    channelConfig = AudioFormat.CHANNEL_IN_MONO;
                    audioFormat = AudioFormat.ENCODING_PCM_16BIT;
//                  int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
                    bufferSizeInBytes = 16384;
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    AudioRecord audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);

                    //set up audio playback parameter
                    streamType = AudioManager.STREAM_MUSIC;
                    sampleRateOutHz = 44100;
                    channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
                    audioFormatOut = AudioFormat.ENCODING_PCM_16BIT;
//                  int bufferSizeOutBytes = AudioTrack.getMinBufferSize(sampleRateOutHz, channelConfigOut, audioFormatOut);
                    bufferSizeOutBytes = 16384;
                    AudioTrack audioTrack = new AudioTrack(streamType, sampleRateOutHz, channelConfigOut, audioFormatOut, bufferSizeOutBytes, AudioTrack.MODE_STREAM);

                    // Start recording and playing audio
                    byte[] audioData = new byte[bufferSizeOutBytes];

                    audioRecord.startRecording();
                    audioTrack.play();
                    while (continueStreamMode) {
                        int numBytesRead = audioRecord.read(audioData, 0, bufferSizeInBytes);
                        Log.d(TAG, "read byte: " + numBytesRead);
                        double[] audioSamples = new double[audioData.length / 2];
                        for (int i = 0; i < audioSamples.length; i++) {
                            int sample = (audioData[2 * i + 1] << 8) | (audioData[2 * i] & 0xff);
                            audioSamples[i] = sample / 32767.0; // normalize to [-1, 1]
                        }
                        Complex[] complexSamples = new Complex[audioSamples.length];
                        for (int i = 0; i < audioSamples.length; i++) {
                            complexSamples[i] = new Complex(audioSamples[i], 0);
                        }
                        fft(complexSamples, false);
                        highPassFilter(complexSamples, 1500, 44100);
                        fft(complexSamples, true);
                        if(outputSoundEnable) {
                            byte[] filteredBytes = new byte[2 * complexSamples.length];
                            for (int i = 0; i < complexSamples.length; i++) {
                                short sample = (short) (complexSamples[i].re * 32767.0);
                                filteredBytes[2 * i] = (byte) (sample & 0xff);
                                filteredBytes[2 * i + 1] = (byte) (sample >> 8);
                            }
                            // Write audio data to audio track buffer
                            audioTrack.write(filteredBytes, 0, numBytesRead);
                        }
                    }

                }
            }
        }).start();
    }

    static void highPassFilter(Complex[] frequencyDomainSignal,int cutoffFrequency,int sampleRate){
        int numSamples = frequencyDomainSignal.length;
        int numFrequencies = numSamples / 2 + 1;
        int cutoffIndex = cutoffFrequency * numSamples / sampleRate;
        for (int i = cutoffIndex; i < numFrequencies; i++) {
            frequencyDomainSignal[i] = Complex.ZERO;
            frequencyDomainSignal[numSamples - i] = Complex.ZERO;
        }
    }
    static class Complex {
        double re, im;
        public static final Complex ZERO = new Complex(0, 0);
        Complex(double x, double y) { re = x; im = y; }
        Complex() { this(0, 0); }
        Complex add(Complex b) { return new Complex(re+b.re, im+b.im); }
        Complex sub(Complex b) { return new Complex(re-b.re, im-b.im); }
        Complex mul(Complex b) { return new Complex(re*b.re - im*b.im, re*b.im + im*b.re); }
        Complex div(double x) { return new Complex(re/x, im/x); }
    }
    static final double PI = Math.acos(-1);
    static void fft(Complex[] a, boolean invert) {
        int n = a.length;
        if (n == 1)
            return;

        Complex[] a0 = new Complex[n / 2];
        Complex[] a1 = new Complex[n / 2];
        for (int i = 0; 2 * i < n; i++) {
            a0[i] = a[2*i];
            a1[i] = a[2*i+1];
        }
        fft(a0, invert);
        fft(a1, invert);

        double ang = 2 * PI / n * (invert ? -1 : 1);
        Complex w = new Complex(1, 0);
        Complex wn = new Complex(Math.cos(ang), Math.sin(ang));
        for (int i = 0; 2 * i < n; i++) {
            a[i] = a0[i].add(w.mul(a1[i]));
            a[i + n/2] = a0[i].sub(w.mul(a1[i]));
            if (invert) {
                a[i] = a[i].div(2);
                a[i + n/2] = a[i + n/2].div(2);
            }
            w = w.mul(wn);
        }
    }
}