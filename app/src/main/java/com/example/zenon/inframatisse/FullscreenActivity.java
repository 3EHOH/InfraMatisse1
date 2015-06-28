package com.example.zenon.inframatisse;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.example.zenon.inframatisse.util.SystemUiHider;
import com.flir.flironesdk.Device;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class FullscreenActivity extends Activity implements Device.Delegate, FrameProcessor.Delegate{

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    private FrameProcessor frameProcessor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        frameProcessor = new FrameProcessor(this, this, EnumSet.of(RenderedImage.ImageType.VisualYCbCr888Image));
        setContentView(R.layout.activity_fullscreen);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.fullscreen_content);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, contentView, HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }


    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    Device flirDevice;

    @Override
    protected void onResume() {
        super.onResume();
        Device.startDiscovery(this, this);
    }

    @Override
    protected void onPause(){
        super.onPause();
        Device.stopDiscovery();
    }

    @Override
    public void onTuningStateChanged(Device.TuningState tuningState) {

    }

    @Override
    public void onAutomaticTuningChanged(boolean b) {

    }

    @Override
    public void onDeviceConnected(Device device) {
        flirDevice = device;
        device.startFrameStream(new Device.StreamDelegate() {
            @Override
            public void onFrameReceived(Frame frame) {
                frameProcessor.processFrame(frame);
            }
        });
    }

    @Override
    public void onDeviceDisconnected(Device device) {

    }

    static int BLACK;

    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        final Bitmap imageBitmap = Bitmap.createBitmap(renderedImage.width(), renderedImage.height(), Bitmap.Config.ARGB_8888);
        final short[] shortPixels = new short[renderedImage.pixelData().length / 2];
        imageBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(renderedImage.pixelData()));
        final ImageView imageView = (ImageView)findViewById(R.id.imageView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                imageView.setImageBitmap(imageBitmap);

                //layer all the other ones on top of this, set up as black/blank canvas
                Bitmap canvas = Bitmap.createBitmap(imageBitmap.getWidth(), imageBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Bitmap finalProduct = Bitmap.createBitmap(imageBitmap.getWidth(), imageBitmap.getHeight(), Bitmap.Config.ARGB_8888);

                    for(int j = 0; j < canvas.getWidth(); j++){
                        for(int k = 0; k < canvas.getHeight(); k++){
                            canvas.setPixel(j,k, BLACK);
                            finalProduct.setPixel(j,k,BLACK);
                        }
                    }

                    //eventually come up with a user input to decide nFrames, and later for the timer too
                    int nFrames = 10;

                   for(int i = 0; i < nFrames; i++){
                        //set up a timer to take snap a photo/screencap every X seconds, for now just delay

                        try {
                            Thread.sleep(5000);
                        } catch(InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                        finalProduct = processImage(shortPixels, canvas);
                        canvas = finalProduct;
                        nFrames++;
                   }
                saveToInternalStorage(finalProduct);
            }
        });
    }

    private Bitmap processImage(short[] shortPixels, Bitmap canvas) {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        canvas.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] canvasArray = stream.toByteArray();
        //rewind();

        int argbPixelArraySize = canvas.getWidth() * canvas.getHeight() * 4;


        byte[] argbPixels = new byte[argbPixelArraySize];

        boolean[] boolPixels = new boolean[argbPixelArraySize];

        final byte aPixValue = (byte) 255;

        for (int p = 0; p < shortPixels.length; p++) {
            int destP = p * 4;
            int tempInC = (shortPixels[p] - 27315) / 100;
            byte rPixValue;
            byte gPixValue;
            byte bPixValue;

            if (tempInC < 20) {
                gPixValue = canvasArray[destP + 1];
                rPixValue = canvasArray[destP];
                bPixValue = canvasArray[destP + 2];
            } else if (tempInC < 36) {
                rPixValue = bPixValue = 0;
                gPixValue = (byte) 160;
            } else if (tempInC < 40) {
                bPixValue = gPixValue = 0;
                rPixValue = 127;
            } else if (tempInC < 50) {
                bPixValue = gPixValue = 0;
                rPixValue = (byte) 255;
            } else if (tempInC < 60) {
                rPixValue = (byte) 255;
                gPixValue = (byte) 166;
                bPixValue = 0;
            } else if (tempInC < 100) {
                rPixValue = gPixValue = (byte) 255;
                bPixValue = 0;
            } else {
                bPixValue = rPixValue = gPixValue = (byte) 255;
            }
            argbPixels[destP + 3] = aPixValue;
            argbPixels[destP] = rPixValue;
            argbPixels[destP + 1] = gPixValue;
            argbPixels[destP + 2] = bPixValue;
        }

        canvas.copyPixelsFromBuffer(ByteBuffer.wrap(argbPixels));
        return canvas;

    }
    private String saveToInternalStorage(Bitmap bitmapImage){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        // path to /data/data/yourapp/app_data/imageDir
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        // Create imageDir
        File mypath=new File(directory,"profile.jpg");

        FileOutputStream fos = null;
        try {

            fos = new FileOutputStream(mypath);

            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return directory.getAbsolutePath();
    }
}
