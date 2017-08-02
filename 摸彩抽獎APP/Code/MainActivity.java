package com.practice.chying.lotteryfordorm;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextView msg;
    private ImageView img1, img2, img3;
    private Button btn;
    private Wheel wheel1, wheel2, wheel3;
    private  boolean isStarted;
    int total = 0;

    public static final Random RANDOM = new Random();

    public static long randomLong(long lower, long upper){
        return lower + (long) (RANDOM.nextDouble()*(upper-lower));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        img1 = (ImageView) findViewById(R.id.img1);
        img2 = (ImageView) findViewById(R.id.img2);
        img3 = (ImageView) findViewById(R.id.img3);
        btn = (Button) findViewById(R.id.btn);
        msg = (TextView)  findViewById(R.id.msg);


        btn.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view){
                if (isStarted){
                    wheel1.stopWheel();
                    wheel2.stopWheel();
                    wheel3.stopWheel();

                    int a = ( (wheel1.currentIndex<3)? wheel1.currentIndex+2:1);
                    int b = ( (wheel2.currentIndex<4)? wheel2.currentIndex+2:1);
                    int c = ( (wheel3.currentIndex<8)? wheel3.currentIndex+2:1);


                    String[] name = {"Michael", "Tina", "John", "Junny", "Alice", "Max"};

                    // String result = "恭喜 " + a + b + c + " 王曉明";
                    String result = "恭喜 " + a + b + c + " " + name[total];
                    msg.setText(result);
                    total++;



                    btn.setText("start");
                    isStarted = false;

                }else{

                    wheel1 = new Wheel(new Wheel.WheelListener() {
                        @Override // 檢查覆蓋父類別方法是否寫錯
                        public void newImage(final int img) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    img1.setImageResource(img);
                                }
                            });
                        }
                    }, 50, randomLong(0, 200), 4);

                    wheel1.start();

                    wheel2 = new Wheel(new Wheel.WheelListener() {
                        @Override
                        public void newImage(final int img) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    img2.setImageResource(img);
                                }
                            });
                        }
                    }, 65, randomLong(0, 150), 5);

                    wheel2.start();

                    wheel3 = new Wheel(new Wheel.WheelListener() {
                        @Override
                        public void newImage(final int img) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    img3.setImageResource(img);
                                }
                            });
                        }
                    }, 80, randomLong(150, 400), 9);

                    wheel3.start();

                    btn.setText("Stop");
                    msg.setText("");
                    isStarted = true;
                }
            }
        });
    }
}