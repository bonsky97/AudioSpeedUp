/*
AudioSpeedUp v.1.0 - 2020/04/01
Created by Lorenzo D'Auria. Be gentle, it's just a hobby for me.

The same main activity will be used both when the app is opened from launcher or from sharing.
*/

package com.lodauria.audiospeedup;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.material.snackbar.Snackbar;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // GLOBAL VARIABLES ----------------------------------------------------------------------------
    public static boolean mp_play = true;
    public static boolean mp_stop = true;
    private Database mDatabase;
    private Cursor data;
    private PopupMenu popup;
    private Menu menuOpts;
    private AlertDialog alertDialog = null;
    private CoordinatorLayout coordinatorLayout;
    private AudioManager m_amAudioManager;
    private SensorManager mSensorManager;
    private Sensor mProximity;
    private Button test;
    private Button help;
    private TextView label;
    private SeekBar speed, player;
    private MediaPlayer mp;
    private ImageButton play_b, restart_b, stop_b;
    private int flag = 0;
    private boolean trig = false;
    private float factor;
    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder notification;
    private Thread mp_updater;


    // GET SPEED FACTOR ----------------------------------------------------------------------------
    // In the shared preferences is stored the speed factor to use
    private void getFactor() {
        SharedPreferences save = getSharedPreferences("factor", 0);
        factor = save.getFloat("factor", (float) 2.0);
    }

    // SAVE NEW SPEED FACTOR -----------------------------------------------------------------------
    // The speed factor has to be updated when the user changes speed
    private void saveFactor() {
        factor = (float) (speed.getProgress() / 4.0 + 0.5);
        SharedPreferences save = getSharedPreferences("factor", 0);
        SharedPreferences.Editor editor = save.edit();
        editor.putFloat("factor", factor);
        editor.apply();
    }

    // COMPLETION LISTENER OF THE MEDIA PLAYER -----------------------------------------------------
    // Operations to be done for a good UI
    // Made as an external function because used in different parts of the code
    private void set_mp_listener(final boolean from_sharing) {
        mp.setOnCompletionListener(media_p -> {
            // Same of the stop button
            if (from_sharing && !hasWindowFocus()) {
                mp.release();
                mp = null;
                notificationManager.cancelAll();
                finishAndRemoveTask();
            } else {
                mp.pause();
                notificationManager.cancelAll();
                mp.seekTo(0);
                player.setProgress(0);
                play_b.setImageResource(R.drawable.play);
            }
        });
    }

    // SETUP ACTIVITY FOR SHARING OPTION -----------------------------------------------------------
    // On create only one setup will be used, here is the one if project is started from sharing
    private boolean setup_for_sharing() {
        // Check if media player definition was successful
        if (mp == null) {
            // This means that the file is not supported
            // TODO: On older Android version opus are not supported, can be resolved in some way?
            Toast.makeText(this, R.string.audio_not_supported, Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
            // Return true to handle the error message and stop the execution
            return true;
        }
        // Everything fine, the bottom buttons are unnecessary
        test.setVisibility(View.INVISIBLE);
        help.setVisibility(View.INVISIBLE);
        return false;
    }

    // SETUP ACTIVITY IF FROM LAUNCHER -------------------------------------------------------------
    // On create the media player will use the test audio file
    private void setup_normal() {
        // Necessary initializations
        mp = MediaPlayer.create(this, R.raw.test);
        player.setEnabled(false);
        restart_b.setEnabled(false);
        stop_b.setEnabled(false);
        play_b.setEnabled(false);

        // Define the test button for reproducing audio
        test.setOnClickListener(v -> {
            flag += 1;
            if (flag == 1) {
                // Check if audio is mute
                AudioManager am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                if (Objects.requireNonNull(am).getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                    Toast.makeText(getApplicationContext(), getString(R.string.up_volume), Toast.LENGTH_SHORT).show();
                // Setup the player
                mp_updater.start();
                player.setEnabled(true);
                restart_b.setEnabled(true);
                stop_b.setEnabled(true);
                play_b.setEnabled(true);
            }
            // Easter egg after 10 tap, the audio file will change
            if (flag == 10) {
                mp.stop();
                mp = MediaPlayer.create(getApplicationContext(), R.raw.easteregg);
                set_mp_listener(false);
                mp.setLooping(false);
                mp.setVolume(1.0f, 1.0f);
                player.setMax(mp.getDuration() / 100);
            }
            // Play the audio
            mp.seekTo(0);
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
            play_b.setImageResource(R.drawable.pause);
        });

        // Define the help button with the warning message
        help.setOnClickListener(v -> {
            // Simple popup tutorial on how to use the app
            // Check UI Mode and change theme
            int nightModeFlags =
                    this.getResources().getConfiguration().uiMode &
                            Configuration.UI_MODE_NIGHT_MASK;
            switch (nightModeFlags) {
                case Configuration.UI_MODE_NIGHT_YES:

                case Configuration.UI_MODE_NIGHT_UNDEFINED:
                    alertDialog = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialogDark).create();
                    break;

                case Configuration.UI_MODE_NIGHT_NO:
                    alertDialog = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog).create();
                    break;
            }

            alertDialog.setTitle(getString(R.string.title_dialog));
            alertDialog.setMessage(getString(R.string.desc_dialog));
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(android.R.string.ok),
                    (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        });
    }

    // ON ACTIVITY CREATION ------------------------------------------------------------------------
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Setup database
        mDatabase = new Database(this);
        data = mDatabase.getData();
        if (data.getCount() == 0)
            AddData("themevalue", "0");

        // Set saved theme
        while (data.moveToNext())
            if (data.getString(1).equals("themevalue") && data.getString(2).equals("0")) {
                setTheme(R.style.AppTheme);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (data.getString(1).equals("themevalue") && data.getString(2).equals("1")) {
                setTheme(R.style.DarkTheme);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }

        setContentView(R.layout.activity_main);

        // All element initialization
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Button more = toolbar.findViewById(R.id.more);
        Button donate = findViewById(R.id.donateButton);
        test = findViewById(R.id.testButton);
        help = findViewById(R.id.helpButton);
        play_b = findViewById(R.id.playButton);
        stop_b = findViewById(R.id.stopButton);
        restart_b = findViewById(R.id.restartButton);
        label = findViewById(R.id.SpeedVal);
        speed = findViewById(R.id.speedBar);
        player = findViewById(R.id.seekBar);
        popup = new PopupMenu(MainActivity.this, more);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        menuOpts = popup.getMenu();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mProximity = Objects.requireNonNull(mSensorManager).getDefaultSensor(Sensor.TYPE_PROXIMITY);

        m_amAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        coordinatorLayout = findViewById(R.id.cordinatorLayout);

        // Setup database
        mDatabase = new Database(this);
        data = mDatabase.getData();

        // FIRST SETUP -----------------------------------------------------------------------------
        // Obtain the speed factor
        getFactor();

        // Identify if the call was from the launcher
        Intent intent = getIntent();
        final boolean from_sharing = Objects.equals(intent.getAction(), "android.intent.action.SEND");
        if (from_sharing) {
            moveTaskToBack(true);
            // Get the file shared and initialize correctly the activity
            Uri data = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            mp = MediaPlayer.create(getApplicationContext(), data);
            // If file can't be open stop all the other things below
            if (setup_for_sharing()) return;
        } else {
            // Setup activity for a launcher event
            setup_normal();
        }

        // Setup the media player end the screen in general
        mp.setLooping(false);
        mp.setVolume(1.0f, 1.0f);
        speed.setProgress((int) ((factor - 0.5) * 4.0));
        label.setText(String.format(getString(R.string.speed), factor));

        // DONATE BUTTON ---------------------------------------------------------------------------
        donate.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/AudioSpeedUp"))));

        // MORE BUTTON -----------------------------------------------------------------------------
        more.setOnClickListener(view -> {
            while (data.moveToNext())
                if (data.getString(1).equals("themevalue") && data.getString(2).equals("0"))
                    menuOpts.getItem(0).setTitle(getString(R.string.action_theme));
                else if (data.getString(1).equals("themevalue") && data.getString(2).equals("1"))
                    menuOpts.getItem(0).setTitle(getString(R.string.action_theme_dark));
            showPopup();
        });

        // SPEED BAR LISTENER ----------------------------------------------------------------------
        // Listener for the speed bar (save the speed factor and change media player speed)
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Modify only the text shown
                label.setText(String.format(getString(R.string.speed), progress / 4.0 + 0.5));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing here
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Save the new speed factor
                saveFactor();
                // If audio reproduction was already started
                if (mp.isPlaying()) {
                    // Update instantaneously the reproduction speed
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                }
            }
        });

        // NOTIFICATION ----------------------------------------------------------------------------
        // Intent for opening the app
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent enter = PendingIntent.getActivity(
                this, 0, contentIntent, 0);

        // Intent for the play/pause button
        Intent playIntent = new Intent(this, PlayReceiver.class);
        PendingIntent playPendingIntent =
                PendingIntent.getBroadcast(this, 0, playIntent, 0);

        // Intent for the sop button
        Intent stopIntent = new Intent(this, StopReceiver.class);
        PendingIntent stopPendingIntent =
                PendingIntent.getBroadcast(this, 0, stopIntent, 0);

        //Notification manager declaration
        notificationManager = NotificationManagerCompat.from(this);
        //Different behaviour for new or old Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String chanel_id = "3000";
            CharSequence name = "Audio player";
            String description = "Notification with the audio player";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(chanel_id, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(mChannel);
            notification = new NotificationCompat.Builder(this, chanel_id);
        } else {
            notification = new NotificationCompat.Builder(this, "channel1");
        }

        // Initial notification content declaration
        notification.setSmallIcon(R.drawable.my_notify)
                .setContentTitle(getString(R.string.notification))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(enter)
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(mp.getDuration(), 0, false)
                .addAction(R.drawable.ic_launcher_background, getString(R.string.play_pause), playPendingIntent)
                .addAction(R.drawable.ic_launcher_background, getString(R.string.stop), stopPendingIntent);

        // THREAD FOR UPDATING MEDIA PLAYER --------------------------------------------------------
        mp_updater = new Thread(() -> {
            // Setup player timeline
            if (mp != null) player.setMax(mp.getDuration() / 100);
            // If the audio file duration can't be obtained set indeterminate progressbar
            if (mp != null && mp.getDuration() == -1) {
                notification.setProgress(0, 0, true);
                notificationManager.notify(1, notification.build());
                player.setEnabled(false);
            } else {
                // Thread start cycling in this loop and exit when media player is deleted
                while (mp != null) {
                    // If inside the loop mp became null some method throw exceptions
                    try {
                        // Update the notification content
                        notification.setProgress(mp.getDuration() / 100,
                                mp.getCurrentPosition() / 100, false);
                        @SuppressLint("DefaultLocale") String tt = String.format("%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(mp.getCurrentPosition()),
                                TimeUnit.MILLISECONDS.toSeconds(mp.getCurrentPosition()) -
                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(
                                                mp.getCurrentPosition())));
                        notification.setContentText(tt);
                        if (!player.isPressed() && mp.isPlaying()) {
                            // Change the player and the notification timeline only during reproduction
                            player.setProgress(mp.getCurrentPosition() / 100);
                            notificationManager.notify(1, notification.build());
                        }
                        // Slow down the looping time (so we change the refresh rate of the timeline)
                        SystemClock.sleep(250);
                    } catch (Exception e) {
                        // If an exception has occurred means that mp has been deleted
                        // So the thread is stopped
                        return;
                    }
                }
            }
        });

        // THREAD FOR HANDLING NOTIFICATION BUTTONS ------------------------------------------------
        // A really bad solution... The idea would be handling buttons action inside the specific
        // classes, but they are static and it's a mess
        // The statics class that are called after pressing notifications buttons change the
        // public global variables mp_play and mp_stop, and this thread catches their changes
        // TODO: can be solved without changing everything?
        Thread mp_handler = new Thread(() -> {
            while (mp != null) {
                // If play/pause button has been pressed recently
                if (!mp_play) {
                    mp_play = true;
                    try {
                        if (mp.isPlaying()) {
                            mp.pause();
                            play_b.setImageResource(R.drawable.play);
                        } else {
                            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                            play_b.setImageResource(R.drawable.pause);
                        }
                    } catch (Exception e) {
                        // For some reason it often throws exceptions, but not the one we are interested in
                        if (mp == null) return;
                    }
                }
                // If stop button has been pressed recently
                if (!mp_stop) {
                    mp_stop = true;
                    try {
                        // Same of the stop button
                        if (from_sharing && !hasWindowFocus()) {
                            mp.release();
                            mp = null;
                            notificationManager.cancelAll();
                            finishAndRemoveTask();
                            return;
                        } else {
                            mp.pause();
                            notificationManager.cancelAll();
                            mp.seekTo(0);
                            player.setProgress(0);
                            play_b.setImageResource(R.drawable.play);
                        }
                    } catch (Exception e) {
                        // Same as above
                        if (mp == null) return;
                    }
                }
            }
        });

        // Start the thread
        mp_handler.start();

        // RESTART BUTTON --------------------------------------------------------------------------
        restart_b.setOnClickListener(v -> {
            // The media player is set to zero without stopping or starting media reproduction
            mp.seekTo(0);
            player.setProgress(0);
            notificationManager.cancelAll();
        });

        // STOP BUTTON -----------------------------------------------------------------------------
        stop_b.setOnClickListener(v -> {
            // Close the activity if we are from a file sharing
            if (from_sharing && !hasWindowFocus()) {
                mp.release();
                mp = null;
                notificationManager.cancelAll();
                finishAndRemoveTask();
            } else {
                // Simply restore activity layout otherwise
                mp.pause();
                notificationManager.cancelAll();
                mp.seekTo(0);
                player.setProgress(0);
                play_b.setImageResource(R.drawable.play);
            }
        });

        // PLAY-PAUSE BUTTON -----------------------------------------------------------------------
        play_b.setOnClickListener(v -> {
            // Different behaviour if the audio is playing or not
            if (mp.isPlaying()) {
                // Pause audio
                mp.pause();
                play_b.setImageResource(R.drawable.play);
            } else {
                // Play audio
                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                play_b.setImageResource(R.drawable.pause);
            }
        });

        // MEDIA PLAYER COMPLETION LISTENER --------------------------------------------------------
        // Setup the listener as declared in the dedicated external function
        set_mp_listener(from_sharing);

        // PLAYER TIMELINE -------------------------------------------------------------------------
        // Handle movements of the timeline
        player.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean wasPlaying;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // If user moves media player cursor
                if (fromUser) mp.seekTo(progress * 100);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Stop audio when moving
                wasPlaying = mp.isPlaying();
                mp.pause();
                notificationManager.cancelAll();
                play_b.setImageResource(R.drawable.play);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Start audio when releasing (if the audio was playing)
                if (wasPlaying) {
                    mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
                    play_b.setImageResource(R.drawable.pause);
                }
            }
        });

        // Special behaviour if app is started from sharing (audio has to start autonomously)
        if (from_sharing) {
            // Check if audio is mute and show toast reminder
            AudioManager am = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if (Objects.requireNonNull(am).getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
                Toast.makeText(getApplicationContext(), getString(R.string.up_volume), Toast.LENGTH_SHORT).show();
            // Start reproduction
            mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(factor));
            mp_updater.start();
            play_b.setImageResource(R.drawable.pause);
        }

    }

    // ADD TO DATABASE -----------------------------------------------------------------------------
    public void AddData(String field, String record) {
        mDatabase.addData(field, record);
    }

    // POPUP MENU ----------------------------------------------------------------------------------
    private void showPopup() {
        popup.setOnMenuItemClickListener(item -> {
            if (!trig) {
                data = mDatabase.getData();
                if (item.getItemId() == R.id.action_theme) {
                    if (data.getCount() == 0) {
                        AddData("themevalue", "1");
                    } else
                        while (data.moveToNext()) {
                            if (data.getString(1).equals("themevalue") && data.getString(2).equals("0")) {
                                mDatabase.deleteName("themevalue", "0");
                                AddData("themevalue", "1");
                            } else if (data.getString(1).equals("themevalue") && data.getString(2).equals("1")) {
                                mDatabase.deleteName("themevalue", "1");
                                AddData("themevalue", "0");
                            }

                        }

                    Snackbar snack = Snackbar.make(coordinatorLayout,
                            getString(R.string.toast_change_theme), Snackbar.LENGTH_SHORT);
                    SnackbarMaterial.configSnackbar(getApplicationContext(), snack);
                    snack.show();
                    trig=true;
                }
                popup.dismiss();

            }
            return true;


        });

        popup.show();
    }

    // PROXIMITY SENSOR CHANGE ---------------------------------------------------------------------
    // TODO: This still need fixes
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] < mProximity.getMaximumRange()) {
            // Audio near
            m_amAudioManager.setSpeakerphoneOn(false);
        } else {
            // Audio far
            m_amAudioManager.setSpeakerphoneOn(true);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // TODO: This two methods below has to be deleted if we want to use proximity sensor also when the app is in background
    // but first the sensor detection has to work properly
    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    // NEW INTENT RECEIVED -------------------------------------------------------------------------
    // If the app has been restarted before having been closed (from launcher or from another file)
    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        // Check if the new intent is from user, otherwise ignore
        if (Objects.equals(newIntent.getAction(), "android.intent.action.SEND") ||
                Objects.equals(newIntent.getAction(), "android.intent.action.MAIN")) {
            // Stop player and also all the threads
            if (mp != null) {
                mp.release();
                mp = null;
            }
            // Remove notification
            if (notificationManager != null) notificationManager.cancelAll();
            // Interrupt previous execution and restart from scratch
            finish();
            startActivity(newIntent);
        }
    }

    // ON DESTROY ----------------------------------------------------------------------------------
    // When closing the app is important to free resources and stop all the threads
    @Override
    protected void onDestroy() {
        super.onDestroy();
        popup.dismiss();
        // Remember that mp=null will stop all the thread
        if (mp != null) {
            mp.release();
            mp = null;
        }
        // Clear notifications
        if (notificationManager != null) notificationManager.cancelAll();
    }

    // ON BACK PRESSED -----------------------------------------------------------------------------
    // Behaviour has to be the same of destroy (this behaviour is more user friendly and intuitive)
    @Override
    public void onBackPressed() {
        if (mp != null) {
            mp.release();
            mp = null;
        }
        if (notificationManager != null) notificationManager.cancelAll();
        finishAndRemoveTask();
    }

// END OF MAIN ACTIVITY ------------------------------------------------------------------------
}