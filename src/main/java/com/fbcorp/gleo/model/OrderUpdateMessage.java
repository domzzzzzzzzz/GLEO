package com.fbcorp.gleo.model;

import com.fbcorp.gleo.domain.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateMessage {
    private Long orderId;
    private String eventCode;
    private String action; // "created", "updated", "deleted"
    private Order order;
}