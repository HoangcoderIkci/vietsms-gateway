package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.webhook.exceptions.WebhookException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * SSRF-safe URL validator for webhook endpoint URLs.
 *
 * <p>The {@code HostResolver} functional interface allows tests to stub DNS resolution
 * without making real network calls.</p>
 */
@Component
public class UrlValidator {

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver resolver;

    /** Production constructor: uses real DNS. */
    public UrlValidator() {
        this.resolver = InetAddress::getAllByName;
    }

    /** Test-friendly constructor: supply a stubbed resolver. */
    public UrlValidator(HostResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Validates the URL for use as a webhook endpoint.
     *
     * @throws WebhookException with code WEBHOOK_URL_INVALID (422) if the URL is not safe.
     */
    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            reject("URL cannot be parsed: " + e.getMessage());
            return; // unreachable but satisfies compiler
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            reject("URL scheme must be http or https");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            reject("URL must have a host");
        }

        // Reject obvious loopback by hostname before DNS
        if ("localhost".equalsIgnoreCase(host)) {
            reject("URL host resolves to a private or loopback address");
        }

        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            reject("URL host cannot be resolved: " + host);
            return;
        }

        if (addresses == null || addresses.length == 0) {
            reject("URL host cannot be resolved: " + host);
        }

        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()) {
                reject("URL host resolves to a private or loopback address");
            }
        }
    }

    private void reject(String reason) {
        throw new WebhookException(
                "WEBHOOK_URL_INVALID",
                "Invalid webhook URL: " + reason,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
