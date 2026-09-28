package org.csanchez.adk.agents.k8sagent.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for A2A endpoints
 * Catches any exceptions that escape the controller methods
 */
@ControllerAdvice
public class GlobalExceptionHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleAllExceptions(Exception e, WebRequest request) {
		logger.error("Unhandled exception in A2A controller", e);
		logger.error("Request URI: {}", request.getDescription(false));
		
		java.util.Map<String, Object> errorResponse = new java.util.LinkedHashMap<>();
		errorResponse.put("success", false);
		errorResponse.put("error", "Unhandled error: " + e.getMessage());
		
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(errorResponse);
	}
}

