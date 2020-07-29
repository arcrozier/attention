package com.aracroproducts.attention;

public class User {

    private String uid;
    public String token;

    public User(String uid, String token) {
        this.uid = uid;
        this.token = token;
    }

    public User() {
        this(null, null);
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUid() {
        return uid;
    }

    public String getToken() {
        return token;
    }
}
