package com.fbcorp.gleo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;

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

    @ExceptionHandler(NoHandlerFoundException.class)
    public ModelAndView handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        System.err.println("404 Not Found: " + ex.getRequestURL() + " error : " + ex );
        return buildErrorView("error/404",
                HttpStatus.NOT_FOUND,
                "Page not found.",
                "We couldn't find the page you're looking for.",
                request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String view = status.is4xxClientError() ? "error/404" : "error/500";
        String message = ex.getReason() != null ? ex.getReason() : defaultMessage(status);
        System.err.println("Error " + status.value() + " : " + message + " at " + request.getRequestURI() + " error: " + ex);
        return buildErrorView(view, status, defaultTitle(status), message, request);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex, HttpServletRequest request) {
        System.err.println("500 Internal Server Error at " + request.getRequestURI() + " error: " + ex);
        return buildErrorView("error/500",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error.",
                "Something went wrong on our side. The team has been notified.",
                request);
    }

    private ModelAndView buildErrorView(String viewName,
                                        HttpStatus status,
                                        String title,
                                        String message,
                                        HttpServletRequest request) {
        ModelAndView mav = new ModelAndView(viewName);
        mav.setStatus(status);
        mav.addObject("title", title);
        mav.addObject("message", message);
        mav.addObject("statusCode", status.value());
        mav.addObject("statusText", status.getReasonPhrase());
        mav.addObject("path", request.getRequestURI());
        mav.addObject("timestamp", OffsetDateTime.now());
       System.err.println("Error View Built: " + status.value() + " - " + title + " - " + message);
        return mav;
    }

    private String defaultTitle(HttpStatus status) {
        if (status.is4xxClientError()) {
            return "Something's missing.";
        }
        return "Something broke.";
    }

    private String defaultMessage(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND)
         {
            return "We looked everywhere but couldn't find that page.";
        }
        if (status.is4xxClientError()) {
            return "This request can't be completed as sent.";
        }
        return "An internal error occurred.";
    }
}
