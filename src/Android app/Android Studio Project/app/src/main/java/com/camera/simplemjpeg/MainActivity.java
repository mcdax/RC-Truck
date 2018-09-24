package com.camera.simplemjpeg;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static boolean debug = false;
    private static final String PREF_URL = "url";
    private static final String URL_DEFAULT = "http://";
    private static String url;
    private MjpegView mv; // video-View, displays webcam-jpeg stream
    private SpeechRecognition speechRec; // same object for speech recognition of driving and other
    private boolean switchChecked; // true = microphone for driving  | false = microphone for other
    private boolean suspending = false;

    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private BufferedReader buffer;

    private Thread inputThread;

    // Input received over socket
    private int distances[] = new int[4];
    private int mode, speed;
    private int lights, lightsauto=1; // lights 1=on/0=off  |  lightsauto 1=on/0=off
    private int caution;
    private int musicPlaying, hornPlaying; // musicPlaying 1=on/0=off  |  hornPlaying 1=on/0=off.  @deprecated hornPlaying replaced by hornPlayingIntern
    private boolean hornPlayingIntern=false;
    private Timer hornTimer;
    // gears
    private int gear=1; // current gear (default=1)
    // driving
    private int driving=0; // -1=driving backward, 0=not driving, 1=driving forward

    public void onCreate(Bundle bundle) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        super.onCreate(bundle);
        String tmp = getPreferences(0).getString(PREF_URL, URL_DEFAULT);
        url = tmp.replaceAll("\\s", "");
        setTitle(url);
        setContentView(R.layout.main);
        mv = (MjpegView) findViewById(R.id.mv);
        new DoRead().execute(url);
        hornTimer = new Timer();
        // Pack this in xml later!
        RelativeLayout rLayout = (RelativeLayout) findViewById(R.id.rHUDLayout);
        for(int i=0; i<rLayout.getChildCount(); i++){
            rLayout.getChildAt(i).setVisibility(View.INVISIBLE);
        }
        //
        speechRec = new SpeechRecognition(this, getApplicationContext(), true);
        //
        new InitSocketThread().execute(url);
        //
//        new InputSocketThread().execute("");
        //
        initControlsListener();
    }

    public void onPause() {
        super.onPause();
        if (mv != null) {
            if (mv.isStreaming()) {
                mv.stopPlayback();
                suspending = true;
            }
        }
    }

    public void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // always start application in portrait mode!
        if (mv != null){
            if (suspending) {
                new DoRead().execute(url);
                suspending = false;
            }
        }
    }

    public void onDestroy() {
        if (mv != null) {
            mv.freeCameraMemory();
        }
        super.onDestroy();
        Log.e(TAG, "speechRec: " + speechRec + "  speechRec.getRecognizer(): " + speechRec.getRecognizer());
        speechRec.getRecognizer().cancel();
        speechRec.getRecognizer().shutdown();
        closeSocketConnection();
    }


    /* Creates the menu items */
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /* Handles item selections */
    public boolean onOptionsItemSelected(MenuItem item) {
        // finish()
        int id = item.getItemId();
        if(id == R.id.change_url) {
            changeurlDialog();
        }else if(id == R.id.debug){
            if(item.isChecked()){
                item.setChecked(false);
                debug = false;
            }else{
                item.setChecked(true);
                debug = true;
            }
        }else if(id == R.id.action_refresh){
            if (mv != null) {
                if (mv.isStreaming()) {
                    Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
                    closeSocketConnection();
                    mv.stopPlayback();
                    new DoRead().execute(url);
                    new InitSocketThread().execute(url);
//                    new InputSocketThread().execute("");
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void changeurlDialog(){
//        mv.stopPlayback();
        final EditText editText = new EditText(MainActivity.this);
        editText.setText(url);
        editText.setSelection(editText.getText().length());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        editText.setLayoutParams(lp);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("URL");
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                closeSocketConnection();
                mv.stopPlayback();
                String text = editText.getText().toString();
                if(text == null || text.equalsIgnoreCase("") || !text.contains(URL_DEFAULT)){
                    text = new String(URL_DEFAULT);
                }
                SharedPreferences prefs = getPreferences(0);
                SharedPreferences.Editor editor = prefs.edit();
                url = new String(text);
                editor.putString(PREF_URL, url);
                editor.commit();
                setTitle(url);
                try{
                    new InitSocketThread().execute(url);
                    new DoRead().execute(url);
//                    new InputSocketThread().execute("");
                }catch(IllegalThreadStateException e){ // user entered correct url (again), cancel dialog and show mv
                    Log.e(TAG, "Thread already started!");
                    e.printStackTrace();
                    dialog.cancel();
                }

            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setView(editText);
        dialog.show();
    }


    // Subclass that deals with the mjpeg streaming on a separate Thread
    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();
            Log.d(TAG, "1. Sending http request");
//            Log.d(TAG, "url: "+new HttpGet(URI.create(url[0])).toString());
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0]+":"+Constants.PORT_MOTION))); // "res.getStatusLine().getStatusCode()==401" if server requires username:password
                Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                // request successfull ...
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-ClientProtocolException", e);
            } catch (IOException e) { // stream service is offline / client has not internet connection
                e.printStackTrace();
                Log.d(TAG, "Request failed-IOException", e);
            } catch(IllegalArgumentException e){
                e.printStackTrace();
                Log.d(TAG, "Host name was null");
            }
            return null;
        }
        protected void onPostExecute(final MjpegInputStream result) {
            Log.i(TAG, "Result: " + result);
            if(result == null){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        networkErrorDialog();
                    }
                });
            }else{
                result.setSkip(1);
                mv.setSource(result);
                getSupportActionBar().show();
                mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
                mv.showFps(true);
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    public class SocketThread extends AsyncTask<String, Void, Integer> {
        protected Integer doInBackground(String... strings) {
            String string = new String(strings[0]);
            Log.e(TAG, "SocketThread starts here! String you want to send: " + string);
            if(string!=null && !string.equalsIgnoreCase("")){
                try{
                    switch(string){
                        case "go":
                            driving = 1;
                            switch(gear){
                                case 2: outputStream.write(Constants.GO_2); break;
                                case 3: outputStream.write(Constants.GO_3); break;
                                case 4: outputStream.write(Constants.GO_4); break;
                                case 5: outputStream.write(Constants.GO_5); break;
                                default: outputStream.write(Constants.GO_1);
                            }
                            break;
                        case "back":
                            driving = -1;
                            switch(gear){
                                case 2: outputStream.write(Constants.BACK_2); break;
                                case 3: outputStream.write(Constants.BACK_3); break;
                                case 4: outputStream.write(Constants.BACK_4); break;
                                case 5: outputStream.write(Constants.BACK_5); break;
                                default: outputStream.write(Constants.BACK_1);
                            }
                            break;
                        case "stand":
                            driving = 0;
                            outputStream.write(Constants.STOP); break;
                        case "left": outputStream.write(Constants.LEFT); break;
                        case "right": outputStream.write(Constants.RIGHT); break;
                        case "line up": outputStream.write(Constants.STRAIGHT); break;
                        case "lights_on": outputStream.write(Constants.LIGHTS_ON); break;
                        case "lights_off": outputStream.write(Constants.LIGHTS_OFF); break;
                        case "lights_auto_on": outputStream.write(Constants.LIGHTS_AUTO_ON); break;
                        case "lights_auto_off": outputStream.write(Constants.LIGHTS_AUTO_OFF); break;
                        // for the following commands, ui elements have to be changed additionally (From here on are the speech-recognized words)
                        case "beep": outputStream.write(Constants.HORN); break;
                        case "on": outputStream.write(Constants.LIGHTS_AUTO_OFF);
                                   outputStream.write(Constants.LIGHTS_ON);
                                   outputStream.flush();
                                   return 2;
                        case "off": outputStream.write(Constants.LIGHTS_OFF);
                                    outputStream.write(Constants.LIGHTS_AUTO_OFF);
                                    outputStream.flush();
                                    return 3;
                        case "auto": outputStream.write(Constants.LIGHTS_AUTO_ON);
                                     outputStream.flush();
                                     return 4;
                        // music
                        case "music": outputStream.write(Constants.MUSIC_ON); break;
                        // sound off
                        case "sound_off": outputStream.write(Constants.SOUND_OFF); break;
                    }
                    outputStream.flush();
                } catch (IOException e){
                    Log.e(TAG, "Exception sending '"+string+"' over socket");
                    e.printStackTrace();
                    return -1;
                }
            }
            return 0;
        }
        @Override
        protected void onPostExecute(Integer result) {
            switch(result){
                case -1: Log.e(TAG, "Failed using the Socket Connection"); break;
                case 0: Log.e(TAG, "Successfully used the Socket Connection"); break;
                case 2: runOnUI(true, false, false); break;
                case 3: runOnUI(false, true, false); break;
                case 4: runOnUI(false, false, true); break;
            }
        }
    }

    public void runOnUI(final boolean on, final boolean off, final boolean auto){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(on)
                    ((ImageView) findViewById(R.id.lights)).setImageResource(R.drawable.ic_action_lights_on);
                if(off)
                    ((ImageView) findViewById(R.id.lights)).setImageResource(R.drawable.ic_action_lights_off);
                if(auto)
                    ((ImageView) findViewById(R.id.lights)).setImageResource(R.drawable.ic_action_lights_auto);
            }
        });
    }

    public class InitSocketThread extends AsyncTask<String, Void, Integer> {
        protected Integer doInBackground(String... url) {
            try {
                String ip = new String(url[0].substring(7));
                Log.e(TAG, "ip:"+ip);
                socket = new Socket(ip, Constants.PORT_SOCKET);
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                buffer = new BufferedReader(new InputStreamReader(inputStream));
            } catch(IOException e) {
                Log.e(TAG, "IOException Socket InitSocketThread.class ");
                e.printStackTrace();
                return -1;
            }
            return 0;
        }
        @Override
        protected void onPostExecute(Integer result) {
            if(result == 0){
                Log.e(TAG, "InitSocketThread-onPostExecute(): Successfully initialized a Socket Connection");
                inputThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject json = new JSONObject();
                        StringBuilder sb;
                        String newLine;
                        final ImageView fritz = ((ImageView) findViewById(R.id.fritz));
                        final TextView debugTextView = ((TextView) findViewById(R.id.debug_textfield));
                        try {
                            while (true) {
                                sb = new StringBuilder();
                                if((newLine = buffer.readLine()).charAt(0) == '{') {
                                    sb.append(newLine);
                                    while((newLine = buffer.readLine()).charAt(0) != '}'){
                                        sb.append(newLine);
                                    }
                                    sb.append(newLine);
                                }
                                json = new JSONObject(sb.toString());
                                distances[0] = json.getJSONObject("distances").getInt("front");
                                distances[1] = json.getJSONObject("distances").getInt("front-right");
                                distances[2] = json.getJSONObject("distances").getInt("front-left");
                                distances[3] = json.getJSONObject("distances").getInt("back");
                                mode = (json.getInt("currentMode")); // left, right, line up
                                speed = (json.getInt("currentSpeed")); // [-5,...,5] forward or backward
                                caution = (json.getInt("caution"));
                                lights = (json.getInt("lights"));
                                lightsauto = (json.getInt("lightsautomation"));
                                musicPlaying = (json.getInt("ridinPlaying")); // 1 if currently playing
                                hornPlaying = (json.getInt("hornPlaying")); // 1 if currently playing
                                Log.i(TAG, "Front: " + distances[0] + " Front-Right: " + distances[1] + " Front-Left: " + distances[2] + " Back: " + distances[3] + " Mode: " + mode + " Speed: " + speed + " Caution: " + caution + " Lights: " + lights + " Automatic Lights: " + lightsauto + " HornPlaying: " + hornPlaying + " RidinPlaying: " + musicPlaying);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if(caution==1) fritz.setVisibility(View.VISIBLE);
                                        else fritz.setVisibility(View.INVISIBLE);
//                                        if(hornPlaying==1) ((ImageView) findViewById(R.id.horn)).setImageResource(R.drawable.ic_action_horn_hold);
//                                        else ((ImageView) findViewById(R.id.horn)).setImageResource(R.drawable.ic_action_horn);
                                        if(musicPlaying==1) ((ImageView) findViewById(R.id.music)).setImageResource(R.drawable.ic_action_music_playing);
                                        else ((ImageView) findViewById(R.id.music)).setImageResource(R.drawable.ic_action_music);

                                        ImageView lightsView = (ImageView) findViewById(R.id.lights);
                                        if(lightsauto==1) lightsView.setImageResource(R.drawable.ic_action_lights_auto);
                                        if(lights==1 && lightsauto==0) lightsView.setImageResource(R.drawable.ic_action_lights_on);
                                        if(lights==0 && lightsauto==0) lightsView.setImageResource(R.drawable.ic_action_lights_off);

                                        if(debug) debugTextView.setText("Front: " + distances[0] + " Front-Right: " + distances[1] + " Front-Left: " + distances[2] + " Back: " + distances[3] + " Mode: " + mode + " Speed: " + speed + " Caution: " + caution + " Lights: " + lights + " Automatic Lights: " + lightsauto + " HornPlaying: " + hornPlaying + " RidinPlaying: " + musicPlaying);
                                    }
                                });
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "IOException in Socket-Input | e: "+e);
                            e.printStackTrace();
                        } catch(JSONException e) {
                            Log.e(TAG, "JSONException in Socket-Input | e: "+e);
                            e.printStackTrace();
                        } catch(NullPointerException e){
                            Log.e(TAG, "NullPointerException in Socket-Input. Server closed the connection. | buffer: " + buffer);
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    networkErrorDialog();
                                }
                            });
                        }
                    }
                });
                inputThread.start();
            }else{
                Log.e(TAG, "InitSocketThread-onPostExecute(): Failed at establishing a Socket Connection");
                networkErrorDialog();
            }
        }
    }


    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Log.e(TAG, "onConfigurationChanged(): " + config.orientation);
        RelativeLayout rLayout = (RelativeLayout) findViewById(R.id.rHUDLayout);
        Log.e(TAG, "rLayout: " + rLayout + "  Childcount: " + rLayout.getChildCount());
        if(config.orientation == Configuration.ORIENTATION_LANDSCAPE){
            for(int i=0; i<rLayout.getChildCount(); i++){
                View child = rLayout.getChildAt(i);
                if(child.getId() == R.id.fritz){
                    continue;
                } else if(child.getId() == R.id.debug_textfield){
                    if(!debug) continue;
                    else child.setVisibility(View.VISIBLE);
                }
                child.setVisibility(View.VISIBLE);
            }
            getSupportActionBar().hide();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            mv.setDisplayMode(MjpegView.SIZE_FULLSCREEN);
        }else{
            for(int i=0; i<rLayout.getChildCount(); i++){
                rLayout.getChildAt(i).setVisibility(View.INVISIBLE);
            }
            getSupportActionBar().show();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        }
    }

    private void networkErrorDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Network Error");
        builder.setMessage("Make sure you are connected to the network and the server is running!");
        builder.setPositiveButton("Retry", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                closeSocketConnection();
                if(mv!=null) mv.stopPlayback();
                new InitSocketThread().execute(url);
                new DoRead().execute(url);
            }
        }).setNegativeButton("Change url", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                changeurlDialog();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();
    }

    private void initControlsListener(){
        Log.e(TAG, "initControlsListener()");
        // microphone button
        final ImageView micView = ((ImageView) findViewById(R.id.microphone));
        micView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        micView.setImageResource(R.drawable.ic_action_mic_new2);
                        speechRec.getRecognizer().stop();
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        micView.setImageResource(R.drawable.ic_action_mic_hold_new2);
                        speechRec.getRecognizer().startListening(SpeechRecognition.ORDER_SEARCH);
                        return true;
                }
                return false;
            }
        });
        // switch button (below microphone)
        final Switch switch1 = ((Switch) findViewById(R.id.switch1));
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                speechRec.getRecognizer().cancel();
                speechRec.getRecognizer().shutdown();
                if(isChecked) {
                    switchChecked = true;
                    speechRec = new SpeechRecognition(getActivity(), getApplicationContext(), true);
                } else {
                    switchChecked = false;
                    speechRec = new SpeechRecognition(getActivity(), getApplicationContext(), false);
                }
            }
        });
        // light button
        final ImageView imgView_light = (ImageView) findViewById(R.id.lights);
        imgView_light.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(lightsauto == 1) {
                    new SocketThread().execute("lights_auto_off");
                    new SocketThread().execute("lights_off");
                }
                if(lightsauto == 0 && lights == 1) {
                    new SocketThread().execute("lights_auto_on");
                }
                    if(lightsauto == 0 && lights == 0) {
                    new SocketThread().execute("lights_on");
                }

            }
        });
        // horn button
        ((ImageView) findViewById(R.id.horn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(hornPlayingIntern){
                    hornPlayingIntern = false;
                    new SocketThread().execute("sound_off");
                    ((ImageView) findViewById(R.id.horn)).setImageResource(R.drawable.ic_action_horn);
//                    hornTimer.cancel();
//                    hornTimer.purge();
                }else{
                    hornPlayingIntern = true;
                    new SocketThread().execute("beep");
                    ((ImageView) findViewById(R.id.horn)).setImageResource(R.drawable.ic_action_horn_hold);
                    hornTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if(hornPlayingIntern){
                                hornPlayingIntern = false;
                                new SocketThread().execute("sound_off");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((ImageView) findViewById(R.id.horn)).setImageResource(R.drawable.ic_action_horn);
                                    }
                                });
                            }
                        }
                    }, 2190);
                }
//                if(hornPlaying==1){
//                    new SocketThread().execute("sound_off");
//                }else{
//                    new SocketThread().execute("beep");
//                }
            }
        });
        // music button
        ((ImageView) findViewById(R.id.music)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(musicPlaying==1){
                    new SocketThread().execute("sound_off");
                }else{
                    new SocketThread().execute("music");
                }
            }
        });
        //
        final int arrowColor = 0x808080;
        // arrow buttons
        ((ImageView) findViewById(R.id.upArrow)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        ((ImageView) findViewById(R.id.upArrow)).setColorFilter(arrowColor);
                        new SocketThread().execute("stand");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        ((ImageView) findViewById(R.id.upArrow)).setColorFilter(Color.BLACK);
                        new SocketThread().execute("go");
                        return true;
                }
                return false;
            }
        });
        ((ImageView) findViewById(R.id.downArrow)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        ((ImageView) findViewById(R.id.downArrow)).setColorFilter(arrowColor);
                        new SocketThread().execute("stand");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        ((ImageView) findViewById(R.id.downArrow)).setColorFilter(Color.BLACK);
                        new SocketThread().execute("back");
                        return true;
                }
                return false;
            }
        });
        ((ImageView) findViewById(R.id.leftArrow)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        ((ImageView) findViewById(R.id.leftArrow)).setColorFilter(arrowColor);
                        new SocketThread().execute("line up");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        ((ImageView) findViewById(R.id.leftArrow)).setColorFilter(Color.BLACK);
                        new SocketThread().execute("left");
                        return true;
                }
                return false;
            }
        });
        ((ImageView) findViewById(R.id.rightArrow)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        ((ImageView) findViewById(R.id.rightArrow)).setColorFilter(arrowColor);
                        new SocketThread().execute("line up");
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        ((ImageView) findViewById(R.id.rightArrow)).setColorFilter(Color.BLACK);
                        new SocketThread().execute("right");
                        return true;
                }
                return false;
            }
        });

        // gear buttons
        final Button gearButton1 = ((Button) findViewById(R.id.gearButton1));
        final Button gearButton2 = ((Button) findViewById(R.id.gearButton2));
        final Button gearButton3 = ((Button) findViewById(R.id.gearButton3));
        final Button gearButton4 = ((Button) findViewById(R.id.gearButton4));
        final Button gearButton5 = ((Button) findViewById(R.id.gearButton5));

        final float alpha = 0.4f;
        gearButton2.setAlpha(alpha);
        gearButton3.setAlpha(alpha);
        gearButton4.setAlpha(alpha);
        gearButton5.setAlpha(alpha);

        gearButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gear = 1;
                gearButton1.setAlpha(1);
                gearButton2.setAlpha(alpha);
                gearButton3.setAlpha(alpha);
                gearButton4.setAlpha(alpha);
                gearButton5.setAlpha(alpha);
                changeGearIfDriving();
            }
        });
        gearButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gear = 2;
                gearButton1.setAlpha(alpha);
                gearButton2.setAlpha(1);
                gearButton3.setAlpha(alpha);
                gearButton4.setAlpha(alpha);
                gearButton5.setAlpha(alpha);
                changeGearIfDriving();
            }
        });
        gearButton3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gear = 3;
                gearButton1.setAlpha(alpha);
                gearButton2.setAlpha(alpha);
                gearButton3.setAlpha(1);
                gearButton4.setAlpha(alpha);
                gearButton5.setAlpha(alpha);
                changeGearIfDriving();
            }
        });
        gearButton4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gear = 4;
                gearButton1.setAlpha(alpha);
                gearButton2.setAlpha(alpha);
                gearButton3.setAlpha(alpha);
                gearButton4.setAlpha(1);
                gearButton5.setAlpha(alpha);
                changeGearIfDriving();
            }
        });
        gearButton5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gear = 5;
                gearButton1.setAlpha(alpha);
                gearButton2.setAlpha(alpha);
                gearButton3.setAlpha(alpha);
                gearButton4.setAlpha(alpha);
                gearButton5.setAlpha(1);
                changeGearIfDriving();
            }
        });
    }

    private void changeGearIfDriving(){
        if(driving==1){
            new SocketThread().execute("go");
        }else if(driving==-1){
            new SocketThread().execute("back");
        }
    }


    public boolean getSwitchChecked(){return switchChecked;}
    private MainActivity getActivity(){return this;}


    private void closeSocketConnection(){
        if(inputThread!=null) inputThread.interrupt();
        try {
            if(outputStream!=null){
                outputStream.write(Constants.CLOSESOCKET);
                outputStream.flush();
                outputStream.close();
            }
            if(socket!=null) socket.close();
            if(inputStream!=null )inputStream.close();
        } catch(IOException e) {
            Log.e(TAG, "Unable to send Close Socket (77)");
            e.printStackTrace();
        }
    }

    public void sendOverSocket(String string){
        Toast.makeText(getApplicationContext(), " "+string, Toast.LENGTH_SHORT).show();
        new SocketThread().execute(string);
    }
}