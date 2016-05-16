package me.smilence;
/**
 * Web-IM Protocol Event
 * Created by leo on 16/1/25.
 */
public class IMEvent {
    public static final String
            LOGIN = "login",
            LOGOUT = "logout",
            REGISTER = "register",
            GET_KET_ENCRYPTED="get key encrypted",
            GET_KET_ENCRYPTED_bck="get key encrypted bck",
            MSG_SYNC = "msg_sync",
            NEW_STAMP = "new stamp",
        SET_KEY_BCK = "set key bck",
        RESET_KEY = "reset key",
        ADD_FRIEND = "add friend",
        REPLY_OF_ADD_FRIEND = "reply of add friend",
        REMOVE_FRIEND = "remove friend",
        SEND_TO_FREIND = "send to friend",
        GET_FRIENDS = "get friends",
        JOIN_GROUP = "join group",
        REPLY_OF_JOIN_GROUP = "reply of join group",
        ADD_GROUP = "add group",
        GET_GROUP_INFO = "get group info",
        GET_CHAT_KEY = "get chat key",
        SET_CHAT_KEY = "set chat key",
        GET_PUBLIC_KEY = "get public key",
        GET_GROUPS = "get groups",
        GET_GROUP_MEMBERS = "get group members",
        EXIT_GROUP = "exit group",
        REMOVE_GROUP_MEMBER = "remove group member";


}
