package cn.jpush.eventbean;

/**
 * event bean interface
 * Created by leo on 16-5-8.
 */
abstract public class EventBean {
    public long rid;

    public boolean validate(){
//        return rid != 0;
        return true;
    }
}
