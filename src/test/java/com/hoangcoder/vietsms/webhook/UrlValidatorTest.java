package com.hoangcoder.vietsms.webhook;

import com.hoangcoder.vietsms.webhook.exceptions.WebhookException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class UrlValidatorTest {

    /** Stubbed resolver that returns a public IP (8.8.8.8) — simulates external host. */
    private static final UrlValidator.HostResolver PUBLIC_RESOLVER = host -> {
        try {
            return new InetAddress[]{InetAddress.getByName("8.8.8.8")};
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };

    private static final UrlValidator.HostResolver LOOPBACK_RESOLVER = host -> {
        try {
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };

    private static final UrlValidator.HostResolver SITE_LOCAL_RESOLVER_192 = host -> {
        try {
            return new InetAddress[]{InetAddress.getByName("192.168.1.1")};
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };

    private static final UrlValidator.HostResolver SITE_LOCAL_RESOLVER_10 = host -> {
        try {
            return new InetAddress[]{InetAddress.getByName("10.0.0.1")};
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    };

    private static final UrlValidator.HostResolver UNRESOLVABLE_RESOLVER = host -> {
        throw new UnknownHostException("no such host: " + host);
    };

    @Test
    void https_public_url_is_valid() {
        UrlValidator validator = new UrlValidator(PUBLIC_RESOLVER);
        assertThatNoException().isThrownBy(() ->
                validator.validate("https://my-app.example.com/hooks/vietsms")
        );
    }

    @Test
    void http_public_url_is_valid() {
        UrlValidator validator = new UrlValidator(PUBLIC_RESOLVER);
        assertThatNoException().isThrownBy(() ->
                validator.validate("http://my-app.example.com/webhook")
        );
    }

    @Test
    void localhost_is_rejected() {
        UrlValidator validator = new UrlValidator(PUBLIC_RESOLVER); // resolver not even called for localhost
        assertThatThrownBy(() -> validator.validate("http://localhost/x"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> {
                    WebhookException we = (WebhookException) ex;
                    assertThat(we.getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID");
                    assertThat(we.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
    }

    @Test
    void loopback_ip_is_rejected() {
        UrlValidator validator = new UrlValidator(LOOPBACK_RESOLVER);
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/x"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }

    @Test
    void site_local_192_is_rejected() {
        UrlValidator validator = new UrlValidator(SITE_LOCAL_RESOLVER_192);
        assertThatThrownBy(() -> validator.validate("http://192.168.1.1/x"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }

    @Test
    void site_local_10_is_rejected() {
        UrlValidator validator = new UrlValidator(SITE_LOCAL_RESOLVER_10);
        assertThatThrownBy(() -> validator.validate("http://10.0.0.1/x"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }

    @Test
    void ftp_scheme_is_rejected() {
        UrlValidator validator = new UrlValidator(PUBLIC_RESOLVER);
        assertThatThrownBy(() -> validator.validate("ftp://host/path"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "//host/path",
            "host/path"
    })
    void no_scheme_is_rejected(String url) {
        UrlValidator validator = new UrlValidator(PUBLIC_RESOLVER);
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }

    @Test
    void unresolvable_host_is_rejected() {
        UrlValidator validator = new UrlValidator(UNRESOLVABLE_RESOLVER);
        assertThatThrownBy(() -> validator.validate("https://this-host-does-not-exist.invalid/hook"))
                .isInstanceOf(WebhookException.class)
                .satisfies(ex -> assertThat(((WebhookException) ex).getErrorCode()).isEqualTo("WEBHOOK_URL_INVALID"));
    }
}
