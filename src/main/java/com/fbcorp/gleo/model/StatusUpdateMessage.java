package com.fbcorp.gleo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateMessage {
    private Long vendorId;
    private String eventCode;
    private VendorStatus status;
}