package cn.rongcloud.im.server.network.http;

import android.util.Log;

import org.apache.http.Header;

import java.io.UnsupportedEncodingException;

/**
 * Used to intercept and handle the responses from requests made using {@link AsyncHttpClient}. The
 * {@link #onSuccess(String)} method is designed to be anonymously overridden with your own response
 * handling code. <p>&nbsp;</p> Additionally, you can override the {@link #onFailure(String,
 * Throwable)}, {@link #onStart()}, and {@link #onFinish()} methods as required. <p>&nbsp;</p> For
 * example: <p>&nbsp;</p>
 * <pre>
 * AsyncHttpClient client = new AsyncHttpClient();
 * client.get("http://www.google.com", new TextHttpResponseHandler() {
 *     &#064;Override
 *     public void onStart() {
 *         // Initiated the request
 *     }
 *
 *     &#064;Override
 *     public void onSuccess(String responseBody) {
 *         // Successfully got a response
 *     }
 *
 *     &#064;Override
 *     public void onFailure(String responseBody, Throwable e) {
 *         // Response failed :(
 *     }
 *
 *     &#064;Override
 *     public void onFinish() {
 *         // Completed the request (either success or failure)
 *     }
 * });
 * </pre>
 */
public class TextHttpResponseHandler extends AsyncHttpResponseHandler {
    private static final String LOG_TAG = "TextHttpResponseHandler";

    /**
     * Creates a new TextHttpResponseHandler
     */

    public TextHttpResponseHandler() {
        this(DEFAULT_CHARSET);
    }

    public TextHttpResponseHandler(String encoding) {
        super();
        setCharset(encoding);
    }

    //
    // Callbacks to be overridden, typically anonymously
    //

    /**
     * Fired when a request fails to complete, override to handle in your own code
     *
     * @param responseBody the response body, if any
     * @param error        the underlying cause of the failure
     */
    public void onFailure(String responseBody, Throwable error) {
    }

    /**
     * Fired when a request fails to complete, override to handle in your own code
     *
     * @param statusCode   the status code of the response
     * @param headers      HTTP response headers
     * @param responseBody the response body, if any
     * @param error        the underlying cause of the failure
     */
    public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
        onFailure(responseBody, error);
    }

    /**
     * Fired when a request returns successfully, override to handle in your own code
     *
     * @param statusCode   the status code of the response
     * @param headers      HTTP response headers
     * @param responseBody the body of the HTTP response from the server
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onSuccess(int statusCode, Header[] headers, String responseBody) {
        onSuccess(statusCode, responseBody);
    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
        try {
            String response = responseBody == null ? null : new String(responseBody, getCharset());
            onSuccess(statusCode, headers, response);
        } catch (UnsupportedEncodingException e) {
            Log.v(LOG_TAG, "String encoding failed, calling onFailure(int, Header[], String, Throwable)");
            onFailure(0, headers, (String) null, e);
        }
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
        try {
            String response = responseBody == null ? null : new String(responseBody, getCharset());
            onFailure(statusCode, headers, response, error);
        } catch (UnsupportedEncodingException e) {
            Log.v(LOG_TAG, "String encoding failed, calling onFailure(int, Header[], String, Throwable)");
            onFailure(0, headers, (String) null, e);
        }
    }

}
