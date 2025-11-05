package com.fbcorp.gleo.web;

public class MenuOrderItem {
    private String text;
    private String href;

    public MenuOrderItem() {}

    public MenuOrderItem(String text, String href) {
        this.text = text;
        this.href = href;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }
}
