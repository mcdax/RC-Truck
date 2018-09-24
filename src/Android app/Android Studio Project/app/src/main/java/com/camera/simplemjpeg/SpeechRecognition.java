package com.camera.simplemjpeg;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.SpeechRecognizer;

import java.io.File;
import java.io.IOException;

public class SpeechRecognition implements edu.cmu.pocketsphinx.RecognitionListener {
    // driving
    public static final String ORDER_SEARCH = "order";
    public static final String TURN_LEFT = "left";
    public static final String TURN_RIGHT = "right";
    public static final String STOP = "stand";
    public static final String DRIVE = "go";
    public static final String BACK = "back";
    public static final String STRAIGHT = "line up";
    // other
    public static final String HORN = "beep";
    public static final String ON = "on";
    public static final String OFF = "off";
    public static final String AUTO = "auto";
    //
    private SpeechRecognizer recognizer;
    private MainActivity mainActivity;
    private boolean driving;

    // the boolean 'driving' indicates, wheater the speech recognition object should be set up for "driving" or "other"
    public SpeechRecognition(MainActivity mainActivity, final Context context, boolean driving) {
        this.mainActivity = mainActivity;
        this.driving = driving;
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(context);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    Log.e(MainActivity.TAG, "Failed to init recognizer   recognizer: "+recognizer);
                } else {
                    Log.e(MainActivity.TAG, "Successfully init recognizer   recognizer: "+recognizer);
                    recognizer.startListening(ORDER_SEARCH);
                }
            }
        }.execute();
    }

     /** In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;
    }

    /** This callback is called when we stop the recognizer */
    @Override
    public void onResult(Hypothesis hypothesis) {
//        ((TextView) findViewById(R.id.caption_text)).setText("");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            if (text.equals(DRIVE)) doSomething(DRIVE);
            else if (text.equals(TURN_LEFT)) doSomething(TURN_LEFT);
            else if (text.equals(TURN_RIGHT)) doSomething(TURN_RIGHT);
            else if (text.equals(STOP)) doSomething(STOP);
            else if (text.equals(BACK)) doSomething(BACK);
            else if (text.equals(STRAIGHT)) doSomething(STRAIGHT);
            else if (text.equals(HORN)) doSomething(HORN);
            else if (text.equals(ON)) doSomething(ON);
            else if (text.equals(OFF)) doSomething(OFF);
            else if (text.equals(AUTO)) doSomething(AUTO);
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /** We stop recognizer here to get a final result */
    @Override
    public void onEndOfSpeech() {
    }

    // int as return type, connect with list!
    private void doSomething(String searchName) {
        recognizer.stop();
        Log.e(MainActivity.TAG, "You said: " + searchName);
        mainActivity.sendOverSocket(searchName);
//        mainActivity.onActionResult(searchName);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them
        recognizer = defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .setRawLogDir(assetsDir)
                // Threshold to tune for keyphrase to balance between false alarms and misses
                .setKeywordThreshold(1e-45f)
                // Use context-independent phonetic search, context-dependent is too slow for mobile
                .setBoolean("-allphone_ci", true)
                .getRecognizer();
        recognizer.addListener(this);
        // Create grammar-based search for selection
        if(driving){
            File orderGrammar = new File(assetsDir, "menu.gram");
            recognizer.addGrammarSearch(ORDER_SEARCH, orderGrammar);
        }else{
            File orderGrammar = new File(assetsDir, "menu2.gram");
            recognizer.addGrammarSearch(ORDER_SEARCH, orderGrammar);
        }
    }

    @Override
    public void onError(Exception error) {
        Log.e(MainActivity.TAG, "error: " + error.getMessage());
    }

    @Override
    public void onTimeout() {
    }

    public SpeechRecognizer getRecognizer() {
        return recognizer;
    }
}