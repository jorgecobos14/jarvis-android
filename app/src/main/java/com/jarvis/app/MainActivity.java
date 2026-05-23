package com.jarvis.app;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
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
import okhttp3.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URL = "wss://web-production-9d13b.up.railway.app/ws/";
    private WebSocket webSocket;
    private OkHttpClient client;
    private TextView chatView;
    private EditText inputField;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String deviceId;

    private static final String[] FILE_KEYWORDS = {
        "archivos", "carpetas", "archivo", "carpeta", "tengo", "storage",
        "descargas", "documentos", "fotos", "música", "musica", "videos"
    };

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
            public void onOpen(WebSocket ws, Response response) {
                try {
                    String files = getFileList(
                        Environment.getExternalStorageDirectory().getAbsolutePath()
                    );
                    JSONObject ctx = new JSONObject();
                    ctx.put("text", "[CONTEXTO INICIAL DEL DISPOSITIVO]\nArchivos y carpetas:\n" + files);
                    ws.send(ctx.toString());
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject obj = new JSONObject(text);
                    String reply = obj.getString("reply");
                    String clean = removeMarkdown(reply);
                    runOnUiThread(() -> appendChat("Jarvis: " + clean));
                } catch (Exception e) { e.printStackTrace(); }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                runOnUiThread(() -> appendChat("[Reconectando...]"));
                reconnect();
            }
        });
    }

    private String removeMarkdown(String text) {
        return text
            .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
            .replaceAll("\\*(.*?)\\*", "$1")
            .replaceAll("#{1,6}\\s", "")
            .replaceAll("`(.*?)`", "$1");
    }

    private boolean hasFileKeyword(String text) {
        String lower = text.toLowerCase();
        for (String kw : FILE_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String getSpecificPath(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("descarga")) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        } else if (lower.contains("foto") || lower.contains("dcim") || lower.contains("camara") || lower.contains("cámara")) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
        } else if (lower.contains("documento")) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        } else if (lower.contains("música") || lower.contains("musica")) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();
        } else if (lower.contains("video")) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        } else {
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        }
    }

    private String getFileList(String path) {
        try {
            JSONArray arr = new JSONArray();
            listRecursive(new File(path), arr, 0);
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private void listRecursive(File dir, JSONArray arr, int depth) {
        if (depth > 4) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                JSONObject fo = new JSONObject();
                fo.put("nombre", f.getName());
                fo.put("ruta", f.getAbsolutePath());
                fo.put("tipo", f.isDirectory() ? "carpeta" : "archivo");
                fo.put("tamaño_kb", f.length() / 1024);
                arr.put(fo);
                if (f.isDirectory()) {
                    listRecursive(f, arr, depth + 1);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void sendMessage(String text) {
        appendChat("Tú: " + text);
        try {
            String finalText = text;
            if (hasFileKeyword(text)) {
                String path = getSpecificPath(text);
                String files = getFileList(path);
                finalText = text + "\n[Archivos en " + path + ": " + files + "]";
            }
            JSONObject obj = new JSONObject();
            obj.put("text", finalText);
            webSocket.send(obj.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void appendChat(String msg) {
        chatView.append(msg + "\n\n");
        ScrollView sv = findViewById(R.id.scrollView);
        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void reconnect() {
        new android.os.Handler().postDelayed(this::connectWebSocket, 3000);
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendMessage(matches.get(0));
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

