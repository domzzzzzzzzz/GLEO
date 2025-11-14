package com.fbcorp.gleo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUpload(MaxUploadSizeExceededException ex,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        String uri = request.getRequestURI();
        redirectAttributes.addFlashAttribute("toastError", "QR image is too large. Please upload a photo under 8 MB.");
        return "redirect:" + uri;
    }
}
