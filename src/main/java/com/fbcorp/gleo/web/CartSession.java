package com.fbcorp.gleo.web;

import java.io.Serializable;
import java.util.*;

public class CartSession implements Serializable {
    // vendorId -> (itemId -> qty)
    private final Map<Long, Map<Long, Integer>> lines = new LinkedHashMap<>();

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
    }

    public boolean isEmpty(){ return lines.isEmpty(); }
}
