/*
    Copyright (C) 2015 Myra Fuchs, Linda Spindler, Clemens Hlawacek, Ebenezer Bonney Ussher

    This file is part of PicFrame.

    PicFrame is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    PicFrame is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with PicFrame.  If not, see <http://www.gnu.org/licenses/>.
*/

package picframe.at.picframe.activities;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.PageTransformer;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.content.Intent;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import picframe.at.picframe.R;
import picframe.at.picframe.helper.owncloud.OC_ConnectionCheck;
import picframe.at.picframe.helper.owncloud.OC_DownloadTask;
import picframe.at.picframe.helper.viewpager.AccordionTransformer;
import picframe.at.picframe.helper.viewpager.BackgroundToForegroundTransformer;
import picframe.at.picframe.helper.viewpager.CubeOutTransformer;
import picframe.at.picframe.helper.viewpager.CustomViewPager;
import picframe.at.picframe.helper.settings.AppData;
import picframe.at.picframe.helper.viewpager.DrawFromBackTransformer;
import picframe.at.picframe.helper.viewpager.EXIF_helper;
import picframe.at.picframe.helper.viewpager.FadeInFadeOutTransformer;
import picframe.at.picframe.helper.viewpager.FlipVerticalTransformer;
import picframe.at.picframe.helper.viewpager.ForegroundToBackgroundTransformer;
import picframe.at.picframe.helper.viewpager.Gestures;
import picframe.at.picframe.helper.GlobalPhoneFuncs;
import picframe.at.picframe.helper.viewpager.NoTransformer;
import picframe.at.picframe.helper.viewpager.RotateDownTransformer;
import picframe.at.picframe.helper.viewpager.StackTransformer;
import picframe.at.picframe.helper.viewpager.ZoomInTransformer;
import picframe.at.picframe.helper.viewpager.ZoomOutPageTransformer;


@SuppressWarnings("deprecation")
public class MainActivity extends ActionBarActivity{

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String mySettingsFilename = "PicFrameSettings";
    private SharedPreferences mPrefs = null;
    public static AppData settingsObj = null;

   // private RelativeLayout playPauseBtn;
    private static DisplayImages setUp;
    private static Context mContext;
    private static CustomViewPager pager;
    private Timer timer;
    private Timer downloadTimer;
    private int page;
    private String mOldPath;
    private boolean mOldRecursive;
    private RelativeLayout mainLayout;

    public static ProgressBar mProgressBar;
    private static OwnCloudClient mClientOwnCloud;
    private static Object[] mParamsOwnCloud;
    private static AsyncTask<Object, Float, Object> mBackgroundTask;
    private static Animation mFadeInAnim, mFadeOutAnim;

    private ArrayList<PageTransformer> transformers;
    private static List<String> mFilePaths;
    private static int size;
    private static int currentPageSaved;
    private static boolean toggleDirection;

    public static boolean mConnCheckOC, mConnCheckSMB;
    public boolean mDoubleBackToExitPressedOnce;

    private final static boolean DEBUG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mainLayout = (RelativeLayout) findViewById(R.id.mainLayout);
        mFadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        mFadeOutAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        mConnCheckOC = false;
        mConnCheckSMB = false;
        toggleDirection = false;
        enableGestures();
        mPrefs = getSharedPreferences(mySettingsFilename, MODE_PRIVATE);
        //deletePreferences();
        //createSettingsIfInexistent();
        if(getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        loadSettings();
        deleteTimerz();
        initializeTransitions();

        pager = (CustomViewPager) findViewById(R.id.pager);
        mContext = getApplicationContext();

        loadAdapter();
        slideShow();

        mOldPath = settingsObj.getImagePath();
        mOldRecursive = settingsObj.getRecursiveSearch();

        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrollStateChanged(int state) {}
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            public void onPageSelected(int position) {
                selectTransformer();
            }
        });
    }

    // TODO: make download task a service (+react to wifi-connection broadcast)     -> later update
    // TODO: Make download timer to alarm                                           -> later update
    // TODO: Use info of "include sub dirs" for file download                       -> later update
    // TODO: stop slideshow with tap                                                -> later update
    // TODO: folderpicker for owncloud server folder                                ! M
        // TODO: Read operation for getting folder structure                        ! C

    protected void onResume() {
        super.onResume();
        loadSettings();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // if the user choose "download NOW", download pictures; then set timer as usual
        if(settingsObj.getdownloadNow()){
            downloadPictures();
            settingsObj.setDownloadNow(false);
        }

        if(settingsObj.getSrcType() == AppData.sourceTypes.OwnCloud) {
            System.out.println("new timer");
            deleteTimerz(true);
            this.downloadTimer = new Timer();
            int downloadInterval = settingsObj.getUpdateIntervalInHours(); // number of hours to wait for next download
            this.downloadTimer.schedule(new DownloadingTimerTask(), downloadInterval * 1000 * 60 * 60, downloadInterval * 1000 * 60 * 60); // delay in hours
        } else {
            System.out.println("no new timer");
            deleteTimerz(true);
        }

        if(GlobalPhoneFuncs.getFileList(settingsObj.getImagePath()).size() > 0) {
            if (!settingsObj.getImagePath().equals(mOldPath) || mOldRecursive != settingsObj.getRecursiveSearch()) {
                loadAdapter();
            }
        }

        updateFileList();

        // start on the page we left in onPause, unless it was the first or last picture (as this freezes the slideshow
        if(currentPageSaved < pager.getAdapter().getCount() -1 && currentPageSaved > 0) {
            pager.setCurrentItem(currentPageSaved);
            page=currentPageSaved;
        }
        slideShow();
    }

    private void downloadPictures(){
        // set to false again every time
        mConnCheckOC = false;
        checkForProblemsAndShowToasts();  // check for connection or file reading problems
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent myIntent;
        switch (item.getItemId()) {
            case R.id.action_settings:
                myIntent = new Intent(this, SettingsActivity.class);
                break;
            case R.id.action_about:
                myIntent = new Intent(this, AboutActivity.class);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        startActivity(myIntent);
        return true;
    }

    protected void onPause() {
        super.onPause();
        if (mBackgroundTask != null && mBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
            mBackgroundTask.cancel(true);
        }
        deleteTimerz();
        mOldPath = settingsObj.getImagePath();
        mOldRecursive = settingsObj.getRecursiveSearch();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        currentPageSaved = pager.getCurrentItem();
    }

    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            if (mBackgroundTask != null && mBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                mBackgroundTask.cancel(true);
            }
            super.onBackPressed();
        } else {
            this.mDoubleBackToExitPressedOnce = true;
            if (mBackgroundTask != null && mBackgroundTask.getStatus() == AsyncTask.Status.RUNNING) {
                Toast.makeText(this, R.string.main_toast_download_interrupted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.main_toast_exitmsg, Toast.LENGTH_SHORT).show();
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDoubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }
    }

    private void loadSettings() {
        settingsObj = AppData.getINSTANCE();
        settingsObj.loadConfig(getApplicationContext(), mPrefs);
    }

    public void showActionBar() {
        if (this.getSupportActionBar() != null) {
            this.getSupportActionBar().show();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                getSupportActionBar().hide();
            }
        }, 2000);
    }

    public void pageSwitcher(int seconds) {
        // At this line a new Thread will be created
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new RemindTask(), 0, seconds * 1000); // delay

    }
    private class RemindTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (setUp.getCount() > 0) {
                        pager.setCurrentItem(page, true);
                        if(!toggleDirection) {
                            page++;
                        }
                        else {
                            page--;
                        }
                        if(page == setUp.getCount()-1 || page == 0){
                            toggleDirection = !toggleDirection;
                        }
                    }
                }
            });
        }
    }

    public class DownloadingTimerTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                public void run() {
                    // set to false again every time
                    mConnCheckOC = false;
                    checkForProblemsAndShowToasts();  // check for connection or file reading problems
                }
            });
        }
    }


    public void slideShow(){
        if (settingsObj.getSlideshow()){
            pager.setScrollDurationFactor(8);
            pager.setPagingEnabled(false);
            pageSwitcher(settingsObj.getDisplayTime());
        }
        else{
            pager.setScrollDurationFactor(3);
            pager.setPagingEnabled(true);
            deleteTimerz(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteTimerz();
    }

    private class DisplayImages extends PagerAdapter {
//        private List<String> mFilePaths;
        private Activity activity;
        private LayoutInflater inflater;
        private ImageView imgDisplay;
        private static final int POS_MAIN_PAGE = 0;
        private static final float WIDTH_MAIN_PAGE = 0.9f;

        private int localpage;
//        private int size;

        public DisplayImages(Activity activity) {
            this.activity = activity;
            updateSettings();
        }

        @Override
        public int getCount() {
            return size;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }


        @Override
        public Object instantiateItem(ViewGroup container, int position) {


            inflater = (LayoutInflater) activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View viewLayout = inflater.inflate(R.layout.fullscreen_layout, container,
                    false);

            imgDisplay = (ImageView) viewLayout.findViewById(R.id.photocontainer);

            if (settingsObj.getScaling()) {
                imgDisplay.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                imgDisplay.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            this.localpage = position;
            imgDisplay.setImageBitmap(EXIF_helper.decodeFile(mFilePaths.get(this.localpage), mContext));

            imgDisplay.setOnTouchListener(new Gestures(getApplicationContext()) {
                @Override
                public void onSwipeBottom() {
                    showActionBar();
                }
                @Override
                public void onSwipeTop() {
                    if (getSupportActionBar() != null){
                        getSupportActionBar().hide();
                    }
                }
            });
            container.addView(viewLayout);

            return viewLayout;

        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
        }

        private void updateSettings() {
            System.out.println("updateSetting setUp.getCount "+size);
            mFilePaths = GlobalPhoneFuncs.getFileList(settingsObj.getImagePath());
            //setUp.notifyDataSetChanged();
            size = mFilePaths.size();
        }

        public int getPage(){
            return this.localpage;
        }

        @Override
        public float getPageWidth (int position) {
            if (position == POS_MAIN_PAGE) {
                return WIDTH_MAIN_PAGE;
            }
            return 1f;
        }

    }

    private void deleteTimerz() { deleteTimerz(true); }
    private void deleteTimerz(boolean deleteDLTimer){
        if(timer != null){
            this.timer.cancel();
            this.timer.purge();
            this.timer = null;
        }
        if(downloadTimer != null && deleteDLTimer){
            this.downloadTimer.cancel();
            this.downloadTimer.purge();
            this.downloadTimer= null;
        }
    }

    public static void updateFileList() {
        mFilePaths = GlobalPhoneFuncs.getFileList(settingsObj.getImagePath());
        size = mFilePaths.size();
        setUp.notifyDataSetChanged();
    }


    private void loadAdapter(){
        setUp = new DisplayImages(MainActivity.this);
//        pager.setPagingEnabled(true);
        setUp = new DisplayImages(MainActivity.this);
        try {
            pager.setAdapter(setUp);
            page = setUp.getPage();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void enableGestures(){
        mainLayout.setOnTouchListener(new Gestures(getApplicationContext()) {
            @Override
            public void onSwipeBottom() {
                showActionBar();
            }

            @Override
            public void onSwipeTop() {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().hide();
                }
            }
        });
    }

    private boolean wifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi != null && wifi.isConnected();
    }

    private boolean checkForProblemsAndShowToasts() {
        // OwnCloud or Samba selected
        if (!settingsObj.getSrcType().equals(AppData.sourceTypes.ExternalSD)) {
            // if no write rights, we don't need to download
            if (!GlobalPhoneFuncs.isExternalStorageWritable()) {
                Toast.makeText(this, R.string.main_toast_noSDWriteRights, Toast.LENGTH_SHORT).show();
            } else {
                // If no Username set although source is not SD Card
                if (settingsObj.getUserName().equals("") || settingsObj.getUserPassword().equals("")) {
                    Toast.makeText(this, R.string.main_toast_noUsernameSet, Toast.LENGTH_SHORT).show();
                } else {
                    if (DEBUG) Log.i(TAG, "username and pw set");
                    // Wifi connected?
                    if (!wifiConnected()) {
                        Toast.makeText(this, R.string.main_toast_noWifiConnection, Toast.LENGTH_LONG).show();
                    } else {
                        if (DEBUG) Log.i(TAG, "wifi connected");
                        // Try to connect & login to selected source server
                        if (settingsObj.getSrcType().equals(AppData.sourceTypes.OwnCloud)) {
                            if (!mConnCheckOC) {
                                if (DEBUG) Log.i(TAG, "trying OC check");
                                startConnectionCheck();
                                return true;
                            }
                        }// else if (settingsObj.getSrcType().equals(AppData.sourceTypes.Samba))
                        {
                            // TODO: Samba checks go here
                        }
                    }
                }
            }
            // SD Card selected
        } else {
            if (!GlobalPhoneFuncs.isExternalStorageReadable()) {
                // If no read rights for SD although source is SD Card
                Toast.makeText(this, R.string.main_toast_noSDReadRights, Toast.LENGTH_SHORT).show();
            } else {
                if (!GlobalPhoneFuncs.hasAllowedFiles()) {
                    Toast.makeText(this, R.string.main_toast_noFileFound, Toast.LENGTH_SHORT).show();
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setUpOcClient() {
        Handler mHandler = new Handler();
        Uri serverUri = Uri.parse(settingsObj.getSrcPath());
        if (DEBUG) Log.i(TAG, "OwnCloud serverUri: " + serverUri);
        // Create client object to perform remote operations
        mClientOwnCloud = OwnCloudClientFactory.createOwnCloudClient(serverUri, mContext , true);
        mClientOwnCloud.setCredentials(
                OwnCloudCredentialsFactory.newBasicCredentials(
                        settingsObj.getUserName(),
                        settingsObj.getUserPassword()
                )
        );
        mParamsOwnCloud = new Object[4];
        mParamsOwnCloud[0] = mClientOwnCloud;
        mParamsOwnCloud[1] = mHandler;
        mParamsOwnCloud[2] = mContext;
        mParamsOwnCloud[3] = settingsObj.getExtFolderAppRoot();
    }

    private void startConnectionCheck() {
        if (settingsObj.getSrcType().equals(AppData.sourceTypes.OwnCloud)) {
            if (mClientOwnCloud == null || mParamsOwnCloud == null ||
                    !mClientOwnCloud.getCredentials().getUsername().equals(settingsObj.getUserName()) ||
                    !mClientOwnCloud.getCredentials().getAuthToken().equals(settingsObj.getUserPassword()) ||
                    !mClientOwnCloud.getBaseUri().toString().equals(settingsObj.getSrcPath()) ) {

               setUpOcClient();
           }
            mBackgroundTask = new OC_ConnectionCheck();
            mBackgroundTask.execute(mParamsOwnCloud);       // OwnCloud connection check
        }
    }

    private static void startOwnCloudDownloadTask() {
        if (mClientOwnCloud == null || mParamsOwnCloud == null ||
                !mClientOwnCloud.getCredentials().getUsername().equals(settingsObj.getUserName()) ||
                !mClientOwnCloud.getCredentials().getAuthToken().equals(settingsObj.getUserPassword()) ||
                !mClientOwnCloud.getBaseUri().toString().equals(settingsObj.getSrcPath()) ) {
            setUpOcClient();
        }
        mBackgroundTask = new OC_DownloadTask();
        mBackgroundTask.execute(mParamsOwnCloud);
    }

    public static void startFileDownload() {
        if (initializedFolders()) {
            if (settingsObj.getSrcType().equals(AppData.sourceTypes.OwnCloud)) {
                if (mConnCheckOC) {
                    startOwnCloudDownloadTask();
                }
            }       /**
                     *  else if (settingsObj.getSrcType().equals(AppData.sourceTypes.Samba)) {
                     *      if (mConnCheckSMB) {
                     *      TODO    Start samba download task here
                     *      }
                     *  }
                     */
        } else {
            if (DEBUG) Log.e(TAG, "Failed to initialise local folders");
        }
    }

    public static boolean recursiveDelete(File dir, boolean delRoot) {       // for directories
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    recursiveDelete(new File(file.getAbsolutePath()), true);
                } else {
                    if (!file.delete()) {
                        if (DEBUG) Log.e(TAG, "Couldn't delete >" + file.getName() + "<");
                    }
                }
            }
        }
        if (delRoot) {
            return dir.delete();
        }
        // Comment to remove warning xD
        return false;
    }

    private static boolean initializedFolders() {
        // mExtFolderCachePath + mExtFolderDisplayPath
        boolean dirCreated;
        // check if folders exist, if not, create them
        ArrayList<String> folderList = new ArrayList<>();
        folderList.add(settingsObj.getExtFolderAppRoot());
        folderList.add(settingsObj.getExtFolderCachePath());
        folderList.add(settingsObj.getExtFolderDisplayPath());
        for (String folder : folderList) {
            File dir = new File(folder);
            if (dir.exists() && dir.isDirectory())
                continue;
            dirCreated = dir.mkdir();
            if (dirCreated)
                if (DEBUG) Log.i(TAG, "Creating folder: >" +dir+ "< successful");
            else {
                Log.i(TAG, "Creating folder: >" +dir+ "< FAILED!");
                return false;
            }
        }
        File folder = new File(folderList.get(1));
        if (DEBUG) Log.i(TAG, "deleting files in cache dir before downloading");
        recursiveDelete(folder, false);     // delete all files and folders in cache folder
        File nomedia = new File(folderList.get(1) + File.separator + ".nomedia");
        if (!nomedia.exists()) {
            try {
                if (nomedia.createNewFile()) {
                    if (DEBUG) Log.i(TAG, "Created .nomedia file successfully");
                }
            } catch (IOException e) {
                if (DEBUG) Log.e(TAG, "Couldn't create .nomedia file");
            }
        }
        return true;
    }

    public static void updateDownloadProgress(Float percent, boolean indeterminate) {
        if (percent == -1f) {
            Toast.makeText(mContext, R.string.main_toast_noNewFiles, Toast.LENGTH_SHORT).show();
            return;
        } else if (percent == -1.5f) {
            Toast.makeText(mContext, R.string.main_toast_notEnoughStorage, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mProgressBar != null && !percent.isNaN() && !percent.isInfinite()) {
            mProgressBar.setIndeterminate(indeterminate);
            if (percent == 0) {
                mProgressBar.setProgress(0);
                progressAnimate(false);

            } else if (percent > 0 && percent <= 0.2) {
                mProgressBar.setProgress(0);
                progressAnimate(true);
            } else if (percent > 0.2 && percent < 99.8) {
                mProgressBar.setProgress(Math.round(percent));
                if (mProgressBar.getVisibility() == View.INVISIBLE)
                    progressAnimate(true);
            } else if (percent >= 99.8) {
                mProgressBar.setProgress(100);
                progressAnimate(false);
            }
        }
    }

    private static void progressAnimate(boolean fadeIn) {
        // FADEIN
        if (fadeIn) {
            if (mProgressBar.getVisibility() == View.GONE ||
                    mProgressBar.getVisibility() == View.INVISIBLE) {
                mProgressBar.setVisibility(View.VISIBLE);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                ViewPropertyAnimator i = mProgressBar.animate();
                i.setDuration(500).alphaBy(0).alpha(1).setInterpolator(new AccelerateInterpolator());

            } else {
                mProgressBar.startAnimation(mFadeInAnim);
            }
        // FADEOUT
        } else {
            if (mProgressBar.getProgress() == 0) {
                mProgressBar.setVisibility(View.INVISIBLE);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                ViewPropertyAnimator i = mProgressBar.animate();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                    i.setStartDelay(1000);
                i.setDuration(1000).alphaBy(1).alpha(0).setInterpolator(new AccelerateInterpolator());

            } else {
                mProgressBar.startAnimation(mFadeOutAnim);
            }
        }
    }


    public void selectTransformer(){
        if(settingsObj.getSlideshow() && settingsObj.getTransitionType() == 11){
            pager.setPageTransformer(true,transformers.get(random()));
        }
        else if(settingsObj.getSlideshow()){
            pager.setPageTransformer(true,transformers.get(settingsObj.getTransitionType()));
        }
        else {
            pager.setPageTransformer(true,new NoTransformer());
        }
    }

    private int random(){
        //Random from 0 to 13

        return (int)(Math.random() * 11);
    }

    private void initializeTransitions(){
        transformers = new ArrayList<>();
        this.transformers.add(new AccordionTransformer());
        this.transformers.add(new BackgroundToForegroundTransformer());
        this.transformers.add(new CubeOutTransformer());
        this.transformers.add(new DrawFromBackTransformer());
        this.transformers.add(new FadeInFadeOutTransformer());
        this.transformers.add(new FlipVerticalTransformer());
        this.transformers.add(new ForegroundToBackgroundTransformer());
        this.transformers.add(new RotateDownTransformer());
        this.transformers.add(new StackTransformer());
        this.transformers.add(new ZoomInTransformer());
        this.transformers.add(new ZoomOutPageTransformer());
    }
}