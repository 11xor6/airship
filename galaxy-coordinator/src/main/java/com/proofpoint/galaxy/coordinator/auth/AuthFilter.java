package com.proofpoint.galaxy.coordinator.auth;

import ch.ethz.ssh2.crypto.Base64;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.proofpoint.units.Duration;
import org.apache.commons.codec.DecoderException;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.list;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static org.apache.commons.codec.binary.Hex.decodeHex;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

public class AuthFilter
        implements Filter
{
    public static final String AUTHORIZED_KEY_ATTRIBUTE = "AuthorizedKey";
    private static final Duration MAX_REQUEST_TIME_SKEW = new Duration(5, TimeUnit.MINUTES);

    private final SignatureVerifier verifier;

    @Inject
    public AuthFilter(SignatureVerifier verifier)
    {
        this.verifier = verifier;
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    /**
     * Verify authorization header:
     * <pre>
     * Authorization: Galaxy fingerprint:signature
     * fingerprint = hex md5 of private key
     * signature = base64 signature of [ts, method, uri, bodyMd5]
     * </pre>
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // parse authorization header
        if (list(request.getHeaders("Authorization")).size() > 1) {
            sendError(response, BAD_REQUEST, "Multiple Authorization headers");
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null) {
            sendError(response, BAD_REQUEST, "Missing Authorization header");
            return;
        }
        List<String> authTokens = ImmutableList.copyOf(Splitter.on(' ').omitEmptyStrings().split(authorization));
        if ((authTokens.size() != 2) || (!authTokens.get(0).equals("Galaxy"))) {
            sendError(response, BAD_REQUEST, "Invalid Authorization header");
            return;
        }
        List<String> authParts = ImmutableList.copyOf(Splitter.on(':').split(authTokens.get(1)));
        if (authParts.size() != 2) {
            sendError(response, BAD_REQUEST, "Invalid Authorization token");
            return;
        }

        // parse authorization token
        String hexFingerprint = authParts.get(0);
        String base64Signature = authParts.get(1);
        byte[] fingerprint;
        try {
            fingerprint = decodeHex(hexFingerprint.toCharArray());
        }
        catch (DecoderException e) {
            sendError(response, BAD_REQUEST, "Invalid Authorization fingerprint encoding");
            return;
        }
        if (fingerprint.length != 16) {
            sendError(response, BAD_REQUEST, "Invalid Authorization fingerprint length");
            return;
        }
        byte[] signature;
        try {
            signature = Base64.decode(base64Signature.toCharArray());
        }
        catch (IOException e) {
            sendError(response, BAD_REQUEST, "Invalid Authorization signature encoding");
            return;
        }

        // get unix timestamp from request time
        long millis;
        try {
            millis = request.getDateHeader("Date");
        }
        catch (IllegalArgumentException e) {
            sendError(response, BAD_REQUEST, "Invalid Date header");
            return;
        }
        if (millis == -1) {
            sendError(response, BAD_REQUEST, "Missing Date header");
            return;
        }
        long serverTime = currentTimeMillis();
        if (abs(serverTime - millis) > MAX_REQUEST_TIME_SKEW.toMillis()) {
            sendError(response, BAD_REQUEST, format("Request time too skewed (server time: %s)", serverTime / 1000));
            return;
        }
        long timestamp = millis / 1000;

        // get method and uri with query parameters
        String method = request.getMethod();
        String uri = getRequestUri(request);

        // wrap request to allow reading body
        RequestWrapper requestWrapper = new RequestWrapper(request);
        String bodyMd5 = md5Hex(requestWrapper.getRequestBody());

        // compute signature payload
        String stringToSign = Joiner.on('\n').join(timestamp, method, uri, bodyMd5);
        byte[] bytesToSign = stringToSign.getBytes(Charsets.UTF_8);

        // verify signature
        AuthorizedKey authorizedKey = verifier.verify(fingerprint, signature, bytesToSign);
        if (authorizedKey == null) {
            sendError(response, FORBIDDEN, "Signature verification failed");
            return;
        }
        request.setAttribute(AUTHORIZED_KEY_ATTRIBUTE, authorizedKey);

        chain.doFilter(requestWrapper, response);
    }

    @Override
    public void destroy()
    {
    }

    private static void sendError(HttpServletResponse response, Response.Status status, String error)
            throws IOException
    {
        response.reset();
        response.setStatus(status.getStatusCode());
        response.setContentType(MediaType.TEXT_PLAIN);
        PrintWriter writer = response.getWriter();
        writer.println(error);
        writer.close();
    }

    private static String getRequestUri(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null) ? uri : (uri + "?" + query);
    }
}
