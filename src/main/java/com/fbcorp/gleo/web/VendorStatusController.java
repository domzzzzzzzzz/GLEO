package com.fbcorp.gleo.web;

import com.fbcorp.gleo.model.VendorStatus;
import com.fbcorp.gleo.service.VendorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendors")
public class VendorStatusController {

    private final VendorService vendorService;

    public VendorStatusController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    @PostMapping("/{vendorId}/status")
    public ResponseEntity<VendorStatus> updateVendorStatus(
            @PathVariable Long vendorId,
            @RequestParam VendorStatus status) {
        vendorService.updateVendorStatus(vendorId, status);
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/{vendorId}/status")
    public ResponseEntity<VendorStatus> getVendorStatus(@PathVariable Long vendorId) {
        VendorStatus status = vendorService.getVendorStatus(vendorId);
        return ResponseEntity.ok(status);
    }
}