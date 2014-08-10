package com.hfast.fakenotification;

/**
 * Created by Carl on 2014-07-16.
 */
public interface MessageDisplayInterface {
    void displayAndroidCall(String caller, String number);
    void displayAndroidText(String sender, String message);
    void displayPebbleCall(String caller, String number);
    void displayPebbleText(String sender, String message);

}
