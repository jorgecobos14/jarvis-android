package com.jarvis.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import okhttp3.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URL = "wss://web-production-9d13b.up.railway.app/ws/";
    private WebSocket webSocket;
    private OkHttpClient client;
    private TextView chatView;
    private EditText inputField;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceId = android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        chatView = findViewById(R.id.chatView);
        inputField = findViewById(R.id.inputField);
        Button sendBtn = findViewById(R.id.sendBtn);
        Button micBtn = findViewById(R.id.micBtn);

        requestPermissions();
        connectWebSocket();
        setupSpeechRecognizer();

        sendBtn.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                inputField.setText("");
            }
        });

        micBtn.setOnClickListener(v -> {
            if (isListening) stopListening();
            else startListening();
        });
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        };
        ActivityCompat.requestPermissions(this, perms, 1);
    }

    private void connectWebSocket() {
        client = new OkHttpClient();
        Request request = new Request.Builder()
            .url(SERVER_URL + deviceId)
            .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject obj = new JSONObject(text);
                    String reply = obj.getString("reply");
                    runOnUiThread(() -> appendChat("Jarvis: " + reply));
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                runOnUiThread(() -> appendChat("[Error de conexión, reintentando...]"));
                reconnect();
            }
        });
    }

    private void reconnect() {
        new android.os.Handler().postDelayed(this::connectWebSocket, 3000);
    }

    private void sendMessage(String text) {
        appendChat("Tú: " + text);
        try {
            JSONObject obj = new JSONObject();
            obj.put("text", text);
            webSocket.send(obj.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void appendChat(String msg) {
        chatView.append(msg + "\n\n");
        ScrollView sv = findViewById(R.id.scrollView);
        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    sendMessage(matches.get(0));
                }
                isListening = false;
            }
            @Override public void onError(int error) { isListening = false; }
            @Override public void onReadyForSpeech(Bundle p) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int t, Bundle b) {}
        });
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX");
        speechRecognizer.startListening(intent);
        isListening = true;
    }

    private void stopListening() {
        speechRecognizer.stopListening();
        isListening = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocket != null) webSocket.close(1000, null);
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}
