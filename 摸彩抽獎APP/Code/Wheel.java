package com.practice.chying.lotteryfordorm;

/**
 * Created by CHYing on 2017/6/16.
 */

public class Wheel extends Thread {

    interface WheelListener {
        void newImage(int img);
    }

    private static int[] imgs = {
            R.drawable.slot1, R.drawable.slot2, R.drawable.slot3,
            R.drawable.slot4, R.drawable.slot5, R.drawable.slot6,
            R.drawable.slot7, R.drawable.slot8, R.drawable.slot9,
            R.drawable.slot0};

    public int currentIndex;
    private WheelListener wheelListener;
    private long frameDuration;
    private long startIn;
    private boolean isStarted;
    public int number;

    public Wheel(WheelListener wheelListener, long frameDuration, long startIn, int number) {
        this.wheelListener = wheelListener;
        this.frameDuration = frameDuration;
        this.startIn = startIn;
        this.number = number;
        currentIndex = 0;
        isStarted = true;
    }

    public void nextImg() {
        currentIndex++;
        if (currentIndex == number) { // == imgs.length
            currentIndex = 0;
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(startIn);
        } catch (InterruptedException e) {
        }

        while(isStarted) {
            try {
                Thread.sleep(frameDuration);
            } catch (InterruptedException e) {
            }

            nextImg();

            if (wheelListener != null) {
                wheelListener.newImage(imgs[currentIndex]);
            }
        }
    }

    public void stopWheel() {
        isStarted = false;
    }

}
