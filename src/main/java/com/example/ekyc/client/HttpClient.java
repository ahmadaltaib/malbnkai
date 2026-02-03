package com.example.ekyc.client;

/**
 * Interface for HTTP client operations.
 */
public interface HttpClient {
    
    /**
     * Makes a POST request to the specified URL.
     * 
     * @param url The URL to send the request to
     * @param body The request body object (will be serialized to JSON)
     * @param timeoutSeconds The timeout in seconds
     * @return The service response
     */
    ServiceResponse post(String url, Object body, int timeoutSeconds);
}
