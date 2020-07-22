package com.ing.psd2pega.service.impl;

import com.ing.psd2pega.service.PsdService;
import org.springframework.stereotype.Service;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
public class PsdServiceImpl implements PsdService {
    private final String SESSION_ID = "JSESSIONID";

    //The current session is stored in a cookie.
    private NewCookie sessionId;
//    PsdServiceImpl psdService;
//
//    {
//        psdService = new PsdServiceImpl();
//    }

    @Override
    public String getPSDinfo(String fkn, String pin) {
        Response response = authorizationRequest();
        Object [] responseAndRedirectUri = redirectToLoginPage(response);
        response = loginAndAuthorize(responseAndRedirectUri, fkn, pin);
        response = grantAccess(response);
        System.out.println("response " +response);
        String accessToken = returnToRedirectAndGetAccessToken(response);


        //String accessToken = "eyJraWQiOiJzaWduZXIyMDIwLWVjIiwiYWxnIjoiRVMyNTYifQ.eyJzdWIiOiIxMDAxMDA0NTM3MDAwMDEiLCJhenAiOiJkZXZlbG9wZXJwb3J0YWwiLCJpc3MiOiJodHRwczpcL1wvc2ltdWxhdG9yLWFwaS5kYi5jb21cL2d3XC9vaWRjXC8iLCJleHAiOjE1OTU0MDA2OTEsImlhdCI6MTU5NTM5NzA5MSwianRpIjoiMGQxZjM1NWEtZmNhYS00YmM4LTgzYzktZWU1MjM0MzQzZjZhIn0.IhyWPt6wKKV5jSPhKYA0eNxeTpK1UwegLaVujXhheUktpEhXq0hRTnPDLVCAMjJYH0dmnJ_RrEtNpzMZnXMA7Q";


        WebTarget wt = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
                .target("https://simulator-api.db.com/gw/dbapi/banking/cashAccounts/v2");
                //.target("https://simulator-api.db.com:443/gw/dbapi/referenceData/partners/v2");

        Response response1 = wt.request()
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .get();

        System.out.println("Calling dbAPI cashAccounts endpoint done. The JSON response is:");
        String jsonResponse = response1.readEntity(String.class);
        System.out.println(jsonResponse);

        return jsonResponse;
    }

    private Response authorizationRequest() {

        WebTarget wt = ClientBuilder.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
                .target("https://simulator-api.db.com/gw/oidc/authorize");

        //Please login to activate your client. The client_id and redirect_uri will be replaced with your activated client.
        Response response = wt.property("jersey.config.client.followRedirects", false)
                .queryParam("response_type", "token")
                .queryParam("client_id", "6581052a-6a2a-4ce1-9530-2b64f3468d56")
                .queryParam("redirect_uri", "https://localhost:8443")
                .queryParam("scope", "read_accounts")
                .queryParam("state", "0.21581183640296075")
                .request()
                .get();

        updateSessionId(response);
        System.out.println("Step 1 executed authorizeRequest.");
        return response;
    }

    private Object[] redirectToLoginPage(Response response) {
        /*
         * We have to follow the redirect manually here because the automatic
         * redirect in the HttpUrlConnection doesn't forward the cookie, i.e.
         */
        URI uri = response.getLocation();
        response =  ClientBuilder.newClient().target(uri)
                .property("jersey.config.client.followRedirects", false)
                .request()
                .cookie(sessionId).get();

        updateSessionId(response);

        System.out.println("Step 2 executed redirected to login page.");
        return new Object[] {response, uri};
    }

    private Response loginAndAuthorize(Object [] responseAndRedirectUri, String username, String password) {
        Response response = (Response) responseAndRedirectUri[0];
        URI uri = (URI) responseAndRedirectUri[1];

        // extract CSRF token for this session
        String webPage = response.readEntity(String.class);
        String csrf = getCsrf(webPage);

        //get the action from the login page
        URI postUrl = getFormPostUrl(uri, webPage);
        // post login
        Form form = new Form();
        form.param("username", username);
        form.param("password", password);
        form.param("_csrf", csrf);
        form.param("submit", "Login");

        response = ClientBuilder.newClient().target(postUrl)
                .property("jersey.config.client.followRedirects", false)
                .request()
                .cookie(sessionId)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        updateSessionId(response);

        if(response.getLocation().toString().contains("noaccess")
                || response.getLocation().toString().contains("commonerror")
                || response.getLocation().toString().contains("failure")) {
            String message = response.readEntity(String.class);
            System.out.println("Failed to login as expected " + username + " loc = " + response.getLocation() + " msg = " + message);
        }

        System.out.println("Step 3.1 login with fkn and pin and authorization done.");
        return  response;
    }

    private Response grantAccess(Response response) {
        URI uri = response.getLocation();
        System.out.println("URI" +uri.toString());
        response = ClientBuilder.newClient().target(uri)
                .property("jersey.config.client.followRedirects", false)
                .request().cookie(sessionId).get();
        updateSessionId(response);

        // grant access
        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {

            String webPage = response.readEntity(String.class);
            String csrf = getCsrf(webPage);
            //get the action from the consent page
            URI postUrl = getFormPostUrl(uri, webPage);
            updateSessionId(response);

            // post consent
            Form form = new Form();
            form.param("user_oauth_approval", "true");
            form.param("_csrf", csrf);
            // give the consent once
            form.param("remember", "none");
            form.param("scope_read_accounts" , "read_accounts");

            response = ClientBuilder.newClient().target(postUrl).property("jersey.config.client.followRedirects", false)
                    .request().cookie(sessionId).post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

            System.out.println("Step 3.2 authorize access with requested scope read_accounts on consent screen.");
            return response;

        }
        return null;
    }

    public String returnToRedirectAndGetAccessToken(Response response) {
        URI loc = response.getLocation();
        String accessToken = null;
        if(loc != null) {
            accessToken = getAccessToken(loc.toString());
        }
        System.out.println("Successfully get an access token.");
        return accessToken;
    }

    /**
     * Get sessionId from cookie from response and set local sessionId.
     *
     * @param response The current {@link Response}.
     */
    private void updateSessionId(Response response) {
        NewCookie cookie = response.getCookies().get(SESSION_ID);
        if(cookie != null) {
            sessionId = cookie;
        }
    }

    /**
     * Just for internal use to avoid potential CSRF attacks .
     * You can read the RFC against CSRF attacks here: https://tools.ietf.org/html/rfc6749.
     *
     * @param webPage The login or consent screen.
     * @return The CSRF code if found, null else.
     */
    static String getCsrf(String webPage) {
        Pattern p = Pattern.compile(" name=\"_csrf\" value=\"(.*?)\"");
        Matcher m = p.matcher(webPage);
        if ( m.find() ) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Helper method. Get URI that is called from action in given HTML page.
     *
     * @param target  The target {@link URI}.
     * @param webPage The login or consent screen.
     * @return
     */
    protected URI getFormPostUrl(URI target, String webPage) {
        Pattern pattern = Pattern.compile("action=\"(.+?)\"");
        Matcher matcher = pattern.matcher(webPage);
        if ( matcher.find() ) {
            String uf = matcher.group(1);
            URI uri = URI.create(uf);
            if(!uri.isAbsolute()) {
                URI targetUri = target.resolve(uri);
                return targetUri;
            }
            return uri;
        }
        return null;
    }

    /**
     * Helper method to extract the access token from the given string.
     *
     * @param uri The URI which contains the access token.
     * @return The access token if available.
     */
    protected String getAccessToken(String uri) {
        String accessToken = getTokenFromString(uri, "access_token=([\\d\\w\\.-]+)&");
        System.out.println("access_token = " + accessToken);
        return accessToken;
    }

    /**
     * Helper method. Get first match from given String.
     *
     * @param uri The string which have to be analyzed.
     * @param pattern The Regex-Pattern for searching.
     * @return Get the first match of the given String or null.
     */
    protected String getTokenFromString(String uri, String pattern) {
        Pattern tokenPattern = Pattern.compile(pattern);
        Matcher tokenMatcher = tokenPattern.matcher(uri);
        if (tokenMatcher.find()) {
            return tokenMatcher.group(1);
        }
        return null;
    }
}
