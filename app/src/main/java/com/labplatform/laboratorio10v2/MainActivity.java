package com.labplatform.laboratorio10v2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
    public static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
    public static final int BUFFER_SIZE_PLAYING = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);


    AudioRecord audioRecord;
    AudioTrack audioTrack;

    private Thread recordingThread;
    private Thread playingThread;


    Button recordAudioRecord;
    Button playAudioTrack;

    boolean isRecordingAudio = false;
    boolean isPlayingAudio = false;

    String fileNameMedia;
    String fileNameAudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // find views
        playAudioTrack = findViewById(R.id.play_audiotrack);
        recordAudioRecord = findViewById(R.id.record_audiorecord);

        fileNameAudio = getFilesDir().getPath() + "/testfile" + ".pcm";

        File fileAudio = new File(fileNameAudio);
        if (fileAudio.exists()) { // create empty files if needed
            try {
                fileAudio.createNewFile();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { // get permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
        }

        setListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // handle user response to permission request
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permisos obtenidos", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(this, "Permisos denegados", Toast.LENGTH_LONG).show();
            playAudioTrack.setEnabled(false);
            recordAudioRecord.setEnabled(false);
        }
    }

    private void setListeners() { // start or stop recording and playback depending on state

        recordAudioRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecordingAudio) {
                    startRecording(recordAudioRecord.getId());
                }
                else {
                    stopRecording(recordAudioRecord.getId());
                }
                setButtonText();
            }
        });

        playAudioTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlayingAudio) {
                    startPlaying(playAudioTrack.getId());
                }
                else {
                    stopPlaying(playAudioTrack.getId());
                }
                setButtonText();
            }
        });

    }

    private void startRecording(int id) {

            if (audioRecord == null) { // safety check
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT, BUFFER_SIZE_RECORDING);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
                    Log.e(TAG, "error initializing AudioRecord");
                    Toast.makeText(this, "Couldn't initialize AudioRecord, check configuration", Toast.LENGTH_SHORT).show();
                    return;
                }

                audioRecord.startRecording();
                Log.d(TAG, "recording started with AudioRecord");

                isRecordingAudio = true;

                recordingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        writeAudioDataToFile();
                    }
                });
                recordingThread.start();
            }

    }

    private void stopRecording(int id) {
        isRecordingAudio = false; // triggers recordingThread to exit while loop
    }

    private void startPlaying(int id) {

        if (audioTrack == null) {
            audioTrack = new AudioTrack(
                    new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build(),
                    new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    BUFFER_SIZE_PLAYING,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Toast.makeText(this, "Couldn't initialize AudioTrack, check configuration", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "error initializing AudioTrack");
                return;
            }

            audioTrack.play();
            Log.d(TAG, "playback started with AudioTrack");

            isPlayingAudio = true;

            playingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readAudioDataFromFile();
                }
            });
            playingThread.start();
        }
    }

    private void stopPlaying(int id) {
        isPlayingAudio = false; // will trigger playingThread to exit while loop
    }

    private void writeAudioDataToFile() { // called inside Runnable of recordingThread

        byte[] data = new byte[BUFFER_SIZE_RECORDING/2]; // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size

        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(fileNameAudio);
        } catch (FileNotFoundException e) {
            // handle error
            Toast.makeText(this, "Couldn't find file to write to", Toast.LENGTH_SHORT).show();
            return;
        }

        while (isRecordingAudio) {
            int read = audioRecord.read(data, 0, data.length);
            try {
                outputStream.write(data, 0, read);
            }
            catch (IOException e) {
                Toast.makeText(this, "Couldn't write to file while recording", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }

        try { // clean up file writing operations
            outputStream.flush();
            outputStream.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        audioRecord.stop();
        audioRecord.release();

        audioRecord = null;
        recordingThread = null;
    }

    private void readAudioDataFromFile() { // called inside Runnable of playingThread

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(fileNameAudio);
        }
        catch (IOException e) {
            Toast.makeText(this, "Couldn't open file input stream, IOException", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return;
        }
        byte[] data = new byte[BUFFER_SIZE_PLAYING/2];
        int i = 0;

        while (isPlayingAudio && (i != -1)) {
            try {
                i = fileInputStream.read(data);
                audioTrack.write(data, 0, i);
            }
            catch (IOException e) {
                Toast.makeText(this, "Couldn't read from file while playing audio, IOException", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            }
        }
        try { // finish file operations
            fileInputStream.close();
        }
        catch (IOException e) {
            Log.e(TAG, "Could not close file input stream " + e.toString());
            e.printStackTrace();
            return;
        }

        // clean up resources
        isPlayingAudio = false;
        setButtonText();
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
        playingThread = null;

    }

    private void setButtonText() { // UI updates for button text

        if (isRecordingAudio) {
            recordAudioRecord.setText(R.string.record_button_stop);
        }
        else {
            recordAudioRecord.setText(R.string.record_audiorecord);
        }

        if (isPlayingAudio) {
            playAudioTrack.setText(R.string.play_button_stop);
        }
        else {
            playAudioTrack.setText(R.string.play_audiotrack);
        }
    }

}