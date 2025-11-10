package com.fbcorp.gleo.web;

import java.io.Serializable;
import java.util.*;

public class CartSession implements Serializable {
    // vendorId -> (itemId -> qty)
    private final Map<Long, Map<Long, Integer>> lines = new LinkedHashMap<>();
    // Optional notes per vendor (special instructions)
    private final Map<Long, String> vendorNotes = new LinkedHashMap<>();
    // Optional cart-wide promo code
    private String promoCode;

    public void add(Long vendorId, Long itemId, int qty){
        lines.computeIfAbsent(vendorId, v -> new LinkedHashMap<>());
        Map<Long, Integer> m = lines.get(vendorId);
        m.put(itemId, m.getOrDefault(itemId, 0) + qty);
    }

    public void setQty(Long vendorId, Long itemId, int qty){
        lines.computeIfAbsent(vendorId, v -> new LinkedHashMap<>()).put(itemId, qty);
    }

    public void removeItem(Long vendorId, Long itemId){
        Map<Long, Integer> m = lines.get(vendorId);
        if (m != null){
            m.remove(itemId);
            if (m.isEmpty()) lines.remove(vendorId);
        }
    }

    public void removeVendorGroup(Long vendorId){
        lines.remove(vendorId);
    }

    public Map<Long, Map<Long, Integer>> getAll(){
        return lines;
    }

    public void clear(){
        lines.clear();
        vendorNotes.clear();
        promoCode = null;
    }

    public boolean isEmpty(){ return lines.isEmpty(); }

    // Notes API
    public void setVendorNote(Long vendorId, String note){
        if (note == null || note.trim().isEmpty()) {
            vendorNotes.remove(vendorId);
        } else {
            vendorNotes.put(vendorId, note.trim());
        }
    }
    public String getVendorNote(Long vendorId){
        return vendorNotes.get(vendorId);
    }
    public Map<Long, String> getAllVendorNotes(){
        return vendorNotes;
    }

    // Promo API
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = (promoCode == null || promoCode.isBlank()) ? null : promoCode.trim(); }
}
