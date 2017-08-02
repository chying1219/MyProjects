package com.example.chying.androidcameraandsave;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback  {

    /* 建立私有Camera物件 */
    private Camera mCamera01;
    private Button btnPhoto, btnInternet;

    /* 作為review照下來的相片之用 */
    private String TAG = "DEBUGTAG";
    private SurfaceView mSurfaceView01;
    private SurfaceHolder mSurfaceHolder01;

    /* 預設相機預覽模式為false */
    private boolean bIfPreview = false; // 控制相機開關

    private int intRecX = 0;
    private int intRecY = 0;
    private int intRecWidth = 0;
    private int intRecHeight = 0;

    double Xfactor, Yfactor, Wfactor, Hfactor;

    /* 2.3+ Camera API */
    private int numberOfCameras;
    private Camera.CameraInfo cameraInfo;
    private int defaultCameraId; // The first rear facing camera
    public int facingCameraId; // front facing camera CAMERA_FACING_FRONT
    public int cameraCurrentlyLocked;
    private int intScreenRotation;
    private Camera.Size mPreviewSize;
    private List<Camera.Size> mSupportedPreviewSizes;

    Bitmap Capture;

    // client
    TextView mshowResult;

    DataOutputStream toServer;
    BufferedReader in;
    String message, r, result;

    Socket socket;
    Thread sentThread;
    Message msg;
    Handler handler;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // 使應用程式全螢幕執行，不使用title bar
        setContentView(R.layout.activity_main);

        getCameraInfo();
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // 設定直向橫向
        DisplayMetrics dm = new DisplayMetrics(); // 取得螢幕解析像素
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        // 針對不同的手機有不同的螢幕解析度，用螢幕寬度來設定瞄準框的相對位置
        // intRecX=x.intRecY=y.intRecWidth=寬度.intRecHeight=高度
        intRecX = (dm.widthPixels/6); // 480 -> 80
        intRecY = (dm.heightPixels/8); // 854 -> 106
        intRecWidth = dm.widthPixels*2/3; // 480 -> 320
        intRecHeight = dm.heightPixels*5/10; // 854 -> 427

        Xfactor = (double) dm.widthPixels / intRecX;
        Yfactor = (double) dm.heightPixels / intRecY;
        Wfactor = (double) dm.widthPixels / intRecWidth;
        Hfactor = (double) dm.heightPixels / intRecHeight;


        DrawCaptureRect mDraw = new DrawCaptureRect
                (
                        MainActivity.this,
                        intRecX, intRecY, intRecWidth, intRecHeight,
                        getResources().getColor(R.color.colorPrimaryDark)
                );
        addContentView(mDraw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // mImageView01 = (ImageView) findViewById(R.id.myImageView1); // 顯示拍照的圖片用

        mSurfaceView01 = (SurfaceView) findViewById(R.id.mSurfaceView); // 以SurfaceView作為相機Preview

        // 初始化SurfaceHolder，傳到setPreviewDisplay(SurfaceHolder)後才可進行預覽
        // 創建surfaceCreated(SurfaceHolder)、銷毀surfaceDestroyed(SurfaceHolder)及改變surfaceChanged()時需回傳surface
        mSurfaceHolder01 = mSurfaceView01.getHolder(); // 繫結SurfaceView，取得SurfaceHolder物件
        mSurfaceHolder01.addCallback(MainActivity.this); // Activity實作SurfaceHolder.Callback
        mSurfaceHolder01.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS); // SurfaceHolder顯示型態

        // Toast.makeText(this, "Android Testing", Toast.LENGTH_LONG).show();
        btnPhoto = (Button)findViewById(R.id.mybtnPhoto);
        btnInternet = (Button)findViewById(R.id.mybtnInternet);
        mshowResult = (TextView)findViewById(R.id.myshowResult);


        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                result = msg.obj.toString();
                mshowResult.setText("辨識結果為:" + result);

                // 跳出彈窗
                new AlertDialog.Builder(MainActivity.this) // 彈出視窗
                        .setTitle("重要") // 上方標題文字
                        .setIcon(R.drawable.hot)  // 圖式
                        .setMessage("辨識結果 " + result + " 是否正確?") // 訊息
                        .setPositiveButton("正確",new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialoginterface, int i)
                            {
                                finish(); // 關閉視窗
                            }
                        })
        /*設定跳出視窗的返回事件*/
                        .setNegativeButton("錯誤", new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialoginterface, int i)
                            {
                                // 取消後要做的事
                                // onRestart();
                                onStart();
                            }
                        }).show();
            }
        };


        btnPhoto.setOnClickListener(new Button.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                // TODO Auto-generated method stub
                // 自訂拍照函數
                takePicture(); // 拍照

                // 執行thread 送到server 做數字辨識 等待回傳結果
                sentThread = new Thread(new ClientToServer());
                sentThread.start();
            }
        });

        btnInternet.setOnClickListener(new Button.OnClickListener()
        {   // 確認是否有網路, 有的話開啟相機
            // 確認是否有SD卡 (待辦)
            @Override
            public void onClick(View arg0)
            {
                // TODO Auto-generated method stub
                initCamera(); // 開啟相機
            }
        });

    }

    private void getCameraInfo()
    {
        // 預設用後置相機，若無後置相機則用前置相機
        numberOfCameras = Camera.getNumberOfCameras();
        cameraInfo = new Camera.CameraInfo();

        for (int i = 0; i < numberOfCameras; i++)
        {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) // 後置相機
            {
                defaultCameraId = i;
            }
            else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) // 前置像機
            {
                facingCameraId = i;
            }
        }
    }


    private void initCamera()     // 自訂初始相機函數
    {
        // if(mCamera01 == null)
        if(!bIfPreview)
        {
            try
            {
                cameraCurrentlyLocked = defaultCameraId; // 依據相機數量,指定開啟相機ID
                mCamera01 = Camera.open((cameraCurrentlyLocked + 2) % numberOfCameras); // 打開相機 // 2 to 1 = front
                mCamera01.setDisplayOrientation(90); // 拍照旋轉90度,為直向, 拍照預覽用
                cameraCurrentlyLocked = (cameraCurrentlyLocked + 2) % numberOfCameras; // 已開啟的相機ID  // 2 to 1 = front
            }
            catch(Exception e)
            {
                Log.e(TAG, e.getMessage()); // 顯示相機發生的錯誤訊息
            }
        }

        // if (mCamera01 != null)
        if (mCamera01 != null && !bIfPreview)
        {
            // Camera.Parameters parameters = mCamera01.getParameters();
            Log.i(TAG, "inside the camera");

            mSupportedPreviewSizes = mCamera01.getParameters().getSupportedPreviewSizes(); // 取得相機支援的像素
            if (mSupportedPreviewSizes != null)
            {
                // 指定相機預覽的像素解析度
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, 480, 640); // 其他規格需上網查一下
            }


            Camera.Parameters parameters = mCamera01.getParameters(); // 取得相機的設定參數
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height); // 拍照預覽圖片的大小
            parameters.setPictureFormat(PixelFormat.JPEG); // 設定拍照後的相片圖像格式

            // set auto focus mode
            List<String> allFocus = parameters.getSupportedFocusModes();
            if(allFocus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)){
                parameters.setFocusMode(
                        Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            else if(allFocus.contains(Camera.Parameters.FLASH_MODE_AUTO)){
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // 重新設定相機參數
            mCamera01.setParameters(parameters); // 設置拍照參數
            try
            {
                mCamera01.setPreviewDisplay(mSurfaceHolder01);
                mCamera01.startPreview(); // 立即執行Preview
                // 在拍照前須西先啟動startPreview(), 開始更新我們的預覽介面
                // 拍照後預覽會停止, 想繼續拍, 需要再使用startPreview()
                //bIfPreview = true;
            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // 顯示相機預覽
            mCamera01.startPreview();
            bIfPreview = true;
        }
    }

    private void takePicture() // 拍照擷取影像
    {
        if (mCamera01 != null)
        {
            // 呼叫takePicture()方法進行拍照, 傳入3個callback
            mCamera01.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    private void resetCamera() // 相機重置
    {
        // if (mCamera01 != null)
        if (mCamera01 != null && bIfPreview)
        {
            mCamera01.stopPreview();

            // 釋放相機物件
            mCamera01.release();
            mCamera01 = null;

            Log.i(TAG, "stopPreview");
            bIfPreview = false;
        }
    }


    // 取得最佳預覽解析度
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h)
    { // 找到 短邊 比 長邊 大於 所接受的最小比例 的最大尺寸
        final double ASPECT_TOLERANCE = 0.1; // 容許縮放的寬高比
        double targetRatio = (double) w / h; // 以寬為主, 計算縮放比
        if (sizes == null) return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;

        // Try to find an size match aspect ratio and size 寬高比及寬高值
        for (Camera.Size size : sizes)
        {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff)
            { // 對於不滿足比例或太小的尺寸則
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement 以絕對值取得等比縮放值
        if (optimalSize == null)
        {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes)
            {
                if (Math.abs(size.height - targetHeight) < minDiff)
                {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize; // 回傳計算後最佳的尺寸(符合最小比例的尺寸)
    }

    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback()
    {
        public void onShutter()
        {
            // Shutter has closed // 快門已關閉的callback
        }
    };
    private Camera.PictureCallback rawCallback = new Camera.PictureCallback()
    {
        public void onPictureTaken(byte[] _data, Camera _camera)
        {
            // TODO Handle RAW image data
        }
    };
    private Camera.PictureCallback jpegCallback = new Camera.PictureCallback()
    {
        public void onPictureTaken(byte[] _data, Camera _camera)
        {
            // TODO Handle JPEG image data

            try
            {
                // onPictureTaken傳入的第一個參數即為相片的byte
                try
                {
                    // 原圖 // 橫向
                    Bitmap bm;
                    bm = BitmapFactory.decodeByteArray(_data, 0, _data.length);

                    // Bitmap.createBitmap(照片, x, y, 長, 寬);
                    Matrix matrixRotation = new Matrix();
                    matrixRotation.postRotate(90); // 旋轉90度,為直向,儲存圖片用
                    Bitmap resizedBitmap1 = Bitmap.createBitmap(
                            bm, 0, 0, bm.getWidth(), bm.getHeight(), matrixRotation,true); // 旋轉成直向


                    // 設定瞄準框需截圖範圍
                    intRecX = (int) (resizedBitmap1.getWidth() / Xfactor);
                    intRecY = (int) (resizedBitmap1.getHeight() / Yfactor);
                    intRecWidth = (int) (resizedBitmap1.getWidth() / Wfactor);
                    intRecHeight = (int) (resizedBitmap1.getHeight() / Hfactor);

                    Bitmap resizedBitmap2 = Bitmap.createBitmap(
                            resizedBitmap1, intRecX, intRecY, intRecWidth, intRecHeight); //建立新的Bitmap物件，縮放用

                    // 截圖後 統一規格為 1200*1200
                    int resizeWidth = 800;
                    int resizeHeight = 800;
                    float scaleWidth = ((float) resizeWidth) / resizedBitmap2.getWidth() ;
                    float scaleHeight = ((float) resizeHeight) / resizedBitmap2.getHeight();

                    Matrix matrixScale = new Matrix(); // 使用Matrix.postScale方法縮小 Bitmap Size
                    matrixScale.postScale(scaleWidth, scaleHeight);

                    Capture = Bitmap.createBitmap(
                            resizedBitmap2, 0, 0, resizedBitmap2.getWidth(), resizedBitmap2.getHeight(), matrixScale, true); //建立新的Bitmap物件，縮放用


                    // mImageView01.setImageBitmap(resizedBitmapSquare); // 將拍照的圖檔以ImageView顯示出來
                    // saveBitmap(Capture); // 存到sd卡 這行會出現BUG
                    // 顯示完圖檔, 關閉相機
                    resetCamera();



                    }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
                // initCamera(); // 再重新啟動相機繼續預覽
            }
            catch (Exception e)
            {
                Log.e(TAG, e.getMessage());
            }
        }
    };


    class DrawCaptureRect extends View // 繪製相機預覽畫面裡的瞄準框
    {
        private int colorFill;
        private int intLeft,intTop, intWidth,intHeight;

        public DrawCaptureRect(Context context, int intX, int intY, int intWidth, int intHeight, int colorFill)
        {
            super(context);
            this.colorFill = colorFill;
            this.intLeft = intX;
            this.intTop = intY;
            this.intWidth = intWidth;
            this.intHeight = intHeight;
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            Paint mPaint01 = new Paint();
            mPaint01.setStyle(Paint.Style.FILL);
            mPaint01.setColor(colorFill);
            mPaint01.setStrokeWidth(5.0F);
            // 在畫布上繪製紅色的四條方框線
            canvas.drawLine(this.intLeft, this.intTop, this.intLeft+intWidth, this.intTop, mPaint01);
            canvas.drawLine(this.intLeft, this.intTop, this.intLeft, this.intTop+intHeight, mPaint01);
            canvas.drawLine(this.intLeft+intWidth, this.intTop, this.intLeft+intWidth, this.intTop+intHeight, mPaint01);
            canvas.drawLine(this.intLeft, this.intTop+intHeight, this.intLeft+intWidth, this.intTop+intHeight, mPaint01);
            super.onDraw(canvas);
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceholder) // 創建surface
    {
        // TODO Auto-generated method stub
        // initCamera();     // 開啟相機及Preview,自訂初始化開啟相機函數

        try
        {
            if (mCamera01 != null)
            {
                mCamera01.setPreviewDisplay(surfaceholder); // 傳入已初始化的surfaceholder, 才可進行預覽
            }
        }
        catch (Exception exception)
        {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
        Log.i(TAG, "Surface Changed");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceholder, int format, int w, int h) // 改變surface
    {
        // TODO Auto-generated method stub
        Log.i(TAG, "Surface Changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceholder) // 銷毀surface
    {
        /* 當Surface不存在，關閉相機 */
        try
        {
            mCamera01.stopPreview();
            mCamera01.release();
            mCamera01 = null;
            Log.i(TAG, "Surface Destroyed");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /*
    public void exitActivity(int exitMethod)
    {
        // throw new RuntimeException("Exit!");
        try
        {
            switch(exitMethod)
            {
                case 0:
                    System.exit(0);
                    break;
                case 1:
                    android.os.Process.killProcess(android.os.Process.myPid());
                    break;
                case 2:
                    finish();
                    break;
            }
        }
        catch(Exception e)
        {
            finish();
        }
    }
*/
    @Override
    protected void onPause()
    {
        // TODO Auto-generated method stub
        try
        {
            resetCamera(); // Activity失去焦點，釋放相機資源
            mCamera01.release(); // 釋放相機給其他app使用, 通常放在onPause(), 在onResume()再重新open().
            // exitActivity(1);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        super.onPause();
    }



    public void saveBitmap(Bitmap bitmap) {
        // save in SDcard dir and show in SDcard when running
        FileOutputStream fOut;

        File path = Environment.getExternalStorageDirectory(); // 得到 SDcard相對路徑
        // 設定欲儲存的資料夾及檔名
        String dirName = "/DVM/";
        // String fileName = System.currentTimeMillis() + ".jpg";
        String fileName = dateformat()+ ".jpg";
        File file = new File(path, dirName+fileName); // 路徑為"/mnt/sdcard/DVM/SystemTime.jpg"

        try {
            // 若手機內無此資料夾則建立資料夾
            File dir = new File(path.getPath()+dirName);
            if (!dir.exists()) {
                dir.mkdir();
                MediaScannerConnection.scanFile(
                        this, new String[]{path.getPath()+dirName}, null, null); // 建立後立即顯示在電腦資料夾上
            }

            fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);

            MediaScannerConnection.scanFile( // path.getPath()+"/DVM/DemoPicture.jpg"
                    this, new String[]{path.getPath()+dirName+fileName}, null, null); // 儲存後立即顯示在電腦資料夾上

            try {
                 fOut.flush();
                 fOut.close();
            } catch (IOException e) {
                  e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String dateformat(){ // 以當前時間做命名, 格式為 2017-03-02_18-49-23

        Date currentTime = new Date();
        String pattern="yyyy-MM-dd_HH-mm-ss";
        SimpleDateFormat sdf=new SimpleDateFormat(pattern);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+08:00"));
        String dateName = sdf.format(currentTime);

        return dateName;
    }


    class ClientToServer implements Runnable{
        @Override
        public void run()
        {
            try{
                socket = new Socket("134.208.3.118", 100); //create client
                toServer = new DataOutputStream(socket.getOutputStream());

                // 送圖片到server
                Send2CSharp(toServer);

                // 從server接收辨識結果
                // boolean receiveCheck = false;
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                message = "";
                r = "";
                while (true) // (receiveCheck!=false)
                {
                    try {
                        if (!in.ready()) { // server送來的訊息尚未接收完畢
                            if (message != null) {
                                r = message;
                            }
                            if(r != ""){
                                msg = new Message();
                                msg = handler.obtainMessage();
                                msg.obj = message;
                                handler.sendMessage(msg);
                                break;
                            }
                        }
                        // 無限制的接收從c#來的文字
                        int num = in.read();
                        message += Character.toString((char) num);
                    } catch (Exception classNot) {
                        // receiveCheck = true;
                    }
                    /*
                    finally { // 釋放在try裡面所使用的資源, 不管是否例外, 程式的執行順序最後一定會跑到這裡面
                        toServer.close();
                        in.close();
                        socket.close();
                    }*/
                }
            }
            catch(IOException e){
                // 沒連到server, 需要提示使用者
                Toast.makeText(MainActivity.this, "請再連線一次，網路或伺服器異常中", Toast.LENGTH_LONG).show();
                /*
                msg = new Message();
                msg = handler.obtainMessage();
                msg.obj = "請在連一次";
                handler.sendMessage(msg);
                */
            }
        }
        // 從 break 跳出這邊, 可考慮關閉及釋放資源
    }



    public void Send2CSharp(DataOutputStream toServer) {

        try {
            // 影像的資料大小
            // Bitmap ourbitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test1);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Capture.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] b = stream.toByteArray(); // 圖片資料
            int size = b.length; // 1018836 // 測試時送出 65536*6的資料量

            byte[] szbuf = new byte[4]; // 必須先將資料量的大小(int)轉成byte[4]送給server，以便其配置接收緩衝區
            for (int i = 0; i < 4; i++) // 將int轉成4個bytes的陣列
                szbuf[i] = (byte) (0xff & (size >> (8 * i)));
            toServer.write(szbuf); // 送出資料量大小到server
            toServer.flush();

            // Send the radius to the server
            toServer.write(b, 0, size); // 送出資料
            toServer.flush();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }



}