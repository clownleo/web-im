package me.smilence.eventbean;

/**
 * event bean interface
 * Created by leo on 16-5-8.
 */
abstract public class AdminGroupBean extends AdminEventBean{
    public String group;

    public boolean validate(){
        return true;
    }
}
