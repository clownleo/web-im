package me.smilence.eventbean;

/**
 * event bean interface
 * Created by leo on 16-5-8.
 */
abstract public class AdminUserBean extends AdminEventBean{
    public String user;

    public boolean validate(){
        return true;
    }
}
