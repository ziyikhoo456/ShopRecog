package com.example.shoprecog;

import android.os.AsyncTask;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

public class ContentGenerationHandler {
    private GenerativeModel gm;
    private GenerativeModelFutures model;
    private String apiKey = "AIzaSyDstmngKXRduwTwzY9_yHUIq7rAkjgLNFY";

    public ContentGenerationHandler() {
        gm = new GenerativeModel("gemini-1.0-pro", this.apiKey);
        model = GenerativeModelFutures.from(gm);
    }

    public void generateContent(String question, ContentGenerationCallback callback, String language) {

        String convert = "Convert the result into  "+ language +" but do not translate the proper noun. ";

        question = question + convert;

        Content content = new Content.Builder()
                .addText(question)
                .build();

        Executor executor = AsyncTask.THREAD_POOL_EXECUTOR;

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = result.getText();
                callback.onSuccess(resultText.trim());
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onFailure("Failed to load response due to " + t.getMessage());
                t.printStackTrace();
            }
        }, executor);
    }

    public interface ContentGenerationCallback {
        void onSuccess(String resultText);
        void onFailure(String errorMessage);
    }
}

