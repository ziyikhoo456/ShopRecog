package com.example.shoprecog;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalGetImage
public class MainActivity extends AppCompatActivity implements OnItemSelectedListener, TextToSpeech.OnInitListener, GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {
    private SharedPreferences sharedPreferences;
    private static final String STATE_SELECTED_MODEL = "selected_model";
    private String selectedModel = "Text Recognition Latin";
    private final String TEXT_RECOGNITION_LATIN = "Text Recognition Latin";
    private final String TEXT_RECOGNITION_CHINESE = "Text Recognition Chinese";
    private final String TEXT_RECOGNITION_DEVANAGARI = "Text Recognition Devanagari";
    private final String TEXT_RECOGNITION_JAPANESE = "Text Recognition Japanese";
    private final String TEXT_RECOGNITION_KOREAN = "Text Recognition Korean";
    private ContentGenerationHandler contentGenerationHandler;
    private Camera camera;
    private int tapCount, langIndex, shopIndex = -1;
    private GestureDetector gestureDetector;
    private boolean isPreviewFrozen, isListening = false;
    private String apiKey, selectedLanguage, shopLongitude = null, shopLatitude = null;
    private Handler TTSHandler, PlacesAPIHandler;
    private final long SPEAK_INTERVAL = 0; // Set the interval for checking and speaking in milliseconds
    private LinkedHashSet<String> uniqueTextSet, capturedShopSet, capturedPlaceIDSet;
    private static final String UTTERANCE_ID = "VOICE_OUT_UTTERANCE";
    private TextToSpeech matchingTTS, informTTS;
    private List<String> shopNameList, shopIDList, shopInfoList;
    @Nullable private ProcessCameraProvider cameraProvider;
    @Nullable private Preview previewUseCase;
    @Nullable private ImageAnalysis analysisUseCase;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private CameraSelector cameraSelector;
    private AlertDialog alertDialog;
    private Spinner modelSpinner, languageSpinner;
    private Locale locale;
    private SpeechRecognizer speechRecognizer;
    private CustomImageButton customImageButton;
    private AlertDialog.Builder builder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        selectedModel = sharedPreferences.getString("model", TEXT_RECOGNITION_LATIN);
        selectedLanguage = sharedPreferences.getString("language", "English");
        langIndex = sharedPreferences.getInt("position", 0);

        //Set the application language
        locale = loadLocale(langIndex);

        setContentView(R.layout.activity_main);

        modelSpinner = findViewById(R.id.modelSpinner);
        languageSpinner = findViewById(R.id.languageSpinner);

        setupModelSpinner();
        setupLanguageSpinner();

        //Initialize the chatbot
        contentGenerationHandler = new ContentGenerationHandler();

        // Initialize the GestureDetector
        gestureDetector = new GestureDetector(this, this);
        gestureDetector.setOnDoubleTapListener(this);

        tapCount = 0;
        isPreviewFrozen = false;

        // Initialize apiKey after setContentView
        apiKey = getString(R.string.API_KEY);

        shopNameList = getIntent().getStringArrayListExtra("shopNameList");
        shopIDList = getIntent().getStringArrayListExtra("shopIDList");
        shopInfoList = new ArrayList<>();

        TTSHandler = new Handler();
        PlacesAPIHandler = new Handler();

        uniqueTextSet = new LinkedHashSet<>();
        capturedShopSet = new LinkedHashSet<>();
        capturedPlaceIDSet = new LinkedHashSet<>();

        //capturedShopSet.add("Villa 21 Cafe");
        //capturedShopSet.add("Esperimento Caffe");

        //capturedPlaceIDSet.add("ChIJX27b5JDiyjERwsMH6HN1utU"); //Villa 21 Café
        //capturedPlaceIDSet.add("ChIJbXscm6njyjERIkRHfCAcw6k"); //Esperimento Caffe

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "No API key found.", Toast.LENGTH_LONG).show();
            return;
        }

        // Initialize TextToSpeech
        matchingTTS = new TextToSpeech(this, this);
        informTTS = new TextToSpeech(this,this);

        // Set up UtteranceProgressListener
        matchingTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // Speech started
            }

            @Override
            public void onDone(String utteranceId) {
                // Speech completed
                if (utteranceId.equals(UTTERANCE_ID)) {
                    // Remove the first item from the set after speaking
                    uniqueTextSet.remove(uniqueTextSet.iterator().next());

                    // Schedule the next run after SPEAK_INTERVAL
                    TTSHandler.postDelayed(checkAndSpeakRunnable, SPEAK_INTERVAL);
                }
            }

            @Override
            public void onError(String utteranceId) {
                // Speech error
            }
        });

        previewView = findViewById(R.id.previewView);

        if (previewView == null) {
            Log.e("Error", "previewView is null");
        }

        // Schedule the checkAndSpeakRunnable to run periodically
        TTSHandler.postDelayed(checkAndSpeakRunnable, SPEAK_INTERVAL);

        // Initialize speech recognizer
        checkSpeechRecognition();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognizer", "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                Log.d("SpeechRecognizer", "onRmsChanged: " + rmsdB);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                Log.d("SpeechRecognizer", "onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognizer", "onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                Log.e("SpeechRecognizer", "onError: " + error);
                if(error == 7) {
                    informTTS.speak(getString(R.string.emptySpeech), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0); // Get the first recognized text
                    Toast.makeText(MainActivity.this, "You said: " + spokenText, Toast.LENGTH_SHORT).show();

                    if(shopIndex >= 0 && shopIndex < shopInfoList.size()){

                        String response = shopInfoList.get(shopIndex);
                        String formatted = formatJson(response);
                        formatted += "\n\n" + spokenText;

                        contentGenerationHandler.generateContent(formatted, new ContentGenerationHandler.ContentGenerationCallback() {
                            @Override
                            public void onSuccess(String resultText) {
                                // Handle the successful response (resultText) here
                                informTTS.speak(resultText,TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                // Handle the failure (errorMessage) here
                                Log.d("SpeechRecognizer", "onFailure: " + errorMessage);
                                informTTS.speak(getString(R.string.error),TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);

                            }
                        }, selectedLanguage);

                    }

                } else {
                    Log.d("SpeechRecognizer", "No speech detected");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                Log.d("SpeechRecognizer", "onPartialResults");
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                Log.d("SpeechRecognizer", "onEvent");
            }
        });

        builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getString(R.string.shop_info));
        builder.setCancelable(true);


        // Find and handle events for your CustomImageButton
        customImageButton = findViewById(R.id.customImageButton);
        customImageButton.setVisibility((View.GONE));

        startCamera();
    }

    public void startSpeechRecognition() {
        if (!isListening) {
            isListening = true;
            if (speechRecognizer != null) {
                informTTS.speak(getString(R.string.start),TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                String languageCode = locale.getLanguage();
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");

                try {
                    speechRecognizer.startListening(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void stopSpeechRecognition() {
        if (isListening) {
            isListening = false;
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
        }
    }

    private void setupModelSpinner() {
        List<String> options = new ArrayList<>();
        options.add(TEXT_RECOGNITION_LATIN);
        options.add(TEXT_RECOGNITION_CHINESE);
        options.add(TEXT_RECOGNITION_DEVANAGARI);
        options.add(TEXT_RECOGNITION_JAPANESE);
        options.add(TEXT_RECOGNITION_KOREAN);

        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedModel = parent.getItemAtPosition(position).toString();
                saveSelectedModel(langIndex);
                bindAnalysisUseCase();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Find the index of selected model in the options list
        int modelIndex = options.indexOf(selectedModel);
        // Set the selection to "TEXT_RECOGNITION_CHINESE"
        modelSpinner.setSelection(modelIndex);
    }

    private void setupLanguageSpinner() {

        String[] languages = {"English", "Chinese", "Japanese", "Korean"};

        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, languages);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);

        int oriIndex = Arrays.asList(languages).indexOf(selectedLanguage);

        if (oriIndex >= 0) {
            languageSpinner.setSelection(oriIndex);
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = (String) parent.getItemAtPosition(position);

                if(oriIndex != position){
                    // Perform operations specific to languageSpinner here
                    switch(position){
                        // Change language to English
                        case 1:
                            selectedModel = TEXT_RECOGNITION_CHINESE;
                            saveSelectedModel(position);
                            changeLanguage("zh"); // Change language to Chinese
                            break;

                        case 2:
                            selectedModel = TEXT_RECOGNITION_JAPANESE;
                            saveSelectedModel(position);
                            changeLanguage("ja"); // Change language to Japanese
                            break;

                        case 3:
                            selectedModel = TEXT_RECOGNITION_KOREAN;
                            saveSelectedModel(position);
                            changeLanguage("ko"); // Change language to Korean
                            break;

                        default:
                            selectedModel = TEXT_RECOGNITION_LATIN;
                            saveSelectedModel(position);
                            changeLanguage("en");
                            break;

                    }

                }


            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void changeLanguage(String languageCode) {
        Locale newLocale = new Locale(languageCode);
        Locale.setDefault(newLocale);

        Configuration configuration = new Configuration();
        configuration.setLocale(newLocale);

        Resources resources = getResources();
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        // Finish the current activity and start a new instance
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }


    private void startCamera() {
        int lensFacing = CameraSelector.LENS_FACING_BACK;
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(CameraXViewModel.class)
                .getProcessCameraProvider()
                .observe(
                        this,
                        provider -> {
                            cameraProvider = provider;
                            cameraProvider.unbindAll();
                            bindPreviewUseCase();
                            bindAnalysisUseCase();
                        });
    }

    private void capturePhoto() {
        if (cameraProvider != null) {
            ImageCapture imageCapture = new ImageCapture.Builder().build();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);

            // Capture the image
            imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(@NonNull ImageProxy image) {
                    super.onCaptureSuccess(image);

                    Log.i("Capture","Capturing the photo");
                    informTTS.speak(getString(R.string.capturing),TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);

                    // Process the captured image using textRecognizer
                    processCapturedImage(image);

                    // Unbind the camera to release resources
                    cameraProvider.unbindAll();
                    informTTS.speak(getString(R.string.capture_success),TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                    isPreviewFrozen = true;

                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    super.onError(exception);
                    Log.e("CaptureError", "Error capturing image: " + exception.getMessage(), exception);
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(STATE_SELECTED_MODEL, selectedModel);
    }

    @Override
    public void onResume() {

        super.onResume();

        if(cameraProvider != null){
            cameraProvider.unbindAll();
        }

        // Rebind the camera and go back to live preview
        isPreviewFrozen = false;
        bindPreviewUseCase();
        bindAnalysisUseCase();

        capturedPlaceIDSet.clear();
        capturedShopSet.clear();
        shopInfoList.clear();

        customImageButton.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (informTTS.isSpeaking()) {
            informTTS.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        // Release the TextToSpeech resources
        if (matchingTTS != null) {
            matchingTTS.stop();
            matchingTTS.shutdown();
        }

        // Release the TextToSpeech resources
        if (informTTS != null) {
            informTTS.stop();
            informTTS.shutdown();
        }

        if (TTSHandler != null) {
            TTSHandler.removeCallbacksAndMessages(null);
        }

        if (PlacesAPIHandler != null) {
            PlacesAPIHandler.removeCallbacksAndMessages(null);
        }
    }

    private void bindPreviewUseCase() {

        if (cameraProvider == null) {
            return;
        }
        if (previewUseCase != null) {
            cameraProvider.unbind(previewUseCase);
        }

        Preview.Builder builder = new Preview.Builder();
        previewUseCase = builder.build();
        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
        camera =
                cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, previewUseCase);
    }


    private void bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return;
        }
        if (analysisUseCase != null) {
            cameraProvider.unbind(analysisUseCase);
        }

        if (selectedModel.equals(TEXT_RECOGNITION_LATIN)) {
            textRecognizer = TextRecognition.getClient(new TextRecognizerOptions.Builder().build());
        } else if (selectedModel.equals(TEXT_RECOGNITION_CHINESE)) {
            textRecognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        } else if (selectedModel.equals(TEXT_RECOGNITION_DEVANAGARI)) {
            textRecognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        } else if (selectedModel.equals(TEXT_RECOGNITION_JAPANESE)) {
            textRecognizer = TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        } else if (selectedModel.equals(TEXT_RECOGNITION_KOREAN)) {
            textRecognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        } else {
            throw new IllegalStateException("Invalid model name");
        }


        cameraExecutor = Executors.newSingleThreadExecutor();

        ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
        analysisUseCase = builder.build();

        analysisUseCase.setAnalyzer(
                cameraExecutor,
                imageProxy -> {
                    // Get the media image from the ImageProxy
                    Image mediaImage = imageProxy.getImage();

                    if (mediaImage != null) {
                        // Create an InputImage from the media image
                        InputImage inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        // Process the input image using TextRecognizer
                        Task<Text> task = textRecognizer.process(inputImage);
                        // Process the recognized text
                        task.addOnSuccessListener(result -> {
                            displayRecognizedText(result, "Live");
                        })
                        .addOnFailureListener(e -> {
                            // Handle errors
                            Log.e("TextRecognition", "Text recognition error: " + e.getMessage(), e);
                        })
                        .addOnCompleteListener(result -> {
                            // Close the imageProxy to allow processing the next frame
                            imageProxy.close();
                        });

                    }

                });

        cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, analysisUseCase);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // Retrieve the selected item
        Object selectedItem = parent.getItemAtPosition(pos);

        if (selectedItem != null) {
            Log.d("ItemSelected", "Selected item: " + selectedItem.toString());
            // Your logic to handle the selected item
        } else {
            Log.e("ItemSelected", "Selected item is null");
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        // Optional: Log or handle the event where nothing is selected
        Log.d("ItemSelected", "Nothing selected");
    }


    private void displayRecognizedText(Text text, String TAG) {
        String recognizedText = text.getText();
        Log.d("RecognizedText", "Recognized Text: " + recognizedText);

        for (Text.TextBlock textBlock : text.getTextBlocks()) {
            for (Text.Line line : textBlock.getLines()) {
                String detectedText = line.getText().toUpperCase();
                for (int k = 0; k < shopNameList.size(); k++) {
                    String shopName = shopNameList.get(k).toUpperCase();
                    if (isSimilar(detectedText, shopName)) {
                        handleSimilarText(shopName, shopIDList.get(k), TAG);
                    }
                }
            }
        }
    }

    private void handleSimilarText(String shopName, String shopID, String TAG) {
        uniqueTextSet.add(shopName);
        Log.i("uniqueTextSet", uniqueTextSet.toString());

        if (Objects.equals(TAG, "Captured")) {
            capturedShopSet.add(shopName);
            capturedPlaceIDSet.add(shopID);
            Log.i("capturedShopSet", capturedShopSet + ", size = " + capturedPlaceIDSet.size());
            Log.i("capturedPlaceIDSet", capturedPlaceIDSet.toString() + ", size = " + capturedPlaceIDSet.size());
        }
    }


    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set the language if needed
            int result = matchingTTS.setLanguage(locale);
            informTTS.setLanguage(locale);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeech", "Language is not supported");
            }
        } else {
            Log.e("TextToSpeech", "Initialization failed");
        }
    }

    private final Runnable checkAndSpeakRunnable = new Runnable() {
        @Override
        public void run() {
            if (!uniqueTextSet.isEmpty()) {
                // Get the first item from the set
                String firstItem = uniqueTextSet.iterator().next();

                // Speak the first item using matchingTTS
                matchingTTS.speak(firstItem, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);

            } else {
                // If the set is empty, schedule the next run after SPEAK_INTERVAL
                TTSHandler.postDelayed(this, SPEAK_INTERVAL);
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event){
        // Pass the touch event to the GestureDetector
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent motionEvent) {

        customImageButton.setVisibility(View.GONE);

        Log.d("Gesture", "Double tap");

        if (informTTS.isSpeaking()) {
            informTTS.stop();
        }

        // Rebind the camera and go back to live preview
        isPreviewFrozen = false;
        bindPreviewUseCase();
        bindAnalysisUseCase();

        capturedPlaceIDSet.clear();
        capturedShopSet.clear();
        shopInfoList.clear();

        return true;
    }

    @Override
    public boolean onDoubleTapEvent(@NonNull MotionEvent motionEvent) {

        return false;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(@NonNull MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent motionEvent) {

        Log.i("Gesture","SingleTap");

        if (!isPreviewFrozen) {
            // If frozen, capture the image and process it
            capturePhoto();
        }

        return true;
    }

    @Override
    public boolean onScroll(@Nullable MotionEvent motionEvent, @NonNull MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent motionEvent) {

        if(isPreviewFrozen && shopLatitude != null && shopLongitude != null){
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + shopLatitude + "," + shopLongitude + "&mode=w");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        }

    }

    @Override
    public boolean onFling(@Nullable MotionEvent motionEvent, @NonNull MotionEvent motionEvent1, float v, float v1) {

        float deltaX = motionEvent1.getX() - motionEvent.getX();
        float deltaY = motionEvent1.getY() - motionEvent.getY();
        boolean horizontalSwipe = Math.abs(deltaX) > Math.abs(deltaY);

        if(isPreviewFrozen) {
            int count = capturedShopSet.size();
            Log.d("CapturedShopSetSize", String.valueOf(count));

            if (horizontalSwipe) {
                // Horizontal swipe detected
               alertDialog.dismiss();

                if (deltaX > 0) {
                    // Right swipe
                    if (tapCount > 0) {
                        tapCount--;
                    }

                } else {
                    // Left swipe
                    if (tapCount < count - 1) {
                        tapCount++;
                    }

                }

                // Update UI with shop information based on tapCount
                if (tapCount >= 0 && tapCount < count) {
                    // Call a method to display shop information based on tapCount
                    displayShopInformation(tapCount);

                }
            }
        }
        return true;
    }

    private class MyThread extends Thread {
        private final String searchURL;

        public MyThread(String searchUrl) {
            this.searchURL = searchUrl;
        }

        public void run() {
            try {
                URL url = new URL(searchURL);
                HttpURLConnection hc = (HttpURLConnection) url.openConnection();

                InputStream input = hc.getInputStream();
                String response = readStream(input);

                //OK response code
                if (hc.getResponseCode() == 200) {
                    if(isPreviewFrozen) {
                        Log.d("Place Detail API","Response: " + response);
                        shopInfoList.add(response);
                        Log.d("shopInfoList", shopInfoList.toString());
                    }

                }else{
                    Log.e("API ERROR", "Response code: " + hc.getResponseCode());
                }

                input.close();

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static String formatJson(String jsonString){

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            StringBuilder formattedBuilder = new StringBuilder();

            // Append formatted_address
            formattedBuilder.append("Address: ").append(jsonObject.getJSONObject("result").getString("formatted_address")).append("\n");
            formattedBuilder.append("Phone number: ").append(jsonObject.getJSONObject("result").getString("international_phone_number")).append("\n");
            formattedBuilder.append("Opening: ").append(jsonObject.getJSONObject("result").getJSONObject("opening_hours").getString("open_now")).append("\n");

            // Append weekday_text
            JSONArray weekdayTextArray = jsonObject.getJSONObject("result").getJSONObject("opening_hours").getJSONArray("weekday_text");
            formattedBuilder.append("Operational hour:\n");
            for (int i = 0; i < weekdayTextArray.length(); i++) {
                formattedBuilder.append(weekdayTextArray.getString(i)).append(",\n");
            }

            formattedBuilder.append("Rating: ").append(jsonObject.getJSONObject("result").getString("rating")).append("\n");

            // Append weekday_text
            JSONArray reviewTextArray = jsonObject.getJSONObject("result").getJSONArray("reviews");
            formattedBuilder.append("Review:\n");
            for (int i = 0; i < reviewTextArray.length(); i++) {
                formattedBuilder.append(reviewTextArray.getJSONObject(i).getString("text")).append(",\n");
            }

            formattedBuilder.append("User Ratings Total: ").append(jsonObject.getJSONObject("result").getString("user_ratings_total")).append("\n");
            formattedBuilder.append("Business status: ").append(jsonObject.getJSONObject("result").getString("business_status")).append("\n");

            if (jsonObject.getJSONObject("result").has("price_level")) {
                String priceLevel = jsonObject.getJSONObject("result").getString("price_level");
                String priceLevelString;
                switch (priceLevel) {
                    case "0":
                        priceLevelString = "Inexpensive";
                        break;
                    case "1":
                        priceLevelString = "Moderate";
                        break;
                    case "2":
                        priceLevelString = "Expensive";
                        break;
                    case "3":
                        priceLevelString = "Very Expensive";
                        break;
                    default:
                        priceLevelString = "Unknown";
                        break;
                }

                formattedBuilder.append("Price level: ").append(priceLevelString).append("\n");
            }

            return formattedBuilder.toString();

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    private String readStream(InputStream is) {
        try {
            ByteArrayOutputStream bo = new
                    ByteArrayOutputStream();
            int i = is.read();
            while (i != -1) {
                bo.write(i);
                i = is.read();
            }
            return bo.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private void processCapturedImage(ImageProxy image) {
        if (image == null) {
            return;
        }

        Image mediaImage = image.getImage();
        if (mediaImage == null) {
            image.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());

        textRecognizer.process(inputImage)
                .addOnSuccessListener(result -> {
                    Log.d("CapturedImage", "The text below is captured text.");
                    displayRecognizedText(result, "Captured");

                    if (!fetchingShopInformation()) {
                        informTTS.speak(getString(R.string.no_shop), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CapturedImage", "Text recognition error: " + e.getMessage(), e);
                })
                .addOnCompleteListener(task -> image.close());
    }


    private boolean isSimilar(String detectedText, String shopName) {
        double similarity = TextSimilarity.calculateSimilarity(detectedText.toUpperCase(), shopName.toUpperCase());
        // Adjust the threshold based on your needs
        //Log.i("Similarity",detectedText+" & "+shopName+" = "+similarity);
        return similarity >= 0.8; // Example threshold: 80% similarity
    }

    private void speakShopNameFromCapturedSet(int index) {
        // Retrieve the shop name at the specified index
        String shopName = getShopNameAtIndex(index);

        // Speak the shop name
        informTTS.speak(shopName, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    private String getShopNameAtIndex(int index) {
        List<String> shopList = new ArrayList<>(capturedShopSet);
        if (index >= 0 && index < shopList.size()) {
            return shopList.get(index);
        } else {
            return ""; // Return an empty string or handle the out-of-bounds case accordingly
        }
    }

    private String getPlaceIDAtIndex(int index) {
        List<String> placeIDList = new ArrayList<>(capturedPlaceIDSet);
        if (index >= 0 && index < placeIDList.size()) {
            return placeIDList.get(index);
        } else {
            return ""; // Return an empty string or handle the out-of-bounds case accordingly
        }
    }

    private boolean fetchingShopInformation() {

        if(capturedPlaceIDSet.isEmpty()){
            return false;
        }

        for(int i = 0; i < capturedPlaceIDSet.size();i++) {
            Log.i("Fetching", "Fetching " + i + " shop information");
            String placeID = getPlaceIDAtIndex(i);
            MyThread shopDetailsThread = getMyThread(placeID);
            // Wait for the shopDetailsThread to finish
            try {
                shopDetailsThread.join();
            } catch (InterruptedException e) {
                // Handle the interruption if needed
                e.printStackTrace();
            }
        }

        Log.d("Fetching", "Done");
        Log.d("ShopInfo",shopInfoList.toString());


        displayShopInformation(0);

        return true;
    }

    @NonNull
    private MyThread getMyThread(String placeID) {
        String strURL = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?fields=name%2C" +
                "geometry%2C" +
                "formatted_address%2C" +
                "international_phone_number%2C" +
                "opening_hours/open_now%2C" +
                "opening_hours/weekday_text%2C" +
                "rating%2C" +
                "price_level%2C" +
                "business_status%2C" +
                "user_ratings_total%2C" +
                "reviews" +
                "&place_id=" + placeID +
                "&key=" + apiKey;

        MyThread shopDetailsThread = new MyThread(strURL);
        shopDetailsThread.start();
        return shopDetailsThread;
    }

    private void displayShopInformation(int index) {

        if (shopInfoList.size() < index) {
            return;
        }

        speakShopNameFromCapturedSet(index);
        String response = shopInfoList.get(index);
        shopIndex = index;

        try {
            JSONObject jsonObject = new JSONObject(response);
            shopLatitude = jsonObject.getJSONObject("result")
                    .getJSONObject("geometry")
                    .getJSONObject("location")
                    .getString("lat");

            shopLongitude = jsonObject.getJSONObject("result")
                    .getJSONObject("geometry")
                    .getJSONObject("location")
                    .getString("lng");

            String formatted = formatJson(response);

            formatted += "\n\n Make a summary paragraph of the shop's information useful to BLIND people. " +
                    "The paragraph should not have any formatting, numbering or listing. ";

            contentGenerationHandler.generateContent(formatted, new ContentGenerationHandler.ContentGenerationCallback() {
                @Override
                public void onSuccess(String resultText) {
                    // Handle the successful response (resultText) here
                    builder.setMessage(resultText);
                    runOnUiThread(() -> {
                        alertDialog = builder.create();
                        alertDialog.show();
                        informTTS.speak(resultText,TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                        customImageButton.setVisibility((View.VISIBLE));

                    });

                }


                @Override
                public void onFailure(String errorMessage) {
                    // Handle the failure (errorMessage) here
                    builder.setMessage(errorMessage);
                    runOnUiThread(() -> {
                        alertDialog.dismiss();
                        alertDialog = builder.create();
                        alertDialog.show();
                        informTTS.speak(getString(R.string.error),TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
                    });

                }
            }, selectedLanguage);


        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    private void saveSelectedModel(int position) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("model", selectedModel);
        editor.putString("language", selectedLanguage);
        editor.putInt("position", position);
        editor.apply();
    }

    private Locale loadLocale(int position){
        switch (position) {
            case 0:
                locale = Locale.ENGLISH;
                break;
            case 1:
                locale = Locale.CHINESE;
                break;
            case 2:
                locale = Locale.JAPANESE;
                break;
            case 3:
                locale = Locale.KOREAN;
                break;
            default:
                locale = Locale.getDefault();
                break;
        }

        Locale.setDefault(locale);
        Configuration config = getResources().getConfiguration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        return locale;
    }

    private void checkSpeechRecognition() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.isEmpty()) {
            // Speech recognition not supported
            Toast.makeText(this, "Speech recognition is not supported on this device", Toast.LENGTH_SHORT).show();

        }
    }

}