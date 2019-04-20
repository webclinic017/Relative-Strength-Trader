package com.pfeiffercapital.rst;

public class SignalFileRenamingWorkflow implements Runnable {

    static int counter = 0;

    @Override
    public void run() {

        System.out.println("I'm here for the " + (++counter) + "th time");
    }
}
