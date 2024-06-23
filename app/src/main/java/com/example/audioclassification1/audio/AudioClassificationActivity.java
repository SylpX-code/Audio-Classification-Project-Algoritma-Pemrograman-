package com.example.audioclassification1.audio;

import android.content.Intent;
import android.graphics.Color;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.Nullable;

import com.example.audioclassification1.R;
import com.example.audioclassification1.helpers.MLAudioHelperActivity;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AudioClassificationActivity extends MLAudioHelperActivity {

    String modelPath = "model_unquant (1).tflite";
    float probabilityThreshold = 0.3f;
    AudioClassifier classifier;
    private TensorAudio tensor;
    private AudioRecord record;
    private TimerTask timerTask;
    private VideoView videoView;
    private MediaPlayer mediaPlayer;
    private TextView outputTextView;
    private TextView specsTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_helper);

        videoView = findViewById(R.id.videoView);
        outputTextView = findViewById(R.id.textViewOutput);
        specsTextView = findViewById(R.id.textViewSpec);

        mediaPlayer = MediaPlayer.create(this, R.raw.audiovisualiser);
        mediaPlayer.setLooping(true);

        // Set the URI and prepare the VideoView
        videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.audiovisualiser));

        // Set listeners for video preparation and completion
        videoView.setOnPreparedListener(mp -> {
            videoView.seekTo(1); // Move to the first frame
            mp.setLooping(true);
        });

        // Set background and text color for the output text view
        outputTextView.setBackgroundColor(Color.WHITE);
        outputTextView.setTextColor(Color.BLACK);
    }

    public void onStartRecording(View view) {
        super.onStartRecording(view);

        mediaPlayer.start();
        videoView.start();

        try {
            classifier = AudioClassifier.createFromFile(this, modelPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        tensor = classifier.createInputTensorAudio();
        TensorAudio.TensorAudioFormat format = classifier.getRequiredTensorAudioFormat();
        String specs = "Number of channels: " + format.getChannels() + "\n" + "Sample Rate: " + format.getSampleRate();
        specsTextView.setText(specs);

        record = classifier.createAudioRecord();
        record.startRecording();

        timerTask = new TimerTask() {
            @Override
            public void run() {
                int numberOfSamples = tensor.load(record);
                List<Classifications> output = classifier.classify(tensor);

                List<Category> finalOutput = new ArrayList<>();
                for (Classifications classifications : output) {
                    for (Category category : classifications.getCategories()) {
                        if (category.getScore() > probabilityThreshold) {
                            finalOutput.add(category);
                        }
                    }
                }

                Collections.sort(finalOutput, (o1, o2) -> Float.compare(o2.getScore(), o1.getScore()));

                DecimalFormat decimalFormat = new DecimalFormat("0.00%");
                DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
                symbols.setPercent('%');
                decimalFormat.setDecimalFormatSymbols(symbols);

                StringBuilder outputStr = new StringBuilder();
                for (Category category : finalOutput) {
                    // Use a regular expression to remove leading numbers and spaces from labels
                    String sanitizedLabel = category.getLabel().replaceFirst("^\\d+\\s*", "");
                    outputStr.append(sanitizedLabel).append(": ").append(decimalFormat.format(category.getScore())).append("\n");
                }

                runOnUiThread(() -> {
                    outputTextView.setBackgroundColor(Color.WHITE);
                    outputTextView.setTextColor(Color.BLACK);
                    if (finalOutput.isEmpty()) {
                        outputTextView.setText("Could not classify");
                    } else {
                        outputTextView.setText(outputStr.toString());
                    }
                });
            }
        };

        new Timer().scheduleAtFixedRate(timerTask, 1, 500);
    }

    public void onStopRecording(View view) {
        super.onStopRecording(view);

        timerTask.cancel();
        record.stop();

        mediaPlayer.pause();
        videoView.pause();
    }

    // Metode untuk menangani klik tombol
    public void goHome(View view) {
        // Hentikan rekaman jika masih berjalan
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (record != null) {
            record.stop();
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        if (videoView != null && videoView.isPlaying()) {
            videoView.stopPlayback();
        }

        // Buat Intent untuk berpindah ke MainActivity (atau Activity tujuan Anda)
        Intent intent = new Intent(this, Home.class);
        startActivity(intent);
    }
}
