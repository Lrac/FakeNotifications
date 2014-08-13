package com.hfast.fakenotification;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Carl on 2014-07-30.
 */
public class TextMessage {

    private String[] messages;
    private String sender;
    private String recentMessage;
    private String date;
    private List<String> messageList = new ArrayList<String>();
    private DateFormat df = new SimpleDateFormat("h:mm a");
    private int id;

    public TextMessage (String sender, String message, int id){
        this.sender = sender;
        messageList.add(message);
        if (message.startsWith("Self:")){
            recentMessage = message.substring(5);
        } else {
            recentMessage = message;
        }
        date = df.format(Calendar.getInstance().getTime());
        this.id = id;
    }

    public void addMessage(String newMessage){
        messageList.add(newMessage);
        recentMessage = newMessage;
        date = df.format(Calendar.getInstance().getTime());
    }

    public void addSelfMessage(String newMessage){
        messageList.add("Self:"+ newMessage);
        recentMessage = newMessage;
        date = df.format(Calendar.getInstance().getTime());
    }

    public String getSender(){
        return sender;
    }

    public List<String> getAllMessages(){
        return messageList;
    }

    public String getRecentMessage(){
        return recentMessage;
    }

    public String getDate(){
        return date;
    }

    public int getId(){
        return id;
    }

}
