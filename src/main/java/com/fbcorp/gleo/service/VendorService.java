package com.fbcorp.gleo.service;

import com.fbcorp.gleo.model.VendorStatus;
import com.fbcorp.gleo.model.StatusUpdateMessage;
import com.fbcorp.gleo.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class VendorService {
    
    private final VendorRepository vendorRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    public VendorService(VendorRepository vendorRepository, 
                        SimpMessagingTemplate messagingTemplate) {
        this.vendorRepository = vendorRepository;
        this.messagingTemplate = messagingTemplate;
    }
    
    @Transactional
    public void updateVendorStatus(Long vendorId, VendorStatus status) {
        var vendor = vendorRepository.findById(vendorId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
        vendor.setStatus(status);
        vendorRepository.save(vendor);
        
        // Broadcast status update to all clients
        messagingTemplate.convertAndSend(
            "/topic/vendor-status/" + vendor.getEvent().getCode(),
            new StatusUpdateMessage(vendorId, vendor.getEvent().getCode(), status)
        );
    }
    
    public VendorStatus getVendorStatus(Long vendorId) {
        return vendorRepository.findById(vendorId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"))
            .getStatus();
    }
}