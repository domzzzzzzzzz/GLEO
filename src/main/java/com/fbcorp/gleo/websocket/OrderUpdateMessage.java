package com.fbcorp.gleo.websocket;

public class OrderUpdateMessage {
    private Long orderId;
    private String eventCode;
    private String status;
    private String customerName;
    private String vendorName;
    private String items;

    public OrderUpdateMessage() {
    }

    public OrderUpdateMessage(Long orderId, String eventCode, String status, String customerName, String vendorName, String items) {
        this.orderId = orderId;
        this.eventCode = eventCode;
        this.status = status;
        this.customerName = customerName;
        this.vendorName = vendorName;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }
}